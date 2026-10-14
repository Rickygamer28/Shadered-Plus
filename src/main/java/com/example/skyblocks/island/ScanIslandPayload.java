package com.example.skyblocks.island;

import com.example.skyblocks.ModItems;
import com.example.skyblocks.SkyBlocksMod;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** Island Scanner menu -> server: save the settings, and scan if asked. */
public record ScanIslandPayload(BlockPos pos, int size, int above, int depth, boolean showArea, boolean scan) {

    public static void encode(ScanIslandPayload payload, FriendlyByteBuf buf) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.size);
        buf.writeVarInt(payload.above);
        buf.writeVarInt(payload.depth);
        buf.writeBoolean(payload.showArea);
        buf.writeBoolean(payload.scan);
    }

    public static ScanIslandPayload decode(FriendlyByteBuf buf) {
        return new ScanIslandPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readBoolean());
    }

    /** Sends it to the server (client side). */
    public static void send(ScanIslandPayload payload) {
        SkyBlocksMod.NETWORK.sendToServer(payload);
    }

    /** Runs on the server thread (registered with consumerMainThread). */
    public static void handle(ScanIslandPayload payload, Supplier<NetworkEvent.Context> context) {
        Player player = context.get().getSender();
        if (player == null || !(player.level() instanceof ServerLevel level)
                || !level.isLoaded(payload.pos())
                || !(level.getBlockEntity(payload.pos()) instanceof IslandScannerBlockEntity scanner)) {
            return;
        }
        if (player.distanceToSqr(payload.pos().getCenter()) > 8 * 8) {
            if (payload.scan()) {
                player.displayClientMessage(Component.translatable("message.skyblocks.island_too_far"), true);
            }
            return;
        }
        scanner.setSettings(payload.size(), payload.above(), payload.depth(), payload.showArea());
        if (!payload.scan()) {
            return;
        }
        if (!scanner.tryStartScan(level.getGameTime())) {
            player.displayClientMessage(Component.translatable("message.skyblocks.island_wait"), true);
            return;
        }
        IslandData island;
        try {
            island = IslandScan.scan(level, payload.pos(), scanner.size(), scanner.above(), scanner.depth());
        } catch (RuntimeException e) {
            SkyBlocksMod.LOGGER.error("Island scan at {} failed", payload.pos(), e);
            player.displayClientMessage(Component.translatable("message.skyblocks.island_failed"), true);
            return;
        }
        ItemStack stack = new ItemStack(ModItems.SKY_ISLAND.get());
        IslandData.set(stack, island);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        level.playSound(null, payload.pos(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.skyblocks.island_scanned",
                island.size(), island.size(), island.mobs()), true);
    }
}
