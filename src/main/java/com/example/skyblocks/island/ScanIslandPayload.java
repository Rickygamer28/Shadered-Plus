package com.example.skyblocks.island;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.ModItems;
import com.example.skyblocks.SkyBlocksMod;
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

/** Island Scanner menu -> server: save the settings, and scan if asked. */
public record ScanIslandPayload(BlockPos pos, int size, int above, int depth, boolean scan) implements CustomPacketPayload {
    public static final Type<ScanIslandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "scan_island"));

    public static final StreamCodec<ByteBuf, ScanIslandPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ScanIslandPayload::pos,
            ByteBufCodecs.VAR_INT, ScanIslandPayload::size,
            ByteBufCodecs.VAR_INT, ScanIslandPayload::above,
            ByteBufCodecs.VAR_INT, ScanIslandPayload::depth,
            ByteBufCodecs.BOOL, ScanIslandPayload::scan,
            ScanIslandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScanIslandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level() instanceof ServerLevel level)
                    || player.distanceToSqr(payload.pos().getCenter()) > 8 * 8
                    || !level.isLoaded(payload.pos())
                    || !(level.getBlockEntity(payload.pos()) instanceof IslandScannerBlockEntity scanner)) {
                return;
            }
            scanner.setSettings(payload.size(), payload.above(), payload.depth());
            if (!payload.scan() || !scanner.tryStartScan(level.getGameTime())) {
                return;
            }
            IslandData island = IslandScan.scan(level, payload.pos(), scanner.size(), scanner.above(), scanner.depth());
            ItemStack stack = new ItemStack(ModItems.SKY_ISLAND.get());
            stack.set(ModComponents.ISLAND.get(), island);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            level.playSound(null, payload.pos(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.skyblocks.island_scanned",
                    island.size(), island.size(), island.mobs()), true);
        });
    }
}
