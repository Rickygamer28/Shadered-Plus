package com.example.skyblocks;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Every photo taken in this world (CameraItem), kept with the world like maps: a photo item only holds its number.
 * Each picture is W x H, its own palette and squeezed (client/ArcadePicture's format).
 */
public class PhotoStore extends SavedData {
    public static final int W = 160, H = 120;
    /** Biggest picture accepted (bytes). */
    public static final int MAX_BYTES = 30000;

    private final Map<Integer, byte[]> photos = new HashMap<>();
    private int next = 1;

    public static PhotoStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(PhotoStore::new, PhotoStore::load, null), "skyblocks_photos");
    }

    private static PhotoStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PhotoStore s = new PhotoStore();
        s.next = Math.max(1, tag.getInt("next"));
        CompoundTag all = tag.getCompound("photos");
        for (String k : all.getAllKeys()) {
            try {
                s.photos.put(Integer.parseInt(k), all.getByteArray(k));
            } catch (NumberFormatException ignored) {
                // (not one of ours)
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next", next);
        CompoundTag all = new CompoundTag();
        photos.forEach((id, data) -> all.putByteArray(Integer.toString(id), data));
        tag.put("photos", all);
        return tag;
    }

    public int add(byte[] data) {
        int id = next++;
        photos.put(id, data);
        setDirty();
        return id;
    }

    @Nullable
    public byte[] get(int id) {
        return photos.get(id);
    }
}
