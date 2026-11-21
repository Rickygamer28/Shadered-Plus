package com.example.skyblocks.client;

import com.example.skyblocks.StationBlockEntity;
import com.example.skyblocks.TrainEntity;
import com.example.skyblocks.TrainPayload;
import com.example.skyblocks.TrainRoute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A train's screen: a map of its whole network (every line it joins, every switch either way, every station on it),
 * its stations to send it to (click one on the map, or in the list - it finds its way, switches and all), its loop
 * order (the numbers on the map: worked out, or set by hand - Edit order, then click the stations in the order to go
 * round them; right-click takes one off), its look, how many cars, going round, and picking it up.
 */
public class TrainScreen extends Screen {
    private static final int W = 300, H = 230;
    private final TrainEntity train;
    /** Its network: the lines' points; its stations (nearest first), and where on its lines each one's trains stop. */
    private final List<List<Vec3>> lines;
    private final List<BlockPos> stations = new ArrayList<>();
    private final Map<BlockPos, Vec3> stops = new HashMap<>();
    private float minX, maxX, minZ, maxZ;
    /** Setting its order by hand: the order so far. */
    private boolean editing;
    private final List<BlockPos> draft = new ArrayList<>();

    private TrainScreen(TrainEntity train) {
        super(Component.translatable("screen.skyblocks.train"));
        this.train = train;
        Vec3 front = train.at(train.head(0.0F));
        lines = TrainRoute.network(train.level(), front);
        for (List<BlockPos> w : train.stations()) {
            for (BlockPos s : w) {
                if (!stations.contains(s)) {
                    stations.add(s);
                }
            }
        }
        for (BlockPos s : TrainRoute.stationsOn(train.level(), front)) {
            if (!stations.contains(s)) {
                stations.add(s);
            }
        }
        for (BlockPos s : stations) {
            Vec3 r = TrainRoute.stopPoint(train.level(), s);
            if (r != null) {
                stops.put(s, r);
            }
        }
        minX = minZ = Float.MAX_VALUE;
        maxX = maxZ = -Float.MAX_VALUE;
        for (List<Vec3> line : lines) {
            for (Vec3 p : line) {
                minX = Math.min(minX, (float) p.x);
                maxX = Math.max(maxX, (float) p.x);
                minZ = Math.min(minZ, (float) p.z);
                maxZ = Math.max(maxZ, (float) p.z);
            }
        }
    }

    public static void open(TrainEntity train) {
        Minecraft.getInstance().setScreen(new TrainScreen(train));
    }

    private String name(BlockPos s) {
        return train.level().getBlockEntity(s) instanceof StationBlockEntity be ? be.name() : s.toShortString();
    }

