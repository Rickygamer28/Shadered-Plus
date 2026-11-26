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
import net.minecraft.world.item.ItemStack;
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

    /** A flat quad of solid glow (the glow dot's bright middle), seen from both sides: p holds its 4 corners' x, y, z. */
    static void flat(VertexConsumer v, Matrix4f m, float[] p, float r, float g, float b) {
        for (int i = 0; i < 4; i++) {
            corner(v, m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], 0.5F, 0.5F, r, g, b);
        }
        for (int i = 3; i >= 0; i--) {
            corner(v, m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], 0.5F, 0.5F, r, g, b);
        }
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
            // cables along the floor from the detector out to its four junction boxes
            VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
            PoseStack.Pose mp = pose.last();
            float cw = 0.09F;
            Weather.box(metal, mp, 0.5F - cw, 0.0F, 1.0F, 0.5F + cw, 0.07F, r - 0.05F, light);
            Weather.box(metal, mp, 0.5F - cw, 0.0F, 1.0F - r + 0.05F, 0.5F + cw, 0.07F, 0.0F, light);
            Weather.box(metal, mp, 1.0F, 0.0F, 0.5F - cw, r - 0.05F, 0.07F, 0.5F + cw, light);
            Weather.box(metal, mp, 1.0F - r + 0.05F, 0.0F, 0.5F - cw, 0.0F, 0.07F, 0.5F + cw, light);
            Vector3f[] cam = camera();
            VertexConsumer v = glows(buffers);
            Matrix4f m = pose.last().pose();
            float t = (float) LavaLampRenderer.seconds();
            // the junction boxes' lights: green idle, cyan running, red just after a misfire; power pulsing out the cables
            boolean misfired = be.flashKind == 2 && now - be.flashAt < 3000;
            float lr = misfired ? 1.0F : 0.3F, lg = misfired ? 0.3F : s > 0.01F ? 0.9F : 1.0F, lb = misfired ? 0.25F : s > 0.01F ? 1.0F : 0.45F;
            float blink = misfired ? (Mth.sin(t * 12) > 0 ? 1.0F : 0.3F) : 0.85F + 0.15F * Mth.sin(t * 3);
            for (int q = 0; q < 4; q++) {
                int dx = q == 0 ? r : q == 2 ? -r : 0, dz = q == 1 ? r : q == 3 ? -r : 0;
                glow(v, m, cam[0], cam[1], 0.5F + dx, 1.0F, 0.5F + dz, 0.35F, lr, lg, lb, blink);
                // (round each junction box, over its flanges: a bar - how fast its beams go)
                float y0 = 13.25F / 16.0F, y1 = 13.85F / 16.0F, a0 = 0.2F, a1 = 0.8F, fill = a0 + (a1 - a0) * Math.min(1.0F, s);
                for (int f = 0; f < 4; f++) {
                    float pl = f % 2 == 0 ? 1.0F / 16.0F - 0.004F : 15.0F / 16.0F + 0.004F;
                    for (int part = 0; part < 2; part++) {
                        float b0 = part == 0 ? a0 : a0, b1 = part == 0 ? a1 : fill;
                        if (part == 1 && s < 0.01F) {
                            continue;
                        }
                        float k = part == 0 ? 0.12F : 0.9F, yy0 = part == 0 ? y0 - 0.01F : y0, yy1 = part == 0 ? y1 + 0.01F : y1;
                        float[] p = f < 2 ? new float[] {dx + pl, yy0, dz + b0, dx + pl, yy1, dz + b0, dx + pl, yy1, dz + b1, dx + pl, yy0, dz + b1}
                                : new float[] {dx + b0, yy0, dz + pl, dx + b0, yy1, dz + pl, dx + b1, yy1, dz + pl, dx + b1, yy0, dz + pl};
                        flat(v, m, p, (part == 0 ? 0.2F : 0.3F) * k, (part == 0 ? 0.6F : 0.9F) * k, k);
                    }
                }
                if (s > 0.01F) {
                    for (int j = 0; j < 3; j++) {
                        float f = ((t * (0.4F + s) + j / 3.0F) % 1.0F);
                        float d = Mth.lerp(f, 0.55F, r - 0.6F);
                        sprite(v, m, cam[0], cam[1], 0.5F + Integer.signum(dx) * d, 0.09F, 0.5F + Integer.signum(dz) * d, 0.12F, 0.35F, 0.95F, 1.0F,
                                0.8F * Mth.sin(f * Mth.PI));
                    }
                }
            }
            // the collider's glow over it: brighter as the beams speed up
            glow(v, m, cam[0], cam[1], 0.5F, 1.15F, 0.5F, 0.5F + 0.9F * s, 0.4F, 0.95F, 1.0F, 0.35F + 0.65F * s * (0.85F + 0.15F * Mth.sin(t * 20)));
            if (s > 0.01F) {
                // two beams, round the ring opposite ways: a bright head, a trail behind it - longer the faster
                beam(v, m, cam, be.clientTrack, r, s, 1, 0.35F, 0.95F, 1.0F);
                beam(v, m, cam, per - be.clientTrack + r, r, s, -1, 1.0F, 0.4F, 0.9F);
            }
            // at speed, what it's smashing rides the beams' heads, spinning
            ItemStack in = be.items.getStackInSlot(ParticleAcceleratorBlockEntity.INPUT);
            if (s > 0.6F && !in.isEmpty()) {
                riding(be, pose, buffers, in, at(be.clientTrack, r), t, s);
                riding(be, pose, buffers, in, at(per - be.clientTrack + r, r), t + 1.3F, s);
            }
            float age = testAge >= 0 ? testAge : (now - be.flashAt) / 1000.0F;
            if (be.flashAt > 0 && age < 2.0F) {
                if (be.flashKind == 1) {
                    collision(be, pose, buffers, cam, r, age);
                } else if (age < 0.9F) {
                    float k = 1.0F - age / 0.9F;
                    glow(glows(buffers), m, cam[0], cam[1], 0.5F, 1.3F, 0.5F, 1.5F + 5.0F * (1.0F - k), 1.0F, 0.25F, 0.15F, k);
                }
            }
        }

        /** (The render test: a collision held at this many seconds in, to be seen; -1 normally.) */
        public static float testAge = -1;

        /** What it's smashing, riding a beam's head: small, spinning, glowing. */
        private static void riding(ParticleAcceleratorBlockEntity be, PoseStack pose, MultiBufferSource buffers, ItemStack item, Vector3f at, float t, float s) {
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(t * 720.0F));
            pose.mulPose(Axis.XP.rotationDegrees(t * 400.0F));
            float k = 0.25F + 0.15F * s;
            pose.scale(k, k, k);
            Minecraft.getInstance().getItemRenderer().renderStatic(item, net.minecraft.world.item.ItemDisplayContext.GROUND, 0xF000F0, OverlayTexture.NO_OVERLAY,
                    pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        /**
         * A collision: two of what it smashed dive in from the ring's sides and crash together over its middle - a flash, a
         * shockwave sweeping out over the ring, sparks flying - and what came of it rises out of the burst, spinning,
         * glowing, and fades.
         */
        private static void collision(ParticleAcceleratorBlockEntity be, PoseStack pose, MultiBufferSource buffers, Vector3f[] cam, int r, float age) {
            float hit = 0.4F;
            ItemStack smashed = be.clientSmashed;
            Minecraft mc = Minecraft.getInstance();
            VertexConsumer v = glows(buffers);
            Matrix4f m = pose.last().pose();
            float cy = 1.3F;
            if (age < hit) {
                // the two diving in: from opposite sides of the ring, faster as they near, trails behind
                float f = age / hit, e = f * f;
                for (int side = 0; side < 2; side++) {
                    float a = side * Mth.PI + 0.4F;
                    float sx = 0.5F + Mth.cos(a) * r, sz = 0.5F + Mth.sin(a) * r;
                    float x = Mth.lerp(e, sx, 0.5F), y = Mth.lerp(e, 0.55F, cy) + 0.9F * Mth.sin(Mth.PI * f), z = Mth.lerp(e, sz, 0.5F);
                    for (int k = 1; k <= 10; k++) {
                        float g = Math.max(0, e - k * 0.035F), gf = Mth.sqrt(g);
                        sprite(v, m, cam[0], cam[1], Mth.lerp(g, sx, 0.5F), Mth.lerp(g, 0.55F, cy) + 0.9F * Mth.sin(Mth.PI * gf), Mth.lerp(g, sz, 0.5F), 0.22F * (1 - k / 11.0F),
                                side == 0 ? 0.35F : 1.0F, side == 0 ? 0.95F : 0.4F, side == 0 ? 1.0F : 0.9F, 0.8F * (1 - k / 11.0F));
                    }
                    glow(v, m, cam[0], cam[1], x, y, z, 0.8F, side == 0 ? 0.35F : 1.0F, side == 0 ? 0.95F : 0.4F, side == 0 ? 1.0F : 0.9F, 0.9F);
                    if (!smashed.isEmpty()) {
                        pose.pushPose();
                        pose.translate(x, y - 0.1F, z);
                        pose.mulPose(Axis.YP.rotationDegrees(age * 900.0F + side * 180));
                        pose.scale(0.6F, 0.6F, 0.6F);
                        mc.getItemRenderer().renderStatic(smashed, net.minecraft.world.item.ItemDisplayContext.GROUND, 0xF000F0, OverlayTexture.NO_OVERLAY,
                                pose, buffers, be.getLevel(), side);
                        pose.popPose();
                    }
                    v = glows(buffers);
                }
                return;
            }
            float since = age - hit;
            // the burst: a flash, white to its colours, fading
            float k = Math.max(0.0F, 1.0F - since / 0.6F);
            glow(v, m, cam[0], cam[1], 0.5F, cy, 0.5F, 1.2F + 4.5F * (1.0F - k), 0.9F, 0.95F, 1.0F, k);
            glow(v, m, cam[0], cam[1], 0.5F, cy, 0.5F, 0.6F + 1.5F * (1.0F - k), 1.0F, 0.6F, 0.95F, k * 0.8F);
            // the shockwave: a ring of light sweeping out over the ring, low, fading as it goes
            float wave = Math.min(1.0F, since / 0.7F), wr = 0.3F + (r + 1.0F) * wave;
            if (wave < 1.0F) {
                int n = 24 + 6 * r;
                for (int i = 0; i < n; i++) {
                    float a = i * Mth.TWO_PI / n;
                    sprite(v, m, cam[0], cam[1], 0.5F + Mth.cos(a) * wr, 0.7F + 0.6F * (1 - wave), 0.5F + Mth.sin(a) * wr, 0.35F, 0.5F, 0.9F, 1.0F,
                            0.7F * (1.0F - wave));
                }
            }
            // debris: glowing bits thrown out to the ring, bouncing back off its tube, fading
            java.util.Random bits = new java.util.Random(be.flashAt);
            for (int i = 0; i < 12; i++) {
                float a = bits.nextFloat() * Mth.TWO_PI, speed = 2.5F + bits.nextFloat() * 3.0F, lift = 0.4F + bits.nextFloat() * 0.8F;
                float reach = r - 0.35F, hitAt = reach / speed, d;
                if (since < hitAt) {
                    d = speed * since;
                } else {
                    d = Math.max(0.0F, reach - (since - hitAt) * speed * 0.35F);
                }
                float life = 1.0F - since / 1.6F;
                if (life <= 0) {
                    continue;
                }
                float y = cy - 0.55F + lift * Mth.sin(Math.min(1.0F, since / (hitAt * 1.4F)) * Mth.PI) * 0.6F;
                y = Math.max(0.5F, y);
                float bx = 0.5F + Mth.cos(a) * d, bz = 0.5F + Mth.sin(a) * d;
                sprite(v, m, cam[0], cam[1], bx, y, bz, 0.07F, 1.0F * life, 0.75F * life, 0.4F * life, 1.0F);
                sprite(v, m, cam[0], cam[1], 0.5F + Mth.cos(a) * d * 0.9F, y, 0.5F + Mth.sin(a) * d * 0.9F, 0.05F, 0.7F * life, 0.45F * life, 0.2F * life, 1.0F);
            }
            // sparks: once, flying out of the burst
            if (!be.clientSparked && be.getLevel() != null) {
                be.clientSparked = true;
                var level = be.getLevel();
                var bp = be.getBlockPos();
                for (int i = 0; i < 40; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2, up = level.random.nextDouble() * 0.5 - 0.1, sp = 0.15 + level.random.nextDouble() * 0.35;
                    level.addParticle(i % 3 == 0 ? net.minecraft.core.particles.ParticleTypes.END_ROD : net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            bp.getX() + 0.5, bp.getY() + cy, bp.getZ() + 0.5, Math.cos(a) * sp, up, Math.sin(a) * sp);
                }
                if (!smashed.isEmpty()) {
                    for (int i = 0; i < 12; i++) {
                        level.addParticle(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, smashed),
                                bp.getX() + 0.5, bp.getY() + cy, bp.getZ() + 0.5, (level.random.nextDouble() - 0.5) * 0.4, level.random.nextDouble() * 0.3,
                                (level.random.nextDouble() - 0.5) * 0.4);
                    }
                }
            }
            // what came of it: rising out of the burst, spinning, a glow behind it, fading at the end
            ItemStack made = be.clientMade;
            if (!made.isEmpty() && since > 0.15F) {
                float u = Math.min(1.0F, (since - 0.15F) / 1.3F);
                float rise = cy + 0.4F + 1.4F * (1.0F - (1.0F - u) * (1.0F - u));
                float pop = u < 0.15F ? u / 0.15F * 1.2F : u < 0.25F ? 1.2F - (u - 0.15F) * 2.0F : 1.0F;
                float fade = u > 0.8F ? (1.0F - u) / 0.2F : 1.0F;
                glow(v, m, cam[0], cam[1], 0.5F, rise, 0.5F, 1.1F * pop, 1.0F, 0.9F, 0.55F, 0.7F * fade);
                pose.pushPose();
                pose.translate(0.5F, rise - 0.15F, 0.5F);
                pose.mulPose(Axis.YP.rotationDegrees(since * 300.0F));
                float sc = 1.0F * pop * (0.4F + 0.6F * fade);
                pose.scale(sc, sc, sc);
                mc.getItemRenderer().renderStatic(made, net.minecraft.world.item.ItemDisplayContext.GROUND, 0xF000F0, OverlayTexture.NO_OVERLAY, pose, buffers,
                        be.getLevel(), 7);
                pose.popPose();
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
        /** (The render test: a weather to show over its dish, -1 normally.) */
        public static int testCloud = -1;
        private static final ResourceLocation PUFF = ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "textures/misc/cloud_puff.png");
        /**
         * A little weather of its own over its dish: a cloud gathering while it turns the weather (as far as it's got), and
         * held there in rain (raining on the dish) or thunder (darker, small bolts down to the dish now and then).
         */
        private static void cloud(WeatherMachineBlockEntity be, PoseStack pose, MultiBufferSource buffers, float t, float y0, int mode, boolean holding,
                                  boolean turning) {
            float amount = turning ? be.charge() : holding && mode >= 2 ? 1.0F : 0.0F;
            if (amount <= 0.02F) {
                return;
            }
            boolean thunder = mode == 3, rain = holding && mode >= 2;
            float cy = y0 + 3.4F;
            long window = (long) (t / 2.7F);
            float sinceBolt = t - window * 2.7F;
            boolean bolt = thunder && holding && (testCloud == 3 || new java.util.Random(window * 7919L).nextFloat() < 0.7F && sinceBolt < 0.18F);
            float shade = thunder ? 0.42F : rain ? 0.7F : 0.92F;
            if (bolt) {
                shade = 1.0F;
            }
            Vector3f[] cam = camera();
            VertexConsumer v = buffers.getBuffer(RenderType.entityTranslucent(PUFF));
            PoseStack.Pose p = pose.last();
            int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            java.util.Random rnd = new java.util.Random(be.getBlockPos().asLong());
            int puffs = 44;
            for (int i = 0; i < puffs; i++) {
                float a = rnd.nextFloat() * Mth.TWO_PI + t * 0.05F, d = (float) Math.sqrt(rnd.nextFloat()) * 2.2F;
                float px = 0.5F + Mth.cos(a) * d, pz = 0.5F + Mth.sin(a) * d * 0.85F, py = cy + (rnd.nextFloat() - 0.35F) * 0.9F * (1.0F - d / 3.0F)
                        + 0.04F * Mth.sin(t * 0.7F + i);
                float size = (0.85F + rnd.nextFloat() * 0.75F) * (0.5F + 0.5F * amount);
                float k = shade * (0.85F + 0.15F * rnd.nextFloat()) * (py > cy ? 1.0F : 0.85F); // (darker underneath)
                billboard(v, p, cam, px, py, pz, size, k, k, Math.min(1.0F, k * 1.04F), 0.6F * amount, light);
            }
            if (rain) { // rain: streaks falling from it onto the dish
                float top = cy - 0.3F, bottom = y0 + 0.7F, h = top - bottom;
                for (int i = 0; i < 36; i++) {
                    float a = rnd.nextFloat() * Mth.TWO_PI, d = (float) Math.sqrt(rnd.nextFloat()) * 1.5F;
                    float f = (rnd.nextFloat() + t * 1.6F) % 1.0F;
                    float x = 0.5F + Mth.cos(a) * d, z = 0.5F + Mth.sin(a) * d, y = top - f * h;
                    streak(v, p, cam, x, y, z, 0.03F, 0.4F, 0.7F, 0.8F, 1.0F, 0.75F, light);
                }
            }
            if (bolt) { // (thunder: a small bolt down to the dish)
                VertexConsumer l = buffers.getBuffer(RenderType.lightning());
                Matrix4f m = pose.last().pose();
                java.util.Random br = new java.util.Random(window * 31L + (long) (sinceBolt * 30));
                float bx = 0.5F + (br.nextFloat() - 0.5F) * 1.2F, bz = 0.5F + (br.nextFloat() - 0.5F) * 1.2F, by = cy - 0.3F;
                float ex = 0.5F, ey = y0 + 0.9F, ez = 0.5F;
                int n = 7;
                float px = bx, py = by, pz = bz;
                for (int i = 1; i <= n; i++) {
                    float f = i / (float) n, j = i == n ? 0 : 0.25F;
                    float qx = Mth.lerp(f, bx, ex) + (br.nextFloat() - 0.5F) * j, qy = Mth.lerp(f, by, ey), qz = Mth.lerp(f, bz, ez) + (br.nextFloat() - 0.5F) * j;
                    for (int s = 0; s < 2; s++) { // (two strips, crossed: seen from any side)
                        float ox = s == 0 ? 0.03F : 0, oz = s == 0 ? 0 : 0.03F;
                        l.addVertex(m, px - ox, py, pz - oz).setColor(0.85F, 0.9F, 1.0F, 0.9F);
                        l.addVertex(m, px + ox, py, pz + oz).setColor(0.85F, 0.9F, 1.0F, 0.9F);
                        l.addVertex(m, qx + ox, qy, qz + oz).setColor(0.85F, 0.9F, 1.0F, 0.9F);
                        l.addVertex(m, qx - ox, qy, qz - oz).setColor(0.85F, 0.9F, 1.0F, 0.9F);
                        l.addVertex(m, qx - ox, qy, qz - oz).setColor(0.85F, 0.9F, 1.0F, 0.9F);
                        l.addVertex(m, qx + ox, qy, qz + oz).setColor(0.85F, 0.9F, 1.0F, 0.9F);
                        l.addVertex(m, px + ox, py, pz + oz).setColor(0.85F, 0.9F, 1.0F, 0.9F);
                        l.addVertex(m, px - ox, py, pz - oz).setColor(0.85F, 0.9F, 1.0F, 0.9F);
                    }
                    px = qx;
                    py = qy;
                    pz = qz;
                }
            }
        }

        /** A soft round puff facing you (translucent: it can be dark). */
        private static void billboard(VertexConsumer v, PoseStack.Pose p, Vector3f[] cam, float x, float y, float z, float size, float r, float g, float b,
                                      float a, int light) {
            float rx = cam[0].x() * size, ry = cam[0].y() * size, rz = cam[0].z() * size, ux = cam[1].x() * size, uy = cam[1].y() * size, uz = cam[1].z() * size;
            puffVertex(v, p, x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, r, g, b, a, light);
            puffVertex(v, p, x + rx - ux, y + ry - uy, z + rz - uz, 1, 1, r, g, b, a, light);
            puffVertex(v, p, x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, r, g, b, a, light);
            puffVertex(v, p, x - rx + ux, y - ry + uy, z - rz + uz, 0, 0, r, g, b, a, light);
        }

        /** A thin upright streak (a raindrop falling), turned to you round its length. */
        private static void streak(VertexConsumer v, PoseStack.Pose p, Vector3f[] cam, float x, float y, float z, float w, float len, float r, float g, float b,
                                   float a, int light) {
            float rx = cam[0].x() * w, rz = cam[0].z() * w;
            puffVertex(v, p, x - rx, y - len, z - rz, 0.45F, 0.5F, r, g, b, a, light);
            puffVertex(v, p, x + rx, y - len, z + rz, 0.55F, 0.5F, r, g, b, a, light);
            puffVertex(v, p, x + rx, y, z + rz, 0.55F, 0.5F, r, g, b, a, light);
            puffVertex(v, p, x - rx, y, z - rz, 0.45F, 0.5F, r, g, b, a, light);
        }

        private static void puffVertex(VertexConsumer v, PoseStack.Pose p, float x, float y, float z, float u, float w, float r, float g, float b, float a,
                                       int light) {
            v.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0.0F, 1.0F, 0.0F);
        }

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
            if (testCloud >= 0) { // (the render test: its rain, its thunder)
                cloud(be, pose, buffers, t, y0, testCloud, true, false);
            } else {
                cloud(be, pose, buffers, t, y0, mode, holding, turning);
            }
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
                float a = level.random.nextFloat() * Mth.TWO_PI, cr = 1.5F + level.random.nextFloat() * 2.5F;
                level.addParticle(mode == 1 ? ParticleTypes.END_ROD : ParticleTypes.CLOUD, bp.getX() + 0.5 + Mth.cos(a) * cr,
                        bp.getY() + ey + 2.5 + level.random.nextFloat() * 2.0, bp.getZ() + 0.5 + Mth.sin(a) * cr,
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
            dressing(be, pose, buffers, (chamber - 1) / 2, full); // (after the box: getting another buffer ends its one)
            hot(be, pose, buffers, (chamber - 1) / 2);
        }

        /**
         * What it just made, on its crucible: glowing red-hot, a shimmer of heat round it and smoke rising, cooling over a
         * few seconds - dimming from white-orange through red to its own colours - then gone.
         */
        private static void hot(StarForgeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int r) {
            if (be.hotItem.isEmpty()) {
                return;
            }
            float age = (System.currentTimeMillis() - be.hotAt) / 1000.0F, life = 4.0F;
            if (age > life) {
                be.hotItem = ItemStack.EMPTY;
                return;
            }
            float heat = 1.0F - age / life;
            // (set down on the chamber's floor in front of the crucible, under its star)
            float y = -r + 9.0F / 16.0F + 0.5F + 0.16F, z = 0.5F + 0.8F;
            Vector3f[] cam = camera();
            VertexConsumer v = glows(buffers);
            Matrix4f m = pose.last().pose();
            // (the heat: white-orange to deep red as it cools)
            float g = 0.25F + 0.5F * heat * heat, b = 0.1F * heat * heat;
            glow(v, m, cam[0], cam[1], 0.5F, y + 0.1F, z, 0.5F + 0.3F * heat, 1.0F, g, b, 0.85F * heat);
            glow(v, m, cam[0], cam[1], 0.5F, y + 0.1F, z, 0.22F, 1.0F, 0.6F + 0.3F * heat, 0.3F * heat, heat);
            pose.pushPose();
            pose.translate(0.5F, y, z);
            pose.mulPose(Axis.YP.rotationDegrees(age * 40.0F));
            pose.scale(0.55F, 0.55F, 0.55F);
            Minecraft.getInstance().getItemRenderer().renderStatic(be.hotItem, net.minecraft.world.item.ItemDisplayContext.GROUND,
                    net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
            pose.popPose();
            var level = be.getLevel();
            if (level != null && heat > 0.25F && level.random.nextFloat() < 0.08F) {
                var bp = be.getBlockPos();
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, bp.getX() + 0.5, bp.getY() + y + 0.3, bp.getZ() + z, 0, 0.03, 0);
            }
        }

        private static final float[][] TINT = {{1.0F, 0.35F, 0.21F}, {1.0F, 0.7F, 0.23F}, {0.35F, 0.66F, 1.0F}, {0.93F, 0.96F, 1.0F}};

        /**
         * Its formed chamber's dressing: a crucible on its floor under the star, a rune circle round it, and - lit - beams
         * of light from the four chimneys' feet holding the star, the runes glowing, and smoke from the chimneys' tops; all
         * in its star's colour.
         */
        private static void dressing(StarForgeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int r, float full) {
            float floor = -r + 9.0F / 16.0F + 0.5F; // (just over the bottom panels)
            VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(Accelerator.METAL));
            PoseStack.Pose p = pose.last();
            int lt = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            Weather.box(metal, p, 0.12F, floor, 0.12F, 0.88F, floor + 0.22F, 0.88F, lt);
            Weather.box(metal, p, 0.04F, floor + 0.22F, 0.04F, 0.96F, floor + 0.4F, 0.96F, lt);
            float[] c = TINT[Mth.clamp(be.colour(), 0, 3)];
            boolean lit = be.lit();
            float k = lit ? 0.6F + 0.4F * full : 0.12F;
            float t = (float) LavaLampRenderer.seconds();
            Vector3f[] cam = camera();
            VertexConsumer v = glows(buffers);
            Matrix4f m = pose.last().pose();
            // the crucible's molten top
            glow(v, m, cam[0], cam[1], 0.5F, floor + 0.42F, 0.5F, 0.7F, 1.0F, 0.55F, 0.2F, lit ? 0.9F : 0.25F);
            // the rune circle: a ring of marks round the crucible, pulsing
            float rr = r - 1.1F;
            int n = 10 + 4 * r;
            for (int i = 0; i < n; i++) {
                float a = i * Mth.TWO_PI / n;
                float pulse = 0.55F + 0.45F * Mth.sin(t * 2.0F + i * 0.9F);
                sprite(v, m, cam[0], cam[1], 0.5F + Mth.cos(a) * rr, floor + 0.03F, 0.5F + Mth.sin(a) * rr, i % 2 == 0 ? 0.2F : 0.12F,
                        c[0], c[1], c[2], k * pulse * 0.8F);
            }
            if (!lit) {
                return;
            }
            // the beams: from each chimney's foot in to the star, a shimmer running along them
            for (int q = 0; q < 4; q++) {
                float sx = (q == 0 || q == 3) ? -1 : 1, sz = q < 2 ? -1 : 1;
                float x0 = 0.5F + sx * (r - 0.45F), y0 = 0.5F + r - 0.2F, z0 = 0.5F + sz * (r - 0.45F);
                for (int j = 0; j <= 16; j++) {
                    float f = j / 16.0F;
                    float shimmer = 0.6F + 0.4F * Mth.sin(t * 6.0F - f * 9.0F + q);
                    sprite(v, m, cam[0], cam[1], Mth.lerp(f, x0, 0.5F), Mth.lerp(f, y0, 0.5F), Mth.lerp(f, z0, 0.5F), 0.16F, c[0], c[1], c[2],
                            0.45F * k * shimmer * (1.0F - 0.5F * f));
                }
            }
            // smoke from the chimneys now and then
            var level = be.getLevel();
            if (level != null && level.random.nextFloat() < 0.06F) {
                int q = level.random.nextInt(4);
                float sx = (q == 0 || q == 3) ? -1 : 1, sz = q < 2 ? -1 : 1;
                var bp = be.getBlockPos();
                level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, bp.getX() + 0.5 + sx * r, bp.getY() + r + 1.95,
                        bp.getZ() + 0.5 + sz * r, 0.0, 0.04, 0.0);
            }
        }

        @Override
        public AABB getRenderBoundingBox(StarForgeBlockEntity be) {
            return new AABB(be.getBlockPos()).inflate(6.0); // (its chimneys stand over it)
        }

        @Override
        public boolean shouldRenderOffScreen(StarForgeBlockEntity be) {
            return true;
        }
    }
}
