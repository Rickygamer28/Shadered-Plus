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
import org.joml.Vector3f;

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
        // round corners: its glass a column at a time - and where one column's glass doesn't meet the next's (the
        // screens are thin, at the backs of their blocks), a bend of glass and case between them, curving from one
        // to the other - and everything on it drawn as if the wall were flat, bent round with it
        int n = wall.width(), h = wall.height();
        Matrix4f base = new Matrix4f(poseStack.last().pose());
        Vector3f[] lefts = new Vector3f[n], rights = new Vector3f[n];
        Matrix4f[] locals = new Matrix4f[n];
        for (int i = 0; i < n; i++) {
            PoseStack ps = new PoseStack();
            column(ps, pos, wall.columns().get(i));
            locals[i] = new Matrix4f(ps.last().pose());
            lefts[i] = locals[i].transformPosition(new Vector3f(1, 0, FRONT));
            rights[i] = locals[i].transformPosition(new Vector3f(0, 0, FRONT));
        }
        java.util.List<Matrix4f> frames = new java.util.ArrayList<>();
        java.util.List<Float> widths = new java.util.ArrayList<>();
        java.util.List<Matrix4f> bends = new java.util.ArrayList<>();
        java.util.List<Float> bendWidths = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            Matrix4f m = new Matrix4f(base).mul(locals[i]);
            frames.add(m);
            widths.add(1.0F);
            float l = i == 0 ? 1 - EDGE : 1, r = i == n - 1 ? EDGE : 0;
            rect(buffers.getBuffer(RenderType.textBackground()), m, l, EDGE, r, h - EDGE, FRONT - 0.002F, on ? 0xFF050608 : 0xFF0C0D11,
                    on ? LightTexture.FULL_BRIGHT : light);
            if (i + 1 < n && rights[i].distance(lefts[i + 1]) > 0.01F) {
                Vector3f d0 = new Vector3f(rights[i]).sub(lefts[i]).normalize(), d1 = new Vector3f(rights[i + 1]).sub(lefts[i + 1]).normalize();
                for (Matrix4f[] seg : bend(rights[i], d0, lefts[i + 1], d1)) {
                    Matrix4f bm = new Matrix4f(base).mul(seg[0]);
                    float w = seg[1].m00();
                    frames.add(bm);
                    widths.add(w);
                    bends.add(bm);
                    bendWidths.add(w);
                    rect(buffers.getBuffer(RenderType.textBackground()), bm, 1, EDGE, 0, h - EDGE, FRONT - 0.002F, on ? 0xFF050608 : 0xFF0C0D11,
                            on ? LightTexture.FULL_BRIGHT : light);
                }
            }
        }
        if (!bends.isEmpty()) {
            caseOf(buffers, st, bends, bendWidths, h, light);
        }
        if (on) {
            float[] ws = new float[widths.size()];
            float total = 0;
            for (int i = 0; i < ws.length; i++) total += ws[i] = widths.get(i);
            CurvedBuffers bent = new CurvedBuffers(buffers, frames.toArray(new Matrix4f[0]), ws);
            flat(new PoseStack(), bent, be, player, total, h, light, false);
            bent.finish();
        }
    }

    /**
     * The bend from one column's glass (ending at a, going along da) to the next's (starting at b, going along db): a
     * curve through where the two lines meet, in a few flat pieces - each its frame (as a column's: x from 1 at its
     * start to 0, its glass at FRONT) and, in the second's m00, how wide it is.
     */
    private static java.util.List<Matrix4f[]> bend(Vector3f a, Vector3f da, Vector3f b, Vector3f db) {
        // (where the lines meet: a + t da = b - u db)
        float cross = da.x * db.z - da.z * db.x;
        float ex = b.x - a.x, ez = b.z - a.z;
        Vector3f c;
        float t = Math.abs(cross) < 1.0e-3F ? -1 : (ex * db.z - ez * db.x) / cross;
        float u = Math.abs(cross) < 1.0e-3F ? -1 : (da.x * ez - da.z * ex) / cross;
        int segs = 6;
        if (t <= 0 || t > 2) {
            c = new Vector3f(a).add(b).mul(0.5F); // (no corner to round: straight across)
            segs = 1;
        } else if (u <= 0.02F) {
            // (the next one's glass starts back behind where this one's line meets it - a television in the corner
            // itself: this one's glass carried on straight to that corner)
            c = new Vector3f(a).fma(t, da);
            b = c;
            segs = 1;
        } else {
            c = new Vector3f(a).fma(t, da);
        }
        java.util.List<Matrix4f[]> out = new java.util.ArrayList<>();
        Vector3f prev = new Vector3f(a);
        for (int k = 1; k <= segs; k++) {
            float s = k / (float) segs, r = 1 - s;
            Vector3f p = new Vector3f(a).mul(r * r).fma(2 * r * s, c).fma(s * s, b);
            float dx = p.x - prev.x, dz = p.z - prev.z, len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len > 1.0e-4F) {
                float nx = -dz / len, nz = dx / len; // (out of the glass)
                // local x 1 -> prev, 0 -> p; z FRONT on the glass, less out of it
                Matrix4f m = new Matrix4f(prev.x - p.x, 0, prev.z - p.z, 0, 0, 1, 0, 0, -nx, 0, -nz, 0,
                        p.x + nx * FRONT, prev.y, p.z + nz * FRONT, 1);
                out.add(new Matrix4f[] {m, new Matrix4f().m00(len)});
            }
            prev = p;
        }
        return out;
    }

    /** The bends' case: their frame above and below the glass, their back, their top and bottom - in the wall's case. */
    private static void caseOf(MultiBufferSource buffers, BlockState st, java.util.List<Matrix4f> bends, java.util.List<Float> widths, int h, int light) {
        int frame = st.hasProperty(TelevisionBlock.FRAME) ? st.getValue(TelevisionBlock.FRAME) : 0;
        String tex = switch (frame) {
            case 1 -> "block/television_case_white";
            case 2 -> "block/television_case_wood";
            case 3 -> "block/television_case_retro";
            default -> "block/television_case";
        };
        net.minecraft.client.renderer.texture.TextureAtlasSprite sp = net.minecraft.client.Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.fromNamespaceAndPath("skyblocks", tex));
        VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        float back = 1.0F;
        for (int i = 0; i < bends.size(); i++) {
            Matrix4f m = bends.get(i);
            org.joml.Matrix3f nm = new org.joml.Matrix3f(m).invert().transpose();
            // front: the frame above and below the glass
            face(v, nm, m, sp, 1, 0, FRONT - 0.001F, FRONT - 0.001F, 0, EDGE, 0, 0, -1, light);
            face(v, nm, m, sp, 1, 0, FRONT - 0.001F, FRONT - 0.001F, h - EDGE, h, 0, 0, -1, light);
            // back
            face(v, nm, m, sp, 0, 1, back, back, 0, h, 0, 0, 1, light);
            // top and bottom
            faceFlat(v, nm, m, sp, h, FRONT - 0.001F, back, 0, 1, 0, light);
            faceFlat(v, nm, m, sp, 0, back, FRONT - 0.001F, 0, -1, 0, light);
        }
    }

    /** An upright face, x0 to x1 across, at depth z0 (at x0) to z1 (at x1), y0 up to y1. */
    private static void face(VertexConsumer v, org.joml.Matrix3f nm, Matrix4f m, net.minecraft.client.renderer.texture.TextureAtlasSprite sp,
                             float x0, float x1, float z0, float z1, float y0, float y1, float nx, float ny, float nz, int light) {
        Vector3f n = nm.transform(new Vector3f(nx, ny, nz)).normalize();
        float u0 = sp.getU0(), u1 = sp.getU1(), va = sp.getV(1 - y0 / Math.max(1, y1)), vb = sp.getV0();
        vert(v, m, x0, y0, z0, u0, sp.getV1(), n, light);
        vert(v, m, x1, y0, z1, u1, sp.getV1(), n, light);
        vert(v, m, x1, y1, z1, u1, vb, n, light);
        vert(v, m, x0, y1, z0, u0, vb, n, light);
    }

    /** A level face at height y, from depth za to zb, across the piece. */
    private static void faceFlat(VertexConsumer v, org.joml.Matrix3f nm, Matrix4f m, net.minecraft.client.renderer.texture.TextureAtlasSprite sp,
                                 float y, float za, float zb, float nx, float ny, float nz, int light) {
        Vector3f n = nm.transform(new Vector3f(nx, ny, nz)).normalize();
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV(2.0F / 16);
        vert(v, m, 1, y, za, u0, v0, n, light);
        vert(v, m, 0, y, za, u1, v0, n, light);
        vert(v, m, 0, y, zb, u1, v1, n, light);
        vert(v, m, 1, y, zb, u0, v1, n, light);
    }

    private static void vert(VertexConsumer v, Matrix4f m, float x, float y, float z, float u, float vv, Vector3f n, int light) {
        v.addVertex(m, x, y, z).setColor(0xFFFFFFFF).setUv(u, vv).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(n.x, n.y, n.z);
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
    private void flat(PoseStack poseStack, MultiBufferSource buffers, TelevisionBlockEntity be, VideoPlayer player, float w, int h, int light, boolean glassToo) {
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
