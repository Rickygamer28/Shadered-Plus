package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A Sky Liquid Bucket: Minecraft's own bucket, with the pixels where a water bucket shows its water drawn with the
 * liquid's Shadered sky (the same live sky that sky's block item shows).
 */
public class SkyLiquidBucketRenderer extends BlockEntityWithoutLevelRenderer {
    /** The water's pixels in Minecraft's water bucket: {row, first column, last column}. */
    private static final int[][] WATER = {{3, 4, 11}, {4, 3, 12}, {5, 5, 10}};
    /** The item's 1 pixel thick slab: front and back faces (item/generated models are 7.5 - 8.5 pixels deep). */
    private static final float FRONT = 8.5F / 16.0F + 0.001F, BACK = 7.5F / 16.0F - 0.001F;

    private final SkyblockType sky;

    public SkyLiquidBucketRenderer(SkyblockType sky) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.sky = sky;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer,
                             int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer items = mc.getItemRenderer();
        ItemStack bucket = new ItemStack(Items.BUCKET);
        BakedModel model = items.getModel(bucket, mc.level, null, 0);
        items.renderModelLists(model, bucket, light, overlay, poseStack, buffer.getBuffer(Sheets.translucentItemSheet()));

        RenderType skyType = SkyLiquidRenderer.skyType(this.sky);
        if (skyType == null) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        if (context != ItemDisplayContext.GUI && com.example.skyblocks.client.iris.IrisIslandTarget.deferSky()) {
            // in the world (or in hand) with a shader pack: drawn at the end of the frame (see SkyLiquidRenderer)
            PoseStack.Pose copy = pose.copy();
            com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(skyType, c -> fill(c, copy, light));
            return;
        }
        fill(buffer.getBuffer(skyType), pose, light);
    }

    private static void fill(VertexConsumer c, PoseStack.Pose pose, int light) {
        for (int[] row : WATER) {
            float y1 = 1.0F - row[0] / 16.0F, y0 = y1 - 1.0F / 16.0F;
            float x0 = row[1] / 16.0F, x1 = (row[2] + 1) / 16.0F;
            quad(c, pose, x0, y0, x1, y1, FRONT, 1.0F, light);
            quad(c, pose, x1, y0, x0, y1, BACK, -1.0F, light);
        }
    }

    private static void quad(VertexConsumer c, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
                             float nz, int light) {
        vertex(c, pose, x0, y0, z, 0, 1, nz, light);
        vertex(c, pose, x1, y0, z, 1, 1, nz, light);
        vertex(c, pose, x1, y1, z, 1, 0, nz, light);
        vertex(c, pose, x0, y1, z, 0, 0, nz, light);
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               float nz, int light) {
        c.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0.0F, 0.0F, nz);
    }
}
