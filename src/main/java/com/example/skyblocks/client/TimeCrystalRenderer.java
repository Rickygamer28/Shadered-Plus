package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TimeCrystalBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A time crystal (TimeCrystalBlockEntity): the crystal and its rune rings, in 3D (a box traced through by
 * shaders/core/time_crystal, as the power core's); while it runs, a clock face on the ground round it
 * (shaders/core/clock_face) and the dome over what it hurries (shaders/core/time_dome: its sky inside, a glassy rim
 * outside), fading in and out; and its violet light (ShaderLightRenderer). Drawn after the coloured lights (LatePass),
 * each part drawn right away in this order (not left to a buffer source: Iris's, installed even with no shader pack on,
 * draws everything later in an order of its own - the crystal before the dome, the dome missing round the crystal):
 * the dome's far side, the crystal, the clock face, the rings' glow, the dome's near side.
 */
public class TimeCrystalRenderer implements BlockEntityRenderer<TimeCrystalBlockEntity> {
    static ShaderInstance crystalShader, domeShader, faceShader;
    /** The box round the crystal: its middle (above the block's base) and half-size. */
    private static final float CY = 1.25F, HALF = 1.45F;
    private static final int AROUND = 64, UP = 16;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "time_crystal"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> crystalShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "time_dome"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> domeShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "clock_face"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> faceShader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CRYSTAL = create("skyblocks_time_crystal", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> crystalShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        /** The rings' glow, drawn without writing depth (a haze written into the depth hid the dome behind it). */
        static final RenderType GLOW = create("skyblocks_time_crystal_glow", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> crystalShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
        static final RenderType DOME_IN = create("skyblocks_time_dome_in", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> domeShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        static final RenderType DOME_OUT = create("skyblocks_time_dome_out", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> domeShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
        static final RenderType FACE = create("skyblocks_clock_face", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> faceShader))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public TimeCrystalRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TimeCrystalBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (crystalShader == null || domeShader == null || faceShader == null) {
            return;
        }
        float on = Mth.clamp(be.shownOn, 0.0F, 1.0F);
        if (on > 0.05F) {
            ShaderLightRenderer.add(pose, 0.5F, CY, 0.5F, 4.0F + 3.0F * on, 0xB070FF);
        }
        Matrix4f m = new Matrix4f(pose.last().pose());
        float spin = be.spin + be.spinSpeed * partialTick;
        int speed = be.speedIndex();
        float radius = be.radius() + 0.5F;
        boolean dome = be.dome() && on > 0.01F;
        // (with a shader pack - drawn into Shadered+'s own target, laid over the pack's image where it wrote depth: the
        // crystal and its glow drawn together, writing depth; without, the crystal itself and then its glow, which doesn't)
        boolean pack = com.example.skyblocks.client.iris.IrisDefer.drawing();
        Runnable draw = () -> {
            if (dome) {
                drawNow(Types.DOME_IN, vc -> dome(vc, m, radius, speed, 0, on));
            }
            drawNow(Types.CRYSTAL, vc -> crystal(vc, m, spin, on, speed, pack ? 0 : 1));
            if (on > 0.01F) {
                drawNow(Types.FACE, vc -> face(vc, m, radius, speed, on));
            }
            if (!pack) {
                drawNow(Types.GLOW, vc -> crystal(vc, m, spin, on, speed, 2));
            }
            if (dome) {
                drawNow(Types.DOME_OUT, vc -> dome(vc, m, radius, speed, 255, on));
            }
        };
        if (!LatePass.later(src -> draw.run())) {
            draw.run();
        }
    }

    /** One part, drawn right now. */
    private static void drawNow(RenderType type, java.util.function.Consumer<VertexConsumer> emit) {
        com.mojang.blaze3d.vertex.BufferBuilder builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(type.mode(), type.format());
        emit.accept(builder);
        com.mojang.blaze3d.vertex.MeshData mesh = builder.build();
        if (mesh != null) {
            type.draw(mesh);
        }
    }

    /**
     * The crystal's box: its faces turned away from the eye (from inside it: all), as the power core's. part: 0 the
     * crystal and its rings' glow, 1 the crystal alone, 2 the glow alone.
     */
    private static void crystal(VertexConsumer vc, Matrix4f m, float spin, float on, int speed, int part) {
        Vector3f centre = m.transformPosition(0.5F, CY, 0.5F, new Vector3f());
        float ex = -centre.x, ey = -centre.y, ez = -centre.z;
        int turn = (int) (Mth.frac(spin) * 65536.0F) & 0xFFFF;
        int r = turn >> 8, a = turn & 255, g = Math.round(on * 255), b = speed + 4 * part;
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        float[] towards = {-ex - HALF, ex - HALF, -ey - HALF, ey - HALF, -ez - HALF, ez - HALF};
        for (int f = 0; f < 6; f++) {
            if (towards[f] > 0) {
                continue;
            }
            for (int c : faces[f]) {
                float x = ((c & 1) * 2 - 1) * HALF, y = (((c >> 1) & 1) * 2 - 1) * HALF, z = (((c >> 2) & 1) * 2 - 1) * HALF;
                vc.addVertex(m, 0.5F + x, CY + y, 0.5F + z).setColor(r, g, b, a).setUv(c, HALF);
            }
        }
    }

    /** The clock face: a square on the ground (the shader cuts the circle), just above it. */
    private static void face(VertexConsumer vc, Matrix4f m, float radius, int speed, float on) {
        int g = Math.round(radius), a = Math.round(on * 255);
        float s = radius + 0.3F, y = 0.03F;
        float k = s / radius;
        vc.addVertex(m, 0.5F - s, y, 0.5F - s).setColor(speed, g, 0, a).setUv(-k, -k);
        vc.addVertex(m, 0.5F - s, y, 0.5F + s).setColor(speed, g, 0, a).setUv(-k, k);
        vc.addVertex(m, 0.5F + s, y, 0.5F + s).setColor(speed, g, 0, a).setUv(k, k);
        vc.addVertex(m, 0.5F + s, y, 0.5F - s).setColor(speed, g, 0, a).setUv(k, -k);
    }

    /** The dome: a half ball (side 0: its inside, the sky; 255: its outside, the rim). */
    private static void dome(VertexConsumer vc, Matrix4f m, float radius, int speed, int side, float on) {
        int a = Math.round(on * 255);
        for (int i = 0; i < AROUND; i++) {
            float az0 = i * Mth.TWO_PI / AROUND, az1 = (i + 1) * Mth.TWO_PI / AROUND;
            for (int j = 0; j < UP; j++) {
                float el0 = j * Mth.HALF_PI / UP, el1 = (j + 1) * Mth.HALF_PI / UP;
                point(vc, m, radius, az0, el0, speed, side, a);
                point(vc, m, radius, az1, el0, speed, side, a);
                point(vc, m, radius, az1, el1, speed, side, a);
                point(vc, m, radius, az0, el1, speed, side, a);
            }
        }
    }

    private static void point(VertexConsumer vc, Matrix4f m, float radius, float az, float el, int speed, int side, int a) {
        float c = Mth.cos(el);
        vc.addVertex(m, 0.5F + radius * c * Mth.cos(az), radius * Mth.sin(el), 0.5F + radius * c * Mth.sin(az))
                .setColor(speed, side, 0, a).setUv(az, el);
    }

    @Override
    public boolean shouldRenderOffScreen(TimeCrystalBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(TimeCrystalBlockEntity be) {
        double r = be.radius() + 1.0;
        return new AABB(be.getBlockPos()).inflate(r, 2.0, r).expandTowards(0, r, 0);
    }
}
