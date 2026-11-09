package com.example.skyblocks.client.arcade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * A fighting game against the cabinet: your fighter against three in turn (Neko, Robo, Blaze), best of three rounds
 * each, a 60 second clock. Punch, kick, a fireball; hold back to block, down to crouch. Each fighter you beat is
 * followed by a harder one; lose a match and it's over.
 */
public class Fighter extends ArcadeGame {
    private static final int GROUND = 128, ROUND_TICKS = 60 * 20;

    private enum Act { IDLE, WALK, JUMP, CROUCH, BLOCK, PUNCH, KICK, SPECIAL, HIT, KO, WIN }

    /** A fighter's look and strength. */
    private record Style(String name, int body, int skin, int band, float speed, float power, float fireballs) {}

    private static final Style YOU = new Style("YOU", 10, 7, 6, 1.6F, 1.0F, 0.0F);
    private static final Style[] FOES = {
            new Style("NEKO", 7, 8, 4, 1.9F, 0.85F, 0.05F),
            new Style("ROBO", 13, 5, 6, 1.2F, 1.35F, 0.08F),
            new Style("BLAZE", 6, 8, 8, 1.6F, 1.1F, 0.22F)};

    private static final class Body {
        final Style style;
        float x, y, vy, vx;
        int facing = 1, hp = 100, actT, cooldown, wins, stun;
        Act act = Act.IDLE;
        boolean hitDone, holdBack;

        Body(Style style, float x) {
            this.style = style;
            this.x = x;
        }

        boolean onGround() {
            return y <= 0.0F;
        }

        boolean busy() {
            return act == Act.PUNCH || act == Act.KICK || act == Act.SPECIAL || act == Act.HIT || act == Act.KO || act == Act.WIN;
        }
    }

    private record Fireball(float[] pos, int dir, Body owner, int colour) {}

    private Body me, foe;
    private final List<Fireball> fireballs = new ArrayList<>();
    private int foeIndex, round, clock, roundOver, streak;
    private String banner = "";
    private boolean kLeft, kRight, kDown;

    @Override
    public String name() {
        return "FIGHTER";
    }

    @Override
    public String[] help() {
        return new String[] {"A D move  W jump  S crouch  hold back: block", "J/Z punch  K/X kick  L/C fireball"};
    }

    @Override
    public void start() {
        score = 0;
        over = won = false;
        foeIndex = 0;
        streak = 0;
        newMatch();
    }

    private void newMatch() {
        round = 0;
        me = new Body(YOU, 50);
        foe = new Body(FOES[foeIndex % FOES.length], W - 50);
        newRound();
    }

    private void newRound() {
        round++;
        me.x = 50;
        foe.x = W - 50;
        me.y = foe.y = 0;
        me.hp = foe.hp = 100;
        me.act = foe.act = Act.IDLE;
        me.stun = foe.stun = 0;
        fireballs.clear();
        clock = ROUND_TICKS;
        roundOver = -40; // (a moment of "ROUND n - FIGHT!")
        banner = "ROUND " + round;
    }

    private int difficulty() {
        return foeIndex;
    }

    // ================= ticking =================

