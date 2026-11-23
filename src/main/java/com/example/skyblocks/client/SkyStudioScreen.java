package com.example.skyblocks.client;

import com.example.skyblocks.StudioSkies;
import com.example.skyblocks.StudioSkiesStore;
import com.example.skyblocks.StudioSpec;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * The Sky Studio's screen. Along the top: the server's 8 studio skies, the sky's name, and tools (undo, a random sky or
 * layer, reset a layer, copy and paste a whole sky, start from one of the looks, the preview turning or not). On the
 * left: the layers (StudioSpec: whole sky, sky colour, nebula, stars, aurora, sun or moon, planet, clouds, light rays,
 * shooting stars, mountains, horizon grid, lake, sparkles), each switched on and off. In the middle: the sky, live -
 * drag it to look around. On the right: the picked layer's colours (hue, saturation, brightness, and quick colours) and
 * its settings. Save shares the sky with everyone (StudioSkies.Save); Sky blocks swaps glass for blocks of it.
 */
public class SkyStudioScreen extends Screen {
    private static final int DIM = 0xFFA7A1BD, PANEL = 0xFF1D1A29, EDGE = 0xFF3A3452, BG = 0xFF14121C, ACCENT = 0xFF7EE8D6;
    /** Quick colours (a click sets the picked colour). */
    private static final int[] QUICK = {0xFFFFFF, 0xF4EFE1, 0xFFD27A, 0xFF7A1A, 0xFF3D81, 0xFF5FD2, 0xB56BFF, 0x7C4DFF,
            0x3AA0FF, 0x2BF0FF, 0x5EF2C2, 0x3DFF8A, 0xC6FF3D, 0x8A95A8, 0x1B3A6B, 0x000000};
    private static float[] clipboard;

    private final BlockPos pos;
    private int slot, layer = 1, colourField = -1, look;
    private float[] params;
    private String name;
    private final float[] hsv = new float[3];
    private final ArrayDeque<float[]> undo = new ArrayDeque<>();
    private EditBox nameBox;
    private int x0, y0, w, h, lwid, rw;
    private int px0, py0, px1, py1;
    private float viewYaw = 0.0F, viewPitch = 0.2F;
    private boolean turning = true, dragging;
    private int layerScroll, fieldScroll;
    private final List<AbstractWidget> layerWidgets = new ArrayList<>(), fieldWidgets = new ArrayList<>();
    private int layerTop, layerBottom, fieldTop, fieldBottom, swatchY = -1, helpY;

    public SkyStudioScreen(BlockPos pos) {
        super(Component.translatable("screen.skyblocks.studio"));
        this.pos = pos;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        load(level != null && level.getBlockEntity(pos) instanceof com.example.skyblocks.SkyStudioBlockEntity be ? be.slot() : 0);
    }

    private void load(int s) {
        slot = s;
        params = StudioSkiesStore.PARAMS[s].length == StudioSpec.SIZE ? StudioSkiesStore.PARAMS[s].clone() : StudioSkies.preset(s);
        name = StudioSkiesStore.NAMES[s];
        undo.clear();
        pickColour(firstColour());
    }

    private StudioSpec.Layer current() {
        return StudioSpec.LAYERS[layer];
    }

    private int firstColour() {
        for (StudioSpec.Field f : current().fields()) {
            if (f.kind() == 'c') {
                return f.at();
            }
        }
        return -1;
    }

    private void pickColour(int at) {
        colourField = at;
        if (at >= 0) {
            toHsv(params[at], params[at + 1], params[at + 2], hsv);
        }
    }

    private void remember() {
        undo.push(params.clone());
        while (undo.size() > 40) {
            undo.removeLast();
        }
    }

