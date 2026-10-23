package com.example.skyblocks;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The fog machine's settings (the fog's area: width x depth x height, in front of it; whether the area is outlined),
 * and its fog: puffed on the client every tick, spread through the area and kept inside it.
 */
public class FogMachineBlockEntity extends BlockEntity {
    public static final int MAX_WIDTH = 32, MAX_DEPTH = 32, MAX_HEIGHT = 12;
    /** Fog machines going on this client: position -> when it last puffed (System.nanoTime). */
    public static final Map<BlockPos, Long> ACTIVE = new HashMap<>();

    private int width = 7, depth = 8, height = 2;
    private boolean showArea;

    public FogMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FOG_MACHINE.get(), pos, state);
    }

    public int width() { return width; }
    public int depth() { return depth; }
    public int height() { return height; }
    public boolean showArea() { return showArea; }

    public void setSettings(int width, int depth, int height, boolean showArea) {
        this.width = Mth.clamp(width, 1, MAX_WIDTH);
        this.depth = Mth.clamp(depth, 1, MAX_DEPTH);
        this.height = Mth.clamp(height, 1, MAX_HEIGHT);
        this.showArea = showArea;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** The fog's area for these settings: starting just in front of the machine, centred on it, from its floor up. */
    public static AABB area(BlockPos pos, Direction facing, int width, int depth, int height) {
        Direction right = facing.getClockWise();
        double left = -(width / 2), rightEnd = width - width / 2;
        double x0 = pos.getX() + 0.5, z0 = pos.getZ() + 0.5;
        double ax = x0 + facing.getStepX() * 0.5, az = z0 + facing.getStepZ() * 0.5; // (the machine's front face)
        double bx = ax + facing.getStepX() * depth, bz = az + facing.getStepZ() * depth;
        double lx = right.getStepX() * (left - 0.5), lz = right.getStepZ() * (left - 0.5);
        double rx = right.getStepX() * (rightEnd - 0.5), rz = right.getStepZ() * (rightEnd - 0.5);
        return new AABB(Math.min(Math.min(ax + lx, ax + rx), Math.min(bx + lx, bx + rx)), pos.getY(),
                Math.min(Math.min(az + lz, az + rz), Math.min(bz + lz, bz + rz)),
                Math.max(Math.max(ax + lx, ax + rx), Math.max(bx + lx, bx + rx)), pos.getY() + height,
                Math.max(Math.max(az + lz, az + rz), Math.max(bz + lz, bz + rz)));
    }

    public AABB area() {
        Direction facing = getBlockState().hasProperty(FogMachineBlock.FACING) ? getBlockState().getValue(FogMachineBlock.FACING) : Direction.NORTH;
        return area(worldPosition, facing, width, depth, height);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FogMachineBlockEntity be) {
        if (!state.getValue(FogMachineBlock.ON)) {
            ACTIVE.remove(pos);
            return;
        }
        ACTIVE.put(pos.immutable(), System.nanoTime());
        Direction facing = state.getValue(FogMachineBlock.FACING);
        RandomSource random = level.getRandom();
        AABB area = be.area();
        // a jet from the nozzle...
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.5 + facing.getStepX() * 0.56 + (random.nextDouble() - 0.5) * 0.12;
            double y = pos.getY() + 0.31 + (random.nextDouble() - 0.5) * 0.1;
            double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.56 + (random.nextDouble() - 0.5) * 0.12;
            double speed = 0.1 + random.nextDouble() * 0.08;
            FogParticleBounds.next = area;
            level.addParticle(ModParticles.FOG.get(), x, y, z, facing.getStepX() * speed + (random.nextDouble() - 0.5) * 0.04,
                    -0.004, facing.getStepZ() * speed + (random.nextDouble() - 0.5) * 0.04);
        }
        // ...and the fog filling the area, more for a bigger area
        double floor = area.getXsize() * area.getZsize();
        int count = Mth.clamp((int) (floor / 14.0), 1, 10);
        for (int i = 0; i < count; i++) {
            if (random.nextFloat() > 0.6F) {
                continue;
            }
            double x = area.minX + random.nextDouble() * area.getXsize();
            double y = area.minY + 0.15 + Math.pow(random.nextDouble(), 1.6) * Math.max(0.1, area.getYsize() - 0.6);
            double z = area.minZ + random.nextDouble() * area.getZsize();
            FogParticleBounds.next = area;
            level.addParticle(ModParticles.FOG.get(), x, y, z, (random.nextDouble() - 0.5) * 0.02, 0.0, (random.nextDouble() - 0.5) * 0.02);
        }
        FogParticleBounds.next = null;
    }

    /** Whether a fog machine has been going within {@code range} blocks of pos in the last couple of seconds. */
    public static boolean fogNear(BlockPos pos, double range) {
        long now = System.nanoTime();
        ACTIVE.values().removeIf(t -> now - t > 3_000_000_000L);
        for (BlockPos p : ACTIVE.keySet()) {
            if (p.distSqr(pos) <= range * range) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("width", width);
        tag.putInt("depth", depth);
        tag.putInt("height", height);
        tag.putBoolean("show_area", showArea);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("width")) {
            width = Mth.clamp(tag.getInt("width"), 1, MAX_WIDTH);
            depth = Mth.clamp(tag.getInt("depth"), 1, MAX_DEPTH);
            height = Mth.clamp(tag.getInt("height"), 1, MAX_HEIGHT);
            showArea = tag.getBoolean("show_area");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
