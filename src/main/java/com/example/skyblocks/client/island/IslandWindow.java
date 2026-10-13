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
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import com.noodlegamer76.shadered.event.RegisterShaders;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
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

    /** Shadered sky blocks of the island being drawn in a window (collected by IslandBlockEntity), or null. */
    static List<Sky> capturing;

    record Sky(SkyblockType type, SkyblockPass pass, Matrix4f pose) {}

    private static ByteBufferBuilder memory() {
        if (memory == null) {
            memory = new ByteBufferBuilder(1 << 18);
        }
        return memory;
    }

    /** Draws the window; view and mesh are null for an empty painting (just the room). */
    static void render(@org.jetbrains.annotations.Nullable IslandView view, @org.jetbrains.annotations.Nullable IslandMesh mesh,
                       Direction facing, PoseStack poseStack, float partialTick, int light) {
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
            drawRoom(poseStack, scene, light);
            List<Sky> skies = new ArrayList<>();
            if (view != null && mesh != null) {
                placeIsland(poseStack, view);
                mesh.render(poseStack.last(), scene);
                capturing = skies;
                try {
                    view.renderBlockEntities(poseStack, scene, partialTick);
                } finally {
                    capturing = null;
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

    /**
     * Draws Shadered sky blocks now (inside the window's mask), the way Shadered's own pass draws them: cube faces
     * showing that sky's picture, which Shadered renders earlier in the frame.
     */
    private static void drawSkyBlocks(List<Sky> skies) {
        if (skies.isEmpty()) {
            return;
        }
        Map<SkyblockType, SkyblockBatchData> byType = new HashMap<>();
        BlockPos nowhere = new BlockPos(29_000_000, 0, 29_000_000); // no neighbours there, so no face is skipped
        for (Sky sky : skies) {
            byType.computeIfAbsent(sky.type(), t -> new SkyblockBatchData()).add(sky.pass(), nowhere, sky.pose(), false, 1.0F);
        }
        int passDepth = ComplexPassRenderer.getInstance().getRenderBuffer().getDepthTextureId();
        for (Map.Entry<SkyblockType, SkyblockBatchData> entry : byType.entrySet()) {
            Integer sky = SkyblockRenderer.DATA_LIST.get(SkyblockRenderer.getData(entry.getKey()));
            if (sky == null) {
                continue; // that sky's picture isn't drawn this frame
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
