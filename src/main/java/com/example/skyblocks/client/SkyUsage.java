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
import com.noodlegamer76.shadered.client.renderer.ComplexPassRenderer;
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

    /**
     * In a mirror, a sky's last picture is reused (it's painted from the real camera's angle anyway, so it's never
     * exact in a mirror); one never painted yet is painted once.
     */

    /**
     * Something uses this sky's picture where we can't tell where on screen it is (an item, a liquid, a painting...):
     * its whole picture is drawn, for a few seconds after.
     */
    public static void mark(@Nullable SkyblockBatchData data) {
        if (MirrorRenderer.isRenderingReflection()) {
            return; // (a sky seen only in a mirror isn't kept painted every frame)
        }
        if (data instanceof Tracked tracked) {
            tracked.skyblocks$markUsed();
        }
        wantWhole(data);
    }

    /** The sky's batch was looked up (to queue sky blocks, mostly): used, but its blocks say where on screen it's needed. */
    public static void markLight(@Nullable SkyblockBatchData data) {
        if (MirrorRenderer.isRenderingReflection()) {
            return;
        }
        if (data instanceof Tracked tracked) {
            tracked.skyblocks$markUsed();
        }
    }

    /**
     * Drawn in the HUD (the hotbar): only the hotbar's strip of the picture is needed, for a few seconds after. Set
     * while the HUD is drawn ({@link #inHud}) and for the hotbar at the start of each frame.
     */
    public static void markHud(@Nullable SkyblockBatchData data) {
        markLight(data);
        if (data != null) {
            HUD_UNTIL.put(data, System.nanoTime() + KEEP_NANOS);
        }
    }

    /** True while the game draws the HUD (hotbar and all): sky items there only need the hotbar's strip. */
    public static boolean inHud;
    private static final Map<SkyblockBatchData, Long> HUD_UNTIL = new IdentityHashMap<>();
    private static boolean skyStageScissor;

    /** The hotbar's strip of the screen, in the sky pictures' pixels (from the bottom), with a margin. */
    private static int[] hudRect() {
        Minecraft mc = Minecraft.getInstance();
        ComplexPassRenderer renderer = ComplexPassRenderer.getInstance();
        int width = Math.max(1, renderer.getPreviousWidth());
        double scale = mc.getWindow().getGuiScale();
        int half = (int) Math.ceil(96 * scale);
        return new int[] {Math.max(0, width / 2 - half), 0, Math.min(width, width / 2 + half), (int) Math.ceil(26 * scale)};
    }

    /**
     * Sky blocks seen in mirrors: a mirror's picture reads the sky's picture where its own pixels are (its lower left
     * corner), so only that part is needed - drawn at the next frame's sky stage (in a mirror it's never exact anyway).
     * Per sky: {frame asked in, x0, y0, x1, y1}.
     */
    private static final Map<SkyblockBatchData, long[]> MIRROR_WANT = new IdentityHashMap<>();

    /** Sky blocks of {@code taken} (sky -> its blocks) drawn now into a mirror's picture, with the view and projection set. */
    public static void inMirror(Map<SkyblockBatchData, SkyblockBatchData> taken) {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        Matrix4f toClip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        for (Map.Entry<SkyblockBatchData, SkyblockBatchData> e : taken.entrySet()) {
            SkyblockBatchData data = e.getKey();
            RenderableComplexPass pass = PASSES.get(data);
            if (pass instanceof Pass tracked && tracked.skyblocks$drawnFrame() < 0) {
                ensureFresh(data); // (never drawn yet: once, all of it)
                continue;
            }
            int[] r = rectOf(e.getValue(), toClip, Math.max(1, viewport[2]), Math.max(1, viewport[3]));
            if (r == null) {
                r = new int[] {0, 0, Math.max(1, viewport[2]), Math.max(1, viewport[3])};
            }
            long[] w = MIRROR_WANT.get(data);
            if (w != null && w[0] == frame) {
                w[1] = Math.min(w[1], r[0]);
                w[2] = Math.min(w[2], r[1]);
                w[3] = Math.max(w[3], r[2]);
                w[4] = Math.max(w[4], r[3]);
            } else {
                MIRROR_WANT.put(data, new long[] {frame, r[0], r[1], r[2], r[3]});
            }
            if (data instanceof Tracked tracked) {
                tracked.skyblocks$markUsed();
            }
        }
    }

    // (F3: how the skies' pictures were drawn this frame)
    private static int wholeNow, partNow;
    private static double wholeAvg, partAvg;
    private static final Map<String, Integer> REASONS_NOW = new java.util.TreeMap<>();
    private static String reasons = "";

    public static String debugLine() {
        return String.format(java.util.Locale.ROOT, "Sky pictures: %.1f whole, %.1f only in part%s", wholeAvg, partAvg, reasons);
    }

    private static void frameDone() {
        wholeAvg = wholeAvg * 0.9 + wholeNow * 0.1;
        partAvg = partAvg * 0.9 + partNow * 0.1;
        if (!REASONS_NOW.isEmpty()) {
            StringBuilder sb = new StringBuilder(" (whole for: ");
            REASONS_NOW.forEach((k, v) -> sb.append(k).append(' ').append(v).append(", "));
            sb.setLength(sb.length() - 2);
            reasons = sb.append(')').toString();
        } else {
            reasons = "";
        }
        wholeNow = 0;
        partNow = 0;
        REASONS_NOW.clear();
    }

    /** After a sky pass drawn at the sky stage: the scissor it got for the hotbar's strip, off again. */
    public static void endSkyStageDraw() {
        if (skyStageScissor) {
            skyStageScissor = false;
            GlStateManager._disableScissorTest();
        }
    }

    private static void wantWhole(@Nullable SkyblockBatchData data) {
        wantWhole(data, reason);
    }

    /** Why the next marks want the whole picture (for F3). */
    private static String reason = "something";
    private static final Map<SkyblockBatchData, String> WHOLE_REASON = new IdentityHashMap<>();

    private static void wantWhole(@Nullable SkyblockBatchData data, String why) {
        if (data != null) {
            WHOLE_UNTIL.put(data, System.nanoTime() + KEEP_NANOS);
            WHOLE_REASON.put(data, why);
        }
    }

    /** {@link #mark}, saying why (for F3). */
    public static void mark(@Nullable SkyblockBatchData data, String why) {
        String before = reason;
        reason = why;
        try {
            mark(data);
        } finally {
            reason = before;
        }
    }

    /**
     * A sky's picture is only drawn where it's needed this frame. Its sky blocks are only known (where they are on
     * screen) just before Shadered draws them: then their part of the screen is drawn (ensureQueuedFresh). Users
     * that could be anywhere on screen (items, liquids, paintings, mirrors) get the whole picture, as before.
     */
    private static final Map<SkyblockBatchData, Long> WHOLE_UNTIL = new IdentityHashMap<>();
    /** Per sky, what of its picture is up to date: {frame, 1 all of it / 0 only x0, y0, x1, y1 (pixels)}. */
    private static final Map<SkyblockBatchData, long[]> COVER = new IdentityHashMap<>();
    /** Whether the on-demand drawing before the sky blocks is in place (else: the whole picture at the sky stage, as before). */
    private static boolean onDemandOk;

    /**
     * At the sky stage, from the sky passes: whether to draw this sky's picture now (the whole of it). On demand
     * (drawn from {@link #ensure}): always.
     */
    public static boolean drawAtSkyStage(@Nullable SkyblockBatchData data) {
        if (drawing) {
            return true;
        }
        if (!recentlyUsed(data)) {
            return false;
        }
        long now = System.nanoTime();
        Long until = data == null ? null : WHOLE_UNTIL.get(data);
        boolean whole = !onDemandOk || data == null || (until != null && until - now > 0);
        if (whole && data != null) {
            COVER.put(data, new long[] {frame, 1, 0, 0, 0, 0});
            wholeNow++;
            REASONS_NOW.merge(WHOLE_REASON.getOrDefault(data, onDemandOk ? "?" : "no on-demand drawing"), 1, Integer::sum);
        }
        if (whole) {
            return true;
        }
        Long hud = HUD_UNTIL.get(data);
        long[] mirror = MIRROR_WANT.get(data);
        boolean hudWanted = hud != null && hud - now > 0, mirrorWanted = mirror != null && frame - mirror[0] <= 2;
        if (hudWanted || mirrorWanted) { // only the hotbar's strip, and what mirrors read
            int[] r = hudWanted ? hudRect() : null;
            if (mirrorWanted) {
                int[] m = {(int) mirror[1], (int) mirror[2], (int) mirror[3], (int) mirror[4]};
                r = r == null ? m : new int[] {Math.min(r[0], m[0]), Math.min(r[1], m[1]), Math.max(r[2], m[2]), Math.max(r[3], m[3])};
            }
            partNow++;
            endSkyStageDraw();
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(r[0], r[1], Math.max(1, r[2] - r[0]), Math.max(1, r[3] - r[1]));
            skyStageScissor = true;
            COVER.put(data, new long[] {frame, 0, r[0], r[1], r[2], r[3]});
            return true;
        }
        return false;
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
            frameDone();
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
        mark(data, "liquids, portals, paintings");
        wantWhole(data, "liquids, portals, paintings"); // (in a mirror too)
        ensure(data, null);
    }

    /** Draws the sky's picture now where it's out of date: within {@code rect} (pixels), or all of it (null). */
    private static void ensure(@Nullable SkyblockBatchData data, @Nullable int[] rect) {
        RenderableComplexPass pass = data == null ? null : PASSES.get(data);
        if (!(pass instanceof Pass tracked) || skyProjection == null || drawing || !RenderSystem.isOnRenderThread()) {
            return;
        }
        long[] cover = COVER.get(data);
        boolean fresh = cover != null && cover[0] == frame && tracked.skyblocks$drawnFrame() == frame;
        if (fresh && (cover[1] == 1 || (rect != null && rect[0] >= cover[2] && rect[1] >= cover[3] && rect[2] <= cover[4] && rect[3] <= cover[5]))) {
            return;
        }
        if (MirrorRenderer.isRenderingReflection() && tracked.skyblocks$drawnFrame() >= 0) {
            return; // in a mirror: its last picture will do (painting one in the middle of a mirror upset the frame)
        }
        int[] box = rect; // (only what's needed now: what was drawn before this frame - the hotbar's strip - stays as it is)
        ComplexPassRenderer renderer = ComplexPassRenderer.getInstance();
        int width = Math.max(1, renderer.getPreviousWidth()), height = Math.max(1, renderer.getPreviousHeight());
        if (box != null && (long) (box[2] - box[0]) * (box[3] - box[1]) > (long) width * height * 6 / 10) {
            box = null; // (most of the screen anyway)
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
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST); // (a mirror draws only its part of the screen)
        int[] scissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        ShaderInstance shader = RenderSystem.getShader();
        int[] textures = {RenderSystem.getShaderTexture(0), RenderSystem.getShaderTexture(1), RenderSystem.getShaderTexture(2)};
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        try {
            if (stencil) {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            if (scissor) {
                GlStateManager._disableScissorTest();
            }
            if (box != null) { // (only this part of the picture)
                GlStateManager._enableScissorTest();
                GlStateManager._scissorBox(box[0], box[1], Math.max(1, box[2] - box[0]), Math.max(1, box[3] - box[1]));
            }
            RenderSystem.setProjectionMatrix(new Matrix4f(skyProjection), VertexSorting.DISTANCE_TO_ORIGIN);
            // the same view rotation Shadered gives its sky passes
            Camera camera = mc.gameRenderer.getMainCamera();
            PoseStack view = new PoseStack();
            view.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            view.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
            pass.render(RenderStage.AFTER_SKY, view, lastRenderTick, lastPartialTick);
            COVER.put(data, box == null ? new long[] {frame, 1, 0, 0, 0, 0} : new long[] {frame, 0, box[0], box[1], box[2], box[3]});
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
            if (scissor) {
                GlStateManager._enableScissorTest();
                GlStateManager._scissorBox(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            } else {
                GlStateManager._disableScissorTest();
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
        onDemandOk = true;
        for (Map.Entry<SkyblockBatchData, RenderableComplexPass> entry : PASSES.entrySet()) {
            SkyblockBatchData data = entry.getKey();
            if (!data.isEmpty()) {
                ensure(data, MirrorRenderer.isRenderingReflection() ? null : queuedRect(data));
            }
        }
    }

    /**
     * Where on screen the sky blocks queued for this sky are (pixels of the sky's picture, a few pixels' margin), or
     * null if that can't be told (one partly behind the camera: then all of the picture).
     */
    @Nullable
    private static int[] queuedRect(SkyblockBatchData data) {
        Minecraft mc = Minecraft.getInstance();
        if (skyProjection == null) {
            return null;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Matrix4f toClip = new Matrix4f(skyProjection)
                .rotateX(camera.getXRot() * net.minecraft.util.Mth.DEG_TO_RAD)
                .rotateY((camera.getYRot() + 180.0F) * net.minecraft.util.Mth.DEG_TO_RAD);
        ComplexPassRenderer renderer = ComplexPassRenderer.getInstance();
        return rectOf(data, toClip, Math.max(1, renderer.getPreviousWidth()), Math.max(1, renderer.getPreviousHeight()));
    }

    /** Where the sky blocks in {@code data} are in a picture {@code width} x {@code height} ({@code toClip}: their poses -> clip space). */
    @Nullable
    private static int[] rectOf(SkyblockBatchData data, Matrix4f toClip, int width, int height) {
        float minX = 2.0F, minY = 2.0F, maxX = -2.0F, maxY = -2.0F;
        org.joml.Vector4f v = new org.joml.Vector4f();
        Matrix4f m = new Matrix4f();
        boolean any = false;
        for (com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass sp : com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass.values()) {
            SkyblockBatchData.PassData pd = data.get(sp);
            if (pd == null) {
                continue;
            }
            java.util.List<Matrix4f> poses = pd.getPose();
            for (Matrix4f pose : poses) {
                m.set(toClip).mul(pose);
                for (int i = 0; i < 8; i++) {
                    // (a block, a little bigger: the cube Shadered draws round it)
                    v.set((i & 1) == 0 ? -0.05F : 1.05F, (i & 2) == 0 ? -0.05F : 1.05F, (i & 4) == 0 ? -0.05F : 1.05F, 1.0F);
                    m.transform(v);
                    if (v.w < 0.05F) {
                        return null;
                    }
                    float x = v.x / v.w, y = v.y / v.w;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
                any = true;
            }
        }
        if (!any) {
            return null;
        }
        int margin = 4;
        int x0 = Math.max(0, (int) Math.floor((Math.max(minX, -1.0F) * 0.5F + 0.5F) * width) - margin);
        int y0 = Math.max(0, (int) Math.floor((Math.max(minY, -1.0F) * 0.5F + 0.5F) * height) - margin);
        int x1 = Math.min(width, (int) Math.ceil((Math.min(maxX, 1.0F) * 0.5F + 0.5F) * width) + margin);
        int y1 = Math.min(height, (int) Math.ceil((Math.min(maxY, 1.0F) * 0.5F + 0.5F) * height) + margin);
        if (x1 <= x0 || y1 <= y0) {
            return new int[] {0, 0, 1, 1}; // (off screen)
        }
        return new int[] {x0, y0, x1, y1};
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
        // the hotbar: only its strip of the screen (the item in your hand: all of it - it's drawn big, in the world);
        // the rest of the inventory only shows with a screen open (below)
        net.minecraft.world.entity.player.Inventory inventory = mc.player.getInventory();
        for (int i = 0; i < 9 && i < inventory.items.size(); i++) {
            net.minecraft.world.item.ItemStack stack = inventory.items.get(i);
            if (i == inventory.selected) {
                markItem(stack);
            } else {
                SkyblockBatchData data = itemSky(stack);
                if (data != null) {
                    markHud(data);
                }
            }
        }
        for (net.minecraft.world.item.ItemStack stack : inventory.offhand) {
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
        SkyblockBatchData data = itemSky(stack);
        if (data != null) {
            mark(data, "items in hand, offhand or an open inventory");
        }
    }

    /** The sky an item shows (a sky block, a Sky Liquid bucket), or null. */
    @Nullable
    private static SkyblockBatchData itemSky(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        com.example.skyblocks.island.ShaderedSkyItems.Sky sky = com.example.skyblocks.island.ShaderedSkyItems.sky(stack);
        if (sky != null) {
            return SkyblockRenderer.getData(sky.type());
        }
        for (com.example.skyblocks.ModFluids.SkyLiquid liquid : com.example.skyblocks.ModFluids.ALL) {
            if (liquid.bucket != null && stack.is(liquid.bucket.get())) {
                return SkyblockRenderer.getData(liquid.sky); // a Sky Liquid bucket shows its sky too
            }
        }
        return null;
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
