package com.example.skyblocks.mixin;

import com.example.skyblocks.client.MirrorCulling;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Without Embeddium, mirrors draw the game's own list of chunk sections in view - only what the camera's frustum holds:
 * what a mirror or a portal showed off to the side or behind you (a portal's other end farther off) was missing from
 * its picture. Sections a mirror sees count as in view too (MirrorCulling), and while mirrors are in view the list is
 * made again every frame (their views change as you move).
 */
@Mixin(LevelRenderer.class)
public abstract class MirrorFrustumMixin {
    @Shadow
    @Final
    private AtomicBoolean needsFrustumUpdate;

    @Redirect(method = "applyFrustum", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z"), require = 0)
    private boolean skyblocks$seenInMirror(Frustum frustum, AABB box) {
        return frustum.isVisible(box) || MirrorCulling.visible((int) ((box.minX + box.maxX) * 0.5), (int) ((box.minY + box.maxY) * 0.5),
                (int) ((box.minZ + box.maxZ) * 0.5));
    }

    @Inject(method = "setupRender", at = @At("HEAD"), require = 0)
    private void skyblocks$mirrorsInView(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean spectator, CallbackInfo ci) {
        if (MirrorCulling.active()) {
            needsFrustumUpdate.set(true);
        }
    }
}
