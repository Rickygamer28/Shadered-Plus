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
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

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
 * stencil, holding a copy of the world's depth so far), exactly as without a shader pack. Binding a target that isn't
 * Minecraft's main one also tells Iris not to swap vanilla shaders for the pack's while we draw. Once Iris has
 * finished its final image, the target is drawn over the screen, except where something ended up in front of it
 * later in the frame (the held item, water, island blocks in front of a cat). The insides then look un-shaded (no
 * shader pack lighting), like Shadered's own blocks do.
 * <p>
 * Approach and overlay shader from Shadered Compat Fix (github.com/Rickygamer28/shadered-compat-fix, MIT License).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
public final class IrisIslandTarget {
    private static TextureTarget target;
    /** Something was drawn into the target this frame (it is cleared on the first use of each frame). */
    private static boolean usedThisFrame;
    private static final Matrix4f worldProjection = new Matrix4f();
    private static boolean warnedNoShader;
    private static boolean warnedNoComposite;
    private static boolean warnedError;

    private IrisIslandTarget() {}

    /** The state to put back after drawing into the target. */
    public record Scope(GlStateSnapshot snapshot, RawGlState raw) {}

    /** True while a shader pack is active and this isn't Iris's shadow pass: draw through this target. */
    public static boolean active() {
        return IrisCompat.shaderPackInUse() && !IrisCompat.renderingShadowPass();
    }

    /** True during Iris's shadow pass (the world seen from the sun): paintings' insides and island cats are skipped. */
    public static boolean shadowPass() {
        return IrisCompat.renderingShadowPass();
    }

    /**
     * Binds the offscreen target for drawing, holding the world's depth so far: draw as usual, then call {@link #end}.
     * Returns null if it couldn't be set up (then draw normally).
     */
    public static Scope begin() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        RawGlState raw = RawGlState.capture();
        try {
            RawGlState.applyVanillaDefaults();
            GlCacheSync.syncAll();
            if (target == null) {
                target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
                target.enableStencil(); // the painting's window needs it
            } else if (target.width != main.width || target.height != main.height) {
                target.resize(main.width, main.height, Minecraft.ON_OSX);
            }
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.stencilMask(0xFF);
            target.bindWrite(true);
            if (!usedThisFrame) {
                usedThisFrame = true;
                RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
                RenderSystem.clearStencil(0);
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
            }
            // Iris draws into Minecraft's own depth texture (its depthtex0), so the main target holds the world's
            // depth so far. Depth can only be copied between buffers of the same format (the main target has
            // stencil: ClientSetup turns it on); otherwise start from "far" and let the end-of-frame overlay hide
            // whatever is in front.
            if (main.isStencilEnabled() == target.isStencilEnabled()) {
                target.copyDepthFrom(main);
            } else {
                RenderSystem.clearDepth(1.0);
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            }
            target.bindWrite(true);
            worldProjection.set(RenderSystem.getProjectionMatrix());
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return new Scope(snapshot, raw);
        } catch (RuntimeException e) {
            if (!warnedError) {
                warnedError = true;
                SkyBlocksMod.LOGGER.error("Couldn't set up the shader pack target for Island Paintings", e);
            }
            restore(snapshot, raw);
            return null;
        }
    }

    /** Puts back the framebuffer and state from before {@link #begin}. */
    public static void end(Scope scope) {
        if (scope != null) {
            restore(scope.snapshot(), scope.raw());
        }
    }

    private static void restore(GlStateSnapshot snapshot, RawGlState raw) {
        // Tells Iris the main target is bound again, so it swaps vanilla shaders for the pack's as before; the
        // snapshot then rebinds the framebuffer that was really bound (Iris's gbuffers).
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        raw.restore();
        GlCacheSync.syncAll();
        snapshot.restore();
    }

    /**
     * Draws the cats collected in IslandCatBatch (a Sky Island on a pedestal) through the target; in the shadow pass
     * they are just thrown away.
     */
    public static void drawCats(float partialTick) {
        try {
            if (!IslandCatBatch.hasPending() || !active()) {
                return;
            }
            Scope scope = begin();
            try {
                IslandCatBatch.flush(partialTick);
            } finally {
                end(scope);
            }
        } finally {
            IslandCatBatch.discard();
        }
    }

    /** Called right after the level (and Iris's final pass) has rendered: draws the target over the screen. */
    public static void composite() {
        if (!usedThisFrame) {
            return;
        }
        usedThisFrame = false;
        if (target == null || !IrisCompat.shaderPackInUse()) {
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
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        RawGlState raw = RawGlState.capture();
        try {
            RawGlState.applyVanillaDefaults();
            GlCacheSync.syncAll();
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            main.bindWrite(true);

            RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0, 1, 0, 1, -1, 1), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewMatrix().identity();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            // the target starts at (0,0,0,0) and everything is blended onto it: its colors are premultiplied
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderColor(1, 1, 1, 1);

            shader.setSampler("OverlaySampler", target.getColorTextureId());
            shader.setSampler("DepthSampler", target.getDepthTextureId());
            shader.setSampler("MainDepthSampler", main.getDepthTextureId());
            shader.safeGetUniform("WorldProjMat").set(worldProjection);
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
        } catch (RuntimeException e) {
            if (!warnedError) {
                warnedError = true;
                SkyBlocksMod.LOGGER.error("Couldn't draw Island Paintings on top of the shader pack", e);
            }
        } finally {
            raw.restore();
            GlCacheSync.syncAll();
            snapshot.restore();
        }
    }

    /** If the end-of-frame hook didn't run, start the next frame clean anyway (and say so once). */
    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        if (usedThisFrame) {
            usedThisFrame = false;
            if (!warnedNoComposite) {
                warnedNoComposite = true;
                SkyBlocksMod.LOGGER.warn("Island Paintings aren't being drawn on top of the shader pack (end-of-frame hook not running)");
            }
        }
    }
}
