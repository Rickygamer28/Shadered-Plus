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
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(STRUCTURES, Structures.all());
    }

    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        // (JEI's own panels keep clear of the chapter buttons)
        registration.addGenericGuiContainerHandler(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>>() {
                    @Override
                    public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
                        return JeiChapterButtons.areas(screen);
                    }
                });
    }

    private static boolean listening;

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        JeiChapterButtons.setRuntime(runtime);
        if (!listening) {
            listening = true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(JeiChapterButtons.class);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.GUIDE_BOOK.get()), STRUCTURES);
    }
}
