package com.example.skyblocks.client.arcade;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A versus match between two cabinets, played on both at once - rollback netcode, as the real fighting games do it.
 * <p>
 * Only what each player holds goes between them, a frame at a time; each cabinet plays the same fight from it (the
 * fight is the same everywhere: the same start, the same dice, the same buttons - the same frames). What you press is
 * played a couple of frames late on both (DELAY), so it usually gets there in time. When it doesn't, your cabinet
 * doesn't wait: it guesses they're still holding what they held, and plays on. When their buttons come and the guess
 * was wrong, it goes back to the frame they changed on (a copy of the fight is kept for every frame) and plays the
 * frames since again, quickly, with what they really pressed - so you see the fight they really fought, a frame or two
 * of it put right. Too far ahead of them (they've stopped, or their cabinet is slow) and it waits for them; a little
 * ahead, now and then it skips a frame to even it up.
 * <p>
 * Every second, each says what the fight was at a frame both have all the buttons for (a number made from all of it);
 * if those ever differ, 2P asks for 1P's fight, and takes it.
 */
public final class Netplay {
    /** A message from the other cabinet: the buttons held at a frame, a fight's number, a piece of a fight, ... */
    public record Msg(int kind, int frame, int x, int y, byte[] data) {}

    /** How it sends to the other cabinet (the server passes it on), and says the match is off from this end. */
    public interface Link {
        void send(int kind, int frame, int x, int y, byte[] data);

        void quit(String why);
    }

    static final int INPUT = 0, HASH = 1, STATE = 2, RESYNC = 3;
    /** Frames late each press is played; the most frames to play on ahead of what's come from them; frames kept. */
    static final int DELAY = 2, MAX_AHEAD = 8, RING = 128;
    /** How often the two compare (frames); how long with nothing from them before the match is given up (frames). */
    static final int HASH_EVERY = 60, GIVE_UP = 15 * 60;
    /** The biggest piece of a fight in one message. */
    static final int CHUNK = 30000;

    /** The match being played here (for the network's thread to pass what comes in to). */
    private static volatile Netplay active;

    final Showdown game;
    final int match, side;
    private final Link link;
    private final ConcurrentLinkedQueue<Msg> in = new ConcurrentLinkedQueue<>();

    /** Frames played (the fight now is as it was before this frame). */
    int frame;
    private final int[] local = new int[RING], remote = new int[RING], used = new int[RING];
    private final Object[][] states = new Object[RING][];
    /** Their buttons are here up to this frame (the first DELAY frames: nothing held, as both know). */
    private int remoteUpTo = DELAY - 1;
    /** The earliest frame played with a wrong guess of theirs: from it, the fight's played again. */
    private int rewindFrom = Integer.MAX_VALUE;
    /** What's held here now (keys, and the bits they make). */
    private final Set<Integer> keys = new HashSet<>();
    private int held;
    /** Keeping pace: the frame they last said they were on, how far ahead of us they said they were; evened out. */
    private int theirFrame, theirLead, sinceSkip;
    private float lead;
    /** Waiting on them (frames), nothing from them (frames); frames gone back last time, at most lately. */
    private int stalled, quiet;
    int rewound, rewoundMost, compared;
    /** The fights' numbers (each frame's, theirs and ours) and the next frame to number. */
    private final Map<Integer, Integer> ours = new HashMap<>(), theirs = new HashMap<>();
    private int nextHash = HASH_EVERY;
    /** Out of step: asked 1P for its fight (when); pieces of it coming; one come, to take at its frame. */
    private int askedAt = -1000, desyncs;
    private byte[][] pieces;
    private int piecesFrame = -1;
    private Object[] taken;
    private int takenFrame = -1;
    private boolean over;

    public Netplay(Showdown game, int match, int side, Link link) {
        this.game = game;
        this.match = match;
        this.side = side;
        this.link = link;
    }

    /** It's this cabinet's match now (what comes in for it goes to it). */
    public void activate() {
        active = this;
    }

    public void deactivate() {
        if (active == this) active = null;
    }

    /** Something from the other cabinet (on the network's thread): for the match it's for. */
    public static void deliver(int match, Msg m) {
        Netplay n = active;
        if (n != null && n.match == match) n.in.add(m);
    }

    /** (the test harness: messages straight in) */
    void receive(Msg m) {
        in.add(m);
    }

    public int matchId() {
        return match;
    }

    /** A key pressed or let go here. */
    void key(int key, boolean down) {
        if (down) keys.add(key);
        else keys.remove(key);
        int bits = 0;
        for (int k : keys) bits |= Showdown.bitOf(k);
        held = bits;
    }

    /** (the test harness: what's held, as bits) */
    void hold(int bits) {
        held = bits;
    }

    /** A frame's time gone by (60 a second): what's come in, the fight put right if it must be, then the next frame. */
    void tick() {
        if (over) return;
        take();
        if (over) return;
        if (taken != null && takenFrame <= frame) { // (1P's fight, from a frame we've reached: ours from then on)
            if (takenFrame > frame - RING + 1) {
                states[Math.floorMod(takenFrame, RING)] = taken;
                rewindFrom = Math.min(rewindFrom, takenFrame);
                ours.keySet().removeIf(f -> f >= takenFrame);
                theirs.keySet().removeIf(f -> f < takenFrame);
                nextHash = (takenFrame / HASH_EVERY + 1) * HASH_EVERY;
            }
            taken = null;
        }
        if (rewindFrom <= frame) { // (a guess of theirs was wrong: back to that frame, and the frames since again)
            Rewind.restore(game, states[Math.floorMod(rewindFrom, RING)]);
            rewound = frame - rewindFrom;
            rewoundMost = Math.max(rewoundMost, rewound);
            game.replaying = true; // (nothing learned from it twice)
            for (int f = rewindFrom; f < frame; f++) play(f);
            game.replaying = false;
            game.drainSounds(); // (heard the first time round)
            rewindFrom = Integer.MAX_VALUE;
        }
        compare();
        if (++quiet > GIVE_UP) {
            end("CONNECTION LOST");
            return;
        }
        if (frame - remoteUpTo > MAX_AHEAD) { // (too far ahead of them: wait)
            stalled++;
            return;
        }
        stalled = 0;
        // keeping pace: our lead over them (as we see it) less theirs over us (as they see it) is twice what we're ahead
        int ourLead = frame - theirFrame;
        lead = lead * 0.9F + (ourLead - theirLead) * 0.1F;
        if (++sinceSkip > 12 && lead >= 2.0F) {
            sinceSkip = 0;
            lead -= 1.0F;
            return; // (a frame's wait: they catch up a frame)
        }
        int f = frame + DELAY; // (what's held now: played that many frames on, on both)
        local[Math.floorMod(f, RING)] = held;
        link.send(INPUT, f, held, ourLead, null);
        play(frame);
        frame++;
    }

    /** A frame played (its fight kept first, to come back to): ours, and theirs - or our guess at it. */
    private void play(int f) {
        int i = Math.floorMod(f, RING);
        states[i] = Rewind.capture(game);
        int r = f <= remoteUpTo ? remote[i] : remote[Math.floorMod(remoteUpTo, RING)];
        used[i] = r;
        int mine = local[i];
        game.simFrame(side == 0 ? mine : r, side == 0 ? r : mine);
    }

    /** What's come in from them. */
    private void take() {
        Msg m;
        while ((m = in.poll()) != null) {
            quiet = 0;
            switch (m.kind()) {
                case INPUT -> {
                    int f = m.frame();
                    if (f <= remoteUpTo) break; // (had it)
                    for (int g = remoteUpTo + 1; g <= f; g++) { // (one each frame - but just in case: any gap, the same)
                        int i = Math.floorMod(g, RING);
                        remote[i] = m.x();
                        if (g < frame && used[i] != m.x()) rewindFrom = Math.min(rewindFrom, g);
                    }
                    remoteUpTo = f;
                    theirFrame = f - DELAY;
                    theirLead = m.y();
                }
                case HASH -> theirs.put(m.frame(), m.x());
                case RESYNC -> {
                    if (side == 0) sendFight();
                }
                case STATE -> piece(m);
                default -> {}
            }
        }
    }

    /** The fights compared, at frames both have all the buttons for. */
    private void compare() {
        int sure = Math.min(frame - 1, remoteUpTo + 1); // (the fight before each of these frames is as it really was)
        while (nextHash <= sure) {
            if (nextHash > frame - RING) {
                int h = Rewind.hash(states[Math.floorMod(nextHash, RING)]);
                ours.put(nextHash, h);
                link.send(HASH, nextHash, h, 0, null);
            }
            nextHash += HASH_EVERY;
        }
        for (var it = ours.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Integer t = theirs.remove(e.getKey());
            if (t == null) {
                if (e.getKey() < frame - 20 * HASH_EVERY) it.remove(); // (never came: forgotten)
                continue;
            }
            it.remove();
            compared++;
            if (t.intValue() != e.getValue()) { // (out of step)
                desyncs++;
                if (side == 1 && frame - askedAt > 2 * 60) {
                    askedAt = frame;
                    link.send(RESYNC, frame, 0, 0, null);
                }
            }
        }
        theirs.keySet().removeIf(f -> f < frame - 20 * HASH_EVERY);
    }

    /** 1P: its fight, as it was at the last frame both have everything for - to 2P, in pieces. */
    private void sendFight() {
        int at = Math.min(frame - 1, remoteUpTo + 1);
        if (at < 0 || at <= frame - RING) return;
        byte[] all = deflate(Rewind.write(states[Math.floorMod(at, RING)]));
        int n = (all.length + CHUNK - 1) / CHUNK;
        for (int k = 0; k < n; k++) {
            byte[] part = java.util.Arrays.copyOfRange(all, k * CHUNK, Math.min(all.length, (k + 1) * CHUNK));
            link.send(STATE, at, k, n, part);
        }
    }

    private void piece(Msg m) {
        if (side != 1 || m.y() <= 0 || m.y() > 64 || m.x() < 0 || m.x() >= m.y()) return;
        if (pieces == null || piecesFrame != m.frame() || pieces.length != m.y()) {
            pieces = new byte[m.y()][];
            piecesFrame = m.frame();
        }
        pieces[m.x()] = m.data();
        for (byte[] p : pieces) {
            if (p == null) return;
        }
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] p : pieces) all.writeBytes(p);
        pieces = null;
        try {
            taken = Rewind.read(inflate(all.toByteArray()));
            takenFrame = piecesFrame;
        } catch (RuntimeException e) {
            askedAt = -1000; // (it didn't come right: ask again)
        }
    }

    private static byte[] deflate(byte[] b) {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(b);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length / 2 + 64);
        byte[] buf = new byte[8192];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] b) {
        Inflater i = new Inflater();
        i.setInput(b);
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length * 3);
        byte[] buf = new byte[8192];
        try {
            while (!i.finished()) {
                int n = i.inflate(buf);
                if (n == 0 && (i.needsInput() || i.needsDictionary())) break;
                out.write(buf, 0, n);
                if (out.size() > 4_000_000) throw new IllegalStateException("too big");
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new IllegalStateException(e);
        } finally {
            i.end();
        }
        return out.toByteArray();
    }

    /** The match off from this end (they've gone quiet). */
    private void end(String why) {
        over = true;
        deactivate();
        link.quit(why);
        game.endVersus(why);
    }

    /** What to say about the link, if anything: waiting on them. */
    String status() {
        if (stalled > 20) return "WAITING FOR " + game.rivalName(side) + "...";
        return "";
    }

    /** (the test harness) */
    int desyncs() {
        return desyncs;
    }
}
