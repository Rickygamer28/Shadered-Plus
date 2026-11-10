package com.example.skyblocks.client.arcade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Shadered Showdown: a 3D fighting game against the cabinet that moves and combos like Tekken 7 - its own fighters and
 * moves, the same systems. It runs at 60 frames a second on its own clock (not the world's 20 ticks), and every move
 * has real frame data: its startup (i10 jab, i15 launchers), and how far ahead or behind it leaves you on block and on
 * hit - so a -13 launcher blocked gets launched back, a +1 jab keeps your turn. Four limbs, 1 2 3 4 (U I J K), and
 * moves in numpad notation from the way you're holding.
 *
 * <p>Movement: walk, dash (f,f) into a run, backdash (b,b - chain them), sidestep (tap up or down) and sidewalk (hold
 * it), jumps (held off a moment, so uf+4 can be a hopkick). Highs, mids and lows: stand to block highs and mids,
 * crouch for lows (highs go over you). While-standing moves as you let go of a crouch. Strings (1,1,2  1,2,4  1,2,3
 * 3,4), launchers (df+2, ws2, uf+4), juggles that hurt less as they go, one screw a combo (b+2, f+3) to carry them on,
 * enders (f+2) into the wall for a wall splat and wall combos. Counter hits and crumples, power crushes, low parry
 * (df just as a low comes), throws (1+3, 2+4, broken with 1 or 2), tech rolls, get-up kicks and rolls, ki charge
 * (1+2+3+4), rage with a rage art (df+1+2) or a rage drive. Each fighter has its own: RIKU the crouch dash (f,n,d,df)
 * and its just-frame PERFECT DRAGON, NEKO attacks out of sidesteps, a slide and a backflip, ROBO a command throw and
 * heavy power crushes, BLAZE the d,df,f+2 burning fist. H shows the move list; P cycles practice (a dummy that stands,
 * blocks everything, or fights - and your moves' frame data shown).
 */
public class Showdown extends ArcadeGame {
    /** It runs at 60 frames a second, like the real thing: every move's frame data is in those frames. */
    private static final int FPS = 60;
    private static final int GROUND = 128, ROUND_FRAMES = 60 * FPS, WALL_L = 10, WALL_R = W - 10;
    private static final int MAX_HP = 170;
    /** Gravity a frame: standing jumps, and someone juggled (floatier). */
    private static final float GRAVITY = 0.4F / 9, GRAVITY_AIR = 0.06F;
    /** How much a frame takes off a slide (a push back on block or hit). */
    private static final float SLIDE = 0.9086F;

    @Override
    public int rate() {
        return FPS;
    }

    private enum Level { HIGH, MID, LOW, THROW }

    /** How a move looks while it's out: which limb, at which height. */
    private enum Pose { JAB, CROSS, LOWPUNCH, MIDKICK, HIGHKICK, LOWKICK, SWEEP, UPPER, THROW, AIRKICK, TACKLE, RAGE, SPIN, PALM }

    // what else a move does
    private static final int SCREW = 1;      // spins someone juggled and carries them on - once a combo
    private static final int CRUSH = 2;      // power crush: soaks up highs and mids while it comes out
    private static final int DUCK = 4;       // goes under highs
    private static final int HOP = 8;        // goes over lows
    private static final int CARRY = 16;     // sends them flying off (to the wall: a wall splat)
    private static final int KND = 32;       // knocks them down
    private static final int HOMING = 64;    // turns to catch a sidestep
    private static final int CH_LAUNCH = 128; // a counter hit launches
    private static final int CH_CRUMPLE = 256; // a counter hit crumples them (time for a launcher)
    private static final int ELECTRIC = 512; // a just-frame one: faster, safe, crackling
    private static final int BREAK_BOTH = 1024; // a command throw: broken with 1+2
    private static final int UNBLOCKABLE = 2048;
    private static final int GROUND_HIT = 4096; // hits someone lying down

    /**
     * A move, in frames: its button (1-4; 0 for none), its startup (the frame it first hits on - "i15"), how long it's
     * out, its recovery after; where and how hard; the frames it leaves you ahead (+) or behind (-) when it's blocked and
     * when it hits, as in the move lists; what it does; the moves that can follow it in a string.
     */
    private record Move(String name, int button, Pose pose, Level level, int startup, int active, int recovery, int damage,
                        int reach, int onBlock, int onHit, float launch, int flags, Move[] next) {
        boolean is(int flag) {
            return (flags & flag) != 0;
        }

        int total() {
            return startup + active + recovery;
        }
    }

    private static Move m(String name, int button, Pose pose, Level level, int i, int a, int r, int dmg, int reach,
                          int onBlock, int onHit, float launch, int flags, Move... next) {
        return new Move(name, button, pose, level, i, a, r, dmg, reach, onBlock, onHit, launch / 3, flags, next.length == 0 ? null : next);
    }

    // ---- everyone: the strings 1,1,2  1,2,4  1,2,3  3,4 ----
    private static final Move ELBOW_FINISH = m("RISING ELBOW", 2, Pose.UPPER, Level.MID, 13, 2, 24, 12, 24, -12, 4, 0, 0);
    private static final Move JAB_2 = m("JAB", 1, Pose.JAB, Level.HIGH, 10, 2, 16, 8, 22, 1, 8, 0, 0, ELBOW_FINISH);
    private static final Move CROSS_KICK = m("CROSS KICK", 4, Pose.HIGHKICK, Level.HIGH, 15, 3, 26, 18, 30, -12, 0, 0, KND);
    private static final Move CROSS_LOW = m("LOW SNAP", 3, Pose.LOWKICK, Level.LOW, 18, 3, 28, 10, 27, -15, 2, 0, DUCK);
    private static final Move CROSS = m("CROSS", 2, Pose.CROSS, Level.HIGH, 10, 2, 18, 10, 25, -1, 6, 0, 0, CROSS_KICK, CROSS_LOW);
    private static final Move JAB = m("JAB", 1, Pose.JAB, Level.HIGH, 10, 2, 16, 7, 22, 1, 8, 0, 0, JAB_2, CROSS);
    private static final Move WHIRL_KICK = m("WHIRL KICK", 4, Pose.HIGHKICK, Level.HIGH, 16, 3, 26, 20, 30, -9, 0, 0, KND);
    private static final Move MID_KICK = m("MID KICK", 3, Pose.MIDKICK, Level.MID, 14, 3, 20, 13, 28, -7, 4, 0, 0, WHIRL_KICK);
    private static final Move HIGH_KICK = m("HIGH KICK", 4, Pose.HIGHKICK, Level.HIGH, 12, 3, 22, 15, 30, -9, 4, 0, 0);
    // ---- forward, back ----
    private static final Move HOMING_KICK = m("HOMING KICK", 4, Pose.MIDKICK, Level.MID, 17, 3, 28, 18, 30, -9, 4, 0, HOMING | CH_LAUNCH);
    private static final Move LUNGE_PUNCH = m("LUNGE PUNCH", 2, Pose.CROSS, Level.MID, 15, 3, 30, 18, 32, -13, 0, 0, KND | CARRY);
    private static final Move SPIRAL_KICK = m("SPIRAL KICK", 3, Pose.MIDKICK, Level.MID, 16, 3, 26, 15, 28, -9, 3, 0, SCREW);
    private static final Move SPIN_FIST = m("SPIN FIST", 2, Pose.SPIN, Level.HIGH, 14, 2, 20, 16, 28, -5, 5, 0, SCREW | CH_CRUMPLE);
    private static final Move IRON_PALM = m("IRON PALM", 0, Pose.PALM, Level.MID, 18, 3, 30, 20, 28, -14, 0, 0, CRUSH | CARRY | KND);
    // ---- down, down-forward, down-back ----
    private static final Move CROUCH_JAB = m("CROUCH JAB", 1, Pose.LOWPUNCH, Level.MID, 10, 2, 14, 5, 20, -5, 6, 0, DUCK);
    private static final Move CROUCH_STRAIGHT = m("CROUCH STRAIGHT", 2, Pose.LOWPUNCH, Level.MID, 11, 2, 18, 9, 24, -4, 7, 0, DUCK);
    private static final Move LOW_KICK = m("LOW KICK", 3, Pose.LOWKICK, Level.LOW, 16, 2, 24, 10, 26, -13, -2, 0, DUCK | GROUND_HIT);
    private static final Move TOE_KICK = m("TOE KICK", 4, Pose.LOWKICK, Level.LOW, 12, 2, 22, 7, 24, -12, -3, 0, DUCK | GROUND_HIT);
    private static final Move SWEEP = m("SWEEP", 4, Pose.SWEEP, Level.LOW, 20, 3, 36, 16, 30, -26, 0, 0, KND | DUCK);
    private static final Move MID_CHECK = m("MID CHECK", 1, Pose.JAB, Level.MID, 13, 2, 18, 10, 24, 1, 8, 0, CH_CRUMPLE);
    private static final Move LAUNCHER = m("LAUNCHER", 2, Pose.UPPER, Level.MID, 15, 2, 32, 15, 22, -13, 0, 6.2F, 0);
    private static final Move FRONT_KICK = m("FRONT KICK", 3, Pose.MIDKICK, Level.MID, 14, 2, 22, 13, 28, -6, 5, 0, 0);
    private static final Move STEP_KICK = m("STEP KICK", 4, Pose.MIDKICK, Level.MID, 13, 2, 20, 12, 30, -3, 8, 0, 0);
    // ---- while standing up from a crouch ----
    private static final Move RISING_JAB = m("RISING JAB", 1, Pose.JAB, Level.MID, 13, 2, 18, 10, 24, 1, 8, 0, HOP);
    private static final Move RISING_UPPER = m("RISING UPPER", 2, Pose.UPPER, Level.MID, 15, 2, 32, 15, 22, -13, 0, 6.0F, HOP);
    private static final Move RISING_KNEE = m("RISING KNEE", 3, Pose.MIDKICK, Level.MID, 13, 2, 20, 14, 24, 2, 7, 0, HOP);
    private static final Move RISING_KICK = m("RISING KICK", 4, Pose.MIDKICK, Level.MID, 11, 2, 22, 15, 28, -3, 8, 0, HOP);
    // ---- up-forward, the floor, throws, the rest ----
    private static final Move HOPKICK = m("HOPKICK", 4, Pose.AIRKICK, Level.MID, 15, 4, 30, 15, 26, -13, 0, 5.4F, HOP);
    private static final Move GETUP_KICK = m("GET-UP KICK", 4, Pose.LOWKICK, Level.MID, 14, 2, 28, 12, 26, -12, 2, 0, 0);
    private static final Move GETUP_LOW = m("GET-UP LOW", 3, Pose.LOWKICK, Level.LOW, 14, 2, 28, 10, 26, -12, 2, 0, 0);
    private static final Move THROW_L = m("THROW", 1, Pose.THROW, Level.THROW, 12, 2, 30, 35, 18, 0, 0, 0, KND);
    private static final Move THROW_R = m("THROW", 2, Pose.THROW, Level.THROW, 12, 2, 30, 38, 18, 0, 0, 0, KND);
    private static final Move AIR_KICK = m("JUMP KICK", 4, Pose.AIRKICK, Level.MID, 8, 18, 12, 14, 26, -4, 6, 0, 0);
    private static final Move TACKLE = m("RUNNING TACKLE", 0, Pose.TACKLE, Level.MID, 3, 6, 40, 15, 20, -15, 0, 0, KND);
    private static final Move RAGE_ART = m("RAGE ART", 0, Pose.RAGE, Level.MID, 20, 3, 50, 55, 34, -15, 0, 0, KND | CRUSH | HOMING | CARRY);

    // ---- RIKU: the crouch dash (f,n,d,df), and its just-frame dragon upper ----
    private static final Move SNAP_ELBOW = m("SNAP ELBOW", 1, Pose.LOWPUNCH, Level.MID, 14, 2, 22, 12, 24, 2, 8, 0, DUCK);
    private static final Move DRAGON_UPPER = m("DRAGON UPPER", 2, Pose.UPPER, Level.MID, 15, 2, 34, 16, 24, -15, 0, 6.6F, DUCK);
    private static final Move PERFECT_DRAGON = m("PERFECT DRAGON", 2, Pose.UPPER, Level.MID, 11, 2, 24, 18, 24, 5, 0, 6.6F, DUCK | ELECTRIC);
    private static final Move HELL_SWEEP = m("HELL SWEEP", 4, Pose.SWEEP, Level.LOW, 20, 3, 34, 16, 30, -23, 0, 0, KND | DUCK);
    private static final Move DEVIL_RUSH = m("DEVIL RUSH", 0, Pose.PALM, Level.MID, 14, 3, 24, 25, 30, 8, 0, 0, KND | CARRY | ELECTRIC);
    // ---- NEKO: quick on its feet - attacks out of a sidestep, a slide, a backflip ----
    private static final Move CLAW_SWIPE = m("CLAW SWIPE", 1, Pose.CROSS, Level.MID, 14, 2, 20, 14, 26, -4, 6, 0, HOMING);
    private static final Move CAT_SLIDE = m("CAT SLIDE", 3, Pose.SWEEP, Level.LOW, 16, 4, 30, 15, 32, -14, 0, 0, KND | DUCK);
    private static final Move BACKFLIP = m("BACKFLIP", 3, Pose.AIRKICK, Level.MID, 16, 3, 28, 16, 26, -14, 0, 6.3F, HOP);
    private static final Move NINE_LIVES = m("NINE LIVES", 0, Pose.HIGHKICK, Level.MID, 13, 3, 24, 22, 30, 3, 0, 6.6F, HOMING);
    // ---- ROBO: heavy - power crushes, a command throw, a slam for someone on the floor ----
    private static final Move IRON_GRAB = m("IRON GRAB", 0, Pose.THROW, Level.THROW, 14, 2, 30, 45, 20, 0, 0, 0, KND | BREAK_BOTH);
    private static final Move PISTON = m("PISTON", 0, Pose.PALM, Level.MID, 16, 3, 28, 22, 30, -10, 0, 0, CRUSH | CARRY | KND);
    private static final Move GROUND_SLAM = m("GROUND SLAM", 0, Pose.LOWPUNCH, Level.MID, 20, 3, 36, 18, 26, -15, 0, 0, GROUND_HIT | DUCK);
    private static final Move OVERDRIVE = m("OVERDRIVE", 0, Pose.PALM, Level.MID, 16, 3, 26, 26, 30, 2, 0, 6.4F, CRUSH);
    // ---- BLAZE: d,df,f+2 - the burning fist ----
    private static final Move BURNING_FIST = m("BURNING FIST", 2, Pose.CROSS, Level.MID, 14, 3, 32, 25, 32, -18, 0, 0, KND | CARRY);
    private static final Move FLAME_HEEL = m("FLAME HEEL", 4, Pose.HIGHKICK, Level.HIGH, 16, 3, 26, 18, 30, -8, 6, 0, CH_LAUNCH);
    private static final Move HEAT_RUSH = m("HEAT RUSH", 0, Pose.PALM, Level.MID, 16, 3, 28, 20, 30, -12, 0, 0, CRUSH | KND);
    private static final Move INFERNO = m("INFERNO FIST", 0, Pose.CROSS, Level.MID, 14, 3, 30, 34, 34, 5, 0, 0, KND | CARRY | ELECTRIC);

    private enum State { STAND, CROUCH, DASH, RUN, BACKDASH, SIDESTEP, SIDEWALK, CROUCH_DASH, JUMP, ATTACK, THROWN, HIT, BLOCK, AIR, SPLAT, DOWN, RISE, KI, KO, WIN }

    /** A fighter: its look (palette colours), speed and strength. */
    private record Style(String name, int gi, int skin, int band, int hair, int kind, float speed, float power) {}

    // kind: 0 a headband, 1 cat ears, 2 an antenna, 3 flame hair
    private static final Style[] FIGHTERS = {
            new Style("RIKU", 10, 7, 6, 13, 0, 1.0F, 1.0F),
            new Style("NEKO", 7, 8, 15, 4, 1, 1.2F, 0.9F),
            new Style("ROBO", 13, 5, 12, 12, 2, 0.85F, 1.15F),
            new Style("BLAZE", 6, 8, 8, 7, 3, 1.05F, 1.05F)};

    private static final class Body {
        final Style style;
        final int who;
        float x, y, vx, vy;
        /** Where it was a frame ago (drawn smoothly between that and now). */
        float ox, oy, oDepth;
        /** How far off the line between them it has stepped (pixels; + into the screen). */
        float depth;
        int facing = 1, hp = MAX_HP, wins, t, stun, combo, sideDir, airHits, wallHits;
        State state = State.STAND;
        Move move;
        boolean hitDone, landed, rage, rageUsed, buffered;
        Move queued;
        /** A throw it's caught in: what breaks it (0: 1, 1: 2, 2: 1+2), who threw, with what. */
        int breakWith = -1;
        Body thrower;
        Move grab;
        /** Spun by a screw this juggle; can tech when it lands; crumpling; blocking low; ki charged. */
        boolean screwed, techable = true, crumple, blockLow, blockedLow, ki, advancing;
        /** While-standing up until; parried at; the combo's damage, how long its counter stays up after; how many moves
         *  it's started (so the cabinet's fighter reacts once to each). */
        int wsUntil = -100, parryAt = -100, comboDmg, comboShow, serial;

        Body(Style style, int who, float x) {
            this.style = style;
            this.who = who;
            this.x = x;
        }

        boolean airborne() {
            return y > 0.01F || vy > 0;
        }

        boolean free() {
            return state == State.STAND || state == State.CROUCH || state == State.SIDEWALK || state == State.RUN;
        }

        /** Down low: highs go over it. (A ducking move only while it's out, not its recovery.) */
        boolean crouching() {
            return state == State.CROUCH || state == State.CROUCH_DASH || (state == State.BLOCK && blockLow)
                    || (state == State.ATTACK && move != null && move.is(DUCK) && t < move.startup() + move.active() + 6);
        }

        /** Up off the floor: lows go under it. */
        boolean hopping() {
            return state == State.ATTACK && move != null && move.is(HOP) && t >= 4 && t < move.startup() + move.active();
        }

        boolean down() {
            return state == State.DOWN || state == State.RISE || state == State.KO;
        }

        /** How many frames until it's free again (a move's recovery). */
        int left() {
            return state == State.ATTACK && move != null ? Math.max(0, move.total() - t) : 0;
        }
    }

    private Body me, foe;
    private final List<float[]> sparks = new ArrayList<>(); // x, y, frames left (in 20ths), colour
    private final List<Object[]> callouts = new ArrayList<>(); // text, x, y, frames left
    private int pick, ladder, round, clock, roundOver, shake, thinkIn, lap;
    private boolean picking;
    private String banner = "";
    private int bannerFor;
    /** The camera turning round as the two step about (pixels the backdrop has slid). */
    private float spin;

    // input: what's held; the way it's held over the last frames (for motions: f,f  f,n,d,df  d,df,f ...)
    private final Set<Integer> held = new HashSet<>();
    private boolean kL, kR, kU, kD;
    private final List<int[]> dirs = new ArrayList<>(); // numpad direction, the frame it began
    private int upAt = -100, downAt = -100, qcfAt = -100, dfAt = -100;
    /** When each button was last pressed (1-4). */
    private final int[] pressedAt = {-100, -100, -100, -100, -100};
    /** A jump about to go (held off a moment, so up-forward + 4 can be a hopkick instead): its way, and when. */
    private int jumpDir, jumpAt = -1;
    /** The move list up (the fight paused). */
    private boolean showList;
    /** Practice: 0 off; 1 the dummy stands; 2 it blocks everything; 3 it fights back. (Health refills, no clock.) */
    private int practice;
    /** Practice: what your last move was and how it went (frame data). */
    private String frameInfo = "";
    private int frameInfoFor;
    /** The cabinet's fighter: the move of yours it's already reacted to; how far into a juggle it is; blocking a
     *  moment ago (to punish what it blocked). */
    private int cpuSeen = -1, cpuJuggle;
    private boolean cpuWasBlocking;

    @Override
    public String name() {
        return "SHADERED SHOWDOWN";
    }

    @Override
    public String[] help() {
        return new String[] {"WASD  U I J K = 1 2 3 4  H: moves  P: practice", "W/S tap: step  FF dash  BB back  W+A/D: jump"};
    }

    @Override
    public void start() {
        score = 0;
        over = won = false;
        picking = true;
        ladder = 0;
        lap = 0;
        banner = "";
        held.clear();
        dirs.clear();
        kL = kR = kU = kD = false;
        showList = false;
        practice = 0;
        jumpAt = -1;
    }

    private Style foeStyle() {
        int n = 0;
        for (int i = 0; i < FIGHTERS.length; i++) {
            if (i == pick) continue;
            if (n == ladder % (FIGHTERS.length - 1)) return FIGHTERS[i];
            n++;
        }
        return FIGHTERS[0];
    }

    private void newMatch() {
        round = 0;
        poses.clear(); // (the last match's fighters: forgotten)
        shownHp.clear();
        trails.clear();
        me = new Body(FIGHTERS[pick], pick, 60);
        int f = styleIndex(foeStyle());
        foe = new Body(FIGHTERS[f], f, W - 60);
        newRound();
    }

    private void newRound() {
        round++;
        me.x = 60;
        foe.x = W - 60;
        for (Body b : new Body[] {me, foe}) {
            b.y = b.vx = b.vy = b.depth = 0;
            b.hp = MAX_HP;
            b.state = State.STAND;
            b.stun = b.combo = b.t = b.airHits = b.wallHits = 0;
            b.move = null;
            b.queued = null;
            b.rage = b.rageUsed = false;
            b.thrower = null;
            b.breakWith = -1;
            b.screwed = b.blockLow = b.blockedLow = b.crumple = b.ki = false;
            b.techable = true;
            b.comboDmg = b.comboShow = 0;
            b.wsUntil = b.parryAt = -100;
        }
        jumpAt = -1;
        cpuJuggle = 0;
        cpuSeen = -1;
        me.facing = 1;
        foe.facing = -1;
        sparks.clear();
        callouts.clear();
        clock = ROUND_FRAMES;
        roundOver = practice > 0 ? -2 : -120;
        if (practice == 0) say(round == 3 ? "FINAL ROUND" : "ROUND " + round, 40);
    }

    /** A banner across the screen, for so many 20ths of a second. */
    private void say(String s, int twentieths) {
        banner = s;
        bannerFor = twentieths * 3;
    }

    private int difficulty() {
        return ladder + lap;
    }

    private static Body other(Body b, Body me, Body foe) {
        return b == me ? foe : me;
    }

    // ================= ticking (60 a second) =================

    @Override
    public void tick() {
        ticks++;
        if (picking) return;
        if (showList) { // (the move list up: everything waits)
            hold();
            return;
        }
        if (slowmo > 0) { // (a K.O.: the fight in slow motion for a moment)
            slowmo--;
            if (slowmo % 2 == 1) {
                hold(); // (nothing moves this frame: drawn standing still, not replaying the last one)
                return;
            }
        }
        animate();
        if (ticks % 3 == 0 && shake > 0) shake--;
        if (bannerFor > 0 && --bannerFor == 0 && roundOver == 0) banner = "";
        if (frameInfoFor > 0) frameInfoFor--;
        sparks.removeIf(s -> (s[2] -= 1 / 3.0F) <= 0);
        callouts.removeIf(c -> {
            c[3] = (Integer) c[3] - 1;
            return (Integer) c[3] <= 0;
        });
        if (roundOver < 0) {
            if (++roundOver == 0) banner = "";
            if (roundOver == -36) say("FIGHT!", 14);
            return;
        }
        if (roundOver > 0) {
            for (Body b : new Body[] {me, foe}) {
                physics(b);
                b.x = Mth.clamp(b.x, WALL_L, WALL_R);
            }
            if (--roundOver == 0) endRound();
            return;
        }
        if (practice == 0 && --clock <= 0) {
            finishRound(me.hp >= foe.hp ? me : foe, "TIME");
            return;
        }
        for (Body b : new Body[] {me, foe}) if (b.comboShow > 0) b.comboShow--;
        if (jumpAt >= 0 && ticks >= jumpAt) { // (the held-off jump: nothing came of it, so it's a jump)
            jumpAt = -1;
            jump(me, jumpDir);
        }
        player();
        if (practice == 0 || practice == 3) cpu();
        else dummy();
        for (Body b : new Body[] {me, foe}) {
            step(b);
            physics(b);
        }
        for (Body b : new Body[] {me, foe}) strike(b, other(b, me, foe));
        // they can't pass through each other (unless one's stepped well off the line)
        float gap = foe.x - me.x;
        if (Math.abs(gap) < 14 && Math.abs(me.y - foe.y) < 24 && Math.abs(me.depth - foe.depth) < 10) {
            float push = (14 - Math.abs(gap)) / 2.0F * (gap >= 0 ? 1 : -1);
            me.x -= push;
            foe.x += push;
        }
        for (Body b : new Body[] {me, foe}) {
            if (b.x < WALL_L || b.x > WALL_R) {
                // slammed into the wall while reeling: a wall splat
                if ((b.state == State.AIR || b.state == State.HIT) && Math.abs(b.vx) > 0.33F && b.wallHits == 0) {
                    b.state = State.SPLAT;
                    b.stun = 42;
                    b.vx = 0;
                    b.vy = 0;
                    b.wallHits = 1;
                    shake = 6;
                    call("WALL SPLAT!", b.x, GROUND - b.y - 40);
                }
                b.x = Mth.clamp(b.x, WALL_L, WALL_R);
            }
        }
        // the line between them turns back straight when nobody's stepping: the camera swings round with it
        boolean stepping = me.state == State.SIDESTEP || me.state == State.SIDEWALK || foe.state == State.SIDESTEP || foe.state == State.SIDEWALK;
        if (!stepping) {
            for (Body b : new Body[] {me, foe}) {
                float d = Mth.clamp(-b.depth, -0.4F, 0.4F);
                b.depth += d;
                spin += d * (b == me ? 3.0F : -3.0F) * b.facing;
            }
        }
        face(me);
        face(foe);
        for (Body b : new Body[] {me, foe}) {
            if (!b.rage && !b.rageUsed && b.hp > 0 && b.hp <= MAX_HP / 4) {
                b.rage = true;
                call("RAGE", b.x, GROUND - 50);
            }
        }
        if (practice > 0) { // (practice: nobody's beaten - health comes back between combos)
            for (Body b : new Body[] {me, foe}) {
                Body o = other(b, me, foe);
                if (b.hp <= 0) b.hp = 1;
                if (o.comboShow == 0 && b.free() && b.hp < MAX_HP) b.hp = Math.min(MAX_HP, b.hp + 4);
                if (b.hp > MAX_HP / 4) b.rage = false;
            }
            return;
        }
        if (me.hp <= 0) finishRound(foe, "K.O.");
        else if (foe.hp <= 0) finishRound(me, "K.O.");
    }

    private void face(Body b) {
        Body o = other(b, me, foe);
        if (b.free() && !b.airborne()) b.facing = o.x > b.x ? 1 : -1;
    }

    // ================= the player =================

    private boolean fwdHeld(Body b) {
        return (kR && b.facing > 0) || (kL && b.facing < 0);
    }

    private boolean backHeld(Body b) {
        if (b == me) return (kL && b.facing > 0) || (kR && b.facing < 0);
        return b.state == State.BLOCK;
    }

    private void player() {
        Body b = me;
        if (!b.free()) return;
        if (b.state == State.RUN) {
            if (!fwdHeld(b)) b.state = State.STAND;
            return;
        }
        if (b.state == State.SIDEWALK) {
            boolean still = b.sideDir > 0 ? kU : kD;
            if (!still) b.state = State.STAND;
            return;
        }
        if (kD) {
            if (b.state != State.CROUCH) b.t = 0;
            b.state = State.CROUCH;
            b.vx = 0;
            return;
        }
        b.state = State.STAND;
        if (fwdHeld(b)) b.x += b.facing * 0.36F * b.style.speed();
        else if (backHeld(b)) b.x -= b.facing * 0.28F * b.style.speed();
    }

    // ================= moving =================

    /** Every frame: what its state does - dashing, stepping, getting up ... */
    private void step(Body b) {
        b.t++;
        switch (b.state) {
            case DASH -> {
                b.x += b.facing * Math.max(0.2F, 1.15F - b.t * 0.028F) * b.style.speed();
                boolean holding = b == me ? fwdHeld(b) : b.buffered;
                if (b.t >= 24) {
                    b.state = holding ? State.RUN : State.STAND;
                    b.t = 0;
                }
            }
            case RUN -> {
                b.x += b.facing * Math.min(1.1F, 0.55F + b.t * 0.009F) * b.style.speed();
                Body o = other(b, me, foe);
                if (b.t > 24 && Math.abs(o.x - b.x) < 22 && Math.abs(o.depth - b.depth) < 8 && !o.down()) {
                    attack(b, TACKLE); // (running into them: a shoulder tackle)
                }
            }
            case CROUCH_DASH -> { // (low and fast in: highs sail over; it comes up into the while-standing moves)
                b.x += b.facing * Math.max(0.17F, 1.27F - b.t * 0.03F) * b.style.speed();
                if (b.t >= 26) {
                    b.state = b == me && kD ? State.CROUCH : State.STAND;
                    b.wsUntil = ticks + 12;
                    b.t = 0;
                }
            }
            case BACKDASH -> {
                b.x -= b.facing * Math.max(0.1F, 1.2F - b.t * 0.035F) * b.style.speed();
                if (b.t >= 30) {
                    b.state = State.STAND;
                    b.t = 0;
                }
            }
            case SIDESTEP -> {
                b.depth += b.sideDir * Math.max(0.13F, 1.0F - b.t * 0.025F);
                if (b.t >= 24) {
                    boolean holding = b == me ? (b.sideDir > 0 ? kU : kD) : b.buffered;
                    b.state = holding ? State.SIDEWALK : State.STAND;
                    b.t = 0;
                }
            }
            case SIDEWALK -> {
                b.depth += b.sideDir * 0.37F;
                // circling round them: the camera follows
                spin += b.sideDir * 0.53F * b.facing * (b == me ? 1 : -1);
                b.depth = Mth.clamp(b.depth, -30, 30);
                if (b != me && !b.buffered) b.state = State.STAND;
            }
            case BLOCK, HIT -> {
                if (--b.stun <= 0) {
                    b.state = State.STAND;
                    b.crumple = false;
                }
                b.x += b.vx;
                b.vx *= SLIDE;
            }
            case SPLAT -> { // (stuck to the wall a moment - then sliding down it)
                if (--b.stun <= 0) {
                    b.state = State.DOWN;
                    b.stun = 70;
                    b.t = 0;
                    b.y = 0;
                    b.wallHits = 0;
                    b.screwed = false;
                    b.airHits = 0;
                    b.techable = false;
                }
            }
            case DOWN -> { // (lying there: get up with a direction, a kick, or after a while on its own)
                if (--b.stun <= 0) {
                    b.state = State.RISE;
                    b.stun = 20;
                }
            }
            case RISE -> {
                if (--b.stun <= 0) b.state = State.STAND;
            }
            case KI -> { // (charging up: wide open, then the next hit can't be blocked)
                if (b.t >= 40) {
                    b.state = State.STAND;
                    b.ki = true;
                    call("CHARGED", b.x, GROUND - 46);
                }
            }
            case THROWN -> {
                if (--b.stun <= 0) {
                    Body by = b.thrower;
                    b.breakWith = -1;
                    b.thrower = null;
                    land(b, by, b.grab != null ? b.grab : THROW_L);
                    b.grab = null;
                }
            }
            case ATTACK -> {
                Move m = b.move;
                if (m == null) {
                    b.state = State.STAND;
                } else if (b.queued != null && b.t >= m.startup() + m.active() + 2) { // (a string: straight into its next hit)
                    Move q = b.queued;
                    b.queued = null;
                    attack(b, q, true);
                } else if (b.t >= m.total()) {
                    // a crouching move: still crouched after, so standing up from it is while-standing too
                    if (m.is(DUCK)) b.wsUntil = b == me && kD ? -100 : ticks + 12;
                    if (b == me && b.landed == false && b.hitDone == false && practice > 0) info(m, "WHIFF");
                    b.state = b == me && kD ? State.CROUCH : State.STAND;
                    b.move = null;
                    b.t = 0;
                } else {
                    moveAlong(b, m);
                }
            }
            default -> {}
        }
    }

    /** Some moves carry you along while they come out. */
    private void moveAlong(Body b, Move m) {
        if (m == TACKLE || m == RAGE_ART || m == LUNGE_PUNCH || m == IRON_PALM || m == BURNING_FIST || m == INFERNO
                || m == PISTON || m == DEVIL_RUSH || m == HEAT_RUSH) {
            b.x += b.facing * (b.t < 12 ? 0.83F : 0.1F);
        } else if (m == CAT_SLIDE && b.t < m.startup() + m.active()) {
            b.x += b.facing * 1.2F;
        } else if (m == HOPKICK && b.t == 3) { // (a hop up off the floor - over any low)
            b.vy = 1.07F;
            b.y = 0.01F;
            b.vx = b.facing * 0.27F;
        } else if (m == BACKFLIP && b.t == 3) {
            b.vy = 1.2F;
            b.y = 0.01F;
            b.vx = -b.facing * 0.2F;
        } else if ((m == DRAGON_UPPER || m == PERFECT_DRAGON) && b.t < m.startup()) {
            b.x += b.facing * 0.4F;
        }
    }

    private void physics(Body b) {
        if (b.airborne()) {
            b.y += b.vy;
            b.x += b.vx;
            b.vy -= b.state == State.AIR ? GRAVITY_AIR : GRAVITY;
            if (b.state == State.AIR) b.vx *= 0.96F; // (a juggle's carry dies off: you can dash in after it)
            if (b.y <= 0) {
                b.y = 0;
                b.vy = 0;
                b.vx = 0;
                if (b.state == State.AIR) { // (a juggle ends on the floor)
                    b.state = State.DOWN;
                    b.stun = 70;
                    b.t = 0;
                    b.airHits = 0;
                    b.wallHits = 0;
                    b.screwed = false;
                    shake = Math.max(shake, 3);
                } else if (b.state == State.JUMP || b.state == State.ATTACK && b.move == AIR_KICK) {
                    b.state = State.STAND;
                    b.move = null;
                }
            }
        }
    }

    private void jump(Body b, int dir) {
        if (b.airborne() || !b.free()) return;
        b.state = State.JUMP;
        b.t = 0;
        b.vy = 1.67F;
        b.y = 0.01F;
        b.vx = dir * b.facing * 0.43F * b.style.speed();
    }

    private void dash(Body b) {
        if ((!b.free() && b.state != State.CROUCH_DASH) || b.airborne()) return;
        b.state = State.DASH;
        b.t = 0;
    }

    private void backdash(Body b) {
        boolean chain = b.state == State.BACKDASH && b.t >= 14; // (cancelled into the next: chained backdashes)
        if ((!b.free() && !chain) || b.airborne()) return;
        b.state = State.BACKDASH;
        b.t = chain ? 4 : 0;
    }

    /** Forward, neutral, down(-forward): a crouch dash (RIKU's). */
    private void crouchDash(Body b) {
        if ((!b.free() && b.state != State.DASH) || b.airborne()) return;
        b.state = State.CROUCH_DASH;
        b.t = 0;
    }

    /** Hitting the floor and rolling straight back up (to the side), not lying there to be hit. */
    private void tech(Body b) {
        if (b.state != State.DOWN || b.t > 10 || !b.techable) return;
        b.state = State.RISE;
        b.stun = 14;
        b.depth += (random.nextBoolean() ? 1 : -1) * 10;
        call("TECH", b.x, GROUND - 30);
    }

    /** Getting up off the floor with a roll (back, or to the side). */
    private void roll(Body b, int side) {
        if (b.state != State.DOWN || b.t < 12) return;
        b.state = State.RISE;
        b.stun = 18;
        if (side == 0) b.x -= b.facing * 10;
        else b.depth += side * 12;
    }

    private void sidestep(Body b, int dir) {
        if ((!b.free() && b.state != State.CROUCH) || b.airborne()) return;
        b.state = State.SIDESTEP;
        b.sideDir = dir;
        b.t = 0;
    }

    private void kiCharge(Body b) {
        if (!b.free() || b.airborne() || b.ki) return;
        b.move = null;
        b.state = State.KI;
        b.t = 0;
    }

    // ================= attacking =================

    private void attack(Body b, Move m) {
        attack(b, m, false);
    }

    private void attack(Body b, Move m, boolean chained) {
        if (b.state == State.ATTACK && !chained) return; // (mid-move: only the next hit of a string - see chain())
        boolean from = b.free() || b.state == State.DASH || b.state == State.BACKDASH || b.state == State.SIDESTEP
                || b.state == State.CROUCH_DASH || (b.state == State.JUMP && m == AIR_KICK)
                || ((b.state == State.DOWN || b.state == State.RISE) && (m == GETUP_KICK || m == GETUP_LOW));
        if (!chained && !from) return;
        if (m == RAGE_ART || isRageDrive(m)) {
            if (!b.rage || b.rageUsed) return;
            b.rageUsed = true;
            b.rage = false;
            if (m == RAGE_ART) {
                say(b.style.name() + " RAGE ART!", 30);
                shake = 8;
            } else {
                call("RAGE DRIVE", b.x, GROUND - 52);
            }
        }
        if (m.is(CRUSH)) call("POWER CRUSH", b.x, GROUND - 48);
        if (m.is(ELECTRIC) && b == me) call(m == PERFECT_DRAGON ? "PERFECT!" : "", b.x, GROUND - 56);
        b.state = State.ATTACK;
        b.move = m;
        b.t = 0;
        b.hitDone = false;
        b.landed = false;
        b.serial++;
    }

    private static boolean isRageDrive(Move m) {
        return m == DEVIL_RUSH || m == NINE_LIVES || m == OVERDRIVE || m == INFERNO;
    }

    /** A button mid-move: the next hit of its string (1,1,2  1,2,4  1,2,3  3,4), queued in time. */
    private void chain(Body b, int button) {
        Move cur = b.move;
        if (b.state != State.ATTACK || cur == null || cur.next() == null || b.t < 4) return;
        for (Move n : cur.next()) {
            if (n.button() != button) continue;
            b.queued = n;
            if (b.t >= cur.startup() + cur.active() + 2) { // (late in it: straight away)
                b.queued = null;
                attack(b, n, true);
            }
            return;
        }
    }

    /** Practice: what your move did, in frames. */
    private void info(Move m, String how) {
        frameInfo = m.name() + "  i" + m.startup() + "  " + how;
        frameInfoFor = 150;
    }

    private static String plus(int f) {
        return f > 0 ? "+" + f : Integer.toString(f);
    }

    /** Its attack's active frames: does it reach them - at the right height, and on the line or homing? */
    private void strike(Body b, Body o) {
        if (b.state != State.ATTACK || b.hitDone || b.move == null) return;
        Move m = b.move;
        if (b.t < m.startup() || b.t >= m.startup() + m.active()) return;
        float reach = (o.x - b.x) * b.facing;
        if (reach < -4 || reach > m.reach()) return;
        if (Math.abs(o.y - b.y) > 30) return;
        // off the line: a straight attack misses (a homing one turns to follow)
        if (Math.abs(o.depth - b.depth) > 7 && !m.is(HOMING)) return;
        if (o.state == State.KO || o.state == State.RISE) return;
        if (o.state == State.DOWN && !(m.level() == Level.LOW || m.is(GROUND_HIT))) return; // (only some moves hit someone lying down)
        if (o.state == State.THROWN) return;
        if (m.level() == Level.THROW) {
            if (o.crouching() || o.airborne() || o.down() || o.state == State.HIT || o.state == State.AIR || o.state == State.SPLAT) return;
            b.hitDone = true;
            o.state = State.THROWN;
            o.thrower = b;
            o.stun = 54;
            o.breakWith = m.is(BREAK_BOTH) ? 2 : m == THROW_L ? 0 : 1;
            o.grab = m;
            o.t = 0;
            o.ki = false;
            return;
        }
        if (m.level() == Level.HIGH && o.crouching()) return; // (ducked)
        if (m.level() == Level.LOW && o.hopping()) return; // (hopped over)
        boolean juggled = o.state == State.AIR || o.state == State.SPLAT;
        // a low parry: down-forward just as a low comes in - it's knocked up off its feet instead
        if (m.level() == Level.LOW && !juggled && ticks - o.parryAt <= 8
                && (o.state == State.STAND || o.state == State.CROUCH || o.state == State.BLOCK)) {
            b.hitDone = true;
            b.state = State.AIR;
            b.move = null;
            b.vy = 0.93F;
            b.vx = -b.facing * 0.2F;
            b.y = 1.0F;
            b.airHits = 0;
            o.state = State.STAND;
            o.parryAt = -100;
            shake = 3;
            spark(o.x + o.facing * 6, GROUND - o.y - 4, 8);
            call("LOW PARRY!", o.x, GROUND - 48);
            return;
        }
        b.hitDone = true;
        // a power crush coming out: soaks up a high or a mid (still hurts) and keeps going
        if (o.state == State.ATTACK && o.move != null && o.move.is(CRUSH) && o.t < o.move.startup() + o.move.active()
                && m.level() != Level.LOW) {
            int soak = Math.max(1, Math.round(m.damage() * b.style.power()));
            o.hp = Math.max(0, o.hp - soak);
            spark(o.x - o.facing * 4, GROUND - o.y - 20, 6);
            flash = 2;
            return;
        }
        // (no guard: ki charged, an unblockable, practice's dummy - or walking in, as the cabinet's fighter does)
        boolean guard = !o.ki && !m.is(UNBLOCKABLE) && !b.ki && !(o == foe && practice == 1) && !o.advancing;
        boolean blocked;
        if (juggled || o.state == State.DOWN || !guard) {
            blocked = false;
        } else if (m.level() == Level.LOW) {
            blocked = (o.state == State.CROUCH || o.state == State.BLOCK && o.blockLow) && (o == me ? kD : true);
        } else {
            blocked = !o.crouching() && (o.state == State.STAND || o.state == State.BLOCK) && !(o == me && fwdHeld(o));
        }
        int left = b.left();
        if (blocked) {
            o.state = State.BLOCK;
            o.blockLow = m.level() == Level.LOW;
            o.blockedLow = o.blockLow;
            o.stun = Math.max(6, left + m.onBlock()); // (so it's as far ahead or behind as its frame data says)
            o.vx = -o.facing * 0.67F;
            spark(o.x - o.facing * 4, GROUND - o.y - (m.level() == Level.LOW ? 6 : 20), 6);
            if (b == me && practice > 0) info(m, "BLOCK " + plus(m.onBlock()));
            return;
        }
        // a hit: a counter hit if they were in the middle of starting something (or dashing about)
        boolean counter = o.state == State.ATTACK && o.t < (o.move == null ? 0 : o.move.startup() + o.move.active())
                || o.state == State.DASH || o.state == State.BACKDASH || o.state == State.CROUCH_DASH || o.state == State.KI;
        float scale = (b.rage ? 1.1F : 1.0F) * (counter ? 1.2F : 1.0F) * b.style.power() * (b.ki ? 1.2F : 1.0F);
        // juggles hurt less the longer they go (less again once screwed, and at the wall)
        if (juggled) scale *= Math.max(0.3F, 0.85F - o.airHits * 0.06F) * (o.screwed ? 0.85F : 1.0F) * (o.wallHits > 0 ? 0.8F : 1.0F);
        if (o.state == State.DOWN) scale *= 0.5F;
        int dmg = Math.max(1, Math.round(m.damage() * scale));
        o.hp = Math.max(0, o.hp - dmg);
        b.landed = true;
        if (b.ki) {
            b.ki = false;
            call("UNBLOCKABLE", o.x, GROUND - 50);
        }
        o.ki = false;
        boolean inCombo = o.state == State.HIT || juggled || o.state == State.DOWN;
        b.combo = inCombo ? b.combo + 1 : 1;
        b.comboDmg = inCombo ? b.comboDmg + dmg : dmg;
        if (b.combo >= 2) b.comboShow = 150;
        if (b == me && practice == 0) score += dmg * 10 * Math.max(1, b.combo);
        if (counter) {
            call("COUNTER HIT", o.x, GROUND - o.y - 44);
            lastCounter = true;
            flash = 3;
        }
        if (b == me && practice > 0) info(m, m.is(KND) || m.launch() > 0 ? "HIT (DOWN)" : "HIT " + plus(m.onHit() + (counter ? 4 : 0)));
        if (m.is(ELECTRIC)) lastCounter = true; // (a blue spark: it crackles)
        spark(b.x + b.facing * m.reach() * 0.8F, GROUND - b.y - (m.level() == Level.LOW ? 4 : m.level() == Level.HIGH ? 28 : 18), 9);
        o.techable = !(m.is(KND) && m.level() == Level.LOW); // (swept off your feet: no tech roll)
        o.crumple = false;
        if (o.state == State.SPLAT) {
            // against the wall: it holds them there a little less each hit; a big one bounces them off it
            o.wallHits++;
            if (m.is(KND) || m.is(CARRY) || m.launch() > 0 || o.wallHits > 4) {
                o.state = State.AIR;
                o.vy = 0.8F;
                o.vx = -o.facing * 0.2F;
                o.y = Math.max(o.y, 4.0F);
            } else {
                o.stun = Math.max(12, 40 - o.wallHits * 7);
            }
        } else if (m.is(CARRY) && (juggled || m.is(KND) || counter)) {
            // a big one: they're sent flying off - into the wall, if it's near (a wall splat)
            o.state = State.AIR;
            o.airHits++;
            o.vy = juggled ? 0.67F : 0.87F;
            o.vx = b.facing * 1.4F;
            o.y = Math.max(o.y, 1.0F);
            shake = Math.max(shake, 4);
        } else if (juggled && m.is(SCREW) && !o.screwed) {
            // the screw: spun round and carried along low - the juggle goes on, once a combo
            o.state = State.AIR;
            o.screwed = true;
            o.airHits++;
            o.vy = 1.25F;
            o.vx = b.facing * 0.45F;
            o.y = Math.max(o.y, 4.0F);
            o.t = 0;
            shake = Math.max(shake, 4);
            call("SCREW!", o.x, GROUND - o.y - 40);
        } else if (juggled) { // (keeps them up: the juggle goes on, each hit lifting them less)
            o.state = State.AIR;
            o.airHits++;
            o.vy = Math.max(o.vy, (o.screwed ? 0.63F : 0.87F) - o.airHits * 0.083F);
            o.vx = b.facing * (o.screwed ? 0.2F : 0.15F);
            o.y = Math.max(o.y, 1.0F);
        } else if (m.launch() > 0 || counter && m.is(CH_LAUNCH)) { // (launched)
            o.state = State.AIR;
            o.vy = m.launch() > 0 ? m.launch() : 1.8F;
            o.vx = b.facing * 0.05F;
            o.y = 1.0F;
            o.airHits = 0;
            o.screwed = false;
            o.t = 0;
            shake = 4;
            call("LAUNCH!", o.x, GROUND - 52);
        } else if (m.is(KND) || o.airborne()) {
            land(o, b, m);
        } else if (o.state == State.DOWN) {
            o.stun = Math.max(o.stun, 24);
        } else {
            o.state = State.HIT;
            o.stun = Math.max(6, left + m.onHit() + (counter ? 4 : 0)); // (as far ahead as its frame data says)
            o.vx = b.facing * 0.8F;
            o.t = 0;
            // a counter-hit spin fist or mid check: a crumple (long enough for a launcher)
            if (counter && m.is(CH_CRUMPLE)) {
                o.stun += 36;
                o.crumple = true;
                o.vx = 0;
                call("CRUMPLE", o.x, GROUND - 36);
            }
        }
    }

    /** Knocked to the floor (thrown, swept, tackled ...). */
    private void land(Body o, Body by, Move m) {
        if (m != null && m.level() == Level.THROW) {
            o.hp = Math.max(0, o.hp - Math.round(m.damage() * (by != null ? by.style.power() : 1.0F)));
            if (by != null) {
                o.x = by.x - by.facing * 16; // (thrown over their shoulder)
                if (by == me && practice == 0) score += 300;
                by.combo = 1;
                by.comboDmg = m.damage();
            }
            o.techable = false; // (thrown down hard: no tech roll)
            shake = 6;
        }
        o.state = State.DOWN;
        o.stun = 70;
        o.vx = 0;
        o.vy = 0;
        o.y = 0;
        o.t = 0;
        shake = Math.max(shake, 4);
    }

    private void spark(float x, float y, int twentieths) {
        sparks.add(new float[] {x, GROUND - y, twentieths, lastCounter ? 1 : 0}); // (along, height up; how long; a counter hit's)
        lastCounter = false;
    }

    private void call(String text, float x, float y) {
        if (!text.isEmpty()) callouts.add(new Object[] {text, x, y, 84});
    }

    private void finishRound(Body winner, String how) {
        roundOver = 270;
        winner.wins++;
        Body loser = winner == me ? foe : me;
        if (loser.hp <= 0) {
            loser.state = State.KO;
            loser.y = 0;
            shake = 12;
            slowmo = 120;
            flash = 4;
        }
        winner.state = State.WIN;
        boolean perfect = winner.hp == MAX_HP;
        say(how + "  " + (winner == me ? "YOU WIN" : foe.style.name() + " WINS") + (perfect ? " - PERFECT" : ""), 90);
        if (winner == me) score += 300 + me.hp * 3 + (perfect ? 2000 : 0) + clock / FPS * 10;
    }

    private void endRound() {
        if (me.wins >= 2) {
            ladder++;
            score += 1500 * ladder;
            if (ladder % (FIGHTERS.length - 1) == 0) {
                lap++;
                score += 5000;
            }
            newMatch();
            say((ladder % (FIGHTERS.length - 1) == 0 ? "CHAMPION! NEXT: " : "NEXT: ") + foe.style.name(), 70);
            roundOver = -210;
        } else if (foe.wins >= 2) {
            over = true;
        } else {
            newRound();
        }
    }

    // ================= the cabinet's fighter =================

    /** Its best answer to having this many frames before you can block again: a launcher if there's time, else 1,2. */
    private Move punisher(Body b, int frames, boolean low) {
        if (frames >= 15) {
            if (low) return RISING_UPPER;
            if (b.who == 0 && frames >= 15) return DRAGON_UPPER;
            return LAUNCHER;
        }
        if (frames >= 12 && low) return RISING_KICK;
        if (frames >= 10) return low ? CROUCH_JAB : JAB;
        return null;
    }

    private void cpu() {
        Body b = foe, o = me;
        b.advancing = false;
        int d = Math.min(difficulty(), 6);
        float dist = Math.abs(o.x - b.x);
        int react = Math.max(9, 22 - d * 2); // (how quick it sees what you're doing, in frames)
        if (b.state == State.THROWN) { // (breaking a throw: the right buttons, in time)
            if (b.t == Math.max(6, 18 - d * 2) && random.nextFloat() < 0.2F + 0.1F * d) breakThrow(b);
            return;
        }
        if (b.state == State.DOWN) {
            if (b.t == 4 && b.techable && random.nextFloat() < 0.25F + 0.1F * d) tech(b);
            else if (b.t == 20 && dist < 26 && random.nextFloat() < 0.15F) attack(b, random.nextBoolean() ? GETUP_KICK : GETUP_LOW);
            else if (b.t == 20 && random.nextFloat() < 0.5F + 0.08F * d) roll(b, random.nextInt(3) - 1);
            return;
        }
        // a juggle: jab, jab ... the screw ... dash in, the lunge punch to finish (into the wall if it's near)
        if (o.state == State.AIR && (b.free() || b.state == State.DASH)) {
            boolean falling = o.y < 26 && (o.vy < 0.3F || o.y < 12);
            if (o.screwed) {
                if (dist > 26 && b.state != State.DASH) dash(b);
                else if (dist <= 30 && o.y < 20) attack(b, b.who == 3 ? BURNING_FIST : LUNGE_PUNCH);
            } else if (dist < 26 && falling) {
                if (cpuJuggle >= 2 || o.airHits >= 3) {
                    attack(b, b.who == 1 ? SPIRAL_KICK : SPIN_FIST);
                    cpuJuggle = 0;
                } else if (random.nextFloat() < 0.6F + 0.1F * d) {
                    attack(b, JAB);
                    b.queued = random.nextFloat() < 0.5F + 0.1F * d ? JAB_2 : null;
                    cpuJuggle += 2;
                }
            } else if (dist >= 26 && b.state != State.DASH) {
                dash(b);
            }
            return;
        }
        if (o.state != State.AIR) cpuJuggle = 0;
        if (b.state == State.CROUCH_DASH) { // (out of a crouch dash: the dragon upper, or the hell sweep)
            if (b.t >= 10 && dist < 30) {
                boolean perfect = random.nextFloat() < 0.15F + 0.1F * d;
                attack(b, random.nextFloat() < 0.7F ? (perfect ? PERFECT_DRAGON : DRAGON_UPPER) : HELL_SWEEP);
            }
            return;
        }
        // blockstun just over: punish what it blocked (it knows how far behind you are)
        boolean wasBlocking = cpuWasBlocking;
        cpuWasBlocking = b.state == State.BLOCK;
        if (wasBlocking && b.free()) {
            boolean low = b.blockedLow;
            b.blockedLow = false;
            Move p = o.state == State.ATTACK ? punisher(b, o.left(), low) : null;
            if (p != null && dist < p.reach() + 4 && random.nextFloat() < 0.3F + 0.12F * d) {
                if (p == RISING_UPPER || p == RISING_KICK || p == CROUCH_JAB) b.state = State.STAND;
                attack(b, p);
                return;
            }
        }
        if (!b.free() || b.airborne()) return;
        b.buffered = b.state == State.SIDEWALK && random.nextFloat() < 0.98F;
        // crumpled: a free launcher
        if (o.state == State.HIT && o.crumple && dist < 26) {
            attack(b, b.who == 0 ? DRAGON_UPPER : LAUNCHER);
            return;
        }
        // you whiffed something slow: whiff punish
        if (o.state == State.ATTACK && o.move != null && !o.hitDone && o.t >= o.move.startup() + o.move.active()
                && o.left() >= 15 && dist < 30 && random.nextFloat() < 0.04F + 0.02F * d) {
            Move p = punisher(b, o.left(), false);
            if (p != null) {
                attack(b, p);
                return;
            }
        }
        if (o.state == State.DOWN && dist < 30 && random.nextFloat() < 0.02F) {
            attack(b, b.who == 2 ? GROUND_SLAM : random.nextBoolean() ? LOW_KICK : TOE_KICK);
            return;
        }
        // your move coming out: once it's seen it (in time), block it right, duck it, low-parry it, power-crush it,
        // step it, or back off
        if (o.state == State.ATTACK && o.move != null && o.serial != cpuSeen && o.t >= react) {
            cpuSeen = o.serial;
            Level lv = o.move.level();
            boolean inTime = o.t < o.move.startup() + o.move.active();
            float r = random.nextFloat();
            if (inTime && dist < o.move.reach() + 12) {
                if (lv == Level.LOW && r < 0.1F + 0.04F * d) {
                    b.parryAt = ticks;
                    b.state = State.STAND;
                } else if (r < 0.1F + 0.05F * d && !o.move.is(HOMING) && lv != Level.THROW) {
                    sidestep(b, random.nextBoolean() ? 1 : -1);
                } else if (r < 0.55F + 0.08F * d) {
                    b.state = lv == Level.LOW || lv == Level.THROW || (lv == Level.HIGH && random.nextFloat() < 0.35F) ? State.CROUCH : State.STAND;
                    b.t = 0;
                } else if (r < 0.62F + 0.05F * d && lv != Level.LOW && o.move.next() != null) {
                    attack(b, b.who == 2 ? PISTON : b.who == 3 ? HEAT_RUSH : IRON_PALM);
                } else if (r < 0.68F + 0.04F * d) {
                    backdash(b);
                }
                return;
            }
        }
        // its own guard: mostly standing (it can't see a fast low coming - it guesses, now and then)
        if (b.state == State.CROUCH && b.t > 20 && random.nextFloat() < 0.08F) b.state = State.STAND;
        if (b.state == State.STAND && dist < 40 && random.nextFloat() < 0.002F * (d + 1)) {
            b.state = State.CROUCH;
            b.t = 0;
        }
        if (--thinkIn > 0) {
            b.advancing = b.state == State.STAND && dist > 44;
            if (b.advancing) b.x += b.facing * 0.33F * b.style.speed();
            else if (b.state == State.STAND && dist < 24 && random.nextFloat() < 0.3F) b.x -= b.facing * 0.25F * b.style.speed();
            return;
        }
        thinkIn = Math.max(8, 30 - d * 4) + random.nextInt(12);
        float r = random.nextFloat();
        if (b.rage && !b.rageUsed && dist < 32 && r < 0.2F) {
            attack(b, random.nextBoolean() ? RAGE_ART : rageDrive(b));
        } else if (dist > 70) {
            if (r < 0.4F) dash(b);
            else if (r < 0.5F) sidestep(b, random.nextBoolean() ? 1 : -1);
            else b.state = State.STAND;
            b.buffered = r < 0.25F;
        } else if (dist > 32) {
            if (r < 0.2F) dash(b);
            else if (r < 0.3F && b.who == 0) crouchDash(b);
            else if (r < 0.4F) attack(b, HOMING_KICK);
            else if (r < 0.5F) sidestep(b, random.nextBoolean() ? 1 : -1);
            else if (r < 0.58F) backdash(b);
            else if (r < 0.64F) attack(b, b.who == 3 ? BURNING_FIST : b.who == 1 ? CAT_SLIDE : LUNGE_PUNCH);
            else if (r < 0.66F && !b.ki && d >= 2) kiCharge(b);
            else b.state = State.STAND;
        } else {
            if (r < 0.14F) attack(b, JAB);
            else if (r < 0.22F) attack(b, MID_CHECK);
            else if (r < 0.30F) attack(b, MID_KICK);
            else if (r < 0.38F) attack(b, random.nextBoolean() ? LOW_KICK : TOE_KICK);
            else if (r < 0.42F) attack(b, b.who == 0 ? HELL_SWEEP : SWEEP);
            else if (r < 0.49F) attack(b, b.who == 2 && random.nextBoolean() ? IRON_GRAB : random.nextBoolean() ? THROW_L : THROW_R);
            else if (r < 0.54F) attack(b, LAUNCHER);
            else if (r < 0.59F) attack(b, b.who == 3 ? FLAME_HEEL : random.nextBoolean() ? SPIN_FIST : HIGH_KICK);
            else if (r < 0.63F) attack(b, b.who == 1 ? BACKFLIP : HOPKICK);
            else if (r < 0.68F) attack(b, STEP_KICK);
            else if (r < 0.76F) backdash(b);
            else if (r < 0.84F) sidestep(b, random.nextBoolean() ? 1 : -1);
            else {
                b.state = random.nextBoolean() ? State.CROUCH : State.STAND;
                b.t = 0;
            }
        }
        // strings: carry on with the next hits
        if (b.state == State.ATTACK && b.move == JAB && random.nextFloat() < 0.5F + 0.1F * d) {
            b.queued = random.nextBoolean() ? CROSS : JAB_2;
        } else if (b.state == State.ATTACK && b.move == MID_KICK && random.nextFloat() < 0.3F + 0.1F * d) {
            b.queued = WHIRL_KICK;
        }
    }

    /** Practice's dummy: stands there, or blocks everything. */
    private void dummy() {
        Body b = foe, o = me;
        if (b.state == State.THROWN || b.state == State.DOWN) {
            if (b.state == State.DOWN && b.t == 30) roll(b, 0);
            return;
        }
        if (practice != 2 || !b.free()) return;
        if (o.state == State.ATTACK && o.move != null) {
            b.state = o.move.level() == Level.LOW ? State.CROUCH : State.STAND;
        } else if (b.state == State.CROUCH && o.state != State.ATTACK) {
            b.state = State.STAND;
        }
    }

    private static Move rageDrive(Body b) {
        return switch (b.who) {
            case 1 -> NINE_LIVES;
            case 2 -> OVERDRIVE;
            case 3 -> INFERNO;
            default -> DEVIL_RUSH;
        };
    }

    private void breakThrow(Body b) {
        Body by = b.thrower;
        b.state = State.STAND;
        b.stun = 0;
        b.thrower = null;
        b.breakWith = -1;
        if (by != null) {
            by.state = State.STAND;
            by.move = null;
            by.x -= by.facing * 10;
        }
        b.x -= b.facing * 10;
        call("BREAK!", b.x, GROUND - 46);
    }

    // ================= keys =================

    /** Which way's held, as the numpad (relative to its facing): 1 down-back, 2 down, 3 down-forward, 4 back, 5 neutral,
     *  6 forward, 7 up-back, 8 up, 9 up-forward. */
    private int dir(Body b) {
        boolean f = (kR && b.facing > 0) || (kL && b.facing < 0), bk = (kL && b.facing > 0) || (kR && b.facing < 0);
        int row = kD ? 0 : kU ? 2 : 1, col = f ? 2 : bk ? 0 : 1;
        return row * 3 + col + 1;
    }

    /** The way held just changed: remember it (for motions), and see what motion it's finished. */
    private void newDir() {
        if (me == null) return;
        int d = dir(me);
        if (!dirs.isEmpty() && dirs.get(dirs.size() - 1)[0] == d) return;
        dirs.add(new int[] {d, ticks});
        while (dirs.size() > 8) dirs.remove(0);
        if (roundOver != 0) return;
        Body b = me;
        if (d == 3) dfAt = ticks;
        if (motion(15, 6, 5, 6)) dash(b); // f,f
        else if (motion(15, 4, 5, 4) || (b.state == State.BACKDASH && d == 4)) backdash(b); // b,b (and again: chained)
        if (b.who == 0 && (motion(18, 6, 5, 2) || motion(18, 6, 5, 3) || motion(18, 6, 5, 2, 3))) crouchDash(b); // f,n,d,df
        if (motion(20, 2, 3, 6)) qcfAt = ticks; // d,df,f
    }

    /** The last few ways held were these, in this order, all within so many frames. */
    private boolean motion(int window, int... seq) {
        int n = dirs.size();
        if (n < seq.length) return false;
        for (int i = 0; i < seq.length; i++) {
            if (dirs.get(n - seq.length + i)[0] != seq[i]) return false;
        }
        return ticks - dirs.get(n - seq.length)[1] <= window + 4 && ticks - dirs.get(n - seq.length + 1)[1] <= window;
    }

    private boolean together(int button, int other) {
        return held.contains(BUTTON_KEYS[other]) && ticks - pressedAt[other] <= 4 || other == button;
    }

    private static final int[] BUTTON_KEYS = {0, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K};

    @Override
    public void keyPressed(int key) {
        boolean repeat = !held.add(key); // (a held key repeating: not a new press)
        if (picking) {
            if (repeat) return;
            if (left(key)) pick = (pick + FIGHTERS.length - 1) % FIGHTERS.length;
            if (right(key)) pick = (pick + 1) % FIGHTERS.length;
            if (key == GLFW.GLFW_KEY_J || key == GLFW.GLFW_KEY_U || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
                picking = false;
                newMatch();
            }
            return;
        }
        if (repeat) return;
        if (key == GLFW.GLFW_KEY_H) {
            showList = !showList;
            return;
        }
        if (key == GLFW.GLFW_KEY_P && me != null) { // (practice: off, the dummy stands, it blocks, it fights)
            practice = (practice + 1) % 4;
            frameInfo = "";
            say(practice == 0 ? "PRACTICE OFF" : practice == 1 ? "PRACTICE: DUMMY" : practice == 2 ? "PRACTICE: BLOCK ALL" : "PRACTICE: CPU", 30);
            me.hp = foe.hp = MAX_HP;
            if (practice == 0) {
                me.wins = foe.wins = 0;
                round = 0;
                newRound();
            }
            return;
        }
        if (showList) return;
        if (left(key)) kL = true;
        if (right(key)) kR = true;
        if (up(key)) kU = true;
        if (down(key)) kD = true;
        if (left(key) || right(key) || up(key) || down(key)) newDir();
        if (roundOver != 0 || me == null) return;
        Body b = me;
        if ((down(key) || left(key) || right(key)) && dir(b) == 3) b.parryAt = ticks; // (df just as a low comes in)
        if (up(key)) {
            upAt = ticks;
            if (b.state == State.DOWN) {
                roll(b, 1);
            } else if (kL || kR) { // (up with a direction: a jump that way - in a moment, so up-forward + 4 can hopkick)
                if (b.free() && !b.airborne()) {
                    jumpDir = fwdHeld(b) ? 1 : -1;
                    jumpAt = ticks + 8;
                }
            } else {
                sidestep(b, 1); // (a tap: a sidestep into the screen, straight away)
            }
        }
        if (key == GLFW.GLFW_KEY_SPACE && b.free() && !b.airborne()) { // (the jump key: straight up, or the way you're held)
            jumpDir = fwdHeld(b) ? 1 : backHeld(b) ? -1 : 0;
            jumpAt = ticks + 8;
        }
        if (down(key)) {
            downAt = ticks;
            if (b.state == State.DOWN) roll(b, -1);
        }
        if (b.state == State.DOWN && (left(key) || right(key))) roll(b, 0);
        int button = key == GLFW.GLFW_KEY_U ? 1 : key == GLFW.GLFW_KEY_I ? 2 : key == GLFW.GLFW_KEY_J ? 3 : key == GLFW.GLFW_KEY_K ? 4 : 0;
        if (button > 0) pressedAt[button] = ticks;
        if (b.state == State.THROWN) { // (breaking a throw: 1, 2 or 1+2 - whichever arm's grabbing you)
            boolean both = together(button, 1) && together(button, 2);
            boolean right = b.breakWith == 2 ? both : button == b.breakWith + 1;
            if (right && b.t <= 22) breakThrow(b);
            return;
        }
        if (key == GLFW.GLFW_KEY_L) {
            attack(b, RAGE_ART);
            return;
        }
        if (button == 0) return;
        // on the floor: a punch as you land rolls you up (a tech); a kick is a get-up kick (3 low, 4 mid)
        if (b.state == State.DOWN || b.state == State.RISE) {
            if (button <= 2) tech(b);
            else attack(b, button == 3 ? GETUP_LOW : GETUP_KICK);
            return;
        }
        int d = dir(b);
        // buttons together (pressed within a few frames, so the first may have already started): ki charge (all
        // four), throws, the rage art and rage drive, power crushes, command moves
        boolean fresh = b.state == State.ATTACK && b.t <= 4 && b.move != null;
        boolean p12 = together(button, 1) && together(button, 2) && (button == 1 || button == 2);
        Move pair = null;
        if (together(button, 1) && together(button, 2) && together(button, 3) && together(button, 4)) {
            b.state = fresh ? State.STAND : b.state;
            kiCharge(b);
            return;
        } else if (button == 1 && together(1, 3) || button == 3 && together(3, 1)) {
            pair = THROW_L;
        } else if (button == 2 && together(2, 4) || button == 4 && together(4, 2)) {
            pair = THROW_R;
        } else if (p12 && b.rage && !b.rageUsed && d == 3) {
            pair = RAGE_ART; // (df+1+2)
        } else if (p12 && b.rage && !b.rageUsed && rageDriveInput(b, d)) {
            pair = rageDrive(b);
        } else if (p12) {
            pair = pairMove(b, d);
        }
        if (pair != null) {
            if (fresh) {
                b.queued = null;
                b.state = State.STAND; // (the first button's move undone: the pair instead)
            }
            if (b.state != State.ATTACK) attack(b, pair);
            return;
        }
        // mid-move: the next hit of a string
        if (b.state == State.ATTACK) {
            chain(b, button);
            return;
        }
        // in the air: a jump kick
        if (b.state == State.JUMP) {
            if (button >= 3) attack(b, AIR_KICK);
            return;
        }
        // about to jump: up(-forward) + 4 is a hopkick instead (3: NEKO's backflip; a punch just stays on the floor)
        if (jumpAt >= 0) {
            jumpAt = -1;
            if (button == 4 || button == 3 && d == 9) {
                attack(b, HOPKICK);
                return;
            }
            d = 5;
        }
        attack(b, pickMove(b, d, button));
    }

    /** What 1+2 does with the way held: power crushes, command throws, slams. */
    private Move pairMove(Body b, int d) {
        boolean back = d == 1 || d == 4 || d == 7;
        return switch (b.who) {
            case 2 -> d == 3 ? IRON_GRAB : d == 6 ? PISTON : d == 2 ? GROUND_SLAM : back ? IRON_PALM : null;
            case 3 -> d == 6 && b.state == State.DASH ? HEAT_RUSH : back ? IRON_PALM : null;
            default -> back ? IRON_PALM : null;
        };
    }

    private boolean rageDriveInput(Body b, int d) {
        return switch (b.who) {
            case 2 -> d == 4 || d == 1 || d == 7; // (b+1+2)
            case 3 -> ticks - qcfAt <= 10; // (d,df,f+1+2)
            default -> d == 6; // (f+1+2)
        };
    }

    /** A button, with the way held (and what it's in the middle of): which move. */
    private Move pickMove(Body b, int d, int button) {
        // RIKU's crouch dash: 1 snap elbow, 2 dragon upper (2 on the very frame you hit df: the perfect one), 3/4 hell sweep
        if (b.state == State.CROUCH_DASH) {
            if (button == 2) return ticks - dfAt <= 1 ? PERFECT_DRAGON : DRAGON_UPPER;
            return button == 1 ? SNAP_ELBOW : HELL_SWEEP;
        }
        // BLAZE: d,df,f+2 - the burning fist
        if (b.who == 3 && button == 2 && ticks - qcfAt <= 10) return BURNING_FIST;
        // NEKO: out of a sidestep, 1 or 2 a claw swipe; dashing, 3 a slide; b+3 a backflip
        if (b.who == 1) {
            if ((b.state == State.SIDESTEP || b.state == State.SIDEWALK) && button <= 2) return CLAW_SWIPE;
            if ((b.state == State.DASH || b.state == State.RUN) && button == 3) return CAT_SLIDE;
            if (d == 4 && button == 3) return BACKFLIP;
        }
        if (b.who == 3 && d == 4 && button == 4) return FLAME_HEEL;
        if (ticks <= b.wsUntil && !kD && (b.state == State.STAND || b.state == State.CROUCH)) {
            b.state = State.STAND;
            return button == 1 ? RISING_JAB : button == 2 ? RISING_UPPER : button == 3 ? RISING_KNEE : RISING_KICK; // (while standing)
        }
        if ((b.state == State.DASH || b.state == State.RUN) && button == 2) return LUNGE_PUNCH;
        return switch (d) {
            case 1 -> button == 1 ? CROUCH_JAB : button == 2 ? CROUCH_STRAIGHT : button == 3 ? LOW_KICK : SWEEP;
            case 2 -> button == 1 ? CROUCH_JAB : button == 2 ? CROUCH_STRAIGHT : button == 3 ? LOW_KICK : TOE_KICK;
            case 3 -> button == 1 ? MID_CHECK : button == 2 ? LAUNCHER : button == 3 ? FRONT_KICK : STEP_KICK;
            case 4 -> button == 1 ? JAB : button == 2 ? SPIN_FIST : button == 3 ? MID_KICK : HIGH_KICK;
            case 6 -> button == 1 ? JAB : button == 2 ? LUNGE_PUNCH : button == 3 ? SPIRAL_KICK : HOMING_KICK;
            default -> button == 1 ? JAB : button == 2 ? CROSS : button == 3 ? MID_KICK : HIGH_KICK;
        };
    }

    @Override
    public void keyReleased(int key) {
        held.remove(key);
        if (left(key)) kL = false;
        if (right(key)) kR = false;
        if (up(key)) kU = false;
        boolean wasDown = down(key) && kD;
        if (down(key)) kD = false;
        if (left(key) || right(key) || up(key) || down(key)) newDir();
        if (wasDown && me != null && !picking && roundOver == 0 && me.state == State.CROUCH) {
            // a quick tap: a sidestep out of the screen; a held crouch let go: standing up - the while-standing moves
            if (ticks - downAt < 10) sidestep(me, -1);
            else me.wsUntil = ticks + 12;
        }
    }

    // ================= how they look: real 3D =================
    //
    // The fighters are jointed 3D figures (a skeleton posed every tick, smoothly, each bone a lit box) on a stone stage
    // under a sunset, drawn in perspective by a camera that sits side-on to the line between them, pulls back as they
    // part, and swings round as they step about - the stage turning under them, the mountains and temple behind sliding by.

    /** Pose: 0 lean, 1 drop, 2 shift, 3 hip near, 4 knee near, 5 hip far, 6 knee far, 7 shoulder near, 8 elbow near,
     *  9 shoulder far, 10 elbow far, 11 head tilt, 12 roll (the whole body pitched over), 13 yaw (turned). Degrees. */
    private static final int P = 14;
    private static final float[] GUARD = {6, 1.5F, 0, 18, -22, -14, -20, 35, 105, 25, 120, 0, 0, 0};

    /** Each fighter's colours (rgb): top, trousers, skin, gloves, shoes, hair, accent. */
    private static final int[][] LOOKS = {
            {0xEDEDF4, 0xE2E2EC, 0xE6B48C, 0xC82828, 0x3C2A20, 0x1E1919, 0xD22828},
            {0xFF78BE, 0x2A1E3C, 0xFAD7BE, 0xFFFFFF, 0xFF78BE, 0xFFF0FA, 0xFF4FA8},
            {0x7887A0, 0x505A6E, 0x96A5BE, 0x3C465A, 0x3C465A, 0x3C465A, 0x3CE6FF},
            {0xC83C1E, 0x282830, 0xDCAA82, 0x1E1E1E, 0x1E1E1E, 0xFF8C28, 0xFFC83C}};

    private record Quad(float[] xs, float[] ys, int[] colours, float depth, int palette) {}

    private final List<Quad> quads = new ArrayList<>();
    private final List<Quad> lastFrame = new ArrayList<>();
    private float camU = W / 2.0F, camV, camD = 160, oCamU = W / 2.0F, oCamD = 160, oSpin;
    private final List<float[]> dust = new ArrayList<>(); // u, h, v, life, du
    private int slowmo, flash;
    private boolean lastCounter;

    /** Per fighter: its pose now and a tick ago, its shown health (draining after a hit), its striking limb's trail. */
    private final java.util.Map<Body, float[][]> poses = new java.util.IdentityHashMap<>();
    private final java.util.Map<Body, float[]> shownHp = new java.util.IdentityHashMap<>();
    private final java.util.Map<Body, List<float[]>> trails = new java.util.IdentityHashMap<>();

    private float[][] poseOf(Body b) {
        return poses.computeIfAbsent(b, k -> new float[][] {GUARD.clone(), GUARD.clone()});
    }

    /** Every tick, after the fight's moved on: poses ease towards what they're doing, the camera follows, dust settles. */
    private void animate() {
        if (me == null) return;
        oCamU = camU;
        oCamD = camD;
        oSpin = spin;
        float sep = Math.abs(me.x - foe.x);
        camU += ((me.x + foe.x) / 2 - camU) * 0.091F; // (as quick a frame as a quarter a 20th)
        camV += ((me.depth + foe.depth) / 2 - camV) * 0.091F;
        camD += (Mth.clamp(sep * 0.8F + 95, 130, 240) - camD) * 0.053F;
        for (Body b : new Body[] {me, foe}) {
            b.ox = b.x;
            b.oy = b.y;
            b.oDepth = b.depth;
            float[][] p = poseOf(b);
            System.arraycopy(p[1], 0, p[0], 0, P);
            float[] want = target(b);
            boolean snap = b.state == State.ATTACK && b.move != null && b.t >= b.move.startup() - 4 && b.t <= b.move.startup() + b.move.active();
            float k = snap ? 0.54F : 0.28F; // (a frame's share of easing there: snapping out as a move hits)
            for (int i = 0; i < P; i++) {
                float d = want[i] - p[1][i];
                if (i == 12) d = Mth.wrapDegrees(d);
                p[1][i] += d * k;
            }
            float[] hp = shownHp.computeIfAbsent(b, x -> new float[] {MAX_HP});
            hp[0] = hp[0] > b.hp ? Math.max(b.hp, hp[0] - 0.35F) : b.hp;
            List<float[]> tr = trails.computeIfAbsent(b, x -> new ArrayList<>());
            boolean active = b.state == State.ATTACK && b.move != null && b.t >= b.move.startup() - 4 && b.t < b.move.startup() + b.move.active() + 3;
            if (active) {
                float[] tip = tip(b);
                if (tip != null) tr.add(tip);
            }
            while (tr.size() > (active ? 10 : 0)) tr.remove(0);
            if ((b.state == State.DASH || b.state == State.BACKDASH || b.state == State.RUN || b.state == State.CROUCH_DASH) && ticks % 9 == 0
                    || b.state == State.DOWN && b.t == 1) {
                for (int i = 0; i < 3; i++) {
                    dust.add(new float[] {b.x + (random.nextFloat() - 0.5F) * 10, 1, b.depth + (random.nextFloat() - 0.5F) * 8, 14,
                            -b.facing * random.nextFloat() * 0.2F});
                }
            }
        }
        dust.removeIf(d -> {
            d[0] += d[4];
            d[1] += 0.083F;
            return (d[3] -= 1 / 3.0F) <= 0;
        });
        if (flash > 0 && ticks % 3 == 0) flash--;
    }

    private void hold() {
        if (me == null) return;
        oCamU = camU;
        oCamD = camD;
        oSpin = spin;
        for (Body b : new Body[] {me, foe}) {
            b.ox = b.x;
            b.oy = b.y;
            b.oDepth = b.depth;
            float[][] p = poseOf(b);
            System.arraycopy(p[1], 0, p[0], 0, P);
        }
    }

    /** What its pose should be now. */
    private float[] target(Body b) {
        float[] p = GUARD.clone();
        float w = ticks / 3.0F * 0.55F;
        Move m = b.move;
        boolean windup = b.state == State.ATTACK && m != null && b.t < m.startup() - 1;
        boolean out = b.state == State.ATTACK && m != null && !windup && b.t < m.startup() + m.active() + 2;
        switch (b.state) {
            case CROUCH, RISE -> crouch(p);
            case CROUCH_DASH -> {
                crouch(p);
                p[0] = 32;
                p[1] = 7;
                p[7] = 30;
                p[9] = 25;
            }
            case DASH, RUN -> {
                float big = b.state == State.RUN ? 1.4F : 1.0F;
                p[0] = 20 * big;
                p[3] = 25 + 30 * Mth.sin(w * 1.6F) * big;
                p[5] = 25 - 30 * Mth.sin(w * 1.6F) * big;
                p[4] = -30 - 30 * Math.max(0, Mth.cos(w * 1.6F));
                p[6] = -30 - 30 * Math.max(0, -Mth.cos(w * 1.6F));
                p[7] = 20 - 30 * Mth.sin(w * 1.6F);
                p[9] = 20 + 30 * Mth.sin(w * 1.6F);
                p[8] = p[10] = 90;
            }
            case BACKDASH -> {
                p[0] = -10;
                p[3] = -10;
                p[4] = -30;
                p[5] = 25;
                p[6] = -45;
            }
            case SIDESTEP, SIDEWALK -> {
                p[13] = 40 * b.sideDir;
                p[3] = 18 + 12 * Mth.sin(w * 1.4F);
                p[5] = -14 - 12 * Mth.sin(w * 1.4F);
            }
            case JUMP -> {
                p[3] = 60;
                p[4] = -100;
                p[5] = 40;
                p[6] = -110;
                p[7] = 50;
                p[9] = 40;
            }
            case BLOCK -> {
                if (b.crouching() || (b == me && kD)) crouch(p);
                p[7] = 75;
                p[8] = 135;
                p[9] = 70;
                p[10] = 140;
                p[0] = 2;
                p[11] = -8;
            }
            case HIT -> {
                if (b.crumple) { // (crumpling: knees going, slumping forward)
                    crouch(p);
                    p[0] = 35;
                    p[11] = 30;
                    p[7] = 10;
                    p[8] = 20;
                    p[9] = 5;
                    p[10] = 15;
                    break;
                }
                p[0] = -20;
                p[11] = -25;
                p[7] = 15;
                p[8] = 30;
                p[9] = 5;
                p[10] = 25;
            }
            case AIR -> {
                // (screwed: spinning over and over, turned side-on)
                p[12] = b.screwed ? 60 + b.t * 10 : 40 + b.airHits * 30 + b.t * 2;
                if (b.screwed) p[13] = 55;
                p[7] = 130;
                p[8] = 20;
                p[9] = 150;
                p[10] = 10;
                p[3] = 30;
                p[4] = -20;
                p[5] = 10;
                p[6] = -50;
            }
            case DOWN, KO -> {
                p[12] = 88;
                p[0] = 0;
                p[3] = 5;
                p[4] = -5;
                p[5] = -5;
                p[6] = -15;
                p[7] = 140;
                p[8] = 10;
                p[9] = 160;
                p[10] = 20;
                p[11] = 0;
            }
            case THROWN -> {
                if (b.t > 24) {
                    p[12] = 88;
                } else {
                    p[0] = -15;
                    p[11] = -20;
                    p[7] = 60;
                    p[9] = 60;
                }
            }
            case SPLAT -> {
                p[0] = -14;
                p[7] = 95;
                p[8] = 0;
                p[9] = 100;
                p[10] = 0;
                p[3] = 6;
                p[5] = -6;
                p[11] = -15;
            }
            case KI -> { // (fists down at the sides, braced, charging up)
                p[1] = 3;
                p[0] = -4;
                p[3] = 28;
                p[5] = -24;
                p[7] = 15;
                p[8] = 70;
                p[9] = 15;
                p[10] = 70;
                p[11] = -12;
            }
            case WIN -> {
                p[7] = 170;
                p[8] = 10;
                p[9] = 30;
                p[10] = 100;
                p[0] = -5;
                p[11] = -10;
            }
            default -> {
                if (b.state == State.STAND && b == me && (kL || kR) || b.state == State.STAND && b != me && thinkIn > 0 && Math.abs(me.x - foe.x) > 40) {
                    p[3] = 18 + 14 * Mth.sin(w);
                    p[5] = -14 - 14 * Mth.sin(w);
                    p[4] = -22 - 18 * Math.max(0, Mth.cos(w));
                    p[6] = -20 - 18 * Math.max(0, -Mth.cos(w));
                } else {
                    p[1] += Mth.sin(ticks * 0.05F) * 0.6F; // (breathing, bouncing a little on its feet)
                }
            }
        }
        if (b.state == State.ATTACK && m != null) {
            switch (m.pose()) {
                case JAB -> {
                    p[0] = 10;
                    p[7] = out ? 88 : 50;
                    p[8] = out ? 4 : 80;
                }
                case CROSS -> {
                    p[0] = out ? 18 : 6;
                    p[2] = out ? 2 : 0;
                    p[9] = out ? 88 : 40;
                    p[10] = out ? 2 : 90;
                    p[13] = out ? -15 : 0;
                }
                case LOWPUNCH -> {
                    crouch(p);
                    p[7] = out ? 75 : 45;
                    p[8] = out ? 4 : 90;
                }
                case MIDKICK -> {
                    p[0] = out ? -12 : 0;
                    p[5] = out ? 90 : 40;
                    p[6] = out ? -5 : -90;
                    p[9] = 0;
                    p[10] = 40;
                }
                case HIGHKICK -> {
                    p[0] = out ? -24 : -4;
                    p[5] = out ? 125 : 50;
                    p[6] = out ? -8 : -100;
                    p[9] = -5;
                    p[10] = 30;
                    p[13] = out ? -20 : 0;
                }
                case LOWKICK -> {
                    p[1] = 4;
                    p[0] = 15;
                    p[3] = out ? 55 : 30;
                    p[4] = out ? -10 : -60;
                    p[5] = -20;
                    p[6] = -40;
                }
                case SWEEP -> {
                    p[1] = 12;
                    p[0] = 25;
                    p[3] = 60;
                    p[4] = -130;
                    p[5] = out ? 88 : 30;
                    p[6] = out ? 0 : -100;
                    p[7] = 15;
                    p[8] = 15;
                    p[9] = -10;
                    p[10] = 10;
                }
                case UPPER -> {
                    if (windup) {
                        crouch(p);
                        p[9] = 15;
                        p[10] = 60;
                    } else {
                        p[0] = -8;
                        p[2] = 2;
                        p[9] = out ? 165 : 90;
                        p[10] = 25;
                        p[13] = -15;
                    }
                }
                case THROW -> {
                    p[0] = 12;
                    p[7] = out ? 85 : 60;
                    p[8] = 20;
                    p[9] = out ? 85 : 60;
                    p[10] = 20;
                }
                case AIRKICK -> {
                    p[3] = out ? 95 : 50;
                    p[4] = out ? -5 : -90;
                    p[5] = -10;
                    p[6] = -100;
                    p[0] = -10;
                }
                case TACKLE -> {
                    p[0] = 35;
                    p[2] = 3;
                    p[7] = 60;
                    p[8] = 90;
                    p[9] = 60;
                    p[10] = 90;
                }
                case SPIN -> { // (turned right round, the back of the fist swung out)
                    p[13] = out ? -35 : 75;
                    p[0] = out ? 6 : -4;
                    p[9] = out ? 95 : 30;
                    p[10] = out ? 5 : 110;
                    p[11] = out ? 0 : 15;
                }
                case PALM -> { // (both palms driven out, low and heavy)
                    p[1] = 3;
                    p[0] = out ? 22 : -8;
                    p[2] = out ? 4 : 0;
                    p[3] = 30;
                    p[7] = out ? 85 : 30;
                    p[8] = out ? 0 : 110;
                    p[9] = out ? 82 : 30;
                    p[10] = out ? 0 : 110;
                }
                case RAGE -> {
                    p[0] = out ? 25 : -5;
                    p[2] = out ? 4 : 0;
                    p[9] = out ? 95 : -20;
                    p[10] = out ? 0 : 90;
                    p[13] = out ? -20 : 10;
                }
            }
        }
        return p;
    }

    private static void crouch(float[] p) {
        p[1] = 9;
        p[0] = 15;
        p[3] = 70;
        p[4] = -110;
        p[5] = 30;
        p[6] = -120;
        p[7] = 45;
        p[8] = 100;
        p[9] = 40;
        p[10] = 110;
    }

    // ---- the skeleton ----

    /** A bone: from a to b (local: forward, up, side - towards the camera negative), how thick, its colour and palette. */
    private record Bone(float[] a, float[] b, float t, float w, int rgb, int palette) {}

    private static float[] dir(float deg) {
        float r = deg * Mth.DEG_TO_RAD;
        return new float[] {Mth.sin(r), -Mth.cos(r)};
    }

    private static float[] at(float[] from, float[] d, float len, float side) {
        return new float[] {from[0] + d[0] * len, from[1] + d[1] * len, side};
    }

    /** Its bones, posed (local space, before it's turned and placed). */
    private List<Bone> skeleton(Body b, float[] p, int[] look, int kind) {
        List<Bone> out = new ArrayList<>();
        int top = look[0], legs = look[1], skin = look[2], gloves = look[3], shoes = look[4], hair = look[5], accent = look[6];
        int gi = b.style.gi(), sk = b.style.skin(), band = b.style.band(), hr = b.style.hair();
        float lean = p[0];
        float[] pelvis = {p[2], 18 - p[1]};
        float[] up = dir(180 - lean); // (leaning forward: its chest ahead of its hips)
        float[] neck = {pelvis[0] + up[0] * 13, pelvis[1] + up[1] * 13, 0};
        // legs (near: the camera's side, the lead leg)
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -2.8F : 2.8F;
            float hip = s == 0 ? p[3] : p[5], knee = s == 0 ? p[4] : p[6];
            float[] h = {pelvis[0], pelvis[1], side};
            float[] k = at(h, dir(hip), 9.5F, side);
            float[] an = at(k, dir(hip + knee), 9.0F, side);
            float[] toe = at(an, dir(hip + knee + 90), 4.0F, side);
            out.add(new Bone(h, k, 4.4F, 4.4F, legs, gi));
            out.add(new Bone(k, an, 3.6F, 3.6F, legs, gi));
            out.add(new Bone(new float[] {an[0] - (toe[0] - an[0]) * 0.2F, an[1] - (toe[1] - an[1]) * 0.2F, side}, toe, 2.4F, 3.2F, shoes, gi));
        }
        // the body: hips, chest (a belt between), neck, head
        out.add(new Bone(new float[] {pelvis[0] + up[0] * -1, pelvis[1] + up[1] * -1, 0}, new float[] {pelvis[0] + up[0] * 3, pelvis[1] + up[1] * 3, 0},
                6.4F, 9.0F, legs, gi));
        out.add(new Bone(new float[] {pelvis[0] + up[0] * 2.4F, pelvis[1] + up[1] * 2.4F, 0}, new float[] {pelvis[0] + up[0] * 4, pelvis[1] + up[1] * 4, 0},
                7.0F, 10.2F, accent, band));
        out.add(new Bone(new float[] {pelvis[0] + up[0] * 3.6F, pelvis[1] + up[1] * 3.6F, 0}, neck, 6.6F, 10.0F, top, gi));
        float[] hd = dir(180 - lean - p[11]);
        float[] headBase = {neck[0] + hd[0] * 1.2F, neck[1] + hd[1] * 1.2F, 0};
        float[] headTop = {neck[0] + hd[0] * 8.6F, neck[1] + hd[1] * 8.6F, 0};
        out.add(new Bone(neck, headBase, 2.8F, 2.8F, skin, sk));
        out.add(new Bone(headBase, headTop, 7.0F, 7.0F, skin, sk));
        // the face: eyes towards its front
        float[] fwd = dir(90 - lean - p[11]);
        for (float side : new float[] {-1.6F, 1.6F}) {
            float[] e = {headBase[0] + hd[0] * 4.4F + fwd[0] * 3.55F, headBase[1] + hd[1] * 4.4F + fwd[1] * 3.55F, side};
            out.add(new Bone(e, new float[] {e[0] + hd[0] * 1.2F, e[1] + hd[1] * 1.2F, side}, 0.3F, 0.9F, kind == 2 ? accent : 0x141018, 0));
        }
        // hair and what makes each one itself
        float[] crown = {headBase[0] + hd[0] * 6.2F - fwd[0] * 0.6F, headBase[1] + hd[1] * 6.2F - fwd[1] * 0.6F, 0};
        switch (kind) {
            case 0 -> { // a headband, its tails flying
                out.add(new Bone(crown, new float[] {headTop[0] + hd[0] * 0.8F, headTop[1] + hd[1] * 0.8F, 0}, 7.4F, 7.4F, hair, hr));
                float[] bandAt = {headBase[0] + hd[0] * 5.4F, headBase[1] + hd[1] * 5.4F, 0};
                out.add(new Bone(bandAt, new float[] {bandAt[0] + hd[0] * 1.1F, bandAt[1] + hd[1] * 1.1F, 0}, 7.5F, 7.5F, accent, band));
                float flap = Mth.sin(vt() * 0.4F) * 15;
                float[] tail = at(new float[] {bandAt[0] - fwd[0] * 3.6F, bandAt[1] - fwd[1] * 3.6F}, dir(-80 + flap), 6, 0);
                out.add(new Bone(new float[] {bandAt[0] - fwd[0] * 3.6F, bandAt[1] - fwd[1] * 3.6F, 0}, tail, 1.0F, 1.6F, accent, band));
            }
            case 1 -> { // cat ears and a tail
                out.add(new Bone(crown, new float[] {headTop[0] + hd[0] * 0.6F, headTop[1] + hd[1] * 0.6F, 0}, 7.4F, 7.4F, hair, hr));
                for (float side : new float[] {-2.4F, 2.4F}) {
                    float[] e0 = {headTop[0], headTop[1], side};
                    out.add(new Bone(e0, new float[] {e0[0] + hd[0] * 3.2F, e0[1] + hd[1] * 3.2F, side}, 2.4F, 1.6F, hair, hr));
                }
                float sway = Mth.sin(vt() * 0.2F) * 20;
                float[] t0 = {pelvis[0] - up[1] * 0 - 3.5F, pelvis[1] + 2, 0};
                float[] t1 = at(t0, dir(-120 + sway), 6, 0);
                float[] t2 = at(t1, dir(-170 + sway * 1.5F), 6, 0);
                out.add(new Bone(t0, t1, 1.8F, 1.8F, hair, hr));
                out.add(new Bone(t1, t2, 1.6F, 1.6F, hair, hr));
            }
            case 2 -> { // a visor, an antenna with a light on top
                float[] vis = {headBase[0] + hd[0] * 4.2F + fwd[0] * 3.4F, headBase[1] + hd[1] * 4.2F + fwd[1] * 3.4F, 0};
                out.add(new Bone(vis, new float[] {vis[0] + hd[0] * 1.4F, vis[1] + hd[1] * 1.4F, 0}, 0.6F, 6.4F, accent, band));
                float[] a0 = {headTop[0], headTop[1], 0};
                float[] a1 = {a0[0] + hd[0] * 4.5F, a0[1] + hd[1] * 4.5F, 0};
                out.add(new Bone(a0, a1, 0.7F, 0.7F, legs, gi));
                out.add(new Bone(a1, new float[] {a1[0] + hd[0] * 1.4F, a1[1] + hd[1] * 1.4F, 0}, 1.6F, 1.6F,
                        ((int) vt() / 8) % 2 == 0 ? 0xFF4040 : accent, hr));
            }
            default -> { // flames for hair
                out.add(new Bone(crown, new float[] {headTop[0] + hd[0] * 0.6F, headTop[1] + hd[1] * 0.6F, 0}, 7.4F, 7.4F, hair, hr));
                for (int i = 0; i < 4; i++) {
                    float flick = Mth.sin(vt() * 0.6F + i * 1.7F) * 1.2F;
                    float side = -2.4F + i * 1.6F;
                    float[] f0 = {headTop[0] - fwd[0] * (i % 2) * 1.5F, headTop[1] - fwd[1] * (i % 2) * 1.5F, side};
                    float[] f1 = at(f0, dir(195 + i * 4 - lean), 3.5F + flick + (i % 2), side);
                    out.add(new Bone(f0, f1, 1.8F, 1.4F, i % 2 == 0 ? hair : accent, hr));
                }
            }
        }
        // arms (near first: the lead hand)
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -5.6F : 5.6F;
            float sh = s == 0 ? p[7] : p[9], el = s == 0 ? p[8] : p[10];
            float[] sp = {neck[0] - up[0] * 1.6F, neck[1] - up[1] * 1.6F, side};
            float[] e = at(sp, dir(sh), 7.0F, side);
            float[] h = at(e, dir(sh + el), 6.5F, side);
            out.add(new Bone(sp, e, 3.4F, 3.4F, kind == 3 ? top : kind == 2 ? top : top, gi));
            out.add(new Bone(e, h, 3.0F, 3.0F, kind == 0 ? skin : top, kind == 0 ? sk : gi));
            float[] d = dir(sh + el);
            out.add(new Bone(h, new float[] {h[0] + d[0] * 3.4F, h[1] + d[1] * 3.4F, side}, 3.8F, 3.8F, gloves, band));
        }
        return out;
    }

    /** Where its striking hand or foot is now (stage space: along, up, depth), for its trail. */
    private float[] tip(Body b) {
        if (b.move == null) return null;
        float[] p = poseOf(b)[1];
        int s;
        boolean leg;
        switch (b.move.pose()) {
            case JAB, LOWPUNCH, THROW -> { s = 0; leg = false; }
            case CROSS, UPPER, RAGE, TACKLE, SPIN, PALM -> { s = 1; leg = false; }
            case LOWKICK, AIRKICK -> { s = 0; leg = true; }
            default -> { s = 1; leg = true; }
        }
        List<Bone> bones = skeleton(b, p, LOOKS[styleIndex(b.style)], b.style.kind());
        // (bones: legs are 0-5, arms the last six)
        Bone end = leg ? bones.get(s * 3 + 2) : bones.get(bones.size() - 6 + s * 3 + 2);
        return place(b, p, end.b());
    }

    private static int styleIndex(Style s) {
        for (int i = 0; i < FIGHTERS.length; i++) if (FIGHTERS[i] == s) return i;
        return 0;
    }

    /** A local point turned (pitched over, turned aside) and put where the fighter is: stage space (along, up, depth). */
    private float[] place(Body b, float[] p, float[] q) {
        float[] r = turn(p, q);
        float lift = b == curBody && p == curPose ? curLift : lift(b, p);
        return new float[] {b.x + r[0] * b.facing, b.y + r[1] + lift, b.depth + r[2]};
    }

    private static float[] turn(float[] p, float[] q) {
        float px = 0, py = 18 - p[1];
        float x = q[0] - px, y = q[1] - py, z = q.length > 2 ? q[2] : 0;
        float roll = p[12] * Mth.DEG_TO_RAD;
        float cr = Mth.cos(roll), sr = Mth.sin(roll);
        float x1 = x * cr - y * sr, y1 = x * sr + y * cr;
        float yaw = p[13] * Mth.DEG_TO_RAD;
        float cy = Mth.cos(yaw), sy = Mth.sin(yaw);
        float x2 = x1 * cy - z * sy, z2 = x1 * sy + z * cy;
        return new float[] {x2 + px, y1 + py, z2};
    }

    /** How far up or down to put it so its feet (or, lying, its back) are on the floor. */
    private float lift(Body b, float[] p) {
        boolean lying = b.state == State.DOWN || b.state == State.KO || b.state == State.THROWN && b.t > 24;
        boolean grounded = !b.airborne() && b.state != State.AIR && b.state != State.SPLAT;
        if (!grounded && !lying) return 0;
        float low = Float.MAX_VALUE;
        for (Bone bone : skeleton(b, p, LOOKS[0], 0)) {
            low = Math.min(low, Math.min(turn(p, bone.a())[1], turn(p, bone.b())[1]) - bone.t() / 2);
        }
        return low == Float.MAX_VALUE ? 0 : -low;
    }

    /** (the fighter being drawn, its pose, and how far it's lifted onto the floor: worked out once) */
    private Body curBody;
    private float[] curPose;
    private float curLift;

    // ---- the camera ----

    private float pt;

    /** Time for drawing, in 20ths of a second (smooth between frames): what the looks were made for. */
    private float vt() {
        return (ticks + pt) / 3.0F;
    }
    private float cu, cd, csp, horizon, camH;
    private static final float FL = 170;

    private void camera(float partial) {
        cu = Mth.lerp(partial, oCamU, camU);
        cd = Mth.lerp(partial, oCamD, camD);
        csp = Mth.lerp(partial, oSpin, spin);
        camH = 26 + cd * 0.1F;
        horizon = 122 - camH * FL / cd;
    }

    /** Stage space -> camera: (x, y, z: distance ahead). */
    private float[] view(float u, float h, float v) {
        return new float[] {u - cu, h, cd + (v - camV)};
    }

    /** Camera -> screen (field coordinates); null if it's behind the lens. */
    private float[] screen(float[] c) {
        if (c[2] < 4) return null;
        return new float[] {W / 2.0F + c[0] * FL / c[2], horizon + (camH - c[1]) * FL / c[2]};
    }

    private void quad(float[][] cam, int[] rgb, float alpha, int palette) {
        float[] xs = new float[4], ys = new float[4];
        float depth = 0;
        for (int i = 0; i < 4; i++) {
            float[] s = screen(cam[i]);
            if (s == null) return;
            xs[i] = s[0];
            ys[i] = s[1];
            depth = Math.max(depth, cam[i][2]);
        }
        int a = Mth.clamp((int) (alpha * 255), 0, 255) << 24;
        int[] cs = new int[4];
        for (int i = 0; i < 4; i++) cs[i] = a | (rgb[i] & 0xFFFFFF);
        quads.add(new Quad(xs, ys, cs, depth - (alpha < 1 ? 0.01F : 0), palette));
    }

    private static int shade(int rgb, float k, float rim) {
        int r = (int) Mth.clamp(((rgb >> 16) & 255) * k + 255 * rim * 0.9F, 0, 255);
        int g = (int) Mth.clamp(((rgb >> 8) & 255) * k + 170 * rim * 0.9F, 0, 255);
        int b = (int) Mth.clamp((rgb & 255) * k + 110 * rim * 0.9F, 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    /** A bone as a lit box: each side facing the camera, lit by the low sun from the left, a warm rim along its edges. */
    private void box(Body b, float[] p, Bone bone, float grow, float alpha, int tint) {
        float dx = bone.b()[0] - bone.a()[0], dy = bone.b()[1] - bone.a()[1];
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1e-3F) {
            dx = 0;
            dy = 1;
            len = 1;
        }
        dx /= len;
        dy /= len;
        float nx = -dy, ny = dx; // (across, in its own plane)
        float t = bone.t() / 2 + grow, w = bone.w() / 2 + grow;
        float[][] corner = new float[8][];
        for (int i = 0; i < 8; i++) {
            float[] end = (i & 1) == 0 ? bone.a() : bone.b();
            float along = (i & 1) == 0 ? -grow : grow;
            float st = (i & 2) == 0 ? -t : t, sw = (i & 4) == 0 ? -w : w;
            float[] local = {end[0] + dx * along + nx * st, end[1] + dy * along + ny * st, (end.length > 2 ? end[2] : 0) + sw};
            float[] s = place(b, p, local);
            corner[i] = view(s[0], s[1], s[2]);
        }
        float[] centre = new float[3];
        for (float[] c : corner) for (int k = 0; k < 3; k++) centre[k] += c[k] / 8;
        int[][] faces = {{0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}};
        float[] light = norm(new float[] {-0.45F, 0.8F, -0.4F});
        for (int[] fc : faces) {
            float[][] q = {corner[fc[0]], corner[fc[1]], corner[fc[2]], corner[fc[3]]};
            float[] n = norm(cross(sub(q[1], q[0]), sub(q[3], q[0])));
            float[] fcen = {(q[0][0] + q[2][0]) / 2, (q[0][1] + q[2][1]) / 2, (q[0][2] + q[2][2]) / 2};
            if (dot(n, sub(fcen, centre)) < 0) n = new float[] {-n[0], -n[1], -n[2]};
            float[] toCam = norm(new float[] {-fcen[0], camH - fcen[1], -fcen[2]});
            if (dot(n, toCam) <= 0.0F) continue;
            float diff = Math.max(0, dot(n, light));
            float rim = (float) Math.pow(1 - Math.max(0, dot(n, toCam)), 3) * 0.55F;
            float k = 0.42F + 0.72F * diff;
            int base = tint != 0 ? tint : bone.rgb();
            int[] cs = new int[4];
            float ymin = Float.MAX_VALUE, ymax = -Float.MAX_VALUE;
            for (float[] c : q) {
                ymin = Math.min(ymin, c[1]);
                ymax = Math.max(ymax, c[1]);
            }
            for (int i = 0; i < 4; i++) {
                float g = ymax - ymin < 0.01F ? 1 : 0.9F + 0.2F * (q[i][1] - ymin) / (ymax - ymin);
                cs[i] = shade(base, k * g, tint != 0 ? 0 : rim);
            }
            quad(q, cs, alpha, bone.palette());
        }
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] norm(float[] a) {
        float l = Mth.sqrt(dot(a, a));
        return l < 1e-6F ? a : new float[] {a[0] / l, a[1] / l, a[2] / l};
    }

    /** A fighter: its rage glow round it first, then its lit bones. */
    private void fighter(Body b, float partial) {
        float[][] pp = poseOf(b);
        float[] p = new float[P];
        for (int i = 0; i < P; i++) p[i] = i == 12 ? pp[0][i] + Mth.wrapDegrees(pp[1][i] - pp[0][i]) * partial : Mth.lerp(partial, pp[0][i], pp[1][i]);
        int[] look = LOOKS[styleIndex(b.style)];
        List<Bone> bones = skeleton(b, p, look, b.style.kind());
        curBody = b;
        curPose = p;
        curLift = lift(b, p);
        boolean hurt = (b.state == State.HIT || b.state == State.AIR) && ((int) vt() / 2) % 2 == 0;
        if (b.rage) {
            for (Bone bone : bones) box(b, p, bone, 1.2F + 0.4F * Mth.sin(vt() * 0.8F), 0.28F, 0xFF3020);
        }
        if (b.state == State.KI || b.ki) { // (ki charge: a gold glow - flaring while it charges)
            float grow = b.state == State.KI ? 1.0F + b.t * 0.04F : 0.9F + 0.3F * Mth.sin(vt() * 1.2F);
            for (Bone bone : bones) box(b, p, bone, grow, 0.3F, 0xFFD040);
        }
        if (b.state == State.ATTACK && b.move != null && b.move.is(ELECTRIC) && b.t < b.move.startup() + b.move.active() + 6) {
            for (Bone bone : bones) box(b, p, bone, 1.4F + 0.6F * Mth.sin(vt() * 3), 0.4F, 0x50A0FF); // (crackling blue)
        }
        if (b.state == State.ATTACK && b.move != null && b.move.is(CRUSH) && b.t < b.move.startup() + b.move.active()) {
            for (Bone bone : bones) box(b, p, bone, 1.0F, 0.35F, 0x60C0FF); // (a power crush: armoured, glowing)
        }
        for (Bone bone : bones) box(b, p, bone, 0, 1.0F, hurt ? 0xFFFFFF : 0);
    }

    // ---- the stage ----

    private static final int SKY_TOP = 0x1A1640, SKY_MID = 0x8A3A6A, SKY_LOW = 0xFF9A5A, HAZE = 0xC87A6A;

    private static int mixRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private void sky(GuiGraphics g, int x0, int y0) {
        int hz = Math.round(horizon);
        for (int y = 0; y < hz; y += 2) {
            float t = y / Math.max(1.0F, horizon);
            int c = t < 0.6F ? mixRgb(SKY_TOP, SKY_MID, t / 0.6F) : mixRgb(SKY_MID, SKY_LOW, (t - 0.6F) / 0.4F);
            box(g, x0, y0, 0, y, W, 2, 0xFF000000 | c);
        }
        float turn = csp * 0.011F; // (radians the camera has swung round)
        // stars up high
        for (int i = 0; i < 40; i++) {
            float sx = Math.floorMod((int) (i * 53 - turn * 140), W + 20) - 10;
            float sy = (i * 29) % Math.max(1, (int) (horizon * 0.5F));
            int a = (int) (90 + 80 * Mth.sin(vt() * 0.1F + i)) << 24;
            box(g, x0, y0, sx, sy, 1, 1, a | 0xFFFFFF);
        }
        // the sun, low, with its glow
        float sunX = W * 0.68F - Mth.wrapDegrees(turn * Mth.RAD_TO_DEG) * 3.0F;
        for (int r = 16; r > 0; r -= 3) {
            box(g, x0, y0, sunX - r, horizon - 12 - r * 0.5F, r * 2, r, ((int) (40 - r * 2) << 24) | 0xFFD08A);
        }
        box(g, x0, y0, sunX - 6, horizon - 18, 12, 12, 0xFFFFE6B0);
        // far mountains, then nearer hills with a temple and lanterns - sliding at their own speeds as the camera turns
        ridge(g, x0, y0, turn * 160, 26, 0.035F, 0xFF4A2A5A, 0);
        ridge(g, x0, y0, turn * 300, 14, 0.06F, 0xFF2A1838, 1);
    }

    private void ridge(GuiGraphics g, int x0, int y0, float slide, float height, float freq, int colour, int layer) {
        for (int x = 0; x < W; x += 2) {
            float wx = x + slide;
            float h = height * (0.55F + 0.25F * Mth.sin(wx * freq) + 0.2F * Mth.sin(wx * freq * 2.7F + 1.3F));
            box(g, x0, y0, x, horizon - h, 2, h + 1, colour);
        }
        if (layer == 1) {
            // a temple: tiered roofs, and lanterns along the hill
            float tx = Math.floorMod((int) (60 - slide), W + 120) - 60;
            for (int k = 0; k < 3; k++) {
                float w = 30 - k * 8, y = horizon - 16 - k * 9;
                box(g, x0, y0, tx - w / 2, y, w, 3, 0xFF1A0E26);
                box(g, x0, y0, tx - w / 2 + 3, y + 3, w - 6, 6, 0xFF221430);
                box(g, x0, y0, tx - 2, y + 4, 4, 3, 0xFFFFB050);
            }
            box(g, x0, y0, tx - 1, horizon - 46, 2, 6, 0xFF1A0E26);
            for (int i = 0; i < 9; i++) {
                float lx = Math.floorMod((int) (i * 27 + 13 - slide * 1.0F), W + 20) - 10;
                float wx = lx + slide;
                float h = 14 * (0.55F + 0.25F * Mth.sin(wx * 0.06F) + 0.2F * Mth.sin(wx * 0.06F * 2.7F + 1.3F));
                float fl = 0.7F + 0.3F * Mth.sin(vt() * 0.3F + i);
                box(g, x0, y0, lx - 1, horizon - h - 3, 3, 3, ((int) (255 * fl) << 24) | 0xFFC060);
            }
        }
    }

    /** The stone stage, in its own space (turned under them by the camera's swing): tiles, a circle, the fog far off. */
    private void floor() {
        float turn = csp * 0.011F;
        float c = Mth.cos(turn), s = Mth.sin(turn);
        float pivot = W / 2.0F;
        int tile = 24;
        int gx0 = (int) Math.floor((cu - 260) / tile), gx1 = (int) Math.ceil((cu + 260) / tile);
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = -8; gz <= 16; gz++) {
                float[][] cam = new float[4][];
                boolean ok = true;
                float[][] corners = {{gx * tile, gz * tile}, {gx * tile, (gz + 1) * tile}, {(gx + 1) * tile, (gz + 1) * tile}, {(gx + 1) * tile, gz * tile}};
                for (int i = 0; i < 4; i++) {
                    float wx = corners[i][0] - pivot, wz = corners[i][1];
                    float u = pivot + wx * c + wz * s, v = -wx * s + wz * c; // (stage -> the fighters' line)
                    cam[i] = view(u, 0, v);
                    if (cam[i][2] < 6) ok = false;
                }
                if (!ok) continue;
                float dist = (cam[0][2] + cam[2][2]) / 2;
                boolean light = Math.floorMod(gx + gz, 2) == 0;
                int base = light ? 0x8A7A72 : 0x6E5F5A;
                // the circle in the middle of the stage
                float mx = (gx + 0.5F) * tile - pivot, mz = (gz + 0.5F) * tile;
                float rr = Mth.sqrt(mx * mx + mz * mz);
                if (Math.abs(rr - 60) < 12) base = light ? 0xB89A6A : 0xA0845A;
                if (rr < 14) base = 0xC83C3C;
                int col = mixRgb(base, HAZE, (dist - 150) / 650);
                int[] cs = {col, col, col, col};
                quad(cam, cs, 1.0F, 11);
            }
        }
    }

    /** The walls at either end of their line: shimmering screens of light. */
    private void walls() {
        for (int wall : new int[] {WALL_L - 6, WALL_R + 6}) {
            for (int k = -4; k < 4; k++) {
                float v0 = k * 16, v1 = v0 + 16;
                float[][] cam = {view(wall, 0, v0), view(wall, 40, v0), view(wall, 40, v1), view(wall, 0, v1)};
                int glow = 0x3AE8FF;
                quad(cam, new int[] {glow, mixRgb(glow, 0xFFFFFF, 0.5F), mixRgb(glow, 0xFFFFFF, 0.5F), glow}, 0.18F, 0);
            }
        }
    }

    /** Draws a fighter where it is between its last tick and this one (smooth at any frame rate), then puts it back. */
    private static void between(Body b, float partial, Runnable draw) {
        float x = b.x, y = b.y, d = b.depth;
        if (Math.abs(b.x - b.ox) < 40) { // (not across a teleport-like jump: a throw over the shoulder)
            b.x = Mth.lerp(partial, b.ox, x);
            b.y = Mth.lerp(partial, b.oy, y);
            b.depth = Mth.lerp(partial, b.oDepth, d);
        }
        try {
            draw.run();
        } finally {
            b.x = x;
            b.y = y;
            b.depth = d;
        }
    }

    private void shadow(Body b) {
        float r = 9 * Math.max(0.4F, 1 - b.y / 60);
        for (int k = 0; k < 10; k++) {
            float a0 = k * Mth.TWO_PI / 10, a1 = (k + 1) * Mth.TWO_PI / 10;
            float[][] cam = {view(b.x, 0.05F, b.depth), view(b.x, 0.05F, b.depth),
                    view(b.x + Mth.cos(a1) * r, 0.05F, b.depth + Mth.sin(a1) * r * 0.6F), view(b.x + Mth.cos(a0) * r, 0.05F, b.depth + Mth.sin(a0) * r * 0.6F)};
            quad(cam, new int[] {0, 0, 0, 0}, 0.45F, 0);
        }
    }

    // ---- putting it together ----

    @Override
    public void draw(GuiGraphics g, Font font, int x0, int y0, float partial) {
        pt = partial;
        if (picking) {
            drawPick(g, font, x0, y0, partial);
            return;
        }
        int sx = shake > 0 ? (shake % 2 == 0 ? 2 : -2) : 0;
        g.pose().pushPose();
        g.pose().translate(sx, 0, 0);
        camera(partial);
        sky(g, x0, y0);
        quads.clear();
        floor();
        flush(g, x0, y0, false); // (the floor under everything)
        for (Body b : new Body[] {me, foe}) between(b, partial, () -> shadow(b));
        flush(g, x0, y0, false);
        walls();
        for (Body b : new Body[] {me, foe}) between(b, partial, () -> fighter(b, partial));
        flush(g, x0, y0, true);
        effects(g, x0, y0);
        g.pose().popPose();
        hud(g, font, x0, y0);
        if (showList) moveList(g, font, x0, y0);
        g.flush();
    }

    private static String rageDriveName(Body b) {
        return b.who == 2 ? "b+1+2" : b.who == 3 ? "qcf+1+2" : "f+1+2";
    }

    /** The move list: everyone's moves, then this fighter's own. */
    private String[][] moveListFor(Body b) {
        List<String[]> l = new ArrayList<>();
        l.add(new String[] {"MOVE LIST - " + b.style.name(), "1 U  2 I  3 J  4 K   H: back"});
        l.add(new String[] {"1,1,2  1,2,4  1,2,3  3,4", "strings"});
        l.add(new String[] {"df+1 mid check  df+2 LAUNCHER", "df+3  df+4  mids"});
        l.add(new String[] {"d+1  d+2  crouching mids", "d+3 d+4 lows   db+4 sweep"});
        l.add(new String[] {"f+2 / dash 2  lunge punch", "f+4  homing (CH launches)"});
        l.add(new String[] {"b+2 spin fist  f+3 spiral", "SCREWS (one a juggle)"});
        l.add(new String[] {"b+1+2  iron palm", "POWER CRUSH (soaks hits)"});
        l.add(new String[] {"let go of down: ws1 ws2 ws4", "ws2: LAUNCHER"});
        l.add(new String[] {"uf+4  hopkick", "LAUNCHER, hops over lows"});
        switch (b.who) {
            case 0 -> {
                l.add(new String[] {"f,n,d,df  crouch dash", "+1  +2 DRAGON UPPER  +4 low"});
                l.add(new String[] {"2 on the very frame of df:", "PERFECT DRAGON (+5, i11)"});
            }
            case 1 -> {
                l.add(new String[] {"sidestep + 1 / 2", "claw swipe (homing)"});
                l.add(new String[] {"dash + 3  cat slide (low)", "b+3  backflip (LAUNCHER)"});
            }
            case 2 -> {
                l.add(new String[] {"df+1+2  iron grab", "command throw (break: 1+2)"});
                l.add(new String[] {"f+1+2  piston (CRUSH)", "d+1+2  ground slam"});
            }
            default -> {
                l.add(new String[] {"d,df,f+2  burning fist", "into the wall"});
                l.add(new String[] {"b+4 flame heel (CH launch)", "dash f+1+2 heat rush"});
            }
        }
        l.add(new String[] {"1+3 / 2+4  throws", "break: 1 / 2 in time"});
        l.add(new String[] {"df just as a low comes", "LOW PARRY"});
        l.add(new String[] {"down: 1 or 2 as you land", "TECH    3 / 4 get-up kicks"});
        l.add(new String[] {"1+2+3+4  ki charge", "next hit can't be blocked"});
        l.add(new String[] {"rage: df+1+2  RAGE ART", rageDriveName(b) + "  RAGE DRIVE"});
        l.add(new String[] {"COMBO: df+2, 1,1, b+2,", "dash, f+2 (to the wall)"});
        l.add(new String[] {"P: practice (frame data)", "W/S tap step  FF dash  BB back"});
        return l.toArray(new String[0][]);
    }

    /** The move list over the paused fight. */
    private void moveList(GuiGraphics g, Font font, int x0, int y0) {
        box(g, x0, y0, 4, 4, W - 8, H - 8, 0xE8100A20);
        box(g, x0, y0, 4, 4, W - 8, 1, 0xFFFFC850);
        box(g, x0, y0, 4, H - 5, W - 8, 1, 0xFFFFC850);
        String[][] list = moveListFor(me);
        g.pose().pushPose();
        g.pose().translate(x0 + 8, y0 + 8, 0);
        g.pose().scale(0.45F, 0.45F, 1);
        for (int i = 0; i < list.length; i++) {
            int y = i * 13;
            boolean head = i == 0;
            g.drawString(font, list[i][0], 0, y, head ? 0xFFFFC850 : 0xFFFFFFFF, true);
            g.drawString(font, list[i][1], 190, y, head ? 0xFFB0B0C8 : 0xFFFFE070, true);
        }
        g.pose().popPose();
    }

    /** Draws what's been gathered, furthest first. */
    private void flush(GuiGraphics g, int x0, int y0, boolean keep) {
        quads.sort((a, b) -> Float.compare(b.depth(), a.depth()));
        com.mojang.blaze3d.vertex.VertexConsumer vc = g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        org.joml.Matrix4f m = g.pose().last().pose();
        for (Quad q : quads) {
            float area = 0;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                area += q.xs()[i] * q.ys()[j] - q.xs()[j] * q.ys()[i];
            }
            for (int k = 0; k < 4; k++) {
                int i = area > 0 ? 3 - k : k; // (always wound the way the screen draws)
                vc.addVertex(m, x0 + q.xs()[i], y0 + q.ys()[i], 0).setColor(q.colours()[i]);
            }
        }
        g.flush();
        if (keep) {
            lastFrame.clear();
            lastFrame.addAll(quads);
            frameId++;
        }
        quads.clear();
    }

    private void effects(GuiGraphics g, int x0, int y0) {
        // trails behind a striking hand or foot
        for (Body b : new Body[] {me, foe}) {
            List<float[]> tr = trails.get(b);
            if (tr == null) continue;
            for (int i = 1; i < tr.size(); i++) {
                float[] a = screen(view(tr.get(i - 1)[0], tr.get(i - 1)[1], tr.get(i - 1)[2]));
                float[] c = screen(view(tr.get(i)[0], tr.get(i)[1], tr.get(i)[2]));
                if (a == null || c == null) continue;
                int alpha = 30 + 30 * i;
                line(g, x0, y0, a, c, 2.4F, (alpha << 24) | 0xFFFFFF);
            }
        }
        // dust
        for (float[] d : dust) {
            float[] s = screen(view(d[0], d[1], d[2]));
            if (s == null) continue;
            float size = (2 + (14 - d[3]) * 0.3F) * FL / cd;
            box(g, x0, y0, s[0] - size / 2, s[1] - size / 2, size, size, ((int) (d[3] * 6) << 24) | 0xD8C8B8);
        }
        // hit sparks: bright rays out from where it landed (blue for a counter hit)
        for (float[] sp : sparks) {
            float[] s = screen(view(sp[0], sp[1], camV));
            if (s == null) continue;
            float k = sp[2];
            int col = sp.length > 3 && sp[3] > 0 ? 0x80D0FF : 0xFFE080;
            for (int r = 0; r < 8; r++) {
                float ang = r * Mth.PI / 4 + sp[0];
                float len = (10 - k) * 2.2F + 4;
                float[] e = {s[0] + Mth.cos(ang) * len, s[1] + Mth.sin(ang) * len};
                line(g, x0, y0, s, e, 1.4F, ((int) (Math.min(1, k / 6) * 255) << 24) | col);
            }
            box(g, x0, y0, s[0] - 3, s[1] - 3, 6, 6, 0xFFFFFFFF);
        }
        if (flash > 0) box(g, x0, y0, 0, 0, W, H, (flash * 30 << 24) | 0xFFFFFF);
    }

    private static void line(GuiGraphics g, int x0, int y0, float[] a, float[] b, float w, int argb) {
        float dx = b[0] - a[0], dy = b[1] - a[1], l = Mth.sqrt(dx * dx + dy * dy);
        if (l < 0.01F) return;
        float nx = -dy / l * w / 2, ny = dx / l * w / 2;
        com.mojang.blaze3d.vertex.VertexConsumer vc = g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        org.joml.Matrix4f m = g.pose().last().pose();
        float[][] p = {{a[0] + nx, a[1] + ny}, {a[0] - nx, a[1] - ny}, {b[0] - nx, b[1] - ny}, {b[0] + nx, b[1] + ny}};
        float area = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            area += p[i][0] * p[j][1] - p[j][0] * p[i][1];
        }
        for (int k = 0; k < 4; k++) {
            int i = area > 0 ? 3 - k : k;
            vc.addVertex(m, x0 + p[i][0], y0 + p[i][1], 0).setColor(argb);
        }
        g.flush();
    }

    // ---- the bars, the clock, the words ----

    private void hud(GuiGraphics g, Font font, int x0, int y0) {
        healthBar(g, x0, y0, 6, me, false);
        healthBar(g, x0, y0, W - 6 - 78, foe, true);
        // the clock
        box(g, x0, y0, W / 2.0F - 11, 6, 22, 14, 0xC0101020);
        box(g, x0, y0, W / 2.0F - 11, 6, 22, 1, 0xFFFFC850);
        String c = practice > 0 ? "--" : Integer.toString(Math.max(0, (clock + FPS - 1) / FPS));
        g.pose().pushPose();
        g.pose().translate(x0 + W / 2.0F, y0 + 8, 0);
        g.pose().scale(1.2F, 1.2F, 1);
        g.drawString(font, c, -font.width(c) / 2, 0, clock < 10 * FPS && ((int) vt() / 5) % 2 == 0 ? 0xFFFF5050 : 0xFFFFE070, true);
        g.pose().popPose();
        // names under the bars, rounds won as lights
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        g.drawString(font, me.style.name(), (int) (8 / 0.75F), (int) (21 / 0.75F), 0xFFFFFFFF, true);
        g.drawString(font, foe.style.name(), (int) ((W - 8) / 0.75F) - font.width(foe.style.name()), (int) (21 / 0.75F), 0xFFFFFFFF, true);
        g.pose().popPose();
        for (int i = 0; i < 2; i++) {
            box(g, x0, y0, 70 - i * 7, 21, 5, 5, i < me.wins ? 0xFFFFC850 : 0x80404050);
            box(g, x0, y0, W - 75 + i * 7, 21, 5, 5, i < foe.wins ? 0xFFFFC850 : 0x80404050);
        }
        if (me.rage && !me.rageUsed) {
            g.pose().pushPose();
            g.pose().translate(x0 + 8, y0 + 30, 0);
            g.pose().scale(0.75F, 0.75F, 1);
            g.drawString(font, "RAGE: df+1+2 ART  " + rageDriveName(me) + " DRIVE", 0, 0, ((int) vt() / 4) % 2 == 0 ? 0xFFFF4040 : 0xFFFF9A2E, true);
            g.pose().popPose();
        }
        // the combo counters: hits and damage, on the side of whoever's doing it
        for (Body b : new Body[] {me, foe}) {
            if (b.combo < 2 || b.comboShow <= 0) continue;
            boolean left = b == me;
            float cx = left ? x0 + 30 : x0 + W - 30;
            int fade = b.comboShow < 30 ? b.comboShow * 8 + 10 : 255;
            big(g, font, b.combo + " HITS", cx, y0 + 40, 1.0F, (fade << 24) | 0xFFC850);
            big(g, font, b.comboDmg + " DAMAGE", cx, y0 + 50, 0.6F, (fade << 24) | 0xFFFFFF);
        }
        for (Object[] co : callouts) {
            String text = (String) co[0];
            float[] s = screen(view((Float) co[1], GROUND - (Float) co[2], camV));
            if (s == null) continue;
            float rise = (84 - (Integer) co[3]) * 0.4F / 3;
            big(g, font, text, x0 + s[0], y0 + s[1] - rise, 0.8F, text.startsWith("COUNTER") ? 0xFF80D0FF : 0xFFFFE74A);
        }
        if (practice > 0) { // (practice: which dummy, and the frame data of your last move)
            big(g, font, practice == 1 ? "PRACTICE - DUMMY" : practice == 2 ? "PRACTICE - BLOCK ALL" : "PRACTICE - CPU", x0 + W / 2.0F, y0 + 24, 0.55F, 0xFF80D0FF);
            if (frameInfoFor > 0 && !frameInfo.isEmpty()) big(g, font, frameInfo, x0 + W / 2.0F, y0 + H - 12, 0.6F, 0xFFFFFFFF);
        }
        if (!banner.isEmpty()) {
            float sc = banner.length() <= 8 ? 2.4F : banner.length() <= 14 ? 1.6F : 1.1F;
            big(g, font, banner, x0 + W / 2.0F, y0 + 54, sc, ((int) vt() / 4) % 2 == 0 ? 0xFFFFE74A : 0xFFFF9A2E);
        }
    }

    private void healthBar(GuiGraphics g, int x0, int y0, int x, Body b, boolean right) {
        float shown = shownHp.computeIfAbsent(b, k -> new float[] {MAX_HP})[0];
        int w = 78;
        box(g, x0, y0, x - 1, 8, w + 2, 9, b.rage && ((int) vt() / 4) % 2 == 0 ? 0xFFFF3030 : 0xFF0A0812);
        box(g, x0, y0, x, 9, w, 7, 0xFF3A0A14);
        float fShown = w * shown / MAX_HP, fNow = w * b.hp / (float) MAX_HP;
        box(g, x0, y0, right ? x + w - fShown : x, 9, fShown, 7, 0xFFFFFFFF); // (the damage just taken, draining away)
        int top = b.hp > MAX_HP / 4 ? 0xFFFFE050 : 0xFFFF5040, bottom = b.hp > MAX_HP / 4 ? 0xFFFF9A20 : 0xFFC02020;
        float fx = right ? x + w - fNow : x;
        box(g, x0, y0, fx, 9, fNow, 4, top);
        box(g, x0, y0, fx, 13, fNow, 3, bottom);
        box(g, x0, y0, fx, 9, fNow, 1, 0x80FFFFFF);
    }

    /** Big outlined words. */
    private static void big(GuiGraphics g, Font font, String s, float cx, float cy, float scale, int colour) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        int w = font.width(s);
        for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            g.drawString(font, s, -w / 2 + o[0], o[1], 0xFF101020, false);
        }
        g.drawString(font, s, -w / 2, 0, colour, false);
        g.pose().popPose();
    }

    private void drawPick(GuiGraphics g, Font font, int x0, int y0, float partial) {
        // a dark hall, a spotlight on the one picked, all four in 3D, the picked one turning to show off
        box(g, x0, y0, 0, 0, W, H, 0xFF120A24);
        for (int y = 0; y < H; y += 4) box(g, x0, y0, 0, y, W, 2, 0x10FFFFFF);
        cu = W / 2.0F;
        cd = 150;
        csp = 0;
        camV = 0;
        camH = 26;
        horizon = 104 - camH * FL / cd;
        quads.clear();
        for (int i = 0; i < FIGHTERS.length; i++) {
            Body b = picks[i];
            if (b == null) {
                b = picks[i] = new Body(FIGHTERS[i], i, 30 + i * 44);
                b.facing = 1;
            }
            b.state = i == pick ? State.WIN : State.STAND;
            float[][] pp = poseOf(b);
            System.arraycopy(pp[1], 0, pp[0], 0, P);
            float[] want = target(b);
            for (int k = 0; k < P; k++) pp[1][k] += (want[k] - pp[1][k]) * 0.2F;
            pp[1][13] = i == pick ? (ticks + partial) % 360 - 180 : 20;
            pp[0][13] = pp[1][13];
            if (i == pick) {
                for (int k = 0; k < 12; k++) {
                    float a0 = k * Mth.TWO_PI / 12, a1 = (k + 1) * Mth.TWO_PI / 12;
                    float[][] cam = {view(b.x, 0, 0), view(b.x, 0, 0), view(b.x + Mth.cos(a1) * 16, 0, Mth.sin(a1) * 10),
                            view(b.x + Mth.cos(a0) * 16, 0, Mth.sin(a0) * 10)};
                    quad(cam, new int[] {0xFFE8A0, 0xFFE8A0, 0x8A6A3A, 0x8A6A3A}, 0.6F, 15);
                }
            }
            fighter(b, partial);
        }
        flush(g, x0, y0, true);
        big(g, font, "SHADERED SHOWDOWN", x0 + W / 2.0F, y0 + 8, 1.0F, 0xFFFF7AD9);
        big(g, font, "CHOOSE YOUR FIGHTER", x0 + W / 2.0F, y0 + 20, 0.75F, 0xFFFFFFFF);
        for (int i = 0; i < FIGHTERS.length; i++) {
            big(g, font, FIGHTERS[i].name(), x0 + 30 + i * 44, y0 + 110, 0.75F, i == pick ? 0xFFFFE74A : 0xFFAAAAAA);
        }
        Style s = FIGHTERS[pick];
        String stats = "SPEED " + "■".repeat(Math.round(s.speed() * 4)) + "  POWER " + "■".repeat(Math.round(s.power() * 4));
        big(g, font, stats, x0 + W / 2.0F, y0 + 122, 0.7F, 0xFF9AD8FF);
        if (((int) vt() / 10) % 2 == 0) big(g, font, "< >   U: FIGHT", x0 + W / 2.0F, y0 + 133, 0.75F, 0xFFFFFFFF);
        g.flush();
    }

    private final Body[] picks = new Body[FIGHTERS.length];

    private int frameId, gridOf = -1;
    private static final int GW = 48, GH = 36;
    private final byte[] grid = new byte[GW * GH];

    @Override
    public int sample(float x, float y) {
        if (gridOf != frameId) { // (a small picture of the last frame, made once: far shapes first, near ones over them)
            gridOf = frameId;
            java.util.Arrays.fill(grid, (byte) 0);
            for (int gy = 0; gy < GH; gy++) {
                if ((gy + 0.5F) * H / GH >= horizon && !picking) {
                    for (int gx = 0; gx < GW; gx++) grid[gy * GW + gx] = 11;
                }
            }
            for (Quad q : lastFrame) {
                float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
                for (int i = 0; i < 4; i++) {
                    x0 = Math.min(x0, q.xs()[i]);
                    x1 = Math.max(x1, q.xs()[i]);
                    y0 = Math.min(y0, q.ys()[i]);
                    y1 = Math.max(y1, q.ys()[i]);
                }
                int cx0 = Math.max(0, (int) (x0 * GW / W)), cx1 = Math.min(GW - 1, (int) (x1 * GW / W));
                int cy0 = Math.max(0, (int) (y0 * GH / H)), cy1 = Math.min(GH - 1, (int) (y1 * GH / H));
                for (int gy = cy0; gy <= cy1; gy++) {
                    for (int gx = cx0; gx <= cx1; gx++) {
                        if (inside(q, (gx + 0.5F) * W / GW, (gy + 0.5F) * H / GH)) grid[gy * GW + gx] = (byte) q.palette();
                    }
                }
            }
        }
        int gx = Mth.clamp((int) (x * GW / W), 0, GW - 1), gy = Mth.clamp((int) (y * GH / H), 0, GH - 1);
        return grid[gy * GW + gx];
    }

    private static boolean inside(Quad q, float x, float y) {
        boolean in = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            if ((q.ys()[i] > y) != (q.ys()[j] > y)
                    && x < (q.xs()[j] - q.xs()[i]) * (y - q.ys()[i]) / (q.ys()[j] - q.ys()[i] + 1e-6F) + q.xs()[i]) {
                in = !in;
            }
        }
        return in;
    }
}
