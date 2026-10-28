package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Neon sign menu -> server: its text, colour, glow and size. */
public record NeonSignPayload(BlockPos pos, String text, int colour, int mode, int size, int background) {




    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(text, NeonSignBlockEntity.MAX_LENGTH * 4);
        buf.writeInt(colour);
        buf.writeVarInt(mode);
        buf.writeVarInt(size);
        buf.writeInt(background);
    }

    public static NeonSignPayload decode(FriendlyByteBuf buf) {
        return new NeonSignPayload(buf.readBlockPos(), buf.readUtf(NeonSignBlockEntity.MAX_LENGTH * 4), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt());
    }

    public static void handle(NeonSignPayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.level() instanceof ServerLevel level && level.isLoaded(payload.pos())
                    && player.distanceToSqr(payload.pos().getCenter()) <= 8 * 8
                    && level.getBlockEntity(payload.pos()) instanceof NeonSignBlockEntity sign) {
                sign.setAll(payload.text(), payload.colour(), payload.mode(), payload.size());
                if (sign.background() != (payload.background() & 0xFFFFFF)) {
                    for (BlockPos p : NeonSignBlock.panel(level, payload.pos())) {
                        if (level.getBlockEntity(p) instanceof NeonSignBlockEntity other) {
                            other.setBackground(payload.background());
                        }
                    }
                }
            }
        });
    }
}
