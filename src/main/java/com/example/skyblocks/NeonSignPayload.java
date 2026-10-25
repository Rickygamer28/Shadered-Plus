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

/** Neon sign menu -> server: its text, colour, glow and size. */
public record NeonSignPayload(BlockPos pos, String text, int colour, int mode, int size) implements CustomPacketPayload {
    public static final Type<NeonSignPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "neon_sign"));

    public static final StreamCodec<ByteBuf, NeonSignPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NeonSignPayload::pos,
            ByteBufCodecs.stringUtf8(NeonSignBlockEntity.MAX_LENGTH * 4), NeonSignPayload::text,
            ByteBufCodecs.INT, NeonSignPayload::colour,
            ByteBufCodecs.VAR_INT, NeonSignPayload::mode,
            ByteBufCodecs.VAR_INT, NeonSignPayload::size,
            NeonSignPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NeonSignPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.level() instanceof ServerLevel level && level.isLoaded(payload.pos())
                    && player.distanceToSqr(payload.pos().getCenter()) <= 8 * 8
                    && level.getBlockEntity(payload.pos()) instanceof NeonSignBlockEntity sign) {
                sign.setAll(payload.text(), payload.colour(), payload.mode(), payload.size());
            }
        });
    }
}
