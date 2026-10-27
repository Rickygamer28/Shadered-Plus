package com.example.skyblocks;

/**
 * The music box's tunes (all long out of copyright), played on note-block bells: each note a note-block pitch
 * (0..24, F# up two octaves) or -1 for a rest, and how long it lasts (in steps of {@link #TICKS} game ticks).
 */
public final class MusicBoxSongs {
    public static final int COUNT = 3;
    /** Their names (for the menu text). */
    public static final String[] NAMES = {"brahms_lullaby", "twinkle_twinkle", "fur_elise"};
    /** How many game ticks one step of each lasts. */
    public static final int[] TICKS = {6, 5, 3};
    public static final int[][] NOTES = {
        {10, 10, 13, 10, 10, 13, 10, 13, 18, 17, 15, 15, 13, 8, 10, 11, 8, 8, 10, 11, 8, 11, 17, 15, 13, 17, 18, 6, 6, 18, 15, 11, 13, 10, 6, 11, 13, 15, 13, 6, 6, 18, 15, 11, 13, 10, 6, 11, 10, 8, 6, -1}, // Brahms' lullaby
        {6, 6, 13, 13, 15, 15, 13, 11, 11, 10, 10, 8, 8, 6, 13, 13, 11, 11, 10, 10, 8, 13, 13, 11, 11, 10, 10, 8, 6, 6, 13, 13, 15, 15, 13, 11, 11, 10, 10, 8, 8, 6, -1}, // Twinkle Twinkle
        {22, 21, 22, 21, 22, 17, 20, 18, 15, 6, 10, 15, 17, 10, 14, 17, 18, 10, 22, 21, 22, 21, 22, 17, 20, 18, 15, 6, 10, 15, 17, 10, 18, 17, 15, -1}, // Fur Elise
    };
    public static final int[][] STEPS = {
        {1, 1, 4, 1, 1, 4, 1, 1, 2, 2, 2, 2, 2, 1, 1, 2, 2, 1, 1, 4, 1, 1, 1, 1, 2, 2, 4, 1, 1, 4, 1, 1, 4, 1, 1, 2, 2, 2, 4, 1, 1, 4, 1, 1, 4, 1, 1, 2, 2, 2, 6, 6},
        {2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 4, 2, 2, 2, 2, 2, 2, 4, 4},
        {1, 1, 1, 1, 1, 1, 1, 1, 3, 1, 1, 1, 3, 1, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 1, 1, 1, 3, 1, 1, 1, 6, 6},
    };

    /** How long a tune takes, in game ticks (then it starts again). */
    public static int length(int song) {
        int steps = 0;
        for (int s : STEPS[song]) steps += s;
        return steps * TICKS[song];
    }

    /** The note starting at this many ticks into the tune, or -2 if none starts then (-1: a rest). */
    public static int noteAt(int song, int tick) {
        int at = 0;
        for (int i = 0; i < NOTES[song].length; i++) {
            if (at == tick) return NOTES[song][i];
            at += STEPS[song][i] * TICKS[song];
            if (at > tick) break;
        }
        return -2;
    }

    /** When the latest note began, at or before this many ticks into the tune (ticks), and which note it was. */
    public static int[] lastNote(int song, int tick) {
        int at = 0, when = 0, which = 0;
        for (int i = 0; i < NOTES[song].length; i++) {
            if (at > tick) break;
            if (NOTES[song][i] >= 0) { when = at; which = i; }
            at += STEPS[song][i] * TICKS[song];
        }
        return new int[] {when, which};
    }

    private MusicBoxSongs() {}
}
