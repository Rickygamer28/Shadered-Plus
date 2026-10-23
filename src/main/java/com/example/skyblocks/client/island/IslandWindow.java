package com.example.skyblocks.client.island;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.renderer.ComplexPassRenderer;
import com.noodlegamer76.shadered.client.renderer.SkyblockRenderer;
import com.noodlegamer76.shadered.client.util.RenderCube;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The Island Painting's "window": the painting is flat on the wall, and through its picture you look into a small
 * sky-walled room that seems to go into the wall, with the island floating in it.
 *
 * The wall would normally hide anything behind it, so: the picture area is marked in the stencil buffer (only where
 * it is actually visible, so things in front of the painting still cover it), the depth there is pushed back to
 * "far", the room and island are drawn only inside the mark, and then the picture's depth and the stencil are put
 * back. The room and island are drawn right away with their own buffers, while that state is set.
 */
final class IslandWindow {
    /** How far the room goes into the wall, per block of the painting's smaller side (blocks). */
    private static final float DEPTH = 1.0F;
    /** The 1-pixel frame: the picture starts this far in from the painting's edges. */
    private static final float LO = 1.0F / 16.0F;
    /**
     * Exactly on the wall's face (local z = 1 for a north-facing painting), so the window's edges line up with the
     * frame's opening from every angle. It is pulled slightly towards the camera in depth only (polygon offset)
     * so it doesn't flicker against the wall.
     */
    private static final float PICTURE_Z = 1.0F;
    private static final ResourceLocation BACKDROP =
            new ResourceLocation(SkyBlocksMod.MOD_ID, "textures/block/island_painting_backdrop.png");

    private static BufferBuilder memory;



    private static BufferBuilder memory() {
        if (memory == null) {
            memory = new BufferBuilder(1 << 18);
        }
        return memory;
    }

    /**
     * Draws the window of a painting {@code width} x {@code height} blocks (joined paintings; 1 x 1 alone), from its
     * bottom corner block; view and mesh are null for an empty painting (just the room).
     */
    static void render(@org.jetbrains.annotations.Nullable IslandView view, @org.jetbrains.annotations.Nullable IslandMesh mesh,
                       net.minecraft.world.item.ItemStack background, int rotation, Direction facing, int width, int height,
                       PoseStack poseStack, float partialTick, int light, double distance, double figureReach, boolean details) {
        // With an Iris shader pack: nothing in Iris's shadow pass (the window doesn't cast shadows), and otherwise
        // drawn into our own target (Iris's buffers have no stencil, and the pack would paint over the inside), which
        // is put over the pack's final image at the end of the frame (client/iris/IrisIslandTarget).
        if (com.example.skyblocks.client.iris.IrisIslandTarget.shadowPass()) {
            return;
        }
        com.example.skyblocks.client.iris.IrisIslandTarget.Scope iris = com.example.skyblocks.client.iris.IrisIslandTarget.active()
                ? com.example.skyblocks.client.iris.IrisIslandTarget.begin() : null;
        try {
            renderWindow(view, mesh, background, rotation, facing, width, height, poseStack, partialTick, light, distance, figureReach, details);
        } finally {
            com.example.skyblocks.client.iris.IrisIslandTarget.end(iris);
        }
    }

