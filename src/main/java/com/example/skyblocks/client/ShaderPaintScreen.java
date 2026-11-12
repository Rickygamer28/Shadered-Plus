package com.example.skyblocks.client;

import com.example.skyblocks.InfusionAltarBlockEntity;
import com.example.skyblocks.ModComponents;
import com.example.skyblocks.ShaderPaintPayload;
import com.example.skyblocks.ShaderPaintStyles;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The infusion altar's shader paint editor, laid out like a paint program: tools down the left (pencil, eraser, bucket
 * fill, picker, rectangle, line; brush size; undo / redo; grid; all / clear), the item's texture big in the middle to
 * paint on, and on the right the shaders to paint with - each its own live swatch - over the item itself, turning, as
 * it will look. Left mouse paints with the tool, right mouse erases.
 */
public class ShaderPaintScreen extends Screen {
    private enum Tool { PENCIL, ERASER, FILL, PICKER, RECT, LINE }

    private final BlockPos pos;
    private ItemStack item = ItemStack.EMPTY;
    private TextureAtlasSprite sprite;
    private int w, h;
    private byte[] pixels;
    private final ArrayDeque<byte[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Tool tool = Tool.PENCIL;
    private int style = ShaderPaintStyles.LIVE, brush = 1;
    private boolean grid = true;
    // the canvas on screen; the swatches
    private int cx, cy, cell;
    private int px0, py0, swatch = 18, cols = 5, rowsShown, scroll;
    private final int[] order = ShaderPaintStyles.order();
    // a stroke in progress
    private boolean dragging, erasing;
    private int startX = -1, startY = -1, hoverX = -1, hoverY = -1;
    private final Button[] toolButtons = new Button[Tool.values().length];
    private Component problem;

    public ShaderPaintScreen(BlockPos pos) {
        super(Component.translatable("screen.skyblocks.shader_paint"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof InfusionAltarBlockEntity altar) {
            item = altar.item().copy();
        }
        problem = null;
        if (item.isEmpty()) {
            problem = Component.translatable("message.skyblocks.infusion_altar.empty");
        } else {
            BakedModel model = mc.getItemRenderer().getModel(item, mc.level, mc.player, 0);
            sprite = ShaderPaintRender.sprite(model);
            if (sprite == null || model.isCustomRenderer()) {
                problem = Component.translatable("screen.skyblocks.shader_paint.cannot");
            } else {
                w = sprite.contents().width();
                h = sprite.contents().height();
                if (w > 64 || h > 64) {
                    problem = Component.translatable("screen.skyblocks.shader_paint.too_big");
                }
            }
        }
        if (pixels == null && problem == null) {
            pixels = new byte[w * h];
            ModComponents.ShaderPaint had = item.get(ModComponents.SHADER_PAINT.get());
            if (had != null && had.w() == w && had.h() == h) {
                pixels = had.pixels().clone();
            }
        }
        // layout: tools | canvas | shaders + preview
        int left = 8, toolW = 76, right = cols * (swatch + 2) + 16;
        int room = Math.min(height - 64, width - toolW - right - 40);
        cell = Math.max(2, room / Math.max(1, Math.max(w, h)));
        cx = left + toolW + 12 + Math.max(0, (width - toolW - right - 40 - cell * w) / 2);
        cy = 28 + Math.max(0, (height - 64 - cell * h) / 2);
        px0 = width - right - 4;
        py0 = 28;
        rowsShown = Math.max(3, Math.min((order.length + cols - 1) / cols, (height - py0 - 150) / (swatch + 2)));
        int y = 28;
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            Tool t = tools[i];
            toolButtons[i] = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.tool." + t.name().toLowerCase()), b -> {
                tool = t;
                refreshTools();
            }).bounds(left, y, toolW, 18).build());
            y += 20;
        }
        y += 4;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> brush = Math.max(1, brush - 1)).bounds(left, y, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> brush = Math.min(8, brush + 1)).bounds(left + toolW - 18, y, 18, 18).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.undo"), b -> undo()).bounds(left, y, toolW / 2 - 1, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.redo"), b -> redo()).bounds(left + toolW / 2 + 1, y, toolW / 2 - 1, 18).build());
        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.grid"), b -> grid = !grid).bounds(left, y, toolW, 18).build());
        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.all"), b -> fillAll((byte) style)).bounds(left, y, toolW / 2 - 1, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.clear"), b -> fillAll((byte) 0)).bounds(left + toolW / 2 + 1, y, toolW / 2 - 1, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.apply"), b -> apply())
                .bounds(width / 2 - 104, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 + 4, height - 26, 100, 20).build());
        refreshTools();
    }

    private void refreshTools() {
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            if (toolButtons[i] != null) {
                toolButtons[i].active = tools[i] != tool;
            }
        }
    }

    // ---- painting ----

    private boolean opaque(int x, int y) {
        return sprite != null && ShaderPaintRender.opaque(sprite, x, y);
    }

    private void set(int x, int y, byte s) {
        if (x >= 0 && y >= 0 && x < w && y < h && (s == 0 || opaque(x, y))) {
            pixels[y * w + x] = s;
        }
    }

    private void dab(int x, int y, byte s) {
        int r0 = -(brush - 1) / 2;
        for (int dy = 0; dy < brush; dy++) {
            for (int dx = 0; dx < brush; dx++) {
                set(x + r0 + dx, y + r0 + dy, s);
            }
        }
    }

    private void line(int x0, int y0, int x1, int y1, byte s) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            dab(x0, y0, s);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private void rect(int x0, int y0, int x1, int y1, byte s) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                set(x, y, s);
            }
        }
    }

    /** Fills the patch of the texture of one colour round (x, y) (with shift: that colour everywhere). */
    private void fill(int x, int y, byte s, boolean everywhere) {
        int target = ShaderPaintRender.argb(sprite, x, y);
        if ((target >>> 24) <= 16) {
            return;
        }
        if (everywhere) {
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    if (ShaderPaintRender.argb(sprite, i, j) == target) {
                        set(i, j, s);
                    }
                }
            }
            return;
        }
        boolean[] seen = new boolean[w * h];
        ArrayDeque<int[]> todo = new ArrayDeque<>();
        todo.add(new int[] {x, y});
        seen[y * w + x] = true;
        while (!todo.isEmpty()) {
            int[] p = todo.poll();
            set(p[0], p[1], s);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && !seen[ny * w + nx] && ShaderPaintRender.argb(sprite, nx, ny) == target) {
                    seen[ny * w + nx] = true;
                    todo.add(new int[] {nx, ny});
                }
            }
        }
    }

    private void fillAll(byte s) {
        if (pixels == null) {
            return;
        }
        remember();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                set(x, y, s);
            }
        }
    }

    private void remember() {
        undo.push(pixels.clone());
        if (undo.size() > 64) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void undo() {
        if (!undo.isEmpty()) {
            redo.push(pixels.clone());
            pixels = undo.pop();
        }
    }

    private void redo() {
        if (!redo.isEmpty()) {
            undo.push(pixels.clone());
            pixels = redo.pop();
        }
    }

    private int pixelX(double mx) {
        return mx < cx ? -1 : (int) ((mx - cx) / cell);
    }

    private int pixelY(double my) {
        return my < cy ? -1 : (int) ((my - cy) / cell);
    }

    private boolean onCanvas(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (problem == null) {
            int sw = swatchAt(mx, my);
            if (sw > 0) {
                style = sw;
                if (tool == Tool.ERASER || tool == Tool.PICKER) {
                    tool = Tool.PENCIL;
                    refreshTools();
                }
                return true;
            }
            int x = pixelX(mx), y = pixelY(my);
            if (onCanvas(x, y) && (button == 0 || button == 1)) {
                erasing = button == 1 || tool == Tool.ERASER;
                byte s = erasing ? 0 : (byte) style;
                switch (tool) {
                    case PICKER -> {
                        int got = pixels[y * w + x] & 255;
                        if (got > 0) {
                            style = got;
                        }
                        tool = Tool.PENCIL;
                        refreshTools();
                    }
                    case FILL -> {
                        remember();
                        fill(x, y, s, hasShiftDown());
                    }
                    default -> {
                        remember();
                        startX = x;
                        startY = y;
                        dragging = true;
                        if (tool == Tool.PENCIL || tool == Tool.ERASER) {
                            dab(x, y, s);
                        }
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            int x = pixelX(mx), y = pixelY(my);
            if ((tool == Tool.PENCIL || tool == Tool.ERASER) && onCanvas(x, y)) {
                line(startX, startY, x, y, erasing ? 0 : (byte) style);
                startX = x;
                startY = y;
            }
            hoverX = x;
            hoverY = y;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            int x = Math.max(0, Math.min(w - 1, pixelX(mx))), y = Math.max(0, Math.min(h - 1, pixelY(my)));
            byte s = erasing ? 0 : (byte) style;
            if (tool == Tool.RECT) {
                rect(startX, startY, x, y, s);
            } else if (tool == Tool.LINE) {
                line(startX, startY, x, y, s);
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= px0) {
            int rows = (order.length + cols - 1) / cols;
            scroll = Math.max(0, Math.min(rows - rowsShown, scroll + (sy > 0 ? -1 : 1)));
            return true;
        }
        brush = Math.max(1, Math.min(8, brush + (sy > 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) redo(); else undo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        Tool t = switch (key) {
            case GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_P -> Tool.PENCIL;
            case GLFW.GLFW_KEY_E -> Tool.ERASER;
            case GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_F -> Tool.FILL;
            case GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_I -> Tool.PICKER;
            case GLFW.GLFW_KEY_R -> Tool.RECT;
            case GLFW.GLFW_KEY_L -> Tool.LINE;
            default -> null;
        };
        if (t != null && !ctrl) {
            tool = t;
            refreshTools();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT_BRACKET) { brush = Math.max(1, brush - 1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT_BRACKET) { brush = Math.min(8, brush + 1); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private int swatchAt(double mx, double my) {
        if (mx < px0 || my < py0) {
            return -1;
        }
        int col = (int) ((mx - px0) / (swatch + 2)), row = (int) ((my - py0) / (swatch + 2));
        int i = (row + scroll) * cols + col;
        return col < cols && row < rowsShown && i < order.length ? order[i] : -1;
    }

    private void apply() {
        if (problem == null && pixels != null) {
            PacketDistributor.sendToServer(new ShaderPaintPayload(pos, new ModComponents.ShaderPaint(w, h, pixels.clone())));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFE8D8FF);
        if (problem != null) {
            g.drawCenteredString(font, problem, width / 2, height / 2, 0xFFFF8080);
            return;
        }
        hoverX = pixelX(mouseX);
        hoverY = pixelY(mouseY);
        int left = 8, toolW = 76;
        g.drawCenteredString(font, Component.translatable("screen.skyblocks.shader_paint.brush", brush), left + toolW / 2, 28 + Tool.values().length * 20 + 9, 0xFFFFFFFF);
        // the canvas: a checkerboard under the texture, the texture, the painted shaders live over it, the grid
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                g.fill(cx + x * cell, cy + y * cell, cx + (x + 1) * cell, cy + (y + 1) * cell, ((x + y) & 1) == 0 ? 0xFF3A3A44 : 0xFF2C2C34);
            }
        }
        g.blit(cx, cy, 0, w * cell, h * cell, sprite);
        drawPaint(g, cx, cy, cell);
        if (grid && cell >= 6) {
            for (int x = 0; x <= w; x++) {
                g.fill(cx + x * cell, cy, cx + x * cell + 1, cy + h * cell, 0x30FFFFFF);
            }
            for (int y = 0; y <= h; y++) {
                g.fill(cx, cy + y * cell, cx + w * cell, cy + y * cell + 1, 0x30FFFFFF);
            }
        }
        g.renderOutline(cx - 1, cy - 1, w * cell + 2, h * cell + 2, 0xFF8866CC);
        // where the tool will go: the brush under the mouse, or the rectangle / line being dragged
        if (onCanvas(hoverX, hoverY) || dragging) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 50);
            if (dragging && (tool == Tool.RECT || tool == Tool.LINE)) {
                int x0 = Math.min(startX, hoverX), x1 = Math.max(startX, hoverX), y0 = Math.min(startY, hoverY), y1 = Math.max(startY, hoverY);
                if (tool == Tool.RECT) {
                    g.renderOutline(cx + x0 * cell, cy + y0 * cell, (x1 - x0 + 1) * cell, (y1 - y0 + 1) * cell, 0xFFFFFFFF);
                } else {
                    g.fill(cx + startX * cell + cell / 2, cy + startY * cell + cell / 2, cx + hoverX * cell + cell / 2 + 1, cy + hoverY * cell + cell / 2 + 1, 0xA0FFFFFF);
                }
            } else if (tool == Tool.PENCIL || tool == Tool.ERASER) {
                int r0 = -(brush - 1) / 2;
                g.renderOutline(cx + (hoverX + r0) * cell, cy + (hoverY + r0) * cell, brush * cell, brush * cell, tool == Tool.ERASER ? 0xFFFF6060 : 0xFFFFFFFF);
            } else {
                g.renderOutline(cx + hoverX * cell, cy + hoverY * cell, cell, cell, 0xFFFFFF80);
            }
            g.pose().popPose();
        }
        // the shaders to paint with
        g.drawString(font, Component.translatable("screen.skyblocks.shader_paint.shaders"), px0, py0 - 11, 0xFFD0C0F0);
        drawSwatches(g);
        int sel = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == style) {
                sel = i;
            }
        }
        int selRow = sel / cols - scroll;
        g.pose().pushPose();
        g.pose().translate(0, 0, 60);
        if (selRow >= 0 && selRow < rowsShown) {
            g.renderOutline(px0 + (sel % cols) * (swatch + 2) - 1, py0 + selRow * (swatch + 2) - 1, swatch + 2, swatch + 2, 0xFFFFFFFF);
        }
        int totalRows = (order.length + cols - 1) / cols;
        if (totalRows > rowsShown) {
            // (a scroll bar)
            int barH = rowsShown * (swatch + 2), knob = Math.max(8, barH * rowsShown / totalRows);
            int knobY = py0 + (barH - knob) * scroll / Math.max(1, totalRows - rowsShown);
            g.fill(px0 + cols * (swatch + 2) + 2, py0, px0 + cols * (swatch + 2) + 5, py0 + barH, 0x40FFFFFF);
            g.fill(px0 + cols * (swatch + 2) + 2, knobY, px0 + cols * (swatch + 2) + 5, knobY + knob, 0xC0D0C0F0);
        }
        int hover = swatchAt(mouseX, mouseY);
        g.pose().popPose();
        int below = py0 + rowsShown * (swatch + 2) + 4;
        g.drawString(font, Component.translatable("shader_paint.skyblocks." + ShaderPaintStyles.NAMES[style]), px0, below, 0xFFFFFFFF);
        // the item as it'll look, turning
        drawPreview(g, px0 + (cols * (swatch + 2)) / 2, below + 14 + 50, partialTick);
        if (!Minecraft.getInstance().player.getAbilities().instabuild) {
            g.drawCenteredString(font, Component.translatable("screen.skyblocks.shader_paint.cost"), width / 2, height - 38, 0xFFB0A0D0);
        }
        if (hover > 0) {
            g.renderTooltip(font, Component.translatable("shader_paint.skyblocks." + ShaderPaintStyles.NAMES[hover]), mouseX, mouseY);
        } else if (onCanvas(hoverX, hoverY) && pixels[hoverY * w + hoverX] != 0) {
            g.renderTooltip(font, Component.translatable("shader_paint.skyblocks." + ShaderPaintStyles.NAMES[pixels[hoverY * w + hoverX] & 255]), mouseX, mouseY);
        }
    }

    private void drawPaint(GuiGraphics g, int x0, int y0, int c) {
        if (ShaderPaintRender.shader == null) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 10);
        var m = g.pose().last().pose();
        boolean[] has = new boolean[256];
        for (byte b : pixels) {
            has[b & 255] = true;
        }
        for (int s = 1; s < ShaderPaintStyles.count(); s++) {
            if (!has[s]) {
                continue;
            }
            var type = ShaderPaintRender.type(s);
            VertexConsumer vc = g.bufferSource().getBuffer(type);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((pixels[y * w + x] & 255) == s) {
                        ShaderPaintRender.square(vc, m, x0 + x * c, y0 + y * c, c, c, s, x / (float) w, y / (float) h, (x + 1) / (float) w, (y + 1) / (float) h, w);
                    }
                }
            }
            g.bufferSource().endBatch(type);
        }
        g.pose().popPose();
    }

    private void drawSwatches(GuiGraphics g) {
        if (ShaderPaintRender.shader == null) {
            return;
        }
        var m = g.pose().last().pose();
        for (int i = scroll * cols; i < Math.min(order.length, (scroll + rowsShown) * cols); i++) {
            int s = order[i];
            int col = i % cols, row = i / cols - scroll;
            var type = ShaderPaintRender.type(s);
            VertexConsumer vc = g.bufferSource().getBuffer(type);
            ShaderPaintRender.square(vc, m, px0 + col * (swatch + 2), py0 + row * (swatch + 2), swatch, swatch, s, 0, 0, 1, 1, 8);
            g.bufferSource().endBatch(type);
        }
    }

    private void drawPreview(GuiGraphics g, int x, int y, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack preview = item.copy();
        boolean any = false;
        for (byte b : pixels) {
            any |= b != 0;
        }
        if (any) {
            preview.set(ModComponents.SHADER_PAINT.get(), new ModComponents.ShaderPaint(w, h, pixels.clone()));
        } else {
            preview.remove(ModComponents.SHADER_PAINT.get());
        }
        float turn = (mc.level == null ? 0 : mc.level.getGameTime() + partialTick) * 2.5F;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 150);
        pose.scale(96, -96, 96);
        pose.mulPose(Axis.XP.rotationDegrees(-12));
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        Lighting.setupForEntityInInventory();
        mc.getItemRenderer().renderStatic(preview, ItemDisplayContext.FIXED, 0x00F000F0, OverlayTexture.NO_OVERLAY, pose, g.bufferSource(), mc.level, 0);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0x60100818);
    }
}
