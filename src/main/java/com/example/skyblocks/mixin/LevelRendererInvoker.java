package com.example.skyblocks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Draws a chunk layer from another camera (client/MirrorRenderer): the chunk meshes already on the graphics card. */
@Mixin(LevelRenderer.class)
public interface LevelRendererInvoker {
    @Invoker("renderChunkLayer")
    void skyblocks$renderChunkLayer(RenderType type, PoseStack pose, double camX, double camY, double camZ, Matrix4f projection);
}
