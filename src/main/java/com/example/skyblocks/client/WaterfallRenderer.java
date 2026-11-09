package com.example.skyblocks.client;

import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.WaterfallBlock;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/** The waterfall: a sheet of water from the spout down the wall to what's below, foam and mist where it lands. */
public class WaterfallRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance shader;
    private static float time, length;
    private static final int MAX = 48;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerWaterfallShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "waterfall"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType WATER = create("skyblocks_waterfall", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_waterfall_time", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("Time").set(time);
                                shader.safeGetUniform("Length").set(length);
                            }
                        }, () -> {}))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public WaterfallRenderer(BlockEntityRendererProvider.Context context) {}

    /** How far it falls: down to the first thing below that stops water (or MAX). */
    private static int fall(ClientLevel level, BlockPos pos) {
        for (int i = 1; i <= MAX; i++) {
            BlockState s = level.getBlockState(pos.below(i));
            if (!s.isAir() && (s.blocksMotion() || !s.getFluidState().isEmpty())) {
                return i;
            }
        }
        return MAX;
    }

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null || !(be.getLevel() instanceof ClientLevel level) || !(be.getBlockState().getBlock() instanceof WaterfallBlock)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Direction facing = be.getBlockState().getValue(WaterfallBlock.FACING);
        int blocks = fall(level, pos);
        float top = 10.0F / 16, bottom = 1.0F - blocks; // (the top of the block it lands on)
        length = top - bottom;
        time = ((level.getGameTime() % 240000L) + partialTick) / 20.0F + (pos.asLong() & 63);
        float lit = Math.max(0.3F, brightness(level, pos));

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F)));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        Matrix4f m = poseStack.last().pose();
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        VertexConsumer vc = source.getBuffer(Types.WATER);
        int c = (int) (lit * 255);
        // over the lip (curving out from the spout), then straight down the wall
        float z0 = 11.0F / 16, z1 = 9.5F / 16, lip = 1.5F / 16;
        quad(vc, m, 2.5F / 16, 13.5F / 16, top + lip, z0, top, z1, 0.0F, lip, c, 200);
        quad(vc, m, 2.5F / 16, 13.5F / 16, top, z1, bottom, z1 + 0.5F / 16, lip, lip + length, c, 200);
        // the foam where it lands
        float pz = z1 - 0.2F, r = 0.75F;
        vc.addVertex(m, 0.5F - r, bottom + 0.02F, pz - r).setUv(0, 0).setColor(c, c, c, 255);
        vc.addVertex(m, 0.5F - r, bottom + 0.02F, pz + r).setUv(0, 1).setColor(c, c, c, 255);
        vc.addVertex(m, 0.5F + r, bottom + 0.02F, pz + r).setUv(1, 1).setColor(c, c, c, 255);
        vc.addVertex(m, 0.5F + r, bottom + 0.02F, pz - r).setUv(1, 0).setColor(c, c, c, 255);
        source.endBatch(Types.WATER);
        poseStack.popPose();

        // splashes and a little mist where it lands (now and then, not every frame)
        long now = level.getGameTime();
        if (now != be.clientTick && !net.minecraft.client.Minecraft.getInstance().isPaused()) {
            be.clientTick = now;
            Direction out = facing;
            double bx = pos.getX() + 0.5 + out.getStepX() * 0.1, bz = pos.getZ() + 0.5 + out.getStepZ() * 0.1, by = pos.getY() + bottom + 0.1;
            if (level.random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SPLASH, bx + (level.random.nextDouble() - 0.5) * 0.6, by, bz + (level.random.nextDouble() - 0.5) * 0.6, 0, 0.1, 0);
            }
            if (level.random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.CLOUD, bx + (level.random.nextDouble() - 0.5) * 0.5, by + 0.1, bz + (level.random.nextDouble() - 0.5) * 0.5,
                        out.getStepX() * 0.01, 0.02, out.getStepZ() * 0.01);
            }
        }
    }

    private static float brightness(ClientLevel level, BlockPos pos) {
        int packed = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos.relative(level.getBlockState(pos).getValue(WaterfallBlock.FACING)));
        float sky = LightTexture.sky(packed) / 15.0F * (1.0F - level.getSkyDarken() / 15.0F), block = LightTexture.block(packed) / 15.0F;
        return Math.min(1.0F, Math.max(sky, block) * 0.9F + 0.1F);
    }

    /** A strip from (y0, z0) at the top to (y1, z1) at the bottom, across x0-x1; v along it. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float x1, float y0, float z0, float y1, float z1, float v0, float v1, int c, int a) {
        vc.addVertex(m, x0, y0, z0).setUv(1, v0).setColor(c, c, c, a);
        vc.addVertex(m, x1, y0, z0).setUv(0, v0).setColor(c, c, c, a);
        vc.addVertex(m, x1, y1, z1).setUv(0, v1).setColor(c, c, c, a);
        vc.addVertex(m, x0, y1, z1).setUv(1, v1).setColor(c, c, c, a);
    }

    @Override
    public boolean shouldRenderOffScreen(SimpleRenderedBlockEntity be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SimpleRenderedBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0, -MAX, 0).inflate(1.0);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
