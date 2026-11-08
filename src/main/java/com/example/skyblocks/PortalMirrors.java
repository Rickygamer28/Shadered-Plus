package com.example.skyblocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Portal mirrors (PortalMirrorBlock): which blocks make one portal, which portal it's linked to, and how the world in
 * front of one lines up with the other's. A portal's blocks all face one way, their fronts in one plane; it's linked to
 * the nearest other portal of its colour in the same world, however far. The two are glued front to front: a turn (a
 * quarter turn at a time) and a shift taking one's middle to the other's and its facing to the other's facing turned
 * round - look into one and you see what's in front of the other; walk in and you come out of the other's front. The
 * server keeps every portal in a world (Registry, saved with it), and tells the players in that world (PortalListPayload):
 * the client's pictures (client/MirrorRenderer) and the server's teleports pair them up the same way.
 */
public final class PortalMirrors {
    private static final int MAX_BLOCKS = 256;

    private PortalMirrors() {}

    /** One portal: its blocks, its lowest one (its name), the way it faces, its colour, the middle of its front. */
    public record Portal(Set<BlockPos> blocks, BlockPos min, Direction facing, DyeColor colour, Vec3 origin) {}

    public static boolean isPortal(BlockState state) {
        return state.getBlock() instanceof PortalMirrorBlock;
    }

    /** The plane of a block's front, along its facing's axis. */
    public static double plane(BlockPos pos, Direction facing) {
        return pos.get(facing.getAxis()) + (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0);
    }

    /** The portal this block is part of: the ones joined to it beside, above and below, facing the same way, the same colour. */
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

