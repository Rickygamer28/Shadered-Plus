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
        /** Per fish: its species, and its own random numbers (size, speed, 3 path phases, depth, jellyfish beat, 3 school offsets). */
        final int[] kinds = new int[MAX_FISH];
        final float[] rnd = new float[MAX_FISH * 10];
        boolean ocean;
        /** Per decoration: its base (x, y, z, tank coordinates) and type + 8 * (turn + 16 * size * 10). */
        final float[] decor = new float[MAX_DECOR * 4];
        int decorCount;
    }

    private static final int OCC_VECS = 16;
    private static final int MAX_BITS = OCC_VECS * 4 * 24;
    private static final int MAX_FISH = 48;
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


    // ---- the fish's movement: worked out here once a frame (not again for every pixel), handed to the shader as a
    // small float texture: per fish 4 texels: (middle x, y, z, size), (forward x, y, z, species), (up x, y, z, swim
    // wave), (tail beat, turn bend, jellyfish squeeze, 0) ----

    /** Each species (as in the shader): body radii, tail radii, fin radii, speed, belly fin size. */
    private static final float[][] SPECIES = {
            {0.16F, 0.075F, 0.035F, 0.06F, 0.065F, 0.01F, 0.07F, 0.03F, 0.008F, 1.0F, 0.6F},
            {0.16F, 0.09F, 0.03F, 0.06F, 0.07F, 0.01F, 0.12F, 0.03F, 0.008F, 1.0F, 0.8F},
            {0.13F, 0.11F, 0.03F, 0.05F, 0.06F, 0.01F, 0.11F, 0.08F, 0.008F, 0.9F, 1.8F},
            {0.11F, 0.04F, 0.025F, 0.04F, 0.04F, 0.008F, 0.0F, 0.0F, 0.0F, 1.4F, 0.7F},
            {0.11F, 0.13F, 0.025F, 0.05F, 0.08F, 0.008F, 0.06F, 0.15F, 0.008F, 0.6F, 2.6F},
            {0.13F, 0.09F, 0.065F, 0.09F, 0.09F, 0.012F, 0.06F, 0.04F, 0.008F, 0.7F, 0.6F},
            {0.12F, 0.05F, 0.03F, 0.13F, 0.17F, 0.008F, 0.11F, 0.11F, 0.008F, 0.6F, 3.5F},
            {0.14F, 0.085F, 0.08F, 0.045F, 0.05F, 0.006F, 0.0F, 0.0F, 0.0F, 0.5F, 0.0F},
            {0.08F, 0.03F, 0.02F, 0.085F, 0.075F, 0.006F, 0.04F, 0.035F, 0.005F, 1.3F, 0.4F},
            {0.12F, 0.13F, 0.025F, 0.04F, 0.05F, 0.008F, 0.11F, 0.035F, 0.008F, 0.6F, 0.8F},
            {0.9F, 0.18F, 0.16F, 0.25F, 0.3F, 0.03F, 0.18F, 0.22F, 0.03F, 0.8F, 0.35F},
            {0.45F, 0.13F, 0.11F, 0.12F, 0.18F, 0.02F, 0.08F, 0.1F, 0.02F, 1.3F, 0.5F},
            {0.35F, 0.05F, 0.75F, 0.35F, 0.015F, 0.015F, 0.0F, 0.0F, 0.0F, 0.6F, 0.5F},
            {0.4F, 0.16F, 0.33F, 0.12F, 0.08F, 0.09F, 0.18F, 0.03F, 0.55F, 0.5F, 0.5F},
            {0.55F, 0.25F, 0.2F, 0.15F, 0.2F, 0.03F, 0.3F, 0.08F, 0.02F, 0.5F, 0.7F},
            {0.25F, 0.18F, 0.25F, 0.06F, 0.4F, 0.06F, 0.0F, 0.0F, 0.0F, 0.25F, 0.5F},
            {0.8F, 0.17F, 0.17F, 0.12F, 0.04F, 0.28F, 0.15F, 0.18F, 0.03F, 1.1F, 0.0F},
            {0.12F, 0.035F, 0.025F, 0.04F, 0.035F, 0.006F, 0.0F, 0.0F, 0.0F, 1.5F, 0.5F},
            {0.5F, 0.42F, 0.12F, 0.05F, 0.4F, 0.02F, 0.14F, 0.6F, 0.03F, 0.35F, 1.7F},
            {1.25F, 0.3F, 0.27F, 0.22F, 0.4F, 0.04F, 0.2F, 0.38F, 0.045F, 0.45F, 0.35F},
            // the bottom creatures: how far they reach (forward/back, up, sideways), speed
            {0.15F, 0.13F, 0.11F, 0, 0, 0, 0, 0, 0, 0.22F, 0},
            {0.16F, 0.09F, 0.17F, 0, 0, 0, 0, 0, 0, 0.45F, 0},
            {0.63F, 0.08F, 0.31F, 0, 0, 0, 0, 0, 0, 0.4F, 0},
            {0.17F, 0.13F, 0.17F, 0, 0, 0, 0, 0, 0, 0.22F, 0},
            {0.38F, 0.1F, 0.17F, 0, 0, 0, 0, 0, 0, 0.18F, 0},
            {0.13F, 0.08F, 0.06F, 0, 0, 0, 0, 0, 0, 0.35F, 0},
    };

    private static final float[] FISH_DATA = new float[MAX_FISH * 16];
    private static final java.nio.FloatBuffer FISH_BUF = org.lwjgl.BufferUtils.createFloatBuffer(MAX_FISH * 16);
    private static int fishTex = -1;

    private static double clampD(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double smooth(double a, double b, double x) {
        double k = clampD((x - a) / (b - a), 0.0, 1.0);
        return k * k * (3.0 - 2.0 * k);
    }

    /** Per tank (by its corner): the fish's dodging offsets and how fast they're changing, and when last moved. */
    private static final Map<Long, double[]> DODGE = new HashMap<>();

    /**
     * Where every fish of this tank is this moment, which way it faces, how it swims: wandering loops round its water
     * (bursts and glides, rising and sinking), schools following their leader, leaning and bending into turns, and
     * easing out of each other's way (the smaller one moving more) instead of swimming through each other.
     */
    private static void fishFrame(Floor floor, double t) {
        java.util.Arrays.fill(FISH_DATA, 0.0F);
        float[] r = floor.rnd;
        int n = floor.fishCount;
        double[] px = new double[n], py = new double[n], pz = new double[n], vxs = new double[n], vys = new double[n], vzs = new double[n];
        double[] axs = new double[n], azs = new double[n], sizes = new double[n], rad = new double[n], waves = new double[n], beats = new double[n];
        double[] walks = new double[n];
        double[] squeezes = new double[n], lo = new double[n * 3], hi = new double[n * 3];
        for (int i = 0; i < n; i++) {
            int kind = floor.kinds[i];
            float[] sp = SPECIES[kind];
            double bx = sp[0], by = sp[1], bz = sp[2], tx = sp[3], ty = sp[4], fy = sp[7], fz = sp[8];
            boolean school = kind == 3 || kind == 17;
            int lead = i;
            if (school) {
                for (int j = 0; j < i; j++) {
                    if (floor.kinds[j] == kind) {
                        lead = j;
                        break;
                    }
                }
            }
            double mx = floor.fish[lead * 8], my = floor.fish[lead * 8 + 1], mz = floor.fish[lead * 8 + 2];
            double qx = floor.fish[lead * 8 + 4], qy = floor.fish[lead * 8 + 5], qz = floor.fish[lead * 8 + 6];
            double ox = 0.0, oy = 0.0, oz = 0.0;
            if (lead < i) {
                double k = clampD(Math.min(qx, qz) * 0.3, 0.06, 0.55);
                ox = r[i * 10 + 7] * k;
                oy = r[i * 10 + 8] * 0.45 * k;
                oz = r[i * 10 + 9] * k;
            }
            boolean crawler = kind >= 20;
            double extent = crawler ? Math.max(bx, bz) : Math.max(bx + tx * 1.6, Math.max(bz, fz));
            // size: mostly middling, now and then a small young one or a big old one (schools more even, the big
            // animals varying most); always made to fit the water it has
            double u = r[i * 10] * 2.0 - 1.0;
            double spread = school ? 0.35 : kind == 10 || kind == 12 || kind == 13 || kind == 14 || kind == 18 || kind == 19 ? 1.0 : 0.9;
            double size = (floor.ocean ? 1.0 : 0.9) * (1.0 + spread * 0.5 * u * Math.abs(u));
            if (kind == 18) {
                size *= 1.9;
            }
            if (crawler && floor.ocean) {
                size *= kind == 21 ? 1.8 : kind == 23 ? 1.5 : kind == 25 ? 1.4 : 1.0; // (the ocean's are bigger)
            }
            size = Math.min(size, Math.max(Math.min(qx - Math.abs(ox), qz - Math.abs(oz)) - 0.08, 0.1) / extent);
            double tallH = by + (kind == 15 ? ty * 2.0 : 0.0) + (kind == 18 ? 0.8 : 0.7) * Math.max(fy, by * 0.42 * sp[10]) + (kind == 4 ? by * 0.8 : 0.0);
            if (crawler) {
                tallH = by;
            }
            size = Math.min(size, Math.max(crawler ? qy * 2.0 - 0.2 : qy - Math.abs(oy) - 0.05, 0.1) / Math.max(tallH, 0.05));
            double speed = (0.25 + 0.35 * r[lead * 10 + 1]) * sp[9];
            double rx = Math.max(qx - Math.abs(ox) - (extent * size + 0.06), 0.02);
            double ry = Math.max(qy - Math.abs(oy) - (tallH * size + 0.04), 0.02);
            double rz = Math.max(qz - Math.abs(oz) - (extent * size + 0.06), 0.02);
            double p0 = r[lead * 10 + 2] * Math.PI * 2.0, p1 = r[lead * 10 + 3] * Math.PI * 2.0, p2 = r[lead * 10 + 4] * Math.PI * 2.0;
            double level = my + (r[lead * 10 + 5] * 2.0 - 1.0) * ry * 0.6;
            // the path, and how fast and which way it's going along it (exactly, not from differences)
            double sn = Math.sin(0.7 * t + p2), cs = Math.cos(0.7 * t + p2);
            double halt = crawler ? 0.93 : 0.45; // (how much it slows between bursts: the bottom creatures stop and go)
            double a = speed * (t + halt * sn / 0.7), da = speed * (1.0 + halt * cs), dda = -speed * halt * 0.7 * sn;
            double x = 0.78 * Math.sin(a + p0) + 0.22 * Math.sin(2.3 * a + p1);
            double x1 = 0.78 * Math.cos(a + p0) + 0.506 * Math.cos(2.3 * a + p1);
            double x2 = -0.78 * Math.sin(a + p0) - 1.1638 * Math.sin(2.3 * a + p1);
            double z = 0.78 * Math.sin(0.73 * a + p1) + 0.22 * Math.sin(1.9 * a + p0 + 1.0);
            double z1 = 0.5694 * Math.cos(0.73 * a + p1) + 0.418 * Math.cos(1.9 * a + p0 + 1.0);
            double z2 = -0.415662 * Math.sin(0.73 * a + p1) - 0.7942 * Math.sin(1.9 * a + p0 + 1.0);
            double ky = kind == 15 || crawler ? 0.0 : ry * 0.4;
            double y = level + ky * Math.sin(0.31 * a + p2);
            double y1 = ky * 0.31 * Math.cos(0.31 * a + p2);
            vxs[i] = rx * x1 * da;
            vys[i] = y1 * da;
            vzs[i] = rz * z1 * da;
            axs[i] = rx * (x2 * da * da + x1 * dda);
            azs[i] = rz * (z2 * da * da + z1 * dda);
            double cx = mx + rx * x, cy = clampD(y, my - ry, my + ry), cz = mz + rz * z;
            // jellyfish: squeeze (quickly) and relax (slowly), rising a little with each squeeze
            double jph = t * 0.4 + r[i * 10 + 6];
            jph -= Math.floor(jph);
            double squeeze = smooth(0.0, 0.12, jph) * (1.0 - smooth(0.12, 0.9, jph));
            double fi = i;
            double bob = kind == 15 ? 0.25 * Math.sin(t * 0.6 + fi) + 0.05 * squeeze : 0.03 * Math.sin(t * 0.7 + fi);
            if (school) {
                cx += 0.03 * Math.sin(t * 1.7 + fi * 2.1);
                cy += 0.015 * Math.sin(t * 1.3 + fi);
                cz += 0.03 * Math.cos(t * 1.5 + fi * 1.3);
            }
            double yRoom = Math.max(qy - tallH * size - 0.04, 0.02);
            px[i] = cx + ox;
            py[i] = clampD(cy + oy + bob, my - yRoom, my + yRoom);
            pz[i] = cz + oz;
            if (crawler) { // on the sand (the stingray gliding just over it, now and then rising; the shrimp hopping)
                double sand = my - qy + 0.12;
                py[i] = sand + (kind == 22 ? 0.03 + 0.18 * Math.max(Math.sin(t * 0.23 + fi * 1.7), 0.0) * Math.min(qy * 2.0 - 0.5, 1.0)
                        : kind == 25 ? 0.03 * Math.pow(Math.max(Math.sin(a * 3.0 + fi), 0.0), 6.0) : 0.002);
                yRoom = 0.0;
                my = py[i];
            }
            // where it may go (dodging included): inside its water
            double xRoom = Math.max(qx - extent * size - 0.04, 0.02), zRoom = Math.max(qz - extent * size - 0.04, 0.02);
            lo[i * 3] = mx - xRoom; hi[i * 3] = mx + xRoom;
            lo[i * 3 + 1] = my - yRoom; hi[i * 3 + 1] = my + yRoom;
            lo[i * 3 + 2] = mz - zRoom; hi[i * 3 + 2] = mz + zRoom;
            sizes[i] = size;
            rad[i] = crawler ? size * Math.max(bx, bz) * 0.6 : size * Math.max(0.55 * (bx + tx * 1.6), Math.max(0.6 * tallH, bz));
            double burst = 1.0 + halt * cs;
            walks[i] = a * (kind == 21 ? 9.0 : 6.0) / Math.max(size, 0.3); // (steps taken: as far as it has gone)
            double beatK = (7.0 + 4.0 * speed) / Math.max(size * extent * 4.0, 1.0);
            double wave = beatK * (0.35 * t + 0.65 * (t + 0.45 * sn / 0.7)) + fi * 2.0;
            waves[i] = wave % (Math.PI * 2.0 * 1000.0); // (kept small: whole turns don't change anything)
            beats[i] = 0.55 + 0.45 * burst / 1.45;
            squeezes[i] = squeeze;
        }
        // ---- keeping out of each other's way: each fish eases away from any it's too close to (the smaller one
        // moving more), and drifts back to its own path once clear ----
        long key = floor.min.asLong() * 31L + n * 7L + (floor.ocean ? 1L : 0L);
        double[] st = DODGE.get(key);
        if (st == null || st.length != n * 6 + 1) {
            if (DODGE.size() > 256) {
                DODGE.clear();
            }
            st = new double[n * 6 + 1];
            st[n * 6] = t;
            DODGE.put(key, st);
        }
        double dt = clampD(t - st[n * 6], 0.0, 0.1);
        if (t < st[n * 6] - 1.0) {
            java.util.Arrays.fill(st, 0.0); // (time went backwards: start over)
        }
        st[n * 6] = t;
        double[] dodgeV = new double[n * 3];
        System.arraycopy(st, n * 3, dodgeV, 0, n * 3); // (drawn again this frame: as it was)
        if (dt > 0.0) {
            double[] push = new double[n * 3];
            boolean[] near = new boolean[n];
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double dx = px[i] + st[i * 3] - px[j] - st[j * 3];
                    double dy = py[i] + st[i * 3 + 1] - py[j] - st[j * 3 + 1];
                    double dz = pz[i] + st[i * 3 + 2] - pz[j] - st[j * 3 + 2];
                    double m = (rad[i] + rad[j]) * 1.1;
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 >= m * m) {
                        continue;
                    }
                    double d = Math.sqrt(d2);
                    if (d < 1.0e-5) {
                        dx = 0.0; dy = i < j ? 1.0 : -1.0; dz = 0.0; d = 1.0;
                    } else {
                        dx /= d; dy /= d; dz /= d;
                    }
                    double over = m - Math.min(d, m);
                    double ri = rad[i] * rad[i], rj = rad[j] * rad[j];
                    double wi = rj / (ri + rj), wj = ri / (ri + rj); // (the smaller one moves more)
                    push[i * 3] += dx * over * wi; push[i * 3 + 1] += dy * over * wi * 0.8; push[i * 3 + 2] += dz * over * wi;
                    push[j * 3] -= dx * over * wj; push[j * 3 + 1] -= dy * over * wj * 0.8; push[j * 3 + 2] -= dz * over * wj;
                    if (floor.kinds[i] >= 20) push[i * 3 + 1] = 0.0; // (the bottom creatures stay on the sand)
                    if (floor.kinds[j] >= 20) push[j * 3 + 1] = 0.0;
                    near[i] = true;
                    near[j] = true;
                }
            }
            // the bottom creatures walk round the decorations (rocks, castle, chest...), not through them
            for (int i = 0; i < n; i++) {
                if (floor.kinds[i] < 20) {
                    continue;
                }
                for (int d = 0; d < floor.decorCount; d++) {
                    int pk = Math.round(floor.decor[d * 4 + 3]);
                    double sz = (pk / 128) / 10.0;
                    int type = pk % 8;
                    double dr = sz * (type == 0 || type == 3 ? 0.24 : type == 5 ? 0.17 : 0.11) + 0.02;
                    double dx = px[i] + st[i * 3] - floor.decor[d * 4], dz = pz[i] + st[i * 3 + 2] - floor.decor[d * 4 + 2];
                    double m = rad[i] * 0.8 + dr;
                    double d2 = dx * dx + dz * dz;
                    if (d2 >= m * m) {
                        continue;
                    }
                    double dd = Math.sqrt(d2);
                    if (dd < 1.0e-5) { dx = 1.0; dz = 0.0; dd = 1.0; } else { dx /= dd; dz /= dd; }
                    double over = m - dd;
                    push[i * 3] += dx * over * 1.5;
                    push[i * 3 + 2] += dz * over * 1.5;
                    near[i] = true;
                }
            }
            double ease = 1.0 - Math.exp(-dt * 2.5);
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < 3; k++) {
                    // one smooth pull: out of the way while too close, back to its own path otherwise (no switching
                    // between the two, which made it jitter at the edge)
                    double before = st[i * 3 + k];
                    double now = before + (push[i * 3 + k] * 3.0 - before * 0.5) * dt;
                    double base = k == 0 ? px[i] : k == 1 ? py[i] : pz[i];
                    now = clampD(base + now, lo[i * 3 + k], hi[i * 3 + k]) - base; // (staying in its water)
                    st[i * 3 + k] = now;
                    double sv = st[n * 3 + i * 3 + k]; // (how fast it's dodging, smoothed: for which way it faces)
                    sv += ((now - before) / dt - sv) * ease;
                    st[n * 3 + i * 3 + k] = sv;
                    dodgeV[i * 3 + k] = sv;
                }
            }
        }
        // ---- heading (along its path plus any dodging: it turns away as it dodges), leaning into turns ----
        for (int i = 0; i < n; i++) {
            int kind = floor.kinds[i];
            // (the dodge only turns it partway: never more than about a third of the way round, so it doesn't spin)
            double pv = Math.sqrt(vxs[i] * vxs[i] + vys[i] * vys[i] + vzs[i] * vzs[i]);
            double dv = Math.sqrt(dodgeV[i * 3] * dodgeV[i * 3] + dodgeV[i * 3 + 1] * dodgeV[i * 3 + 1] + dodgeV[i * 3 + 2] * dodgeV[i * 3 + 2]);
            double dk = dv > 0.6 * pv ? 0.6 * pv / dv : 1.0;
            double vx = vxs[i] + dodgeV[i * 3] * dk, vy = vys[i] + dodgeV[i * 3 + 1] * dk, vz = vzs[i] + dodgeV[i * 3 + 2] * dk;
            double hl = Math.sqrt(vx * vx + vz * vz);
            double hx = hl > 1.0e-6 ? vx / hl : 1.0, hz = hl > 1.0e-6 ? vz / hl : 0.0;
            double fyv = clampD(vy / Math.max(hl, 1.0e-4), -0.45, 0.45);
            double fl = Math.sqrt(1.0 + fyv * fyv);
            double fx = hx / fl, fwy = fyv / fl, fwz = hz / fl;
            double sx = -hz, sz = hx; // (sideways, level)
            double curve = (axs[i] * sx + azs[i] * sz) / (hl * hl + 0.002);
            double bank = clampD(curve * 0.25, -0.4, 0.4) * (kind == 12 ? 2.0 : 1.0);
            double ux = -sz * fwy + sx * bank, uy = sz * fx - sx * fwz, uz = sx * fwy + sz * bank; // cross(side, fwd) + side * bank
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul;
            uy /= ul;
            uz /= ul;
            if (kind == 15) {
                fx = 1.0; fwy = 0.0; fwz = 0.0;
                ux = 0.0; uy = 1.0; uz = 0.0;
            }
            if (kind >= 20) { // the bottom creatures stay level; a crab faces across the way it walks (sideways)
                fx = hx; fwy = 0.0; fwz = hz;
                if (kind == 21) {
                    double pa = t * 0.05 + floor.rnd[i * 10 + 4] * 6.0;
                    double sgn = (-hz * Math.cos(pa) + hx * Math.sin(pa)) >= 0.0 ? 1.0 : -1.0;
                    fx = -hz * sgn;
                    fwz = hx * sgn;
                }
                ux = 0.0; uy = 1.0; uz = 0.0;
            }
            double bx = SPECIES[kind][0];
            double bend = kind == 15 || kind >= 20 ? 0.0 : clampD(curve * sizes[i], -0.6 / bx, 0.6 / bx);
            int o = i * 16;
            FISH_DATA[o] = (float) (px[i] + st[i * 3]); FISH_DATA[o + 1] = (float) (py[i] + st[i * 3 + 1]); FISH_DATA[o + 2] = (float) (pz[i] + st[i * 3 + 2]);
            FISH_DATA[o + 3] = (float) sizes[i];
            FISH_DATA[o + 4] = (float) fx; FISH_DATA[o + 5] = (float) fwy; FISH_DATA[o + 6] = (float) fwz; FISH_DATA[o + 7] = kind;
            FISH_DATA[o + 8] = (float) ux; FISH_DATA[o + 9] = (float) uy; FISH_DATA[o + 10] = (float) uz; FISH_DATA[o + 11] = (float) waves[i];
            FISH_DATA[o + 12] = (float) beats[i]; FISH_DATA[o + 13] = (float) bend; FISH_DATA[o + 14] = (float) squeezes[i];
            FISH_DATA[o + 15] = (float) (walks[i] % 1000.0);
        }
    }

    /** This tank's fish, this moment, into the fish texture. */
    private static void uploadFish(Floor floor) {
        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level == null ? 0L : mc.level.getGameTime();
        double t = ((gameTime % 1_728_000L) + mc.getTimer().getGameTimeDeltaPartialTick(false)) / 20.0;
        fishFrame(floor, t);
        if (fishTex < 0) {
            fishTex = com.mojang.blaze3d.platform.TextureUtil.generateTextureId();
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(fishTex);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        } else {
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(fishTex);
        }
        FISH_BUF.clear();
        FISH_BUF.put(FISH_DATA);
        FISH_BUF.flip();
        com.mojang.blaze3d.platform.GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        com.mojang.blaze3d.platform.GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        com.mojang.blaze3d.platform.GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        com.mojang.blaze3d.platform.GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30.GL_RGBA32F, 4, MAX_FISH, 0, GL11.GL_RGBA, GL11.GL_FLOAT, FISH_BUF);
    }

    private static void setShape(Floor floor) {
        float[] o = floor.occupancy;
        for (int v = 0; v < OCC_VECS; v++) {
            shader.safeGetUniform("Occ" + v).set(o[v * 4], o[v * 4 + 1], o[v * 4 + 2], o[v * 4 + 3]);
        }
        shader.safeGetUniform("Full").set(floor.full ? 1.0F : 0.0F);
        uploadFish(floor);
        shader.setSampler("FishTex", fishTex);
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
            if (b.getY() == floor.min.getY()) { // (only on the real bottom, never on top of a corridor built through it)
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
    // ---- which fish a tank gets: a share of water for each by its size (more fish the bigger the tank, never
    // crowded), big animals only in tanks big enough for them, a limit on each kind (higher when fewer kinds are on) ----

    /** How much water (blocks) each kind needs. */
    private static final float[] COST = {0.8F, 1.0F, 1.0F, 0.45F, 1.2F, 1.0F, 0.9F, 1.2F, 0.5F, 1.2F,
            14.0F, 6.0F, 10.0F, 6.0F, 8.0F, 4.0F, 10.0F, 1.2F, 16.0F, 30.0F,
            0.6F, 0.7F, 6.0F, 1.0F, 3.0F, 0.3F};
    /** How often each kind is picked, against the others. */
    private static final float[] WEIGHT = {1, 1, 1, 1.3F, 1, 1, 1, 0.8F, 1.2F, 1,
            0.9F, 1.2F, 0.7F, 1.0F, 0.9F, 0.9F, 0.7F, 2.4F, 0.7F, 0.6F,
            0.8F, 0.8F, 0.7F, 0.7F, 0.6F, 1.0F};
    /** Most of each kind in one tank (with every kind on). */
    private static final int[] CAP = {8, 6, 6, 16, 4, 8, 4, 3, 12, 4,
            3, 6, 3, 4, 3, 5, 3, 16, 2, 2,
            3, 3, 2, 2, 2, 4};

    /** What each aquarium can have: the normal one's fish and bottom creatures, and the ocean one's. */
    static final int[] NORMAL_KINDS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 20, 21, 23, 25};
    static final int[] OCEAN_KINDS = {10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 23, 24, 25};

    /** The smallest tank a kind appears in: how wide (both ways) and how tall, in blocks. */
    static int[] tankNeeds(int kind) {
        return switch (kind) {
            case 19 -> new int[] {6, 3};
            case 18 -> new int[] {4, 3};
            case 10 -> new int[] {5, 2};
            case 12, 16 -> new int[] {4, 2};
            case 14 -> new int[] {3, 2};
            case 11, 13 -> new int[] {2, 1};
            case 15 -> new int[] {1, 2};
            case 22 -> new int[] {3, 1};
            case 24 -> new int[] {2, 1};
            default -> new int[] {1, 1};
        };
    }

    /** The settings changed: pick the tanks' fish again. */
    static void settingsChanged() {
        dirty = true;
    }

    private static List<Integer> chooseFish(Floor floor, int water, java.util.Random random) {
        int[] kinds = floor.ocean ? OCEAN_KINDS : NORMAL_KINDS;
        List<Integer> allowed = new ArrayList<>();
        int enabled = 0;
        for (int k : kinds) {
            if (!AquariumSettings.enabled(k)) {
                continue;
            }
            enabled++;
            int[] need = tankNeeds(k);
            if (Math.min(floor.sizeX, floor.sizeZ) >= need[0] && floor.sizeY >= need[1]) {
                allowed.add(k);
            }
        }
        List<Integer> out = new ArrayList<>();
        if (allowed.isEmpty()) {
            return out;
        }
        float scale = (float) kinds.length / Math.max(enabled, 1); // (fewer kinds on: more of each allowed)
        float[] budget = {water * (floor.ocean ? 0.9F : 2.0F)};
        int maxSlots = floor.ocean ? 30 : 40; // (a school counts as one)
        int[] count = new int[SPECIES.length];
        int[] slots = {0};
        // first one of every kind that's on and fits this tank (in a random order, while there's room), so none go missing
        List<Integer> order = new ArrayList<>(allowed);
        java.util.Collections.shuffle(order, random);
        for (int k : order) {
            if (COST[k] <= budget[0] && out.size() < MAX_FISH && slots[0] < maxSlots) {
                addFish(out, count, budget, slots, k, scale, random);
            }
        }
        // then fill up by the water left
        for (int tries = 0; tries < 400 && out.size() < MAX_FISH && slots[0] < maxSlots; tries++) {
            float total = 0.0F;
            for (int k : allowed) {
                if (count[k] < Math.ceil(CAP[k] * scale) && COST[k] <= budget[0]) total += WEIGHT[k];
            }
            if (total <= 0.0F) {
                break;
            }
            float pick = random.nextFloat() * total;
            for (int k : allowed) {
                if (count[k] < Math.ceil(CAP[k] * scale) && COST[k] <= budget[0]) {
                    pick -= WEIGHT[k];
                    if (pick <= 0.0F) {
                        addFish(out, count, budget, slots, k, scale, random);
                        break;
                    }
                }
            }
        }
        if (out.isEmpty()) { // (a tiny tank: still one, the smallest kind that's on)
            int best = allowed.get(0);
            for (int k : allowed) {
                if (COST[k] < COST[best]) best = k;
            }
            out.add(best);
        }
        return out;
    }

    /** One fish of this kind - or, for neon tetras and sardines, a whole little school (one slot). */
    private static void addFish(List<Integer> out, int[] count, float[] budget, int[] slots, int k, float scale, java.util.Random random) {
        int n = 1;
        if (k == 3 || k == 17) {
            int room = (int) Math.ceil(CAP[k] * scale) - count[k];
            n = Math.max(1, Math.min(Math.min(room, MAX_FISH - out.size()), Math.min(4 + random.nextInt(5), (int) (budget[0] / COST[k]))));
        }
        for (int m = 0; m < n; m++) {
            out.add(k);
        }
        count[k] += n;
        budget[0] -= COST[k] * n;
        slots[0]++;
    }

    private static void placeFish(Floor floor) {
        java.util.List<BlockPos> water = new ArrayList<>(floor.blocks);
        water.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        java.util.Random random = new java.util.Random(floor.min.asLong() * 31L + water.size());
        List<Integer> chosen = chooseFish(floor, water.size(), random);
        floor.fishCount = chosen.size();
        List<BlockPos> floorCells = new ArrayList<>();
        for (BlockPos b : water) {
            if (b.getY() == floor.min.getY()) {
                floorCells.add(b);
            }
        }
        for (int i = 0; i < floor.fishCount; i++) {
            boolean crawler = chosen.get(i) >= 20; // (walks on the sand: only the bottom layer, grown sideways)
            BlockPos start = crawler ? floorCells.get(random.nextInt(floorCells.size())) : water.get(random.nextInt(water.size()));
            int[] lo = {start.getX(), start.getY(), start.getZ()}, hi = {start.getX(), start.getY(), start.getZ()};
            int[] limit = floor.ocean ? new int[] {24, 10, 24} : new int[] {12, 6, 12};
            for (int round = 0; round < (floor.ocean ? 60 : 24); round++) {
                int axis = random.nextInt(3);
                if (crawler && axis == 1) {
                    continue;
                }
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
            floor.kinds[i] = chosen.get(i);
            for (int k = 0; k < 10; k++) {
                floor.rnd[i * 10 + k] = k >= 7 ? random.nextFloat() * 2.0F - 1.0F : random.nextFloat();
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
