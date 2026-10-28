package com.example.skyblocks.client;

import com.example.skyblocks.KoiPondBlock;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.iris.IrisCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Life round the koi ponds (both kinds), drawn after everything else: dragonflies darting and hovering over the water
 * on fine days, fireflies blinking over it at night; on the vanilla pond also rings spreading where the rain falls on
 * it and the moon's reflection (the shader pond does those in its own water). The ponds seen this frame are told by
 * their renderers.
 */
public final class PondLife {
    private static final float SURF = 14.5F / 16.0F;
    private static final ResourceLocation GLOW = new ResourceLocation(SkyBlocksMod.MOD_ID, "textures/entity/pond/glow.png");
    private static final ResourceLocation RIPPLE = new ResourceLocation(SkyBlocksMod.MOD_ID, "textures/entity/pond/ripple.png");
    private static final ResourceLocation DRAGONFLY = new ResourceLocation(SkyBlocksMod.MOD_ID, "textures/entity/pond/dragonfly.png");
    /** A dragonfly's colours: blue, red, green. */
    private static final int[] DRAGONFLY_TINT = {0xFF3F7FEA, 0xFFD8402E, 0xFF52B04A};

    private record Seen(KoiPondRenderer.Pond pond, int y) {}

    private static final Map<Long, Seen> PONDS = new HashMap<>();
    private static final Set<Long> VANILLA_WATER = new HashSet<>();
    private static ModelPart dragonfly;

    private PondLife() {}

    /** A pond block was drawn this frame (its pond: once). */
    static void saw(KoiPondRenderer.Pond pond, BlockPos pos) {
        PONDS.putIfAbsent(pond.anchor(), new Seen(pond, pos.getY()));
    }

    /** A vanilla pond block's water was drawn this frame (for its rain rings and the moon on it). */
    static void sawVanillaWater(BlockPos pos) {
        VANILLA_WATER.add(pos.asLong());
    }

    /** 1 by day .. 0 at night, as the ponds take it. */
    static float day(ClientLevel level) {
        return Math.max(0.0F, Math.min(1.0F, (level.getSkyDarken(1.0F) - 0.2F) / 0.8F));
    }

    /** How hard it's raining here (0 none, or under a roof). */
    static float rain(ClientLevel level, BlockPos pos, float partialTick) {
        return level.isRainingAt(pos.above(2)) ? level.getRainLevel(partialTick) : 0.0F;
    }

