package com.example.skyblocks;

import com.example.skyblocks.island.IslandData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A weather globe: the Sky Island put in it (shown inside, in place of the little house), and when it was last shaken
 * (a flurry of snow inside for a few seconds).
 */
public class WeatherGlobeBlockEntity extends BlockEntity {
    private ItemStack island = ItemStack.EMPTY;
    private long shakenAt = Long.MIN_VALUE / 2;
    /** Client only: the decoded island and its model (client/island/IslandInGlobe). */
    @Nullable
    public Object clientView;

    public WeatherGlobeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WEATHER_GLOBE.get(), pos, state);
    }

    public ItemStack islandItem() {
        return island;
    }

    @Nullable
    public IslandData island() {
        return island.isEmpty() ? null : island.get(ModComponents.ISLAND.get());
    }

    public long shakenAt() {
        return shakenAt;
    }

    public void shake() {
        shakenAt = level != null ? level.getGameTime() : 0L;
        changed();
    }

    /** Puts a Sky Island in (empty: takes it out); gives back the one that was in. */
    public ItemStack swapIsland(ItemStack stack) {
        ItemStack old = island;
        island = stack.copyWithCount(stack.isEmpty() ? 0 : 1);
        changed();
        return old;
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
        if (!island.isEmpty()) {
            tag.put("island", island.save(registries));
        }
        tag.putBoolean("has_island", !island.isEmpty()); // (always something: an emptied globe reaches clients)
        tag.putLong("shaken", shakenAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStack loaded = tag.contains("island") ? ItemStack.parse(registries, tag.getCompound("island")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        if (!ItemStack.matches(loaded, island)) {
            island = loaded;
        }
        shakenAt = tag.contains("shaken") ? tag.getLong("shaken") : Long.MIN_VALUE / 2;
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

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (clientView instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // (nothing to do)
            }
            clientView = null;
        }
    }
}
