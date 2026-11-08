package com.example.skyblocks;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** An empty hand clicked an orrery's or planetarium's model with nothing behind it (SolarSystemEvents). (1.20.1: SimpleChannel id 6) */
public record SolarSystemClickPayload(BlockPos pos) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static SolarSystemClickPayload decode(FriendlyByteBuf buf) {
        return new SolarSystemClickPayload(buf.readBlockPos());
    }

    public static void handle(SolarSystemClickPayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                SolarSystemEvents.clicked(player, payload.pos());
            }
        });
    }
}
