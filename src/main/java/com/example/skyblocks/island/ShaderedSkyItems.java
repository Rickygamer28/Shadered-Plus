package com.example.skyblocks.island;

import com.mojang.serialization.Codec;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the sky of a Shadered sky block item: its {@code shadered:skyblock_data} component, {blockType, pass} (the same
 * data our recipes give our sky block items). Looked up by id, so it doesn't depend on Shadered's internal classes.
 */
public final class ShaderedSkyItems {
    private static final ResourceLocation COMPONENT = ResourceLocation.fromNamespaceAndPath("shadered", "skyblock_data");

    public record Sky(SkyblockType type, SkyblockPass pass) {}

    @Nullable
    private static DataComponentType<?> component() {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.get(COMPONENT);
    }

    /** Whether the item is a Shadered sky block (has a sky). */
    public static boolean isSkyBlock(ItemStack stack) {
        DataComponentType<?> type = component();
        return type != null && !stack.isEmpty() && stack.has(type);
    }

    /** The item's sky, or null if it has none (or it can't be read). */
    @Nullable
    public static Sky sky(ItemStack stack) {
        DataComponentType<?> type = component();
        if (type == null || stack.isEmpty()) {
            return null;
        }
        Tag tag = encode(stack, type);
        if (!(tag instanceof CompoundTag data)) {
            return null;
        }
        try {
            SkyblockType sky = SkyblockType.valueOf(data.getString("blockType"));
            SkyblockPass pass = data.contains("pass") ? SkyblockPass.valueOf(data.getString("pass")) : SkyblockPass.NORMAL;
            return new Sky(sky, pass);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static <T> Tag encode(ItemStack stack, DataComponentType<T> type) {
        T value = stack.get(type);
        Codec<T> codec = type.codec();
        if (value == null || codec == null) {
            return null;
        }
        return codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
    }

    private ShaderedSkyItems() {}
}
