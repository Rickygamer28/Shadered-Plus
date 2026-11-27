package com.example.skyblocks.client;

import com.example.skyblocks.TelevisionBlockEntity;
import com.example.skyblocks.TelevisionPayload;
import com.example.skyblocks.client.tv.TvPlayers;
import com.example.skyblocks.client.tv.VideoPlayer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A television's window: a link to play (YouTube or any video), or a video file dropped onto it from the computer;
 * play/pause, stop, back and on, going anywhere along it, repeat and volume. Doesn't pause the game (it plays on).
 */
public class TelevisionScreen extends Screen {
    private static final int W = 330, ACCENT = 0xFF4FB3FF;
    private int H = 178;
    /** What it controls: a television wall, or the tablet in a hand. */
    public interface Target {
        @org.jetbrains.annotations.Nullable
        com.example.skyblocks.TvState state();

        @org.jetbrains.annotations.Nullable
        VideoPlayer player();

        void send(int action, String text, long value);

        /** Gone, or too far: the window closes. */
        boolean gone();

        /** The television it is (its channels and queue), or null: a tablet. */
        @org.jetbrains.annotations.Nullable
        default TelevisionBlockEntity television() {
            return null;
        }
    }

    private final Target target;
    private EditBox link;
    private MachineUi.Flat pauseButton, loopButton, prevButton, nextButton, shuffleButton;
    private VolumeSlider volume;
    private long dropFlash;
    private String dropError = "";

    public TelevisionScreen(BlockPos pos) {
        this(Component.translatable("block.skyblocks.television"), new Target() {
            private TelevisionBlockEntity be() {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                return mc.level != null && mc.level.getBlockEntity(pos) instanceof TelevisionBlockEntity t ? t : null;
            }

            public com.example.skyblocks.TvState state() {
                return be();
            }

            public VideoPlayer player() {
                return TvPlayers.existing(pos);
            }

            public void send(int action, String text, long value) {
                PacketDistributor.sendToServer(new TelevisionPayload(pos, action, text, value));
            }

            public boolean gone() {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                boolean remote = mc.player != null && (mc.player.getMainHandItem().is(com.example.skyblocks.ModItems.REMOTE.get())
                        || mc.player.getOffhandItem().is(com.example.skyblocks.ModItems.REMOTE.get()));
                return be() == null || mc.player == null || mc.player.distanceToSqr(pos.getCenter()) > (remote ? 48 * 48 : 24 * 24);
            }

            public TelevisionBlockEntity television() {
                return be();
            }
        });
    }

