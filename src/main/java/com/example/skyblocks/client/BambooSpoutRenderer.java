package com.example.skyblocks.client;

import com.example.skyblocks.BambooSpoutBlock;
import com.example.skyblocks.BambooSpoutBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The bamboo water spout's rocking tube (its own model, block/bamboo_spout_tube, turned round its pivot) and its
 * water: a thin trickle from the pipe into the tube, and, as it tips, the water pouring out of its open end down into
 * the pond (the pond's own deep green, as the ponds' water).
 */
public class BambooSpoutRenderer implements BlockEntityRenderer<BambooSpoutBlockEntity> {
    public static final ModelResourceLocation TUBE = ModelResourceLocation.standalone(
            new ResourceLocation(SkyBlocksMod.MOD_ID, "block/bamboo_spout_tube"));
    private static final ResourceLocation FLOW = new ResourceLocation("block/water_flow");
    private static final int WATER = 0xD82E7466; // (the ponds' water)

    public BambooSpoutRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BambooSpoutBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof BambooSpoutBlock) || be.getLevel() == null) {
            return;
        }
        double seconds = ((be.getLevel().getGameTime() % 1_728_000L) + partialTick) / 20.0;
        double p = BambooSpoutBlock.phase(be.getBlockPos(), seconds);
        float angle = BambooSpoutBlock.angle(p);
        Direction facing = state.getValue(BambooSpoutBlock.FACING);
        poseStack.pushPose();
        // (built pouring north: turned as the block's model is)
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-((facing.get2DDataValue() + 2) % 4) * 90.0F));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        // the tube, rocking round its pivot
        poseStack.pushPose();
        poseStack.translate(0.5F, 7.0F / 16.0F, 0.5F);
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        poseStack.translate(-0.5F, -7.0F / 16.0F, -0.5F);
        BakedModel tube = Minecraft.getInstance().getModelManager().getModel(TUBE);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(poseStack.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                state, tube, 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        poseStack.popPose();
        // the water
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FLOW);
        VertexConsumer vc = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        PoseStack.Pose pose = poseStack.last();
        double a = Math.toRadians(angle);
        // the trickle from the pipe's end onto the tube (where its top is, under the pipe)
        float sz = 1.5F;
        float top = (float) (7.0 + (8.0 - sz) * Math.tan(a) + 1.5 / Math.cos(a));
        stream(vc, pose, sp, 7.15F, sz, 14.5F, top, 1.1F, light, WATER);
        // pouring out of its open end, as it tips (its mouth out over the pond)
        double pour = smooth(BambooSpoutBlock.TIP + 0.22, BambooSpoutBlock.TIP + 0.32, p) * (1.0 - smooth(BambooSpoutBlock.BACK - 0.1, BambooSpoutBlock.BACK + 0.15, p));
        if (pour > 0.01) {
            float mouth = (float) (7.0 + 15.0 * Math.sin(a)) - 0.8F;
            float zm = (float) (8.0 - 15.0 * Math.cos(a));
            if (mouth > -1.4F) {
                int alpha = (int) (0xD8 * pour);
                stream(vc, pose, sp, 8.0F, zm - 0.3F, mouth, -1.5F, (float) (2.2 * pour + 0.4), light, alpha << 24 | (WATER & 0xFFFFFF));
            }
        }
        poseStack.popPose();
    }

    /** A falling ribbon of water (two crossed strips, both sides), x and z its middle, from y0 down to y1 (pixels). */
    private static void stream(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float x, float z, float y0, float y1, float width,
                               int light, int colour) {
        if (y0 <= y1) {
            return;
        }
        float h = width / 32.0F;
        float cx = x / 16.0F, cz = z / 16.0F, a = y0 / 16.0F, b = y1 / 16.0F;
        float u0 = sp.getU0(), u1 = sp.getU0() + (sp.getU1() - sp.getU0()) * 0.25F;
        float v0 = sp.getV0(), v1 = sp.getV0() + (sp.getV1() - sp.getV0()) * Math.min(1.0F, (y0 - y1) / 16.0F);
        for (int k = 0; k < 2; k++) {
            float dx = k == 0 ? h : 0.0F, dz = k == 0 ? 0.0F : h;
            quad(vc, pose, cx - dx, cz - dz, cx + dx, cz + dz, a, b, u0, v0, u1, v1, light, colour);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float ya, float yb, float u0, float v0,
                             float u1, float v1, int light, int colour) {
        v(vc, pose, x0, ya, z0, u0, v0, light, colour);
        v(vc, pose, x0, yb, z0, u0, v1, light, colour);
        v(vc, pose, x1, yb, z1, u1, v1, light, colour);
        v(vc, pose, x1, ya, z1, u1, v0, light, colour);
        v(vc, pose, x1, ya, z1, u1, v0, light, colour); // (and its back)
        v(vc, pose, x1, yb, z1, u1, v1, light, colour);
        v(vc, pose, x0, yb, z0, u0, v1, light, colour);
        v(vc, pose, x0, ya, z0, u0, v0, light, colour);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, int colour) {
        vc.vertex(pose, x, y, z).color(colour).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), 0.0F, 1.0F, 0.0F).endVertex();
    }

    private static double smooth(double a, double b, double x) {
        double k = Math.max(0.0, Math.min(1.0, (x - a) / (b - a)));
        return k * k * (3.0 - 2.0 * k);
    }

        public net.minecraft.world.phys.AABB getRenderBoundingBox(BambooSpoutBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0);
    }
}
