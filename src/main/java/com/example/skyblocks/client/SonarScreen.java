package com.example.skyblocks.client;

import com.example.skyblocks.SonarItem;
import com.example.skyblocks.SonarPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A sonar's list of what it lights up: type a block or mob - a name, a #tag, or for mobs a kind like "monster" - and
 * add it as a block or a mob; click a row's colour to change it, x to take it off. Kept when closed.
 */
public class SonarScreen extends Screen {
    private final InteractionHand hand;
    private final List<SonarItem.Entry> entries;
    private EditBox input;
    private String typed = "";
    private Component problem = Component.empty();

    private SonarScreen(InteractionHand hand, List<SonarItem.Entry> entries) {
        super(Component.translatable("screen.skyblocks.sonar"));
        this.hand = hand;
        this.entries = new ArrayList<>(entries);
    }

    public static void open(InteractionHand hand, List<SonarItem.Entry> entries) {
        Minecraft.getInstance().setScreen(new SonarScreen(hand, entries));
    }

    private int left() {
        return width / 2 - 130;
    }

    private int top() {
        return Math.max(8, height / 2 - 130);
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        input = addRenderableWidget(new EditBox(font, x, y + 18, 260, 18, Component.translatable("screen.skyblocks.sonar.input")));
        input.setMaxLength(96);
        input.setValue(typed);
        input.setHint(Component.translatable("screen.skyblocks.sonar.hint"));
        input.setResponder(s -> {
            typed = s;
            problem = Component.empty();
        });
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.sonar.add_block"), b -> add(false)).bounds(x, y + 40, 128, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.sonar.add_mob"), b -> add(true)).bounds(x + 132, y + 40, 128, 18).build());
        for (int i = 0; i < entries.size(); i++) {
            int row = y + 76 + i * 13, at = i;
            addRenderableWidget(Button.builder(Component.empty(), b -> recolour(at)).bounds(x, row, 12, 12).build());
            addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                entries.remove(at);
                rebuildWidgets();
            }).bounds(x + 248, row, 12, 12).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 50, y + 80 + SonarItem.MOST * 13, 100, 18).build());
    }

    private void add(boolean mob) {
        String id = typed.trim().toLowerCase(java.util.Locale.ROOT);
        if (id.isEmpty()) {
            problem = Component.translatable("screen.skyblocks.sonar.empty");
            return;
        }
        if (entries.size() >= SonarItem.MOST) {
            problem = Component.translatable("screen.skyblocks.sonar.full");
            return;
        }
        if (!SonarPulse.valid(id, mob)) {
            problem = Component.translatable(mob ? "screen.skyblocks.sonar.no_mob" : "screen.skyblocks.sonar.no_block", id);
            return;
        }
        entries.add(new SonarItem.Entry(id, mob, SonarItem.PALETTE[entries.size() % SonarItem.PALETTE.length]));
        typed = "";
        rebuildWidgets();
    }

    private void recolour(int i) {
        SonarItem.Entry e = entries.get(i);
        int next = 0;
        for (int k = 0; k < SonarItem.PALETTE.length; k++) {
            if (SonarItem.PALETTE[k] == e.colour()) {
                next = (k + 1) % SonarItem.PALETTE.length;
            }
        }
        entries.set(i, new SonarItem.Entry(e.id(), e.mob(), SonarItem.PALETTE[next]));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == 257 || key == 335) && input.isFocused()) {
            add(net.minecraft.client.gui.screens.Screen.hasShiftDown());
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        g.drawCenteredString(font, title, width / 2, y + 4, 0x7FF4FF);
        g.drawString(font, problem, x, y + 63, 0xFF6A6A);
        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("screen.skyblocks.sonar.nothing"), x + 16, y + 78, 0x9A9A9A);
        }
        for (int i = 0; i < entries.size(); i++) {
            SonarItem.Entry e = entries.get(i);
            int row = y + 76 + i * 13;
            g.fill(x + 2, row + 2, x + 10, row + 10, 0xFF000000 | e.colour());
            boolean ok = SonarPulse.valid(e.id(), e.mob());
            g.drawString(font, Component.literal(e.id()), x + 16, row + 2, ok ? 0xFFFFFF : 0xFF6A6A);
            g.drawString(font, Component.translatable(e.mob() ? "screen.skyblocks.sonar.mob" : "screen.skyblocks.sonar.block"), x + 196, row + 2, 0x8A8A8A);
        }
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new SonarPayload(hand == InteractionHand.MAIN_HAND, SonarItem.write(entries)));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
