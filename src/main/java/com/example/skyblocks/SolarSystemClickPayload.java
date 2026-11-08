package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** An empty hand clicked an orrery's or planetarium's model with nothing behind it (SolarSystemEvents). */
public record SolarSystemClickPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<SolarSystemClickPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "solar_system_click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SolarSystemClickPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeBlockPos(p.pos), buf -> new SolarSystemClickPayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SolarSystemClickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SolarSystemEvents.clicked(player, payload.pos());
            }
        });
    }
}
