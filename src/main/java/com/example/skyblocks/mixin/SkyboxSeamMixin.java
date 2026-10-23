package com.example.skyblocks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.noodlegamer76.shadered.client.util.skyblock.SkyBoxRenderer;
import com.noodlegamer76.shadered.client.util.skyblock.SkyboxTranslation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shadered's skyboxes are six huge squares around the camera. Where two of them meet, the graphics card can leave a
 * hair-thin gap (the squares are cut where they pass behind you, and each one is cut on its own), and the black behind
 * showed as a line along the cube's edges. Each square is drawn a fraction larger (its picture stretched with it, so it
 * lines up the same), so neighbours overlap a little and no gap can open.
 */
@Mixin(value = SkyBoxRenderer.class, remap = false)
public abstract class SkyboxSeamMixin {
    @Unique
    private static final float SKYBLOCKS_GROW = 1.004F;

    @Inject(method = "drawQuad", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void skyblocks$overlap(PoseStack poseStack, float x1, float y1, float z1, float x2, float y2, float z2,
                                          float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int b, int a,
                                          SkyboxTranslation.SkyboxRotation rotation, SkyboxTranslation.SkyboxFlip flip, CallbackInfo ci) {
        float[][] p = {{x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}, {x4, y4, z4}};
        int plane = -1;
        for (int k = 0; k < 3; k++) {
            if (p[0][k] == p[1][k] && p[1][k] == p[2][k] && p[2][k] == p[3][k]) {
                plane = k;
                break;
            }
        }
        if (plane < 0) {
            return; // not a flat face of the box: drawn as it was
        }
        float[][] uv = {{0.0F, 1.0F}, {0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}};
        Matrix4f m = poseStack.last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder buffer = SkyBoxRenderer.startQuad();
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 3; k++) {
                if (k != plane) {
                    p[i][k] *= SKYBLOCKS_GROW;
                }
            }
            float u = 0.5F + (uv[i][0] - 0.5F) * SKYBLOCKS_GROW, v = 0.5F + (uv[i][1] - 0.5F) * SKYBLOCKS_GROW;
            switch (rotation) {
                case ROTATE_90_CW -> { float t = u; u = 1.0F - v; v = t; }
                case ROTATE_180 -> { u = 1.0F - u; v = 1.0F - v; }
                case ROTATE_90_CCW -> { float t = u; u = v; v = 1.0F - t; }
                default -> { }
            }
            switch (flip) {
                case HORIZONTAL -> u = 1.0F - u;
                case VERTICAL -> v = 1.0F - v;
                case BOTH -> { u = 1.0F - u; v = 1.0F - v; }
                default -> { }
            }
            buffer.addVertex(m, p[i][0], p[i][1], p[i][2]).setUv(u, v).setColor(r, g, b, a);
        }
        SkyBoxRenderer.endQuad(buffer);
        ci.cancel();
    }
}
