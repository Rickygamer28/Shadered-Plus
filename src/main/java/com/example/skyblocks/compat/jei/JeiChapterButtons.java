package com.example.skyblocks.compat.jei;

import com.example.skyblocks.GuideChapters;
import com.example.skyblocks.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Beside an inventory with JEI's list showing: a button for each of Shadered+'s chapters (and one for all of it) -
 * click one and JEI's list shows just that chapter's items (it searches for the chapter's tag, skyblocks:shaderedplus_*);
 * click it again to clear the search.
 */
public final class JeiChapterButtons {
    private static final int SIZE = 20, COLUMNS = 2;
    private static IJeiRuntime runtime;
    private static List<GuideChapters.Chapter> chapters;
    private static String active = "";

    private JeiChapterButtons() {}

    static void setRuntime(IJeiRuntime r) {
        runtime = r;
    }

    private static List<GuideChapters.Chapter> chapters() {
        if (chapters == null) {
            chapters = GuideChapters.all();
        }
        return chapters;
    }

    /** The buttons' places beside this screen: [0] all of Shadered+, then each chapter (empty: no room, or no JEI list). */
    static List<Rect2i> areas(AbstractContainerScreen<?> screen) {
        List<Rect2i> out = new ArrayList<>();
        if (runtime == null || !runtime.getIngredientListOverlay().isListDisplayed()) {
            return out;
        }
        int count = chapters().size() + 1;
        int x0 = screen.getGuiLeft() - 4 - COLUMNS * SIZE, y0 = screen.getGuiTop();
        if (x0 < 2) {
            return out;
        }
        for (int i = 0; i < count; i++) {
            out.add(new Rect2i(x0 + (i % COLUMNS) * SIZE, y0 + (i / COLUMNS) * SIZE, SIZE, SIZE));
        }
        return out;
    }

    private static String filter(int i) {
        return i == 0 ? "$shaderedplus_" : "$shaderedplus_" + chapters().get(i - 1).id();
    }

    private static ItemStack icon(int i) {
        return i == 0 ? new ItemStack(ModItems.GUIDE_BOOK.get()) : chapters().get(i - 1).icon();
    }

    private static Component name(int i) {
        return i == 0 ? Component.literal("Shadered+") : Component.translatable("guide.skyblocks.chapter." + chapters().get(i - 1).id());
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        List<Rect2i> areas = areas(screen);
        if (areas.isEmpty()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        String current = runtime.getIngredientFilter().getFilterText();
        int mx = event.getMouseX(), my = event.getMouseY();
        Component hovered = null;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300); // (over JEI's own overlays)
        for (int i = 0; i < areas.size(); i++) {
            Rect2i r = areas.get(i);
            boolean on = current.equalsIgnoreCase(filter(i)) && !current.isEmpty();
            boolean over = r.contains(mx, my);
            g.fill(r.getX(), r.getY(), r.getX() + SIZE - 1, r.getY() + SIZE - 1, on ? 0xFF8B5CF6 : over ? 0xFF5A5A6E : 0xFF2E2E3A);
            g.fill(r.getX() + 1, r.getY() + 1, r.getX() + SIZE - 2, r.getY() + SIZE - 2, on ? 0xFF3B2A60 : 0xFF1C1C24);
            g.renderItem(icon(i), r.getX() + 1, r.getY() + 1);
            if (over) {
                hovered = name(i);
            }
        }
        if (hovered != null) {
            g.renderTooltip(Minecraft.getInstance().font, hovered, mx, my);
        }
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || event.getButton() != 0) {
            return;
        }
        List<Rect2i> areas = areas(screen);
        for (int i = 0; i < areas.size(); i++) {
            if (areas.get(i).contains((int) event.getMouseX(), (int) event.getMouseY())) {
                String f = filter(i);
                boolean on = runtime.getIngredientFilter().getFilterText().equalsIgnoreCase(f);
                runtime.getIngredientFilter().setFilterText(on ? "" : f);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                event.setCanceled(true);
                return;
            }
        }
    }
}
