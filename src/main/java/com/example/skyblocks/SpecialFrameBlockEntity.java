package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A special containment frame's own things: a port's filter (only this item moves; none: any), a monitor's item and how
 * many of it the singularity holds.
 */
public class SpecialFrameBlockEntity extends ContainmentFrameBlockEntity {
    /** A port moves a stack every this many ticks. */
    private static final int PORT_EVERY = 8;

    private ItemStack filter = ItemStack.EMPTY;
    /** (A monitor: how many of its item there are.) */
    private long count = -1;
    /** (A port putting out with no filter: which of the singularity's kinds is next, round them all.) */
    private int cursor;

    public SpecialFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPECIAL_FRAME.get(), pos, state);
    }

    @Nullable
    public SpecialFrameBlock.Kind kind() {
        return getBlockState().getBlock() instanceof SpecialFrameBlock b ? b.kind() : null;
    }

    public ItemStack filter() {
        return filter;
    }

    public void setFilter(ItemStack stack) {
        filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        sync();
    }

    /** A monitor: how many of its item there are (-1: not formed). */
    public long count() {
        return count;
    }

    /** (A monitor: counts its item again, and shows it if that changed.) */
    public void updateCount() {
        SingularityBlockEntity s = singularity();
        long now = s == null || filter.isEmpty() ? -1 : s.countOf(filter);
        if (now != count) {
            count = now;
            sync();
        }
    }

    /** A redstone frame's signal: how full its singularity is (0 while the chamber isn't formed). */
    public int signal() {
        SingularityBlockEntity s = singularity();
        return s == null ? 0 : s.signal();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- a port: moving items between the singularity and the containers beside it ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpecialFrameBlockEntity be) {
        if (be.kind() == SpecialFrameBlock.Kind.MONITOR) {
            if ((level.getGameTime() + pos.hashCode()) % 10 == 0) {
                be.updateCount();
            }
            return;
        }
        SingularityBlockEntity s = be.singularity();
        if (s == null || (level.getGameTime() + pos.hashCode()) % PORT_EVERY != 0) {
            return;
        }
        boolean out = state.getValue(SpecialFrameBlock.OUTPUT);
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState ns = level.getBlockState(n);
            if (ns.getBlock() instanceof ContainmentFrameBlock || ns.getBlock() instanceof SingularityBlock || !level.isLoaded(n)) {
                continue; // (its own chamber: not a container beside it)
            }
            IItemHandler other = level.getCapability(Capabilities.ItemHandler.BLOCK, n, d.getOpposite());
            if (other == null) {
                continue;
            }
            if (out ? be.pushInto(s, other) : be.pullFrom(s, other)) {
                return; // (one stack a turn)
            }
        }
    }

    /** One stack (of the filter's item, or the next kind) from the singularity into {@code to}. */
    private boolean pushInto(SingularityBlockEntity s, IItemHandler to) {
        java.util.List<SingularityBlockEntity.Entry> entries = s.entries();
        if (entries.isEmpty()) {
            return false;
        }
        int tries = filter.isEmpty() ? Math.min(entries.size(), 16) : 1;
        for (int t = 0; t < tries; t++) {
            ItemStack like;
            if (filter.isEmpty()) {
                cursor = Math.floorMod(cursor, entries.size());
                like = entries.get(cursor).item;
                cursor++;
            } else {
                like = filter;
            }
            ItemStack can = s.extract(like, 64, true);
            if (can.isEmpty()) {
                continue;
            }
            ItemStack left = ItemHandlerHelper.insertItemStacked(to, can, true);
            int moved = can.getCount() - left.getCount();
            if (moved <= 0) {
                continue;
            }
            ItemStack taken = s.extract(like, moved, false);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(to, taken, false);
            if (!rest.isEmpty()) {
                s.insert(rest, false); // (it changed its mind: back in)
            }
            return true;
        }
        return false;
    }

    /** One stack (of the filter's item, or anything it takes) from {@code from} into the singularity. */
    private boolean pullFrom(SingularityBlockEntity s, IItemHandler from) {
        for (int slot = 0; slot < from.getSlots(); slot++) {
            ItemStack there = from.getStackInSlot(slot);
            if (there.isEmpty() || (!filter.isEmpty() && !ItemStack.isSameItemSameComponents(there, filter)) || !s.accepts(there)) {
                continue;
            }
            ItemStack got = from.extractItem(slot, 64, false);
            if (got.isEmpty()) {
                continue;
            }
            ItemStack left = s.insert(got, false);
            if (!left.isEmpty()) {
                ItemStack back = from.insertItem(slot, left, false);
                if (!back.isEmpty() && level != null) {
                    Block.popResource(level, worldPosition, back);
                }
            }
            return true;
        }
        return false;
    }

    // ---- saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!filter.isEmpty()) {
            tag.put("filter", filter.save(registries));
        }
        tag.putLong("count", count);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filter = tag.contains("filter") ? ItemStack.parseOptional(registries, tag.getCompound("filter")) : ItemStack.EMPTY;
        count = tag.contains("count") ? tag.getLong("count") : -1;
    }
}
