package com.example.skyblocks.client;

import com.example.skyblocks.NeonSignBlockEntity;
import com.example.skyblocks.NeonSignPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The neon sign's menu: its text ('|' for a new line), one of the 16 colours (and a second one, for fading along the
 * text), how it glows, its lettering and its size. The sign behind updates as you go.
 */
public class NeonSignScreen extends Screen {
    private static final int PANEL_W = 236;
    private static final int PANEL_H = 262;
    private static final String[] MODES = {"steady", "flicker", "pulse", "rainbow", "scroll", "typing", "blink", "broken", "fade"};
    private static final String[] FONTS = {"classic", "italic", "bold", "thin", "wide", "narrow"};

    private final BlockPos pos;
    private String text;
    private int colour, mode, size, background, lettering, colour2;
    private boolean outline;
    /** Which colour the colour buttons set: the main one, or the fade's second one. */
    private boolean pickSecond;
    private EditBox box;

    public NeonSignScreen(BlockPos pos, String text, int colour, int mode, int size, int background, int lettering, int colour2) {
        super(Component.translatable("screen.skyblocks.neon_sign"));
        this.pos = pos;
        this.text = text;
        this.colour = colour;
        this.outline = (mode & NeonSignBlockEntity.OUTLINE_BIT) != 0;
        this.mode = mode & (NeonSignBlockEntity.OUTLINE_BIT - 1);
        this.size = size;
        this.background = background;
        this.lettering = lettering;
        this.colour2 = colour2;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        box = new EditBox(font, left() + 12, top() + 24, PANEL_W - 24, 18, Component.translatable("screen.skyblocks.neon_text"));
        box.setMaxLength(NeonSignBlockEntity.MAX_LENGTH);
        box.setValue(text);
        box.setResponder(v -> {
            text = v;
            send();
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        DyeColor[] dyes = DyeColor.values();
        for (int i = 0; i < dyes.length; i++) {
            final int c = dyes[i].getTextureDiffuseColor() & 0xFFFFFF;
            int x = left() + 12 + (i % 8) * 27, y = top() + 50 + (i / 8) * 24;
            addRenderableWidget(new Button(x, y, 24, 20, Component.empty(), b -> {
                if (pickSecond) {
                    colour2 = c;
                } else {
                    colour = c;
                }
                send();
            }, s -> Component.literal("colour")) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    super.renderWidget(g, mx, my, pt);
                    g.fill(getX() + 4, getY() + 4, getX() + width - 4, getY() + height - 4, 0xFF000000 | c);
                    if (colour == c) {
                        g.renderOutline(getX() + 2, getY() + 2, width - 4, height - 4, 0xFFFFFFFF);
                    }
                    if (colour2 == c && mode == NeonSignBlockEntity.FADE) { // (the fade's second colour: a gold frame)
                        g.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, 0xFFFFC040);
                    }
                }
            });
        }
        // the panel's colour: smoky (the default), clear, or any of the 16 colours (darkened, so the tubes still stand out)
        int[] bgs = new int[18];
        bgs[0] = NeonSignBlockEntity.DEFAULT_BACKGROUND;
        bgs[1] = 0xFFFFFF;
        for (int i = 0; i < 16; i++) {
            int c = dyes[i].getTextureDiffuseColor();
            bgs[i + 2] = (((c >> 16) & 255) * 3 / 5 << 16) | (((c >> 8) & 255) * 3 / 5 << 8) | ((c & 255) * 3 / 5);
        }
        for (int i = 0; i < bgs.length; i++) {
            final int c = bgs[i];
            int x = left() + 12 + (i % 9) * 24, y = top() + 164 + (i / 9) * 22;
            addRenderableWidget(new Button(x, y, 21, 19, Component.empty(), b -> {
                background = c;
                send();
            }, s -> Component.literal("background")) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    super.renderWidget(g, mx, my, pt);
                    g.fill(getX() + 4, getY() + 4, getX() + width - 4, getY() + height - 4, 0xFF000000 | c);
                    if (background == c) {
                        g.renderOutline(getX() + 2, getY() + 2, width - 4, height - 4, 0xFFFFFFFF);
                    }
                }
            });
        }
        addRenderableWidget(Button.builder(modeLabel(), b -> {
            mode = (mode + 1) % NeonSignBlockEntity.MODES;
            b.setMessage(modeLabel());
            send();
        }).bounds(left() + 12, top() + 102, 104, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            size = Math.max(1, size - 1);
            send();
        }).bounds(left() + 160, top() + 102, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            size = Math.min(NeonSignBlockEntity.MAX_SIZE, size + 1);
            send();
        }).bounds(left() + 204, top() + 102, 20, 20).build());
        addRenderableWidget(Button.builder(fontLabel(), b -> {
            lettering = (lettering + 1) % NeonSignBlockEntity.FONTS;
            b.setMessage(fontLabel());
            send();
        }).bounds(left() + 12, top() + 126, 104, 20).build());
        addRenderableWidget(Button.builder(pickLabel(), b -> {
            pickSecond = !pickSecond;
            b.setMessage(pickLabel());
        }).bounds(left() + 120, top() + 126, 104, 20).build());
        addRenderableWidget(Button.builder(outlineLabel(), b -> {
            outline = !outline;
            b.setMessage(outlineLabel());
            send();
        }).bounds(left() + 12, top() + PANEL_H - 28, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + PANEL_W - 88, top() + PANEL_H - 28, 76, 20).build());
    }

    private Component fontLabel() {
        return Component.translatable("screen.skyblocks.neon_font_" + FONTS[lettering]);
    }

    private Component pickLabel() {
        return Component.translatable(pickSecond ? "screen.skyblocks.neon_pick_fade" : "screen.skyblocks.neon_pick_main");
    }

    private Component outlineLabel() {
        return Component.translatable(outline ? "screen.skyblocks.neon_outline_on" : "screen.skyblocks.neon_outline_off");
    }

    private Component modeLabel() {
        return Component.translatable("screen.skyblocks.neon_mode_" + MODES[mode]);
    }

    private void send() {
        PacketDistributor.sendToServer(NeonSignPayload.of(pos, text, colour, mode | (outline ? NeonSignBlockEntity.OUTLINE_BIT : 0), lettering, size, background, colour2));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left(), top(), left() + PANEL_W, top() + PANEL_H, 0xC0101018);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left() + PANEL_W / 2, top() + 9, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal(Integer.toString(size)), left() + 192, top() + 108, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.skyblocks.neon_size"), left() + 124, top() + 108, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("screen.skyblocks.neon_hint"), left() + 12, top() + 150, 0x808080);
        graphics.drawString(font, Component.translatable("screen.skyblocks.neon_background"), left() + 12, top() + 210, 0xA0A0A0);
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
