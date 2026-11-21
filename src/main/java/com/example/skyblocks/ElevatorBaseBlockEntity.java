package com.example.skyblocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * An elevator's base: at the bottom of its shaft (3 x 3, open above it), floor 1 on top of it. Its floors are found by
 * their call panels (ElevatorPanelBlock), beside the shaft (2 to 4 blocks from its middle), anywhere up it; its cabin
 * (ElevatorCabinEntity) rides up and down it, sent to the floors asked for, one after another. It keeps how it looks -
 * a glass capsule, a hard-light platform, a bubble pod, a warp shaft, a crystal cabin or a cloud - and how fast it goes.
 * (Drawn - the cabin and the shaft both - by client/ElevatorRenderer.)
 */
public class ElevatorBaseBlockEntity extends BlockEntity {
    public static final String[] SKINS = {"glass", "hardlight", "bubble", "warp", "crystal", "cloud"};
    /** How fast it goes (blocks a tick, at its fastest). */
    public static final float[] SPEEDS = {0.15F, 0.3F, 0.6F};
    /** How high it reaches above its base, at most; how close two floors may be. */
    public static final int REACH = 256, GAP = 3;

    private int skin, speed = 1;
    /** Its size: as set (0 fitted to the shaft, else 1 .. 7 across) and its cabin's height (2 .. 4 blocks); the shaft as it is
     *  now - how wide, and its corner (from the base: the base under it, in the middle when it's odd). */
    private int sizeSetting, cabinHeight = 3;
    private int width = 3, ox = -1, oz = -1;
    /** The side its floor buttons and crystal rail are on: -1 worked out (the side with the most wall), 0 north, 1 south, 2 west, 3 east. */
    private int side = -1;
    /** Whether the call panels at the floor its cabin's stopped at give a redstone signal (doors by them opening); which floor's do now. */
    private boolean redstone;
    private int poweredLanding = Integer.MIN_VALUE;
    /** Whether it opens the doors (trapdoors, fence gates) in front of the shaft at the floor it's stopped at; which it opened. */
    private boolean doors = true;
    private int doorsLanding = Integer.MIN_VALUE;
    private final List<BlockPos> opened = new ArrayList<>();
    /** Its floors: where you stand on each (y), from the bottom; the first on top of the base. */
    private int[] floors = new int[0];
    @Nullable
    private UUID cabinId;
    private final ArrayDeque<Integer> queue = new ArrayDeque<>();
    /** Its floors to be found again (a call panel put up or taken down by it): at once, rather than at the next look. */
    private boolean floorsChanged = true;

    /** (client) Its cabin, as last found. */
    @Nullable
    public ElevatorCabinEntity clientCabin;
    /** (client) The shaft's walls, as last found (each: x, z of the wall's cell from the middle, side, y). */
    public int[] clientWalls = new int[0];
    public long clientWallsAt = -1000;
    /** (client) The side its floor buttons are on, as last worked out (client/ElevatorButtons). */
    public int clientPanelSide;
    public long clientPanelAt = -1000;

