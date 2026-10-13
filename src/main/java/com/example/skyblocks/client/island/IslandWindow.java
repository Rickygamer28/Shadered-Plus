package com.example.skyblocks.client.island;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
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
import com.noodlegamer76.shadered.event.RegisterShaders;
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
    /** How far the room goes into the wall (blocks). */
    private static final float DEPTH = 1.0F;
    /** The picture area, inside the 1-pixel frame. */
    private static final float LO = 1.0F / 16.0F, HI = 15.0F / 16.0F;
    /** Just in front of the wall's face (local z = 1 for a north-facing painting). */
    private static final float PICTURE_Z = 1.0F - 0.03F; // far enough from the wall not to flicker at a distance
    private static final ResourceLocation BACKDROP =
            ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/block/island_painting_backdrop.png");

    private static ByteBufferBuilder memory;



    private static ByteBufferBuilder memory() {
        if (memory == null) {
            memory = new ByteBufferBuilder(1 << 18);
        }
        return memory;
    }

    /** Draws the window; view and mesh are null for an empty painting (just the room). */
    static void render(@org.jetbrains.annotations.Nullable IslandView view, @org.jetbrains.annotations.Nullable IslandMesh mesh,
                       net.minecraft.world.item.ItemStack background, Direction facing, PoseStack poseStack,
                       float partialTick, int light) {
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
            drawPicture(picture);

            // 2. Push the depth there back to "far", so the room behind the wall can be seen.
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.stencilMask(0x00);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            GL11.glDepthRange(1.0, 1.0);
            drawPicture(picture);
            GL11.glDepthRange(0.0, 1.0);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableCull();

            // 3. The room and the island, only inside the mark.
            MultiBufferSource.BufferSource scene = MultiBufferSource.immediate(memory());
            if (!drawSkyRoom(poseStack, background)) {
                drawRoom(poseStack, scene, light);
            }
            Map<SkyblockBatchData, SkyblockBatchData> skies = new HashMap<>();
            if (view != null && mesh != null) {
                placeIsland(poseStack, view);
                mesh.render(poseStack.last(), scene);
                // Shadered's renderers (sky blocks, Illusorite ores...) only queue their sky cubes for Shadered's own
                // pass later in the frame, which would draw them outside this window: take what they queue here and
                // draw it now, inside the window.
                Map<SkyblockBatchData, Map<SkyblockPass, Integer>> before = queuedSizes();
                try {
                    view.renderBlockEntities(poseStack, scene, partialTick);
                } finally {
                    takeQueued(before, skies);
                }
                view.renderFigures(poseStack, scene, partialTick);
            }
            scene.endBatch();
            drawSkyBlocks(skies);

            // 4. Put the picture's own depth back (so later effects treat it as the wall) and clear the mark.
            RenderSystem.disableCull();
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_ZERO);
            drawPicture(picture);
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
        }
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
        int passDepth = ComplexPassRenderer.getInstance().getRenderBuffer().getDepthTextureId();
        for (Map.Entry<SkyblockBatchData, SkyblockBatchData> entry : skies.entrySet()) {
            Integer sky = SkyblockRenderer.DATA_LIST.get(entry.getKey());
            if (sky == null) {
                continue;
            }
            for (SkyblockPass pass : SkyblockPass.values()) {
                SkyblockBatchData.PassData data = entry.getValue().get(pass);
                ShaderInstance shader = RegisterShaders.get(pass.getShaderName());
                if (data == null || shader == null) {
                    continue;
                }
                shader.setSampler("Skybox", sky);
                shader.setSampler("PassDepth", passDepth);
                RenderCube.renderSkyBlocks(data, false, shader);
            }
        }
    }

    /** The picture area as a plain quad (only depth / stencil are written while it is drawn). */
    private static void drawPicture(Matrix4f m) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(m, LO, LO, PICTURE_Z);
        builder.addVertex(m, HI, LO, PICTURE_Z);
        builder.addVertex(m, HI, HI, PICTURE_Z);
        builder.addVertex(m, LO, HI, PICTURE_Z);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /**
     * A Shadered sky block as the background: the room's walls show that sky the way Shadered's sky blocks do (a
     * window onto a faraway sky), with the block's filter. False if there is no such sky to draw (painted sky then).
     */
    private static boolean drawSkyRoom(PoseStack poseStack, net.minecraft.world.item.ItemStack background) {
        if (background.isEmpty()) {
            return false;
        }
        com.noodlegamer76.shadered.item.SkyblockData data =
                background.get(com.noodlegamer76.shadered.util.InitDataComponents.SKYBLOCK_DATA.get());
        if (data == null || data.type() == null) {
            return false;
        }
        Integer sky = SkyblockRenderer.DATA_LIST.get(SkyblockRenderer.getData(data.type()));
        SkyblockPass pass = data.pass() != null ? data.pass() : SkyblockPass.NORMAL;
        ShaderInstance shader = RegisterShaders.get(pass.getShaderName());
        if (sky == null || shader == null) {
            return false;
        }
        shader.setSampler("Skybox", sky);
        shader.setSampler("PassDepth", ComplexPassRenderer.getInstance().getRenderBuffer().getDepthTextureId());
        RenderSystem.setShader(() -> shader);
        RenderSystem.disableCull();
        Matrix4f m = poseStack.last().pose();
        float z0 = 1.0F, z1 = 1.0F + DEPTH;
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        skyQuad(builder, m, LO, LO, z1, HI, LO, z1, HI, HI, z1, LO, HI, z1); // back
        skyQuad(builder, m, LO, LO, z0, LO, LO, z1, LO, HI, z1, LO, HI, z0); // left
        skyQuad(builder, m, HI, LO, z1, HI, LO, z0, HI, HI, z0, HI, HI, z1); // right
        skyQuad(builder, m, LO, HI, z1, HI, HI, z1, HI, HI, z0, LO, HI, z0); // top
        skyQuad(builder, m, LO, LO, z0, HI, LO, z0, HI, LO, z1, LO, LO, z1); // floor
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.enableCull();
        return true;
    }

    private static void skyQuad(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                                float x2, float y2, float z2, float x3, float y3, float z3) {
        b.addVertex(m, x0, y0, z0).setUv(0, 1).setColor(255, 255, 255, 255);
        b.addVertex(m, x1, y1, z1).setUv(1, 1).setColor(255, 255, 255, 255);
        b.addVertex(m, x2, y2, z2).setUv(1, 0).setColor(255, 255, 255, 255);
        b.addVertex(m, x3, y3, z3).setUv(0, 0).setColor(255, 255, 255, 255);
    }

    /** The room behind the picture: sky on the back, sides, top and (darker) floor; lit like a painting. */
    private static void drawRoom(PoseStack poseStack, MultiBufferSource buffer, int light) {
        VertexConsumer c = buffer.getBuffer(RenderType.entityCutoutNoCull(BACKDROP));
        PoseStack.Pose pose = poseStack.last();
        float z0 = 1.0F, z1 = 1.0F + DEPTH;
        // back
        quad(c, pose, light, 1.0F, 0, 0, -1, LO, LO, z1, HI, LO, z1, HI, HI, z1, LO, HI, z1);
        // left / right
        quad(c, pose, light, 0.8F, 1, 0, 0, LO, LO, z0, LO, LO, z1, LO, HI, z1, LO, HI, z0);
        quad(c, pose, light, 0.8F, -1, 0, 0, HI, LO, z1, HI, LO, z0, HI, HI, z0, HI, HI, z1);
        // top / floor
        quad(c, pose, light, 0.9F, 0, -1, 0, LO, HI, z1, HI, HI, z1, HI, HI, z0, LO, HI, z0);
        quad(c, pose, light, 0.55F, 0, 1, 0, LO, LO, z0, HI, LO, z0, HI, LO, z1, LO, LO, z1);
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
        c.addVertex(pose.pose(), x, y, z)
                .setColor(shade, shade, shade, 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * Size of one island block in the room (blocks): the same in every painting, so blocks look the same size whatever
     * size the island is; the biggest island (25 wide) just fits the room's width.
     */
    private static final float BLOCK_SCALE = (HI - LO - 0.06F) / com.example.skyblocks.island.IslandScan.MAX_SIZE;
    /** How high above the room's floor the island's lowest block floats (blocks). */
    private static final float ISLAND_LIFT = 0.16F;
    /** Room left above the island's bottom; only islands taller than this are shrunk to stay inside. */
    private static final float MAX_TALL = HI - (LO + ISLAND_LIFT) - 0.03F;

    /** Every block the same size; the island centred on its blocks, bottom at a fixed height. */
    private static void placeIsland(PoseStack poseStack, IslandView view) {
        float scale = Math.min(BLOCK_SCALE, MAX_TALL / view.contentHeight());
        poseStack.translate(0.5F, LO + ISLAND_LIFT, 1.0F + DEPTH / 2.0F);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-view.contentCenterX(), -view.lowestLayer(), -view.contentCenterZ());
    }

    private IslandWindow() {}
}
