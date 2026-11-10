package com.example.skyblocks.client;

import com.example.skyblocks.TeleportPadPayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A teleport pad's menu: its name, its network (colour) and whether only you may use it; every pad on the network you
 * can go to, nearest first - Go takes you there now, the star makes it where crouching on this pad sends you.
 */
public class TeleportPadScreen extends Screen {
    private static final int ROWS = 6, ROW_H = 22, W = 270;
    private TeleportPadPayloads.Menu menu;
    private EditBox nameBox;
    private int scroll, swatchX, swatchY, listY, idle = -1;
    private String typed;

    public TeleportPadScreen(TeleportPadPayloads.Menu menu) {
        super(Component.translatable("block.skyblocks.teleport_pad"));
        this.menu = menu;
        this.typed = menu.name();
    }

    public BlockPos pos() {
        return menu.pos();
    }

    public void update(TeleportPadPayloads.Menu m) {
        boolean typing = nameBox != null && nameBox.isFocused() && idle >= 0;
        menu = m;
        if (!typing) typed = m.name();
        scroll = Mth.clamp(scroll, 0, Math.max(0, menu.pads().size() + 1 - ROWS));
        rebuildWidgets();
    }

    private void act(int action, String text, long target, int value) {
        PacketDistributor.sendToServer(new TeleportPadPayloads.Action(menu.pos(), action, text, target, value));
    }

    @Override
    protected void init() {
        int x = width / 2 - W / 2, y = height / 2 - 112;
        if (menu.canEdit()) {
            nameBox = new EditBox(font, x, y + 14, W, 18, Component.translatable("screen.skyblocks.teleport_pad.name"));
            nameBox.setMaxLength(32);
            nameBox.setValue(typed);
            nameBox.setHint(Component.translatable("screen.skyblocks.teleport_pad.name_hint"));
            nameBox.setResponder(s -> {
                typed = s;
                idle = 0;
            });
            addRenderableWidget(nameBox);
            swatchX = x;
            swatchY = y + 40;
            addRenderableWidget(Button.builder(Component.translatable(menu.priv() ? "screen.skyblocks.teleport_pad.only_me" : "screen.skyblocks.teleport_pad.everyone"),
                    b -> act(TeleportPadPayloads.PRIVATE, "", 0, menu.priv() ? 0 : 1)).bounds(x + W - 92, y + 36, 92, 18).build());
        } else {
            nameBox = null;
        }
        listY = y + 78;
        // the rows: next in line (only a star), then each pad
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(null, Component.translatable("screen.skyblocks.teleport_pad.next")));
        for (TeleportPadPayloads.Entry e : menu.pads()) {
            rows.add(new Row(e, label(e)));
        }
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int ry = listY + i * ROW_H;
            long t = r.entry == null ? Long.MIN_VALUE : r.entry.pos().asLong();
            boolean star = t == menu.target();
            Button fav = Button.builder(Component.literal(star ? "★" : "☆"), b -> act(TeleportPadPayloads.TARGET, "", t, 0))
                    .bounds(x + W - 20, ry, 20, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.skyblocks.teleport_pad.star")))
                    .build();
            fav.active = menu.canEdit();
            addRenderableWidget(fav);
            if (r.entry != null) {
                addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.teleport_pad.go"), b -> {
                    act(TeleportPadPayloads.GO, "", t, 0);
                    onClose();
                }).bounds(x + W - 64, ry, 42, 20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 50, listY + ROWS * ROW_H + 6, 100, 20).build());
    }

    private record Row(TeleportPadPayloads.Entry entry, Component text) {}

    private Component label(TeleportPadPayloads.Entry e) {
        BlockPos p = e.pos();
        String n = e.name().isEmpty() ? p.getX() + " " + p.getY() + " " + p.getZ() : e.name();
        int d = minecraft != null && minecraft.player != null ? (int) Math.sqrt(minecraft.player.distanceToSqr(p.getCenter())) : 0;
        return Component.literal(n).append(Component.literal("  " + d + "m").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public void tick() {
        super.tick();
        if (idle >= 0 && ++idle >= 12) { // (renamed: sent once you stop typing)
            idle = -1;
            act(TeleportPadPayloads.RENAME, typed, 0, 0);
        }
    }

    @Override
    public void removed() {
        if (idle >= 0) act(TeleportPadPayloads.RENAME, typed, 0, 0);
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, menu.pads().size() + 1 - ROWS);
        int s = Mth.clamp(scroll - (int) Math.signum(sy), 0, max);
        if (s != scroll) {
            scroll = s;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menu.canEdit() && my >= swatchY && my < swatchY + 10 && mx >= swatchX && mx < swatchX + 17 * 10) {
            act(TeleportPadPayloads.COLOUR, "", 0, Mth.clamp((int) ((mx - swatchX) / 10), 0, 16));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = width / 2 - W / 2, y = height / 2 - 112;
        g.drawCenteredString(font, title, width / 2, y, 0xFFFFFFFF);
        if (!menu.canEdit()) {
            g.drawCenteredString(font, menu.name().isEmpty() ? Component.translatable("screen.skyblocks.teleport_pad.unnamed") : Component.literal(menu.name()),
                    width / 2, y + 18, 0xFFE0E0E0);
        } else {
            for (int i = 0; i < 17; i++) {
                int sx = swatchX + i * 10;
                if (i == menu.colour()) g.fill(sx - 1, swatchY - 1, sx + 9, swatchY + 9, 0xFFFFFFFF);
                if (i < 16) {
                    g.fill(sx, swatchY, sx + 8, swatchY + 8, 0xFF000000 | DyeColor.byId(i).getTextureDiffuseColor());
                } else {
                    for (int k = 0; k < 8; k++) g.fill(sx + k, swatchY, sx + k + 1, swatchY + 8, 0xFF000000 | Mth.hsvToRgb(k / 8.0F, 0.7F, 1.0F));
                }
            }
            g.drawString(font, Component.translatable("screen.skyblocks.teleport_pad.network"), swatchX, swatchY + 12, 0xFFA0A0A0);
        }
        g.drawString(font, Component.translatable("screen.skyblocks.teleport_pad.go_to"), x, listY - 12, 0xFFFFFFFF);
        List<Component> labels = new ArrayList<>();
        labels.add(Component.translatable("screen.skyblocks.teleport_pad.next"));
        for (TeleportPadPayloads.Entry e : menu.pads()) labels.add(label(e));
        for (int i = 0; i < ROWS && scroll + i < labels.size(); i++) {
            int ry = listY + i * ROW_H;
            g.fill(x, ry, x + W - 66, ry + 20, 0x60000000);
            g.drawString(font, labels.get(scroll + i), x + 5, ry + 6, scroll + i == 0 ? 0xFFB0E0FF : 0xFFFFFFFF);
        }
        if (menu.pads().isEmpty()) {
            g.drawString(font, Component.translatable("screen.skyblocks.teleport_pad.alone"), x + 5, listY + ROW_H + 6, 0xFFA0A0A0);
        }
        if (menu.pads().size() + 1 > ROWS) {
            g.drawString(font, (scroll + 1) + "-" + Math.min(scroll + ROWS, menu.pads().size() + 1) + " / " + (menu.pads().size() + 1),
                    x + W - 60, listY - 12, 0xFFA0A0A0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
