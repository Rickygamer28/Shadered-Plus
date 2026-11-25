package com.example.skyblocks.client;

import com.example.skyblocks.PowerCoreBlockEntity;
import com.example.skyblocks.PowerCorePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The power core's screen: how much it holds (a bar in its look's colour), energy in and out each tick and how long
 * until it's full or empty at that rate, its chamber, its look (four buttons) - and its energy links: the blocks up to
 * 5 away that hold energy, each set to off, take (it draws energy from it) or give (it sends energy to it), outlined
 * in the world while that page is open.
 */
public class PowerCoreScreen extends Screen {
    private static final int W = 240, H = 170, ROW = 20, ROWS = 5, LIST_Y = 36;
    /** Each look's colour (the bar, the frame round the screen). */
    private static final int[] ACCENT = {0xFFFF9A3C, 0xFF9A9CFF, 0xFFC070FF, 0xFF60C8FF};
    /** Off, take, give: their colours. */
    public static final int[] MODE_COLOUR = {0xFF5A6578, 0xFF46D07A, 0xFFFF9A3C};
    private static final String[] MODE = {"off", "take", "give"};
    private final BlockPos pos;
    private int left, top;
    private final Button[] skinButtons = new Button[PowerCoreBlockEntity.SKINS.length];
    private MachineUi.Flat linksButton;
    private Button backButton;
    private boolean linksView;
    private int scroll;
    /** (For the core's renderer: the links page open at this core; the link the mouse is on.) */
    private static BlockPos openAt, hovered;

