package com.example.skyblocks.client.tv;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TelevisionBlockEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * One television's player, on this computer: finds what its source is (a YouTube video, a link, a file), opens a
 * decoder for its picture and one for its sound, plays the sound in the world and shows the picture that goes with
 * it - keeping to where the television is (its block entity's time, the same for everyone; corrected when it drifts).
 */
public final class VideoPlayer {
    public enum State { LOADING, PLAYING, PAUSED, ENDED, ERROR }

    private final BlockPos pos;
    private final ResourceLocation textureId;
    private final VideoTexture texture = new VideoTexture();
    private final AudioOut audio = new AudioOut();
    private String source = "";
    private volatile Decoder picture, sound;
    private volatile String error = "", title = "";
    private volatile boolean live;
    private volatile boolean resolving;
    private int pictureGen, soundGen;
    private boolean shown;
    private long seekedAt;
    private Decoder.Picture current;
    private long liveStart;
    private long lastPosition;
    private State state = State.LOADING;

    VideoPlayer(BlockPos pos) {
        this.pos = pos;
        textureId = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "tv/" + Long.toHexString(pos.asLong()));
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
    }

    public ResourceLocation textureId() {
        return textureId;
    }

    public State state() {
        return state;
    }

    public String error() {
        return error;
    }

    public String title() {
        return title;
    }

    public boolean live() {
        return live;
    }

    /** Whether there's a picture to show yet. */
    public boolean hasPicture() {
        return shown && texture.width > 0;
    }

    public int pictureWidth() {
        return texture.width;
    }

    public int pictureHeight() {
        return texture.height;
    }

    /** How long it is (ms; 0 unknown or live). */
    public long duration() {
        Decoder p = picture, s = sound;
        long d = Math.max(p != null && p.open ? p.durationUs : 0, s != null && s.open ? s.durationUs : 0);
        return d / 1000;
    }

    /** Where it is now (ms). */
    public long position() {
        return lastPosition / 1000;
    }

    // ---- starting

    private void start(TelevisionBlockEntity be, long atUs) {
        stopDecoders();
        source = be.source();
        error = "";
        title = "";
        live = false;
        shown = false;
        current = null;
        state = State.LOADING;
        String src = source.trim();
        String by = be.chosenBy();
        resolving = true;
        Thread t = new Thread(() -> {
            try {
                Map<String, String> opts = new HashMap<>();
                opts.put("reconnect", "1");
                opts.put("reconnect_streamed", "1");
                opts.put("reconnect_on_network_error", "1");
                opts.put("reconnect_delay_max", "5");
                opts.put("rw_timeout", "15000000");
                String videoUrl, audioUrl;
                String id = YouTube.id(src);
                if (id != null) {
                    YouTube.Streams s = YouTube.resolve(id);
                    title = s.title();
                    live = s.live();
                    opts.put("user_agent", s.userAgent());
                    videoUrl = s.video();
                    audioUrl = s.audio();
                } else if (src.startsWith("file:") || looksLikePath(src)) {
                    Path file = Path.of(src.startsWith("file:") ? src.substring(5) : src);
                    if (!Files.isRegularFile(file)) {
                        throw new Exception(by.isEmpty() ? "that file isn't on this computer" : "that file is on " + by + "'s computer");
                    }
                    title = file.getFileName().toString();
                    opts.clear();
                    videoUrl = audioUrl = file.toAbsolutePath().toString();
                } else if (src.matches("(?i)^(https?|rtmps?|rtsp|srt)://.+")) {
                    String name = src.replaceAll("[?#].*$", "");
                    title = name.substring(name.lastIndexOf('/') + 1);
                    opts.put("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36");
                    videoUrl = audioUrl = src;
                } else {
                    throw new Exception("that isn't a link to a video");
                }
                synchronized (this) {
                    if (!src.equals(source.trim()) || released) {
                        return;
                    }
                    Decoder p = new Decoder(videoUrl, opts, true), s = new Decoder(audioUrl, opts, false);
                    picture = p;
                    sound = s;
                    long at = live ? 0 : atUs;
                    p.start(at);
                    s.start(at);
                    seekedAt = System.currentTimeMillis();
                    liveStart = System.nanoTime();
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            } finally {
                resolving = false;
            }
        }, "Shadered+ TV link");
        t.setDaemon(true);
        t.start();
    }

    private static boolean looksLikePath(String s) {
        return s.matches("^[A-Za-z]:[\\\\/].*") || s.startsWith("/") || s.startsWith("\\\\");
    }

    private boolean released;

    // ---- every frame

    void update(TelevisionBlockEntity be, Vec3 centre, float range, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        long target = be.position(partialTick) * 1000L;
        if (!be.source().equals(source)) {
            start(be, target);
        }
        Decoder p = picture, s = sound;
        boolean gaveUp = p != null && s != null && !p.open && p.failed && !s.open && s.failed;
        if (!error.isEmpty() || gaveUp) {
            if (error.isEmpty()) {
                error = !p.missing ? p.error : s.error;
            }
            state = State.ERROR;
            audio.flush();
            return;
        }
        if (p == null || s == null || !p.open && !p.failed || !s.open && !s.failed) {
            state = State.LOADING;
            return;
        }
        boolean hasSound = s.open && audio.available(), hasPicture = p.open;
        long dur = duration() * 1000L;
        boolean ended = false;
        if (live) {
            target = (System.nanoTime() - liveStart) / 1000L;
        } else if (dur > 0) {
            if (be.loop()) {
                target %= dur;
            } else if (target >= dur) {
                target = dur;
                ended = true;
            }
        }
        boolean pause = be.paused() || mc.isPaused() || ended;
        // the sound: in the world, its volume the television's times the game's jukebox volume
        float gain = be.volume() / 100.0F * mc.options.getSoundSourceVolume(SoundSource.RECORDS);
        audio.place(centre.x, centre.y, centre.z, range, gain);
        if (hasSound) {
            audio.pump(s, soundGen, pause);
        }
        long clock = hasSound ? audio.clock() : -1;
        long now;
        boolean settling = System.currentTimeMillis() - seekedAt < 1500;
        if (!live) {
            // drifted from where everyone is (or gone somewhere): everything goes there
            long ref = clock >= 0 && !pause ? clock : -1;
            long head = current != null ? current.us() : -1;
            boolean off = ref >= 0 && Math.abs(ref - target) > 400_000L
                    || !hasSound && head >= 0 && Math.abs(head - target) > 1_500_000L
                    || pause && head >= 0 && Math.abs(head - target) > 250_000L && !settling;
            if (off && !settling) {
                seek(target);
                clock = -1;
            }
        }
        now = clock >= 0 && !pause ? clock : target;
        lastPosition = live ? now : Math.min(now, dur > 0 ? dur : now);
        // the picture that goes with it
        if (hasPicture) {
            Decoder.Picture show = null;
            while (true) {
                Decoder.Picture next = p.pictures.peek();
                if (next == null) {
                    break;
                }
                if (next.gen() < pictureGen) {
                    p.pictures.poll();
                    p.spare.offer(next.rgba());
                    continue;
                }
                if (next.us() > now + 15_000 && (shown || current != null)) {
                    break; // (not yet)
                }
                p.pictures.poll();
                if (show != null) {
                    p.spare.offer(show.rgba());
                }
                show = next;
                if (!shown && current == null) {
                    break; // (the first after starting or going somewhere: shown right away)
                }
            }
            if (show != null) {
                texture.upload(show.rgba(), show.w(), show.h());
                if (current != null) {
                    p.spare.offer(current.rgba());
                }
                current = show;
                shown = true;
            }
        }
        state = ended ? State.ENDED : pause ? State.PAUSED : !shown && hasPicture || hasSound && clock < 0 && !pause ? State.LOADING : State.PLAYING;
    }

    private void seek(long us) {
        Decoder p = picture, s = sound;
        if (p != null) {
            pictureGen = p.gen + 1;
            p.seek(us);
        }
        if (s != null) {
            soundGen = s.gen + 1;
            s.seek(us);
        }
        audio.flush();
        if (current != null && p != null) {
            p.spare.offer(current.rgba());
        }
        current = null;
        seekedAt = System.currentTimeMillis();
    }

    private synchronized void stopDecoders() {
        if (picture != null) {
            picture.close();
        }
        if (sound != null) {
            sound.close();
        }
        picture = null;
        sound = null;
        pictureGen = soundGen = 0;
        audio.flush();
    }

    synchronized void release() {
        released = true;
        stopDecoders();
        audio.release();
        Minecraft.getInstance().getTextureManager().release(textureId);
    }

    public BlockPos pos() {
        return pos;
    }
}
