package com.example.skyblocks.client;

import com.example.skyblocks.PondWaterfallBlock;
import com.example.skyblocks.PondWaterfallBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A pond waterfall's water: a little pool on top of the rocks, spilling over a stone lip, dropping onto the ledge
 * below, running across it and pouring off its edge in a curve into the water in front (the game's water, tinted:
 * the pond's own green pouring into a koi pond, else the biome's water colour), the water running down it all the
 * time, white streaks of foam in it and foam where each drop lands (client/PondLife). Turned off: only the still pool.
 */
public class PondWaterfallRenderer implements BlockEntityRenderer<PondWaterfallBlockEntity> {
    static net.minecraft.client.renderer.ShaderInstance shader;

    @net.neoforged.fml.common.EventBusSubscriber(modid = com.example.skyblocks.SkyBlocksMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,
            value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @net.neoforged.bus.api.SubscribeEvent
        public static void register(net.neoforged.neoforge.client.event.RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "pond_waterfall"),
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s);
        }
    }

    /** Pouring into the koi pond: its water as the koi pond's own (shaders/core/pond_waterfall), see-through, both faces. */
    abstract static class Types extends net.minecraft.client.renderer.RenderType {
        static final net.minecraft.client.renderer.RenderType WATER = create("skyblocks_pond_waterfall",
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_pond_waterfall_time", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("PondTime").set(LavaLampRenderer.seconds());
                                net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
                                shader.safeGetUniform("Daylight").set(level == null ? 1.0F : PondLife.day(level));
                            }
                        }, () -> {}))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 0, false, false,
                    () -> {}, () -> {});
        }
    }

    private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");
    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final float PX = 1.0F / 16.0F;

    public PondWaterfallRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(PondWaterfallBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 1, p.getY() - 5, p.getZ() - 1, p.getX() + 2, p.getY() + 2, p.getZ() + 2);
    }

    @Override
    public void render(PondWaterfallBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (be.getLevel() == null || !(state.getBlock() instanceof PondWaterfallBlock) || state.getValue(PondWaterfallBlock.COLUMN)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Direction facing = state.getValue(PondWaterfallBlock.FACING);
        double land = PondWaterfallBlock.landing(be.getLevel(), pos, facing) - pos.getY();
        boolean pond = false, koi = false;
        for (int down = 0; down < 5; down++) {
            net.minecraft.world.level.block.Block b = be.getLevel().getBlockState(pos.relative(facing).below(down)).getBlock();
            if (b instanceof com.example.skyblocks.KoiPondBlock) {
                pond = true;
                koi = b == com.example.skyblocks.ModBlocks.KOI_POND.get(); // (the koi pond: its own water, not the game's)
                break;
            }
        }
        int tint = pond ? 0xFF3C8A78 : 0xFF000000 | BiomeColors.getAverageWaterColor(be.getLevel(), pos);
        TextureAtlasSprite flow = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FLOW);
        TextureAtlasSprite still = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(STILL);
        float t = LavaLampRenderer.seconds();
        // (worked out facing north - pouring to -z - then turned round the block's middle)
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180.0F));
        poseStack.translate(-0.5, 0.0, -0.5);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        // the pool on top, in its stone rim (and its water in the spillway)
        org.joml.Matrix4f world = null;
        if (koi) { // (drawn after everything, as the pond's water: client/PondLife)
            world = new org.joml.Matrix4f().translate(pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F)
                    .rotate(Axis.YP.rotationDegrees(-facing.toYRot() + 180.0F)).translate(-0.5F, 0.0F, -0.5F);
        } else {
            flat(vc, pose, still, 4 * PX, 12 * PX, 11 * PX, 14 * PX, 27 * PX, tint, 215, light);
        }
        if (!PondWaterfallBlock.pouring(state)) {
            if (koi) {
                PondLife.sawKoiFall(world, null);
            } else {
                flat(vc, pose, still, 6 * PX, 10 * PX, 9 * PX, 11 * PX, 26.2F * PX, tint, 215, light);
            }
            poseStack.popPose();
            return;
        }
        // the water's way down: along the lip, a drop to the ledge, across the ledge, then the fall into the water
        // in front: points {y, z, half width}, each a little way along
        float lipY = 25.6F * PX, ledgeY = 17.15F * PX;
        float fallTop = ledgeY, drop = (float) (fallTop - land);
        java.util.List<float[]> path = new java.util.ArrayList<>();
        path.add(new float[] {26.9F * PX, 11.5F * PX, 2.6F * PX});
        path.add(new float[] {lipY, 9.0F * PX, 2.4F * PX});
        path.add(new float[] {lipY - 0.2F * PX, 6.0F * PX, 2.3F * PX});
        for (int k = 1; k <= 6; k++) { // (the first drop: off the lip, curving down onto the ledge)
            float s = k / 6.0F;
            path.add(new float[] {lipY - (lipY - ledgeY) * s * s, 6.0F * PX - 1.6F * PX * (float) Math.sqrt(s), (2.3F + 0.5F * s) * PX});
        }
        path.add(new float[] {ledgeY, 2.6F * PX, 3.0F * PX});
        path.add(new float[] {ledgeY - 0.1F * PX, 1.0F * PX, 3.2F * PX});
        float out = 5.5F * PX + 0.05F * Math.min(drop, 3.0F);
        for (int k = 1; k <= 9; k++) { // (the fall: out over the edge, then down into the water)
            float s = k / 9.0F;
            float y = fallTop - 0.1F * PX - drop * s * s;
            path.add(new float[] {y, 1.0F * PX - out * (float) Math.sqrt(s), (3.2F + 0.8F * s) * PX});
        }
        if (koi) {
            PondLife.sawKoiFall(world, path);
        }
        // the water: running down all the time (the texture moving along it), a little ripple across it
        float run = (t * 1.1F) % 1.0F;
        float along = 0.0F;
        for (int k = 0; k + 1 < path.size() && !koi; k++) {
            float[] a = path.get(k), b = path.get(k + 1);
            float seg = (float) Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]));
            float va = along * 1.6F - run, vb = (along + seg) * 1.6F - run;
            along += seg;
            sheet(vc, pose, flow, a, b, va, vb, tint, 225, light, 0.0F);
            // white streaks of foam in it, running faster
            sheet(vc, pose, flow, a, b, va * 1.7F - run, vb * 1.7F - run, 0xFFFFFFFF, k >= path.size() - 10 ? 70 : 40, light, 0.003F);
        }
        poseStack.popPose();
        // the foam where it lands in the water (drawn after everything, over the pond)
        double reach = 0.5 - 1.0 / 16.0 + out;
        double fx = pos.getX() + 0.5 + facing.getStepX() * reach, fz = pos.getZ() + 0.5 + facing.getStepZ() * reach;
        PondLife.sawWaterfall(fx, pos.getY() + land, fz, facing);
    }

    /** A strip of falling water from a to b (both faces), its texture from va to vb along it (wrapped into the sprite). */
    private static void sheet(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float[] a, float[] b, float va, float vb, int tint,
                              int alpha, int light, float lift) {
        // (the sprite's top half only: the texture coordinates kept in it; the moving offset makes it run)
        float f0 = va - (float) Math.floor(va), f1 = f0 + (vb - va);
        if (f1 > 1.0F) { // (wraps round: split in two)
            float m = (1.0F - f0) / Math.max(f1 - f0, 1.0e-4F);
            float[] mid = {a[0] + (b[0] - a[0]) * m, a[1] + (b[1] - a[1]) * m, a[2] + (b[2] - a[2]) * m};
            strip(vc, pose, sp, a, mid, f0, 1.0F, tint, alpha, light, lift);
            strip(vc, pose, sp, mid, b, 0.0F, f1 - 1.0F, tint, alpha, light, lift);
        } else {
            strip(vc, pose, sp, a, b, f0, f1, tint, alpha, light, lift);
        }
    }

    private static void strip(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float[] a, float[] b, float f0, float f1, int tint,
                              int alpha, int light, float lift) {
        float u0 = lu(sp, 0.25F), u1 = lu(sp, 0.75F), v0 = lv(sp, f0 * 0.5F), v1 = lv(sp, f1 * 0.5F);
        float dy = b[0] - a[0], dz = b[1] - a[1];
        float l = Math.max((float) Math.sqrt(dy * dy + dz * dz), 1.0e-5F);
        float ny = -dz / l, nz = dy / l; // (its normal: out from its front)
        float ya = a[0] + ny * lift, za = a[1] + nz * lift, yb = b[0] + ny * lift, zb = b[1] + nz * lift;
        for (int side = 0; side < 2; side++) {
            float n = side == 0 ? 1.0F : -1.0F;
            v(vc, pose, 0.5F - a[2] * n, ya, za, u0, v0, tint, alpha, light, ny * n, nz * n);
            v(vc, pose, 0.5F + a[2] * n, ya, za, u1, v0, tint, alpha, light, ny * n, nz * n);
            v(vc, pose, 0.5F + b[2] * n, yb, zb, u1, v1, tint, alpha, light, ny * n, nz * n);
            v(vc, pose, 0.5F - b[2] * n, yb, zb, u0, v1, tint, alpha, light, ny * n, nz * n);
        }
    }

    /** A flat level water surface from (x0, z0) to (x1, z1) at height y (seen from above). */
    private static void flat(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float x0, float x1, float z0, float z1, float y, int tint,
                             int alpha, int light) {
        float u0 = lu(sp, x0), u1 = lu(sp, x1), v0 = lv(sp, z0), v1 = lv(sp, z1);
        v(vc, pose, x0, y, z0, u0, v0, tint, alpha, light, 0.0F, 0.0F, 1.0F);
        v(vc, pose, x0, y, z1, u0, v1, tint, alpha, light, 0.0F, 0.0F, 1.0F);
        v(vc, pose, x1, y, z1, u1, v1, tint, alpha, light, 0.0F, 0.0F, 1.0F);
        v(vc, pose, x1, y, z0, u1, v0, tint, alpha, light, 0.0F, 0.0F, 1.0F);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float w, int tint, int alpha, int light,
                          float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor((tint >> 16) & 255, (tint >> 8) & 255, tint & 255, alpha).setUv(u, w)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, ny, nz);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float w, int tint, int alpha, int light,
                          float nx, float nz, float ny) {
        vc.addVertex(pose, x, y, z).setColor((tint >> 16) & 255, (tint >> 8) & 255, tint & 255, alpha).setUv(u, w)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** A place across / down the sprite (0..1) on the atlas. */
    private static float lu(TextureAtlasSprite sp, float f) {
        return sp.getU0() + (sp.getU1() - sp.getU0()) * f;
    }

    private static float lv(TextureAtlasSprite sp, float f) {
        return sp.getV0() + (sp.getV1() - sp.getV0()) * f;
    }

    @Override
    public boolean shouldRenderOffScreen(PondWaterfallBlockEntity be) {
        return true;
    }
}