    @Override
    protected void init() {
        w = Math.min(width - 12, 540);
        h = Math.min(height - 12, 300);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        layerWidgets.clear();
        fieldWidgets.clear();
        for (int i = 0; i < StudioSkies.SLOTS; i++) {
            final int s = i;
            Button b = Button.builder(Component.literal(String.valueOf(i + 1)), x -> {
                keepName();
                load(s);
                rebuildWidgets();
            }).bounds(x0 + w - 6 - (StudioSkies.SLOTS - i) * 19, y0 + 5, 18, 16).build();
            b.active = i != slot;
            addRenderableWidget(b);
        }
        nameBox = new EditBox(font, x0 + 80, y0 + 6, Math.max(60, w - 80 - StudioSkies.SLOTS * 19 - 14), 14, Component.translatable("screen.skyblocks.studio.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(name);
        nameBox.setHint(Component.translatable("screen.skyblocks.studio.name_hint", slot + 1));
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);
        // ---- tools ----
        int tx = x0 + 6, ty = y0 + 25;
        tx = tool(tx, ty, "undo", () -> {
            if (!undo.isEmpty()) {
                params = undo.pop();
                pickColour(colourField);
            }
        });
        tx = tool(tx, ty, "random", () -> {
            remember();
            randomise(-1);
        });
        tx = tool(tx, ty, "random_layer", () -> {
            remember();
            randomise(layer);
        });
        tx = tool(tx, ty, "reset_layer", () -> {
            remember();
            float[] p = StudioSkies.preset(slot);
            StudioSpec.Layer l = current();
            if (l.on() >= 0) {
                params[l.on()] = p[l.on()];
            }
            for (StudioSpec.Field f : l.fields()) {
                System.arraycopy(p, f.at(), params, f.at(), f.kind() == 'c' ? 3 : 1);
            }
            pickColour(colourField);
        });
        tx = tool(tx, ty, "copy", () -> clipboard = params.clone());
        tx = tool(tx, ty, "paste", () -> {
            if (clipboard != null && clipboard.length == params.length) {
                remember();
                params = clipboard.clone();
                pickColour(colourField);
            }
        });
        // ---- left: the layers ----
        lwid = Mth.clamp(w / 4, 92, 122);
        rw = Mth.clamp(w * 9 / 28, 132, 176);
        int lx = x0 + 6;
        layerTop = y0 + 46;
        layerBottom = y0 + h - 6;
        int[] shown = shownOrder();
        for (int row = 0; row < shown.length; row++) {
            final int k = shown[row];
            StudioSpec.Layer l = StudioSpec.LAYERS[k];
            int y = layerTop + 2 + row * 20 - layerScroll;
            int pickW = lwid - 4 - (l.on() >= 0 ? 29 : 0) - (l.order() >= 0 ? 13 : 0);
            Button pick = Button.builder(Component.translatable("screen.skyblocks.studio.layer." + l.id()), x -> {
                keepName();
                layer = k;
                fieldScroll = 0;
                pickColour(firstColour());
                rebuildWidgets();
            }).bounds(lx + 2, y, pickW, 18).build();
            pick.active = k != layer;
            layerWidgets.add(addRenderableWidget(pick));
            if (l.order() >= 0) {
                // further back (up the list) or further forward (down it): swaps places with its neighbour
                final int above = row > 0 && StudioSpec.LAYERS[shown[row - 1]].order() >= 0 ? shown[row - 1] : -1;
                final int below = row + 1 < shown.length ? shown[row + 1] : -1;
                Arrow up = new Arrow(lx + 3 + pickW, y, true, () -> swapRank(k, above));
                up.active = above >= 0;
                up.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.skyblocks.studio.back")));
                Arrow down = new Arrow(lx + 3 + pickW, y + 9, false, () -> swapRank(k, below));
                down.active = below >= 0;
                down.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.skyblocks.studio.front")));
                layerWidgets.add(addRenderableWidget(up));
                layerWidgets.add(addRenderableWidget(down));
            }
            if (l.on() >= 0) {
                layerWidgets.add(addRenderableWidget(Button.builder(Component.translatable(params[l.on()] > 0.5F ? "screen.skyblocks.studio.on" : "screen.skyblocks.studio.off"), x -> {
                    remember();
                    params[l.on()] = params[l.on()] > 0.5F ? 0.0F : 1.0F;
                    rebuildWidgets();
                }).bounds(lx + lwid - 29, y, 27, 18).build()));
            }
        }
        // ---- middle: the preview, save, sky blocks ----
        int mx0 = lx + lwid + 6, mx1 = x0 + w - rw - 10;
        px0 = mx0 + 2;
        px1 = mx1 - 2;
        py0 = y0 + 48;
        py1 = Math.max(py0 + 40, Math.min(py0 + (int) ((px1 - px0) * 0.62F), y0 + h - (px1 - px0 >= 150 ? 50 : 90)));
        int by = py1 + 5, mw = px1 - px0;
        boolean wide = mw >= 150;
        int half = wide ? (mw - 4) / 2 : mw;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.studio.save"), x -> {
            keepName();
            PacketDistributor.sendToServer(new StudioSkies.Save(pos, slot, name, params.clone()));
        }).bounds(px0, by, half, 18).build());
        if (!wide) {
            by += 20;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.studio.take"), x ->
                PacketDistributor.sendToServer(new StudioSkies.Take(pos, slot))).bounds(wide ? px0 + half + 4 : px0, by, half, 18).build());
        by += 20;
        Component lookName = Component.translatable("screen.skyblocks.studio.look." + StudioSpec.PRESET_NAMES[look]);
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.studio.look", lookName), x -> {
            remember();
            params = StudioSpec.PRESETS[look].clone();
            look = (look + 1) % StudioSpec.PRESETS.length;
            pickColour(firstColour());
            rebuildWidgets();
        }).bounds(px0, by, half, 18).build());
        if (!wide) {
            by += 20;
        }
        Component turnLabel = Component.translatable("screen.skyblocks.studio.turn",
                Component.translatable(turning ? "screen.skyblocks.studio.on" : "screen.skyblocks.studio.off"));
        addRenderableWidget(Button.builder(turnLabel, x -> {
            turning = !turning;
            rebuildWidgets();
        }).bounds(wide ? px0 + half + 4 : px0, by, half, 18).build());
        helpY = by + 24;
        // ---- right: the picked layer's colours and settings ----
        int fx = x0 + w - rw - 6;
        fieldTop = y0 + 46;
        fieldBottom = y0 + h - 6;
        int y = fieldTop + 2 - fieldScroll;
        swatchY = -1;
        if (colourField >= 0) {
            String[] names = {"hue", "saturation", "value"};
            for (int i = 0; i < 3; i++) {
                final int k = i;
                fieldWidgets.add(addRenderableWidget(new Slider(fx + 2, y, rw - 4, Component.translatable("screen.skyblocks.studio." + names[i]),
                        () -> hsv[k], v -> {
                    hsv[k] = v.floatValue();
                    setColour(Mth.hsvToRgb(hsv[0] >= 1.0F ? 0.999F : hsv[0], hsv[1], hsv[2]));
                })));
                y += 18;
            }
            swatchY = y + fieldScroll; // (the quick colours: drawn and clicked by hand)
            y += 22;
            for (StudioSpec.Field f : current().fields()) {
                if (f.kind() != 'c') {
                    continue;
                }
                Button b = Button.builder(Component.translatable("screen.skyblocks.studio.field." + f.id()), x -> {
                    pickColour(f.at());
                    rebuildWidgets();
                }).bounds(fx + 2, y, rw - 26, 16).build();
                b.active = f.at() != colourField;
                fieldWidgets.add(addRenderableWidget(b));
                y += 18;
            }
            y += 4;
        }
        for (StudioSpec.Field f : current().fields()) {
            if (f.kind() != 'n') {
                continue;
            }
            final int at = f.at();
            fieldWidgets.add(addRenderableWidget(new Slider(fx + 2, y, rw - 4, Component.translatable("screen.skyblocks.studio.field." + f.id()),
                    () -> params[at], v -> params[at] = v.floatValue())));
            y += 18;
        }
        clip();
    }

    /** The layers as the list shows them: the ones always at the back, then the rest in draw order (back first). */
    private int[] shownOrder() {
        Integer[] idx = new Integer[StudioSpec.LAYERS.length];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, java.util.Comparator.comparingDouble(i -> {
            StudioSpec.Layer l = StudioSpec.LAYERS[i];
            return l.order() < 0 ? -1.0 + i * 0.01 : params[l.order()] + i * 1.0E-4;
        }));
        int[] out = new int[idx.length];
        for (int i = 0; i < idx.length; i++) {
            out[i] = idx[i];
        }
        return out;
    }

    /** Two layers swap places in the draw order (then every rank is tidied to 0, 1/16, 2/16, ...). */
    private void swapRank(int a, int b) {
        if (b < 0 || StudioSpec.LAYERS[a].order() < 0 || StudioSpec.LAYERS[b].order() < 0) {
            return;
        }
        remember();
        int oa = StudioSpec.LAYERS[a].order(), ob = StudioSpec.LAYERS[b].order();
        float t = params[oa];
        params[oa] = params[ob];
        params[ob] = t;
        if (params[oa] == params[ob]) {
            params[oa] += a < b ? 0.5F / 16.0F : -0.5F / 16.0F; // (they were level: put them apart)
        }
        int rank = 0;
        for (int k : shownOrder()) {
            if (StudioSpec.LAYERS[k].order() >= 0) {
                params[StudioSpec.LAYERS[k].order()] = rank++ / 16.0F;
            }
        }
    }

    /** A little arrow button (half a row high): moves a layer back or forward. */
    private final class Arrow extends net.minecraft.client.gui.components.AbstractButton {
        private final boolean up;
        private final Runnable run;

        Arrow(int x, int y, boolean up, Runnable run) {
            super(x, y, 11, 9, Component.translatable(up ? "screen.skyblocks.studio.back" : "screen.skyblocks.studio.front"));
            this.up = up;
            this.run = run;
        }

        @Override
        public void onPress() {
            run.run();
            rebuildWidgets();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY();
            g.fill(x, y, x + width, y + height, !active ? 0xFF24212F : isHoveredOrFocused() ? 0xFF4A4266 : EDGE);
            int c = active ? (isHoveredOrFocused() ? ACCENT : 0xFFE6E2F2) : 0xFF55506A;
            for (int r = 0; r < 3; r++) {
                int row = up ? y + 3 + r : y + 5 - r;
                g.fill(x + 5 - r, row, x + 6 + r, row + 1, c);
            }
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** A tool button along the top; returns where the next one goes. */
    private int tool(int x, int y, String id, Runnable run) {
        Component label = Component.translatable("screen.skyblocks.studio." + id);
        int bw = font.width(label) + 10;
        addRenderableWidget(Button.builder(label, b -> {
            run.run();
            rebuildWidgets();
        }).bounds(x, y, bw, 16).build());
        return x + bw + 2;
    }

    /** Hides what's scrolled out of its column. */
    private void clip() {
        for (AbstractWidget wd : layerWidgets) {
            wd.visible = wd.getY() >= layerTop && wd.getY() + wd.getHeight() <= layerBottom;
        }
        for (AbstractWidget wd : fieldWidgets) {
            wd.visible = wd.getY() >= fieldTop && wd.getY() + wd.getHeight() <= fieldBottom;
        }
    }

    private void setColour(int rgb) {
        if (colourField < 0) {
            return;
        }
        params[colourField] = ((rgb >> 16) & 255) / 255.0F;
        params[colourField + 1] = ((rgb >> 8) & 255) / 255.0F;
        params[colourField + 2] = (rgb & 255) / 255.0F;
    }

    /** A random look for one layer (or every layer, -1): colours that go together, settings away from the extremes. */
    private void randomise(int only) {
        RandomSource r = RandomSource.create();
        float baseHue = r.nextFloat();
        for (int i = 0; i < StudioSpec.LAYERS.length; i++) {
            if (only >= 0 && i != only) {
                continue;
            }
            StudioSpec.Layer l = StudioSpec.LAYERS[i];
            if (only < 0 && l.on() >= 0) {
                params[l.on()] = l.id().equals("gradient") || r.nextFloat() < 0.45F ? 1.0F : 0.0F;
            }
            if (l.id().equals("global")) {
                continue; // (the whole sky's look stays as it is)
            }
            for (StudioSpec.Field f : l.fields()) {
                if (f.kind() == 'c') {
                    float hue = (baseHue + (r.nextFloat() - 0.5F) * 0.45F + (r.nextFloat() < 0.25F ? 0.5F : 0.0F)) % 1.0F;
                    int rgb = Mth.hsvToRgb(hue < 0 ? hue + 1 : hue, 0.35F + r.nextFloat() * 0.6F, 0.25F + r.nextFloat() * 0.75F);
                    params[f.at()] = ((rgb >> 16) & 255) / 255.0F;
                    params[f.at() + 1] = ((rgb >> 8) & 255) / 255.0F;
                    params[f.at() + 2] = (rgb & 255) / 255.0F;
                } else {
                    params[f.at()] = 0.15F + r.nextFloat() * 0.7F;
                }
            }
        }
        pickColour(colourField);
    }

    private void keepName() {
        if (nameBox != null) {
            name = nameBox.getValue();
        }
    }

    // ---- drawing ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(x0 - 2, y0 - 2, x0 + w + 2, y0 + h + 2, EDGE);
        g.fill(x0, y0, x0 + w, y0 + h, BG);
        g.fill(x0 + 6, layerTop, x0 + 6 + lwid, layerBottom, PANEL);
        g.fill(x0 + w - rw - 6, fieldTop, x0 + w - 6, fieldBottom, PANEL);
        g.fill(px0 - 2, py0 - 2, px1 + 2, py1 + 2, EDGE);
        g.flush();
        ShaderInstance shader = ShaderSky.studioPreview();
        if (shader == null) {
            g.fill(px0, py0, px1, py1, 0xFF000000);
            return;
        }
        if (turning && !dragging) {
            viewYaw += 0.0025F;
        }
        StudioSkiesClient.apply(shader, params);
        float ticks = (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() % 240000L : 0) + partialTick;
        shader.safeGetUniform("Time").set(ticks);
        shader.safeGetUniform("View").set(viewYaw, viewPitch);
        RenderSystem.setShader(() -> shader);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, px0, py0, 0).setUv(0, 0);
        b.addVertex(m, px0, py1, 0).setUv(0, 1);
        b.addVertex(m, px1, py1, 0).setUv(1, 1);
        b.addVertex(m, px1, py0, 0).setUv(1, 0);
        MeshData mesh = b.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, x0 + 8, y0 + 9, ACCENT, false);
        int fx = x0 + w - rw - 6;
        if (swatchY >= 0 && colourField >= 0) {
            int sy = swatchY - fieldScroll;
            if (sy >= fieldTop && sy + 19 <= fieldBottom) {
                for (int i = 0; i < QUICK.length; i++) {
                    int sx = fx + 4 + (i % 8) * sw(), row = sy + (i / 8) * 10;
                    g.fill(sx, row, sx + sw() - 2, row + 9, 0xFF000000 | QUICK[i]);
                }
            }
            for (StudioSpec.Field f : current().fields()) {
                if (f.kind() == 'c') {
                    int cy = fieldY(f.at());
                    if (cy >= fieldTop && cy + 16 <= fieldBottom) {
                        g.fill(fx + rw - 20, cy + 1, fx + rw - 2, cy + 15, f.at() == colourField ? ACCENT : EDGE);
                        g.fill(fx + rw - 18, cy + 3, fx + rw - 4, cy + 13, rgb(f.at()));
                    }
                }
            }
        }
        Component help = Component.translatable("screen.skyblocks.studio.help");
        if (helpY + font.split(help, px1 - px0).size() * 9 <= y0 + h - 4) {
            g.drawWordWrap(font, help, px0, helpY, px1 - px0, DIM);
        }
        if (layerScroll > 0 || maxLayerScroll() > layerScroll) {
            g.drawString(font, "↕", x0 + 118, layerBottom - 10, DIM, false);
        }
        if (fieldScroll > 0 || maxFieldScroll() > fieldScroll) {
            g.drawString(font, "↕", x0 + w - 14, fieldBottom - 10, DIM, false);
        }
    }

    /** A quick colour's width (8 to a row). */
    private int sw() {
        return (rw - 8) / 8;
    }

    private int rgb(int at) {
        return 0xFF000000 | Math.round(params[at] * 255) << 16 | Math.round(params[at + 1] * 255) << 8 | Math.round(params[at + 2] * 255);
    }

    /** Where a colour's button is (its row in the right column). */
    private int fieldY(int at) {
        int y = fieldTop + 2 - fieldScroll + 3 * 18 + 22;
        for (StudioSpec.Field f : current().fields()) {
            if (f.kind() == 'c') {
                if (f.at() == at) {
                    return y;
                }
                y += 18;
            }
        }
        return -1000;
    }

    private int maxLayerScroll() {
        return Math.max(0, StudioSpec.LAYERS.length * 20 + 4 - (layerBottom - layerTop));
    }

    private int maxFieldScroll() {
        int rows = 0, colours = 0;
        for (StudioSpec.Field f : current().fields()) {
            if (f.kind() == 'c') {
                colours++;
            } else {
                rows++;
            }
        }
        int total = (colours > 0 ? 3 * 18 + 22 + colours * 18 + 4 : 0) + rows * 18 + 4;
        return Math.max(0, total - (fieldBottom - fieldTop));
    }

    // ---- mouse ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (swatchY >= 0 && colourField >= 0) {
            int fx = x0 + w - rw - 6, sy = swatchY - fieldScroll;
            for (int i = 0; i < QUICK.length; i++) {
                int sx = fx + 4 + (i % 8) * sw(), row = sy + (i / 8) * 10;
                if (mx >= sx && mx < sx + sw() - 2 && my >= row && my < row + 9 && row >= fieldTop && row + 9 <= fieldBottom) {
                    remember();
                    setColour(QUICK[i]);
                    pickColour(colourField);
                    rebuildWidgets();
                    return true;
                }
            }
        }
        if (mx >= px0 && mx < px1 && my >= py0 && my < py1) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            viewYaw -= (float) dx * 0.012F;
            viewPitch = Mth.clamp(viewPitch + (float) dy * 0.012F, -1.2F, 1.4F);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < x0 + 8 + lwid) {
            layerScroll = Mth.clamp(layerScroll - (int) (sy * 20), 0, maxLayerScroll());
            rebuildWidgets();
            return true;
        }
        if (mx > x0 + w - rw - 8) {
            fieldScroll = Mth.clamp(fieldScroll - (int) (sy * 18), 0, maxFieldScroll());
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A setting from 0 to 1 (shown as a percentage); a change can be undone. */
    private final class Slider extends AbstractSliderButton {
        private final Component label;
        private final Consumer<Double> set;

        Slider(int x, int y, int width, Component label, DoubleSupplier get, Consumer<Double> set) {
            super(x, y, width, 16, Component.empty(), Mth.clamp(get.getAsDouble(), 0.0, 1.0));
            this.label = label;
            this.set = set;
            updateMessage();
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            remember();
            super.onClick(mouseX, mouseY);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.skyblocks.studio.slider", label, Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            set.accept(value);
        }
    }

    private static void toHsv(float r, float g, float b, float[] out) {
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float hue = 0.0F;
        if (d > 1.0E-5F) {
            if (max == r) {
                hue = ((g - b) / d) % 6.0F;
            } else if (max == g) {
                hue = (b - r) / d + 2.0F;
            } else {
                hue = (r - g) / d + 4.0F;
            }
            hue /= 6.0F;
            if (hue < 0.0F) {
                hue += 1.0F;
            }
        }
        out[0] = hue;
        out[1] = max <= 1.0E-5F ? 0.0F : d / max;
        out[2] = max;
    }
}
