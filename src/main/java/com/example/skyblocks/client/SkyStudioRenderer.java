package com.example.skyblocks.client;

import com.example.skyblocks.SkyStudioBlock;
import com.example.skyblocks.SkyStudioBlockEntity;
import com.example.skyblocks.StudioSkies;
import com.example.skyblocks.StudioSkiesStore;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Sky Studio's console top: a tilted panel whose screen shows, live, the studio sky this studio last worked on
 * (the studio preview shader, slowly looking round), and a little cube of that sky floating and turning over it.
 */
public class SkyStudioRenderer implements BlockEntityRenderer<SkyStudioBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("skyblocks", "textures/entity/white.png");
    private static float[] screenParams;
    private static float screenTime, screenYaw;

    abstract static class Types extends RenderType {
        static final RenderType SCREEN = create("skyblocks_studio_screen", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(ShaderSky::studioPreview))
                        .setTexturingState(new TexturingStateShard("skyblocks_studio_screen", SkyStudioRenderer::setUp, () -> {}))
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private static void setUp() {
        var shader = ShaderSky.studioPreview();
        if (shader != null && screenParams != null) {
            StudioSkiesClient.apply(shader, screenParams);
            shader.safeGetUniform("Time").set(screenTime);
            shader.safeGetUniform("View").set(screenYaw, 0.22F);
        }
    }

    public SkyStudioRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SkyStudioBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        int slot = be.slot();
        float t = (be.getLevel().getGameTime() % 240000L) + partialTick;
        float facing = be.getBlockState().hasProperty(SkyStudioBlock.FACING) ? be.getBlockState().getValue(SkyStudioBlock.FACING).toYRot() : 180.0F;
        ps.pushPose();
        ps.translate(0.5F, 0.0F, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - facing));
        // ---- the tilted panel: its frame, then the screen ----
        ps.pushPose();
        ps.translate(0.0F, 11.0F / 16.0F, 0.0F);
        ps.mulPose(Axis.XP.rotationDegrees(-22.5F)); // (as the model's bezel: block/sky_studio.json)
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutout(WHITE));
        // (four little lights along the bezel's front edge)
        for (int i = 0; i < 4; i++) {
            float x = -4.5F + i * 3.0F;
            int c = (int) ((t * 0.05F + i) % 4) == 0 ? 0x7EE8D6 : 0x2E8F7F;
            solidBox(solid, ps, x - 0.5F, 0.8F, -5.4F, x + 0.5F, 1.05F, -5.0F, c, LightTexture.FULL_BRIGHT);
        }
        if (ShaderSky.studioPreview() != null) {
            float[] p = StudioSkiesStore.PARAMS[slot];
            screenParams = p;
            screenTime = t;
            screenYaw = t * 0.0035F + be.getBlockPos().hashCode() * 0.1F;
            MultiBufferSource.BufferSource now = DrawNow.now(buffers);
            Matrix4f m = ps.last().pose();
            VertexConsumer c = now.getBuffer(Types.SCREEN);
            float y = 0.53F / 16.0F, x0 = -5.6F / 16.0F, x1 = 5.6F / 16.0F, z0 = -4.7F / 16.0F, z1 = 4.7F / 16.0F;
            // (seen from the front: the far edge the top of the picture, +x on the viewer's left)
            c.addVertex(m, x1, y, z1).setUv(0, 0);
            c.addVertex(m, x1, y, z0).setUv(0, 1);
            c.addVertex(m, x0, y, z0).setUv(1, 1);
            c.addVertex(m, x0, y, z1).setUv(1, 0);
            now.endBatch(Types.SCREEN);
        }
        ps.popPose();
        // ---- the sky orb: a little cube of the sky, floating and turning ----
        RenderType sky = SkyLiquidRenderer.skyType(StudioSkies.type(slot));
        if (sky != null) {
            ps.pushPose();
            ps.translate(0.0F, 1.2F + Mth.sin(t * 0.06F) * 0.04F, 0.18F);
            ps.mulPose(Axis.YP.rotationDegrees(t * 1.5F));
            ps.mulPose(Axis.XP.rotationDegrees(35.0F));
            ps.mulPose(Axis.ZP.rotationDegrees(45.0F));
            float[] d = skyBox(ps, 0.13F);
            var data = com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(StudioSkies.type(slot));
            if (com.example.skyblocks.client.iris.IrisIslandTarget.deferSky()) {
                SkyUsage.ensureFresh(data);
                com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(sky, v -> emitSky(v, d));
            } else {
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
                for (int i = 0; i < d.length; i += 5) {
                    minX = Math.min(minX, d[i]);
                    maxX = Math.max(maxX, d[i]);
                    minY = Math.min(minY, d[i + 1]);
                    maxY = Math.max(maxY, d[i + 1]);
                    minZ = Math.min(minZ, d[i + 2]);
                    maxZ = Math.max(maxZ, d[i + 2]);
                }
                SkyUsage.ensurePart(data, SkyUsage.rectOfBox(minX, minY, minZ, maxX, maxY, maxZ));
                MultiBufferSource.BufferSource now = DrawNow.now(buffers);
                SkyUsage.placeKnown = true;
                try {
                    emitSky(now.getBuffer(sky), d);
                    now.endBatch(sky);
                } finally {
                    SkyUsage.placeKnown = false;
                }
            }
            ps.popPose();
        }
        ps.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SkyStudioBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0, 0.6, 0);
    }

    // ---- boxes ----

    private static final int[][] SIDES = {{0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}};
    private static final float[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    /** A solid box (in 16ths of a block), one colour, lit. */
    private static void solidBox(VertexConsumer c, PoseStack ps, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
        Matrix4f m = ps.last().pose();
        Vector3f[] v = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            v[i] = m.transformPosition(((i & 1) == 0 ? x0 : x1) / 16.0F, ((i & 2) == 0 ? y0 : y1) / 16.0F, ((i & 4) == 0 ? z0 : z1) / 16.0F, new Vector3f());
        }
        Vector3f n = new Vector3f();
        for (int f = 0; f < 6; f++) {
            ps.last().normal().transform(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2], n);
            for (int k = 0; k < 4; k++) {
                Vector3f p = v[SIDES[f][k]];
                c.addVertex(p.x, p.y, p.z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255).setUv(0.5F, 0.5F)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
            }
        }
    }

    /** A cube of half-size r (in blocks), posed: x y z u v per vertex, both sides of each face. */
    private static float[] skyBox(PoseStack ps, float r) {
        Matrix4f m = ps.last().pose();
        Vector3f[] v = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            v[i] = m.transformPosition((i & 1) == 0 ? -r : r, (i & 2) == 0 ? -r : r, (i & 4) == 0 ? -r : r, new Vector3f());
        }
        float[] out = new float[6 * 8 * 5];
        int n = 0;
        for (int[] s : SIDES) {
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    int kk = pass == 0 ? k : 3 - k;
                    Vector3f p = v[s[kk]];
                    out[n++] = p.x;
                    out[n++] = p.y;
                    out[n++] = p.z;
                    out[n++] = kk == 1 || kk == 2 ? 1 : 0;
                    out[n++] = kk >= 2 ? 1 : 0;
                }
            }
        }
        return out;
    }

    private static void emitSky(VertexConsumer c, float[] d) {
        for (int i = 0; i < d.length; i += 5) {
            c.addVertex(d[i], d[i + 1], d[i + 2]).setColor(255, 255, 255, 255).setUv(d[i + 3], d[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
        }
    }
}
