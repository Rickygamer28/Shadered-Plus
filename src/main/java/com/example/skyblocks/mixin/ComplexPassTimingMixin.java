package com.example.skyblocks.mixin;

import com.example.skyblocks.client.WorldTiming;
import com.mojang.blaze3d.vertex.PoseStack;
import com.noodlegamer76.shadered.client.renderer.ComplexPassRenderer;
import com.noodlegamer76.shadered.client.util.RenderStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times Shadered's passes at each of its stages, for the F3 screen (WorldTiming). */
@Mixin(ComplexPassRenderer.class)
public abstract class ComplexPassTimingMixin {
    @org.spongepowered.asm.mixin.Unique
    private static java.lang.reflect.Field[] skyblocks$targetFields;

    @Inject(method = "render", at = @At("HEAD"), require = 0, remap = false)
    private void skyblocks$timeStart(RenderStage stage, PoseStack poseStack, int renderTick, float partialTick, CallbackInfo ci) {
        WorldTiming.shaderedStart(stage);
        skyblocks$matchDepthFormat((ComplexPassRenderer) (Object) this);
    }

    /**
     * Shadered copies the screen's depth into its own pictures before drawing its sky blocks and models, so they're
     * hidden behind what's in front of them - but our screen has a stencil (for the Island Paintings), a different
     * depth format, and the copy failed: its models and sky blocks were drawn over everything (through black holes,
     * walls). Its pictures get a stencil too, so the copy works.
     */
    private static void skyblocks$matchDepthFormat(ComplexPassRenderer renderer) {
        if (skyblocks$targetFields == null) { // (looked up once: this runs at every stage of every frame)
            java.util.List<java.lang.reflect.Field> found = new java.util.ArrayList<>();
            for (java.lang.reflect.Field f : ComplexPassRenderer.class.getDeclaredFields()) {
                if (!com.mojang.blaze3d.pipeline.RenderTarget.class.isAssignableFrom(f.getType()) || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    found.add(f);
                } catch (RuntimeException ignored) {
                    // (left as it is)
                }
            }
            skyblocks$targetFields = found.toArray(new java.lang.reflect.Field[0]);
        }
        for (java.lang.reflect.Field f : skyblocks$targetFields) {
            try {
                com.mojang.blaze3d.pipeline.RenderTarget target = (com.mojang.blaze3d.pipeline.RenderTarget) f.get(renderer);
                if (target != null && !target.isStencilEnabled()) {
                    target.enableStencil();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // (left as it is)
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0, remap = false)
    private void skyblocks$timeEnd(RenderStage stage, PoseStack poseStack, int renderTick, float partialTick, CallbackInfo ci) {
        WorldTiming.shaderedEnd();
        if (skyblocks$afterBlockEntities(stage)) {
            com.example.skyblocks.client.BlackHoleRenderer.drawPending("after Shadered's pass");
        }
    }

    /** The black holes: drawn in Shadered's pass, its sky blocks and models drawn, before its picture goes back on screen. */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/noodlegamer76/shadered/client/renderer/ComplexPassRenderer;postRender()V"),
            require = 0, remap = false)
    private void skyblocks$beforePutBack(RenderStage stage, PoseStack poseStack, int renderTick, float partialTick, CallbackInfo ci) {
        if (skyblocks$afterBlockEntities(stage)) {
            com.example.skyblocks.client.BlackHoleRenderer.drawPending("in Shadered's pass");
        }
    }

    private static boolean skyblocks$afterBlockEntities(RenderStage stage) {
        return String.valueOf(stage).contains("BLOCK") && !com.example.skyblocks.client.MirrorRenderer.isRenderingReflection();
    }
}
