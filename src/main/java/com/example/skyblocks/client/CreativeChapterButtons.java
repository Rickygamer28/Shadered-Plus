package com.example.skyblocks.client;

import com.example.skyblocks.GuideChapters;
import com.example.skyblocks.ModItems;
import com.example.skyblocks.SkyBlocksMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The Shadered+ creative tab's chapters: a column of buttons beside it (lights, music, ... skies, and one for
 * everything) - click one and the tab shows just that chapter's items; click it again (or "everything") for all.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CreativeChapterButtons {
    private static final int SIZE = 20, COLUMNS = 2;
    private static List<GuideChapters.Chapter> chapters;
    /** The chapter shown (-1: everything). */
    private static int active = -1;
    private static java.lang.reflect.Field selectedTab, scrollOffs;

    private CreativeChapterButtons() {}

    private static List<GuideChapters.Chapter> chapters() {
        if (chapters == null) {
            chapters = GuideChapters.all();
        }
        return chapters;
    }

    private static CreativeModeTab selected() {
        try {
            if (selectedTab == null) {
                selectedTab = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
                selectedTab.setAccessible(true);
            }
            return (CreativeModeTab) selectedTab.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean ours() {
        return selected() == SkyBlocksMod.TAB.get();
    }

    /** The buttons' places beside the creative menu (empty: not on Shadered+'s tab, or no room). [0]: everything. */
    public static List<Rect2i> areas(CreativeModeInventoryScreen screen) {
        List<Rect2i> out = new ArrayList<>();
        if (!ours()) {
            return out;
        }
        int x0 = screen.getGuiLeft() - 4 - COLUMNS * SIZE, y0 = screen.getGuiTop() + 4;
        if (x0 < 2) {
            return out;
        }
        int count = chapters().size() + 1;
        for (int i = 0; i < count; i++) {
            out.add(new Rect2i(x0 + (i % COLUMNS) * SIZE, y0 + (i / COLUMNS) * SIZE, SIZE, SIZE));
        }
        return out;
    }

    private static ItemStack icon(int i) {
        return i == 0 ? new ItemStack(ModItems.GUIDE_BOOK.get()) : chapters().get(i - 1).icon();
    }

    private static Component name(int i) {
        return i == 0 ? Component.translatable("guide.skyblocks.chapter.everything")
                : Component.translatable("guide.skyblocks.chapter." + chapters().get(i - 1).id());
    }

    /** The tab's items: one chapter's, or everything. */
    private static void show(CreativeModeInventoryScreen screen, int chapter) {
        active = chapter;
        CreativeModeInventoryScreen.ItemPickerMenu menu = screen.getMenu();
        menu.items.clear();
        if (chapter < 0) {
            menu.items.addAll(SkyBlocksMod.TAB.get().getDisplayItems());
        } else {
            for (ItemStack s : chapters().get(chapter).items()) {
                menu.items.add(s.copy());
            }
        }
        try {
            if (scrollOffs == null) {
                scrollOffs = CreativeModeInventoryScreen.class.getDeclaredField("scrollOffs");
                scrollOffs.setAccessible(true);
            }
            scrollOffs.setFloat(screen, 0.0F);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // (the scroll bar stays where it was)
        }
        menu.scrollTo(0.0F);
    }

    private static CreativeModeTab lastTab;

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) {
            return;
        }
        CreativeModeTab tab = selected();
        if (tab != lastTab) { // (another tab, or back to ours: everything again)
            lastTab = tab;
            active = -1;
        }
        List<Rect2i> areas = areas(screen);
        if (areas.isEmpty()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int mx = event.getMouseX(), my = event.getMouseY();
        Component hovered = null;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (int i = 0; i < areas.size(); i++) {
            Rect2i r = areas.get(i);
            boolean on = active == i - 1;
            boolean over = r.contains(mx, my);
            g.fill(r.getX(), r.getY(), r.getX() + SIZE - 1, r.getY() + SIZE - 1, on ? 0xFF8B5CF6 : over ? 0xFF8A8A9E : 0xFF555566);
            g.fill(r.getX() + 1, r.getY() + 1, r.getX() + SIZE - 2, r.getY() + SIZE - 2, on ? 0xFF4A3478 : 0xFFC6C6C6);
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
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || event.getButton() != 0) {
            return;
        }
        List<Rect2i> areas = areas(screen);
        for (int i = 0; i < areas.size(); i++) {
            if (areas.get(i).contains((int) event.getMouseX(), (int) event.getMouseY())) {
                show(screen, i == 0 || active == i - 1 ? -1 : i - 1);
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                event.setCanceled(true);
                return;
            }
        }
    }
}
