package com.example.skyblocks.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.renderer.SkyblockRenderer;
import com.noodlegamer76.shadered.client.util.RenderStage;
import com.noodlegamer76.shadered.client.util.RenderableComplexPass;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Which Shadered skies are actually in use, so the ones nobody is looking at aren't drawn every frame.
 *
 * Shadered draws the picture of every sky (its own ten and our six) into its own screen-sized texture every frame,
 * whether or not a block, liquid or item of that sky is anywhere in sight: sixteen full-screen passes, one of them
 * the whole vanilla sky with clouds again. A sky is marked as used whenever something queues a block of it
 * (SkyblockBatchData.add), asks for its texture (render types, Sky Liquids) or looks up its batch; its picture is then
 * kept up to date for a few seconds after the last use, and not drawn while unused (see SkyPassMixin).
 *
 * A sky that comes back into use after a pause would show an out-of-date picture for one frame (its picture is
 * normally drawn early in the frame, before anything in the frame has asked for it). So the users draw it on demand
 * first ({@link #ensureFresh}): sky blocks (just before Shadered draws them), Sky Liquids and Island Paintings, all at
 * points in the frame where that is safe; sky items in the inventory, hands and open containers are seen at the start
 * of the frame ({@link #markHeldItems}), in time for the normal pass.
 */
public final class SkyUsage {
    /** How long a sky keeps being drawn after it was last used (so looking around doesn't stop and start it). */
    private static final long KEEP_NANOS = 3_000_000_000L;

    /** Added to SkyblockBatchData by SkyblockBatchDataMixin. */
    public interface Tracked {
        long skyblocks$lastUsed();

        void skyblocks$markUsed();
    }

    /** Added to Shadered's sky passes by SkyPassMixin: the frame its picture was last drawn in. */
    public interface Pass {
        long skyblocks$drawnFrame();

        void skyblocks$setDrawnFrame(long frame);
    }

    /** Each sky's pass, by its batch (registered as the passes are made). */
    private static final Map<SkyblockBatchData, RenderableComplexPass> PASSES = new IdentityHashMap<>();

    /** The current frame (counted at the sky stage), and what the sky stage used, for drawing a sky later on. */
    private static long frame;
    private static int lastRenderTick = Integer.MIN_VALUE;
    private static float lastPartialTick = Float.NaN;
    @Nullable
    private static Matrix4f skyProjection;
    private static boolean drawing;

    public static void mark(@Nullable SkyblockBatchData data) {
        if (data instanceof Tracked tracked) {
            tracked.skyblocks$markUsed();
        }
    }

    /** Whether the sky with this batch was used recently (always true if the tracking isn't there). */
    public static boolean recentlyUsed(@Nullable SkyblockBatchData data) {
        if (!(data instanceof Tracked tracked)) {
            return true;
        }
        long last = tracked.skyblocks$lastUsed();
        return last != 0 && System.nanoTime() - last < KEEP_NANOS;
    }

    // ---- called by SkyPassMixin ----

    public static void register(SkyblockBatchData data, Object pass) {
        if (data != null && pass instanceof RenderableComplexPass p) {
            PASSES.put(data, p);
        }
    }

    /** A sky pass is about to run at the sky stage: counts frames and keeps the frame's sky projection. */
    public static void skyStage(int renderTick, float partialTick) {
        if (renderTick != lastRenderTick || Float.compare(partialTick, lastPartialTick) != 0 || skyProjection == null) {
            lastRenderTick = renderTick;
            lastPartialTick = partialTick;
            frame++;
            skyProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        }
    }

    public static long frame() {
        return frame;
    }

    // ---- drawing a sky on demand ----

    /**
     * Marks the sky as used and, if its picture wasn't drawn this frame (it was resting), draws it now. Only call this
     * from a point in the frame where nothing is half set up (not from inside a render type's setup): the GL state
     * that drawing the sky changes is put back, but render states set up for something else would be lost.
     */
    public static void ensureFresh(@Nullable SkyblockBatchData data) {
        mark(data);
        RenderableComplexPass pass = data == null ? null : PASSES.get(data);
        if (!(pass instanceof Pass tracked) || tracked.skyblocks$drawnFrame() == frame || skyProjection == null
                || drawing || !RenderSystem.isOnRenderThread()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        drawing = true;
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        ShaderInstance shader = RenderSystem.getShader();
        int[] textures = {RenderSystem.getShaderTexture(0), RenderSystem.getShaderTexture(1), RenderSystem.getShaderTexture(2)};
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        try {
            if (stencil) {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            RenderSystem.setProjectionMatrix(new Matrix4f(skyProjection), VertexSorting.DISTANCE_TO_ORIGIN);
            // the same view rotation Shadered gives its sky passes
            Camera camera = mc.gameRenderer.getMainCamera();
            PoseStack view = new PoseStack();
            view.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            view.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
            pass.render(RenderStage.AFTER_SKY, view, lastRenderTick, lastPartialTick);
        } catch (RuntimeException e) {
            // leave it out of date rather than break the frame
        } finally {
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.getModelViewMatrix().set(modelView);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (stencil) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            }
            if (shader != null) {
                RenderSystem.setShader(() -> shader);
            }
            for (int i = 0; i < textures.length; i++) {
                RenderSystem.setShaderTexture(i, textures[i]);
            }
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            drawing = false;
        }
    }

    /** Before Shadered draws the queued sky blocks: brings the picture of every sky with blocks queued up to date. */
    public static void ensureQueuedFresh() {
        for (Map.Entry<SkyblockBatchData, RenderableComplexPass> entry : PASSES.entrySet()) {
            if (!entry.getKey().isEmpty()) {
                ensureFresh(entry.getKey());
            }
        }
    }

    /**
     * At the start of a frame: sky items the player holds, has in the hotbar or inventory, or sees in an open
     * container count as used, so their pictures are drawn at the normal time this frame.
     */
    public static void markHeldItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        for (net.minecraft.world.item.ItemStack stack : mc.player.getInventory().items) {
            markItem(stack);
        }
        for (net.minecraft.world.item.ItemStack stack : mc.player.getInventory().offhand) {
            markItem(stack);
        }
        markItem(mc.player.containerMenu.getCarried());
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
                markItem(slot.getItem());
            }
        }
    }

    private static void markItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        com.example.skyblocks.island.ShaderedSkyItems.Sky sky = com.example.skyblocks.island.ShaderedSkyItems.sky(stack);
        if (sky != null) {
            mark(SkyblockRenderer.getData(sky.type()));
            return;
        }
        for (com.example.skyblocks.ModFluids.SkyLiquid liquid : com.example.skyblocks.ModFluids.ALL) {
            if (liquid.bucket != null && stack.is(liquid.bucket.get())) {
                mark(SkyblockRenderer.getData(liquid.sky)); // a Sky Liquid bucket shows its sky too
                return;
            }
        }
    }

    /** Logs whether the tracking mixins are in place (once, at client setup). */
    public static void report() {
        boolean batch = new SkyblockBatchData() instanceof Tracked;
        boolean passes = false;
        for (java.lang.reflect.Method m : com.noodlegamer76.shadered.client.renderer.complexpasses.SkyboxRenderPass.class.getDeclaredMethods()) {
            if (m.getName().contains("skyblocks$skipUnused")) {
                passes = true;
            }
        }
        boolean onDemand = false;
        for (java.lang.reflect.Method m : com.noodlegamer76.shadered.client.renderer.complexpasses.SkyblockRenderPass.class.getDeclaredMethods()) {
            if (m.getName().contains("skyblocks$freshSkies")) {
                onDemand = true;
            }
        }
        com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+: sky usage tracking: batches {}, sky passes {}, {} skies known, on-demand drawing {}",
                batch ? "tracked" : "NOT tracked", passes ? "skipped while unused" : "NOT skipped", PASSES.size(), onDemand ? "on" : "OFF");
    }

    private SkyUsage() {}
}
