package com.example.skyblocks;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** A player went into a portal mirror: where they were, which block, whether their client moved them already. (1.20.1: SimpleChannel id 7) */
public record PortalCrossPayload(BlockPos pos, double x, double y, double z, boolean moved) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeBoolean(moved);
    }

    public static PortalCrossPayload decode(FriendlyByteBuf buf) {
        return new PortalCrossPayload(buf.readBlockPos(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }

    public static void handle(PortalCrossPayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PortalMirrors.playerCrossed(player, payload.pos(), new Vec3(payload.x(), payload.y(), payload.z()), payload.moved());
            }
        });
    }
}
