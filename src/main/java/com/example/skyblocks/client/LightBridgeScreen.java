package com.example.skyblocks.client;

import com.example.skyblocks.LightBridgeEmitterBlock;
import com.example.skyblocks.LightBridgeEmitterBlockEntity;
import com.example.skyblocks.LightBridgePath;
import com.example.skyblocks.LightBridgeSettingsPayload;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A light bridge's settings: what it is (walkway, arch, moving walkway, wall, tunnel) and which way it carries, how
 * wide, its pattern, how bright, glowing rails, and its colour. Each change goes straight to both its emitters.
 */
public class LightBridgeScreen extends Screen {
    private static final String[] PATTERNS = {"arrows", "honeycomb", "plain", "stripes", "stars"};
    private static final String[] GLOWS = {"faint", "soft", "bright", "solid"};
    private final BlockPos pos;
    private int mode, width, pattern, opacity, colour;
    private boolean reverse, rails;
    private int swatchX, swatchY;

    public LightBridgeScreen(BlockPos pos, LightBridgeEmitterBlockEntity be) {
        super(Component.translatable("block.skyblocks.light_bridge_emitter"));
        this.pos = pos;
        mode = be.mode();
        reverse = be.reverse();
        width = be.width();
        pattern = be.pattern();
        opacity = be.opacity();
        rails = be.rails();
        colour = be.getBlockState().getValue(LightBridgeEmitterBlock.COLOR);
    }

    @Override
    protected void init() {
        int w = 200, x = width / 2 - w / 2, y = height / 2 - 92;
        row(x, y, w, () -> label("kind", Component.translatable("light_bridge.skyblocks." + LightBridgePath.MODE_NAMES[mode])),
                () -> mode = (mode + 1) % LightBridgePath.MODES);
        row(x, y + 24, w, () -> label("carries", Component.translatable("screen.skyblocks.light_bridge." + (reverse ? "backward" : "forward"))),
                () -> reverse = !reverse);
        row(x, y + 48, w, () -> label("width", Component.literal(String.valueOf(width))), () -> width = width % 3 + 1);
        row(x, y + 72, w, () -> label("pattern", Component.translatable("screen.skyblocks.light_bridge.pattern." + PATTERNS[pattern])),
                () -> pattern = (pattern + 1) % PATTERNS.length);
        row(x, y + 96, w, () -> label("glow", Component.translatable("screen.skyblocks.light_bridge.glow." + GLOWS[opacity])),
                () -> opacity = (opacity + 1) % GLOWS.length);
        row(x, y + 120, w, () -> label("rails", Component.translatable(rails ? "options.on" : "options.off")), () -> rails = !rails);
        swatchX = width / 2 - (17 * 12) / 2;
        swatchY = y + 148;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 50, y + 172, 100, 20).build());
    }

    private Component label(String key, Component value) {
        return Component.translatable("screen.skyblocks.light_bridge." + key, value);
    }

    private void row(int x, int y, int w, Supplier<Component> text, Runnable change) {
        Button b = Button.builder(text.get(), btn -> {
            change.run();
            btn.setMessage(text.get());
            send();
        }).bounds(x, y, w, 20).build();
        addRenderableWidget(b);
    }

    private void send() {
        PacketDistributor.sendToServer(new LightBridgeSettingsPayload(pos, mode, reverse, width, pattern, opacity, rails, colour));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (my >= swatchY && my < swatchY + 10 && mx >= swatchX && mx < swatchX + 17 * 12) {
            colour = Mth.clamp((int) ((mx - swatchX) / 12), 0, 16);
            send();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 108, 0xFFFFFFFF);
        for (int i = 0; i < 17; i++) {
            int x = swatchX + i * 12, y = swatchY;
            if (i == colour) g.fill(x - 1, y - 1, x + 11, y + 11, 0xFFFFFFFF);
            if (i < 16) {
                g.fill(x, y, x + 10, y + 10, 0xFF000000 | DyeColor.byId(i).getTextureDiffuseColor());
            } else {
                for (int k = 0; k < 10; k++) {
                    g.fill(x + k, y, x + k + 1, y + 10, 0xFF000000 | Mth.hsvToRgb(k / 10.0F, 0.7F, 1.0F));
                }
            }
        }
        if (mouseY >= swatchY && mouseY < swatchY + 10 && mouseX >= swatchX && mouseX < swatchX + 17 * 12) {
            int i = Mth.clamp((mouseX - swatchX) / 12, 0, 16);
            g.renderTooltip(font, i < 16 ? Component.translatable("color.minecraft." + DyeColor.byId(i).getName())
                    : Component.translatable("screen.skyblocks.light_bridge.rainbow"), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
