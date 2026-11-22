package com.example.skyblocks.client;

import com.example.skyblocks.SkyBrushItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The Sky Brush: its picture (item/sky_brush), the bristles drawn with the sky it's dipped in. */
public class SkyBrushItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelResourceLocation BASE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("skyblocks", "item/sky_brush_base"));
    /** The bristles' pixels: {row, first column, last column}. */
    private static final int[][] BRISTLES = {{1, 12, 13}, {2, 11, 14}, {3, 10, 14}, {4, 10, 13}, {5, 10, 12}};
    private static final float FRONT = 8.5F / 16.0F + 0.001F, BACK = 7.5F / 16.0F - 0.001F;

    public SkyBrushItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer,
                             int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(BASE);
        mc.getItemRenderer().renderModelLists(model, stack, light, overlay, poseStack, buffer.getBuffer(Sheets.translucentItemSheet()));
        SkyblockType sky = SkyBrushItem.sky(stack);
        RenderType skyType = sky == null ? null : SkyLiquidRenderer.skyType(sky);
        if (skyType == null) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        if (context != ItemDisplayContext.GUI && com.example.skyblocks.client.iris.IrisIslandTarget.deferSky()) {
            PoseStack.Pose copy = pose.copy();
            com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(skyType, c -> fill(c, copy, light));
            return;
        }
        fill(buffer.getBuffer(skyType), pose, light);
    }

    private static void fill(VertexConsumer c, PoseStack.Pose pose, int light) {
        for (int[] row : BRISTLES) {
            float y1 = 1.0F - row[0] / 16.0F, y0 = y1 - 1.0F / 16.0F;
            float x0 = row[1] / 16.0F, x1 = (row[2] + 1) / 16.0F;
            quad(c, pose, x0, y0, x1, y1, FRONT, 1.0F, light);
            quad(c, pose, x1, y0, x0, y1, BACK, -1.0F, light);
        }
    }

    private static void quad(VertexConsumer c, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z, float nz, int light) {
        vertex(c, pose, x0, y0, z, 0, 1, nz, light);
        vertex(c, pose, x1, y0, z, 1, 1, nz, light);
        vertex(c, pose, x1, y1, z, 1, 0, nz, light);
        vertex(c, pose, x0, y1, z, 0, 0, nz, light);
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nz, int light) {
        c.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0.0F, 0.0F, nz);
    }
}
