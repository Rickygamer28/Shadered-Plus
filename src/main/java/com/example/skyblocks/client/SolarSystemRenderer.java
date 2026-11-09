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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
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
    private static long lastLogged = -1L;

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerSolarSystemRenderer(RegisterShadersEvent event) {
            ShaderLoad.register(event, new ResourceLocation(SkyBlocksMod.MOD_ID, "solar_system"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
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
            shader.safeGetUniform("DepthOnly").set(0.0F);
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
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_solar_system_time", SolarSystemRenderer::setUp, () -> {}))
                        .setTransparencyState(new TransparencyStateShard("skyblocks_solar_system_premultiplied", SolarSystemRenderer::premultipliedOn, SolarSystemRenderer::premultipliedOff))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL) // (from inside the planetarium: its box's far side; the shader keeps one side)
                        .createCompositeState(false));

        // then the planets' depth (nothing drawn: what comes later - other blocks' effects - hidden behind them)
        static final RenderType DEPTH = create("skyblocks_solar_system_depth", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_solar_system_depth", () -> {
                            SolarSystemRenderer.setUp();
                            if (shader != null) shader.safeGetUniform("DepthOnly").set(1.0F);
                        }, () -> {
                            if (shader != null) shader.safeGetUniform("DepthOnly").set(0.0F);
                        }))
                        .setWriteMaskState(DEPTH_WRITE)
                        .setCullState(NO_CULL)
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
        if (RenderTest.ON && !holo && time % 20L == 0L && time != lastLogged) { // (render test: the speed it's drawn at)
            lastLogged = time;
            SkyBlocksMod.LOGGER.info("Shadered+ render test: orrery at {} drawn - speed {}, years {}", be.getBlockPos().toShortString(), be.speed(), years);
        }
        // the hologram: dimmer in daylight
        float day = Math.max(0.0F, Math.min(1.0F, (level.getSkyDarken(partialTick) - 0.2F) / 0.8F));
        float sky = LightTexture.sky(light) / 15.0F;
        glow = holo ? 1.0F - 0.3F * day * sky : 1.0F;
        // drawn now, with its own time and brightness (the main buffers if these can't be drawn at once)
        MultiBufferSource.BufferSource source = buffers instanceof MultiBufferSource.BufferSource s ? s : Minecraft.getInstance().renderBuffers().bufferSource();
        box(source.getBuffer(Types.MODEL), poseStack.last().pose(), holo, holo ? be.size() : 0, be.dome(), be.realSizes());
        source.endBatch(Types.MODEL);
        box(source.getBuffer(Types.DEPTH), poseStack.last().pose(), holo, holo ? be.size() : 0, be.dome(), be.realSizes());
        source.endBatch(Types.DEPTH);
        if (!MirrorRenderer.isRenderingReflection()) {
            label(be, holo, poseStack, buffers, partialTick);
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
        ITEM_INV.set(m).invert().mul(ViewState.current()); // (1.20.1: the pose holds the camera's turn, which the shader takes out first)
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
        float[] lo = holo ? new float[] {0.5F - 1.55F * s, 0.12F, 0.5F - 1.55F * s} : new float[] {-0.3F, 0.3F, -0.3F};
        float[] hi = holo ? new float[] {0.5F + 1.55F * s, 0.3F + 2.65F * s, 0.5F + 1.55F * s} : new float[] {1.3F, 1.1F, 1.3F};
        int[][] faces = {{4, 5, 7, 6}, {1, 0, 2, 3}, {5, 1, 3, 7}, {0, 4, 6, 2}, {6, 7, 3, 2}, {0, 1, 5, 4}};
        for (int[] f : faces) {
            for (int k : f) {
                vc.vertex(m, (k & 1) != 0 ? hi[0] : lo[0], (k & 2) != 0 ? hi[1] : lo[1], (k & 4) != 0 ? hi[2] : lo[2])
                        .uv(k + (holo ? 8 : 0), extra).color(255, 255, 255, 255).endVertex();
            }
        }
    }

    // ---- the planets' places (as the shader's) and their names ----

    private static final String[] NAMES = {"sun", "mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune", "moon",
            "io", "europa", "ganymede", "callisto", "titan", "comet"};
    private static final double[] ORBIT = {0, 0.15, 0.23, 0.32, 0.435, 0.61, 0.765, 0.885, 0.985};
    private static final double[] PERIOD = {1, 0.241, 0.615, 1.0, 1.881, 11.86, 29.46, 84.01, 164.8};
    private static final double[] PHASE = {0, 0.1, 0.6, 0.3, 0.85, 0.2, 0.55, 0.05, 0.7};
    private static final double[] SHOWN = {0.1, 0.026, 0.042, 0.045, 0.032, 0.085, 0.065, 0.05, 0.048, 0.015, 0.008, 0.008, 0.010, 0.008, 0.011, 0.006};
    private static final double[] REAL = {0.12, 0.006, 0.014, 0.015, 0.008, 0.055, 0.046, 0.020, 0.019, 0.004, 0.003, 0.003, 0.003, 0.003, 0.004, 0.006};
    private static final double[] JUPITER_MOON_PERIOD = {0.0048, 0.0097, 0.0196, 0.0457};
    private static final double[] JUPITER_MOON_ORBIT = {0.1, 0.11, 0.122, 0.135};

    /** Where body i is (as the shader's bodyAt): the Sun, the planets, the Moon, Jupiter's four moons, Titan, the comet. */
    private static Vec3 planet(int i, double y, Vec3 c, double r, boolean real) {
        if (i == 0) return c;
        if (i == 9) {
            Vec3 e = planet(3, y, c, r, real);
            double a = Math.PI * 2.0 * y / 0.0748, node = Math.PI * 2.0 * y / 18.6;
            double mr = r * (real ? 0.05 : 0.062);
            return e.add(Math.cos(a) * mr, Math.sin(a - node) * Math.sin(0.09) * mr, Math.sin(a) * mr);
        }
        if (i >= 10 && i <= 13) {
            Vec3 j = planet(5, y, c, r, real);
            double a = Math.PI * 2.0 * (y / JUPITER_MOON_PERIOD[i - 10] + i * 0.37);
            double mr = JUPITER_MOON_ORBIT[i - 10] * r;
            Vec3 u = new Vec3(j.x - c.x, 0.0, j.z - c.z).normalize();
            Vec3 ax = u.scale(Math.cos(0.6)).add(0.0, Math.sin(0.6), 0.0);
            return j.add(ax.scale(Math.cos(a) * mr)).add(new Vec3(-u.z, 0.0, u.x).scale(Math.sin(a) * mr));
        }
        if (i == 14) {
            Vec3 s = planet(6, y, c, r, real);
            double a = Math.PI * 2.0 * (y / 0.0437 + 0.2), mr = 0.175 * r, t = 0.47;
            return s.add(Math.cos(a) * mr, Math.sin(a) * mr * Math.sin(t), Math.sin(a) * mr * Math.cos(t));
        }
        if (i == 15) { // (the comet: Kepler's equation, a few steps - as the shader's)
            double ca = 0.55, e = 0.8, period = 9.0;
            double f = y / period + 0.35;
            double m = Math.PI * 2.0 * (f - Math.floor(f));
            double ea = m;
            for (int k = 0; k < 6; k++) ea = m + e * Math.sin(ea);
            double qx = ca * (Math.cos(ea) - e), qy = ca * Math.sqrt(1.0 - e * e) * Math.sin(ea);
            double w = 2.2, inc = 0.17;
            double px = qx * Math.cos(w) - qy * Math.sin(w), pz = qx * Math.sin(w) + qy * Math.cos(w);
            return c.add(px * r, pz * Math.sin(inc) * r, pz * r);
        }
        double a = Math.PI * 2.0 * (y / PERIOD[i] + PHASE[i]);
        double or = ORBIT[i] * r;
        return c.add(Math.cos(a) * or, Math.sin(a) * or * 0.012 * i, Math.sin(a) * or);
    }

    /** Looking at a planet (near enough): its name over it. */
    private static void label(SolarSystemBlockEntity be, boolean holo, PoseStack poseStack, MultiBufferSource buffers, float partialTick) {
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
        double r = holo ? 1.38 * sc : 0.74;
        double y = (float) (be.years(be.getLevel().getGameTime(), partialTick) % 100000.0); // (just as the shader's)
        double best = Double.MAX_VALUE;
        int hit = -1;
        Vec3 at = null;
        double atR = 0.0;
        for (int i = 0; i < NAMES.length; i++) {
            Vec3 p = planet(i, y, c, r, be.realSizes());
            double pr = (be.realSizes() ? REAL[i] : SHOWN[i]) * r * 1.5 + 0.01; // (a little bigger: easier to point at)
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

    /** (1.20.1: the block entity's getRenderBoundingBox says - kept for reference.) */
    public AABB getRenderBoundingBox(SolarSystemBlockEntity be) {
        if (!hologram(be)) return new AABB(be.getBlockPos()).inflate(0.35, 0.15, 0.35);
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
