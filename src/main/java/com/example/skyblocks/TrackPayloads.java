package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Smooth track over the network: the world's whole track sent to its players (on joining, and whenever it changes);
 * the Track Tool's pieces built, removed and switches flipped (client/TrackTool). And redstone under a switch: on, its
 * other way.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class TrackPayloads {
    private TrackPayloads() {}

    /**
     * One end of a piece, as picked: 0 a free spot (pos, and the way it runs: dir), 1 a node (id), 2 a point along a
     * piece (id, index) - a switch made there.
     */
    public record Spec(int type, int id, int index, Vec3 pos, Vec3 dir) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Spec> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeVarInt(s.type);
            buf.writeVarInt(s.id);
            buf.writeVarInt(s.index);
            buf.writeDouble(s.pos.x);
            buf.writeDouble(s.pos.y);
            buf.writeDouble(s.pos.z);
            buf.writeFloat((float) s.dir.x);
            buf.writeFloat((float) s.dir.z);
        }, buf -> new Spec(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readFloat(), 0, buf.readFloat())));

        /** Where it is (a node's, a piece point's, or its own). */
        @Nullable
        public Vec3 at(TrackNetwork net) {
            if (type == 1) {
                TrackNetwork.Node n = net.nodes.get(id);
                return n == null ? null : n.pos;
            }
            if (type == 2) {
                TrackNetwork.Seg s = net.segs.get(id);
                return s == null || index < 0 || index >= s.pts.length ? null : s.pts[index];
            }
            return pos;
        }

        /** The way the track runs there (level). */
        @Nullable
        Vec3 way(TrackNetwork net) {
            if (type == 1) {
                TrackNetwork.Node n = net.nodes.get(id);
                return n == null ? null : n.dir;
            }
            if (type == 2) {
                TrackNetwork.Seg s = net.segs.get(id);
                if (s == null || index < 0 || index >= s.pts.length) {
                    return null;
                }
                Vec3 t = s.tan[index];
                return new Vec3(t.x, 0, t.z).normalize();
            }
            return dir;
        }
    }

    /** Two picked ends, worked out: the piece's start (and the way it leaves), its end (and the way it arrives), its plan, sides. */
    public record Resolved(Vec3 p0, Vec3 d0, int side0, Vec3 p3, Vec3 d1, int side3, TrackNetwork.Plan plan) {}

    /** Works out a piece from two ends (straight on: its end runs the way its start does - an S-bend - rather than round). */
    @Nullable
    public static Resolved resolve(TrackNetwork net, Spec a, Spec b, boolean straightOn) {
        Vec3 p0 = a.at(net), p3 = b.at(net);
        Vec3 w0 = a.way(net), w3 = b.way(net);
        if (p0 == null || p3 == null || w0 == null || w3 == null) {
            return null;
        }
        Vec3 chord = new Vec3(p3.x - p0.x, 0, p3.z - p0.z);
        if (chord.lengthSqr() < 1e-6) {
            return null;
        }
        Vec3 c = chord.normalize();
        int side0 = 1;
        Vec3 d0 = w0;
        if (a.type != 0) {
            side0 = w0.dot(c) >= 0 ? 1 : -1;
            d0 = w0.scale(side0);
        }
        int side3 = -1;
        Vec3 d1;
        if (b.type != 0) {
            side3 = w3.dot(c) <= 0 ? 1 : -1;
            d1 = w3.scale(-side3);
        } else {
            d1 = straightOn ? d0 : c.scale(2 * d0.dot(c)).subtract(d0).normalize();
        }
        TrackNetwork.Plan plan = TrackNetwork.plan(p0, d0, p3, d1);
        String problem = plan.problem();
        if (problem == null) {
            if ((a.type == 1 && full(net, a.id, side0)) || (b.type == 1 && full(net, b.id, side3))) {
                problem = "full";
            } else if (a.type == b.type && a.type != 0 && a.id == b.id) {
                problem = "same";
            }
        }
        if (problem != plan.problem()) {
            plan = new TrackNetwork.Plan(plan.pts(), plan.tan(), plan.cum(), problem);
        }
        return new Resolved(p0, d0, side0, p3, d1, side3, plan);
    }

    private static boolean full(TrackNetwork net, int node, int side) {
        TrackNetwork.Node n = net.nodes.get(node);
        return n == null || n.side(side).size() >= 2;
    }

    // ---- the payloads ----

    public record Sync(CompoundTag net) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "track_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.of((buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.net),
                buf -> new Sync(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Sync p, IPayloadContext context) {
            context.enqueueWork(() -> {
                TrackNetwork.client = TrackNetwork.read(p.net);
                TrackNetwork.client.version = TrackNetwork.client.version * 31 + (int) (System.nanoTime() & 0xFFFF);
                com.example.skyblocks.client.TrackTool.synced();
            });
        }
    }

    public record Build(Spec a, Spec b, boolean straightOn) implements CustomPacketPayload {
        public static final Type<Build> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "track_build"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Build> STREAM_CODEC = StreamCodec.composite(Spec.CODEC, Build::a, Spec.CODEC, Build::b,
                ByteBufCodecs.BOOL, Build::straightOn, Build::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Build p, IPayloadContext context) {
            context.enqueueWork(() -> {
                Player player = context.player();
                if (!(player.level() instanceof ServerLevel level) || !player.getMainHandItem().is(ModItems.TRACK_TOOL.get())) {
                    return;
                }
                TrackNetwork net = TrackNetwork.of(level);
                Resolved r = resolve(net, p.a, p.b, p.straightOn);
                if (r == null || player.position().distanceTo(r.p0()) > 96 || player.position().distanceTo(r.p3()) > 96) {
                    return;
                }
                if (r.plan().problem() != null) {
                    player.displayClientMessage(Component.translatable("message.skyblocks.track." + r.plan().problem()), true);
                    return;
                }
                if (p.a.type == 2 && p.b.type == 2 && p.a.id == p.b.id) {
                    player.displayClientMessage(Component.translatable("message.skyblocks.track.same"), true);
                    return;
                }
                int cost = r.plan().cost();
                if (!player.getAbilities().instabuild && !take(player, cost)) {
                    player.displayClientMessage(Component.translatable("message.skyblocks.track.rails", cost), true);
                    return;
                }
                TrackNetwork.End from = end(net, p.a, r.side0(), r.p0(), r.d0());
                TrackNetwork.End to = end(net, p.b, r.side3(), r.p3(), r.d1());
                net.build(from, to);
                send(level);
            });
        }

        /** An end made real: a free spot a new node; a point along a piece, that piece cut there. */
        private static TrackNetwork.End end(TrackNetwork net, Spec s, int side, Vec3 p, Vec3 d) {
            if (s.type == 1) {
                return new TrackNetwork.End(net.nodes.get(s.id), side, p, d);
            }
            if (s.type == 2) {
                TrackNetwork.Seg seg = net.segs.get(s.id);
                TrackNetwork.Node n = net.split(seg, Mth.clamp(s.index, 1, seg.pts.length - 2));
                int sd = n.dir.dot(d) >= 0 ? side : side;
                // (its side: the one whose way out is the piece's: at the start, d; at the end, back along d)
                return new TrackNetwork.End(n, sd, p, d);
            }
            return new TrackNetwork.End(null, 0, p, d);
        }
    }

    /** Takes Train Rails from a player (all or none). */
    private static boolean take(Player player, int n) {
        int have = 0;
        for (ItemStack st : player.getInventory().items) {
            if (st.is(ModItems.TRAIN_RAIL.get())) {
                have += st.getCount();
            }
        }
        if (have < n) {
            return false;
        }
        for (ItemStack st : player.getInventory().items) {
            if (n > 0 && st.is(ModItems.TRAIN_RAIL.get())) {
                int t = Math.min(n, st.getCount());
                st.shrink(t);
                n -= t;
            }
        }
        return true;
    }

    public record Remove(int seg) implements CustomPacketPayload {
        public static final Type<Remove> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "track_remove"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Remove> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Remove::seg, Remove::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Remove p, IPayloadContext context) {
            context.enqueueWork(() -> {
                Player player = context.player();
                if (!(player.level() instanceof ServerLevel level) || !player.getMainHandItem().is(ModItems.TRACK_TOOL.get())) {
                    return;
                }
                TrackNetwork net = TrackNetwork.of(level);
                TrackNetwork.Seg s = net.segs.get(p.seg);
                if (s == null || player.position().distanceTo(s.pts[s.pts.length / 2]) > s.length() / 2 + 16) {
                    return;
                }
                int back = Math.max(1, Mth.ceil(s.length() / 2.0F));
                net.remove(s);
                if (!player.getAbilities().instabuild) {
                    ItemStack rails = new ItemStack(ModItems.TRAIN_RAIL.get(), back);
                    if (!player.getInventory().add(rails)) {
                        player.drop(rails, false);
                    }
                }
                send(level);
            });
        }
    }

    public record Toggle(int node) implements CustomPacketPayload {
        public static final Type<Toggle> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "track_toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Toggle> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Toggle::node, Toggle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Toggle p, IPayloadContext context) {
            context.enqueueWork(() -> {
                Player player = context.player();
                if (!(player.level() instanceof ServerLevel level)) {
                    return;
                }
                TrackNetwork net = TrackNetwork.of(level);
                TrackNetwork.Node n = net.nodes.get(p.node);
                if (n != null && player.position().distanceTo(n.pos) < 16) {
                    net.toggle(n);
                    level.playSound(null, n.pos.x, n.pos.y, n.pos.z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, 1.4F);
                    send(level);
                }
            });
        }
    }

    // ---- sending ----

    public static void send(ServerLevel level) {
        Sync s = new Sync(TrackNetwork.of(level).write());
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, s);
        }
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new Sync(TrackNetwork.of(sp.serverLevel()).write()));
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new Sync(TrackNetwork.of(sp.serverLevel()).write()));
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new Sync(TrackNetwork.of(sp.serverLevel()).write()));
        }
    }

    /** Switches with redstone under them (the block they're on, or their own spot): on, the other way; off, back. */
    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        Level lv = event.getLevel();
        if (!(lv instanceof ServerLevel level) || level.getGameTime() % 4 != 0) {
            return;
        }
        TrackNetwork net = TrackNetwork.of(level);
        boolean any = false;
        for (TrackNetwork.Node n : net.nodes.values()) {
            int side = n.switchSide();
            if (side == 0) {
                continue;
            }
            BlockPos at = BlockPos.containing(n.pos);
            if (!level.isLoaded(at)) {
                continue;
            }
            boolean on = level.hasNeighborSignal(at.below()) || level.hasNeighborSignal(at) || level.getBestNeighborSignal(at.below()) > 0;
            if (on != n.powered) {
                n.powered = on;
                n.pick[side > 0 ? 1 : 0] = on ? 1 : 0;
                net.changed();
                any = true;
            }
        }
        if (any) {
            send(level);
        }
    }
}
