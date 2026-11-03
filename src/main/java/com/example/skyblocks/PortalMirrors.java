package com.example.skyblocks;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Portal mirrors (PortalMirrorBlock): which panes make one portal, which portal it's linked to, and how the world on
 * one side lines up with the other's. A portal's panes all face one way in one plane; it's linked to the nearest other
 * portal of its colour. The two are glued together by a turn (a quarter turn at a time) and a shift taking one's
 * middle to the other's and its facing to the other's facing: looking through one at its front you see the world behind
 * the other, walking through you come out of the other's back still going the same way (turned with it). The client's
 * pictures (client/MirrorRenderer) and the server's teleports work it out the same way.
 */
public final class PortalMirrors {
    /** How far apart linked portals can be (blocks, between their middles). */
    public static final int PAIR_RANGE = 96;
    private static final int MAX_BLOCKS = 256;

    public static final DeferredRegister<PoiType> POI = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, SkyBlocksMod.MOD_ID);
    public static final DeferredHolder<PoiType, PoiType> PORTAL_POI = POI.register("portal_mirror",
            () -> new PoiType(Set.copyOf(ModBlocks.PORTAL_MIRROR.get().getStateDefinition().getPossibleStates()), 0, 1));

    private PortalMirrors() {}

    /** One portal: its panes, its lowest one (its name), the way it faces, its colour, its middle on its plane. */
    public record Portal(Set<BlockPos> blocks, BlockPos min, Direction facing, DyeColor colour, Vec3 origin) {}

    public static boolean isPortal(BlockState state) {
        return state.getBlock() instanceof PortalMirrorBlock;
    }

    /** The plane through a pane's middle, along its facing's axis. */
    public static double plane(BlockPos pos, Direction facing) {
        return pos.get(facing.getAxis()) + 0.5;
    }

    /** The portal this pane is part of: the panes joined to it beside, above and below, facing the same way, the same colour. */
    @org.jetbrains.annotations.Nullable
    public static Portal component(BlockGetter level, BlockPos start) {
        BlockState first = level.getBlockState(start);
        if (!isPortal(first)) {
            return null;
        }
        Direction facing = first.getValue(PortalMirrorBlock.FACING);
        DyeColor colour = first.getValue(PortalMirrorBlock.COLOR);
        Direction across = facing.getClockWise();
        Direction[] steps = {Direction.UP, Direction.DOWN, across, across.getOpposite()};
        Set<BlockPos> blocks = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        blocks.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && blocks.size() < MAX_BLOCKS) {
            BlockPos p = queue.poll();
            for (Direction d : steps) {
                BlockPos n = p.relative(d);
                if (blocks.contains(n)) {
                    continue;
                }
                BlockState s = level.getBlockState(n);
                if (isPortal(s) && s.getValue(PortalMirrorBlock.FACING) == facing && s.getValue(PortalMirrorBlock.COLOR) == colour) {
                    blocks.add(n);
                    queue.add(n);
                }
            }
        }
        return of(blocks, facing, colour);
    }

    /** A portal from its panes. */
    public static Portal of(Set<BlockPos> blocks, Direction facing, DyeColor colour) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        BlockPos min = null;
        for (BlockPos p : blocks) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
            if (min == null || p.getY() < min.getY() || (p.getY() == min.getY() && (p.getX() < min.getX() || (p.getX() == min.getX() && p.getZ() < min.getZ())))) {
                min = p;
            }
        }
        Vec3 origin = new Vec3((minX + maxX + 1) * 0.5, (minY + maxY + 1) * 0.5, (minZ + maxZ + 1) * 0.5);
        return new Portal(Set.copyOf(blocks), min, facing, colour, origin);
    }

    /** Quarter turns (each the way yaw goes) taking {@code from} to {@code to}. */
    public static int turns(Direction from, Direction to) {
        return Math.floorMod(to.get2DDataValue() - from.get2DDataValue(), 4);
    }

    /** v turned by quarter turns about the up axis (south to west to north to east, as yaw goes). */
    public static Vec3 rotate(Vec3 v, int q) {
        return switch (q & 3) {
            case 1 -> new Vec3(-v.z, v.y, v.x);
            case 2 -> new Vec3(-v.x, v.y, -v.z);
            case 3 -> new Vec3(v.z, v.y, -v.x);
            default -> v;
        };
    }

    /** Where a point by portal a is by portal b (the two glued together). */
    public static Vec3 apply(Portal a, Portal b, Vec3 p) {
        return rotate(p.subtract(a.origin()), turns(a.facing(), b.facing())).add(b.origin());
    }

    /** Of the portals given, the nearest other one of a's colour within range (null: none). */
    @org.jetbrains.annotations.Nullable
    public static Portal nearest(Portal a, Iterable<Portal> others) {
        Portal best = null;
        double bestD = 0.0;
        for (Portal o : others) {
            if (o.colour() != a.colour() || o.min().equals(a.min())) {
                continue;
            }
            double d = o.origin().distanceToSqr(a.origin());
            if (d > (double) PAIR_RANGE * PAIR_RANGE) {
                continue;
            }
            if (best == null || d < bestD || (d == bestD && o.min().asLong() < best.min().asLong())) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /** (server) The portal a is linked to: found by its panes' points of interest round it. */
    @org.jetbrains.annotations.Nullable
    public static Portal partner(ServerLevel level, Portal a) {
        PoiManager poi = level.getPoiManager();
        BlockPos centre = BlockPos.containing(a.origin());
        poi.ensureLoadedAndValid(level, centre, PAIR_RANGE + 16);
        List<BlockPos> found = poi.getInRange(h -> h.is(PORTAL_POI.getKey()), centre, PAIR_RANGE + 16, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos).filter(p -> !a.blocks().contains(p)).toList();
        Set<BlockPos> seen = new HashSet<>();
        List<Portal> portals = new java.util.ArrayList<>();
        for (BlockPos p : found) {
            if (seen.contains(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (!isPortal(s) || s.getValue(PortalMirrorBlock.COLOR) != a.colour()) {
                continue;
            }
            Portal o = component(level, p);
            if (o != null) {
                seen.addAll(o.blocks());
                portals.add(o);
            }
        }
        return nearest(a, portals);
    }

    /** Which side of the pane's plane p is on (+ in front). */
    private static double side(Vec3 p, BlockPos pos, Direction facing) {
        double along = facing.getAxis().choose(p.x, p.y, p.z) - plane(pos, facing);
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along : -along;
    }

    /**
     * Something in a portal pane's block: if it went through the plane this tick, it comes out of the linked portal.
     * Players do it themselves on their side (no waiting for the server: client/PortalClient), the server checks;
     * everything else the server moves.
     */
    public static void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.isPassenger() || entity.isVehicle() || entity.isOnPortalCooldown()) {
            return;
        }
        Direction facing = state.getValue(PortalMirrorBlock.FACING);
        double before = side(new Vec3(entity.xo, entity.yo, entity.zo), pos, facing), now = side(entity.position(), pos, facing);
        if (!(before > 0.0 && now <= 0.0) && !(before < 0.0 && now >= 0.0)) {
            return;
        }
        if (level.isClientSide) {
            if (entity instanceof Player) {
                com.example.skyblocks.client.PortalClient.crossed(entity, pos); // (client-only class, never loaded on a server)
            }
            return;
        }
        if (entity instanceof Player || !(level instanceof ServerLevel server)) {
            return; // (players: their own client says so, PortalCrossPayload)
        }
        Portal a = component(level, pos);
        Portal b = a == null ? null : partner(server, a);
        if (b == null) {
            return;
        }
        int q = turns(a.facing(), b.facing());
        Vec3 to = apply(a, b, entity.position());
        entity.teleportTo(to.x, to.y, to.z);
        entity.setYRot(entity.getYRot() + 90.0F * q);
        entity.setYHeadRot(entity.getYHeadRot() + 90.0F * q);
        entity.setDeltaMovement(rotate(entity.getDeltaMovement(), q));
        entity.hurtMarked = true; // (its new speed sent on)
        entity.setPortalCooldown(5);
    }

    /** (server) A player's client says they went through a portal: moved there here too (if it checks out). */
    public static void playerCrossed(ServerPlayer player, BlockPos pos, Vec3 at) {
        ServerLevel level = player.serverLevel();
        if (player.position().distanceToSqr(at) > 8.0 * 8.0 || at.distanceToSqr(Vec3.atCenterOf(pos)) > 3.0 * 3.0
                || !level.isLoaded(pos) || !isPortal(level.getBlockState(pos))) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            return;
        }
        Portal a = component(level, pos);
        Portal b = a == null ? null : partner(level, a);
        if (b == null) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            return;
        }
        int q = turns(a.facing(), b.facing());
        Vec3 to = apply(a, b, at);
        player.absMoveTo(to.x, to.y, to.z, player.getYRot() + 90.0F * q, player.getXRot());
        player.setYHeadRot(player.getYHeadRot() + 90.0F * q);
        player.connection.resetPosition();
        player.setPortalCooldown(5);
        level.playSound(player, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.6F); // (they heard it already)
    }
}
