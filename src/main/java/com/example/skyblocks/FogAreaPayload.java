package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Fog machine menu -> server: the fog's area and whether it's outlined. */
public record FogAreaPayload(BlockPos pos, int front, int back, int left, int right, int height, boolean showArea) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(front);
        buf.writeVarInt(back);
        buf.writeVarInt(left);
        buf.writeVarInt(right);
        buf.writeVarInt(height);
        buf.writeBoolean(showArea);
    }

    public static FogAreaPayload decode(FriendlyByteBuf buf) {
        return new FogAreaPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(FogAreaPayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.level() instanceof ServerLevel level && level.isLoaded(payload.pos())
                    && player.distanceToSqr(payload.pos().getCenter()) <= 8 * 8
                    && level.getBlockEntity(payload.pos()) instanceof FogMachineBlockEntity machine) {
                machine.setSettings(payload.front(), payload.back(), payload.left(), payload.right(), payload.height(), payload.showArea());
            }
        });
    }
}
