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
 *
 * <p>And the rest of it: hits freeze both fighters a few frames (heavier, longer); hits from the side spin you round,
 * back turned you can't block (turn with a direction, or use a turning attack); side and back throws; stances (NEKO's
 * cat, ROBO's siege, BLAZE's sway); run attacks; the Korean backdash and the wavedash; kip-ups, spring kicks, stomps;
 * five stages (walls that break, floors that break, one with no walls) picked after your fighter; arcade (the ladder,
 * then NYX, the boss) or survival; practice's input display, recording (O) and playback, and combo trials;
 * entrances, win poses, the rage art's camera, and music for the select screen and each stage.
 */
public class Showdown extends ArcadeGame {
    /** It runs at 60 frames a second, like the real thing: every move's frame data is in those frames. */
    private static final int FPS = 60;
    private static final int GROUND = 128, ROUND_FRAMES = 60 * FPS;
    /**
     * The arena, in its own (stage) space: a square, its walls on all four sides (far off on a stage without walls;
     * one moved back by a wall break): min along, max along, min across, max across. The middle is (W / 2, 0).
     */
    private final float[] arena = {10, W - 10, -86, 86};

    /**
     * The fighters' line in the arena: the line's coordinates (along it, and off it - depth) turned by the line's
     * angle (spin * SPIN_RAD) and moved by (tX, tZ) give the arena's. The line turns to stay through both fighters - and
     * the camera, side-on to it, swings round with it - but the fighters stay where they are on the floor.
     */
    private float tX, tZ, oTX, oTZ;
    private static final float SPIN_RAD = 0.011F;

    private float[] toStage(float x, float z, float spinNow, float tx, float tz) {
        float a = spinNow * SPIN_RAD, c = Mth.cos(a), sn = Mth.sin(a);
        return new float[] {x * c - z * sn + tx, x * sn + z * c + tz};
    }

    private float[] toLine(float sx, float sz, float spinNow, float tx, float tz) {
        float a = spinNow * SPIN_RAD, c = Mth.cos(a), sn = Mth.sin(a);
        float dx = sx - tx, dz = sz - tz;
        return new float[] {dx * c + dz * sn, -dx * sn + dz * c};
    }

    /** Fighters in the arena: kept inside its walls (a fighter reeling into one: a wall splat); which wall it's at. */
    private void keepInArena(Body b, boolean splats) {
        float[] p = toStage(b.x, b.depth, spin, tX, tZ);
        int side = p[0] < arena[0] ? 0 : p[0] > arena[1] ? 1 : p[1] < arena[2] ? 2 : p[1] > arena[3] ? 3 : -1;
        b.wallSide = side;
        if (side < 0) return;
        p[0] = Mth.clamp(p[0], arena[0], arena[1]);
        p[1] = Mth.clamp(p[1], arena[2], arena[3]);
        float[] l = toLine(p[0], p[1], spin, tX, tZ);
        b.x = l[0];
        b.depth = l[1];
        if (splats && (b.state == State.AIR || b.state == State.HIT) && Math.abs(b.vx) > 0.33F && b.wallHits == 0) {
            b.state = State.SPLAT; // (slammed into the wall while reeling: a wall splat)
            b.stun = 42;
            b.vx = 0;
            b.vy = 0;
            b.wallHits = 1;
            shake = 6;
            call("WALL SPLAT!", b.x, GROUND - b.y - 40);
            sfx(Sfx.WALL_SPLAT);
        }
    }

    /**
     * The line turning to go through both of them again (quickly when nobody's stepping; slowly mid-sidestep, so the
     * step still takes you off it; in between while sidewalking - circling them): it turns round the middle of them,
     * so neither moves on the floor - only the camera swings.
     */
    private void realign() {
        float[] a = toStage(me.x, me.depth, spin, tX, tZ), b = toStage(foe.x, foe.depth, spin, tX, tZ);
        float dx = b[0] - a[0], dz = b[1] - a[1];
        if (foe.x < me.x) {
            dx = -dx;
            dz = -dz;
        }
        if (dx * dx + dz * dz < 4) return;
        boolean stepping = me.state == State.SIDESTEP || foe.state == State.SIDESTEP;
        boolean walking = me.state == State.SIDEWALK || foe.state == State.SIDEWALK;
        float rate = stepping ? 0.008F : walking ? 0.04F : 0.07F;
        float want = (float) Math.atan2(dz, dx) / SPIN_RAD;
        float newSpin = spin + Mth.wrapDegrees((want - spin) * SPIN_RAD * Mth.RAD_TO_DEG) / (SPIN_RAD * Mth.RAD_TO_DEG) * rate;
        // the middle of them stays put (on the floor and on the line - drifting back onto the line's middle)
        float mx = (me.x + foe.x) / 2, mz = (me.depth + foe.depth) / 2;
        float[] mid = toStage(mx, mz, spin, tX, tZ);
        float a2 = newSpin * SPIN_RAD, c = Mth.cos(a2), sn = Mth.sin(a2);
        float mz2 = mz * (1 - rate);
        tX = mid[0] - (mx * c - mz2 * sn);
        tZ = mid[1] - (mx * sn + mz2 * c);
        for (Body f : new Body[] {me, foe}) {
            float[] st = f == me ? a : b;
            float[] l = toLine(st[0], st[1], newSpin, tX, tZ);
            f.x = l[0];
            f.depth = l[1];
        }
        spin = newSpin;
    }

    /**
     * A stage: its sky (top, middle, low), the haze far off, the floor's tiles, its ring and middle, the hills behind,
     * what's on them (0 a temple, 1 a city, 2 ice, 3 a volcano, 4 the void), its walls' colour, whether it has walls,
     * and whether they (or the floor) break - and the floor below, once it has.
     */
    private record Stage(String name, int skyTop, int skyMid, int skyLow, int haze, int tileA, int tileB, int ringA, int ringB,
                         int middle, int farHills, int nearHills, int feature, int wall, boolean walls, boolean wallBreak,
                         boolean floorBreak, int lowA, int lowB, int outside, int light, int rim, boolean glossy) {}

    private static final Stage[] STAGES = {
            new Stage("SUNSET TEMPLE", 0x1A1640, 0x8A3A6A, 0xFF9A5A, 0xC87A6A, 0x8A7A72, 0x6E5F5A, 0xB89A6A, 0xA0845A, 0xC83C3C,
                    0xFF4A2A5A, 0xFF2A1838, 0, 0x3AE8FF, true, true, false, 0, 0, 0x3A5A2A, 0xFFE4CC, 0xFF9A50, false),
            new Stage("NEON ROOFTOP", 0x05051A, 0x2A0A4A, 0x6A1A8A, 0x3A2A6A, 0x3A3A4A, 0x2A2A38, 0x40E0FF, 0x30B0D0, 0xFF3AA0,
                    0xFF1A1030, 0xFF0E0820, 1, 0xFF4AD0, true, false, true, 0x5A4A3A, 0x4A3A2E, 0x0A0A16, 0xC8B8FF, 0xFF4AD0, true),
            new Stage("FROZEN CAVERN", 0x0A1A2A, 0x2A5A7A, 0x8AD0E8, 0x9AC8D8, 0xB8DCE8, 0x9CC4D4, 0xE0F4FF, 0xC8E8F4, 0x3A8AC8,
                    0xFF5A8AA0, 0xFF3A6A80, 2, 0xFFFFFF, false, false, false, 0, 0, 0xC8E4F0, 0xD8ECFF, 0x8AE0FF, true),
            new Stage("VOLCANO FORGE", 0x1A0505, 0x6A1A0A, 0xFF6A1A, 0xA0401A, 0x4A3A34, 0x3A2C28, 0xFF8A2A, 0xD06A1A, 0xFFD040,
                    0xFF3A1208, 0xFF200A04, 3, 0xFF8A3A, true, true, true, 0x6A2A10, 0x5A200C, 0xFF4A10, 0xFFC8A0, 0xFF6A20, false),
            new Stage("THE VOID", 0x000000, 0x1A0A2A, 0x4A1A6A, 0x2A1A3A, 0x1A1A24, 0x101018, 0x8A3AFF, 0x6A2ACC, 0xFF3AFF,
                    0xFF120A1E, 0xFF0A0612, 4, 0xB04AFF, true, false, false, 0, 0, 0x05020A, 0xC8B0FF, 0xC040FF, true),
            new Stage("HARBOR NIGHT", 0x020614, 0x0A1A3A, 0x1A3A6A, 0x1A2A4A, 0x6A4A30, 0x5A3C26, 0x8A6A40, 0x7A5A34, 0xC8A040,
                    0xFF0A1428, 0xFF060C1A, 5, 0xFFC860, true, true, true, 0x3A2A20, 0x2E2018, 0x1A3A6A, 0xA8B8E0, 0xC8D8FF, false),
            new Stage("SAKURA COURT", 0x5AA8E8, 0xB8D8F0, 0xFFD8E8, 0xF0D0E0, 0xD8C8B8, 0xC8B4A4, 0xE89AB8, 0xD88AA8, 0xC83C5A,
                    0xFF7A98B8, 0xFF5A7A6A, 6, 0xFF9AC8, true, true, false, 0, 0, 0x5A8A4A, 0xFFF4F0, 0xFFC0D8, false)};
    /** The stage now; the one picked (4: random); the floor broken through; how far each wall's been broken back. */
    private int stage, stageChoice, pickPhase;
    /** The stages you can pick (the fifth, THE VOID, is the boss's); after them, random. */
    private static final int[] PICKABLE = {0, 1, 2, 3, 5, 6};
    private boolean floorBroken;
    /** 0 arcade (the ladder, then the boss), 1 survival (one round each, health carried over). */
    private int mode, survived, carryHp = MAX_HP;

    private Stage st() {
        return STAGES[stage];
    }

    /** The stage for the next fight: the one picked, or one at random (the boss: always its own). */
    private void pickStage() {
        stage = foe != null && foe.who == BOSS ? 4 : stageChoice == PICKABLE.length ? PICKABLE[random.nextInt(PICKABLE.length)]
                : PICKABLE[stageChoice];
        floorBroken = false;
        float r = st().walls() ? 86 : 100000;
        arena[0] = W / 2.0F - r;
        arena[1] = W / 2.0F + r;
        arena[2] = -r;
        arena[3] = r;
    }
    private static final int MAX_HP = 170;
    /** Gravity a frame: standing jumps, and someone juggled (floatier). */
    private static final float GRAVITY = 0.4F / 9, GRAVITY_AIR = 0.06F;
    /** How much a frame takes off a slide (a push back on block or hit). */
    private static final float SLIDE = 0.9086F;

    @Override
    public int rate() {
        return FPS;
    }

