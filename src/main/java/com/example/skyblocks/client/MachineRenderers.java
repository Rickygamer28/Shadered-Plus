package com.example.skyblocks.client;

import com.example.skyblocks.ParticleAcceleratorBlockEntity;
import com.example.skyblocks.StarForgeBlockEntity;
import com.example.skyblocks.WeatherMachineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The new machines' renderers: the particle accelerator's beams, the weather machine's dish, the star forge's star. */
public final class MachineRenderers {
    private MachineRenderers() {}

    /** A soft round glow (its picture: bright in the middle, fading to its edge), added on. */
    static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "textures/misc/glow_dot.png");

    static VertexConsumer glows(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.eyes(GLOW));
    }

    /** A glowing dot facing the camera (added on: brighter where they overlap; {@code a} dims it). */
    static void sprite(VertexConsumer v, Matrix4f m, Vector3f right, Vector3f up, float x, float y, float z, float size, float r, float g, float b, float a) {
        float rx = right.x * size, ry = right.y * size, rz = right.z * size, ux = up.x * size, uy = up.y * size, uz = up.z * size;
        float cr = r * a, cg = g * a, cb = b * a;
        corner(v, m, x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, cr, cg, cb);
        corner(v, m, x + rx - ux, y + ry - uy, z + rz - uz, 1, 1, cr, cg, cb);
        corner(v, m, x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, cr, cg, cb);
        corner(v, m, x - rx + ux, y - ry + uy, z - rz + uz, 0, 0, cr, cg, cb);
    }

    private static void corner(VertexConsumer v, Matrix4f m, float x, float y, float z, float u, float w, float r, float g, float b) {
        v.addVertex(m, x, y, z).setColor(Math.min(1, r), Math.min(1, g), Math.min(1, b), 1.0F).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0).setNormal(0, 1, 0);
    }

    /** A soft glow: a few dots, one in another (a bright middle, fading out). */
    static void glow(VertexConsumer v, Matrix4f m, Vector3f right, Vector3f up, float x, float y, float z, float size, float r, float g, float b, float a) {
        sprite(v, m, right, up, x, y, z, size, r, g, b, a * 0.55F);
        sprite(v, m, right, up, x, y, z, size * 0.35F, Math.min(1, r + 0.5F), Math.min(1, g + 0.5F), Math.min(1, b + 0.5F), a);
    }

    static Vector3f[] camera() {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        return new Vector3f[] {cam.rotation().transform(new Vector3f(1, 0, 0)), cam.rotation().transform(new Vector3f(0, 1, 0))};
    }

    // ---- the particle accelerator ----

    public static class Accelerator implements BlockEntityRenderer<ParticleAcceleratorBlockEntity> {
        public Accelerator(BlockEntityRendererProvider.Context context) {}

        /** Where a point {@code p} blocks round the ring is (round its circle, through its blocks, at the beam's height). */
        private static Vector3f at(float p, int r) {
            float a = p / r;
            return new Vector3f(0.5F + Mth.cos(a) * r, 0.55F, 0.5F + Mth.sin(a) * r);
        }

        @Override
        public void render(ParticleAcceleratorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            int size = be.size();
            if (size == 0) {
                return;
            }
            int r = ParticleAcceleratorBlockEntity.RADIUS[size];
            long now = System.currentTimeMillis();
            float dt = be.clientAt == 0 ? 0.0F : Math.min(0.1F, (now - be.clientAt) / 1000.0F);
            be.clientAt = now;
            be.clientSpeed += (be.speed() - be.clientSpeed) * Math.min(1.0F, dt * 4.0F);
            float s = Mth.clamp(be.clientSpeed, 0.0F, 1.0F);
            float per = Mth.TWO_PI * r;
            be.clientTrack = (be.clientTrack + dt * (1.0F + 8.0F * s + 50.0F * s * s)) % per;
            tube(be, pose, buffers, r, s);
            Vector3f[] cam = camera();
            VertexConsumer v = glows(buffers);
            Matrix4f m = pose.last().pose();
            float t = (float) LavaLampRenderer.seconds();
            // the collider's glow over it: brighter as the beams speed up
            glow(v, m, cam[0], cam[1], 0.5F, 1.15F, 0.5F, 0.5F + 0.9F * s, 0.4F, 0.95F, 1.0F, 0.35F + 0.65F * s * (0.85F + 0.15F * Mth.sin(t * 20)));
            if (s > 0.01F) {
                // two beams, round the ring opposite ways: a bright head, a trail behind it - longer the faster
                beam(v, m, cam, be.clientTrack, r, s, 1, 0.35F, 0.95F, 1.0F);
                beam(v, m, cam, per - be.clientTrack + r, r, s, -1, 1.0F, 0.4F, 0.9F);
            }
            // a collision: a flash in the middle, beams of light from the ring in to it; a misfire: a red flash
            float age = (now - be.flashAt) / 1000.0F;
            if (be.flashAt > 0 && age < 0.9F) {
                float k = 1.0F - age / 0.9F;
                boolean hit = be.flashKind == 1;
                float cr = hit ? 0.85F : 1.0F, cg = hit ? 0.95F : 0.25F, cb = hit ? 1.0F : 0.15F;
                glow(v, m, cam[0], cam[1], 0.5F, 1.3F, 0.5F, 1.5F + 5.0F * (1.0F - k), cr, cg, cb, k);
                if (hit) {
                    for (int i = 0; i < 4; i++) {
                        float a = i * Mth.HALF_PI;
                        float sx = 0.5F + Mth.cos(a) * r, sz = 0.5F + Mth.sin(a) * r;
                        for (int j = 0; j <= 12; j++) {
                            float f = j / 12.0F;
                            glow(v, m, cam[0], cam[1], Mth.lerp(f, sx, 0.5F), Mth.lerp(f, 0.55F, 1.3F), Mth.lerp(f, sz, 0.5F), 0.5F, cr, cg, cb, k * 0.8F);
                        }
                    }
                }
            }
        }

        private static final ResourceLocation METAL = ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID,
                "textures/block/accelerator_frame_edge.png");
        private static final float BEAM_Y = 0.55F, TUBE = 0.34F;

        /**
         * The ring, formed: one round tube (its frames' own models hidden - but for the four ports, due north, east, south
         * and west): a metal channel open along its top, the beams racing round in it, bands round it every so often,
         * posts holding it up, a glowing strip along its floor (brighter as the beams speed up).
         */
        private static void tube(ParticleAcceleratorBlockEntity be, PoseStack pose, MultiBufferSource buffers, int r, float s) {
            var level = be.getLevel();
            if (level == null) {
                return;
            }
            VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
            PoseStack.Pose p = pose.last();
            int segs = Math.max(48, 16 * r);
            int bandEvery = Math.max(2, segs / (4 * r));
            net.minecraft.core.BlockPos base = be.getBlockPos();
            int[] light = new int[segs + 1];
            for (int i = 0; i <= segs; i++) {
                float a = i * Mth.TWO_PI / segs;
                light[i] = LevelRenderer.getLightColor(level, base.offset(Math.round(Mth.cos(a) * r), 0, Math.round(Mth.sin(a) * r)));
            }
            // the channel: round from its inner top edge, down under, up to its outer top edge (open along the top)
            int steps = 10;
            float len = Mth.TWO_PI * r / segs; // (its picture's width to a block along it: not squeezed into each piece)
            float phiFrom = Mth.PI * 0.62F, phiTo = Mth.PI * 2.38F;
            for (int i = 0; i < segs; i++) {
                float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
                for (int j = 0; j < steps; j++) {
                    float f0 = Mth.lerp(j / (float) steps, phiFrom, phiTo), f1 = Mth.lerp((j + 1) / (float) steps, phiFrom, phiTo);
                    ring(v, p, r, TUBE, a0, a1, f0, f1, light[i], light[i + 1], 0xFFC8D4DC, j / (float) steps, (j + 1) / (float) steps, len);
                }
                // a band round it, now and then: the whole way round, a little proud
                if (i % bandEvery == 0) {
                    float w = 0.06F / r;
                    for (int j = 0; j < 12; j++) {
                        float f0 = j * Mth.TWO_PI / 12, f1 = (j + 1) * Mth.TWO_PI / 12;
                        ring(v, p, r, TUBE + 0.04F, a0 - w, a0 + w, f0, f1, light[i], light[i], 0xFF8A98A8, 0, 1, 0.1F);
                    }
                    // and every other one, a post down to the ground
                    if ((i / bandEvery) % 2 == 0) {
                        float cx = 0.5F + Mth.cos(a0) * r, cz = 0.5F + Mth.sin(a0) * r, k = 0.07F;
                        Weather.box(v, p, cx - k, 0.0F, cz - k, cx + k, BEAM_Y - TUBE + 0.02F, cz + k, light[i]);
                    }
                }
            }
            // the glowing strip along its floor
            VertexConsumer g = glows(buffers);
            Matrix4f m = p.pose();
            float bright = 0.25F + 0.75F * s, y = BEAM_Y - TUBE + 0.05F, w = 0.1F;
            for (int i = 0; i < segs; i++) {
                float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
                float[][] q = {{0.5F + Mth.cos(a0) * (r - w), y, 0.5F + Mth.sin(a0) * (r - w)}, {0.5F + Mth.cos(a1) * (r - w), y, 0.5F + Mth.sin(a1) * (r - w)},
                        {0.5F + Mth.cos(a1) * (r + w), y, 0.5F + Mth.sin(a1) * (r + w)}, {0.5F + Mth.cos(a0) * (r + w), y, 0.5F + Mth.sin(a0) * (r + w)}};
                float[][] uv = {{0.5F, 0.0F}, {0.5F, 0.0F}, {0.5F, 1.0F}, {0.5F, 1.0F}};
                for (int k = 0; k < 4; k++) {
                    g.addVertex(m, q[k][0], q[k][1], q[k][2]).setColor(0.25F * bright, 0.85F * bright, 0.95F * bright, 1.0F).setUv(uv[k][0], uv[k][1])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
                }
            }
        }

        /** A piece of a torus round the ring: between angles a0, a1 round the ring, f0, f1 round the tube (0 outward, up, in, down). */
        private static void ring(VertexConsumer v, PoseStack.Pose p, int r, float tube, float a0, float a1, float f0, float f1, int l0, int l1, int argb,
                                 float v0, float v1, float u1) {
            float[][] pts = new float[4][];
            float[][] nrm = new float[4][];
            float[] as = {a0, a1, a1, a0}, fs = {f0, f0, f1, f1};
            for (int k = 0; k < 4; k++) {
                float ca = Mth.cos(as[k]), sa = Mth.sin(as[k]), cf = Mth.cos(fs[k]), sf = Mth.sin(fs[k]);
                nrm[k] = new float[] {cf * ca, sf, cf * sa};
                pts[k] = new float[] {0.5F + ca * (r + tube * cf), BEAM_Y + tube * sf, 0.5F + sa * (r + tube * cf)};
            }
            float[] us = {0, u1, u1, 0}, vs = {v0, v0, v1, v1};
            int[] ls = {l0, l1, l1, l0};
            for (int k = 0; k < 4; k++) {
                v.addVertex(p, pts[k][0], pts[k][1], pts[k][2]).setColor(argb).setUv(us[k], vs[k]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(ls[k])
                        .setNormal(p, nrm[k][0], nrm[k][1], nrm[k][2]);
            }
        }

        private static void beam(VertexConsumer v, Matrix4f m, Vector3f[] cam, float head, int r, float s, int way, float cr, float cg, float cb) {
            int n = 40;
            float spacing = 0.08F + 0.3F * s;
            for (int k = n; k >= 0; k--) {
                float f = k / (float) n;
                Vector3f p = at(head - way * k * spacing, r);
                float size = 0.07F + 0.3F * (1.0F - f);
                float a = (float) Math.pow(1.0F - f, 1.5) * (0.35F + 0.65F * s);
                sprite(v, m, cam[0], cam[1], p.x, p.y, p.z, size, cr, cg, cb, a * 0.5F);
            }
            Vector3f h = at(head, r);
            glow(v, m, cam[0], cam[1], h.x, h.y, h.z, 0.9F + 0.6F * s, cr, cg, cb, 0.6F + 0.4F * s);
        }

        @Override
        public AABB getRenderBoundingBox(ParticleAcceleratorBlockEntity be) {
            int r = ParticleAcceleratorBlockEntity.RADIUS[Math.max(0, Math.min(3, be.size()))] + 2;
            return new AABB(be.getBlockPos()).inflate(r, 4, r);
        }

        @Override
        public boolean shouldRenderOffScreen(ParticleAcceleratorBlockEntity be) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 96;
        }
    }

    // ---- the weather machine ----

    public static class Weather implements BlockEntityRenderer<WeatherMachineBlockEntity> {
        private static final ResourceLocation METAL = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
        private static final float[][] COLOURS = {{0.6F, 0.65F, 0.75F}, {1.0F, 0.88F, 0.45F}, {0.45F, 0.72F, 1.0F}, {0.78F, 0.55F, 1.0F}};

        public Weather(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(WeatherMachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (be.size() == 0 || be.getLevel() == null) {
                return;
            }
            float t = (float) LavaLampRenderer.seconds();
            int mode = be.mode();
            boolean holding = be.holding(), turning = mode != 0 && !holding;
            float y0 = be.masts() + 1.0F;
            int dishLight = LevelRenderer.getLightColor(be.getLevel(), be.dish());
            // its dish: a shallow metal bowl on a post, turning (fast while it turns the weather)
            pose.pushPose();
            pose.translate(0.5F, y0, 0.5F);
            float spin = t * (turning ? 220.0F : holding ? 45.0F : 12.0F);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
            PoseStack.Pose p = pose.last();
            post(metal, p, dishLight);
            // the bowl: curved (deeper to its middle), a lip round its rim
            int segs = 28;
            float[] rr = {0.14F, 0.42F, 0.72F, 1.0F, 1.22F};
            for (int i = 0; i < segs; i++) {
                float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
                for (int k = 0; k < rr.length - 1; k++) {
                    float r0 = rr[k], r1 = rr[k + 1];
                    float h0 = 0.24F + 0.36F * (r0 / 1.22F) * (r0 / 1.22F), h1 = 0.24F + 0.36F * (r1 / 1.22F) * (r1 / 1.22F);
                    dishQuad(metal, p, a0, a1, r0, r1, h0, h1, dishLight, k % 2 == 0 ? 0xFFE6EAF0 : 0xFFD6DCE4);
                }
                dishQuad(metal, p, a0, a1, 1.22F, 1.28F, 0.6F, 0.66F, dishLight, 0xFF9AA4B4);
            }
            // its feed: three arms up from the bowl to a horn over its middle
            for (int k = 0; k < 3; k++) {
                float a = k * Mth.TWO_PI / 3;
                arm(metal, p, Mth.cos(a) * 1.0F, 0.6F, Mth.sin(a) * 1.0F, 0, 0.95F, 0, dishLight);
            }
            box(metal, p, -0.07F, 0.88F, -0.07F, 0.07F, 1.05F, 0.07F, dishLight);
            pose.popPose();
            // its emitter: a glow over the dish in its weather's colour; a beam into the sky while turning it
            Matrix4f m = pose.last().pose();
            Vector3f[] cam = camera();
            float[] c = COLOURS[mode];
            float k = mode == 0 ? 0.25F : holding ? 0.9F + 0.1F * Mth.sin(t * 3) : 0.55F + 0.4F * Mth.sin(t * 9);
            float ey = y0 + 1.12F;
            VertexConsumer gl = glows(buffers);
            glow(gl, m, cam[0], cam[1], 0.5F, ey, 0.5F, 0.9F + (holding ? 0.4F : 0), c[0], c[1], c[2], k);
            // a strike (or the weather turned): a flash on the dish
            float age = (System.currentTimeMillis() - be.clientStruckAt) / 1000.0F;
            if (be.clientStruckAt > 0 && age < 1.0F) {
                glow(gl, m, cam[0], cam[1], 0.5F, ey, 0.5F, 3.0F + 6.0F * age, 0.85F, 0.85F, 1.0F, 1.0F - age);
            }
            VertexConsumer v = buffers.getBuffer(RenderType.lightning());
            if (turning) {
                float charge = be.charge();
                float h = 48.0F;
                for (int q = 0; q < 2; q++) {
                    float dx = q == 0 ? 1 : 0, dz = q == 0 ? 0 : 1;
                    float w0 = 0.06F, w1 = 0.35F;
                    float a = 0.25F + 0.55F * charge;
                    v.addVertex(m, 0.5F - dx * w0, ey, 0.5F - dz * w0).setColor(c[0], c[1], c[2], a);
                    v.addVertex(m, 0.5F + dx * w0, ey, 0.5F + dz * w0).setColor(c[0], c[1], c[2], a);
                    v.addVertex(m, 0.5F + dx * w1, ey + h, 0.5F + dz * w1).setColor(c[0], c[1], c[2], 0.0F);
                    v.addVertex(m, 0.5F - dx * w1, ey + h, 0.5F - dz * w1).setColor(c[0], c[1], c[2], 0.0F);
                    v.addVertex(m, 0.5F - dx * w1, ey + h, 0.5F - dz * w1).setColor(c[0], c[1], c[2], 0.0F);
                    v.addVertex(m, 0.5F + dx * w1, ey + h, 0.5F + dz * w1).setColor(c[0], c[1], c[2], 0.0F);
                    v.addVertex(m, 0.5F + dx * w0, ey, 0.5F + dz * w0).setColor(c[0], c[1], c[2], a);
                    v.addVertex(m, 0.5F - dx * w0, ey, 0.5F - dz * w0).setColor(c[0], c[1], c[2], a);
                }
            }
            // clouds gathering round over it, swirling, while it works
            long now = System.currentTimeMillis();
            if (mode != 0 && now - be.clientCloudAt > (turning ? 60 : 250) && !Minecraft.getInstance().isPaused()) {
                be.clientCloudAt = now;
                var level = be.getLevel();
                var bp = be.getBlockPos();
                float a = level.random.nextFloat() * Mth.TWO_PI, rr = 1.5F + level.random.nextFloat() * 2.5F;
                level.addParticle(mode == 1 ? ParticleTypes.END_ROD : ParticleTypes.CLOUD, bp.getX() + 0.5 + Mth.cos(a) * rr,
                        bp.getY() + ey + 2.5 + level.random.nextFloat() * 2.0, bp.getZ() + 0.5 + Mth.sin(a) * rr,
                        -Mth.sin(a) * 0.12, 0.01, Mth.cos(a) * 0.12);
            }
        }

        /** A piece of the bowl: between angles a0, a1 round it, radii r0..r1 out, heights h0..h1. */
        private static void dishQuad(VertexConsumer v, PoseStack.Pose p, float a0, float a1, float r0, float r1, float h0, float h1, int light, int argb) {
            float[][] q = {{Mth.cos(a0) * r0, h0, Mth.sin(a0) * r0}, {Mth.cos(a1) * r0, h0, Mth.sin(a1) * r0},
                    {Mth.cos(a1) * r1, h1, Mth.sin(a1) * r1}, {Mth.cos(a0) * r1, h1, Mth.sin(a0) * r1}};
            float[][] uv = {{0.0F, 0.0F}, {0.25F, 0.0F}, {0.25F, 0.25F}, {0.0F, 0.25F}};
            for (int k = 0; k < 4; k++) {
                v.addVertex(p, q[k][0], q[k][1], q[k][2]).setColor(argb).setUv(uv[k][0], uv[k][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(p, 0, 1, 0);
            }
        }

        private static void post(VertexConsumer v, PoseStack.Pose p, int light) {
            float w = 0.07F;
            box(v, p, -w, -0.02F, -w, w, 0.3F, w, light);
            box(v, p, -0.14F, 0.2F, -0.14F, 0.14F, 0.26F, 0.14F, light);
        }

        private static void arm(VertexConsumer v, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
            int n = 6;
            for (int i = 0; i < n; i++) {
                float f = i / (float) n, g = (i + 1) / (float) n;
                float ax = Mth.lerp(f, x0, x1), ay = Mth.lerp(f, y0, y1), az = Mth.lerp(f, z0, z1);
                float w = 0.025F;
                box(v, p, ax - w, ay - w, az - w, ax + w, Mth.lerp(g, y0, y1) + w, az + w, light);
            }
            box(v, p, -0.06F, y1 - 0.06F, -0.06F, 0.06F, y1 + 0.06F, 0.06F, light);
        }

        static void box(VertexConsumer v, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
            float[][][] faces = {
                    {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {0, 1, 0}},
                    {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {0, -1, 0}},
                    {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {0, 0, -1}},
                    {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {0, 0, 1}},
                    {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {-1, 0, 0}},
                    {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {1, 0, 0}}};
            float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            for (float[][] f : faces) {
                for (int i = 0; i < 4; i++) {
                    v.addVertex(p, f[i][0], f[i][1], f[i][2]).setColor(0xFFB8BCC8).setUv(uv[i][0] * 0.3F, uv[i][1] * 0.3F).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light).setNormal(p, f[4][0], f[4][1], f[4][2]);
                }
            }
        }

        @Override
        public AABB getRenderBoundingBox(WeatherMachineBlockEntity be) {
            return new AABB(be.getBlockPos()).inflate(2.0).expandTowards(0, be.masts() + 50, 0);
        }

        @Override
        public boolean shouldRenderOffScreen(WeatherMachineBlockEntity be) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 128;
        }
    }

    // ---- the star forge ----

    public static class Forge implements BlockEntityRenderer<StarForgeBlockEntity> {
        private static final int[] LIGHT = {0xFF5030, 0xFFB040, 0x5A9CFF, 0xF0F0FF};

        public Forge(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(StarForgeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            int size = be.size();
            if (size == 0) {
                return;
            }
            int chamber = size >= 2 ? 7 : 5;
            float half = (chamber - 1) / 2.0F - 0.55F;
            // its star: big and bright burning (swelling as it works), a dim ember with no fuel
            float target = be.lit() ? 0.55F + 0.3F * be.fuelLeft() + (be.progress() > 0 ? 0.1F : 0.0F) : 0.04F;
            be.clientFull += (target - be.clientFull) * 0.05F;
            float full = Mth.clamp(be.clientFull, 0.0F, 1.0F);
            int colour = be.colour();
            if (be.lit()) {
                ShaderLightRenderer.add(pose, 0.5F, 0.5F, 0.5F, chamber + 1.0F + 3.0F * full, LIGHT[colour]);
            }
            ItemStreams.render(be.getBlockPos(), pose, buffers); // (what it's working, flying in; what it made, out)
            PowerCoreRenderer.drawBox(pose, buffers, half, 4 + colour, Math.round(full * 255), 255);
        }

        @Override
        public AABB getRenderBoundingBox(StarForgeBlockEntity be) {
            return new AABB(be.getBlockPos()).inflate(4.5);
        }

        @Override
        public boolean shouldRenderOffScreen(StarForgeBlockEntity be) {
            return true;
        }
    }
}
