package com.example.skyblocks.client;

import com.example.skyblocks.GuideChapters;
import com.example.skyblocks.ModItems;
import com.example.skyblocks.Structures;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * The Shadered+ guide book: a tab for each chapter (as in the creative tab), the chapter's blocks listed on the left
 * page, the chosen one on the right - what it does, how to use it (its tooltip), how to make it. A first page to begin
 * with, and the structures (things built of several blocks) each with a picture turning and what it takes.
 */
public class GuideScreen extends Screen {
    private static final int BW = 300, BH = 192, PAGE_W = 130, ROW = 18;
    private static final int INK = 0xFF3B2A1A, FAINT = 0xFF8A7458, PAPER = 0xFFF4EAD3, PAPER_DARK = 0xFFE6D8B8;

    private enum Kind { START, ITEMS, STRUCTURES }

    private record Tab(Kind kind, String id, ItemStack icon, List<ItemStack> items) {}

    /** A row of the right page: some text, a gap, a recipe, a structure's picture, or what a structure takes. */
    private record Row(FormattedCharSequence text, int colour, int height, Object special) {}

    private final List<Tab> tabs = new ArrayList<>();
    private final List<Structures.Structure> structures = Structures.all();
    private static int lastTab, lastEntry; // (opened again where it was left)
    private int tab, entry;
    private double leftScroll, rightScroll;
    private List<Row> rows = new ArrayList<>();
    private int left, top;
    /** Items drawn this frame, for their tooltips and following them: x, y, the stack. */
    private final List<Object[]> shown = new ArrayList<>();

    public GuideScreen() {
        super(Component.translatable("item.skyblocks.guide_book"));
        tabs.add(new Tab(Kind.START, "start", new ItemStack(ModItems.GUIDE_BOOK.get()), List.of()));
        for (GuideChapters.Chapter c : GuideChapters.all()) {
            tabs.add(new Tab(Kind.ITEMS, c.id(), c.icon(), c.items()));
        }
        List<ItemStack> icons = new ArrayList<>();
        for (Structures.Structure s : structures) {
            icons.add(s.result());
        }
        tabs.add(new Tab(Kind.STRUCTURES, "structures", new ItemStack(ModItems.CONTAINMENT_FRAME.get()), icons));
        tab = Mth.clamp(lastTab, 0, tabs.size() - 1);
        entry = lastEntry;
    }

