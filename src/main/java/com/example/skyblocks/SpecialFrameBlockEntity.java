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
 * A special containment frame's own things: a port's filter (only this item moves; none: any), an upgrade frame's
 * upgrades (up to {@link #MAX_UPGRADES} of one kind).
 */
public class SpecialFrameBlockEntity extends ContainmentFrameBlockEntity {
    public static final int MAX_UPGRADES = 4;
    /** A port moves a stack every this many ticks (fewer with speed upgrades). */
    private static final int PORT_EVERY = 8;

    private ItemStack filter = ItemStack.EMPTY;
    private ItemStack upgrade = ItemStack.EMPTY;
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

    public ItemStack upgrade() {
        return upgrade;
    }

    /** Puts one of this upgrade in (the same kind as what's there, up to four); false if it can't. */
    public boolean addUpgrade(ItemStack stack) {
        if (!(stack.getItem() instanceof UpgradeItem)) {
            return false;
        }
        if (upgrade.isEmpty()) {
            upgrade = stack.copyWithCount(1);
        } else if (upgrade.is(stack.getItem()) && upgrade.getCount() < MAX_UPGRADES) {
            upgrade.grow(1);
        } else {
            return false;
        }
        sync();
        rescan();
        return true;
    }

    public ItemStack takeUpgrade() {
        ItemStack out = upgrade.split(1);
        sync();
        rescan();
        return out;
    }

    /** (Its singularity looks at its upgrades again now, not at its next check.) */
    private void rescan() {
        SingularityBlockEntity s = singularity();
        if (s != null) {
            s.scanFrames();
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
        SingularityBlockEntity s = be.singularity();
        if (s == null) {
            return;
        }
        int every = Math.max(1, PORT_EVERY >> s.speedLevel());
        if ((level.getGameTime() + pos.hashCode()) % every != 0) {
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
        if (!upgrade.isEmpty()) {
            tag.put("upgrade", upgrade.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filter = tag.contains("filter") ? ItemStack.parseOptional(registries, tag.getCompound("filter")) : ItemStack.EMPTY;
        upgrade = tag.contains("upgrade") ? ItemStack.parseOptional(registries, tag.getCompound("upgrade")) : ItemStack.EMPTY;
    }
}
