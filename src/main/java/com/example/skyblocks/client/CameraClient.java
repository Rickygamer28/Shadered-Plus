package com.example.skyblocks.client;

import com.example.skyblocks.PhotoPayloads;
import com.example.skyblocks.PhotoStore;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Taking a photo: the HUD and hand hidden for a couple of frames, the finished frame read back, cut to 4:3, shrunk to
 * PhotoStore's size and sent to the server (which prints it). Then a little white flash.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CameraClient {
    private CameraClient() {}

    /** Frames to go before the picture is taken (0: not taking one). */
    private static int frames;
    private static boolean hadHidden;
    private static float flash;
    /** How many photos this client has taken (each picture's pieces carry it). */
    private static int takes;

    static void take() {
        if (frames > 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        hadHidden = mc.options.hideGui;
        mc.options.hideGui = true;
        frames = 2;
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (frames <= 0 || --frames > 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            int[] rgb = grab(mc);
            if (rgb != null) {
                byte[] data = PhotoCodec.encodeFull(rgb, PhotoStore.W, PhotoStore.H);
                if (data.length <= PhotoStore.MAX_BYTES) {
                    // (in pieces: a packet to the server can't carry it all)
                    int total = (data.length + PhotoStore.PIECE - 1) / PhotoStore.PIECE, take = ++takes;
                    for (int i = 0; i < total; i++) {
                        int from = i * PhotoStore.PIECE, to = Math.min(data.length, from + PhotoStore.PIECE);
                        PacketDistributor.sendToServer(new PhotoPayloads.Take(take, i, total, java.util.Arrays.copyOfRange(data, from, to)));
                    }
                }
            }
        } catch (RuntimeException e) {
            SkyBlocksMod.LOGGER.warn("Couldn't take the photo: {}", e.toString());
        } finally {
            mc.options.hideGui = hadHidden;
            flash = 1.0F;
        }
    }

    /** The screen as it is now, the middle 4:3 of it, shrunk (each pixel the average of the ones it covers). */
    private static int[] grab(Minecraft mc) {
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = img.getWidth(), h = img.getHeight();
            int cw = w, ch = h;
            if (w * 3 > h * 4) {
                cw = h * 4 / 3;
            } else {
                ch = w * 3 / 4;
            }
            int ox = (w - cw) / 2, oy = (h - ch) / 2;
            int W = PhotoStore.W, H = PhotoStore.H;
            int[] out = new int[W * H];
            for (int y = 0; y < H; y++) {
                int y0 = oy + y * ch / H, y1 = Math.max(y0 + 1, oy + (y + 1) * ch / H);
                for (int x = 0; x < W; x++) {
                    int x0 = ox + x * cw / W, x1 = Math.max(x0 + 1, ox + (x + 1) * cw / W);
                    long r = 0, g = 0, b = 0;
                    int n = 0;
                    // (a few source pixels across each is plenty, and much quicker on a big screen)
                    int step = Math.max(1, (x1 - x0) / 3);
                    for (int sy = y0; sy < y1; sy += step) {
                        for (int sx = x0; sx < x1; sx += step) {
                            int c = img.getPixelRGBA(sx, sy); // (ABGR)
                            r += c & 255;
                            g += c >> 8 & 255;
                            b += c >> 16 & 255;
                            n++;
                        }
                    }
                    out[y * W + x] = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
                }
            }
            return out;
        }
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        if (flash <= 0) {
            return;
        }
        int a = (int) (Math.min(1.0F, flash) * 200);
        var g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), a << 24 | 0xFFFFFF);
        flash -= 0.12F;
    }
}
