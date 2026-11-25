package com.example.skyblocks.client;

import com.example.skyblocks.GuideChapters;
import com.example.skyblocks.ModItems;
import com.example.skyblocks.Structures;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
 * The Shadered+ guide book, in three columns: its chapters down the left (a name each, or just their icons on a small
 * screen), the chosen chapter's entries in the middle (with a search over the whole book at their top), and the chosen
 * entry's page on the right - its picture and name, what it does, how to use it, how to make it (a structure: a picture
 * of it turning, what it takes, how to build it) - with the previous and next entry at its foot.
 */
public class GuideScreen extends Screen {
    private static final int ROW = 18, CHAP_ROW = 18, HEAD = 22, FOOT = 16;
    private static final int FRAME = 0xFF4A3220, SIDE = 0xFFE8DABB, LIST = 0xFFF1E6CD, PAGE = 0xFFF9F2E2, SLOT = 0xFFE4D5B4, SLOT_EDGE = 0xFFC8B48C;
    private static final int INK = 0xFF3B2A1A, SOFT = 0xFF6A5640, FAINT = 0xFF9A8466, ACCENT = 0xFFB0642A, PICK = 0x30B0642A, HOVER = 0x18B0642A;

    private enum Kind { START, ITEMS, STRUCTURES }

    private record Tab(Kind kind, String id, ItemStack icon, List<ItemStack> items) {}

    /** An entry in the middle column: which chapter and entry it opens, its picture and name. */
    private record Ref(int tab, int entry, ItemStack stack, Component name) {}

    /** A row of the page: some text, a gap, a heading's rule, a recipe, a structure's picture, or what a structure takes. */
    private record Row(FormattedCharSequence text, int colour, int height, Object special) {}

    private final List<Tab> tabs = new ArrayList<>();
    private final List<Structures.Structure> structures = Structures.all();
    private static int lastTab, lastEntry; // (opened again where it was left)
    private static String lastSearch = "";
    private int tab, entry;
    private double chapScroll, listScroll, pageScroll;
    private List<Ref> list = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();
    private EditBox search;
    // where everything is
    private int px, py, w, h, sideW, listX, listW, pageX, pageW;
    private boolean compact;
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

    /** (The render test: the guide opened at a chapter's entry, searching for something.) */
    public static GuideScreen at(String chapter, int entry, String search) {
        GuideScreen s = new GuideScreen();
        for (int i = 0; i < s.tabs.size(); i++) {
            if (s.tabs.get(i).id.equals(chapter)) {
                s.tab = i;
            }
        }
        s.entry = entry;
        lastSearch = search;
        return s;
    }

