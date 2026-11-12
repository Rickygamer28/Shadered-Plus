package com.example.skyblocks.mixin;

import com.example.skyblocks.client.ArmorPaintRender;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skies painted on armour (ArmorPaintRender): drawn over an entity's armour once the game has drawn it. */
@Mixin(value = HumanoidArmorLayer.class, remap = false)
public abstract class HumanoidArmorLayerPaintMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("TAIL"), require = 0)
    private void skyblocks$paintedArmour(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing, float limbSwingAmount,
                                         float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        ArmorPaintRender.renderWorn((HumanoidArmorLayer<?, ?, ?>) (Object) this, pose, buffers, light, entity);
    }
}
