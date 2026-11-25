package com.example.skyblocks.compat.jei;

import com.example.skyblocks.ModItems;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.Structures;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** JEI: the structures (things built of several blocks) as pages of their own - how each is built, what it takes. */
@JeiPlugin
public class ShaderedJeiPlugin implements IModPlugin {
    public static final RecipeType<Structures.Structure> STRUCTURES = RecipeType.create(SkyBlocksMod.MOD_ID, "structures", Structures.Structure.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new StructureCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new AcceleratorCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new StarForgeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(STRUCTURES, Structures.everySize());
        registration.addRecipes(AcceleratorCategory.TYPE, AcceleratorCategory.pages());
        registration.addRecipes(StarForgeCategory.TYPE, StarForgeCategory.pages());
        // (the crafting recipes made in code, written out for each sky)
        registration.addRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, SkyCraftingExamples.all());
    }

    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        // (JEI's own panels keep clear of the Shadered+ tab's chapter buttons)
        registration.addGuiContainerHandler(net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen>() {
                    @Override
                    public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
                        return com.example.skyblocks.client.CreativeChapterButtons.areas(screen);
                    }
                });
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.GUIDE_BOOK.get()), STRUCTURES);
        registration.addRecipeCatalyst(new ItemStack(ModItems.PARTICLE_ACCELERATOR.get()), AcceleratorCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.ACCELERATOR_FRAME.get()), AcceleratorCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.STAR_FORGE.get()), StarForgeCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModItems.FORGE_FRAME.get()), StarForgeCategory.TYPE);
    }
}
