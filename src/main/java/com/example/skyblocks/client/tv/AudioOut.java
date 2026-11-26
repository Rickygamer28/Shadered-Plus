package com.example.skyblocks.client.tv;

import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;

/**
 * A television's sound: one OpenAL source in the world (at the screen, fading with distance like the game's own
 * sounds), fed the decoded sound a chunk at a time. Knows how far it has played: the clock the picture follows.
 * Only used on the render thread.
 */
final class AudioOut {
    private static final int BUFFERS = 24;
    private static final int AL_DISTANCE_MODEL = 0xD000, AL_LINEAR_DISTANCE = 0xD003;
    private int source = -1;
    private final ArrayDeque<Integer> free = new ArrayDeque<>();
    /** What's queued, oldest first: buffer, its start (us), its samples. */
    private final ArrayDeque<long[]> queued = new ArrayDeque<>();
    private ShortBuffer scratch;
    private boolean paused;

    private boolean ready() {
        if (source != -1 && AL10.alIsSource(source)) {
            return true;
        }
        // (first time, or the game's sound was restarted: everything made anew)
        queued.clear();
        free.clear();
        source = AL10.alGenSources();
        if (AL10.alGetError() != AL10.AL_NO_ERROR || source == 0) {
            source = -1;
            return false;
        }
        for (int i = 0; i < BUFFERS; i++) {
            free.add(AL10.alGenBuffers());
        }
        AL10.alSourcei(source, AL_DISTANCE_MODEL, AL_LINEAR_DISTANCE);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0F);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 0.0F);
        AL10.alGetError();
        if (scratch == null) {
            scratch = MemoryUtil.memAllocShort(Decoder.RATE);
        }
        return true;
    }

    void place(double x, double y, double z, float range, float gain) {
        if (!ready()) {
            return;
        }
        AL10.alSource3f(source, AL10.AL_POSITION, (float) x, (float) y, (float) z);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, range);
        AL10.alSourcef(source, AL10.AL_GAIN, gain);
    }

    /** Hands back what has played, queues what's waiting (of this seek or later), and plays unless paused. */
    void pump(Decoder d, int wantGen, boolean pause) {
        if (!ready()) {
            return;
        }
        int done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < done && !queued.isEmpty(); i++) {
            free.add(AL10.alSourceUnqueueBuffers(source));
            queued.poll();
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
            int n = Math.min(s.samples().length, scratch.capacity());
            scratch.clear();
            scratch.put(s.samples(), 0, n).flip();
            int buf = free.poll();
            AL10.alBufferData(buf, AL10.AL_FORMAT_MONO16, scratch, Decoder.RATE);
            AL10.alSourceQueueBuffers(source, buf);
            queued.add(new long[] {buf, s.us(), n});
        }
        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (pause) {
            if (state == AL10.AL_PLAYING) {
                AL10.alSourcePause(source);
            }
        } else if (state != AL10.AL_PLAYING && queued.size() >= (paused || state == AL10.AL_PAUSED ? 1 : 3)) {
            AL10.alSourcePlay(source);
        }
        paused = pause;
        AL10.alGetError();
    }

    /** Where it is (us), or -1 with nothing queued. */
    long clock() {
        if (source == -1 || queued.isEmpty()) {
            return -1;
        }
        long[] front = queued.peek();
        int at = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
        return front[1] + at * 1_000_000L / Decoder.RATE;
    }

    boolean playing() {
        return source != -1 && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

    /** Everything queued thrown away (gone somewhere else). */
    void flush() {
        if (source == -1 || !AL10.alIsSource(source)) {
            return;
        }
        AL10.alSourceStop(source);
        int done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < done; i++) {
            free.add(AL10.alSourceUnqueueBuffers(source));
        }
        queued.clear();
        AL10.alGetError();
    }

    void release() {
        if (source != -1 && AL10.alIsSource(source)) {
            AL10.alSourceStop(source);
            int done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
            for (int i = 0; i < done; i++) {
                free.add(AL10.alSourceUnqueueBuffers(source));
            }
            AL10.alDeleteSources(source);
            for (int b : free) {
                AL10.alDeleteBuffers(b);
            }
            AL10.alGetError();
        }
        source = -1;
        free.clear();
        queued.clear();
        if (scratch != null) {
            MemoryUtil.memFree(scratch);
            scratch = null;
        }
    }
}
