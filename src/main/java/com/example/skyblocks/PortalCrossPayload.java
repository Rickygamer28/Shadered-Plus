package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A player went through a portal mirror (their client moved them at once): where they were, which pane. */
public record PortalCrossPayload(BlockPos pos, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<PortalCrossPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "portal_cross"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortalCrossPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBlockPos(p.pos);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
    }, buf -> new PortalCrossPayload(buf.readBlockPos(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PortalCrossPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PortalMirrors.playerCrossed(player, payload.pos(), new Vec3(payload.x(), payload.y(), payload.z()));
            }
        });
    }
}
