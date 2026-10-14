package com.example.skyblocks.island;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the sky of a Shadered sky block item: Shadered keeps it on the item as BlockEntityTag {blockType, pass}
 * (the same data our sky block items get).
 */
public final class ShaderedSkyItems {
    public record Sky(SkyblockType type, SkyblockPass pass) {}

    /** Whether the item is a Shadered sky block (has a sky). */
    public static boolean isSkyBlock(ItemStack stack) {
        return sky(stack) != null;
    }

    /** The item's sky, or null if it has none (or it can't be read). */
    @Nullable
    public static Sky sky(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CompoundTag data = stack.getTagElement("BlockEntityTag");
        if (data == null || !data.contains("blockType")) {
            return null;
        }
        try {
            SkyblockType sky = SkyblockType.valueOf(data.getString("blockType"));
            SkyblockPass pass = data.contains("pass") && !data.getString("pass").isEmpty()
                    ? SkyblockPass.valueOf(data.getString("pass")) : SkyblockPass.NORMAL;
            return new Sky(sky, pass);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ShaderedSkyItems() {}
}
