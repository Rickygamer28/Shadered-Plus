package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** An elevator's screen: a floor to go to, its look or its speed chosen (value: which). */
public record ElevatorPayload(BlockPos base, int action, int value) implements CustomPacketPayload {
    public static final int GO = 0, SKIN = 1, SPEED = 2;
    public static final Type<ElevatorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "elevator_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ElevatorPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBlockPos(p.base);
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.value);
    }, buf -> new ElevatorPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ElevatorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!player.level().isLoaded(payload.base) || !(player.level().getBlockEntity(payload.base) instanceof ElevatorBaseBlockEntity be)) {
                return;
            }
            // (anywhere along its shaft, near it)
            if (!be.shaft().inflate(6.0).contains(player.position())) {
                return;
            }
            switch (payload.action) {
                case GO -> be.call(payload.value);
                case SKIN -> be.setSkin(payload.value);
                case SPEED -> be.setSpeed(payload.value);
                default -> {}
            }
        });
    }
}
