package com.example.skyblocks.compat.jei;

import com.example.skyblocks.ModItems;
import com.example.skyblocks.Structures;
import com.example.skyblocks.client.StructurePreview;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A structure's JEI page: it turning (hover it: how it's built), what it takes on the right, what it makes under that. */
public class StructureCategory implements IRecipeCategory<Structures.Structure> {
    private static final int WIDTH = 168, HEIGHT = 124, VIEW = 112;
    private final IDrawable icon;

    public StructureCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(ModItems.CONTAINMENT_FRAME.get()));
    }

    @Override
    public RecipeType<Structures.Structure> getRecipeType() {
        return ShaderedJeiPlugin.STRUCTURES;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("guide.skyblocks.chapter.structures");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    // (the page's size: as getWidth / getHeight, or a blank background in JEIs that still ask for one)
    public int getWidth() {
        return WIDTH;
    }

    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Structures.Structure s, IFocusGroup focuses) {
        List<ItemStack> needs = s.needs();
        for (int i = 0; i < needs.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, VIEW + 14, 14 + i * 20).addItemStack(needs.get(i));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, VIEW + 14, HEIGHT - 26).addItemStack(s.result());
    }

    @Override
    public void draw(Structures.Structure s, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable("guide.skyblocks.structure." + s.id() + ".name").append(" (" + s.size() + "x" + s.size() + "x" + s.size() + ")"),
                0, 0, 0xFF404040, false);
        g.fill(0, 11, VIEW, HEIGHT, 0x30000000);
        float yaw = (System.currentTimeMillis() % 20000L) / 20000.0F * 360.0F;
        StructurePreview.draw(g, s, VIEW / 2.0F, 11 + (HEIGHT - 11) / 2.0F, VIEW - 8, yaw, 28.0F);
        g.drawString(font, "↓", VIEW + 19, HEIGHT - 38, 0xFF404040, false);
    }

    @Override
    public void getTooltip(mezz.jei.api.gui.builder.ITooltipBuilder tooltip, Structures.Structure s, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseX < VIEW && mouseY > 11) { // (over the picture: how it's built)
            tooltip.addAll(Minecraft.getInstance().font.getSplitter().splitLines(
                    Component.translatable("guide.skyblocks.structure." + s.id()), 200, net.minecraft.network.chat.Style.EMPTY));
        }
    }
}
