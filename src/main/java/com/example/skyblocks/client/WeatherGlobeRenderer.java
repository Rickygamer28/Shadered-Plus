package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.WeatherGlobeBlockEntity;
import com.example.skyblocks.island.IslandData;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The weather globe: the inside of the ball (sky, and the little world - or the Sky Island put in it), then the glass
 * over it with the rain or snow inside (shaders/core/weather_globe), each drawn at once with this globe's weather.
 */
public class WeatherGlobeRenderer implements BlockEntityRenderer<WeatherGlobeBlockEntity> {
    static ShaderInstance shader;
    private static final Matrix4f POSE = new Matrix4f(), POSE_INV = new Matrix4f();
    private static float time, sunY, rain, thunder, snowy, shake, flash, hasIsland, pass;
    /** Where the island's ground sits in the ball (the shader's earth is under it). */
    private static final float ISLAND_Y = 0.37F;
    private static final float X0 = 0.14F, X1 = 0.86F, Y0 = 0.255F, Y1 = 0.975F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerWeatherGlobeShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "weather_globe"), DefaultVertexFormat.POSITION, s -> shader = s);
        }
    }

    private static void setUp() {
        if (shader == null) {
            return;
        }
        shader.safeGetUniform("PoseMat").set(POSE);
        shader.safeGetUniform("PoseInv").set(POSE_INV);
        shader.safeGetUniform("Pass").set(pass);
        shader.safeGetUniform("Time").set(time);
        shader.safeGetUniform("SunY").set(sunY);
        shader.safeGetUniform("Rain").set(rain);
        shader.safeGetUniform("Thunder").set(thunder);
        shader.safeGetUniform("Snowy").set(snowy);
        shader.safeGetUniform("Shake").set(shake);
        shader.safeGetUniform("Flash").set(flash);
        shader.safeGetUniform("HasIsland").set(hasIsland);
    }

    abstract static class Types extends RenderType {
        static final RenderType INSIDE = create("skyblocks_weather_globe_inside", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_weather_globe_in", () -> {
                            pass = 0.0F;
                            setUp();
                        }, () -> {}))
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        static final RenderType GLASS = create("skyblocks_weather_globe_glass", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_weather_globe_out", () -> {
                            pass = 1.0F;
                            setUp();
                        }, () -> {}))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public WeatherGlobeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(WeatherGlobeBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null || !(be.getLevel() instanceof ClientLevel level)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        long now = level.getGameTime();
        time = ((now % 240000L) + partialTick) / 20.0F;
        sunY = Mth.cos(level.getTimeOfDay(partialTick) * Mth.TWO_PI);
        rain = level.getRainLevel(partialTick);
        thunder = level.getThunderLevel(partialTick);
        snowy = level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW ? 1.0F : 0.0F;
        shake = Math.max(0.0F, (now - be.shakenAt() + partialTick) / 20.0F);
        flash = level.getSkyFlashTime() > 0 ? 1.0F : 0.0F;
        IslandData island = be.island();
        hasIsland = island != null ? 1.0F : 0.0F;
        POSE.set(poseStack.last().pose());
        POSE_INV.set(POSE).invert();

        if (be.base() != null) {
            drawBase(be.base(), level, pos, poseStack, buffers, light, overlay);
        }
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        box(source.getBuffer(Types.INSIDE), poseStack.last().pose());
        source.endBatch(Types.INSIDE);
        if (island != null) {
            poseStack.pushPose();
            poseStack.translate(0.5F, ISLAND_Y, 0.5F);
            // (as big as fits the ball's lower part; anything past the glass left out)
            be.clientView = com.example.skyblocks.client.island.IslandInGlobe.draw(be.clientView, island, level, pos, poseStack, source, partialTick,
                    0.54F, 0.56F, 0.615F - ISLAND_Y, 0.345F);
            poseStack.popPose();
            source.endBatch(); // (the island before the glass over it)
        }
        box(source.getBuffer(Types.GLASS), poseStack.last().pose());
        source.endBatch(Types.GLASS);
    }

    /**
     * A base made of another block: the base's box (2-14 across, 3 pixels tall) with that block's textures - each side
     * the block's own for that side, its lowest 3 pixels; tinted as the block would be (grass, leaves).
     */
    private static void drawBase(net.minecraft.world.level.block.state.BlockState base, ClientLevel level, BlockPos pos, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, int overlay) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.resources.model.BakedModel model = mc.getBlockRenderer().getBlockModel(base);
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42L);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose last = poseStack.last();
        float x0 = 2.0F / 16, x1 = 14.0F / 16, y0 = 0.0F, y1 = 3.0F / 16;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads;
            try {
                quads = model.getQuads(base, d, random, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            } catch (RuntimeException e) {
                quads = java.util.List.of();
            }
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = quads.isEmpty() ? model.getParticleIcon() : quads.get(0).getSprite();
            int tint = -1;
            if (!quads.isEmpty() && quads.get(0).isTinted()) {
                tint = 0xFF000000 | mc.getBlockColors().getColor(base, level, pos, quads.get(0).getTintIndex());
            }
            float[][] v; // (four corners, each x y z u v - u, v as parts of the texture)
            switch (d) {
                case UP -> v = new float[][] {{x0, y1, x0, 2, 2}, {x0, y1, x1, 2, 14}, {x1, y1, x1, 14, 14}, {x1, y1, x0, 14, 2}};
                case DOWN -> v = new float[][] {{x0, y0, x0, 2, 2}, {x1, y0, x0, 14, 2}, {x1, y0, x1, 14, 14}, {x0, y0, x1, 2, 14}};
                case NORTH -> v = new float[][] {{x0, y0, x0, 14, 16}, {x0, y1, x0, 14, 13}, {x1, y1, x0, 2, 13}, {x1, y0, x0, 2, 16}};
                case SOUTH -> v = new float[][] {{x0, y0, x1, 2, 16}, {x1, y0, x1, 14, 16}, {x1, y1, x1, 14, 13}, {x0, y1, x1, 2, 13}};
                case WEST -> v = new float[][] {{x0, y0, x0, 2, 16}, {x0, y0, x1, 14, 16}, {x0, y1, x1, 14, 13}, {x0, y1, x0, 2, 13}};
                default -> v = new float[][] {{x1, y0, x0, 14, 16}, {x1, y1, x0, 14, 13}, {x1, y1, x1, 2, 13}, {x1, y0, x1, 2, 16}};
            }
            float shade = switch (d) {
                case UP -> 1.0F;
                case DOWN -> 0.5F;
                case NORTH, SOUTH -> 0.8F;
                default -> 0.6F;
            };
            int r = (int) (((tint >> 16) & 255) * shade), g = (int) (((tint >> 8) & 255) * shade), b = (int) ((tint & 255) * shade);
            int colour = 0xFF000000 | (r << 16) | (g << 8) | b;
            for (float[] c : v) {
                vc.addVertex(last, c[0], c[1], c[2]).setColor(colour).setUv(sprite.getU(c[3] / 16.0F), sprite.getV(c[4] / 16.0F))
                        .setOverlay(overlay).setLight(light).setNormal(last, d.getStepX(), d.getStepY(), d.getStepZ());
            }
        }
    }

    /** As an item: the ball on its base, the weather where you are, at once with this pose. */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (shader == null || mc.level == null) {
            return;
        }
        ClientLevel level = mc.level;
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        time = ((level.getGameTime() % 240000L) + partialTick) / 20.0F;
        sunY = Mth.cos(level.getTimeOfDay(partialTick) * Mth.TWO_PI);
        rain = level.getRainLevel(partialTick);
        thunder = level.getThunderLevel(partialTick);
        BlockPos at = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        snowy = level.getBiome(at).value().getPrecipitationAt(at) == Biome.Precipitation.SNOW ? 1.0F : 0.0F;
        shake = 100.0F;
        flash = level.getSkyFlashTime() > 0 ? 1.0F : 0.0F;
        hasIsland = 0.0F;
        POSE.set(poseStack.last().pose());
        POSE_INV.set(POSE).invert();
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        box(source.getBuffer(Types.INSIDE), poseStack.last().pose());
        source.endBatch(Types.INSIDE);
        box(source.getBuffer(Types.GLASS), poseStack.last().pose());
        source.endBatch(Types.GLASS);
    }

    /** The box round the ball. */
    private static void box(VertexConsumer vc, Matrix4f m) {
        float[][] faces = {
                {X0, Y0, X0, X1, Y0, X0, X1, Y0, X1, X0, Y0, X1}, {X0, Y1, X0, X0, Y1, X1, X1, Y1, X1, X1, Y1, X0},
                {X0, Y0, X0, X0, Y1, X0, X1, Y1, X0, X1, Y0, X0}, {X0, Y0, X1, X1, Y0, X1, X1, Y1, X1, X0, Y1, X1},
                {X0, Y0, X0, X0, Y0, X1, X0, Y1, X1, X0, Y1, X0}, {X1, Y0, X0, X1, Y1, X0, X1, Y1, X1, X1, Y0, X1}};
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) {
                vc.addVertex(m, f[i], f[i + 1], f[i + 2]);
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
