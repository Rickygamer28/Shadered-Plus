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

/** Player -> server: their ghost (how they fight with this fighter, as Tikkin learned it), for the cabinet to keep. */
public record ArcadeGhostPayload(BlockPos pos, int fighter, String profile) implements CustomPacketPayload {
    public static final Type<ArcadeGhostPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "arcade_ghost"));

    public static final StreamCodec<ByteBuf, ArcadeGhostPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArcadeGhostPayload::pos,
            ByteBufCodecs.VAR_INT, ArcadeGhostPayload::fighter,
            ByteBufCodecs.stringUtf8(16000), ArcadeGhostPayload::profile,
            ArcadeGhostPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArcadeGhostPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.level() instanceof ServerLevel level && level.isLoaded(payload.pos())
                    && player.distanceToSqr(payload.pos().getCenter()) <= 10 * 10 && payload.fighter() >= 0 && payload.fighter() < 16
                    && !payload.profile().isEmpty() && level.getBlockEntity(payload.pos()) instanceof ArcadeBlockEntity be) {
                be.putGhost(player.getGameProfile().getName(), payload.fighter(), payload.profile());
            }
        });
    }
}
