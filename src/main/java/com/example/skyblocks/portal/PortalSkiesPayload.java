package com.example.skyblocks.portal;

import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: which portals show which sky (changed blocks, or all of a dimension's on joining it). */
public record PortalSkiesPayload(CompoundTag data) {

    public static void encode(PortalSkiesPayload payload, FriendlyByteBuf buf) {
        buf.writeNbt(payload.data);
    }

    public static PortalSkiesPayload decode(FriendlyByteBuf buf) {
        CompoundTag data = buf.readNbt();
        return new PortalSkiesPayload(data == null ? new CompoundTag() : data);
    }

    /** Runs on the client thread (registered with consumerMainThread). */
    public static void handle(PortalSkiesPayload payload, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.skyblocks.client.PortalSkyRenderer.apply(payload.data()));
    }
}
