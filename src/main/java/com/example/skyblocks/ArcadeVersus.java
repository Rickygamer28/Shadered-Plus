package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Versus at the arcade: two cabinets side by side (facing the same way), or back to back, are linked - when there's
 * someone on the fighting game at each, it's a match between them (the one who was there first is 1P, on the left).
 * Each cabinet plays the fight itself (client/arcade/Netplay); the server only says who's playing whom, passes what
 * each holds on to the other as it comes in, and calls the match off when one of them leaves, goes too far, or logs off.
 */
public final class ArcadeVersus {
    private ArcadeVersus() {}

    /** Someone sitting at a cabinet on the fighting game: where, since when, last heard from. */
    private static final class Seat {
        final BlockPos pos;
        final ResourceKey<Level> dim;
        final long since;
        long heard;

        Seat(BlockPos pos, ResourceKey<Level> dim, long now) {
            this.pos = pos;
            this.dim = dim;
            this.since = now;
            this.heard = now;
        }
    }

    private record Match(int id, ServerPlayer p1, ServerPlayer p2, BlockPos pos1, BlockPos pos2) {
        ServerPlayer other(ServerPlayer p) {
            return p.getUUID().equals(p1.getUUID()) ? p2 : p.getUUID().equals(p2.getUUID()) ? p1 : null;
        }
    }

    private static final Map<UUID, Seat> SEATS = new ConcurrentHashMap<>();
    private static final Map<Integer, Match> MATCHES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> MATCH_OF = new ConcurrentHashMap<>();
    private static final AtomicInteger IDS = new AtomicInteger(1);

    /** A player's message about sitting down, getting up, leaving. */
    static void control(ServerPlayer p, ArcadeVersusPayload m) {
        switch (m.kind()) {
            case ArcadeVersusPayload.SEAT -> seat(p, m.pos());
            case ArcadeVersusPayload.UNSEAT -> unseat(p.getUUID(), p.getServer());
            case ArcadeVersusPayload.LEAVE -> {
                Integer id = MATCH_OF.get(p.getUUID());
                if (id != null && id == m.match()) end(id, p.getGameProfile().getName() + " LEFT", p.getUUID());
            }
            default -> {}
        }
    }

