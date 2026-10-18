package com.example.skyblocks.client.iris;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.IslandCatBatch;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

/**
 * Island Paintings and the cats inside Sky Islands while an Iris shader pack is active.
 * <p>
 * Normally the painting's window is a stencil mark plus a depth punch (IslandWindow) drawn into whatever framebuffer
 * is bound while block entities render. With a shader pack that is Iris's gbuffer framebuffer: it has no stencil (so
 * the mark does nothing), deferred packs treat the punched "far" depth as sky and paint over it (the white inside),
 * and custom-shader draws (the sky room, the cats' PBR shader) only reach colortex0 and get lit or overwritten by the
 * pack, so the cats vanish.
 * <p>
 * So in Iris mode, while block entities render, paintings and island cats are only noted down. Once the level (and
 * Iris's final pass) is done, they are all drawn in one go into our own offscreen target (same size as the screen,
 * with depth and stencil) holding a copy of the frame's final depth (Iris draws into Minecraft's own depth texture,
 * its depthtex0), exactly as without a shader pack. That depth handles what covers them: the painting's window marks
 * only where its opening is visible (depth-tested against the world, water and the hand included) and the island is
 * drawn only inside that mark; cats are depth-tested directly. So the target is then simply laid over the screen
 * where it has something. Binding a target that isn't Minecraft's main one tells Iris not to swap vanilla shaders for
 * the pack's while we draw. The insides look un-shaded (no shader pack lighting), like Shadered's own blocks do.
 * <p>
 * Speed: one target bind, one depth copy and one batch for all island cats per frame, however many paintings are in
 * view. Asking OpenGL for its state (glGet...) makes the driver wait for the GPU, so it is avoided: GlStateManager's
 * remembered state may be out of date under Sodium/Iris (calls it thinks are already in effect get skipped), so every
 * setting needed is set twice (the other way first), which always reaches OpenGL and leaves GlStateManager right.
 * Only the framebuffer and storage buffer bindings are read back.
 * <p>
 * Approach and overlay shader from Shadered Compat Fix (github.com/Rickygamer28/shadered-compat-fix, MIT License).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
public final class IrisIslandTarget {
    private static TextureTarget target;
    /** The paintings to draw at the end of the frame (each with the pose it had). */
    private static final List<Runnable> PAINTINGS = new ArrayList<>();
    /** Cats of Sky Islands on pedestals, to draw at the end of the frame in one batch. */
    private static final List<RenderableModel> CATS = new ArrayList<>();
    private static float catPartialTick;
    /** The view block entities were drawn with (the end of the frame may have other matrices and fog set). */
    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f modelView = new Matrix4f();
    private static VertexSorting sorting = VertexSorting.DISTANCE_TO_ORIGIN;
    private static float fogStart, fogEnd;
    private static final float[] fogColor = new float[4];
    private static FogShape fogShape = FogShape.SPHERE;

    /**
     * This frame's camera matrix and projection exactly as the level gets them (GameRendererIrisMixin). With a shader
     * pack, Iris moves the view bob from the projection into the camera matrix and draws the world with these, so the
     * paintings use them too (otherwise their insides slide against the frame while walking, and the window's depth
     * test against the world flickers).
     */
    private static final Matrix4f levelView = new Matrix4f();
    private static final Matrix4f levelProjection = new Matrix4f();
    private static boolean levelViewSet, levelProjectionSet;

    /** True while drawAll draws into the target (outside renderers may bind another framebuffer meanwhile). */
    private static boolean drawing;

    private static boolean warnedOtherPass;
    private static boolean warnedNoShader;
    private static boolean warnedNoComposite;
    private static boolean warnedError;

    private IrisIslandTarget() {}

    /** True while a shader pack is active and this isn't Iris's shadow pass: paintings go through here. */
    public static boolean active() {
        return IrisCompat.shaderPackInUse() && !IrisCompat.renderingShadowPass();
    }

    /** True during Iris's shadow pass (the world seen from the sun): paintings' insides and island cats are skipped. */
    public static boolean shadowPass() {
        return IrisCompat.renderingShadowPass();
    }

    public static void levelView(Matrix4f frustumMatrix) {
        levelView.set(frustumMatrix);
        levelViewSet = true;
    }

    public static void levelProjection(Matrix4f projection) {
        levelProjection.set(projection);
        levelProjectionSet = true;
    }

    /** While the paintings are being drawn into the target: binds it again (after a renderer bound another one). */
    public static void rebindIfDrawing() {
        if (drawing && target != null) {
            target.bindWrite(true);
        }
    }

    /**
     * True if a block entity's pose is the main view's: only the block's position relative to the camera, no turn
     * (the camera's turn is in the camera matrix). Iris's shadow pass hands block entities a pose with the sun's view
     * turned into it; a painting noted down from there was drawn at a wrong spot that slid around as you moved (and
     * its depth hid the real paintings at some angles). Checked here as well as through Iris's API.
     */
    public static boolean mainViewPose(PoseStack poseStack) {
        Matrix4f m = poseStack.last().pose();
        float e = 1.0E-3F;
        boolean plain = Math.abs(m.m00() - 1) < e && Math.abs(m.m11() - 1) < e && Math.abs(m.m22() - 1) < e
                && Math.abs(m.m01()) < e && Math.abs(m.m02()) < e && Math.abs(m.m10()) < e
                && Math.abs(m.m12()) < e && Math.abs(m.m20()) < e && Math.abs(m.m21()) < e
                && Math.abs(m.m03()) < e && Math.abs(m.m13()) < e && Math.abs(m.m23()) < e && Math.abs(m.m33() - 1) < e;
        if (!plain && !warnedOtherPass) {
            warnedOtherPass = true;
            SkyBlocksMod.LOGGER.info("Shadered+: skipping Island Paintings drawn from another view than the camera's (Iris shadow pass reported: {})",
                    IrisCompat.renderingShadowPass());
        }
        return plain;
    }

    /** Notes down a painting to draw at the end of the frame: {@code draw} gets a copy of the pose it has now. */
    public static void deferPainting(PoseStack poseStack, Consumer<PoseStack> draw) {
        if (!mainViewPose(poseStack)) {
            return;
        }
        rememberView();
        PoseStack copy = new PoseStack();
        copy.last().pose().set(poseStack.last().pose());
        copy.last().normal().set(poseStack.last().normal());
        PAINTINGS.add(() -> draw.accept(copy));
    }

    /** Takes the cats collected in IslandCatBatch (a Sky Island on a pedestal) to draw at the end of the frame. */
    public static void deferCats(boolean mainView, float partialTick) {
        if (!IslandCatBatch.hasPending()) {
            return;
        }
        if (!active() || !mainView) {
            IslandCatBatch.discard(); // shadow pass: cats don't cast shadows
            return;
        }
        rememberView();
        IslandCatBatch.drainTo(CATS);
        catPartialTick = partialTick;
    }

    private static void rememberView() {
        if (!PAINTINGS.isEmpty() || !CATS.isEmpty()) {
            return;
        }
        projection.set(RenderSystem.getProjectionMatrix());
        sorting = RenderSystem.getVertexSorting();
        modelView.set(RenderSystem.getModelViewMatrix());
        fogStart = RenderSystem.getShaderFogStart();
        fogEnd = RenderSystem.getShaderFogEnd();
        System.arraycopy(RenderSystem.getShaderFogColor(), 0, fogColor, 0, 4);
        fogShape = RenderSystem.getShaderFogShape();
    }

    /** Called right after the level (and Iris's final pass) has rendered: draws everything noted down and shows it. */
    public static void composite() {
        if (PAINTINGS.isEmpty() && CATS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        // what to put back afterwards (read without asking OpenGL, except the bindings)
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        Matrix4f savedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        ShaderInstance savedShader = RenderSystem.getShader();
        float savedFogStart = RenderSystem.getShaderFogStart(), savedFogEnd = RenderSystem.getShaderFogEnd();
        float[] savedFogColor = RenderSystem.getShaderFogColor().clone();
        FogShape savedFogShape = RenderSystem.getShaderFogShape();
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean ssbo = GL.getCapabilities().OpenGL43;
        int ssboGeneric = ssbo ? GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING) : 0;
        int ssboIndexed0 = ssbo ? GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 0) : 0;
        try {
            if (IrisCompat.shaderPackInUse() && drawAll(mc, main)) {
                overlay(main);
            }
        } catch (RuntimeException e) {
            warnOnce("Couldn't draw Island Paintings over the shader pack", e);
        } finally {
            PAINTINGS.clear();
            CATS.clear();
            IslandCatBatch.discard();
            // back to vanilla's defaults (what the hand and GUI drawn next expect) and the previous view
            RenderSystem.setShaderFogStart(savedFogStart);
            RenderSystem.setShaderFogEnd(savedFogEnd);
            RenderSystem.setShaderFogColor(savedFogColor[0], savedFogColor[1], savedFogColor[2], savedFogColor[3]);
            RenderSystem.setShaderFogShape(savedFogShape);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            RenderSystem.getModelViewMatrix().set(savedModelView);
            RenderSystem.setShader(() -> savedShader);
            // tells Iris the main target is bound again, then rebinds what really was
            main.bindWrite(false);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(0, 0, main.width, main.height);
            if (ssbo) {
                // Shadered's cat renderer uses storage buffers; a shader pack may keep its own bound
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssboIndexed0);
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboGeneric);
            }
            BufferUploader.invalidate();
        }
    }

    /** Draws the noted paintings and cats into the target; false if nothing could be drawn. */
    private static boolean drawAll(Minecraft mc, RenderTarget main) {
        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            target.enableStencil(); // the painting's window needs it
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        resetState();
        target.bindWrite(true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clearStencil(0);
        // The frame's final depth, so whatever ends up in front (world, water, the hand) hides them. Depth can only be
        // copied between buffers of the same format (the main target has stencil: ClientSetup turns it on);
        // otherwise they are drawn without anything in front.
        if (main.isStencilEnabled() == target.isStencilEnabled()) {
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
            target.copyDepthFrom(main);
            target.bindWrite(true);
        } else {
            RenderSystem.clearDepth(1.0);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
        }

        // the view the level was drawn with (block entities' poses are camera-relative, the camera's turn is in here)
        RenderSystem.setProjectionMatrix(levelProjectionSet ? levelProjection : projection, sorting);
        RenderSystem.getModelViewMatrix().set(levelViewSet ? levelView : modelView);
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
        RenderSystem.setShaderFogShape(fogShape);
        if (mc.level != null && mc.level.effects().constantAmbientLight()) {
            Lighting.setupNetherLevel();
        } else {
            Lighting.setupLevel();
        }

        drawing = true;
        try {
            for (Runnable painting : PAINTINGS) {
                try {
                    target.bindWrite(true); // (in case the last one ended with another framebuffer bound)
                    painting.run();
                } catch (RuntimeException e) {
                    warnOnce("Couldn't draw an Island Painting over the shader pack", e);
                }
            }
            if (!CATS.isEmpty()) {
                target.bindWrite(true);
                IslandCatBatch.addAll(CATS);
                IslandCatBatch.flush(catPartialTick);
            }
        } finally {
            drawing = false;
        }
        target.bindWrite(true);
        return true;
    }

    /**
     * Sets everything drawing relies on to vanilla's defaults, each one set the other way first so the call reaches
     * OpenGL even if GlStateManager's memory of it is wrong.
     */
    private static void resetState() {
        GL11.glDepthRange(0.0, 1.0);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glDisable(GL32.GL_DEPTH_CLAMP);
        GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
        RenderSystem.disableColorLogicOp();
        RenderSystem.disableDepthTest();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.disableBlend();
        RenderSystem.enablePolygonOffset();
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.defaultBlendFunc();
        RenderSystem.stencilMask(0x00);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilFunc(GL11.GL_NEVER, 0, 0x00);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        RenderSystem.stencilOp(GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ZERO);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GlCacheSync.forgetTextures();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferUploader.invalidate();
    }

    /** Lays the target over the screen where it has something (what covers it was handled while drawing it). */
    private static void overlay(RenderTarget main) {
        ShaderInstance shader = IrisShaders.overlay;
        if (shader == null) {
            if (!warnedNoShader) {
                warnedNoShader = true;
                SkyBlocksMod.LOGGER.error("Shadered+'s shader pack overlay shader didn't load; Island Paintings won't show with shader packs");
            }
            return;
        }
        resetState();
        main.bindWrite(true);
        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0, 1, 0, 1, -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.getModelViewMatrix().identity();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        // the target starts at (0,0,0,0) and everything is blended onto it: its colors are premultiplied
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        shader.setSampler("OverlaySampler", target.getColorTextureId());
        RenderSystem.setShader(() -> shader);

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(0, 0, 0).setUv(0, 0);
        builder.addVertex(1, 0, 0).setUv(1, 0);
        builder.addVertex(1, 1, 0).setUv(1, 1);
        builder.addVertex(0, 1, 0).setUv(0, 1);
        MeshData mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void warnOnce(String message, RuntimeException e) {
        if (!warnedError) {
            warnedError = true;
            SkyBlocksMod.LOGGER.error(message, e);
        }
    }

    /** If the end-of-frame hook didn't run, don't let the notes pile up (and say so once). */
    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        if (!PAINTINGS.isEmpty() || !CATS.isEmpty()) {
            PAINTINGS.clear();
            CATS.clear();
            if (!warnedNoComposite) {
                warnedNoComposite = true;
                SkyBlocksMod.LOGGER.warn("Island Paintings aren't being drawn on top of the shader pack (end-of-frame hook not running)");
            }
        }
    }
}
