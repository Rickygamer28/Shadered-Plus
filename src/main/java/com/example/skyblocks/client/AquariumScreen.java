package com.example.skyblocks.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The aquarium's menu (sneak + right-click): turn each kind of fish and bottom creature on or off for every aquarium
 * of this type (normal or ocean). The tanks fill up with the kinds left on; big animals only in tanks big enough.
 */
public class AquariumScreen extends Screen {
    private static final int COLS = 3;
    private static final int BTN_W = 124;
    private static final int PANEL_W = COLS * (BTN_W + 4) + 20;

    private final boolean ocean;
    private final boolean deep;

    public AquariumScreen(boolean ocean, boolean deep) {
        super(Component.translatable(deep ? "screen.skyblocks.deep_sea_aquarium" : ocean ? "screen.skyblocks.ocean_aquarium" : "screen.skyblocks.aquarium"));
        this.ocean = ocean;
        this.deep = deep;
    }

    /** As tall as its rows of buttons need (the ocean aquarium has more kinds). */
    private int panelH() {
        return 24 + (kinds().length + COLS - 1) / COLS * 23 + 99;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - panelH()) / 2;
    }

    private int[] kinds() {
        return deep ? AquariumRenderer.DEEP_KINDS : ocean ? AquariumRenderer.OCEAN_KINDS : AquariumRenderer.NORMAL_KINDS;
    }

    /** Is this kind in more than one type of aquarium (one setting for them all)? */
    private static boolean shared(int kind) {
        int in = 0;
        for (int[] list : new int[][] {AquariumRenderer.NORMAL_KINDS, AquariumRenderer.OCEAN_KINDS, AquariumRenderer.DEEP_KINDS}) {
            for (int k : list) {
                if (k == kind) {
                    in++;
                    break;
                }
            }
        }
        return in > 1;
    }

    @Override
    protected void init() {
        clearWidgets();
        int[] kinds = kinds();
        for (int n = 0; n < kinds.length; n++) {
            final int kind = kinds[n];
            int x = left() + 12 + (n % COLS) * (BTN_W + 4), y = top() + 24 + (n / COLS) * 23;
            Button b = Button.builder(label(kind), btn -> {
                AquariumSettings.set(kind, !AquariumSettings.enabled(kind));
                btn.setMessage(label(kind));
            }).bounds(x, y, BTN_W, 20).build();
            int[] need = AquariumRenderer.tankNeeds(kind);
            Component tip = null;
            if (need[0] > 1 || need[1] > 1) {
                tip = Component.translatable("screen.skyblocks.aquarium_needs", need[0], need[0], need[1]);
            }
            if (shared(kind)) {
                Component s = Component.translatable("screen.skyblocks.aquarium_shared");
                tip = tip == null ? s : Component.empty().append(tip).append("\n").append(s);
            }
            if (tip != null) {
                b.setTooltip(Tooltip.create(tip));
            }
            addRenderableWidget(b);
        }
        int y = top() + panelH() - 28;
        if (!deep) { // (the deep sea: always dark)
            addRenderableWidget(Button.builder(lightLabel(), b -> {
                AquariumSettings.setLight(ocean, AquariumSettings.light(ocean) + 1);
                b.setMessage(lightLabel());
            }).bounds(left() + 12, y - 24, 160, 20).tooltip(Tooltip.create(Component.translatable("screen.skyblocks.aquarium_light_tip"))).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.aquarium_all_on"), b -> setAll(true))
                .bounds(left() + 12, y, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.aquarium_all_off"), b -> setAll(false))
                .bounds(left() + 96, y, 76, 20).build());
        addRenderableWidget(Button.builder(nightLabel(), b -> {
            AquariumSettings.setNightMode(!AquariumSettings.nightMode());
            b.setMessage(nightLabel());
        }).bounds(left() + 180, y, 116, 20).tooltip(Tooltip.create(Component.translatable("screen.skyblocks.aquarium_night_tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + PANEL_W - 88, y, 76, 20).build());
    }

    private Component lightLabel() {
        return Component.translatable("screen.skyblocks.aquarium_light")
                .append(": ").append(Component.translatable("screen.skyblocks.aquarium_light." + AquariumSettings.LIGHTS[AquariumSettings.light(ocean)])
                        .withStyle(ChatFormatting.AQUA));
    }

    private static Component nightLabel() {
        boolean on = AquariumSettings.nightMode();
        return Component.translatable("screen.skyblocks.aquarium_night")
                .append(Component.literal(on ? ": On" : ": Off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private void setAll(boolean on) {
        for (int k : kinds()) {
            AquariumSettings.set(k, on);
        }
        init();
    }

    private static Component label(int kind) {
        boolean on = AquariumSettings.enabled(kind);
        return Component.translatable("fish.skyblocks." + AquariumSettings.NAMES[kind])
                .append(Component.literal(on ? ": On" : ": Off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left(), top(), left() + PANEL_W, top() + panelH(), 0xC0101018);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left() + PANEL_W / 2, top() + 9, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.skyblocks.aquarium_hint"), left() + PANEL_W / 2, top() + panelH() - 68, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
