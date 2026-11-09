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

/** Arcade cabinet -> server: a game played to its end, and its score. */
public record ArcadeScorePayload(BlockPos pos, int game, int score) implements CustomPacketPayload {
    public static final Type<ArcadeScorePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "arcade_score"));

    public static final StreamCodec<ByteBuf, ArcadeScorePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArcadeScorePayload::pos,
            ByteBufCodecs.VAR_INT, ArcadeScorePayload::game,
            ByteBufCodecs.VAR_INT, ArcadeScorePayload::score,
            ArcadeScorePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArcadeScorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.level() instanceof ServerLevel level && level.isLoaded(payload.pos())
                    && player.distanceToSqr(payload.pos().getCenter()) <= 10 * 10
                    && level.getBlockEntity(payload.pos()) instanceof ArcadeBlockEntity be) {
                be.scored(payload.game(), Math.min(payload.score(), 999999), player.getGameProfile().getName());
            }
        });
    }
}
