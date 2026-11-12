package com.example.skyblocks;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The map on a map table, and what the table shows of it (sent to clients, not saved): the map's colours, where it is
 * and its scale, the land's height under each pixel and the water's depth (MapHeights). Kept up to date every
 * couple of seconds.
 */
public class MapTableBlockEntity extends BlockEntity {
    private ItemStack map = ItemStack.EMPTY;
    /** What's shown (clients): null with no map, or nothing known of it yet. */
    public byte[] colors, top, depth;
    public int scale, centerX, centerZ;
    /** Changes whenever what's shown does (clients: build the land again). */
    public int version;
    private int shownHash;

    public MapTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAP_TABLE.get(), pos, state);
    }

    public ItemStack map() {
        return map;
    }

    public void setMap(ItemStack stack) {
        map = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        shownHash = 0;
        colors = top = depth = null;
        if (level instanceof ServerLevel server) {
            refresh(server);
        }
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MapTableBlockEntity be) {
        if (level instanceof ServerLevel server && !be.map.isEmpty() && (level.getGameTime() + pos.hashCode()) % 40 == 0 && be.refresh(server)) {
            be.sync();
        }
    }

    /** The map's colours and heights now; whether they changed. */
    private boolean refresh(ServerLevel level) {
        MapId id = MapHeights.id(map);
        MapItemSavedData data = id == null ? null : MapItem.getSavedData(id, level);
        if (data == null) {
            boolean had = colors != null;
            colors = top = depth = null;
            return had;
        }
        MapHeights heights = MapHeights.get(level.getServer());
        ServerLevel there = level.getServer().getLevel(data.dimension);
        if (there != null) {
            heights.sample(there, id.id(), data, 0, 127, 0, 127);
        }
        MapHeights.Map128 m = heights.of(id.id());
        int hash = Arrays.hashCode(data.colors) * 31 + Arrays.hashCode(m.top) * 17 + Arrays.hashCode(m.depth) * 5 + Arrays.hashCode(m.colour) * 3
                + data.scale * 7 + data.centerX * 13 + data.centerZ;
        if (hash == shownHash && colors != null) {
            return false;
        }
        shownHash = hash;
        colors = data.colors.clone();
        for (int i = 0; i < colors.length; i++) {
            if (m.colour[i] != 0) {
                colors[i] = m.colour[i]; // (the land as it is now, where it's been seen: the map's own colours may be old)
            }
        }
        top = MapHeights.topBytes(m.top);
        depth = m.depth.clone();
        scale = data.scale;
        centerX = data.centerX;
        centerZ = data.centerZ;
        version++;
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!map.isEmpty()) {
            tag.put("map", map.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        map = tag.contains("map") ? ItemStack.parseOptional(registries, tag.getCompound("map")) : ItemStack.EMPTY;
        if (tag.contains("shown")) { // (a client: what's shown)
            CompoundTag s = tag.getCompound("shown");
            colors = s.getByteArray("colors");
            top = s.getByteArray("top");
            depth = s.getByteArray("depth");
            scale = s.getInt("scale");
            centerX = s.getInt("cx");
            centerZ = s.getInt("cz");
            if (colors.length != 128 * 128 || top.length != 128 * 128 * 2 || depth.length != 128 * 128) {
                colors = top = depth = null;
            }
            version++;
        } else if (level != null && level.isClientSide) {
            colors = top = depth = null;
            version++;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        if (colors != null && !map.isEmpty()) {
            CompoundTag s = new CompoundTag();
            s.putByteArray("colors", colors);
            s.putByteArray("top", top);
            s.putByteArray("depth", depth);
            s.putInt("scale", scale);
            s.putInt("cx", centerX);
            s.putInt("cz", centerZ);
            tag.put("shown", s);
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && !map.isEmpty()) {
            refresh(server);
        }
    }
}