    private static void renderWindow(@org.jetbrains.annotations.Nullable IslandView view, @org.jetbrains.annotations.Nullable IslandMesh mesh,
                       net.minecraft.world.item.ItemStack background, int rotation, Direction facing, int width, int height,
                       PoseStack poseStack, float partialTick, int light, double distance, double figureReach, boolean details) {
        Box box = new Box(width, height);
        poseStack.pushPose();
        // work in a north-facing painting's space: the wall behind is at z = 1..2, the room goes from z = 1 to 1 + DEPTH
        float yRot = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        Matrix4f picture = new Matrix4f(poseStack.last().pose());

        PaintingTimings.painting();
        PaintingTimings.mark(PaintingTimings.Phase.WINDOW_MASK);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        try {
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            // 1. Mark where the picture is visible.
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            drawPicture(picture, box);

            // 2. Push the depth there back to "far", so the room behind the wall can be seen.
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.stencilMask(0x00);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            GL11.glDepthRange(1.0, 1.0);
            drawPicture(picture, box);
            GL11.glDepthRange(0.0, 1.0);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableCull();

            // 3. The room and the island, only inside the mark.
            MultiBufferSource.BufferSource scene = MultiBufferSource.immediate(memory());
            PaintingTimings.mark(PaintingTimings.Phase.ROOM);
            if (!drawSkyRoom(poseStack, background, box)) {
                drawRoom(poseStack, scene, light, box);
                scene.endBatch(); // the room first (the island's solid blocks are drawn right away)
            }
            Map<SkyblockBatchData, SkyblockBatchData> skies = new HashMap<>();
            if (view != null && mesh != null) {
                PaintingTimings.mark(PaintingTimings.Phase.ISLAND);
                placeIsland(poseStack, view, rotation, box);
                mesh.render(poseStack.last(), scene);
                if (details) {
                    // infinity mirrors and cubes inside, with their own shaders, inside the window
                    com.example.skyblocks.client.InfinityMirrorRenderer.drawInWindow(view.infinity(), poseStack.last().pose());
                    com.example.skyblocks.client.PortalSkyRenderer.drawInIsland(view, view.portalSkies, poseStack, scene, false);
                    backInside();
                }
                // Shadered's renderers (sky blocks, Illusorite ores...) only queue their sky cubes for Shadered's own
                // pass later in the frame, which would draw them outside this window: take what they queue here and
                // draw it now, inside the window.
                Map<SkyblockBatchData, Map<SkyblockPass, Integer>> before = queuedSizes();
                // cats collected before this painting (in a mirror: the world's cats) are not this window's: set aside
                java.util.List<com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel> outsideCats = new java.util.ArrayList<>();
                com.example.skyblocks.client.IslandCatBatch.drainTo(outsideCats);
                IslandRendering.inWindow = true;
                PaintingTimings.mark(PaintingTimings.Phase.BLOCK_ENTITIES);
                try {
                    if (details) {
                        view.renderBlockEntities(poseStack, scene, partialTick);
                    }
                } finally {
                    IslandRendering.inWindow = false;
                    takeQueued(before, skies);
                }
                PaintingTimings.mark(PaintingTimings.Phase.CATS);
                try {
                    com.example.skyblocks.client.IslandCatBatch.flush(partialTick); // the painting's cats, in one batch
                    backInside(); // Shadered's model renderer may have bound another framebuffer or changed the stencil
                } finally {
                    com.example.skyblocks.client.IslandCatBatch.discard();
                    com.example.skyblocks.client.IslandCatBatch.addAll(outsideCats);
                }
                PaintingTimings.mark(PaintingTimings.Phase.BATCH_DRAW); // (figures queued, then everything queued drawn at endBatch)
                view.renderFigures(poseStack, scene, partialTick, distance, figureReach);
            }
            scene.endBatch();
            PaintingTimings.mark(PaintingTimings.Phase.SKY_BLOCKS);
            drawSkyBlocks(skies);
            backInside();
            PaintingTimings.mark(PaintingTimings.Phase.WINDOW_MASK);

            // (with a shader pack: note the picture's depth where it is visible, so the hand can cover the painting)
            com.example.skyblocks.client.iris.IrisIslandTarget.recordPictureDepth(() -> drawPicture(picture, box));

            // 4. Put the picture's own depth back (so later effects treat it as the wall) and clear the mark.
            RenderSystem.disableCull();
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_ZERO);
            drawPicture(picture, box);
        } finally {
            GL11.glDepthRange(0.0, 1.0);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.stencilMask(0xFF);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.enableCull();
            poseStack.popPose();
            PaintingTimings.stop();
        }
    }

    /**
     * After Shadered's renderers ran (sky pictures, cats), which can bind their own framebuffer and leave a different
     * one bound, or change the stencil test: back to the framebuffer the painting is drawn into (with a shader pack,
     * Shadered+'s own target) and to drawing only inside the window's mark. Each setting is set twice (the other way
     * first), so it reaches OpenGL even if GlStateManager's memory of it is out of date.
     */
    private static void backInside() {
        com.example.skyblocks.client.iris.IrisIslandTarget.rebindIfDrawing();
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glDepthRange(0.0, 1.0);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.stencilMask(0x00);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0x00);
        RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        RenderSystem.stencilOp(GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ZERO);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.disableDepthTest();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.colorMask(true, true, true, true);
    }

    /** How many sky cubes each of Shadered's skies has queued so far this frame, per pass. */
    private static Map<SkyblockBatchData, Map<SkyblockPass, Integer>> queuedSizes() {
        Map<SkyblockBatchData, Map<SkyblockPass, Integer>> sizes = new HashMap<>();
        for (SkyblockBatchData data : SkyblockRenderer.DATA_LIST.keySet()) {
            Map<SkyblockPass, Integer> perPass = new HashMap<>();
            for (SkyblockPass pass : SkyblockPass.values()) {
                SkyblockBatchData.PassData passData = data.get(pass);
                perPass.put(pass, passData == null ? 0 : passData.getPositions().size());
            }
            sizes.put(data, perPass);
        }
        return sizes;
    }

    /** Moves the sky cubes queued since {@code before} out of Shadered's queues into {@code taken} (sky -> cubes). */
    private static void takeQueued(Map<SkyblockBatchData, Map<SkyblockPass, Integer>> before,
                                   Map<SkyblockBatchData, SkyblockBatchData> taken) {
        for (SkyblockBatchData data : SkyblockRenderer.DATA_LIST.keySet()) {
            Map<SkyblockPass, Integer> sizes = before.get(data);
            for (SkyblockPass pass : SkyblockPass.values()) {
                SkyblockBatchData.PassData passData = data.get(pass);
                if (passData == null) {
                    continue;
                }
                int from = sizes == null ? 0 : sizes.getOrDefault(pass, 0);
                int to = passData.getPositions().size();
                if (to <= from) {
                    continue;
                }
                SkyblockBatchData mine = taken.computeIfAbsent(data, d -> new SkyblockBatchData());
                for (int i = from; i < to; i++) {
                    mine.add(pass, passData.getPositions().get(i), passData.getPose().get(i),
                            passData.getInverts().get(i), passData.getAlphas().get(i));
                }
                passData.getPositions().subList(from, to).clear();
                passData.getPose().subList(from, to).clear();
                passData.getAlphas().subList(from, to).clear();
                passData.getInverts().subList(from, to).clear();
                passData.getInvertedIndices().removeIf(index -> index >= from);
            }
        }
    }

    /**
     * Draws the taken sky cubes now (inside the window's mask) the way Shadered's own pass draws them: cube faces
     * showing that sky's picture, which Shadered renders earlier in the frame.
     */
    private static void drawSkyBlocks(Map<SkyblockBatchData, SkyblockBatchData> skies) {
        if (skies.isEmpty()) {
            return;
        }
        for (SkyblockBatchData data : skies.keySet()) {
            com.example.skyblocks.client.SkyUsage.ensureFresh(data); // a resting sky: draw its picture first
        }
        backInside(); // drawing a sky picture binds Shadered's own framebuffer
        int passDepth = ComplexPassRenderer.getInstance().getRenderBuffer().getDepthTextureId();
        for (Map.Entry<SkyblockBatchData, SkyblockBatchData> entry : skies.entrySet()) {
            Integer sky = SkyblockRenderer.DATA_LIST.get(entry.getKey());
            if (sky == null) {
                continue;
            }
            for (SkyblockPass pass : SkyblockPass.values()) {
                SkyblockBatchData.PassData data = entry.getValue().get(pass);
                ShaderInstance shader = pass.getShader();
                if (data == null || shader == null) {
                    continue;
                }
                shader.setSampler("Skybox", sky);
                shader.setSampler("PassDepth", passDepth);
                RenderCube.renderSkyBlocks(data, false, shader);
            }
        }
    }

    /**
     * The room's box, in the bottom corner block's (north-facing) space: the picture from (x0, y0) to (x1, y1) on the
     * wall's face (z = 1), the room going back to z = 1 + depth. {@code scale} = the painting's smaller side, in blocks.
     */
    private record Box(float x0, float y0, float x1, float y1, float depth, float scale) {
        Box(int width, int height) {
            this(LO, LO, width - LO, height - LO, DEPTH * Math.min(width, height), Math.min(width, height));
        }
    }

    /** The picture area as a plain quad (only depth / stencil are written while it is drawn). */
    private static void drawPicture(Matrix4f m, Box box) {
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -10.0F);
        try {
            drawPictureQuad(m, box);
        } finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
        }
    }

    private static void drawPictureQuad(Matrix4f m, Box b) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(m, b.x0(), b.y0(), PICTURE_Z).endVertex();
        builder.vertex(m, b.x1(), b.y0(), PICTURE_Z).endVertex();
        builder.vertex(m, b.x1(), b.y1(), PICTURE_Z).endVertex();
        builder.vertex(m, b.x0(), b.y1(), PICTURE_Z).endVertex();
        ShaderInstance recording = com.example.skyblocks.client.iris.IrisIslandTarget.pictureShader();
        if (recording != null) {
            RenderSystem.setShader(() -> recording);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionShader);
        }
        BufferUploader.drawWithShader(builder.end());
    }

    /**
     * A Shadered sky block as the background: the room's walls show that sky the way Shadered's sky blocks do (a
     * window onto a faraway sky), with the block's filter. False if there is no such sky to draw (painted sky then).
     */
    private static boolean drawSkyRoom(PoseStack poseStack, net.minecraft.world.item.ItemStack background, Box b) {
        if (background.isEmpty()) {
            return false;
        }
        com.example.skyblocks.island.ShaderedSkyItems.Sky data = com.example.skyblocks.island.ShaderedSkyItems.sky(background);
        if (data == null) {
            return false;
        }
        SkyblockBatchData batch = SkyblockRenderer.getData(data.type());
        com.example.skyblocks.client.SkyUsage.ensureFresh(batch); // a resting sky: draw its picture first
        backInside(); // drawing a sky picture binds Shadered's own framebuffer
        Integer sky = SkyblockRenderer.DATA_LIST.get(batch);
        SkyblockPass pass = data.pass();
        ShaderInstance shader = pass.getShader();
        if (sky == null || shader == null) {
            return false;
        }
        shader.setSampler("Skybox", sky);
        shader.setSampler("PassDepth", ComplexPassRenderer.getInstance().getRenderBuffer().getDepthTextureId());
        RenderSystem.setShader(() -> shader);
        RenderSystem.disableCull();
        Matrix4f m = poseStack.last().pose();
        float z0 = 1.0F, z1 = 1.0F + b.depth();
        float x0 = b.x0(), x1 = b.x1(), y0 = b.y0(), y1 = b.y1();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        skyQuad(builder, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // back
        skyQuad(builder, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // left
        skyQuad(builder, m, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1); // right
        skyQuad(builder, m, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0); // top
        skyQuad(builder, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // floor
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
        return true;
    }

    private static void skyQuad(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                                float x2, float y2, float z2, float x3, float y3, float z3) {
        b.vertex(m, x0, y0, z0).uv(0, 1).color(255, 255, 255, 255).endVertex();
        b.vertex(m, x1, y1, z1).uv(1, 1).color(255, 255, 255, 255).endVertex();
        b.vertex(m, x2, y2, z2).uv(1, 0).color(255, 255, 255, 255).endVertex();
        b.vertex(m, x3, y3, z3).uv(0, 0).color(255, 255, 255, 255).endVertex();
    }

    /** The room behind the picture: sky on the back, sides, top and (darker) floor; lit like a painting. */
    private static void drawRoom(PoseStack poseStack, MultiBufferSource buffer, int light, Box b) {
        VertexConsumer c = buffer.getBuffer(RenderType.entityCutoutNoCull(BACKDROP));
        PoseStack.Pose pose = poseStack.last();
        float z0 = 1.0F, z1 = 1.0F + b.depth();
        float x0 = b.x0(), x1 = b.x1(), y0 = b.y0(), y1 = b.y1();
        // back
        quad(c, pose, light, 1.0F, 0, 0, -1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        // left / right
        quad(c, pose, light, 0.8F, 1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(c, pose, light, 0.8F, -1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        // top / floor
        quad(c, pose, light, 0.9F, 0, -1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(c, pose, light, 0.55F, 0, 1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private static void quad(VertexConsumer c, PoseStack.Pose pose, int light, float shade, float nx, float ny, float nz,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vertex(c, pose, light, shade, nx, ny, nz, x0, y0, z0, 0, 1);
        vertex(c, pose, light, shade, nx, ny, nz, x1, y1, z1, 1, 1);
        vertex(c, pose, light, shade, nx, ny, nz, x2, y2, z2, 1, 0);
        vertex(c, pose, light, shade, nx, ny, nz, x3, y3, z3, 0, 0);
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, int light, float shade, float nx, float ny, float nz,
                               float x, float y, float z, float u, float v) {
        c.vertex(pose.pose(), x, y, z)
                .color(shade, shade, shade, 1.0F)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }

    /**
     * Size of one island block in the room (blocks): the same in every painting, so blocks look the same size whatever
     * size the island is; the biggest island (25 wide) just fits the room's width.
     */
    private static final float BLOCK_SCALE = (1.0F - 2 * LO - 0.06F) / com.example.skyblocks.island.IslandScan.MAX_SIZE;
    /** How high above the room's floor the island's lowest block floats (blocks, per block of the smaller side). */
    private static final float ISLAND_LIFT = 0.16F;

    /**
     * Every block the same size; the island centred on its blocks, bottom at a fixed height, turned by
     * {@code rotation} x 45 degrees. Only an island too tall, or too wide when turned, for the room is shrunk.
     */
    private static void placeIsland(PoseStack poseStack, IslandView view, int rotation, Box b) {
        float angle = rotation * 45.0F;
        double radians = Math.toRadians(angle);
        float turnedWidth = (float) (Math.abs(view.contentWidth() * Math.cos(radians))
                + Math.abs(view.contentDepth() * Math.sin(radians)));
        float turnedDepth = (float) (Math.abs(view.contentWidth() * Math.sin(radians))
                + Math.abs(view.contentDepth() * Math.cos(radians)));
        // a joined painting shows the island bigger, by its smaller side (2x2 -> twice as big)
        float lift = ISLAND_LIFT * b.scale();
        float roomWide = b.x1() - b.x0() - 0.06F * b.scale();
        float maxTall = b.y1() - (b.y0() + lift) - 0.03F * b.scale();
        float scale = Math.min(BLOCK_SCALE * b.scale(), maxTall / view.contentHeight());
        scale = Math.min(scale, Math.min(roomWide / turnedWidth, b.depth() * 0.9F / turnedDepth));
        poseStack.translate((b.x0() + b.x1()) / 2.0F, b.y0() + lift, 1.0F + b.depth() / 2.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-view.contentCenterX(), -view.lowestLayer(), -view.contentCenterZ());
    }

    private IslandWindow() {}
}
