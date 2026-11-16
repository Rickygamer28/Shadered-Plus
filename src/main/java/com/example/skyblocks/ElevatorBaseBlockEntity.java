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
    /** Its floors: where you stand on each (y), from the bottom; the first on top of the base. */
    private int[] floors = new int[0];
    @Nullable
    private UUID cabinId;
    private final ArrayDeque<Integer> queue = new ArrayDeque<>();

    /** (client) Its cabin, as last found. */
    @Nullable
    public ElevatorCabinEntity clientCabin;
    /** (client) The shaft's walls, as last found (each: x, z of the wall's cell from the middle, side, y). */
    public int[] clientWalls = new int[0];
    public long clientWallsAt = -1000;

    public ElevatorBaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELEVATOR_BASE.get(), pos, state);
    }

    public int skin() {
        return skin;
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
        return f[f.length - 1] + 3;
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
        return new AABB(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 1,
                worldPosition.getX() + 2, top() + 1, worldPosition.getZ() + 2);
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
        if (level.getGameTime() % 40 == pos.hashCode() % 40 || be.floors.length == 0) {
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
        int lo = Math.min(a, b), hi = Math.max(a, b) + 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = lo; y <= hi; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    p.set(worldPosition.getX() + dx, y, worldPosition.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (!s.getCollisionShape(level, p).isEmpty()) {
                        return y;
                    }
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Its floors: floor 1 on top of it, and one for each call panel beside the shaft above. */
    private void findFloors() {
        List<Integer> found = new ArrayList<>();
        int base = worldPosition.getY() + 1;
        found.add(base);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int max = Math.min(worldPosition.getY() + REACH, level.getMaxBuildHeight() - 3);
        for (int y = base; y <= max; y++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) < 2) {
                        continue;
                    }
                    p.set(worldPosition.getX() + dx, y, worldPosition.getZ() + dz);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        skin = Math.floorMod(tag.getInt("skin"), SKINS.length);
        speed = tag.contains("speed") ? Math.floorMod(tag.getInt("speed"), SPEEDS.length) : 1;
        floors = tag.getIntArray("floors");
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
    public static boolean standingIn(Entity e, double cx, double cz, double landing) {
        return Math.abs(e.getX() - cx) < 1.45 && Math.abs(e.getZ() - cz) < 1.45 && e.getY() > landing - 0.4 && e.getY() < landing + 1.2;
    }
}
