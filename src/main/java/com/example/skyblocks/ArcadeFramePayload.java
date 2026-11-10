package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What's on an arcade cabinet's screen while someone plays it: a little picture of their game (WIDTH x HEIGHT cells, each
 * a colour of ArcadeBlockEntity's palette), the game, the score, who's playing, and the sounds it's made since the last
 * one (client/ArcadeSounds - heard from the cabinet by everyone else near it). The player's game sends it to the
 * server a few times a second; the server passes it on to everyone near the cabinet, whose screen shows it. No cells:
 * they stopped playing.
 */
public record ArcadeFramePayload(BlockPos pos, int game, int score, String name, byte[] cells, byte[] sounds) implements CustomPacketPayload {
    public static final int WIDTH = 32, HEIGHT = 24;
    public static final Type<ArcadeFramePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "arcade_frame"));

    public static final StreamCodec<ByteBuf, ArcadeFramePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArcadeFramePayload::pos,
            ByteBufCodecs.VAR_INT, ArcadeFramePayload::game,
            ByteBufCodecs.VAR_INT, ArcadeFramePayload::score,
            ByteBufCodecs.stringUtf8(64), ArcadeFramePayload::name,
            ByteBufCodecs.byteArray(WIDTH * HEIGHT), ArcadeFramePayload::cells,
            ByteBufCodecs.byteArray(32), ArcadeFramePayload::sounds,
            ArcadeFramePayload::new);

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
                        && (payload.cells().length == 0 || payload.cells().length == WIDTH * HEIGHT) && payload.sounds().length <= 32) {
                    // (passed on with the real player's name)
                    PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(payload.pos()), new ArcadeFramePayload(payload.pos(),
                            payload.game(), payload.score(), player.getGameProfile().getName(), payload.cells(), payload.sounds()));
                }
            } else {
                com.example.skyblocks.client.ClientAccess.arcadeFrame(payload); // (client-only class)
            }
        });
    }
}
