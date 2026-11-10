package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A light bridge emitter. Two of them joined by the light (straight across, round benders, up or down) make a bridge,
 * kept by one of the two (the one further north-west-down, by position): it puts the bridge's blocks down and takes them
 * away, draws it, and carries things along it when it's a moving walkway or a tunnel. Each keeps its colour (-1: rainbow),
 * what kind of bridge it makes, and which way that carries things.
 */
public class LightBridgeEmitterBlockEntity extends BlockEntity {
    private int colour = -1;
    private int mode = LightBridgePath.BRIDGE;
    private boolean reverse;
    /** How it looks: how wide (1-3 blocks), its pattern, how see-through (0 faint - 3 solid), glowing rails along its edges. */
    private int width = 1, pattern, opacity = 2;
    private boolean rails;
    public static final int PATTERNS = 5;
    /** The keeper's: the corners of its bridge (none: no bridge), and where it put its blocks (server). */
    private List<LightBridgePath.Node> nodes = List.of();
    private final List<BlockPos> placed = new ArrayList<>();
    private int placedWidth = 1, placedMode;
    @Nullable
    private LightBridgePath path;

    public LightBridgeEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHT_BRIDGE_EMITTER.get(), pos, state);
    }

    public int colour() {
        return colour;
    }

    public int mode() {
        return mode;
    }

    public boolean reverse() {
        return reverse;
    }

    /** Its bridge, if it keeps one. */
    @Nullable
    public LightBridgePath path() {
        if (path == null && nodes.size() >= 2) {
            path = new LightBridgePath(nodes, mode, width);
        }
        return nodes.size() >= 2 ? path : null;
    }

    public int width() {
        return width;
    }

    public int pattern() {
        return pattern;
    }

    public int opacity() {
        return opacity;
    }

    public boolean rails() {
        return rails;
    }

    /** Everything about it at once (from the settings screen). */
    public void setSettings(int m, boolean rev, int w, int pat, int op, boolean r) {
        mode = Math.floorMod(m, LightBridgePath.MODES);
        reverse = rev;
        width = Math.max(1, Math.min(3, w));
        pattern = Math.floorMod(pat, PATTERNS);
        opacity = Math.max(0, Math.min(3, op));
        rails = r;
        path = null;
        changed();
    }

    public void setColour(int c) {
        colour = c;
        changed();
    }

    public void setMode(int m, boolean rev) {
        mode = m;
        reverse = rev;
        path = null;
        changed();
    }

    private Direction facing() {
        return getBlockState().getValue(LightBridgeEmitterBlock.FACING);
    }

    /** The emitter at the other end of its light, if the light reaches one. */
    @Nullable
    public LightBridgeEmitterBlockEntity partner() {
        if (level == null) return null;
        List<LightBridgePath.Node> found = LightBridgePath.trace(level, worldPosition, facing());
        if (found == null) return null;
        LightBridgePath.Node end = found.get(found.size() - 1);
        return level.getBlockEntity(new BlockPos(end.x(), end.y(), end.z())) instanceof LightBridgeEmitterBlockEntity e ? e : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LightBridgeEmitterBlockEntity be) {
        if (level.getGameTime() % 10L == Math.floorMod(pos.asLong(), 10L)) {
            be.update(level, pos, state);
        }
        be.carry(level, null);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LightBridgeEmitterBlockEntity be) {
        Player me = com.example.skyblocks.client.ClientAccess.player();
        if (me != null) be.carry(level, me);
    }

    /** Looks again where its light goes, and puts its bridge there (or takes it away). */
    private void update(Level level, BlockPos pos, BlockState state) {
        List<LightBridgePath.Node> found = LightBridgePath.trace(level, pos, state.getValue(LightBridgeEmitterBlock.FACING));
        List<LightBridgePath.Node> want = List.of();
        if (found != null) {
            LightBridgePath.Node end = found.get(found.size() - 1);
            BlockPos other = new BlockPos(end.x(), end.y(), end.z());
            boolean keeper = pos.asLong() < other.asLong();
            if (keeper && !level.hasNeighborSignal(pos) && !level.hasNeighborSignal(other)) {
                want = found;
            }
        }
        if (want.equals(nodes) && placedWidth == width && placedMode == mode && (want.isEmpty() || stillThere(level))) {
            return;
        }
        boolean had = !nodes.isEmpty();
        clear(level);
        nodes = want;
        placedWidth = width;
        placedMode = mode;
        path = null;
        LightBridgePath p = path();
        if (p != null && !place(level, p)) {
            clear(level);
            nodes = List.of();
            path = null;
        }
        if (had != !nodes.isEmpty()) {
            level.playSound(null, pos, nodes.isEmpty() ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.6F);
        }
        if (had || !nodes.isEmpty()) changed();
    }

    private boolean stillThere(Level level) {
        for (BlockPos p : placed) {
            if (!(level.getBlockState(p).getBlock() instanceof LightBridgeBlock)) return false;
        }
        return true;
    }

    /** Where its blocks go - the bridge's plate, the wall, or the tunnel's light - and whether there's room for them all. */
    private boolean place(Level level, LightBridgePath p) {
        if (!p.walkable) return false;
        Map<BlockPos, BlockState> want = new HashMap<>();
        BlockState base = ModBlocks.LIGHT_BRIDGE.get().defaultBlockState();
        for (LightBridgePath.Sample a : p.samples) {
            float half = p.width / 2.0F - 0.05F;
            for (float across = -half; across <= half + 0.01F; across += Math.min(0.45F, half)) {
                double x = a.x() - a.dz() * across, z = a.z() + a.dx() * across;
                double y = a.y();
                int by = (int) Math.floor(y + 1e-4);
                int off = Math.min(14, Math.round((float) ((y - by) * 16)));
                BlockPos cell = BlockPos.containing(x, by, z);
                if (p.mode == LightBridgePath.WALL) {
                    LightBridgeBlock.Kind k = Math.abs(a.dx()) > 0.9F ? LightBridgeBlock.Kind.WALL_X
                            : Math.abs(a.dz()) > 0.9F ? LightBridgeBlock.Kind.WALL_Z : LightBridgeBlock.Kind.WALL;
                    if (Math.abs(across) > 0.1F) continue;
                    for (int h = 0; h < 3; h++) {
                        want.put(cell.above(h), base.setValue(LightBridgeBlock.KIND, k).setValue(LightBridgeBlock.OFFSET, 0));
                    }
                } else if (p.mode == LightBridgePath.TUNNEL) {
                    for (int h = 0; h < Math.round(p.tunnelRadius() * 2); h++) {
                        want.putIfAbsent(cell.above(h), base.setValue(LightBridgeBlock.KIND, LightBridgeBlock.Kind.TUNNEL));
                    }
                } else {
                    BlockState s = base.setValue(LightBridgeBlock.OFFSET, off);
                    BlockState had = want.get(cell);
                    if (had == null || had.getValue(LightBridgeBlock.OFFSET) < off) want.put(cell, s);
                }
            }
        }
        for (BlockPos c : want.keySet()) {
            BlockState s = level.getBlockState(c);
            if (!s.isAir() && !(s.getBlock() instanceof LightBridgeBlock) && !(s.getBlock() instanceof LightBridgeEmitterBlock)
                    && !(s.getBlock() instanceof LightBridgeBenderBlock) && !s.canBeReplaced()) {
                return false;
            }
        }
        for (Map.Entry<BlockPos, BlockState> e : want.entrySet()) {
            BlockState s = level.getBlockState(e.getKey());
            if (s.getBlock() instanceof LightBridgeEmitterBlock || s.getBlock() instanceof LightBridgeBenderBlock) continue;
            level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
            placed.add(e.getKey().immutable());
        }
        return true;
    }

    /** Takes its blocks away. */
    public void clear(Level level) {
        for (BlockPos p : placed) {
            if (level.getBlockState(p).getBlock() instanceof LightBridgeBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        placed.clear();
    }

    /**
     * A moving walkway carries whatever stands on it along; a tunnel carries whatever is in it through, floating. On the
     * server for everything but players; on each player's own client for them (their movement is theirs).
     */
    private void carry(Level level, @Nullable Player only) {
        LightBridgePath p = path();
        if (p == null || p.mode != LightBridgePath.SPEED && p.mode != LightBridgePath.TUNNEL || p.samples.isEmpty()) return;
        double[] b = p.bounds();
        List<? extends Entity> who = only != null ? List.of(only)
                : level.getEntities((Entity) null, new AABB(b[0], b[1], b[2], b[3], b[4], b[5]), e -> !(e instanceof Player) && !e.isSpectator());
        float[] dist = new float[1];
        float sign = reverse ? -1 : 1;
        for (Entity e : who) {
            if (only != null && !new AABB(b[0], b[1], b[2], b[3], b[4], b[5]).contains(e.position())) continue;
            LightBridgePath.Sample a = p.nearest(e.getX(), e.getY(), e.getZ(), dist);
            if (a == null) continue;
            float along = reverse ? p.length - a.s() : a.s();
            if (along > p.length - 0.6F) continue; // (at the end: let go)
            Vec3 v = e.getDeltaMovement();
            double hx = a.dx() * sign, hz = a.dz() * sign;
            if (p.mode == LightBridgePath.SPEED) {
                double top = a.y() + 2.0 / 16;
                if (dist[0] > p.width / 2.0F + 0.05F || e.getY() < top - 0.3 || e.getY() > top + 0.35) continue;
                double speed = 0.42, now = v.x * hx + v.z * hz;
                double push = Math.max(0, speed - now) * 0.35;
                e.setDeltaMovement(v.x + hx * push, v.y, v.z + hz * push);
            } else {
                double cy = a.y() + p.tunnelRadius() - 0.2, ey = e.getY() + e.getBbHeight() / 2;
                double off = Math.hypot(dist[0], ey - cy);
                if (off > p.tunnelRadius() + 0.05) continue;
                double toX = a.x() - e.getX(), toY = cy - ey, toZ = a.z() - e.getZ();
                double speed = 0.24;
                e.setDeltaMovement(hx * speed + toX * 0.12, toY * 0.15 + 0.0, hz * speed + toZ * 0.12);
                e.resetFallDistance();
            }
            e.hurtMarked = only == null;
        }
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveShared(tag);
        long[] p = new long[placed.size()];
        for (int i = 0; i < p.length; i++) p[i] = placed.get(i).asLong();
        tag.putLongArray("placed", p);
    }

    private void saveShared(CompoundTag tag) {
        tag.putInt("colour", colour);
        tag.putInt("mode", mode);
        tag.putBoolean("reverse", reverse);
        tag.putIntArray("nodes", LightBridgePath.pack(nodes));
        tag.putInt("width", width);
        tag.putInt("pattern", pattern);
        tag.putInt("opacity", opacity);
        tag.putBoolean("rails", rails);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        colour = tag.contains("colour") ? tag.getInt("colour") : -1;
        mode = tag.getInt("mode");
        reverse = tag.getBoolean("reverse");
        nodes = LightBridgePath.unpack(tag.getIntArray("nodes"));
        width = tag.contains("width") ? Math.max(1, Math.min(3, tag.getInt("width"))) : 1;
        pattern = tag.getInt("pattern");
        opacity = tag.contains("opacity") ? tag.getInt("opacity") : 2;
        rails = tag.getBoolean("rails");
        path = null;
        if (tag.contains("placed")) {
            placed.clear();
            for (long l : tag.getLongArray("placed")) placed.add(BlockPos.of(l));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveShared(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
