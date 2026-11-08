package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A player went into a portal mirror: where they were, which block, whether their client moved them already. */
public record PortalCrossPayload(BlockPos pos, double x, double y, double z, boolean moved) implements CustomPacketPayload {
    public static final Type<PortalCrossPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "portal_cross"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortalCrossPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBlockPos(p.pos);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeBoolean(p.moved);
    }, buf -> new PortalCrossPayload(buf.readBlockPos(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PortalCrossPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PortalMirrors.playerCrossed(player, payload.pos(), new Vec3(payload.x(), payload.y(), payload.z()), payload.moved());
            }
        });
    }
}
