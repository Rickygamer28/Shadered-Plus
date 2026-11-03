package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SolarSystemBlock;
import com.example.skyblocks.SolarSystemBlockEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The solar systems: a box round the model, traced in shaders/core/solar_system (each drawn at once, with its own
 * time, sizes and brightness). Looking at a planet: its name above it - the planets' places worked out here just as
 * in the shader.
 */
public class SolarSystemRenderer implements BlockEntityRenderer<SolarSystemBlockEntity> {
    static ShaderInstance shader;
    // the model being drawn now (for the uniforms)
    private static float years, spin, glow = 1.0F, itemMode;
    static final Matrix4f ITEM_INV = new Matrix4f();

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerSolarSystemRenderer(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "solar_system"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    private static void setUp() {
        if (shader != null) {
            MusicReactor.tick();
            shader.safeGetUniform("SimYears").set(years);
            shader.safeGetUniform("SpinTime").set(spin);
            shader.safeGetUniform("Boost").set(Math.min(MusicReactor.bass() * 1.5F, 1.0F));
            shader.safeGetUniform("Glow").set(glow);
            shader.safeGetUniform("ItemMode").set(itemMode);
            shader.safeGetUniform("LampInv").set(ITEM_INV);
        }
    }

    private static void premultipliedOn() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    private static void premultipliedOff() {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    abstract static class Types extends RenderType {
        static final RenderType MODEL = create("skyblocks_solar_system", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_solar_system_time", SolarSystemRenderer::setUp, () -> {}))
                        .setTransparencyState(new TransparencyStateShard("skyblocks_solar_system_premultiplied", SolarSystemRenderer::premultipliedOn, SolarSystemRenderer::premultipliedOff))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL) // (from inside the planetarium: its box's far side; the shader keeps one side)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public SolarSystemRenderer(BlockEntityRendererProvider.Context context) {}

    private static boolean hologram(SolarSystemBlockEntity be) {
        return be.getBlockState().getBlock() instanceof SolarSystemBlock b && b.hologram();
    }

    @Override
    public void render(SolarSystemBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null || !(be.getLevel() instanceof ClientLevel level)) {
            return;
        }
        boolean holo = hologram(be);
        long time = level.getGameTime();
        years = (float) (be.years(time, partialTick) % 100000.0);
        spin = (float) (be.spin(time, partialTick) % 36000.0);
        itemMode = 0.0F;
        // the hologram: dimmer in daylight
        float day = Math.max(0.0F, Math.min(1.0F, (level.getSkyDarken(partialTick) - 0.2F) / 0.8F));
        float sky = LightTexture.sky(light) / 15.0F;
        glow = holo ? 1.0F - 0.3F * day * sky : 1.0F;
        // drawn now, with its own time and brightness (the main buffers if these can't be drawn at once)
        MultiBufferSource.BufferSource source = buffers instanceof MultiBufferSource.BufferSource s ? s : Minecraft.getInstance().renderBuffers().bufferSource();
        box(source.getBuffer(Types.MODEL), poseStack.last().pose(), holo, holo ? be.size() : 0, be.dome(), be.realSizes());
        source.endBatch(Types.MODEL);
        if (!MirrorRenderer.isRenderingReflection()) {
            label(be, holo, poseStack, buffers);
        }
    }

    /** A model as an item, drawn at once with this item's matrix (the planetarium shrunk to fit). */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers, boolean holo) {
        if (shader == null) {
            return;
        }
        poseStack.pushPose();
        if (holo) {
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.scale(0.34F, 0.34F, 0.34F);
            poseStack.translate(-0.5F, 0.0F, -0.5F);
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert();
        Minecraft mc = Minecraft.getInstance();
        float t = mc.level == null ? 0.0F : (mc.level.getGameTime() % 72000L) / 20.0F;
        years = 26.3F + t / 120.0F;
        spin = t;
        glow = 1.0F;
        itemMode = 1.0F;
        MultiBufferSource.BufferSource source = buffers instanceof MultiBufferSource.BufferSource s ? s : mc.renderBuffers().bufferSource();
        box(source.getBuffer(Types.MODEL), m, holo, 0, true, false);
        source.endBatch(Types.MODEL);
        itemMode = 0.0F;
        poseStack.popPose();
    }

    /** The box round the model, its faces facing out (the same numbers as the shader's). */
    static void box(VertexConsumer vc, Matrix4f m, boolean holo, int size, boolean dome, boolean real) {
        float s = size == 2 ? 4.0F : size == 1 ? 2.0F : 1.0F;
        int extra = (dome ? 1 : 0) + (real ? 2 : 0) + 4 * size;
        float[] lo = holo ? new float[] {0.5F - 1.55F * s, 0.12F, 0.5F - 1.55F * s} : new float[] {-0.18F, 0.3F, -0.18F};
        float[] hi = holo ? new float[] {0.5F + 1.55F * s, 0.3F + 2.65F * s, 0.5F + 1.55F * s} : new float[] {1.18F, 1.08F, 1.18F};
        int[][] faces = {{4, 5, 7, 6}, {1, 0, 2, 3}, {5, 1, 3, 7}, {0, 4, 6, 2}, {6, 7, 3, 2}, {0, 1, 5, 4}};
        for (int[] f : faces) {
            for (int k : f) {
                vc.addVertex(m, (k & 1) != 0 ? hi[0] : lo[0], (k & 2) != 0 ? hi[1] : lo[1], (k & 4) != 0 ? hi[2] : lo[2])
                        .setUv(k + (holo ? 8 : 0), extra).setColor(255, 255, 255, 255);
            }
        }
    }

    // ---- the planets' places (as the shader's) and their names ----

    private static final String[] NAMES = {"sun", "mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune", "moon"};
    private static final double[] ORBIT = {0, 0.15, 0.23, 0.31, 0.40, 0.60, 0.74, 0.86, 0.97};
    private static final double[] PERIOD = {1, 0.241, 0.615, 1.0, 1.881, 11.86, 29.46, 84.01, 164.8};
    private static final double[] PHASE = {0, 0.1, 0.6, 0.3, 0.85, 0.2, 0.55, 0.05, 0.7};
    private static final double[] SHOWN = {0.105, 0.028, 0.046, 0.050, 0.036, 0.105, 0.085, 0.060, 0.058, 0.015};
    private static final double[] REAL = {0.12, 0.006, 0.014, 0.015, 0.008, 0.055, 0.046, 0.020, 0.019, 0.004};

    private static Vec3 planet(int i, double y, Vec3 c, double r) {
        if (i == 0) return c;
        if (i == 9) {
            Vec3 e = planet(3, y, c, r);
            double a = Math.PI * 2.0 * y / 0.0748, node = Math.PI * 2.0 * y / 18.6;
            double mr = r * 0.085;
            return e.add(Math.cos(a) * mr, Math.sin(a - node) * Math.sin(0.09) * mr, Math.sin(a) * mr);
        }
        double a = Math.PI * 2.0 * (y / PERIOD[i] + PHASE[i]);
        double or = ORBIT[i] * r;
        return c.add(Math.cos(a) * or, Math.sin(a) * or * 0.012 * i, Math.sin(a) * or);
    }

    /** Looking at a planet (near enough): its name over it. */
    private static void label(SolarSystemBlockEntity be, boolean holo, PoseStack poseStack, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        BlockPos pos = be.getBlockPos();
        Vec3 ro = camera.getPosition().subtract(Vec3.atLowerCornerOf(pos));
        double sc = holo ? be.scale() : 1.0;
        if (ro.distanceToSqr(0.5, 1.0, 0.5) > (holo ? 14.0 * 14.0 * sc * sc : 7.0 * 7.0)) {
            return;
        }
        Vec3 rd = new Vec3(camera.getLookVector());
        Vec3 c = holo ? new Vec3(0.5, 0.3 + 1.15 * sc, 0.5) : new Vec3(0.5, 0.74, 0.5);
        double r = holo ? 1.38 * sc : 0.62;
        double y = be.years(be.getLevel().getGameTime(), mc.getTimer().getGameTimeDeltaPartialTick(false));
        double best = Double.MAX_VALUE;
        int hit = -1;
        Vec3 at = null;
        double atR = 0.0;
        for (int i = 0; i <= 9; i++) {
            Vec3 p = planet(i, y, c, r);
            double pr = (be.realSizes() ? REAL[i] : SHOWN[i]) * r * (holo || i == 0 ? 1.0 : 1.2) * 1.5 + 0.01; // (a little bigger: easier to point at)
            Vec3 oc = ro.subtract(p);
            double b = oc.dot(rd), q = oc.lengthSqr() - pr * pr, h = b * b - q;
            if (h < 0.0) continue;
            double t = -b - Math.sqrt(h);
            if (t > 0.0 && t < best) {
                best = t;
                hit = i;
                at = p;
                atR = pr;
            }
        }
        if (hit < 0) {
            return;
        }
        Font font = mc.font;
        Component name = Component.translatable("solar.skyblocks." + NAMES[hit]);
        poseStack.pushPose();
        poseStack.translate(at.x, at.y + atR + (holo ? 0.06 * sc : 0.03), at.z);
        poseStack.mulPose(camera.rotation());
        float s = holo ? 0.012F * (float) Math.sqrt(sc) : 0.0055F;
        s = Math.min(s, 0.004F * (float) ro.distanceTo(at)); // (close up - standing in the planetarium - no bigger on screen)
        poseStack.scale(s, -s, s);
        float x = -font.width(name) / 2.0F;
        font.drawInBatch(name, x, 0.0F, 0xFFFFE6A0, false, poseStack.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0x55000000, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(SolarSystemBlockEntity be) {
        if (!hologram(be)) return new AABB(be.getBlockPos()).inflate(0.2, 0.1, 0.2);
        double s = be.scale();
        return new AABB(be.getBlockPos()).inflate(1.55 * s - 0.4, 0.0, 1.55 * s - 0.4).expandTowards(0.0, 2.65 * s - 0.7, 0.0);
    }

    @Override
    public boolean shouldRenderOffScreen(SolarSystemBlockEntity be) {
        return true; // (drawn whether or not the base's part of the world is: a planetarium reaches far past it)
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
