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

    /** A glowing dot facing the camera (added on: brighter where they overlap). */
    static void sprite(VertexConsumer v, Matrix4f m, Vector3f right, Vector3f up, float x, float y, float z, float size, float r, float g, float b, float a) {
        float rx = right.x * size, ry = right.y * size, rz = right.z * size, ux = up.x * size, uy = up.y * size, uz = up.z * size;
        v.addVertex(m, x - rx - ux, y - ry - uy, z - rz - uz).setColor(r, g, b, a);
        v.addVertex(m, x + rx - ux, y + ry - uy, z + rz - uz).setColor(r, g, b, a);
        v.addVertex(m, x + rx + ux, y + ry + uy, z + rz + uz).setColor(r, g, b, a);
        v.addVertex(m, x - rx + ux, y - ry + uy, z - rz + uz).setColor(r, g, b, a);
    }

    /** A soft glow: a few dots, one in another (a bright middle, fading out). */
    static void glow(VertexConsumer v, Matrix4f m, Vector3f right, Vector3f up, float x, float y, float z, float size, float r, float g, float b, float a) {
        sprite(v, m, right, up, x, y, z, size, r, g, b, a * 0.18F);
        sprite(v, m, right, up, x, y, z, size * 0.5F, r, g, b, a * 0.35F);
        sprite(v, m, right, up, x, y, z, size * 0.22F, Math.min(1, r + 0.4F), Math.min(1, g + 0.4F), Math.min(1, b + 0.4F), a * 0.9F);
    }

    static Vector3f[] camera() {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        return new Vector3f[] {cam.rotation().transform(new Vector3f(1, 0, 0)), cam.rotation().transform(new Vector3f(0, 1, 0))};
    }

    // ---- the particle accelerator ----

    public static class Accelerator implements BlockEntityRenderer<ParticleAcceleratorBlockEntity> {
        public Accelerator(BlockEntityRendererProvider.Context context) {}

        /** Where a point {@code p} blocks round the ring is (its blocks' middles, at the beam's height). */
        private static Vector3f at(float p, int r) {
            float per = 8.0F * r;
            p = ((p % per) + per) % per;
            int side = Math.min(3, (int) (p / (2 * r)));
            float off = p - side * 2 * r;
            float x, z;
            switch (side) {
                case 0 -> { x = -r + off; z = -r; }
                case 1 -> { x = r; z = -r + off; }
                case 2 -> { x = r - off; z = r; }
                default -> { x = -r; z = r - off; }
            }
            return new Vector3f(0.5F + x, 0.55F, 0.5F + z);
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
            float per = 8.0F * r;
            be.clientTrack = (be.clientTrack + dt * (1.0F + 8.0F * s + 50.0F * s * s)) % per;
            Vector3f[] cam = camera();
            VertexConsumer v = buffers.getBuffer(RenderType.lightning());
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
            int segs = 20;
            float rIn = 0.12F, rOut = 1.15F, yIn = 0.22F, yOut = 0.55F;
            for (int i = 0; i < segs; i++) {
                float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
                float[][] q = {{Mth.cos(a0) * rIn, yIn, Mth.sin(a0) * rIn}, {Mth.cos(a1) * rIn, yIn, Mth.sin(a1) * rIn},
                        {Mth.cos(a1) * rOut, yOut, Mth.sin(a1) * rOut}, {Mth.cos(a0) * rOut, yOut, Mth.sin(a0) * rOut}};
                float[] uv = {0.45F, 0.55F, 0.55F, 1.0F};
                for (int k = 0; k < 4; k++) {
                    float shade = k < 2 ? 0.7F : 1.0F;
                    int c = (int) (255 * shade);
                    metal.addVertex(p, q[k][0], q[k][1], q[k][2]).setColor(c, c, c, 255).setUv(uv[k] * (k == 1 || k == 2 ? 1 : 0.2F), k < 2 ? 0.1F : 0.9F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(dishLight).setNormal(p, 0, 1, 0);
                }
            }
            // its feed arm over the dish
            for (int k = 0; k < 3; k++) {
                float a = k * Mth.TWO_PI / 3;
                arm(metal, p, Mth.cos(a) * 0.95F, yOut + 0.02F, Mth.sin(a) * 0.95F, 0, 0.95F, 0, dishLight);
            }
            pose.popPose();
            // its emitter: a glow over the dish in its weather's colour; a beam into the sky while turning it
            VertexConsumer v = buffers.getBuffer(RenderType.lightning());
            Matrix4f m = pose.last().pose();
            Vector3f[] cam = camera();
            float[] c = COLOURS[mode];
            float k = mode == 0 ? 0.25F : holding ? 0.9F + 0.1F * Mth.sin(t * 3) : 0.55F + 0.4F * Mth.sin(t * 9);
            float ey = y0 + 0.97F;
            glow(v, m, cam[0], cam[1], 0.5F, ey, 0.5F, 0.9F + (holding ? 0.4F : 0), c[0], c[1], c[2], k);
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
            // a strike (or the weather turned): a flash on the dish
            float age = (System.currentTimeMillis() - be.clientStruckAt) / 1000.0F;
            if (be.clientStruckAt > 0 && age < 1.0F) {
                glow(v, m, cam[0], cam[1], 0.5F, ey, 0.5F, 3.0F + 6.0F * age, 0.85F, 0.85F, 1.0F, 1.0F - age);
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

        private static void box(VertexConsumer v, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
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
