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
@Mixin(SkyblockRenderer.class)
public abstract class SkyblockRendererMixin {

    @Inject(method = "getData", at = @At("HEAD"), cancellable = true)
    private static void skyblocks$ourData(SkyblockType type, CallbackInfoReturnable<SkyblockBatchData> cir) {
        int index = SkyTypes.index(type);
        if (index >= 0) {
            SkyblockBatchData data = ShaderedSkies.data(index);
            com.example.skyblocks.client.SkyUsage.mark(data);
            cir.setReturnValue(data);
        }
    }

    /** Looking up a sky's batch (to queue blocks or draw it) counts as using that sky (see SkyUsage). */
    @Inject(method = "getData", at = @At("RETURN"), require = 0)
    private static void skyblocks$used(SkyblockType type, CallbackInfoReturnable<SkyblockBatchData> cir) {
        com.example.skyblocks.client.SkyUsage.mark(cir.getReturnValue());
    }
}
