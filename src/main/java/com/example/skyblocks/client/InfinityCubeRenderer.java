package com.example.skyblocks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Draws infinity cubes (InfinitySet boxes, drawn along with the infinity mirrors by InfinityMirrorRenderer): each
 * cube's faces that can be seen, with the infinity_cube shader working out the endless grid of edges behind them.
 */
final class InfinityCubeRenderer {
    private static final double RANGE = 128.0;

    /** Drawing into a mirror's picture: the cheaper look (fewer reflections followed). */
    static boolean lite;

    private InfinityCubeRenderer() {}

    /**
     * Draws the boxes (the view and projection set, positions relative to {@code origin}): {@code eye} where the eye is
     * (same coordinates as the boxes), {@code eyeRel} the same relative to origin. What was drawn (for the shader pack
     * overlay's depth test) is added to {@code shapes}.
     */
    static void draw(List<InfinitySet.Box> boxes, Vec3 origin, Vec3 eye, Vector3f eyeRel, Matrix4f modelView, Matrix4f projection,
                     float seconds, boolean writeDepth, List<Runnable> shapes) {
        ShaderInstance shader = MirrorShaders.infinityCube;
        if (shader == null || boxes.isEmpty()) {
            return;
        }
        FrustumIntersection seen = new FrustumIntersection(new Matrix4f(projection).mul(modelView));
        // (the picture being drawn into: in a mirror, its own picture - not the screen - sets how big a pixel is)
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int height = Math.max(1, viewport[3] > 0 ? viewport[3] : Minecraft.getInstance().getMainRenderTarget().height);
        float pixel = 2.0F / (Math.abs(projection.m11()) * height);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(writeDepth);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2.0F, -20.0F); // over the block's own faces
        // (in the normal view: worked out at half size, then put on screen - see HalfRes)
        boolean half = false;
        List<Runnable> drawnShapes = shapes;
        try {
            for (InfinitySet.Box b : boxes) {
                if (b.faces().isEmpty()
                        || new Vec3(b.minX() + 0.5, b.minY() + 0.5, b.minZ() + 0.5).distanceTo(eye) > RANGE + (b.maxX() - b.minX()) + (b.maxY() - b.minY())
                        || !seen.testAab((float) (b.minX() - origin.x), (float) (b.minY() - origin.y), (float) (b.minZ() - origin.z),
                        (float) (b.maxX() + 1 - origin.x), (float) (b.maxY() + 1 - origin.y), (float) (b.maxZ() + 1 - origin.z))) {
                    continue;
                }
                List<InfinitySet.Face> facing = new ArrayList<>();
                for (InfinitySet.Face f : b.faces()) {
                    Direction d = f.side();
                    double plane = f.pos().get(d.getAxis()) + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
                    if ((d.getAxis().choose(eye.x, eye.y, eye.z) - plane) * d.getAxisDirection().getStep() > 0.0) {
                        facing.add(f); // (the faces turned away are behind the ones in front)
                    }
                }
                if (facing.isEmpty()) {
                    continue;
                }
                if (!half && drawnShapes == shapes && HalfRes.begin()) { // (only once there's a cube to draw)
                    half = true;
                    drawnShapes = new ArrayList<>();
                    GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                    pixel = 2.0F / (Math.abs(projection.m11()) * Math.max(1, viewport[3]));
                }
                float[] c = InfinityMirrorRenderer.color(b.color());
                shader.safeGetUniform("Eye").set(eyeRel.x, eyeRel.y, eyeRel.z);
                shader.safeGetUniform("BoxMin").set((float) (b.minX() - origin.x), (float) (b.minY() - origin.y), (float) (b.minZ() - origin.z));
                shader.safeGetUniform("BoxSize").set((float) (b.maxX() - b.minX() + 1), (float) (b.maxY() - b.minY() + 1), (float) (b.maxZ() - b.minZ() + 1));
                shader.safeGetUniform("ColorFront").set(c[0], c[1], c[2]);
                shader.safeGetUniform("ColorDeep").set(c[3], c[4], c[5]);
                shader.safeGetUniform("Rainbow").set(InfinityMirrorRenderer.isRainbow(b.color()) ? 1.0F : 0.0F);
                shader.safeGetUniform("Time").set(seconds);
                shader.safeGetUniform("PixelSize").set(pixel);
                shader.safeGetUniform("Lite").set(lite ? 1.0F : 0.0F);
                shader.safeGetUniform("Pull").set(InfinityMirrorRenderer.pull);
                RenderSystem.setShader(() -> shader);
                MeshData mesh = mesh(facing, origin);
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                drawnShapes.add(() -> {
                    MeshData shape = mesh(facing, origin);
                    if (shape != null) {
                        BufferUploader.drawWithShader(shape);
                    }
                });
            }
        } finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.enableCull();
            if (half) {
                HalfRes.finish(drawnShapes, writeDepth);
                shapes.addAll(drawnShapes);
            }
        }
    }

    private static MeshData mesh(List<InfinitySet.Face> faces, Vec3 origin) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (InfinitySet.Face f : faces) {
            float x = (float) (f.pos().getX() - origin.x), y = (float) (f.pos().getY() - origin.y), z = (float) (f.pos().getZ() - origin.z);
            switch (f.side()) {
                case UP -> quad(b, x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1);
                case DOWN -> quad(b, x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1);
                case NORTH -> quad(b, x, y, z, x + 1, y, z, x + 1, y + 1, z, x, y + 1, z);
                case SOUTH -> quad(b, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1);
                case WEST -> quad(b, x, y, z, x, y, z + 1, x, y + 1, z + 1, x, y + 1, z);
                case EAST -> quad(b, x + 1, y, z, x + 1, y, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z);
            }
        }
        return b.build();
    }

    private static void quad(BufferBuilder b, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        b.addVertex(x0, y0, z0);
        b.addVertex(x1, y1, z1);
        b.addVertex(x2, y2, z2);
        b.addVertex(x3, y3, z3);
    }
}
