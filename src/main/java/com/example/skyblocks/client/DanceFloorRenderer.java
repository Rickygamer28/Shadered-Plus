package com.example.skyblocks.client;

import com.example.skyblocks.DanceFloorBlock;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.iris.IrisCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Draws the dance floors: every set of touching dance floor tiles is one floor with one animation running across it
 * (shaders/core/dance_floor.fsh, in the floor's own coordinates so it flows from tile to tile). When the animation
 * changes (a redstone cycle, a right-click) the old one fades into the new one. Bass from music playing nearby
 * (client/MusicReactor) makes the lights pulse; one animation is an equaliser.
 */
public final class DanceFloorRenderer {
    private static final int SCAN_RADIUS = 4; // chunks
    private static final int RESCAN_TICKS = 20;
    private static final double RANGE = 128.0;
    private static final long FADE_NANOS = 1_500_000_000L;

    private record Face(BlockPos pos, Direction side) {}

    /** One floor: its tiles' exposed faces, its corner and size, its animation. */
    private static final class Floor {
        final List<Face> faces = new ArrayList<>();
        BlockPos min;
        int sizeX, sizeY, sizeZ;
        int animation;
        boolean powered;
    }

    /** What a floor is showing, kept between scans (by its corner): for the fade from one animation to the next. */
    private static final class Show {
        int current = -1, previous;
        long changed;
    }

    static ShaderInstance shader;
    private static final List<Floor> FLOORS = new ArrayList<>();
    private static final Map<BlockPos, Show> SHOWS = new HashMap<>();
    private static boolean dirty = true;
    private static ClientLevel lastLevel;
    private static long lastScan;
    private static int lastChunkX, lastChunkZ;

    private DanceFloorRenderer() {}

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(SkyBlocksMod.MOD_ID, "dance_floor"), DefaultVertexFormat.POSITION_TEX), s -> shader = s);
        }
    }

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onStage(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
                if (MirrorRenderer.isRenderingReflection()) {
                    return;
                }
                if (IrisCompat.renderingShadowPass() || Minecraft.getInstance().level == null) {
                    ISLANDS.clear();
                    return;
                }
                drawIslands(new Matrix4f(event.getPoseStack().last().pose()), new Matrix4f(event.getProjectionMatrix()));
                return;
            }
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
            Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
            Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
            int timing = WorldTiming.start(WorldTiming.DANCE_FLOORS);
            InfinityMirrorRenderer.scoped(view, projection, overlay -> {
                List<Runnable> shapes = new ArrayList<>();
                draw(FLOORS, cam, view, projection, !overlay, shapes, RANGE);
                InfinityMirrorRenderer.recordShapes(overlay, shapes, 0.0F);
            });
            WorldTiming.stop(timing);
        }
    }

    /** A block changed on the client: rescan if a dance floor tile came, went or changed. */
    static void blockChanged(BlockState oldState, BlockState newState) {
        if (oldState.getBlock() instanceof DanceFloorBlock || newState.getBlock() instanceof DanceFloorBlock) {
            dirty = true;
        }
    }

    /** Inside a mirror's picture: the floors seen from the mirrored eye. */
    static void drawInMirror(double ex, double ey, double ez, Matrix4f modelView, Matrix4f projection) {
        if (FLOORS.isEmpty() || shader == null || Minecraft.getInstance().level == null) {
            return;
        }
        draw(FLOORS, new Vec3(ex, ey, ez), modelView, projection, true, new ArrayList<>(), RANGE);
    }

    private static void draw(List<Floor> floors, Vec3 cam, Matrix4f modelView, Matrix4f projection, boolean writeDepth, List<Runnable> shapes,
                             double range) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = cam.add(new Vec3(InfinityMirrorRenderer.eyeOf(projection, modelView)));
        FrustumIntersection seen = new FrustumIntersection(new Matrix4f(projection).mul(modelView));
        long now = System.nanoTime();
        long gameTime = mc.level.getGameTime();
        float partial = mc.getFrameTime();
        float seconds = (float) ((gameTime % 1_728_000L) + partial) / 20.0F;
        MusicReactor.tick();

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(writeDepth);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2.0F, -20.0F); // over the tiles' own faces
        try {
            shader.safeGetUniform("Time").set(seconds);
            shader.safeGetUniform("Music").set(MusicReactor.music());
            shader.safeGetUniform("Bass").set(MusicReactor.bass());
            for (int i = 0; i < 4; i++) {
                shader.safeGetUniform("Bars" + i).set(MusicReactor.band(i * 4), MusicReactor.band(i * 4 + 1), MusicReactor.band(i * 4 + 2),
                        MusicReactor.band(i * 4 + 3));
            }
            RenderSystem.setShader(() -> shader);
            for (Floor floor : floors) {
                List<Face> shown = new ArrayList<>();
                for (Face f : floor.faces) {
                    Direction d = f.side();
                    double plane = f.pos().get(d.getAxis()) + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
                    if ((d.getAxis().choose(eye.x, eye.y, eye.z) - plane) * d.getAxisDirection().getStep() <= 0.0
                            || f.pos().distToCenterSqr(eye) > range * range
                            || !seen.testAab((float) (f.pos().getX() - cam.x), (float) (f.pos().getY() - cam.y), (float) (f.pos().getZ() - cam.z),
                            (float) (f.pos().getX() + 1 - cam.x), (float) (f.pos().getY() + 1 - cam.y), (float) (f.pos().getZ() + 1 - cam.z))) {
                        continue;
                    }
                    shown.add(f);
                }
                if (shown.isEmpty()) {
                    continue;
                }
                // which animation, and the fade from the last one
                Show show = SHOWS.computeIfAbsent(floor.min, p -> new Show());
                int target = DanceFloorBlock.shown(floor.animation, floor.powered, gameTime);
                if (show.current < 0) {
                    show.current = show.previous = target;
                    show.changed = now - FADE_NANOS;
                } else if (show.current != target) {
                    show.previous = show.current;
                    show.current = target;
                    show.changed = now;
                }
                float fade = Math.min(1.0F, (now - show.changed) / (float) FADE_NANOS);
                shader.safeGetUniform("AnimA").set((float) show.previous);
                shader.safeGetUniform("AnimB").set((float) show.current);
                shader.safeGetUniform("Blend").set(fade * fade * (3.0F - 2.0F * fade));
                shader.safeGetUniform("Size").set((float) floor.sizeX, (float) floor.sizeZ);
                BufferBuilder.RenderedBuffer mesh = mesh(floor, shown, cam);
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                final float a = show.previous, b = show.current, blend = fade * fade * (3.0F - 2.0F * fade);
                shapes.add(() -> {
                    shader.safeGetUniform("AnimA").set(a);
                    shader.safeGetUniform("AnimB").set(b);
                    shader.safeGetUniform("Blend").set(blend);
                    shader.safeGetUniform("Size").set((float) floor.sizeX, (float) floor.sizeZ);
                    BufferBuilder.RenderedBuffer shape = mesh(floor, shown, cam);
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

    /** The floor's faces; texture coordinates = where on the floor (blocks from its corner), so patterns run across. */
    private static BufferBuilder.RenderedBuffer mesh(Floor floor, List<Face> faces, Vec3 cam) {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (Face f : faces) {
            int x = f.pos().getX(), y = f.pos().getY(), z = f.pos().getZ();
            switch (f.side()) {
                case UP -> quad(b, floor, cam, x, y + 1, z, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z, Direction.UP);
                case DOWN -> quad(b, floor, cam, x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1, Direction.DOWN);
                case NORTH -> quad(b, floor, cam, x + 1, y, z, x, y, z, x, y + 1, z, x + 1, y + 1, z, Direction.NORTH);
                case SOUTH -> quad(b, floor, cam, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1, Direction.SOUTH);
                case WEST -> quad(b, floor, cam, x, y, z, x, y, z + 1, x, y + 1, z + 1, x, y + 1, z, Direction.WEST);
                case EAST -> quad(b, floor, cam, x + 1, y, z + 1, x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, Direction.EAST);
            }
        }
        return b.end();
    }

    private static void quad(BufferBuilder b, Floor floor, Vec3 cam, int x0, int y0, int z0, int x1, int y1, int z1,
                             int x2, int y2, int z2, int x3, int y3, int z3, Direction side) {
        vertex(b, floor, cam, x0, y0, z0, side);
        vertex(b, floor, cam, x1, y1, z1, side);
        vertex(b, floor, cam, x2, y2, z2, side);
        vertex(b, floor, cam, x3, y3, z3, side);
    }

    private static void vertex(BufferBuilder b, Floor floor, Vec3 cam, int x, int y, int z, Direction side) {
        float lx = x - floor.min.getX(), ly = y - floor.min.getY(), lz = z - floor.min.getZ();
        float u, v;
        switch (side.getAxis()) {
            case Y -> { u = lx; v = lz; }       // top / bottom: the floor itself
            case Z -> { u = lx; v = ly; }       // the sides: along them, and up
            default -> { u = lz; v = ly; }
        }
        b.vertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).uv(u, v).endVertex();
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
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof DanceFloorBlock)) {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (section.getBlockState(x, y, z).getBlock() instanceof DanceFloorBlock) {
                                    tiles.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                                }
                            }
                        }
                    }
                }
            }
        }
        FLOORS.clear();
        FLOORS.addAll(build(level, tiles));
        if (FLOORS.size() > 4096) {
            FLOORS.clear();
        }
    }

    /** Touching blocks -> floors (in the world, or in an island's own blocks). */
    private static List<Floor> build(net.minecraft.world.level.BlockGetter level, Set<BlockPos> tiles) {
        List<Floor> out = new ArrayList<>();
        // touching tiles -> floors
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
            BlockPos first = start;
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                BlockState state = level.getBlockState(p);
                if (state.getBlock() instanceof DanceFloorBlock) {
                    floor.powered |= state.getValue(DanceFloorBlock.POWERED);
                    if (p.compareTo(first) <= 0) {
                        first = p;
                        floor.animation = state.getValue(DanceFloorBlock.ANIMATION);
                    }
                }
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
            out.add(floor);
        }
        return out;
    }

    // ---- in Sky Islands (on pedestals and in paintings) ----

    /** The floors in an island (built once per island view). */
    public static final class Island {
        public static final Island EMPTY = new Island(List.of());
        final List<Floor> floors;

        Island(List<Floor> floors) {
            this.floors = floors;
        }

        public boolean isEmpty() {
            return floors.isEmpty();
        }
    }

    private record IslandJob(Island island, Matrix4f pose) {}

    private static final List<IslandJob> ISLANDS = new ArrayList<>();

    public static Island islandSet(net.minecraft.world.level.BlockGetter island, int sizeX, int height, int sizeZ) {
        Set<BlockPos> tiles = new HashSet<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = island.getBlockState(p);
                    if (state.getBlock() instanceof DanceFloorBlock b) {
                        tiles.add(p);
                    }
                }
            }
        }
        return tiles.isEmpty() ? Island.EMPTY : new Island(build(island, tiles));
    }

    /** A Sky Island on a pedestal with these, drawn this frame with this pose (drawn after the block entities). */
    public static void queueIsland(Island island, Matrix4f pose) {
        if (!island.isEmpty() && ISLANDS.size() < 64) {
            ISLANDS.add(new IslandJob(island, new Matrix4f(pose)));
        }
    }

    /** Inside an Island Painting's window (its target bound, its mask set): drawn now, in the island's coordinates by pose. */
    public static void drawInWindow(Island island, Matrix4f pose) {
        if (island.isEmpty() || shader == null || Minecraft.getInstance().level == null) {
            return;
        }
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.vertex.PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        try {
            stack.mulPoseMatrix(pose);
            RenderSystem.applyModelViewMatrix();
            draw(island.floors, Vec3.ZERO, modelView, projection, true, new ArrayList<>(), Double.MAX_VALUE / 4);
        } finally {
            stack.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void drawIslands(Matrix4f view, Matrix4f projection) {
        List<IslandJob> jobs = new ArrayList<>(ISLANDS);
        ISLANDS.clear();
        if (jobs.isEmpty() || shader == null) {
            return;
        }
        InfinityMirrorRenderer.scoped(view, projection, overlay -> {
            for (IslandJob job : jobs) {
                Matrix4f modelView = new Matrix4f(view).mul(job.pose());
                com.mojang.blaze3d.vertex.PoseStack stack = RenderSystem.getModelViewStack();
                stack.pushPose();
                try {
                    stack.mulPoseMatrix(job.pose());
                    RenderSystem.applyModelViewMatrix();
                    List<Runnable> shapes = new ArrayList<>();
                    draw(job.island().floors, Vec3.ZERO, modelView, projection, !overlay, shapes, Double.MAX_VALUE / 4);
                    InfinityMirrorRenderer.recordShapes(overlay, shapes, overlay ? 0.012F : 0.0F);
                } finally {
                    stack.popPose();
                    RenderSystem.applyModelViewMatrix();
                }
            }
        });
    }
}
