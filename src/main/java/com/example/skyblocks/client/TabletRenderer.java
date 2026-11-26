package com.example.skyblocks.client;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TabletState;
import com.example.skyblocks.client.tv.Music;
import com.example.skyblocks.client.tv.TvPlayers;
import com.example.skyblocks.client.tv.VideoPlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * A tablet: a slim slab with rounded corners (graphite, a camera on its back), its screen on its front - showing what
 * it plays (the video, or a song's cover and words), the same in your hand and in anyone else's.
 */
public class TabletRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation BODY = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/item/tablet_body.png");
    /** Its size (in the item's 0-1 box): a 16:10 slab, its screen inset from its edge. */
    private static final float X0 = 0.0F, X1 = 1.0F, Y0 = 0.19F, Y1 = 0.81F, Z0 = 0.47F, Z1 = 0.53F, R = 0.055F, INSET = 0.035F;
    private static final int CORNER = 5;

    public TabletRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        PoseStack.Pose p = pose.last();
        body(buffers.getBuffer(RenderType.entitySolid(BODY)), p, light, overlay);
        TabletState s = stack.get(ModComponents.TABLET.get());
        VideoPlayer player = s == null || s.source().isEmpty() ? null : TvPlayers.tablet(s.id());
        float sx0 = X0 + INSET, sx1 = X1 - INSET, sy0 = Y0 + INSET, sy1 = Y1 - INSET, z = Z1 + 0.0015F;
        Matrix4f m = p.pose();
        VertexConsumer glass = buffers.getBuffer(RenderType.textBackground());
        boolean on = player != null;
        quad(glass, m, sx0, sy0, sx1, sy1, z, on ? 0xFF040506 : 0xFF0B0C10, on ? LightTexture.FULL_BRIGHT : light);
        if (!on) {
            // (off: a sheen across the glass)
            sheen(glass, m, sx0, sy0, sx1, sy1, z + 0.0005F, light);
            if (s != null && !s.source().isEmpty()) {
                text(pose, buffers, "Loading...", (sx0 + sx1) / 2, (sy0 + sy1) / 2 - 0.02F, 0.045F, sx1 - sx0, 0xFFB8C2D2, true);
            }
            return;
        }
        float aw = sx1 - sx0, ah = sy1 - sy0;
        Music music = player.music();
        if (music != null && player.state() != VideoPlayer.State.ERROR) {
            music(pose, buffers, player, music, sx0, sy0, sx1, sy1, z);
        } else if (player.hasPicture()) {
            float ar = player.pictureWidth() / (float) Math.max(1, player.pictureHeight());
            float pw = Math.min(aw, ah * ar), ph = pw / ar, cx = (sx0 + sx1) / 2, cy = (sy0 + sy1) / 2;
            VertexConsumer v = buffers.getBuffer(RenderType.text(player.textureId()));
            int c = player.state() == VideoPlayer.State.PAUSED || player.state() == VideoPlayer.State.ENDED ? 0xFFB8B8B8 : 0xFFFFFFFF;
            float vz = z + 0.001F;
            v.addVertex(m, cx - pw / 2, cy - ph / 2, vz).setColor(c).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, cx + pw / 2, cy - ph / 2, vz).setColor(c).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, cx + pw / 2, cy + ph / 2, vz).setColor(c).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, cx - pw / 2, cy + ph / 2, vz).setColor(c).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
        } else {
            String line = player.state() == VideoPlayer.State.ERROR ? "Can't play this" : "Loading" + ".".repeat((int) (System.currentTimeMillis() / 400 % 4));
            int col = player.state() == VideoPlayer.State.ERROR ? 0xFFFF8A7A : 0xFFB8C2D2;
            text(pose, buffers, line, (sx0 + sx1) / 2, (sy0 + sy1) / 2, 0.05F, aw * 0.9F, col, true);
            String sub = player.state() == VideoPlayer.State.ERROR ? player.error() : player.title();
            if (!sub.isEmpty()) {
                text(pose, buffers, sub, (sx0 + sx1) / 2, (sy0 + sy1) / 2 - 0.07F, 0.03F, aw * 0.9F, 0xFF8A96AA, true);
            }
        }
    }

    /** A song, small: its cover on the left, title and artist, the line being sung. */
    private void music(PoseStack pose, MultiBufferSource buffers, VideoPlayer player, Music mu, float sx0, float sy0, float sx1, float sy1, float z) {
        Matrix4f m = pose.last().pose();
        float aw = sx1 - sx0, ah = sy1 - sy0;
        VertexConsumer bg = buffers.getBuffer(RenderType.textBackground());
        int hi = 0xFF000000 | MachineUi.shade(mu.colour, 0.55F) & 0xFFFFFF, lo = 0xFF000000 | MachineUi.shade(mu.colour, 0.28F) & 0xFFFFFF;
        float bz = z + 0.0005F;
        bg.addVertex(m, sx0, sy0, bz).setColor(lo).setLight(LightTexture.FULL_BRIGHT);
        bg.addVertex(m, sx1, sy0, bz).setColor(lo).setLight(LightTexture.FULL_BRIGHT);
        bg.addVertex(m, sx1, sy1, bz).setColor(hi).setLight(LightTexture.FULL_BRIGHT);
        bg.addVertex(m, sx0, sy1, bz).setColor(hi).setLight(LightTexture.FULL_BRIGHT);
        long dur = player.duration();
        float f = dur > 0 ? Math.min(1, player.position() / (float) dur) : 0;
        quad(bg, m, sx0 + aw * 0.04F, sy0 + ah * 0.05F, sx1 - aw * 0.04F, sy0 + ah * 0.065F, bz + 0.0005F, 0x40FFFFFF, LightTexture.FULL_BRIGHT);
        quad(bg, m, sx0 + aw * 0.04F, sy0 + ah * 0.05F, sx0 + aw * 0.04F + aw * 0.92F * f, sy0 + ah * 0.065F, bz + 0.001F, 0xE6FFFFFF, LightTexture.FULL_BRIGHT);
        float cs = ah * 0.62F, cx0 = sx0 + aw * 0.06F, cy1 = sy0 + ah * 0.86F, cy0 = cy1 - cs;
        ResourceLocation cover = player.cover();
        if (cover != null) {
            VertexConsumer v = buffers.getBuffer(RenderType.text(cover));
            float cz = z + 0.0015F;
            v.addVertex(m, cx0, cy0, cz).setColor(0xFFFFFFFF).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, cx0 + cs, cy0, cz).setColor(0xFFFFFFFF).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, cx0 + cs, cy1, cz).setColor(0xFFFFFFFF).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, cx0, cy1, cz).setColor(0xFFFFFFFF).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
        }
        text(pose, buffers, mu.title, cx0, cy0 - ah * 0.12F, ah * 0.07F, cs * 1.5F, 0xFFFFFFFF, false);
        text(pose, buffers, mu.artist, cx0, cy0 - ah * 0.21F, ah * 0.055F, cs * 1.5F, 0xB4FFFFFF, false);
        java.util.List<Music.Line> lines = mu.lyrics;
        float lx = sx0 + aw * 0.5F, room = aw * 0.46F, mid = sy0 + ah * 0.55F, lh = ah * 0.13F;
        if (lines == null || lines.isEmpty()) {
            text(pose, buffers, lines == null ? "Looking for the lyrics..." : "No lyrics found", lx, mid, ah * 0.05F, room, 0x80FFFFFF, false);
            return;
        }
        long pos = player.position();
        float at;
        if (mu.timed) {
            int ci = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).ms() <= pos) ci = i;
            }
            at = ci;
            if (ci + 1 < lines.size()) {
                float k = Mth.clamp((pos - (lines.get(ci + 1).ms() - 300)) / 300.0F, 0, 1);
                at += k * k * (3 - 2 * k);
            }
        } else {
            at = f * lines.size() - 1.5F;
        }
        int now = Math.round(at);
        for (int k = (int) Math.floor(at) - 2; k <= (int) Math.ceil(at) + 3; k++) {
            if (k < 0 || k >= lines.size()) continue;
            float y = mid - (k - at) * lh;
            if (y < sy0 + ah * 0.1F || y > sy1 - lh * 0.6F) continue;
            String t = lines.get(k).text().isEmpty() ? "♪" : lines.get(k).text();
            int alpha = k == now && mu.timed ? 0xFF : Math.max(0x18, (int) (0x78 - Math.abs(k - at) * 0x20));
            text(pose, buffers, t, lx, y, ah * 0.075F, room, alpha << 24 | 0xFFFFFF, false);
        }
    }

    private void text(PoseStack pose, MultiBufferSource buffers, String line, float x, float y, float height, float room, int colour, boolean centred) {
        Font font = Minecraft.getInstance().font;
        float scale = height / 9.0F;
        if (font.width(line) * scale > room) scale = room / Math.max(1, font.width(line));
        pose.pushPose();
        pose.translate(x, y + 9 * scale, Z1 + 0.003F);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(line, centred ? -font.width(line) / 2.0F : 0.0F, 0.0F, colour, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    // ---- its body

    /** The rounded outline (x, y), going round anticlockwise. */
    private static float[][] outline() {
        float[][] c = {{X1 - R, Y1 - R}, {X0 + R, Y1 - R}, {X0 + R, Y0 + R}, {X1 - R, Y0 + R}};
        float[][] out = new float[4 * (CORNER + 1)][];
        int n = 0;
        for (int k = 0; k < 4; k++) {
            for (int i = 0; i <= CORNER; i++) {
                float a = (k + i / (float) CORNER) * Mth.HALF_PI;
                out[n++] = new float[] {c[k][0] + Mth.cos(a) * R, c[k][1] + Mth.sin(a) * R};
            }
        }
        return out;
    }

    private static final float[][] OUTLINE = outline();

    private static void body(VertexConsumer v, PoseStack.Pose p, int light, int overlay) {
        float[][] o = OUTLINE;
        int n = o.length;
        float cx = (X0 + X1) / 2, cy = (Y0 + Y1) / 2;
        // front and back (fans round the middle), its edge all round
        for (int i = 0; i < n; i++) {
            float[] a = o[i], b = o[(i + 1) % n];
            vert(v, p, cx, cy, Z1, 0.5F, 0.5F, 0, 0, 1, light, overlay);
            vert(v, p, cx, cy, Z1, 0.5F, 0.5F, 0, 0, 1, light, overlay);
            vert(v, p, a[0], a[1], Z1, a[0], 1 - a[1], 0, 0, 1, light, overlay);
            vert(v, p, b[0], b[1], Z1, b[0], 1 - b[1], 0, 0, 1, light, overlay);
            vert(v, p, cx, cy, Z0, 0.5F, 0.5F, 0, 0, -1, light, overlay);
            vert(v, p, cx, cy, Z0, 0.5F, 0.5F, 0, 0, -1, light, overlay);
            vert(v, p, b[0], b[1], Z0, b[0], 1 - b[1], 0, 0, -1, light, overlay);
            vert(v, p, a[0], a[1], Z0, a[0], 1 - a[1], 0, 0, -1, light, overlay);
            float nx = (b[1] - a[1]), ny = -(b[0] - a[0]), len = Mth.sqrt(nx * nx + ny * ny);
            nx /= len;
            ny /= len;
            vert(v, p, a[0], a[1], Z0, 0.02F, 0.02F, nx, ny, 0, light, overlay);
            vert(v, p, b[0], b[1], Z0, 0.06F, 0.02F, nx, ny, 0, light, overlay);
            vert(v, p, b[0], b[1], Z1, 0.06F, 0.06F, nx, ny, 0, light, overlay);
            vert(v, p, a[0], a[1], Z1, 0.02F, 0.06F, nx, ny, 0, light, overlay);
        }
        // the camera on its back (a dark ring near a corner)
        float camX = X0 + 0.09F, camY = Y1 - 0.09F, cz = Z0 - 0.001F;
        for (int i = 0; i < 12; i++) {
            float a0 = i * Mth.TWO_PI / 12, a1 = (i + 1) * Mth.TWO_PI / 12, r = 0.035F;
            vert(v, p, camX, camY, cz, 0.97F, 0.97F, 0, 0, -1, light, overlay);
            vert(v, p, camX, camY, cz, 0.97F, 0.97F, 0, 0, -1, light, overlay);
            vert(v, p, camX + Mth.cos(a1) * r, camY + Mth.sin(a1) * r, cz, 0.97F, 0.97F, 0, 0, -1, light, overlay);
            vert(v, p, camX + Mth.cos(a0) * r, camY + Mth.sin(a0) * r, cz, 0.97F, 0.97F, 0, 0, -1, light, overlay);
        }
    }

    private static void vert(VertexConsumer v, PoseStack.Pose p, float x, float y, float z, float u, float vv, float nx, float ny, float nz, int light, int overlay) {
        v.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, vv).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
    }

    private static void quad(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float z, int c, int light) {
        v.addVertex(m, x0, y0, z).setColor(c).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(c).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(c).setLight(light);
        v.addVertex(m, x0, y1, z).setColor(c).setLight(light);
    }

    private static void sheen(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float z, int light) {
        float w = x1 - x0;
        int c = 0x14FFFFFF;
        v.addVertex(m, x0 + w * 0.15F, y0, z).setColor(c).setLight(light);
        v.addVertex(m, x0 + w * 0.32F, y0, z).setColor(c).setLight(light);
        v.addVertex(m, x0 + w * 0.52F, y1, z).setColor(c).setLight(light);
        v.addVertex(m, x0 + w * 0.35F, y1, z).setColor(c).setLight(light);
    }
}
