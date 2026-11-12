package com.example.skyblocks;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * How high the land is under each pixel of each map (a map only keeps colours): learned while players carry the map
 * about (as the map itself is drawn) and wherever the world's loaded round a map table. For the map table's 3D view.
 * Per map: the top block's height under each pixel (unknown: Short.MIN_VALUE), and over water how deep it is.
 */
public class MapHeights extends SavedData {
    public static final short UNKNOWN = Short.MIN_VALUE;

    public static final class Map128 {
        public final short[] top = new short[128 * 128];
        public final byte[] depth = new byte[128 * 128];
        /** The colour there now, as a map would draw it (0: not seen) - a map's own colours are only as new as when it was last held. */
        public final byte[] colour = new byte[128 * 128];

        Map128() {
            java.util.Arrays.fill(top, UNKNOWN);
        }
    }

    private final Map<Integer, Map128> maps = new HashMap<>();

    public static MapHeights get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(MapHeights::new, MapHeights::load, null),
                "skyblocks_map_heights");
    }

    private static MapHeights load(CompoundTag tag, HolderLookup.Provider registries) {
        MapHeights h = new MapHeights();
        CompoundTag all = tag.getCompound("maps");
        for (String k : all.getAllKeys()) {
            try {
                CompoundTag c = all.getCompound(k);
                Map128 m = new Map128();
                byte[] top = c.getByteArray("top"), depth = c.getByteArray("depth");
                if (top.length == 128 * 128 * 2) {
                    for (int i = 0; i < 128 * 128; i++) {
                        m.top[i] = (short) ((top[i * 2] & 255) << 8 | top[i * 2 + 1] & 255);
                    }
                }
                if (depth.length == 128 * 128) {
                    System.arraycopy(depth, 0, m.depth, 0, depth.length);
                }
                byte[] colour = c.getByteArray("colour");
                if (colour.length == 128 * 128) {
                    System.arraycopy(colour, 0, m.colour, 0, colour.length);
                }
                h.maps.put(Integer.parseInt(k), m);
            } catch (NumberFormatException ignored) {
                // (not one of ours)
            }
        }
        return h;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        maps.forEach((id, m) -> {
            CompoundTag c = new CompoundTag();
            c.putByteArray("top", topBytes(m.top));
            c.putByteArray("depth", m.depth);
            c.putByteArray("colour", m.colour);
            all.put(Integer.toString(id), c);
        });
        tag.put("maps", all);
        return tag;
    }

    static byte[] topBytes(short[] top) {
        byte[] b = new byte[top.length * 2];
        for (int i = 0; i < top.length; i++) {
            b[i * 2] = (byte) (top[i] >> 8);
            b[i * 2 + 1] = (byte) top[i];
        }
        return b;
    }

    public Map128 of(int mapId) {
        return maps.computeIfAbsent(mapId, k -> new Map128());
    }

    /**
     * Learns the heights under the map's pixels from px0..px1, pz0..pz1 (inclusive) wherever the world is loaded.
     * @return whether anything changed
     */
    public boolean sample(ServerLevel level, int mapId, MapItemSavedData data, int px0, int px1, int pz0, int pz1) {
        Map128 m = of(mapId);
        int step = 1 << data.scale;
        boolean changed = false;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int pz = Math.max(0, pz0); pz <= Math.min(127, pz1); pz++) {
            for (int px = Math.max(0, px0); px <= Math.min(127, px1); px++) {
                // (the middle of the pixel's square of the world, as the map draws it)
                int bx = (data.centerX / step + px - 64) * step + step / 2, bz = (data.centerZ / step + pz - 64) * step + step / 2;
                LevelChunk chunk = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
                if (chunk == null) {
                    continue;
                }
                // (as a map sees it: down through anything it doesn't draw - glass, air - to the first thing it does)
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx & 15, bz & 15);
                BlockState st = chunk.getBlockState(at.set(bx, top, bz));
                net.minecraft.world.level.material.MapColor mc = st.getMapColor(level, at);
                for (int down = 0; mc == net.minecraft.world.level.material.MapColor.NONE && down < 64 && top > level.getMinBuildHeight(); down++) {
                    top--;
                    st = chunk.getBlockState(at.set(bx, top, bz));
                    mc = st.getMapColor(level, at);
                }
                int depth = 0;
                if (st.getFluidState().is(FluidTags.WATER)) {
                    int floor = top;
                    while (floor > level.getMinBuildHeight() && top - floor < 255 && chunk.getBlockState(at.set(bx, floor - 1, bz)).getFluidState().is(FluidTags.WATER)) {
                        floor--;
                    }
                    depth = top - floor + 1;
                    mc = net.minecraft.world.level.material.MapColor.WATER;
                }
                byte colour = mc == net.minecraft.world.level.material.MapColor.NONE ? 0
                        : mc.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.NORMAL);
                int i = pz * 128 + px;
                if (m.top[i] != top || (m.depth[i] & 255) != depth || m.colour[i] != colour) {
                    m.top[i] = (short) top;
                    m.depth[i] = (byte) depth;
                    m.colour[i] = colour;
                    changed = true;
                }
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    @Nullable
    static MapId id(ItemStack stack) {
        return stack.is(Items.FILLED_MAP) ? stack.get(DataComponents.MAP_ID) : null;
    }

    /** Twice a second: the maps players hold learn the land round them (where the map's being drawn). */
    static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        MapHeights heights = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = p.getItemInHand(hand);
                MapId id = id(stack);
                if (id == null) {
                    continue;
                }
                MapItemSavedData data = MapItem.getSavedData(id, p.level());
                if (data == null || data.dimension != p.level().dimension()) {
                    continue;
                }
                if (heights == null) {
                    heights = get(server);
                }
                int step = 1 << data.scale;
                int px = (int) Math.floor((p.getX() - data.centerX) / step) + 64, pz = (int) Math.floor((p.getZ() - data.centerZ) / step) + 64;
                int r = Math.min(64, 128 / step);
                heights.sample(p.serverLevel(), id.id(), data, px - r, px + r, pz - r, pz + r);
            }
        }
    }
}
