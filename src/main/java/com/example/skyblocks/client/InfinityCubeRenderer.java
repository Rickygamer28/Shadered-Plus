package com.example.skyblocks.client;

import com.example.skyblocks.InfinityCubeBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Draws the infinity cubes (found and drawn along with the infinity mirrors, InfinityMirrorRenderer): each cube's faces
 * that you can see, with the infinity_cube shader working out the endless grid of edges behind them. Cubes of one
 * colour that fill a whole box together are drawn as one big cube.
 */
final class InfinityCubeRenderer {
    private static final double RANGE = 128.0;

    /** A cube (one block, or a whole box of them): its corners (block coordinates, inclusive), colour, outer faces. */
    private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color, List<Face> faces) {}

    private record Face(BlockPos pos, Direction side) {}

    private static final List<Box> BOXES = new ArrayList<>();

    private InfinityCubeRenderer() {}

    static boolean isEmpty() {
        return BOXES.isEmpty();
    }

    /** Called by the infinity mirrors' scan with every infinity cube found near the camera (position -> colour). */
    static void found(ClientLevel level, Map<BlockPos, Integer> cubes) {
        BOXES.clear();
        Map<BlockPos, Integer> left = new HashMap<>(cubes);
        while (!left.isEmpty()) {
            BlockPos start = left.keySet().iterator().next();
            int color = left.remove(start);
            List<BlockPos> blocks = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                blocks.add(p);
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
                maxZ = Math.max(maxZ, p.getZ());
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    Integer c = left.get(n);
                    if (c != null && c == color) {
                        left.remove(n);
                        queue.add(n);
                    }
                }
            }
            long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (volume == blocks.size()) {
                BOXES.add(box(level, minX, minY, minZ, maxX, maxY, maxZ, color, blocks));
            } else {
                // not a full box: each cube on its own
                for (BlockPos p : blocks) {
                    BOXES.add(box(level, p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ(), color, List.of(p)));
                }
            }
        }
    }

    private static Box box(ClientLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color, List<BlockPos> blocks) {
        List<Face> faces = new ArrayList<>();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (BlockPos p : blocks) {
            for (Direction d : Direction.values()) {
                n.setWithOffset(p, d);
                boolean inside = n.getX() >= minX && n.getX() <= maxX && n.getY() >= minY && n.getY() <= maxY
                        && n.getZ() >= minZ && n.getZ() <= maxZ;
                if (inside) {
                    continue;
                }
                BlockState next = level.getBlockState(n);
                if (next.isSolidRender(level, n)) {
                    continue; // covered
                }
                faces.add(new Face(p, d));
            }
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ, color, faces);
    }

    /**
     * Draws the cubes seen from {@code cam} (positions relative to it; the view and projection are set): with
     * {@code eye} the real eye relative to it.
     */
    static List<Runnable> draw(Vec3 cam, Vector3f eye, Matrix4f view, Matrix4f projection, float seconds, boolean writeDepth) {
        List<Runnable> shapes = new ArrayList<>();
        ShaderInstance shader = MirrorShaders.infinityCube;
        if (shader == null || BOXES.isEmpty()) {
            return shapes;
        }
        FrustumIntersection seen = new FrustumIntersection(new Matrix4f(projection).mul(view));
        int height = Math.max(1, Minecraft.getInstance().getMainRenderTarget().height);
        float pixel = 2.0F / (Math.abs(projection.m11()) * height);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(writeDepth);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2.0F, -20.0F); // over the block's own faces
        try {
            for (Box b : BOXES) {
                if (b.faces.isEmpty() || new Vec3(b.minX + 0.5, b.minY + 0.5, b.minZ + 0.5).distanceTo(cam) > RANGE + (b.maxX - b.minX) + (b.maxY - b.minY)
                        || !seen.testAab((float) (b.minX - cam.x), (float) (b.minY - cam.y), (float) (b.minZ - cam.z),
                        (float) (b.maxX + 1 - cam.x), (float) (b.maxY + 1 - cam.y), (float) (b.maxZ + 1 - cam.z))) {
                    continue;
                }
                List<Face> facing = new ArrayList<>();
                for (Face f : b.faces) {
                    Direction d = f.side;
                    double plane = f.pos.get(d.getAxis()) + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
                    if ((d.getAxis().choose(cam.x, cam.y, cam.z) - plane) * d.getAxisDirection().getStep() > 0.0) {
                        facing.add(f); // (the faces turned away are behind the ones in front)
                    }
                }
                if (facing.isEmpty()) {
                    continue;
                }
                float[] c = InfinityMirrorRenderer.color(b.color);
                shader.safeGetUniform("Eye").set(eye.x, eye.y, eye.z);
                shader.safeGetUniform("BoxMin").set((float) (b.minX - cam.x), (float) (b.minY - cam.y), (float) (b.minZ - cam.z));
                shader.safeGetUniform("BoxSize").set((float) (b.maxX - b.minX + 1), (float) (b.maxY - b.minY + 1), (float) (b.maxZ - b.minZ + 1));
                shader.safeGetUniform("ColorFront").set(c[0], c[1], c[2]);
                shader.safeGetUniform("ColorDeep").set(c[3], c[4], c[5]);
                shader.safeGetUniform("Rainbow").set(InfinityMirrorRenderer.isRainbow(b.color) ? 1.0F : 0.0F);
                shader.safeGetUniform("Time").set(seconds);
                shader.safeGetUniform("PixelSize").set(pixel);
                RenderSystem.setShader(() -> shader);
                MeshData mesh = mesh(facing, cam);
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                shapes.add(() -> {
                    MeshData shape = mesh(facing, cam);
                    if (shape != null) {
                        BufferUploader.drawWithShader(shape);
                    }
                });
            }
        } finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.enableCull();
        }
        return shapes;
    }

    private static MeshData mesh(List<Face> faces, Vec3 cam) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (Face f : faces) {
            float x = (float) (f.pos.getX() - cam.x), y = (float) (f.pos.getY() - cam.y), z = (float) (f.pos.getZ() - cam.z);
            switch (f.side) {
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

    static boolean isCube(BlockState state) {
        return state.getBlock() instanceof InfinityCubeBlock;
    }
}
