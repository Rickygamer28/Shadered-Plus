package com.example.skyblocks.client.iris;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

/**
 * Shader packs read some of their own screen buffers while drawing see-through blocks, and those hold what the real
 * view saw at that pixel, not the mirror. Complementary's water/glass/portal program throws away anything behind the
 * clouds it finds in gaux2 (written later in the frame, so in a mirror it reads as "clouds right in front of you") and
 * every portal, glass pane and water surface in a mirror vanished. While a mirror draws, that buffer is swapped for
 * one that says "no clouds anywhere".
 */
public final class MirrorPackSamplers {
    private static final String[] CLOUD_DEPTH = {"gaux2", "colortex5"};
    private static int texture, width, height;

    private MirrorPackSamplers() {}

    /** Called with the pack's chunk program bound and its textures set up. */
    public static void apply() {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program == 0) {
            return;
        }
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            for (String name : CLOUD_DEPTH) {
                int location = GL20.glGetUniformLocation(program, name);
                if (location < 0) {
                    continue;
                }
                int unit = GL20.glGetUniformi(program, location);
                if (unit < 0 || unit >= 32) {
                    continue;
                }
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                GlStateManager._bindTexture(noClouds());
            }
        } finally {
            GlStateManager._activeTexture(active);
        }
    }

    /** A screen-sized texture of (0, 0, 0, 1): "the clouds are as far as can be" everywhere. */
    private static int noClouds() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (texture != 0 && width == main.width && height == main.height) {
            return texture;
        }
        if (texture == 0) {
            texture = GL11.glGenTextures();
        }
        width = main.width;
        height = main.height;
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        try {
            for (int i = 0; i < width * height; i++) {
                pixels.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 255);
            }
            pixels.flip();
            GlStateManager._bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            MemoryUtil.memFree(pixels);
        }
        return texture;
    }
}
