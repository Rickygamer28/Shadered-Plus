package com.example.skyblocks.client;

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
 * The time crystal's screen: whether it's running, its speed (x4 .. x256) and radius (4, 8, 12 blocks), on / off and its
 * dome shown or not.
 */
public class TimeCrystalScreen extends Screen {
    private static final int W = 260, H = 150;
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
                    b -> PacketDistributor.sendToServer(new TimeCrystalPayload(pos, TimeCrystalPayload.SPEED, k))).bounds(left + 8 + i * (bw + 4), top + 50, bw, 18).build());
        }
        int rw = (W - 16 - 2 * 4) / 3;
        for (int i = 0; i < radiusButtons.length; i++) {
            final int k = i;
            radiusButtons[i] = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.time_crystal.blocks", TimeCrystalBlockEntity.RADII[i]),
                    b -> PacketDistributor.sendToServer(new TimeCrystalPayload(pos, TimeCrystalPayload.RADIUS, k))).bounds(left + 8 + i * (rw + 4), top + 86, rw, 18).build());
        }
        int hw = (W - 16 - 4) / 2;
        onButton = addRenderableWidget(Button.builder(Component.empty(), b -> PacketDistributor.sendToServer(new TimeCrystalPayload(pos, TimeCrystalPayload.ON, 0)))
                .bounds(left + 8, top + 112, hw, 18).build());
        domeButton = addRenderableWidget(Button.builder(Component.empty(), b -> PacketDistributor.sendToServer(new TimeCrystalPayload(pos, TimeCrystalPayload.DOME, 0)))
                .bounds(left + 12 + hw, top + 112, hw, 18).build());
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
        double a = be.running() ? (ms / 1000.0) * (1 + be.speedIndex()) * 1.5 : 0.0;
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
        } else {
            status = Component.translatable("screen.skyblocks.time_crystal.status_running", be.speed(), be.radius());
            statusColour = TEAL;
        }
        g.drawString(font, status, left + 8, top + 20, statusColour, false);
        g.drawString(font, Component.translatable("screen.skyblocks.time_crystal.speed"), left + 8, top + 38, 0xFFB8B0C8, false);
        g.drawString(font, Component.translatable("screen.skyblocks.time_crystal.radius"), left + 8, top + 74, 0xFFB8B0C8, false);
        g.drawString(font, Component.translatable("screen.skyblocks.time_crystal.hint"), left + 8, top + H - 12 + 2, 0xFF6F687A, false);
    }
}
