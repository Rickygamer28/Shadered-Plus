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
    private static final int W = 240, H = 156;
    /** Each look's colour (the bar, the frame round the screen). */
    private static final int[] ACCENT = {0xFFFF9A3C, 0xFF9A9CFF, 0xFFC070FF, 0xFF60C8FF};
    private final BlockPos pos;
    private int left, top;
    private final Button[] skinButtons = new Button[PowerCoreBlockEntity.SKINS.length];
    private MachineUi.Flat pushButton;

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

    private int accent() {
        PowerCoreBlockEntity be = be();
        return be != null ? ACCENT[Math.floorMod(be.skin(), ACCENT.length)] : ACCENT[0];
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int bw = (W - 16 - 3 * 4) / 4;
        for (int i = 0; i < skinButtons.length; i++) {
            final int skin = i;
            skinButtons[i] = addRenderableWidget(MachineUi.button(left + 8 + i * (bw + 4), top + 104, bw, 14,
                    Component.translatable("screen.skyblocks.power_core.skin." + PowerCoreBlockEntity.SKINS[i]),
                    b -> PacketDistributor.sendToServer(new PowerCorePayload(pos, PowerCorePayload.SKIN, skin)), this::accent));
        }
        pushButton = (MachineUi.Flat) addRenderableWidget(MachineUi.button(left + 8, top + 124, W - 16, 14, Component.empty(),
                b -> PacketDistributor.sendToServer(new PowerCorePayload(pos, PowerCorePayload.PUSH, 0)), this::accent));
    }

    @Override
    public void tick() {
        PowerCoreBlockEntity be = be();
        if (be == null || minecraft == null || minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 15.0 * 15.0) {
            onClose();
            return;
        }
        for (int i = 0; i < skinButtons.length; i++) {
            skinButtons[i].active = be.skin() != i; // (the one it's showing: lit)
        }
        pushButton.lit = be.push();
        pushButton.setMessage(be.push() ? (be.pushTargets() > 0 ? Component.translatable("screen.skyblocks.power_core.push_on_n", be.pushTargets())
                : Component.translatable("screen.skyblocks.power_core.push_on_none")) : Component.translatable("screen.skyblocks.power_core.push_off"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        MachineUi.panel(g, left, top, W, H, accent());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        PowerCoreBlockEntity be = be();
        if (be == null) {
            return;
        }
        int accent = accent();
        Component chamber = be.chamber() > 0
                ? Component.translatable("screen.skyblocks.power_core.chamber", be.chamber(), be.chamber(), be.chamber())
                : Component.translatable("screen.skyblocks.power_core.alone");
        MachineUi.header(g, font, title, chamber, left, top, W, be.chamber() > 0 ? accent : MachineUi.DIM);
        // how full: a bar, lighter towards its end, a soft shine moving along it
        int bx0 = left + 8, bx1 = left + W - 8, by0 = top + 26, by1 = top + 44;
        g.fill(bx0 - 1, by0 - 1, bx1 + 1, by1 + 1, MachineUi.SLOT_EDGE);
        g.fill(bx0, by0, bx1, by1, MachineUi.TRACK);
        double full = Math.min(1.0, (double) be.energy() / be.capacity());
        int fx = bx0 + (int) Math.round((bx1 - bx0) * full);
        if (fx > bx0) {
            for (int x = bx0; x < fx; x++) {
                float k = (x - bx0) / (float) Math.max(1, bx1 - bx0);
                g.fill(x, by0, x + 1, by1, MachineUi.mix(MachineUi.shade(accent, 0.4F), accent, 0.35F + 0.65F * k));
            }
            long ms = System.currentTimeMillis();
            int shine = bx0 + (int) ((ms / 6) % Math.max(1, (fx - bx0) + 40)) - 20;
            for (int x = Math.max(bx0, shine - 10); x < Math.min(fx, shine + 10); x++) {
                g.fill(x, by0, x + 1, by1, 0x30FFFFFF);
            }
            g.fill(bx0, by0, fx, by0 + 2, 0x40FFFFFF);
        }
        String pct = String.format(java.util.Locale.ROOT, full >= 0.9995 ? "100%%" : full > 0 && full < 0.001 ? "<0.1%%" : "%.1f%%", full * 100);
        g.drawCenteredString(font, pct, (bx0 + bx1) / 2, by0 + 5, 0xFFFFFFFF);
        // how much
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.stored", PowerCoreBlockEntity.fe(be.energy()),
                PowerCoreBlockEntity.fe(be.capacity())), left + 8, top + 52, MachineUi.TEXT, false);
        // in, out, and how long at that rate
        long in = be.rateIn(), out = be.rateOut(), net = in - out;
        int col = (W - 16) / 3;
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.in", PowerCoreBlockEntity.fe(in)), left + 8, top + 64, MachineUi.GOOD, false);
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.out", PowerCoreBlockEntity.fe(out)), left + 8 + col, top + 64, MachineUi.BAD, false);
        String sign = net > 0 ? "+" : net < 0 ? "-" : "";
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.net", sign + PowerCoreBlockEntity.fe(Math.abs(net))), left + 8 + 2 * col, top + 64,
                net > 0 ? MachineUi.GOOD : net < 0 ? MachineUi.BAD : MachineUi.DIM, false);
        Component eta;
        if (net > 0 && be.energy() < be.capacity()) {
            eta = Component.translatable("screen.skyblocks.power_core.full_in", time((be.capacity() - be.energy()) / net));
        } else if (net < 0 && be.energy() > 0) {
            eta = Component.translatable("screen.skyblocks.power_core.empty_in", time(be.energy() / -net));
        } else {
            eta = Component.translatable("screen.skyblocks.power_core.steady");
        }
        g.drawString(font, eta, left + 8, top + 75, MachineUi.DIM, false);
        MachineUi.divider(g, left + 8, top + 88, W - 16);
        MachineUi.label(g, font, Component.translatable("screen.skyblocks.power_core.look"), left + 8, top + 93);
        g.drawString(font, MachineUi.fit(font, Component.translatable("screen.skyblocks.power_core.hint").getString(), W - 16), left + 8, top + H - 12,
                MachineUi.FAINT, false);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