    @Override
    protected void init() {
        left = (width - BW) / 2;
        top = (height - BH) / 2 + 10;
        select(tab, entry);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void select(int t, int e) {
        tab = Mth.clamp(t, 0, tabs.size() - 1);
        Tab tb = tabs.get(tab);
        entry = tb.items.isEmpty() ? 0 : Mth.clamp(e, 0, tb.items.size() - 1);
        lastTab = tab;
        lastEntry = entry;
        rightScroll = 0;
        rows = build(tb);
    }

    // ================= the right page's contents =================

    private void text(List<Row> out, Component c, int colour) {
        for (FormattedCharSequence line : font.split(c, PAGE_W)) {
            out.add(new Row(line, colour, 10, null));
        }
    }

    private void gap(List<Row> out, int h) {
        out.add(new Row(null, 0, h, null));
    }

    private List<Row> build(Tab tb) {
        List<Row> out = new ArrayList<>();
        switch (tb.kind) {
            case START -> {
                text(out, Component.translatable("guide.skyblocks.howto.title").withStyle(ChatFormatting.BOLD), INK);
                gap(out, 4);
                text(out, Component.translatable("guide.skyblocks.howto"), INK);
            }
            case ITEMS -> {
                ItemStack stack = tb.items.get(entry);
                gap(out, 22); // (its picture and name)
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                String key = "guide.skyblocks." + id;
                if (!I18n.exists(key)) {
                    key = id.endsWith("_bucket") ? "guide.skyblocks.sky_liquid" : id.endsWith("_sky_block") ? "guide.skyblocks.sky_block" : null;
                }
                if (key != null) {
                    text(out, Component.translatable(key), INK);
                    gap(out, 5);
                }
                Minecraft mc = Minecraft.getInstance();
                List<Component> tips = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
                if (tips.size() > 1) {
                    text(out, Component.translatable("guide.skyblocks.use").withStyle(ChatFormatting.BOLD), INK);
                    for (int i = 1; i < tips.size(); i++) {
                        text(out, Component.literal("• ").append(tips.get(i).copy().withStyle(s -> s.withColor((net.minecraft.network.chat.TextColor) null))), 0xFF5A4630);
                    }
                    gap(out, 5);
                }
                RecipeHolder<CraftingRecipe> recipe = recipeFor(stack);
                if (recipe != null) {
                    text(out, Component.translatable("guide.skyblocks.recipe").withStyle(ChatFormatting.BOLD), INK);
                    gap(out, 2);
                    out.add(new Row(null, 0, 58, recipe));
                }
            }
            case STRUCTURES -> {
                Structures.Structure s = structures.get(entry);
                gap(out, 22);
                out.add(new Row(null, 0, 104, s));
                text(out, Component.translatable("guide.skyblocks.needs").withStyle(ChatFormatting.BOLD), INK);
                gap(out, 2);
                out.add(new Row(null, 0, 20 * ((s.needs().size() + 5) / 6), s.needs()));
                gap(out, 4);
                text(out, Component.translatable("guide.skyblocks.structure." + s.id()), INK);
            }
        }
        return out;
    }

    private static RecipeHolder<CraftingRecipe> recipeFor(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        for (RecipeHolder<CraftingRecipe> h : mc.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack out = h.value().getResultItem(mc.level.registryAccess());
            if (!out.isEmpty() && out.is(stack.getItem()) && !h.value().getIngredients().isEmpty()) {
                return h;
            }
        }
        return null;
    }

    // ================= drawing =================

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        shown.clear();
        int l = left, t = top, mid = l + BW / 2;
        // the tabs over the book
        for (int i = 0; i < tabs.size(); i++) {
            int tx = l + 6 + i * 24, ty = t - 24, h = i == tab ? 27 : 22;
            g.fill(tx, ty, tx + 22, ty + h, 0xFF5A3A22);
            g.fill(tx + 1, ty + 1, tx + 21, ty + h - (i == tab ? 0 : 1), i == tab ? PAPER : PAPER_DARK);
            item(g, tabs.get(i).icon, tx + 3, ty + 3);
        }
        // the book
        g.fill(l - 5, t - 3, l + BW + 5, t + BH + 5, 0xFF5A3A22);
        g.fill(l, t, mid - 1, t + BH, PAPER);
        g.fill(mid + 1, t, l + BW, t + BH, PAPER);
        g.fill(mid - 1, t, mid + 1, t + BH, 0xFFC9B48C);
        for (int i = 0; i < tabs.size(); i++) { // (the chosen tab joined to its page)
            if (i == tab) {
                g.fill(l + 7 + i * 24, t - 2, l + 27 + i * 24, t + 1, PAPER);
            }
        }
        Tab tb = tabs.get(tab);
        drawLeft(g, tb, l + 8, t, mx, my);
        drawRight(g, tb, mid + 9, t, mx, my, partialTick);
        // the hovered tab's or item's name
        for (int i = 0; i < tabs.size(); i++) {
            int tx = l + 6 + i * 24, ty = t - 24;
            if (mx >= tx && mx < tx + 22 && my >= ty && my < ty + 22) {
                g.renderTooltip(font, Component.translatable("guide.skyblocks.chapter." + tabs.get(i).id), mx, my);
                return;
            }
        }
        for (Object[] o : shown) {
            int x = (int) o[0], y = (int) o[1];
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                g.renderTooltip(font, (ItemStack) o[2], mx, my);
                return;
            }
        }
    }

    private void item(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
        shown.add(new Object[] {x, y, stack});
    }

    private void drawLeft(GuiGraphics g, Tab tb, int x, int t, int mx, int my) {
        g.drawString(font, Component.translatable("guide.skyblocks.chapter." + tb.id).withStyle(ChatFormatting.BOLD), x, t + 8, INK, false);
        g.fill(x, t + 19, x + PAGE_W, t + 20, FAINT);
        if (tb.kind == Kind.START) {
            int y = t + 26;
            for (FormattedCharSequence line : font.split(Component.translatable("guide.skyblocks.intro"), PAGE_W)) {
                g.drawString(font, line, x, y, INK, false);
                y += 10;
            }
            return;
        }
        int listTop = t + 23, listBottom = t + BH - 6;
        int total = tb.items.size() * ROW;
        leftScroll = Mth.clamp(leftScroll, 0, Math.max(0, total - (listBottom - listTop)));
        g.enableScissor(x - 2, listTop, x + PAGE_W + 2, listBottom);
        for (int i = 0; i < tb.items.size(); i++) {
            int y = listTop + i * ROW - (int) leftScroll;
            if (y + ROW < listTop || y > listBottom) {
                continue;
            }
            boolean hover = mx >= x - 2 && mx < x + PAGE_W && my >= y && my < y + ROW && my >= listTop && my < listBottom;
            if (i == entry) {
                g.fill(x - 2, y, x + PAGE_W, y + ROW, 0x40A07840);
            } else if (hover) {
                g.fill(x - 2, y, x + PAGE_W, y + ROW, 0x20A07840);
            }
            ItemStack stack = tb.items.get(i);
            g.renderItem(stack, x, y + 1);
            Component name = tb.kind == Kind.STRUCTURES ? Component.translatable("guide.skyblocks.structure." + structures.get(i).id() + ".name") : stack.getHoverName();
            String s = font.plainSubstrByWidth(name.getString(), PAGE_W - 22);
            g.drawString(font, s, x + 20, y + 5, i == entry ? 0xFF7A3A10 : INK, false);
        }
        g.disableScissor();
        if (total > listBottom - listTop) { // (a little bar: there's more)
            int h = listBottom - listTop, bar = Math.max(12, h * h / total);
            int by = listTop + (int) ((h - bar) * leftScroll / (total - h));
            g.fill(x + PAGE_W + 1, by, x + PAGE_W + 3, by + bar, FAINT);
        }
    }

    private void drawRight(GuiGraphics g, Tab tb, int x, int t, int mx, int my, float partialTick) {
        int areaTop = t + 8, areaBottom = t + BH - 6;
        int total = 0;
        for (Row r : rows) {
            total += r.height;
        }
        rightScroll = Mth.clamp(rightScroll, 0, Math.max(0, total - (areaBottom - areaTop)));
        g.enableScissor(x - 2, areaTop, x + PAGE_W + 4, areaBottom);
        int y = areaTop - (int) rightScroll;
        if (tb.kind != Kind.START) { // its picture, twice the size, and its name
            ItemStack stack = tb.items.get(entry);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(1.25F, 1.25F, 1.0F);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
            Component name = tb.kind == Kind.STRUCTURES ? Component.translatable("guide.skyblocks.structure." + structures.get(entry).id() + ".name") : stack.getHoverName();
            List<FormattedCharSequence> nameLines = font.split(name.copy().withStyle(ChatFormatting.BOLD), PAGE_W - 26);
            for (int i = 0; i < Math.min(2, nameLines.size()); i++) {
                g.drawString(font, nameLines.get(i), x + 25, y + (nameLines.size() > 1 ? 1 : 6) + i * 10, INK, false);
            }
        }
        for (Row r : rows) {
            if (y + r.height >= areaTop && y <= areaBottom) {
                if (r.text != null) {
                    g.drawString(font, r.text, x, y, r.colour, false);
                } else if (r.special instanceof RecipeHolder<?> h) {
                    drawRecipe(g, (CraftingRecipe) h.value(), x, y);
                } else if (r.special instanceof Structures.Structure s) {
                    g.fill(x, y, x + PAGE_W, y + 100, PAPER_DARK);
                    float yaw = (System.currentTimeMillis() % 20000L) / 20000.0F * 360.0F;
                    StructurePreview.draw(g, s, x + PAGE_W / 2.0F, y + 50, 96, yaw, 28.0F);
                } else if (r.special instanceof List<?> needs) {
                    int i = 0;
                    for (Object o : needs) {
                        ItemStack stack = (ItemStack) o;
                        int ix = x + (i % 6) * 21, iy = y + (i / 6) * 20;
                        g.fill(ix, iy, ix + 18, iy + 18, PAPER_DARK);
                        item(g, stack, ix + 1, iy + 1);
                        g.renderItemDecorations(font, stack, ix + 1, iy + 1);
                        i++;
                    }
                }
            }
            y += r.height;
        }
        g.disableScissor();
        if (total > areaBottom - areaTop) {
            int h = areaBottom - areaTop, bar = Math.max(12, h * h / total);
            int by = areaTop + (int) ((h - bar) * rightScroll / (total - h));
            g.fill(x + PAGE_W + 3, by, x + PAGE_W + 5, by + bar, FAINT);
        }
    }

    private void drawRecipe(GuiGraphics g, CraftingRecipe recipe, int x, int y) {
        List<Ingredient> ings = recipe.getIngredients();
        int w = recipe instanceof ShapedRecipe s ? s.getWidth() : 3;
        int cycle = (int) (System.currentTimeMillis() / 1000L);
        for (int slot = 0; slot < 9; slot++) {
            int sx = x + (slot % 3) * 18, sy = y + (slot / 3) * 18;
            g.fill(sx, sy, sx + 17, sy + 17, PAPER_DARK);
        }
        for (int i = 0; i < ings.size(); i++) {
            ItemStack[] options = ings.get(i).getItems();
            if (options.length == 0) {
                continue;
            }
            int sx = x + (i % w) * 18, sy = y + (i / w) * 18;
            item(g, options[cycle % options.length], sx, sy);
        }
        g.drawString(font, "→", x + 62, y + 22, INK, false);
        Minecraft mc = Minecraft.getInstance();
        ItemStack result = recipe.getResultItem(mc.level.registryAccess());
        g.fill(x + 76, y + 17, x + 96, y + 37, PAPER_DARK);
        item(g, result, x + 78, y + 19);
        g.renderItemDecorations(font, result, x + 78, y + 19);
    }

    // ================= input =================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int l = left, t = top;
        for (int i = 0; i < tabs.size(); i++) {
            int tx = l + 6 + i * 24, ty = t - 24;
            if (mx >= tx && mx < tx + 22 && my >= ty && my < ty + 22) {
                leftScroll = 0;
                select(i, 0);
                click();
                return true;
            }
        }
        Tab tb = tabs.get(tab);
        int x = l + 8, listTop = t + 23, listBottom = t + BH - 6;
        if (tb.kind != Kind.START && mx >= x - 2 && mx < x + PAGE_W && my >= listTop && my < listBottom) {
            int i = (int) ((my - listTop + leftScroll) / ROW);
            if (i >= 0 && i < tb.items.size()) {
                select(tab, i);
                click();
                return true;
            }
        }
        // an item of ours in a recipe: to its page
        for (Object[] o : shown) {
            int ix = (int) o[0], iy = (int) o[1];
            if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16 && mx > l + BW / 2) {
                ItemStack stack = (ItemStack) o[2];
                for (int ti = 0; ti < tabs.size(); ti++) {
                    if (tabs.get(ti).kind != Kind.ITEMS) {
                        continue;
                    }
                    List<ItemStack> items = tabs.get(ti).items;
                    for (int e = 0; e < items.size(); e++) {
                        if (ItemStack.isSameItemSameComponents(items.get(e), stack) && !(ti == tab && e == entry)) {
                            select(ti, e);
                            leftScroll = Math.max(0, e * ROW - 60);
                            click();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx < left + BW / 2.0) {
            leftScroll -= dy * ROW;
        } else {
            rightScroll -= dy * 12;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        Tab tb = tabs.get(tab);
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN && entry + 1 < tb.items.size()) {
            select(tab, entry + 1);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP && entry > 0) {
            select(tab, entry - 1);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT && tab + 1 < tabs.size()) {
            leftScroll = 0;
            select(tab + 1, 0);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT && tab > 0) {
            leftScroll = 0;
            select(tab - 1, 0);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
