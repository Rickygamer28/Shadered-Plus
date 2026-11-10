package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** Every teleport pad in a dimension and its colour - to find where one leads. Kept with the dimension's data. */
public class TeleportPads extends SavedData {
    private final Map<Long, Integer> pads = new HashMap<>();

    public static TeleportPads get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TeleportPads::new, TeleportPads::load, null), "skyblocks_teleport_pads");
    }

    private static TeleportPads load(CompoundTag tag, HolderLookup.Provider registries) {
        TeleportPads t = new TeleportPads();
        long[] p = tag.getLongArray("pos");
        int[] c = tag.getIntArray("colour");
        for (int i = 0; i < Math.min(p.length, c.length); i++) {
            t.pads.put(p[i], c[i]);
        }
        return t;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] p = new long[pads.size()];
        int[] c = new int[pads.size()];
        int i = 0;
        for (Map.Entry<Long, Integer> e : pads.entrySet()) {
            p[i] = e.getKey();
            c[i++] = e.getValue();
        }
        tag.putLongArray("pos", p);
        tag.putIntArray("colour", c);
        return tag;
    }

    public void put(BlockPos pos, int colour) {
        Integer had = pads.put(pos.asLong(), colour);
        if (had == null || had != colour) setDirty();
    }

    public void remove(BlockPos pos) {
        if (pads.remove(pos.asLong()) != null) setDirty();
    }

    /** The next pad of this one's colour after it (in a fixed order, round to the first), still there; null if none. */
    @Nullable
    public BlockPos next(ServerLevel level, BlockPos from, int colour) {
        put(from, colour);
        List<Long> same = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : pads.entrySet()) {
            if (e.getValue() == colour) same.add(e.getKey());
        }
        same.sort(Long::compare);
        int at = same.indexOf(from.asLong());
        for (int k = 1; k < same.size(); k++) {
            BlockPos p = BlockPos.of(same.get((at + k) % same.size()));
            var s = level.getBlockState(p);
            if (s.getBlock() instanceof TeleportPadBlock && s.getValue(TeleportPadBlock.COLOR) == colour) {
                return p;
            }
            if (!(s.getBlock() instanceof TeleportPadBlock)) {
                remove(p); // (gone)
            } else {
                put(p, s.getValue(TeleportPadBlock.COLOR));
            }
        }
        return null;
    }
}
