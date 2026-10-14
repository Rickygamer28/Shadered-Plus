package com.example.skyblocks.client.island;

import com.example.skyblocks.island.IslandScan;
import com.example.skyblocks.island.ScanIslandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The Island Scanner's menu: area size (3x3 to 25x25, centred on the scanner), how far up and down to scan, and
 * the Scan button. The world stays visible behind it, with the area outlined (IslandScannerRenderer).
 * Shift-click the - / + buttons for bigger steps.
 */
public class IslandScannerScreen extends Screen {
    private static final int PANEL_W = 200;
    private static final int PANEL_H = 156;

    private final BlockPos pos;
    private int size;
    private int above;
    private int depth;
    private boolean showArea;
    /** Closed by the Scan button (the scan request already carried the settings). */
    private boolean scanned;

    public IslandScannerScreen(BlockPos pos, int size, int above, int depth, boolean showArea) {
        super(Component.translatable("screen.skyblocks.island_scanner"));
        this.pos = pos;
        this.size = size;
        this.above = above;
        this.depth = depth;
        this.showArea = showArea;
    }

    public BlockPos pos() {
        return pos;
    }

    public int size() {
        return size;
    }

    public int above() {
        return above;
    }

    public int depth() {
        return depth;
    }

    public boolean showArea() {
        return showArea;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        int x = left() + PANEL_W - 56, y = top() + 24;
        addStepper(x, y, () -> size = IslandScan.clampSize(size - (hasShiftDown() ? 6 : 2)),
                () -> size = IslandScan.clampSize(size + (hasShiftDown() ? 6 : 2)));
        addStepper(x, y + 24, () -> above = IslandScan.clampAbove(above - (hasShiftDown() ? 5 : 1)),
                () -> above = IslandScan.clampAbove(above + (hasShiftDown() ? 5 : 1)));
        addStepper(x, y + 48, () -> depth = IslandScan.clampDepth(depth - (hasShiftDown() ? 5 : 1)),
                () -> depth = IslandScan.clampDepth(depth + (hasShiftDown() ? 5 : 1)));

        // Area: shows / hides the outline of the area in the world (stays after closing the menu).
        addRenderableWidget(Button.builder(areaLabel(), b -> {
            showArea = !showArea;
            b.setMessage(areaLabel());
            send(false);
        }).bounds(left() + 12, y + 72, PANEL_W - 24, 20).build());

        int by = top() + PANEL_H - 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.scan"), b -> {
            send(true);
            scanned = true;
            onClose();
        }).bounds(left() + 12, by, 84, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + PANEL_W - 96, by, 84, 20).build());
    }

    private Component areaLabel() {
        return Component.translatable(showArea ? "screen.skyblocks.area_shown" : "screen.skyblocks.area_hidden");
    }

    private void addStepper(int x, int y, Runnable minus, Runnable plus) {
        addRenderableWidget(Button.builder(Component.literal("-"), b -> minus.run()).bounds(x, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> plus.run()).bounds(x + 24, y, 20, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // no dimming, so the outlined area stays visible; just a panel behind the controls
        graphics.fill(left(), top(), left() + PANEL_W, top() + PANEL_H, 0xC0101018);
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left() + 12, y = top() + 30;
        graphics.drawCenteredString(font, title, left() + PANEL_W / 2, top() + 8, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.skyblocks.island_size", size, size), x, y, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.skyblocks.island_above", above), x, y + 24, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.skyblocks.island_depth", depth), x, y + 48, 0xFFFFFF);
    }

    private void send(boolean scan) {
        ScanIslandPayload.send(new ScanIslandPayload(pos, size, above, depth, showArea, scan));
    }

    /** Closing keeps the settings (sends them without scanning). */
    @Override
    public void onClose() {
        if (!scanned) {
            send(false);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
