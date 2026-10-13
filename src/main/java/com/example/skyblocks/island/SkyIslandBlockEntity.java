package com.example.skyblocks.island;

import com.example.skyblocks.ModBlockEntities;
import com.example.skyblocks.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A placed Sky Island: holds the scanned island (from the item) and how wide to show it. */
public class SkyIslandBlockEntity extends BlockEntity {
    /** The miniature is always shown 1 block wide. */
    public static final int DISPLAY_WIDTH = 1;

    @Nullable
    private IslandData island;

    /** Client only: the decoded island, its baked model and mob figures (client/island/IslandView). */
    @Nullable
    public Object clientView;

    public SkyIslandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_ISLAND.get(), pos, state);
    }

    @Nullable
    public IslandData island() {
        return island;
    }

    public int displayWidth() {
        return DISPLAY_WIDTH;
    }


    // The island travels between the item and the block as the skyblocks:island component.
    @Override
    public void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        island = input.get(ModComponents.ISLAND.get());
    }

    @Override
    public void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (island != null) {
            builder.set(ModComponents.ISLAND.get(), island);
        }
    }

    /** The island is already on the item as its component; don't copy it into block_entity_data too. */
    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("island");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        IslandData loaded = tag.contains("island") ? new IslandData(tag.getCompound("island")) : null;
        if (loaded == null || !loaded.equals(island)) {
            island = loaded; // keep the same instance when nothing changed (the client renderer compares it)
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (island != null) {
            tag.put("island", island.tag());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Client: free the baked model when the block goes away or its chunk unloads. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide && clientView instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
            clientView = null;
        }
    }
}
