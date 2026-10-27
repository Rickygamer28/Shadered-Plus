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
import net.minecraftforge.network.PacketDistributor;

/**
 * The neon sign's menu: its text ('|' for a new line), one of the 16 colours, how it glows (steady, flicker, pulse,
 * rainbow) and its size. The sign behind updates as you go.
 */
public class NeonSignScreen extends Screen {
    private static final int PANEL_W = 236;
    private static final int PANEL_H = 238;
    private static final String[] MODES = {"steady", "flicker", "pulse", "rainbow", "scroll", "typing", "blink"};

    private final BlockPos pos;
    private String text;
    private int colour, mode, size, background;
    private boolean outline;
    private EditBox box;

    public NeonSignScreen(BlockPos pos, String text, int colour, int mode, int size, int background) {
        super(Component.translatable("screen.skyblocks.neon_sign"));
        this.pos = pos;
        this.text = text;
        this.colour = colour;
        this.outline = (mode & NeonSignBlockEntity.OUTLINE_BIT) != 0;
        this.mode = mode & (NeonSignBlockEntity.OUTLINE_BIT - 1);
        this.size = size;
        this.background = background;
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
                colour = c;
                send();
            }, s -> Component.literal("colour")) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    super.renderWidget(g, mx, my, pt);
                    g.fill(getX() + 4, getY() + 4, getX() + width - 4, getY() + height - 4, 0xFF000000 | c);
                    if (colour == c) {
                        g.renderOutline(getX() + 2, getY() + 2, width - 4, height - 4, 0xFFFFFFFF);
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
            int x = left() + 12 + (i % 9) * 24, y = top() + 140 + (i / 9) * 22;
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
        addRenderableWidget(Button.builder(outlineLabel(), b -> {
            outline = !outline;
            b.setMessage(outlineLabel());
            send();
        }).bounds(left() + 12, top() + PANEL_H - 28, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + PANEL_W - 88, top() + PANEL_H - 28, 76, 20).build());
    }

    private Component outlineLabel() {
        return Component.translatable(outline ? "screen.skyblocks.neon_outline_on" : "screen.skyblocks.neon_outline_off");
    }

    private Component modeLabel() {
        return Component.translatable("screen.skyblocks.neon_mode_" + MODES[mode]);
    }

    private void send() {
        com.example.skyblocks.SkyBlocksMod.NETWORK.sendToServer(new NeonSignPayload(pos, text, colour, mode | (outline ? NeonSignBlockEntity.OUTLINE_BIT : 0), size, background));
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(left(), top(), left() + PANEL_W, top() + PANEL_H, 0xC0101018);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); // (1.20.1: not done by Screen.render)
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left() + PANEL_W / 2, top() + 9, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal(Integer.toString(size)), left() + 192, top() + 108, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.skyblocks.neon_size"), left() + 124, top() + 108, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("screen.skyblocks.neon_hint"), left() + 12, top() + 126, 0x808080);
        graphics.drawString(font, Component.translatable("screen.skyblocks.neon_background"), left() + 12, top() + 186, 0xA0A0A0);
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
