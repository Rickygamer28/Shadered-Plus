package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TrainEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a train (TrainEntity): each of its cars a box turned along the rails, traced through by the train_car shader
 * (maglev or steam; windows, seats, wheels); under a maglev, light along its rails (train_glow); its headlight and lit
 * cars as shader lights. With a shader pack on, laid over the pack's image with the block entities (iris/IrisDefer).
 */
public class TrainRenderer extends EntityRenderer<TrainEntity> {
    static ShaderInstance carShader, glowShader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "train_car"), DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
                    s -> carShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "train_glow"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> glowShader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CAR = create("skyblocks_train_car", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS, 1024, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> carShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        static final RenderType GLOW = create("skyblocks_train_glow", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 8192, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> glowShader))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** The box round a car (its own space, as the shader's). */
    private static final float[] LO = {-2.8F, -0.15F, -1.15F}, HI = {2.8F, 2.95F, 1.15F};

    public TrainRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TrainEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    @Override
    public boolean shouldRender(TrainEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return e.shouldRenderAtSqrDistance(e.distanceToSqr(x, y, z)) && frustum.isVisible(e.getBoundingBoxForCulling());
    }

    @Override
    public void render(TrainEntity train, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (carShader == null || glowShader == null || com.example.skyblocks.client.iris.IrisCompat.renderingShadowPass()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 base = new Vec3(Mth.lerp(partialTick, train.xo, train.getX()), Mth.lerp(partialTick, train.yo, train.getY()),
                Mth.lerp(partialTick, train.zo, train.getZ()));
        int units = train.units(), loco = train.locoUnit(), style = train.style();
        float speed = train.speed(partialTick), head = train.head(partialTick);
        float sky = mc.level == null ? 1.0F : mc.level.getSkyDarken(partialTick);
        Vec3[] mid = new Vec3[units], dir = new Vec3[units];
        float[] lit = new float[units];
        for (int k = 0; k < units; k++) {
            Vec3[] u = train.unit(k, partialTick);
            mid[k] = u[0].subtract(base);
            dir[k] = k == loco && !train.locoFront() ? u[1].scale(-1) : u[1];
            BlockPos b = BlockPos.containing(u[0].add(0, 1.5, 0));
            float bl = train.level().getBrightness(LightLayer.BLOCK, b) / 15.0F, sl = train.level().getBrightness(LightLayer.SKY, b) / 15.0F;
            lit[k] = Mth.clamp(Math.max(bl, sl * (sky - 0.2F) / 0.8F), 0.12F, 1.0F);
        }
        // its headlight, and its cars lit inside
        Vec3 nose = mid[loco].add(dir[loco].scale(3.2)).add(0, 1.1, 0);
        ShaderLightRenderer.add(pose, (float) nose.x, (float) nose.y, (float) nose.z, 9.0F, style == 0 ? 0xD8F4FF : 0xFFE0A0);
        for (int k = 0; k < units; k++) {
            if (k != loco && lit[k] < 0.6F) {
                ShaderLightRenderer.add(pose, (float) mid[k].x, (float) mid[k].y + 1.6F, (float) mid[k].z, 4.5F, 0xFFD9A0);
            }
        }
        Matrix4f m = new Matrix4f(pose.last().pose());
        float travel = head;
        float length = train.length();
        // (the rails' glow: along it, from behind its last car to ahead of it - brighter the faster it goes)
        float[] trail = style == 0 ? trail(train, partialTick, head, length, base) : new float[0];
        float bright = 0.35F + Mth.clamp(speed * 2.0F, 0.0F, 1.0F) * 0.65F;
        Runnable draw = () -> {
            if (trail.length > 0) {
                ElevatorRenderer.drawNow(Types.GLOW, vc -> glow(vc, m, trail, bright));
            }
            ElevatorRenderer.drawNow(Types.CAR, vc -> {
                for (int k = 0; k < units; k++) {
                    box(vc, m, mid[k], dir[k], style, k == loco ? 0 : 1, speed, travel, lit[k]);
                }
            });
        };
        if (com.example.skyblocks.client.iris.IrisCompat.shaderPackInUse()) {
            com.example.skyblocks.client.iris.IrisDefer.extra(draw);
        } else if (!LatePass.later(src -> draw.run())) {
            draw.run();
        }
    }

    /** Points along its rails (x y z, then the way across: x z), from behind it to ahead of it, every half block. */
    private static float[] trail(TrainEntity train, float partialTick, float head, float length, Vec3 base) {
        float from = Math.max(head - length - 6.0F, 0.0F), to = Math.min(head + 3.0F, train.total());
        int n = Math.max(0, (int) ((to - from) / 0.5F) + 1);
        float[] out = new float[n * 6];
        for (int i = 0; i < n; i++) {
            float s = from + i * 0.5F;
            Vec3 a = train.at(s), b = train.at(s + 0.25F), c = train.at(s - 0.25F);
            Vec3 d = b.subtract(c);
            if (d.lengthSqr() < 1e-6) d = new Vec3(1, 0, 0);
            d = d.normalize();
            // (how bright here: full under it, fading off its ends)
            float f = s > head ? 1.0F - (s - head) / 3.0F : s < head - length ? 1.0F - (head - length - s) / 6.0F : 1.0F;
            out[i * 6] = (float) (a.x - base.x);
            out[i * 6 + 1] = (float) (a.y - base.y) - 0.035F;
            out[i * 6 + 2] = (float) (a.z - base.z);
            out[i * 6 + 3] = (float) -d.z;
            out[i * 6 + 4] = (float) d.x;
            out[i * 6 + 5] = Mth.clamp(f, 0.0F, 1.0F);
        }
        return out;
    }

    private static void glow(VertexConsumer vc, Matrix4f m, float[] t, float bright) {
        int n = t.length / 6;
        float w = 0.45F;
        for (int i = 0; i + 1 < n; i++) {
            int a = i * 6, b = a + 6;
            int ca = Math.round(t[a + 5] * bright * 255), cb = Math.round(t[b + 5] * bright * 255);
            float sa = i * 0.5F, sb = sa + 0.5F;
            vc.addVertex(m, t[a] - t[a + 3] * w, t[a + 1], t[a + 2] - t[a + 4] * w).setUv(-1, sa).setColor(60, 220, 255, ca);
            vc.addVertex(m, t[a] + t[a + 3] * w, t[a + 1], t[a + 2] + t[a + 4] * w).setUv(1, sa).setColor(60, 220, 255, ca);
            vc.addVertex(m, t[b] + t[b + 3] * w, t[b + 1], t[b + 2] + t[b + 4] * w).setUv(1, sb).setColor(60, 220, 255, cb);
            vc.addVertex(m, t[b] - t[b + 3] * w, t[b + 1], t[b + 2] - t[b + 4] * w).setUv(-1, sb).setColor(60, 220, 255, cb);
        }
    }

    /** How a direction's kept in a vertex (a byte each way): the shader's box turned the same, its faces where it'll trace. */
    private static int q(double v) {
        return (int) (Mth.clamp((float) v, -1.0F, 1.0F) * 127.0F);
    }

    /** What to give a vertex for a byte k to be kept (a hair over it: kept as it's cut down). */
    private static float kept(int k) {
        return (k + 0.5F * Math.signum(k)) / 127.0F;
    }

    /** The box round a car: its faces turned away from the eye (from inside it: all of them). */
    private static void box(VertexConsumer vc, Matrix4f m, Vec3 mid, Vec3 dir, int style, int kind, float speed, float travel, float lit) {
        int kx = q(dir.x), ky = q(dir.y), kz = q(dir.z);
        Vector3f f = new Vector3f(kx, ky, kz).normalize();
        Vector3f s = new Vector3f(f).cross(0, 1, 0).normalize(), u = new Vector3f(s).cross(f);
        Vector3f cam = m.transformPosition((float) mid.x, (float) mid.y, (float) mid.z, new Vector3f()).negate();
        float[] eye = {cam.dot(f), cam.dot(u), cam.dot(s)};
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        boolean[] seen = {eye[0] < LO[0], eye[0] > HI[0], eye[1] < LO[1], eye[1] > HI[1], eye[2] < LO[2], eye[2] > HI[2]};
        int sp = Mth.clamp(Math.round(speed * 40.0F), 0, 255), li = Math.round(lit * 255);
        for (int fc = 0; fc < 6; fc++) {
            if (seen[fc]) {
                continue;
            }
            for (int c : faces[fc]) {
                float x = (c & 1) == 0 ? LO[0] : HI[0], y = ((c >> 1) & 1) == 0 ? LO[1] : HI[1], z = ((c >> 2) & 1) == 0 ? LO[2] : HI[2];
                vc.addVertex(m, (float) mid.x + f.x * x + u.x * y + s.x * z, (float) mid.y + f.y * x + u.y * y + s.y * z,
                                (float) mid.z + f.z * x + u.z * y + s.z * z)
                        .setUv(c, travel).setColor(style, kind, sp, li).setNormal(kept(kx), kept(ky), kept(kz));
            }
        }
    }
}
