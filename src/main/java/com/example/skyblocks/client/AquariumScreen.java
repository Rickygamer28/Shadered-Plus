package com.example.skyblocks.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The aquarium's menu (sneak + right-click): turn each kind of fish on or off for every aquarium of this type
 * (normal or ocean). The tanks fill up with the kinds left on; big animals only in tanks big enough for them.
 */
public class AquariumScreen extends Screen {
    private static final int PANEL_W = 272;
    private static final int PANEL_H = 196;

    private final boolean ocean;

    public AquariumScreen(boolean ocean) {
        super(Component.translatable(ocean ? "screen.skyblocks.ocean_aquarium" : "screen.skyblocks.aquarium"));
        this.ocean = ocean;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    private int first() {
        return ocean ? 10 : 0;
    }

    @Override
    protected void init() {
        clearWidgets();
        for (int n = 0; n < 10; n++) {
            final int kind = first() + n;
            int x = left() + 12 + (n % 2) * 128, y = top() + 26 + (n / 2) * 24;
            Button b = Button.builder(label(kind), btn -> {
                AquariumSettings.set(kind, !AquariumSettings.enabled(kind));
                btn.setMessage(label(kind));
            }).bounds(x, y, 120, 20).build();
            int[] need = AquariumRenderer.tankNeeds(kind);
            if (need[0] > 1 || need[1] > 1) {
                b.setTooltip(Tooltip.create(Component.translatable("screen.skyblocks.aquarium_needs", need[0], need[0], need[1])));
            }
            addRenderableWidget(b);
        }
        int y = top() + PANEL_H - 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.aquarium_all_on"), b -> setAll(true))
                .bounds(left() + 12, y, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.aquarium_all_off"), b -> setAll(false))
                .bounds(left() + 96, y, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + PANEL_W - 88, y, 76, 20).build());
    }

    private void setAll(boolean on) {
        for (int n = 0; n < 10; n++) {
            AquariumSettings.set(first() + n, on);
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
        graphics.fill(left(), top(), left() + PANEL_W, top() + PANEL_H, 0xC0101018);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left() + PANEL_W / 2, top() + 9, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.skyblocks.aquarium_hint"), left() + PANEL_W / 2, top() + 148, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
