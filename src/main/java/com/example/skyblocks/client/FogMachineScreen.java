package com.example.skyblocks.client;

import com.example.skyblocks.FogAreaPayload;
import com.example.skyblocks.FogMachineBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The fog machine's menu (sneak + right-click): the fog's area (width, depth, height) and the Area button that shows
 * or hides its outline in the world. The world stays visible behind it, with the outline following the values.
 * Shift-click - / + for steps of 4.
 */
public class FogMachineScreen extends Screen {
    private static final int PANEL_W = 200;
    private static final int PANEL_H = 132;

    private final BlockPos pos;
    private int width, depth, height;
    private boolean showArea;

    public FogMachineScreen(BlockPos pos, int width, int depth, int height, boolean showArea) {
        super(Component.translatable("screen.skyblocks.fog_machine"));
        this.pos = pos;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.showArea = showArea;
    }

    public BlockPos pos() { return pos; }
    public int fogWidth() { return width; }
    public int fogDepth() { return depth; }
    public int fogHeight() { return height; }
    public boolean showArea() { return showArea; }

    private int left() {
        return (super.width - PANEL_W) / 2;
    }

    private int top() {
        return (super.height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        int x = left() + PANEL_W - 56, y = top() + 24;
        addStepper(x, y, d -> width = Mth.clamp(width + d, 1, FogMachineBlockEntity.MAX_WIDTH));
        addStepper(x, y + 24, d -> depth = Mth.clamp(depth + d, 1, FogMachineBlockEntity.MAX_DEPTH));
        addStepper(x, y + 48, d -> height = Mth.clamp(height + d, 1, FogMachineBlockEntity.MAX_HEIGHT));
        addRenderableWidget(Button.builder(areaLabel(), b -> {
            showArea = !showArea;
            b.setMessage(areaLabel());
            send();
        }).bounds(left() + 12, top() + PANEL_H - 28, 84, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + PANEL_W - 96, top() + PANEL_H - 28, 84, 20).build());
    }

    private Component areaLabel() {
        return Component.translatable(showArea ? "screen.skyblocks.area_shown" : "screen.skyblocks.area_hidden");
    }

    private void addStepper(int x, int y, java.util.function.IntConsumer change) {
        addRenderableWidget(Button.builder(Component.literal("-"), b -> change.accept(hasShiftDown() ? -4 : -1)).bounds(x, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> change.accept(hasShiftDown() ? 4 : 1)).bounds(x + 24, y, 20, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left(), top(), left() + PANEL_W, top() + PANEL_H, 0xC0101018); // (no blur: the outline stays visible)
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left() + 12, y = top() + 30;
        graphics.drawCenteredString(font, title, left() + PANEL_W / 2, top() + 8, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.skyblocks.fog_width", width), x, y, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.skyblocks.fog_depth", depth), x, y + 24, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.skyblocks.fog_height", height), x, y + 48, 0xFFFFFF);
    }

    private void send() {
        PacketDistributor.sendToServer(new FogAreaPayload(pos, width, depth, height, showArea));
    }

    @Override
    public void onClose() {
        send();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
