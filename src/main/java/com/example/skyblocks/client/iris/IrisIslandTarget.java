package com.example.skyblocks.client.iris;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.IslandCatBatch;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
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
 * So in Iris mode they are drawn into our own offscreen target instead (same size as the screen, with depth and
 * stencil, holding a copy of the world's depth), exactly as without a shader pack. Binding a target that isn't
 * Minecraft's main one also tells Iris not to swap vanilla shaders for the pack's while we draw. Once Iris has
 * finished its final image, the target is drawn over the screen, except where something ended up in front of it
 * later in the frame (the held item, water, island blocks in front of a cat). The insides then look un-shaded (no
 * shader pack lighting), like Shadered's own blocks do.
 * <p>
 * Speed: asking OpenGL for its state (glGet...) makes the driver wait for the GPU, which costs a lot of frame time, so
 * this avoids it. GlStateManager's remembered state may be out of date under Sodium/Iris (calls it thinks are already
 * in effect get skipped), so instead of reading the real state, every setting the painting relies on is set twice
 * (once the other way), which always reaches OpenGL and leaves GlStateManager right. The world's depth is copied once
 * a frame, and only the framebuffer bindings (and Shadered's storage buffer) are read back per painting.
 * <p>
 * Approach and overlay shader from Shadered Compat Fix (github.com/Rickygamer28/shadered-compat-fix, MIT License).
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class IrisIslandTarget {
    private static TextureTarget target;
    /**
     * The cats of Sky Islands on pedestals: drawn into their own colors and depth (not tested against the world when
     * drawn: entities and other things are only drawn into the world's depth later in the frame), then shown where
     * nothing in the frame's final depth is in front of them.
     */
    private static TextureTarget catTarget;
    private static boolean catsUsedThisFrame, drawingCats;
    private static final java.util.List<com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel> PEDESTAL_CATS = new java.util.ArrayList<>();
    /** Something was drawn into the target this frame (it is cleared, and the depth copied, on the first use). */
    private static boolean usedThisFrame;
    private static final Matrix4f worldProjection = new Matrix4f();
    /** Between begin() and end(): the target is what should be bound. */
    private static boolean inScope;
    private static int pictureDepth, pictureDepthWidth, pictureDepthHeight;
    private static int attachedFramebuffer = -1, attachedDepth = -1;
    private static ShaderInstance pictureShader;
    private static boolean warnedIncomplete;
    private static boolean warnedNoShader;
    private static boolean warnedNoComposite;
    private static boolean warnedError;

    private IrisIslandTarget() {}

    /** What to put back after drawing into the target. */
    public record Scope(int drawFramebuffer, int readFramebuffer, int ssboGeneric, int ssboIndexed0) {}

    /** True while a shader pack is active and this isn't Iris's shadow pass: draw through this target. */
    public static boolean active() {
        return IrisCompat.shaderPackInUse() && !IrisCompat.renderingShadowPass();
    }

    /** True during Iris's shadow pass (the world seen from the sun): paintings' insides and island cats are skipped. */
    public static boolean shadowPass() {
        return IrisCompat.renderingShadowPass();
    }

    /** Inside begin()/end(): binds the target again (after one of Shadered's renderers bound another framebuffer). */
    public static void rebindIfDrawing() {
        if (drawingCats && catTarget != null) {
            catTarget.bindWrite(true);
        } else if (inScope && target != null) {
            target.bindWrite(true);
        }
    }

    /**
     * Makes the target: our own colors (what gets laid over the screen), a "painting depth" layer (the depth of each
     * painting's picture where it is visible, so the overlay can let the hand cover it), and as its depth and stencil
     * Minecraft's own depth buffer, which Iris draws the world into. So no copy of the world's depth is needed (that
     * copy cost several ms a frame): the window is tested against the world directly, and as without a shader pack
     * the painting leaves that depth as it found it (the picture's depth put back, the stencil mark cleared).
     */
    private static void prepare(RenderTarget main) {
        if (target == null) {
            target = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
            attachedFramebuffer = -1;
        }
        int mainDepth = main.getDepthTextureId();
        if (attachedFramebuffer == target.frameBufferId && attachedDepth == mainDepth && pictureDepthWidth == main.width
                && pictureDepthHeight == main.height) {
            return;
        }
        if (pictureDepth == 0 || pictureDepthWidth != main.width || pictureDepthHeight != main.height) {
            if (pictureDepth == 0) {
                pictureDepth = GL11.glGenTextures();
            }
            GlStateManager._bindTexture(pictureDepth);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, main.width, main.height, 0, GL11.GL_RED, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            GlStateManager._bindTexture(0);
            pictureDepthWidth = main.width;
            pictureDepthHeight = main.height;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, pictureDepth, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, main.isStencilEnabled() ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, mainDepth, 0);
        GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE && !warnedIncomplete) {
            warnedIncomplete = true;
            SkyBlocksMod.LOGGER.error("Shadered+'s shader pack target is incomplete (status {}); Island Paintings may not show", status);
        }
        attachedFramebuffer = target.frameBufferId;
        attachedDepth = mainDepth;
    }

    /** True between begin() and end(), when the painting is being drawn into the target. */
    public static boolean drawing() {
        return inScope && target != null;
    }

    /**
     * Writes the depth of the painting's picture into the painting depth layer, where the window's stencil mark is
     * (call while the mark is in place); {@code drawPicture} draws the picture quad.
     */
    public static void recordPictureDepth(Runnable drawPicture) {
        ShaderInstance shader = IrisShaders.pictureDepth;
        if (!drawing() || shader == null) {
            return;
        }
        GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT1);
        try {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.disableBlend();
            RenderSystem.stencilMask(0x00);
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            pictureShader = shader;
            drawPicture.run();
        } finally {
            pictureShader = null;
            GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        }
    }

    /** The shader to draw the picture quad with right now (the painting depth one while recording it), else null. */
    public static ShaderInstance pictureShader() {
        return pictureShader;
    }

    /** Binds the offscreen target for drawing: draw as usual, then call {@link #end}. Null if it couldn't be set up. */
    public static Scope begin() {
        com.example.skyblocks.client.island.PaintingTimings.mark(com.example.skyblocks.client.island.PaintingTimings.Phase.IRIS_SETUP);
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        boolean ssbo = GL.getCapabilities().OpenGL43;
        Scope scope = new Scope(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                ssbo ? GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING) : -1,
                ssbo ? GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 0) : -1);
        try {
            prepare(main);
            resetState();
            target.bindWrite(true);
            if (!usedThisFrame) {
                usedThisFrame = true;
                com.example.skyblocks.client.island.PaintingTimings.mark(com.example.skyblocks.client.island.PaintingTimings.Phase.IRIS_DEPTH_COPY);
                // our colors to transparent, the painting depth layer to "far", and the stencil (part of Minecraft's
                // depth buffer, which Iris doesn't use) to 0; the depth itself is the world's and stays
                RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
                RenderSystem.clearStencil(0);
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
                GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT1);
                RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
                GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
                RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
                worldProjection.set(RenderSystem.getProjectionMatrix());
                com.example.skyblocks.client.island.PaintingTimings.mark(com.example.skyblocks.client.island.PaintingTimings.Phase.IRIS_SETUP);
            }
            inScope = true;
            return scope;
        } catch (RuntimeException e) {
            warnOnce("Couldn't set up the shader pack target for Island Paintings", e);
            end(scope);
            return null;
        }
    }

    /**
     * Sets everything the painting (or a full-screen draw) relies on to vanilla's defaults, each one set the other way
     * first so the call reaches OpenGL even if GlStateManager's memory of it is wrong.
     */
    private static void resetState() {
        GL11.glDepthRange(0.0, 1.0);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glDisable(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
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

    /** Puts back the framebuffer from before {@link #begin}. */
    public static void end(Scope scope) {
        inScope = false;
        if (scope == null) {
            return;
        }
        com.example.skyblocks.client.island.PaintingTimings.mark(com.example.skyblocks.client.island.PaintingTimings.Phase.IRIS_RESTORE);
        // Tells Iris the main target is bound again (it swaps vanilla shaders for the pack's as before), then rebinds
        // what was really bound (Iris's gbuffers).
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        main.bindWrite(false);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, scope.readFramebuffer());
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scope.drawFramebuffer());
        RenderSystem.viewport(0, 0, main.width, main.height);
        if (scope.ssboGeneric() >= 0) {
            // Shadered's cat renderer uses storage buffers; a shader pack may keep its own bound
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, scope.ssboIndexed0());
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, scope.ssboGeneric());
        }
        BufferUploader.invalidate();
        com.example.skyblocks.client.island.PaintingTimings.stop();
    }

    /**
     * Takes the cats collected in IslandCatBatch for a Sky Island on a pedestal, to draw with all the others once the
     * block entities are done (one batch a frame); in the shadow pass they are just thrown away.
     */
    public static void queueCats() {
        if (active()) {
            IslandCatBatch.drainTo(PEDESTAL_CATS);
        } else {
            IslandCatBatch.discard();
        }
    }

    /** Draws the queued pedestal cats (once the world's block entities are done, while the world's view is set up). */
    @SubscribeEvent
    public static void onRenderStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                || PEDESTAL_CATS.isEmpty()) {
            return;
        }
        try {
            if (active()) {
                drawCats(event.getPartialTick());
            }
        } finally {
            PEDESTAL_CATS.clear();
            IslandCatBatch.discard();
        }
    }

    private static void drawCats(float partialTick) {
        com.example.skyblocks.client.island.PaintingTimings.mark(com.example.skyblocks.client.island.PaintingTimings.Phase.PEDESTAL_CATS);
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        boolean ssbo = GL.getCapabilities().OpenGL43;
        Scope scope = new Scope(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                ssbo ? GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING) : -1,
                ssbo ? GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 0) : -1);
        try {
            if (catTarget == null) {
                catTarget = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            } else if (catTarget.width != main.width || catTarget.height != main.height) {
                catTarget.resize(main.width, main.height, Minecraft.ON_OSX);
            }
            resetState();
            catTarget.bindWrite(true);
            drawingCats = true;
            if (!catsUsedThisFrame) {
                catsUsedThisFrame = true;
                RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
                RenderSystem.clearDepth(1.0);
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                worldProjection.set(RenderSystem.getProjectionMatrix());
            }
            IslandCatBatch.addAll(PEDESTAL_CATS);
            IslandCatBatch.flush(partialTick);
        } catch (RuntimeException e) {
            warnOnce("Couldn't draw the cats of Sky Islands with the shader pack", e);
        } finally {
            drawingCats = false;
            end(scope);
        }
    }

    /** Called right after the level (and Iris's final pass) has rendered: draws the target over the screen. */
    public static void composite() {
        boolean paintings = usedThisFrame && target != null, cats = catsUsedThisFrame && catTarget != null;
        usedThisFrame = false;
        catsUsedThisFrame = false;
        PEDESTAL_CATS.clear();
        if (!(paintings || cats) || !IrisCompat.shaderPackInUse()) {
            return;
        }
        ShaderInstance shader = IrisShaders.overlay;
        if (shader == null) {
            if (!warnedNoShader) {
                warnedNoShader = true;
                SkyBlocksMod.LOGGER.error("Shadered+'s shader pack overlay shader didn't load; Island Paintings won't show with shader packs");
            }
            return;
        }
        com.example.skyblocks.client.island.PaintingTimings.mark(com.example.skyblocks.client.island.PaintingTimings.Phase.COMPOSITE);
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        Matrix4f savedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        ShaderInstance savedShader = RenderSystem.getShader();
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
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

            // (a part not drawn this frame is switched off with Used, its texture just has to be a valid one)
            shader.setSampler("OverlaySampler", paintings ? target.getColorTextureId() : catTarget.getColorTextureId());
            shader.setSampler("DepthSampler", paintings ? pictureDepth : catTarget.getDepthTextureId());
            shader.setSampler("CatSampler", cats ? catTarget.getColorTextureId() : target.getColorTextureId());
            shader.setSampler("CatDepthSampler", cats ? catTarget.getDepthTextureId() : pictureDepth);
            shader.setSampler("MainDepthSampler", main.getDepthTextureId());
            shader.safeGetUniform("Used").set(paintings ? 1.0F : 0.0F, cats ? 1.0F : 0.0F);
            shader.safeGetUniform("WorldProjMat").set(worldProjection);
            RenderSystem.setShader(() -> shader);

            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.vertex(0, 0, 0).uv(0, 0).endVertex();
            builder.vertex(1, 0, 0).uv(1, 0).endVertex();
            builder.vertex(1, 1, 0).uv(1, 1).endVertex();
            builder.vertex(0, 1, 0).uv(0, 1).endVertex();
            BufferUploader.drawWithShader(builder.end());
        } catch (RuntimeException e) {
            warnOnce("Couldn't draw Island Paintings on top of the shader pack", e);
        } finally {
            // back to vanilla's defaults (what the hand and GUI drawn next expect) and the previous view
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            RenderSystem.getModelViewMatrix().set(savedModelView);
            RenderSystem.setShader(() -> savedShader);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            BufferUploader.invalidate();
            com.example.skyblocks.client.island.PaintingTimings.stop();
        }
    }

    private static void warnOnce(String message, RuntimeException e) {
        if (!warnedError) {
            warnedError = true;
            SkyBlocksMod.LOGGER.error(message, e);
        }
    }

    /** If the end-of-frame hook didn't run, start the next frame clean anyway (and say so once). */
    @SubscribeEvent
    public static void onFrameStart(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        PEDESTAL_CATS.clear();
        if (usedThisFrame || catsUsedThisFrame) {
            usedThisFrame = false;
            catsUsedThisFrame = false;
            if (!warnedNoComposite) {
                warnedNoComposite = true;
                SkyBlocksMod.LOGGER.warn("Island Paintings aren't being drawn on top of the shader pack (end-of-frame hook not running)");
            }
        }
    }
}
