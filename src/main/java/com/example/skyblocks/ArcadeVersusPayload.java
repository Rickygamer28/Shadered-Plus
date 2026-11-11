package com.example.skyblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Versus at the arcade (ArcadeVersus): a player sitting down at a cabinet on the fighting game, getting up, leaving a
 * match (to the server); a match starting - who's which side, the fight's dice, both names - or ending, and why (to
 * the players).
 */
public record ArcadeVersusPayload(int kind, BlockPos pos, int match, int side, long seed, String text) implements CustomPacketPayload {
    public static final int SEAT = 0, UNSEAT = 1, LEAVE = 2, START = 3, END = 4;
    public static final Type<ArcadeVersusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "arcade_versus"));

    public static final StreamCodec<ByteBuf, ArcadeVersusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ArcadeVersusPayload::kind,
            BlockPos.STREAM_CODEC, ArcadeVersusPayload::pos,
            ByteBufCodecs.VAR_INT, ArcadeVersusPayload::match,
            ByteBufCodecs.VAR_INT, ArcadeVersusPayload::side,
            ByteBufCodecs.VAR_LONG, ArcadeVersusPayload::seed,
            ByteBufCodecs.stringUtf8(80), ArcadeVersusPayload::text,
            ArcadeVersusPayload::new);

    public static ArcadeVersusPayload of(int kind, BlockPos pos, int match) {
        return new ArcadeVersusPayload(kind, pos, match, 0, 0, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArcadeVersusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isServerbound()) {
                if (context.player() instanceof ServerPlayer player) ArcadeVersus.control(player, payload);
            } else {
                com.example.skyblocks.client.ClientAccess.arcadeVersus(payload); // (client-only class)
            }
        });
    }
}
