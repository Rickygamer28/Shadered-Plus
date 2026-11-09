package com.example.skyblocks.client.arcade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Shadered Showdown: a fighting game against the cabinet. Pick a fighter (Riku, Neko, Robo or Blaze - each with its
 * own speed, strength and fireball), then beat the other three in turn, best of three rounds each on a 60 second
 * clock. Punch, kick, a low sweep that knocks down, an uppercut against jumpers, a flying kick, a fireball, and a
 * super once its meter's full; hold back to block. Combos chain punch into kick. Beat all three and they come back
 * harder.
 */
public class Showdown extends ArcadeGame {
    private static final int GROUND = 128, ROUND_TICKS = 60 * 20, SUPER_COST = 100;

    private enum Act { IDLE, WALK, JUMP, CROUCH, BLOCK, PUNCH, KICK, UPPER, SWEEP, AIRKICK, SPECIAL, SUPER, HIT, DOWN, RISE, KO, WIN }

    /** A fighter: its look (palette colours), speed, strength, how often (as the cabinet) it throws fireballs. */
    private record Style(String name, int gi, int skin, int band, int hair, int kind, float speed, float power, float fireballs) {}

    // kind: 0 a headband, 1 cat ears, 2 an antenna, 3 flame hair
    private static final Style[] FIGHTERS = {
            new Style("RIKU", 10, 7, 6, 13, 0, 1.6F, 1.0F, 0.12F),
            new Style("NEKO", 7, 8, 15, 4, 1, 2.0F, 0.85F, 0.08F),
            new Style("ROBO", 13, 5, 12, 12, 2, 1.2F, 1.35F, 0.10F),
            new Style("BLAZE", 6, 8, 8, 7, 3, 1.6F, 1.1F, 0.25F)};

    private static final class Body {
        final Style style;
        float x, y, vy, vx;
        int facing = 1, hp = 100, meter, actT, cooldown, wins, stun, combo, comboShow;
        Act act = Act.IDLE;
        boolean hitDone, holdBack, landedHit;

        Body(Style style, float x) {
            this.style = style;
            this.x = x;
        }

        boolean onGround() {
            return y <= 0.0F;
        }

        boolean attacking() {
            return act == Act.PUNCH || act == Act.KICK || act == Act.UPPER || act == Act.SWEEP || act == Act.AIRKICK
                    || act == Act.SPECIAL || act == Act.SUPER;
        }

        boolean busy() {
            return attacking() || act == Act.HIT || act == Act.DOWN || act == Act.RISE || act == Act.KO || act == Act.WIN;
        }
    }

    private record Shot(float[] pos, int dir, Body owner, int colour, boolean big) {}

    private Body me, foe;
    private final List<Shot> shots = new ArrayList<>();
    private final List<float[]> sparks = new ArrayList<>(); // x, y, ticks left
    private int pick, ladder, round, clock, roundOver, shake, thinkIn, lap;
    private boolean picking;
    private String banner = "";
    private boolean kLeft, kRight, kDown;

    @Override
    public String name() {
        return "SHADERED SHOWDOWN";
    }

    @Override
    public String[] help() {
        return new String[] {"WASD move  J punch  K kick  L fire", "I super  S+J uppercut  S+K sweep"};
    }

    @Override
    public void start() {
        score = 0;
        over = won = false;
        picking = true;
        ladder = 0;
        lap = 0;
        banner = "";
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
        me = new Body(FIGHTERS[pick], 50);
        foe = new Body(foeStyle(), W - 50);
        newRound();
    }

    private void newRound() {
        round++;
        me.x = 50;
        foe.x = W - 50;
        for (Body b : new Body[] {me, foe}) {
            b.y = 0;
            b.vy = b.vx = 0;
            b.hp = 100;
            b.act = Act.IDLE;
            b.stun = b.combo = 0;
        }
        shots.clear();
        clock = ROUND_TICKS;
        roundOver = -40;
        banner = round == 3 ? "FINAL ROUND" : "ROUND " + round;
    }

    private int difficulty() {
        return ladder + lap;
    }

    // ================= ticking =================

