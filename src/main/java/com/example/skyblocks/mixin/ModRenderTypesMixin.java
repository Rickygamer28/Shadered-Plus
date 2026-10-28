package com.example.skyblocks.mixin;

import com.example.skyblocks.SkyTypes;
import com.example.skyblocks.client.ShaderedSkies;
import com.noodlegamer76.shadered.client.util.ModRenderTypes;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item render types (Shadered builds one per sky type) need the screen texture of our skies. */
@Mixin(value = ModRenderTypes.class, remap = false)
public abstract class ModRenderTypesMixin {

    @Inject(method = "getSkyboxTextureId", at = @At("HEAD"), cancellable = true)
    private static void skyblocks$ourTexture(SkyblockType type, CallbackInfoReturnable<Integer> cir) {
        // something is drawn with this sky's render type: keep its picture up to date (see SkyUsage)
        com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData data = com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(type);
        if (com.example.skyblocks.client.SkyUsage.inHud) {
            com.example.skyblocks.client.SkyUsage.markHud(data); // (the hotbar: only its strip of the picture)
        } else {
            com.example.skyblocks.client.SkyUsage.mark(data, net.minecraft.client.Minecraft.getInstance().screen != null
                    ? "sky items in an open screen" : "sky items drawn in the world");
        }
        int index = SkyTypes.index(type);
        if (index >= 0) {
            cir.setReturnValue(ShaderedSkies.skyboxTexture(index));
        }
    }
}
