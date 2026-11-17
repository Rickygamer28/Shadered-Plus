package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A spotlight aimed (which way: yaw, pitch, radians), or set to follow players again (follow). */
public record SpotlightPayload(BlockPos pos, boolean follow, float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<SpotlightPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "spotlight_aim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpotlightPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBlockPos(p.pos);
        buf.writeBoolean(p.follow);
        buf.writeFloat(p.yaw);
        buf.writeFloat(p.pitch);
    }, buf -> new SpotlightPayload(buf.readBlockPos(), buf.readBoolean(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpotlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.distanceToSqr(payload.pos.getCenter()) > 48.0 * 48.0 || !Float.isFinite(payload.yaw) || !Float.isFinite(payload.pitch)
                    || !(player.level().getBlockEntity(payload.pos) instanceof SpotlightBlockEntity be)) {
                return;
            }
            if (payload.follow) {
                be.follow();
            } else {
                be.aim(payload.yaw, payload.pitch);
            }
        });
    }
}
