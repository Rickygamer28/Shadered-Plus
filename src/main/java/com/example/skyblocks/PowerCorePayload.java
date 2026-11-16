package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The power core's screen: its look chosen (value: which), push mode on / off. */
public record PowerCorePayload(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final int SKIN = 0, PUSH = 1;
    public static final Type<PowerCorePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "power_core_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PowerCorePayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.value);
    }, buf -> new PowerCorePayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PowerCorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            // (near it: the screen is opened at it or at its chamber, at most a chamber's half plus reach away)
            if (player.distanceToSqr(payload.pos.getCenter()) > 16.0 * 16.0
                    || !(player.level().getBlockEntity(payload.pos) instanceof PowerCoreBlockEntity be)) {
                return;
            }
            switch (payload.action) {
                case SKIN -> be.setSkin(payload.value);
                case PUSH -> be.togglePush();
                default -> {}
            }
        });
    }
}
