package com.example.skyblocks.client;

import com.example.skyblocks.StationBlockEntity;
import com.example.skyblocks.TrainEntity;
import com.example.skyblocks.TrainPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A train's screen: a map of its line (its stations on it, it on it), its stations to send it to, its look, how many
 * cars, going round from station to station, and picking it up.
 */
public class TrainScreen extends Screen {
    private static final int W = 300, H = 230;
    private final TrainEntity train;
    /** Its line: the rails along it (both ways from its front), and its stations on it, by name. */
    private final List<Vec3> rails = new ArrayList<>();
    private final List<BlockPos> stations = new ArrayList<>();
    private int minX, maxX, minZ, maxZ;
    /** Where each station's trains stop (on the map). */
    private final java.util.Map<BlockPos, Vec3> stops = new java.util.HashMap<>();

    private TrainScreen(TrainEntity train) {
        super(Component.translatable("screen.skyblocks.train"));
        this.train = train;
        Vec3 front = train.at(train.head(0.0F)), d = train.unit(0, 0.0F)[1];
        rails.add(front);
        // (every way on, through switches either way)
        for (Vec3 way : new Vec3[] {d, d.scale(-1)}) {
            List<com.example.skyblocks.TrainRoute.Branch> bs = com.example.skyblocks.TrainRoute.branches(train.level(), front, way, 1500);
            if (bs.isEmpty()) {
                rails.addAll(com.example.skyblocks.TrainRoute.onward(train.level(), front, way, 1500).pts());
            }
            for (com.example.skyblocks.TrainRoute.Branch b : bs) {
                rails.addAll(b.pts());
            }
        }
        for (List<BlockPos> w : train.stations()) {
            for (BlockPos s : w) {
                if (!stations.contains(s)) {
                    stations.add(s);
                }
            }
        }
        for (BlockPos s : stations) {
            Vec3 r = com.example.skyblocks.TrainRoute.stopPoint(train.level(), s);
            if (r != null) {
                stops.put(s, r);
            }
        }
        minX = minZ = Integer.MAX_VALUE;
        maxX = maxZ = Integer.MIN_VALUE;
        for (Vec3 r : rails) {
            minX = Math.min(minX, (int) Math.floor(r.x));
            maxX = Math.max(maxX, (int) Math.floor(r.x));
            minZ = Math.min(minZ, (int) Math.floor(r.z));
            maxZ = Math.max(maxZ, (int) Math.floor(r.z));
        }
    }

    public static void open(TrainEntity train) {
        Minecraft.getInstance().setScreen(new TrainScreen(train));
    }

