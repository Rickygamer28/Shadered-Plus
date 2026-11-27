package com.example.skyblocks.client;

import com.example.skyblocks.TelevisionBlock;
import com.example.skyblocks.TelevisionBlockEntity;
import com.example.skyblocks.client.tv.TvPlayers;
import com.example.skyblocks.client.tv.VideoPlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * A television wall's screen (drawn once, by its bottom left block): one dark glass across the whole wall, and on it
 * the video, as big as fits and in its own shape - or, while there's none, what it's doing (loading, why it can't play).
 */
public class TelevisionRenderer implements BlockEntityRenderer<TelevisionBlockEntity> {
    private static final float FRONT = 14.0F / 16, EDGE = 1.0F / 16;
    private final Font font;

    public TelevisionRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(TelevisionBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState st = be.getBlockState();
        if (level == null || !(st.getBlock() instanceof TelevisionBlock)) return;
        BlockPos pos = be.getBlockPos();
        if (!TelevisionBlock.isAnchor(level, pos, st.getValue(TelevisionBlock.FACING))) return; // (drawn by the wall's first one)
        com.example.skyblocks.TvWall wall = com.example.skyblocks.TvWall.of(level, pos);
        boolean on = !be.source().isEmpty();
        VideoPlayer player = on ? TvPlayers.get(be) : null;
        if (!wall.curved()) {
            column(poseStack, pos, wall.columns().get(0));
            flat(poseStack, buffers, be, player, wall.width(), wall.height(), light, true);
            poseStack.popPose();
            return;
        }
        // round corners: its glass, and its picture spread along it, a column at a time
        int n = wall.width(), h = wall.height();
        boolean picture = player != null && player.hasPicture() && player.music() == null && player.state() != VideoPlayer.State.ERROR;
        float ar = picture ? player.pictureWidth() / (float) Math.max(1, player.pictureHeight()) : 1;
        float aw = n - 2 * EDGE, ah = h - 2 * EDGE;
        float pw = Math.min(aw, ah * ar), ph = pw / ar, s0 = (n - pw) / 2, y0 = h / 2.0F - ph / 2, y1 = h / 2.0F + ph / 2;
        for (int i = 0; i < n; i++) {
            column(poseStack, pos, wall.columns().get(i));
            Matrix4f m = poseStack.last().pose();
            float l = i == 0 ? 1 - EDGE : 1, r = i == n - 1 ? EDGE : 0;
            rect(buffers.getBuffer(RenderType.textBackground()), m, l, EDGE, r, h - EDGE, FRONT - 0.002F, on ? 0xFF050608 : 0xFF0C0D11,
                    on ? LightTexture.FULL_BRIGHT : light);
            float a0 = Math.max(i, s0), a1 = Math.min(i + 1, s0 + pw);
            if (picture && a0 < a1) {
                VertexConsumer v = buffers.getBuffer(RenderType.text(player.textureId()));
                float x0 = 1 - (a0 - i), x1 = 1 - (a1 - i), u0 = (a0 - s0) / pw, u1 = (a1 - s0) / pw, z = FRONT - 0.004F;
                int c = player.state() == VideoPlayer.State.PAUSED || player.state() == VideoPlayer.State.ENDED ? 0xFFB8B8B8 : 0xFFFFFFFF;
                v.addVertex(m, x0, y0, z).setColor(c).setUv(u0, 1).setLight(LightTexture.FULL_BRIGHT);
                v.addVertex(m, x1, y0, z).setColor(c).setUv(u1, 1).setLight(LightTexture.FULL_BRIGHT);
                v.addVertex(m, x1, y1, z).setColor(c).setUv(u1, 0).setLight(LightTexture.FULL_BRIGHT);
                v.addVertex(m, x0, y1, z).setColor(c).setUv(u0, 0).setLight(LightTexture.FULL_BRIGHT);
            }
            poseStack.popPose();
        }
        if (on && !picture) {
            // (anything else - loading, why it can't, a song - on its longest straight stretch)
            int bestStart = 0, bestLen = 1;
            for (int i = 0, start = 0; i < n; i++) {
                if (i > 0 && wall.columns().get(i).facing() != wall.columns().get(i - 1).facing()) start = i;
                if (i - start + 1 > bestLen) {
                    bestLen = i - start + 1;
                    bestStart = start;
                }
            }
            column(poseStack, pos, wall.columns().get(bestStart));
            flat(poseStack, buffers, be, player, bestLen, h, light, false);
            poseStack.popPose();
        }
    }

    /** Into a column's frame (pushed): x from 1 (its left, as you look at it) to 0, y from its bottom up, its front at FRONT. */
    private static void column(PoseStack poseStack, BlockPos anchor, com.example.skyblocks.TvWall.Column c) {
        poseStack.pushPose();
        poseStack.translate(c.bottom().getX() - anchor.getX(), c.bottom().getY() - anchor.getY(), c.bottom().getZ() - anchor.getZ());
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(c.facing().toYRot() + 180.0F))); // (as the model's turned: its front to the north)
        poseStack.translate(-0.5F, 0.0F, -0.5F);
    }

    /** A flat stretch w wide and h tall from the frame's column (x from 1 - w to 1): its glass (if asked) and what's on it. */
    private void flat(PoseStack poseStack, MultiBufferSource buffers, TelevisionBlockEntity be, VideoPlayer player, int w, int h, int light, boolean glassToo) {
        Matrix4f m = poseStack.last().pose();
        float left = 1 - EDGE, right = 1 - w + EDGE, bottom = EDGE, top = h - EDGE;
        boolean on = !be.source().isEmpty();
        if (glassToo) {
            VertexConsumer glass = buffers.getBuffer(RenderType.textBackground());
            int g = on ? 0xFF050608 : 0xFF0C0D11;
            rect(glass, m, left, bottom, right, top, FRONT - 0.002F, g, on ? LightTexture.FULL_BRIGHT : light);
            if (!on) {
                // (off: a faint sheen across the glass)
                float sx = left - (left - right) * 0.18F, ex = left - (left - right) * 0.42F;
                sheen(glass, m, sx, ex, bottom, top, FRONT - 0.003F, light);
                return;
            }
        }
        if (!on) return;
        float cx = 1 - w / 2.0F, cy = h / 2.0F, aw = w - 2 * EDGE, ah = h - 2 * EDGE;
        if (player != null && player.music() != null && player.state() != VideoPlayer.State.ERROR) {
            music(poseStack, buffers, player, player.music(), left, bottom, right, top);
        } else if (player != null && player.hasPicture()) {
            float ar = player.pictureWidth() / (float) Math.max(1, player.pictureHeight());
            float pw = Math.min(aw, ah * ar), ph = pw / ar;
            VertexConsumer v = buffers.getBuffer(RenderType.text(player.textureId()));
            float x0 = cx + pw / 2, x1 = cx - pw / 2, y0 = cy - ph / 2, y1 = cy + ph / 2, z = FRONT - 0.004F;
            int c = player.state() == VideoPlayer.State.PAUSED || player.state() == VideoPlayer.State.ENDED ? 0xFFB8B8B8 : 0xFFFFFFFF;
            v.addVertex(m, x0, y0, z).setColor(c).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, x1, y0, z).setColor(c).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, x1, y1, z).setColor(c).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, x0, y1, z).setColor(c).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
            if (player.state() == VideoPlayer.State.PAUSED) {
                pauseMark(buffers.getBuffer(RenderType.textBackground()), m, cx, cy, Math.min(pw, ph) * 0.12F, FRONT - 0.006F);
            }
        } else {
            String line, sub = "";
            int colour = 0xFFB8C2D2;
            if (player == null) {
                line = "Starting...";
            } else if (player.state() == VideoPlayer.State.ERROR) {
                line = "Can't play this";
                sub = player.error();
                colour = 0xFFFF8A7A;
            } else {
                int dots = (int) (System.currentTimeMillis() / 400 % 4);
                line = "Loading" + ".".repeat(dots);
                sub = player.title();
            }
            float hgt = Math.min(ah * 0.09F, aw * 0.06F);
            text(poseStack, buffers, line, cx, cy + hgt * 0.2F, aw * 0.9F, hgt, colour);
            if (!sub.isEmpty()) {
                text(poseStack, buffers, sub, cx, cy - hgt * 1.3F, aw * 0.9F, hgt * 0.6F, 0xFF8A96AA);
            }
        }
    }

    /**
     * A song (YouTube Music): the background in its cover's colour, the cover on the left with its title and artist
     * under it, and its words on the right - the line being sung bright in the middle, the ones before and after it
     * fading, moving up as it goes on - and how far along it is, along the bottom.
     */
    private void music(PoseStack poseStack, MultiBufferSource buffers, VideoPlayer player, com.example.skyblocks.client.tv.Music m,
                       float left, float bottom, float right, float top) {
        Matrix4f mat = poseStack.last().pose();
        float aw = left - right, ah = top - bottom;
        int base = m.colour;
        VertexConsumer bg = buffers.getBuffer(RenderType.textBackground());
        int hi = 0xFF000000 | MachineUi.shade(base, 0.55F) & 0xFFFFFF, lo = 0xFF000000 | MachineUi.shade(base, 0.28F) & 0xFFFFFF;
        float z = FRONT - 0.003F;
        bg.addVertex(mat, left, bottom, z).setColor(lo).setLight(LightTexture.FULL_BRIGHT);
        bg.addVertex(mat, right, bottom, z).setColor(lo).setLight(LightTexture.FULL_BRIGHT);
        bg.addVertex(mat, right, top, z).setColor(hi).setLight(LightTexture.FULL_BRIGHT);
        bg.addVertex(mat, left, top, z).setColor(hi).setLight(LightTexture.FULL_BRIGHT);
        // its progress, along the bottom
        long dur = player.duration();
        float f = dur > 0 ? Math.min(1.0F, player.position() / (float) dur) : 0;
        float by = bottom + ah * 0.03F, bh = Math.max(0.01F, ah * 0.008F);
        rect(bg, mat, left - aw * 0.04F, by, right + aw * 0.04F, by + bh, z - 0.0005F, 0x40FFFFFF, LightTexture.FULL_BRIGHT);
        rect(bg, mat, left - aw * 0.04F, by, left - aw * 0.04F - aw * 0.92F * f, by + bh, z - 0.001F, 0xE6FFFFFF, LightTexture.FULL_BRIGHT);

        // the cover
        float cs = Math.min(ah * 0.6F, aw * 0.34F);
        float cx0 = left - aw * 0.07F, cx1 = cx0 - cs;
        float cyTop = bottom + ah * 0.5F + cs * 0.55F, cyBottom = cyTop - cs;
        ResourceLocation cover = player.cover();
        if (cover != null) {
            VertexConsumer v = buffers.getBuffer(RenderType.text(cover));
            float cz = FRONT - 0.0045F;
            v.addVertex(mat, cx0, cyBottom, cz).setColor(0xFFFFFFFF).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(mat, cx1, cyBottom, cz).setColor(0xFFFFFFFF).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(mat, cx1, cyTop, cz).setColor(0xFFFFFFFF).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(mat, cx0, cyTop, cz).setColor(0xFFFFFFFF).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
        } else {
            VertexConsumer v = buffers.getBuffer(RenderType.textBackground());
            rect(v, mat, cx0, cyBottom, cx1, cyTop, FRONT - 0.0045F, 0xFF000000 | MachineUi.shade(base, 0.8F) & 0xFFFFFF, LightTexture.FULL_BRIGHT);
            textAt(poseStack, buffers, "\u266B", cx0 - cs / 2, cyBottom + cs * 0.3F, cs * 0.4F, cs, 0x80FFFFFF, true);
        }
        float th = Math.min(ah * 0.055F, aw * 0.03F);
        textAt(poseStack, buffers, m.title, cx0, cyBottom - th * 1.9F, th, cs * 1.6F, 0xFFFFFFFF, false);
        textAt(poseStack, buffers, m.artist, cx0, cyBottom - th * 3.3F, th * 0.85F, cs * 1.6F, 0xB4FFFFFF, false);

        // its words
        float lx = left - aw * 0.47F, room = aw * 0.48F, mid = bottom + ah * 0.52F;
        float lh = Math.min(ah * 0.11F, aw * 0.06F), lt = lh * 0.55F;
        java.util.List<com.example.skyblocks.client.tv.Music.Line> lines = m.lyrics;
        if (lines == null || lines.isEmpty()) {
            textAt(poseStack, buffers, lines == null ? "Looking for the lyrics..." : "No lyrics found", lx, mid, lt * 0.8F, room, 0x80FFFFFF, false);
            return;
        }
        long pos = player.position();
        float at;
        if (m.timed) {
            int ci = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).ms() <= pos) ci = i;
            }
            at = ci;
            if (ci + 1 < lines.size()) {
                long next = lines.get(ci + 1).ms();
                float k = Math.max(0.0F, Math.min(1.0F, (pos - (next - 300)) / 300.0F)); // (moving up just before the next line)
                at += k * k * (3 - 2 * k);
            }
        } else {
            at = dur > 0 ? f * lines.size() - 1.5F : 0;
        }
        int now = Math.round(at);
        for (int k = (int) Math.floor(at) - 3; k <= (int) Math.ceil(at) + 5; k++) {
            if (k < 0 || k >= lines.size()) continue;
            float y = mid - (k - at) * lh;
            if (y < bottom + ah * 0.08F || y > top - lh * 0.6F) continue;
            String text = lines.get(k).text();
            if (text.isEmpty()) text = "\u266A";
            float d = Math.abs(k - at);
            int alpha = k == now && m.timed ? 0xFF : Math.max(0x18, (int) (0x78 - d * 0x16));
            textAt(poseStack, buffers, text, lx, y, lt, room, alpha << 24 | 0xFFFFFF, false);
        }
    }

    /** A line of text: its left edge (or its middle) at x, its foot at y, this tall unless too wide for the room. */
    private void textAt(PoseStack poseStack, MultiBufferSource buffers, String line, float x, float y, float height, float room, int colour, boolean centred) {
        float scale = height / 9.0F;
        if (font.width(line) * scale > room) scale = room / Math.max(1, font.width(line));
        poseStack.pushPose();
        poseStack.translate(x, y + 9 * scale, FRONT - 0.006F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(line, centred ? -font.width(line) / 2.0F : 0.0F, 0.0F, colour, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static void rect(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float z, int c, int light) {
        v.addVertex(m, x0, y0, z).setColor(c).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(c).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(c).setLight(light);
        v.addVertex(m, x0, y1, z).setColor(c).setLight(light);
    }

    private static void sheen(VertexConsumer v, Matrix4f m, float xa, float xb, float y0, float y1, float z, int light) {
        float slant = (y1 - y0) * 0.35F, wdt = (xa - xb);
        int c = 0x10FFFFFF;
        v.addVertex(m, xa, y0, z).setColor(c).setLight(light);
        v.addVertex(m, xa - wdt * 0.35F, y0, z).setColor(c).setLight(light);
        v.addVertex(m, xb - slant * 0.2F, y1, z).setColor(c).setLight(light);
        v.addVertex(m, xb + wdt * 0.25F, y1, z).setColor(c).setLight(light);
    }

    /** Two bars in the middle: paused. */
    private static void pauseMark(VertexConsumer v, Matrix4f m, float cx, float cy, float s, float z) {
        int c = 0xC0FFFFFF;
        rect(v, m, cx + s * 0.55F, cy - s * 0.6F, cx + s * 0.2F, cy + s * 0.6F, z, c, LightTexture.FULL_BRIGHT);
        rect(v, m, cx - s * 0.2F, cy - s * 0.6F, cx - s * 0.55F, cy + s * 0.6F, z, c, LightTexture.FULL_BRIGHT);
    }

    private void text(PoseStack poseStack, MultiBufferSource buffers, String line, float x, float y, float room, float height, int colour) {
        float scale = height / 9.0F;
        if (font.width(line) * scale > room) scale = room / Math.max(1, font.width(line));
        poseStack.pushPose();
        poseStack.translate(x, y + 9 * scale, FRONT - 0.006F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(line, -font.width(line) / 2.0F, 0.0F, colour, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(TelevisionBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(TelevisionBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(com.example.skyblocks.TvWall.MAX_COLUMNS / 2.0, 0, com.example.skyblocks.TvWall.MAX_COLUMNS / 2.0)
                .expandTowards(0, com.example.skyblocks.TvWall.MAX_H, 0);
    }
}
