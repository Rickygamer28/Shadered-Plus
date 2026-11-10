package com.example.skyblocks.client.arcade;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Pong against the cabinet: first to 7 takes the match; win and the next match is harder. Lose one and it's over. */
public class Pong extends ArcadeGame {
    private static final int PH = 26, PW = 4, PX = 8, CX = W - 12;
    private float py, cy, py0, cy0, bx, by, bx0, by0, vx, vy, cpuSpeed;
    private int you, cpu, serveWait, serveDir, match;
    private boolean up, down;

    @Override
    public String name() {
        return "PONG";
    }

    @Override
    public String[] help() {
        return new String[] {"Up / down (W / S): your paddle", "First to 7 - each match harder"};
    }

    @Override
    public void start() {
        score = 0;
        over = won = false;
        match = 0;
        cpuSpeed = 2.0F;
        newMatch();
    }

    private void newMatch() {
        you = cpu = 0;
        py = cy = py0 = cy0 = H / 2.0F;
        serve(1);
    }

    private void serve(int dir) {
        bx = bx0 = W / 2.0F;
        by = by0 = H / 2.0F;
        vx = vy = 0;
        serveWait = 30;
        serveDir = dir;
    }

    @Override
    public void tick() {
        ticks++;
        py0 = py;
        cy0 = cy;
        bx0 = bx;
        by0 = by;
        if (up) py -= 4.0F;
        if (down) py += 4.0F;
        py = Mth.clamp(py, PH / 2.0F, H - PH / 2.0F);
        // the cabinet's paddle: after the ball when it's coming, back to the middle when not
        float target = vx > 0 ? by + Mth.sin(ticks * 0.05F) * 6.0F : H / 2.0F;
        cy += Mth.clamp(target - cy, -cpuSpeed, cpuSpeed);
        cy = Mth.clamp(cy, PH / 2.0F, H - PH / 2.0F);
        if (serveWait > 0) {
            if (--serveWait == 0) {
                float a = (random.nextFloat() - 0.5F) * 0.9F;
                vx = serveDir * 2.6F * Mth.cos(a);
                vy = 2.6F * Mth.sin(a);
            }
            return;
        }
        for (int s = 0; s < 3; s++) {
            bx += vx / 3.0F;
            by += vy / 3.0F;
            if (by < 2) {
                by = 2;
                vy = Math.abs(vy);
                sfx(Sfx.PONG_WALL);
            } else if (by > H - 2) {
                by = H - 2;
                vy = -Math.abs(vy);
                sfx(Sfx.PONG_WALL);
            }
            if (vx < 0 && bx <= PX + PW + 2 && bx >= PX - 2 && Math.abs(by - py) <= PH / 2.0F + 2) {
                vx = Math.min(Math.abs(vx) * 1.06F, 6.5F);
                vy += (by - py) / PH * 2.6F;
                score += 10;
                sfx(Sfx.PONG_HIT);
            }
            if (vx > 0 && bx >= CX - 2 && bx <= CX + PW + 2 && Math.abs(by - cy) <= PH / 2.0F + 2) {
                vx = -Math.min(Math.abs(vx) * 1.06F, 6.5F);
                vy += (by - cy) / PH * 2.6F;
                sfx(Sfx.PONG_HIT, 0.85F);
            }
        }
        if (bx < -4) {
            sfx(Sfx.PONG_LOSE);
            if (++cpu >= 7) {
                over = true;
            } else {
                serve(1);
            }
        } else if (bx > W + 4) {
            score += 100;
            sfx(Sfx.PONG_SCORE);
            if (++you >= 7) {
                sfx(Sfx.WIN);
                score += 500 * (match + 1);
                match++;
                cpuSpeed += 0.5F; // (the next one: quicker)
                newMatch();
            } else {
                serve(-1);
            }
        }
    }

    @Override
    public void keyPressed(int key) {
        up |= up(key);
        down |= down(key);
    }

    @Override
    public void keyReleased(int key) {
        if (up(key)) up = false;
        if (down(key)) down = false;
    }

    @Override
    public void draw(GuiGraphics g, Font font, int x0, int y0, float pt) {
        for (int y = 0; y < H; y += 8) {
            box(g, x0, y0, W / 2.0F - 1, y + 2, 2, 4, rgb(13));
        }
        centred(g, font, Integer.toString(you), x0 + W / 2 - 24, y0 + 14, 0xFFFFFFFF);
        centred(g, font, Integer.toString(cpu), x0 + W / 2 + 24, y0 + 14, 0xFFFFFFFF);
        float p = Mth.lerp(pt, py0, py), c = Mth.lerp(pt, cy0, cy);
        box(g, x0, y0, PX, p - PH / 2.0F, PW, PH, rgb(5));
        box(g, x0, y0, CX, c - PH / 2.0F, PW, PH, rgb(6));
        if (serveWait == 0 || (ticks / 4) % 2 == 0) {
            box(g, x0, y0, Mth.lerp(pt, bx0, bx) - 2, Mth.lerp(pt, by0, by) - 2, 4, 4, 0xFFFFFFFF);
        }
    }

    @Override
    public int sample(float x, float y) {
        if (Math.abs(x - bx) < 4 && Math.abs(y - by) < 4) return 4;
        if (x >= PX - 2 && x <= PX + PW + 2 && Math.abs(y - py) < PH / 2.0F) return 5;
        if (x >= CX - 2 && x <= CX + PW + 2 && Math.abs(y - cy) < PH / 2.0F) return 6;
        if (Math.abs(x - W / 2.0F) < 2 && ((int) y / 8) % 2 == 0) return 13;
        return 0;
    }

    @Override
    public String status() {
        return "MATCH " + (match + 1);
    }
}
