package com.example.skyblocks.client;

import com.example.skyblocks.HourglassBlockEntity;
import com.example.skyblocks.HourglassPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** An hourglass's screen: how long it runs, how much is left, looping on / off, and turning it over. */
public class HourglassScreen extends Screen {
    private static final int W = 240, H = 150, GOLD = 0xFFFFC860;
    private final BlockPos pos;
    private int left, top;
    private final Button[] durations = new Button[HourglassBlockEntity.DURATIONS.length];
    private Button loopButton;

    public HourglassScreen(BlockPos pos) {
        super(Component.translatable("block.skyblocks.hourglass"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new HourglassScreen(pos));
    }

    private HourglassBlockEntity be() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof HourglassBlockEntity h ? h : null;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int per = 6, bw = (W - 16 - (per - 1) * 3) / per;
        for (int i = 0; i < durations.length; i++) {
            final int k = i;
            durations[i] = addRenderableWidget(Button.builder(Component.literal(HourglassBlockEntity.label(HourglassBlockEntity.DURATIONS[i])),
                    b -> PacketDistributor.sendToServer(new HourglassPayload(pos, HourglassPayload.DURATION, k)))
                    .bounds(left + 8 + (i % per) * (bw + 3), top + 50 + (i / per) * 22, bw, 18).build());
        }
        int hw = (W - 16 - 4) / 2;
        loopButton = addRenderableWidget(Button.builder(Component.empty(), b -> PacketDistributor.sendToServer(new HourglassPayload(pos, HourglassPayload.LOOP, 0)))
                .bounds(left + 8, top + 104, hw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.hourglass.turn"),
                b -> PacketDistributor.sendToServer(new HourglassPayload(pos, HourglassPayload.TURN, 0))).bounds(left + 12 + hw, top + 104, hw, 20).build());
    }

    @Override
    public void tick() {
        HourglassBlockEntity be = be();
        if (be == null || minecraft == null || minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 10.0 * 10.0) {
            onClose();
            return;
        }
        for (int i = 0; i < durations.length; i++) {
            durations[i].active = be.durationIndex() != i;
        }
        loopButton.setMessage(Component.translatable(be.loop() ? "screen.skyblocks.hourglass.loop_on" : "screen.skyblocks.hourglass.loop_off"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, GOLD);
        g.fill(left, top, left + W, top + H, 0xF0141008);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        HourglassBlockEntity be = be();
        if (be == null) {
            return;
        }
        g.drawString(font, title, left + 8, top + 8, 0xFFFFF0D8, false);
        float fallen = be.fallen(partialTick);
        Component status = be.running()
                ? Component.translatable("screen.skyblocks.hourglass.left", time(Math.round((1.0F - fallen) * be.ticks())))
                : Component.translatable("screen.skyblocks.hourglass.stopped");
        g.drawString(font, status, left + 8, top + 20, be.running() ? GOLD : 0xFF9A9080, false);
        // the sand: a bar
        int x0 = left + 8, x1 = left + W - 8, y0 = top + 31, y1 = top + 37;
        g.fill(x0, y0, x1, y1, 0xFF2A2418);
        g.fill(x0, y0, x0 + Math.round((x1 - x0) * (1.0F - fallen)), y1, GOLD);
        g.drawString(font, Component.translatable("screen.skyblocks.hourglass.duration"), left + 8, top + 41, 0xFFB8B0A0, false);
        g.drawString(font, Component.translatable("screen.skyblocks.hourglass.hint"), left + 8, top + H - 14, 0xFF6F6858, false);
    }

    private static String time(int ticks) {
        int s = (ticks + 19) / 20;
        return s < 60 ? s + "s" : s < 3600 ? s / 60 + "m " + s % 60 + "s" : s / 3600 + "h " + s % 3600 / 60 + "m";
    }
}
