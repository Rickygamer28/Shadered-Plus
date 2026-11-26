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

/** Television window -> server: what to play, play/pause, stop, go to, volume, repeat. */
public record TelevisionPayload(BlockPos pos, int action, String text, long value) implements CustomPacketPayload {
    public static final int CHOOSE = 0, PLAY = 1, PAUSE = 2, STOP = 3, SEEK = 4, VOLUME = 5, LOOP = 6, GOTO = 7, SHUFFLE = 8;

    /** GOTO's value: from one of its playlist's videos to another. */
    public static long fromTo(int from, int to) {
        return ((long) from << 32) | (to & 0xFFFFFFFFL);
    }

    public static final Type<TelevisionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "television"));

    public static final StreamCodec<ByteBuf, TelevisionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TelevisionPayload::pos,
            ByteBufCodecs.VAR_INT, TelevisionPayload::action,
            ByteBufCodecs.stringUtf8(TelevisionBlockEntity.MAX_SOURCE * 4), TelevisionPayload::text,
            ByteBufCodecs.VAR_LONG, TelevisionPayload::value,
            TelevisionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TelevisionPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(p.pos()) || player.distanceToSqr(p.pos().getCenter()) > (p.action() == GOTO ? 96 * 96 : 24 * 24)) {
                return;
            }
            BlockPos at = TelevisionBlock.anchor(level, p.pos());
            if (!(level.getBlockEntity(at) instanceof TelevisionBlockEntity tv)) {
                return;
            }
            switch (p.action()) {
                case CHOOSE -> tv.choose(p.text().trim(), player.getGameProfile().getName());
                case PLAY -> tv.play(true);
                case PAUSE -> tv.play(false);
                case STOP -> tv.stop();
                case SEEK -> tv.seek(p.value());
                case VOLUME -> tv.setVolume((int) p.value());
                case LOOP -> tv.setLoop(p.value() != 0);
                case GOTO -> tv.goTo((int) (p.value() >> 32), (int) p.value());
                case SHUFFLE -> { // (value: on, and the video playing; text: the count keeping it playing)
                    try {
                        tv.setShuffle((p.value() & 1) != 0, (int) (p.value() >> 1), Integer.parseInt(p.text()));
                    } catch (NumberFormatException ignored) {
                    }
                }
                default -> { }
            }
        });
    }
}
