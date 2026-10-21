package com.example.skyblocks;

import com.example.skyblocks.island.ShaderedSkyItems;
import com.noodlegamer76.shadered.client.util.SkyblockRegistry;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.item.SkyblockItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Any layout: one empty Bucket + one Shadered sky block (any sky, any filter) = that sky's liquid bucket. */
public class SkyLiquidBucketRecipe extends CustomRecipe {
    public SkyLiquidBucketRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        return result(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        ModFluids.SkyLiquid liquid = result(input);
        return liquid == null ? ItemStack.EMPTY : new ItemStack(liquid.bucket.get());
    }

    private static ModFluids.SkyLiquid result(CraftingContainer input) {
        boolean bucket = false;
        SkyblockType sky = null;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.BUCKET) && !bucket) {
                bucket = true;
            } else if (sky == null && (sky = skyOf(stack)) != null) {
                // the sky block
            } else {
                return null; // anything else, or more than one of them
            }
        }
        return bucket && sky != null ? ModFluids.of(sky) : null;
    }

    /** The sky of a Shadered sky block item (its data, or the kind of sky item it is), or null. */
    public static SkyblockType skyOf(ItemStack stack) {
        ShaderedSkyItems.Sky sky = ShaderedSkyItems.sky(stack);
        if (sky != null) {
            return sky.type();
        }
        if (stack.getItem() instanceof SkyblockItem item) {
            try {
                return SkyblockRegistry.getType(item.getItemType());
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModFluids.BUCKET_RECIPE.get();
    }
}
