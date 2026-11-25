package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A machine: its heart (MachineBlock) and the frames built round it. It looks at its frames now and then (and when one's
 * put down near it), and when they make its structure - in one of its sizes - it's formed and works; its energy and items
 * are reached through its frames too. Its kinds: ParticleAcceleratorBlockEntity, WeatherMachineBlockEntity,
 * StarForgeBlockEntity.
 */
public abstract class MachineBlockEntity extends BlockEntity {
    /** Its energy: how much it holds, and room for more (its size sets how much). */
    public static class Battery extends EnergyStorage {
        private final Runnable changed;

        Battery(int capacity, Runnable changed) {
            super(capacity, Integer.MAX_VALUE, Integer.MAX_VALUE);
            this.changed = changed;
        }

        void setCapacity(int capacity) {
            this.capacity = capacity;
            energy = Math.min(energy, capacity);
        }

        void set(int energy) {
            this.energy = Math.max(0, Math.min(capacity, energy));
        }

        int add(int fe) {
            int took = Math.min(fe, capacity - energy);
            energy += took;
            if (took > 0) {
                changed.run();
            }
            return took;
        }

        boolean take(int fe) {
            if (energy < fe) {
                return false;
            }
            energy -= fe;
            changed.run();
            return true;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            int got = super.receiveEnergy(amount, simulate);
            if (got > 0 && !simulate) {
                changed.run();
            }
            return got;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            int got = super.extractEnergy(amount, simulate);
            if (got > 0 && !simulate) {
                changed.run();
            }
            return got;
        }
    }

    /** Found frames: its size (1, 2, 3: small, medium, large) and which they are. */
    protected record Found(int size, List<BlockPos> frames) {}

