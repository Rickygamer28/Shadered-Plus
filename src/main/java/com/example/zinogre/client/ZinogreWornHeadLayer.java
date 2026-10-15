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
    /** Mesh units -> blocks: about 1.6 blocks tall and long, a big trophy-sized head. */
    private static final float WORN_SCALE = 0.036F;
    /** The point of the (closed-mouth) head mesh that goes where the middle of the wearer's head was (so its jaw rests on the neck). */
    private static final float WORN_CENTRE_Y = 21.5F, WORN_CENTRE_Z = -4.0F;

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
        // model space is y down with the face towards -Z: turn it to y up, face +Z (like the head mesh), centred on
        // the middle of the wearer's (hidden) head, then place the head mesh so it sits on the neck in its place
        poseStack.translate(0.0F, -0.25F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.scale(WORN_SCALE, WORN_SCALE, WORN_SCALE);
        poseStack.translate(0.0F, -WORN_CENTRE_Y, -WORN_CENTRE_Z);
        if (!ZinogreHeadRenderer.drawClosedHead(poseStack, buffer, packedLight, !normal)) {
            Debug.once("worn-missing", "Worn Zinogre head not drawn: model not loaded");
        }
        poseStack.popPose();
    }
}