    /** The select screen's music, then each stage's own. */
    @Override
    public String music() {
        if (picking || me == null) return "music_select";
        return new String[] {"music_temple", "music_rooftop", "music_cavern", "music_forge", "music_void", "music_harbor", "music_sakura"}[stage];
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
    private static final int BACKWARD = 8192; // hits behind you (back turned)
    private static final int TURNS = 16384; // leaves you back turned
    private static final int CROUCH_THROW = 32768; // a throw that only catches someone crouching

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
    private static final Move MELTDOWN = m("MELTDOWN", 0, Pose.THROW, Level.THROW, 14, 2, 30, 65, 20, 0, 0, 0, KND);
    private static final Move IRON_GRAB = m("IRON GRAB", 0, Pose.THROW, Level.THROW, 14, 2, 30, 45, 20, 0, 0, 0, KND | BREAK_BOTH);
    private static final Move PISTON = m("PISTON", 0, Pose.PALM, Level.MID, 16, 3, 28, 22, 30, -10, 0, 0, CRUSH | CARRY | KND);
    private static final Move GROUND_SLAM = m("GROUND SLAM", 0, Pose.LOWPUNCH, Level.MID, 20, 3, 36, 18, 26, -15, 0, 0, GROUND_HIT | DUCK);
    private static final Move OVERDRIVE = m("OVERDRIVE", 0, Pose.PALM, Level.MID, 16, 3, 26, 26, 30, 2, 0, 6.4F, CRUSH);
    // ---- BLAZE: d,df,f+2 - the burning fist ----
    private static final Move BURNING_FIST = m("BURNING FIST", 2, Pose.CROSS, Level.MID, 14, 3, 32, 25, 32, -18, 0, 0, KND | CARRY);
    private static final Move FLAME_HEEL = m("FLAME HEEL", 4, Pose.HIGHKICK, Level.HIGH, 16, 3, 26, 18, 30, -8, 6, 0, CH_LAUNCH);
    private static final Move HEAT_RUSH = m("HEAT RUSH", 0, Pose.PALM, Level.MID, 16, 3, 28, 20, 30, -12, 0, 0, CRUSH | KND);
    // ---- running: 1/2 a chop, 3 a slide, 4 a jump kick ----
    private static final Move RUN_CHOP = m("RUNNING CHOP", 2, Pose.CROSS, Level.MID, 14, 3, 30, 20, 30, -12, 0, 0, KND);
    private static final Move RUN_SLIDE = m("RUNNING SLIDE", 3, Pose.SWEEP, Level.LOW, 16, 4, 34, 18, 32, -20, 0, 0, KND | DUCK);
    private static final Move RUN_KICK = m("RUNNING JUMP KICK", 4, Pose.AIRKICK, Level.MID, 15, 4, 30, 22, 30, -8, 0, 0, KND | HOP);
    // ---- back turned: turning attacks (and one that kicks straight back) ----
    private static final Move BT_BACKFIST = m("TURN BACKFIST", 1, Pose.SPIN, Level.HIGH, 10, 2, 18, 10, 26, 1, 8, 0, 0);
    private static final Move BT_ELBOW = m("TURN ELBOW", 2, Pose.SPIN, Level.MID, 13, 2, 20, 14, 24, -3, 6, 0, 0);
    private static final Move BT_LOW = m("TURN LOW", 3, Pose.LOWKICK, Level.LOW, 15, 2, 24, 12, 26, -12, 1, 0, DUCK);
    private static final Move MULE_KICK = m("MULE KICK", 4, Pose.MIDKICK, Level.MID, 14, 3, 26, 16, 28, -6, 4, 0, BACKWARD | CH_LAUNCH);
    // ---- throws from the side and behind (behind: no breaking it) ----
    // ---- the thrower, once it's caught them: over the shoulder (turning round with them), or up and slammed in front;
    // ROBO's chain throw: twice. (No hit of their own: the throw's already landed.)
    private static final Move THROWING_OVER = m("THROW", 0, Pose.THROW, Level.THROW, 20, 1, 39, 0, 0, 0, 0, 0, 0);
    private static final Move THROWING_FRONT = m("THROW", 0, Pose.THROW, Level.THROW, 20, 1, 39, 0, 0, 0, 0, 0, 0);
    private static final Move THROWING_CHAIN = m("CHAIN THROW", 0, Pose.THROW, Level.THROW, 20, 1, 49, 0, 0, 0, 0, 0, 0);
    /** When a throw's victim is in the air (frames into it), lands, and - chained - lands again. */
    private static final int THROW_LIFT = 20, THROW_SLAM = 40, CHAIN_SLAM = 70;
    private static final Move SIDE_THROW = m("SIDE THROW", 1, Pose.THROW, Level.THROW, 12, 2, 30, 40, 18, 0, 0, 0, KND);
    private static final Move BACK_THROW = m("BACK THROW", 1, Pose.THROW, Level.THROW, 12, 2, 30, 50, 18, 0, 0, 0, KND);
    // ---- the floor: a spring kick up off it; a stomp on someone lying there ----
    private static final Move SPRING_KICK = m("SPRING KICK", 0, Pose.AIRKICK, Level.MID, 22, 3, 30, 18, 28, -10, 0, 0, KND);
    private static final Move STOMP = m("STOMP", 0, Pose.LOWKICK, Level.MID, 20, 3, 30, 14, 26, -12, 0, 0, GROUND_HIT);
    // ---- RIKU's own ----
    private static final Move DEMON_HEEL = m("DEMON HEEL", 4, Pose.HIGHKICK, Level.HIGH, 16, 3, 26, 18, 30, -9, 5, 0, CH_LAUNCH);
    private static final Move TWIN_PALM = m("TWIN PALM", 0, Pose.PALM, Level.MID, 18, 3, 30, 20, 30, -10, 0, 0, KND | CARRY);
    // ---- NEKO's cat stance (f+3+4): low, under highs ----
    private static final Move PAW_JAB = m("PAW JAB", 1, Pose.JAB, Level.MID, 12, 2, 16, 9, 22, 2, 8, 0, DUCK);
    private static final Move POUNCE = m("POUNCE", 2, Pose.UPPER, Level.MID, 16, 2, 32, 14, 22, -12, 0, 6.2F, DUCK);
    private static final Move TAIL_SWEEP = m("TAIL SWEEP", 3, Pose.SWEEP, Level.LOW, 18, 3, 32, 14, 30, -18, 0, 0, KND | DUCK);
    private static final Move CAT_FLIP = m("CAT FLIP", 4, Pose.AIRKICK, Level.MID, 17, 3, 30, 16, 26, -13, 0, 6.0F, HOP | TURNS);
    // ---- ROBO's siege stance (b+3+4): planted, armoured; a crouch throw (db+1+2) ----
    private static final Move PISTON_JAB = m("PISTON JAB", 1, Pose.JAB, Level.MID, 13, 2, 20, 12, 24, 3, 9, 0, 0);
    private static final Move CANNON = m("CANNON", 2, Pose.PALM, Level.MID, 24, 3, 36, 32, 32, -16, 0, 0, KND | CARRY | CRUSH);
    private static final Move TREAD_SWEEP = m("TREAD SWEEP", 3, Pose.SWEEP, Level.LOW, 18, 3, 32, 15, 30, -18, 0, 0, KND | DUCK);
    private static final Move STOMP_KICK = m("STOMP KICK", 4, Pose.MIDKICK, Level.MID, 15, 3, 24, 16, 28, -6, 6, 0, CH_CRUMPLE);
    private static final Move SCRAP_GRAB = m("SCRAP GRAB", 0, Pose.THROW, Level.THROW, 12, 2, 30, 35, 18, 0, 0, 0, KND | BREAK_BOTH | CROUCH_THROW);
    // ---- BLAZE's sway (d,db,b): leaning back out of highs (and mids, at first) ----
    private static final Move SWAY_JAB = m("SWAY JAB", 1, Pose.JAB, Level.MID, 12, 2, 18, 10, 24, 1, 7, 0, 0);
    private static final Move SWAY_BURNER = m("SWAY BURNER", 2, Pose.CROSS, Level.MID, 16, 3, 34, 28, 34, -16, 0, 0, KND | CARRY);
    private static final Move SWAY_SWEEP = m("SWAY SWEEP", 3, Pose.SWEEP, Level.LOW, 18, 3, 32, 15, 30, -18, 0, 0, KND | DUCK);
    private static final Move SWAY_KICK = m("SWAY KICK", 4, Pose.MIDKICK, Level.MID, 14, 2, 22, 14, 28, -4, 6, 0, 0);
    // ---- NYX, the boss: a beam you can't block, a dark uppercut ----
    private static final Move VOID_BEAM = m("VOID BEAM", 0, Pose.PALM, Level.MID, 30, 4, 34, 40, 64, 0, 0, 0, KND | UNBLOCKABLE | CARRY);
    private static final Move DARK_UPPER = m("DARK UPPER", 2, Pose.UPPER, Level.MID, 13, 2, 26, 16, 24, -10, 0, 6.4F, ELECTRIC);
    private static final Move INFERNO = m("INFERNO FIST", 0, Pose.CROSS, Level.MID, 14, 3, 30, 34, 34, 5, 0, 0, KND | CARRY | ELECTRIC);

    private enum State { STAND, CROUCH, DASH, RUN, BACKDASH, SIDESTEP, SIDEWALK, CROUCH_DASH, JUMP, ATTACK, THROWN, HIT, BLOCK, AIR, SPLAT,
        DOWN, RISE, KI, TURN, STANCE, INTRO, KO, WIN }

    /** A fighter: its look (palette colours), speed and strength. */
    private record Style(String name, int gi, int skin, int band, int hair, int kind, float speed, float power) {}

    // kind: 0 a headband, 1 cat ears, 2 an antenna, 3 flame hair
    private static final Style[] FIGHTERS = {
            new Style("RIKU", 10, 7, 6, 13, 0, 1.0F, 1.0F),
            new Style("NEKO", 7, 8, 15, 4, 1, 1.2F, 0.9F),
            new Style("ROBO", 13, 5, 12, 12, 2, 0.85F, 1.15F),
            new Style("BLAZE", 6, 8, 8, 7, 3, 1.05F, 1.05F),
            new Style("NYX", 1, 13, 10, 13, 4, 1.1F, 1.25F)};
    /** The four you can pick; the fifth, NYX, is the boss at the end of the arcade ladder. */
    private static final int PLAYABLE = 4, BOSS = 4;

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
        Move queued, queued2;
        /** A throw it's caught in: what breaks it (0: 1, 1: 2, 2: 1+2), who threw, with what. */
        int breakWith = -1;
        Body thrower;
        Move grab;
        /** Spun by a screw this juggle; can tech when it lands; crumpling; blocking low; ki charged. */
        boolean screwed, techable = true, crumple, blockLow, blockedLow, ki, advancing;
        /** Back turned (facing away: can't block); in a stance (1 NEKO's cat, 2 ROBO's siege, 3 BLAZE's sway);
         *  when a chain throw was asked for (the thrower) or escaped (the one thrown). */
        boolean bt;
        int stance, chainAt = -100, escapeAt = -100;
        /** What height it was last hit at (0 high, 1 mid, 2 low). */
        int hitLevel = 1;
        /** The arena wall it's up against (0-3), or -1. */
        int wallSide = -1;
        /** While-standing up until; parried at; the combo's damage, how long its counter stays up after; how many moves
         *  it's started (so the cabinet's fighter reacts once to each). */
        int wsUntil = -100, parryAt = -100, comboDmg, comboShow, serial;
        /** Its controls, if it's on a pad (you; the dummy playing back what you recorded); none: the cabinet plays it. */
        Pad in;

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
            return state == State.CROUCH || state == State.CROUCH_DASH || (state == State.BLOCK && blockLow) || (state == State.STANCE && stance == 1)
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

    /**
     * A fighter's controls: what's held; the way it's held over the last frames (for motions: f,f  f,n,d,df  d,df,f
     * ...); when things were pressed; a jump about to go (held off a moment, so up-forward + 4 can be a hopkick
     * instead). Yours, or - practice's playback - the dummy's, fed what you recorded.
     */
    private static final class Pad {
        final Set<Integer> held = new HashSet<>();
        boolean kL, kR, kU, kD;
        final List<int[]> dirs = new ArrayList<>(); // numpad direction, the frame it began
        int upAt = -100, upTapAt = -100, downAt = -100, qcfAt = -100, qcbAt = -100, dfAt = -100, kbdUntil = -100;
        /** When each button was last pressed (1-4). */
        final int[] pressedAt = {-100, -100, -100, -100, -100};
        int jumpDir, jumpAt = -1;
        /** What's been pressed lately, as move-list notation (practice's input display): text, frame. */
        final List<Object[]> shown = new ArrayList<>();
    }

    private Pad myPad = new Pad();
    /** The move list up (the fight paused). */
    private boolean showList;
    /** The move list: which tab, which row's picked, how far it's scrolled. */
    private int listTab, listSel, listTop;
    /** Practice: 0 off; 1 the dummy stands; 2 it blocks everything; 3 it fights back. (Health refills, no clock.) */
    private int practice;
    /** Practice: what your last move was and how it went (frame data). */
    private String frameInfo = "";
    private int frameInfoFor;
    /** The cabinet's fighter: the move of yours it's already reacted to; how far into a juggle it is; blocking a
     *  moment ago (to punish what it blocked). */
    private int cpuSeen = -1, cpuJuggle;
    /** Frames still frozen by a hit landing. */
    private int hitstop;
    private boolean cpuWasBlocking;

    @Override
    public String name() {
        return "SHADERED SHOWDOWN";
    }

    @Override
    public String[] help() {
        return new String[] {"WASD  U I J K = 1 2 3 4  H moves  P practice", "hold W jump  W,W step in  S tap step out  FF dash"};
    }

    @Override
    public void start() {
        score = 0;
        over = won = false;
        picking = true;
        pickPhase = 0;
        ladder = 0;
        lap = 0;
        banner = "";
        myPad = new Pad();
        showList = false;
        practice = 0;
    }

    /** Who's next: the arcade ladder (the other three, then the boss), or survival's next at random. */
    private Style foeStyle() {
        if (mode == 1) {
            if (survived > 0 && survived % 5 == 4) return FIGHTERS[BOSS]; // (every fifth: the boss)
            int f = random.nextInt(PLAYABLE - 1);
            return FIGHTERS[f >= pick ? f + 1 : f];
        }
        int k = ladder % PLAYABLE;
        if (k == PLAYABLE - 1) return FIGHTERS[BOSS];
        int n = 0;
        for (int i = 0; i < PLAYABLE; i++) {
            if (i == pick) continue;
            if (n == k) return FIGHTERS[i];
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
        me.in = myPad;
        int f = styleIndex(foeStyle());
        foe = new Body(FIGHTERS[f], f, W - 60);
        pickStage();
        newRound();
        if (mode == 1) me.hp = carryHp; // (survival: what's left, carried on)
    }

    private void newRound() {
        round++;
        spin = oSpin = 0; // (the line back straight across the middle of the arena)
        tX = tZ = oTX = oTZ = 0;
        me.x = W / 2.0F - 36;
        foe.x = W / 2.0F + 36;
        for (Body b : new Body[] {me, foe}) {
            b.y = b.vx = b.vy = b.depth = 0;
            b.hp = MAX_HP;
            b.state = State.STAND;
            b.stun = b.combo = b.t = b.airHits = b.wallHits = 0;
            b.move = null;
            b.queued = b.queued2 = null;
            b.rage = b.rageUsed = false;
            b.thrower = null;
            b.breakWith = -1;
            b.screwed = b.blockLow = b.blockedLow = b.crumple = b.ki = false;
            b.techable = true;
            b.comboDmg = b.comboShow = 0;
            b.wsUntil = b.parryAt = -100;
        }
        myPad.jumpAt = -1;
        cpuJuggle = 0;
        cpuSeen = -1;
        me.facing = 1;
        foe.facing = -1;
        sparks.clear();
        callouts.clear();
        clock = ROUND_FRAMES;
        boolean intro = round == 1 && practice == 0 && mode != 1;
        roundOver = practice > 0 ? -2 : intro ? -240 : -120;
        if (intro) { // (the first round: their entrances, then the round)
            me.state = foe.state = State.INTRO;
            say(me.style.name() + "  VS  " + foe.style.name(), 40);
        } else if (practice == 0) {
            roundCall();
        }
    }

    private void roundCall() {
        say(mode == 1 ? "SURVIVAL " + (survived + 1) : round == 3 ? "FINAL ROUND" : "ROUND " + round, 40);
        sfx(Sfx.ROUND);
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
        if (hitstop > 0) { // (a hit landing: both frozen a few frames - the weight of it)
            hitstop--;
            hold();
            return;
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
            for (Body b : new Body[] {me, foe}) {
                if (b.state == State.INTRO) b.t++;
            }
            if (roundOver == -121 && me.state == State.INTRO) { // (entrances over: the round)
                me.state = foe.state = State.STAND;
                roundCall();
            }
            if (++roundOver == 0) banner = "";
            if (roundOver == -36) {
                say("FIGHT!", 14);
                sfx(Sfx.FIGHT);
            }
            return;
        }
        if (roundOver > 0) {
            for (Body b : new Body[] {me, foe}) {
                physics(b);
                keepInArena(b, false);
            }
            if (--roundOver == 0) endRound();
            return;
        }
        if (practice == 0 && --clock <= 0) {
            finishRound(me.hp >= foe.hp ? me : foe, "TIME");
            return;
        }
        for (Body b : new Body[] {me, foe}) if (b.comboShow > 0) b.comboShow--;
        if (trialClear > 0 && --trialClear == 0) { // (cleared: on to the next trial)
            if (practice < 7) practice++;
            trialStep = 0;
            say(practiceName(), 30);
        }
        if (practice >= 5 && trialStep > 0 && foe.free() && me.free() && me.combo > 0 && foe.comboShow == 0
                && foe.state == State.STAND) trialStep = 0; // (the combo's over before it was done)
        for (Body b : new Body[] {me, foe}) {
            if (b.in != null && b.in.jumpAt >= 0 && ticks >= b.in.jumpAt) { // (up held long enough: a jump - the way held now)
                b.in.jumpAt = -1;
                jump(b, fwdHeld(b) ? 1 : backHeld(b) ? -1 : 0);
            }
        }
        player(me);
        if (foe.in != null) {
            playback();
            player(foe);
        } else if (practice == 0 || practice == 3) {
            cpu();
        } else {
            dummy();
        }
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
        for (Body b : new Body[] {me, foe}) keepInArena(b, true);
        realign(); // (the line through them again: the camera swinging round as they circle)
        face(me);
        face(foe);
        for (Body b : new Body[] {me, foe}) {
            if (!b.rage && !b.rageUsed && b.hp > 0 && b.hp <= MAX_HP / 4) {
                b.rage = true;
                call("RAGE", b.x, GROUND - 50);
                sfx(Sfx.RAGE);
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
        if (b.free() && !b.airborne() && !b.bt) b.facing = o.x > b.x ? 1 : -1;
    }

    /** Toward the other one, whichever way it's facing now. */
    private int toward(Body b) {
        return other(b, me, foe).x > b.x ? 1 : -1;
    }

    /** Turned round to face them again (snapping the drawn pose round with it, so it's seen to spin). */
    private void faceAgain(Body b) {
        if (!b.bt) return;
        b.bt = false;
        b.facing = toward(b);
        float[][] pp = poseOf(b);
        pp[0][13] = pp[1][13] = 180;
    }

    /** Turning its back on them on purpose (NEKO's 3+4). */
    private void turnAway(Body b) {
        if (!b.free() || b.airborne()) return;
        b.state = State.TURN;
        b.t = -100; // (turning away, not back: see step)
        sfx(Sfx.STEP, 1.2F);
    }

    // ================= the player =================

    private boolean fwdHeld(Body b) {
        return b.in != null && ((b.in.kR && b.facing > 0) || (b.in.kL && b.facing < 0));
    }

    private boolean backHeld(Body b) {
        if (b.in != null) return (b.in.kL && b.facing > 0) || (b.in.kR && b.facing < 0);
        return b.state == State.BLOCK;
    }

    /** Down held (a crouch, for whoever's on a pad). */
    private static boolean crouchHeld(Body b) {
        return b.in != null && b.in.kD;
    }

    private void player(Body b) {
        Pad in = b.in;
        if (!b.free()) return;
        if (b.state == State.RUN) {
            if (!fwdHeld(b)) b.state = State.STAND;
            return;
        }
        if (b.state == State.SIDEWALK) {
            boolean still = b.sideDir > 0 ? in.kU : in.kD;
            if (!still) b.state = State.STAND;
            return;
        }
        if (in.kD) {
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
                boolean holding = b.in != null ? fwdHeld(b) : b.buffered;
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
                    b.state = crouchHeld(b) ? State.CROUCH : State.STAND;
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
            case SIDESTEP -> { // (into the screen: quicker; out of it: slower, but further) - eased in and out
                boolean in = b.sideDir > 0;
                int frames = in ? 20 : 26;
                float far = in ? 15 : 18;
                b.depth += b.sideDir * far * (ease(Math.min(1, b.t / (float) frames)) - ease((b.t - 1) / (float) frames));
                if (b.t >= frames) {
                    boolean holding = b.in != null ? (b.sideDir > 0 ? b.in.kU : b.in.kD) : b.buffered;
                    b.state = holding ? State.SIDEWALK : State.STAND;
                    b.t = 0;
                }
            }
            case SIDEWALK -> { // (circling: picking up speed out of the step)
                b.depth += b.sideDir * Math.min(0.42F, 0.2F + b.t * 0.02F);
                if (b.in == null && !b.buffered) b.state = State.STAND;
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
            case TURN -> { // (turning round to face them - or, from t = -100, turning its back)
                if (b.t < 0 && b.t >= -92) {
                    b.bt = true;
                    b.facing = -toward(b);
                    float[][] pp = poseOf(b);
                    pp[0][13] = pp[1][13] = -180;
                    b.state = State.STAND;
                } else if (b.t >= 8) {
                    faceAgain(b);
                    b.state = State.STAND;
                }
            }
            case STANCE -> { // (a stance: its own moves; back to stand after a while, or on back)
                if (b.stance == 2) b.x += b.facing * (fwdHeld(b) ? 0.15F : backHeld(b) ? -0.12F : 0);
                if (b.t >= 90) {
                    b.state = State.STAND;
                    b.stance = 0;
                }
            }
            case INTRO -> {}
            case KI -> { // (charging up: wide open, then the next hit can't be blocked)
                if (b.t >= 40) {
                    b.state = State.STAND;
                    b.ki = true;
                    call("CHARGED", b.x, GROUND - 46);
                }
            }
            case THROWN -> {
                // ROBO's iron grab: 1+2 in time chains it on (more damage) - unless the one thrown escapes first (1+2)
                Body th = b.thrower;
                if (b.grab == IRON_GRAB && th != null && b.t >= 10 && b.t <= 36) {
                    if (th.in == null && th.chainAt < 0 && random.nextFloat() < 0.06F) th.chainAt = ticks;
                    if (b.in == null && b.escapeAt < 0 && random.nextFloat() < 0.02F * (1 + difficulty())) b.escapeAt = ticks;
                    if (b.escapeAt >= 0 && (th.chainAt < 0 || b.escapeAt < th.chainAt)) {
                        b.escapeAt = th.chainAt = -100;
                        breakThrow(b);
                        call("ESCAPE!", b.x, GROUND - 50);
                        return;
                    }
                    if (th.chainAt >= 0 && b.grab == IRON_GRAB) {
                        b.grab = MELTDOWN;
                        if (th.state == State.ATTACK) th.move = THROWING_CHAIN; // (same frames: just longer)
                        th.chainAt = b.escapeAt = -100;
                        shake = 6;
                        call("CHAIN THROW!", b.x, GROUND - 50);
                        sfx(Sfx.HIT_HEAVY, 0.8F);
                    }
                }
                // where they are: held in front; then up over the shoulder (or up and down in front); slammed
                Move g = b.grab != null ? b.grab : THROW_L;
                b.vy = 0;
                if (th != null) {
                    float k = Mth.clamp((b.t - THROW_LIFT) / (float) (THROW_SLAM - THROW_LIFT), 0, 1);
                    if (b.t > THROW_SLAM && g == MELTDOWN) k = Mth.clamp((b.t - THROW_SLAM) / (float) (CHAIN_SLAM - THROW_SLAM), 0, 1);
                    float arc = Mth.sin(k * Mth.PI);
                    if (overShoulder(g)) {
                        b.x = th.x + th.facing * (-1 + 15 * Mth.cos(k * Mth.PI)); // (from in front, over, to behind)
                        b.y = 30 * arc;
                    } else {
                        b.x = th.x + th.facing * (14 + 4 * k);
                        b.y = (g == MELTDOWN && b.t > THROW_SLAM ? 26 : 34) * arc;
                    }
                    b.depth = th.depth;
                    if (b.t == THROW_SLAM && g == MELTDOWN) { // (the first slam of a chain: then up again)
                        b.hp = Math.max(1, b.hp - 20);
                        shake = 6;
                        sfx(Sfx.HIT_HEAVY, 0.75F);
                        spark(b.x, GROUND - 4, 10);
                    }
                }
                if (b.t >= (g == MELTDOWN ? CHAIN_SLAM : THROW_SLAM)) { // (slammed down)
                    b.breakWith = -1;
                    b.thrower = null;
                    b.y = 0;
                    land(b, th, g);
                    b.grab = null;
                    spark(b.x, GROUND - 4, 12);
                }
            }
            case ATTACK -> {
                Move m = b.move;
                if (m == null) {
                    b.state = State.STAND;
                } else if (b.queued != null && b.t >= m.startup() + m.active() + 2) { // (a string: straight into its next hit)
                    Move q = b.queued;
                    b.queued = b.queued2;
                    b.queued2 = null;
                    attack(b, q, true);
                } else if (b.t >= m.total()) {
                    if (m == THROWING_OVER) { // (turned round with them: facing them, behind it now)
                        b.facing = -b.facing;
                        float[][] pp = poseOf(b);
                        pp[0][13] += 180;
                        pp[1][13] += 180;
                    }
                    if (m.is(TURNS)) { // (it leaves you facing away)
                        b.bt = true;
                        b.facing = -toward(b);
                    }
                    // a crouching move: still crouched after, so standing up from it is while-standing too
                    if (m.is(DUCK)) b.wsUntil = crouchHeld(b) ? -100 : ticks + 12;
                    if (b == me && b.landed == false && b.hitDone == false && practice > 0) info(m, "WHIFF");
                    b.state = crouchHeld(b) ? State.CROUCH : State.STAND;
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

    /** 0 to 1, slow at both ends. */
    private static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
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
                    sfx(Sfx.THUD);
                } else if (b.state == State.JUMP || b.state == State.ATTACK && b.move == AIR_KICK) {
                    b.state = State.STAND;
                    b.move = null;
                }
            }
        }
    }

    private void jump(Body b, int dir) {
        if (b.airborne() || !b.free()) return;
        sfx(Sfx.STEP, 1.3F);
        b.state = State.JUMP;
        b.t = 0;
        b.vy = 1.67F;
        b.y = 0.01F;
        b.vx = dir * b.facing * 0.43F * b.style.speed();
    }

    private void dash(Body b) {
        if ((!b.free() && b.state != State.CROUCH_DASH) || b.airborne()) return;
        sfx(Sfx.STEP);
        b.state = State.DASH;
        b.t = 0;
    }

    private void backdash(Body b) {
        boolean chain = b.state == State.BACKDASH && b.t >= 14; // (cancelled into the next: chained backdashes)
        if ((!b.free() && !chain) || b.airborne()) return;
        sfx(Sfx.STEP, 0.85F);
        b.state = State.BACKDASH;
        b.t = chain ? 4 : 0;
    }

    /** Forward, neutral, down(-forward): a crouch dash (RIKU's). */
    private void crouchDash(Body b) {
        // (out of another: a wavedash, crouch dash after crouch dash)
        if ((!b.free() && b.state != State.DASH && !(b.state == State.CROUCH_DASH && b.t >= 8)) || b.airborne()) return;
        sfx(Sfx.STEP, 0.7F);
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
        sfx(Sfx.STEP, 1.4F);
    }

    /** Forward on the floor: a kip-up, straight onto your feet. */
    private void kipUp(Body b) {
        if (b.state != State.DOWN || b.t < 12) return;
        b.state = State.RISE;
        b.stun = 10;
        sfx(Sfx.STEP, 1.2F);
    }

    /** Getting up off the floor with a roll (back, or to the side). */
    private void roll(Body b, int side) {
        if (b.state != State.DOWN || b.t < 12) return;
        b.state = State.RISE;
        b.stun = 18;
        if (side == 0) b.x -= toward(b) * 10; // (away from them - whichever way it fell)
        else b.depth += side * 12;
    }

    private void sidestep(Body b, int dir) {
        if ((!b.free() && b.state != State.CROUCH) || b.airborne()) return;
        sfx(Sfx.STEP, 1.1F, 0.7F);
        b.state = State.SIDESTEP;
        b.sideDir = dir;
        b.t = 0;
    }

    private void kiCharge(Body b) {
        if (!b.free() || b.airborne() || b.ki) return;
        b.move = null;
        b.state = State.KI;
        sfx(Sfx.KI);
        b.t = 0;
    }

    // ================= attacking =================

    private void attack(Body b, Move m) {
        attack(b, m, false);
    }

    private void attack(Body b, Move m, boolean chained) {
        if (b.state == State.ATTACK && !chained) return; // (mid-move: only the next hit of a string - see chain())
        boolean from = b.free() || b.state == State.DASH || b.state == State.BACKDASH || b.state == State.SIDESTEP
                || b.state == State.CROUCH_DASH || b.state == State.STANCE || (b.state == State.JUMP && m == AIR_KICK)
                || ((b.state == State.DOWN || b.state == State.RISE) && (m == GETUP_KICK || m == GETUP_LOW || m == SPRING_KICK));
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
        if (m.is(CRUSH)) {
            call("POWER CRUSH", b.x, GROUND - 48);
            sfx(Sfx.CRUSH, 0.8F);
        }
        if (m.is(ELECTRIC)) sfx(Sfx.ELECTRIC);
        if (m == RAGE_ART) sfx(Sfx.RAGE, 1.2F);
        sfx(Sfx.WHOOSH, Mth.clamp(1.5F - m.startup() * 0.03F, 0.7F, 1.4F)); // (a swish: quicker moves, higher)
        if (m.is(ELECTRIC) && b == me) call(m == PERFECT_DRAGON ? "PERFECT!" : "", b.x, GROUND - 56);
        if (!chained) b.queued = b.queued2 = null; // (a fresh move: nothing left over from an interrupted string)
        if (b.bt && !m.is(BACKWARD)) faceAgain(b); // (back turned: a turning attack spins you round into it)
        b.stance = 0;
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
        if (b.state != State.ATTACK || cur == null) return; // (buffered from its first frame)
        if (b.queued != null) { // (the next hit already waiting: this is the one after it)
            if (b.queued.next() == null) return;
            for (Move n : b.queued.next()) {
                if (n.button() == button) {
                    b.queued2 = n;
                    return;
                }
            }
            return;
        }
        if (cur.next() == null) return;
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
        if (!connects(b, o, m)) return; // (its limb against their body: reach, height, depth)
        // BLAZE swaying back: highs miss, and mids early on
        if (o.state == State.STANCE && o.stance == 3 && (m.level() == Level.HIGH || m.level() == Level.MID && o.t < 12)) return;
        if (o.state == State.KO || o.state == State.RISE) return;
        if (o.state == State.DOWN && !(m.level() == Level.LOW || m.is(GROUND_HIT))) return; // (only some moves hit someone lying down)
        if (o.state == State.THROWN) return;
        boolean side = Math.abs(o.depth - b.depth) > 7 || o.state == State.SIDESTEP || o.state == State.SIDEWALK;
        if (m.level() == Level.THROW) {
            if (m.is(CROUCH_THROW) != o.crouching() || o.airborne() || o.down() || o.state == State.HIT || o.state == State.AIR || o.state == State.SPLAT) return;
            b.hitDone = true;
            // from behind: a back throw (no breaking it); from the side: a side throw (broken by the arm on that side)
            Move g = o.bt ? BACK_THROW : side && !m.is(BREAK_BOTH) ? SIDE_THROW : m;
            o.state = State.THROWN;
            o.thrower = b;
            o.stun = 0;
            o.breakWith = g == BACK_THROW ? 3 : g == SIDE_THROW ? (b.depth > o.depth ? 0 : 1) : m.is(BREAK_BOTH) ? 2 : m == THROW_L ? 0 : 1;
            if (g != m) call(g.name(), o.x, GROUND - 52);
            o.chainAt = o.escapeAt = -100;
            o.grab = g;
            b.move = overShoulder(g) ? THROWING_OVER : THROWING_FRONT; // (the thrower carries it through)
            b.t = 0;
            b.hitDone = true;
            o.t = 0;
            o.ki = false;
            sfx(Sfx.THROW);
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
            sfx(Sfx.PARRY);
            return;
        }
        b.hitDone = true;
        // a power crush coming out (or ROBO's siege stance): soaks up a high or a mid (still hurts) and keeps going
        if ((o.state == State.ATTACK && o.move != null && o.move.is(CRUSH) && o.t < o.move.startup() + o.move.active()
                || o.state == State.STANCE && o.stance == 2) && m.level() != Level.LOW) {
            int soak = Math.max(1, Math.round(m.damage() * b.style.power()));
            o.hp = Math.max(0, o.hp - soak);
            spark(o.x - o.facing * 4, GROUND - o.y - 20, 6);
            flash = 2;
            sfx(Sfx.CRUSH, 1.2F);
            sfx(Sfx.HIT_LIGHT, 0.8F);
            return;
        }
        // (no guard: ki charged, an unblockable, practice's dummy - or walking in, as the cabinet's fighter does)
        boolean guard = !o.ki && !m.is(UNBLOCKABLE) && !b.ki && !(o == foe && (practice == 1 || practice >= 5)) && !o.advancing && !o.bt;
        boolean blocked;
        if (juggled || o.state == State.DOWN || !guard) {
            blocked = false;
        } else if (m.level() == Level.LOW) {
            blocked = (o.state == State.CROUCH || o.state == State.BLOCK && o.blockLow) && (o.in != null ? o.in.kD : true);
        } else {
            blocked = !o.crouching() && (o.state == State.STAND || o.state == State.BLOCK) && !(o.in != null && fwdHeld(o));
        }
        int left = b.left();
        if (blocked) {
            o.state = State.BLOCK;
            o.blockLow = m.level() == Level.LOW;
            o.blockedLow = o.blockLow;
            o.stun = Math.max(6, left + m.onBlock()); // (so it's as far ahead or behind as its frame data says)
            o.vx = -o.facing * 0.45F;
            spark(o.x - o.facing * 4, GROUND - o.y - (m.level() == Level.LOW ? 6 : 20), 6);
            sfx(Sfx.BLOCK, 0.9F + random.nextFloat() * 0.25F);
            hitstop = Math.max(hitstop, 4);
            if (b == me && practice > 0) info(m, "BLOCK " + plus(m.onBlock()));
            return;
        }
        // a hit: a counter hit if they were in the middle of starting something (or dashing about)
        boolean counter = o.state == State.ATTACK && o.t < (o.move == null ? 0 : o.move.startup() + o.move.active())
                || o.state == State.DASH || o.state == State.BACKDASH || o.state == State.CROUCH_DASH || o.state == State.KI;
        boolean back = o.bt && !juggled && o.state != State.DOWN;
        boolean sideHit = side && !back && !juggled && o.state != State.DOWN;
        float scale = (b.rage ? 1.1F : 1.0F) * (counter ? 1.2F : 1.0F) * b.style.power() * (b.ki ? 1.2F : 1.0F)
                * (back ? 1.15F : sideHit ? 1.1F : 1.0F);
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
        boolean big = dmg >= 14 || m.is(KND) || m.is(CARRY) || m.launch() > 0;
        sfx(big ? Sfx.HIT_HEAVY : Sfx.HIT_LIGHT, 0.9F + random.nextFloat() * 0.2F);
        hitstop = Math.max(hitstop, (big ? 9 : 6) + (counter ? 3 : 0)); // (the hit freezing them both)
        if (back) call("BACK HIT", o.x, GROUND - o.y - 36);
        else if (sideHit) call("SIDE HIT", o.x, GROUND - o.y - 36);
        if (counter) {
            sfx(Sfx.COUNTER);
            call("COUNTER HIT", o.x, GROUND - o.y - 44);
            lastCounter = true;
            flash = 3;
        }
        if (b == me && practice > 0) info(m, m.is(KND) || m.launch() > 0 ? "HIT (DOWN)" : "HIT " + plus(m.onHit() + (counter ? 4 : 0)));
        if (b == me) trialHit(m);
        if (m.is(ELECTRIC)) lastCounter = true; // (a blue spark: it crackles)
        spark(b.x + b.facing * m.reach() * 0.8F, GROUND - b.y - (m.level() == Level.LOW ? 4 : m.level() == Level.HIGH ? 28 : 18), 9);
        o.techable = !(m.is(KND) && m.level() == Level.LOW); // (swept off your feet: no tech roll)
        o.stance = 0;
        if (juggled || m.launch() > 0 || m.is(KND) || m.is(CARRY)) o.bt = false;
        o.crumple = false;
        boolean heavy = m.is(KND) || m.is(CARRY) || m.launch() > 0;
        if (o.state == State.SPLAT && heavy && st().wallBreak() && o.wallSide >= 0) {
            // a wall that breaks: they go through it, into the next area (that wall now further back)
            int ws = o.wallSide;
            arena[ws] += ws % 2 == 0 ? -110 : 110;
            float[] out = {ws == 0 ? -1 : ws == 1 ? 1 : 0, ws == 2 ? -1 : ws == 3 ? 1 : 0};
            float a = spin * SPIN_RAD;
            float along = out[0] * Mth.cos(a) + out[1] * Mth.sin(a); // (that way, along their line)
            o.state = State.AIR;
            o.vy = 1.0F;
            o.vx = (along >= 0 ? 1 : -1) * 1.6F;
            o.y = Math.max(o.y, 6);
            o.wallHits = 0;
            shake = 12;
            flash = 4;
            hitstop = 14;
            call("WALL BREAK!", o.x, GROUND - 56);
            sfx(Sfx.BOOM, 1.3F);
            for (int k = 0; k < 6; k++) spark(o.x + (random.nextFloat() - 0.5F) * 20, GROUND - 10 - random.nextFloat() * 30, 12);
        } else if (st().floorBreak() && !floorBroken && (juggled || o.state == State.DOWN) && o.y < 20 && (heavy || dmg >= 18)) {
            // a floor that breaks: they crash through to the floor below - and the combo goes on
            floorBroken = true;
            o.state = State.AIR;
            o.vy = 0.95F;
            o.vx = b.facing * 0.1F;
            o.y = Math.max(o.y, 2);
            o.screwed = false;
            shake = 14;
            flash = 5;
            hitstop = 16;
            call("FLOOR BREAK!", o.x, GROUND - 56);
            sfx(Sfx.BOOM, 1.1F);
            for (int k = 0; k < 8; k++) spark(o.x + (random.nextFloat() - 0.5F) * 30, GROUND - random.nextFloat() * 8, 14);
        } else if (o.state == State.SPLAT) {
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
            sfx(Sfx.SCREW);
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
            sfx(Sfx.LAUNCH);
        } else if (m.is(KND) || o.airborne()) {
            land(o, b, m);
        } else if (o.state == State.DOWN) {
            o.stun = Math.max(o.stun, 24);
        } else {
            o.state = State.HIT;
            o.hitLevel = m.level() == Level.HIGH ? 0 : m.level() == Level.LOW ? 2 : 1;
            o.stun = Math.max(6, left + m.onHit() + (counter ? 4 : 0) + (back ? 8 : sideHit ? 6 : 0)); // (as far ahead as its frame data says)
            if (sideHit) { // (hit from the side: spun round, back to you)
                o.bt = true;
                o.facing = -toward(o);
            }
            o.vx = b.facing * 0.4F; // (pushed back a little: strings still reach)
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

    // ---- hitboxes: from the fighters' own posed bodies ----

    /** Its body, as posed now: the box round all its bones (along, up, depth), each bone's thickness included. */
    private float[] hurtBox(Body o) {
        float[] p = poseOf(o)[1];
        float lift = lift(o, p);
        float[] box = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Bone bone : skeleton(o, p, LOOKS[0], 0)) {
            if (bone.t() < 1 || bone.decor()) continue; // (not the eyes, not its clothes and hair)
            float r = bone.t() / 2;
            for (float[] q : new float[][] {bone.a(), bone.b()}) {
                float[] w = world(o, p, q, lift);
                box[0] = Math.min(box[0], w[0] - r);
                box[1] = Math.max(box[1], w[0] + r);
                box[2] = Math.min(box[2], w[1] - r);
                box[3] = Math.max(box[3], w[1] + r);
                box[4] = Math.min(box[4], w[2] - r);
                box[5] = Math.max(box[5], w[2] + r);
            }
        }
        return box;
    }

    /** Its striking limb as posed now: the elbow or knee, and the fist or foot (world points). */
    private float[][] strikingLimb(Body b) {
        float[] p = poseOf(b)[1];
        float lift = lift(b, p);
        List<Bone> bones = skeleton(b, p, LOOKS[0], 0);
        int[] sl = limbOf(b.move);
        int base = sl[1] == 1 ? sl[0] * 3 : bones.size() - 6 + sl[0] * 3;
        return new float[][] {world(b, p, bones.get(base + 1).a(), lift), world(b, p, bones.get(base + 2).b(), lift)};
    }

    /** Which limb a move strikes with: side (0 near, 1 far), and leg (1) or arm (0). */
    private static int[] limbOf(Move m) {
        if (m == null) return new int[] {0, 0};
        Anim an = ANIMS.get(m);
        if (an != null) return new int[] {an.side(), an.leg() ? 1 : 0};
        return switch (m.pose()) {
            case JAB, LOWPUNCH, THROW -> new int[] {0, 0};
            case CROSS, UPPER, RAGE, TACKLE, SPIN, PALM -> new int[] {1, 0};
            case LOWKICK, AIRKICK -> new int[] {0, 1};
            default -> new int[] {1, 1};
        };
    }

    private float[] world(Body b, float[] p, float[] q, float lift) {
        float[] r = turn(p, q);
        return new float[] {b.x + r[0] * b.facing, b.y + r[1] + lift, b.depth + r[2]};
    }

    /** The striking limb's thickness (half), and a little give. */
    private static final float STRIKE_R = 3.0F;
    /** Someone standing guarded, side on: each bone (from x, y, to x, y - from their middle) and its thickness. */
    private float[][] guardShape;

    /**
     * Where the front of someone standing guarded is at this height (their guard up high, their feet down low), as far
     * as a strike as thick as STRIKE_R touches it: a move's limb is taken out to its reach less this - so it just
     * touches them at exactly the move's reach, standing guarded; anything of theirs sticking out further, it hits
     * sooner; leaning or ducking away, later or not at all.
     */
    private float guardReach(float height) {
        if (guardShape == null) {
            Body ref = new Body(FIGHTERS[0], 0, 0);
            float[] p = GUARD.clone();
            float lift = lift(ref, p);
            List<float[]> shape = new ArrayList<>();
            for (Bone bone : skeleton(ref, p, LOOKS[0], 0)) {
                if (bone.t() < 1 || bone.decor()) continue;
                float[] a = turn(p, bone.a()), c = turn(p, bone.b());
                shape.add(new float[] {a[0], a[1] + lift, c[0], c[1] + lift, bone.t() / 2});
            }
            guardShape = shape.toArray(new float[0][]);
        }
        float best = -Float.MAX_VALUE;
        for (float[] g : guardShape) {
            float r = g[4] + STRIKE_R;
            for (int k = 0; k <= 8; k++) {
                float x = g[0] + (g[2] - g[0]) * k / 8, y = g[1] + (g[3] - g[1]) * k / 8, dy = y - height;
                if (Math.abs(dy) <= r) best = Math.max(best, x + Mth.sqrt(r * r - dy * dy));
            }
        }
        return best == -Float.MAX_VALUE ? 0 : best - 0.5F;
    }

    /**
     * Does its move reach them? Its striking limb - from elbow or knee out to fist or foot, reaching as far as the move
     * reaches - against every part of their body: head, torso, arms and legs, each as thick as it's drawn. So a kick
     * can catch an outstretched leg, a punch the arm thrown at it; a low only reaches what's low, a high goes over
     * someone crouched, someone juggled is only where they are, someone stepped aside is off the line (unless it's
     * homing). And someone reeling in a combo is never out of a string's reach.
     */
    private boolean connects(Body b, Body o, Move m) {
        int way = b.facing * (m.is(BACKWARD) ? -1 : 1);
        if ((o.x - b.x) * way < -6) return false; // (behind it)
        if (m.level() == Level.THROW) { // (a throw takes hold of the body, whichever way it's facing)
            return (o.x - b.x) * way <= m.reach() && Math.abs(o.depth - b.depth) <= 8 && Math.abs(o.y - b.y) < 10;
        }
        float[][] limb = strikingLimb(b);
        float[] j = limb[0].clone(), tip = limb[1].clone();
        float reachTo = b.x + way * (m.reach() - guardReach((m.level() == Level.LOW ? Math.min(tip[1], b.y + 3) : tip[1]) - o.y));
        // (out to exactly the move's reach along its line - no further, however long the arm)
        tip[0] = reachTo;
        if ((j[0] - b.x) * way < 0 || (j[0] - reachTo) * way > 0) j[0] = b.x + way * 4; // (from just in front of it)
        if (m.level() == Level.LOW) { // (a low sweeps along the floor)
            j[1] = Math.min(j[1], b.y + 6);
            tip[1] = Math.min(tip[1], b.y + 3);
        }
        // (on the line it's thrown along - a spin's limb swings round through it; homing: turning to follow them)
        j[2] = tip[2] = m.is(HOMING) ? o.depth : b.depth;
        float[] p = poseOf(o)[1];
        float lift = lift(o, p);
        for (Bone bone : skeleton(o, p, LOOKS[0], 0)) {
            if (bone.t() < 1 || bone.decor()) continue; // (not the eyes, not its clothes and hair)
            float[] u = world(o, p, bone.a(), lift), v = world(o, p, bone.b(), lift);
            // (depth: that part of them on the strike's line, near enough - then how close, along and up)
            if (Math.abs((u[2] + v[2]) / 2 - tip[2]) > 6 + bone.t() / 2) continue;
            u[2] = v[2] = tip[2];
            if (segDist(j, tip, u, v) <= STRIKE_R + bone.t() / 2) return true;
        }
        // reeling in a combo: a string's next hit still reaches (as far as their middle)
        if (o.state == State.HIT && (o.x - b.x) * way <= m.reach() && Math.abs(o.depth - b.depth) <= 8 && Math.abs(o.y - b.y) < 20
                && m.level() != Level.LOW) return true;
        return false;
    }

    /** The closest two line segments (3D) come to each other. */
    private static float segDist(float[] p1, float[] q1, float[] p2, float[] q2) {
        float[] d1 = {q1[0] - p1[0], q1[1] - p1[1], q1[2] - p1[2]}, d2 = {q2[0] - p2[0], q2[1] - p2[1], q2[2] - p2[2]};
        float[] r = {p1[0] - p2[0], p1[1] - p2[1], p1[2] - p2[2]};
        float a = dot(d1, d1), e = dot(d2, d2), f = dot(d2, r);
        float sc, tc;
        if (a < 1e-6F && e < 1e-6F) {
            sc = tc = 0;
        } else if (a < 1e-6F) {
            sc = 0;
            tc = Mth.clamp(f / e, 0, 1);
        } else {
            float c = dot(d1, r);
            if (e < 1e-6F) {
                tc = 0;
                sc = Mth.clamp(-c / a, 0, 1);
            } else {
                float bb = dot(d1, d2), den = a * e - bb * bb;
                sc = den != 0 ? Mth.clamp((bb * f - c * e) / den, 0, 1) : 0;
                tc = (bb * sc + f) / e;
                if (tc < 0) {
                    tc = 0;
                    sc = Mth.clamp(-c / a, 0, 1);
                } else if (tc > 1) {
                    tc = 1;
                    sc = Mth.clamp((bb - c) / a, 0, 1);
                }
            }
        }
        float dx = p1[0] + d1[0] * sc - (p2[0] + d2[0] * tc), dy = p1[1] + d1[1] * sc - (p2[1] + d2[1] * tc),
                dz = p1[2] + d1[2] * sc - (p2[2] + d2[2] * tc);
        return Mth.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Which throws go over the thrower's shoulder (the rest are slammed down in front). */
    private static boolean overShoulder(Move g) {
        return g == THROW_L || g == BACK_THROW;
    }

    /** Knocked to the floor (thrown, swept, tackled ...). */
    private void land(Body o, Body by, Move m) {
        if (m != null && m.level() == Level.THROW) {
            o.hp = Math.max(0, o.hp - Math.round(m.damage() * (by != null ? by.style.power() : 1.0F)));
            if (by != null) {
                // (where the throw put them: behind, over the shoulder - or in front)
                o.x = overShoulder(m) ? by.x - by.facing * 16 : by.x + by.facing * 18;
                if (by == me && practice == 0) score += 300;
                by.combo = 1;
                by.comboDmg = m.damage();
            }
            o.techable = false; // (thrown down hard: no tech roll)
            shake = 6;
            sfx(Sfx.HIT_HEAVY, 0.7F);
        }
        o.state = State.DOWN;
        o.bt = false;
        o.stance = 0;
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
            sfx(Sfx.KO);
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
        if (mode == 1) { // (survival: one round each - win it and on to the next, with a little health back)
            if (me.wins >= 1) {
                survived++;
                score += 1000 * survived;
                carryHp = Math.min(MAX_HP, me.hp + 40);
                sfx(Sfx.COIN);
                newMatch();
            } else {
                over = true;
            }
            return;
        }
        if (me.wins >= 2) {
            sfx(Sfx.WIN);
            boolean beatBoss = foe.who == BOSS;
            ladder++;
            score += 1500 * ladder;
            if (ladder % PLAYABLE == 0) {
                lap++;
                score += 5000;
            }
            newMatch();
            say((beatBoss ? "CHAMPION! NEXT: " : foe.who == BOSS ? "BOSS: " : "NEXT: ") + foe.style.name(), 70);
        } else if (foe.wins >= 2) {
            over = true;
        } else {
            newRound();
        }
    }

    // ================= the cabinet's fighter =================

    /** Its best answer to having this many frames before you can block again: a launcher if there's time, else 1,2. */
    private Move punisher(Body b, int frames, boolean low) {
        if (frames >= 13 && b.who == BOSS) return DARK_UPPER;
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
        // in a stance: one of its moves, soon
        if (b.state == State.STANCE) {
            if (b.t >= 6 + random.nextInt(20) && dist < 34) {
                attack(b, pickStanceMove(b, random.nextInt(4) + 1));
            } else if (b.t > 40) {
                b.state = State.STAND;
                b.stance = 0;
            }
            return;
        }
        // running at you: a running attack when close
        if (b.state == State.RUN && b.t >= 10 && dist < 30 && random.nextFloat() < 0.15F) {
            attack(b, random.nextInt(3) == 0 ? RUN_SLIDE : random.nextBoolean() ? RUN_CHOP : RUN_KICK);
            return;
        }
        if (!b.free() || b.airborne()) return;
        // back turned: turn round - or, close, a turning attack
        if (b.bt) {
            if (dist < 26 && random.nextFloat() < 0.3F) attack(b, pickStanceMove(b, -random.nextInt(4) - 1));
            else if (random.nextFloat() < 0.25F) {
                b.state = State.TURN;
                b.t = 0;
            }
            return;
        }
        b.buffered = b.state == State.SIDEWALK && random.nextFloat() < 0.98F;
        // you crouching close by: ROBO's crouch throw
        if (b.who == 2 && o.crouching() && o.state == State.CROUCH && dist < 18 && random.nextFloat() < 0.03F) {
            attack(b, SCRAP_GRAB);
            return;
        }
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
        if (b.who == BOSS) { // (NYX: a beam from afar; stepping through the shadows behind you)
            if (dist > 36 && dist < 64 && r < 0.18F) {
                attack(b, VOID_BEAM);
                return;
            }
            if (r > 0.9F && o.free() && !o.airborne()) {
                b.x = o.x - o.facing * 18;
                keepInArena(b, false);
                b.facing = toward(b);
                o.bt = true; // (it's behind you: you're facing away from it)
                flash = 3;
                call("SHADOW STEP", b.x, GROUND - 50);
                sfx(Sfx.ELECTRIC, 0.6F);
                thinkIn = 6;
                return;
            }
        }
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
            else if (r < 0.72F && b.who >= 1 && b.who <= 3) { // (into its stance)
                b.state = State.STANCE;
                b.stance = b.who;
                b.t = 0;
            }
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

    // ---- practice: recording what you do, the dummy playing it back ----

    /** What you did while recording: frame (from the start), key, pressed (1) or let go (0). */
    private final List<int[]> recording = new ArrayList<>();
    private int recordFrom = -1, recordFacing = 1, recordLength, playFrom;
    private boolean recordingNow;

    /** Recording (practice, O): your keys as you press them. */
    private void record(int key, boolean down) {
        if (!recordingNow) return;
        if (ticks - recordFrom > RECORD_MAX) {
            stopRecording();
            return;
        }
        recording.add(new int[] {ticks - recordFrom, key, down ? 1 : 0});
    }

    private static final int RECORD_MAX = 6 * FPS;

    private void startRecording() {
        recording.clear();
        recordingNow = true;
        recordFrom = ticks;
        recordFacing = me.facing;
        foe.in = null;
        say("RECORDING", 20);
    }

    private void stopRecording() {
        recordingNow = false;
        recordLength = Math.max(30, ticks - recordFrom);
        // (anything still held: let go at the end)
        for (int k : new ArrayList<>(myPad.held)) recording.add(new int[] {recordLength - 1, k, 0});
        say(recording.isEmpty() ? "NOTHING RECORDED" : "RECORDED - P: PLAYBACK", 30);
    }

    private String practiceName() {
        return switch (practice) {
            case 0 -> "PRACTICE OFF";
            case 1 -> "PRACTICE: DUMMY";
            case 2 -> "PRACTICE: BLOCK ALL";
            case 3 -> "PRACTICE: CPU";
            case 4 -> "PRACTICE: PLAYBACK";
            default -> "COMBO TRIAL " + (practice - 4);
        };
    }

    // ---- combo trials: a set combo to land, move by move ----

    private Move[] trial() {
        Move[][] mine = switch (me.who) {
            case 1 -> new Move[][] {{POUNCE, JAB, JAB_2, SPIRAL_KICK, LUNGE_PUNCH}};
            case 2 -> new Move[][] {{HOPKICK, JAB, JAB_2, SPIN_FIST, PISTON}};
            case 3 -> new Move[][] {{HOPKICK, JAB, JAB_2, SPIN_FIST, BURNING_FIST}};
            default -> new Move[][] {{PERFECT_DRAGON, JAB, JAB_2, SPIN_FIST, LUNGE_PUNCH}};
        };
        return switch (practice) {
            case 5 -> new Move[] {JAB, CROSS, CROSS_KICK};
            case 6 -> new Move[] {LAUNCHER, JAB, JAB_2, SPIN_FIST, LUNGE_PUNCH};
            default -> mine[0];
        };
    }

    private int trialStep, trialClear;

    /** Your hit landed in a trial: the next step of it, or back to the start. */
    private void trialHit(Move m) {
        if (practice < 5 || trialClear > 0) return;
        Move[] tr = trial();
        if (m == tr[trialStep] && (trialStep == 0 || me.combo == trialStep + 1)) {
            trialStep++;
            if (trialStep >= tr.length) {
                trialClear = 120;
                say("TRIAL CLEAR!", 40);
                sfx(Sfx.WIN);
            }
        } else {
            trialStep = m == tr[0] ? 1 : 0;
        }
    }

    /** The dummy doing what you recorded, over and over (left and right swapped if it faces the other way). */
    private void playback() {
        if (recording.isEmpty() || foe.in == null) return;
        int f = Math.floorMod(ticks - playFrom, recordLength + 40);
        if (f == 0) foe.in = new Pad(); // (each time round: from nothing held)
        boolean swap = foe.facing != recordFacing;
        for (int[] e : recording) {
            if (e[0] != f) continue;
            int key = e[1];
            if (swap && (left(key) || right(key))) key = left(key) ? GLFW.GLFW_KEY_RIGHT : GLFW.GLFW_KEY_LEFT;
            if (e[2] == 1) press(foe, key);
            else release(foe, key);
        }
    }

    /** A stance's move for the cabinet's fighter (button 1-4; negative: back turned). */
    private Move pickStanceMove(Body b, int button) {
        if (button < 0) return new Move[] {BT_BACKFIST, BT_ELBOW, BT_LOW, MULE_KICK}[-button - 1];
        return switch (b.stance) {
            case 1 -> new Move[] {PAW_JAB, POUNCE, TAIL_SWEEP, CAT_FLIP}[button - 1];
            case 2 -> new Move[] {PISTON_JAB, CANNON, TREAD_SWEEP, STOMP_KICK}[button - 1];
            default -> new Move[] {SWAY_JAB, SWAY_BURNER, SWAY_SWEEP, SWAY_KICK}[button - 1];
        };
    }

    /** Practice's dummy: stands there, or blocks everything. */
    private void dummy() {
        Body b = foe, o = me;
        if (b.bt && b.free() && practice == 2) faceAgain(b);
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
        sfx(Sfx.BLOCK, 1.4F);
    }

    // ================= keys =================

    /** Which way's held, as the numpad (relative to its facing): 1 down-back, 2 down, 3 down-forward, 4 back, 5 neutral,
     *  6 forward, 7 up-back, 8 up, 9 up-forward. */
    private static int dir(Body b) {
        Pad in = b.in;
        if (in == null) return 5;
        boolean f = (in.kR && b.facing > 0) || (in.kL && b.facing < 0), bk = (in.kL && b.facing > 0) || (in.kR && b.facing < 0);
        int row = in.kD ? 0 : in.kU ? 2 : 1, col = f ? 2 : bk ? 0 : 1;
        return row * 3 + col + 1;
    }

    /** The way held just changed: remember it (for motions), and see what motion it's finished. */
    private void newDir(Body b) {
        if (b == null) return;
        Pad in = b.in;
        int d = dir(b);
        if (!in.dirs.isEmpty() && in.dirs.get(in.dirs.size() - 1)[0] == d) return;
        in.dirs.add(new int[] {d, ticks});
        if (d != 5) show(in, NOTATION[d], false);
        while (in.dirs.size() > 8) in.dirs.remove(0);
        if (roundOver != 0) return;
        if (d == 3) in.dfAt = ticks;
        // the Korean backdash: a backdash cut short by down(-back), then back again - over and over, quick and safe
        if (b.state == State.BACKDASH && b.t >= 8 && (d == 1 || d == 2)) {
            b.state = State.CROUCH;
            b.t = 0;
            in.kbdUntil = ticks + 10;
        }
        if (motion(in, 15, 6, 5, 6)) dash(b); // f,f
        else if (motion(in, 15, 4, 5, 4) || (b.state == State.BACKDASH && d == 4) || (d == 4 && ticks <= in.kbdUntil)) backdash(b); // b,b (and again: chained)
        if (b.who == 0 && (motion(in, 18, 6, 5, 2) || motion(in, 18, 6, 5, 3) || motion(in, 18, 6, 5, 2, 3))) crouchDash(b); // f,n,d,df
        if (motion(in, 20, 2, 3, 6)) in.qcfAt = ticks; // d,df,f
        if (b.who == 3 && motion(in, 20, 2, 1, 4) && (b.free() || b.state == State.CROUCH)) { // d,db,b: BLAZE's sway
            b.state = State.STANCE;
            b.stance = 3;
            b.t = 0;
            sfx(Sfx.STEP, 0.8F);
        }
    }

    private static final String[] NOTATION = {"", "db", "d", "df", "b", "n", "f", "ub", "u", "uf"};

    /** Practice's input display: what was pressed, as the move lists write it (buttons together: 1+2). */
    private void show(Pad in, String text, boolean button) {
        if (practice == 0) return;
        if (button && !in.shown.isEmpty()) {
            Object[] last = in.shown.get(in.shown.size() - 1);
            if ((Boolean) last[2] && ticks - (Integer) last[1] <= 3) { // (buttons a frame or two apart: together)
                last[0] = last[0] + "+" + text.substring(text.lastIndexOf('+') + 1);
                return;
            }
        }
        in.shown.add(new Object[] {text, ticks, button});
        while (in.shown.size() > 9) in.shown.remove(0);
    }

    /** The last few ways held were these, in this order, all within so many frames. */
    private boolean motion(Pad in, int window, int... seq) {
        int n = in.dirs.size();
        if (n < seq.length) return false;
        for (int i = 0; i < seq.length; i++) {
            if (in.dirs.get(n - seq.length + i)[0] != seq[i]) return false;
        }
        return ticks - in.dirs.get(n - seq.length)[1] <= window + 4 && ticks - in.dirs.get(n - seq.length + 1)[1] <= window;
    }

    private boolean together(Pad in, int button, int other) {
        return in.held.contains(BUTTON_KEYS[other]) && ticks - in.pressedAt[other] <= 4 || other == button;
    }

    private static final int[] BUTTON_KEYS = {0, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K};

    @Override
    public void keyPressed(int key) {
        boolean repeat = !myPad.held.add(key); // (a held key repeating: not a new press)
        if (picking) {
            if (repeat) return;
            boolean ok = key == GLFW.GLFW_KEY_J || key == GLFW.GLFW_KEY_U || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER;
            if (pickPhase == 0) { // (the fighter, and up/down: the mode)
                if (left(key)) pick = (pick + PLAYABLE - 1) % PLAYABLE;
                if (right(key)) pick = (pick + 1) % PLAYABLE;
                if (up(key) || down(key)) mode = 1 - mode;
                if (left(key) || right(key) || up(key) || down(key)) sfx(Sfx.BLIP);
                if (ok) {
                    sfx(Sfx.SELECT);
                    pickPhase = 1;
                }
            } else { // (the stage - or random)
                if (left(key)) stageChoice = (stageChoice + PICKABLE.length) % (PICKABLE.length + 1);
                if (right(key)) stageChoice = (stageChoice + 1) % (PICKABLE.length + 1);
                if (left(key) || right(key)) sfx(Sfx.BLIP);
                if (key == GLFW.GLFW_KEY_K || key == GLFW.GLFW_KEY_I) {
                    pickPhase = 0;
                    sfx(Sfx.BLIP, 0.8F);
                } else if (ok) {
                    sfx(Sfx.SELECT);
                    picking = false;
                    survived = 0;
                    carryHp = MAX_HP;
                    newMatch();
                }
            }
            return;
        }
        if (repeat) return;
        if (key == GLFW.GLFW_KEY_H) { // (the move list: two pages, then gone)
            showList = !showList;
            listSel = listTop = 0;
            sfx(Sfx.BLIP);
            return;
        }
        if (key == GLFW.GLFW_KEY_O && me != null && practice > 0 && roundOver == 0) { // (practice: record, then O again to stop)
            if (recordingNow) stopRecording();
            else startRecording();
            sfx(Sfx.BLIP, 1.3F);
            return;
        }
        if (key == GLFW.GLFW_KEY_P && me != null) {
            // practice: off, the dummy stands, it blocks all, it fights, it plays back what you recorded, the trials
            practice = (practice + 1) % 8;
            if (practice == 4 && recording.isEmpty()) practice = 5; // (nothing recorded: no playback)
            sfx(Sfx.BLIP);
            frameInfo = "";
            recordingNow = false;
            foe.in = null;
            trialStep = 0;
            trialClear = 0;
            say(practiceName(), 30);
            me.hp = foe.hp = MAX_HP;
            if (practice == 4) {
                foe.in = new Pad();
                playFrom = ticks;
            }
            if (practice == 0) {
                me.wins = foe.wins = 0;
                round = 0;
                newRound();
            }
            return;
        }
        if (showList) { // (the move list: W/S a row, A/D a tab)
            if (me == null) return;
            int rows = listRows(me, listTab).size();
            if (up(key)) listSel = Math.max(0, listSel - 1);
            if (down(key)) listSel = Math.min(rows - 1, listSel + 1);
            if (left(key) || right(key)) {
                listTab = Math.floorMod(listTab + (right(key) ? 1 : -1), LIST_TABS.length);
                listSel = listTop = 0;
            }
            if (up(key) || down(key) || left(key) || right(key)) sfx(Sfx.BLIP, 1.2F, 0.6F);
            return;
        }
        if (me == null) return;
        record(key, true);
        press(me, key);
    }

    /** A key pressed on a fighter's pad: moves, motions, throws, everything. */
    private void press(Body b, int key) {
        Pad in = b.in;
        if (in == null) return;
        if (b != me && !in.held.add(key)) return;
        if (left(key)) in.kL = true;
        if (right(key)) in.kR = true;
        if (up(key)) in.kU = true;
        if (down(key)) in.kD = true;
        if (left(key) || right(key) || up(key) || down(key)) newDir(b);
        if (roundOver != 0) return;
        if ((down(key) || left(key) || right(key)) && dir(b) == 3) b.parryAt = ticks; // (df just as a low comes in)
        if (up(key)) {
            // as in Tekken: hold up to jump (straight up, or with a direction that way - after a moment's crouch, so
            // up-forward + 4 can be a hopkick); tap it twice to sidestep into the screen (hold the second: sidewalk)
            in.upAt = ticks;
            if (b.state == State.DOWN) {
                roll(b, 1);
            } else if (ticks - in.upTapAt <= 15 && !(in.kL || in.kR)) {
                in.jumpAt = -1;
                in.upTapAt = -100;
                sidestep(b, 1);
            } else if (b.free() && !b.airborne()) {
                in.jumpAt = ticks + 9; // (its way: whatever's held when it goes)
            }
        }
        if (down(key)) {
            in.downAt = ticks;
            if (b.state == State.DOWN) roll(b, -1);
        }
        boolean dirKey = left(key) || right(key) || up(key) || down(key);
        if (b.state == State.DOWN && (left(key) || right(key))) {
            boolean towards = (right(key) ? 1 : -1) == toward(b);
            if (towards) kipUp(b); // (forward: straight up onto your feet)
            else roll(b, 0); // (back: rolling away)
        }
        if (dirKey && b.bt && b.free() && !(up(key) && (in.kL || in.kR))) { // (back turned: a direction turns you round)
            b.state = State.TURN;
            b.t = 0;
        }
        if (b.state == State.STANCE && (left(key) || right(key)) && backHeld(b)) { // (back: out of the stance)
            b.state = State.STAND;
            b.stance = 0;
        }
        int button = key == GLFW.GLFW_KEY_U ? 1 : key == GLFW.GLFW_KEY_I ? 2 : key == GLFW.GLFW_KEY_J ? 3 : key == GLFW.GLFW_KEY_K ? 4 : 0;
        if (button > 0) {
            in.pressedAt[button] = ticks;
            int dd = dir(b);
            show(in, (dd == 5 ? "" : NOTATION[dd] + "+") + button, true);
        }
        if (b.state == State.THROWN) { // (breaking a throw: 1, 2 or 1+2 - whichever arm's grabbing you)
            boolean both = together(in, button, 1) && together(in, button, 2);
            boolean right = b.breakWith == 2 ? both : button == b.breakWith + 1;
            if (right && b.t <= THROW_LIFT) breakThrow(b);
            else if (both && b.grab == IRON_GRAB && b.t >= 10) b.escapeAt = ticks; // (escaping the chain coming)
            return;
        }
        // throwing ROBO's iron grab: 1+2 in time chains it on
        Body o = other(b, me, foe);
        if (o.state == State.THROWN && o.thrower == b && o.grab == IRON_GRAB && together(in, button, 1) && together(in, button, 2)
                && o.t >= 10 && o.t <= 36) {
            b.chainAt = ticks;
            return;
        }
        if (key == GLFW.GLFW_KEY_L) {
            attack(b, RAGE_ART);
            return;
        }
        if (button == 0) return;
        // on the floor: a punch as you land rolls you up (a tech); a kick is a get-up kick (3 low, 4 mid)
        if (b.state == State.DOWN || b.state == State.RISE) {
            if (button <= 2) {
                tech(b);
            } else if (together(in, button, 3) && together(in, button, 4)) { // (3+4: a spring kick up off the floor)
                if (b.state == State.ATTACK) b.state = State.DOWN;
                b.state = State.DOWN;
                attack(b, SPRING_KICK);
            } else {
                attack(b, button == 3 ? GETUP_LOW : GETUP_KICK);
            }
            return;
        }
        if (b.state == State.ATTACK && (b.move == GETUP_LOW || b.move == GETUP_KICK) && b.t <= 4 && button >= 3
                && together(in, button, 3) && together(in, button, 4)) { // (3 then 4 just after: still a spring kick)
            b.state = State.DOWN;
            attack(b, SPRING_KICK);
            return;
        }
        int d = dir(b);
        // buttons together (pressed within a few frames, so the first may have already started): ki charge (all
        // four), throws, the rage art and rage drive, power crushes, command moves
        boolean fresh = b.state == State.ATTACK && b.t <= 4 && b.move != null;
        boolean p12 = together(in, button, 1) && together(in, button, 2) && (button == 1 || button == 2);
        Move pair = null;
        if (together(in, button, 1) && together(in, button, 2) && together(in, button, 3) && together(in, button, 4)) {
            b.state = fresh ? State.STAND : b.state;
            kiCharge(b);
            return;
        } else if (button == 1 && together(in, 1, 3) || button == 3 && together(in, 3, 1)) {
            pair = THROW_L;
        } else if (button == 2 && together(in, 2, 4) || button == 4 && together(in, 4, 2)) {
            pair = THROW_R;
        } else if (p12 && b.rage && !b.rageUsed && d == 3) {
            pair = RAGE_ART; // (df+1+2)
        } else if (p12 && b.rage && !b.rageUsed && rageDriveInput(b, d)) {
            pair = rageDrive(b);
        } else if (p12) {
            pair = pairMove(b, d);
        } else if (button >= 3 && together(in, button, 3) && together(in, button, 4)) {
            // 3+4: d+3+4 a stomp; NEKO: f+3+4 the cat stance, 3+4 turning its back; ROBO: b+3+4 the siege stance
            if (fresh) {
                b.state = State.STAND;
                b.move = null;
            }
            if (d == 2 || d == 1 || d == 3) {
                pair = STOMP;
            } else if (b.who == 1 && d == 6 || b.who == 2 && (d == 4 || d == 7)) {
                if (b.state != State.ATTACK && (b.free() || b.state == State.DASH)) {
                    b.state = State.STANCE;
                    b.stance = b.who == 1 ? 1 : 2;
                    b.t = 0;
                    call(b.who == 1 ? "CAT STANCE" : "SIEGE", b.x, GROUND - 46);
                }
                return;
            } else if (b.who == 1) {
                turnAway(b);
                return;
            }
        }
        if (pair != null) {
            if (fresh) {
                b.queued = null;
                b.move = null;
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
        if (in.jumpAt >= 0) {
            in.jumpAt = -1;
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
            case 0 -> d == 6 ? TWIN_PALM : back ? IRON_PALM : null;
            case 2 -> d == 3 ? IRON_GRAB : d == 6 ? PISTON : d == 2 ? GROUND_SLAM : d == 1 ? SCRAP_GRAB : back ? IRON_PALM : null;
            case 4 -> d == 6 ? VOID_BEAM : back ? IRON_PALM : null;
            case 3 -> d == 6 && b.state == State.DASH ? HEAT_RUSH : back ? IRON_PALM : null;
            default -> back ? IRON_PALM : null;
        };
    }

    private boolean rageDriveInput(Body b, int d) {
        Pad in = b.in;
        return switch (b.who) {
            case 2 -> d == 4 || d == 1 || d == 7; // (b+1+2)
            case 3 -> ticks - in.qcfAt <= 10; // (d,df,f+1+2)
            default -> d == 6; // (f+1+2)
        };
    }

    /** A button, with the way held (and what it's in the middle of): which move. */
    private Move pickMove(Body b, int d, int button) {
        Pad in = b.in;
        if (b.bt) return button == 1 ? BT_BACKFIST : button == 2 ? BT_ELBOW : button == 3 ? BT_LOW : MULE_KICK; // (back turned)
        if (b.state == State.STANCE) {
            return switch (b.stance) {
                case 1 -> button == 1 ? PAW_JAB : button == 2 ? POUNCE : button == 3 ? TAIL_SWEEP : CAT_FLIP;
                case 2 -> button == 1 ? PISTON_JAB : button == 2 ? CANNON : button == 3 ? TREAD_SWEEP : STOMP_KICK;
                default -> button == 1 ? SWAY_JAB : button == 2 ? SWAY_BURNER : button == 3 ? SWAY_SWEEP : SWAY_KICK;
            };
        }
        if (b.state == State.RUN && b.t >= 10) return button <= 2 ? RUN_CHOP : button == 3 ? RUN_SLIDE : RUN_KICK; // (running)
        if (b.who == 0 && d == 4 && button == 4) return DEMON_HEEL;
        if (b.who == 4 && d == 3 && button == 2) return DARK_UPPER;
        // RIKU's crouch dash: 1 snap elbow, 2 dragon upper (2 on the very frame you hit df: the perfect one), 3/4 hell sweep
        if (b.state == State.CROUCH_DASH) {
            if (button == 2) return ticks - in.dfAt <= 1 ? PERFECT_DRAGON : DRAGON_UPPER;
            return button == 1 ? SNAP_ELBOW : HELL_SWEEP;
        }
        // BLAZE: d,df,f+2 - the burning fist
        if (b.who == 3 && button == 2 && ticks - in.qcfAt <= 10) return BURNING_FIST;
        // NEKO: out of a sidestep, 1 or 2 a claw swipe; dashing, 3 a slide; b+3 a backflip
        if (b.who == 1) {
            if ((b.state == State.SIDESTEP || b.state == State.SIDEWALK) && button <= 2) return CLAW_SWIPE;
            if ((b.state == State.DASH || b.state == State.RUN) && button == 3) return CAT_SLIDE;
            if (d == 4 && button == 3) return BACKFLIP;
        }
        if (b.who == 3 && d == 4 && button == 4) return FLAME_HEEL;
        if (ticks <= b.wsUntil && !in.kD && (b.state == State.STAND || b.state == State.CROUCH)) {
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
        myPad.held.remove(key);
        if (me == null || picking) return;
        record(key, false);
        release(me, key);
    }

    private void release(Body b, int key) {
        Pad in = b.in;
        if (in == null) return;
        if (b != me) in.held.remove(key);
        if (left(key)) in.kL = false;
        if (right(key)) in.kR = false;
        if (up(key)) {
            in.kU = false;
            // let go before the jump went (no direction held): a tap - the first of a double tap
            if (in.jumpAt >= 0 && !(in.kL || in.kR)) {
                in.jumpAt = -1;
                in.upTapAt = ticks;
            }
        }
        boolean wasDown = down(key) && in.kD;
        if (down(key)) in.kD = false;
        if (left(key) || right(key) || up(key) || down(key)) newDir(b);
        if (wasDown && roundOver == 0 && b.state == State.CROUCH) {
            // a quick tap: a sidestep out of the screen; a held crouch let go: standing up - the while-standing moves
            if (ticks - in.downAt < 10) sidestep(b, -1);
            else b.wsUntil = ticks + 12;
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
            {0xC83C1E, 0x282830, 0xDCAA82, 0x1E1E1E, 0x1E1E1E, 0xFF8C28, 0xFFC83C},
            {0x2A1A3A, 0x1A1024, 0x9A8AB0, 0x8A3AFF, 0x1A1024, 0xE0D0FF, 0xFF3AFF}};

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
        oTX = tX;
        oTZ = tZ;
        float sep = Math.abs(me.x - foe.x);
        camU += ((me.x + foe.x) / 2 - camU) * 0.091F; // (as quick a frame as a quarter a 20th)
        camV += ((me.depth + foe.depth) / 2 - camV) * 0.091F;
        // a rage art: the camera swings round and in on it
        boolean art = false;
        for (Body b : new Body[] {me, foe}) {
            if (b.state == State.ATTACK && b.move == RAGE_ART && b.t < b.move.startup() + b.move.active() + 10) art = true;
        }
        camD += ((art ? 105 : Mth.clamp(sep * 0.8F + 95, 130, 240)) - camD) * (art ? 0.1F : 0.053F);
        for (Body b : new Body[] {me, foe}) {
            b.ox = b.x;
            b.oy = b.y;
            b.oDepth = b.depth;
            float[][] p = poseOf(b);
            System.arraycopy(p[1], 0, p[0], 0, P);
            float[] want = target(b);
            boolean snap = b.state == State.ATTACK && b.move != null && b.t >= b.move.startup() - 4 && b.t <= b.move.startup() + b.move.active();
            // (a frame's share of easing there: an attack follows its own curve closely, snapping out as it hits)
            float k = snap ? 0.62F : b.state == State.ATTACK ? 0.5F : 0.28F;
            for (int i = 0; i < P; i++) {
                float d = want[i] - p[1][i];
                if (i == 12 || i == 13) d = Mth.wrapDegrees(d);
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
        oTX = tX;
        oTZ = tZ;
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
                // stepping across: the feet in time with how far it's gone (no skating), body turned a little into
                // the step, bobbing as each foot lands, guard kept up and eyes on them
                float phase = b.depth * 0.55F;
                p[13] = 24 * b.sideDir;
                p[3] = 16 + 16 * Mth.sin(phase);
                p[5] = -12 - 16 * Mth.sin(phase);
                p[4] = -24 - 20 * Math.max(0, Mth.cos(phase));
                p[6] = -22 - 20 * Math.max(0, -Mth.cos(phase));
                p[1] = 2.2F + 1.4F * Math.abs(Mth.sin(phase));
                p[0] = 8;
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
                if (b.crouching() || (crouchHeld(b))) crouch(p);
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
                float k = Mth.clamp(1 - b.t / 14.0F, 0.3F, 1); // (the jolt of it, easing off)
                if (b.hitLevel == 0) { // (a high: head snapped back)
                    p[0] = -22 * k;
                    p[11] = -32 * k;
                    p[7] = 15;
                    p[8] = 30;
                    p[9] = 5;
                    p[10] = 25;
                } else if (b.hitLevel == 1) { // (a mid: doubled over it)
                    p[0] = 32 * k;
                    p[1] = 3;
                    p[11] = 20 * k;
                    p[7] = 20;
                    p[8] = 115;
                    p[9] = 15;
                    p[10] = 120;
                    p[3] = 30;
                    p[4] = -40;
                } else { // (a low: a leg buckling)
                    p[1] = 5 * k;
                    p[0] = 12;
                    p[3] = 10;
                    p[4] = -70 * k;
                    p[7] = 45;
                    p[9] = 40;
                }
            }
            case AIR -> {
                // (screwed: spinning over and over, turned side-on)
                p[12] = b.screwed ? 60 + b.t * 10 : 40 + b.airHits * 30 + b.t * 2;
                if (b.screwed) p[13] = 55;
                float fl = Mth.sin(b.t * 0.35F); // (limbs flailing as they tumble)
                p[7] = 130 + 20 * fl;
                p[8] = 20 + 15 * fl;
                p[9] = 150 - 20 * fl;
                p[10] = 10;
                p[3] = 30 + 15 * fl;
                p[4] = -20 - 20 * fl;
                p[5] = 10 - 15 * fl;
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
            case THROWN -> { // (grabbed and struggling; then flipped over through the air, limbs flying)
                Move g = b.grab != null ? b.grab : THROW_L;
                if (b.t <= THROW_LIFT) {
                    p[0] = -15;
                    p[11] = -20;
                    p[7] = 60 + 10 * Mth.sin(b.t * 0.9F);
                    p[9] = 60 - 10 * Mth.sin(b.t * 0.9F);
                    p[8] = p[10] = 40;
                    p[3] = 10;
                    p[4] = -30;
                } else {
                    float k = Mth.clamp((b.t - THROW_LIFT) / (float) (THROW_SLAM - THROW_LIFT), 0, 1);
                    float spinTo = -272;
                    if (b.t > THROW_SLAM && g == MELTDOWN) { // (up again and over a second time)
                        k = Mth.clamp((b.t - THROW_SLAM) / (float) (CHAIN_SLAM - THROW_SLAM), 0, 1);
                        p[12] = -272 - 360 * k;
                    } else {
                        p[12] = spinTo * k;
                    }
                    float fl = Mth.sin(b.t * 0.5F);
                    p[7] = 150 + 20 * fl;
                    p[9] = 160 - 20 * fl;
                    p[8] = 20;
                    p[10] = 30;
                    p[3] = 50 + 20 * fl;
                    p[4] = -40;
                    p[5] = 20 - 20 * fl;
                    p[6] = -60;
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
            case STANCE -> {
                if (b.stance == 1) { // (the cat: crouched low, forward, paws up)
                    crouch(p);
                    p[0] = 38;
                    p[7] = 70;
                    p[8] = 60;
                    p[9] = 60;
                    p[10] = 70;
                } else if (b.stance == 2) { // (siege: wide, planted, fists high)
                    p[1] = 5;
                    p[3] = 40;
                    p[4] = -40;
                    p[5] = -30;
                    p[6] = -40;
                    p[7] = 80;
                    p[8] = 120;
                    p[9] = 80;
                    p[10] = 125;
                } else { // (sway: leaning right back)
                    p[0] = -30;
                    p[11] = -10;
                    p[3] = 35;
                    p[5] = -30;
                    p[7] = 40;
                    p[9] = 30;
                }
            }
            case TURN -> p[13] = b.t >= 0 ? 0 : -90;
            case INTRO -> intro(b, p);
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
                switch (b.who) {
                    case 1 -> { // (both arms up, a bounce)
                        p[7] = p[9] = 165;
                        p[8] = p[10] = 15;
                        p[1] = 1.5F + 2 * Math.abs(Mth.sin(w));
                    }
                    case 2 -> { // (arms folded, head up)
                        p[7] = p[9] = 70;
                        p[8] = p[10] = 150;
                        p[11] = -15;
                        p[0] = -4;
                    }
                    case 3 -> { // (a fist pumped)
                        p[7] = 120 + 40 * Mth.sin(w * 0.8F);
                        p[8] = 40;
                        p[9] = 30;
                        p[10] = 100;
                    }
                    case 4 -> { // (floating, arms wide)
                        p[7] = p[9] = 110;
                        p[8] = p[10] = 5;
                        p[11] = -20;
                        p[1] = -2;
                    }
                    default -> { // (one fist high)
                        p[7] = 170;
                        p[8] = 10;
                        p[9] = 30;
                        p[10] = 100;
                        p[0] = -5;
                        p[11] = -10;
                    }
                }
            }
            default -> {
                if (b.state == State.STAND && b.in != null && (b.in.kL || b.in.kR) || b.state == State.STAND && b != me && thinkIn > 0 && Math.abs(me.x - foe.x) > 40) {
                    p[3] = 18 + 14 * Mth.sin(w);
                    p[5] = -14 - 14 * Mth.sin(w);
                    p[4] = -22 - 18 * Math.max(0, Mth.cos(w));
                    p[6] = -20 - 18 * Math.max(0, -Mth.cos(w));
                } else {
                    p[1] += Mth.sin(ticks * 0.05F) * 0.6F; // (breathing, bouncing a little on its feet)
                }
            }
        }
        Anim an = m == null ? null : ANIMS.get(m);
        if (b.state == State.ATTACK && an != null) {
            applyAnim(p, an, m, b.t);
        } else if (b.state == State.ATTACK && m != null) {
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

    /** Its entrance before the first round (each its own). */
    private void intro(Body b, float[] p) {
        float k = Mth.clamp(b.t / 60.0F, 0, 1);
        switch (b.who) {
            case 1 -> { // (a stretch, then a wave)
                p[7] = 160;
                p[8] = 30 + 40 * Mth.sin(b.t * 0.3F);
                p[0] = -6;
            }
            case 2 -> { // (fists together, a bow of the head)
                p[7] = 60;
                p[9] = 60;
                p[8] = p[10] = 140;
                p[11] = 25 * k;
            }
            case 3 -> { // (fist to palm, flames)
                p[7] = 75;
                p[8] = 100;
                p[9] = 70;
                p[10] = 110;
                p[0] = 10 * Mth.sin(b.t * 0.15F);
            }
            case 4 -> { // (arms spread, hovering)
                p[7] = 100;
                p[9] = 100;
                p[8] = p[10] = 10;
                p[11] = -15;
            }
            default -> { // (a bow)
                p[0] = 40 * Mth.sin(Mth.clamp(b.t / 40.0F, 0, 1) * Mth.PI);
                p[7] = p[9] = 15;
                p[8] = p[10] = 30;
            }
        }
    }


    // ---- animations: each move its own ----

    /**
     * A move's animation: the limb it strikes with (side 0 near, 1 far; leg or arm), whether it starts from a crouch,
     * and its keys - w the wind-up (just over halfway to the hit), W a second wind-up (just before it), h the strike
     * (held while it's out), f the follow-through, r the recovery - each a few pose angles (the rest stay as they are).
     */
    private record Anim(int side, boolean leg, boolean crouched, char[] at, float[][] keys) {}

    private static final java.util.Map<Move, Anim> ANIMS = new java.util.IdentityHashMap<>();

    /** "side A|L [C] | w: i=v i=v | h: ... | f: ... | r: ..." */
    private static void anim(Move m, String spec) {
        String[] parts = spec.split("\\|");
        String[] head = parts[0].trim().split("\\s+");
        List<Character> at = new ArrayList<>();
        List<float[]> keys = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            float[] k = new float[P];
            java.util.Arrays.fill(k, Float.NaN);
            for (String kv : part.substring(2).trim().split("\\s+")) {
                String[] x = kv.split("=");
                k[Integer.parseInt(x[0])] = Float.parseFloat(x[1]);
            }
            at.add(part.charAt(0));
            keys.add(k);
        }
        char[] ats = new char[at.size()];
        for (int i = 0; i < ats.length; i++) ats[i] = at.get(i);
        ANIMS.put(m, new Anim(Integer.parseInt(head[0]), head[1].equals("L"), head.length > 2 && head[2].equals("C"), ats, keys.toArray(new float[0][])));
    }

    static {
        anim(THROWING_OVER, "0 A | w: 7=92 8=25 9=92 10=25 0=8 2=2 | W: 7=100 9=100 0=4 | h: 7=130 8=40 9=130 10=40 0=-8 13=-30 1=3 | f: 7=165 9=165 0=-18 13=-90 1=1 | r: 7=60 8=20 9=60 10=20 0=38 13=-180 1=4");
        anim(THROWING_FRONT, "0 A | w: 7=92 8=25 9=92 10=25 0=8 2=2 | W: 7=100 9=100 0=2 1=3 | h: 7=140 8=30 9=140 10=30 0=-14 1=4 | f: 7=175 9=175 0=-20 1=2 | r: 7=70 8=10 9=70 10=10 0=45 1=6 2=3");
        anim(THROWING_CHAIN, "0 A | w: 7=92 8=25 9=92 10=25 0=8 2=2 | W: 7=100 9=100 0=2 1=3 | h: 7=140 8=30 9=140 10=30 0=-14 1=4 | f: 7=175 9=175 0=-20 1=2 | r: 7=70 8=10 9=70 10=10 0=45 1=6 2=3");
        anim(JAB, "0 A   | w: 7=50 8=85 0=8 | h: 7=88 8=3 0=12 13=-6 2=1 | r: 7=55 8=70");
        anim(JAB_2, "0 A   | w: 7=55 8=75 2=2 | h: 7=90 8=2 0=14 2=3 13=-8 | r: 7=55 8=70 2=1");
        anim(ELBOW_FINISH, "1 A   | w: 9=30 10=130 1=4 0=10 | h: 9=150 10=95 0=-6 1=0 13=-25 11=-8 | r: 9=90 10=110");
        anim(CROSS, "1 A   | w: 9=40 10=95 13=8 | h: 9=88 10=2 0=16 2=2 13=-22 | r: 9=50 10=100");
        anim(CROSS_KICK, "1 L | w: 5=60 6=-110 13=20 0=-4 | h: 5=125 6=-6 0=-26 13=-25 9=-10 10=40 | f: 5=100 6=-30 13=-55 | r: 5=20 6=-40");
        anim(CROSS_LOW, "0 L   | w: 3=50 4=-80 1=3 | h: 3=55 4=-8 1=5 0=18 13=-10 | r: 3=30 4=-40");
        anim(MID_KICK, "1 L | w: 5=55 6=-100 13=15 | h: 5=92 6=-4 0=-14 13=-20 | r: 5=30 6=-60");
        anim(HIGH_KICK, "0 L   | w: 3=70 4=-110 0=-4 | h: 3=128 4=-6 0=-24 13=-15 7=10 | r: 3=50 4=-60");
        anim(WHIRL_KICK, "1 L | w: 13=160 5=40 6=-60 | h: 13=350 5=115 6=-5 0=-20 | f: 13=390 5=60 | r: 13=400 5=10");
        anim(HOMING_KICK, "1 L | w: 13=40 5=50 6=-90 0=4 | h: 13=-20 5=95 6=-10 0=-18 2=2 | f: 13=-60 5=70 | r: 13=-20 5=20");
        anim(LUNGE_PUNCH, "1 A   | w: 9=30 10=110 1=4 | h: 9=92 10=0 0=30 2=5 1=3 3=50 4=-50 5=-30 13=-25 | r: 9=60 10=90 0=15");
        anim(SPIRAL_KICK, "1 L | w: 13=150 1=4 5=30 | h: 13=345 1=-3 5=100 6=-8 3=40 4=-90 0=-10 | f: 13=380 | r: 13=390 1=1");
        anim(SPIN_FIST, "1 A | w: 13=160 9=20 10=120 | h: 13=340 9=95 10=8 | f: 13=375 9=70 | r: 13=380 9=30 10=110");
        anim(IRON_PALM, "1 A   | w: 7=20 8=120 9=20 10=120 1=5 0=-8 | h: 7=85 8=0 9=82 10=0 0=22 2=4 1=4 3=45 4=-40 | r: 7=50 9=50 0=10");
        anim(CROUCH_JAB, "0 A C | w: 7=45 8=90 | h: 7=78 8=4 0=20 | r: 7=50 8=80");
        anim(CROUCH_STRAIGHT, "1 A C | w: 9=40 10=100 | h: 9=80 10=2 0=24 13=-20 2=2 | r: 9=45 10=90");
        anim(LOW_KICK, "0 L C | w: 3=50 4=-100 | h: 3=40 4=-4 1=8 0=12 | r: 3=55 4=-90");
        anim(TOE_KICK, "1 L C | w: 5=40 6=-100 | h: 5=35 6=-2 0=10 | r: 5=30 6=-110");
        anim(SWEEP, "1 L C | w: 13=-150 1=12 0=25 | h: 13=-345 1=12 5=88 6=0 0=28 3=60 4=-130 | f: 13=-380 | r: 13=-390 5=40");
        anim(MID_CHECK, "0 A   | w: 7=40 8=95 1=3 | h: 7=82 8=6 0=22 1=4 2=3 13=-8 | r: 7=50 8=80");
        anim(LAUNCHER, "1 A   | w: 1=8 0=18 9=10 10=70 3=60 4=-90 | h: 1=-1 0=-12 9=168 10=18 13=-18 2=2 11=-10 | f: 9=150 | r: 9=80 10=100 0=0");
        anim(FRONT_KICK, "0 L   | w: 3=85 4=-110 0=2 | h: 3=88 4=-4 0=-14 2=-1 | r: 3=40 4=-70");
        anim(STEP_KICK, "1 L   | w: 5=70 6=-110 2=3 | h: 5=86 6=-2 0=-10 2=4 3=10 | r: 5=30 6=-60");
        anim(RISING_JAB, "0 A C | w: 1=5 7=50 8=80 | h: 1=1 7=86 8=4 0=10 | r: 7=55");
        anim(RISING_UPPER, "1 A C | w: 1=9 9=10 10=60 0=20 | h: 1=-1 9=165 10=20 0=-10 13=-15 | r: 9=90");
        anim(RISING_KNEE, "0 L C | w: 1=5 3=60 4=-110 | h: 1=0 3=105 4=-125 0=-8 7=60 8=60 9=60 10=60 | r: 3=40");
        anim(RISING_KICK, "1 L C | w: 1=5 5=50 6=-100 | h: 1=1 5=95 6=-4 0=-16 13=-18 | r: 5=30");
        anim(HOPKICK, "0 L   | w: 1=5 3=40 4=-60 | h: 1=-4 3=120 4=-5 5=70 6=-120 0=-15 7=70 9=60 | r: 3=50 5=20 1=1");
        anim(GETUP_KICK, "0 L   | w: 12=80 3=40 4=-60 | h: 12=40 3=95 4=-4 0=-10 | r: 12=0 3=30");
        anim(GETUP_LOW, "1 L   | w: 12=80 5=20 | h: 12=60 5=70 6=-2 1=6 | r: 12=0");
        anim(THROW_L, "0 A   | w: 7=70 8=40 9=70 10=40 0=10 | h: 7=88 8=15 9=88 10=15 0=14 2=2 | f: 7=100 9=100 13=60 0=-10 | r: 13=90 7=60 9=60");
        anim(THROW_R, "1 A   | w: 7=70 8=40 9=70 10=40 0=10 | h: 7=88 8=15 9=88 10=15 0=14 2=2 | f: 7=130 9=130 13=-60 0=-14 | r: 13=-90 7=60 9=60");
        anim(AIR_KICK, "0 L   | w: 3=60 4=-100 5=-10 6=-100 | h: 3=100 4=-4 5=-20 6=-110 0=-14 7=40 | r: 3=70 4=-30");
        anim(TACKLE, "1 A   | w: 0=30 1=4 | h: 0=45 2=5 7=60 8=90 9=40 10=80 13=-30 | r: 0=20");
        anim(RAGE_ART, "1 A   | w: 1=7 0=-10 9=-30 10=100 7=60 8=120 13=30 | W: 1=8 0=35 2=4 9=40 | h: 0=10 1=-2 9=170 10=10 13=-30 2=3 11=-15 | f: 9=160 | r: 9=90 0=0");
        anim(RUN_CHOP, "1 A   | w: 9=175 10=40 0=-8 | h: 9=70 10=10 0=30 2=4 | r: 9=40");
        anim(RUN_SLIDE, "1 L   | w: 1=6 | h: 12=-60 1=14 5=80 6=0 3=60 4=-100 7=120 9=120 | r: 12=-30 1=8");
        anim(RUN_KICK, "0 L   | w: 3=70 4=-120 1=-2 | h: 3=105 4=0 5=-20 6=-120 0=-20 1=-6 7=80 9=-20 | r: 3=40 1=0");
        anim(BT_BACKFIST, "0 A | w: 13=180 7=30 8=120 | h: 13=10 7=95 8=5 | r: 13=0 7=40 8=100");
        anim(BT_ELBOW, "1 A   | w: 13=180 9=60 10=140 | h: 13=20 9=90 10=130 0=10 | r: 13=0");
        anim(BT_LOW, "0 L C | w: 13=180 | h: 13=10 3=40 4=-2 1=9 | r: 13=0");
        anim(MULE_KICK, "1 L   | w: 0=20 5=10 6=-100 | h: 0=45 5=-85 6=-5 7=20 9=20 11=-20 | r: 0=15 5=-20");
        anim(SPRING_KICK, "0 L   | w: 12=85 3=110 4=-120 5=110 6=-120 | h: 12=30 3=100 4=-2 5=95 6=-2 1=-3 | r: 12=0 3=30 5=-10");
        anim(STOMP, "0 L   | w: 3=100 4=-110 0=-6 | h: 3=30 4=-5 0=20 1=3 | r: 3=20");
        anim(DEMON_HEEL, "1 L   | w: 5=100 6=-30 0=-15 | W: 5=165 6=-2 0=-30 | h: 5=100 6=-2 0=-10 | f: 5=60 0=10 | r: 5=20");
        anim(TWIN_PALM, "1 A   | w: 7=30 8=120 9=30 10=120 1=4 | h: 7=60 8=5 9=60 10=5 0=26 2=5 3=45 4=-40 | r: 7=40 9=40");
        anim(SNAP_ELBOW, "0 A C | w: 7=60 8=130 | h: 7=90 8=135 0=24 13=-20 2=3 | r: 7=50 8=100");
        anim(DRAGON_UPPER, "1 A C | w: 1=11 0=25 9=0 10=50 3=75 4=-120 | h: 1=-5 0=-14 9=172 10=15 13=-20 2=3 3=40 4=-30 11=-10 | f: 9=160 1=-3 | r: 9=90 1=2");
        anim(PERFECT_DRAGON, "1 A C | w: 1=11 0=28 9=0 10=50 3=75 4=-120 | h: 1=-6 0=-16 9=175 10=10 13=-25 2=3 3=40 4=-30 11=-15 | f: 9=165 1=-4 | r: 9=90 1=2");
        anim(HELL_SWEEP, "1 L C | w: 13=-160 1=12 0=30 | h: 13=-350 1=13 5=90 6=0 0=30 3=70 4=-140 7=20 | f: 13=-390 | r: 13=-400 5=60");
        anim(DEVIL_RUSH, "1 A   | w: 1=6 9=-20 10=90 7=60 0=-10 | h: 9=95 10=0 0=28 2=6 13=-35 3=60 4=-60 | r: 9=60 0=12");
        anim(CLAW_SWIPE, "0 A | w: 13=40 7=70 8=60 | h: 13=-15 7=95 8=10 0=10 | f: 13=-60 | r: 13=-20 7=50");
        anim(CAT_SLIDE, "1 L C | w: 1=8 0=10 | h: 12=-45 1=14 5=85 6=0 3=50 4=-110 7=110 9=110 | r: 12=-20 1=10");
        anim(BACKFLIP, "0 L   | w: 1=6 0=10 | h: 12=-200 3=120 4=-5 5=100 6=-10 1=-6 7=160 9=160 | f: 12=-300 | r: 12=-360 1=2");
        anim(NINE_LIVES, "1 L   | w: 1=5 13=30 | h: 12=-120 5=130 6=-5 1=-6 13=-20 | f: 12=-250 | r: 12=-360");
        anim(PAW_JAB, "0 A C | w: 7=60 8=70 | h: 7=85 8=10 0=40 | r: 7=65 8=60");
        anim(POUNCE, "0 A C | w: 1=12 0=45 7=30 9=30 | h: 1=-6 0=-10 7=160 8=20 9=155 10=25 3=80 4=-40 5=-20 | r: 7=90 9=90 1=4");
        anim(TAIL_SWEEP, "1 L C | w: 13=-160 1=12 0=38 | h: 13=-350 1=13 5=85 6=0 | f: 13=-390 | r: 13=-400");
        anim(CAT_FLIP, "0 L C | w: 1=10 0=30 | h: 12=-180 3=115 4=-5 5=60 6=-100 1=-6 | f: 12=-280 | r: 12=-360 1=6");
        anim(IRON_GRAB, "0 A   | w: 7=80 8=30 9=80 10=30 0=12 1=3 | h: 7=95 8=10 9=95 10=10 2=3 | f: 7=170 9=170 8=20 10=20 0=-15 1=-2 | r: 7=120 9=120");
        anim(PISTON, "1 A   | w: 7=40 8=130 9=40 10=130 1=5 0=-6 | h: 7=88 8=0 9=86 10=0 0=24 2=5 1=5 3=50 4=-60 | r: 7=60 9=60");
        anim(GROUND_SLAM, "0 A   | w: 7=175 8=20 9=175 10=20 0=-15 1=-2 | h: 7=60 8=10 9=60 10=10 0=60 1=10 3=70 4=-110 5=30 6=-120 | r: 0=30 1=6");
        anim(OVERDRIVE, "1 A   | w: 1=8 0=20 7=20 9=20 | h: 1=-3 0=-12 7=165 9=168 8=20 10=20 | r: 7=90 9=90");
        anim(PISTON_JAB, "0 A   | w: 7=40 8=110 1=5 | h: 7=88 8=0 0=14 2=3 1=5 | r: 7=60 8=90 1=5");
        anim(CANNON, "1 A   | w: 7=-20 8=100 9=-30 10=100 0=-14 1=6 13=25 | W: 7=-30 9=-40 0=-18 | h: 7=88 8=0 9=88 10=0 0=30 2=6 1=6 13=-10 3=60 4=-70 | r: 7=60 9=60 0=12 1=5");
        anim(TREAD_SWEEP, "1 L C | w: 1=10 13=40 | h: 13=-15 5=85 6=0 1=11 0=25 | r: 13=-30 5=40");
        anim(STOMP_KICK, "0 L   | w: 3=95 4=-120 1=4 | h: 3=80 4=-2 0=-12 2=2 1=4 | r: 3=40 4=-60 1=4");
        anim(SCRAP_GRAB, "0 A C | w: 7=40 9=40 0=30 | h: 7=55 8=20 9=55 10=20 0=40 2=3 | f: 7=150 9=150 0=-10 1=3 | r: 7=100 9=100");
        anim(SWAY_JAB, "0 A   | w: 0=-25 7=50 8=80 | h: 0=10 7=88 8=3 2=2 | r: 0=-10 7=55");
        anim(SWAY_BURNER, "1 A   | w: 0=-30 9=10 10=110 13=20 | h: 0=30 9=92 10=0 2=6 13=-35 3=55 4=-60 | f: 0=34 | r: 0=12 9=60");
        anim(SWAY_SWEEP, "1 L | w: 0=-30 1=4 13=30 | h: 0=10 1=12 5=85 6=0 13=-15 3=60 4=-120 | r: 13=-20 1=6");
        anim(SWAY_KICK, "1 L | w: 0=-30 5=50 6=-90 | h: 0=-18 5=95 6=-4 13=-20 | r: 0=-10 5=30");
        anim(BURNING_FIST, "1 A   | w: 1=6 9=-10 10=100 13=25 | h: 0=28 1=4 2=6 9=92 10=0 13=-40 3=60 4=-60 5=-35 | f: 0=32 | r: 0=15 9=60");
        anim(FLAME_HEEL, "1 L | w: 13=160 5=40 | h: 13=345 5=130 6=-4 0=-22 | f: 13=380 | r: 13=390 5=20");
        anim(HEAT_RUSH, "1 A   | w: 0=10 9=40 10=120 7=60 8=120 | h: 0=38 2=5 9=80 10=60 7=80 8=60 13=-25 | r: 0=15");
        anim(INFERNO, "1 A   | w: 1=8 9=-30 10=100 13=35 0=-6 | W: 0=-10 | h: 0=34 1=5 2=7 9=94 10=0 13=-45 3=65 4=-70 5=-40 | f: 0=36 | r: 0=15 9=60");
        anim(VOID_BEAM, "1 A   | w: 7=20 8=130 9=20 10=130 1=4 11=10 | W: 7=10 9=10 0=-10 | h: 7=92 8=0 9=92 10=0 0=10 2=2 | f: 7=92 9=92 | r: 7=50 9=50");
        anim(DARK_UPPER, "1 A   | w: 1=7 0=15 9=10 10=80 | h: 1=-2 0=-14 9=170 10=15 13=-25 11=-12 | r: 9=90");
    }

    /** Where in the move each kind of key falls (frames). */
    private static float keyTime(char at, Move m) {
        return switch (at) {
            case 'w' -> m.startup() * 0.55F;
            case 'W' -> m.startup() * 0.85F;
            case 'h' -> m.startup();
            case 'f' -> m.startup() + m.active() + Math.min(5, m.recovery() / 3.0F);
            default -> m.startup() + m.active() + m.recovery() * 0.55F;
        };
    }

    /** The move's pose at frame t: eased from key to key (the strike held while it's out), back to its stance at the end. */
    private static void applyAnim(float[] p, Anim an, Move m, float t) {
        if (an.crouched()) crouch(p);
        float[] base = p.clone();
        // the timeline: the stance at 0, each key, the strike held to the end of its active frames, the stance at the end
        List<Float> times = new ArrayList<>();
        List<float[]> vals = new ArrayList<>();
        times.add(0F);
        vals.add(base);
        for (int i = 0; i < an.at().length; i++) {
            float kt = keyTime(an.at()[i], m);
            float[] full = vals.get(vals.size() - 1).clone();
            for (int j = 0; j < P; j++) if (!Float.isNaN(an.keys()[i][j])) full[j] = an.keys()[i][j];
            if (kt <= times.get(times.size() - 1)) kt = times.get(times.size() - 1) + 0.5F;
            times.add(kt);
            vals.add(full);
            if (an.at()[i] == 'h') { // (held while it's out)
                times.add(Math.max(kt + 0.5F, m.startup() + m.active()));
                vals.add(full);
            }
        }
        float end = m.total();
        float[] last = base.clone();
        float[] lastKey = vals.get(vals.size() - 1);
        last[13] = m == THROWING_OVER ? lastKey[13] : Math.round(lastKey[13] / 360) * 360; // (a spin ends a whole turn round, not unwinding)
        last[12] = Math.round(lastKey[12] / 360) * 360;
        times.add(Math.max(end, times.get(times.size() - 1) + 1));
        vals.add(last);
        int k = 0;
        while (k < times.size() - 2 && t > times.get(k + 1)) k++;
        float t0 = times.get(k), t1 = times.get(k + 1);
        float u = ease(Mth.clamp((t - t0) / Math.max(0.01F, t1 - t0), 0, 1));
        float[] a = vals.get(k), b = vals.get(k + 1);
        for (int j = 0; j < P; j++) p[j] = a[j] + (b[j] - a[j]) * u;
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
    private record Bone(float[] a, float[] b, float t, float w, int rgb, int palette, boolean decor) {
        Bone(float[] a, float[] b, float t, float w, int rgb, int palette) {
            this(a, b, t, w, rgb, palette, false);
        }
    }

    /** A detail (cloth, hair, a face): drawn, but not part of it to hit. */
    private static Bone decor(float[] a, float[] b, float t, float w, int rgb, int palette) {
        return new Bone(a, b, t, w, rgb, palette, true);
    }

    private static float[] off(float[] from, float[] d1, float k1, float[] d2, float k2, float side) {
        return new float[] {from[0] + d1[0] * k1 + d2[0] * k2, from[1] + d1[1] * k1 + d2[1] * k2, side};
    }

    private static int darker(int rgb, float k) {
        return mixRgb(rgb, 0x000000, k);
    }

    private static float[] dir(float deg) {
        float r = deg * Mth.DEG_TO_RAD;
        return new float[] {Mth.sin(r), -Mth.cos(r)};
    }

    private static float[] at(float[] from, float[] d, float len, float side) {
        return new float[] {from[0] + d[0] * len, from[1] + d[1] * len, side};
    }

    /**
     * Its bones, posed (local space, before it's turned and placed): the legs first (thigh, shin, shoe - six), then the
     * body, head, face, hair, clothes and what makes each fighter itself, and the arms last (upper, fore, fist - six).
     */
    private List<Bone> skeleton(Body b, float[] p, int[] look, int kind) {
        List<Bone> out = new ArrayList<>();
        int top = look[0], legs = look[1], skin = look[2], gloves = look[3], shoes = look[4], hair = look[5], accent = look[6];
        int gi = b.style.gi(), sk = b.style.skin(), band = b.style.band(), hr = b.style.hair();
        float lean = p[0];
        float[] pelvis = {p[2], 18 - p[1]};
        float[] up = dir(180 - lean); // (leaning forward: its chest ahead of its hips)
        float[] fwdB = dir(90 - lean);
        float[] neck = {pelvis[0] + up[0] * 13, pelvis[1] + up[1] * 13, 0};
        // ---- the legs (near: the camera's side, the lead leg): thigh, shin, shoe ----
        float[][] knees = new float[2][], ankles = new float[2][];
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -2.9F : 2.9F;
            float hip = s == 0 ? p[3] : p[5], knee = s == 0 ? p[4] : p[6];
            float[] h = {pelvis[0], pelvis[1], side};
            float[] k = at(h, dir(hip), 9.5F, side);
            float[] an = at(k, dir(hip + knee), 9.0F, side);
            float[] toe = at(an, dir(hip + knee + 90), 4.4F, side);
            knees[s] = k;
            ankles[s] = an;
            out.add(new Bone(h, k, 5.0F, 5.0F, legs, gi));
            out.add(new Bone(k, an, 3.9F, 3.9F, legs, gi));
            out.add(new Bone(new float[] {an[0] - (toe[0] - an[0]) * 0.25F, an[1] - (toe[1] - an[1]) * 0.25F, side}, toe, 2.6F, 3.4F, shoes, gi));
        }
        for (int s = 0; s < 2; s++) { // (soles under the shoes; and, for loose trousers, the hems flaring)
            float side = s == 0 ? -2.9F : 2.9F;
            float hip = s == 0 ? p[3] : p[5], knee = s == 0 ? p[4] : p[6];
            float[] fd = dir(hip + knee + 90), dn = dir(hip + knee);
            float[] an = ankles[s];
            float[] heel = {an[0] - fd[0] * 1.1F + dn[0] * 1.5F, an[1] - fd[1] * 1.1F + dn[1] * 1.5F, side};
            out.add(decor(heel, new float[] {heel[0] + fd[0] * 5.6F, heel[1] + fd[1] * 5.6F, side}, 0.8F, 3.6F, darker(shoes, 0.55F), gi));
            if (kind == 0 || kind == 3) {
                float[] hem0 = {an[0] - dn[0] * 2.4F, an[1] - dn[1] * 2.4F, side};
                out.add(decor(hem0, new float[] {an[0] - dn[0] * 0.2F, an[1] - dn[1] * 0.2F, side}, 4.8F, 4.8F, legs, gi));
            }
            if (kind == 2) { // (knee pads)
                float[] kp = {knees[s][0] + fwdB[0] * 1.6F, knees[s][1] + fwdB[1] * 1.6F, side};
                out.add(decor(new float[] {kp[0], kp[1] + 1.4F, side}, new float[] {kp[0], kp[1] - 1.4F, side}, 2.6F, 4.6F, accent, band));
            }
        }
        // ---- the body: hips, a belt, waist, chest, shoulders across, neck ----
        float[] hipLo = off(pelvis, up, -1.2F, fwdB, 0, 0), hipHi = off(pelvis, up, 3.0F, fwdB, 0, 0);
        out.add(new Bone(hipLo, hipHi, 6.6F, 9.6F, legs, gi));
        float[] belt0 = off(pelvis, up, 2.6F, fwdB, 0, 0), belt1 = off(pelvis, up, 4.0F, fwdB, 0, 0);
        int beltCol = kind == 0 ? 0x1A1A1E : kind == 2 ? darker(top, 0.4F) : accent;
        out.add(decor(belt0, belt1, 7.2F, 10.4F, beltCol, band));
        int waistCol = kind == 1 ? skin : kind == 2 ? darker(top, 0.25F) : top; // (NEKO's crop top: bare waist)
        out.add(new Bone(off(pelvis, up, 3.8F, fwdB, 0, 0), off(pelvis, up, 7.6F, fwdB, 0, 0), 5.8F, 8.8F, waistCol, kind == 1 ? sk : gi));
        float[] chest0 = off(pelvis, up, 7.2F, fwdB, 0.3F, 0);
        out.add(new Bone(chest0, off(neck, up, -1.0F, fwdB, 0.2F, 0), 7.0F, 11.0F, top, gi));
        float[] sh0 = off(neck, up, -2.6F, fwdB, -0.4F, 0), sh1 = off(neck, up, -0.4F, fwdB, -0.4F, 0);
        out.add(new Bone(sh0, sh1, 5.6F, 13.6F, top, gi)); // (across the shoulders)
        out.add(new Bone(neck, off(neck, up, 1.6F, fwdB, 0, 0), 3.0F, 3.0F, kind == 2 ? darker(top, 0.3F) : skin, sk));
        // ---- the head: skull, jaw, nose, eyes, brows, mouth, ears ----
        float[] hd = dir(180 - lean - p[11]);
        float[] fwd = dir(90 - lean - p[11]);
        float[] headBase = {neck[0] + hd[0] * 1.2F, neck[1] + hd[1] * 1.2F, 0};
        float[] headTop = {neck[0] + hd[0] * 8.8F, neck[1] + hd[1] * 8.8F, 0};
        int face = kind == 2 ? 0x8A98B0 : skin;
        out.add(new Bone(off(headBase, hd, 2.4F, fwd, 0, 0), headTop, 7.0F, 7.0F, face, sk));
        out.add(new Bone(off(headBase, hd, 0.4F, fwd, 0.6F, 0), off(headBase, hd, 2.8F, fwd, 0.5F, 0), 5.6F, 5.8F, face, sk)); // (jaw)
        if (kind != 2) {
            out.add(decor(off(headBase, hd, 3.2F, fwd, 3.7F, 0), off(headBase, hd, 4.2F, fwd, 3.9F, 0), 1.0F, 1.0F, face, sk)); // (nose)
            out.add(decor(off(headBase, hd, 2.0F, fwd, 3.25F, 0), off(headBase, hd, 2.35F, fwd, 3.25F, 0), 0.3F, 2.2F, darker(skin, 0.45F), sk)); // (mouth)
        }
        boolean glow = kind == 2 || kind == 4;
        for (float side : new float[] {-1.6F, 1.6F}) {
            float[] e0 = off(headBase, hd, 4.5F, fwd, 3.55F, side);
            if (!glow) out.add(decor(e0, off(headBase, hd, 5.6F, fwd, 3.55F, side), 0.3F, 1.5F, 0xF4F0F0, 0)); // (eye whites)
            out.add(decor(off(headBase, hd, 4.6F, fwd, 3.62F, side), off(headBase, hd, 5.5F, fwd, 3.62F, side), 0.3F, glow ? 1.6F : 0.8F,
                    glow ? accent : 0x141018, 0)); // (pupils - or glowing eyes)
            if (kind != 2) out.add(decor(off(headBase, hd, 6.1F, fwd, 3.45F, side), off(headBase, hd, 6.5F, fwd, 3.45F, side), 0.5F, 1.9F,
                    darker(hair, 0.15F), hr)); // (brows)
            if (kind == 0 || kind == 3) out.add(decor(off(headBase, hd, 3.6F, fwd, 0.2F, side * 2.25F), off(headBase, hd, 5.6F, fwd, 0.2F, side * 2.25F),
                    1.6F, 0.8F, skin, sk)); // (ears)
        }
        // ---- hair, and what makes each one itself ----
        float[] crown = {headBase[0] + hd[0] * 6.2F - fwd[0] * 0.6F, headBase[1] + hd[1] * 6.2F - fwd[1] * 0.6F, 0};
        switch (kind) {
            case 0 -> { // RIKU: short spiky hair, a headband (its tails flying), a gi with lapels and a black belt
                out.add(new Bone(crown, new float[] {headTop[0] + hd[0] * 0.8F, headTop[1] + hd[1] * 0.8F, 0}, 7.4F, 7.4F, hair, hr));
                for (int i = 0; i < 3; i++) {
                    float[] s0 = off(headTop, hd, 0.4F, fwd, -1.5F + i * 1.6F, -1.8F + i * 1.8F);
                    out.add(decor(s0, at(s0, dir(170 - lean - p[11] - i * 15), 2.4F, s0[2]), 1.6F, 1.8F, hair, hr));
                }
                float[] bandAt = {headBase[0] + hd[0] * 5.4F, headBase[1] + hd[1] * 5.4F, 0};
                out.add(decor(bandAt, new float[] {bandAt[0] + hd[0] * 1.1F, bandAt[1] + hd[1] * 1.1F, 0}, 7.5F, 7.5F, accent, band));
                float flap = Mth.sin(vt() * 0.4F) * 15;
                float[] t0 = {bandAt[0] - fwd[0] * 3.6F, bandAt[1] - fwd[1] * 3.6F, 0};
                float[] t1 = at(t0, dir(-80 + flap), 3.4F, 0);
                out.add(decor(t0, t1, 1.0F, 1.6F, accent, band));
                out.add(decor(t1, at(t1, dir(-70 + flap * 1.6F), 3.2F, 0), 0.9F, 1.4F, accent, band));
                for (float side : new float[] {-1, 1}) { // (lapels crossing down to the belt)
                    float[] l0 = off(neck, up, -1.4F, fwdB, 3.4F, side * 3.4F);
                    out.add(decor(l0, off(pelvis, up, 4.6F, fwdB, 3.7F, -side * 0.8F), 0.4F, 1.6F, darker(top, 0.12F), gi));
                }
                float[] knot = off(pelvis, up, 3.3F, fwdB, 3.8F, -1.2F);
                out.add(decor(knot, off(knot, up, 0.9F, fwdB, 0.2F, -1.2F), 1.4F, 1.8F, beltCol, band));
                float swing = Mth.sin(vt() * 0.25F) * 6;
                for (float side : new float[] {-2.0F, -0.4F}) {
                    float[] k0 = off(pelvis, up, 3.0F, fwdB, 3.9F, side);
                    out.add(decor(k0, at(k0, dir(180 - lean + 180 + swing), 4.6F, side), 0.4F, 1.1F, beltCol, band));
                }
            }
            case 1 -> { // NEKO: hair with a fringe and side locks, cat ears (pink inside), a bell on a collar, a tail
                out.add(new Bone(crown, new float[] {headTop[0] + hd[0] * 0.6F, headTop[1] + hd[1] * 0.6F, 0}, 7.6F, 7.6F, hair, hr));
                out.add(decor(off(headBase, hd, 6.6F, fwd, 3.2F, 0), off(headBase, hd, 7.8F, fwd, 3.0F, 0), 1.2F, 7.0F, hair, hr)); // (fringe)
                for (float side : new float[] {-3.6F, 3.6F}) {
                    out.add(decor(off(headBase, hd, 6.4F, fwd, 1.0F, side), off(headBase, hd, 1.2F, fwd, 1.2F, side), 1.4F, 1.0F, hair, hr));
                }
                for (float side : new float[] {-2.4F, 2.4F}) {
                    float[] e0 = {headTop[0], headTop[1], side};
                    out.add(new Bone(e0, new float[] {e0[0] + hd[0] * 3.2F, e0[1] + hd[1] * 3.2F, side}, 2.4F, 1.6F, hair, hr));
                    out.add(decor(off(e0, hd, 0.4F, fwd, 0.9F, side), off(e0, hd, 2.6F, fwd, 0.9F, side), 0.5F, 1.0F, 0xFF9AC8, hr));
                }
                out.add(decor(off(neck, up, 0.2F, fwdB, 0, 0), off(neck, up, 0.9F, fwdB, 0, 0), 3.6F, 3.6F, accent, band)); // (collar)
                out.add(decor(off(neck, up, -0.6F, fwdB, 2.1F, 0), off(neck, up, 0.4F, fwdB, 2.1F, 0), 1.3F, 1.3F, 0xFFD040, band)); // (bell)
                float sway = Mth.sin(vt() * 0.2F) * 20;
                float[] t0 = {pelvis[0] - 3.5F, pelvis[1] + 2, 0};
                float[] t1 = at(t0, dir(-120 + sway), 6, 0);
                float[] t2 = at(t1, dir(-170 + sway * 1.5F), 6, 0);
                out.add(decor(t0, t1, 1.8F, 1.8F, hair, hr));
                out.add(decor(t1, t2, 1.6F, 1.6F, hair, hr));
                out.add(decor(t2, at(t2, dir(-200 + sway * 2), 1.8F, 0), 2.0F, 2.0F, 0xFFFFFF, hr));
            }
            case 2 -> { // ROBO: a helmet ridge, a visor, ear discs, an antenna; a chest plate with a glowing core, pauldrons
                out.add(decor(off(headBase, hd, 4.2F, fwd, 3.4F, 0), off(headBase, hd, 5.6F, fwd, 3.4F, 0), 0.6F, 6.4F, accent, band)); // (visor)
                out.add(decor(off(headTop, hd, -0.4F, fwd, -2.6F, 0), off(headTop, hd, 0.7F, fwd, 2.6F, 0), 1.2F, 1.4F, darker(top, 0.2F), gi)); // (ridge)
                for (float side : new float[] {-3.8F, 3.8F}) {
                    out.add(decor(off(headBase, hd, 3.6F, fwd, 0.4F, side), off(headBase, hd, 5.6F, fwd, 0.4F, side), 2.4F, 0.8F, legs, gi));
                }
                float[] a0 = {headTop[0], headTop[1], 0};
                float[] a1 = {a0[0] + hd[0] * 4.5F, a0[1] + hd[1] * 4.5F, 0};
                out.add(decor(a0, a1, 0.7F, 0.7F, legs, gi));
                out.add(decor(a1, new float[] {a1[0] + hd[0] * 1.4F, a1[1] + hd[1] * 1.4F, 0}, 1.6F, 1.6F,
                        ((int) vt() / 8) % 2 == 0 ? 0xFF4040 : accent, hr));
                out.add(decor(off(chest0, up, 1.2F, fwdB, 3.4F, 0), off(neck, up, -2.2F, fwdB, 3.6F, 0), 0.8F, 8.4F, darker(top, 0.18F), gi)); // (plate)
                float pulse = 0.6F + 0.4F * Mth.sin(vt() * 0.3F);
                out.add(decor(off(chest0, up, 2.4F, fwdB, 3.9F, 0), off(chest0, up, 4.0F, fwdB, 3.9F, 0), 0.4F, 2.2F, mixRgb(accent, 0xFFFFFF, pulse * 0.4F), band));
                for (float side : new float[] {-5.6F, 5.6F}) {
                    out.add(decor(off(neck, up, -3.2F, fwdB, 0, side), off(neck, up, 0.2F, fwdB, 0, side), 5.6F, 3.6F, darker(top, 0.1F), gi));
                }
            }
            case 3 -> { // BLAZE: flames for hair, sideburns; an open jacket, its collar up; a gold buckle
                out.add(new Bone(crown, new float[] {headTop[0] + hd[0] * 0.6F, headTop[1] + hd[1] * 0.6F, 0}, 7.4F, 7.4F, hair, hr));
                for (int i = 0; i < 5; i++) {
                    float flick = Mth.sin(vt() * 0.6F + i * 1.7F) * 1.2F;
                    float side = -3.0F + i * 1.5F;
                    float[] f0 = {headTop[0] - fwd[0] * (i % 2) * 1.5F, headTop[1] - fwd[1] * (i % 2) * 1.5F, side};
                    float[] f1 = at(f0, dir(195 + i * 4 - lean), 3.8F + flick + (i % 2), side);
                    out.add(decor(f0, f1, 1.8F, 1.4F, i % 2 == 0 ? hair : accent, hr));
                    out.add(decor(f1, at(f1, dir(200 + i * 6 - lean), 1.6F + flick * 0.5F, side), 1.0F, 0.9F, accent, hr));
                }
                for (float side : new float[] {-3.4F, 3.4F}) {
                    out.add(decor(off(headBase, hd, 2.8F, fwd, 1.2F, side), off(headBase, hd, 5.4F, fwd, 1.0F, side), 1.0F, 0.8F, darker(hair, 0.3F), hr));
                }
                out.add(decor(off(pelvis, up, 8.0F, fwdB, 3.55F, 0), off(neck, up, -1.6F, fwdB, 3.55F, 0), 0.3F, 3.0F, skin, sk)); // (open front)
                for (float side : new float[] {-1, 1}) { // (the collar, turned up)
                    out.add(decor(off(neck, up, -0.6F, fwdB, 1.2F, side * 4.2F), off(neck, up, 2.0F, fwdB, 0.4F, side * 4.6F), 2.2F, 0.8F, darker(top, 0.15F), gi));
                }
                out.add(decor(off(pelvis, up, 2.9F, fwdB, 3.9F, 0), off(pelvis, up, 3.9F, fwdB, 3.9F, 0), 0.6F, 2.0F, 0xFFD040, band)); // (buckle)
            }
            default -> { // NYX: a hood, its eyes glowing in it; a cape, swaying; spikes on the shoulders
                out.add(new Bone(off(headBase, hd, 2.0F, fwd, -0.8F, 0), off(headTop, hd, 1.4F, fwd, -0.6F, 0), 8.8F, 8.6F, legs, gi)); // (hood)
                out.add(decor(off(headTop, hd, 1.2F, fwd, -1.4F, 0), off(headTop, hd, 3.2F, fwd, -3.4F, 0), 3.0F, 3.0F, legs, gi)); // (its point)
                float sway = Mth.sin(vt() * 0.12F) * 8;
                for (float side : new float[] {-3.6F, 0, 3.6F}) {
                    float[] c0 = off(neck, up, -1.0F, fwdB, -3.6F, side);
                    float[] c1 = at(c0, dir(-8 + lean * 0.4F + sway + side * 2), 11, side); // (hanging down its back)
                    float[] c2 = at(c1, dir(-14 + sway * 1.6F + side * 3), 10, side);
                    out.add(decor(c0, c1, 0.8F, 4.0F, darker(legs, 0.1F), gi));
                    out.add(decor(c1, c2, 0.8F, 4.2F, darker(legs, 0.2F), gi));
                }
                for (float side : new float[] {-6.0F, 6.0F}) {
                    float[] s0 = off(neck, up, -0.6F, fwdB, 0, side);
                    out.add(decor(s0, at(s0, dir(180 - lean + (side < 0 ? -25 : 25)), 3.0F, side), 1.4F, 1.4F, accent, band));
                }
            }
        }
        // ---- the arms (near first: the lead hand): upper, fore, fist - with wraps, cuffs, pads and the like ----
        float[][] sp = new float[2][], el = new float[2][], hand = new float[2][];
        float[][] fd = new float[2][];
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -5.5F : 5.5F;
            float sh = s == 0 ? p[7] : p[9], elb = s == 0 ? p[8] : p[10];
            sp[s] = new float[] {neck[0] - up[0] * 1.6F, neck[1] - up[1] * 1.6F, side};
            el[s] = at(sp[s], dir(sh), 7.0F, side);
            hand[s] = at(el[s], dir(sh + elb), 6.5F, side);
            fd[s] = dir(sh + elb);
            out.add(decor(off(sp[s], up, 0.4F, fwdB, 0, side), off(sp[s], up, -2.8F, fwdB, 0, side), 4.4F, 4.0F, kind == 1 ? skin : top, kind == 1 ? sk : gi)); // (deltoid)
            if (kind == 0) { // (hand wraps)
                out.add(decor(at(hand[s], dir(sh + elb + 180), 1.8F, side), at(hand[s], dir(sh + elb + 180), 0.2F, side), 3.4F, 3.4F, 0xF0EEE6, band));
            } else if (kind == 3) { // (cuffs: the jacket's sleeve ends)
                out.add(decor(at(hand[s], dir(sh + elb + 180), 2.4F, side), at(hand[s], dir(sh + elb + 180), 0.8F, side), 3.6F, 3.6F, darker(top, 0.15F), gi));
            } else if (kind == 2) { // (glowing elbow joints)
                out.add(decor(at(el[s], dir(sh + elb), -0.8F, side), at(el[s], dir(sh + elb), 0.8F, side), 3.4F, 3.4F, accent, band));
            }
        }
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? -5.5F : 5.5F;
            out.add(new Bone(sp[s], el[s], 3.7F, 3.7F, kind == 1 ? skin : top, kind == 1 ? sk : gi));
            out.add(new Bone(el[s], hand[s], 3.2F, 3.2F, kind == 0 || kind == 1 ? skin : top, kind == 0 || kind == 1 ? sk : gi));
            float k = kind == 1 ? 4.4F : 3.6F; // (NEKO's paw gloves: bigger)
            out.add(new Bone(hand[s], new float[] {hand[s][0] + fd[s][0] * k, hand[s][1] + fd[s][1] * k, side}, kind == 1 ? 4.6F : 4.0F, kind == 1 ? 4.6F : 4.2F,
                    gloves, band));
        }
        return out;
    }

    /** Where its striking hand or foot is now (stage space: along, up, depth), for its trail. */
    private float[] tip(Body b) {
        if (b.move == null) return null;
        float[] p = poseOf(b)[1];
        int[] sl = limbOf(b.move);
        int s = sl[0];
        boolean leg = sl[1] == 1;
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
        boolean lying = b.state == State.DOWN || b.state == State.KO;
        if (b.state == State.THROWN && b.t > THROW_LIFT) return 0; // (flying through the air: where the throw puts it)
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
    /** The line's place in the arena as drawn (between frames). */
    private float rTX, rTZ;
    private static final float FL = 170;

    private void camera(float partial) {
        cu = Mth.lerp(partial, oCamU, camU);
        cd = Mth.lerp(partial, oCamD, camD);
        csp = Mth.lerp(partial, oSpin, spin);
        rTX = Mth.lerp(partial, oTX, tX);
        rTZ = Mth.lerp(partial, oTZ, tZ);
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
        return shade(rgb, k, rim, 0xFFAA6E);
    }

    /** Lit by k, with a rim of light (its colour) along the edges turned away. */
    private static int shade(int rgb, float k, float rim, int rimRgb) {
        int r = (int) Mth.clamp(((rgb >> 16) & 255) * k + ((rimRgb >> 16) & 255) * rim * 0.9F, 0, 255);
        int g = (int) Mth.clamp(((rgb >> 8) & 255) * k + ((rimRgb >> 8) & 255) * rim * 0.9F, 0, 255);
        int b = (int) Mth.clamp((rgb & 255) * k + (rimRgb & 255) * rim * 0.9F, 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int mulRgb(int a, int b) {
        return ((((a >> 16) & 255) * ((b >> 16) & 255) / 255) << 16) | ((((a >> 8) & 255) * ((b >> 8) & 255) / 255) << 8) | ((a & 255) * (b & 255) / 255);
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
            corner[i] = view(s[0], mirrored ? -s[1] : s[1], s[2]); // (in a glossy floor: upside down, under it)
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
            int base = tint != 0 ? tint : mulRgb(bone.rgb(), st().light()); // (lit by the stage's own light)
            int[] cs = new int[4];
            float ymin = Float.MAX_VALUE, ymax = -Float.MAX_VALUE;
            for (float[] c : q) {
                ymin = Math.min(ymin, c[1]);
                ymax = Math.max(ymax, c[1]);
            }
            for (int i = 0; i < 4; i++) {
                float g = ymax - ymin < 0.01F ? 1 : 0.9F + 0.2F * (q[i][1] - ymin) / (ymax - ymin);
                cs[i] = shade(base, k * g, tint != 0 ? 0 : rim, st().rim());
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
    /** Drawing its reflection in a glossy floor (mirrored under the floor, faint). */
    private boolean mirrored;

    private void reflection(Body b, float partial) {
        if (!st().glossy() || (floorBroken && st().floorBreak())) return;
        float[][] pp = poseOf(b);
        float[] p = new float[P];
        for (int i = 0; i < P; i++) p[i] = i >= 12 ? pp[0][i] + Mth.wrapDegrees(pp[1][i] - pp[0][i]) * partial : Mth.lerp(partial, pp[0][i], pp[1][i]);
        List<Bone> bones = skeleton(b, p, LOOKS[styleIndex(b.style)], b.style.kind());
        curBody = b;
        curPose = p;
        curLift = lift(b, p);
        mirrored = true;
        for (Bone bone : bones) if (!bone.decor() || bone.t() > 1) box(b, p, bone, 0, 0.16F, 0);
        mirrored = false;
    }

    private void fighter(Body b, float partial) {
        float[][] pp = poseOf(b);
        float[] p = new float[P];
        for (int i = 0; i < P; i++) p[i] = i >= 12 ? pp[0][i] + Mth.wrapDegrees(pp[1][i] - pp[0][i]) * partial : Mth.lerp(partial, pp[0][i], pp[1][i]);
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
            Stage sg = st();
            int c = t < 0.6F ? mixRgb(sg.skyTop(), sg.skyMid(), t / 0.6F) : mixRgb(sg.skyMid(), sg.skyLow(), (t - 0.6F) / 0.4F);
            box(g, x0, y0, 0, y, W, 2, 0xFF000000 | c);
        }
        // the ground far off, in the haze (where the floor's drawn out to, it covers this)
        box(g, x0, y0, 0, hz, W, H - hz, 0xFF000000 | mixRgb(st().walls() ? st().outside() : st().tileB(), st().haze(), 0.7F));
        float turn = csp * 0.011F; // (radians the camera has swung round)
        // stars up high
        for (int i = 0; i < 40; i++) {
            float sx = Math.floorMod((int) (i * 53 - turn * 140), W + 20) - 10;
            float sy = (i * 29) % Math.max(1, (int) (horizon * 0.5F));
            int a = (int) (90 + 80 * Mth.sin(vt() * 0.1F + i)) << 24;
            box(g, x0, y0, sx, sy, 1, 1, a | 0xFFFFFF);
        }
        // the sun (the temple's low and gold, the forge's huge and red), with its glow
        float sunX = W * 0.68F - Mth.wrapDegrees(turn * Mth.RAD_TO_DEG) * 3.0F;
        if (st().feature() == 5) { // (the harbour: a big moon over the sea)
            for (int r = 18; r > 0; r -= 3) box(g, x0, y0, sunX - r, horizon - 40 - r * 0.5F, r * 2, r, ((int) (30 - r) << 24) | 0xC8D8FF);
            box(g, x0, y0, sunX - 7, horizon - 48, 14, 14, 0xFFF0F4FF);
            box(g, x0, y0, sunX - 3, horizon - 45, 3, 3, 0xFFD0D8EE);
        }
        if (st().feature() == 0 || st().feature() == 3 || st().feature() == 6) {
            boolean red = st().feature() == 3;
            int glow = red ? 0xFF5A2A : 0xFFD08A;
            for (int r = red ? 26 : 16; r > 0; r -= 3) {
                box(g, x0, y0, sunX - r, horizon - 12 - r * 0.5F, r * 2, r, ((int) (40 - r * 1.4F) << 24) | glow);
            }
            box(g, x0, y0, sunX - (red ? 9 : 6), horizon - (red ? 24 : 18), red ? 18 : 12, red ? 18 : 12, red ? 0xFFFF8A4A : 0xFFFFE6B0);
        }
        // clouds drifting (pink at dusk, white by day, smoke at the forge, none at night)
        int f = st().feature();
        if (f == 0 || f == 3 || f == 6) {
            int cloud = f == 0 ? 0xE8A0A8 : f == 3 ? 0x5A3028 : 0xFFFFFF;
            for (int i = 0; i < 7; i++) {
                float cx = Math.floorMod((int) (i * 61 + vt() * 0.15F * (1 + i % 3) - turn * 90), W + 80) - 40;
                float cy = 12 + (i * 17) % Math.max(10, (int) (horizon * 0.45F));
                float wdt = 22 + (i * 11) % 18;
                for (int k = 0; k < 4; k++) {
                    float kw = wdt * (1 - Math.abs(k - 1.5F) * 0.25F);
                    box(g, x0, y0, cx - kw / 2 + k * 2, cy + k * 2.2F, kw, 2.4F, ((f == 3 ? 150 : 110) << 24) | cloud);
                }
            }
        }
        // far hills, then nearer ones with what's on them - sliding at their own speeds as the camera turns
        ridge(g, x0, y0, turn * 160, 26, 0.035F, st().farHills(), 0);
        ridge(g, x0, y0, turn * 300, 14, 0.06F, st().nearHills(), 1);
    }

    private void ridge(GuiGraphics g, int x0, int y0, float slide, float height, float freq, int colour, int layer) {
        for (int x = 0; x < W; x += 2) {
            float wx = x + slide;
            float h = height * (0.55F + 0.25F * Mth.sin(wx * freq) + 0.2F * Mth.sin(wx * freq * 2.7F + 1.3F));
            box(g, x0, y0, x, horizon - h, 2, h + 1, colour);
        }
        if (layer == 1 && st().feature() != 0) {
            stageFeature(g, x0, y0, slide);
        } else if (layer == 1) {
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

    /** What stands on the near hills: a city's towers and windows, ice spires, a smoking volcano, floating shards. */
    private void stageFeature(GuiGraphics g, int x0, int y0, float slide) {
        switch (st().feature()) {
            case 1 -> { // (towers, lit windows blinking, a beacon on the tallest)
                for (int i = 0; i < 14; i++) {
                    float tx = Math.floorMod((int) (i * 23 + 7 - slide), W + 40) - 20;
                    float h = 18 + (i * 37 % 26), w = 8 + i % 3 * 3;
                    box(g, x0, y0, tx, horizon - h, w, h, 0xFF0C0A1C);
                    for (int wy = 0; wy < h - 4; wy += 4) {
                        for (int wx = 1; wx < w - 1; wx += 3) {
                            boolean lit = ((i * 7 + wy * 3 + wx * 5 + (int) vt() / 30) % 5) < 2;
                            if (lit) box(g, x0, y0, tx + wx, horizon - h + 2 + wy, 1, 1, (i % 2 == 0) ? 0xFFFFD86A : 0xFF6AE8FF);
                        }
                    }
                    if (i % 5 == 2) box(g, x0, y0, tx + w / 2 - 1, horizon - h - 3, 2, 2, ((int) vt() / 10) % 2 == 0 ? 0xFFFF3A6A : 0xFF5A1020);
                }
            }
            case 2 -> { // (ice spires, glinting)
                for (int i = 0; i < 10; i++) {
                    float tx = Math.floorMod((int) (i * 31 + 11 - slide), W + 40) - 20;
                    float h = 14 + (i * 13 % 18);
                    for (int k = 0; k < h; k += 2) {
                        float w = 6 * (1 - k / h) + 1;
                        box(g, x0, y0, tx - w / 2, horizon - k - 2, w, 2, k % 4 == 0 ? 0xFFDDF4FF : 0xFFA8D8EC);
                    }
                    float glint = Mth.sin(vt() * 0.2F + i * 2.1F);
                    if (glint > 0.92F) box(g, x0, y0, tx - 1, horizon - h - 1, 2, 2, 0xFFFFFFFF);
                }
                for (int i = 0; i < 30; i++) { // (snow drifting down)
                    float sx = Math.floorMod((int) (i * 41 - slide * 0.5F + vt() * 0.3F * (i % 3 + 1)), W);
                    float sy = Math.floorMod((int) (i * 17 + vt() * (0.5F + i % 4 * 0.2F)), (int) Math.max(1, horizon));
                    box(g, x0, y0, sx, sy, 1, 1, 0xC0FFFFFF);
                }
            }
            case 3 -> { // (a volcano, lava glowing in its mouth, smoke and embers going up)
                float tx = Math.floorMod((int) (100 - slide), W + 140) - 70;
                for (int k = 0; k < 34; k += 2) {
                    float w = 70 - k * 1.7F;
                    box(g, x0, y0, tx - w / 2, horizon - k - 2, w, 2, 0xFF1A0804);
                }
                float pulse = 0.6F + 0.4F * Mth.sin(vt() * 0.25F);
                box(g, x0, y0, tx - 8, horizon - 36, 16, 3, ((int) (255 * pulse) << 24) | 0xFF7A1A);
                for (int i = 0; i < 16; i++) {
                    float life = (vt() * 0.6F + i * 7) % 60;
                    float ex = tx + Mth.sin(i * 1.7F + life * 0.1F) * (4 + life * 0.4F), ey = horizon - 38 - life;
                    box(g, x0, y0, ex, ey, 1, 1, ((int) (255 * (1 - life / 60)) << 24) | (i % 3 == 0 ? 0xFFD040 : 0xFF6A2A));
                }
            }
            case 5 -> { // (the sea to the horizon, boats' lights, a lighthouse sweeping its beam)
                box(g, x0, y0, 0, horizon - 6, W, 6, 0xFF0A1A34);
                for (int i = 0; i < 24; i++) {
                    float wx = Math.floorMod((int) (i * 17 - slide * 0.6F + vt() * 0.3F), W);
                    box(g, x0, y0, wx, horizon - 5 + (i % 3) * 2, 4, 1, 0x60A8C8FF);
                }
                for (int i = 0; i < 4; i++) {
                    float bx = Math.floorMod((int) (i * 53 + 20 - slide * 0.8F), W + 20) - 10;
                    box(g, x0, y0, bx, horizon - 8, 6, 2, 0xFF101420);
                    box(g, x0, y0, bx + 2, horizon - 10, 1, 1, ((int) vt() / 12 + i) % 2 == 0 ? 0xFFFFD060 : 0xFFFF6040);
                }
                float lx = Math.floorMod((int) (150 - slide), W + 80) - 40;
                box(g, x0, y0, lx - 3, horizon - 34, 6, 28, 0xFFE8E0D8);
                for (int k = 0; k < 4; k++) box(g, x0, y0, lx - 3, horizon - 30 + k * 7, 6, 3, 0xFFC03030);
                box(g, x0, y0, lx - 4, horizon - 38, 8, 4, 0xFF202028);
                box(g, x0, y0, lx - 2, horizon - 37, 4, 2, 0xFFFFF0A0);
                float beam = Mth.sin(vt() * 0.08F);
                for (int k = 1; k < 12; k++) { // (the beam sweeping round)
                    float bw = k * 4 * Math.abs(beam);
                    box(g, x0, y0, beam > 0 ? lx + 4 + (k - 1) * bw * 0.9F : lx - 4 - k * bw * 0.9F, horizon - 37 - k * 0.3F, bw, 1 + k * 0.3F,
                            ((int) (60 - k * 4) << 24) | 0xFFF0A0);
                }
            }
            case 6 -> { // (cherry trees in bloom on the hills, a pagoda, petals drifting down)
                for (int i = 0; i < 9; i++) {
                    float tx = Math.floorMod((int) (i * 29 + 9 - slide), W + 40) - 20;
                    float ty = horizon - 12 - (i * 7 % 6);
                    box(g, x0, y0, tx - 1, ty, 2, 8, 0xFF5A3A2A);
                    for (int k = 0; k < 5; k++) {
                        float ox = Mth.sin(i * 3.1F + k * 1.7F) * 5, oy = Mth.cos(i * 2.3F + k * 1.3F) * 3;
                        box(g, x0, y0, tx + ox - 3, ty - 4 + oy, 6, 5, k % 2 == 0 ? 0xFFFFB8D0 : 0xFFF098B8);
                    }
                }
                float px = Math.floorMod((int) (70 - slide), W + 100) - 50;
                for (int k = 0; k < 4; k++) {
                    float w = 22 - k * 5, y = horizon - 14 - k * 8;
                    box(g, x0, y0, px - w / 2, y, w, 2, 0xFF6A1A28);
                    box(g, x0, y0, px - w / 2 + 3, y + 2, w - 6, 6, 0xFFE8D8C0);
                }
                for (int i = 0; i < 36; i++) { // (petals drifting across and down)
                    float sx = Math.floorMod((int) (i * 37 - slide * 0.4F + vt() * (0.6F + i % 3 * 0.3F)), W);
                    float sy = Math.floorMod((int) (i * 23 + vt() * (0.4F + i % 4 * 0.15F)), H);
                    box(g, x0, y0, sx, sy, 1.5F, 1, 0xD0FFC0D8);
                }
            }
            default -> { // (the void: shards hanging in the dark, slowly turning)
                for (int i = 0; i < 12; i++) {
                    float tx = Math.floorMod((int) (i * 29 + 5 - slide * (0.5F + i % 3 * 0.25F)), W + 40) - 20;
                    float ty = horizon - 20 - (i * 19 % 40) + Mth.sin(vt() * 0.05F + i) * 3;
                    float sz = 2 + i % 4;
                    box(g, x0, y0, tx, ty, sz, sz * 2, (i % 2 == 0) ? 0xFF6A2ACC : 0xFF3A1A6A);
                    box(g, x0, y0, tx, ty, sz, 1, 0xFFB08AFF);
                }
            }
        }
    }

    /**
     * The floor, in the arena's own space (seen along their line as it turns): its tiles, the ring in the middle, the
     * floor below once it's broken through - and outside the walls, what's out there (grass, a drop, lava, the sea ...).
     */
    private void floor() {
        int tile = 24;
        Stage sg = st();
        float[] at = toStage(cu, camV, csp, rTX, rTZ); // (where the camera's looking, on the floor)
        int gx0 = (int) Math.floor((at[0] - 280) / tile), gx1 = (int) Math.ceil((at[0] + 280) / tile);
        int gz0 = (int) Math.floor((at[1] - 280) / tile), gz1 = (int) Math.ceil((at[1] + 280) / tile);
        float midX = W / 2.0F;
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                float[][] cam = new float[4][];
                boolean ok = true;
                float[][] corners = {{gx * tile, gz * tile}, {gx * tile, (gz + 1) * tile}, {(gx + 1) * tile, (gz + 1) * tile}, {(gx + 1) * tile, gz * tile}};
                for (int i = 0; i < 4; i++) {
                    float[] l = toLine(corners[i][0], corners[i][1], csp, rTX, rTZ);
                    cam[i] = view(l[0], 0, l[1]);
                    if (cam[i][2] < 6) ok = false;
                }
                if (!ok) continue;
                float dist = (cam[0][2] + cam[2][2]) / 2;
                float cx = (gx + 0.5F) * tile, cz = (gz + 0.5F) * tile;
                boolean inside = cx > arena[0] && cx < arena[1] && cz > arena[2] && cz < arena[3];
                boolean light = Math.floorMod(gx + gz, 2) == 0;
                int base;
                if (!inside && sg.walls()) { // (beyond the walls)
                    base = sg.outside();
                    if (sg.feature() == 5) base = mixRgb(base, 0x3A6AA0, 0.3F + 0.3F * Mth.sin(vt() * 0.15F + gx * 0.9F + gz * 0.5F)); // (the sea)
                    if (sg.feature() == 3) base = mixRgb(base, 0xFFB040, 0.3F + 0.3F * Mth.sin(vt() * 0.2F + gx + gz * 1.3F)); // (lava)
                    if (sg.feature() == 0 || sg.feature() == 6) base = mixRgb(base, 0x000000, light ? 0 : 0.12F);
                } else {
                    boolean below = floorBroken && sg.floorBreak(); // (through the floor: the one below)
                    base = below ? (light ? sg.lowA() : sg.lowB()) : light ? sg.tileA() : sg.tileB();
                    float mx = cx - midX, rr = Mth.sqrt(mx * mx + cz * cz); // (the ring in the middle of the arena)
                    if (!below && Math.abs(rr - 60) < 12) base = light ? sg.ringA() : sg.ringB();
                    if (!below && rr < 14) base = sg.middle();
                    if (sg.feature() == 3 && !below && Math.floorMod(gx * 7 + gz * 3, 11) == 0) { // (the forge: lava in the cracks)
                        base = mixRgb(base, 0xFF6A1A, 0.5F + 0.3F * Mth.sin(vt() * 0.2F + gx));
                    }
                    if (sg.walls() && (Math.abs(cx - arena[0]) < tile || Math.abs(cx - arena[1]) < tile
                            || Math.abs(cz - arena[2]) < tile || Math.abs(cz - arena[3]) < tile)) {
                        base = mixRgb(base, 0x000000, 0.18F); // (the edge by the walls, darker)
                    }
                }
                if (inside || !sg.walls()) { // (each tile a touch different; a glossy one catching the light)
                    float vary = (Math.floorMod(gx * 73 + gz * 151, 17) - 8) * 0.006F;
                    base = vary > 0 ? mixRgb(base, 0xFFFFFF, vary) : mixRgb(base, 0x000000, -vary);
                }
                int col = mixRgb(base, sg.haze(), (dist - 150) / 650);
                boolean grout = dist < 420 && (inside || !sg.walls());
                if (grout) { // (the grout: the tile drawn a little inside a darker square)
                    int gc = mixRgb(darker(base, 0.35F), sg.haze(), (dist - 150) / 650);
                    quad(cam, new int[] {gc, gc, gc, gc}, 1.0F, 11);
                    float[][] in = new float[4][];
                    float[][] innerCorners = {{gx * tile + 0.8F, gz * tile + 0.8F}, {gx * tile + 0.8F, (gz + 1) * tile - 0.8F},
                            {(gx + 1) * tile - 0.8F, (gz + 1) * tile - 0.8F}, {(gx + 1) * tile - 0.8F, gz * tile + 0.8F}};
                    for (int i = 0; i < 4; i++) {
                        float[] l = toLine(innerCorners[i][0], innerCorners[i][1], csp, rTX, rTZ);
                        in[i] = view(l[0], 0.02F, l[1]);
                    }
                    int hi = sg.glossy() ? mixRgb(col, 0xFFFFFF, 0.08F) : col;
                    quad(in, new int[] {hi, col, col, hi}, sg.glossy() ? 0.86F : 1.0F, 11); // (glossy: see-through a touch, for the reflections)
                } else {
                    quad(cam, new int[] {col, col, col, col}, 1.0F, 11);
                }
            }
        }
    }

    // ---- props round the arena, and the weather ----

    /** A box standing in the arena's space (centre, size, from its bottom to its top), lit like the fighters. */
    private void prop(float cx, float cz, float sx, float sz, float y0, float y1, int rgb, float alpha) {
        float[] at = toStage(cu, camV, csp, rTX, rTZ);
        if (Math.abs(cx - at[0]) > 320 || Math.abs(cz - at[1]) > 320) return;
        float[][] corner = new float[8][];
        for (int i = 0; i < 8; i++) {
            float x = cx + ((i & 1) == 0 ? -sx : sx) / 2, z = cz + ((i & 4) == 0 ? -sz : sz) / 2, y = (i & 2) == 0 ? y0 : y1;
            float[] l = toLine(x, z, csp, rTX, rTZ);
            corner[i] = view(l[0], y, l[1]);
            if (corner[i][2] < 6) return;
        }
        float[] centre = new float[3];
        for (float[] c : corner) for (int k = 0; k < 3; k++) centre[k] += c[k] / 8;
        int[][] faces = {{0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}};
        float[] light = norm(new float[] {-0.45F, 0.8F, -0.4F});
        int base = mulRgb(rgb, st().light());
        for (int[] fc : faces) {
            float[][] q = {corner[fc[0]], corner[fc[1]], corner[fc[2]], corner[fc[3]]};
            float[] n = norm(cross(sub(q[1], q[0]), sub(q[3], q[0])));
            float[] fcen = {(q[0][0] + q[2][0]) / 2, (q[0][1] + q[2][1]) / 2, (q[0][2] + q[2][2]) / 2};
            if (dot(n, sub(fcen, centre)) < 0) n = new float[] {-n[0], -n[1], -n[2]};
            float[] toCam = norm(new float[] {-fcen[0], camH - fcen[1], -fcen[2]});
            if (dot(n, toCam) <= 0.0F) continue;
            float k = 0.45F + 0.65F * Math.max(0, dot(n, light));
            int c = mixRgb(shade(base, k, 0), st().haze(), (fcen[2] - 150) / 650);
            quad(q, new int[] {c, c, c, c}, alpha, 0);
        }
    }

    /** A glowing thing (a lamp, a fire, a crystal): bright, with a soft halo. */
    private void glowProp(float cx, float cz, float s, float y0, float y1, int rgb) {
        prop(cx, cz, s, s, y0, y1, rgb, 1.0F);
        float pulse = 0.85F + 0.15F * Mth.sin(vt() * 0.3F + cx);
        prop(cx, cz, s * 2.6F, s * 2.6F, y0 - (y1 - y0) * 0.5F, y1 + (y1 - y0) * 0.5F, rgb, 0.16F * pulse);
    }

    /** What stands round each arena (outside its walls): it turns with the floor. */
    private void props() {
        float r = 112, mid = W / 2.0F;
        switch (st().feature()) {
            case 0 -> { // the temple: stone lanterns at the corners, a torii gate behind
                for (float[] c : new float[][] {{-r, -r}, {r, -r}, {-r, r}, {r, r}, {0, -r - 20}, {0, r + 20}}) {
                    float x = mid + c[0], z = c[1];
                    prop(x, z, 7, 7, 0, 3, 0x8A8478, 1);
                    prop(x, z, 3, 3, 3, 13, 0x9A9488, 1);
                    glowProp(x, z, 5, 13, 19, 0xFFC860);
                    prop(x, z, 10, 10, 19, 21, 0x6A645C, 1);
                    prop(x, z, 4, 4, 21, 24, 0x6A645C, 1);
                }
                for (float side : new float[] {-22, 22}) prop(mid + side, r + 50, 4, 4, 0, 48, 0xC83228, 1);
                prop(mid, r + 50, 64, 5, 44, 49, 0xC83228, 1);
                prop(mid, r + 50, 52, 3, 36, 39, 0xC83228, 1);
                prop(mid, r + 50, 70, 6, 49, 51, 0x2A2420, 1);
            }
            case 1 -> { // the rooftop: air-con units, a water tank, neon signs flickering
                for (float[] c : new float[][] {{-r, -60}, {r, 40}, {-40, r}, {70, -r}}) {
                    prop(mid + c[0], c[1], 22, 14, 0, 12, 0x5A5A66, 1);
                    prop(mid + c[0], c[1], 10, 10, 12, 13, 0x2A2A30, 1);
                }
                for (float[] c : new float[][] {{-r + 10, r - 10}, {r - 10, -r + 10}}) {
                    for (float sx : new float[] {-6, 6}) for (float sz : new float[] {-6, 6}) prop(mid + c[0] + sx, c[1] + sz, 1.5F, 1.5F, 0, 18, 0x3A3A44, 1);
                    prop(mid + c[0], c[1], 18, 18, 18, 36, 0x6A5040, 1);
                    prop(mid + c[0], c[1], 20, 20, 36, 38, 0x4A3A30, 1);
                }
                boolean on = ((int) vt() / 4) % 11 != 0;
                for (float[] c : new float[][] {{-60, -r - 10}, {50, r + 10}, {r + 10, -20}}) {
                    prop(mid + c[0], c[1], 2, 2, 0, 30, 0x2A2A30, 1);
                    glowProp(mid + c[0], c[1], 3, 30, 48, on ? 0xFF4AD0 : 0x6A2050);
                    glowProp(mid + c[0] + 0.1F, c[1] + 4, 3, 30, 48, on ? 0x40E0FF : 0x205060);
                }
            }
            case 2 -> { // the cavern: ice pillars all round, glinting crystals
                for (int i = 0; i < 12; i++) {
                    float a = i * Mth.TWO_PI / 12, rr = 150 + (i % 3) * 25;
                    float x = mid + Mth.cos(a) * rr, z = Mth.sin(a) * rr, h = 40 + (i * 13) % 30;
                    prop(x, z, 12, 12, 0, h * 0.6F, 0xA8D4E8, 0.9F);
                    prop(x, z, 8, 8, h * 0.6F, h, 0xC8E8F8, 0.9F);
                    if (i % 2 == 0) glowProp(x + 9, z, 3, 0, 8, 0x8AE8FF);
                }
            }
            case 3 -> { // the forge: braziers burning, anvils, stone pillars
                for (float[] c : new float[][] {{-r, -r}, {r, -r}, {-r, r}, {r, r}}) {
                    prop(mid + c[0], c[1], 14, 14, 0, 50, 0x3A2A24, 1);
                    prop(mid + c[0], c[1], 18, 18, 50, 54, 0x2A1E1A, 1);
                }
                for (float[] c : new float[][] {{0, -r}, {0, r}, {-r, 0}, {r, 0}}) {
                    prop(mid + c[0], c[1], 8, 8, 0, 10, 0x2A2A2E, 1);
                    float flick = 12 + 3 * Mth.sin(vt() * 0.9F + c[0]);
                    glowProp(mid + c[0], c[1], 6, 10, 10 + flick, 0xFF7A20);
                }
                prop(mid - r + 20, 30, 12, 6, 0, 6, 0x303034, 1);
                prop(mid - r + 20, 30, 16, 6, 6, 9, 0x404048, 1);
            }
            case 5 -> { // the harbour: crates stacked, bollards, lamps on posts
                for (float[] c : new float[][] {{-r, -50}, {-r + 4, -36}, {r, 60}, {50, -r}}) {
                    prop(mid + c[0], c[1], 13, 13, 0, 13, 0x8A6A3A, 1);
                }
                prop(mid - r, -43, 13, 13, 13, 26, 0x7A5A30, 1);
                for (float[] c : new float[][] {{-30, r}, {30, r}, {r, -30}, {-r, 30}}) {
                    prop(mid + c[0], c[1], 5, 5, 0, 6, 0x202428, 1);
                    prop(mid + c[0], c[1], 7, 7, 6, 7.5F, 0x202428, 1);
                }
                for (float[] c : new float[][] {{-r, -r}, {r, -r}, {-r, r}, {r, r}}) {
                    prop(mid + c[0], c[1], 2, 2, 0, 36, 0x202428, 1);
                    glowProp(mid + c[0], c[1], 4, 36, 41, 0xFFD890);
                }
            }
            case 6 -> { // the court: cherry trees in bloom, stone lanterns
                for (float[] c : new float[][] {{-r - 10, -r}, {r + 10, r}, {-r - 10, r - 20}, {r + 10, -r + 20}, {0, r + 30}}) {
                    float x = mid + c[0], z = c[1];
                    prop(x, z, 5, 5, 0, 26, 0x5A3A2A, 1);
                    for (int k = 0; k < 4; k++) {
                        float ox = Mth.sin(k * 2.1F + c[0]) * 8, oz = Mth.cos(k * 1.7F + c[1]) * 8;
                        prop(x + ox, z + oz, 16, 16, 22 + k * 3, 34 + k * 3, k % 2 == 0 ? 0xFFB8D0 : 0xF098B8, 1);
                    }
                }
                for (float[] c : new float[][] {{-r, 0}, {r, 0}}) {
                    prop(mid + c[0], c[1], 7, 7, 0, 3, 0x8A8478, 1);
                    prop(mid + c[0], c[1], 3, 3, 3, 12, 0x9A9488, 1);
                    glowProp(mid + c[0], c[1], 5, 12, 17, 0xFFE0A0);
                    prop(mid + c[0], c[1], 9, 9, 17, 19, 0x6A645C, 1);
                }
            }
            default -> { // the void: crystals hanging in the dark, turning slowly
                for (int i = 0; i < 8; i++) {
                    float a = i * Mth.TWO_PI / 8 + vt() * 0.004F, rr = 130;
                    float y = 20 + 10 * Mth.sin(vt() * 0.05F + i);
                    glowProp(mid + Mth.cos(a) * rr, Mth.sin(a) * rr, 6, y, y + 16, i % 2 == 0 ? 0x8A3AFF : 0xFF3AFF);
                }
            }
        }
    }

    /** Over everything: clouds of it - rain on the rooftop, fireflies at the temple, shafts of sunlight, snow, ash. */
    private void weather(GuiGraphics g, int x0, int y0) {
        float turn = csp * 0.011F;
        switch (st().feature()) {
            case 1 -> { // rain, slanting down
                for (int i = 0; i < 70; i++) {
                    float x = Math.floorMod((int) (i * 37 + vt() * 2.2F - turn * 120), W + 20) - 10;
                    float y = Math.floorMod((int) (i * 61 + vt() * 7.5F), H + 10) - 10;
                    box(g, x0, y0, x, y, 0.6F, 4, 0x60B0C8FF);
                }
            }
            case 0 -> { // fireflies drifting low over the grass, and a warm shaft of evening light
                for (int i = 0; i < 18; i++) {
                    float x = Math.floorMod((int) (i * 47 + Mth.sin(vt() * 0.05F + i) * 14 - turn * 160), W);
                    float y = horizon + 6 + (i * 13) % 30 + Mth.sin(vt() * 0.08F + i * 2) * 4;
                    int a = (int) (120 + 120 * Mth.sin(vt() * 0.2F + i * 1.3F));
                    box(g, x0, y0, x, y, 1, 1, (Math.max(0, a) << 24) | 0xFFF080);
                }
                shafts(g, x0, y0, 0xFFC890);
            }
            case 6 -> shafts(g, x0, y0, 0xFFF0E0);
            case 3 -> { // ash drifting up through the heat
                for (int i = 0; i < 26; i++) {
                    float x = Math.floorMod((int) (i * 41 + Mth.sin(vt() * 0.04F + i) * 10 - turn * 100), W);
                    float y = Math.floorMod((int) (i * 23 - vt() * 0.8F), H);
                    box(g, x0, y0, x, y, 1, 1, 0x90FFA060);
                }
            }
            default -> {}
        }
    }

    /** Soft shafts of light slanting down from the sun. */
    private void shafts(GuiGraphics g, int x0, int y0, int rgb) {
        float turn = csp * 0.011F;
        float sunX = W * 0.68F - Mth.wrapDegrees(turn * Mth.RAD_TO_DEG) * 3.0F;
        for (int k = 0; k < 4; k++) {
            float a = (0.05F + 0.03F * Mth.sin(vt() * 0.03F + k)) * 255;
            for (int y = 0; y < H; y += 4) {
                float x = sunX - 30 - k * 22 - y * 0.6F;
                box(g, x0, y0, x, y, 6 + k * 2, 4, ((int) a << 24) | rgb);
            }
        }
    }

    /** The arena's walls, all four sides: screens of light (see-through where they're between the camera and them). */
    private void walls() {
        if (!st().walls()) return;
        float[] at = toStage(cu, camV, csp, rTX, rTZ);
        int glow = st().wall();
        for (int side = 0; side < 4; side++) {
            boolean alongX = side >= 2;
            float fixed = arena[side];
            float from = alongX ? arena[0] : arena[2], to = alongX ? arena[1] : arena[3];
            float push = side % 2 == 0 ? -2 : 2; // (just outside the floor they stand on)
            for (float k = from; k < to; k += 16) {
                float k1 = Math.min(to, k + 16);
                float mid = (k + k1) / 2;
                if (Math.abs((alongX ? mid : fixed) - at[0]) > 300 || Math.abs((alongX ? fixed : mid) - at[1]) > 300) continue;
                float[] a = alongX ? new float[] {k, fixed + push} : new float[] {fixed + push, k};
                float[] b = alongX ? new float[] {k1, fixed + push} : new float[] {fixed + push, k1};
                float[] la = toLine(a[0], a[1], csp, rTX, rTZ), lb = toLine(b[0], b[1], csp, rTX, rTZ);
                boolean nearSide = (la[1] + lb[1]) / 2 < camV - 20; // (between the camera and the fight)
                float[][] cam = {view(la[0], 0, la[1]), view(la[0], 40, la[1]), view(lb[0], 40, lb[1]), view(lb[0], 0, lb[1])};
                quad(cam, new int[] {glow, mixRgb(glow, 0xFFFFFF, 0.5F), mixRgb(glow, 0xFFFFFF, 0.5F), glow}, nearSide ? 0.06F : 0.2F, 0);
                float[][] rim = {view(la[0], 0, la[1]), view(la[0], 2, la[1]), view(lb[0], 2, lb[1]), view(lb[0], 0, lb[1])};
                quad(rim, new int[] {glow, glow, glow, glow}, nearSide ? 0.3F : 0.7F, 0); // (a bright line where wall meets floor)
            }
        }
    }

    /** Draws a fighter where it is between its last tick and this one (smooth at any frame rate), then puts it back. */
    private void between(Body b, float partial, Runnable draw) {
        float x = b.x, y = b.y, d = b.depth;
        if (Math.abs(b.x - b.ox) < 40) { // (not across a teleport-like jump: a throw over the shoulder)
            // where it was and is on the floor, in between - then along the line as it's drawn now
            float[] s0 = toStage(b.ox, b.oDepth, oSpin, oTX, oTZ), s1 = toStage(x, d, spin, tX, tZ);
            float[] l = toLine(Mth.lerp(partial, s0[0], s1[0]), Mth.lerp(partial, s0[1], s1[1]), csp, rTX, rTZ);
            b.x = l[0];
            b.y = Mth.lerp(partial, b.oy, y);
            b.depth = l[1];
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
        for (Body b : new Body[] {me, foe}) between(b, partial, () -> reflection(b, partial));
        flush(g, x0, y0, false);
        for (Body b : new Body[] {me, foe}) between(b, partial, () -> shadow(b));
        flush(g, x0, y0, false);
        walls();
        props();
        for (Body b : new Body[] {me, foe}) between(b, partial, () -> fighter(b, partial));
        flush(g, x0, y0, true);
        effects(g, x0, y0);
        weather(g, x0, y0);
        g.pose().popPose();
        hud(g, font, x0, y0);
        if (showList) moveList(g, font, x0, y0);
        g.flush();
    }

    private static String rageDriveName(Body b) {
        return b.who == 2 ? "b+1+2" : b.who == 3 ? "qcf+1+2" : "f+1+2";
    }

    // ---- the move list: tabs of moves, each with its command (arrows, the four-button diamond), hit level, startup ----

    private static final String[] LIST_TABS = {"BASICS", "STRINGS", "SPECIAL", "SYSTEMS"};

    /** A row: the command (move-list notation), the move (or none: a system), its name and what to know about it. */
    private record Row(String cmd, Move move, String name, String note) {}

    private static Row row(String cmd, Move m, String note) {
        return new Row(cmd, m, m.name(), note);
    }

    private static Row sys(String cmd, String name, String note) {
        return new Row(cmd, null, name, note);
    }

    private List<Row> listRows(Body b, int tab) {
        List<Row> l = new ArrayList<>();
        switch (tab) {
            case 0 -> {
                l.add(row("1", JAB, "Quick, safe. Your fastest punish"));
                l.add(row("2", CROSS, "Starts 1,2 strings"));
                l.add(row("3", MID_KICK, ""));
                l.add(row("4", HIGH_KICK, ""));
                l.add(row("df+1", MID_CHECK, "Plus on block. Counter hit: crumple"));
                l.add(row("df+2", LAUNCHER, "LAUNCHER - start your juggle"));
                l.add(row("df+3", FRONT_KICK, ""));
                l.add(row("df+4", STEP_KICK, "Plus on hit"));
                l.add(row("d+1", CROUCH_JAB, "Ducks highs"));
                l.add(row("d+2", CROUCH_STRAIGHT, "Ducks highs"));
                l.add(row("d+3", LOW_KICK, "Hits them lying down"));
                l.add(row("d+4", TOE_KICK, "Quick low"));
                l.add(row("db+4", SWEEP, "Knocks down - no tech"));
                l.add(row("f+2", LUNGE_PUNCH, "Juggle ender: carries to the wall"));
                l.add(row("f+3", SPIRAL_KICK, "SCREW - spins a juggle on"));
                l.add(row("f+4", HOMING_KICK, "Catches sidesteps. Counter hit: launch"));
                l.add(row("b+2", SPIN_FIST, "SCREW. Counter hit: crumple"));
                l.add(row("b+1+2", IRON_PALM, "POWER CRUSH - soaks highs and mids"));
                l.add(row("uf+4", HOPKICK, "LAUNCHER - hops over lows"));
                l.add(row("WS 1", RISING_JAB, "Standing up from a crouch"));
                l.add(row("WS 2", RISING_UPPER, "LAUNCHER - punish a blocked low"));
                l.add(row("WS 3", RISING_KNEE, ""));
                l.add(row("WS 4", RISING_KICK, "Quick punisher after a low"));
                l.add(row("1+3", THROW_L, "Break: 1"));
                l.add(row("2+4", THROW_R, "Break: 2"));
                l.add(row("d+3+4", STOMP, "Only on someone lying down"));
                l.add(row("RUN 1", RUN_CHOP, "Running: f,f,f held"));
                l.add(row("RUN 3", RUN_SLIDE, ""));
                l.add(row("RUN 4", RUN_KICK, ""));
                l.add(row("BT 1", BT_BACKFIST, "Back turned: turns you round"));
                l.add(row("BT 4", MULE_KICK, "Back turned: kicks behind you"));
            }
            case 1 -> {
                l.add(row("1,1", JAB_2, "Natural combo"));
                l.add(row("1,1,2", ELBOW_FINISH, ""));
                l.add(row("1,2", CROSS, "Natural combo"));
                l.add(row("1,2,4", CROSS_KICK, "High - duckable"));
                l.add(row("1,2,3", CROSS_LOW, "Low ender - mix it with 1,2,4"));
                l.add(row("3,4", WHIRL_KICK, ""));
                l.add(sys("df+2 1,1 b+2 f,f+2", "BASIC JUGGLE", "Launch, jab twice, screw, dash in, finish"));
            }
            case 2 -> {
                switch (b.who) {
                    case 0 -> {
                        l.add(sys("f,n,d,df", "CROUCH DASH", "Low and fast - under highs"));
                        l.add(row("CD 1", SNAP_ELBOW, ""));
                        l.add(row("CD 2", DRAGON_UPPER, "LAUNCHER"));
                        l.add(row("CD df+2", PERFECT_DRAGON, "Just frame: 2 on the very frame you hit df"));
                        l.add(row("CD 4", HELL_SWEEP, "Low, knocks down"));
                        l.add(sys("CD f,n,d,df", "WAVEDASH", "Crouch dash out of a crouch dash"));
                        l.add(row("b+4", DEMON_HEEL, "Counter hit: launch"));
                        l.add(row("f+1+2", TWIN_PALM, "Carries to the wall"));
                    }
                    case 1 -> {
                        l.add(sys("f+3+4", "CAT STANCE", "Low: highs go over you. b: out"));
                        l.add(row("CAT 1", PAW_JAB, ""));
                        l.add(row("CAT 2", POUNCE, "LAUNCHER"));
                        l.add(row("CAT 3", TAIL_SWEEP, "Low, knocks down"));
                        l.add(row("CAT 4", CAT_FLIP, "LAUNCHER - leaves you back turned"));
                        l.add(sys("3+4", "TURN AWAY", "Back turned: BT moves (can't block!)"));
                        l.add(row("SS 1", CLAW_SWIPE, "Out of a sidestep"));
                        l.add(row("f,f+3", CAT_SLIDE, "Dashing low"));
                        l.add(row("b+3", BACKFLIP, "LAUNCHER"));
                    }
                    case 2 -> {
                        l.add(sys("b+3+4", "SIEGE", "Planted: soaks highs and mids"));
                        l.add(row("SIEGE 1", PISTON_JAB, ""));
                        l.add(row("SIEGE 2", CANNON, "POWER CRUSH, huge"));
                        l.add(row("SIEGE 3", TREAD_SWEEP, ""));
                        l.add(row("SIEGE 4", STOMP_KICK, "Counter hit: crumple"));
                        l.add(row("df+1+2", IRON_GRAB, "Break: 1+2. Then 1+2: chain throw"));
                        l.add(row("db+1+2", SCRAP_GRAB, "Only catches someone crouching"));
                        l.add(row("f+1+2", PISTON, "POWER CRUSH"));
                        l.add(row("d+1+2", GROUND_SLAM, "Hits them lying down"));
                    }
                    default -> {
                        l.add(sys("d,db,b", "SWAY", "Leans out of highs (and mids, at first)"));
                        l.add(row("SWAY 1", SWAY_JAB, ""));
                        l.add(row("SWAY 2", SWAY_BURNER, "Carries to the wall"));
                        l.add(row("SWAY 3", SWAY_SWEEP, ""));
                        l.add(row("SWAY 4", SWAY_KICK, ""));
                        l.add(row("d,df,f+2", BURNING_FIST, "Carries to the wall"));
                        l.add(row("b+4", FLAME_HEEL, "Counter hit: launch"));
                        l.add(row("f,f+1+2", HEAT_RUSH, "POWER CRUSH"));
                    }
                }
                l.add(row("df+1+2", RAGE_ART, "In rage (low health). Also L"));
                l.add(row(rageDriveName(b), rageDrive(b), "RAGE DRIVE - in rage"));
            }
            default -> {
                l.add(sys("u (hold)", "JUMP", "ub / uf: back / forward"));
                l.add(sys("u,u", "SIDESTEP IN", "Quick. Hold: sidewalk"));
                l.add(sys("d (tap)", "SIDESTEP OUT", "Further. Hold: crouch"));
                l.add(sys("f,f", "DASH", "Hold f: run"));
                l.add(sys("b,b", "BACKDASH", "b,b again: chained"));
                l.add(sys("b,b,db,b", "KOREAN BACKDASH", "Backdash, crouch-cancel, again"));
                l.add(sys("df", "LOW PARRY", "Just as a low comes in"));
                l.add(sys("1+2+3+4", "KI CHARGE", "Next hit can't be blocked"));
                l.add(sys("1 / 2", "THROW BREAK", "The arm that grabs you"));
                l.add(sys("1 / 2", "TECH ROLL", "As you hit the floor"));
                l.add(sys("f", "KIP-UP", "Lying down: up at once"));
                l.add(sys("b / u / d", "ROLL", "Lying down: away or aside"));
                l.add(sys("3 / 4", "GET-UP KICK", "Lying down: low / mid"));
                l.add(sys("3+4", "SPRING KICK", "Lying down"));
                l.add(sys("BT", "BACK TURNED", "Can't block. A direction turns you"));
                l.add(sys("P", "PRACTICE", "Dummy, block all, CPU, playback, trials"));
                l.add(sys("O", "RECORD", "Practice: record, then P to playback"));
            }
        }
        return l;
    }

    private static final int ROW_H = 11, LIST_ROWS = 9;

    /** The move list over the paused fight. */
    private void moveList(GuiGraphics g, Font font, int x0, int y0) {
        box(g, x0, y0, 0, 0, W, H, 0xE00A0614);
        // the header: the fighter's name on a red bar, gold edged
        box(g, x0, y0, 0, 0, W, 15, 0xFF6A0A14);
        box(g, x0, y0, 0, 15, W, 1, 0xFFFFC850);
        g.pose().pushPose();
        g.pose().translate(x0 + 4, y0 + 4, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        g.drawString(font, "MOVE LIST", 0, 0, 0xFFFFC850, true);
        g.drawString(font, me.style.name(), (int) ((W - 8) / 0.75F) - font.width(me.style.name()), 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        // the tabs
        float tw = W / (float) LIST_TABS.length;
        for (int i = 0; i < LIST_TABS.length; i++) {
            boolean on = i == listTab;
            box(g, x0, y0, i * tw + 1, 17, tw - 2, 9, on ? 0xFFFFC850 : 0xFF2A2036);
            small(g, font, LIST_TABS[i], x0 + i * tw + tw / 2, y0 + 19, on ? 0xFF1A0A14 : 0xFFB0A8C0, true);
        }
        List<Row> rows = listRows(me, listTab);
        listSel = Mth.clamp(listSel, 0, rows.size() - 1);
        if (listSel < listTop) listTop = listSel;
        if (listSel >= listTop + LIST_ROWS) listTop = listSel - LIST_ROWS + 1;
        int top = 29;
        for (int k = 0; k < LIST_ROWS && listTop + k < rows.size(); k++) {
            int i = listTop + k;
            Row r = rows.get(i);
            int y = top + k * ROW_H;
            boolean sel = i == listSel;
            box(g, x0, y0, 2, y, W - 4, ROW_H - 1, sel ? 0xC0FFC850 : k % 2 == 0 ? 0x60201830 : 0x40100C1C);
            if (sel) box(g, x0, y0, 2, y, 2, ROW_H - 1, 0xFFFFFFFF);
            // its name, its command, its level and startup
            int nameCol = sel ? 0xFF1A0A14 : 0xFFFFFFFF;
            small(g, font, r.name(), x0 + 6, y0 + y + 3, nameCol, false);
            command(g, font, r.cmd(), x0 + 92, y0 + y + 1, sel);
            if (r.move() != null) {
                Level lv = r.move().level();
                int lc = lv == Level.HIGH ? 0xFFE0405A : lv == Level.MID ? 0xFFE8C040 : lv == Level.LOW ? 0xFF40B8E8 : 0xFFB060E0;
                box(g, x0, y0, W - 28, y + 2, 7, 6, lc);
                small(g, font, lv == Level.THROW ? "T" : lv.name().substring(0, 1), x0 + W - 24.5F, y0 + y + 3, 0xFF101018, true);
                small(g, font, "i" + r.move().startup(), x0 + W - 6, y0 + y + 3, sel ? 0xFF1A0A14 : 0xFFB0D8FF, true, true);
            }
        }
        // the scroll bar
        if (rows.size() > LIST_ROWS) {
            float barH = LIST_ROWS * ROW_H * LIST_ROWS / (float) rows.size();
            float barY = top + (LIST_ROWS * ROW_H - barH) * listTop / (float) (rows.size() - LIST_ROWS);
            box(g, x0, y0, W - 2, top, 1, LIST_ROWS * ROW_H, 0x40FFFFFF);
            box(g, x0, y0, W - 2, barY, 1, barH, 0xFFFFC850);
        }
        // the one picked: its frame data and what it does
        Row r = rows.get(listSel);
        int fy = top + LIST_ROWS * ROW_H + 2;
        box(g, x0, y0, 0, fy - 1, W, 1, 0xFFFFC850);
        String data = r.move() == null ? "" : "BLOCK " + plus(r.move().onBlock())
                + (r.move().is(KND) || r.move().launch() > 0 ? "   HIT: DOWN" : "   HIT " + plus(r.move().onHit())) + "   DMG " + r.move().damage() + "   ";
        small(g, font, data + r.note(), x0 + 4, y0 + fy + 2, 0xFFFFE070, false);
        small(g, font, "W/S: move   A/D: tab   H: back to the fight", x0 + W / 2.0F, y0 + H - 6, 0xFF8A80A0, true);
    }

    /** Small writing (half size): centred, or right-aligned. */
    private static void small(GuiGraphics g, Font font, String s, float x, float y, int colour, boolean centred) {
        small(g, font, s, x, y, colour, centred, false);
    }

    private static void small(GuiGraphics g, Font font, String s, float x, float y, int colour, boolean centred, boolean right) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5F, 0.5F, 1);
        int w = font.width(s);
        g.drawString(font, s, right ? -w : centred ? -w / 2 : 0, 0, colour, false);
        g.pose().popPose();
    }

    private static final String[] ARROWS = {"", "\u2199", "\u2193", "\u2198", "\u2190", "\u2022", "\u2192", "\u2196", "\u2191", "\u2197"};

    /**
     * A command as the move lists draw it: each direction an arrow, each button press the four-button diamond (1 top
     * left, 2 top right, 3 bottom left, 4 bottom right) with the pressed ones lit, commas as little gaps, and the rest
     * (WS, CD, RUN, BT, a stance's name ...) as words.
     */
    private void command(GuiGraphics g, Font font, String cmd, float x, float y, boolean sel) {
        float cx = x;
        int ink = sel ? 0xFF1A0A14 : 0xFFFFFFFF;
        for (String step : cmd.split(",")) {
            step = step.trim();
            for (String part : step.split(" ")) {
                if (part.isEmpty()) continue;
                String dirPart = part, buttons = "";
                int plus = part.indexOf('+');
                if (part.matches("[1-4](\\+[1-4])*")) {
                    dirPart = "";
                    buttons = part;
                } else if (plus > 0 && part.substring(plus + 1).matches("[1-4](\\+[1-4])*")) {
                    dirPart = part.substring(0, plus);
                    buttons = part.substring(plus + 1);
                }
                int d = java.util.Arrays.asList(NOTATION).indexOf(dirPart);
                if (d > 0) { // (an arrow in a little ring; n a dot)
                    box(g, 0, 0, cx, y + 1, 7, 7, sel ? 0x60000000 : 0x60FFFFFF);
                    small(g, font, ARROWS[d], cx + 3.5F, y + 2.5F, ink, true);
                    cx += 8;
                } else if (!dirPart.isEmpty() && d < 0) { // (a word)
                    small(g, font, dirPart, cx, y + 2.5F, sel ? 0xFF5A1020 : 0xFFFF9AB0, false);
                    cx += font.width(dirPart) * 0.5F + 2;
                }
                if (!buttons.isEmpty()) {
                    if (d > 0) cx -= 0.5F;
                    boolean[] on = new boolean[5];
                    for (String bt : buttons.split("\\+")) on[Integer.parseInt(bt)] = true;
                    // the diamond of four: lit ones bright
                    float[][] at = {{0, 0}, {0, 0}, {4, 0}, {0, 4}, {4, 4}};
                    for (int k = 1; k <= 4; k++) {
                        box(g, 0, 0, cx + at[k][0], y + 1 + at[k][1], 3, 3, on[k] ? (k <= 2 ? 0xFFFFE070 : 0xFF80E0FF) : (sel ? 0x50000000 : 0x40FFFFFF));
                    }
                    cx += 9;
                }
            }
            cx += 2; // (a comma: a little gap)
        }
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
            big(g, font, practiceName() + (recordingNow ? "  - REC " + (RECORD_MAX - (ticks - recordFrom)) / FPS : ""), x0 + W / 2.0F, y0 + 24, 0.55F,
                    recordingNow && ((int) vt() / 6) % 2 == 0 ? 0xFFFF5050 : 0xFF80D0FF);
            // the input display: what you've pressed, frames apart
            g.pose().pushPose();
            g.pose().translate(x0 + 4, y0 + 44, 0);
            g.pose().scale(0.5F, 0.5F, 1);
            List<Object[]> shown = myPad.shown;
            for (int i = 0; i < shown.size(); i++) {
                Object[] e = shown.get(i);
                int gap = i == 0 ? 0 : (Integer) e[1] - (Integer) shown.get(i - 1)[1];
                g.drawString(font, e[0] + (i == 0 ? "" : "  " + gap + "f"), 0, i * 10, (Boolean) e[2] ? 0xFFFFE070 : 0xFFB0B0C8, true);
            }
            g.pose().popPose();
            if (practice >= 5) { // (the trial: its moves, the ones done lit)
                Move[] tr = trial();
                g.pose().pushPose();
                g.pose().translate(x0 + W - 4, y0 + 44, 0);
                g.pose().scale(0.5F, 0.5F, 1);
                for (int i = 0; i < tr.length; i++) {
                    String t = (i < trialStep ? "+ " : "  ") + tr[i].name();
                    g.drawString(font, t, -font.width(t), i * 10, i < trialStep ? 0xFF60FF80 : 0xFFFFFFFF, true);
                }
                g.pose().popPose();
            }
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
        if (pickPhase == 1) {
            drawStagePick(g, font, x0, y0, partial);
            return;
        }
        for (int i = 0; i < PLAYABLE; i++) {
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
        for (int i = 0; i < PLAYABLE; i++) {
            big(g, font, FIGHTERS[i].name(), x0 + 30 + i * 44, y0 + 110, 0.75F, i == pick ? 0xFFFFE74A : 0xFFAAAAAA);
        }
        big(g, font, (mode == 0 ? "ARCADE" : "SURVIVAL") + "  (W/S)", x0 + W / 2.0F, y0 + 30, 0.6F, 0xFF80D0FF);
        Style s = FIGHTERS[pick];
        String stats = "SPEED " + "■".repeat(Math.round(s.speed() * 4)) + "  POWER " + "■".repeat(Math.round(s.power() * 4));
        big(g, font, stats, x0 + W / 2.0F, y0 + 122, 0.7F, 0xFF9AD8FF);
        if (((int) vt() / 10) % 2 == 0) big(g, font, "< >   U: FIGHT", x0 + W / 2.0F, y0 + 133, 0.75F, 0xFFFFFFFF);
        g.flush();
    }

    private final Body[] picks = new Body[PLAYABLE];

    /** Picking the stage: the stage itself, live behind its name. */
    private void drawStagePick(GuiGraphics g, Font font, int x0, int y0, float partial) {
        boolean random = stageChoice == PICKABLE.length;
        stage = random ? PICKABLE[(int) (vt() / 8) % PICKABLE.length] : PICKABLE[stageChoice]; // (random: flicking through them)
        floorBroken = false;
        cu = W / 2.0F;
        cd = 170;
        csp = vt() * 2;
        camV = 0;
        float sa = csp * SPIN_RAD; // (turning round the arena's middle)
        rTX = W / 2.0F - W / 2.0F * Mth.cos(sa);
        rTZ = -W / 2.0F * Mth.sin(sa);
        camH = 26 + cd * 0.1F;
        horizon = 122 - camH * FL / cd;
        sky(g, x0, y0);
        floor();
        walls();
        flush(g, x0, y0, true);
        big(g, font, "SELECT STAGE", x0 + W / 2.0F, y0 + 10, 0.9F, 0xFFFFFFFF);
        big(g, font, random ? "RANDOM" : STAGES[stage].name(), x0 + W / 2.0F, y0 + 60, 1.2F, 0xFFFFE74A);
        Stage sg = STAGES[stage];
        String about = random ? "a different one each fight" : !sg.walls() ? "no walls" : sg.wallBreak() && sg.floorBreak() ? "walls and floor break"
                : sg.wallBreak() ? "the walls break" : sg.floorBreak() ? "the floor breaks" : "walls";
        big(g, font, about, x0 + W / 2.0F, y0 + 74, 0.6F, 0xFFB0D8FF);
        if (((int) vt() / 10) % 2 == 0) big(g, font, "< >   U: FIGHT   K: BACK", x0 + W / 2.0F, y0 + 133, 0.7F, 0xFFFFFFFF);
        g.flush();
    }

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
