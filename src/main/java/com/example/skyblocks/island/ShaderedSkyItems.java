package com.example.skyblocks.island;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Shadered 1.3.7 - 1.4.2 sky blocks (one block per sky) that can be an Island Painting's background. Works on both
 * sides: it only looks at the item's id; the client turns the result into Shadered's sky (client/island/ShaderedSkies13).
 */
public final class ShaderedSkyItems {
    /** Shadered block id -> the name of its sky's queue in Shadered's SkyblockRenderer ("spaceData"...). */
    private static final java.util.Map<String, String> SKIES = java.util.Map.of(
            "space_block", "spaceData",
            "stormy_block", "stormyData",
            "ocean_block", "oceanData",
            "eclipse_block", "eclipseData",
            "iridia_block", "iridiaData",
            "forest_block", "forestData",
            "light_block", "lightData");

    /** The sky queue's name for a Shadered sky block item, or null if it isn't one. */
    @Nullable
    public static String skyField(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && "shadered".equals(id.getNamespace()) ? SKIES.get(id.getPath()) : null;
    }

    public static boolean isSkyBlock(ItemStack stack) {
        return skyField(stack) != null;
    }

    private ShaderedSkyItems() {}
}
