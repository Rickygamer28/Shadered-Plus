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
 * Shadered Showdown: a 3D-style fighting game against the cabinet, moving like the big 3D fighters. The two fighters
 * face each other on a line that turns with them: tap up or down to sidestep off it (into the screen or out of it) -
 * straight attacks then miss you, though homing ones turn to follow - and hold the step to keep circling. Walk, dash in
 * (forward twice) and run (keep holding), backdash out (back twice, and again - chained backdashes), crouch, jump.
 *
 * <p>Four limbs: U left punch, I right punch, J left kick, K right kick. Attacks hit high, mid or low: standing still or
 * holding back blocks high and mid, crouching blocks low and ducks under highs. Strings (punch, punch, kick), a low
 * sweep, a homing kick (forward + K), a launcher (down-forward + I) that sends them up for a juggle, throws (U+J or
 * I+K; break one with the matching punch). Hit them into the wall for a wall splat. Knocked down: press a direction to
 * roll up. Low on health you go into rage - harder hits, and a rage art (L) once. Counter hits on someone mid-move or
 * mid-sidestep. Pick from four fighters, beat the other three, best of three rounds each.
 */
public class Showdown extends ArcadeGame {
    private static final int GROUND = 128, ROUND_TICKS = 60 * 20, WALL_L = 10, WALL_R = W - 10;
    private static final int MAX_HP = 100;

    private enum Level { HIGH, MID, LOW, THROW }

    /** How a move looks while it's out: which limb, at which height. */
    private enum Pose { JAB, CROSS, LOWPUNCH, MIDKICK, HIGHKICK, LOWKICK, SWEEP, UPPER, THROW, AIRKICK, TACKLE, RAGE }

    /** A move: its speed (ticks before, during, after it hits), where and how hard, what it does on hit and block. */
    private record Move(String name, Pose pose, Level level, int startup, int active, int recovery, int damage, int reach,
                        int hitstun, int blockstun, float launch, boolean knockdown, boolean homing, Move[] next) {}

    private static final Move CROSS_KICK = new Move("CROSS KICK", Pose.HIGHKICK, Level.HIGH, 5, 2, 10, 12, 30, 14, 4, 0, true, false, null);
    private static final Move CROSS = new Move("CROSS", Pose.CROSS, Level.HIGH, 3, 2, 6, 8, 25, 10, 6, 0, false, false, new Move[] {CROSS_KICK});
    private static final Move JAB = new Move("JAB", Pose.JAB, Level.HIGH, 2, 2, 4, 5, 22, 8, 6, 0, false, false, new Move[] {CROSS});
    private static final Move MID_KICK = new Move("MID KICK", Pose.MIDKICK, Level.MID, 4, 2, 8, 10, 28, 12, 3, 0, false, false, null);
    private static final Move HIGH_KICK = new Move("HIGH KICK", Pose.HIGHKICK, Level.HIGH, 5, 3, 9, 13, 30, 14, 2, 0, false, true, null);
    private static final Move HOMING = new Move("HOMING KICK", Pose.MIDKICK, Level.MID, 6, 3, 11, 13, 30, 16, 1, 0, true, true, null);
    private static final Move LOW_PUNCH = new Move("CROUCH JAB", Pose.LOWPUNCH, Level.MID, 2, 2, 4, 4, 20, 6, 5, 0, false, false, null);
    private static final Move LOW_KICK = new Move("LOW KICK", Pose.LOWKICK, Level.LOW, 4, 2, 8, 6, 26, 9, 2, 0, false, false, null);
    private static final Move SWEEP = new Move("SWEEP", Pose.SWEEP, Level.LOW, 6, 3, 14, 9, 30, 0, -2, 0, true, false, null);
    private static final Move LAUNCHER = new Move("LAUNCHER", Pose.UPPER, Level.MID, 6, 2, 15, 12, 22, 0, -4, 6.2F, false, false, null);
    private static final Move THROW_L = new Move("THROW", Pose.THROW, Level.THROW, 4, 2, 10, 20, 18, 0, 0, 0, true, false, null);
    private static final Move THROW_R = new Move("THROW", Pose.THROW, Level.THROW, 4, 2, 10, 22, 18, 0, 0, 0, true, false, null);
    private static final Move AIR_KICK = new Move("JUMP KICK", Pose.AIRKICK, Level.MID, 3, 8, 4, 10, 26, 12, 4, 0, false, false, null);
    private static final Move TACKLE = new Move("RUNNING TACKLE", Pose.TACKLE, Level.MID, 0, 3, 14, 10, 20, 0, -2, 0, true, false, null);
    private static final Move RAGE_ART = new Move("RAGE ART", Pose.RAGE, Level.MID, 7, 3, 20, 36, 34, 0, -6, 0, true, true, null);

    private enum State { STAND, CROUCH, DASH, RUN, BACKDASH, SIDESTEP, SIDEWALK, JUMP, ATTACK, THROWN, HIT, BLOCK, AIR, SPLAT, DOWN, RISE, KO, WIN }

    /** A fighter: its look (palette colours), speed and strength. */
    private record Style(String name, int gi, int skin, int band, int hair, int kind, float speed, float power) {}

    // kind: 0 a headband, 1 cat ears, 2 an antenna, 3 flame hair
    private static final Style[] FIGHTERS = {
            new Style("RIKU", 10, 7, 6, 13, 0, 1.0F, 1.0F),
            new Style("NEKO", 7, 8, 15, 4, 1, 1.25F, 0.85F),
            new Style("ROBO", 13, 5, 12, 12, 2, 0.8F, 1.3F),
            new Style("BLAZE", 6, 8, 8, 7, 3, 1.05F, 1.1F)};

    private static final class Body {
        final Style style;
        float x, y, vx, vy;
        /** How far off the line between them it has stepped (pixels; + into the screen). */
        float depth;
        int facing = 1, hp = MAX_HP, wins, t, stun, combo, sideDir, airHits;
        State state = State.STAND;
        Move move;
        boolean hitDone, landed, rage, rageUsed, buffered;
        Move queued;
        /** A throw it's caught in: which punch breaks it (0 left, 1 right), and who threw. */
        int breakWith = -1;
        Body thrower;
        Move grab;

        Body(Style style, float x) {
            this.style = style;
            this.x = x;
        }

        boolean airborne() {
            return y > 0.01F || vy > 0;
        }

