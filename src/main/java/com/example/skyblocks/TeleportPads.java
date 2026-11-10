package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Every teleport pad in a dimension: its colour (its network), its name, who placed it and whether only they may use it
 * - to list where one can send you without loading the places they are. Kept with the dimension's data.
 */
public class TeleportPads extends SavedData {
    public record Pad(int colour, String name, @Nullable UUID owner, boolean priv) {
        public boolean usableBy(@Nullable Player p) {
            return !priv || owner == null || p != null && (owner.equals(p.getUUID()) || p.hasPermissions(2));
        }
    }

    private final Map<Long, Pad> pads = new HashMap<>();

    public static TeleportPads get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TeleportPads::new, TeleportPads::load, null), "skyblocks_teleport_pads");
    }

    private static TeleportPads load(CompoundTag tag, HolderLookup.Provider registries) {
        TeleportPads t = new TeleportPads();
        ListTag list = tag.getList("pads", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            t.pads.put(c.getLong("pos"), new Pad(c.getInt("colour"), c.getString("name"), c.hasUUID("owner") ? c.getUUID("owner") : null,
                    c.getBoolean("private")));
        }
        return t;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Pad> e : pads.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", e.getKey());
            c.putInt("colour", e.getValue().colour());
            c.putString("name", e.getValue().name());
            if (e.getValue().owner() != null) c.putUUID("owner", e.getValue().owner());
            c.putBoolean("private", e.getValue().priv());
            list.add(c);
        }
        tag.put("pads", list);
        return tag;
    }

    public void put(BlockPos pos, Pad pad) {
        Pad had = pads.put(pos.asLong(), pad);
        if (!pad.equals(had)) setDirty();
    }

    public void remove(BlockPos pos) {
        if (pads.remove(pos.asLong()) != null) setDirty();
    }

    /** Still a pad there, of this colour? (forgotten if not) */
    private boolean valid(ServerLevel level, long at, int colour) {
        BlockPos p = BlockPos.of(at);
        var s = level.getBlockState(p);
        if (!(s.getBlock() instanceof TeleportPadBlock)) {
            remove(p);
            return false;
        }
        if (level.getBlockEntity(p) instanceof TeleportPadBlockEntity be) {
            put(p, be.pad());
            return be.pad().colour() == colour;
        }
        return false;
    }

    /** The other pads on this one's network that {@code who} may go to, nearest first. */
    public List<Map.Entry<BlockPos, Pad>> network(ServerLevel level, BlockPos from, int colour, @Nullable Player who) {
        List<Map.Entry<BlockPos, Pad>> out = new ArrayList<>();
        for (Map.Entry<Long, Pad> e : new ArrayList<>(pads.entrySet())) {
            if (e.getKey() == from.asLong() || e.getValue().colour() != colour || !e.getValue().usableBy(who)) continue;
            out.add(Map.entry(BlockPos.of(e.getKey()), e.getValue()));
        }
        out.sort((a, b) -> Double.compare(a.getKey().distSqr(from), b.getKey().distSqr(from)));
        return out;
    }

    /** Whether {@code to} is a pad on that network that {@code who} may go to. */
    public boolean reachable(ServerLevel level, BlockPos to, int colour, @Nullable Player who) {
        Pad p = pads.get(to.asLong());
        return p != null && p.usableBy(who) && valid(level, to.asLong(), colour);
    }

    /** The next pad of this one's colour after it (in a fixed order, round to the first), still there; null if none. */
    @Nullable
    public BlockPos next(ServerLevel level, BlockPos from, int colour, @Nullable Player who) {
        List<Long> same = new ArrayList<>();
        for (Map.Entry<Long, Pad> e : pads.entrySet()) {
            if (e.getValue().colour() == colour && (e.getKey() == from.asLong() || e.getValue().usableBy(who))) same.add(e.getKey());
        }
        if (!same.contains(from.asLong())) same.add(from.asLong());
        same.sort(Long::compare);
        int at = same.indexOf(from.asLong());
        for (int k = 1; k < same.size(); k++) {
            long p = same.get((at + k) % same.size());
            if (valid(level, p, colour)) return BlockPos.of(p);
        }
        return null;
    }
}