    @Override
    protected void init() {
        w = Math.min(width - 16, 470);
        h = Math.min(height - 16, 262);
        px = (width - w) / 2;
        py = (height - h) / 2;
        compact = w < 400;
        sideW = compact ? 26 : 112;
        listW = compact ? 116 : 124;
        listX = px + sideW;
        pageX = listX + listW;
        pageW = px + w - pageX;
        search = new EditBox(font, listX + 16, py + 7, listW - 22, 10, Component.translatable("guide.skyblocks.search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(0xFF3B2A1A);
        search.setTextColorUneditable(0xFF3B2A1A);
        search.setHint(Component.translatable("guide.skyblocks.search").withStyle(s -> s.withColor(0x9A8466)));
        search.setValue(lastSearch);
        search.setResponder(s -> {
            lastSearch = s;
            listScroll = 0;
            relist();
        });
        addRenderableWidget(search);
        relist();
        select(tab, entry);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component nameOf(int t, int e) {
        Tab tb = tabs.get(t);
        return tb.kind == Kind.STRUCTURES ? Component.translatable("guide.skyblocks.structure." + structures.get(e).id() + ".name") : tb.items.get(e).getHoverName();
    }

    /** The middle column: the chapter's entries, or (searching) whatever in the book matches. */
    private void relist() {
        list = new ArrayList<>();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            Tab tb = tabs.get(tab);
            for (int e = 0; e < tb.items.size(); e++) {
                list.add(new Ref(tab, e, tb.items.get(e), nameOf(tab, e)));
            }
            return;
        }
        List<ItemStack> seen = new ArrayList<>();
        for (int t = 0; t < tabs.size(); t++) {
            Tab tb = tabs.get(t);
            for (int e = 0; e < tb.items.size(); e++) {
                ItemStack s = tb.items.get(e);
                Component name = nameOf(t, e);
                if (!name.getString().toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                boolean dup = false;
                for (ItemStack o : seen) {
                    dup |= tb.kind == Kind.ITEMS && ItemStack.isSameItemSameComponents(o, s);
                }
                if (!dup) {
                    if (tb.kind == Kind.ITEMS) {
                        seen.add(s);
                    }
                    list.add(new Ref(t, e, s, name));
                }
            }
        }
    }

    private void select(int t, int e) {
        boolean chapter = t != tab;
        tab = Mth.clamp(t, 0, tabs.size() - 1);
        Tab tb = tabs.get(tab);
        entry = tb.items.isEmpty() ? 0 : Mth.clamp(e, 0, tb.items.size() - 1);
        lastTab = tab;
        lastEntry = entry;
        pageScroll = 0;
        if (chapter && (search == null || search.getValue().isEmpty())) {
            relist();
        }
        rows = build(tb);
    }

    // ================= the page's contents =================

    private int textW() {
        return pageW - 18;
    }

    private void text(List<Row> out, Component c, int colour) {
        for (FormattedCharSequence line : font.split(c, textW())) {
            out.add(new Row(line, colour, 10, null));
        }
    }

    private void heading(List<Row> out, String key) {
        out.add(new Row(Component.translatable(key).withStyle(ChatFormatting.BOLD).getVisualOrderText(), ACCENT, 11, null));
        out.add(new Row(null, 0, 5, "rule"));
    }

    private void gap(List<Row> out, int h) {
        out.add(new Row(null, 0, h, null));
    }

    private List<Row> build(Tab tb) {
        List<Row> out = new ArrayList<>();
        switch (tb.kind) {
            case START -> {
                text(out, Component.translatable("guide.skyblocks.intro"), INK);
                gap(out, 8);
                heading(out, "guide.skyblocks.howto.title");
                text(out, Component.translatable("guide.skyblocks.howto"), SOFT);
            }
            case ITEMS -> {
                ItemStack stack = tb.items.get(entry);
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                String key = "guide.skyblocks." + id;
                if (!I18n.exists(key)) {
                    key = id.endsWith("_bucket") ? "guide.skyblocks.sky_liquid" : id.endsWith("_sky_block") ? "guide.skyblocks.sky_block"
                            : stack.getItem() instanceof com.example.skyblocks.ModSkyShapes.ShapeItem ? "guide.skyblocks.sky_shapes" : null;
                }
                if (key != null) {
                    text(out, Component.translatable(key), INK);
                    gap(out, 8);
                }
                Minecraft mc = Minecraft.getInstance();
                List<Component> tips = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
                if (tips.size() > 1) {
                    heading(out, "guide.skyblocks.use");
                    for (int i = 1; i < tips.size(); i++) {
                        String line = tips.get(i).getString().trim();
                        if (line.isEmpty()) {
                            continue;
                        }
                        List<FormattedCharSequence> parts = font.split(Component.literal(line), textW() - 8);
                        for (int k = 0; k < parts.size(); k++) {
                            out.add(new Row(parts.get(k), SOFT, 10, k == 0 ? "bullet" : "indent"));
                        }
                        gap(out, 2);
                    }
                    gap(out, 6);
                }
                RecipeHolder<CraftingRecipe> recipe = recipeFor(stack);
                if (recipe != null) {
                    heading(out, "guide.skyblocks.recipe");
                    gap(out, 2);
                    out.add(new Row(null, 0, 60, recipe));
                }
            }
            case STRUCTURES -> {
                Structures.Structure s = structures.get(entry);
                out.add(new Row(null, 0, 108, s));
                heading(out, "guide.skyblocks.needs");
                int per = Math.max(1, (textW() + 3) / 21);
                out.add(new Row(null, 0, 21 * ((s.needs().size() + per - 1) / per) + 4, s.needs()));
                gap(out, 4);
                heading(out, "guide.skyblocks.build");
                text(out, Component.translatable("guide.skyblocks.structure." + s.id()), SOFT);
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
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        super.renderBackground(g, mx, my, partialTick);
        // the book: its frame, then its three columns in three shades of paper (no lines needed between them)
        g.fill(px - 3, py - 3, px + w + 3, py + h + 3, FRAME);
        g.fill(px, py, listX, py + h, SIDE);
        g.fill(listX, py, pageX, py + h, LIST);
        g.fill(pageX, py, px + w, py + h, PAGE);
        // the search field's bed
        g.fill(listX + 5, py + 4, pageX - 5, py + 18, 0xFFFFFAEE);
        g.fill(listX + 5, py + 17, pageX - 5, py + 18, search != null && search.isFocused() ? ACCENT : SLOT_EDGE);
        magnifier(g, listX + 8, py + 6);
    }

    private static void magnifier(GuiGraphics g, int x, int y) {
        int c = 0xFF9A8466;
        g.fill(x + 1, y, x + 5, y + 1, c);
        g.fill(x + 1, y + 5, x + 5, y + 6, c);
        g.fill(x, y + 1, x + 1, y + 5, c);
        g.fill(x + 5, y + 1, x + 6, y + 5, c);
        g.fill(x + 5, y + 6, x + 7, y + 8, c);
        g.fill(x + 6, y + 7, x + 8, y + 9, c);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        shown.clear();
        Component hoverChapter = drawChapters(g, mx, my);
        drawList(g, mx, my);
        drawPage(g, mx, my);
        if (hoverChapter != null) {
            g.renderTooltip(font, hoverChapter, mx, my);
            return;
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

    private String fit(Component c, int width) {
        String s = c.getString();
        if (font.width(s) <= width) {
            return s;
        }
        return font.plainSubstrByWidth(s, width - font.width("...")) + "...";
    }

    /** The chapters down the left: the chosen one marked with a bar. Returns the hovered one's name (compact: icons only). */
    private Component drawChapters(GuiGraphics g, int mx, int my) {
        int top = py + 4, bottom = py + h - 4;
        int total = tabs.size() * CHAP_ROW;
        chapScroll = Mth.clamp(chapScroll, 0, Math.max(0, total - (bottom - top)));
        Component hovered = null;
        g.enableScissor(px, top, listX, bottom);
        for (int i = 0; i < tabs.size(); i++) {
            int y = top + i * CHAP_ROW - (int) chapScroll;
            if (y + CHAP_ROW < top || y > bottom) {
                continue;
            }
            boolean hover = mx >= px && mx < listX && my >= y && my < y + CHAP_ROW && my >= top && my < bottom;
            if (i == tab) {
                g.fill(px, y, listX, y + CHAP_ROW, PICK);
                g.fill(px, y, px + 2, y + CHAP_ROW, ACCENT);
            } else if (hover) {
                g.fill(px, y, listX, y + CHAP_ROW, HOVER);
            }
            g.renderItem(tabs.get(i).icon, px + 5, y + 1);
            Component name = Component.translatable("guide.skyblocks.chapter." + tabs.get(i).id);
            if (!compact) {
                g.drawString(font, fit(name, sideW - 28), px + 24, y + 5, i == tab ? INK : SOFT, false);
            } else if (hover) {
                hovered = name;
            }
        }
        g.disableScissor();
        scrollbar(g, listX - 3, top, bottom, total, chapScroll);
        return hovered;
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        int x = listX + 4, top = py + HEAD + 2, bottom = py + h - 4;
        if (tabs.get(tab).kind == Kind.START && search.getValue().isEmpty()) {
            // (the first chapter has no entries: a word on where to start)
            int y = top + 4;
            for (FormattedCharSequence line : font.split(Component.translatable("guide.skyblocks.start_tip"), listW - 12)) {
                g.drawString(font, line, x + 2, y, SOFT, false);
                y += 10;
            }
            return;
        }
        if (!search.getValue().isEmpty()) {
            g.drawString(font, Component.translatable("guide.skyblocks.results", list.size()), x + 2, top, FAINT, false);
            top += 11;
        }
        int total = list.size() * ROW;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, total - (bottom - top)));
        g.enableScissor(listX, top, pageX, bottom);
        for (int i = 0; i < list.size(); i++) {
            int y = top + i * ROW - (int) listScroll;
            if (y + ROW < top || y > bottom) {
                continue;
            }
            Ref r = list.get(i);
            boolean picked = r.tab == tab && r.entry == entry;
            boolean hover = mx >= listX && mx < pageX && my >= y && my < y + ROW && my >= top && my < bottom;
            if (picked) {
                g.fill(listX + 2, y, pageX - 2, y + ROW, PICK);
            } else if (hover) {
                g.fill(listX + 2, y, pageX - 2, y + ROW, HOVER);
            }
            g.renderItem(r.stack, x, y + 1);
            g.drawString(font, fit(r.name, listW - 30), x + 20, y + 5, picked ? 0xFF7A3A10 : INK, false);
        }
        g.disableScissor();
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("guide.skyblocks.nothing"), x + 2, top + 4, FAINT, false);
        }
        scrollbar(g, pageX - 3, top, bottom, total, listScroll);
    }

    private static void scrollbar(GuiGraphics g, int x, int top, int bottom, int total, double scroll) {
        int span = bottom - top;
        if (total <= span) {
            return;
        }
        int bar = Math.max(14, span * span / total);
        int by = top + (int) ((span - bar) * scroll / (total - span));
        g.fill(x, top, x + 2, bottom, 0x14000000);
        g.fill(x, by, x + 2, by + bar, 0xFFB8A27E);
    }

    private void drawPage(GuiGraphics g, int mx, int my) {
        Tab tb = tabs.get(tab);
        int x = pageX + 9;
        // its header: the picture large, its name, the chapter it's in
        ItemStack stack = tb.kind == Kind.START ? tb.icon : tb.items.get(entry);
        g.pose().pushPose();
        g.pose().translate(x, py + 7, 0);
        g.pose().scale(1.5F, 1.5F, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        Component name = tb.kind == Kind.START ? Component.translatable("guide.skyblocks.chapter.start") : nameOf(tab, entry);
        List<FormattedCharSequence> nameLines = font.split(name.copy().withStyle(ChatFormatting.BOLD), pageW - 46);
        boolean two = nameLines.size() > 1;
        for (int i = 0; i < Math.min(2, nameLines.size()); i++) {
            g.drawString(font, nameLines.get(i), x + 30, py + (two ? 7 : 9) + i * 10, INK, false);
        }
        if (!two) {
            g.drawString(font, fit(Component.translatable("guide.skyblocks.chapter." + tb.id), pageW - 46), x + 30, py + 20, FAINT, false);
        }
        int areaTop = py + 36, areaBottom = py + h - FOOT - 2;
        g.fill(x, areaTop - 4, pageX + pageW - 9, areaTop - 3, SLOT_EDGE);
        int total = 0;
        for (Row r : rows) {
            total += r.height;
        }
        pageScroll = Mth.clamp(pageScroll, 0, Math.max(0, total - (areaBottom - areaTop)));
        g.enableScissor(pageX, areaTop, px + w, areaBottom);
        int y = areaTop - (int) pageScroll;
        for (Row r : rows) {
            if (y + r.height >= areaTop && y <= areaBottom) {
                if (r.text != null) {
                    int tx = x;
                    if ("bullet".equals(r.special)) {
                        g.fill(x + 1, y + 3, x + 4, y + 6, ACCENT);
                        tx += 8;
                    } else if ("indent".equals(r.special)) {
                        tx += 8;
                    }
                    g.drawString(font, r.text, tx, y, r.colour, false);
                } else if ("rule".equals(r.special)) {
                    g.fill(x, y, x + 24, y + 1, ACCENT);
                } else if (r.special instanceof RecipeHolder<?> hd) {
                    drawRecipe(g, (CraftingRecipe) hd.value(), x, y);
                } else if (r.special instanceof Structures.Structure s) {
                    int bw = textW();
                    g.fill(x, y, x + bw, y + 102, SLOT);
                    float yaw = (System.currentTimeMillis() % 20000L) / 20000.0F * 360.0F;
                    StructurePreview.draw(g, s, x + bw / 2.0F, y + 51, Math.min(96, bw - 8), yaw, 28.0F);
                } else if (r.special instanceof List<?> needs) {
                    int per = Math.max(1, (textW() + 3) / 21), i = 0;
                    for (Object o : needs) {
                        ItemStack st = (ItemStack) o;
                        int ix = x + (i % per) * 21, iy = y + (i / per) * 21;
                        slot(g, ix, iy, 19);
                        item(g, st, ix + 2, iy + 2);
                        g.renderItemDecorations(font, st, ix + 2, iy + 2);
                        i++;
                    }
                }
            }
            y += r.height;
        }
        g.disableScissor();
        scrollbar(g, px + w - 4, areaTop, areaBottom, total, pageScroll);
        // its foot: the previous and next entries
        if (tb.kind != Kind.START && tb.items.size() > 1) {
            int fy = py + h - FOOT + 3;
            g.fill(x, fy - 4, pageX + pageW - 9, fy - 3, 0x30000000 | (SLOT_EDGE & 0xFFFFFF));
            boolean hasPrev = entry > 0, hasNext = entry + 1 < tb.items.size();
            boolean overPrev = hasPrev && mx >= x && mx < x + 30 && my >= fy - 2 && my < fy + 10;
            boolean overNext = hasNext && mx >= pageX + pageW - 40 && mx < pageX + pageW - 6 && my >= fy - 2 && my < fy + 10;
            if (hasPrev) {
                g.drawString(font, "< " + I18n.get("guide.skyblocks.prev"), x, fy, overPrev ? ACCENT : SOFT, false);
            }
            String count = (entry + 1) + " / " + tb.items.size();
            g.drawString(font, count, pageX + (pageW - font.width(count)) / 2, fy, FAINT, false);
            if (hasNext) {
                String next = I18n.get("guide.skyblocks.next") + " >";
                g.drawString(font, next, pageX + pageW - 9 - font.width(next), fy, overNext ? ACCENT : SOFT, false);
            }
        }
    }

    private static void slot(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, SLOT_EDGE);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, SLOT);
    }

    private void drawRecipe(GuiGraphics g, CraftingRecipe recipe, int x, int y) {
        List<Ingredient> ings = recipe.getIngredients();
        int rw = recipe instanceof ShapedRecipe s ? s.getWidth() : 3;
        int cycle = (int) (System.currentTimeMillis() / 1000L);
        int ox = x + Math.max(0, (textW() - 104) / 2); // (centred on the page)
        for (int slot = 0; slot < 9; slot++) {
            slot(g, ox + (slot % 3) * 19, y + (slot / 3) * 19, 19);
        }
        for (int i = 0; i < ings.size(); i++) {
            ItemStack[] options = ings.get(i).getItems();
            if (options.length == 0) {
                continue;
            }
            item(g, options[cycle % options.length], ox + (i % rw) * 19 + 2, y + (i / rw) * 19 + 2);
        }
        // the arrow
        int ax = ox + 61, ay = y + 27;
        g.fill(ax, ay - 1, ax + 12, ay + 2, FAINT);
        for (int k = 0; k < 5; k++) {
            g.fill(ax + 11 + k, ay - 4 + k, ax + 12 + k, ay + 5 - k, FAINT);
        }
        Minecraft mc = Minecraft.getInstance();
        ItemStack result = recipe.getResultItem(mc.level.registryAccess());
        slot(g, ox + 80, y + 17, 24);
        item(g, result, ox + 84, y + 21);
        g.renderItemDecorations(font, result, ox + 84, y + 21);
    }

    // ================= input =================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // a chapter
        int top = py + 4, bottom = py + h - 4;
        if (mx >= px && mx < listX && my >= top && my < bottom) {
            int i = (int) ((my - top + chapScroll) / CHAP_ROW);
            if (i >= 0 && i < tabs.size()) {
                search.setValue("");
                search.setFocused(false);
                listScroll = 0;
                select(i, 0);
                relist();
                click();
                return true;
            }
        }
        // an entry
        int lt = py + HEAD + 2 + (search.getValue().isEmpty() ? 0 : 11);
        if (mx >= listX && mx < pageX && my >= lt && my < py + h - 4) {
            int i = (int) ((my - lt + listScroll) / ROW);
            if (i >= 0 && i < list.size()) {
                Ref r = list.get(i);
                select(r.tab, r.entry);
                click();
                return true;
            }
        }
        // the page's foot: previous, next
        Tab tb = tabs.get(tab);
        int fy = py + h - FOOT + 3, x = pageX + 9;
        if (tb.kind != Kind.START && my >= fy - 2 && my < fy + 10) {
            if (entry > 0 && mx >= x && mx < x + 30) {
                select(tab, entry - 1);
                click();
                return true;
            }
            if (entry + 1 < tb.items.size() && mx >= pageX + pageW - 40 && mx < pageX + pageW - 6) {
                select(tab, entry + 1);
                click();
                return true;
            }
        }
        // an item of ours in a recipe or what a structure takes: to its page
        for (Object[] o : shown) {
            int ix = (int) o[0], iy = (int) o[1];
            if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16 && mx > pageX) {
                ItemStack stack = (ItemStack) o[2];
                for (int ti = 0; ti < tabs.size(); ti++) {
                    if (tabs.get(ti).kind != Kind.ITEMS) {
                        continue;
                    }
                    List<ItemStack> items = tabs.get(ti).items;
                    for (int e = 0; e < items.size(); e++) {
                        if (ItemStack.isSameItemSameComponents(items.get(e), stack) && !(ti == tab && e == entry)) {
                            search.setValue("");
                            select(ti, e);
                            relist();
                            listScroll = Math.max(0, e * ROW - 60);
                            click();
                            return true;
                        }
                    }
                }
            }
        }
        boolean handled = super.mouseClicked(mx, my, button);
        if (!search.isMouseOver(mx, my)) {
            search.setFocused(false);
        }
        return handled;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx < listX) {
            chapScroll -= dy * CHAP_ROW;
        } else if (mx < pageX) {
            listScroll -= dy * ROW;
        } else {
            pageScroll -= dy * 12;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (search.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && key != org.lwjgl.glfw.GLFW.GLFW_KEY_UP
                && key != org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            return search.keyPressed(key, scan, modifiers) || search.canConsumeInput() || super.keyPressed(key, scan, modifiers);
        }
        // up, down: through the middle column's entries
        int at = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).tab == tab && list.get(i).entry == entry) {
                at = i;
            }
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN && at + 1 < list.size()) {
            Ref r = list.get(at + 1);
            select(r.tab, r.entry);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP && at > 0) {
            Ref r = list.get(at - 1);
            select(r.tab, r.entry);
            return true;
        }
        // left, right: the chapters
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT && tab + 1 < tabs.size()) {
            search.setValue("");
            select(tab + 1, 0);
            relist();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT && tab > 0) {
            search.setValue("");
            select(tab - 1, 0);
            relist();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
