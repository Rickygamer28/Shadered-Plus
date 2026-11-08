package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.MirrorCulling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium's chunk search goes only outwards from the camera's section. A portal's other end can be off to one side or
 * behind you, and what its picture shows from there can come back towards you: those sections were never reached (the
 * ground missing in the picture, coming and going as you moved). While a portal is in view the search may go every
 * way - only through sections some view sees (yours, a mirror's, or the line to a portal's other end: MirrorCulling).
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller", remap = false)
public abstract class SodiumPortalSearchMixin {
    @Inject(method = "getOutwardDirections", at = @At("RETURN"), cancellable = true, require = 0)
    private static void skyblocks$everyWayToPortals(CallbackInfoReturnable<Integer> cir) {
        if (MirrorCulling.searchEveryWay()) {
            cir.setReturnValue(0b111111); // (all six directions)
        }
    }
}
