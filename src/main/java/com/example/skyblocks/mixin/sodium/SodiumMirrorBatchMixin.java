package com.example.skyblocks.mixin.sodium;

import com.example.skyblocks.client.MirrorRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sodium keeps each region's list of draw commands between frames, built for the camera it was first drawn from (it
 * leaves out block faces that camera can't see). A mirror draws from another camera: with the kept list it lost faces
 * only it could see, and when the mirror built a list first, the real view used the mirror's (faces flickering in the
 * real world). While a mirror draws, regions get a separate list, rebuilt every time.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion", remap = false)
public abstract class SodiumMirrorBatchMixin {
    private static final Map<Object, Object> SCRATCH = new WeakHashMap<>();
    private static MethodHandle create, clear, delete;
    private static boolean failed;

    @Inject(method = "getCachedBatch", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyblocks$mirrorBatch(CallbackInfoReturnable<Object> cir) {
        if (!MirrorRenderer.isRenderingReflection() || failed) {
            return;
        }
        try {
            if (create == null) {
                Class<?> batch = Class.forName("net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                create = lookup.findConstructor(batch, MethodType.methodType(void.class, int.class));
                clear = lookup.findVirtual(batch, "clear", MethodType.methodType(void.class));
                delete = lookup.findVirtual(batch, "delete", MethodType.methodType(void.class));
            }
            if (SCRATCH.size() > 512) {
                for (Object old : SCRATCH.values()) {
                    delete.invoke(old);
                }
                SCRATCH.clear();
            }
            Object batch = SCRATCH.get(this);
            if (batch == null) {
                batch = create.invoke(7 * 256 + 64); // (facings x sections per region, with room to spare)
                SCRATCH.put(this, batch);
            }
            clear.invoke(batch);
            cir.setReturnValue(batch);
        } catch (Throwable t) {
            failed = true;
        }
    }
}
