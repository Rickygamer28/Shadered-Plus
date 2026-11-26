package com.example.skyblocks.compat.jei;

import com.example.skyblocks.SingularityMenu;
import com.example.skyblocks.SingularityPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * JEI's + on a crafting recipe, at a singularity opened from a crafting frame: lays the recipe in its grid, taking what
 * it needs from what the singularity holds (or your inventory) - shift for as many as it can. What's missing is marked.
 */
final class CraftingFrameTransfer implements IRecipeTransferHandler<SingularityMenu, RecipeHolder<CraftingRecipe>> {
    private final IRecipeTransferHandlerHelper helper;

    CraftingFrameTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends SingularityMenu> getContainerClass() {
        return SingularityMenu.class;
    }

    @Override
    public Optional<MenuType<SingularityMenu>> getMenuType() {
        return Optional.of(SingularityMenu.TYPE.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(SingularityMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (!menu.crafting) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.skyblocks.crafting_frame.needed"));
        }
        // (what's missing: in neither what it holds nor your inventory)
        List<IRecipeSlotView> missing = new ArrayList<>();
        for (IRecipeSlotView view : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<ItemStack> options = view.getItemStacks().toList();
            if (options.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (ItemStack want : options) {
                for (int i = 0; i < menu.items.size() && !found; i++) {
                    found = ItemStack.isSameItemSameComponents(menu.items.get(i), want) && menu.counts.get(i) > 0;
                }
                for (ItemStack s : player.getInventory().items) {
                    found |= !s.isEmpty() && ItemStack.isSameItemSameComponents(s, want);
                }
                if (found) {
                    break;
                }
            }
            if (!found) {
                missing.add(view);
            }
        }
        if (!missing.isEmpty()) {
            return helper.createUserErrorForMissingSlots(Component.translatable("jei.skyblocks.crafting_frame.missing"), missing);
        }
        if (doTransfer) {
            PacketDistributor.sendToServer(new SingularityPayloads.Action(menu.containerId, SingularityMenu.FILL_RECIPE, ItemStack.EMPTY,
                    maxTransfer ? 1 : 0, recipe.id().toString()));
        }
        return null;
    }
}