        boolean free() {
            return state == State.STAND || state == State.CROUCH || state == State.SIDEWALK || state == State.RUN;
        }

        boolean crouching() {
            return state == State.CROUCH || (state == State.ATTACK && move != null
                    && (move == LOW_PUNCH || move == LOW_KICK || move == SWEEP));
        }

        boolean down() {
            return state == State.DOWN || state == State.RISE || state == State.KO;
        }
    }

    private Body me, foe;
    private final List<float[]> sparks = new ArrayList<>(); // x, y, ticks left, colour
    private final List<Object[]> callouts = new ArrayList<>(); // text, x, y, ticks left
    private int pick, ladder, round, clock, roundOver, shake, thinkIn, lap;
    private boolean picking;
    private String banner = "";
    private int bannerFor;
    /** The camera turning round as the two step about (pixels the backdrop has slid). */
    private float spin;

    // input: what's held, and when the directions were last tapped (for dashes and sidesteps)
    private final Set<Integer> held = new HashSet<>();
    private boolean kL, kR, kU, kD;
    private int tapFwd = -100, tapBack = -100, upAt = -100, downAt = -100;

    @Override
    public String name() {
        return "SHADERED SHOWDOWN";
    }

    @Override
    public String[] help() {
        return new String[] {"WASD  U I punch  J K kick  L rage", "tap W/S sidestep  FF dash  BB backdash"};
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
        kL = kR = kU = kD = false;
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
        me = new Body(FIGHTERS[pick], 60);
        foe = new Body(foeStyle(), W - 60);
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
            b.stun = b.combo = b.t = 0;
            b.move = null;
            b.queued = null;
            b.rage = b.rageUsed = false;
            b.thrower = null;
            b.breakWith = -1;
        }
        me.facing = 1;
        foe.facing = -1;
        sparks.clear();
        callouts.clear();
        clock = ROUND_TICKS;
        roundOver = -40;
        say(round == 3 ? "FINAL ROUND" : "ROUND " + round, 40);
    }

    private void say(String s, int ticks) {
        banner = s;
        bannerFor = ticks;
    }

    private int difficulty() {
        return ladder + lap;
    }

    private static Body other(Body b, Body me, Body foe) {
        return b == me ? foe : me;
    }

    /** Toward the other one (+1) or away (-1), from a screen direction. */
    private static int fwd(Body b, int screenDir) {
        return screenDir * b.facing;
    }

    // ================= ticking =================

    @Override
    public void tick() {
        ticks++;
        if (picking) return;
        if (shake > 0) shake--;
        if (bannerFor > 0 && --bannerFor == 0 && roundOver == 0) banner = "";
        sparks.removeIf(s -> --s[2] <= 0);
        callouts.removeIf(c -> {
            c[3] = (Integer) c[3] - 1;
            return (Integer) c[3] <= 0;
        });
        if (roundOver < 0) {
            if (++roundOver == 0) banner = "";
            if (roundOver == -12) say("FIGHT!", 14);
            return;
        }
        if (roundOver > 0) {
            for (Body b : new Body[] {me, foe}) physics(b);
            if (--roundOver == 0) endRound();
            return;
        }
        if (--clock <= 0) {
            finishRound(me.hp >= foe.hp ? me : foe, "TIME");
            return;
        }
        player();
        cpu();
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
                if ((b.state == State.AIR || b.state == State.HIT) && Math.abs(b.vx) > 1.0F) {
                    b.state = State.SPLAT;
                    b.stun = 26;
                    b.vx = 0;
                    b.vy = 0;
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
                float d = Mth.clamp(-b.depth, -1.2F, 1.2F);
                b.depth += d;
                spin += d * (b == me ? 3.0F : -3.0F) * b.facing;
            }
        }
        face(me);
        face(foe);
        for (Body b : new Body[] {me, foe}) {
            if (!b.rage && b.hp > 0 && b.hp <= MAX_HP / 4) {
                b.rage = true;
                if (b == me) call("RAGE", b.x, GROUND - 50);
            }
        }
        if (me.hp <= 0) finishRound(foe, "K.O.");
        else if (foe.hp <= 0) finishRound(me, "K.O.");
    }

    private void face(Body b) {
        Body o = other(b, me, foe);
        if (b.free() && !b.airborne()) b.facing = o.x > b.x ? 1 : -1;
    }

    // ================= the player =================

    private void player() {
        Body b = me;
        boolean fwdHeld = (kR && b.facing > 0) || (kL && b.facing < 0);
        boolean backHeld = (kL && b.facing > 0) || (kR && b.facing < 0);
        if (b.state == State.THROWN || !b.free() && b.state != State.ATTACK) {
            return;
        }
        if (b.state == State.ATTACK) return;
        // holding up past a tap: a jump (forward, back or straight)
        if (kU && ticks - upAt >= 4 && b.state != State.SIDEWALK && b.state != State.RUN) {
            jump(b, fwdHeld ? 1 : backHeld ? -1 : 0);
            return;
        }
        if (b.state == State.RUN) {
            if (!fwdHeld) b.state = State.STAND;
            return;
        }
        if (b.state == State.SIDEWALK) {
            boolean still = b.sideDir > 0 ? kU : kD;
            if (!still) b.state = State.STAND;
            return;
        }
        if (kD) {
            b.state = State.CROUCH;
            b.vx = 0;
            return;
        }
        b.state = State.STAND;
        if (fwdHeld) b.x += b.facing * 1.0F * b.style.speed();
        else if (backHeld) b.x -= b.facing * 0.8F * b.style.speed();
    }

    // ================= moving =================

    /** Every tick: what its state does - dashing, stepping, getting up ... */
    private void step(Body b) {
        b.t++;
        switch (b.state) {
            case DASH -> {
                b.x += b.facing * Math.max(0.6F, 3.4F - b.t * 0.25F) * b.style.speed();
                boolean holding = b == me ? (b.facing > 0 ? kR : kL) : b.buffered;
                if (b.t >= 9) {
                    b.state = holding && b.t >= 9 ? State.RUN : State.STAND;
                    b.t = 0;
                }
            }
            case RUN -> {
                b.x += b.facing * Math.min(3.2F, 1.6F + b.t * 0.08F) * b.style.speed();
                Body o = other(b, me, foe);
                if (b.t > 8 && Math.abs(o.x - b.x) < 22 && Math.abs(o.depth - b.depth) < 8 && !o.down()) {
                    attack(b, TACKLE); // (running into them: a shoulder tackle)
                }
            }
            case BACKDASH -> {
                b.x -= b.facing * Math.max(0.3F, 3.6F - b.t * 0.3F) * b.style.speed();
                if (b.t >= 12) {
                    b.state = State.STAND;
                    b.t = 0;
                }
            }
            case SIDESTEP -> {
                b.depth += b.sideDir * Math.max(0.4F, 3.0F - b.t * 0.22F);
                if (b.t >= 10) {
                    boolean holding = b == me ? (b.sideDir > 0 ? kU : kD) : b.buffered;
                    b.state = holding ? State.SIDEWALK : State.STAND;
                    b.t = 0;
                }
            }
            case SIDEWALK -> {
                b.depth += b.sideDir * 1.1F;
                // circling round them: the camera follows
                spin += b.sideDir * 1.6F * b.facing * (b == me ? 1 : -1);
                b.depth = Mth.clamp(b.depth, -30, 30);
                if (b != me && !b.buffered) b.state = State.STAND;
            }
            case BLOCK, HIT -> {
                if (--b.stun <= 0) b.state = State.STAND;
                b.x += b.vx;
                b.vx *= 0.75F;
            }
            case SPLAT -> {
                if (--b.stun <= 0) {
                    b.state = State.AIR;
                    b.vy = 0.5F;
                    b.y = Math.max(b.y, 0.5F);
                }
            }
            case DOWN -> {
                if (--b.stun <= 0) {
                    b.state = State.RISE;
                    b.stun = 10;
                }
            }
            case RISE -> {
                if (--b.stun <= 0) b.state = State.STAND;
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
                } else if (b.t >= m.startup() + m.active() + m.recovery()) {
                    if (b.queued != null) {
                        Move q = b.queued;
                        b.queued = null;
                        attack(b, q, true);
                    } else {
                        b.state = b == me && kD ? State.CROUCH : State.STAND;
                        b.move = null;
                    }
                } else if (m == TACKLE || m == RAGE_ART) {
                    b.x += b.facing * (b.t < 4 ? 2.5F : 0.3F);
                }
            }
            default -> {}
        }
    }

    private void physics(Body b) {
        if (b.airborne()) {
            b.y += b.vy;
            b.x += b.vx;
            b.vy -= b.state == State.AIR ? 0.32F : 0.4F;
            if (b.y <= 0) {
                b.y = 0;
                b.vy = 0;
                b.vx = 0;
                if (b.state == State.AIR) { // (a juggle ends on the floor)
                    b.state = State.DOWN;
                    b.stun = 22;
                    b.airHits = 0;
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
        b.vy = 5.0F;
        b.y = 0.01F;
        b.vx = dir * b.facing * 1.3F * b.style.speed();
    }

    private void dash(Body b) {
        if (!b.free() || b.airborne()) return;
        b.state = State.DASH;
        b.t = 0;
    }

    private void backdash(Body b) {
        boolean chain = b.state == State.BACKDASH && b.t >= 5; // (cancelled into the next: chained backdashes)
        if ((!b.free() && !chain) || b.airborne()) return;
        b.state = State.BACKDASH;
        b.t = chain ? 2 : 0;
    }

    private void sidestep(Body b, int dir) {
        if ((!b.free() && b.state != State.CROUCH) || b.airborne()) return;
        b.state = State.SIDESTEP;
        b.sideDir = dir;
        b.t = 0;
    }

    // ================= attacking =================

    private void attack(Body b, Move m) {
        attack(b, m, false);
    }

    private void attack(Body b, Move m, boolean chained) {
        if (b.state == State.ATTACK && !chained) {
            // a string: the next hit of it, queued in time
            Move cur = b.move;
            if (cur != null && cur.next() != null && b.t >= cur.startup()) {
                for (Move n : cur.next()) {
                    if (n == m) {
                        b.queued = m;
                        if (b.t >= cur.startup() + cur.active()) { // (cancel the rest of the recovery)
                            b.queued = null;
                            attack(b, m, true);
                        }
                        return;
                    }
                }
            }
            return;
        }
        if (!chained && !b.free() && b.state != State.DASH && b.state != State.BACKDASH && b.state != State.SIDESTEP
                && !(b.state == State.JUMP && m == AIR_KICK)) {
            return;
        }
        if (m == RAGE_ART) {
            if (!b.rage || b.rageUsed) return;
            b.rageUsed = true;
            b.rage = false;
            say(b.style.name() + " RAGE ART!", 30);
            shake = 8;
        }
        b.state = State.ATTACK;
        b.move = m;
        b.t = 0;
        b.hitDone = false;
        b.landed = false;
    }

    /** Its attack's active ticks: does it reach them - at the right height, and on the line or homing? */
    private void strike(Body b, Body o) {
        if (b.state != State.ATTACK || b.hitDone || b.move == null) return;
        Move m = b.move;
        if (b.t < m.startup() || b.t >= m.startup() + m.active()) return;
        float reach = (o.x - b.x) * b.facing;
        if (reach < -4 || reach > m.reach()) return;
        if (Math.abs(o.y - b.y) > 30) return;
        // off the line: a straight attack misses (a homing one turns to follow)
        if (Math.abs(o.depth - b.depth) > 7 && !m.homing()) return;
        if (o.state == State.KO || o.state == State.RISE) return;
        if (o.state == State.DOWN && m.level() != Level.LOW) return; // (only lows hit someone lying down)
        if (o.state == State.THROWN) return;
        if (m.level() == Level.THROW) {
            if (o.crouching() || o.airborne() || o.down() || o.state == State.HIT) return; // (throws whiff on crouchers)
            b.hitDone = true;
            o.state = State.THROWN;
            o.thrower = b;
            o.stun = 18;
            o.breakWith = m == THROW_L ? 0 : 1;
            o.grab = m;
            o.t = 0;
            return;
        }
        if (m.level() == Level.HIGH && o.crouching()) return; // (ducked)
        b.hitDone = true;
        boolean standGuard = !o.crouching() && (o.state == State.STAND || o.state == State.BLOCK || (o.state == State.STAND && backHeld(o)));
        boolean blocked;
        if (o.state == State.AIR || o.state == State.SPLAT || o.state == State.DOWN) {
            blocked = false;
        } else if (m.level() == Level.LOW) {
            blocked = o.crouching() && (o == me ? kD : true) && o.state != State.ATTACK;
        } else {
            blocked = standGuard && !forwardHeld(o) && o.state != State.ATTACK;
        }
        if (blocked) {
            o.state = State.BLOCK;
            o.stun = Math.max(4, 8 + m.blockstun());
            o.vx = -o.facing * 2.0F;
            o.hp -= m.level() == Level.MID ? 1 : 0;
            spark(o.x - o.facing * 4, GROUND - o.y - (m.level() == Level.LOW ? 6 : 20), 6);
            return;
        }
        // a hit: a counter hit if they were mid-move or mid-step
        boolean counter = o.state == State.ATTACK && o.t < (o.move == null ? 0 : o.move.startup() + o.move.active())
                || o.state == State.SIDESTEP || o.state == State.DASH || o.state == State.BACKDASH;
        float scale = (b.rage ? 1.1F : 1.0F) * (counter ? 1.25F : 1.0F) * b.style.power();
        if (o.state == State.AIR) scale *= Math.max(0.3F, 0.8F - o.airHits * 0.1F); // (juggles hurt less the longer they go)
        if (o.state == State.DOWN) scale *= 0.5F;
        int dmg = Math.max(1, Math.round(m.damage() * scale));
        o.hp = Math.max(0, o.hp - dmg);
        b.landed = true;
        b.combo = o.state == State.HIT || o.state == State.AIR || o.state == State.SPLAT || o.state == State.DOWN ? b.combo + 1 : 1;
        if (b == me) score += dmg * 10 * Math.max(1, b.combo);
        if (counter && b == me) call("COUNTER HIT", o.x, GROUND - o.y - 44);
        spark(b.x + b.facing * m.reach() * 0.8F, GROUND - b.y - (m.level() == Level.LOW ? 4 : m.level() == Level.HIGH ? 28 : 18), 9);
        if (o.state == State.AIR || o.state == State.SPLAT) { // (keeps them up: the juggle goes on)
            o.state = State.AIR;
            o.airHits++;
            o.vy = Math.max(o.vy, 2.6F - o.airHits * 0.25F);
            o.vx = b.facing * 1.6F;
            o.y = Math.max(o.y, 1.0F);
        } else if (m.launch() > 0 || counter && m == HOMING) { // (launched)
            o.state = State.AIR;
            o.vy = m.launch() > 0 ? m.launch() : 4.5F;
            o.vx = b.facing * 0.9F;
            o.y = 1.0F;
            o.airHits = 0;
            shake = 4;
            if (b == me) call("LAUNCH!", o.x, GROUND - 52);
        } else if (m.knockdown() || o.airborne()) {
            land(o, b, m);
        } else if (o.state == State.DOWN) {
            o.stun = Math.max(o.stun, 8);
        } else {
            o.state = State.HIT;
            o.stun = m.hitstun() + (counter ? 4 : 0);
            o.vx = b.facing * 2.4F;
            o.t = 0;
        }
    }

    /** Knocked to the floor (thrown, swept, tackled ...). */
    private void land(Body o, Body by, Move m) {
        if (m != null && m.level() == Level.THROW) {
            o.hp = Math.max(0, o.hp - Math.round(m.damage() * (by != null ? by.style.power() : 1.0F)));
            if (by != null) {
                o.x = by.x - by.facing * 16; // (thrown over their shoulder)
                if (by == me) score += 300;
            }
            shake = 6;
        }
        o.state = State.DOWN;
        o.stun = 24;
        o.vx = 0;
        o.vy = 0;
        o.y = 0;
        o.t = 0;
        shake = Math.max(shake, 4);
    }

    private boolean backHeld(Body b) {
        if (b == me) return (kL && b.facing > 0) || (kR && b.facing < 0);
        return b.state == State.BLOCK;
    }

    private boolean forwardHeld(Body b) {
        if (b == me) return (kR && b.facing > 0) || (kL && b.facing < 0);
        return b.state == State.DASH || b.state == State.RUN;
    }

    private void spark(float x, float y, int ticks) {
        sparks.add(new float[] {x, y, ticks});
    }

    private void call(String text, float x, float y) {
        callouts.add(new Object[] {text, x, y, 28});
    }

    private void finishRound(Body winner, String how) {
        roundOver = 90;
        winner.wins++;
        Body loser = winner == me ? foe : me;
        if (loser.hp <= 0) {
            loser.state = State.KO;
            loser.y = 0;
            shake = 12;
        }
        winner.state = State.WIN;
        boolean perfect = winner.hp == MAX_HP;
        say(how + "  " + (winner == me ? "YOU WIN" : foe.style.name() + " WINS") + (perfect ? " - PERFECT" : ""), 90);
        if (winner == me) score += 300 + me.hp * 5 + (perfect ? 2000 : 0) + clock / 20 * 10;
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
            roundOver = -70;
        } else if (foe.wins >= 2) {
            over = true;
        } else {
            newRound();
        }
    }

    // ================= the cabinet's fighter =================

    private void cpu() {
        Body b = foe, o = me;
        int d = difficulty();
        if (b.state == State.THROWN) { // (breaking a throw: the right punch, in time)
            if (b.t == 3 + Math.max(0, 4 - d) && random.nextFloat() < 0.25F + 0.12F * d) breakThrow(b);
            return;
        }
        if (!b.free() || b.airborne()) {
            if (b.state == State.DOWN && b.stun > 6 && random.nextFloat() < 0.08F + 0.03F * d) { // (rolling up)
                b.state = State.RISE;
                b.stun = 6;
                b.x -= b.facing * 10;
            }
            return;
        }
        b.buffered = b.state == State.SIDEWALK && random.nextFloat() < 0.7F;
        float dist = Math.abs(o.x - b.x);
        // the player down: a sweep at them; juggling them: jabs and a kick
        if (o.state == State.AIR && dist < 30 && random.nextFloat() < 0.5F + 0.1F * d) {
            attack(b, random.nextBoolean() ? JAB : MID_KICK);
            return;
        }
        if (o.state == State.DOWN && dist < 30 && random.nextFloat() < 0.15F) {
            attack(b, LOW_KICK);
            return;
        }
        // the player attacking: block it right, step aside from a straight one, or back off
        if (o.state == State.ATTACK && o.move != null && o.t < o.move.startup() + o.move.active() && dist < o.move.reach() + 10) {
            float r = random.nextFloat();
            if (r < 0.12F + 0.06F * d && !o.move.homing() && o.move.level() != Level.THROW) {
                sidestep(b, random.nextBoolean() ? 1 : -1);
            } else if (r < 0.5F + 0.1F * d) {
                if (o.move.level() == Level.LOW) {
                    b.state = State.CROUCH;
                } else if (o.move.level() == Level.HIGH && random.nextFloat() < 0.4F) {
                    b.state = State.CROUCH; // (ducking under it)
                } else if (o.move.level() == Level.THROW) {
                    b.state = State.CROUCH;
                } else {
                    b.state = State.STAND;
                }
            } else if (r < 0.6F + 0.05F * d) {
                backdash(b);
            }
            return;
        }
        // the player recovering from a big move: punish it
        if (o.state == State.ATTACK && o.move != null && o.t >= o.move.startup() + o.move.active() && o.move.recovery() >= 12 && dist < 28
                && random.nextFloat() < 0.3F + 0.15F * d) {
            attack(b, LAUNCHER);
            return;
        }
        if (b.state == State.CROUCH && random.nextFloat() < 0.3F) b.state = State.STAND;
        if (--thinkIn > 0) {
            if (b.state == State.STAND && dist > 40 && random.nextFloat() < 0.6F) b.x += b.facing * b.style.speed();
            return;
        }
        thinkIn = Math.max(3, 10 - d * 2);
        float r = random.nextFloat();
        if (b.rage && !b.rageUsed && dist < 32 && r < 0.25F) {
            attack(b, RAGE_ART);
        } else if (dist > 70) {
            if (r < 0.35F) dash(b);
            else if (r < 0.45F) sidestep(b, random.nextBoolean() ? 1 : -1);
            else b.state = State.STAND;
            b.buffered = r < 0.2F;
        } else if (dist > 32) {
            if (r < 0.25F) dash(b);
            else if (r < 0.40F) attack(b, HOMING);
            else if (r < 0.52F) sidestep(b, random.nextBoolean() ? 1 : -1);
            else if (r < 0.62F) backdash(b);
            else if (r < 0.70F) jump(b, 1);
            else b.state = State.STAND;
        } else {
            if (r < 0.22F) attack(b, JAB);
            else if (r < 0.34F) attack(b, MID_KICK);
            else if (r < 0.44F) attack(b, LOW_KICK);
            else if (r < 0.50F) attack(b, SWEEP);
            else if (r < 0.58F) attack(b, random.nextBoolean() ? THROW_L : THROW_R);
            else if (r < 0.64F) attack(b, LAUNCHER);
            else if (r < 0.70F) attack(b, HIGH_KICK);
            else if (r < 0.78F) backdash(b);
            else if (r < 0.86F) sidestep(b, random.nextBoolean() ? 1 : -1);
            else b.state = random.nextBoolean() ? State.CROUCH : State.STAND;
        }
        // strings: carry on with the next hit
        if (b.state == State.ATTACK && b.move == JAB && random.nextFloat() < 0.5F + 0.1F * d) b.queued = CROSS;
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
        if (left(key)) kL = true;
        if (right(key)) kR = true;
        if (up(key)) kU = true;
        if (down(key)) kD = true;
        if (roundOver != 0 || me == null) return;
        Body b = me;
        // the directions: forward twice dashes, back twice backdashes (and again: chained), up or down tapped steps aside
        boolean isFwd = (right(key) && b.facing > 0) || (left(key) && b.facing < 0);
        boolean isBack = (left(key) && b.facing > 0) || (right(key) && b.facing < 0);
        if (isFwd) {
            if (ticks - tapFwd <= 8) dash(b);
            tapFwd = ticks;
        }
        if (isBack) {
            if (ticks - tapBack <= 8 || b.state == State.BACKDASH) backdash(b);
            tapBack = ticks;
        }
        if (up(key)) {
            upAt = ticks;
            if (b.state == State.DOWN) roll(b);
        }
        if (down(key)) {
            downAt = ticks;
            if (b.state == State.DOWN) roll(b);
        }
        if (b.state == State.THROWN) {
            if ((key == GLFW.GLFW_KEY_U && b.breakWith == 0) || (key == GLFW.GLFW_KEY_I && b.breakWith == 1)) breakThrow(b);
            return;
        }
        boolean lp = key == GLFW.GLFW_KEY_U, rp = key == GLFW.GLFW_KEY_I, lk = key == GLFW.GLFW_KEY_J, rk = key == GLFW.GLFW_KEY_K;
        boolean fwdHeld = (kR && b.facing > 0) || (kL && b.facing < 0);
        // throws: both left limbs, or both right ones, together
        if ((lp && held.contains(GLFW.GLFW_KEY_J)) || (lk && held.contains(GLFW.GLFW_KEY_U))) {
            attack(b, THROW_L);
            return;
        }
        if ((rp && held.contains(GLFW.GLFW_KEY_K)) || (rk && held.contains(GLFW.GLFW_KEY_I))) {
            attack(b, THROW_R);
            return;
        }
        if (b.state == State.JUMP && (lk || rk)) {
            attack(b, AIR_KICK);
            return;
        }
        if (lp) attack(b, kD ? LOW_PUNCH : JAB);
        if (rp) attack(b, kD && fwdHeld ? LAUNCHER : kD ? LOW_PUNCH : CROSS);
        if (lk) attack(b, kD ? LOW_KICK : MID_KICK);
        if (rk) attack(b, kD ? SWEEP : fwdHeld ? HOMING : b.state == State.ATTACK ? CROSS_KICK : HIGH_KICK);
        if (key == GLFW.GLFW_KEY_L) attack(b, RAGE_ART);
    }

    /** Getting up off the floor with a roll. */
    private void roll(Body b) {
        if (b.stun < 4) return;
        b.state = State.RISE;
        b.stun = 6;
        b.x -= b.facing * 10;
    }

    @Override
    public void keyReleased(int key) {
        held.remove(key);
        if (left(key)) kL = false;
        if (right(key)) kR = false;
        if (up(key)) {
            kU = false;
            // a quick tap: a sidestep into the screen
            if (me != null && !picking && roundOver == 0 && ticks - upAt < 4) sidestep(me, 1);
        }
        if (down(key)) {
            kD = false;
            // a quick tap: a sidestep out of the screen (a held one was a crouch)
            if (me != null && !picking && roundOver == 0 && ticks - downAt < 4 && me.state == State.CROUCH) sidestep(me, -1);
        }
    }

    // ================= how they look =================

    /** The fighter's shape now: boxes x y w h colour (palette), in field coordinates. */
    private List<float[]> shape(Body b) {
        List<float[]> out = new ArrayList<>();
        float x = b.x, base = GROUND - b.y + b.depth * 0.45F;
        float scale = 1.0F - b.depth * 0.006F;
        int f = b.facing;
        Style s = b.style;
        int gi = s.gi(), skin = s.skin(), band = s.band(), hair = s.hair();
        if ((b.state == State.HIT || b.state == State.AIR) && (ticks / 2) % 2 == 0) gi = 4;
        float baseY = base;
        java.util.function.Consumer<float[]> add = r -> {
            float rx = f > 0 ? r[0] : -r[0] - r[2];
            out.add(new float[] {x + rx * scale, baseY - (r[1] + r[3]) * scale, r[2] * scale, r[3] * scale, r[4]});
        };
        if (b.state == State.KO || b.state == State.DOWN || b.state == State.THROWN && b.t > 8) { // (lying flat)
            add.accept(new float[] {-18, 0, 28, 6, gi});
            add.accept(new float[] {-18, 0, 28, 2, band});
            add.accept(new float[] {-26, 0, 8, 7, skin});
            return out;
        }
        if (b.state == State.AIR) { // (tumbling in the air)
            boolean flip = (ticks / 4) % 2 == 0;
            add.accept(new float[] {-14, 4, 24, 7, gi});
            add.accept(new float[] {-14, 4, 24, 2, band});
            add.accept(new float[] {flip ? 10 : -22, 3, 8, 8, skin});
            return out;
        }
        if (b.state == State.SPLAT) { // (flat against the wall)
            add.accept(new float[] {-3, 4, 7, 22, gi});
            add.accept(new float[] {-3, 14, 7, 2, band});
            add.accept(new float[] {-3, 26, 7, 7, skin});
            add.accept(new float[] {-8, 22, 5, 3, gi});
            add.accept(new float[] {4, 22, 5, 3, gi});
            return out;
        }
        Pose pose = b.state == State.ATTACK && b.move != null ? b.move.pose() : null;
        boolean out1 = b.state == State.ATTACK && b.move != null && b.t >= b.move.startup() - 1 && b.t < b.move.startup() + b.move.active() + 2;
        float bob = (b.state == State.STAND && (ticks / 10) % 2 == 0) ? 1 : 0;
        boolean low = b.crouching() || b.state == State.RISE || pose == Pose.SWEEP || pose == Pose.LOWKICK || pose == Pose.LOWPUNCH;
        float drop = (low ? 9 : 0) + bob;
        float lean = switch (b.state) {
            case HIT -> -3;
            case DASH, RUN -> 3;
            case BACKDASH -> -3;
            default -> pose != null ? 1 : 0;
        };
        boolean side = b.state == State.SIDESTEP || b.state == State.SIDEWALK; // (turned: seen more from the front)
        // legs
        if (b.state == State.JUMP || pose == Pose.AIRKICK) {
            add.accept(new float[] {-5, 4, 4, 8, gi});
            if (pose == Pose.AIRKICK && out1) {
                add.accept(new float[] {1, 4, 22, 4, gi});
                add.accept(new float[] {22, 3, 5, 5, skin});
            } else {
                add.accept(new float[] {1, 6, 4, 7, gi});
            }
        } else if ((pose == Pose.MIDKICK || pose == Pose.HIGHKICK) && out1) {
            float hy = pose == Pose.HIGHKICK ? 22 : 12;
            add.accept(new float[] {-5, 0, 4, 12, gi});
            add.accept(new float[] {2, hy, 22, 4, gi});
            add.accept(new float[] {24, hy, 4, 4, skin});
        } else if ((pose == Pose.SWEEP || pose == Pose.LOWKICK) && out1) {
            add.accept(new float[] {-6, 0, 5, 4, gi});
            add.accept(new float[] {0, 0, pose == Pose.SWEEP ? 26 : 20, 3, gi});
            add.accept(new float[] {pose == Pose.SWEEP ? 26 : 20, 0, 4, 3, skin});
        } else {
            float stride = switch (b.state) {
                case DASH, RUN -> Mth.sin(ticks * 0.9F) * 4;
                case BACKDASH -> -3;
                default -> 0;
            };
            float legH = 12 - (low ? 9 : 0);
            add.accept(new float[] {side ? -4 : -5 + stride, 0, 4, legH, gi});
            add.accept(new float[] {side ? 1 : 2 - stride, 0, 4, legH, gi});
        }
        // body, belt, head
        float bw = side ? 8 : 10;
        add.accept(new float[] {-5 + lean, 12 - drop, bw, 12, gi});
        add.accept(new float[] {-5 + lean, 12 - drop, bw, 2, band});
        add.accept(new float[] {-2 + lean, 19 - drop, 4, 4, gi == 4 ? 4 : skin});
        add.accept(new float[] {-4 + lean, 24 - drop, 8, 8, skin});
        add.accept(new float[] {side ? -1 + lean : 1 + lean, 27 - drop, 2, 2, 0});
        if (side) add.accept(new float[] {2 + lean, 27 - drop, 2, 2, 0}); // (both eyes: turned towards you)
        switch (s.kind()) {
            case 0 -> {
                add.accept(new float[] {-4 + lean, 29 - drop, 8, 2, band});
                add.accept(new float[] {-9 + lean, 28 - drop + (ticks / 6) % 2, 5, 2, band});
                add.accept(new float[] {-4 + lean, 31 - drop, 8, 2, hair});
            }
            case 1 -> {
                add.accept(new float[] {-4 + lean, 32 - drop, 3, 3, hair});
                add.accept(new float[] {1 + lean, 32 - drop, 3, 3, hair});
                add.accept(new float[] {-10 + lean, 14 - drop + (ticks / 8) % 2, 6, 2, gi});
            }
            case 2 -> {
                add.accept(new float[] {-1 + lean, 26 - drop, 5, 2, band});
                add.accept(new float[] {-1 + lean, 32 - drop, 1, 4, hair});
                add.accept(new float[] {-2 + lean, 36 - drop, 3, 2, (ticks / 8) % 2 == 0 ? 6 : hair});
            }
            default -> {
                int flick = (ticks / 3) % 3;
                add.accept(new float[] {-5 + lean, 32 - drop, 9, 2, hair});
                add.accept(new float[] {-4 + lean, 34 - drop, 3, 2 + flick, 6});
                add.accept(new float[] {0 + lean, 34 - drop, 3, 3 - flick % 2, 8});
            }
        }
        // arms
        if ((pose == Pose.JAB || pose == Pose.CROSS || pose == Pose.LOWPUNCH) && out1) {
            float ay = pose == Pose.LOWPUNCH ? 15 : 21;
            float len = pose == Pose.CROSS ? 16 : 13;
            add.accept(new float[] {4, ay - drop, len, 3, gi});
            add.accept(new float[] {4 + len, ay - 1 - drop, 5, 5, skin});
            add.accept(new float[] {-4 + lean, 18 - drop, 4, 4, skin}); // (the other fist guarding)
        } else if (pose == Pose.UPPER) {
            boolean up1 = b.t >= b.move.startup() - 1;
            add.accept(new float[] {4, 18 - drop, 4, up1 ? 16 : 8, gi});
            add.accept(new float[] {4, (up1 ? 33 : 26) - drop, 5, 5, skin});
        } else if (pose == Pose.THROW || b.state == State.THROWN) {
            add.accept(new float[] {4, 20 - drop, 14, 3, gi});
            add.accept(new float[] {4, 16 - drop, 14, 3, gi});
            add.accept(new float[] {17, 15 - drop, 4, 8, skin});
        } else if (pose == Pose.TACKLE) {
            add.accept(new float[] {3, 18 - drop, 8, 6, gi});
        } else if (pose == Pose.RAGE) {
            add.accept(new float[] {4, 20 - drop, out1 ? 20 : 8, 4, gi});
            add.accept(new float[] {out1 ? 24 : 12, 18 - drop, 7, 7, out1 ? 6 : skin});
        } else if (b.state == State.BLOCK) {
            add.accept(new float[] {5, 14 - drop, 3, 14, gi});
            add.accept(new float[] {5, 26 - drop, 4, 4, skin});
        } else if (b.state == State.WIN) {
            add.accept(new float[] {3, 24 - drop, 3, 12, gi});
            add.accept(new float[] {3, 36 - drop, 4, 4, skin});
        } else { // (a guard up: fists by the face)
            add.accept(new float[] {4, 20 - drop, 6, 3, gi});
            add.accept(new float[] {9, 21 - drop, 4, 4, skin});
            add.accept(new float[] {-1 + lean, 23 - drop, 4, 4, skin});
        }
        return out;
    }

    @Override
    public void draw(GuiGraphics g, Font font, int x0, int y0, float pt) {
        if (picking) {
            drawPick(g, font, x0, y0);
            return;
        }
        int sx = shake > 0 ? (shake % 2 == 0 ? 2 : -2) : 0;
        g.pose().pushPose();
        g.pose().translate(sx, 0, 0);
        // the stage: a dusk sky, a city that slides by as the camera swings round the two, a dojo floor with its lines
        for (int y = 0; y < GROUND; y += 4) {
            float t = y / (float) GROUND;
            int c = 0xFF000000 | ((int) (36 + 100 * t) << 16) | ((int) (12 + 24 * t) << 8) | (int) (70 + 20 * (1 - t));
            box(g, x0, y0, -4, y, W + 8, 4, c);
        }
        float moonX = Math.floorMod((int) (150 - spin * 0.15F), W + 40) - 20;
        box(g, x0, y0, moonX, 30, 12, 12, 0xFFFFF0C8);
        box(g, x0, y0, moonX + 4, 30, 8, 8, 0xFFE8D8A8);
        int slide = Math.floorMod((int) (spin * 0.5F), 16 * 14);
        for (int i = -2; i < 15; i++) {
            int k = Math.floorMod(i + slide / 16, 14);
            float bx = i * 16 - (slide % 16);
            int h = 22 + (Math.abs(k * 37) % 34);
            box(g, x0, y0, bx, GROUND - h, 14, h, 0xFF1A1030);
            for (int wy = GROUND - h + 4; wy < GROUND - 4; wy += 6) {
                for (int wx = 3; wx < 12; wx += 4) {
                    if (((k * 7 + wy + wx) % 3) == 0) box(g, x0, y0, bx + wx, wy, 2, 2, 0xFFFFE74A);
                }
            }
        }
        box(g, x0, y0, -4, GROUND - 6, W + 8, H - GROUND + 6, 0xFF3A2A5A);
        box(g, x0, y0, -4, GROUND - 6, W + 8, 1, 0xFFFF7AD9);
        // the floor's lines, slanting as the camera turns
        float tilt = Mth.clamp((me.depth - foe.depth) * 0.04F, -1.2F, 1.2F);
        for (int i = -2; i < 16; i++) {
            float lx = i * 16 - Math.floorMod((int) spin, 16);
            for (int row = 0; row < 3; row++) {
                float ly = GROUND - 2 + row * 6;
                box(g, x0, y0, lx + tilt * row * 6, ly, 8, 1, 0xFF5A4A7A);
            }
        }
        // the walls at either end
        box(g, x0, y0, 0, GROUND - 40, WALL_L - 4, 40, 0xFF2A1A4A);
        box(g, x0, y0, WALL_R + 4, GROUND - 40, W - WALL_R - 4, 40, 0xFF2A1A4A);
        // shadows, then the further fighter first
        for (Body b : new Body[] {foe, me}) {
            box(g, x0, y0, b.x - 9, GROUND + b.depth * 0.45F, 18, 2, 0x50000000);
        }
        Body first = me.depth > foe.depth ? me : foe, second = first == me ? foe : me;
        for (Body b : new Body[] {first, second}) {
            List<float[]> parts = shape(b);
            if (b.rage) { // (rage: a red glow round it)
                int glow = (ticks / 3) % 2 == 0 ? 0x50FF3030 : 0x30FF3030;
                for (float[] r : parts) box(g, x0, y0, r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, glow);
            }
            for (float[] r : parts) box(g, x0, y0, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 0xFF0A0614);
            for (float[] r : parts) {
                box(g, x0, y0, r[0], r[1], r[2], r[3], rgb((int) r[4]));
                if (r[3] > 3) box(g, x0, y0, r[0], r[1], r[2], 1, 0x30FFFFFF);
            }
            if (b.state == State.SIDESTEP || b.state == State.SIDEWALK) { // (a trail as it steps)
                box(g, x0, y0, b.x - 8, GROUND + b.depth * 0.45F - 2, 16, 1, 0x603AE8FF);
            }
        }
        for (float[] s : sparks) {
            float k = s[2];
            box(g, x0, y0, s[0] - k, s[1] - 0.5F, k * 2, 1, 0xFFFFF0A0);
            box(g, x0, y0, s[0] - 0.5F, s[1] - k, 1, k * 2, 0xFFFFF0A0);
            box(g, x0, y0, s[0] - 2, s[1] - 2, 4, 4, 0xFFFFFFFF);
        }
        g.pose().popPose();
        // health, rage, clock, names, rounds won, combo, call-outs
        bar(g, x0, y0, 6, me.hp, false, me.rage);
        bar(g, x0, y0, W - 6 - 76, foe.hp, true, foe.rage);
        centred(g, font, Integer.toString(Math.max(0, clock / 20)), x0 + W / 2, y0 + 13, 0xFFFFE74A);
        g.drawString(font, me.style.name() + " " + "★".repeat(me.wins), x0 + 6, y0 + 22, 0xFFFFFFFF, false);
        String fn = "★".repeat(foe.wins) + " " + foe.style.name();
        g.drawString(font, fn, x0 + W - 6 - font.width(fn), y0 + 22, 0xFFFFFFFF, false);
        if (me.rage && !me.rageUsed) {
            g.drawString(font, "RAGE: L", x0 + 6, y0 + 32, (ticks / 4) % 2 == 0 ? 0xFFFF4040 : 0xFFFF9A2E, false);
        }
        if (me.combo >= 2 && (foe.state == State.HIT || foe.state == State.AIR || foe.state == State.SPLAT || foe.state == State.DOWN)) {
            g.drawString(font, me.combo + " HITS", x0 + 8, y0 + 44, 0xFFFF9A2E, false);
        }
        for (Object[] c : callouts) {
            String text = (String) c[0];
            float cx = (Float) c[1], cy = (Float) c[2] - (28 - (Integer) c[3]) * 0.4F;
            g.pose().pushPose();
            g.pose().translate(x0 + cx, y0 + cy, 0);
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.drawString(font, text, -font.width(text) / 2, 0, 0xFFFFE74A, true);
            g.pose().popPose();
        }
        if (!banner.isEmpty()) {
            centred(g, font, banner, x0 + W / 2, y0 + 58, (ticks / 4) % 2 == 0 ? 0xFFFFE74A : 0xFFFF9A2E);
        }
    }

    private void drawPick(GuiGraphics g, Font font, int x0, int y0) {
        box(g, x0, y0, 0, 0, W, H, 0xFF120A24);
        centred(g, font, "SHADERED SHOWDOWN", x0 + W / 2, y0 + 14, 0xFFFF7AD9);
        centred(g, font, "CHOOSE YOUR FIGHTER", x0 + W / 2, y0 + 28, 0xFFFFFFFF);
        for (int i = 0; i < FIGHTERS.length; i++) {
            int cx = 26 + i * 46;
            boolean chosen = i == pick;
            box(g, x0, y0, cx - 20, 44, 40, 62, chosen ? 0xFFFF7AD9 : 0xFF3A2A5A);
            box(g, x0, y0, cx - 19, 45, 38, 60, 0xFF1A1030);
            Body preview = new Body(FIGHTERS[i], cx);
            preview.state = chosen ? State.WIN : State.STAND;
            List<float[]> parts = shape(preview);
            for (float[] r : parts) r[1] += 96 - GROUND;
            for (float[] r : parts) box(g, x0, y0, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 0xFF0A0614);
            for (float[] r : parts) box(g, x0, y0, r[0], r[1], r[2], r[3], rgb((int) r[4]));
            centred(g, font, FIGHTERS[i].name(), x0 + cx, y0 + 108, chosen ? 0xFFFFE74A : 0xFFAAAAAA);
        }
        Style s = FIGHTERS[pick];
        String stats = "SPEED " + "■".repeat(Math.round(s.speed() * 4)) + "  POWER " + "■".repeat(Math.round(s.power() * 4));
        centred(g, font, stats, x0 + W / 2, y0 + 120, 0xFF9AD8FF);
        if ((ticks / 10) % 2 == 0) centred(g, font, "< >  U: FIGHT", x0 + W / 2, y0 + 132, 0xFFFFFFFF);
    }

    private void bar(GuiGraphics g, int x0, int y0, int x, int hp, boolean right, boolean rage) {
        box(g, x0, y0, x - 1, 12, 78, 7, rage && (ticks / 4) % 2 == 0 ? 0xFFFF3030 : 0xFF000000);
        box(g, x0, y0, x, 13, 76, 5, 0xFF5A1020);
        int w = Math.round(76 * hp / (float) MAX_HP);
        box(g, x0, y0, right ? x + 76 - w : x, 13, w, 5, hp > MAX_HP / 4 ? 0xFFFFE74A : 0xFFFF4D5E);
    }

    @Override
    public int sample(float x, float y) {
        if (picking) {
            int i = Mth.clamp((int) ((x - 6) / 46), 0, FIGHTERS.length - 1);
            if (y > 44 && y < 106) return i == pick ? 15 : FIGHTERS[i].gi();
            return 0;
        }
        for (Body b : new Body[] {me, foe}) {
            for (float[] r : shape(b)) {
                if (x >= r[0] - 1 && x < r[0] + r[2] + 1 && y >= r[1] - 1 && y < r[1] + r[3] + 1) return (int) r[4];
            }
        }
        if (y >= 12 && y < 18) {
            if (x >= 6 && x < 6 + 76 * me.hp / (float) MAX_HP) return 8;
            if (x >= W - 6 - 76 * foe.hp / (float) MAX_HP && x < W - 6) return 8;
            return 14;
        }
        return y >= GROUND - 6 ? 11 : 0;
    }
}
