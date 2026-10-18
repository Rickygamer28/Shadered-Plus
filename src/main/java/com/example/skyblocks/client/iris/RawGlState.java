// Adapted from Shadered Compat Fix (github.com/Rickygamer28/shadered-compat-fix, version 13), MIT License.
package com.example.skyblocks.client.iris;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

/**
 * OpenGL state that vanilla never changes (and so Shadered never sets), but other renderers might leave
 * changed: winding/culling side, scissor and stencil tests, polygon mode, depth clamp, clip planes,
 * rasterizer discard, logic op, polygon offset, depth range. Any of these left non-default can make
 * Shadered's skybox and skyblock drawing produce nothing. Captured and restored with raw GL calls.
 */
public final class RawGlState {
    private static final int CLIP_DISTANCES = 6;

    private int frontFace;
    private int cullFaceMode;
    private boolean scissor;
    private boolean stencilTest;
    private int polygonMode;
    private boolean depthClamp;
    private final boolean[] clipDistances = new boolean[CLIP_DISTANCES];
    private boolean rasterizerDiscard;
    private boolean logicOp;
    private boolean polygonOffsetFill;
    private double depthNear;
    private double depthFar;

    private RawGlState() {}

    public static RawGlState capture() {
        RawGlState s = new RawGlState();
        s.frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        s.cullFaceMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        s.scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        s.stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer mode = stack.mallocInt(2);
            GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, mode);
            s.polygonMode = mode.get(0);
            DoubleBuffer range = stack.mallocDouble(2);
            GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, range);
            s.depthNear = range.get(0);
            s.depthFar = range.get(1);
        }
        s.depthClamp = GL11.glIsEnabled(GL32.GL_DEPTH_CLAMP);
        for (int i = 0; i < CLIP_DISTANCES; i++) {
            s.clipDistances[i] = GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0 + i);
        }
        s.rasterizerDiscard = GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
        s.logicOp = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
        s.polygonOffsetFill = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        // Drain errors from queries a driver might not support, so they don't show up elsewhere.
        for (int i = 0; i < 8 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
        }
        return s;
    }

    /** Puts everything here to vanilla's defaults. */
    public static void applyVanillaDefaults() {
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glDisable(GL32.GL_DEPTH_CLAMP);
        for (int i = 0; i < CLIP_DISTANCES; i++) {
            GL11.glDisable(GL30.GL_CLIP_DISTANCE0 + i);
        }
        GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glDepthRange(0.0, 1.0);
    }

    public void restore() {
        GL11.glFrontFace(frontFace);
        GL11.glCullFace(cullFaceMode);
        setEnabled(GL11.GL_SCISSOR_TEST, scissor);
        setEnabled(GL11.GL_STENCIL_TEST, stencilTest);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, polygonMode);
        setEnabled(GL32.GL_DEPTH_CLAMP, depthClamp);
        for (int i = 0; i < CLIP_DISTANCES; i++) {
            setEnabled(GL30.GL_CLIP_DISTANCE0 + i, clipDistances[i]);
        }
        setEnabled(GL30.GL_RASTERIZER_DISCARD, rasterizerDiscard);
        setEnabled(GL11.GL_COLOR_LOGIC_OP, logicOp);
        setEnabled(GL11.GL_POLYGON_OFFSET_FILL, polygonOffsetFill);
        GL11.glDepthRange(depthNear, depthFar);
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        }
        else {
            GL11.glDisable(capability);
        }
    }

    @Override
    public String toString() {
        StringBuilder clips = new StringBuilder();
        for (int i = 0; i < CLIP_DISTANCES; i++) {
            if (clipDistances[i]) {
                clips.append(i);
            }
        }
        return "frontFace=" + (frontFace == GL11.GL_CCW ? "CCW" : frontFace == GL11.GL_CW ? "CW" : frontFace)
                + " cullFace=" + cullFaceMode + " cullEnabled=" + GL11.glIsEnabled(GL11.GL_CULL_FACE)
                + " scissor=" + scissor + " stencilTest=" + stencilTest + " polygonMode=" + polygonMode
                + " depthClamp=" + depthClamp + " clipPlanes=[" + clips + "] rasterizerDiscard=" + rasterizerDiscard
                + " logicOp=" + logicOp + " polyOffsetFill=" + polygonOffsetFill
                + " depthRange=" + depthNear + ".." + depthFar
                + " depthTest=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST) + " depthFunc=" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    }
}
