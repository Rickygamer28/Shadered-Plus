// Adapted from Shadered Compat Fix (github.com/Rickygamer28/shadered-compat-fix, version 13), MIT License.
package com.example.skyblocks.client.iris;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Captures the GL / RenderSystem state that Shadered's passes change, and puts it back afterwards.
 * <p>
 * Shadered renders in the middle of the level render (RenderLevelStageEvent). Other mods' renderers
 * that run right after it (Immersive Engineering, Supplementaries, ...) expect to find the state that
 * vanilla left, not whatever Shadered's last pass used. Restoring goes through GlStateManager so its
 * state cache stays in sync with the real GL state.
 */
public final class GlStateSnapshot {
    private static final int TEXTURE_UNITS = 8;
    private static final int SHADER_TEXTURES = 12;

    private boolean blend;
    private int blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha, blendEquation;
    private boolean depthTest;
    private int depthFunc;
    private boolean depthMask;
    private boolean cull;
    private final boolean[] colorMask = new boolean[4];

    private int activeTexture;
    private final int[] boundTextures = new int[TEXTURE_UNITS];
    private final int[] shaderTextures = new int[SHADER_TEXTURES];

    private int drawFramebuffer, readFramebuffer;
    private final int[] viewport = new int[4];

    private boolean hasSsbo;
    private int ssboGeneric, ssboIndexed0;

    private ShaderInstance shader;
    private final float[] shaderColor = new float[4];
    private Matrix4f projection;
    private VertexSorting vertexSorting;
    private Matrix4f modelView;

    private GlStateSnapshot() {}

    public static GlStateSnapshot capture() {
        RenderSystem.assertOnRenderThread();
        GlStateSnapshot s = new GlStateSnapshot();

        s.blend = GL11.glIsEnabled(GL11.GL_BLEND);
        s.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        s.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        s.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        s.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        s.blendEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);

        s.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        s.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        s.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        s.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) {
                s.colorMask[i] = mask.get(i) != 0;
            }

            IntBuffer vp = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            vp.get(s.viewport);
        }

        s.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        // Read straight from OpenGL (GlStateManager's cache can be stale; see GlCacheSync), and put the
        // active unit back the same way so the cache stays as it was.
        for (int i = 0; i < TEXTURE_UNITS; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            s.boundTextures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GL13.glActiveTexture(s.activeTexture);

        for (int i = 0; i < SHADER_TEXTURES; i++) {
            s.shaderTextures[i] = RenderSystem.getShaderTexture(i);
        }

        s.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        s.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        s.hasSsbo = GL.getCapabilities().OpenGL43;
        if (s.hasSsbo) {
            s.ssboGeneric = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
            s.ssboIndexed0 = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 0);
        }

        s.shader = RenderSystem.getShader();
        float[] color = RenderSystem.getShaderColor();
        System.arraycopy(color, 0, s.shaderColor, 0, 4);
        s.projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        s.vertexSorting = RenderSystem.getVertexSorting();
        s.modelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        return s;
    }

    public void restore() {
        RenderSystem.assertOnRenderThread();

        if (blend) GlStateManager._enableBlend(); else GlStateManager._disableBlend();
        GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        GlStateManager._blendEquation(blendEquation);

        if (depthTest) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
        GlStateManager._depthFunc(depthFunc);
        GlStateManager._depthMask(depthMask);
        if (cull) GlStateManager._enableCull(); else GlStateManager._disableCull();
        GlStateManager._colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);

        for (int i = 0; i < TEXTURE_UNITS; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(boundTextures[i]);
        }
        GlStateManager._activeTexture(activeTexture);

        for (int i = 0; i < SHADER_TEXTURES; i++) {
            RenderSystem.setShaderTexture(i, shaderTextures[i]);
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        if (hasSsbo) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssboIndexed0);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboGeneric);
        }

        final ShaderInstance previousShader = shader;
        RenderSystem.setShader(() -> previousShader);
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        RenderSystem.setProjectionMatrix(projection, vertexSorting);
        RenderSystem.getModelViewMatrix().set(modelView);

        // Our passes drew through VertexBuffer/BufferUploader; make the next immediate draw rebind its buffers.
        BufferUploader.invalidate();
    }
}
