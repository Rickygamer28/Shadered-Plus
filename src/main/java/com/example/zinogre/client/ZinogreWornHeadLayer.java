package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a worn Zinogre head on the wearer's head (players, armor stands, zombies, any mob with a head). The item's own
 * "head" display is scaled to nothing, so this is the only place it's drawn when worn.
 */
public class ZinogreWornHeadLayer<T extends LivingEntity, M extends EntityModel<T> & HeadedModel> extends RenderLayer<T, M> {
    public ZinogreWornHeadLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = entity.getItemBySlot(EquipmentSlot.HEAD);
        boolean normal = stack.is(ZinogreMod.HEAD_ITEM.get());
        if (!normal && !stack.is(ZinogreMod.STYGIAN_HEAD_ITEM.get())) {
            return;
        }
        poseStack.pushPose();
        if (entity.isBaby()) {
            poseStack.translate(0.0F, 0.03125F, 0.0F);
            poseStack.scale(0.7F, 0.7F, 0.7F);
            poseStack.translate(0.0F, 1.0F, 0.0F);
        }
        getParentModel().getHead().translateAndRotate(poseStack);
        // the same placement vanilla uses for a worn block (a carved pumpkin): a block-sized cube around the head
        poseStack.translate(0.0F, -0.25F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.625F, -0.625F, -0.625F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        if (!ZinogreHeadRenderer.drawHeadInCube(poseStack, buffer, packedLight, !normal)) {
            Debug.once("worn-missing", "Worn Zinogre head not drawn: model not loaded");
        }
        Debug.once("worn-drawn", "Worn Zinogre head drawn on {}", entity.getType().getDescriptionId());
        poseStack.popPose();
    }
}
