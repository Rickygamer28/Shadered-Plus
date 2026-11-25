package com.example.skyblocks.client;

import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * One look for all the multiblocks' screens (the singularity, the power core, the whirlpool tank, the particle
 * accelerator, the weather machine, the star forge): the same dark panel, a header band with its name (and how it
 * stands, on the right) over a line in the machine's own colour, the same slots, bars, flat buttons and quiet text.
 */
public final class MachineUi {
    public static final int BG = 0xF2121620, HEADER = 0xFF1A2130, SLOT = 0xFF1A2130, SLOT_EDGE = 0xFF34405A, TRACK = 0xFF0B0F17;
    public static final int TEXT = 0xFFE6EDF7, SOFT = 0xFFB8C2D2, DIM = 0xFF8A96AA, FAINT = 0xFF5A6578, GOOD = 0xFF8FEFA0, WARN = 0xFFFFC870,
            BAD = 0xFFFF8A7A, RULE = 0xFF252D3C;
    /** How tall the header band is. */
    public static final int HEAD = 18;

    private MachineUi() {}

    /** The panel: an edge in its colour (darker), the body, the header band and its line. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, shade(accent, 0.55F));
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + w, y + HEAD, HEADER);
        g.fill(x, y + HEAD, x + w, y + HEAD + 1, accent);
    }

    /** Its name on the header band, and (if any) how it stands at its right, in its colour. */
    public static void header(GuiGraphics g, Font font, Component title, Component status, int x, int y, int w, int statusColour) {
        g.drawString(font, title, x + 8, y + 5, TEXT, false);
        if (status != null) {
            int room = w - 24 - font.width(title);
            String s = fit(font, status.getString(), room);
            g.drawString(font, s, x + w - 8 - font.width(s), y + 5, statusColour, false);
        }
    }

    public static String fit(Font font, String s, int width) {
        if (font.width(s) <= width) {
            return s;
        }
        return font.plainSubstrByWidth(s, Math.max(0, width - font.width("..."))) + "...";
    }

    public static void label(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text, x, y, DIM, false);
    }

    /** A line across, between parts. */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, RULE);
    }

    /** A slot's bed (the slot at x, y, 16 square). */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        g.fill(x, y, x + 16, y + 16, SLOT);
    }

    public static void slots(GuiGraphics g, AbstractContainerMenu menu, int left, int top) {
        for (Slot s : menu.slots) {
            slot(g, left + s.x, top + s.y);
        }
    }

    /** A bar: its track, filled this much in the colour (darker at its start), a little shine along its top. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int accent) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_EDGE);
        g.fill(x, y, x + w, y + h, TRACK);
        int end = x + Math.round(w * Math.max(0.0F, Math.min(1.0F, f)));
        for (int i = x; i < end; i++) {
            g.fill(i, y, i + 1, y + h, mix(shade(accent, 0.5F), accent, (i - x) / (float) Math.max(1, w)));
        }
        if (end > x) {
            g.fill(x, y, end, y + 1, 0x38FFFFFF);
        }
    }

    public static int shade(int c, float k) {
        return (c & 0xFF000000) | Math.min(255, (int) ((c >> 16 & 255) * k)) << 16 | Math.min(255, (int) ((c >> 8 & 255) * k)) << 8
                | Math.min(255, (int) ((c & 255) * k));
    }

    public static int mix(int a, int b, float k) {
        int r = (int) ((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k);
        int gg = (int) ((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k);
        int bb = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | r << 16 | gg << 8 | bb;
    }

    /** A flat button in the machine's colour: the chosen one (not pressable: it's what's on) filled with its colour. */
    public static Button button(int x, int y, int w, int h, Component message, Button.OnPress press, IntSupplier accent) {
        return new Flat(x, y, w, h, message, press, accent);
    }

    public static final class Flat extends Button {
        private final IntSupplier accent;
        /** Shown as on (its colour) though still pressable: a toggle that's on. */
        public boolean lit;

        Flat(int x, int y, int w, int h, Component message, OnPress press, IntSupplier accent) {
            super(x, y, w, h, message, press, DEFAULT_NARRATION);
            this.accent = accent;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int a = accent.getAsInt();
            boolean on = !active || lit, hover = active && isHoveredOrFocused();
            int x = getX(), y = getY();
            g.fill(x, y, x + width, y + height, on ? mix(HEADER, a, 0.35F) : hover ? 0xFF2A3447 : 0xFF202838);
            g.fill(x, y + height - 1, x + width, y + height, on ? a : hover ? shade(a, 0.7F) : 0xFF2C3648);
            Font font = Minecraft.getInstance().font;
            String s = fit(font, getMessage().getString(), width - 6);
            g.drawString(font, s, x + (width - font.width(s)) / 2, y + (height - 8) / 2, on || hover ? TEXT : SOFT, false);
        }
    }
}
