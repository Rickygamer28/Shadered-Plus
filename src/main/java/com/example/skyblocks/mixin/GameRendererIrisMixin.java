package com.example.skyblocks.mixin;

import com.example.skyblocks.client.iris.IrisIslandTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws Island Paintings and island cats over the shader pack's image once the level, including Iris's final pass,
 * has finished rendering (see client/iris/IrisIslandTarget). Same hook as Shadered Compat Fix's.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererIrisMixin {
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
            ),
            // never crash the game over this: IrisIslandTarget warns once if the overlay isn't being drawn
            require = 0
    )
    private void skyblocks$afterLevel(float partialTick, long finishTimeNano, com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        IrisIslandTarget.composite();
    }
}
