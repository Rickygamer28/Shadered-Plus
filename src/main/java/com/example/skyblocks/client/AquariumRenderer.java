package com.example.skyblocks.client;

import com.example.skyblocks.AquariumBlock;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.iris.IrisCompat;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Draws the aquariums: every set of touching aquarium blocks is one tank; each of its outer faces shows the inside of
 * the tank, traced in shaders/core/aquarium.fsh (fish, bubbles, sand, seaweed, light from the surface).
 */
public final class AquariumRenderer {
    private static final int SCAN_RADIUS = 4; // chunks
    private static final int RESCAN_TICKS = 20;
    private static final double RANGE = 128.0;
    private static final long FADE_NANOS = 1_500_000_000L;

    private record Face(BlockPos pos, Direction side) {}

    /** One tank: its blocks' outer faces, its corner and size. */
    private static final class Floor {
        final List<Face> faces = new ArrayList<>();
        BlockPos min;
        int sizeX, sizeY, sizeZ;
    }

    static ShaderInstance shader;
    private static final List<Floor> FLOORS = new ArrayList<>();
    private static boolean dirty = true;
    private static ClientLevel lastLevel;
    private static long lastScan;
    private static int lastChunkX, lastChunkZ;

    private AquariumRenderer() {}

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "aquarium"), DefaultVertexFormat.POSITION), s -> shader = s);
        }
    }

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || MirrorRenderer.isRenderingReflection()
                    || IrisCompat.renderingShadowPass()) {
                return;
            }
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || shader == null) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            int chunkX = SectionPos.blockToSectionCoord(cam.x), chunkZ = SectionPos.blockToSectionCoord(cam.z);
            long time = level.getGameTime();
            if (dirty || level != lastLevel || time - lastScan >= RESCAN_TICKS || time < lastScan || chunkX != lastChunkX || chunkZ != lastChunkZ) {
                scan(level, chunkX, chunkZ);
                dirty = false;
                lastLevel = level;
                lastScan = time;
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
            }
            if (FLOORS.isEmpty()) {
                return;
            }
            Matrix4f view = new Matrix4f(event.getModelViewMatrix());
            Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
            InfinityMirrorRenderer.scoped(view, projection, overlay -> {
                List<Runnable> shapes = new ArrayList<>();
                draw(cam, view, projection, !overlay, shapes);
                InfinityMirrorRenderer.recordShapes(overlay, shapes, 0.0F);
            });
        }
    }

    /** A block changed on the client: rescan if an aquarium block came or went. */
    static void blockChanged(BlockState oldState, BlockState newState) {
        if (oldState.getBlock() instanceof AquariumBlock || newState.getBlock() instanceof AquariumBlock) {
            dirty = true;
        }
    }

    /** Inside a mirror's picture: the tanks seen from the mirrored eye. */
    static void drawInMirror(double ex, double ey, double ez, Matrix4f modelView, Matrix4f projection) {
        if (FLOORS.isEmpty() || shader == null || Minecraft.getInstance().level == null) {
            return;
        }
        draw(new Vec3(ex, ey, ez), modelView, projection, true, new ArrayList<>());
    }

    private static void draw(Vec3 cam, Matrix4f modelView, Matrix4f projection, boolean writeDepth, List<Runnable> shapes) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = cam.add(new Vec3(InfinityMirrorRenderer.eyeOf(projection, modelView)));
        FrustumIntersection seen = new FrustumIntersection(new Matrix4f(projection).mul(modelView));
        long gameTime = mc.level.getGameTime();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        float seconds = (float) ((gameTime % 1_728_000L) + partial) / 20.0F;
        Vector3f eyeOffset = InfinityMirrorRenderer.eyeOf(projection, modelView);

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(writeDepth);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2.0F, -20.0F); // over the blocks' own faces
        try {
            shader.safeGetUniform("Time").set(seconds);
            shader.safeGetUniform("Eye").set(eyeOffset.x, eyeOffset.y, eyeOffset.z);
            RenderSystem.setShader(() -> shader);
            for (Floor floor : FLOORS) {
                List<Face> shown = new ArrayList<>();
                for (Face f : floor.faces) {
                    Direction d = f.side();
                    double plane = f.pos().get(d.getAxis()) + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
                    if ((d.getAxis().choose(eye.x, eye.y, eye.z) - plane) * d.getAxisDirection().getStep() <= 0.0
                            || f.pos().distToCenterSqr(eye) > RANGE * RANGE
                            || !seen.testAab((float) (f.pos().getX() - cam.x), (float) (f.pos().getY() - cam.y), (float) (f.pos().getZ() - cam.z),
                            (float) (f.pos().getX() + 1 - cam.x), (float) (f.pos().getY() + 1 - cam.y), (float) (f.pos().getZ() + 1 - cam.z))) {
                        continue;
                    }
                    shown.add(f);
                }
                if (shown.isEmpty()) {
                    continue;
                }
                float mx = (float) (floor.min.getX() - cam.x), my = (float) (floor.min.getY() - cam.y), mz = (float) (floor.min.getZ() - cam.z);
                float seed = (floor.min.hashCode() & 1023) / 64.0F;
                shader.safeGetUniform("BoxMin").set(mx, my, mz);
                shader.safeGetUniform("BoxSize").set((float) floor.sizeX, (float) floor.sizeY, (float) floor.sizeZ);
                shader.safeGetUniform("Seed").set(seed);
                MeshData mesh = mesh(floor, shown, cam);
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                shapes.add(() -> {
                    shader.safeGetUniform("BoxMin").set(mx, my, mz);
                    shader.safeGetUniform("BoxSize").set((float) floor.sizeX, (float) floor.sizeY, (float) floor.sizeZ);
                    shader.safeGetUniform("Seed").set(seed);
                    MeshData shape = mesh(floor, shown, cam);
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
    }

    /** The tank's outer faces (positions relative to the camera). */
    private static MeshData mesh(Floor floor, List<Face> faces, Vec3 cam) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (Face f : faces) {
            float x = (float) (f.pos().getX() - cam.x), y = (float) (f.pos().getY() - cam.y), z = (float) (f.pos().getZ() - cam.z);
            switch (f.side()) {
                case UP -> quad(b, x, y + 1, z, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z);
                case DOWN -> quad(b, x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1);
                case NORTH -> quad(b, x + 1, y, z, x, y, z, x, y + 1, z, x + 1, y + 1, z);
                case SOUTH -> quad(b, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1);
                case WEST -> quad(b, x, y, z, x, y, z + 1, x, y + 1, z + 1, x, y + 1, z);
                case EAST -> quad(b, x + 1, y, z + 1, x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1);
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

    private static void scan(ClientLevel level, int centerX, int centerZ) {
        FLOORS.clear();
        Set<BlockPos> tiles = new HashSet<>();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(centerX + dx, centerZ + dz, false);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof AquariumBlock)) {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (section.getBlockState(x, y, z).getBlock() instanceof AquariumBlock) {
                                    tiles.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                                }
                            }
                        }
                    }
                }
            }
        }
        // touching aquarium blocks -> tanks
        Set<BlockPos> done = new HashSet<>();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (BlockPos start : tiles) {
            if (done.contains(start)) {
                continue;
            }
            Floor floor = new Floor();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            done.add(start);
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
                for (Direction d : Direction.values()) {
                    n.setWithOffset(p, d);
                    if (tiles.contains(n)) {
                        if (!done.contains(n)) {
                            BlockPos next = n.immutable();
                            done.add(next);
                            queue.add(next);
                        }
                        continue; // (between two tiles: hidden)
                    }
                    if (!level.getBlockState(n).isSolidRender(level, n)) {
                        floor.faces.add(new Face(p, d));
                    }
                }
            }
            floor.min = new BlockPos(minX, minY, minZ);
            floor.sizeX = maxX - minX + 1;
            floor.sizeY = maxY - minY + 1;
            floor.sizeZ = maxZ - minZ + 1;
            FLOORS.add(floor);
        }
    }
}
