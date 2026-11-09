package com.example.skyblocks.client;

import com.example.skyblocks.EffectBlockEntity;
import com.example.skyblocks.EffectBlockPayload;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The effect block's menu: the effect; a button for each of its colours (1-8, - and + for how many), the chosen one set
 * with red, green and blue sliders or a hex code; speed, size, brightness, blur, pixelated. Changes go to the whole
 * group as you make them.
 */
public class EffectBlockScreen extends Screen {
    private static final int PANEL_W = 276;
    private static final int PANEL_H = 254;

    private final BlockPos pos;
    private int effect, count, speed, size, brightness, blur;
    private boolean pixel;
    private final int[] colours;
    /** The colour being set. */
    private int sel;
    private EditBox hex;
    private boolean settingHex;
    private Button effectButton;
    private final Button[] swatches = new Button[EffectBlockEntity.MAX_COLOURS];
    private final Slider[] rgb = new Slider[3];
    private boolean dirty;
    private int sinceSent;

    public EffectBlockScreen(BlockPos pos, EffectBlockEntity.Settings s) {
        super(Component.translatable("screen.skyblocks.effect_block"));
        this.pos = pos;
        effect = s.effect();
        count = s.count();
        colours = s.colours().clone();
        speed = s.speed();
        size = s.size();
        brightness = s.brightness();
        blur = s.blur();
        pixel = s.pixel();
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> setEffect(effect - 1)).bounds(l + 12, t + 24, 20, 20).build());
        effectButton = addRenderableWidget(Button.builder(effectLabel(), b -> setEffect(effect + 1)).bounds(l + 36, t + 24, 204, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> setEffect(effect + 1)).bounds(l + 244, t + 24, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            count = Math.max(1, count - 1);
            if (sel != EffectBlockEntity.BACKGROUND && sel >= count) {
                select(count - 1);
            }
            showSwatches();
            changed();
        }).bounds(l + 196, t + 50, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            count = Math.min(EffectBlockEntity.MAX_COLOURS, count + 1);
            showSwatches();
            select(count - 1);
            changed();
        }).bounds(l + 246, t + 50, 18, 18).build());
        for (int i = 0; i < EffectBlockEntity.MAX_COLOURS; i++) {
            final int index = i;
            swatches[i] = addRenderableWidget(new Button(l + 12 + i * 32, t + 72, 28, 20, Component.empty(), b -> select(index),
                    s -> Component.literal("colour " + (index + 1))) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    super.renderWidget(g, mx, my, pt);
                    g.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3, 0xFF000000 | colours[index]);
                    if (index == sel) {
                        g.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, 0xFFFFFFFF);
                    }
                }
            });
        }
        showSwatches();
        // the background (behind the effects that light only part of the side): set like the colours
        addRenderableWidget(new Button(l + 70, t + 49, 118, 20, Component.translatable("screen.skyblocks.effect_block.background"),
                b -> select(EffectBlockEntity.BACKGROUND), s -> Component.literal("background")) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                super.renderWidget(g, mx, my, pt);
                g.fill(getX() + 4, getY() + 4, getX() + 16, getY() + height - 4, 0xFFFFFFFF);
                g.fill(getX() + 5, getY() + 5, getX() + 15, getY() + height - 5, 0xFF000000 | colours[EffectBlockEntity.BACKGROUND]);
                if (sel == EffectBlockEntity.BACKGROUND) {
                    g.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, 0xFFFFFFFF);
                }
            }
        });
        String[] names = {"red", "green", "blue"};
        for (int k = 0; k < 3; k++) {
            final int shift = 16 - 8 * k;
            rgb[k] = addRenderableWidget(new Slider(l + 12, t + 100 + k * 24, 180, names[k], 0, 255, (colours[sel] >> shift) & 255, 1, v -> {
                colours[sel] = (colours[sel] & ~(255 << shift)) | (v << shift);
                showHex();
                changed();
            }));
        }
        hex = new EditBox(font, l + 198, t + 148, 66, 18, Component.literal("hex"));
        hex.setMaxLength(7);
        hex.setResponder(v -> {
            if (settingHex) {
                return;
            }
            String s = v.startsWith("#") ? v.substring(1) : v;
            if (s.length() == 6) {
                try {
                    colours[sel] = Integer.parseInt(s, 16) & 0xFFFFFF;
                    showSliders();
                    changed();
                } catch (NumberFormatException ignored) {
                    // (not a colour yet)
                }
            }
        });
        addRenderableWidget(hex);
        showHex();
        addRenderableWidget(new Slider(l + 12, t + 176, 124, "speed", 0, 400, speed, 5, v -> speed = v));
        addRenderableWidget(new Slider(l + 140, t + 176, 124, "size", 25, 800, size, 5, v -> size = v));
        addRenderableWidget(new Slider(l + 12, t + 200, 124, "brightness", 10, 100, brightness, 1, v -> brightness = v));
        addRenderableWidget(new Slider(l + 140, t + 200, 124, "blur", 0, 100, blur, 1, v -> blur = v));
        addRenderableWidget(Button.builder(pixelLabel(), b -> {
            pixel = !pixel;
            b.setMessage(pixelLabel());
            changed();
        }).bounds(l + 12, t + 226, 124, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(l + 188, t + 226, 76, 20).build());
    }

    /** A slider for a number from {@code min} to {@code max}, in steps. */
    private class Slider extends AbstractSliderButton {
        private final String name;
        private final int min, max, step;
        private final IntConsumer set;

        Slider(int x, int y, int w, String name, int min, int max, int value, int step, IntConsumer set) {
            super(x, y, w, 20, Component.empty(), (value - min) / (double) (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.step = step;
            this.set = set;
            updateMessage();
        }

        int amount() {
            int v = (int) Math.round(min + value * (max - min));
            return Math.round(v / (float) step) * step;
        }

        void show(int v) {
            value = (v - min) / (double) (max - min);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.skyblocks.effect_block." + name, amount()));
        }

        @Override
        protected void applyValue() {
            set.accept(amount());
            changed();
        }
    }

    private Component effectLabel() {
        return Component.translatable("screen.skyblocks.effect_block.effect",
                Component.translatable("effect_block.skyblocks." + EffectBlockEntity.EFFECTS[effect]));
    }

    private Component pixelLabel() {
        return Component.translatable(pixel ? "screen.skyblocks.effect_block.pixel_on" : "screen.skyblocks.effect_block.pixel_off");
    }

    private void setEffect(int e) {
        effect = Math.floorMod(e, EffectBlockEntity.EFFECTS.length);
        effectButton.setMessage(effectLabel());
        changed();
    }

    private void select(int i) {
        sel = i;
        showSliders();
        showHex();
    }

    /** Only as many colour buttons as colours used. */
    private void showSwatches() {
        for (int i = 0; i < swatches.length; i++) {
            if (swatches[i] != null) {
                swatches[i].visible = i < count;
            }
        }
    }

    private void showSliders() {
        for (int k = 0; k < 3; k++) {
            if (rgb[k] != null) {
                rgb[k].show((colours[sel] >> (16 - 8 * k)) & 255);
            }
        }
    }

    private void showHex() {
        if (hex != null) {
            settingHex = true;
            hex.setValue(String.format("#%06X", colours[sel]));
            settingHex = false;
        }
    }

    private void changed() {
        dirty = true;
    }

    @Override
    public void tick() {
        super.tick();
        sinceSent++;
        if (dirty && sinceSent >= 2) {
            send();
        }
    }

    private void send() {
        dirty = false;
        sinceSent = 0;
        PacketDistributor.sendToServer(EffectBlockPayload.of(pos,
                new EffectBlockEntity.Settings(effect, count, colours, speed, size, brightness, pixel, blur)));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left(), top(), left() + PANEL_W, top() + PANEL_H, 0xC0101018);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();
        g.drawCenteredString(font, title, l + PANEL_W / 2, t + 9, 0xFFFFFF);
        g.drawString(font, Component.translatable("screen.skyblocks.effect_block.colours"), l + 12, t + 55, 0xA0A0A0);
        Component editing = sel == EffectBlockEntity.BACKGROUND ? Component.translatable("screen.skyblocks.effect_block.background")
                : Component.translatable("screen.skyblocks.effect_block.editing", sel + 1);
        g.drawCenteredString(font, Component.literal(Integer.toString(count)), l + 230, t + 55, 0xFFFFFF);
        // the colour being set, big
        g.fill(l + 198, t + 100, l + 264, t + 142, 0xFFFFFFFF);
        g.fill(l + 199, t + 101, l + 263, t + 141, 0xFF000000 | colours[sel]);
        g.drawCenteredString(font, editing, l + 231, t + 116,
                (((colours[sel] >> 16) & 255) * 3 + ((colours[sel] >> 8) & 255) * 6 + (colours[sel] & 255)) > 1300 ? 0x202020 : 0xFFFFFF);
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
