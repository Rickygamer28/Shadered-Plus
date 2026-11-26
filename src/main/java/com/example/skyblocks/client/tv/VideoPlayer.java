package com.example.skyblocks.client.tv;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TvState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
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

    /** Its name (for its textures). */
    private final String name;
    /** Asks the server for something (what's asked at a playlist video's end). */
    private Sender sender = (a, t, v) -> { };
    private final ResourceLocation textureId;
    private final VideoTexture texture = new VideoTexture();
    private final AudioOut audio = new AudioOut();
    private String source = "";
    /** Which of its playlist's videos (as the television counts them), how many there are and which one that is. */
    private int item;
    private volatile int listSize, listIndex;
    private boolean askedNext;
    /** How far along its playlist's order (0 first). */
    private volatile int listAlong;
    /** Its playlist (once read), and the shuffle it was started with. */
    private volatile Playlist.List2 list;
    private boolean shuffled;
    private long shuffleSeed;
    private int shuffleFirst;
    /** Bumped on every start: a link thread of an older one gives up. */
    private volatile int startGen;
    /** The song's cover, words and look (a YouTube Music link), or null. */
    private volatile Music music;
    private net.minecraft.client.renderer.texture.DynamicTexture coverTexture;
    private ResourceLocation coverId;
    private long errorAt;
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
    /** Whether it has gone to where it should be once its length is known (joining a repeating video part way). */
    private boolean placed;
    private State state = State.LOADING;

    /** Asks the server for something about this screen (TelevisionPayload's actions). */
    public interface Sender {
        void send(int action, String text, long value);
    }

    VideoPlayer(String name) {
        this.name = name;
        textureId = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "tv/" + name);
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

    /** Why there's no sound, if its sound couldn't play (the picture still can). */
    public String soundProblem() {
        Decoder s = sound;
        return s != null && s.failed && !s.missing ? s.error : "";
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

    private void start(TvState be, long atUs) {
        stopDecoders();
        source = be.source();
        item = be.item();
        shuffled = be.shuffle();
        shuffleSeed = be.shuffleSeed();
        shuffleFirst = be.shuffleFirst();
        int gen = ++startGen;
        list = null;
        releaseCover();
        music = null;
        askedNext = false;
        errorAt = 0;
        listSize = 0;
        listIndex = 0;
        error = "";
        title = "";
        live = false;
        shown = false;
        placed = false;
        current = null;
        state = State.LOADING;
        String whole = source.trim();
        String by = be.chosenBy();
        int at = item;
        boolean shuf = shuffled;
        long seed = shuffleSeed;
        int sFirst = shuffleFirst;
        resolving = true;
        Thread t = new Thread(() -> {
            try {
                Map<String, String> opts = new HashMap<>();
                opts.put("reconnect", "1");
                opts.put("reconnect_streamed", "1");
                opts.put("reconnect_on_network_error", "1");
                opts.put("reconnect_delay_max", "5");
                opts.put("rw_timeout", "15000000");
                opts.put("icy", "0");
                java.util.List<String> videoUrl, audioUrl;
                String src = whole;
                if (Playlist.is(whole)) {
                    Playlist.List2 l = Playlist.resolve(whole);
                    int n = l.entries().size();
                    int i = Playlist.index(l, shuf, seed, sFirst, at);
                    if (gen != startGen) {
                        return;
                    }
                    list = l;
                    listSize = n;
                    listIndex = i;
                    listAlong = Playlist.along(l, shuf, at);
                    src = l.entries().get(i).source();
                    title = l.entries().get(i).title();
                }
                String id = YouTube.id(src);
                if (id != null) {
                    YouTube.Streams s = YouTube.resolve(id);
                    if (!s.title().isEmpty()) {
                        title = s.title();
                    }
                    live = s.live();
                    opts.put("user_agent", s.userAgent());
                    opts.put(Decoder.JAVA_HTTP, s.userAgent());
                    videoUrl = s.video();
                    audioUrl = s.audio();
                    if (src.contains("music.youtube.com") && !live) {
                        // (a song: its cover and words instead of its video - only its sound is read)
                        music = Music.load(s.title(), s.author(), s.thumbnail(), id, s.seconds());
                        videoUrl = java.util.List.of();
                    }
                } else if (src.startsWith("file:") || looksLikePath(src)) {
                    Path file = Path.of(src.startsWith("file:") ? src.substring(5) : src);
                    if (!Files.isRegularFile(file)) {
                        throw new Exception(by.isEmpty() ? "that file isn't on this computer" : "that file is on " + by + "'s computer");
                    }
                    title = file.getFileName().toString();
                    opts.clear();
                    videoUrl = audioUrl = java.util.List.of(file.toAbsolutePath().toString());
                } else if (src.matches("(?i)^(https?|rtmps?|rtsp|srt)://.+")) {
                    String name = src.replaceAll("[?#].*$", "");
                    title = name.substring(name.lastIndexOf('/') + 1);
                    opts.put("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36");
                    videoUrl = audioUrl = java.util.List.of(src);
                } else {
                    throw new Exception("that isn't a link to a video");
                }
                synchronized (this) {
                    if (gen != startGen || released) {
                        return;
                    }
                    Decoder p = new Decoder(videoUrl, opts, true), s = new Decoder(audioUrl, opts, false);
                    picture = p;
                    sound = s;
                    long from = live ? 0 : atUs;
                    p.start(from);
                    s.start(from);
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

    void update(TvState be, Sender send, Vec3 centre, float range, float partialTick) {
        sender = send;
        Minecraft mc = Minecraft.getInstance();
        long target = be.position(partialTick) * 1000L;
        if (!be.source().equals(source)) {
            start(be, target);
        } else if (be.item() != item || be.shuffle() != shuffled || be.shuffleSeed() != shuffleSeed || be.shuffleFirst() != shuffleFirst) {
            Playlist.List2 l = list;
            if (l != null && Playlist.index(l, be.shuffle(), be.shuffleSeed(), be.shuffleFirst(), be.item()) == listIndex) {
                // (shuffle turned on or off: the same video goes on, only the order after it changed)
                item = be.item();
                shuffled = be.shuffle();
                shuffleSeed = be.shuffleSeed();
                shuffleFirst = be.shuffleFirst();
                listAlong = Playlist.along(l, shuffled, item);
                askedNext = false;
            } else {
                start(be, target);
            }
        }
        Decoder p = picture, s = sound;
        boolean gaveUp = p != null && s != null && !p.open && p.failed && !s.open && s.failed;
        if (!error.isEmpty() || gaveUp) {
            if (error.isEmpty()) {
                error = !p.missing ? p.error : s.error;
            }
            state = State.ERROR;
            audio.flush();
            if (errorAt == 0) {
                errorAt = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - errorAt > 3000) {
                askNext(be); // (a playlist's video that won't play: on to the next)
            }
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
        } else if (dur > 0 && listSize > 0) {
            // (in a playlist: at its end, on to the next - repeating the whole list, or stopping after its last)
            if (target >= dur) {
                target = dur;
                ended = true;
                askNext(be);
            }
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
            audio.pump(s, soundGen, pause, live ? -1 : target);
        }
        long clock = hasSound ? audio.clock() : -1;
        long now;
        boolean settling = System.currentTimeMillis() - seekedAt < 1500;
        if (!placed && !live) {
            placed = true;
            if (be.position(partialTick) * 1000L != target) {
                seek(target); // (it started at where it'd be without repeating: past its end)
                settling = true;
            }
        }
        // (its sound ran out before its end - the link dropped: it goes on from where it should be)
        if (!live && !settling && hasSound && s.ended && s.sounds.isEmpty() && dur > 0 && target < dur - 2_000_000L && !pause
                && !audio.playing()) {
            seek(target);
            settling = true;
        }
        if (!live && !settling && hasPicture && current == null && p.ended && p.pictures.isEmpty()) {
            seek(target); // (ran out with nothing to show: back to where it should be)
            settling = true;
        }
        if (!live) {
            // drifted from where everyone is (or gone somewhere): everything goes there
            long ref = clock >= 0 && !pause && audio.playing() ? clock : -1;
            long head = current != null ? current.us() : -1;
            boolean off = ref >= 0 && Math.abs(ref - target) > 900_000L
                    || !hasSound && head >= 0 && Math.abs(head - target) > 1_500_000L
                    || pause && head >= 0 && Math.abs(head - target) > 250_000L && !settling;
            if (off && !settling) {
                seek(target);
                clock = -1;
            }
        }
        now = clock >= 0 && !pause && audio.playing() ? clock : target;
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

    /** Asks for its playlist's next video (once; everyone watching asks - the television takes the first). */
    private void askNext(TvState be) {
        if (askedNext || listSize == 0 || !be.loop() && listAlong + 1 >= listSize) {
            return;
        }
        askedNext = true;
        sender.send(com.example.skyblocks.TelevisionPayload.GOTO, "", com.example.skyblocks.TelevisionPayload.fromTo(item, item + 1));
    }

    /** Which of its playlist's videos it is (from 0), and how many; 0 not a playlist. */
    public int listIndex() {
        return listIndex;
    }

    public int listSize() {
        return listSize;
    }

    /** The count that keeps the video playing now with shuffle turned off. */
    public int itemInOrder() {
        Playlist.List2 l = list;
        return l == null ? item : Playlist.itemFor(l, listIndex);
    }

    /** The song shown instead of a video (YouTube Music), or null. */
    @org.jetbrains.annotations.Nullable
    public Music music() {
        return music;
    }

    /** Its cover as a texture (made on first asking, on the render thread), or null. */
    @org.jetbrains.annotations.Nullable
    public ResourceLocation cover() {
        Music m = music;
        if (m == null) {
            return null;
        }
        if (coverTexture == null && m.cover != null) {
            coverTexture = new net.minecraft.client.renderer.texture.DynamicTexture(m.cover);
            coverTexture.setFilter(true, false);
            coverId = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "tv/cover_" + name + "_" + startGen);
            Minecraft.getInstance().getTextureManager().register(coverId, coverTexture);
        }
        return coverTexture == null ? null : coverId;
    }

    private void releaseCover() {
        if (coverId != null) {
            Minecraft.getInstance().getTextureManager().release(coverId); // (frees the picture too)
        } else if (music != null && music.cover != null) {
            music.cover.close();
            music.cover = null;
        }
        coverTexture = null;
        coverId = null;
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
        releaseCover();
    }

}
