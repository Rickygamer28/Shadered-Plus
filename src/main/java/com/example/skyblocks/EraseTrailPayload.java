package com.example.skyblocks;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A left-click in the air with the sky wand: rub out the lines of sky it's pointed at (SkyWandItem.erase). */
public record EraseTrailPayload() implements CustomPacketPayload {
    public static final EraseTrailPayload INSTANCE = new EraseTrailPayload();
    public static final Type<EraseTrailPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "erase_trail"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EraseTrailPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EraseTrailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SkyWandItem.erase(context.player()));
    }
}