    protected int size;
    private final List<BlockPos> frames = new ArrayList<>();
    private final List<BlockPos> outlets = new ArrayList<>();
    private final List<Direction> outletSides = new ArrayList<>();
    private boolean relink = true, syncPending;
    private int checkIn, syncIn;
    public final Battery energy;
    public final ItemStackHandler items;

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        energy = new Battery(capacityFor(1), this::changedQuietly);
        items = new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                sync();
            }

            @Override
            public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                return accepts(slot, stack);
            }
        };
    }

    public abstract MachineFrameBlock.Kind kind();

    /** Its frames, if they make its structure (null: they don't). */
    @Nullable
    protected abstract Found detect();

    /** How much energy it holds at a size. */
    protected abstract int capacityFor(int size);

    /** Each tick, formed. */
    protected abstract void work(ServerLevel level);

    /** Whether this can go in that slot (by hand or by hopper). */
    protected boolean accepts(int slot, net.minecraft.world.item.ItemStack stack) {
        return true;
    }

    /** Its energy for cables (on it and its frames). */
    public abstract IEnergyStorage energyHandler();

    /** Its items for hoppers and pipes (on it and its frames; null: none). */
    @Nullable
    public abstract IItemHandler itemHandler();

    /** Broken: what it was holding mid-way that isn't in its slots (dropped). */
    public void dropHeld() {}

    public int size() {
        return size;
    }

    public List<BlockPos> frames() {
        return frames;
    }

    public void relinkSoon() {
        relink = true;
    }

    /** (A frame put down: the machines near it look again.) */
    public static void relinkNear(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-8, -8, -8), pos.offset(8, 8, 8))) {
            if (level.getBlockEntity(p) instanceof MachineBlockEntity m) {
                m.relinkSoon();
            }
        }
    }

    /** Whether a frame there can be one of its (one of its kind; not another formed machine's). */
    protected boolean isFrame(BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!(s.getBlock() instanceof MachineFrameBlock f) || f.kind() != kind()) {
            return false;
        }
        return !(level.getBlockEntity(p) instanceof MachineFrameBlockEntity fe && fe.master() != null && !fe.master().equals(worldPosition)
                && level.getBlockEntity(fe.master()) instanceof MachineBlockEntity other && other.size > 0);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        if (relink || --checkIn <= 0) {
            checkIn = 20;
            relink = false;
            check();
        }
        if (size > 0) {
            work(sl);
        }
        if (syncPending && --syncIn <= 0) {
            syncPending = false;
            syncIn = 5;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void check() {
        Found found = detect();
        int now = found == null ? 0 : found.size();
        Set<BlockPos> next = found == null ? Set.of() : new HashSet<>(found.frames());
        if (now == size && next.equals(new HashSet<>(frames))) {
            return;
        }
        boolean grew = now > size;
        for (BlockPos p : frames) {
            if (!next.contains(p)) {
                link(p, false);
            }
        }
        frames.clear();
        if (found != null) {
            frames.addAll(found.frames());
        }
        for (BlockPos p : frames) {
            link(p, true);
        }
        int was = size;
        size = now;
        energy.setCapacity(capacityFor(Math.max(1, size)));
        outlets.clear();
        BlockState s = level.getBlockState(worldPosition);
        if (s.getBlock() instanceof MachineBlock && s.getValue(MachineBlock.FORMED) != (size > 0)) {
            level.setBlock(worldPosition, s.setValue(MachineBlock.FORMED, size > 0), Block.UPDATE_CLIENTS);
        }
        if (now != was) {
            level.playSound(null, worldPosition, grew ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.1F);
        }
        formedChanged();
        setChanged();
        sync();
    }

    /** (Its size changed: anything to reset.) */
    protected void formedChanged() {}

    private void link(BlockPos p, boolean formed) {
        BlockState s = level.getBlockState(p);
        if (!(s.getBlock() instanceof MachineFrameBlock) || !(level.getBlockEntity(p) instanceof MachineFrameBlockEntity f)) {
            return;
        }
        if (formed) {
            f.setMaster(worldPosition);
        } else if (f.master() == null || f.master().equals(worldPosition)) {
            f.setMaster(null);
        } else {
            return;
        }
        BlockState next = s.setValue(MachineFrameBlock.FORMED, formed).setValue(MachineFrameBlock.HIDDEN, formed && hiddenWhenFormed(p));
        if (next != s) {
            level.setBlock(p, next, Block.UPDATE_CLIENTS);
        }
    }

    /** Whether a frame of its, formed, hides its own model (it's drawn as part of the whole: the accelerator's tube). */
    protected boolean hiddenWhenFormed(BlockPos frame) {
        return false;
    }

    public void unform() {
        if (size > 0 && level != null && !level.isClientSide) {
            for (BlockPos p : frames) {
                link(p, false);
            }
            frames.clear();
            size = 0;
            formedChanged();
        }
    }

    /** What's beside it and its frames, outside them (where it gives energy, and the singularities a forge reaches). */
    protected List<BlockPos> outlets(List<Direction> sides) {
        if (outlets.isEmpty()) {
            Set<BlockPos> own = new HashSet<>(frames);
            own.add(worldPosition);
            for (BlockPos f : own) {
                for (Direction d : Direction.values()) {
                    BlockPos q = f.relative(d);
                    if (!own.contains(q)) {
                        outlets.add(q);
                        outletSides.add(d.getOpposite());
                    }
                }
            }
        }
        sides.clear();
        sides.addAll(outletSides);
        return outlets;
    }

    /** Gives up to {@code max} of its energy (from {@code from}) to the machines and cables beside it and its frames. */
    protected int giveEnergy(Battery from, int max) {
        List<Direction> sides = new ArrayList<>();
        List<BlockPos> at = outlets(sides);
        int given = 0;
        for (int i = 0; i < at.size() && given < max && from.getEnergyStored() > 0; i++) {
            BlockPos q = at.get(i);
            if (level.getBlockEntity(q) instanceof MachineFrameBlockEntity || q.equals(worldPosition)) {
                continue;
            }
            IEnergyStorage next = level.getCapability(Capabilities.EnergyStorage.BLOCK, q, sides.get(i));
            if (next != null && next.canReceive()) {
                int got = next.receiveEnergy(Math.min(max - given, from.getEnergyStored()), false);
                from.set(from.getEnergyStored() - got);
                given += got;
            }
        }
        return given;
    }

    /** (Changed: saved, and the clients told - not every tick.) */
    protected void sync() {
        syncPending = true;
    }

    private void changedQuietly() {
        setChanged();
        sync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("size", size);
        tag.putInt("energy", energy.getEnergyStored());
        tag.put("items", items.serializeNBT(registries));
        saveMore(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        size = tag.getInt("size");
        energy.setCapacity(capacityFor(Math.max(1, size)));
        energy.set(tag.getInt("energy"));
        if (tag.contains("items")) {
            items.deserializeNBT(registries, tag.getCompound("items"));
        }
        loadMore(tag, registries);
    }

    protected void saveMore(CompoundTag tag, HolderLookup.Provider registries) {}

    protected void loadMore(CompoundTag tag, HolderLookup.Provider registries) {}

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
