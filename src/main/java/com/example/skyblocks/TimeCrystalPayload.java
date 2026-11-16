package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The time crystal's screen: its speed or radius chosen (value: which), on / off, its dome shown or not. */
public record TimeCrystalPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final int SPEED = 0, RADIUS = 1, ON = 2, DOME = 3;
    public static final Type<TimeCrystalPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "time_crystal_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeCrystalPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.value);
    }, buf -> new TimeCrystalPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TimeCrystalPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.distanceToSqr(payload.pos.getCenter()) > 12.0 * 12.0
                    || !(player.level().getBlockEntity(payload.pos) instanceof TimeCrystalBlockEntity be)) {
                return;
            }
            switch (payload.action) {
                case SPEED -> be.setSpeed(payload.value);
                case RADIUS -> be.setRadius(payload.value);
                case ON -> be.toggleOn();
                case DOME -> be.toggleDome();
                default -> {}
            }
        });
    }
}
