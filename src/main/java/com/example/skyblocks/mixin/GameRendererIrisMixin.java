package com.example.skyblocks.mixin;

import com.example.skyblocks.client.iris.IrisIslandTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws Island Paintings and island cats over the shader pack's image once the level, including Iris's final pass,
 * has finished rendering (see client/iris/IrisIslandTarget). Same hook as Shadered Compat Fix's.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class GameRendererIrisMixin {
    private static final String LEVEL_RENDER =
            "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V";

    /**
     * The camera matrix the level is drawn with (with a shader pack, Iris has moved the view bob into it). Passed
     * through unchanged; IrisIslandTarget draws the paintings with it so they bob along with the world.
     */
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = LEVEL_RENDER), index = 5, require = 0)
    private Matrix4f skyblocks$levelView(Matrix4f frustumMatrix) {
        IrisIslandTarget.levelView(frustumMatrix);
        return frustumMatrix;
    }

    /** The projection the level is drawn with (with a shader pack, without the view bob). Passed through unchanged. */
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = LEVEL_RENDER), index = 6, require = 0)
    private Matrix4f skyblocks$levelProjection(Matrix4f projection) {
        IrisIslandTarget.levelProjection(projection);
        return projection;
    }
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
            ),
            // never crash the game over this: IrisIslandTarget warns once if the overlay isn't being drawn
            require = 0
    )
    private void skyblocks$afterLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        IrisIslandTarget.composite();
    }
}