    /** A portal from its blocks. */
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
        double front = plane(min, facing);
        Direction.Axis axis = facing.getAxis();
        Vec3 origin = new Vec3(axis == Direction.Axis.X ? front : (minX + maxX + 1) * 0.5, (minY + maxY + 1) * 0.5,
                axis == Direction.Axis.Z ? front : (minZ + maxZ + 1) * 0.5);
        return new Portal(Set.copyOf(blocks), min, facing, colour, origin);
    }

    /** Quarter turns (each the way yaw goes) taking {@code from} to {@code to}. */
    public static int turns(Direction from, Direction to) {
        return Math.floorMod(to.get2DDataValue() - from.get2DDataValue(), 4);
    }

    /** The turn from portal a to b: a's facing to b's facing turned round (going in a's front, out of b's). */
    public static int turns(Portal a, Portal b) {
        return turns(a.facing(), b.facing().getOpposite());
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
        return rotate(p.subtract(a.origin()), turns(a, b)).add(b.origin());
    }

    /** Of the portals given, the nearest other one of a's colour (null: none). */
    @org.jetbrains.annotations.Nullable
    public static Portal nearest(Portal a, Iterable<Portal> others) {
        Portal best = null;
        double bestD = 0.0;
        for (Portal o : others) {
            if (o.colour() != a.colour() || o.min().equals(a.min())) {
                continue;
            }
            double d = o.origin().distanceToSqr(a.origin());
            if (best == null || d < bestD || (d == bestD && o.min().asLong() < best.min().asLong())) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    // ---- every portal in a world (server) ----

    /** The portals in one world, kept with it: found again round a block whenever a portal block there changes. */
    public static final class Registry extends SavedData {
        private final List<Portal> portals = new ArrayList<>();

        public static Registry of(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Registry::load, Registry::new, "skyblocks_portal_mirrors");
        }

        public List<Portal> portals() {
            return portals;
        }

        /** The portal this block is part of, as last found. */
        @org.jetbrains.annotations.Nullable
        public Portal at(BlockPos pos) {
            for (Portal p : portals) {
                if (p.blocks().contains(pos)) {
                    return p;
                }
            }
            return null;
        }

        /** A portal block placed, changed or broken at pos: the portals round it found again, and the players told. */
        public void refresh(ServerLevel level, BlockPos pos) {
            Set<BlockPos> seeds = new HashSet<>();
            seeds.add(pos.immutable());
            portals.removeIf(p -> {
                for (Direction d : Direction.values()) {
                    if (p.blocks().contains(pos.relative(d))) {
                        seeds.addAll(p.blocks());
                        return true;
                    }
                }
                if (p.blocks().contains(pos)) {
                    seeds.addAll(p.blocks());
                    return true;
                }
                return false;
            });
            for (Direction d : Direction.values()) {
                seeds.add(pos.relative(d));
            }
            Set<BlockPos> done = new HashSet<>();
            for (BlockPos s : seeds) {
                if (done.contains(s) || !level.isLoaded(s) || !isPortal(level.getBlockState(s))) {
                    continue;
                }
                Portal p = component(level, s);
                if (p != null) {
                    done.addAll(p.blocks());
                    portals.removeIf(o -> !java.util.Collections.disjoint(o.blocks(), p.blocks()));
                    portals.add(p);
                }
            }
            setDirty();
            sendTo(level);
        }

        /** The portals of this world to everyone in it. */
        public void sendTo(ServerLevel level) {
            SkyBlocksMod.NETWORK.send(PacketDistributor.DIMENSION.with(level::dimension), payload());
        }

        public PortalListPayload payload() {
            List<PortalListPayload.Entry> list = new ArrayList<>();
            for (Portal p : portals) {
                list.add(new PortalListPayload.Entry(p.min(), p.facing(), p.colour(), p.origin()));
            }
            return new PortalListPayload(list);
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Portal p : portals) {
                CompoundTag t = new CompoundTag();
                t.putInt("facing", p.facing().get3DDataValue());
                t.putInt("colour", p.colour().getId());
                long[] blocks = new long[p.blocks().size()];
                int i = 0;
                for (BlockPos b : p.blocks()) {
                    blocks[i++] = b.asLong();
                }
                t.put("blocks", new LongArrayTag(blocks));
                list.add(t);
            }
            tag.put("portals", list);
            return tag;
        }

        private static Registry load(CompoundTag tag) {
            Registry r = new Registry();
            for (Tag t : tag.getList("portals", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                Set<BlockPos> blocks = new HashSet<>();
                for (long l : c.getLongArray("blocks")) {
                    blocks.add(BlockPos.of(l));
                }
                Direction facing = Direction.from3DDataValue(c.getInt("facing"));
                if (!blocks.isEmpty() && facing.getAxis().isHorizontal()) {
                    r.portals.add(PortalMirrors.of(blocks, facing, DyeColor.byId(c.getInt("colour"))));
                }
            }
            return r;
        }
    }

    /** (server) The portal a block is part of, and the portal it's linked to. */
    @org.jetbrains.annotations.Nullable
    private static Portal[] link(ServerLevel level, BlockPos pos) {
        Registry registry = Registry.of(level);
        Portal a = registry.at(pos);
        if (a == null) {
            registry.refresh(level, pos); // (not known yet: an older world)
            a = registry.at(pos);
        }
        Portal b = a == null ? null : nearest(a, registry.portals());
        return b == null ? null : new Portal[] {a, b};
    }

    /** Which side of a block's front p is on (+ in front). */
    private static double side(Vec3 p, BlockPos pos, Direction facing) {
        double along = facing.getAxis().choose(p.x, p.y, p.z) - plane(pos, facing);
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along : -along;
    }

    /**
     * Something in a portal block: if it went in through its front this tick, it comes out of the linked portal.
     * Players do it themselves on their side (no waiting for the server: client/PortalClient), the server checks;
     * everything else the server moves.
     */
    public static void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.isPassenger() || entity.isVehicle() || entity.isOnPortalCooldown()) {
            return;
        }
        Direction facing = state.getValue(PortalMirrorBlock.FACING);
        double before = side(new Vec3(entity.xo, entity.yo, entity.zo), pos, facing), now = side(entity.position(), pos, facing);
        if (!(before > 0.0 && now <= 0.0)) {
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
        Portal[] ab = link(server, pos);
        if (ab == null) {
            return;
        }
        int q = turns(ab[0], ab[1]);
        Vec3 to = apply(ab[0], ab[1], entity.position());
        server.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(BlockPos.containing(to)), 3, BlockPos.containing(to));
        entity.teleportTo(to.x, to.y, to.z);
        entity.setYRot(entity.getYRot() + 90.0F * q);
        entity.setYHeadRot(entity.getYHeadRot() + 90.0F * q);
        entity.setDeltaMovement(rotate(entity.getDeltaMovement(), q));
        entity.hurtMarked = true; // (its new speed sent on)
        entity.setPortalCooldown(5);
    }

    /**
     * (server) A player's client says they went into a portal at {@code at}: moved there here too, if it checks out -
     * quietly when their client moved them already (the other portal was in sight: no snap back), or with a teleport.
     */
    public static void playerCrossed(ServerPlayer player, BlockPos pos, Vec3 at, boolean moved) {
        ServerLevel level = player.serverLevel();
        Portal[] ab = player.position().distanceToSqr(at) > 8.0 * 8.0 || at.distanceToSqr(Vec3.atCenterOf(pos)) > 3.0 * 3.0
                || !level.isLoaded(pos) || !isPortal(level.getBlockState(pos)) ? null : link(level, pos);
        if (ab == null) {
            if (moved) {
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            }
            return;
        }
        int q = turns(ab[0], ab[1]);
        Vec3 to = apply(ab[0], ab[1], at);
        float yaw = player.getYRot() + 90.0F * q;
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(BlockPos.containing(to)), 3, BlockPos.containing(to));
        if (moved) {
            player.absMoveTo(to.x, to.y, to.z, yaw, player.getXRot());
            player.setYHeadRot(player.getYHeadRot() + 90.0F * q);
            player.connection.resetPosition();
        } else {
            player.teleportTo(level, to.x, to.y, to.z, yaw, player.getXRot());
            level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        player.setPortalCooldown(5);
        if (moved) {
            level.playSound(player, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.6F); // (they heard it already)
        }
    }
}
