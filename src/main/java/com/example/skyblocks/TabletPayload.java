package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tablet window -> server: the same asks as a television's (TelevisionPayload's actions), for the tablet in this hand. */
public record TabletPayload(int hand, int action, String text, long value) implements CustomPacketPayload {
    public static final Type<TabletPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "tablet"));

    public static final StreamCodec<ByteBuf, TabletPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletPayload::hand,
            ByteBufCodecs.VAR_INT, TabletPayload::action,
            ByteBufCodecs.stringUtf8(TelevisionBlockEntity.MAX_SOURCE * 4), TabletPayload::text,
            ByteBufCodecs.VAR_LONG, TabletPayload::value,
            TabletPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TabletPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            ItemStack stack = player.getItemInHand(p.hand() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!stack.is(ModItems.TABLET.get())) {
                return;
            }
            TabletState s = stack.getOrDefault(ModComponents.TABLET.get(), TabletState.EMPTY);
            if (p.action() == TelevisionPayload.CAST) {
                // (what it plays, where it is, onto a television: value its position)
                net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.of(p.value());
                if (player.distanceToSqr(at.getCenter()) < 48 * 48 && player.level().isLoaded(at)
                        && player.level().getBlockEntity(TelevisionBlock.anchor(player.level(), at)) instanceof TelevisionBlockEntity tv && !s.source().isEmpty()) {
                    tv.castFrom(s, player.getGameProfile().getName());
                }
                return;
            }
            stack.set(ModComponents.TABLET.get(), s.apply(p.action(), p.text(), p.value(), player.level().getGameTime(),
                    player.getGameProfile().getName(), player.getRandom()));
        });
    }
}
