package com.example.skyblocks;

/** What a screen (a television wall, or a tablet) plays and where it is in it: what its player on each computer follows. */
public interface TvState {
    String source();

    String chosenBy();

    /** Where it should be now (ms). */
    long position(float partialTick);

    boolean paused();

    int volume();

    boolean loop();

    int item();

    boolean shuffle();

    long shuffleSeed();

    int shuffleFirst();

    /** How many links are waiting to play after this one (a television's queue; a tablet has none). */
    default int queueSize() {
        return 0;
    }

    /** Bumped every time something is chosen (the same link chosen again starts again). */
    default int playId() {
        return 0;
    }
}
