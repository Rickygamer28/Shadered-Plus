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
}
