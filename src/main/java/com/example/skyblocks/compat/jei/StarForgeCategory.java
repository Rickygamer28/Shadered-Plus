package com.example.skyblocks.compat.jei;

import com.example.skyblocks.ModItems;
import com.example.skyblocks.StarForgeBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The star forge in JEI: its fuels (the colour each burns, how long, the energy), its alloys and transmutations (from
 * which colour up), and its smelting (any furnace recipe, a whole stack at once).
 */
public class StarForgeCategory implements IRecipeCategory<StarForgeCategory.Page> {
    public static final RecipeType<Page> TYPE = RecipeType.create("skyblocks", "star_forge", Page.class);
    private static final int WIDTH = 160, HEIGHT = 56;
    private static final int[] COLOURS = {0xFFD04020, 0xFFC08A10, 0xFF2A70D0, 0xFF8080A0};
    private static final int[] MAKES = {80, 200, 500, 1200};

    /** A page: 0 a fuel, 1 an alloy, 2 a transmutation, 3 smelting; what goes in, what comes out, the star's colour (at least). */
    public record Page(int kind, List<ItemStack> in, ItemStack out, int colour, int ticks) {}

    private final IDrawable icon;

    public StarForgeCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(ModItems.STAR_FORGE.get()));
    }

    public static List<Page> pages() {
        List<Page> out = new ArrayList<>();
        for (StarForgeBlockEntity.Alloy a : StarForgeBlockEntity.ALLOYS) {
            out.add(new Page(1, List.of(new ItemStack(a.a()), new ItemStack(a.b())), new ItemStack(a.out().get()), a.colour(), 0));
        }
        for (StarForgeBlockEntity.Transmute t : StarForgeBlockEntity.TRANSMUTES) {
            out.add(new Page(2, List.of(new ItemStack(t.from(), t.need())), new ItemStack(t.to()), t.colour(), 0));
        }
        List<Map.Entry<Item, StarForgeBlockEntity.Fuel>> fuels = new ArrayList<>(StarForgeBlockEntity.FUELS.entrySet());
        fuels.sort((x, y) -> x.getValue().colour() != y.getValue().colour() ? Integer.compare(x.getValue().colour(), y.getValue().colour())
                : Integer.compare(x.getValue().ticks(), y.getValue().ticks()));
        for (Map.Entry<Item, StarForgeBlockEntity.Fuel> e : fuels) {
            out.add(new Page(0, List.of(new ItemStack(e.getKey())), ItemStack.EMPTY, e.getValue().colour(), e.getValue().ticks()));
        }
        out.add(new Page(3, List.of(new ItemStack(Items.RAW_IRON, 64)), new ItemStack(Items.IRON_INGOT, 64), 0, 0));
        return out;
    }

    @Override
    public RecipeType<Page> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.skyblocks.star_forge");
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
        if (p.kind() == 0) {
            builder.addSlot(RecipeIngredientRole.INPUT, 8, 18).addItemStack(p.in().get(0));
            return;
        }
        for (int i = 0; i < p.in().size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 8 + i * 20, 18).addItemStack(p.in().get(i));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 120, 18).addItemStack(p.out());
        builder.addSlot(RecipeIngredientRole.CATALYST, 140, 38).addItemStack(new ItemStack(ModItems.STAR_FORGE.get()));
    }

    @Override
    public void draw(Page p, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        Component colour = Component.translatable("screen.skyblocks.star_forge.colour." + StarForgeBlockEntity.CLASSES[p.colour()]);
        switch (p.kind()) {
            case 0 -> {
                g.drawString(font, Component.translatable("jei.skyblocks.forge.fuel"), 0, 0, 0xFF404040, false);
                g.fill(34, 18, 50, 34, COLOURS[p.colour()]);
                g.drawString(font, Component.translatable("jei.skyblocks.forge.burns", colour), 56, 16, COLOURS[p.colour()], false);
                g.drawString(font, Component.translatable("jei.skyblocks.forge.time", p.ticks() / 20, MAKES[p.colour()]), 56, 27, 0xFF606060, false);
            }
            case 3 -> {
                g.drawString(font, Component.translatable("jei.skyblocks.forge.smelting"), 0, 0, 0xFF404040, false);
                arrow(g);
                g.drawString(font, Component.translatable("jei.skyblocks.forge.smelting_note"), 0, HEIGHT - 9, 0xFF606060, false);
            }
            default -> {
                g.drawString(font, Component.translatable(p.kind() == 1 ? "jei.skyblocks.forge.alloy" : "jei.skyblocks.forge.transmute"), 0, 0, 0xFF404040, false);
                arrow(g);
                g.drawString(font, Component.translatable("jei.skyblocks.forge.needs", colour), 0, HEIGHT - 9, COLOURS[p.colour()], false);
            }
        }
    }

    private static void arrow(GuiGraphics g) {
        g.fill(60, 25, 108, 27, 0xFF8A8A8A);
        for (int i = 0; i < 5; i++) {
            g.fill(104 + i, 21 + i, 106 + i, 31 - i, 0xFF8A8A8A);
        }
    }
}
