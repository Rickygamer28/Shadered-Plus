package com.example.skyblocks.portal;

import com.example.skyblocks.SkyBlocksMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: which portals show which sky (changed blocks, or all of a dimension's on joining it). */
public record PortalSkiesPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<PortalSkiesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "portal_skies"));

    public static final StreamCodec<ByteBuf, PortalSkiesPayload> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(PortalSkiesPayload::new, PortalSkiesPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PortalSkiesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.skyblocks.client.PortalSkyRenderer.apply(payload.data()));
    }
}
