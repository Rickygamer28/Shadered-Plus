package com.example.skyblocks.client;

import com.example.skyblocks.PowerCoreBlockEntity;
import com.example.skyblocks.TimeCrystalBlockEntity;
import com.example.skyblocks.TimeCrystalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The time crystal's screen: whether it's running, the energy it holds and uses, its speed (x2 .. x16) and radius
 * (4, 8, 12 blocks) - each with what it would cost - and on / off and its dome shown or not.
 */
public class TimeCrystalScreen extends Screen {
    private static final int W = 260, H = 200;
    private static final int ACCENT = 0xFFB070FF, TEAL = 0xFF5CF0DC;
    private final BlockPos pos;
    private int left, top;
    private final Button[] speedButtons = new Button[TimeCrystalBlockEntity.SPEEDS.length];
    private final Button[] radiusButtons = new Button[TimeCrystalBlockEntity.RADII.length];
    private Button onButton, domeButton;

    public TimeCrystalScreen(BlockPos pos) {
        super(Component.translatable("block.skyblocks.time_crystal"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new TimeCrystalScreen(pos));
    }

    private TimeCrystalBlockEntity be() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof TimeCrystalBlockEntity t ? t : null;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int bw = (W - 16 - 3 * 4) / 4;
        for (int i = 0; i < speedButtons.length; i++) {
            final int k = i;
            speedButtons[i] = addRenderableWidget(Button.builder(Component.literal("x" + TimeCrystalBlockEntity.SPEEDS[i]),
                    b -> PacketDistributor.sendToServer(new TimeCrystalPayload(pos, TimeCrystalPayload.SPEED, k))).bounds(left + 8 + i * (bw + 4), top + 100, bw, 18).build());
        }
        int rw = (W - 16 - 2 * 4) / 3;
        for (int i = 0; i < radiusButtons.length; i++) {
            final int k = i;
            radiusButtons[i] = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.time_crystal.blocks", TimeCrystalBlockEntity.RADII[i]),
                    b -> PacketDistributor.sendToServer(new TimeCrystalPayload(pos, TimeCrystalPayload.RADIUS, k))).bounds(left + 8 + i * (rw + 4), top + 136, rw, 18).build());
        }
        int hw = (W - 16 - 4) / 2;
        onButton = addRenderableWidget(Button.builder(Component.empty(), b -> PacketDistributor.sendToServer(new TimeCrystalPayload(pos, TimeCrystalPayload.ON, 0)))
                .bounds(left + 8, top + 162, hw, 18).build());
        domeButton = addRenderableWidget(Button.builder(Component.empty(), b -> PacketDistributor.sendToServer(new TimeCrystalPayload(pos, TimeCrystalPayload.DOME, 0)))
                .bounds(left + 12 + hw, top + 162, hw, 18).build());
    }

    @Override
    public void tick() {
        TimeCrystalBlockEntity be = be();
        if (be == null || minecraft == null || minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 10.0 * 10.0) {
            onClose();
            return;
        }
        for (int i = 0; i < speedButtons.length; i++) {
            speedButtons[i].active = be.speedIndex() != i;
        }
        for (int i = 0; i < radiusButtons.length; i++) {
            radiusButtons[i].active = be.radiusIndex() != i;
        }
        onButton.setMessage(Component.translatable(be.on() ? "screen.skyblocks.time_crystal.on" : "screen.skyblocks.time_crystal.off"));
        domeButton.setMessage(Component.translatable(be.dome() ? "screen.skyblocks.time_crystal.dome_on" : "screen.skyblocks.time_crystal.dome_off"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, ACCENT);
        g.fill(left, top, left + W, top + H, 0xF0100C18);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        TimeCrystalBlockEntity be = be();
        if (be == null) {
            return;
        }
        g.drawString(font, title, left + 8, top + 8, 0xFFF2E8FF, false);
        // a little clock in the corner, its hand going round as fast as it's set
        int cx = left + W - 18, cy = top + 14;
        long ms = System.currentTimeMillis();
        double a = be.running() ? (ms / 1000.0) * be.speed() * 0.5 : 0.0;
        for (int i = 0; i < 12; i++) {
            double t = i / 12.0 * Math.PI * 2;
            g.fill(cx + (int) Math.round(Math.sin(t) * 9), cy - (int) Math.round(Math.cos(t) * 9),
                    cx + (int) Math.round(Math.sin(t) * 9) + 1, cy - (int) Math.round(Math.cos(t) * 9) + 1, TEAL);
        }
        for (int s = 0; s <= 7; s++) {
            g.fill(cx + (int) Math.round(Math.sin(a) * s), cy - (int) Math.round(Math.cos(a) * s),
                    cx + (int) Math.round(Math.sin(a) * s) + 1, cy - (int) Math.round(Math.cos(a) * s) + 1, 0xFFFFC060);
        }
        Component status;
        int statusColour;
        if (!be.on()) {
            status = Component.translatable("screen.skyblocks.time_crystal.status_off");
            statusColour = 0xFF8F8A99;
        } else if (be.running()) {
            status = Component.translatable("screen.skyblocks.time_crystal.status_running", be.speed(), be.radius());
            statusColour = TEAL;
        } else {
            status = Component.translatable("screen.skyblocks.time_crystal.status_hungry");
            statusColour = 0xFFFF8F7F;
        }
        g.drawString(font, status, left + 8, top + 20, statusColour, false);
        // energy: a bar
        int bx0 = left + 8, bx1 = left + W - 8, by0 = top + 34, by1 = top + 46;
        g.fill(bx0 - 1, by0 - 1, bx1 + 1, by1 + 1, 0xFF3A3344);
        g.fill(bx0, by0, bx1, by1, 0xFF1A1522);
        double full = Math.min(1.0, (double) be.energy() / TimeCrystalBlockEntity.CAPACITY);
        int fx = bx0 + (int) Math.round((bx1 - bx0) * full);
        for (int x = bx0; x < fx; x++) {
            float k = (x - bx0) / (float) Math.max(1, bx1 - bx0);
            g.fill(x, by0, x + 1, by1, mix(0xFF5A2E9A, ACCENT, 0.35F + 0.65F * k));
        }
        g.drawString(font, Component.translatable("screen.skyblocks.time_crystal.stored", PowerCoreBlockEntity.fe(be.energy()),
                PowerCoreBlockEntity.fe(TimeCrystalBlockEntity.CAPACITY)), left + 8, top + 52, 0xFFE8E0F0, false);
        long cost = be.cost();
        g.drawString(font, Component.translatable("screen.skyblocks.time_crystal.uses", PowerCoreBlockEntity.fe(cost)), left + 8, top + 64, 0xFFFFC27F, false);
        if (be.running() && cost > 0) {
            g.drawString(font, Component.translatable("screen.skyblocks.time_crystal.lasts", time(be.energy() / cost)), left + 132, top + 64, 0xFFB8B0C8, false);
        }
        g.drawString(font, Component.translatable("screen.skyblocks.time_crystal.speed"), left + 8, top + 88, 0xFFB8B0C8, false);
        g.drawString(font, Component.translatable("screen.skyblocks.time_crystal.radius"), left + 8, top + 124, 0xFFB8B0C8, false);
        // what each would cost, on hovering its button
        for (int i = 0; i < speedButtons.length; i++) {
            if (speedButtons[i].isHovered()) {
                g.renderTooltip(font, Component.translatable("screen.skyblocks.time_crystal.would_use",
                        PowerCoreBlockEntity.fe(TimeCrystalBlockEntity.cost(TimeCrystalBlockEntity.SPEEDS[i], be.radius()))), mouseX, mouseY);
            }
        }
        for (int i = 0; i < radiusButtons.length; i++) {
            if (radiusButtons[i].isHovered()) {
                g.renderTooltip(font, Component.translatable("screen.skyblocks.time_crystal.would_use",
                        PowerCoreBlockEntity.fe(TimeCrystalBlockEntity.cost(be.speed(), TimeCrystalBlockEntity.RADII[i]))), mouseX, mouseY);
            }
        }
        g.drawString(font, Component.translatable("screen.skyblocks.time_crystal.hint"), left + 8, top + H - 12 + 2, 0xFF6F687A, false);
    }

    private static String time(long ticks) {
        long s = ticks / 20;
        return s < 60 ? s + "s" : s < 3600 ? s / 60 + "m " + s % 60 + "s" : s / 3600 + "h " + s % 3600 / 60 + "m";
    }

    private static int mix(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
