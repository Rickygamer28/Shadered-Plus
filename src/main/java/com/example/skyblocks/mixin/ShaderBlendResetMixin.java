package com.example.skyblocks.mixin;

import com.mojang.blaze3d.shaders.BlendMode;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After drawing with one of Shadered+'s shaders that blend by their own .json: Minecraft remembers "no blending of its
 * own" again, so the next ordinary shader doesn't turn blending off behind its back (client/BlendGuard says more).
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderBlendResetMixin {
    @Shadow
    @Final
    private BlendMode blend;

    @Shadow
    public abstract String getName();

    @Inject(method = "clear", at = @At("TAIL"), require = 0)
    private void skyblocks$forgetOwnBlend(CallbackInfo ci) {
        if (!blend.isOpaque() && getName().startsWith("skyblocks")) {
            com.example.skyblocks.client.BlendGuard.afterOwnShader();
        }
    }
}
