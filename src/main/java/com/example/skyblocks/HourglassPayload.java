package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** An hourglass's screen: how long it runs (value: which), looping on / off, or turned over. */
public record HourglassPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final int DURATION = 0, LOOP = 1, TURN = 2;
    public static final Type<HourglassPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "hourglass_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HourglassPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.value);
    }, buf -> new HourglassPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HourglassPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.distanceToSqr(payload.pos.getCenter()) > 10.0 * 10.0
                    || !(player.level().getBlockEntity(payload.pos) instanceof HourglassBlockEntity be)) {
                return;
            }
            switch (payload.action) {
                case DURATION -> be.setDuration(payload.value);
                case LOOP -> be.toggleLoop();
                case TURN -> be.turn();
                default -> {}
            }
        });
    }
}