    private List<BlockPos> order() {
        return editing ? draft : train.order();
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = width / 2 - W / 2, y = height / 2 - H / 2;
        for (int i = 0; i < Math.min(7, stations.size()); i++) {
            BlockPos s = stations.get(i);
            addRenderableWidget(Button.builder(Component.literal(name(s)), b -> go(s)).bounds(x + 160, y + 22 + i * 21, 132, 19).build());
        }
        // its loop order: set by hand, or worked out again
        if (editing) {
            addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.train.order_done"), b -> {
                editing = false;
                send(draft);
                train.setOrder(new ArrayList<>(draft), true);
                init();
            }).bounds(x + 8, y + 151, 70, 16).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.train.order_clear"), b -> draft.clear()).bounds(x + 82, y + 151, 70, 16).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.train.order_edit"), b -> {
                editing = true;
                draft.clear();
                init();
            }).bounds(x + 8, y + 151, 70, 16).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.train.order_reset"), b -> {
                PacketDistributor.sendToServer(TrainPayload.of(train, TrainPayload.ORDER, 1));
            }).bounds(x + 82, y + 151, 70, 16).build());
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

    private void go(BlockPos s) {
        PacketDistributor.sendToServer(new TrainPayload(train.getId(), TrainPayload.GO, 0, s, ""));
        onClose();
    }

    private void send(List<BlockPos> order) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : order) {
            sb.append(p.asLong()).append(',');
        }
        PacketDistributor.sendToServer(new TrainPayload(train.getId(), TrainPayload.ORDER, 0, BlockPos.ZERO, sb.toString()));
    }

    private void cars(int d) {
        int n = Math.max(1, Math.min(4, train.cars() + d));
        PacketDistributor.sendToServer(TrainPayload.of(train, TrainPayload.CARS, n));
        train.setCars(n);
    }

    // ---- the map ----

    private static final int MW = 144, MH = 126;

    /** The map's scale and offsets: [scale, x offset, z offset]. */
    private float[] mapScale() {
        int x = width / 2 - W / 2, y = height / 2 - H / 2;
        int mx0 = x + 8, my0 = y + 22;
        float span = Math.max(Math.max(maxX - minX, maxZ - minZ), 8.0F);
        float sc = (Math.min(MW, MH) - 14) / span;
        return new float[] {sc, mx0 + MW / 2.0F - (minX + maxX) / 2.0F * sc, my0 + MH / 2.0F - (minZ + maxZ) / 2.0F * sc};
    }

    @Nullable
    private BlockPos stationAt(double mx, double my) {
        if (lines.isEmpty()) {
            return null;
        }
        float[] m = mapScale();
        BlockPos best = null;
        double bd = 30.0;
        for (BlockPos s : stations) {
            Vec3 r = stops.get(s);
            if (r != null) {
                double dx = m[1] + (float) r.x * m[0] + 1 - mx, dy = m[2] + (float) r.z * m[0] + 1 - my;
                if (dx * dx + dy * dy < bd) {
                    bd = dx * dx + dy * dy;
                    best = s;
                }
            }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        BlockPos s = stationAt(mx, my);
        if (s != null) {
            if (editing) {
                if (button == 0) {
                    draft.add(s);
                } else if (button == 1) {
                    int i = draft.lastIndexOf(s);
                    if (i >= 0) {
                        draft.remove(i);
                    }
                }
                return true;
            }
            if (button == 0) {
                go(s);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int x = width / 2 - W / 2, y = height / 2 - H / 2;
        g.fill(x, y, x + W, y + H, 0xE0101820);
        g.renderOutline(x, y, W, H, 0xFF3FC8E8);
        super.render(g, mx, my, pt);
        g.drawString(font, title, x + 8, y + 7, 0x8FEFFF, false);
        g.drawString(font, Component.translatable("screen.skyblocks.train.go"), x + 160, y + 7, 0xB0C8D0, false);
        if (train.waiting()) {
            // (waiting for track to clear: where to)
            BlockPos w = train.waitingFor();
            g.drawString(font, Component.translatable("screen.skyblocks.train.waiting", w == null ? "" : name(w)), x + 50, y + 7, 0xFF7050, false);
        }
        int mx0 = x + 8, my0 = y + 22;
        g.fill(mx0, my0, mx0 + MW, my0 + MH, editing ? 0xFF14101A : 0xFF0A1016);
        if (editing) {
            g.renderOutline(mx0, my0, MW, MH, 0xFFFFC040);
        }
        List<BlockPos> order = order();
        if (!lines.isEmpty()) {
            float[] ms = mapScale();
            float sc = ms[0], ox = ms[1], oz = ms[2];
            for (List<Vec3> line : lines) {
                for (int i = 0; i < line.size(); i += 2) {
                    Vec3 r = line.get(i);
                    int px = Math.round(ox + (float) r.x * sc), pz = Math.round(oz + (float) r.z * sc);
                    g.fill(px, pz, px + 2, pz + 2, 0xFF3A6A80);
                }
            }
            // its stations: amber, in its order (their places in it beside them); grey, passed by
            for (BlockPos s : stations) {
                Vec3 r = stops.get(s);
                if (r == null) {
                    continue;
                }
                int px = Math.round(ox + (float) r.x * sc), pz = Math.round(oz + (float) r.z * sc);
                boolean in = order.contains(s) || (order.isEmpty() && !editing && !train.customOrder());
                if (in) {
                    g.fill(px - 3, pz - 3, px + 5, pz + 5, 0xFF000000);
                    g.fill(px - 2, pz - 2, px + 4, pz + 4, 0xFFFFC040);
                } else {
                    g.renderOutline(px - 2, pz - 2, 6, 6, 0xFF808890);
                }
                StringBuilder nums = new StringBuilder();
                for (int i = 0; i < order.size() && nums.length() < 8; i++) {
                    if (order.get(i).equals(s)) {
                        nums.append(nums.length() == 0 ? "" : ",").append(i + 1);
                    }
                }
                if (nums.length() > 0) {
                    g.pose().pushPose();
                    g.pose().translate(px + 5, pz - 8, 0);
                    g.pose().scale(0.75F, 0.75F, 1.0F);
                    g.drawString(font, nums.toString(), 0, 0, 0xFFFFE080, true);
                    g.pose().popPose();
                }
            }
            Vec3 h = train.at(train.head(pt));
            int px = Math.round(ox + (float) h.x * sc), pz = Math.round(oz + (float) h.z * sc);
            int blink = (System.currentTimeMillis() / 300) % 2 == 0 ? 0xFF6FF0FF : 0xFFFFFFFF;
            g.fill(px - 1, pz - 1, px + 3, pz + 3, blink);
        }
        g.drawString(font, Component.translatable(editing ? "screen.skyblocks.train.order_hint" : "screen.skyblocks.train.map_hint"), x + 8, y + 170,
                editing ? 0xFFC060 : 0x7890A0, false);
        BlockPos hover = stationAt(mx, my);
        if (hover != null && Math.abs(mx - (mx0 + MW / 2)) <= MW / 2 && Math.abs(my - (my0 + MH / 2)) <= MH / 2) {
            g.renderTooltip(font, List.of(Component.literal(name(hover)).getVisualOrderText(),
                    Component.translatable(editing ? "screen.skyblocks.train.click_add" : "screen.skyblocks.train.click_go").withStyle(ChatFormatting.GRAY)
                            .getVisualOrderText()), mx, my);
        }
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
