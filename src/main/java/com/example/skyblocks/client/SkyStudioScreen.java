package com.example.skyblocks.client;

import com.example.skyblocks.StudioSkies;
import com.example.skyblocks.StudioSkiesStore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * The Sky Studio's screen: pick one of the server's 8 studio skies, switch its layers on and off, pick a layer to edit
 * its colours (hue, saturation, brightness) and numbers, watch the sky change live in the preview, name it, save it
 * for everyone (StudioSkies.Save), and take sky blocks of it for glass (StudioSkies.Take).
 */
public class SkyStudioScreen extends Screen {
    private static final int W = 400, H = 232;
    private static final int INK = 0xFFE9E6F2, DIM = 0xFFA7A1BD, PANEL = 0xFF1D1A29, EDGE = 0xFF3A3452, BG = 0xFF14121C;

    /** A layer: its name, where its "on" is, its colours (where each starts) and its numbers. */
    private record Layer(String id, int on, int[] colours, String[] colourNames, int[] numbers, String[] numberNames) {}

    private static final Layer[] LAYERS = {
            new Layer("gradient", StudioSkies.GRADIENT_ON, new int[] {0, 3, 6}, new String[] {"top", "middle", "horizon"},
                    new int[] {9, 10}, new String[] {"horizon_height", "glow"}),
            new Layer("nebula", StudioSkies.NEBULA_ON, new int[] {13, 16}, new String[] {"colour1", "colour2"},
                    new int[] {19, 20, 21}, new String[] {"amount", "drift", "softness"}),
            new Layer("stars", StudioSkies.STARS_ON, new int[0], new String[0],
                    new int[] {23, 24, 25}, new String[] {"density", "twinkle", "size"}),
            new Layer("aurora", StudioSkies.AURORA_ON, new int[] {27, 30}, new String[] {"colour1", "colour2"},
                    new int[] {33, 34, 35}, new String[] {"brightness", "wave_speed", "height"}),
            new Layer("moon", StudioSkies.MOON_ON, new int[] {37}, new String[] {"colour"},
                    new int[] {40, 41, 42}, new String[] {"size", "glow", "height"}),
            new Layer("shooting", StudioSkies.SHOOTING_ON, new int[0], new String[0],
                    new int[] {44, 45}, new String[] {"how_often", "trail"}),
            new Layer("grid", StudioSkies.GRID_ON, new int[] {47}, new String[] {"colour"},
                    new int[] {50, 51}, new String[] {"spacing", "scroll"})};
    /** Every colour's first setting (what a palette changes). */
    private static final int[] ALL_COLOURS = {0, 3, 6, 13, 16, 27, 30, 37, 47};

    private final BlockPos pos;
    private int slot, layer = 0, colour = 0;
    private float[] params;
    private String name;
    private final float[] hsv = new float[3];
    private EditBox nameBox;
    private int x0, y0;

    public SkyStudioScreen(BlockPos pos) {
        super(Component.translatable("screen.skyblocks.studio"));
        this.pos = pos;
        load(0);
    }

    private void load(int slot) {
        this.slot = slot;
        this.params = StudioSkiesStore.PARAMS[slot].clone();
        this.name = StudioSkiesStore.NAMES[slot];
        pickColour(0);
    }

