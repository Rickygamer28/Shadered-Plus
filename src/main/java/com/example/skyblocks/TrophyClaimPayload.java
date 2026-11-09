package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Arcade cabinet -> server: the holder of a game's best score claims its trophy (once for each best score). */
public record TrophyClaimPayload(BlockPos pos, int game) implements CustomPacketPayload {
    public static final Type<TrophyClaimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "trophy_claim"));
    public static final StreamCodec<ByteBuf, TrophyClaimPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TrophyClaimPayload::pos, ByteBufCodecs.VAR_INT, TrophyClaimPayload::game, TrophyClaimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrophyClaimPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(p.pos()) || player.distanceToSqr(p.pos().getCenter()) > 10 * 10
                    || !(level.getBlockEntity(p.pos()) instanceof ArcadeBlockEntity be)) {
                return;
            }
            String name = player.getGameProfile().getName();
            if (!be.claim(p.game(), name)) {
                player.displayClientMessage(Component.translatable("message.skyblocks.trophy_no"), true);
                return;
            }
            ItemStack trophy = new ItemStack(ModItems.TROPHY.get());
            trophy.set(ModComponents.TROPHY.get(), new ModComponents.Trophy(name, ArcadeBlockEntity.TITLES[p.game()], be.best(p.game())));
            if (!player.getInventory().add(trophy)) {
                player.drop(trophy, false);
            }
            level.playSound(null, p.pos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
        });
    }
}
