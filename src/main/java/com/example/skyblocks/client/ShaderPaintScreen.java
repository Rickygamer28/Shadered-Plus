package com.example.skyblocks.client;

import com.example.skyblocks.InfusionAltarBlockEntity;
import com.example.skyblocks.ModComponents;
import com.example.skyblocks.ShaderPaintPayload;
import com.example.skyblocks.ShaderPaintStyles;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
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
 * The infusion altar's editor, laid out like a paint program: the tools down the left (pencil, eraser, bucket fill,
 * picker, rectangle, line; brush size; undo / redo; grid; all / clear; back to the original), then the colours the
 * item's texture uses (pick one, or type any colour) to redraw the texture itself with - add pixels, recolour them, rub
 * them out - then the texture big in the middle to paint on, and on the right Shadered's sky blocks' skies to paint
 * onto it (each sky seen through the look chosen: normal, inverted, posterised, blueprint, game boy...) over the item
 * itself, turning, as it will look. Left mouse paints with the tool, right mouse rubs out.
 */
public class ShaderPaintScreen extends Screen {
    private enum Tool { PENCIL, ERASER, FILL, PICKER, RECT, LINE }

    private final BlockPos pos;
    private ItemStack item = ItemStack.EMPTY;
    private TextureAtlasSprite sprite;
    private int w, h;
    /** The texture as it's being redrawn (ARGB), as it was, and the skies painted on it (styles: ShaderPaintStyles). */
    private int[] colours, original;
    private byte[] skies;
    private final ArrayDeque<Object[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Tool tool = Tool.PENCIL;
    /** Painting with a sky (else a colour), which, through which look; the colour. */
    private boolean skyBrush = true;
    private int sky, pass, colour = 0xFFFFFFFF, brush = 1;
    private boolean grid = true;
    // the canvas, the colours, the skies on screen
    private int cx, cy, cell;
    private int colX, colY, colCols = 2, colRows;
    private int px0, py0, swatch = 18, cols = 5, rowsShown, scroll, colScroll;
    private final List<Integer> used = new ArrayList<>();
    private EditBox hex;
    private Button lookButton;
    // a stroke in progress
    private boolean dragging, erasing;
    private int startX = -1, startY = -1, hoverX = -1, hoverY = -1;
    private final Button[] toolButtons = new Button[Tool.values().length];
    private Component problem;

    public ShaderPaintScreen(BlockPos pos) {
        super(Component.translatable("screen.skyblocks.shader_paint"));
        this.pos = pos;
    }

    private int skyCount() {
        return ShaderPaintRender.skies().length;
    }

    private int style() {
        return ShaderPaintStyles.of(sky, pass);
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
            ItemStack plain = item.copy();
            plain.remove(ModComponents.ITEM_TEXTURE.get());
            BakedModel model = mc.getItemRenderer().getModel(plain, mc.level, mc.player, 0);
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
        if (colours == null && problem == null) {
            original = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    original[y * w + x] = ShaderPaintRender.argb(sprite, x, y);
                }
            }
            colours = original.clone();
            ModComponents.ItemTexture tex = item.get(ModComponents.ITEM_TEXTURE.get());
            if (tex != null && tex.w() == w && tex.h() == h) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        colours[y * w + x] = tex.at(x, y);
                    }
                }
            }
            skies = new byte[w * h];
            ModComponents.ShaderPaint had = item.get(ModComponents.SHADER_PAINT.get());
            if (had != null && had.w() == w && had.h() == h) {
                skies = had.pixels().clone();
            }
            refreshUsed();
            for (int c : used) {
                colour = c; // (start with the brightest it uses)
            }
        }
        // layout: tools | colours | canvas | skies + preview
        int left = 8, toolW = 76, colW = colCols * 14 + 8, right = cols * (swatch + 2) + 16;
        colX = left + toolW + 8;
        colY = 28 + 40;
        colRows = Math.max(3, (height - colY - 40) / 14);
        int room = Math.min(height - 64, width - toolW - colW - right - 48);
        cell = Math.max(2, room / Math.max(1, Math.max(w, h)));
        cx = colX + colW + 8 + Math.max(0, (width - (colX + colW + 8) - right - 12 - cell * w) / 2);
        cy = 28 + Math.max(0, (height - 64 - cell * h) / 2);
        px0 = width - right - 4;
        py0 = 28;
        rowsShown = Math.max(3, Math.min((skyCount() + cols - 1) / cols, (height - py0 - 170) / (swatch + 2)));
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
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.all"), b -> skyAll((byte) style()))
                .bounds(left, y, toolW / 2 - 1, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.clear"), b -> skyAll((byte) 0))
                .bounds(left + toolW / 2 + 1, y, toolW / 2 - 1, 18).build());
        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.original"), b -> {
            if (colours != null) {
                remember();
                colours = original.clone();
                refreshUsed();
            }
        }).bounds(left, y, toolW, 18).build());
        // the colour being painted with, typed in
        hex = new EditBox(font, colX, 28 + 22, colW, 14, Component.translatable("screen.skyblocks.shader_paint.colour"));
        hex.setMaxLength(7);
        hex.setValue(String.format("#%06X", colour & 0xFFFFFF));
        hex.setResponder(s -> {
            String t = s.startsWith("#") ? s.substring(1) : s;
            if (t.length() == 6) {
                try {
                    colour = 0xFF000000 | Integer.parseInt(t, 16);
                    skyBrush = false;
                } catch (NumberFormatException ignored) {
                    // (not a colour yet)
                }
            }
        });
        addRenderableWidget(hex);
        // the look the skies are seen through
        lookButton = addRenderableWidget(Button.builder(lookName(), b -> {
            pass = (pass + 1) % Math.min(ShaderPaintStyles.PASSES, SkyblockPass.values().length);
            b.setMessage(lookName());
            skyBrush = true;
        }).bounds(px0, py0 + rowsShown * (swatch + 2) + 4, cols * (swatch + 2) - 2, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.apply"), b -> apply())
                .bounds(width / 2 - 104, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 + 4, height - 26, 100, 20).build());
        refreshTools();
    }

    private Component lookName() {
        return Component.translatable("screen.skyblocks.shader_paint.look", SkyblockPass.values()[pass].getDisplayName());
    }

    private void refreshTools() {
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            if (toolButtons[i] != null) {
                toolButtons[i].active = tools[i] != tool;
            }
        }
    }

    /** The colours the texture uses now, darkest first. */
    private void refreshUsed() {
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        for (int c : colours) {
            if ((c >>> 24) > 16) {
                set.add(c | 0xFF000000);
            }
        }
        used.clear();
        used.addAll(set);
        used.sort((a, b) -> Integer.compare(luma(a), luma(b)));
        colScroll = Math.max(0, Math.min(colScroll, (used.size() + colCols - 1) / colCols - colRows));
    }

    private static int luma(int c) {
        return ((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255);
    }

    // ---- painting ----

    private boolean opaque(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && (colours[y * w + x] >>> 24) > 16;
    }

    /** One pixel: the brush there (a sky only on a pixel that's there; a colour anywhere), or rubbed out. */
    private void set(int x, int y, boolean rubOut) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return;
        }
        int i = y * w + x;
        if (skyBrush) {
            if (rubOut) {
                skies[i] = 0;
            } else if (opaque(x, y)) {
                skies[i] = (byte) style();
            }
        } else if (rubOut) {
            colours[i] = 0;
            skies[i] = 0;
        } else {
            colours[i] = colour | 0xFF000000;
        }
    }

    private void dab(int x, int y, boolean rubOut) {
        int r0 = -(brush - 1) / 2;
        for (int dy = 0; dy < brush; dy++) {
            for (int dx = 0; dx < brush; dx++) {
                set(x + r0 + dx, y + r0 + dy, rubOut);
            }
        }
    }

    private void line(int x0, int y0, int x1, int y1, boolean rubOut) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            dab(x0, y0, rubOut);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private void rect(int x0, int y0, int x1, int y1, boolean rubOut) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                set(x, y, rubOut);
            }
        }
    }

    /** Fills the patch of one colour round (x, y) (with shift: that colour everywhere) - empty space too, with a colour. */
    private void fill(int x, int y, boolean rubOut, boolean everywhere) {
        int target = colours[y * w + x];
        boolean empty = (target >>> 24) <= 16;
        if (empty && (skyBrush || rubOut)) {
            return;
        }
        boolean[] seen = new boolean[w * h];
        ArrayDeque<int[]> todo = new ArrayDeque<>();
        List<int[]> patch = new ArrayList<>();
        if (everywhere) {
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    if (same(colours[j * w + i], target)) {
                        patch.add(new int[] {i, j});
                    }
                }
            }
        } else {
            todo.add(new int[] {x, y});
            seen[y * w + x] = true;
            while (!todo.isEmpty()) {
                int[] p = todo.poll();
                patch.add(p);
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = p[0] + d[0], ny = p[1] + d[1];
                    if (nx >= 0 && ny >= 0 && nx < w && ny < h && !seen[ny * w + nx] && same(colours[ny * w + nx], target)) {
                        seen[ny * w + nx] = true;
                        todo.add(new int[] {nx, ny});
                    }
                }
            }
        }
        for (int[] p : patch) {
            set(p[0], p[1], rubOut);
        }
    }

    private static boolean same(int a, int b) {
        boolean ea = (a >>> 24) <= 16, eb = (b >>> 24) <= 16;
        return ea || eb ? ea == eb : (a | 0xFF000000) == (b | 0xFF000000);
    }

    private void skyAll(byte s) {
        if (skies == null) {
            return;
        }
        remember();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (s == 0 || opaque(x, y)) {
                    skies[y * w + x] = s;
                }
            }
        }
    }

    private void remember() {
        undo.push(new Object[] {colours.clone(), skies.clone()});
        if (undo.size() > 64) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void undo() {
        if (!undo.isEmpty()) {
            redo.push(new Object[] {colours.clone(), skies.clone()});
            Object[] s = undo.pop();
            colours = (int[]) s[0];
            skies = (byte[]) s[1];
            refreshUsed();
        }
    }

    private void redo() {
        if (!redo.isEmpty()) {
            undo.push(new Object[] {colours.clone(), skies.clone()});
            Object[] s = redo.pop();
            colours = (int[]) s[0];
            skies = (byte[]) s[1];
            refreshUsed();
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
        if (problem == null && !hex.isMouseOver(mx, my)) {
            int sw = skyAt(mx, my);
            if (sw >= 0) {
                sky = sw;
                skyBrush = true;
                if (tool == Tool.PICKER) {
                    tool = Tool.PENCIL;
                    refreshTools();
                }
                return true;
            }
            int cc = colourAt(mx, my);
            if (cc != 0) {
                colour = cc;
                skyBrush = false;
                hex.setValue(String.format("#%06X", colour & 0xFFFFFF));
                skyBrush = false;
                if (tool == Tool.PICKER) {
                    tool = Tool.PENCIL;
                    refreshTools();
                }
                return true;
            }
            int x = pixelX(mx), y = pixelY(my);
            if (onCanvas(x, y) && (button == 0 || button == 1)) {
                erasing = button == 1 || tool == Tool.ERASER;
                switch (tool) {
                    case PICKER -> {
                        int i = y * w + x;
                        if (skyBrush && skies[i] != 0) {
                            sky = ShaderPaintStyles.sky(skies[i] & 255);
                            pass = ShaderPaintStyles.pass(skies[i] & 255);
                            lookButton.setMessage(lookName());
                        } else if (!skyBrush && opaque(x, y)) {
                            colour = colours[i] | 0xFF000000;
                            hex.setValue(String.format("#%06X", colour & 0xFFFFFF));
                            skyBrush = false;
                        }
                        tool = Tool.PENCIL;
                        refreshTools();
                    }
                    case FILL -> {
                        remember();
                        fill(x, y, erasing, hasShiftDown());
                        refreshUsed();
                    }
                    default -> {
                        remember();
                        startX = x;
                        startY = y;
                        dragging = true;
                        if (tool == Tool.PENCIL || tool == Tool.ERASER) {
                            dab(x, y, erasing);
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
                line(startX, startY, x, y, erasing);
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
            if (tool == Tool.RECT) {
                rect(startX, startY, x, y, erasing);
            } else if (tool == Tool.LINE) {
                line(startX, startY, x, y, erasing);
            }
            refreshUsed();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= px0) {
            int rows = (skyCount() + cols - 1) / cols;
            scroll = Math.max(0, Math.min(rows - rowsShown, scroll + (sy > 0 ? -1 : 1)));
            return true;
        }
        if (mx >= colX && mx < cx - 4) {
            int rows = (used.size() + colCols - 1) / colCols;
            colScroll = Math.max(0, Math.min(rows - colRows, colScroll + (sy > 0 ? -1 : 1)));
            return true;
        }
        brush = Math.max(1, Math.min(8, brush + (sy > 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (hex != null && hex.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                hex.setFocused(false);
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
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

    private int skyAt(double mx, double my) {
        if (mx < px0 || my < py0) {
            return -1;
        }
        int col = (int) ((mx - px0) / (swatch + 2)), row = (int) ((my - py0) / (swatch + 2));
        int i = (row + scroll) * cols + col;
        return col < cols && row < rowsShown && i < skyCount() ? i : -1;
    }

    /** The colour swatch under the mouse (its colour), or 0. */
    private int colourAt(double mx, double my) {
        if (mx < colX || my < colY || mx >= colX + colCols * 14) {
            return 0;
        }
        int col = (int) ((mx - colX) / 14), row = (int) ((my - colY) / 14);
        int i = (row + colScroll) * colCols + col;
        return row < colRows && i < used.size() ? used.get(i) : 0;
    }

    /** Does the texture differ from the item's own? */
    private boolean redrawn() {
        for (int i = 0; i < colours.length; i++) {
            if (!same(colours[i], original[i]) || ((colours[i] >>> 24) > 16 && (colours[i] | 0xFF000000) != (original[i] | 0xFF000000))) {
                return true;
            }
        }
        return false;
    }

    private void apply() {
        if (problem == null && skies != null) {
            Optional<ModComponents.ItemTexture> tex = redrawn() ? Optional.of(ModComponents.ItemTexture.of(w, h, colours)) : Optional.empty();
            PacketDistributor.sendToServer(new ShaderPaintPayload(pos, new ModComponents.ShaderPaint(w, h, skies.clone()), tex));
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
        // the colours: the one painting with (big), then each the texture uses
        g.drawString(font, Component.translatable("screen.skyblocks.shader_paint.colours"), colX, 28 - 11, 0xFFD0C0F0);
        g.fill(colX, 28, colX + colCols * 14, 28 + 18, 0xFF000000 | colour);
        g.renderOutline(colX - 1, 27, colCols * 14 + 2, 20, !skyBrush ? 0xFFFFFFFF : 0xFF606060);
        for (int i = colScroll * colCols; i < Math.min(used.size(), (colScroll + colRows) * colCols); i++) {
            int col = i % colCols, row = i / colCols - colScroll;
            int x = colX + col * 14, y = colY + row * 14;
            g.fill(x, y, x + 12, y + 12, used.get(i));
            if (!skyBrush && (used.get(i) | 0xFF000000) == (colour | 0xFF000000)) {
                g.renderOutline(x - 1, y - 1, 14, 14, 0xFFFFFFFF);
            }
        }
        // the canvas: a checkerboard under the texture, the texture as it's being redrawn, the skies painted over it, the grid
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int x0 = cx + x * cell, y0 = cy + y * cell;
                g.fill(x0, y0, x0 + cell, y0 + cell, ((x + y) & 1) == 0 ? 0xFF3A3A44 : 0xFF2C2C34);
                int c = colours[y * w + x];
                if ((c >>> 24) > 16) {
                    g.fill(x0, y0, x0 + cell, y0 + cell, c | 0xFF000000);
                }
            }
        }
        drawSkies(g, cx, cy, cell);
        if (grid && cell >= 6) {
            for (int x = 0; x <= w; x++) {
                g.fill(cx + x * cell, cy, cx + x * cell + 1, cy + h * cell, 0x30FFFFFF);
            }
            for (int y = 0; y <= h; y++) {
                g.fill(cx, cy + y * cell, cx + w * cell, cy + y * cell + 1, 0x30FFFFFF);
            }
        }
        g.renderOutline(cx - 1, cy - 1, w * cell + 2, h * cell + 2, 0xFF8866CC);
        // where the tool will go
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
        // the skies to paint with
        g.drawString(font, Component.translatable("screen.skyblocks.shader_paint.skies"), px0, py0 - 11, 0xFFD0C0F0);
        drawSwatches(g);
        g.pose().pushPose();
        g.pose().translate(0, 0, 60);
        int selRow = sky / cols - scroll;
        if (skyBrush && selRow >= 0 && selRow < rowsShown) {
            g.renderOutline(px0 + (sky % cols) * (swatch + 2) - 1, py0 + selRow * (swatch + 2) - 1, swatch + 2, swatch + 2, 0xFFFFFFFF);
        }
        int totalRows = (skyCount() + cols - 1) / cols;
        if (totalRows > rowsShown) {
            int barH = rowsShown * (swatch + 2), knob = Math.max(8, barH * rowsShown / totalRows);
            int knobY = py0 + (barH - knob) * scroll / Math.max(1, totalRows - rowsShown);
            g.fill(px0 + cols * (swatch + 2) + 2, py0, px0 + cols * (swatch + 2) + 5, py0 + barH, 0x40FFFFFF);
            g.fill(px0 + cols * (swatch + 2) + 2, knobY, px0 + cols * (swatch + 2) + 5, knobY + knob, 0xC0D0C0F0);
        }
        g.pose().popPose();
        int below = py0 + rowsShown * (swatch + 2) + 24;
        g.drawString(font, skyBrush ? ShaderPaintRender.name(style()) : Component.literal(String.format("#%06X", colour & 0xFFFFFF)), px0, below, 0xFFFFFFFF);
        // the item as it'll look, turning
        drawPreview(g, px0 + (cols * (swatch + 2)) / 2, below + 14 + 50, partialTick);
        if (!Minecraft.getInstance().player.getAbilities().instabuild) {
            g.drawCenteredString(font, Component.translatable("screen.skyblocks.shader_paint.cost"), width / 2, height - 38, 0xFFB0A0D0);
        }
        int hover = skyAt(mouseX, mouseY);
        if (hover >= 0) {
            g.renderTooltip(font, ShaderPaintRender.name(ShaderPaintStyles.of(hover, pass)), mouseX, mouseY);
        } else if (onCanvas(hoverX, hoverY) && skies[hoverY * w + hoverX] != 0) {
            g.renderTooltip(font, ShaderPaintRender.name(skies[hoverY * w + hoverX] & 255), mouseX, mouseY);
        }
    }

    private void drawSkies(GuiGraphics g, int x0, int y0, int c) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 40); // (in front of the canvas: the skies' render types draw only nearer than what's there)
        var m = g.pose().last().pose();
        boolean[] has = new boolean[256];
        for (byte b : skies) {
            has[b & 255] = true;
        }
        for (int s = 1; s < 256; s++) {
            RenderType type = has[s] ? ShaderPaintRender.type(s) : null;
            if (type == null) {
                continue;
            }
            VertexConsumer vc = g.bufferSource().getBuffer(type);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if ((skies[y * w + x] & 255) == s) {
                        ShaderPaintRender.square(vc, m, x0 + x * c, y0 + y * c, c, c, s, x / (float) w, y / (float) h, (x + 1) / (float) w, (y + 1) / (float) h, w);
                    }
                }
            }
            g.bufferSource().endBatch(type);
        }
        g.pose().popPose();
    }

    private void drawSwatches(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 40); // (in front of the screen's background)
        var m = g.pose().last().pose();
        for (int i = scroll * cols; i < Math.min(skyCount(), (scroll + rowsShown) * cols); i++) {
            int s = ShaderPaintStyles.of(i, pass);
            RenderType type = ShaderPaintRender.type(s);
            if (type == null) {
                continue;
            }
            int col = i % cols, row = i / cols - scroll;
            VertexConsumer vc = g.bufferSource().getBuffer(type);
            ShaderPaintRender.square(vc, m, px0 + col * (swatch + 2), py0 + row * (swatch + 2), swatch, swatch, s, 0, 0, 1, 1, 8);
            g.bufferSource().endBatch(type);
        }
        g.pose().popPose();
    }

    private void drawPreview(GuiGraphics g, int x, int y, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack preview = item.copy();
        boolean any = false;
        for (byte b : skies) {
            any |= b != 0;
        }
        if (any) {
            preview.set(ModComponents.SHADER_PAINT.get(), new ModComponents.ShaderPaint(w, h, skies.clone()));
        } else {
            preview.remove(ModComponents.SHADER_PAINT.get());
        }
        if (redrawn()) {
            preview.set(ModComponents.ITEM_TEXTURE.get(), ModComponents.ItemTexture.of(w, h, colours));
        } else {
            preview.remove(ModComponents.ITEM_TEXTURE.get());
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
