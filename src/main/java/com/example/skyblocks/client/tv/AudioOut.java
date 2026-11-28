package com.example.skyblocks.client.tv;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;

/**
 * A screen's sound: one OpenAL source at the screen (fading with distance like the game's own sounds), and one more at
 * each speaker playing it - all fed the same decoded sound a chunk at a time, started together. Knows how far it has
 * played (the clock the picture follows) and how hard its bass is hitting right now (the beat lights and speakers go
 * by). Only used on the render thread.
 */
final class AudioOut {
    private static final int BUFFERS = 24;
    private static final int AL_DISTANCE_MODEL = 0xD000, AL_LINEAR_DISTANCE = 0xD003;
    private int source = -1;
    private int[] extras = new int[0];
    private int wanted;
    private long triedAt;
    /** Each band's running level and loudest lately (what its loud moments are measured against). */
    private final float[] bandAverage = new float[16], bandPeak = new float[16], bandsNow = new float[16];
    private final ArrayDeque<Integer> free = new ArrayDeque<>();
    /** How many sources still hold each buffer queued (it's free again once none do). */
    private final Map<Integer, Integer> holders = new HashMap<>();
    /** What's queued on the main source, oldest first: buffer, its start (us), its samples, its bass (x1000). */
    private final ArrayDeque<long[]> queued = new ArrayDeque<>();
    private ShortBuffer scratch;
    private boolean paused, started;
    private float lowPass, bassAverage = 0.05F, beat;

    /**
     * No sound here for now (no device, or the game's sound restarting): tried again after a moment - never for good
     * (a hiccup of the game's sound used to leave it silent till the game restarted).
     */
    private static boolean none;
    private static long noneSince;

    private boolean ready() {
        if (none) {
            if (System.currentTimeMillis() - noneSince < 3000) {
                return false;
            }
            none = false;
            source = -1; // (made anew)
            extras = new int[0];
        wanted = 0;
            queued.clear();
            free.clear();
            holders.clear();
        }
        try {
            return readyNow();
        } catch (Throwable e) {
            if (noneSince == 0) {
                com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Shadered+ television: no sound for now ({})", e.toString());
            }
            none = true;
            noneSince = System.currentTimeMillis();
            return false;
        }
    }

    private boolean readyNow() {
        if (source != -1 && AL10.alIsSource(source)) {
            return true;
        }
        // (first time, or the game's sound was restarted: everything made anew)
        queued.clear();
        free.clear();
        holders.clear();
        extras = new int[0];
        wanted = 0;
        source = newSource();
        if (source == -1) {
            return false;
        }
        for (int i = 0; i < BUFFERS; i++) {
            free.add(AL10.alGenBuffers());
        }
        if (scratch == null) {
            scratch = MemoryUtil.memAllocShort(Decoder.RATE);
        }
        return true;
    }

    private static int newSource() {
        AL10.alGetError(); // (an earlier call's error isn't this one's)
        int s = AL10.alGenSources();
        if (AL10.alGetError() != AL10.AL_NO_ERROR || s == 0) {
            if (s != 0 && AL10.alIsSource(s)) AL10.alDeleteSources(s);
            AL10.alGetError();
            return -1;
        }
        AL10.alSourcei(s, AL_DISTANCE_MODEL, AL_LINEAR_DISTANCE);
        AL10.alSourcef(s, AL10.AL_ROLLOFF_FACTOR, 1.0F);
        AL10.alSourcef(s, AL10.AL_REFERENCE_DISTANCE, 0.0F);
        AL10.alGetError();
        return s;
    }

