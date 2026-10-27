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
 * The fog machine's settings (the fog's area: how far it goes in front, behind, left, right and up; whether it's outlined),
 * and its fog: puffed on the client every tick, spread through the area and kept inside it.
 */
public class FogMachineBlockEntity extends BlockEntity {
    public static final int MAX_REACH = 32, MAX_HEIGHT = 12;
    /** Fog machines going on this client: position -> when it last puffed (System.nanoTime). */
    public static final Map<BlockPos, Long> ACTIVE = new HashMap<>();

    /** How many blocks the fog goes out from the machine: in front, behind, to its left, to its right; how high. */
    private int front = 8, back = 0, left = 3, right = 3, height = 2;
    private boolean showArea;

    public FogMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FOG_MACHINE.get(), pos, state);
    }

    public int front() { return front; }
    public int back() { return back; }
    public int left() { return left; }
    public int right() { return right; }
    public int height() { return height; }
    public boolean showArea() { return showArea; }

    public void setSettings(int front, int back, int left, int right, int height, boolean showArea) {
        this.front = Mth.clamp(front, 0, MAX_REACH);
        this.back = Mth.clamp(back, 0, MAX_REACH);
        this.left = Mth.clamp(left, 0, MAX_REACH);
        this.right = Mth.clamp(right, 0, MAX_REACH);
        this.height = Mth.clamp(height, 1, MAX_HEIGHT);
        this.showArea = showArea;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** The fog's area: the machine's block, stretched out that far each way (relative to where it faces), from its floor up. */
    public static AABB area(BlockPos pos, Direction facing, int front, int back, int left, int right, int height) {
        Direction toRight = facing.getClockWise();
        AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + height, pos.getZ() + 1);
        box = box.expandTowards(facing.getStepX() * front, 0, facing.getStepZ() * front);
        box = box.expandTowards(-facing.getStepX() * back, 0, -facing.getStepZ() * back);
        box = box.expandTowards(toRight.getStepX() * right, 0, toRight.getStepZ() * right);
        return box.expandTowards(-toRight.getStepX() * left, 0, -toRight.getStepZ() * left);
    }

    public AABB area() {
        Direction facing = getBlockState().hasProperty(FogMachineBlock.FACING) ? getBlockState().getValue(FogMachineBlock.FACING) : Direction.NORTH;
        return area(worldPosition, facing, front, back, left, right, height);
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("front", front);
        tag.putInt("back", back);
        tag.putInt("left", left);
        tag.putInt("right", right);
        tag.putInt("height", height);
        tag.putBoolean("show_area", showArea);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("front")) {
            front = Mth.clamp(tag.getInt("front"), 0, MAX_REACH);
            back = Mth.clamp(tag.getInt("back"), 0, MAX_REACH);
            left = Mth.clamp(tag.getInt("left"), 0, MAX_REACH);
            right = Mth.clamp(tag.getInt("right"), 0, MAX_REACH);
            height = Mth.clamp(tag.getInt("height"), 1, MAX_HEIGHT);
            showArea = tag.getBoolean("show_area");
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** What its renderer draws (1.20.1: the block entity says, not the renderer). */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(MAX_REACH + 1, MAX_HEIGHT + 1, MAX_REACH + 1);
    }
}
