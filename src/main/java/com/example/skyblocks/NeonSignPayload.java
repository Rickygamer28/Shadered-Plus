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

/**
 * Neon sign menu -> server: its text, colour, glow and size. (style: the glow mode, the outline bit and the lettering
 * << 8; colours: the panel's background, and the second colour << 24.)
 */
public record NeonSignPayload(BlockPos pos, String text, int colour, int style, int size, long colours) implements CustomPacketPayload {
    public static NeonSignPayload of(BlockPos pos, String text, int colour, int mode, int font, int size, int background, int colour2) {
        return new NeonSignPayload(pos, text, colour, mode | (font << 8), size, (background & 0xFFFFFFL) | ((long) (colour2 & 0xFFFFFF) << 24));
    }

    public int mode() { return style & 0xFF; }
    public int font() { return (style >> 8) & 0xFF; }
    public int background() { return (int) (colours & 0xFFFFFF); }
    public int colour2() { return (int) ((colours >> 24) & 0xFFFFFF); }

    public static final Type<NeonSignPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "neon_sign"));

    public static final StreamCodec<ByteBuf, NeonSignPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NeonSignPayload::pos,
            ByteBufCodecs.stringUtf8(NeonSignBlockEntity.MAX_LENGTH * 4), NeonSignPayload::text,
            ByteBufCodecs.INT, NeonSignPayload::colour,
            ByteBufCodecs.VAR_INT, NeonSignPayload::style,
            ByteBufCodecs.VAR_INT, NeonSignPayload::size,
            ByteBufCodecs.VAR_LONG, NeonSignPayload::colours,
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
                sign.setStyle(payload.font(), payload.colour2());
                if (sign.background() != (payload.background() & 0xFFFFFF)) {
                    for (BlockPos p : NeonSignBlock.panel(level, payload.pos())) {
                        if (level.getBlockEntity(p) instanceof NeonSignBlockEntity other) {
                            other.setBackground(payload.background());
                        }
                    }
                }
            }
        });
    }
}
