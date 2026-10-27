package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Makes the synthwave sky react to the music playing in the game: music discs in jukeboxes (fading with the distance,
 * like their sound), the background music, and note blocks.
 *
 * Minecraft gives no access to the sound it is playing, so when a disc or a music track starts, the same .ogg file is
 * decoded here in the background and cut into 16 frequency bands (bass to treble) every ~23 ms; while it plays, the
 * bands at the current play position are looked up. Note blocks just give a kick in the band of their pitch.
 *
 * The shader gets the bands now (Bars0..3), bass / mid / high, how loud the music is overall (Music), and a small
 * texture with the last seconds of the bands (x: how long ago, y: band) that the neon grid raises into moving peaks.
 */
public final class MusicReactor {
    public static final ResourceLocation HISTORY = new ResourceLocation(SkyBlocksMod.MOD_ID, "dynamic/music_history");
    private static final int BANDS = 16;
    private static final int FFT = 1024;
    private static final int HISTORY_LENGTH = 128;
    private static final float HISTORY_STEPS_PER_SECOND = 24.0F;

    /** A song being followed. */
    private static final class Track {
        final SoundInstance sound;
        final ResourceLocation file;
        long started = System.nanoTime();
        long pausedNanos;
        @Nullable
        CompletableFuture<Analysis> analysis;

        Track(SoundInstance sound, ResourceLocation file) {
            this.sound = sound;
            this.file = file;
        }
    }

    /** A song cut into bands: BANDS values (0..1) per frame of {@code frameSeconds}. */
    private record Analysis(float[] bands, int frames, float frameSeconds) {}

