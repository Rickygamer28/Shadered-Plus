package com.example.skyblocks.mixin;

import com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer;
import com.noodlegamer76.shadered.client.renderer.assimp.BoneMatrixSsbo;
import java.nio.FloatBuffer;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Shadered's model renderer uploads its whole bone buffer (16384 matrices, 1 MB, rebuilt on the CPU) every time it
 * draws a batch, even when nothing in the batch is animated, and then makes the graphics card wait for it. Updating
 * a buffer the card may still be reading from also makes the CPU wait. Island Paintings draw a batch of cats per
 * painting, so this cost several ms a frame. Here only the matrices actually used are uploaded, and nothing at all
 * (no upload, no wait) when no model in the batch has a skeleton: unanimated models never read the buffer (their
 * offset is -1).
 */
@Mixin(value = AssimpRenderer.class, remap = false)
public abstract class AssimpRendererBonesMixin {
    @Unique
    private static boolean skyblocks$uploaded;

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/noodlegamer76/shadered/client/renderer/assimp/BoneMatrixSsbo;upload(Ljava/util/List;)V"), require = 0)
    private void skyblocks$uploadUsed(BoneMatrixSsbo ssbo, List<Matrix4f> matrices) {
        skyblocks$uploaded = !matrices.isEmpty();
        if (!skyblocks$uploaded) {
            return;
        }
        int count = Math.min(matrices.size(), BoneMatrixSsbo.MAX_BONES);
        FloatBuffer buffer = MemoryUtil.memAllocFloat(count * 16);
        try {
            for (int i = 0; i < count; i++) {
                matrices.get(i).get(i * 16, buffer);
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo.ssbo);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, buffer);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL43;glMemoryBarrier(I)V"), require = 0)
    private void skyblocks$barrierIfUploaded(int barriers) {
        if (skyblocks$uploaded) {
            GL43.glMemoryBarrier(barriers);
        }
    }
}