    @Override
    public void tick() {
        ticks++;
        if (roundOver < 0) { // (the call before the round)
            if (++roundOver == 0) banner = "";
            if (roundOver == -10) banner = "FIGHT!";
            return;
        }
        if (roundOver > 0) { // (the round's end: a pause, then the next or the match's end)
            physics(me);
            physics(foe);
            if (--roundOver == 0) endRound();
            return;
        }
        if (--clock <= 0) {
            finishRound(me.hp >= foe.hp ? me : foe, "TIME");
            return;
        }
        // you
        me.facing = foe.x > me.x ? 1 : -1;
        me.holdBack = (kLeft && me.facing == 1) || (kRight && me.facing == -1);
        if (!me.busy() && me.stun == 0) {
            if (me.onGround()) {
                if (kDown) {
                    me.act = me.holdBack ? Act.BLOCK : Act.CROUCH;
                } else if (me.holdBack && near(foeAttacking())) {
                    me.act = Act.BLOCK;
                } else if (kLeft != kRight) {
                    me.act = Act.WALK;
                    me.x += (kRight ? 1 : -1) * me.style.speed() * (me.holdBack ? 0.7F : 1.0F);
                } else {
                    me.act = Act.IDLE;
                }
            }
        }
        cpu();
        for (Body b : new Body[] {me, foe}) {
            physics(b);
            attack(b, b == me ? foe : me);
        }
        // apart, and in the ring
        float gap = foe.x - me.x;
        if (Math.abs(gap) < 14 && me.y < 20 && foe.y < 20) {
            float push = (14 - Math.abs(gap)) / 2.0F * Math.signum(gap == 0 ? 1 : gap);
            me.x -= push;
            foe.x += push;
        }
        me.x = Mth.clamp(me.x, 8, W - 8);
        foe.x = Mth.clamp(foe.x, 8, W - 8);
        // fireballs
        for (int i = fireballs.size() - 1; i >= 0; i--) {
            Fireball f = fireballs.get(i);
            f.pos()[0] += f.dir() * 3.2F;
            Body target = f.owner() == me ? foe : me;
            if (Math.abs(f.pos()[0] - target.x) < 9 && target.y < 18 && target.act != Act.KO) {
                damage(f.owner(), target, Math.round(12 * f.owner().style.power()), 16, true);
                fireballs.remove(i);
            } else if (f.pos()[0] < -10 || f.pos()[0] > W + 10) {
                fireballs.remove(i);
            }
        }
        if (me.hp <= 0) finishRound(foe, "K.O.");
        else if (foe.hp <= 0) finishRound(me, "K.O.");
    }

    private Act foeAttacking() {
        return foe.act;
    }

    private boolean near(Act a) {
        return a == Act.PUNCH || a == Act.KICK || a == Act.SPECIAL || !fireballs.isEmpty();
    }

    private void physics(Body b) {
        if (b.cooldown > 0) b.cooldown--;
        if (b.stun > 0 && --b.stun == 0 && b.act == Act.HIT) b.act = Act.IDLE;
        if (!b.onGround() || b.vy > 0) {
            b.y += b.vy;
            b.x += b.vx;
            b.vy -= 0.35F;
            if (b.y <= 0) {
                b.y = 0;
                b.vy = 0;
                b.vx = 0;
                if (b.act == Act.JUMP) b.act = Act.IDLE;
            }
        }
        if (b.act == Act.PUNCH || b.act == Act.KICK || b.act == Act.SPECIAL) {
            b.actT++;
            int total = b.act == Act.PUNCH ? 12 : b.act == Act.KICK ? 18 : 24;
            if (b.actT >= total) {
                b.act = b.onGround() ? Act.IDLE : Act.JUMP;
            }
        }
    }

    private void doAttack(Body b, Act a) {
        if (b.busy() || b.stun > 0) return;
        if (a == Act.SPECIAL && (b.cooldown > 0 || !b.onGround())) return;
        b.act = a;
        b.actT = 0;
        b.hitDone = false;
    }

    private void jump(Body b) {
        if (b.onGround() && !b.busy() && b.stun == 0) {
            b.act = Act.JUMP;
            b.vy = 5.6F;
            b.y = 0.01F;
            b.vx = (b == me ? (kLeft ? -1 : kRight ? 1 : 0) : Math.signum(me.x - foe.x)) * b.style.speed() * 1.2F;
        }
    }

    private void attack(Body b, Body other) {
        if (b.hitDone) return;
        if (b.act == Act.SPECIAL && b.actT == 8) {
            fireballs.add(new Fireball(new float[] {b.x + b.facing * 12, GROUND - 22}, b.facing, b, b.style.band()));
            b.cooldown = 70;
            b.hitDone = true;
            return;
        }
        int from, to, range, dmg, stun;
        if (b.act == Act.PUNCH) {
            from = 4; to = 6; range = 22; dmg = 6; stun = 10;
        } else if (b.act == Act.KICK) {
            from = 6; to = 9; range = 28; dmg = 9; stun = 14;
        } else {
            return;
        }
        if (b.actT < from || b.actT > to) return;
        float reach = (other.x - b.x) * b.facing;
        if (reach > 0 && reach <= range && Math.abs(other.y - b.y) < 22 && other.act != Act.KO) {
            b.hitDone = true;
            damage(b, other, Math.round(dmg * b.style.power()), stun, false);
        }
    }

