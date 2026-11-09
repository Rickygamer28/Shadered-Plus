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
 * Drawing with one of Shadered+'s shaders that blend their own way (by their .json): what Minecraft remembered as the
 * last blending put back after, so ordinary shaders don't set their blend function over the code using them
 * (client/BlendGuard says more).
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderBlendResetMixin {
    @Shadow
    @Final
    private BlendMode blend;

    @Shadow
    public abstract String getName();

    @org.spongepowered.asm.mixin.Unique
    private static Object skyblocks$before;

    @Inject(method = "apply", at = @At("HEAD"), require = 0)
    private void skyblocks$rememberBlend(CallbackInfo ci) {
        if (skyblocks$before == null && !blend.isOpaque() && getName().startsWith("skyblocks")) {
            skyblocks$before = com.example.skyblocks.client.BlendGuard.before();
        }
    }

    @Inject(method = "clear", at = @At("TAIL"), require = 0)
    private void skyblocks$putBackBlend(CallbackInfo ci) {
        if (!blend.isOpaque() && getName().startsWith("skyblocks") && skyblocks$before != null) {
            com.example.skyblocks.client.BlendGuard.after(skyblocks$before);
            skyblocks$before = null;
        }
    }
}
