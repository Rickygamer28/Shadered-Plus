package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.iris.IrisCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * With an Oculus shader pack, chunk meshes get their vertex attributes at Oculus's binding points (one further along
 * than Embeddium's own). Mirrors draw chunks with Embeddium's own program then (OculusChunkProgramMixin), so that
 * program is linked with Oculus's binding points while a pack is in use (the real view uses the pack's programs then).
 */
@Pseudo // (Embeddium may not be installed)
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class SodiumMirrorAttribMixin {
    @ModifyArg(method = "createShader", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/gl/shader/GlProgram$Builder;bindAttribute(Ljava/lang/String;I)Lme/jellysquid/mods/sodium/client/gl/shader/GlProgram$Builder;"),
            index = 1, require = 0)
    private int skyblocks$oculusBindingPoint(int index) {
        return IrisCompat.shaderPackInUse() ? index + 1 : index;
    }
}
