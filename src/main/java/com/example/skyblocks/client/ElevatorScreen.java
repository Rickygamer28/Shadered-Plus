package com.example.skyblocks.client;

import com.example.skyblocks.ElevatorBaseBlockEntity;
import com.example.skyblocks.ElevatorCabinEntity;
import com.example.skyblocks.ElevatorPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * An elevator's screen (from inside its cabin, its base, or a call panel where the cabin is): its floors - a button for
 * each, the one it's at pressed in - where it is and where it's going, and its look and speed.
 */
public class ElevatorScreen extends Screen {
    private static final int W = 276, PER_ROW = 8, BUTTON = 30;
    /** Each look's colour. */
    public static final int[] ACCENT = {0xFF6CC8FF, 0xFF7CFFCC, 0xFFE59CFF, 0xFFB09CFF, 0xFFC68CFF, 0xFFFFE08C};
    private final BlockPos base;
    private int left, top, h;
    private Button[] floorButtons = new Button[0];
    private final Button[] skinButtons = new Button[ElevatorBaseBlockEntity.SKINS.length];
    private final Button[] speedButtons = new Button[ElevatorBaseBlockEntity.SPEEDS.length];
    private int shownFloors = -1;

    public ElevatorScreen(BlockPos base) {
        super(Component.translatable("screen.skyblocks.elevator"));
        this.base = base;
    }

    public static void open(BlockPos base) {
        Minecraft.getInstance().setScreen(new ElevatorScreen(base));
    }

    private ElevatorBaseBlockEntity be() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(base) instanceof ElevatorBaseBlockEntity e ? e : null;
    }

    private ElevatorCabinEntity cabin(ElevatorBaseBlockEntity be) {
        Minecraft mc = Minecraft.getInstance();
        List<ElevatorCabinEntity> l = mc.level.getEntitiesOfClass(ElevatorCabinEntity.class, be.shaft(), c -> c.base().equals(base));
        return l.isEmpty() ? null : l.get(0);
    }

    @Override
    protected void init() {
        ElevatorBaseBlockEntity be = be();
        int n = be != null ? be.floors().length : 1;
        shownFloors = n;
        int rows = (n + PER_ROW - 1) / PER_ROW;
        h = 64 + rows * (BUTTON + 4) + 74;
        left = (width - W) / 2;
        top = (height - h) / 2;
        floorButtons = new Button[n];
        int gridW = Math.min(n, PER_ROW) * (BUTTON + 4) - 4;
        // floors bottom-up: floor 1 at the bottom row's left
        for (int i = 0; i < n; i++) {
            final int floor = i;
            int row = rows - 1 - i / PER_ROW, col = i % PER_ROW;
            floorButtons[i] = addRenderableWidget(Button.builder(Component.literal(String.valueOf(i + 1)),
                    b -> {
                        PacketDistributor.sendToServer(new ElevatorPayload(base, ElevatorPayload.GO, floor));
                        onClose();
                    }).bounds(left + (W - gridW) / 2 + col * (BUTTON + 4), top + 48 + row * (BUTTON + 4), BUTTON, BUTTON).build());
        }
        int y = top + 48 + rows * (BUTTON + 4) + 18;
        int sw = (W - 16 - 5 * 3) / 6;
        for (int i = 0; i < skinButtons.length; i++) {
            final int k = i;
            skinButtons[i] = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.elevator.skin." + ElevatorBaseBlockEntity.SKINS[i]),
                    b -> PacketDistributor.sendToServer(new ElevatorPayload(base, ElevatorPayload.SKIN, k))).bounds(left + 8 + i * (sw + 3), y, sw, 18).build());
        }
        int pw = (W - 16 - 2 * 4) / 3;
        for (int i = 0; i < speedButtons.length; i++) {
            final int k = i;
            speedButtons[i] = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.elevator.speed." + i),
                    b -> PacketDistributor.sendToServer(new ElevatorPayload(base, ElevatorPayload.SPEED, k))).bounds(left + 8 + i * (pw + 4), y + 34, pw, 18).build());
        }
    }

    @Override
    public void tick() {
        ElevatorBaseBlockEntity be = be();
        if (be == null || minecraft == null || minecraft.player == null || !be.shaft().inflate(8.0).contains(minecraft.player.position())) {
            onClose();
            return;
        }
        if (be.floors().length != shownFloors) {
            rebuildWidgets();
            return;
        }
        ElevatorCabinEntity c = cabin(be);
        int at = c != null ? be.floorAt(c.landing()) : 0;
        boolean moving = c != null && c.moving();
        for (int i = 0; i < floorButtons.length; i++) {
            floorButtons[i].active = moving || i != at;
        }
        for (int i = 0; i < skinButtons.length; i++) {
            skinButtons[i].active = be.skin() != i;
        }
        for (int i = 0; i < speedButtons.length; i++) {
            speedButtons[i].active = be.speedIndex() != i;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        ElevatorBaseBlockEntity be = be();
        int accent = ACCENT[be != null ? be.skin() : 0];
        g.fill(left - 1, top - 1, left + W + 1, top + h + 1, accent);
        g.fill(left, top, left + W, top + h, 0xF0101018);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ElevatorBaseBlockEntity be = be();
        if (be == null) {
            return;
        }
        int accent = ACCENT[be.skin()];
        g.drawString(font, title, left + 8, top + 8, 0xFFF0F0FF, false);
        ElevatorCabinEntity c = cabin(be);
        Component status;
        if (c == null) {
            status = Component.translatable("screen.skyblocks.elevator.no_cabin");
        } else if (c.moving()) {
            int to = be.floorAt(c.target()) + 1;
            status = Component.translatable(c.target() > c.landing() ? "screen.skyblocks.elevator.going_up" : "screen.skyblocks.elevator.going_down", to);
        } else {
            status = Component.translatable("screen.skyblocks.elevator.at", be.floorAt(c.landing()) + 1, be.floors().length);
        }
        g.drawString(font, status, left + 8, top + 20, accent, false);
        g.drawString(font, Component.translatable("screen.skyblocks.elevator.choose"), left + 8, top + 34, 0xFFB8B0C8, false);
        int y = floorButtons.length > 0 ? floorButtons[0].getY() + BUTTON + 6 : top + 80;
        g.drawString(font, Component.translatable("screen.skyblocks.elevator.look"), left + 8, y, 0xFFB8B0C8, false);
        g.drawString(font, Component.translatable("screen.skyblocks.elevator.speed"), left + 8, y + 34, 0xFFB8B0C8, false);
    }
}
