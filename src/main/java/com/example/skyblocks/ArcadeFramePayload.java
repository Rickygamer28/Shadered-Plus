package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What's on an arcade cabinet's screen while someone plays it: a picture of their game (w x h, its own colours -
 * client/ArcadePicture), the game, the score, who's playing, what they're holding on the controls (the cabinet's
 * stick and buttons move with it), and the sounds it's made since the last one (heard from the cabinet by everyone
 * else near it). The player's game sends it to the server ten times a second; the server passes it on to everyone
 * near the cabinet. No picture: they stopped playing.
 */
public record ArcadeFramePayload(BlockPos pos, int game, int score, String name, int w, int h, byte[] picture, byte[] sounds, int input)
        implements CustomPacketPayload {
    public static final int MAX_W = 128, MAX_H = 96, MAX_BYTES = 24000;
    /** The plain little picture's size (frame(): the palette's colours, when the real one can't be taken). */
    public static final int WIDTH = 32, HEIGHT = 24;
    /** How far the picture goes (blocks). */
    public static final double RANGE = 48;
    public static final Type<ArcadeFramePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "arcade_frame"));

    public static final StreamCodec<ByteBuf, ArcadeFramePayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        BlockPos.STREAM_CODEC.encode(buf, p.pos());
        ByteBufCodecs.VAR_INT.encode(buf, p.game());
        ByteBufCodecs.VAR_INT.encode(buf, p.score());
        ByteBufCodecs.stringUtf8(64).encode(buf, p.name());
        ByteBufCodecs.VAR_INT.encode(buf, p.w());
        ByteBufCodecs.VAR_INT.encode(buf, p.h());
        ByteBufCodecs.byteArray(MAX_BYTES).encode(buf, p.picture());
        ByteBufCodecs.byteArray(32).encode(buf, p.sounds());
        ByteBufCodecs.VAR_INT.encode(buf, p.input());
    }, buf -> new ArcadeFramePayload(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.stringUtf8(64).decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.byteArray(MAX_BYTES).decode(buf), ByteBufCodecs.byteArray(32).decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    /** (they've stopped: no picture) */
    public static ArcadeFramePayload stopped(BlockPos pos, int game, int score, byte[] sounds) {
        return new ArcadeFramePayload(pos, game, score, "", 0, 0, new byte[0], sounds, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArcadeFramePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isServerbound()) {
                Player player = context.player();
                if (player.level() instanceof ServerLevel level && level.isLoaded(payload.pos())
                        && player.distanceToSqr(payload.pos().getCenter()) <= 10 * 10
                        && level.getBlockEntity(payload.pos()) instanceof ArcadeBlockEntity
                        && payload.w() >= 0 && payload.w() <= MAX_W && payload.h() >= 0 && payload.h() <= MAX_H && payload.sounds().length <= 32) {
                    // (passed on with the real player's name, to everyone near - them too: their own cabinet, the big screens round it)
                    var c = payload.pos().getCenter();
                    PacketDistributor.sendToPlayersNear(level, null, c.x, c.y, c.z, RANGE,
                            new ArcadeFramePayload(payload.pos(), payload.game(), payload.score(), player.getGameProfile().getName(),
                                    payload.w(), payload.h(), payload.picture(), payload.sounds(), payload.input()));
                }
            } else {
                com.example.skyblocks.client.ClientAccess.arcadeFrame(payload); // (client-only class)
            }
        });
    }
}
