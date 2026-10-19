package com.example.skyblocks.mixin;

import com.example.skyblocks.client.iris.IrisIslandTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right before the level's see-through blocks (glass, water...) are drawn, at the same point where Iris draws its
 * batched entities: keeps a copy of the world's depth (solid blocks and entities, no glass) for Shadered+'s
 * end-of-frame shader pack overlay (client/iris/IrisIslandTarget), so paintings, island cats and Sky Liquids stay
 * visible through glass.
 */
@Mixin(value = LevelRenderer.class, remap = false)
public abstract class LevelRendererTranslucentMixin {
    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=translucent", shift = At.Shift.AFTER), require = 0)
    private void skyblocks$beforeTranslucent(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                             LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        IrisIslandTarget.snapshotDepth();
    }
}