    private static final Map<SoundInstance, Track> TRACKS = new IdentityHashMap<>();
    private static final Map<ResourceLocation, CompletableFuture<Analysis>> CACHE = new java.util.LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, CompletableFuture<Analysis>> eldest) {
            return size() > 12;
        }
    };

    private static final float[] current = new float[BANDS];
    private static final float[] kicks = new float[BANDS];
    private static float music;
    private static long lastUpdate;
    private static float historyClock;
    @Nullable
    private static DynamicTexture history;

    private MusicReactor() {}

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        private Events() {}

        /** Discs and music are streamed: follow them. */
        @SubscribeEvent
        public static void onStream(PlayStreamingSourceEvent event) {
            SoundInstance sound = event.getSound();
            if (sound.getSource() != SoundSource.RECORDS && sound.getSource() != SoundSource.MUSIC) {
                return;
            }
            Sound file = sound.getSound();
            if (file != null) {
                TRACKS.put(sound, new Track(sound, file.getPath()));
            }
        }

        /** Note blocks: a kick in the band of the note's pitch. */
        @SubscribeEvent
        public static void onSound(PlaySoundSourceEvent event) {
            if (!event.getName().startsWith("block.note_block.")) {
                return;
            }
            SoundInstance sound = event.getSound();
            float weight = weight(sound);
            if (weight <= 0.0F) {
                return;
            }
            float pitch = Math.max(0.5F, Math.min(2.0F, sound.getPitch()));
            int band = Math.round((float) ((Math.log(pitch) / Math.log(2.0) + 1.0) / 2.0 * (BANDS - 1)));
            for (int b = 0; b < BANDS; b++) {
                float k = b == band ? 1.0F : Math.abs(b - band) == 1 ? 0.6F : Math.abs(b - band) == 2 ? 0.25F : 0.12F;
                kicks[b] = Math.max(kicks[b], k * weight);
            }
        }
    }

    // ---- every frame the synthwave sky is drawn ----

    /** Brings the bands up to date and hands them to the synthwave shader. */
    static void apply(ShaderInstance shader) {
        update();
        shader.safeGetUniform("Music").set(music);
        shader.safeGetUniform("HistoryPhase").set(Math.min(1.0F, historyClock));
        float bass = (current[0] + current[1] + current[2]) / 3.0F;
        float mid = 0.0F;
        for (int b = 3; b < 10; b++) {
            mid += current[b] / 7.0F;
        }
        float high = 0.0F;
        for (int b = 10; b < BANDS; b++) {
            high += current[b] / 6.0F;
        }
        shader.safeGetUniform("Bass").set(bass);
        shader.safeGetUniform("Mid").set(mid);
        shader.safeGetUniform("High").set(high);
        for (int i = 0; i < 4; i++) {
            shader.safeGetUniform("Bars" + i).set(current[i * 4], current[i * 4 + 1], current[i * 4 + 2], current[i * 4 + 3]);
        }
    }

    /** For others reacting to music (the dance floor): brings it up to date (at most once a frame). */
    static void tick() {
        if (System.nanoTime() - lastUpdate > 2_000_000L) {
            update();
        }
    }

    static float music() {
        return music;
    }

    static float bass() {
        return (current[0] + current[1] + current[2]) / 3.0F;
    }

    static float band(int b) {
        return current[Math.max(0, Math.min(BANDS - 1, b))];
    }

    private static void update() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = lastUpdate == 0 ? 0.0F : Math.min(0.25F, (now - lastUpdate) / 1.0e9F);
        lastUpdate = now;
        boolean paused = mc.isPaused();

        float[] target = new float[BANDS];
        float loudest = 0.0F;
        var it = TRACKS.values().iterator();
        while (it.hasNext()) {
            Track track = it.next();
            if (!mc.getSoundManager().isActive(track.sound)) {
                it.remove();
                continue;
            }
            if (paused) {
                track.pausedNanos += (long) (dt * 1.0e9F);
            }
            if (track.analysis == null) {
                track.analysis = analyse(track.file);
            }
            Analysis analysis = track.analysis.getNow(null);
            float weight = weight(track.sound);
            if (analysis == null || weight <= 0.0F) {
                continue;
            }
            float seconds = (now - track.started - track.pausedNanos) / 1.0e9F;
            int frame = (int) (seconds / analysis.frameSeconds());
            if (frame < 0 || frame >= analysis.frames()) {
                continue;
            }
            for (int b = 0; b < BANDS; b++) {
                target[b] = Math.max(target[b], analysis.bands()[frame * BANDS + b] * weight);
            }
            loudest = Math.max(loudest, weight);
        }

        float decay = (float) Math.exp(-dt * 7.0); // (falls back smoothly, jumps up at once)
        float kickDecay = (float) Math.exp(-dt * 5.0);
        boolean anyKick = false;
        for (int b = 0; b < BANDS; b++) {
            float value = Math.max(target[b], kicks[b]);
            current[b] = paused ? current[b] : Math.max(value, current[b] * decay);
            if (!paused) {
                kicks[b] *= kickDecay;
            }
            anyKick |= kicks[b] > 0.01F;
        }
        float wanted = Math.max(loudest, anyKick ? 1.0F : 0.0F);
        music = paused ? music : wanted > music ? Math.min(wanted, music + dt * 4.0F) : Math.max(wanted, music - dt * 0.5F);

        // the last seconds, for the grid's moving peaks
        if (history == null) {
            history = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, HISTORY_LENGTH, BANDS, true));
            mc.getTextureManager().register(HISTORY, history);
        }
        if (!paused) {
            historyClock += dt * HISTORY_STEPS_PER_SECOND;
        }
        NativeImage pixels = history.getPixels();
        if (pixels != null && historyClock >= 1.0F) {
            int steps = Math.min(HISTORY_LENGTH, (int) historyClock);
            historyClock -= (int) historyClock;
            for (int y = 0; y < BANDS; y++) {
                for (int x = HISTORY_LENGTH - 1; x >= steps; x--) {
                    pixels.setPixelRGBA(x, y, pixels.getPixelRGBA(x - steps, y));
                }
                int v = Math.round(Math.min(1.0F, current[y]) * 255.0F);
                for (int x = 0; x < steps; x++) {
                    pixels.setPixelRGBA(x, y, 0xFF000000 | v);
                }
            }
            history.bind();
            pixels.upload(0, 0, 0, 0, 0, HISTORY_LENGTH, BANDS, true, true, false, false); // (smooth between steps)
        }
    }

    /** How strongly a sound counts: its volume settings, and for a jukebox how far you are, as its sound fades. */
    private static float weight(SoundInstance sound) {
        Minecraft mc = Minecraft.getInstance();
        float volume = mc.options.getSoundSourceVolume(SoundSource.MASTER) * mc.options.getSoundSourceVolume(sound.getSource());
        if (volume <= 0.0F) {
            return 0.0F;
        }
        float strength = 1.0F;
        if (!sound.isRelative() && sound.getAttenuation() == SoundInstance.Attenuation.LINEAR) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            float range = Math.max(sound.getVolume(), 1.0F) * (sound.getSound() != null ? sound.getSound().getAttenuationDistance() : 16);
            double distance = cam.distanceTo(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
            strength = (float) Math.max(0.0, 1.0 - distance / range);
            if (sound.getSource() == SoundSource.RECORDS) {
                // speakers within 24 blocks of the jukebox carry it: as loud near them as near the jukebox
                Vec3 at = new Vec3(sound.getX(), sound.getY(), sound.getZ());
                synchronized (com.example.skyblocks.SpeakerBlockEntity.CLIENT_SPEAKERS) {
                    for (net.minecraft.core.BlockPos speaker : com.example.skyblocks.SpeakerBlockEntity.CLIENT_SPEAKERS) {
                        Vec3 s = Vec3.atCenterOf(speaker);
                        if (s.distanceToSqr(at) < 24.0 * 24.0) {
                            strength = Math.max(strength, (float) Math.max(0.0, 1.0 - cam.distanceTo(s) / 32.0));
                        }
                    }
                }
            }
        }
        // (quiet settings still react, a bit less)
        return strength * (0.6F + 0.4F * Math.min(1.0F, volume));
    }

    // ---- cutting a song into bands (in the background) ----

    private static CompletableFuture<Analysis> analyse(ResourceLocation file) {
        return CACHE.computeIfAbsent(file, f -> new SoundBufferLibrary(Minecraft.getInstance().getResourceManager())
                .getStream(f, false)
                .thenApplyAsync(MusicReactor::analyse, Util.backgroundExecutor())
                .exceptionally(e -> {
                    SkyBlocksMod.LOGGER.debug("Shadered+: couldn't follow the music {}", f, e);
                    return new Analysis(new float[0], 0, 1.0F);
                }));
    }

    private static Analysis analyse(AudioStream stream) {
        try (stream) {
            AudioFormat format = stream.getFormat();
            int channels = Math.max(1, format.getChannels());
            int bytes = Math.max(1, format.getSampleSizeInBits() / 8);
            float rate = format.getSampleRate();
            ByteOrder order = format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
            int[] edges = bandEdges(rate);
            float[] window = new float[FFT];
            for (int i = 0; i < FFT; i++) {
                window[i] = 0.5F - 0.5F * (float) Math.cos(2.0 * Math.PI * i / (FFT - 1));
            }
            float[] re = new float[FFT];
            float[] im = new float[FFT];
            float[] mono = new float[FFT];
            int filled = 0;
            List<float[]> frames = new ArrayList<>();
            while (true) {
                ByteBuffer data = stream.read(FFT * channels * bytes * 16);
                if (data == null || !data.hasRemaining()) {
                    break;
                }
                data.order(order);
                while (data.remaining() >= channels * bytes) {
                    float sum = 0.0F;
                    for (int c = 0; c < channels; c++) {
                        sum += bytes == 2 ? data.getShort() / 32768.0F : bytes == 1 ? (data.get() & 0xFF) / 128.0F - 1.0F
                                : data.getFloat();
                    }
                    mono[filled++] = sum / channels;
                    if (filled == FFT) {
                        frames.add(bands(mono, window, re, im, edges));
                        filled = 0;
                    }
                }
            }
            // each band: its loud moments reach 1 (the 95th percentile), quiet ones fall towards 0
            int count = frames.size();
            float[] out = new float[count * BANDS];
            float[] column = new float[count];
            for (int b = 0; b < BANDS; b++) {
                for (int i = 0; i < count; i++) {
                    column[i] = frames.get(i)[b];
                }
                float[] sorted = column.clone();
                Arrays.sort(sorted);
                float low = count == 0 ? 0.0F : sorted[(int) (count * 0.2)];
                float high = count == 0 ? 1.0F : sorted[Math.min(count - 1, (int) (count * 0.95))];
                float span = Math.max(1.0e-4F, high - low);
                for (int i = 0; i < count; i++) {
                    float v = Math.max(0.0F, Math.min(1.0F, (column[i] - low) / span));
                    out[i * BANDS + b] = v * v; // (punchier: only the loud parts spike high)
                }
            }
            return new Analysis(out, count, FFT / rate);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** FFT bins where each of the 16 bands starts (log-spaced, 40 Hz .. 12 kHz), plus the end. */
    private static int[] bandEdges(float rate) {
        int[] edges = new int[BANDS + 1];
        for (int b = 0; b <= BANDS; b++) {
            double hz = 40.0 * Math.pow(12000.0 / 40.0, b / (double) BANDS);
            edges[b] = Math.max(1, Math.min(FFT / 2, (int) Math.round(hz / rate * FFT)));
        }
        for (int b = 1; b <= BANDS; b++) {
            edges[b] = Math.max(edges[b], edges[b - 1] + 1);
        }
        return edges;
    }

    private static float[] bands(float[] mono, float[] window, float[] re, float[] im, int[] edges) {
        for (int i = 0; i < FFT; i++) {
            re[i] = mono[i] * window[i];
            im[i] = 0.0F;
        }
        fft(re, im);
        float[] out = new float[BANDS];
        for (int b = 0; b < BANDS; b++) {
            float energy = 0.0F;
            int from = Math.min(edges[b], FFT / 2 - 1), to = Math.min(edges[b + 1], FFT / 2);
            for (int k = from; k < Math.max(from + 1, to); k++) {
                energy += re[k] * re[k] + im[k] * im[k];
            }
            out[b] = (float) Math.log1p(Math.sqrt(energy / Math.max(1, to - from)) * 10.0);
        }
        return out;
    }

    /** In-place radix-2 FFT. */
    private static void fft(float[] re, float[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len;
            float wr = (float) Math.cos(angle), wi = (float) Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                float cr = 1.0F, ci = 0.0F;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    float xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    float nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = nr;
                }
            }
        }
    }
}