    private void damage(Body from, Body to, int dmg, int stun, boolean fireball) {
        boolean blocked = to.act == Act.BLOCK || (to.holdBack && to.onGround() && !to.busy());
        if (blocked) {
            to.hp -= Math.max(1, dmg / 5);
            to.x += -to.facing * 3;
        } else {
            to.hp -= dmg;
            to.act = Act.HIT;
            to.stun = stun;
            to.x += -to.facing * 6;
            if (!to.onGround()) to.vy = Math.max(to.vy, 2.0F);
        }
        if (from == me) score += (blocked ? 1 : dmg) * 5;
        to.hp = Math.max(0, to.hp);
    }

    private void finishRound(Body winner, String how) {
        roundOver = 70;
        winner.wins++;
        Body loser = winner == me ? foe : me;
        if (loser.hp <= 0) loser.act = Act.KO;
        winner.act = Act.WIN;
        banner = how + "  " + (winner == me ? "YOU WIN" : foe.style.name() + " WINS");
        if (winner == me) score += 300 + me.hp * 5;
    }

    private void endRound() {
        if (me.wins >= 2) { // (the match: on to the next, harder)
            streak++;
            score += 1000 * streak;
            foeIndex++;
            newMatch();
            banner = "NEXT: " + foe.style.name();
            roundOver = -60;
        } else if (foe.wins >= 2) {
            over = true;
        } else {
            newRound();
        }
    }

    // ================= the cabinet's fighter =================

    private int thinkIn;

    private void cpu() {
        foe.facing = me.x > foe.x ? 1 : -1;
        foe.holdBack = false;
        if (foe.busy() || foe.stun > 0) return;
        int d = difficulty();
        float dist = Math.abs(me.x - foe.x);
        // blocking what's coming
        boolean danger = (me.act == Act.PUNCH || me.act == Act.KICK) && dist < 34
                || fireballs.stream().anyMatch(f -> f.owner() == me && Math.abs(f.pos()[0] - foe.x) < 40);
        if (danger && random.nextFloat() < 0.25F + 0.1F * d) {
            if (!fireballs.isEmpty() && random.nextFloat() < 0.3F) {
                jump(foe);
            } else {
                foe.act = Act.BLOCK;
                foe.holdBack = true;
            }
            return;
        }
        if (foe.act == Act.BLOCK && !danger) foe.act = Act.IDLE;
        if (--thinkIn > 0) {
            if (foe.act == Act.WALK) foe.x += foe.facing * foe.style.speed();
            return;
        }
        thinkIn = Math.max(3, 10 - d * 2);
        float r = random.nextFloat();
        if (dist > 60) {
            if (r < foe.style.fireballs() + 0.04F * d && foe.cooldown == 0) doAttack(foe, Act.SPECIAL);
            else if (r < 0.8F) foe.act = Act.WALK;
            else if (r < 0.85F) jump(foe);
            else foe.act = Act.IDLE;
        } else if (dist > 26) {
            if (r < 0.25F) doAttack(foe, Act.KICK);
            else if (r < 0.8F) foe.act = Act.WALK;
            else foe.act = Act.IDLE;
        } else {
            if (r < 0.4F) doAttack(foe, Act.PUNCH);
            else if (r < 0.65F) doAttack(foe, Act.KICK);
            else if (r < 0.75F) jump(foe);
            else {
                foe.act = Act.WALK;
                foe.facing = -foe.facing; // (a step back)
                foe.x += foe.facing * 6;
                foe.facing = -foe.facing;
            }
        }
    }

    // ================= keys =================

