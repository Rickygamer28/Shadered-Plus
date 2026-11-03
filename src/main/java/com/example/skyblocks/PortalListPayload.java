package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The portal mirrors in your world (PortalMirrors.Registry): which links to which, however far. */
public record PortalListPayload(List<Entry> portals) implements CustomPacketPayload {
    public record Entry(BlockPos min, Direction facing, DyeColor colour, Vec3 origin) {}

    public static final Type<PortalListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "portal_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortalListPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.portals.size());
        for (Entry e : p.portals) {
            buf.writeBlockPos(e.min());
            buf.writeVarInt(e.facing().get3DDataValue());
            buf.writeVarInt(e.colour().getId());
            buf.writeDouble(e.origin().x);
            buf.writeDouble(e.origin().y);
            buf.writeDouble(e.origin().z);
        }
    }, buf -> {
        int n = Math.min(buf.readVarInt(), 100000);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readBlockPos(), Direction.from3DDataValue(buf.readVarInt()), DyeColor.byId(buf.readVarInt()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
        }
        return new PortalListPayload(list);
    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PortalListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.skyblocks.client.PortalClient.known(payload)); // (client-only class)
    }
}