    private void pickColour(int c) {
        Layer l = LAYERS[layer];
        colour = l.colours.length == 0 ? 0 : Math.min(c, l.colours.length - 1);
        if (l.colours.length > 0) {
            int at = l.colours[colour];
            toHsv(params[at], params[at + 1], params[at + 2], hsv);
        }
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        // the slots
        for (int i = 0; i < StudioSkies.SLOTS; i++) {
            final int s = i;
            Button b = Button.builder(Component.literal(String.valueOf(i + 1)), x -> {
                saveName();
                load(s);
                rebuildWidgets();
            }).bounds(x0 + W - 8 - (StudioSkies.SLOTS - i) * 22, y0 + 6, 20, 16).build();
            b.active = i != slot;
            addRenderableWidget(b);
        }
        nameBox = new EditBox(font, x0 + 8, y0 + 26, 150, 14, Component.translatable("screen.skyblocks.studio.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(name);
        nameBox.setHint(Component.translatable("screen.skyblocks.studio.name_hint", slot + 1));
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);
        // the layers: pick one, switch it on and off
        for (int i = 0; i < LAYERS.length; i++) {
            final int k = i;
            Layer l = LAYERS[i];
            int y = y0 + 46 + i * 22;
            Button pick = Button.builder(Component.translatable("screen.skyblocks.studio.layer." + l.id), x -> {
                saveName();
                layer = k;
                pickColour(0);
                rebuildWidgets();
            }).bounds(x0 + 8, y, 84, 20).build();
            pick.active = i != layer;
            addRenderableWidget(pick);
            addRenderableWidget(Button.builder(Component.translatable(params[l.on] > 0.5F ? "screen.skyblocks.studio.on" : "screen.skyblocks.studio.off"), x -> {
                saveName();
                params[l.on] = params[l.on] > 0.5F ? 0.0F : 1.0F;
                rebuildWidgets();
            }).bounds(x0 + 94, y, 26, 20).build());
        }
        // palettes, save, take sky blocks
        for (int i = 0; i < 4; i++) {
            final int p = i;
            addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.studio.palette." + i), x -> {
                saveName();
                float[] preset = StudioSkies.preset(p);
                for (int at : ALL_COLOURS) {
                    System.arraycopy(preset, at, params, at, 3);
                }
                pickColour(colour);
                rebuildWidgets();
            }).bounds(x0 + 126 + i * 39, y0 + 142, 37, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.studio.save"), x -> {
            saveName();
            PacketDistributor.sendToServer(new StudioSkies.Save(pos, slot, name, params.clone()));
        }).bounds(x0 + 126, y0 + 164, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.studio.take"), x ->
                PacketDistributor.sendToServer(new StudioSkies.Take(pos, slot))).bounds(x0 + 206, y0 + 164, 76, 20).build());
        // the layer's colours and numbers
        Layer l = LAYERS[layer];
        int y = y0 + 46;
        if (l.colours.length > 0) {
            for (int i = 0; i < l.colours.length; i++) {
                final int c = i;
                Button b = Button.builder(Component.translatable("screen.skyblocks.studio.colour." + l.colourNames[i]), x -> {
                    saveName();
                    pickColour(c);
                    rebuildWidgets();
                }).bounds(x0 + 290 + i * 31, y, 29, 16).build();
                b.active = i != colour;
                addRenderableWidget(b);
            }
            y += 20;
            String[] hsvNames = {"hue", "saturation", "brightness"};
            for (int i = 0; i < 3; i++) {
                final int k = i;
                addRenderableWidget(new Slider(x0 + 290, y, 102, "screen.skyblocks.studio.number." + hsvNames[i], () -> hsv[k], v -> {
                    hsv[k] = v.floatValue();
                    int rgb = Mth.hsvToRgb(hsv[0] >= 1.0F ? 0.999F : hsv[0], hsv[1], hsv[2]);
                    int at = LAYERS[layer].colours[colour];
                    params[at] = ((rgb >> 16) & 255) / 255.0F;
                    params[at + 1] = ((rgb >> 8) & 255) / 255.0F;
                    params[at + 2] = (rgb & 255) / 255.0F;
                }));
                y += 18;
            }
            y += 4;
        }
        for (int i = 0; i < l.numbers.length; i++) {
            final int at = l.numbers[i];
            addRenderableWidget(new Slider(x0 + 290, y, 102, "screen.skyblocks.studio.number." + l.numberNames[i], () -> params[at],
                    v -> params[at] = v.floatValue()));
            y += 18;
        }
    }

    private void saveName() {
        if (nameBox != null) {
            name = nameBox.getValue();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, EDGE);
        g.fill(x0, y0, x0 + W, y0 + H, BG);
        g.fill(x0 + 4, y0 + 44, x0 + 122, y0 + 44 + LAYERS.length * 22 + 2, PANEL);
        g.fill(x0 + 286, y0 + 44, x0 + 396, y0 + 226, PANEL);
        // the preview: the sky being made, live, slowly turning
        int px0 = x0 + 126, py0 = y0 + 46, px1 = px0 + 154, py1 = py0 + 90;
        g.fill(px0 - 2, py0 - 2, px1 + 2, py1 + 2, EDGE);
        g.flush();
        ShaderInstance shader = ShaderSky.studioPreview();
        if (shader != null) {
            StudioSkiesClient.apply(shader, params);
            float ticks = (minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() % 240000L : 0) + partialTick;
            shader.safeGetUniform("Time").set(ticks);
            shader.safeGetUniform("View").set(ticks * 0.0015F, 0.25F);
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
        } else {
            g.fill(px0, py0, px1, py1, 0xFF000000);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, x0 + 8, y0 + 10, INK, false);
        g.drawString(font, Component.translatable("screen.skyblocks.studio.editing", Component.translatable("screen.skyblocks.studio.layer." + LAYERS[layer].id)),
                x0 + 290, y0 + 34, DIM, false);
        if (LAYERS[layer].colours.length > 0) {
            int at = LAYERS[layer].colours[colour];
            int rgb = 0xFF000000 | Math.round(params[at] * 255) << 16 | Math.round(params[at + 1] * 255) << 8 | Math.round(params[at + 2] * 255);
            g.fill(x0 + 291 + LAYERS[layer].colours.length * 31, y0 + 46, x0 + 392, y0 + 62, rgb);
        }
        g.drawWordWrap(font, Component.translatable("screen.skyblocks.studio.help"), x0 + 126, y0 + 190, 156, DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A slider for a setting from 0 to 1 (shown as a percentage). */
    private static final class Slider extends AbstractSliderButton {
        private final String key;
        private final Consumer<Double> set;

        Slider(int x, int y, int w, String key, DoubleSupplier get, Consumer<Double> set) {
            super(x, y, w, 16, Component.empty(), Mth.clamp(get.getAsDouble(), 0.0, 1.0));
            this.key = key;
            this.set = set;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            set.accept(value);
        }
    }

    private static void toHsv(float r, float g, float b, float[] out) {
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0.0F;
        if (d > 1.0E-5F) {
            if (max == r) {
                h = ((g - b) / d) % 6.0F;
            } else if (max == g) {
                h = (b - r) / d + 2.0F;
            } else {
                h = (r - g) / d + 4.0F;
            }
            h /= 6.0F;
            if (h < 0.0F) {
                h += 1.0F;
            }
        }
        out[0] = h;
        out[1] = max <= 1.0E-5F ? 0.0F : d / max;
        out[2] = max;
    }
}
