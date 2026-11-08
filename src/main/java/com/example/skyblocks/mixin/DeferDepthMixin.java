package com.example.skyblocks.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * With a shader pack, Shadered+'s blocks drawn into their own target (client/iris/IrisDefer) all keep their depth, even
 * the see-through ones that normally don't: the target is laid over the pack's image only where it has something
 * drawn, tested against the frame's depth - with no depth of their own, lamps, tanks and plasma balls never showed.
 */
@Mixin(RenderSystem.class)
public abstract class DeferDepthMixin {
    @ModifyVariable(method = "depthMask", at = @At("HEAD"), argsOnly = true, require = 0)
    private static boolean skyblocks$keepDepthOverPack(boolean flag) {
        return flag || com.example.skyblocks.client.iris.IrisDefer.drawing();
    }
}
