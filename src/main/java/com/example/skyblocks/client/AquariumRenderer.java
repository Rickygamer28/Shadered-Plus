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
        final Set<BlockPos> blocks = new HashSet<>();
        BlockPos min;
        int sizeX, sizeY, sizeZ;
        /** Which spots of the box round the tank are water (1 bit each, 24 to a number, 4 numbers to a vec4); or the whole box. */
        final float[] occupancy = new float[OCC_VECS * 4];
        boolean full;
        /** Per fish: the middle of the stretch of water it swims in (x, y, z) and how far it reaches each way (x, y, z). */
        final float[] fish = new float[MAX_FISH * 8];
        int fishCount;
        boolean ocean;
        /** Per decoration: its base (x, y, z, tank coordinates) and type + 8 * (turn + 16 * size * 10). */
        final float[] decor = new float[MAX_DECOR * 4];
        int decorCount;
    }

    private static final int OCC_VECS = 16;
    private static final int MAX_BITS = OCC_VECS * 4 * 24;
    private static final int MAX_FISH = 32;
    private static final int MAX_DECOR = 24;

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
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "aquarium"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s);
        }
    }

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
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
                drawIslands(new Matrix4f(event.getModelViewMatrix()), new Matrix4f(event.getProjectionMatrix()));
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
            Matrix4f view = new Matrix4f(event.getModelViewMatrix());
            Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
            InfinityMirrorRenderer.scoped(view, projection, overlay -> {
                List<Runnable> shapes = new ArrayList<>();
                draw(FLOORS, cam, view, projection, !overlay, shapes, RANGE);
                InfinityMirrorRenderer.recordShapes(overlay, shapes, 0.0F);
            });
        }
    }

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
    public static final class Outline {
        private Outline() {}

        /** No black outline round the one block you look at: the tank is one piece of glass. */
        @SubscribeEvent
        public static void onHighlight(net.neoforged.neoforge.client.event.RenderHighlightEvent.Block event) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && level.getBlockState(event.getTarget().getBlockPos()).getBlock() instanceof AquariumBlock) {
                event.setCanceled(true);
            }
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
        draw(FLOORS, new Vec3(ex, ey, ez), modelView, projection, true, new ArrayList<>(), RANGE);
    }

    private static void draw(List<Floor> floors, Vec3 cam, Matrix4f modelView, Matrix4f projection, boolean writeDepth, List<Runnable> shapes,
                             double range) {
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
                float mx = (float) (floor.min.getX() - cam.x), my = (float) (floor.min.getY() - cam.y), mz = (float) (floor.min.getZ() - cam.z);
                float seed = (floor.min.hashCode() & 1023) / 64.0F;
                shader.safeGetUniform("BoxMin").set(mx, my, mz);
                shader.safeGetUniform("BoxSize").set((float) floor.sizeX, (float) floor.sizeY, (float) floor.sizeZ);
                shader.safeGetUniform("Seed").set(seed);
                setShape(floor);
                MeshData mesh = mesh(floor, shown, cam);
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                shapes.add(() -> {
                    shader.safeGetUniform("BoxMin").set(mx, my, mz);
                    shader.safeGetUniform("BoxSize").set((float) floor.sizeX, (float) floor.sizeY, (float) floor.sizeZ);
                    shader.safeGetUniform("Seed").set(seed);
                    setShape(floor);
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

    private static void setShape(Floor floor) {
        float[] o = floor.occupancy;
        for (int v = 0; v < OCC_VECS; v++) {
            shader.safeGetUniform("Occ" + v).set(o[v * 4], o[v * 4 + 1], o[v * 4 + 2], o[v * 4 + 3]);
        }
        shader.safeGetUniform("Full").set(floor.full ? 1.0F : 0.0F);
        float[] f = floor.fish;
        for (int v = 0; v < MAX_FISH * 2; v++) {
            shader.safeGetUniform("Fish" + v).set(f[v * 4], f[v * 4 + 1], f[v * 4 + 2], f[v * 4 + 3]);
        }
        shader.safeGetUniform("FishCount").set((float) floor.fishCount);
        shader.safeGetUniform("Ocean").set(floor.ocean ? 1.0F : 0.0F);
        float[] d = floor.decor;
        for (int v = 0; v < MAX_DECOR; v++) {
            shader.safeGetUniform("Deco" + v).set(d[v * 4], d[v * 4 + 1], d[v * 4 + 2], d[v * 4 + 3]);
        }
        shader.safeGetUniform("DecoCount").set((float) floor.decorCount);
    }

    /**
     * Puts decorations on the tank's floor (the bottom of each column of water): rocks, shells, starfish, a castle,
     * coral, vases, anemones; in the ocean tank bigger, with coral and anemones more often and a treasure chest.
     */
    private static void placeDecor(Floor floor) {
        java.util.List<BlockPos> bottom = new ArrayList<>();
        for (BlockPos b : floor.blocks) {
            if (!floor.blocks.contains(b.below())) {
                bottom.add(b);
            }
        }
        bottom.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        java.util.Random random = new java.util.Random(floor.min.asLong() * 17L + bottom.size() * 7L + 3L);
        int count = Math.max(2, Math.min(MAX_DECOR, (int) (bottom.size() * (floor.ocean ? 0.45F : 0.6F))));
        boolean castle = false, chest = false;
        floor.decorCount = 0;
        for (int i = 0; i < count; i++) {
            BlockPos cell = bottom.get(random.nextInt(bottom.size()));
            float r = random.nextFloat();
            int type;
            if (floor.ocean) {
                type = r < 0.3F ? 0 : r < 0.55F ? 4 : r < 0.7F ? 6 : r < 0.75F && !chest ? 5 : r < 0.85F ? 2 : r < 0.93F ? 1 : 7;
            } else {
                type = r < 0.3F ? 0 : r < 0.45F ? 1 : r < 0.57F ? 2 : r < 0.65F && !castle ? 3 : r < 0.8F ? 4 : r < 0.9F ? 7 : 6;
            }
            castle |= type == 3;
            chest |= type == 5;
            float size = floor.ocean ? 1.3F + random.nextFloat() * 1.1F : 0.7F + random.nextFloat() * 0.6F;
            size = Math.min(size, floor.sizeY * 2.0F); // (fits under the surface)
            int sizeQ = Math.max(1, Math.min(60, Math.round(size * 10.0F)));
            int turn = random.nextInt(16);
            int k = floor.decorCount++;
            floor.decor[k * 4] = cell.getX() - floor.min.getX() + 0.2F + random.nextFloat() * 0.6F;
            floor.decor[k * 4 + 1] = cell.getY() - floor.min.getY() + (cell.getY() == floor.min.getY() ? 0.12F : 0.0F);
            floor.decor[k * 4 + 2] = cell.getZ() - floor.min.getZ() + 0.2F + random.nextFloat() * 0.6F;
            floor.decor[k * 4 + 3] = type + 8 * (turn + 16 * sizeQ);
        }
    }

    /**
     * Gives each fish a stretch of the tank to swim in: from a random spot of water, a box grown as far as the water
     * goes (so in an L-shaped tank no fish swims into the corner that isn't there).
     */
    private static void placeFish(Floor floor) {
        java.util.List<BlockPos> water = new ArrayList<>(floor.blocks);
        water.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        java.util.Random random = new java.util.Random(floor.min.asLong() * 31L + water.size());
        floor.fishCount = Math.max(3, Math.min(MAX_FISH, (int) (water.size() * (floor.ocean ? 0.8F : 2.4F))));
        for (int i = 0; i < floor.fishCount; i++) {
            BlockPos start = water.get(random.nextInt(water.size()));
            int[] lo = {start.getX(), start.getY(), start.getZ()}, hi = {start.getX(), start.getY(), start.getZ()};
            int[] limit = floor.ocean ? new int[] {24, 10, 24} : new int[] {12, 6, 12};
            for (int round = 0; round < (floor.ocean ? 60 : 24); round++) {
                int axis = random.nextInt(3);
                boolean up = random.nextBoolean();
                if (hi[axis] - lo[axis] + 1 >= limit[axis]) {
                    continue;
                }
                int[] nlo = lo.clone(), nhi = hi.clone();
                if (up) nhi[axis]++; else nlo[axis]--;
                int at = up ? nhi[axis] : nlo[axis];
                boolean all = true;
                for (int x = nlo[0]; x <= nhi[0] && all; x++) {
                    for (int y = nlo[1]; y <= nhi[1] && all; y++) {
                        for (int z = nlo[2]; z <= nhi[2] && all; z++) {
                            int c = axis == 0 ? x : axis == 1 ? y : z;
                            if (c == at && !floor.blocks.contains(new BlockPos(x, y, z))) {
                                all = false;
                            }
                        }
                    }
                }
                if (all) {
                    lo = nlo;
                    hi = nhi;
                }
            }
            for (int k = 0; k < 3; k++) {
                int min = k == 0 ? floor.min.getX() : k == 1 ? floor.min.getY() : floor.min.getZ();
                floor.fish[i * 8 + k] = (lo[k] + hi[k] + 1) * 0.5F - min;   // middle (tank coordinates)
                floor.fish[i * 8 + 4 + k] = (hi[k] - lo[k] + 1) * 0.5F;     // reach each way
            }
        }
    }

    /**
     * The tank's outer faces (positions relative to the camera). Texture coordinates: across the face (0..1); colour:
     * which of the face's 4 edges are edges of the tank (and so get a frame): u = 0, u = 1, v = 0, v = 1.
     */
    private static MeshData mesh(Floor floor, List<Face> faces, Vec3 cam) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Face f : faces) {
            Direction side = f.side();
            Direction du = side.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
            Direction dv = side.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
            int flags = (framed(floor, f.pos(), du.getOpposite(), side) ? 0xFF : 0)
                    | (framed(floor, f.pos(), du, side) ? 0xFF00 : 0)
                    | (framed(floor, f.pos(), dv.getOpposite(), side) ? 0xFF0000 : 0)
                    | (framed(floor, f.pos(), dv, side) ? 0xFF000000 : 0);
            int x = f.pos().getX(), y = f.pos().getY(), z = f.pos().getZ();
            int[][] corners = switch (side) {
                case UP -> new int[][] {{x, y + 1, z}, {x, y + 1, z + 1}, {x + 1, y + 1, z + 1}, {x + 1, y + 1, z}};
                case DOWN -> new int[][] {{x, y, z}, {x + 1, y, z}, {x + 1, y, z + 1}, {x, y, z + 1}};
                case NORTH -> new int[][] {{x + 1, y, z}, {x, y, z}, {x, y + 1, z}, {x + 1, y + 1, z}};
                case SOUTH -> new int[][] {{x, y, z + 1}, {x + 1, y, z + 1}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1}};
                case WEST -> new int[][] {{x, y, z}, {x, y, z + 1}, {x, y + 1, z + 1}, {x, y + 1, z}};
                case EAST -> new int[][] {{x + 1, y, z + 1}, {x + 1, y, z}, {x + 1, y + 1, z}, {x + 1, y + 1, z + 1}};
            };
            for (int[] c : corners) {
                int ox = c[0] - x, oy = c[1] - y, oz = c[2] - z;
                float u = side.getAxis() == Direction.Axis.X ? oz : ox;
                float v = side.getAxis() == Direction.Axis.Y ? oz : oy;
                b.addVertex((float) (c[0] - cam.x), (float) (c[1] - cam.y), (float) (c[2] - cam.z)).setUv(u, v)
                        .setColor(flags & 0xFF, (flags >> 8) & 0xFF, (flags >> 16) & 0xFF, (flags >>> 24) & 0xFF);
            }
        }
        return b.build();
    }

    /** Whether the edge of this face towards {@code d} is an edge of the tank (not where it carries on, flat or round a corner). */
    private static boolean framed(Floor floor, BlockPos pos, Direction d, Direction side) {
        BlockPos next = pos.relative(d);
        return !floor.blocks.contains(next) || floor.blocks.contains(next.relative(side));
    }

    private static void scan(ClientLevel level, int centerX, int centerZ) {
        FLOORS.clear();
        Map<BlockPos, Boolean> tiles = new HashMap<>(); // (true: an ocean aquarium block)
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
                                if (section.getBlockState(x, y, z).getBlock() instanceof AquariumBlock tank) {
                                    tiles.put(new BlockPos(baseX + x, baseY + y, baseZ + z), tank.ocean());
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

    /** Touching blocks -> tanks (in the world, or in an island's own blocks). */
    private static List<Floor> build(net.minecraft.world.level.BlockGetter level, Map<BlockPos, Boolean> tiles) {
        List<Floor> out = new ArrayList<>();
        // touching aquarium blocks -> tanks
        Set<BlockPos> done = new HashSet<>();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (BlockPos start : tiles.keySet()) {
            if (done.contains(start)) {
                continue;
            }
            Floor floor = new Floor();
            floor.ocean = tiles.get(start);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            done.add(start);
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                floor.blocks.add(p);
                minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
                for (Direction d : Direction.values()) {
                    n.setWithOffset(p, d);
                    Boolean kind = tiles.get(n);
                    if (kind != null && kind == floor.ocean) { // (the same kind of tank: one tank)
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
            // the tank's shape for the shader (so an L-shaped tank isn't traced as a full box)
            int volume = floor.sizeX * floor.sizeY * floor.sizeZ;
            floor.full = floor.blocks.size() == volume || volume > MAX_BITS;
            if (!floor.full) {
                for (BlockPos b : floor.blocks) {
                    int index = (b.getX() - minX) + floor.sizeX * ((b.getZ() - minZ) + floor.sizeZ * (b.getY() - minY));
                    floor.occupancy[index / 24] += (float) (1 << (index % 24));
                }
            }
            placeFish(floor);
            placeDecor(floor);
            out.add(floor);
        }
        return out;
    }

    // ---- in Sky Islands (on pedestals and in paintings) ----

    /** The tanks in an island (built once per island view). */
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
        Map<BlockPos, Boolean> tiles = new HashMap<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = island.getBlockState(p);
                    if (state.getBlock() instanceof AquariumBlock b) {
                        tiles.put(p, b.ocean());
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
        org.joml.Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
            stack.mul(pose);
            RenderSystem.applyModelViewMatrix();
            draw(island.floors, Vec3.ZERO, modelView, projection, true, new ArrayList<>(), Double.MAX_VALUE / 4);
        } finally {
            stack.popMatrix();
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
                org.joml.Matrix4fStack stack = RenderSystem.getModelViewStack();
                stack.pushMatrix();
                try {
                    stack.mul(job.pose());
                    RenderSystem.applyModelViewMatrix();
                    List<Runnable> shapes = new ArrayList<>();
                    draw(job.island().floors, Vec3.ZERO, modelView, projection, !overlay, shapes, Double.MAX_VALUE / 4);
                    InfinityMirrorRenderer.recordShapes(overlay, shapes, overlay ? 0.012F : 0.0F);
                } finally {
                    stack.popMatrix();
                    RenderSystem.applyModelViewMatrix();
                }
            }
        });
    }
}
