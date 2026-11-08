package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** The portal mirrors in your world (PortalMirrors.Registry): which links to which, however far. (1.20.1: SimpleChannel id 11) */
public record PortalListPayload(List<Entry> portals) {
    public record Entry(BlockPos min, Direction facing, DyeColor colour, Vec3 origin) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(portals.size());
        for (Entry e : portals) {
            buf.writeBlockPos(e.min());
            buf.writeVarInt(e.facing().get3DDataValue());
            buf.writeVarInt(e.colour().getId());
            buf.writeDouble(e.origin().x);
            buf.writeDouble(e.origin().y);
            buf.writeDouble(e.origin().z);
        }
    }

    public static PortalListPayload decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 100000);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readBlockPos(), Direction.from3DDataValue(buf.readVarInt()), DyeColor.byId(buf.readVarInt()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
        }
        return new PortalListPayload(list);
    }

    public static void handle(PortalListPayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> com.example.skyblocks.client.PortalClient.known(payload)); // (client-only class)
    }
}