    private void place0(double x, double y, double z, float range, float gain) {
        if (!ready()) {
            return;
        }
        AL10.alSource3f(source, AL10.AL_POSITION, (float) x, (float) y, (float) z);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, range);
        AL10.alSourcef(source, AL10.AL_GAIN, gain);
    }

    /**
     * Its speakers (where each is): placed, as loud as the screen. A different number of them than before: all made
     * anew, what was queued thrown away - true (it has to be gone back to).
     */
    private boolean speakers0(List<Vec3> at, float gain) {
        if (!ready()) {
            return false;
        }
        boolean changed = false;
        int want = Math.min(at.size(), 8);
        // (a different number of them than asked for last time - not than made: if the game had no more sources to
        // give, it doesn't ask again every frame, flushing and going back each time)
        if (want != wanted && (want < wanted || System.currentTimeMillis() - triedAt > 5000)) {
            wanted = want;
            triedAt = System.currentTimeMillis();
            flush();
            for (int s : extras) {
                if (AL10.alIsSource(s)) AL10.alDeleteSources(s);
            }
            int n = want;
            int[] made = new int[n];
            int k = 0;
            for (int i = 0; i < n; i++) {
                int s = newSource();
                if (s != -1) made[k++] = s;
            }
            extras = java.util.Arrays.copyOf(made, k);
            if (k < n) {
                com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+ television: only {} of {} speakers could play it (the game's sound has no more room)", k, n);
            }
            changed = true;
        }
        for (int i = 0; i < extras.length && i < at.size(); i++) {
            Vec3 p = at.get(i);
            AL10.alSource3f(extras[i], AL10.AL_POSITION, (float) p.x, (float) p.y, (float) p.z);
            AL10.alSourcef(extras[i], AL10.AL_MAX_DISTANCE, 24.0F);
            AL10.alSourcef(extras[i], AL10.AL_GAIN, gain);
        }
        AL10.alGetError();
        return changed;
    }

    private int[] all() {
        int[] a = new int[extras.length + 1];
        a[0] = source;
        System.arraycopy(extras, 0, a, 1, extras.length);
        return a;
    }

    /** Hands back what has played, queues what's waiting (of this seek or later), and plays unless paused. */
    private void pump0(Decoder d, int wantGen, boolean pause, long target) {
        if (!ready()) {
            return;
        }
        for (int s : all()) {
            int done = AL10.alGetSourcei(s, AL10.AL_BUFFERS_PROCESSED);
            for (int i = 0; i < done; i++) {
                int b = AL10.alSourceUnqueueBuffers(s);
                if (s == source && !queued.isEmpty()) {
                    queued.poll();
                }
                release(b);
            }
        }
        while (!free.isEmpty()) {
            Decoder.Sound s = d.sounds.peek();
            if (s == null) {
                break;
            }
            d.sounds.poll();
            if (s.gen() < wantGen) {
                continue;
            }
            // (starting, behind where everyone is: what's already past is skipped, not played late)
            if (queued.isEmpty() && target >= 0 && s.us() + s.samples().length * 1_000_000L / Decoder.RATE < target - 30_000) {
                continue;
            }
            int n = Math.min(s.samples().length, scratch.capacity());
            scratch.clear();
            scratch.put(s.samples(), 0, n).flip();
            int buf = free.poll();
            AL10.alBufferData(buf, AL10.AL_FORMAT_MONO16, scratch, Decoder.RATE);
            int[] sources = all();
            for (int src : sources) {
                AL10.alSourceQueueBuffers(src, buf);
            }
            holders.put(buf, sources.length);
            // (its sound's bands - 1024 samples at a time - kept with it, to show when it plays)
            int frames = Math.min(64, (n + 1023) / 1024);
            long[] entry = new long[4 + frames * 16];
            entry[0] = buf;
            entry[1] = s.us();
            entry[2] = n;
            entry[3] = (long) (bass(s.samples(), n) * 1000);
            for (int f = 0; f < frames; f++) {
                float[] sp = com.example.skyblocks.client.MusicReactor.spectrum(s.samples(), f * 1024, Math.min(n, f * 1024 + 1024), Decoder.RATE);
                for (int b = 0; b < 16; b++) entry[4 + f * 16 + b] = (long) (sp[b] * 10000);
            }
            queued.add(entry);
        }
        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (pause) {
            if (state == AL10.AL_PLAYING) {
                for (int s : all()) AL10.alSourcePause(s);
            }
        } else if (state != AL10.AL_PLAYING && queued.size() >= (paused || state == AL10.AL_PAUSED ? 1 : 3)) {
            int[] sources = all();
            IntBuffer ids = MemoryUtil.memAllocInt(sources.length).put(sources).flip();
            AL10.alSourcePlayv(ids); // (all together: the speakers in step with the screen)
            MemoryUtil.memFree(ids);
            if (!started) {
                started = true;
                com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+ television: sound started");
            }
        }
        paused = pause;
        // how hard the bass hits now, against how hard it's been hitting
        float now = 0;
        long[] front = queued.peek();
        if (front != null && playing()) {
            now = front[3] / 1000.0F;
        }
        bassAverage += (now - bassAverage) * 0.02F;
        // its 16 bands now: each against its own running level, so the loud moments of any song reach 1
        int frame = front == null || !playing() ? -1 : AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET) / 1024;
        for (int b = 0; b < 16; b++) {
            float v = 0;
            if (frame >= 0 && 4 + frame * 16 + b < front.length) {
                v = front[4 + frame * 16 + b] / 10000.0F;
            }
            if (v > 0) {
                bandAverage[b] += (v - bandAverage[b]) * 0.01F;
                bandPeak[b] = Math.max(v, bandPeak[b] - 0.0008F);
            }
            float floor = bandAverage[b] * 0.85F;
            float x = v <= 0 ? 0 : Math.max(0, Math.min(1, (v - floor) / Math.max(0.05F, bandPeak[b] - floor)));
            bandsNow[b] = x * x;
        }
        float hit = Math.max(0, Math.min(1, (now / Math.max(0.005F, bassAverage) - 0.9F) * 1.4F));
        beat = Math.max(hit, beat * 0.86F);
        AL10.alGetError();
    }

    private void release(int b) {
        int left = holders.getOrDefault(b, 1) - 1;
        if (left <= 0) {
            holders.remove(b);
            free.add(b);
        } else {
            holders.put(b, left);
        }
    }

    /** The bass in some sound: how loud it is below about 150 Hz (0-1). */
    private float bass(short[] samples, int n) {
        float sum = 0;
        for (int i = 0; i < n; i++) {
            lowPass += (samples[i] / 32768.0F - lowPass) * 0.02F;
            sum += lowPass * lowPass;
        }
        return n == 0 ? 0 : (float) Math.sqrt(sum / n);
    }

    /** How loud each of its 16 bands (40 Hz .. 12 kHz) is right now, against how loud it's been (0-1). */
    float[] bands() {
        return bandsNow;
    }

    /** How hard the beat hits right now (0-1; falls away between beats). */
    float beat() {
        return beat;
    }

    /** Where it is (us), or -1 with nothing queued. */
    private long clock0() {
        if (none || source == -1 || queued.isEmpty()) {
            return -1;
        }
        long[] front = queued.peek();
        int at = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
        return front[1] + at * 1_000_000L / Decoder.RATE;
    }

    /** Whether the game has sound here (none: no device - the picture keeps time by itself). */
    private boolean available0() {
        return ready();
    }

    private boolean playing0() {
        return !none && source != -1 && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

    /** Everything queued thrown away (gone somewhere else). */
    private void flush0() {
        if (none || source == -1 || !AL10.alIsSource(source)) {
            return;
        }
        for (int s : all()) {
            AL10.alSourceStop(s);
            int done = AL10.alGetSourcei(s, AL10.AL_BUFFERS_PROCESSED);
            for (int i = 0; i < done; i++) {
                release(AL10.alSourceUnqueueBuffers(s));
            }
        }
        queued.clear();
        beat = 0;
        AL10.alGetError();
    }

    private void release0() {
        if (!none && source != -1 && AL10.alIsSource(source)) {
            flush();
            for (int s : all()) {
                AL10.alDeleteSources(s);
            }
            for (int b : free) {
                AL10.alDeleteBuffers(b);
            }
            AL10.alGetError();
        }
        source = -1;
        extras = new int[0];
        wanted = 0;
        free.clear();
        holders.clear();
        queued.clear();
        if (scratch != null) {
            MemoryUtil.memFree(scratch);
            scratch = null;
        }
    }

    // (each into the screens' own sound context while it works - see OwnSound)
    void place(double x, double y, double z, float range, float gain) {
        long t = OwnSound.enter();
        try {
            place0(x, y, z, range, gain);
        } finally {
            OwnSound.leave(t);
        }
    }

    boolean speakers(List<Vec3> at, float gain) {
        long t = OwnSound.enter();
        try {
            return speakers0(at, gain);
        } finally {
            OwnSound.leave(t);
        }
    }

    void pump(Decoder d, int wantGen, boolean pause, long target) {
        long t = OwnSound.enter();
        try {
            pump0(d, wantGen, pause, target);
        } finally {
            OwnSound.leave(t);
        }
    }

    long clock() {
        long t = OwnSound.enter();
        try {
            return clock0();
        } finally {
            OwnSound.leave(t);
        }
    }

    boolean available() {
        long t = OwnSound.enter();
        try {
            return available0();
        } finally {
            OwnSound.leave(t);
        }
    }

    boolean playing() {
        long t = OwnSound.enter();
        try {
            return playing0();
        } finally {
            OwnSound.leave(t);
        }
    }

    void flush() {
        long t = OwnSound.enter();
        try {
            flush0();
        } finally {
            OwnSound.leave(t);
        }
    }

    void release() {
        long t = OwnSound.enter();
        try {
            release0();
        } finally {
            OwnSound.leave(t);
        }
    }
}
