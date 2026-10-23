package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Fog machine menu -> server: the fog's area and whether it's outlined. */
public record FogAreaPayload(BlockPos pos, int front, int back, int left, int right, int height, boolean showArea) implements CustomPacketPayload {
    public static final Type<FogAreaPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "fog_area"));

    // (more fields than StreamCodec.composite takes: written out by hand)
    public static final StreamCodec<ByteBuf, FogAreaPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                BlockPos.STREAM_CODEC.encode(buf, p.pos());
                ByteBufCodecs.VAR_INT.encode(buf, p.front());
                ByteBufCodecs.VAR_INT.encode(buf, p.back());
                ByteBufCodecs.VAR_INT.encode(buf, p.left());
                ByteBufCodecs.VAR_INT.encode(buf, p.right());
                ByteBufCodecs.VAR_INT.encode(buf, p.height());
                ByteBufCodecs.BOOL.encode(buf, p.showArea());
            },
            buf -> new FogAreaPayload(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FogAreaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.level() instanceof ServerLevel level && level.isLoaded(payload.pos())
                    && player.distanceToSqr(payload.pos().getCenter()) <= 8 * 8
                    && level.getBlockEntity(payload.pos()) instanceof FogMachineBlockEntity machine) {
                machine.setSettings(payload.front(), payload.back(), payload.left(), payload.right(), payload.height(), payload.showArea());
            }
        });
    }
}
