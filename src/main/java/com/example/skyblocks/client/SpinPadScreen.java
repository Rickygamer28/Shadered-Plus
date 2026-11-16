package com.example.skyblocks.client;

import com.example.skyblocks.ModItems;
import com.example.skyblocks.SpinPadBlockEntity;
import com.example.skyblocks.SpinPadMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A spin pad's screen (SpinPadMenu): its cats' slot, and how it's going - spinning or not (and why), its speed and turns
 * a minute, the energy it's making and holding, how big the cat it spins is - for a big pad, how big it is.
 */
public class SpinPadScreen extends AbstractContainerScreen<SpinPadMenu> {
    private static final int ACCENT = 0xFFFF6FD8, PANEL = 0xF0140E1C, SLOT = 0xFF2A2236, SLOT_EDGE = 0xFF4A3F5A;

    public SpinPadScreen(SpinPadMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = SpinPadMenu.INVENTORY_Y + 82;
        inventoryLabelY = SpinPadMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, ACCENT);
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        for (Slot s : menu.slots) {
            g.fill(leftPos + s.x - 1, topPos + s.y - 1, leftPos + s.x + 17, topPos + s.y + 17, SLOT_EDGE);
            g.fill(leftPos + s.x, topPos + s.y, leftPos + s.x + 16, topPos + s.y + 16, SLOT);
        }
        // the cats' slot: bigger, glowing
        int cx = leftPos + SpinPadMenu.CAT_X, cy = topPos + SpinPadMenu.CAT_Y;
        g.fill(cx - 4, cy - 4, cx + 20, cy + 20, ACCENT);
        g.fill(cx - 3, cy - 3, cx + 19, cy + 19, 0xFF1D1428);
        g.fill(cx - 1, cy - 1, cx + 17, cy + 17, SLOT_EDGE);
        g.fill(cx, cy, cx + 16, cy + 16, SLOT);
        if (menu.slots.get(0).getItem().isEmpty()) {
            g.renderFakeItem(new ItemStack(ModItems.OIIA_CAT.get()), cx, cy);
            g.fill(cx, cy, cx + 16, cy + 16, 0xA01D1428); // (a ghost: put cats here)
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        SpinPadBlockEntity pad = menu.pad();
        int side = pad == null ? 1 : Math.max(1, pad.side());
        Component name = side > 1 ? Component.translatable("screen.skyblocks.spin_pad.big", side, side) : title;
        g.drawString(font, name, 8, 6, 0xFFFFE6F6, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFFB8A8C8, false);
        if (pad == null) {
            return;
        }
        int cats = pad.catCount(), max = pad.maxCats();
        g.drawCenteredString(font, Component.translatable("screen.skyblocks.spin_pad.cats", cats, max), SpinPadMenu.CAT_X + 8, SpinPadMenu.CAT_Y + 24, 0xFFE8DDF0);
        g.drawCenteredString(font, Component.translatable("screen.skyblocks.spin_pad.cats_label"), SpinPadMenu.CAT_X + 8, SpinPadMenu.CAT_Y - 14, 0xFFB8A8C8);
        // what it's doing
        int px = 58, w = imageWidth - px - 8;
        boolean stopped = pad.getLevel() != null && pad.getLevel().hasNeighborSignal(pad.getBlockPos());
        Component state;
        int stateColour;
        if (stopped) {
            state = Component.translatable("screen.skyblocks.spin_pad.stopped");
            stateColour = 0xFFFF7A7A;
        } else if (pad.occupied()) {
            int k = pad.catSize();
            state = cats > 0 && k > 1 ? Component.translatable("screen.skyblocks.spin_pad.spinning_big", k, k, k)
                    : Component.translatable("screen.skyblocks.spin_pad.spinning");
            stateColour = 0xFF8FF0A0;
        } else {
            state = Component.translatable(pad.speed() > 0.01F ? "screen.skyblocks.spin_pad.slowing" : "screen.skyblocks.spin_pad.empty");
            stateColour = 0xFFFFC870;
        }
        g.drawString(font, state, px, 20, stateColour, false);
        // speed: a bar
        float speed = Math.max(0.0F, Math.min(1.0F, pad.speed()));
        g.drawString(font, Component.translatable("screen.skyblocks.spin_pad.speed", Math.round(speed * 100), pad.rpm()), px, 34, 0xFFE8DDF0, false);
        bar(g, px, 44, w, speed, 0xFF6A2BB0, ACCENT);
        // energy: made, held
        g.drawString(font, Component.translatable("screen.skyblocks.spin_pad.making", String.format("%,d", pad.lastFe())), px, 54, 0xFF7FE0FF, false);
        int stored = pad.energy.getEnergyStored(), capacity = pad.energy.getMaxEnergyStored();
        g.drawString(font, Component.translatable("screen.skyblocks.spin_pad.stored", compact(stored), compact(capacity)), px, 66, 0xFFE8DDF0, false);
        bar(g, px, 76, w, capacity <= 0 ? 0 : (float) stored / capacity, 0xFF1F6FA0, 0xFF7FE0FF);
        int full = SpinPadBlockEntity.MAX_FE * Math.max(1, cats);
        g.drawString(font, Component.translatable("screen.skyblocks.spin_pad.max", String.format("%,d", full)), px, 84, 0xFF8F8099, false);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, float f, int from, int to) {
        g.fill(x - 1, y - 1, x + w + 1, y + 6, 0xFF3A3344);
        g.fill(x, y, x + w, y + 5, 0xFF1A1522);
        int end = x + Math.round(w * Math.max(0.0F, Math.min(1.0F, f)));
        for (int i = x; i < end; i++) {
            float k = (i - x) / (float) Math.max(1, w);
            int r = (int) (((from >> 16) & 255) * (1 - k) + ((to >> 16) & 255) * k);
            int gg = (int) (((from >> 8) & 255) * (1 - k) + ((to >> 8) & 255) * k);
            int b = (int) ((from & 255) * (1 - k) + (to & 255) * k);
            g.fill(i, y, i + 1, y + 5, 0xFF000000 | r << 16 | gg << 8 | b);
        }
    }

    private static String compact(int fe) {
        if (fe >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", fe / 1_000_000.0);
        }
        if (fe >= 10_000) {
            return String.format(java.util.Locale.ROOT, "%.0fk", fe / 1_000.0);
        }
        return String.format("%,d", fe);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