    @Override
    public void tick() {
        ticks++;
        if (picking) return;
        if (shake > 0) shake--;
        sparks.removeIf(s -> --s[2] <= 0);
        if (me.comboShow > 0) me.comboShow--;
        if (roundOver < 0) {
            if (++roundOver == 0) banner = "";
            if (roundOver == -12) banner = "FIGHT!";
            return;
        }
        if (roundOver > 0) {
            physics(me);
            physics(foe);
            if (--roundOver == 0) endRound();
            return;
        }
        if (--clock <= 0) {
            finishRound(me.hp >= foe.hp ? me : foe, "TIME");
            return;
        }
        me.facing = me.onGround() && !me.busy() ? (foe.x > me.x ? 1 : -1) : me.facing;
        me.holdBack = (kLeft && foe.x > me.x) || (kRight && foe.x < me.x);
        if (!me.busy() && me.stun == 0 && me.onGround()) {
            if (kDown) {
                me.act = Act.CROUCH;
            } else if (me.holdBack && danger(foe, me)) {
                me.act = Act.BLOCK;
            } else if (kLeft != kRight) {
                me.act = Act.WALK;
                me.x += (kRight ? 1 : -1) * me.style.speed() * (me.holdBack ? 0.7F : 1.0F);
            } else {
                me.act = Act.IDLE;
            }
        }
        cpu();
        for (Body b : new Body[] {me, foe}) {
            physics(b);
            strike(b, b == me ? foe : me);
        }
        float gap = foe.x - me.x;
        if (Math.abs(gap) < 14 && Math.abs(me.y - foe.y) < 24) {
            float push = (14 - Math.abs(gap)) / 2.0F * (gap >= 0 ? 1 : -1);
            me.x -= push;
            foe.x += push;
        }
        me.x = Mth.clamp(me.x, 8, W - 8);
        foe.x = Mth.clamp(foe.x, 8, W - 8);
        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot s = shots.get(i);
            s.pos()[0] += s.dir() * (s.big() ? 4.5F : 3.2F);
            Body target = s.owner() == me ? foe : me;
            boolean hits = Math.abs(s.pos()[0] - target.x) < (s.big() ? 12 : 9) && target.y < (s.big() ? 30 : 18)
                    && target.act != Act.KO && target.act != Act.DOWN && target.act != Act.RISE;
            if (hits) {
                damage(s.owner(), target, Math.round((s.big() ? 30 : 12) * s.owner().style.power()), s.big() ? 30 : 16, s.big(), s.pos()[0], s.pos()[1]);
                shots.remove(i);
            } else if (s.pos()[0] < -14 || s.pos()[0] > W + 14) {
                shots.remove(i);
            } else { // (two meeting cancel out)
                for (int j = shots.size() - 1; j >= 0; j--) {
                    Shot o = shots.get(j);
                    if (j != i && o.owner() != s.owner() && Math.abs(o.pos()[0] - s.pos()[0]) < 8) {
                        sparks.add(new float[] {s.pos()[0], s.pos()[1], 8});
                        shots.remove(Math.max(i, j));
                        shots.remove(Math.min(i, j));
                        i = Math.min(i, shots.size());
                        break;
                    }
                }
            }
        }
        if (me.hp <= 0) finishRound(foe, "K.O.");
        else if (foe.hp <= 0) finishRound(me, "K.O.");
    }

    /** Whether {@code from} is attacking {@code at} now (or a shot of its is on its way). */
    private boolean danger(Body from, Body at) {
        if (from.attacking() && Math.abs(from.x - at.x) < 40) return true;
        for (Shot s : shots) {
            if (s.owner() == from && Math.abs(s.pos()[0] - at.x) < 50) return true;
        }
        return false;
    }

    private void physics(Body b) {
        if (b.cooldown > 0) b.cooldown--;
        if (b.stun > 0 && --b.stun == 0) {
            if (b.act == Act.HIT) b.act = Act.IDLE;
            else if (b.act == Act.DOWN) {
                b.act = Act.RISE;
                b.stun = 12;
            } else if (b.act == Act.RISE) b.act = Act.IDLE;
        }
        if (!b.onGround() || b.vy > 0) {
            b.y += b.vy;
            b.x += b.vx;
            b.vy -= 0.35F;
            if (b.y <= 0) {
                b.y = 0;
                b.vy = 0;
                b.vx = 0;
                if (b.act == Act.JUMP || b.act == Act.AIRKICK) b.act = Act.IDLE;
            }
        }
        if (b.attacking()) {
            b.actT++;
            int total = switch (b.act) {
                case PUNCH -> 11;
                case KICK -> 17;
                case UPPER -> 20;
                case SWEEP -> 20;
                case AIRKICK -> 30;
                case SPECIAL -> 24;
                default -> 36; // super
            };
            if (b.actT >= total) {
                b.act = b.onGround() ? (b == me && kDown ? Act.CROUCH : Act.IDLE) : Act.JUMP;
            }
        }
    }

    private void act(Body b, Act a) {
        boolean cancel = b.act == Act.PUNCH && b.landedHit && b.actT >= 5 && (a == Act.KICK || a == Act.SPECIAL || a == Act.SUPER);
        if ((b.busy() && !cancel) || b.stun > 0) return;
        if (a == Act.SPECIAL && (b.cooldown > 0 || !b.onGround())) return;
        if (a == Act.SUPER && (b.meter < SUPER_COST || !b.onGround())) return;
        if (!b.onGround() && a != Act.AIRKICK) return;
        if (a == Act.SUPER) {
            b.meter = 0;
            banner = b.style.name() + " SUPER!";
            roundBannerClear = 30;
        }
        b.act = a;
        b.actT = 0;
        b.hitDone = false;
        b.landedHit = false;
    }

    private int roundBannerClear;

    private void jump(Body b, int dir) {
        if (b.onGround() && !b.busy() && b.stun == 0) {
            b.act = Act.JUMP;
            b.vy = 5.6F;
            b.y = 0.01F;
            b.vx = dir * b.style.speed() * 1.2F;
        }
    }

    private void strike(Body b, Body other) {
        if (b.hitDone) return;
        if (b.act == Act.SPECIAL && b.actT == 8) {
            shots.add(new Shot(new float[] {b.x + b.facing * 12, GROUND - 22}, b.facing, b, b.style.band(), false));
            b.cooldown = 70;
            b.hitDone = true;
            return;
        }
        if (b.act == Act.SUPER && b.actT == 14) {
            shots.add(new Shot(new float[] {b.x + b.facing * 14, GROUND - 22}, b.facing, b, b.style.hair(), true));
            shake = 10;
            b.hitDone = true;
            return;
        }
        int from, to, range, dmg, stun;
        float low = 0, high = 34;
        switch (b.act) {
            case PUNCH -> { from = 3; to = 5; range = 22; dmg = 6; stun = 11; }
            case KICK -> { from = 6; to = 9; range = 28; dmg = 9; stun = 15; }
            case UPPER -> { from = 4; to = 9; range = 20; dmg = 11; stun = 20; high = 60; }
            case SWEEP -> { from = 6; to = 10; range = 30; dmg = 8; stun = 40; high = 10; }
            case AIRKICK -> { from = 3; to = 20; range = 24; dmg = 10; stun = 16; }
            default -> { return; }
        }
        if (b.actT < from || b.actT > to) return;
        float reach = (other.x - b.x) * b.facing;
        float dy = other.y - b.y;
        if (reach > -2 && reach <= range && dy >= low - 24 && dy <= high && other.act != Act.KO && other.act != Act.DOWN && other.act != Act.RISE) {
            b.hitDone = true;
            b.landedHit = true;
            if (b.act == Act.SWEEP && !(other.act == Act.BLOCK && other.holdBack && kDownFor(other))) {
                damage(b, other, Math.round(dmg * b.style.power()), stun, true, other.x, GROUND - 4);
            } else {
                damage(b, other, Math.round(dmg * b.style.power()), stun, b.act == Act.UPPER && !other.onGround(),
                        b.x + b.facing * range * 0.8F, GROUND - b.y - (b.act == Act.UPPER ? 30 : 20));
            }
        }
    }

    private void damage(Body from, Body to, int dmg, int stun, boolean knockDown, float sx, float sy) {
        boolean blocked = (to.act == Act.BLOCK || (to.holdBack && to.onGround() && !to.busy())) && to.onGround();
        if (blocked) {
            to.hp -= Math.max(1, dmg / 5);
            to.x += -to.facing * 4;
            to.meter = Math.min(SUPER_COST, to.meter + 4);
        } else {
            to.hp -= dmg;
            to.x += -to.facing * 7;
            to.meter = Math.min(SUPER_COST, to.meter + 8);
            from.meter = Math.min(SUPER_COST, from.meter + 12);
            if (knockDown) {
                to.act = Act.DOWN;
                to.stun = 30;
                to.y = 0;
                to.vy = 0;
                shake = 6;
            } else {
                to.act = Act.HIT;
                to.stun = stun;
                if (!to.onGround()) to.vy = Math.max(to.vy, 2.5F);
            }
            from.combo = to.act == Act.HIT || to.act == Act.DOWN ? from.combo + 1 : 1;
            if (from == me) {
                me.comboShow = from.combo >= 2 ? 40 : 0;
                score += dmg * 5 * Math.max(1, from.combo);
            }
            sparks.add(new float[] {sx, sy, 8});
        }
        to.hp = Math.max(0, to.hp);
        if (to.act != Act.HIT && to.act != Act.DOWN) to.combo = 0;
    }

    private void finishRound(Body winner, String how) {
        roundOver = 80;
        winner.wins++;
        Body loser = winner == me ? foe : me;
        if (loser.hp <= 0) {
            loser.act = Act.KO;
            shake = 12;
        }
        winner.act = Act.WIN;
        boolean perfect = winner.hp == 100;
        banner = how + "  " + (winner == me ? "YOU WIN" : foe.style.name() + " WINS") + (perfect ? " - PERFECT" : "");
        if (winner == me) score += 300 + me.hp * 5 + (perfect ? 2000 : 0) + clock / 20 * 10;
    }

    private void endRound() {
        if (me.wins >= 2) {
            ladder++;
            score += 1500 * ladder;
            if (ladder % (FIGHTERS.length - 1) == 0) { // (all three beaten: round again, harder)
                lap++;
                score += 5000;
            }
            newMatch();
            banner = (ladder % (FIGHTERS.length - 1) == 0 ? "CHAMPION! NEXT: " : "NEXT: ") + foe.style.name();
            roundOver = -70;
        } else if (foe.wins >= 2) {
            over = true;
        } else {
            newRound();
        }
    }

    // ================= the cabinet's fighter =================

    private void cpu() {
        if (foe.onGround() && !foe.busy()) foe.facing = me.x > foe.x ? 1 : -1;
        foe.holdBack = false;
        if (foe.busy() || foe.stun > 0) return;
        int d = difficulty();
        float dist = Math.abs(me.x - foe.x);
        // an uppercut for one jumping in
        if (!me.onGround() && dist < 34 && me.vy < 0 && random.nextFloat() < 0.15F + 0.12F * d) {
            act(foe, Act.UPPER);
            return;
        }
        if (danger(me, foe) && foe.onGround() && random.nextFloat() < 0.3F + 0.12F * d) {
            boolean shotComing = shots.stream().anyMatch(s -> s.owner() == me);
            if (shotComing && random.nextFloat() < 0.3F) {
                jump(foe, foe.facing);
            } else {
                foe.act = Act.BLOCK;
                foe.holdBack = true;
            }
            return;
        }
        if (foe.act == Act.BLOCK) foe.act = Act.IDLE;
        if (--thinkIn > 0) {
            if (foe.act == Act.WALK) foe.x += foe.facing * foe.style.speed();
            return;
        }
        thinkIn = Math.max(3, 11 - d * 2);
        float r = random.nextFloat();
        if (foe.meter >= SUPER_COST && dist < 120 && r < 0.3F) {
            act(foe, Act.SUPER);
        } else if (dist > 60) {
            if (r < foe.style.fireballs() + 0.04F * d && foe.cooldown == 0) act(foe, Act.SPECIAL);
            else if (r < 0.8F) foe.act = Act.WALK;
            else if (r < 0.88F) jump(foe, foe.facing);
            else foe.act = Act.IDLE;
        } else if (dist > 26) {
            if (r < 0.2F) act(foe, Act.KICK);
            else if (r < 0.3F) act(foe, Act.SWEEP);
            else if (r < 0.38F) jump(foe, foe.facing);
            else if (r < 0.85F) foe.act = Act.WALK;
            else foe.act = Act.IDLE;
        } else {
            if (r < 0.35F) act(foe, Act.PUNCH);
            else if (r < 0.55F) act(foe, Act.KICK);
            else if (r < 0.68F) act(foe, Act.SWEEP);
            else if (r < 0.75F) act(foe, Act.UPPER);
            else foe.x -= foe.facing * 8; // (a step back)
        }
    }

    // ================= keys =================

    @Override
    public void keyPressed(int key) {
        if (picking) {
            if (left(key)) pick = (pick + FIGHTERS.length - 1) % FIGHTERS.length;
            if (right(key)) pick = (pick + 1) % FIGHTERS.length;
            if (key == GLFW.GLFW_KEY_J || key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
                picking = false;
                newMatch();
            }
            return;
        }
        if (left(key)) kLeft = true;
        if (right(key)) kRight = true;
        if (down(key)) kDown = true;
        if (roundOver != 0 || me == null) return;
        if (up(key)) jump(me, kLeft ? -1 : kRight ? 1 : 0);
        boolean punch = key == GLFW.GLFW_KEY_J || key == GLFW.GLFW_KEY_Z, kick = key == GLFW.GLFW_KEY_K || key == GLFW.GLFW_KEY_X;
        if (punch) act(me, kDown ? Act.UPPER : Act.PUNCH);
        if (kick) act(me, !me.onGround() ? Act.AIRKICK : kDown ? Act.SWEEP : Act.KICK);
        if (key == GLFW.GLFW_KEY_L || key == GLFW.GLFW_KEY_C) act(me, Act.SPECIAL);
        if (key == GLFW.GLFW_KEY_I || key == GLFW.GLFW_KEY_V) act(me, Act.SUPER);
    }

    @Override
    public void keyReleased(int key) {
        if (left(key)) kLeft = false;
        if (right(key)) kRight = false;
        if (down(key)) kDown = false;
    }

    private boolean kDownFor(Body b) {
        return b == me ? kDown : b.act == Act.CROUCH;
    }

    // ================= how they look =================

    /** The fighter's shape now: boxes x y w h colour (palette; -1 its dark outline), in field coordinates. */
    private List<float[]> shape(Body b) {
        List<float[]> out = new ArrayList<>();
        float x = b.x, base = GROUND - b.y;
        int f = b.facing;
        Style s = b.style;
        int gi = s.gi(), skin = s.skin(), band = s.band(), hair = s.hair();
        if (b.act == Act.HIT && (ticks / 2) % 2 == 0) {
            gi = 4;
        }
        java.util.function.Consumer<float[]> add = r -> {
            float rx = f > 0 ? x + r[0] : x - r[0] - r[2];
            out.add(new float[] {rx, base - r[1] - r[3], r[2], r[3], r[4]});
        };
        if (b.act == Act.KO || b.act == Act.DOWN) { // (lying flat)
            add.accept(new float[] {-18, 0, 28, 6, gi});
            add.accept(new float[] {-18, 0, 28, 2, band});
            add.accept(new float[] {-26, 0, 8, 7, skin});
            return out;
        }
        float bob = (b.act == Act.IDLE && (ticks / 10) % 2 == 0) ? 1 : 0;
        boolean low = b.act == Act.CROUCH || b.act == Act.SWEEP || b.act == Act.RISE || (b.act == Act.BLOCK && kDownFor(b));
        float drop = (low ? 9 : 0) + bob;
        float lean = b.act == Act.HIT ? -3 : b.attacking() ? 1 : 0;
        // legs
        switch (b.act) {
            case JUMP -> {
                add.accept(new float[] {-5, 4, 4, 8, gi});
                add.accept(new float[] {1, 6, 4, 7, gi});
            }
            case AIRKICK -> {
                add.accept(new float[] {-6, 6, 4, 8, gi});
                add.accept(new float[] {1, 4, 22, 4, gi});
                add.accept(new float[] {22, 3, 5, 5, skin});
            }
            case KICK -> {
                boolean out1 = b.actT >= 5 && b.actT <= 11;
                add.accept(new float[] {-5, 0, 4, 12, gi});
                if (out1) {
                    add.accept(new float[] {2, 12, 22, 4, gi});
                    add.accept(new float[] {24, 12, 4, 4, skin});
                } else {
                    add.accept(new float[] {2, 6, 4, 8, gi});
                }
            }
            case SWEEP -> {
                add.accept(new float[] {-6, 0, 5, 4, gi});
                add.accept(new float[] {0, 0, 26, 3, gi});
                add.accept(new float[] {26, 0, 4, 3, skin});
            }
            default -> {
                float step = b.act == Act.WALK ? Mth.sin(ticks * 0.5F) * 2 : 0;
                add.accept(new float[] {-5 + step, 0, 4, 12 - (low ? 9 : 0), gi});
                add.accept(new float[] {2 - step, 0, 4, 12 - (low ? 9 : 0), gi});
            }
        }
        // body, belt
        add.accept(new float[] {-5 + lean, 12 - drop, 10, 12, gi});
        add.accept(new float[] {-5 + lean, 12 - drop, 10, 2, band});
        add.accept(new float[] {-2 + lean, 19 - drop, 4, 4, gi == 4 ? 4 : skin}); // (the open collar)
        // head, eye, and its look
        add.accept(new float[] {-4 + lean, 24 - drop, 8, 8, skin});
        add.accept(new float[] {1 + lean, 27 - drop, 2, 2, 0});
        switch (s.kind()) {
            case 0 -> { // a headband with its tails flying behind
                add.accept(new float[] {-4 + lean, 29 - drop, 8, 2, band});
                add.accept(new float[] {-9 + lean, 28 - drop + (ticks / 6) % 2, 5, 2, band});
                add.accept(new float[] {-4 + lean, 31 - drop, 8, 2, hair});
            }
            case 1 -> { // cat ears and a tail
                add.accept(new float[] {-4 + lean, 32 - drop, 3, 3, hair});
                add.accept(new float[] {1 + lean, 32 - drop, 3, 3, hair});
                add.accept(new float[] {-10 + lean, 14 - drop + (ticks / 8) % 2, 6, 2, gi});
            }
            case 2 -> { // a visor and an antenna
                add.accept(new float[] {-1 + lean, 26 - drop, 5, 2, band});
                add.accept(new float[] {-1 + lean, 32 - drop, 1, 4, hair});
                add.accept(new float[] {-2 + lean, 36 - drop, 3, 2, (ticks / 8) % 2 == 0 ? 6 : hair});
            }
            default -> { // flame hair
                int flick = (ticks / 3) % 3;
                add.accept(new float[] {-5 + lean, 32 - drop, 9, 2, hair});
                add.accept(new float[] {-4 + lean, 34 - drop, 3, 2 + flick, 6});
                add.accept(new float[] {0 + lean, 34 - drop, 3, 3 - flick % 2, 8});
            }
        }
        // arms
        switch (b.act) {
            case PUNCH -> {
                boolean out1 = b.actT >= 2 && b.actT <= 7;
                add.accept(new float[] {4, 19 - drop, out1 ? 14 : 8, 3, gi});
                add.accept(new float[] {out1 ? 18 : 11, 18 - drop, 5, 5, skin});
            }
            case UPPER -> {
                boolean up1 = b.actT >= 3;
                add.accept(new float[] {4, 18 - drop, 4, up1 ? 16 : 8, gi});
                add.accept(new float[] {4, (up1 ? 33 : 26) - drop, 5, 5, skin});
            }
            case SPECIAL, SUPER -> {
                add.accept(new float[] {4, 18 - drop, 10, 3, gi});
                add.accept(new float[] {4, 15 - drop, 10, 3, gi});
                add.accept(new float[] {14, 15 - drop, 4, 7, skin});
            }
            case BLOCK -> {
                add.accept(new float[] {5, 14 - drop, 3, 14, gi});
                add.accept(new float[] {5, 26 - drop, 4, 4, skin});
            }
            case WIN -> {
                add.accept(new float[] {3, 24 - drop, 3, 12, gi});
                add.accept(new float[] {3, 36 - drop, 4, 4, skin});
            }
            default -> {
                add.accept(new float[] {4, 18 - drop, 7, 3, gi});
                add.accept(new float[] {10, 19 - drop, 4, 4, skin});
                add.accept(new float[] {-6 + lean, 17 - drop, 3, 6, gi}); // (the back arm)
            }
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
        // the stage: a dusk sky with a moon, a city behind, neon signs, the floor
        for (int y = 0; y < GROUND; y += 4) {
            float t = y / (float) GROUND;
            int c = 0xFF000000 | ((int) (36 + 100 * t) << 16) | ((int) (12 + 24 * t) << 8) | (int) (70 + 20 * (1 - t));
            box(g, x0, y0, -4, y, W + 8, 4, c);
        }
        box(g, x0, y0, 150, 30, 12, 12, 0xFFFFF0C8);
        box(g, x0, y0, 154, 30, 8, 8, 0xFFE8D8A8);
        for (int i = -1; i < 13; i++) {
            int h = 22 + (Math.abs(i * 37) % 34);
            box(g, x0, y0, i * 16, GROUND - h, 14, h, 0xFF1A1030);
            for (int wy = GROUND - h + 4; wy < GROUND - 4; wy += 6) {
                for (int wx = 3; wx < 12; wx += 4) {
                    if (((i * 7 + wy + wx) % 3) == 0) box(g, x0, y0, i * 16 + wx, wy, 2, 2, 0xFFFFE74A);
                }
            }
        }
        box(g, x0, y0, 30, GROUND - 46, 20, 6, (ticks / 20) % 2 == 0 ? 0xFFFF7AD9 : 0xFF8A3A70);
        box(g, x0, y0, 130, GROUND - 40, 24, 5, 0xFF3AE8FF);
        box(g, x0, y0, -4, GROUND, W + 8, H - GROUND, 0xFF3A2A5A);
        box(g, x0, y0, -4, GROUND, W + 8, 1, 0xFFFF7AD9);
        for (int x = 0; x < W; x += 16) box(g, x0, y0, x, GROUND + 6, 8, 1, 0xFF5A4A7A);
        // shadows, fighters (an outline under each part), shots, sparks
        for (Body b : new Body[] {foe, me}) {
            box(g, x0, y0, b.x - 9, GROUND, 18, 2, 0x50000000);
        }
        for (Body b : new Body[] {foe, me}) {
            List<float[]> parts = shape(b);
            for (float[] r : parts) box(g, x0, y0, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 0xFF0A0614);
            for (float[] r : parts) {
                box(g, x0, y0, r[0], r[1], r[2], r[3], rgb((int) r[4]));
                if (r[3] > 3) box(g, x0, y0, r[0], r[1], r[2], 1, 0x30FFFFFF); // (a little light on top)
            }
        }
        for (Shot s : shots) {
            int c = rgb(s.colour());
            float r = s.big() ? 8 : 4;
            box(g, x0, y0, s.pos()[0] - r - s.dir() * r, s.pos()[1] - r / 2, r * 2, r, (c & 0x00FFFFFF) | 0x60000000);
            box(g, x0, y0, s.pos()[0] - r, s.pos()[1] - r, r * 2, r * 2, c);
            box(g, x0, y0, s.pos()[0] - r / 2, s.pos()[1] - r / 2, r, r, 0xFFFFFFFF);
        }
        for (float[] s : sparks) {
            float k = s[2];
            box(g, x0, y0, s[0] - k, s[1] - 0.5F, k * 2, 1, 0xFFFFF0A0);
            box(g, x0, y0, s[0] - 0.5F, s[1] - k, 1, k * 2, 0xFFFFF0A0);
            box(g, x0, y0, s[0] - 2, s[1] - 2, 4, 4, 0xFFFFFFFF);
        }
        g.pose().popPose();
        // health, super meters, clock, names, rounds won
        bar(g, x0, y0, 6, me.hp, false, 0xFFFFE74A);
        bar(g, x0, y0, W - 6 - 76, foe.hp, true, 0xFFFFE74A);
        meter(g, x0, y0, 6, me.meter, false);
        meter(g, x0, y0, W - 6 - 50, foe.meter, true);
        centred(g, font, Integer.toString(Math.max(0, clock / 20)), x0 + W / 2, y0 + 13, 0xFFFFE74A);
        g.drawString(font, me.style.name() + " " + "★".repeat(me.wins), x0 + 6, y0 + 26, 0xFFFFFFFF, false);
        String fn = "★".repeat(foe.wins) + " " + foe.style.name();
        g.drawString(font, fn, x0 + W - 6 - font.width(fn), y0 + 26, 0xFFFFFFFF, false);
        if (me.comboShow > 0 && me.combo >= 2) {
            g.drawString(font, me.combo + " HIT COMBO!", x0 + 8, y0 + 40, 0xFFFF9A2E, false);
        }
        if (roundBannerClear > 0 && --roundBannerClear == 0 && roundOver == 0) banner = "";
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
            preview.act = chosen ? Act.WIN : Act.IDLE;
            for (float[] r : shapeAt(preview, 96)) {
                box(g, x0, y0, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 0xFF0A0614);
            }
            for (float[] r : shapeAt(preview, 96)) {
                box(g, x0, y0, r[0], r[1], r[2], r[3], rgb((int) r[4]));
            }
            centred(g, font, FIGHTERS[i].name(), x0 + cx, y0 + 108, chosen ? 0xFFFFE74A : 0xFFAAAAAA);
        }
        Style s = FIGHTERS[pick];
        String stats = "SPEED " + "■".repeat(Math.round(s.speed() * 2)) + "  POWER " + "■".repeat(Math.round(s.power() * 3));
        centred(g, font, stats, x0 + W / 2, y0 + 120, 0xFF9AD8FF);
        if ((ticks / 10) % 2 == 0) centred(g, font, "< >  J: FIGHT", x0 + W / 2, y0 + 132, 0xFFFFFFFF);
    }

    /** (a shape drawn standing on another floor - the picking screen's) */
    private List<float[]> shapeAt(Body b, float floor) {
        List<float[]> parts = shape(b);
        for (float[] r : parts) r[1] += floor - GROUND;
        return parts;
    }

    private static void bar(GuiGraphics g, int x0, int y0, int x, int hp, boolean right, int colour) {
        box(g, x0, y0, x - 1, 12, 78, 7, 0xFF000000);
        box(g, x0, y0, x, 13, 76, 5, 0xFF5A1020);
        int w = Math.round(76 * hp / 100.0F);
        box(g, x0, y0, right ? x + 76 - w : x, 13, w, 5, hp > 30 ? colour : 0xFFFF4D5E);
    }

    private void meter(GuiGraphics g, int x0, int y0, int x, int value, boolean right) {
        box(g, x0, y0, x - 1, 20, 52, 4, 0xFF000000);
        int w = Math.round(50 * value / (float) SUPER_COST);
        int c = value >= SUPER_COST ? ((ticks / 4) % 2 == 0 ? 0xFF3AE8FF : 0xFFFFFFFF) : 0xFF2A7AFF;
        box(g, x0, y0, right ? x + 50 - w : x, 21, w, 2, c);
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
        for (Shot s : shots) {
            if (Math.abs(x - s.pos()[0]) < (s.big() ? 10 : 5) && Math.abs(y - s.pos()[1]) < (s.big() ? 10 : 5)) return s.colour();
        }
        if (y >= 12 && y < 18) {
            if (x >= 6 && x < 6 + 76 * me.hp / 100.0F) return 8;
            if (x >= W - 6 - 76 * foe.hp / 100.0F && x < W - 6) return 8;
            return 14;
        }
        return y >= GROUND ? 11 : 0;
    }
}
