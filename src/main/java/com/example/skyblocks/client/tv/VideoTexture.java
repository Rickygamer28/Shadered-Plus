package com.example.skyblocks.client.tv;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL21;

/** A television's picture: a texture the newest frame is copied into (on the render thread). */
final class VideoTexture extends AbstractTexture {
    int width, height;

    @Override
    public void load(ResourceManager manager) {
    }

    void upload(ByteBuffer rgba, int w, int h) {
        RenderSystem.assertOnRenderThread();
        if (rgba.remaining() < w * h * 4) {
            return;
        }
        // (another mod may have left a pixel buffer bound: the upload would read from it instead, past its end)
        int pbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        if (pbo != 0) {
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        }
        int id = getId();
        if (w != width || h != height) {
            TextureUtil.prepareImage(id, 0, w, h);
            width = w;
            height = h;
        }
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
        if (pbo != 0) {
            GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
        }
    }
}
