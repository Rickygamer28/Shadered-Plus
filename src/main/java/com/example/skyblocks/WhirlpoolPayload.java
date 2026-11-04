package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The whirlpool's screen: drain mode on / off, a fluid marked void or not, a fluid tag in the void filter or not. */
public record WhirlpoolPayload(BlockPos pos, int action, String text) implements CustomPacketPayload {
    public static final int DRAIN = 0, VOID_FLUID = 1, VOID_TAG = 2;
    public static final Type<WhirlpoolPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "whirlpool_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WhirlpoolPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.action);
        buf.writeUtf(p.text, 256);
    }, buf -> new WhirlpoolPayload(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WhirlpoolPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            // (near it: the screen is opened at it or at its tank, at most a tank's half plus reach away)
            if (player.distanceToSqr(payload.pos.getCenter()) > 16.0 * 16.0
                    || !(player.level().getBlockEntity(payload.pos) instanceof WhirlpoolBlockEntity be)) {
                return;
            }
            switch (payload.action) {
                case DRAIN -> be.toggleDrain();
                case VOID_FLUID -> {
                    ResourceLocation id = ResourceLocation.tryParse(payload.text);
                    if (id != null) {
                        BuiltInRegistries.FLUID.getOptional(id).ifPresent(be::toggleVoid);
                    }
                }
                case VOID_TAG -> be.toggleVoidTag(payload.text);
                default -> {}
            }
        });
    }
}
