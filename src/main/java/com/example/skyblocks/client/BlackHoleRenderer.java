package com.example.skyblocks.client;

import com.example.skyblocks.BlackHoleBlock;
import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.example.skyblocks.client.iris.IrisCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The black holes: a box round each hole's reach, traced in shaders/core/black_hole. Right before they are drawn, a
 * copy is made of what has been drawn so far (whatever picture is being drawn: the world, a mirror's), which the
 * shader looks up bent round the hole.
 */
public class BlackHoleRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance shader;
    private static TextureTarget copy;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerBlackHoleRenderer(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "black_hole"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    private static int depthTex, depthFbo, depthW, depthH, depthFormat;

    /**
     * Copies the depth of the picture being drawn now into our own texture (made in the same format as the one being
     * drawn into: only then can it be copied), for the shader: what's really there at each pixel (a singularity in
     * its chamber: its frames left as they are, but not what's in front of them). False if it can't.
     */
    /** (the render test: as if the depth couldn't be copied) */
    public static boolean noDepth;

    private static boolean copyDepth() {
        try {
            int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int name = type == GL11.GL_NONE ? 0 : GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int format;
            if (type == GL11.GL_TEXTURE) {
                int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, name);
                format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
            } else if (type == GL30.GL_RENDERBUFFER) {
                int prev = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, name);
                format = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, prev);
            } else {
                return false;
            }
            int[] vp = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            int w = Math.max(vp[2], 1), h = Math.max(vp[3], 1);
            boolean stencil = format == GL30.GL_DEPTH24_STENCIL8 || format == GL30.GL_DEPTH32F_STENCIL8 || format == GL30.GL_DEPTH_STENCIL;
            if (depthTex == 0 || depthW != w || depthH != h || depthFormat != format) {
                if (depthTex == 0) {
                    depthTex = GL11.glGenTextures();
                    depthFbo = GL30.glGenFramebuffers();
                }
                int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
                int fmt = stencil ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT;
                int ty = format == GL30.GL_DEPTH32F_STENCIL8 ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV : stencil ? GL30.GL_UNSIGNED_INT_24_8 : GL11.GL_FLOAT;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, w, h, 0, fmt, ty, (java.nio.ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, stencil ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTex, 0);
                GL11.glDrawBuffer(GL11.GL_NONE);
                GL11.glReadBuffer(GL11.GL_NONE);
                depthW = w;
                depthH = h;
                depthFormat = format;
            }
            for (int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
                // (earlier errors cleared: the copy's own is what's checked)
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthFbo);
            GL30.glBlitFramebuffer(vp[0], vp[1], vp[0] + w, vp[1] + h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            boolean ok = GL11.glGetError() == GL11.GL_NO_ERROR;
            if (LOGGED.add("depth copy " + ok + " " + format)) { // (once each: what a player's log shows)
                SkyBlocksMod.LOGGER.info("black hole depth copy: format {} ({}), {}", Integer.toHexString(format), type == GL11.GL_TEXTURE ? "texture" : "renderbuffer", ok ? "ok" : "failed");
            }
            RenderSystem.setShaderTexture(1, depthTex);
            return ok;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Copies the picture being drawn now (its colour) into our texture, and hands it to the shader. */
    private static void copyScreen() {
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        int w = Math.max(vp[2], 1), h = Math.max(vp[3], 1);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        if (copy == null) {
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(vp[0], vp[1], vp[0] + w, vp[1] + h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
    }

    abstract static class Types extends RenderType {
        static final RenderType HOLE = hole("skyblocks_black_hole", false);
        /** With the eye inside the hole's box: not hidden by what's in the box (the floor under it, say) - all of it bent. */
        static final RenderType HOLE_INSIDE = hole("skyblocks_black_hole_inside", true);

        /** A singularity in its containment chamber: its colour (its depth not kept), then its shadow's depth only. */
        static final RenderType CHAMBER = hole("skyblocks_black_hole_chamber", false, 1);
        static final RenderType CHAMBER_INSIDE = hole("skyblocks_black_hole_chamber_inside", true, 1);
        static final RenderType CHAMBER_DEPTH = hole("skyblocks_black_hole_chamber_depth", false, 2);
        static final RenderType CHAMBER_DEPTH_INSIDE = hole("skyblocks_black_hole_chamber_depth_inside", true, 2);

        private static RenderType hole(String name, boolean inside) {
            return hole(name, inside, 0);
        }

        /** pass: 0 colour and depth, 1 colour only, 2 its shadow's depth only. */
        private static RenderType hole(String name, boolean inside, int pass) {
            return create(name, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                    false, false, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTexturingState(new TexturingStateShard(name + "_copy", () -> {
                                if (pass != 2) {
                                    copyScreen();
                                }
                                boolean depth = pass == 1 && !noDepth && copyDepth();
                                if (shader != null) {
                                    shader.safeGetUniform("HasDepth").set(depth ? 1.0F : 0.0F);
                                }
                                if (shader != null) {
                                    shader.safeGetUniform("HoleTime").set(LavaLampRenderer.seconds());
                                    shader.safeGetUniform("ItemMode").set(0.0F);
                                    shader.safeGetUniform("DepthOnly").set(pass == 2 ? 1.0F : 0.0F);
                                }
                                if (inside) { // (in front of everything, but its depth still written: the test on, always passing)
                                    RenderSystem.depthFunc(GL11.GL_ALWAYS);
                                }
                            }, () -> {
                                if (inside) {
                                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                                }
                                if (shader != null) {
                                    shader.safeGetUniform("DepthOnly").set(0.0F);
                                }
                            }))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setWriteMaskState(pass == 1 ? COLOR_WRITE : pass == 2 ? DEPTH_WRITE : COLOR_DEPTH_WRITE) // (its depth: see the shader)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL) // (both sides: still there with the eye inside the box)
                            .createCompositeState(false));
        }

        /** As an item: no world behind it to bend; drawn at once with its own matrix. */
        static final RenderType ITEM = create("skyblocks_black_hole_item", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_black_hole_item", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("HoleTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("ItemMode").set(1.0F);
                                shader.safeGetUniform("LampInv").set(ITEM_INV);
                            }
                        }, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    static final Matrix4f ITEM_INV = new Matrix4f();

    public BlackHoleRenderer(BlockEntityRendererProvider.Context context) {}

    private static boolean big(GlowColourBlockEntity be) {
        return be.getBlockState().getBlock() instanceof BlackHoleBlock b && b.big();
    }

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        hole(be.getBlockPos(), poseStack, buffers, big(be) ? 1 : 0, 0.0F, GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds()));
    }

    /** Where a hole of this kind sits in its block, and how far it reaches (kind 2, the singularity: by how full it is). */
    static float centreY(int kind) {
        return kind == 0 ? 0.62F : 0.5F;
    }

    /** (kinds 3, 4, 5: a singularity in its 5, 7 or 9 across containment chamber: {@code grow} is its reach over CHAMBER_REACH.) */
    static final float CHAMBER_REACH = 4.6F;

    static float reach(int kind, float grow) {
        return kind == 0 ? 0.36F : kind == 1 ? 1.5F : kind >= 3 ? CHAMBER_REACH * grow : 0.38F + 0.92F * grow;
    }

    /** A hole (kind 0 the small ornament, 1 the big one, 2 the singularity, {@code grow} 0..1 how full) at this block. */
    static void hole(net.minecraft.core.BlockPos pos, PoseStack poseStack, MultiBufferSource buffers, int kind, float grow, int colour) {
        hole(pos, poseStack, buffers, kind, grow, colour, null);
    }

    /** Things drawn right after a hole (its own pose; the buffers to draw into): in front of it, not bent by it. */
    public interface After {
        void draw(PoseStack poseStack, MultiBufferSource buffers);
    }

    static void hole(net.minecraft.core.BlockPos pos, PoseStack poseStack, MultiBufferSource buffers, int kind, float grow, int colour, After after) {
        if (shader == null) {
            return;
        }
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        float seed = (float) ((h >>> 40) & 0xFFFF) / 65536.0F * 0.9F;
        // the eye inside the box? (the main camera's; the box as in box())
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double reach = reach(kind, grow) * 1.03;
        boolean inside = Math.abs(cam.x - (pos.getX() + 0.5)) < reach && Math.abs(cam.z - (pos.getZ() + 0.5)) < reach
                && Math.abs(cam.y - (pos.getY() + centreY(kind))) < reach;
        if (IrisCompat.renderingShadowPass()) {
            return;
        }
        // drawn after the other block entities (shouldRenderOffScreen: the holes are among the world's "global" ones,
        // drawn last); what's still waiting to be drawn is drawn first - so it's in the picture the hole bends
        // and drawn now (left waiting, the last one would only be drawn at the very end of the frame, too late)
        RenderType type = inside ? Types.HOLE_INSIDE : Types.HOLE;
        boolean main = buffers == Minecraft.getInstance().renderBuffers().bufferSource();
        if (main && !MirrorRenderer.isRenderingReflection() && !IrisCompat.shaderPackInUse()) {
            // the world's view: drawn in Shadered's pass, once its sky blocks and models (the cats) are drawn, so they
            // are in the picture the hole bends (see drawPending)
            if (PENDING.size() < 256) {
                PENDING.add(new Pending(new Matrix4f(poseStack.last().pose()), new Matrix4f(RenderSystem.getModelViewMatrix()),
                        new Matrix4f(RenderSystem.getProjectionMatrix()), inside, kind, grow, colour, seed, after));
            }
            return;
        }
        if (main) {
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        }
        if (kind >= 3) {
            drawChamber(buffers, poseStack.last().pose(), inside, kind, grow, colour, seed);
        } else {
            box(buffers.getBuffer(type), poseStack.last().pose(), kind, grow, colour, seed);
            if (main) {
                Minecraft.getInstance().renderBuffers().bufferSource().endBatch(type);
            }
        }
        if (after != null) {
            after.draw(poseStack, buffers);
        }
    }

    /**
     * Any hole still waiting at the end of the world's drawing (Shadered's pass didn't run this frame - it skips frames
     * with nothing of its own to draw): drawn now, not left out of this frame (it blinked while moving).
     */
    @net.neoforged.fml.common.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME,
            value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Fallback {
        private Fallback() {}

        @net.neoforged.bus.api.SubscribeEvent
        public static void afterWorld(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
            if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER
                    && !MirrorRenderer.isRenderingReflection()) {
                drawPending("at the end of the world's drawing");
            }
        }
    }

    private record Pending(Matrix4f pose, Matrix4f modelView, Matrix4f projection, boolean inside, int kind, float grow, int colour, float seed, After after) {}
    private static final java.util.List<Pending> PENDING = new java.util.ArrayList<>();
    private static int debugDraws;
    private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();

    /**
     * Draws the holes waiting, into whatever picture is being drawn now: called inside Shadered's pass after the
     * block entities - its sky blocks and models drawn, its picture not yet put back on the screen - so they're bent
     * too (or, if that pass didn't run, at its end). Each with the view and projection it was given among the block
     * entities.
     */
    public static void drawPending(String why) {
        if (PENDING.isEmpty()) {
            return;
        }
        java.util.List<Pending> jobs = new java.util.ArrayList<>(PENDING);
        PENDING.clear();
        if (shader == null) {
            return;
        }
        if (LOGGED.add(why) || (RenderTest.ON && (debugDraws++ % 300) == 0)) {
            SkyBlocksMod.LOGGER.info("black holes drawn {}: {}", why, jobs.size());
        }
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource main = mc.renderBuffers().bufferSource();
        main.endBatch();
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.vertex.VertexSorting savedSorting = RenderSystem.getVertexSorting();
        org.joml.Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
            for (Pending job : jobs) {
                RenderSystem.setProjectionMatrix(job.projection(), com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
                stack.set(job.modelView());
                RenderSystem.applyModelViewMatrix();
                if (job.kind() >= 3) {
                    drawChamber(main, job.pose(), job.inside(), job.kind(), job.grow(), job.colour(), job.seed());
                } else {
                    RenderType type = job.inside() ? Types.HOLE_INSIDE : Types.HOLE;
                    box(main.getBuffer(type), job.pose(), job.kind(), job.grow(), job.colour(), job.seed());
                    main.endBatch(type); // (each its own copy of the picture: one hole bends another)
                }
                if (job.after() != null) {
                    PoseStack ps = new PoseStack();
                    ps.last().pose().set(job.pose());
                    ps.last().normal().set(new org.joml.Matrix3f(job.pose()).invert().transpose());
                    try {
                        job.after().draw(ps, main);
                    } catch (RuntimeException ignored) {
                        // (left out this frame)
                    }
                    main.endBatch();
                }
            }
        } finally {
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        }
    }

    /** A hole as an item, drawn at once with this item's matrix (shrunk to fit for the big one). */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers, boolean big) {
        if (shader == null) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert();
        box(buffers.getBuffer(Types.ITEM), m, big ? 1 : 0, 0.0F, big ? 0xFFB070 : 0x9A6BFF, 0.3F);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(Types.ITEM);
        }
    }

    /** A hole of any kind as an item (the singularity: as it looks with {@code grow} filled). */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers, int kind, float grow, int colour) {
        if (shader == null) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert();
        box(buffers.getBuffer(Types.ITEM), m, kind, grow, colour, 0.3F);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(Types.ITEM);
        }
    }

    /** A singularity in its chamber: its colour (bending what's beyond, its frames left as they are), then its shadow's depth. */
    private static void drawChamber(MultiBufferSource buffers, Matrix4f pose, boolean inside, int kind, float grow, int colour, float seed) {
        RenderType colourPass = inside ? Types.CHAMBER_INSIDE : Types.CHAMBER;
        RenderType depthPass = inside ? Types.CHAMBER_DEPTH_INSIDE : Types.CHAMBER_DEPTH;
        box(buffers.getBuffer(colourPass), pose, kind, grow, colour, seed);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(colourPass);
        }
        box(buffers.getBuffer(depthPass), pose, kind, grow, colour, seed);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(depthPass);
        }
    }

    /** The box round the hole's reach, its faces facing out (the same numbers as the shader's). */
    static void box(VertexConsumer vc, Matrix4f m, int kind, float grow, int colour, float seed) {
        // (a little bigger than the reach: so its faces never lie flat on a floor, flickering against it)
        float cx = 0.5F, cy = centreY(kind), cz = 0.5F, reach = reach(kind, grow) * 1.03F;
        int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255, a = Math.round(Math.max(0.0F, Math.min(1.0F, grow)) * 255.0F);
        int[][] faces = {{4, 5, 7, 6}, {1, 0, 2, 3}, {5, 1, 3, 7}, {0, 4, 6, 2}, {6, 7, 3, 2}, {0, 1, 5, 4}};
        for (int[] f : faces) {
            for (int k : f) {
                vc.addVertex(m, cx + ((k & 1) != 0 ? reach : -reach), cy + ((k & 2) != 0 ? reach : -reach), cz + ((k & 4) != 0 ? reach : -reach))
                        .setUv(k, kind * 10.0F + seed).setColor(r, g, b, a);
            }
        }
    }

    public AABB getRenderBoundingBox(GlowColourBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(big(be) ? 1.1 : 0.0);
    }

    @Override
    public boolean shouldRenderOffScreen(GlowColourBlockEntity be) {
        return true; // (so it is drawn after the rest: see render)
    }
}
