package com.example.skyblocks.island;

import com.example.skyblocks.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
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

    /** Island Painting: how the island is turned, in steps of 45 degrees (0-7). */
    private int rotation;

    public int rotation() {
        return rotation;
    }

    void rotate() {
        rotation = (rotation + 1) % 8;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Island Painting: the Shadered sky block used as the room's background (empty = painted sky). */
    private ItemStack background = ItemStack.EMPTY;

    public ItemStack background() {
        return background;
    }

    void setBackground(ItemStack background) {
        this.background = background.copy();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The name the Sky Island item was given in an anvil (shown on a plaque), or null. */
    @Nullable
    private Component name;

    @Nullable
    public Component name() {
        return name;
    }

    /** Island Painting: puts an island in, with its item's name (or takes it out with null). */
    void setIsland(@Nullable IslandData island, @Nullable Component name) {
        this.island = island;
        this.name = island != null ? name : null;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }


    /** Pedestal: the name of the item it was placed from (SkyIslandBlock.setPlacedBy). */
    void setName(@Nullable Component name) {
        this.name = name;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rotation = Math.floorMod(tag.getInt("rotation"), 8);
        background = tag.contains("background")
                ? ItemStack.of(tag.getCompound("background")) : ItemStack.EMPTY;
        name = tag.contains("CustomName") ? parseName(tag.getString("CustomName")) : null;
        IslandData loaded = tag.contains("island") ? new IslandData(tag.getCompound("island")) : null;
        if (loaded == null || !loaded.equals(island)) {
            island = loaded; // keep the same instance when nothing changed (the client renderer compares it)
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (island != null) {
            tag.put("island", island.tag());
        }
        // Always write something: clients ignore an empty update, so without this, taking the island out of a
        // painting would never reach them and it would still be shown.
        tag.putBoolean("has_island", island != null);
        tag.putInt("rotation", rotation);
        if (name != null) {
            tag.putString("CustomName", Component.Serializer.toJson(name));
        }
        if (!background.isEmpty()) {
            tag.put("background", background.save(new CompoundTag()));
        }
    }

    @Nullable
    private static Component parseName(String json) {
        try {
            return Component.Serializer.fromJson(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** What the renderer draws: the island above the pedestal, or a (joined) painting and the room behind it. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        BlockState state = getBlockState();
        if (state.getBlock() instanceof IslandPaintingBlock && level != null) {
            // a joined painting spans width x height blocks; the room goes into the wall behind it
            int w = IslandPaintingBlock.width(level, pos, state);
            int h = IslandPaintingBlock.height(level, pos, state);
            BlockPos far = pos.relative(state.getValue(IslandPaintingBlock.FACING).getClockWise(), w - 1).above(h - 1);
            return new net.minecraft.world.phys.AABB(pos).minmax(new net.minecraft.world.phys.AABB(far)).inflate(Math.max(w, h) + 1.0);
        }
        double half = displayWidth() / 2.0 + 0.5;
        double top = 0.35 + 1.0 + (island != null && island.size() > 0 ? displayWidth() * (double) island.height() / island.size() : 1.0);
        return new net.minecraft.world.phys.AABB(pos.getX() + 0.5 - half, pos.getY(), pos.getZ() + 0.5 - half,
                pos.getX() + 0.5 + half, pos.getY() + top, pos.getZ() + 0.5 + half);
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