    @Override
    public void keyPressed(int key) {
        if (left(key)) kLeft = true;
        if (right(key)) kRight = true;
        if (down(key)) kDown = true;
        if (roundOver != 0) return;
        if (up(key)) jump(me);
        if (key == GLFW.GLFW_KEY_J || key == GLFW.GLFW_KEY_Z) doAttack(me, Act.PUNCH);
        if (key == GLFW.GLFW_KEY_K || key == GLFW.GLFW_KEY_X) doAttack(me, Act.KICK);
        if (key == GLFW.GLFW_KEY_L || key == GLFW.GLFW_KEY_C) doAttack(me, Act.SPECIAL);
    }

    @Override
    public void keyReleased(int key) {
        if (left(key)) kLeft = false;
        if (right(key)) kRight = false;
        if (down(key)) kDown = false;
    }

    // ================= drawing =================

    /** The fighter's shape now: boxes x y w h colour (the palette's), in field coordinates. */
    private List<float[]> shape(Body b) {
        List<float[]> out = new ArrayList<>();
        float x = b.x, base = GROUND - b.y;
        int f = b.facing;
        int body = b.style.body(), skin = b.style.skin(), band = b.style.band();
        if (b.act == Act.HIT && (ticks / 2) % 2 == 0) {
            body = 4;
        }
        java.util.function.Consumer<float[]> add = r -> {
            // (r: x from the middle (towards its front), y up from its feet, w, h, colour) -> facing the right way
            float rx = f > 0 ? x + r[0] : x - r[0] - r[2];
            out.add(new float[] {rx, base - r[1] - r[3], r[2], r[3], r[4]});
        };
        if (b.act == Act.KO) {
            add.accept(new float[] {-16, 0, 30, 6, body});
            add.accept(new float[] {-22, 0, 7, 6, skin});
            return out;
        }
        boolean low = b.act == Act.CROUCH || (b.act == Act.BLOCK && kDownFor(b));
        float drop = low ? 9 : 0;
        float lean = b.act == Act.HIT ? -2 : 0;
        // legs
        if (b.act == Act.JUMP) {
            add.accept(new float[] {-5, 4, 4, 8, body});
            add.accept(new float[] {1, 6, 4, 7, body});
        } else if (b.act == Act.KICK && b.actT >= 5 && b.actT <= 11) {
            add.accept(new float[] {-5, 0, 4, 12, body});
            add.accept(new float[] {2, 10, 22, 4, body}); // (the kicking leg, out straight)
            add.accept(new float[] {24, 10, 4, 4, skin});
        } else {
            float step = b.act == Act.WALK ? Mth.sin(ticks * 0.5F) * 2 : 0;
            add.accept(new float[] {-5 + step, 0, 4, 12 - drop, body});
            add.accept(new float[] {2 - step, 0, 4, 12 - drop, body});
        }
        // body, belt, head, headband
        add.accept(new float[] {-5 + lean, 12 - drop, 10, 12, body});
        add.accept(new float[] {-5 + lean, 12 - drop, 10, 2, band});
        add.accept(new float[] {-4 + lean, 24 - drop, 8, 8, skin});
        add.accept(new float[] {-4 + lean, 29 - drop, 8, 2, band});
        add.accept(new float[] {1 + lean, 27 - drop, 2, 2, 0}); // (its eye)
        // arms
        if (b.act == Act.PUNCH && b.actT >= 3 && b.actT <= 8) {
            add.accept(new float[] {4, 19 - drop, 14, 3, body});
            add.accept(new float[] {18, 18 - drop, 5, 5, skin});
        } else if (b.act == Act.SPECIAL) {
            add.accept(new float[] {4, 18 - drop, 10, 3, body});
            add.accept(new float[] {4, 15 - drop, 10, 3, body});
            add.accept(new float[] {14, 15 - drop, 4, 7, skin});
        } else if (b.act == Act.BLOCK) {
            add.accept(new float[] {5, 14 - drop, 3, 14, body});
            add.accept(new float[] {5, 26 - drop, 4, 4, skin});
        } else if (b.act == Act.WIN) {
            add.accept(new float[] {3, 24 - drop, 3, 12, body});
            add.accept(new float[] {3, 36 - drop, 4, 4, skin});
        } else {
            add.accept(new float[] {4, 18 - drop, 7, 3, body}); // (its guard up)
            add.accept(new float[] {10, 19 - drop, 4, 4, skin});
        }
        return out;
    }