    /** The tablet in this hand. */
    public static TelevisionScreen tablet(net.minecraft.world.InteractionHand hand) {
        int h = hand == net.minecraft.world.InteractionHand.OFF_HAND ? 1 : 0;
        return new TelevisionScreen(Component.translatable("item.skyblocks.tablet"), new Target() {
            private com.example.skyblocks.TabletState data() {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player == null) return null;
                net.minecraft.world.item.ItemStack st = mc.player.getItemInHand(hand);
                return st.is(com.example.skyblocks.ModItems.TABLET.get())
                        ? st.getOrDefault(com.example.skyblocks.ModComponents.TABLET.get(), com.example.skyblocks.TabletState.EMPTY) : null;
            }

            public com.example.skyblocks.TvState state() {
                com.example.skyblocks.TabletState d = data();
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                return d == null || mc.level == null ? null : d.view(mc.level);
            }

            public VideoPlayer player() {
                com.example.skyblocks.TabletState d = data();
                return d == null ? null : TvPlayers.tablet(d.id());
            }

            public void send(int action, String text, long value) {
                PacketDistributor.sendToServer(new com.example.skyblocks.TabletPayload(h, action, text, value));
            }

            public boolean gone() {
                return data() == null;
            }
        });
    }

    private TelevisionScreen(Component title, Target target) {
        super(title);
        this.target = target;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private com.example.skyblocks.TvState tv() {
        return target.state();
    }

    private void send(int action, String text, long value) {
        target.send(action, text, value);
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        com.example.skyblocks.TvState tv = tv();
        boolean isTv = target.television() != null;
        boolean projector = isTv && target.television().getBlockState().getBlock() instanceof com.example.skyblocks.ProjectorBlock;
        H = projector ? 278 : isTv ? 256 : 220;
        y = top();
        link = new EditBox(font, x + 10, y + 34, W - (isTv ? 132 : 78), 18, Component.literal("link"));
        link.setMaxLength(TelevisionBlockEntity.MAX_SOURCE);
        link.setHint(Component.literal("Paste a YouTube video or playlist link, or a video link").withColor(0xFF5A6578));
        if (tv != null && !tv.source().startsWith("file")) {
            link.setValue(tv.source());
        }
        addRenderableWidget(link);
        setInitialFocus(link);
        if (isTv) {
            addRenderableWidget(MachineUi.button(x + W - 118, y + 34, 50, 18, Component.literal("Play"), b -> choose(link.getValue()), () -> ACCENT));
            addRenderableWidget(MachineUi.button(x + W - 64, y + 34, 54, 18, Component.literal("+ Queue"), b -> {
                String l = link.getValue().trim();
                if (!l.isEmpty()) {
                    send(TelevisionPayload.QUEUE_ADD, l, 0);
                    link.setValue("");
                }
            }, () -> ACCENT));
        } else {
            addRenderableWidget(MachineUi.button(x + W - 64, y + 34, 54, 18, Component.literal("Play"), b -> choose(link.getValue()), () -> ACCENT));
        }

        int by = y + 128;
        prevButton = (MachineUi.Flat) addRenderableWidget(MachineUi.button(x + 10, by, 22, 18, Component.literal("|<"), b -> step(-1), () -> ACCENT));
        pauseButton = (MachineUi.Flat) addRenderableWidget(MachineUi.button(x + 34, by, 48, 18, Component.literal("Pause"), b -> {
            com.example.skyblocks.TvState t = tv();
            if (t != null) send(t.paused() ? TelevisionPayload.PLAY : TelevisionPayload.PAUSE, "", 0);
        }, () -> ACCENT));
        nextButton = (MachineUi.Flat) addRenderableWidget(MachineUi.button(x + 84, by, 22, 18, Component.literal(">|"), b -> {
            com.example.skyblocks.TvState t = tv();
            VideoPlayer p = target.player();
            if (p != null && p.listSize() > 1) {
                step(1);
            } else if (t != null && t.queueSize() > 0) {
                send(TelevisionPayload.QUEUE_NEXT, t.source(), 0); // (on to what's queued)
            }
        }, () -> ACCENT));
        addRenderableWidget(MachineUi.button(x + 108, by, 34, 18, Component.literal("-10s"), b -> skip(-10_000), () -> ACCENT));
        addRenderableWidget(MachineUi.button(x + 144, by, 34, 18, Component.literal("+10s"), b -> skip(10_000), () -> ACCENT));
        shuffleButton = (MachineUi.Flat) addRenderableWidget(MachineUi.button(x + 180, by, 50, 18, Component.literal("Shuffle"), b -> {
            com.example.skyblocks.TvState t = tv();
            VideoPlayer p = target.player();
            if (t != null && p != null && p.listSize() > 1) {
                // (on: a new random order from the video playing; off: back in order, from the video playing)
                boolean on = !t.shuffle();
                send(TelevisionPayload.SHUFFLE, Integer.toString(on ? 0 : p.itemInOrder()), ((long) p.listIndex() << 1) | (on ? 1 : 0));
            }
        }, () -> ACCENT));
        loopButton = (MachineUi.Flat) addRenderableWidget(MachineUi.button(x + 232, by, 48, 18, Component.literal("Repeat"), b -> {
            com.example.skyblocks.TvState t = tv();
            if (t != null) send(TelevisionPayload.LOOP, "", t.loop() ? 0 : 1);
        }, () -> ACCENT));
        addRenderableWidget(MachineUi.button(x + 282, by, 38, 18, Component.literal("Off"), b -> {
            send(TelevisionPayload.STOP, "", 0);
            link.setValue("");
        }, () -> 0xFFFF8A7A));
        volume = addRenderableWidget(new VolumeSlider(x + 10, by + 24, W - 20, 16, tv == null ? 70 : tv.volume()));
        int cy = by + 58;
        if (isTv) {
            // its channels: a click plays one, shift-click saves the link above (or what's on) to it
            for (int i = 0; i < TelevisionBlockEntity.CHANNELS; i++) {
                int slot = i;
                channelButtons[i] = (MachineUi.Flat) addRenderableWidget(MachineUi.button(x + 62 + i * 29, cy, 26, 16, Component.literal(Integer.toString(i + 1)), b -> {
                    if (hasShiftDown()) {
                        send(TelevisionPayload.CHANNEL_SAVE, link.getValue().trim(), slot);
                    } else {
                        send(TelevisionPayload.CHANNEL_PLAY, "", slot);
                    }
                }, () -> ACCENT));
            }
            addRenderableWidget(MachineUi.button(x + W - 62, cy + 26, 52, 14, Component.literal("Clear"), b -> send(TelevisionPayload.QUEUE_CLEAR, "", 0),
                    () -> 0xFFFF8A7A));
        } else {
            addRenderableWidget(MachineUi.button(x + 10, cy, W - 20, 18, Component.literal("Cast to the television you're looking at"), b -> cast(), () -> ACCENT));
        }
        if (target.television() != null && target.television().getBlockState().getBlock() instanceof com.example.skyblocks.ProjectorBlock) {
            // (a projector: how big its picture is, in place of the queue list's room)
            addRenderableWidget(new SizeSlider(x + 10, cy + 64, W - 20, 14, target.television().projection()));
        }
    }

    private final MachineUi.Flat[] channelButtons = new MachineUi.Flat[TelevisionBlockEntity.CHANNELS];
    private String castNote = "";

    /** The tablet's video, from where it is, onto the television looked at (or the nearest within 16 blocks). */
    private void cast() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        BlockPos at = null;
        net.minecraft.world.phys.HitResult hit = mc.player.pick(32, 0, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult bh && mc.level.getBlockEntity(bh.getBlockPos()) instanceof TelevisionBlockEntity) {
            at = bh.getBlockPos();
        } else {
            double best = 16 * 16;
            BlockPos me = mc.player.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(me.offset(-16, -8, -16), me.offset(16, 8, 16))) {
                if (mc.level.getBlockEntity(p) instanceof TelevisionBlockEntity && p.distSqr(me) < best) {
                    best = p.distSqr(me);
                    at = p.immutable();
                }
            }
        }
        if (at == null) {
            castNote = "No television in sight";
            return;
        }
        send(TelevisionPayload.CAST, "", at.asLong());
        castNote = "Cast";
    }

    private void choose(String s) {
        s = s.trim();
        if (!s.isEmpty()) {
            send(TelevisionPayload.CHOOSE, s, 0);
        }
    }

    /** Back or on along its playlist. */
    private void step(int by) {
        com.example.skyblocks.TvState t = tv();
        if (t != null) {
            send(TelevisionPayload.GOTO, "", TelevisionPayload.fromTo(t.item(), t.item() + by));
        }
    }

    private void skip(long ms) {
        com.example.skyblocks.TvState t = tv();
        VideoPlayer p = target.player();
        if (t != null && p != null && !p.live()) {
            long d = p.duration();
            long to = Math.max(0, p.position() + ms);
            send(TelevisionPayload.SEEK, "", d > 0 ? Math.min(to, d - 500) : to);
        }
    }

    /** A video file dragged onto the window from the computer: played (from where it is on this computer). */
    @Override
    public void onFilesDrop(List<Path> files) {
        List<String> paths = new java.util.ArrayList<>();
        for (Path f : files) {
            if (Files.isRegularFile(f)) {
                paths.add(f.toAbsolutePath().toString());
            }
        }
        if (paths.isEmpty()) {
            dropError = "That isn't a file";
            return;
        }
        // (several at once: a playlist of them, in name order)
        paths.sort(String.CASE_INSENSITIVE_ORDER);
        String src = paths.size() == 1 ? "file:" + paths.get(0) : "files:" + String.join("\n", paths);
        if (src.length() > TelevisionBlockEntity.MAX_SOURCE) {
            dropError = "Too many files at once";
            return;
        }
        send(TelevisionPayload.CHOOSE, src, 0);
        link.setValue("");
        dropFlash = System.currentTimeMillis();
        dropError = "";
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && link.isFocused()) { // (enter)
            choose(link.getValue());
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // the bar along it: go there
        int x = left() + 10, y = top() + 108, w = W - 20;
        VideoPlayer p = target.player();
        if (button == 0 && p != null && !p.live() && p.duration() > 0 && mx >= x - 1 && mx <= x + w + 1 && my >= y - 3 && my <= y + 8) {
            float f = (float) Math.max(0, Math.min(1, (mx - x) / w));
            send(TelevisionPayload.SEEK, "", (long) (p.duration() * f));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void tick() {
        com.example.skyblocks.TvState tv = tv();
        if (tv == null || target.gone()) {
            onClose();
            return;
        }
        pauseButton.setMessage(Component.literal(tv.paused() ? "Play" : "Pause"));
        pauseButton.active = !tv.source().isEmpty();
        VideoPlayer p = target.player();
        boolean list = p != null && p.listSize() > 1;
        prevButton.active = list;
        nextButton.active = list || tv.queueSize() > 0;
        TelevisionBlockEntity te = target.television();
        if (te != null) {
            for (int i = 0; i < channelButtons.length; i++) {
                channelButtons[i].lit = te.channel() == i;
                String c = te.channel(i);
                channelButtons[i].setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        (c.isEmpty() ? "Empty" : c.length() > 60 ? c.substring(0, 60) + "..." : c) + "\nShift-click: save the link above (or what's on) here")));
            }
        }
        shuffleButton.active = list;
        shuffleButton.lit = tv.shuffle();
        loopButton.lit = tv.loop();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int x = left(), y = top();
        com.example.skyblocks.TvState tv = tv();
        VideoPlayer p = target.player();
        String status;
        int sc;
        if (tv == null || tv.source().isEmpty()) {
            status = "Off";
            sc = MachineUi.DIM;
        } else if (p == null) {
            status = "Starting";
            sc = MachineUi.WARN;
        } else {
            switch (p.state()) {
                case PLAYING -> { status = p.live() ? "Live" : "Playing"; sc = MachineUi.GOOD; }
                case PAUSED -> { status = "Paused"; sc = MachineUi.WARN; }
                case ENDED -> { status = "Ended"; sc = MachineUi.DIM; }
                case ERROR -> { status = "Can't play"; sc = MachineUi.BAD; }
                default -> { status = "Loading"; sc = MachineUi.WARN; }
            }
        }
        boolean flash = System.currentTimeMillis() - dropFlash < 900;
        MachineUi.panel(g, x, y, W, H, flash ? MachineUi.GOOD : ACCENT);
        MachineUi.header(g, font, title, Component.literal(status), x, y, W, sc);
        MachineUi.label(g, font, Component.literal("Link"), x + 10, y + 24);
        // the drop area
        int dy = y + 58;
        int edge = flash ? MachineUi.GOOD : 0xFF34405A;
        for (int i = x + 10; i < x + W - 10; i += 6) {
            g.fill(i, dy, Math.min(i + 3, x + W - 10), dy + 1, edge);
            g.fill(i, dy + 21, Math.min(i + 3, x + W - 10), dy + 22, edge);
        }
        g.fill(x + 10, dy, x + 11, dy + 22, edge);
        g.fill(x + W - 11, dy, x + W - 10, dy + 22, edge);
        String hint = !dropError.isEmpty() ? dropError : "...or drag video files onto this window";
        g.drawString(font, MachineUi.fit(font, hint, W - 30), x + 16, dy + 7, dropError.isEmpty() ? MachineUi.FAINT : MachineUi.BAD, false);
        // what's on
        MachineUi.divider(g, x + 10, y + 86, W - 20);
        String now;
        if (tv == null || tv.source().isEmpty()) {
            now = "Nothing on";
        } else if (p != null && p.state() == VideoPlayer.State.ERROR) {
            now = p.error();
        } else if (p != null && !p.title().isEmpty()) {
            now = p.title();
        } else {
            String s = tv.source();
            now = s.startsWith("file:") ? Path.of(s.substring(5)).getFileName().toString() : s.startsWith("files:") ? "Files" : s;
        }
        if (p != null && p.listSize() > 0) {
            now = (p.listIndex() + 1) + "/" + p.listSize() + "  " + now;
        }
        g.drawString(font, MachineUi.fit(font, now, W - 110), x + 10, y + 93, p != null && p.state() == VideoPlayer.State.ERROR ? MachineUi.BAD : MachineUi.TEXT, false);
        String time = "";
        float f = 0;
        if (p != null && tv != null && !tv.source().isEmpty()) {
            if (p.live()) {
                time = "LIVE";
            } else if (p.duration() > 0) {
                time = clock(p.position()) + " / " + clock(p.duration());
                f = p.position() / (float) p.duration();
            }
        }
        g.drawString(font, time, x + W - 10 - font.width(time), y + 93, MachineUi.SOFT, false);
        MachineUi.bar(g, x + 10, y + 108, W - 20, 5, f, ACCENT);
        int cy = y + 186;
        TelevisionBlockEntity te = target.television();
        if (te != null) {
            MachineUi.label(g, font, Component.literal("Channels"), x + 10, cy + 4);
            MachineUi.label(g, font, Component.literal("Up next" + (te.queue().isEmpty() ? "" : " (" + te.queue().size() + ")")), x + 10, cy + 28);
            if (te.queue().isEmpty()) {
                g.drawString(font, "Nothing queued: paste a link and press + Queue", x + 10, cy + 40, MachineUi.FAINT, false);
            }
            for (int i = 0; i < Math.min(2, te.queue().size()); i++) {
                TelevisionBlockEntity.Queued q = te.queue().get(i);
                String name = q.source().startsWith("file:") ? Path.of(q.source().substring(5)).getFileName().toString() : q.source();
                g.drawString(font, MachineUi.fit(font, (i + 1) + ". " + name + "  - " + q.by(), W - 80), x + 10, cy + 40 + i * 10, MachineUi.SOFT, false);
            }
        } else if (!castNote.isEmpty()) {
            g.drawString(font, castNote, x + 10, cy + 22, castNote.startsWith("No") ? MachineUi.BAD : MachineUi.GOOD, false);
        }
        if (p != null && !p.soundProblem().isEmpty()) {
            g.drawString(font, MachineUi.fit(font, "No sound: " + p.soundProblem(), W - 20), x + 10, y + 117, MachineUi.BAD, false);
        } else if (p != null && !p.soundStatus().isEmpty()) {
            boolean bad = p.soundStatus().startsWith("Sound stopped");
            g.drawString(font, MachineUi.fit(font, p.soundStatus(), W - 20), x + 10, y + 117, bad ? MachineUi.BAD : MachineUi.FAINT, false);
        } else if (tv != null && tv.source().startsWith("file") && !tv.chosenBy().isEmpty()) {
            g.drawString(font, MachineUi.fit(font, "A file from " + tv.chosenBy() + "'s computer: only plays where that file is", W - 20), x + 10, y + 117,
                    MachineUi.FAINT, false);
        }
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A projector's picture: Auto (by its distance), or 1 to 32 blocks wide. */
    private final class SizeSlider extends AbstractSliderButton {
        SizeSlider(int x, int y, int w, int h, int v) {
            super(x, y, w, h, Component.empty(), v / 32.0);
            updateMessage();
        }

        private int blocks() {
            return (int) Math.round(value * 32);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Picture size: " + (blocks() == 0 ? "Auto (by distance)" : blocks() + " blocks wide")));
        }

        @Override
        protected void applyValue() {
            send(TelevisionPayload.PROJECTION, "", blocks()); // (live, as it's dragged)
        }
    }

    private final class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int x, int y, int w, int h, int v) {
            super(x, y, w, h, Component.empty(), v / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Volume: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
        }

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            send(TelevisionPayload.VOLUME, "", Math.round(value * 100));
        }
    }
}