    private static boolean isCabinet(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        return st.getBlock() instanceof ArcadeBlock && st.getValue(ArcadeBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static void seat(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        if (!level.isLoaded(pos) || !isCabinet(level, pos) || p.distanceToSqr(pos.getCenter()) > 8 * 8) return;
        long now = level.getGameTime();
        Seat s = SEATS.get(p.getUUID());
        if (s != null && s.pos.equals(pos) && s.dim == level.dimension()) {
            s.heard = now;
        } else {
            Integer id = MATCH_OF.get(p.getUUID());
            if (id != null) end(id, p.getGameProfile().getName() + " LEFT", p.getUUID());
            s = new Seat(pos.immutable(), level.dimension(), now);
            SEATS.put(p.getUUID(), s);
        }
        if (!MATCH_OF.containsKey(p.getUUID())) pair(p, s, level);
    }

    /** Someone at a cabinet linked to theirs, not in a match: a match. */
    private static void pair(ServerPlayer p, Seat mine, ServerLevel level) {
        for (BlockPos other : linked(level, mine.pos)) {
            for (Map.Entry<UUID, Seat> e : SEATS.entrySet()) {
                Seat s = e.getValue();
                if (!s.pos.equals(other) || s.dim != mine.dim || e.getKey().equals(p.getUUID()) || MATCH_OF.containsKey(e.getKey())) continue;
                ServerPlayer q = level.getServer().getPlayerList().getPlayer(e.getKey());
                if (q == null || q.level() != level) continue;
                boolean theyWereFirst = s.since <= mine.since;
                start(theyWereFirst ? q : p, theyWereFirst ? s.pos : mine.pos, theyWereFirst ? p : q, theyWereFirst ? mine.pos : s.pos, level);
                return;
            }
        }
    }

    /** The cabinets one's linked to: each side of it facing the same way, and back to back with it. */
    static List<BlockPos> linked(Level level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        BlockState st = level.getBlockState(pos);
        if (!isCabinet(level, pos)) return out;
        Direction f = st.getValue(ArcadeBlock.FACING);
        for (Direction side : new Direction[] {f.getClockWise(), f.getCounterClockWise()}) {
            BlockPos q = pos.relative(side);
            if (isCabinet(level, q) && level.getBlockState(q).getValue(ArcadeBlock.FACING) == f) out.add(q);
        }
        BlockPos back = pos.relative(f.getOpposite());
        if (isCabinet(level, back) && level.getBlockState(back).getValue(ArcadeBlock.FACING) == f.getOpposite()) out.add(back);
        return out;
    }

    private static void start(ServerPlayer p1, BlockPos pos1, ServerPlayer p2, BlockPos pos2, ServerLevel level) {
        int id = IDS.getAndIncrement();
        long seed = level.getRandom().nextLong();
        Match m = new Match(id, p1, p2, pos1, pos2);
        MATCHES.put(id, m);
        MATCH_OF.put(p1.getUUID(), id);
        MATCH_OF.put(p2.getUUID(), id);
        String names = p1.getGameProfile().getName() + "|" + p2.getGameProfile().getName();
        PacketDistributor.sendToPlayer(p1, new ArcadeVersusPayload(ArcadeVersusPayload.START, pos1, id, 0, seed, names));
        PacketDistributor.sendToPlayer(p2, new ArcadeVersusPayload(ArcadeVersusPayload.START, pos2, id, 1, seed, names));
    }

    /** A match off: whoever's still in it is told why. */
    private static void end(int id, String why, UUID leaving) {
        Match m = MATCHES.remove(id);
        if (m == null) return;
        MATCH_OF.remove(m.p1.getUUID(), id);
        MATCH_OF.remove(m.p2.getUUID(), id);
        for (ServerPlayer p : new ServerPlayer[] {m.p1, m.p2}) {
            if (p.getUUID().equals(leaving) || p.hasDisconnected()) continue;
            BlockPos pos = p == m.p1 ? m.pos1 : m.pos2;
            PacketDistributor.sendToPlayer(p, new ArcadeVersusPayload(ArcadeVersusPayload.END, pos, id, 0, 0, why));
        }
    }

    private static void unseat(UUID who, MinecraftServer server) {
        SEATS.remove(who);
        Integer id = MATCH_OF.get(who);
        if (id != null) {
            ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(who);
            end(id, (p == null ? "YOUR RIVAL" : p.getGameProfile().getName()) + " LEFT", who);
        }
    }

    /** (on the network's thread, as it arrives) What one of a match holds, its fight's number ...: on to the other. */
    static void relay(ServerPlayer from, ArcadeNetPayload m) {
        Match match = MATCHES.get(m.match());
        if (match == null) return;
        ServerPlayer to = match.other(from);
        if (to != null && !to.hasDisconnected()) PacketDistributor.sendToPlayer(to, m);
    }

    /** Every second: anyone gone quiet, gone, too far from their cabinet - up from it (and out of any match). */
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0 || SEATS.isEmpty() && MATCHES.isEmpty()) return;
        for (Map.Entry<UUID, Seat> e : SEATS.entrySet()) {
            Seat s = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            ServerLevel level = server.getLevel(s.dim);
            boolean gone = p == null || level == null || p.level() != level || p.distanceToSqr(s.pos.getCenter()) > 10 * 10
                    || level.getGameTime() - s.heard > 120 || !level.isLoaded(s.pos) || !isCabinet(level, s.pos);
            if (gone) unseat(e.getKey(), server);
        }
        for (Match m : MATCHES.values()) {
            for (ServerPlayer p : new ServerPlayer[] {m.p1, m.p2}) {
                Seat s = SEATS.get(p.getUUID());
                if (s == null || !s.pos.equals(p == m.p1 ? m.pos1 : m.pos2)) {
                    end(m.id, p.getGameProfile().getName() + " LEFT", p.getUUID());
                    break;
                }
            }
        }
    }

    /** The server stopping (a world closed): nobody's sitting anywhere. */
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        SEATS.clear();
        MATCHES.clear();
        MATCH_OF.clear();
    }

    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) unseat(p.getUUID(), p.getServer());
    }
}
