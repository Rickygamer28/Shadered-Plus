package com.example.skyblocks.client;

import com.example.skyblocks.LightBridgeEmitterBlock;
import com.example.skyblocks.LightBridgeEmitterBlockEntity;
import com.example.skyblocks.LightBridgePath;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/** A light bridge, drawn by the emitter that keeps it along its path: a plate of light, a wall of it, or a tunnel. */
public class LightBridgeRenderer implements BlockEntityRenderer<LightBridgeEmitterBlockEntity> {
    static ShaderInstance shader;
    private static float time, length, dir, speed, kind, pattern, alpha;
    private static int colour;
    private static final float THICK = 2.0F / 16.0F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerLightBridgeShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "light_bridge"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    private static void setUp() {
        if (shader == null) {
            return;
        }
        shader.safeGetUniform("Time").set(time);
        shader.safeGetUniform("Length").set(length);
        shader.safeGetUniform("Rainbow").set(colour < 0 ? 1.0F : 0.0F);
        shader.safeGetUniform("Dir").set(dir);
        shader.safeGetUniform("Speed").set(speed);
        shader.safeGetUniform("Kind").set(kind);
        shader.safeGetUniform("Pattern").set(pattern);
        shader.safeGetUniform("Alpha").set(alpha);
        int c = Math.max(colour, 0);
        shader.safeGetUniform("Colour").set(((c >> 16) & 255) / 255.0F, ((c >> 8) & 255) / 255.0F, (c & 255) / 255.0F);
    }

