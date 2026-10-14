package com.example.skyblocks.mixin;

import com.example.skyblocks.SkyTypes;
import com.example.skyblocks.client.ShaderedSkies;
import com.noodlegamer76.shadered.client.renderer.SkyblockRenderer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Batch data for our sky types (used by the Illusorite ore renderer, the Sky Emitter renderer, ...). */
@Mixin(value = SkyblockRenderer.class, remap = false)
public abstract class SkyblockRendererMixin {

    @Inject(method = "getData", at = @At("HEAD"), cancellable = true)
    private static void skyblocks$ourData(SkyblockType type, CallbackInfoReturnable<SkyblockBatchData> cir) {
        int index = SkyTypes.index(type);
        if (index >= 0) {
            cir.setReturnValue(ShaderedSkies.data(index));
        }
    }
}
