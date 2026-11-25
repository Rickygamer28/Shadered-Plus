package com.example.skyblocks.compat.jei;

import com.example.skyblocks.ModItems;
import com.example.skyblocks.ParticleAcceleratorBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The particle accelerator in JEI: its collision recipes (smash this, get that, from a ring this big up) and, for each
 * ring size, what anything else might make, with the chance of each.
 */
public class AcceleratorCategory implements IRecipeCategory<AcceleratorCategory.Page> {
    public static final RecipeType<Page> TYPE = RecipeType.create("skyblocks", "particle_accelerator", Page.class);
    private static final int WIDTH = 160, HEIGHT = 70;
    private static final String[] SIZES = {"none", "small", "medium", "large"};

    /** A page: one recipe (in, out), or a ring size's random loot (in empty; out with their chances). */
    public record Page(ItemStack in, List<ItemStack> out, List<Float> chances, int size) {}

    private final IDrawable icon;

    public AcceleratorCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(ModItems.PARTICLE_ACCELERATOR.get()));
    }

    /** Every page: each recipe, then each size's loot. */
    public static List<Page> pages() {
        List<Page> out = new ArrayList<>();
        for (ParticleAcceleratorBlockEntity.Smash s : ParticleAcceleratorBlockEntity.SMASHES) {
            out.add(new Page(new ItemStack(s.in()), List.of(new ItemStack(s.out(), s.count())), List.of(), s.size()));
        }
        for (int size = 1; size <= 3; size++) {
            List<ItemStack> items = new ArrayList<>();
            List<Float> chances = new ArrayList<>();
            for (Map.Entry<ItemStack, Float> e : ParticleAcceleratorBlockEntity.chances(size)) {
                items.add(e.getKey());
                chances.add(e.getValue());
            }
            out.add(new Page(ItemStack.EMPTY, items, chances, size));
        }
        return out;
    }

    @Override
    public RecipeType<Page> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.skyblocks.particle_accelerator");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    public int getWidth() {
        return WIDTH;
    }

    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Page p, IFocusGroup focuses) {
        if (!p.in().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 22, 22).addItemStack(p.in());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 118, 22).addItemStack(p.out().get(0));
            builder.addSlot(RecipeIngredientRole.CATALYST, 70, 40).addItemStack(new ItemStack(ModItems.PARTICLE_ACCELERATOR.get()));
            return;
        }
        for (int i = 0; i < p.out().size(); i++) {
            final float chance = p.chances().get(i);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 2 + (i % 8) * 20, 14 + (i / 8) * 20).addItemStack(p.out().get(i))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.skyblocks.accelerator.chance",
                            String.format(Locale.ROOT, "%.1f", chance * 100)).withStyle(ChatFormatting.GOLD)));
        }
    }

    @Override
    public void draw(Page p, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        Component ring = Component.translatable("jei.skyblocks.accelerator.ring",
                Component.translatable("screen.skyblocks.machine.size." + SIZES[p.size()]));
        if (!p.in().isEmpty()) {
            g.drawString(font, Component.translatable("jei.skyblocks.accelerator.smash"), 0, 0, 0xFF404040, false);
            // the two beams meeting: an arrow in, a burst in the middle, an arrow out
            g.fill(44, 29, 66, 31, 0xFF4FB8C8);
            g.fill(96, 29, 114, 31, 0xFFB050C0);
            g.fill(76, 22, 86, 38, 0x40FFFFFF);
            g.fill(78, 20, 84, 40, 0x60FFFFFF);
            g.drawCenteredString(font, "*", 81, 25, 0xFFFFFFFF);
            g.drawString(font, ring, 0, HEIGHT - 9, 0xFF606060, false);
        } else {
            g.drawString(font, Component.translatable("jei.skyblocks.accelerator.random", Component.translatable("screen.skyblocks.machine.size." + SIZES[p.size()])),
                    0, 0, 0xFF404040, false);
            g.drawString(font, Component.translatable(p.size() == 3 ? "jei.skyblocks.accelerator.rolls_two" : p.size() == 2 ? "jei.skyblocks.accelerator.rolls_some"
                    : "jei.skyblocks.accelerator.rolls_one"), 0, HEIGHT - 9, 0xFF606060, false);
        }
    }
}
