package com.example.skyblocks.client;

import com.example.skyblocks.EffectBlockEntity;
import com.example.skyblocks.EffectBlockPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The effect block's menu: the effect, how many colours (1-8) and which (a colour square and hue bar, a hex box, the
 * 16 dye colours), speed, size, brightness, pixelated. Changes go to the whole group as you make them.
 */
public class EffectBlockScreen extends Screen {
    private static final int PANEL_W = 276;
    private static final int PANEL_H = 256;
    private static final int SV_X = 12, SV_Y = 100, SV_W = 100, SV_H = 60, HUE_Y = 164, HUE_H = 10;

    private final BlockPos pos;
    private int effect, count, speed, size, brightness;
    private boolean pixel;
    private final int[] colours;
    /** The colour being edited. */
    private int sel;
    /** The edited colour as hue, saturation, value (kept apart, so a grey keeps its hue while you drag). */
    private float hue, sat, val;
    private EditBox hex;
    private boolean settingHex;
    private Button effectButton;
    private int dragging; // 0 none, 1 the square, 2 the hue bar
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
        pixel = s.pixel();
        select(0);
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
            if (sel >= count) {
                select(count - 1);
            }
            changed();
        }).bounds(l + 196, t + 50, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            count = Math.min(EffectBlockEntity.MAX_COLOURS, count + 1);
            select(count - 1);
            changed();
        }).bounds(l + 246, t + 50, 18, 18).build());
        for (int i = 0; i < EffectBlockEntity.MAX_COLOURS; i++) {
            final int index = i;
            addRenderableWidget(new Button(l + 12 + i * 32, t + 72, 28, 20, Component.empty(), b -> select(index), s -> Component.literal("colour " + (index + 1))) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    super.renderWidget(g, mx, my, pt);
                    int c = 0xFF000000 | colours[index];
                    g.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3, c);
                    if (index >= count) { // (not used now)
                        g.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3, 0xB0101018);
                    }
                    if (index == sel) {
                        g.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, 0xFFFFFFFF);
                    }
                }
            });
        }
        hex = new EditBox(font, l + 148, t + 100, 72, 16, Component.literal("hex"));
        hex.setMaxLength(7);
        hex.setResponder(v -> {
            if (settingHex) {
                return;
            }
            String s = v.startsWith("#") ? v.substring(1) : v;
            if (s.length() == 6) {
                try {
                    colours[sel] = Integer.parseInt(s, 16) & 0xFFFFFF;
                    fromRgb(colours[sel]);
                    changed();
                } catch (NumberFormatException ignored) {
                    // (not a colour yet)
                }
            }
        });
        addRenderableWidget(hex);
        showHex();
        DyeColor[] dyes = DyeColor.values();
        for (int i = 0; i < dyes.length; i++) {
            final int c = dyes[i].getTextureDiffuseColor() & 0xFFFFFF;
            addRenderableWidget(new Button(l + 124 + (i % 8) * 18, t + 124 + (i / 8) * 18, 16, 16, Component.empty(), b -> {
                colours[sel] = c;
                fromRgb(c);
                showHex();
                changed();
            }, s -> Component.literal("dye")) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? 0xFFFFFFFF : 0xFF000000);
                    g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF000000 | c);
                }
            });
        }
        addRenderableWidget(new Slider(l + 12, t + 182, 124, "speed", 0, 400, speed, v -> speed = v));
        addRenderableWidget(new Slider(l + 140, t + 182, 124, "size", 25, 800, size, v -> size = v));
        addRenderableWidget(new Slider(l + 12, t + 206, 124, "brightness", 10, 100, brightness, v -> brightness = v));
        addRenderableWidget(Button.builder(pixelLabel(), b -> {
            pixel = !pixel;
            b.setMessage(pixelLabel());
            changed();
        }).bounds(l + 140, t + 206, 124, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(l + PANEL_W - 88, t + PANEL_H - 26, 76, 20).build());
    }

    /** A slider for one of the amounts, in percent. */
    private class Slider extends AbstractSliderButton {
        private final String name;
        private final int min, max;
        private final java.util.function.IntConsumer set;

        Slider(int x, int y, int w, String name, int min, int max, int value, java.util.function.IntConsumer set) {
            super(x, y, w, 20, Component.empty(), (value - min) / (double) (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.set = set;
            updateMessage();
        }

        private int amount() {
            int v = (int) Math.round(min + value * (max - min));
            return max > 100 ? Math.round(v / 5.0F) * 5 : v;
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
        fromRgb(colours[i]);
        if (hex != null) {
            showHex();
        }
    }

    private void showHex() {
        settingHex = true;
        hex.setValue(String.format("#%06X", colours[sel]));
        settingHex = false;
    }

    private void fromRgb(int c) {
        float r = ((c >> 16) & 255) / 255.0F, g = ((c >> 8) & 255) / 255.0F, b = (c & 255) / 255.0F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        if (d > 0.0F && max > 0.0F) { // (a grey keeps the hue it had; black keeps the saturation too)
            float h = max == r ? (g - b) / d : max == g ? 2.0F + (b - r) / d : 4.0F + (r - g) / d;
            hue = (h / 6.0F + 1.0F) % 1.0F;
        }
        if (max > 0.0F) {
            sat = d / max;
        }
        val = max;
    }

    private static int hsv(float h, float s, float v) {
        return Mth.hsvToRgb(Mth.clamp(h, 0.0F, 0.999F), Mth.clamp(s, 0.0F, 1.0F), Mth.clamp(v, 0.0F, 1.0F)) & 0xFFFFFF;
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
                new EffectBlockEntity.Settings(effect, count, colours, speed, size, brightness, pixel)));
    }

    // ---- the colour square (saturation across, brightness up) and the hue bar ----

    private boolean pick(double mx, double my) {
        int x0 = left() + SV_X, y0 = top() + SV_Y;
        if (dragging == 1) {
            sat = Mth.clamp((float) (mx - x0) / SV_W, 0.0F, 1.0F);
            val = 1.0F - Mth.clamp((float) (my - y0) / SV_H, 0.0F, 1.0F);
        } else if (dragging == 2) {
            hue = Mth.clamp((float) (mx - x0) / SV_W, 0.0F, 0.999F);
        } else {
            return false;
        }
        colours[sel] = hsv(hue, sat, val);
        showHex();
        changed();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x0 = left() + SV_X;
        if (button == 0 && mx >= x0 && mx < x0 + SV_W) {
            if (my >= top() + SV_Y && my < top() + SV_Y + SV_H) {
                dragging = 1;
                return pick(mx, my);
            }
            if (my >= top() + HUE_Y && my < top() + HUE_Y + HUE_H) {
                dragging = 2;
                return pick(mx, my);
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != 0) {
            return pick(mx, my);
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = 0;
        return super.mouseReleased(mx, my, button);
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
        g.drawCenteredString(font, Component.literal(Integer.toString(count)), l + 230, t + 55, 0xFFFFFF);
        // the square: saturation across, brightness up, at this hue
        for (int x = 0; x < SV_W; x++) {
            int top = 0xFF000000 | hsv(hue, x / (float) (SV_W - 1), 1.0F);
            g.fillGradient(l + SV_X + x, t + SV_Y, l + SV_X + x + 1, t + SV_Y + SV_H, top, 0xFF000000);
        }
        for (int x = 0; x < SV_W; x++) {
            g.fill(l + SV_X + x, t + HUE_Y, l + SV_X + x + 1, t + HUE_Y + HUE_H, 0xFF000000 | hsv(x / (float) SV_W, 1.0F, 1.0F));
        }
        int cx = l + SV_X + Math.round(sat * (SV_W - 1)), cy = t + SV_Y + Math.round((1.0F - val) * (SV_H - 1));
        g.renderOutline(cx - 2, cy - 2, 5, 5, val > 0.5F ? 0xFF000000 : 0xFFFFFFFF);
        int hx = l + SV_X + Math.round(hue * (SV_W - 1));
        g.fill(hx, t + HUE_Y - 2, hx + 1, t + HUE_Y + HUE_H + 2, 0xFFFFFFFF);
        // the colour being edited, big, by the hex box
        g.fill(l + 124, t + 100, l + 144, t + 116, 0xFFFFFFFF);
        g.fill(l + 125, t + 101, l + 143, t + 115, 0xFF000000 | colours[sel]);
        g.drawString(font, Component.translatable("screen.skyblocks.effect_block.editing", sel + 1), l + 226, t + 104, 0xA0A0A0);
        g.drawString(font, Component.translatable("screen.skyblocks.effect_block.hint"), l + 12, t + PANEL_H - 20, 0x808080);
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
