package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer;
import com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Like Shadered's MaxwellItemRenderer: a flat icon in the inventory, the real model everywhere else
 * (standing still: as an item it gets no redstone signal).
 */
public class OiiaCatItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation ICON = new ResourceLocation(SkyBlocksMod.MOD_ID, "textures/item/oiia_cat.png");

    public OiiaCatItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);

        if (context == ItemDisplayContext.GUI) {
            poseStack.scale(0.5F, 0.5F, 0.5F);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, ICON);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Matrix4f m = poseStack.last().pose();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.vertex(m, -1, 1, 0).uv(0, 0).endVertex();
            builder.vertex(m, -1, -1, 0).uv(0, 1).endVertex();
            builder.vertex(m, 1, -1, 0).uv(1, 1).endVertex();
            builder.vertex(m, 1, 1, 0).uv(1, 0).endVertex();
            BufferUploader.drawWithShader(builder.end());
            RenderSystem.disableBlend();
        } else {
            RenderableModel model = OiiaCatModel.create();
            if (model != null) {
                poseStack.translate(0, -0.4F, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90));
                poseStack.scale(OiiaCatModel.SCALE, OiiaCatModel.SCALE, OiiaCatModel.SCALE);
                poseStack.translate(0, OiiaCatModel.FEET, 0);
                model.modelMatrix = new Matrix4f(poseStack.last().pose());
                AssimpRenderer.getInstance().renderDirect(model, poseStack, false);
            }
        }
        poseStack.popPose();
    }
}