    public PowerCoreScreen(BlockPos pos) {
        super(Component.translatable("block.skyblocks.power_core"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new PowerCoreScreen(pos));
    }

    /** Whether this core's links page is open (its links outlined in the world), and the one the mouse is on. */
    public static boolean linksOpen(BlockPos core) {
        return core.equals(openAt) && Minecraft.getInstance().screen instanceof PowerCoreScreen;
    }

    public static BlockPos hoveredLink() {
        return hovered;
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
        linksButton = (MachineUi.Flat) addRenderableWidget(MachineUi.button(left + 8, top + 124, W - 16, 14, Component.empty(), b -> view(true), this::accent));
        backButton = addRenderableWidget(MachineUi.button(left + 8, top + H - 20, 60, 14, Component.translatable("screen.skyblocks.power_core.back"),
                b -> view(false), this::accent));
        view(linksView);
    }

    /** (The render test: its links page.) */
    public void showLinks() {
        view(true);
    }

    private void view(boolean links) {
        linksView = links;
        openAt = links ? pos : null;
        hovered = null;
        for (Button b : skinButtons) {
            b.visible = !links;
        }
        linksButton.visible = !links;
        backButton.visible = links;
    }

    @Override
    public void removed() {
        openAt = null;
        hovered = null;
        super.removed();
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
        int take = 0, give = 0;
        for (PowerCoreBlockEntity.Link l : be.links()) {
            take += l.mode() == PowerCoreBlockEntity.TAKE ? 1 : 0;
            give += l.mode() == PowerCoreBlockEntity.GIVE ? 1 : 0;
        }
        linksButton.lit = take + give > 0;
        linksButton.setMessage(take + give > 0 ? Component.translatable("screen.skyblocks.power_core.links_summary", take, give)
                : Component.translatable("screen.skyblocks.power_core.links_none"));
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
        if (linksView) {
            MachineUi.header(g, font, Component.translatable("screen.skyblocks.power_core.links_title"), chamber, left, top, W, accent);
            renderLinks(g, be, mouseX, mouseY);
            return;
        }
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
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.stored", PowerCoreBlockEntity.fe(be.energy()),
                PowerCoreBlockEntity.fe(be.capacity())), left + 8, top + 52, MachineUi.TEXT, false);
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
        g.drawString(font, MachineUi.fit(font, Component.translatable("screen.skyblocks.power_core.hint").getString(), W - 16), left + 8, top + H - 14,
                MachineUi.FAINT, false);
    }

    // ================= the links page =================

    /** Where a mode's button of a row is: x of its left. */
    private int modeX(int m) {
        return left + W - 8 - (3 - m) * 30 + 2;
    }

    private void renderLinks(GuiGraphics g, PowerCoreBlockEntity be, int mouseX, int mouseY) {
        List<PowerCoreBlockEntity.Link> links = be.links();
        g.drawString(font, Component.translatable("screen.skyblocks.power_core.links_help", PowerCoreBlockEntity.REACH), left + 8, top + 24, MachineUi.DIM, false);
        scroll = Math.max(0, Math.min(scroll, links.size() - ROWS));
        hovered = null;
        Component tip = null;
        if (links.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.skyblocks.power_core.links_empty"), left + W / 2, top + LIST_Y + 40, MachineUi.FAINT);
        }
        for (int i = 0; i < ROWS && scroll + i < links.size(); i++) {
            PowerCoreBlockEntity.Link l = links.get(scroll + i);
            int y = top + LIST_Y + i * ROW;
            boolean over = mouseX >= left + 8 && mouseX < left + W - 8 && mouseY >= y && mouseY < y + ROW - 2;
            if (over) {
                hovered = l.pos();
            }
            g.fill(left + 8, y, left + W - 8, y + ROW - 2, over ? 0xFF222C3E : 0xFF182030);
            g.fill(left + 8, y, left + 10, y + ROW - 2, MODE_COLOUR[l.mode()]);
            ItemStack icon = new ItemStack(minecraft.level.getBlockState(l.pos()).getBlock().asItem());
            g.renderItem(icon, left + 13, y + 1);
            Component name = minecraft.level.getBlockState(l.pos()).getBlock().getName();
            g.drawString(font, MachineUi.fit(font, name.getString(), modeX(0) - left - 36), left + 33, y + 1, MachineUi.TEXT, false);
            g.drawString(font, MachineUi.fit(font, where(l.pos()), modeX(0) - left - 36), left + 33, y + 10, MachineUi.FAINT, false);
            for (int m = 0; m < 3; m++) {
                boolean can = m == 0 || (m == 1 ? l.canGive() : l.canTake());
                boolean on = l.mode() == m;
                int bx = modeX(m), by = y + 3;
                boolean bover = can && mouseX >= bx && mouseX < bx + 28 && mouseY >= by && mouseY < by + 12;
                g.fill(bx, by, bx + 28, by + 12, on ? MachineUi.mix(MachineUi.HEADER, MODE_COLOUR[m], 0.45F) : bover ? 0xFF2A3447 : 0xFF202838);
                if (on) {
                    g.fill(bx, by + 11, bx + 28, by + 12, MODE_COLOUR[m]);
                }
                String t = Component.translatable("screen.skyblocks.power_core.mode." + MODE[m]).getString();
                g.drawString(font, t, bx + (28 - font.width(t)) / 2, by + 2, !can ? 0xFF3A4458 : on || bover ? MachineUi.TEXT : MachineUi.SOFT, false);
            }
            if (over && mouseX < modeX(0) - 2) {
                tip = Component.translatable("screen.skyblocks.power_core.link_holds", PowerCoreBlockEntity.fe(l.stored()), PowerCoreBlockEntity.fe(l.cap()));
            }
        }
        if (links.size() > ROWS) {
            String page = (scroll + 1) + "-" + Math.min(links.size(), scroll + ROWS) + " / " + links.size();
            g.drawString(font, page, left + W - 8 - font.width(page), top + H - 16, MachineUi.FAINT, false);
        }
        if (tip != null) {
            g.renderTooltip(font, tip, mouseX, mouseY);
        }
    }

    /** Where a block is from the core: "3 east, 1 up, 2 north". */
    private String where(BlockPos p) {
        int dx = p.getX() - pos.getX(), dy = p.getY() - pos.getY(), dz = p.getZ() - pos.getZ();
        List<String> parts = new ArrayList<>();
        if (dx != 0) {
            parts.add(Math.abs(dx) + " " + Component.translatable("screen.skyblocks.power_core.dir." + (dx > 0 ? "east" : "west")).getString());
        }
        if (dy != 0) {
            parts.add(Math.abs(dy) + " " + Component.translatable("screen.skyblocks.power_core.dir." + (dy > 0 ? "up" : "down")).getString());
        }
        if (dz != 0) {
            parts.add(Math.abs(dz) + " " + Component.translatable("screen.skyblocks.power_core.dir." + (dz > 0 ? "south" : "north")).getString());
        }
        return String.join(", ", parts);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        PowerCoreBlockEntity be = be();
        if (linksView && be != null) {
            List<PowerCoreBlockEntity.Link> links = be.links();
            for (int i = 0; i < ROWS && scroll + i < links.size(); i++) {
                PowerCoreBlockEntity.Link l = links.get(scroll + i);
                int by = top + LIST_Y + i * ROW + 3;
                for (int m = 0; m < 3; m++) {
                    boolean can = m == 0 || (m == 1 ? l.canGive() : l.canTake());
                    int bx = modeX(m);
                    if (can && mx >= bx && mx < bx + 28 && my >= by && my < by + 12 && l.mode() != m) {
                        int dx = l.pos().getX() - pos.getX() + 32, dy = l.pos().getY() - pos.getY() + 32, dz = l.pos().getZ() - pos.getZ() + 32;
                        PacketDistributor.sendToServer(new PowerCorePayload(pos, PowerCorePayload.LINK, m | dx << 2 | dy << 8 | dz << 14));
                        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (linksView) {
            scroll -= (int) Math.signum(dy);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
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