    private boolean kDownFor(Body b) {
        return b == me && kDown;
    }

    @Override
    public void draw(GuiGraphics g, Font font, int x0, int y0, float pt) {
        // the stage: a dusk sky, a skyline, the floor
        for (int y = 0; y < GROUND; y += 4) {
            float t = y / (float) GROUND;
            int c = 0xFF000000 | ((int) (30 + 90 * t) << 16) | ((int) (14 + 20 * t) << 8) | (int) (60 + 30 * (1 - t));
            box(g, x0, y0, 0, y, W, 4, c);
        }
        for (int i = 0; i < 12; i++) {
            int h = 20 + (i * 37 % 30);
            box(g, x0, y0, i * 16, GROUND - h, 14, h, 0xFF1A1030);
            for (int wy = GROUND - h + 4; wy < GROUND - 4; wy += 6) {
                if (((i * 7 + wy) % 3) == 0) box(g, x0, y0, i * 16 + 4, wy, 2, 2, 0xFFFFE74A);
            }
        }
        box(g, x0, y0, 0, GROUND, W, H - GROUND, 0xFF3A4A6A);
        box(g, x0, y0, 0, GROUND, W, 1, 0xFF9AD8FF);
        // the fighters and fireballs
        for (Body b : new Body[] {foe, me}) {
            for (float[] r : shape(b)) {
                box(g, x0, y0, r[0], r[1], r[2], r[3], rgb((int) r[4]));
            }
        }
        for (Fireball f : fireballs) {
            int c = rgb(f.colour());
            box(g, x0, y0, f.pos()[0] - 4, f.pos()[1] - 4, 8, 8, c);
            box(g, x0, y0, f.pos()[0] - 2, f.pos()[1] - 2, 4, 4, 0xFFFFFFFF);
        }
        // health bars, the clock, the names, rounds won
        bar(g, x0, y0, 6, me.hp, false);
        bar(g, x0, y0, W - 6 - 76, foe.hp, true);
        centred(g, font, Integer.toString(Math.max(0, clock / 20)), x0 + W / 2, y0 + 13, 0xFFFFE74A);
        g.drawString(font, me.style.name() + " " + "*".repeat(me.wins), x0 + 6, y0 + 22, 0xFFFFFFFF, false);
        String fn = "*".repeat(foe.wins) + " " + foe.style.name();
        g.drawString(font, fn, x0 + W - 6 - font.width(fn), y0 + 22, 0xFFFFFFFF, false);
        if (!banner.isEmpty()) {
            centred(g, font, banner, x0 + W / 2, y0 + 56, (ticks / 4) % 2 == 0 ? 0xFFFFE74A : 0xFFFF9A2E);
        }
    }

    private static void bar(GuiGraphics g, int x0, int y0, int x, int hp, boolean right) {
        box(g, x0, y0, x - 1, 12, 78, 7, 0xFF000000);
        box(g, x0, y0, x, 13, 76, 5, 0xFF5A1020);
        int w = Math.round(76 * hp / 100.0F);
        box(g, x0, y0, right ? x + 76 - w : x, 13, w, 5, hp > 30 ? 0xFFFFE74A : 0xFFFF4D5E);
    }

    @Override
    public int sample(float x, float y) {
        for (Body b : new Body[] {me, foe}) {
            for (float[] r : shape(b)) {
                if (x >= r[0] - 1 && x < r[0] + r[2] + 1 && y >= r[1] - 1 && y < r[1] + r[3] + 1) return (int) r[4];
            }
        }
        for (Fireball f : fireballs) {
            if (Math.abs(x - f.pos()[0]) < 5 && Math.abs(y - f.pos()[1]) < 5) return f.colour();
        }
        if (y >= 12 && y < 18) { // (the health bars)
            if (x >= 6 && x < 6 + 76 * me.hp / 100.0F) return 8;
            if (x >= W - 6 - 76 * foe.hp / 100.0F && x < W - 6) return 8;
            return 14;
        }
        return y >= GROUND ? 13 : 11;
    }

    @Override
    public String status() {
        return "";
    }
}
