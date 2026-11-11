package com.example.skyblocks.client;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.PhotoFrameBlock;
import com.example.skyblocks.PhotoFrameBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Photo frames: the picture in each; and the bigger frames themselves (the small ones are block models) - medium in light
 * wood, large gilded; on a wall, two or three blocks wide; standing, on a wooden easel, leaning back a little.
 */
public class PhotoFrameRenderer implements BlockEntityRenderer<PhotoFrameBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/misc/white.png");
    private static final ResourceLocation BIRCH = ResourceLocation.withDefaultNamespace("block/birch_planks");
    private static final ResourceLocation GOLD = ResourceLocation.withDefaultNamespace("block/gold_block");
    private static final ResourceLocation EASEL = ResourceLocation.withDefaultNamespace("block/stripped_spruce_log");

    public PhotoFrameRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PhotoFrameBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof PhotoFrameBlock)) {
            return;
        }
        ModComponents.Photo photo = be.photo();
        ResourceLocation tex = photo == null ? null : PhotoCache.get(photo.id());
        PhotoFrameBlock.Size size = state.getValue(PhotoFrameBlock.SIZE);
        boolean standing = state.getValue(PhotoFrameBlock.STANDING);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.getValue(PhotoFrameBlock.FACING).toYRot()));
        poseStack.translate(-0.5F, 0, -0.5F);
        // (as built facing north: the picture's front towards -z)
        if (size == PhotoFrameBlock.Size.SMALL) {
            float x0 = (standing ? 4 : 2) / 16.0F, x1 = (standing ? 12 : 14) / 16.0F;
            float y0 = (standing ? 2 : 3.5F) / 16.0F, y1 = (standing ? 8 : 12.5F) / 16.0F;
            float z = (standing ? 7.48F : 14.98F) / 16.0F;
            if (tex != null) {
                picture(buffers.getBuffer(RenderType.entitySolid(tex)), poseStack.last(), x0, y0, x1, y1, z, light, overlay);
            }
            poseStack.popPose();
            return;
        }
        boolean large = size == PhotoFrameBlock.Size.LARGE;
        TextureAtlasSprite wood = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(large ? GOLD : BIRCH);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        float w, h, border = large ? 2.2F / 16 : 1.6F / 16, depth = large ? 2.0F / 16 : 1.5F / 16;
        if (standing) {
            // an easel: two front legs and a back one, a ledge; the picture resting on it, leaning back
            w = large ? 1.5F : 1.0F;
            h = w * 0.75F;
            float base = large ? 0.55F : 0.5F, top = base + h + border * 2;
            TextureAtlasSprite leg = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(EASEL);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0, 0.5F);
            poseStack.mulPose(Axis.XP.rotationDegrees(10.0F)); // (leaning back: its top towards +z)
            float half = w / 2 + border, legW = 1.2F / 16;
            // (the front legs just behind the picture, showing above and below it)
            box(solid, poseStack.last(), leg, -half * 0.75F - legW / 2, 0, depth, -half * 0.75F + legW / 2, top + 0.1F, depth + legW, 0xFFFFFFFF, light, overlay);
            box(solid, poseStack.last(), leg, half * 0.75F - legW / 2, 0, depth, half * 0.75F + legW / 2, top + 0.1F, depth + legW, 0xFFFFFFFF, light, overlay);
            box(solid, poseStack.last(), leg, -half - 0.05F, base - border - 1.6F / 16, -2.4F / 16, half + 0.05F, base - border, 0.4F / 16, 0xFFFFFFFF, light,
                    overlay); // (the ledge)
            frame(solid, buffers, poseStack.last(), wood, tex, -w / 2, base, w / 2, base + h, 0, border, depth, light, overlay);
            poseStack.popPose();
            poseStack.pushPose();
            poseStack.translate(0.5F, 0, 0.5F + 0.62F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-13.0F)); // (the back leg, from behind up to the top, sloping the other way)
            box(solid, poseStack.last(), leg, -legW / 2, 0, 0, legW / 2, top * 0.9F, legW, 0xFFFFFFFF, light, overlay);
            poseStack.popPose();
        } else {
            // on the wall: centred on its block, two (three) blocks wide
            w = large ? 3.0F : 2.0F;
            h = w * 0.75F;
            frame(solid, buffers, poseStack.last(), wood, tex, 0.5F - w / 2 + border, 0.5F - h / 2 + border, 0.5F + w / 2 - border, 0.5F + h / 2 - border,
                    1.0F - depth, border, depth, light, overlay);
        }
        poseStack.popPose();
    }

    /**
     * A frame round the picture area (x0, y0) .. (x1, y1), its front at z (the picture just behind it), the border round it,
     * as deep as given (towards +z); a white backing; the photo if there is one.
     */
    private static void frame(VertexConsumer solid, MultiBufferSource buffers, PoseStack.Pose pose, TextureAtlasSprite wood, ResourceLocation tex,
                              float x0, float y0, float x1, float y1, float z, float b, float depth, int light, int overlay) {
        int c = 0xFFFFFFFF;
        box(solid, pose, wood, x0 - b, y1, z, x1 + b, y1 + b, z + depth, c, light, overlay);
        box(solid, pose, wood, x0 - b, y0 - b, z, x1 + b, y0, z + depth, c, light, overlay);
        box(solid, pose, wood, x0 - b, y0, z, x0, y1, z + depth, c, light, overlay);
        box(solid, pose, wood, x1, y0, z, x1 + b, y1, z + depth, c, light, overlay);
        float back = z + depth * 0.5F;
        VertexConsumer white = buffers.getBuffer(RenderType.entitySolid(WHITE));
        picture(white, pose, x0, y0, x1, y1, back, light, overlay);
        if (tex != null) {
            picture(buffers.getBuffer(RenderType.entitySolid(tex)), pose, x0, y0, x1, y1, back - 0.002F, light, overlay);
        }
    }

    /** The picture (or the backing) facing -z, at z. */
    private static void picture(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z, int light, int overlay) {
        v(vc, pose, x1, y1, z, 0, 0, light, overlay);
        v(vc, pose, x1, y0, z, 0, 1, light, overlay);
        v(vc, pose, x0, y0, z, 1, 1, light, overlay);
        v(vc, pose, x0, y1, z, 1, 0, light, overlay);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, int overlay) {
        vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, -1);
    }

    /** A box (blocks) with a block texture: each side the part of the sprite as big as it (wound to be seen from outside). */
    private static void box(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float x0, float y0, float z0, float x1, float y1, float z1,
                            int colour, int light, int overlay) {
        float ux = Math.min(1, x1 - x0), uy = Math.min(1, y1 - y0), uz = Math.min(1, z1 - z0);
        q(vc, pose, sp, colour, light, overlay, 0, 1, 0, ux, uz, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        q(vc, pose, sp, colour, light, overlay, 0, -1, 0, ux, uz, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        q(vc, pose, sp, colour, light, overlay, 0, 0, -1, ux, uy, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
        q(vc, pose, sp, colour, light, overlay, 0, 0, 1, ux, uy, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
        q(vc, pose, sp, colour, light, overlay, -1, 0, 0, uz, uy, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
        q(vc, pose, sp, colour, light, overlay, 1, 0, 0, uz, uy, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
    }

    /** Corners: top-left, bottom-left, bottom-right, top-right (as seen from outside). */
    private static void q(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, int colour, int light, int overlay, float nx, float ny, float nz,
                          float uw, float vh, float... p) {
        float u0 = sp.getU(0), u1 = sp.getU(uw), v0 = sp.getV(0), v1 = sp.getV(vh);
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(colour).setUv(uv[i][0], uv[i][1]).setOverlay(overlay).setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    @Override
    public AABB getRenderBoundingBox(PhotoFrameBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.6);
    }
}
