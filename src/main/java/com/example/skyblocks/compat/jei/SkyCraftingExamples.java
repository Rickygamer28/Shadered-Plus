package com.example.skyblocks.compat.jei;

import com.example.skyblocks.ModFluids;
import com.example.skyblocks.ModItems;
import com.example.skyblocks.ModSkyShapes;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SkyBrushItem;
import com.example.skyblocks.SkyLiquidBucketRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * The crafting recipes made in code (JEI can't show those by itself), written out for each sky as plain ones it can:
 * a sky liquid bucket (a bucket and the sky block), the sky's shapes (stairs, slabs, walls, panes, carpet, button), the
 * sky brush dipped in it, and things given its glow (elytra, arrows, an amethyst shard - a sky crystal).
 */
final class SkyCraftingExamples {
    private SkyCraftingExamples() {}

    static List<RecipeHolder<CraftingRecipe>> all() {
        List<RecipeHolder<CraftingRecipe>> out = new ArrayList<>();
        for (var item : ModItems.SKY_ITEMS) {
            ItemStack sky = new ItemStack(item.get());
            var type = SkyLiquidBucketRecipe.skyOf(sky);
            if (type == null) {
                continue;
            }
            String id = type.name().toLowerCase(java.util.Locale.ROOT);
            Ingredient s = Ingredient.of(sky);
            ModFluids.SkyLiquid liquid = ModFluids.of(type);
            if (liquid != null) {
                out.add(shapeless("bucket/" + id, new ItemStack(liquid.bucket.get()), s, Ingredient.of(Items.BUCKET)));
            }
            ModSkyShapes.Shapes shapes = ModSkyShapes.of(type);
            if (shapes != null) {
                Map<Character, Ingredient> k = Map.of('S', s, 'P', Ingredient.of(Items.GLASS_PANE), 'B', Ingredient.of(Items.STONE_BUTTON));
                out.add(shaped("stairs/" + id, shape(shapes, ModSkyShapes.Kind.STAIRS, 4), k, "S  ", "SS ", "SSS"));
                out.add(shaped("slab/" + id, shape(shapes, ModSkyShapes.Kind.SLAB, 6), k, "SSS"));
                out.add(shaped("wall/" + id, shape(shapes, ModSkyShapes.Kind.WALL, 6), k, "SSS", "SSS"));
                out.add(shaped("pane/" + id, shape(shapes, ModSkyShapes.Kind.PANE, 8), k, "PPP", "PSP", "PPP"));
                out.add(shaped("carpet/" + id, shape(shapes, ModSkyShapes.Kind.CARPET, 3), k, "SS"));
                out.add(shapeless("button/" + id, shape(shapes, ModSkyShapes.Kind.BUTTON, 1), s, Ingredient.of(Items.STONE_BUTTON)));
            }
            ItemStack brush = new ItemStack(ModItems.SKY_BRUSH.get());
            out.add(shapeless("brush/" + id, SkyBrushItem.with(brush, type), Ingredient.of(brush), s));
            ItemStack crystal = new ItemStack(ModItems.SKY_CRYSTAL.get());
            crystal.set(com.example.skyblocks.ModComponents.SKY_GLOW.get(), type.name());
            out.add(shapeless("glow_crystal/" + id, crystal, Ingredient.of(Items.AMETHYST_SHARD), s));
            ItemStack elytra = new ItemStack(Items.ELYTRA);
            elytra.set(com.example.skyblocks.ModComponents.SKY_GLOW.get(), type.name());
            out.add(shapeless("glow_elytra/" + id, elytra, Ingredient.of(Items.ELYTRA), s));
        }
        return out;
    }

    private static ItemStack shape(ModSkyShapes.Shapes shapes, ModSkyShapes.Kind kind, int count) {
        return new ItemStack(shapes.items.get(kind).get(), count);
    }

    private static RecipeHolder<CraftingRecipe> shapeless(String id, ItemStack result, Ingredient... in) {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(List.of(in));
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "jei/" + id),
                new ShapelessRecipe("", CraftingBookCategory.MISC, result, list));
    }

    private static RecipeHolder<CraftingRecipe> shaped(String id, ItemStack result, Map<Character, Ingredient> key, String... rows) {
        Map<Character, Ingredient> used = new java.util.HashMap<>();
        for (String r : rows) {
            for (char c : r.toCharArray()) {
                if (c != ' ') {
                    used.put(c, key.get(c));
                }
            }
        }
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "jei/" + id),
                new ShapedRecipe("", CraftingBookCategory.MISC, ShapedRecipePattern.of(used, rows), result));
    }
}
