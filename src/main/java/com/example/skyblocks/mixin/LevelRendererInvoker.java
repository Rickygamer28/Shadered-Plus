package com.example.skyblocks.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Draws one chunk layer with given matrices (for mirrors' reflections, client/MirrorRenderer). With Sodium installed,
 * Sodium has replaced this method and draws its own chunk meshes (the ones already on the graphics card).
 */
@Mixin(value = LevelRenderer.class, remap = false)
public interface LevelRendererInvoker {
    @Invoker("renderSectionLayer")
    void skyblocks$renderSectionLayer(RenderType renderType, double x, double y, double z, Matrix4f frustumMatrix, Matrix4f projectionMatrix);
}
