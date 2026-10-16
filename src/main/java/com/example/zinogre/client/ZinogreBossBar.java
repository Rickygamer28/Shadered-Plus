package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Alpha Zinogres' boss bar, drawn instead of the vanilla one: a wider framed bar with a glowing electric fill (blue
 * for the Alpha Zinogre, crimson for the Alpha Stygian), a shimmer running along it, a pale trail that shows the damage
 * just taken before draining away, notches every 10%, and the name with the health left in percent above it.
 * (Recognised by the title's translation key, see ZinogreEntity#bossName.)
 */
@EventBusSubscriber(modid = ZinogreMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ZinogreBossBar {
    private static final int WIDTH = 240, HEIGHT = 9;
    /** Per bar: the damage trail's progress and when the health last went down. */
    private static final Map<UUID, float[]> TRAILS = new HashMap<>();

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents key) || !key.getKey().startsWith("boss.zinogre.")) {
            return;
        }
        boolean stygian = key.getKey().endsWith("stygian");
        Component name = key.getArgs().length > 0 && key.getArgs()[0] instanceof Component c ? c : boss.getName();
        event.setCanceled(true);
        event.setIncrement(HEIGHT + 17);
        draw(event.getGuiGraphics(), boss, name, stygian, event.getGuiGraphics().guiWidth() / 2 - WIDTH / 2, event.getY());
    }

    private static void draw(GuiGraphics g, LerpingBossEvent boss, Component name, boolean stygian, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float progress = Mth.clamp(boss.getProgress(), 0.0F, 1.0F);
        long now = System.currentTimeMillis();
        float time = (now % 100000L) / 1000.0F;

        // the damage trail: stays where the health was for a moment, then drains down to it
        float[] trail = TRAILS.computeIfAbsent(boss.getId(), id -> new float[] {progress, 0});
        if (progress > trail[0]) {
            trail[0] = progress;
        } else if (progress < trail[0] - 0.0005F) {
            if (trail[1] == 0) {
                trail[1] = now;
            }
            if (now - trail[1] > 450) {
                trail[0] = Math.max(progress, trail[0] - 0.006F);
            }
        } else {
            trail[1] = 0;
        }
        if (TRAILS.size() > 32) {
            TRAILS.clear();
        }

        int dark = stygian ? 0xFF2A0508 : 0xFF041A2A;
        int frame = stygian ? 0xFFB8323C : 0xFF5FC8F0;
        int frameLight = stygian ? 0xFFFF8A6A : 0xFFE6FBFF;
        int from = stygian ? 0xFF7A0A14 : 0xFF0C5FA8;
        int to = stygian ? 0xFFFF4A2A : 0xFF6FF0FF;

        // shadow and frame (corners cut for a rounded look)
        g.fill(x - 2, y - 1, x + WIDTH + 2, y + HEIGHT + 2, 0x80000000);
        g.fill(x - 1, y - 2, x + WIDTH + 1, y + HEIGHT + 1, frame);
        g.fill(x - 2, y - 1, x + WIDTH + 2, y + HEIGHT, frame);
        g.fill(x - 1, y - 2, x + WIDTH + 1, y - 1, frameLight);
        g.fill(x, y, x + WIDTH, y + HEIGHT - 1, dark);

        int filled = Math.round(WIDTH * progress);
        int trailEnd = Math.round(WIDTH * trail[0]);
        // the damage trail
        if (trailEnd > filled) {
            g.fill(x + filled, y, x + trailEnd, y + HEIGHT - 1, stygian ? 0xFFFFD2B0 : 0xFFFFF4B0);
        }
        // the fill: a gradient along the bar, brighter on top, with a pulse
        float pulse = 0.85F + 0.15F * Mth.sin(time * 3.0F);
        for (int i = 0; i < filled; i += 2) {
            int c = lerpColor(from, to, (float) i / WIDTH);
            c = scale(c, pulse);
            g.fill(x + i, y, x + Math.min(i + 2, filled), y + HEIGHT - 1, c);
        }
        if (filled > 0) {
            g.fill(x, y, x + filled, y + 3, 0x40FFFFFF); // shine on the top
            g.fill(x, y + HEIGHT - 3, x + filled, y + HEIGHT - 1, 0x30000000); // shade at the bottom
            // a shimmer running along the filled part
            int band = (int) ((time * 90.0F) % (WIDTH + 40)) - 20;
            for (int i = -6; i <= 6; i++) {
                int bx = band + i;
                if (bx >= 0 && bx < filled) {
                    int a = (int) (110 * (1.0F - Math.abs(i) / 7.0F));
                    g.fill(x + bx, y, x + bx + 1, y + HEIGHT - 1, a << 24 | 0xFFFFFF);
                }
            }
            // a bright edge where the health ends, with a flicker like a spark
            int edge = 0xFFFFFFFF;
            if ((now / 80) % 3 == 0) {
                edge = frameLight;
            }
            g.fill(x + filled - 1, y - 1, x + filled + 1, y + HEIGHT, edge);
        }
        // notches every 10%
        for (int n = 1; n < 10; n++) {
            int nx = x + WIDTH * n / 10;
            g.fill(nx, y + 1, nx + 1, y + HEIGHT - 2, 0x60000000);
        }

        // the name, and the health left
        String percent = Math.round(progress * 100.0F) + "%";
        Component title = Component.literal("⚡ ").withColor(stygian ? 0xFF6A4A : 0xFFE45C)
                .append(name.copy().withColor(stygian ? 0xFFB0A0 : 0xD8F6FF))
                .append(Component.literal(" ⚡").withColor(stygian ? 0xFF6A4A : 0xFFE45C));
        int tw = font.width(title);
        g.drawString(font, title, x + WIDTH / 2 - tw / 2, y - 11, 0xFFFFFF, true);
        g.drawString(font, percent, x + WIDTH - font.width(percent), y - 11, stygian ? 0xFFFF8A6A : 0xFF9FE9FF, true);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    private static int scale(int c, float k) {
        int r = Math.min(255, (int) ((c >> 16 & 255) * k)), gr = Math.min(255, (int) ((c >> 8 & 255) * k)), b = Math.min(255, (int) ((c & 255) * k));
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private ZinogreBossBar() {}
}