    private String name(BlockPos s) {
        return train.level().getBlockEntity(s) instanceof StationBlockEntity be ? be.name() : s.toShortString();
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = width / 2 - W / 2, y = height / 2 - H / 2;
        // its stations (up to 8)
        for (int i = 0; i < Math.min(8, stations.size()); i++) {
            BlockPos s = stations.get(i);
            addRenderableWidget(Button.builder(Component.literal(name(s)), b -> {
                PacketDistributor.sendToServer(new TrainPayload(train.getId(), TrainPayload.GO, 0, s, ""));
                onClose();
            }).bounds(x + 160, y + 22 + i * 21, 110, 19).build());
            // (stopped at going round, or passed by)
            Button stop = addRenderableWidget(Button.builder(Component.literal(train.skips(s) ? "-" : "\u2714"), b -> toggle(s))
                    .bounds(x + 272, y + 22 + i * 21, 20, 19).build());
            stop.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(train.skips(s) ? "screen.skyblocks.train.skipped" : "screen.skyblocks.train.stops")));
        }
        int by = y + H - 50;
        for (int i = 0; i < TrainEntity.STYLES.length; i++) {
            int k = i;
            Button b = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.train.style." + TrainEntity.STYLES[i]), btn -> {
                PacketDistributor.sendToServer(TrainPayload.of(train, TrainPayload.STYLE, k));
                train.setStyle(k);
                init();
            }).bounds(x + 8 + i * 72, by, 70, 18).build());
            b.active = train.style() != i;
        }
        addRenderableWidget(Button.builder(Component.literal("-"), b -> cars(-1)).bounds(x + 160, by, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> cars(1)).bounds(x + 272, by, 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(train.loop() ? "screen.skyblocks.train.loop_on" : "screen.skyblocks.train.loop_off"), b -> {
            PacketDistributor.sendToServer(TrainPayload.of(train, TrainPayload.LOOP, train.loop() ? 0 : 1));
            train.setLoop(!train.loop());
            init();
        }).bounds(x + 8, by + 24, 142, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.train.pick_up"), b -> {
            PacketDistributor.sendToServer(TrainPayload.of(train, TrainPayload.PICK_UP, 0));
            onClose();
        }).bounds(x + 160, by + 24, 132, 18).build());
    }

    /** A station stopped at going round, or passed by. */
    private void toggle(BlockPos s) {
        boolean skip = !train.skips(s);
        PacketDistributor.sendToServer(new TrainPayload(train.getId(), TrainPayload.SKIP, skip ? 1 : 0, s, ""));
        train.setSkip(s, skip);
        init();
    }

    /** The map's scale and where its middle is: [scale, x offset, z offset]. */
    private float[] mapScale() {
        int x = width / 2 - W / 2, y = height / 2 - H / 2;
        int mx0 = x + 8, my0 = y + 22, mw = 144, mh = 126;
        float span = Math.max(Math.max(maxX - minX, maxZ - minZ), 8);
        float sc = (Math.min(mw, mh) - 12) / span;
        return new float[] {sc, mx0 + mw / 2.0F - (minX + maxX) / 2.0F * sc, my0 + mh / 2.0F - (minZ + maxZ) / 2.0F * sc};
    }

    /** The station whose dot on the map is under the mouse, or null. */
    @org.jetbrains.annotations.Nullable
    private BlockPos stationAt(double mx, double my) {
        if (rails.isEmpty()) {
            return null;
        }
        float[] m = mapScale();
        for (BlockPos s : stations) {
            Vec3 r = stops.get(s);
            if (r != null && Math.abs(m[1] + (float) r.x * m[0] + 1 - mx) <= 4 && Math.abs(m[2] + (float) r.z * m[0] + 1 - my) <= 4) {
                return s;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        BlockPos s = stationAt(mx, my);
        if (s != null && button == 0) {
            toggle(s);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void cars(int d) {
        int n = Math.max(1, Math.min(4, train.cars() + d));
        PacketDistributor.sendToServer(TrainPayload.of(train, TrainPayload.CARS, n));
        train.setCars(n);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int x = width / 2 - W / 2, y = height / 2 - H / 2;
        g.fill(x, y, x + W, y + H, 0xE0101820);
        g.renderOutline(x, y, W, H, 0xFF3FC8E8);
        super.render(g, mx, my, pt);
        g.drawString(font, title, x + 8, y + 7, 0x8FEFFF, false);
        g.drawString(font, Component.translatable("screen.skyblocks.train.go"), x + 160, y + 7, 0xB0C8D0, false);
        // the line's map
        int mx0 = x + 8, my0 = y + 22, mw = 144, mh = 126;
        g.fill(mx0, my0, mx0 + mw, my0 + mh, 0xFF0A1016);
        if (!rails.isEmpty()) {
            float[] ms = mapScale();
            float sc = ms[0], ox = ms[1], oz = ms[2];
            for (Vec3 r : rails) {
                int px = Math.round(ox + (float) r.x * sc), pz = Math.round(oz + (float) r.z * sc);
                g.fill(px, pz, px + 2, pz + 2, 0xFF3A6A80);
            }
            // its stations: filled, stopped at going round; hollow, passed by
            for (BlockPos s : stations) {
                Vec3 r = stops.get(s);
                if (r != null) {
                    int px = Math.round(ox + (float) r.x * sc), pz = Math.round(oz + (float) r.z * sc);
                    if (train.skips(s)) {
                        g.renderOutline(px - 2, pz - 2, 6, 6, 0xFF808890);
                    } else {
                        g.fill(px - 3, pz - 3, px + 5, pz + 5, 0xFF000000);
                        g.fill(px - 2, pz - 2, px + 4, pz + 4, 0xFFFFC040);
                    }
                }
            }
            Vec3 h = train.at(train.head(pt));
            int px = Math.round(ox + (float) h.x * sc), pz = Math.round(oz + (float) h.z * sc);
            int blink = (System.currentTimeMillis() / 300) % 2 == 0 ? 0xFF6FF0FF : 0xFFFFFFFF;
            g.fill(px - 2, pz - 2, px + 4, pz + 4, blink);
        }
        BlockPos hover = stationAt(mx, my);
        if (hover != null) {
            g.renderTooltip(font, java.util.List.of(Component.literal(name(hover)).getVisualOrderText(),
                    Component.translatable(train.skips(hover) ? "screen.skyblocks.train.skipped" : "screen.skyblocks.train.stops").withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText()), mx, my);
        }
        g.drawString(font, Component.translatable("screen.skyblocks.train.map_hint"), x + 8, y + 150, 0x7890A0, false);
        if (stations.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("screen.skyblocks.train.no_stations"), x + 160, y + 24, 132, 0x90A0A8);
        }
        int by = y + H - 50;
        g.drawCenteredString(font, Component.translatable("screen.skyblocks.train.cars", train.cars()), x + 226, by + 5, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
