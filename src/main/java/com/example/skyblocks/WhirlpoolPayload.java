package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

/**
 * The whirlpool's screen: drain mode on / off, a fluid marked void or not, a fluid tag in the void filter or not, shown
 * as a whirlpool or as a plain tank. (1.20.1: SimpleChannel id 8)
 */
public record WhirlpoolPayload(BlockPos pos, int action, String text) {
    public static final int DRAIN = 0, VOID_FLUID = 1, VOID_TAG = 2, STILL = 3;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(action);
        buf.writeUtf(text, 256);
    }

    public static WhirlpoolPayload decode(FriendlyByteBuf buf) {
        return new WhirlpoolPayload(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(WhirlpoolPayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            Player player = context.getSender();
            // (near it: the screen is opened at it or at its tank, at most a tank's half plus reach away)
            if (player == null || player.distanceToSqr(payload.pos.getCenter()) > 16.0 * 16.0
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
                case STILL -> be.toggleStill();
                default -> {}
            }
        });
    }
}
