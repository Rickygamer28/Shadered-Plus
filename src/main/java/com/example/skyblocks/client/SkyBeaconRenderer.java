package com.example.skyblocks.client;

import com.example.skyblocks.SkyHolderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Sky Beacon: a glowing core of its sky in a glass case, and from it a beam of the sky straight up into the
 * heavens (stopped, like a beacon's, by a solid block), turning slowly in a soft glow.
 */
public class SkyBeaconRenderer implements BlockEntityRenderer<SkyHolderBlockEntity> {
    private final float[] quads = new float[(6 + 4) * 40];

    public SkyBeaconRenderer(BlockEntityRendererProvider.Context context) {}

    /** How high the beam goes (blocks above the beacon), looked at again every two seconds. */
    private static int beamHeight(SkyHolderBlockEntity be, Level level) {
        long now = level.getGameTime();
        if (be.beamTop == Integer.MIN_VALUE || now - be.beamChecked > 40 || now < be.beamChecked) {
            BlockPos pos = be.getBlockPos();
            int top = level.getMaxBuildHeight();
            BlockPos.MutableBlockPos p = pos.mutable();
            for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++) {
                p.setY(y);
                var state = level.getBlockState(p);
                if (!state.isAir() && state.isSolidRender(level, p)) {
                    top = y;
                    break;
                }
            }
            be.beamTop = top;
            be.beamChecked = now;
        }
        return be.beamTop - be.getBlockPos().getY() - 1;
    }

    @Override
    public void render(SkyHolderBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        SkyblockType type = be.sky();
        float t = (level.getGameTime() % 240000L) + partialTick;
        int height = beamHeight(be, level);
        ps.pushPose();
        ps.translate(0.5F, 0.0F, 0.5F);
        int n = 0;
        // ---- the core: a cube of the sky, turning ----
        ps.pushPose();
        ps.translate(0.0F, 0.5F + 2.0F / 16.0F, 0.0F);
        ps.mulPose(Axis.YP.rotationDegrees(t * 1.2F));
        n = box(quads, n, ps.last().pose(), 0.21F);
        ps.popPose();
        // ---- the beam: a slowly turning square column of the sky ----
        float r = 0.16F;
        if (height > 0) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 0.8F));
            Matrix4f m = ps.last().pose();
            float y0 = 1.0F, y1 = 1.0F + height;
            float[][] c = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
            for (int i = 0; i < 4; i++) {
                float[] a = c[i], b = c[(i + 1) % 4];
                n = SkyQuads.quad(quads, n, at(m, a[0], y0, a[1]), at(m, a[0], y1, a[1]), at(m, b[0], y1, b[1]), at(m, b[0], y0, b[1]));
            }
            ps.popPose();
        }
        if (type != null) {
            SkyQuads.draw(type, quads, n, buffers);
        }
        // ---- its glow: two fainter columns round it, and a halo where it leaves the beacon ----
        if (height > 0) {
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
            float y1 = 1.0F + height;
            for (int layer = 0; layer < 2; layer++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(-t * (0.5F + layer * 0.4F) + layer * 45.0F));
                Matrix4f m = ps.last().pose();
                float rr = r + 0.07F + layer * 0.08F;
                int alpha = layer == 0 ? 46 : 22;
                float[][] c = {{-rr, -rr}, {rr, -rr}, {rr, rr}, {-rr, rr}};
                for (int i = 0; i < 4; i++) {
                    float[] a = c[i], b = c[(i + 1) % 4];
                    for (int pass = 0; pass < 2; pass++) {
                        float[] p = pass == 0 ? a : b, q = pass == 0 ? b : a;
                        put(glow, m, p[0], 1.0F, p[1], alpha);
                        put(glow, m, p[0], y1, p[1], alpha / 3);
                        put(glow, m, q[0], y1, q[1], alpha / 3);
                        put(glow, m, q[0], 1.0F, q[1], alpha);
                    }
                }
                ps.popPose();
            }
            Matrix4f m = ps.last().pose();
            float pulse = 0.5F + 0.12F * Mth.sin(t * 0.1F);
            for (int i = 0; i < 12; i++) {
                float a0 = i * Mth.TWO_PI / 12, a1 = (i + 1) * Mth.TWO_PI / 12;
                for (int pass = 0; pass < 2; pass++) {
                    float p = pass == 0 ? a0 : a1, q = pass == 0 ? a1 : a0;
                    glow.addVertex(m, 0.0F, 1.01F, 0.0F).setColor(255, 255, 255, 90);
                    glow.addVertex(m, Mth.cos(p) * pulse, 1.01F, Mth.sin(p) * pulse).setColor(200, 190, 255, 0);
                    glow.addVertex(m, Mth.cos(q) * pulse, 1.01F, Mth.sin(q) * pulse).setColor(200, 190, 255, 0);
                    glow.addVertex(m, 0.0F, 1.01F, 0.0F).setColor(255, 255, 255, 90);
                }
            }
        }
        ps.popPose();
    }

    /** A cube (half-size h, about the pose's origin), sky-filled. */
    private static int box(float[] out, int n, Matrix4f m, float h) {
        float[][] v = new float[8][];
        for (int i = 0; i < 8; i++) {
            v[i] = at(m, (i & 1) == 0 ? -h : h, (i & 2) == 0 ? -h : h, (i & 4) == 0 ? -h : h);
        }
        int[][] sides = {{0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}};
        for (int[] s : sides) {
            n = SkyQuads.quad(out, n, v[s[0]], v[s[1]], v[s[2]], v[s[3]]);
        }
        return n;
    }

    private static void put(VertexConsumer c, Matrix4f m, float x, float y, float z, int a) {
        c.addVertex(m, x, y, z).setColor(225, 220, 255, a);
    }

    private static float[] at(Matrix4f m, float x, float y, float z) {
        Vector3f v = m.transformPosition(x, y, z, new Vector3f());
        return new float[] {v.x, v.y, v.z};
    }

    @Override
    public boolean shouldRenderOffScreen(SkyHolderBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SkyHolderBlockEntity be) {
        return net.minecraft.world.phys.AABB.INFINITE;
    }
}