    public ElevatorBaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELEVATOR_BASE.get(), pos, state);
    }

    public int skin() {
        return skin;
    }

    /** How wide the shaft (and its cabin) is: 1 .. 7. */
    public int width() {
        return width;
    }

    /** How tall its cabin is (blocks). */
    public int cabinHeight() {
        return cabinHeight;
    }

    public int sizeSetting() {
        return sizeSetting;
    }

    /** The shaft's corner (smallest x, z). */
    public int x0() {
        return worldPosition.getX() + ox;
    }

    public int z0() {
        return worldPosition.getZ() + oz;
    }

    /** The shaft's middle (x, z). */
    public double centreX() {
        return x0() + width / 2.0;
    }

    public double centreZ() {
        return z0() + width / 2.0;
    }

    /** How far outside the shaft a column is (0 in it, 1 next to it ...). */
    public int outside(int x, int z) {
        int dx = x < x0() ? x0() - x : x >= x0() + width ? x - (x0() + width - 1) : 0;
        int dz = z < z0() ? z0() - z : z >= z0() + width ? z - (z0() + width - 1) : 0;
        return Math.max(dx, dz);
    }

    public void setSize(int s) {
        sizeSetting = Math.max(0, Math.min(7, s));
        floorsChanged = true;
        changed();
    }

    public void setCabinHeight(int h) {
        cabinHeight = Math.max(2, Math.min(4, h));
        floorsChanged = true;
        changed();
    }

    public int speedIndex() {
        return speed;
    }

    public int[] floors() {
        return floors.length == 0 ? new int[] {worldPosition.getY() + 1} : floors;
    }

    /** The top of the shaft (y): a few blocks over its top floor. */
    public int top() {
        int[] f = floors();
        return f[f.length - 1] + cabinHeight;
    }

    /** Which floor (0 ..) is nearest a height. */
    public int floorAt(double y) {
        int[] f = floors();
        int best = 0;
        for (int i = 1; i < f.length; i++) {
            if (Math.abs(f[i] - y) < Math.abs(f[best] - y)) {
                best = i;
            }
        }
        return best;
    }

    public int side() {
        return side;
    }

    /** The next side (auto, north, east, south, west). */
    public boolean redstone() {
        return redstone;
    }

    public boolean doors() {
        return doors;
    }

    public void toggleDoors() {
        doors = !doors;
        changed();
    }

    public void toggleRedstone() {
        redstone = !redstone;
        changed();
    }

    public void nextSide() {
        int[] order = {-1, 0, 3, 1, 2};
        int at = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == side) {
                at = i;
            }
        }
        side = order[(at + 1) % order.length];
        clientPanelAt = -1000;
        changed();
    }

    public void setSkin(int s) {
        skin = Math.floorMod(s, SKINS.length);
        changed();
    }

    public void setSpeed(int s) {
        speed = Math.floorMod(s, SPEEDS.length);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The space the shaft takes (the cabin's whole way). */
    public AABB shaft() {
        return new AABB(x0(), worldPosition.getY(), z0(), x0() + width, top() + 1, z0() + width);
    }

    /** Sends the cabin to a floor (once it's done with those asked for before). */
    public void call(int floor) {
        int[] f = floors();
        if (floor < 0 || floor >= f.length) {
            return;
        }
        int y = f[floor];
        if (!queue.contains(y)) {
            queue.add(y);
        }
    }

    @Nullable
    public ElevatorCabinEntity cabin() {
        if (level instanceof ServerLevel sl && cabinId != null && sl.getEntity(cabinId) instanceof ElevatorCabinEntity c && !c.isRemoved()) {
            return c;
        }
        return null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElevatorBaseBlockEntity be) {
        // (found again when a panel's put up or taken down by it - and now and then, in case)
        if (be.floorsChanged || Math.floorMod(level.getGameTime() + pos.hashCode(), 600L) == 0) {
            be.floorsChanged = false;
            be.findFloors();
        }
        ElevatorCabinEntity cabin = be.cabin();
        if (cabin == null) {
            // (its cabin: the one in its shaft, or a new one on floor 1)
            List<ElevatorCabinEntity> found = level.getEntitiesOfClass(ElevatorCabinEntity.class, be.shaft(), c -> c.base().equals(pos));
            if (!found.isEmpty()) {
                cabin = found.get(0);
                for (int i = 1; i < found.size(); i++) {
                    found.get(i).discard();
                }
            } else if (level.getGameTime() % 10 == 0) {
                cabin = ElevatorCabinEntity.make(level, pos, be.floors()[0]);
                level.addFreshEntity(cabin);
            }
            if (cabin != null) {
                be.cabinId = cabin.getUUID();
                be.setChanged();
            }
        }
        // the panels at the floor it's stopped at: a redstone signal (when it's set to)
        int at = be.redstone && cabin != null && !cabin.moving() ? Math.round(cabin.landing()) : Integer.MIN_VALUE;
        if (at != be.poweredLanding) {
            be.powerPanels(be.poweredLanding, false);
            be.powerPanels(at, true);
            be.poweredLanding = at;
        }
        // the doors in front of the shaft at the floor it's stopped at: open (and the ones it opened shut, when it leaves)
        int doorsAt = be.doors && cabin != null && !cabin.moving() ? Math.round(cabin.landing()) : Integer.MIN_VALUE;
        if (doorsAt != be.doorsLanding) {
            be.shutDoors();
            be.openDoors(doorsAt);
            be.doorsLanding = doorsAt;
        }
        if (cabin != null) {
            cabin.shape(be.width, (float) (be.centreX() - pos.getX() - 0.5), (float) (be.centreZ() - pos.getZ() - 0.5), be.cabinHeight);
        }
        if (cabin != null && !cabin.moving() && !be.queue.isEmpty()) {
            int to = be.queue.poll();
            int from = Math.round(cabin.landing());
            if (to != from) {
                int blocked = be.blockedBetween(from, to);
                if (blocked == Integer.MIN_VALUE) {
                    cabin.go(to, SPEEDS[be.speed]);
                } else {
                    for (net.minecraft.world.entity.player.Player p : level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                            be.shaft().inflate(4))) {
                        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.skyblocks.elevator.blocked", blocked)
                                .withStyle(net.minecraft.ChatFormatting.RED), true);
                    }
                }
            }
        }
    }

    /** The height where something solid blocks the shaft between two floors, or Integer.MIN_VALUE if it's clear. */
    private int blockedBetween(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b) + cabinHeight - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = lo; y <= hi; y++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < width; dz++) {
                    p.set(x0() + dx, y, z0() + dz);
                    BlockState s = level.getBlockState(p);
                    if (!s.getCollisionShape(level, p).isEmpty()) {
                        return y;
                    }
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Its floors: floor 1 on top of it, and one for each call panel beside the shaft above. (A stretch of 16 blocks up
     * with no call panel anywhere in it, by the game's own note of which blocks each chunk section has, is passed over.)
     */
    /** Opens the doors, trapdoors and fence gates one or two blocks out from the shaft at a floor (feet and head height). */
    private void openDoors(int landing) {
        if (landing == Integer.MIN_VALUE || level == null) {
            return;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = landing; y <= landing + 1; y++) {
            for (int x = x0() - 2; x < x0() + width + 2; x++) {
                for (int z = z0() - 2; z < z0() + width + 2; z++) {
                    if (outside(x, z) == 0) {
                        continue;
                    }
                    p.set(x, y, z);
                    if (setOpen(p, true)) {
                        opened.add(p.immutable());
                    }
                }
            }
        }
        if (!opened.isEmpty()) {
            setChanged();
        }
    }

    /** Shuts the doors it opened (those still there and open). */
    private void shutDoors() {
        for (BlockPos p : opened) {
            setOpen(p, false);
        }
        if (!opened.isEmpty()) {
            opened.clear();
            setChanged();
        }
    }

    /** A door, trapdoor or fence gate there opened or shut; whether it changed. */
    private boolean setOpen(BlockPos p, boolean open) {
        BlockState st = level.getBlockState(p);
        if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door) {
            if (st.getValue(net.minecraft.world.level.block.DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    || door.isOpen(st) == open) {
                return false;
            }
            door.setOpen(null, level, st, p, open);
            return true;
        }
        net.minecraft.world.level.block.state.properties.BooleanProperty prop = net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;
        if ((st.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock || st.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock)
                && st.getValue(prop) != open) {
            level.setBlock(p, st.setValue(prop, open), Block.UPDATE_ALL);
            level.playSound(null, p, open ? net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_OPEN : net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    /** The call panels round the shaft for the floor at landing: on or off. */
    private void powerPanels(int landing, boolean on) {
        if (landing == Integer.MIN_VALUE || level == null) {
            return;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = landing - 3; y <= landing + 3; y++) {
            for (int x = x0() - 3; x < x0() + width + 3; x++) {
                for (int z = z0() - 3; z < z0() + width + 3; z++) {
                    if (outside(x, z) == 0) {
                        continue;
                    }
                    p.set(x, y, z);
                    BlockState st = level.getBlockState(p);
                    if (st.getBlock() instanceof ElevatorPanelBlock && st.getValue(ElevatorPanelBlock.POWERED) != on
                            && (!on || ElevatorPanelBlock.landing(level, p) == landing)) {
                        level.setBlock(p, st.setValue(ElevatorPanelBlock.POWERED, on), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /**
     * The shaft's size and corner: as set, or fitted - the widest square (7 across at most) clear of anything solid all the
     * way up, from just over the base to its top floor's ceiling, over the base (odd: the base in its middle; even: to
     * whichever side's clear). As set: the place for that size with the most clear.
     */
    private void fitShaft() {
        int bx = worldPosition.getX(), bz = worldPosition.getZ();
        int lo = worldPosition.getY() + 1, hi = floors()[floors().length - 1] + cabinHeight - 1;
        // which columns round the base are clear all the way up
        boolean[][] clear = new boolean[13][13];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 13; i++) {
            for (int k = 0; k < 13; k++) {
                boolean ok = true;
                for (int y = lo; y <= hi && ok; y++) {
                    p.set(bx - 6 + i, y, bz - 6 + k);
                    ok = level.getBlockState(p).getCollisionShape(level, p).isEmpty();
                }
                clear[i][k] = ok;
            }
        }
        int bestN = 1, bestX = 0, bestZ = 0;
        if (sizeSetting == 0) {
            // the widest that's clear all over
            search:
            for (int n = 7; n >= 1; n--) {
                for (int ax : corners(n)) {
                    for (int az : corners(n)) {
                        if (score(clear, n, ax, az) == n * n) {
                            bestN = n;
                            bestX = ax;
                            bestZ = az;
                            break search;
                        }
                    }
                }
            }
        } else {
            // as set: where it's most clear
            int n = sizeSetting, best = -1;
            for (int ax : corners(n)) {
                for (int az : corners(n)) {
                    int sc = score(clear, n, ax, az);
                    if (sc > best) {
                        best = sc;
                        bestX = ax;
                        bestZ = az;
                    }
                }
            }
            bestN = n;
        }
        if (bestN != width || bestX != ox || bestZ != oz) {
            width = bestN;
            ox = bestX;
            oz = bestZ;
            clientWallsAt = -1000;
            floorsChanged = true; // (the panels round it looked for again, round it as it is now)
            changed();
        }
    }

    /** Where a shaft n across can have its corner (from the base) with the base under it: the middle first. */
    private static int[] corners(int n) {
        int[] out = new int[n];
        int mid = (n - 1) / 2;
        for (int k = 0; k < n; k++) {
            out[k] = -(k % 2 == 0 ? mid + k / 2 : mid - (k + 1) / 2);
        }
        // (past either end: folded back in)
        for (int k = 0; k < n; k++) {
            out[k] = Math.max(-(n - 1), Math.min(0, out[k]));
        }
        return out;
    }

    /** How many of a square's columns are clear (the grid round the base: 6, 6 its middle). */
    private static int score(boolean[][] clear, int n, int ax, int az) {
        int score = 0;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < n; k++) {
                if (clear[6 + ax + i][6 + az + k]) {
                    score++;
                }
            }
        }
        return score;
    }

    private void findFloors() {
        List<Integer> found = new ArrayList<>();
        int base = worldPosition.getY() + 1;
        found.add(base);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int max = Math.min(worldPosition.getY() + REACH, level.getMaxBuildHeight() - 3);
        // the chunks the ring round the shaft is in
        List<net.minecraft.world.level.chunk.LevelChunk> chunks = new ArrayList<>();
        for (int cx = (worldPosition.getX() - 10) >> 4; cx <= (worldPosition.getX() + 10) >> 4; cx++) {
            for (int cz = (worldPosition.getZ() - 10) >> 4; cz <= (worldPosition.getZ() + 10) >> 4; cz++) {
                if (level.hasChunk(cx, cz)) {
                    chunks.add(level.getChunk(cx, cz));
                }
            }
        }
        for (int y = base; y <= max; y++) {
            if (y == base || (y & 15) == 0) {
                // (a section with no panel in any of these chunks: on to the next)
                int section = level.getSectionIndex(y);
                boolean any = false;
                for (net.minecraft.world.level.chunk.LevelChunk c : chunks) {
                    if (section >= 0 && section < c.getSections().length
                            && c.getSections()[section].maybeHas(st -> st.getBlock() instanceof ElevatorPanelBlock)) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    y = (y | 15);
                    continue;
                }
            }
            for (int x = x0() - 3; x < x0() + width + 3; x++) {
                for (int z = z0() - 3; z < z0() + width + 3; z++) {
                    if (outside(x, z) == 0) {
                        continue;
                    }
                    p.set(x, y, z);
                    if (level.getBlockState(p).getBlock() instanceof ElevatorPanelBlock) {
                        int landing = ElevatorPanelBlock.landing(level, p);
                        if (landing > base && found.stream().allMatch(f -> Math.abs(f - landing) >= GAP)) {
                            found.add(landing);
                        }
                    }
                }
            }
        }
        int[] now = found.stream().mapToInt(Integer::intValue).sorted().toArray();
        if (!Arrays.equals(now, floors)) {
            floors = now;
            changed();
        }
        fitShaft();
    }

    /** Its floors to be found again (ElevatorPanelBlock: one put up or taken down by it). */
    public void floorsChanged() {
        floorsChanged = true;
    }

    /** Taken away: its cabin goes too (what it carried set down where it is). */
    public void removed() {
        ElevatorCabinEntity c = cabin();
        if (c != null) {
            c.release();
            c.discard();
        }
    }

    // ---- saving, and what the client sees ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        save(tag);
        if (cabinId != null) {
            tag.putUUID("cabin", cabinId);
        }
    }

    private void save(CompoundTag tag) {
        tag.putInt("skin", skin);
        tag.putInt("speed", speed);
        tag.putIntArray("floors", floors);
        tag.putInt("side", side);
        tag.putBoolean("redstone", redstone);
        tag.putInt("size", sizeSetting);
        tag.putInt("cabin_height", cabinHeight);
        tag.putInt("width", width);
        tag.putInt("ox", ox);
        tag.putInt("oz", oz);
        tag.putBoolean("no_doors", !doors);
        tag.putInt("doors_landing", doorsLanding);
        tag.putLongArray("opened", opened.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("powered_landing", poweredLanding);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        skin = Math.floorMod(tag.getInt("skin"), SKINS.length);
        speed = tag.contains("speed") ? Math.floorMod(tag.getInt("speed"), SPEEDS.length) : 1;
        floors = tag.getIntArray("floors");
        side = tag.contains("side") ? Math.max(-1, Math.min(3, tag.getInt("side"))) : -1;
        redstone = tag.getBoolean("redstone");
        sizeSetting = Math.max(0, Math.min(7, tag.getInt("size")));
        cabinHeight = tag.contains("cabin_height") ? Math.max(2, Math.min(4, tag.getInt("cabin_height"))) : 3;
        width = tag.contains("width") ? Math.max(1, Math.min(7, tag.getInt("width"))) : 3;
        ox = tag.contains("ox") ? Math.max(-6, Math.min(0, tag.getInt("ox"))) : -1;
        oz = tag.contains("oz") ? Math.max(-6, Math.min(0, tag.getInt("oz"))) : -1;
        doors = !tag.getBoolean("no_doors");
        doorsLanding = tag.contains("doors_landing") ? tag.getInt("doors_landing") : Integer.MIN_VALUE;
        opened.clear();
        for (long l : tag.getLongArray("opened")) {
            opened.add(BlockPos.of(l));
        }
        poweredLanding = tag.contains("powered_landing") ? tag.getInt("powered_landing") : Integer.MIN_VALUE;
        clientPanelAt = -1000;
        cabinId = tag.hasUUID("cabin") ? tag.getUUID("cabin") : null;
        clientWallsAt = -1000;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        save(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Whether an entity is where the cabin's riders stand (in the shaft, a little over the cabin's floor). */
    public static boolean standingIn(Entity e, double cx, double cz, double half, double landing) {
        return Math.abs(e.getX() - cx) < half - 0.05 && Math.abs(e.getZ() - cz) < half - 0.05 && e.getY() > landing - 0.4 && e.getY() < landing + 1.2;
    }
}