    /** Where the moon is (a direction; under the horizon by day). */
    static Vec3 moon(ClientLevel level, float partialTick) {
        float a = level.getSunAngle(partialTick);
        return new Vec3(Mth.sin(a), -Mth.cos(a), 0.0).normalize();
    }

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onStagePondLife(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            if (MirrorRenderer.isRenderingReflection() || IrisCompat.renderingShadowPass()) {
                return;
            }
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || (PONDS.isEmpty() && VANILLA_WATER.isEmpty())) {
                PONDS.clear();
                VANILLA_WATER.clear();
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            float pt = event.getPartialTick();
            PoseStack poseStack = new PoseStack();
            poseStack.mulPoseMatrix(new Matrix4f(event.getPoseStack().last().pose()));
            poseStack.translate(-cam.x, -cam.y, -cam.z);
            var modelView = RenderSystem.getModelViewStack();
            modelView.pushPose();
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            try {
                float t = LavaLampRenderer.seconds();
                float day = day(level);
                Quaternionf facing = event.getCamera().rotation();
                for (Seen s : PONDS.values()) {
                    BlockPos at = BlockPos.of(s.pond().anchor());
                    float rain = rain(level, at, pt);
                    if (day > 0.5F && rain < 0.05F) {
                        dragonflies(level, s, t, (day - 0.5F) * 2.0F, poseStack, buffers);
                    }
                    if (day < 0.4F && rain < 0.3F) {
                        fireflies(s, t, (0.4F - day) / 0.4F * (1.0F - rain / 0.3F), facing, poseStack, buffers);
                    }
                }
                vanillaWater(level, cam, pt, t, day, poseStack, buffers);
                buffers.endBatch();
            } finally {
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
                PONDS.clear();
                VANILLA_WATER.clear();
            }
        }
    }

    private static float h(long anchor, int k) {
        BlockPos a = BlockPos.of(anchor);
        return KoiPondBlock.u01(KoiPondBlock.hashu(Math.floorMod(a.getX(), 512) + 3000, Math.floorMod(a.getZ(), 512) + 3000, k));
    }

    private static float smooth(float a, float b, float x) {
        float k = Math.max(0.0F, Math.min(1.0F, (x - a) / (b - a)));
        return k * k * (3.0F - 2.0F * k);
    }

    // ---- dragonflies: darting from spot to spot over the water, hovering between, wings a blur ----

    private static ModelPart dragonfly() {
        if (dragonfly == null) {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-1.0F, -1.0F, -6.0F, 2, 2, 2)
                    .texOffs(8, 0).addBox(-1.0F, -1.0F, -4.0F, 2, 2, 3)
                    .texOffs(0, 6).addBox(-0.5F, -0.5F, -1.0F, 1, 1, 9), PartPose.ZERO);
            CubeDeformation thin = new CubeDeformation(0.0F, -0.42F, 0.0F); // (a wing: a hair thick)
            body.addOrReplaceChild("right_front_wing", CubeListBuilder.create().texOffs(0, 16).addBox(0.0F, -0.5F, -1.0F, 7, 1, 2, thin),
                    PartPose.offset(1.0F, -1.0F, -3.2F));
            body.addOrReplaceChild("right_back_wing", CubeListBuilder.create().texOffs(0, 19).addBox(0.0F, -0.5F, -1.0F, 7, 1, 2, thin),
                    PartPose.offset(1.0F, -1.0F, -1.6F));
            body.addOrReplaceChild("left_front_wing", CubeListBuilder.create().texOffs(0, 22).addBox(-7.0F, -0.5F, -1.0F, 7, 1, 2, thin),
                    PartPose.offset(-1.0F, -1.0F, -3.2F));
            body.addOrReplaceChild("left_back_wing", CubeListBuilder.create().texOffs(0, 25).addBox(-7.0F, -0.5F, -1.0F, 7, 1, 2, thin),
                    PartPose.offset(-1.0F, -1.0F, -1.6F));
            dragonfly = LayerDefinition.create(mesh, 32, 32).bakeRoot();
        }
        return dragonfly;
    }

    /** A spot over the pond (one of its parts, the water's middle, never right at the stone), at a height over the water. */
    private static double[] spot(KoiPondRenderer.Pond pond, int y, int k) {
        int[][] parts = pond.parts();
        int[] part = parts[(int) (h(pond.anchor(), k) * parts.length) % parts.length];
        double x = part[0] + 0.3 + (part[1] - part[0] + 0.4) * h(pond.anchor(), k + 1);
        double z = part[2] + 0.3 + (part[3] - part[2] + 0.4) * h(pond.anchor(), k + 2);
        return new double[] {x, y + SURF + 0.25 + 0.6 * h(pond.anchor(), k + 3), z};
    }

    private static void dragonflies(ClientLevel level, Seen s, float t, float fade, PoseStack poseStack, MultiBufferSource buffers) {
        KoiPondRenderer.Pond pond = s.pond();
        long anchor = pond.anchor();
        int count = Math.min(3, 1 + pond.blocks() / 8);
        ModelPart m = dragonfly();
        ModelPart body = m.getChild("body");
        for (int d = 0; d < count; d++) {
            // (coming and going: about half the time each one is about)
            float visit = t / 50.0F + h(anchor, 10 + d);
            int epoch = (int) Math.floor(visit);
            if (h(anchor, 20 + epoch * 7 + d) > 0.6F) continue;
            float inVisit = (visit - epoch) * 50.0F;
            float here = smooth(0.0F, 2.0F, inVisit) * (1.0F - smooth(48.0F, 50.0F, inVisit)) * fade;
            if (here <= 0.02F) continue;
            // darts: every couple of seconds to a new spot, quick, then a hover
            float span = 2.2F + 0.8F * h(anchor, 30 + d);
            float ph = t / span + h(anchor, 40 + d) * 9.0F;
            int seg = (int) Math.floor(ph);
            float u = ph - seg;
            double[] a = spot(pond, s.y(), 1000 + d * 97 + (seg - 1) * 7), b = spot(pond, s.y(), 1000 + d * 97 + seg * 7);
            float k = smooth(0.0F, 0.16F, u);
            double x = a[0] + (b[0] - a[0]) * k, y = a[1] + (b[1] - a[1]) * k, z = a[2] + (b[2] - a[2]) * k;
            // (hovering: bobbing a little, drifting a touch; arriving from out of the sky, leaving into it)
            y += 0.02 * Mth.sin(t * 5.0F + d) + 1.5 * (1.0F - here);
            x += 0.015 * Mth.sin(t * 3.1F + d * 2.0F);
            double dx = b[0] - a[0], dz = b[2] - a[2];
            float yaw = (float) Math.toDegrees(Math.atan2(dx, dz)) + 8.0F * Mth.sin(t * 1.3F + d);
            float pitch = (float) (-Math.atan2(b[1] - a[1], Math.max(Math.sqrt(dx * dx + dz * dz), 0.01)) * (1.0F - k) * 0.6);
            // its wings: a blur of beating, the back pair out of step with the front
            float beat = t * 48.0F + d;
            body.getChild("right_front_wing").zRot = 0.45F * Mth.sin(beat);
            body.getChild("left_front_wing").zRot = -0.45F * Mth.sin(beat);
            body.getChild("right_back_wing").zRot = 0.45F * Mth.sin(beat + 1.6F);
            body.getChild("left_back_wing").zRot = -0.45F * Mth.sin(beat + 1.6F);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0F));
            poseStack.mulPose(Axis.XP.rotation(pitch));
            float scale = 0.65F / 16.0F * (0.85F + 0.3F * h(anchor, 50 + d)); // (about half a block long)
            poseStack.scale(scale, scale, scale);
            int tint = DRAGONFLY_TINT[(int) (h(anchor, 60 + d) * 3.0F) % 3];
            ClientCompat.render(m, poseStack, buffers.getBuffer(RenderType.entityTranslucent(DRAGONFLY)), light, OverlayTexture.NO_OVERLAY, tint);
            poseStack.popPose();
        }
    }

    // ---- fireflies: drifting slowly over and round the pond, each glowing on and off in its own time ----

    private static void fireflies(Seen s, float t, float fade, Quaternionf facing, PoseStack poseStack, MultiBufferSource buffers) {
        KoiPondRenderer.Pond pond = s.pond();
        long anchor = pond.anchor();
        int[] rect = pond.rect();
        int count = Math.max(4, Math.min(14, (int) (pond.blocks() * 1.5F)));
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW));
        // a music box playing near the pond: they all flash with its notes (taking turns, in threes) and bob to it
        net.minecraft.client.multiplayer.ClientLevel lv = Minecraft.getInstance().level;
        float[] beat = lv == null ? new float[] {0.0F, 0.0F, 0.0F} : com.example.skyblocks.MusicBoxBlockEntity.beat(lv,
                (rect[0] + rect[1] + 1) * 0.5, s.y() + 1.0, (rect[2] + rect[3] + 1) * 0.5, 12.0 + (rect[1] - rect[0] + rect[3] - rect[2]) * 0.25,
                Minecraft.getInstance().getFrameTime());
        float pulse = beat[2] > 0.0F ? beat[0] / beat[2] : 0.0F;
        for (int f = 0; f < count; f++) {
            float bx = rect[0] - 0.3F + (rect[1] - rect[0] + 1.6F) * h(anchor, 200 + f);
            float bz = rect[2] - 0.3F + (rect[3] - rect[2] + 1.6F) * h(anchor, 210 + f);
            float sp = 0.15F + 0.1F * h(anchor, 220 + f);
            double x = bx + 0.45 * Mth.sin(t * sp + f * 1.7F) + 0.15 * Mth.sin(t * sp * 2.3F + f);
            double z = bz + 0.45 * Mth.cos(t * sp * 0.9F + f * 2.3F) + 0.15 * Mth.cos(t * sp * 2.1F + f * 0.5F);
            double y = s.y() + SURF + 0.15 + 0.75 * h(anchor, 230 + f) + 0.12 * Mth.sin(t * sp * 1.7F + f * 3.1F);
            // its glow: a soft pulse every few seconds (each its own rhythm), dark between
            float period = 2.5F + 2.5F * h(anchor, 240 + f);
            float ph = (t / period + h(anchor, 250 + f)) % 1.0F;
            float glow = (float) Math.pow(Math.max(Mth.sin(ph * Mth.TWO_PI), 0.0F), 3.0) * fade;
            if (beat[2] > 0.0F) {
                float turn = (f + (int) beat[1]) % 3 == 0 ? 1.0F : 0.2F;
                glow = glow + (Math.min(1.0F, pulse * turn * 1.2F) * fade - glow) * beat[2];
                y += 0.08 * pulse * beat[2] * turn;
            }
            if (glow < 0.02F) continue;
            billboard(vc, poseStack, facing, x, y, z, 0.16F * (0.6F + 0.4F * glow), 0.55F * glow, 0.85F * glow, 0.18F * glow); // (its halo)
            billboard(vc, poseStack, facing, x, y, z, 0.035F, glow, glow, 0.6F * glow);                                          // (its light)
        }
    }

    private static void billboard(VertexConsumer vc, PoseStack poseStack, Quaternionf facing, double x, double y, double z, float size,
                                  float r, float g, float b) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(facing);
        poseStack.scale(size, size, size);
        flat(vc, poseStack.last(), r, g, b, false);
        poseStack.popPose();
    }

    /** A square of the texture from -1 to 1 (on its x and y; or x and z, lying flat), that colour, glowing. */
    private static void flat(VertexConsumer vc, PoseStack.Pose pose, float r, float g, float b, boolean lying) {
        float[][] c = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (float[] q : c) {
            float vx = q[0], vy = lying ? 0.0F : q[1], vz = lying ? q[1] : 0.0F;
            vc.vertex(pose.pose(), vx, vy, vz).color(Math.min(r, 1.0F), Math.min(g, 1.0F), Math.min(b, 1.0F), 1.0F).uv(q[2], q[3])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
        }
    }

    // ---- the vanilla pond's water: rings where the rain falls on it, the moon shining on it ----

    private static void vanillaWater(ClientLevel level, Vec3 cam, float pt, float t, float day, PoseStack poseStack, MultiBufferSource buffers) {
        if (VANILLA_WATER.isEmpty()) {
            return;
        }
        // rain: a few rings at a time on each block, spreading and fading
        VertexConsumer rings = buffers.getBuffer(RenderType.eyes(RIPPLE));
        Map<Long, Float> rainAt = new HashMap<>();
        for (long l : VANILLA_WATER) {
            BlockPos p = BlockPos.of(l);
            float rain = rainAt.computeIfAbsent(BlockPos.asLong(p.getX() >> 3, 0, p.getZ() >> 3), k -> rain(level, p, pt));
            if (rain < 0.02F) continue;
            int cx = Math.floorMod(p.getX(), 512), cz = Math.floorMod(p.getZ(), 512);
            int drops = 2 + Math.round(3.0F * rain);
            for (int k = 0; k < drops; k++) {
                float period = 0.9F + 0.5F * KoiPondBlock.u01(KoiPondBlock.hashu(cx, cz, 7000 + k));
                float ph = t / period + KoiPondBlock.u01(KoiPondBlock.hashu(cx, cz, 7100 + k));
                int n = (int) Math.floor(ph);
                float age = ph - n;
                float rx = 0.2F + 0.6F * KoiPondBlock.u01(KoiPondBlock.hashu(cx + n * 3, cz, 7200 + k));
                float rz = 0.2F + 0.6F * KoiPondBlock.u01(KoiPondBlock.hashu(cx, cz + n * 5, 7300 + k));
                float size = 0.02F + 0.12F * (float) Math.sqrt(age);
                float a = (1.0F - age) * (1.0F - age) * 0.45F * rain * (0.4F + 0.6F * day);
                poseStack.pushPose();
                poseStack.translate(p.getX() + rx, p.getY() + SURF + 0.004, p.getZ() + rz);
                poseStack.scale(size, 1.0F, size);
                flat(rings, poseStack.last(), a * 0.8F, a * 0.9F, a, true);
                poseStack.popPose();
            }
        }
        // the moon: its reflection on the water (where the view, bounced off the surface, meets it), stretched by the ripples
        Vec3 moon = moon(level, pt);
        if (moon.y < 0.05 || day > 0.6F) {
            return;
        }
        float strength = Math.min(1.0F, (float) (moon.y * 4.0)) * (1.0F - day / 0.6F);
        List<Double> heights = new ArrayList<>();
        for (long l : VANILLA_WATER) {
            double surf = BlockPos.of(l).getY() + SURF;
            if (!heights.contains(surf)) heights.add(surf);
        }
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        for (double surf : heights) {
            if (cam.y <= surf + 0.05) continue;
            double along = (cam.y - surf) / moon.y;
            double x = cam.x + moon.x * along, z = cam.z + moon.z * along;
            if (!VANILLA_WATER.contains(BlockPos.containing(x, surf - 0.5, z).asLong())) continue;
            double dist = Math.sqrt((x - cam.x) * (x - cam.x) + (cam.y - surf) * (cam.y - surf) + (z - cam.z) * (z - cam.z));
            float size = (float) (0.06 * dist);
            float shimmer = 0.85F + 0.15F * Mth.sin(t * 3.7F) * Mth.sin(t * 5.3F);
            double hx = x - cam.x, hz = z - cam.z, hl = Math.max(Math.sqrt(hx * hx + hz * hz), 1.0e-4);
            float yaw = (float) Math.atan2(hx / hl, hz / hl);
            for (int i = 0; i < 2; i++) { // (a soft glow, and a bright core)
                float s2 = i == 0 ? size * 2.6F : size;
                float a = (i == 0 ? 0.25F : 0.85F) * strength * shimmer;
                poseStack.pushPose();
                poseStack.translate(x, surf + 0.006, z);
                poseStack.mulPose(Axis.YP.rotation(yaw));
                poseStack.scale(s2, 1.0F, s2 * (1.0F + (float) Math.min(3.0, 0.35 * dist / Math.max(cam.y - surf, 0.3))));
                flat(glow, poseStack.last(), 0.8F * a, 0.86F * a, a, true);
                poseStack.popPose();
            }
        }
    }
}