    abstract static class Types extends RenderType {
        static final RenderType BRIDGE = create("skyblocks_light_bridge", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_light_bridge_settings", LightBridgeRenderer::setUp, () -> {}))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public LightBridgeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(LightBridgeEmitterBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        LightBridgePath path = be.path();
        if (shader == null || level == null || path == null || path.samples.size() < 2) {
            return;
        }
        time = ((level.getGameTime() % 240000L) + partialTick) / 20.0F;
        length = path.length + 1.0F;
        colour = be.colour();
        int mode = path.mode;
        dir = be.reverse() ? -1.0F : 1.0F;
        speed = mode == LightBridgePath.SPEED ? 3.0F : mode == LightBridgePath.TUNNEL ? 2.0F : 1.0F;
        kind = mode == LightBridgePath.WALL ? 1.0F : mode == LightBridgePath.TUNNEL ? 2.0F : 0.0F;
        pattern = be.pattern();
        alpha = new float[] {0.45F, 0.75F, 1.0F, 1.5F}[be.opacity()];
        float half = path.width / 2.0F;
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        VertexConsumer vc = source.getBuffer(Types.BRIDGE);
        Matrix4f m = poseStack.last().pose();
        BlockPos o = be.getBlockPos();
        java.util.List<LightBridgePath.Sample> ss = path.samples;
        for (int i = 0; i + 1 < ss.size(); i++) {
            LightBridgePath.Sample a = ss.get(i), b = ss.get(i + 1);
            float ax = (float) (a.x() - o.getX()), ay = (float) (a.y() - o.getY()), az = (float) (a.z() - o.getZ());
            float bx = (float) (b.x() - o.getX()), by = (float) (b.y() - o.getY()), bz = (float) (b.z() - o.getZ());
            // (sideways from each: its left)
            float alx = -a.dz(), alz = a.dx(), blx = -b.dz(), blz = b.dx();
            float sa = a.s() + 1.0F, sb = b.s() + 1.0F;
            if (mode == LightBridgePath.WALL) {
                float h = 3.0F, w = 0.06F;
                for (float side : new float[] {-w, w}) {
                    quad(vc, m, ax + alx * side, ay, az + alz * side, sa, 0, bx + blx * side, by, bz + blz * side, sb, 0,
                            bx + blx * side, by + h, bz + blz * side, sb, 1, ax + alx * side, ay + h, az + alz * side, sa, 1, 220);
                }
                quad(vc, m, ax - alx * w, ay + h, az - alz * w, sa, 0.95F, bx - blx * w, by + h, bz - blz * w, sb, 0.95F,
                        bx + blx * w, by + h, bz + blz * w, sb, 1, ax + alx * w, ay + h, az + alz * w, sa, 1, 255);
            } else if (mode == LightBridgePath.TUNNEL) {
                int n = 10 + 4 * path.width;
                float r = path.tunnelRadius(), cy = r - 0.2F;
                for (int k = 0; k < n; k++) {
                    double t0 = Math.PI * 2 * k / n, t1 = Math.PI * 2 * (k + 1) / n;
                    float c0 = (float) Math.cos(t0) * r, s0 = (float) Math.sin(t0) * r, c1 = (float) Math.cos(t1) * r, s1 = (float) Math.sin(t1) * r;
                    float u0 = (float) k / n, u1 = (float) (k + 1) / n;
                    int shade = 170 + (int) (60 * Math.sin(t0));
                    quad(vc, m, ax + alx * c0, ay + cy + s0, az + alz * c0, sa, u0, bx + blx * c0, by + cy + s0, bz + blz * c0, sb, u0,
                            bx + blx * c1, by + cy + s1, bz + blz * c1, sb, u1, ax + alx * c1, ay + cy + s1, az + alz * c1, sa, u1, shade);
                }
            } else {
                float w = half, t = THICK;
                // top, bottom, both edges
                quad(vc, m, ax + alx * w, ay + t, az + alz * w, sa, 0, bx + blx * w, by + t, bz + blz * w, sb, 0,
                        bx - blx * w, by + t, bz - blz * w, sb, 1, ax - alx * w, ay + t, az - alz * w, sa, 1, 255);
                quad(vc, m, ax + alx * w, ay + 0.002F, az + alz * w, sa, 0, bx + blx * w, by + 0.002F, bz + blz * w, sb, 0,
                        bx - blx * w, by + 0.002F, bz - blz * w, sb, 1, ax - alx * w, ay + 0.002F, az - alz * w, sa, 1, 150);
                for (float side : new float[] {w, -w}) {
                    float u = side > 0 ? 0 : 1;
                    quad(vc, m, ax + alx * side, ay, az + alz * side, sa, u, bx + blx * side, by, bz + blz * side, sb, u,
                            bx + blx * side, by + t, bz + blz * side, sb, u, ax + alx * side, ay + t, az + alz * side, sa, u, 200);
                }
                if (be.rails()) { // glowing rails along both edges, on little posts
                    for (float side : new float[] {w - 0.06F, -w + 0.06F}) {
                        float u = side > 0 ? 0 : 1, rt = 0.55F, rw = 0.05F;
                        for (float[] f2 : new float[][] {{-rw, rw, rt - 0.06F, rt}, {rw, -rw, rt - 0.06F, rt}}) {
                            float s0 = side + f2[0], s1 = side + f2[1];
                            quad(vc, m, ax + alx * s0, ay + f2[2], az + alz * s0, sa, u, bx + blx * s0, by + f2[2], bz + blz * s0, sb, u,
                                    bx + blx * s1, by + f2[3], bz + blz * s1, sb, u, ax + alx * s1, ay + f2[3], az + alz * s1, sa, u, 255);
                        }
                        quad(vc, m, ax + alx * side, ay + t, az + alz * side, sa, u, bx + blx * side, by + t, bz + blz * side, sb, u,
                                bx + blx * side, by + rt, bz + blz * side, sb, 0.5F, ax + alx * side, ay + rt, az + alz * side, sa, 0.5F, 120);
                    }
                }
            }
        }
        source.endBatch(Types.BRIDGE);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float u0, float v0, float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3, int shade) {
        vc.addVertex(m, x0, y0, z0).setUv(u0, v0).setColor(shade, shade, shade, 255);
        vc.addVertex(m, x1, y1, z1).setUv(u1, v1).setColor(shade, shade, shade, 255);
        vc.addVertex(m, x2, y2, z2).setUv(u2, v2).setColor(shade, shade, shade, 255);
        vc.addVertex(m, x3, y3, z3).setUv(u3, v3).setColor(shade, shade, shade, 255);
    }

    @Override
    public boolean shouldRenderOffScreen(LightBridgeEmitterBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(LightBridgeEmitterBlockEntity be) {
        LightBridgePath path = be.path();
        if (path == null || path.samples.isEmpty()) {
            return new AABB(be.getBlockPos());
        }
        double[] b = path.bounds();
        return new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
