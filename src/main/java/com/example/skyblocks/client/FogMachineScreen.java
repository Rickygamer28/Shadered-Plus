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
 * The fog machine's menu (sneak + right-click): how far the fog goes each way from the machine (front, back, left and
 * right as the machine faces, and how high), and the Area button that shows or hides its outline. The world stays
 * visible behind it, with the outline following the values. Shift-click - / + for steps of 4.
 */
public class FogMachineScreen extends Screen {
    private static final int PANEL_W = 200;
    private static final int PANEL_H = 180;
    private static final String[] ROWS = {"front", "back", "left", "right", "height"};

    private final BlockPos pos;
    /** front, back, left, right, height */
    private final int[] values;
    private boolean showArea;

    public FogMachineScreen(BlockPos pos, int front, int back, int left, int right, int height, boolean showArea) {
        super(Component.translatable("screen.skyblocks.fog_machine"));
        this.pos = pos;
        this.values = new int[] {front, back, left, right, height};
        this.showArea = showArea;
    }

    public BlockPos pos() { return pos; }
    public int value(int i) { return values[i]; }
    public boolean showArea() { return showArea; }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        int x = left() + PANEL_W - 56, y = top() + 22;
        for (int i = 0; i < ROWS.length; i++) {
            final int row = i;
            int min = row == 4 ? 1 : 0, max = row == 4 ? FogMachineBlockEntity.MAX_HEIGHT : FogMachineBlockEntity.MAX_REACH;
            addRenderableWidget(Button.builder(Component.literal("-"),
                    b -> values[row] = Mth.clamp(values[row] - (hasShiftDown() ? 4 : 1), min, max)).bounds(x, y + i * 24, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+"),
                    b -> values[row] = Mth.clamp(values[row] + (hasShiftDown() ? 4 : 1), min, max)).bounds(x + 24, y + i * 24, 20, 20).build());
        }
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

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left(), top(), left() + PANEL_W, top() + PANEL_H, 0xC0101018); // (no blur: the outline stays visible)
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left() + PANEL_W / 2, top() + 8, 0xFFFFFF);
        for (int i = 0; i < ROWS.length; i++) {
            graphics.drawString(font, Component.translatable("screen.skyblocks.fog_" + ROWS[i], values[i]), left() + 12, top() + 28 + i * 24, 0xFFFFFF);
        }
    }

    private void send() {
        PacketDistributor.sendToServer(new FogAreaPayload(pos, values[0], values[1], values[2], values[3], values[4], showArea));
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
