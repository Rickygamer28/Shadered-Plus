package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A teleport pad's menu: what the server shows (Menu), and what's chosen in it (Action). */
public final class TeleportPadPayloads {
    public static final int RENAME = 0, COLOUR = 1, PRIVATE = 2, TARGET = 3, GO = 4;

    private TeleportPadPayloads() {}

    public record Entry(BlockPos pos, String name) {}

    /** Server -> client: a pad's settings, whether you may change them, where crouching sends you, and where you can go. */
    public record Menu(BlockPos pos, String name, int colour, boolean priv, boolean canEdit, long target, List<Entry> pads)
            implements CustomPacketPayload {
        public static final Type<Menu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "teleport_pad_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Menu> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBlockPos(p.pos());
            buf.writeUtf(p.name(), 64);
            buf.writeVarInt(p.colour());
            buf.writeBoolean(p.priv());
            buf.writeBoolean(p.canEdit());
            buf.writeLong(p.target());
            buf.writeVarInt(p.pads().size());
            for (Entry e : p.pads()) {
                buf.writeBlockPos(e.pos());
                buf.writeUtf(e.name(), 64);
            }
        }, buf -> {
            BlockPos pos = buf.readBlockPos();
            String name = buf.readUtf(64);
            int colour = buf.readVarInt();
            boolean priv = buf.readBoolean(), edit = buf.readBoolean();
            long target = buf.readLong();
            int n = Math.min(buf.readVarInt(), 256);
            List<Entry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new Entry(buf.readBlockPos(), buf.readUtf(64)));
            }
            return new Menu(pos, name, colour, priv, edit, target, list);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Menu p, IPayloadContext context) {
            context.enqueueWork(() -> com.example.skyblocks.client.ClientAccess.teleportMenu(p)); // (client-only class)
        }
    }

    /** Client -> server: rename it, its colour, keep it private, where crouching sends you, or go somewhere now. */
    public record Action(BlockPos pos, int action, String text, long target, int value) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "teleport_pad_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBlockPos(p.pos());
            buf.writeVarInt(p.action());
            buf.writeUtf(p.text(), 64);
            buf.writeLong(p.target());
            buf.writeVarInt(p.value());
        }, buf -> new Action(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(64), buf.readLong(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Action p, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer sp && sp.level().isLoaded(p.pos())
                        && sp.distanceToSqr(p.pos().getCenter()) < 10 * 10
                        && sp.level().getBlockEntity(p.pos()) instanceof TeleportPadBlockEntity be) {
                    be.act(sp, p.action(), p.text(), p.target(), p.value());
                }
            });
        }
    }
}
