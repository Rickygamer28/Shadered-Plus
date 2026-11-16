package com.example.skyblocks.client;

import com.example.skyblocks.PowerCoreBlockEntity;
import com.example.skyblocks.PowerCorePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The power core's screen: how much it holds (a bar in its look's colour), energy in and out each tick and how long
 * until it's full or empty at that rate, its chamber, its look (four buttons) and push mode.
 */
public class PowerCoreScreen extends Screen {
    private static final int W = 270, H = 196;
    /** Each look's colour (the bar, the frame round the screen). */
    private static final int[] ACCENT = {0xFFFF9A3C, 0xFF9A9CFF, 0xFFC070FF, 0xFF60C8FF};
    private final BlockPos pos;
    private int left, top;
    private final Button[] skinButtons = new Button[PowerCoreBlockEntity.SKINS.length];
    private Button pushButton;

    public PowerCoreScreen(BlockPos pos) {
        super(Component.translatable("block.skyblocks.power_core"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new PowerCoreScreen(pos));
    }

    private PowerCoreBlockEntity be() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof PowerCoreBlockEntity p ? p : null;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int bw = (W - 16 - 3 * 4) / 4;
        for (int i = 0; i < skinButtons.length; i++) {
            final int skin = i;
            skinButtons[i] = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.power_core.skin." + PowerCoreBlockEntity.SKINS[i]),
                    b -> PacketDistributor.sendToServer(new PowerCorePayload(pos, PowerCorePayload.SKIN, skin))).bounds(left + 8 + i * (bw + 4), top + 132, bw, 18).build());
        }
        pushButton = addRenderableWidget(Button.builder(Component.empty(), b -> PacketDistributor.sendToServer(new PowerCorePayload(pos, PowerCorePayload.PUSH, 0)))
                .bounds(left + 8, top + 158, W - 16, 18).build());
    }

    @Override
    public void tick() {
        PowerCoreBlockEntity be = be();
        if (be == null || minecraft == null || minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 15.0 * 15.0) {
            onClose();
            return;
        }
        for (int i = 0; i < skinButtons.length; i++) {
            skinButtons[i].active = be.skin() != i; // (the one it's showing: pressed in)
        }
        pushButton.setMessage(Component.translatable(be.push() ? "screen.skyblocks.power_core.push_on" : "screen.skyblocks.power_core.push_off"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        PowerCoreBlockEntity be = be();
        int accent = be != null ? ACCENT[Math.floorMod(be.skin(), ACCENT.length)] : 0xFFFF9A3C;
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, accent);
        g.fill(left, top, left + W, top + H, 0xF0110D16);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        PowerCoreBlockEntity be = be();
        if (be == null) {
            return;
        }
        int accent = ACCENT[Math.floorMod(be.skin(), ACCENT.length)];
        g.drawString(font, title, left + 8, top + 8, 0xFFFFF2E0, false);
        Component chamber = be.chamber() > 0
                ? Component.translatable("screen.skyblocks.power_core.chamber", be.chamber(), be.chamber(), be.chamber())
                : Component.translatable("screen.skyblocks.power_core.alone");
        g.drawString(font, chamber, left + 8, top + 19, be.chamber() > 0 ? accent : 0xFF8F8A99, false);
        // how full: a bar, lighter towards its end, a soft shine moving along it
        int bx0 = left + 8, bx1 = left + W - 8, by0 = top + 34, by1 = top + 58;
        g.fill(bx0 - 1, by0 - 1, bx1 + 1, by1 + 1, 0xFF3A3344);
        g.fill(bx0, by0, bx1, by1, 0xFF1A1522);
        double full = Math.min(1.0, (double) be.energy() / be.capacity());
        int fx = bx0 + (int) Math.round((bx1 - bx0) * full);
        if (fx > bx0) {
            for (int x = bx0; x < fx; x++) {
                float k = (x - bx0) / (float) Math.max(1, bx1 - bx0);
                g.fill(x, by0, x + 1, by1, mix(darker(accent), accent, 0.35F + 0.65F * k));
            }
            long ms = System.currentTimeMillis();
            int shine = bx0 + (int) ((ms / 6) % Math.max(1, (fx - bx0) + 40)) - 20;
            for (int x = Math.max(bx0, shine - 10); x < Math.min(fx, shine + 10); x++) {
                g.fill(x, by0, x + 1, by1, 0x30FFFFFF);
            }
            g.fill(bx0, by0, fx, by0 + 3, 0x40FFFFFF);
        }
        String pct = String.format(java.util.Locale.ROOT, full >= 0.9995 ? "100%%" : full > 0 && full < 0.001 ? "<0.1%%" : "%.1f%%", full * 100);
        g.drawCenteredString(font, pct, (bx0 + bx1) / 2, by0 + 8, 0xFFFFFFFF);
        // how much
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.stored", PowerCoreBlockEntity.fe(be.energy()),
                PowerCoreBlockEntity.fe(be.capacity())), left + 8, top + 66, 0xFFE8E0F0, false);
        // in, out, and how long at that rate
        long in = be.rateIn(), out = be.rateOut(), net = in - out;
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.in", PowerCoreBlockEntity.fe(in)), left + 8, top + 82, 0xFF7FE08F, false);
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.out", PowerCoreBlockEntity.fe(out)), left + 8 + 86, top + 82, 0xFFFF8F7F, false);
        String sign = net > 0 ? "+" : net < 0 ? "-" : "";
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.net", sign + PowerCoreBlockEntity.fe(Math.abs(net))), left + 8 + 172, top + 82,
                net > 0 ? 0xFF7FE08F : net < 0 ? 0xFFFF8F7F : 0xFF8F8A99, false);
        Component eta;
        if (net > 0 && be.energy() < be.capacity()) {
            eta = Component.translatable("screen.skyblocks.power_core.full_in", time((be.capacity() - be.energy()) / net));
        } else if (net < 0 && be.energy() > 0) {
            eta = Component.translatable("screen.skyblocks.power_core.empty_in", time(be.energy() / -net));
        } else {
            eta = Component.translatable("screen.skyblocks.power_core.steady");
        }
        g.drawString(font, eta, left + 8, top + 96, 0xFFB8B0C8, false);
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.look"), left + 8, top + 120, 0xFFB8B0C8, false);
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.hint"), left + 8, top + H - 14, 0xFF6F687A, false);
    }

    /** A number of ticks as a time: "45s", "12m 5s", "3h 20m", "2d 4h", "over a year". */
    private static String time(long ticks) {
        long s = ticks / 20;
        if (s < 60) {
            return s + "s";
        }
        if (s < 3600) {
            return s / 60 + "m " + s % 60 + "s";
        }
        if (s < 86400) {
            return s / 3600 + "h " + s % 3600 / 60 + "m";
        }
        if (s < 86400L * 365) {
            return s / 86400 + "d " + s % 86400 / 3600 + "h";
        }
        return "> 1y";
    }

    private static int darker(int c) {
        return 0xFF000000 | ((c >> 16 & 255) * 2 / 5) << 16 | ((c >> 8 & 255) * 2 / 5) << 8 | (c & 255) * 2 / 5;
    }

    private static int mix(int a, int b, float k) {
        int r = (int) ((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k);
        int gg = (int) ((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k);
        int bb = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | r << 16 | gg << 8 | bb;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
