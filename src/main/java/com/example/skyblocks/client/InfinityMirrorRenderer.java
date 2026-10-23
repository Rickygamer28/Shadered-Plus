package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.iris.IrisCompat;
import com.example.skyblocks.client.iris.IrisIslandTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Draws the infinity mirrors' and cubes' fronts (InfinitySet): in the world, in mirrors and in Sky Islands. Infinity
 * mirrors' fronts: each frame (infinity mirrors side by side on one wall, facing the same way, same
 * colour) is one draw of its faces with the infinity_mirror shader, which works out the endless tunnel per pixel. With
 * a shader pack the faces go into Shadered+'s overlay, like the mirrors' faces.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
public final class InfinityMirrorRenderer {
    private static final int SCAN_RADIUS = 4; // chunks
    private static final int RESCAN_TICKS = 20;
    private static final double RANGE = 128.0;
    /** Front and deep colours of each colour setting (the last one is the rainbow). */
    private static final float[][] COLORS = {
            {0.55F, 0.35F, 1.0F, 0.15F, 0.75F, 1.0F},  // blue
            {1.0F, 0.15F, 0.1F, 1.0F, 0.05F, 0.4F},    // red
            {0.2F, 1.0F, 0.3F, 0.1F, 0.9F, 0.75F},     // green
            {0.1F, 0.9F, 1.0F, 0.25F, 0.45F, 1.0F},    // cyan
            {1.0F, 0.35F, 0.8F, 0.7F, 0.3F, 1.0F},     // pink
            {1.0F, 0.95F, 0.9F, 0.75F, 0.85F, 1.0F},   // white
            {1.0F, 0.7F, 0.2F, 1.0F, 0.4F, 0.1F},      // gold
            {1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F},      // rainbow
    };

    /** What's near the camera. */
    private static InfinitySet world = InfinitySet.EMPTY;
    private static boolean dirty = true;
    private static ClientLevel lastLevel;
    private static long lastScan;
    private static int lastChunkX, lastChunkZ;

    /** Sky Islands on pedestals this frame with infinity blocks: their set and pose (island -> camera-relative). */
    private record IslandJob(InfinitySet set, Matrix4f pose) {}

    private static final List<IslandJob> ISLANDS = new ArrayList<>();

    private InfinityMirrorRenderer() {}

    /** Front and deep colour of a colour setting. */
    static float[] color(int color) {
        return COLORS[Math.floorMod(color, COLORS.length)];
    }

    static boolean isRainbow(int color) {
        return Math.floorMod(color, COLORS.length) == COLORS.length - 1;
    }

    /** A block changed on the client: rescan if it's an infinity block or next to one. */
    static void blockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (InfinitySet.ours(oldState) || InfinitySet.ours(newState)) {
            dirty = true;
            return;
        }
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            if (InfinitySet.ours(level.getBlockState(n.setWithOffset(pos, d)))) {
                dirty = true;
                return;
            }
        }
    }

    /** A Sky Island on a pedestal with infinity blocks, drawn this frame with this pose (drawn after the block entities). */
    public static void queueIsland(InfinitySet set, Matrix4f pose) {
        if (!set.isEmpty() && ISLANDS.size() < 64) {
            ISLANDS.add(new IslandJob(set, new Matrix4f(pose)));
        }
    }

    /** What an island holds (built once per island view). */
    public static InfinitySet islandSet(net.minecraft.world.level.BlockGetter island, int sizeX, int height, int sizeZ) {
        Map<BlockPos, BlockState> found = new HashMap<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = island.getBlockState(p);
                    if (InfinitySet.ours(state)) {
                        found.put(p, state);
                    }
                }
            }
        }
        return found.isEmpty() ? InfinitySet.EMPTY : InfinitySet.build(island, found);
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        boolean worldStage = event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        boolean islandStage = event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
        if (!worldStage && !islandStage) {
            return;
        }
        if (MirrorRenderer.isRenderingReflection() || IrisCompat.renderingShadowPass()) {
            if (islandStage && !MirrorRenderer.isRenderingReflection()) {
                ISLANDS.clear();
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ISLANDS.clear();
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        if (islandStage) {
            List<IslandJob> jobs = new ArrayList<>(ISLANDS);
            ISLANDS.clear();
            if (!jobs.isEmpty()) {
                scoped(view, projection, overlay -> {
                    for (IslandJob job : jobs) {
                        // in the island's own coordinates: positions relative to its corner, the eye brought in too
                        Matrix4f modelView = new Matrix4f(view).mul(job.pose);
                        Vector3f eye = eyeOf(projection, modelView);
                        setModelView(modelView);
                        List<Runnable> shapes = new ArrayList<>();
                        drawSet(job.set, Vec3.ZERO, new Vec3(eye.x, eye.y, eye.z), eye, modelView, projection, !overlay, shapes);
                        recordShapes(overlay, shapes);
                    }
                });
            }
            return;
        }
        int chunkX = SectionPos.blockToSectionCoord(cam.x), chunkZ = SectionPos.blockToSectionCoord(cam.z);
        long time = level.getGameTime();
        if (dirty || level != lastLevel || time - lastScan >= RESCAN_TICKS || time < lastScan || chunkX != lastChunkX || chunkZ != lastChunkZ) {
            world = scan(level, chunkX, chunkZ);
            dirty = false;
            lastLevel = level;
            lastScan = time;
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
        }
        if (world.isEmpty()) {
            return;
        }
        // the real eye (the model-view can hold a small shift: view bobbing with a shader pack)
        Vector3f eyeRel = eyeOf(projection, view);
        Vec3 eye = cam.add(eyeRel.x, eyeRel.y, eyeRel.z);
        InfinitySet set = world;
        scoped(view, projection, overlay -> {
            List<Runnable> shapes = new ArrayList<>();
            drawSet(set, cam, eye, eyeRel, view, projection, !overlay, shapes);
            recordShapes(overlay, shapes);
        });
    }

    private interface Body {
        void draw(boolean overlay);
    }

    /**
     * Runs {@code body} set up for drawing into the world: the projection and view set, the main framebuffer (or, with
     * a shader pack, Shadered+'s overlay, laid over the pack's image at the end of the frame), everything put back after.
     */
    private static void scoped(Matrix4f view, Matrix4f projection, Body body) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        boolean iris = IrisIslandTarget.active();
        int irisDraw = iris ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) : 0;
        int irisRead = iris ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : 0;
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.set(view);
        RenderSystem.applyModelViewMatrix();
        IrisIslandTarget.Scope overlay = null;
        try {
            if (iris) {
                overlay = IrisIslandTarget.begin();
                if (overlay == null) {
                    return;
                }
            } else {
                MirrorRenderer.mainDrawState(main);
            }
            body.draw(overlay != null);
        } finally {
            RenderSystem.depthMask(true);
            if (iris) {
                IrisIslandTarget.end(overlay);
                main.bindWrite(false);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, irisRead);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, irisDraw);
                RenderSystem.viewport(0, 0, main.width, main.height);
                BufferUploader.invalidate();
            }
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        }
    }

    private static void setModelView(Matrix4f modelView) {
        RenderSystem.getModelViewStack().set(modelView);
        RenderSystem.applyModelViewMatrix();
    }

    /** With a shader pack: where the faces just drawn are (with the view now set), for the overlay's end-of-frame test. */
    private static void recordShapes(boolean overlay, List<Runnable> shapes) {
        if (!overlay || shapes.isEmpty()) {
            return;
        }
        IrisIslandTarget.recordPictureDepth(() -> {
            ShaderInstance depthShader = IrisIslandTarget.pictureShader();
            if (depthShader == null) {
                return;
            }
            for (Runnable shape : shapes) {
                RenderSystem.setShader(() -> depthShader);
                shape.run();
            }
        });
    }

    /**
     * Where the eye really is (in the coordinates {@code modelView} takes): the centre of the whole projection. While
     * walking, the game puts the view bobbing (a small sway and shift) into the projection; working the tunnels out
     * from the camera position without it, their insides swayed and stretched against their frames with every step.
     */
    static Vector3f eyeOf(Matrix4f projection, Matrix4f modelView) {
        org.joml.Vector4f centre = new Matrix4f(projection).mul(modelView).invert().transform(new org.joml.Vector4f(0, 0, 1, 0));
        if (Math.abs(centre.w) < 1e-9F) {
            return new Matrix4f(modelView).invert().transformPosition(new Vector3f());
        }
        return new Vector3f(centre.x / centre.w, centre.y / centre.w, centre.z / centre.w);
    }

    private static float seconds() {
        return (Util.getMillis() % 3_600_000L) / 1000.0F;
    }

    /**
     * Draws a set (the view and projection already set; positions relative to {@code origin}): {@code eye} where the
     * eye is, in the set's coordinates, {@code eyeRel} the same relative to origin.
     */
    private static void drawSet(InfinitySet set, Vec3 origin, Vec3 eye, Vector3f eyeRel, Matrix4f modelView, Matrix4f projection,
                                boolean writeDepth, List<Runnable> shapes) {
        float seconds = seconds();
        ShaderInstance shader = MirrorShaders.infinity;
        if (shader != null && !set.frames.isEmpty()) {
            FrustumIntersection seen = new FrustumIntersection(new Matrix4f(projection).mul(modelView));
            List<InfinitySet.Frame> shown = new ArrayList<>();
            for (InfinitySet.Frame f : set.frames) {
                Vec3 corner = InfinitySet.corner(f, f.minU(), f.minV());
                if (facesTowards(f, eye) && corner.distanceTo(eye) <= RANGE + (f.maxU() - f.minU() + f.maxV() - f.minV())
                        && inView(f, origin, seen)) {
                    shown.add(f);
                }
            }
            drawFrames(shader, shown, origin, eyeRel, seconds, writeDepth, shapes);
        }
        InfinityCubeRenderer.draw(set.boxes, origin, eye, eyeRel, modelView, projection, seconds, writeDepth, shapes);
    }

    /** Draws the frames' faces with the tunnel shader (positions relative to {@code origin}; the view is already set). */
    private static void drawFrames(ShaderInstance shader, List<InfinitySet.Frame> frames, Vec3 origin, Vector3f eye, float seconds,
                                   boolean writeDepth, List<Runnable> shapes) {
        if (frames.isEmpty()) {
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(writeDepth);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2.0F, -20.0F); // over the block's own front face
        try {
            for (InfinitySet.Frame f : frames) {
                setUniforms(shader, f, origin, eye, seconds);
                RenderSystem.setShader(() -> shader);
                MeshData mesh = mesh(f, origin);
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                shapes.add(() -> {
                    MeshData shape = mesh(f, origin);
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

    /**
     * Inside a mirror's picture: the infinity blocks it sees, drawn from the mirrored eye at (ex, ey, ez) with the
     * mirror's model-view (already set, with its projection), so they're seen in the mirror as they would be from there.
     */
    static void drawInMirror(double ex, double ey, double ez, Matrix4f modelView, Matrix4f projection) {
        if (world.isEmpty()) {
            return;
        }
        Vec3 at = new Vec3(ex, ey, ez);
        Vector3f eyeRel = eyeOf(projection, modelView);
        drawSet(world, at, at.add(eyeRel.x, eyeRel.y, eyeRel.z), eyeRel, modelView, projection, true, new ArrayList<>());
    }

    /**
     * Inside an Island Painting's window (its target bound, its mask set): the island's infinity blocks, positions in the
     * island's coordinates turned into the world by {@code pose} (on top of the view now set).
     */
    public static void drawInWindow(InfinitySet set, Matrix4f pose) {
        if (set.isEmpty()) {
            return;
        }
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Vector3f eye = eyeOf(projection, modelView);
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
            setModelView(modelView);
            drawSet(set, Vec3.ZERO, new Vec3(eye.x, eye.y, eye.z), eye, modelView, projection, true, new ArrayList<>());
        } finally {
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static boolean facesTowards(InfinitySet.Frame f, Vec3 eye) {
        return (f.facing().getAxis().choose(eye.x, eye.y, eye.z) - f.plane()) * f.facing().getAxisDirection().getStep() > 0.0;
    }

    private static boolean inView(InfinitySet.Frame f, Vec3 cam, FrustumIntersection view) {
        Vec3 corner = InfinitySet.corner(f, f.minU(), f.minV());
        Vec3 far = InfinitySet.corner(f, f.maxU() + 1, f.maxV() + 1);
        return view.testAab((float) (Math.min(corner.x, far.x) - cam.x), (float) (Math.min(corner.y, far.y) - cam.y),
                (float) (Math.min(corner.z, far.z) - cam.z), (float) (Math.max(corner.x, far.x) - cam.x),
                (float) (Math.max(corner.y, far.y) - cam.y), (float) (Math.max(corner.z, far.z) - cam.z));
    }

    private static void setUniforms(ShaderInstance shader, InfinitySet.Frame f, Vec3 origin, Vector3f eye, float seconds) {
        Vec3 corner = InfinitySet.corner(f, f.minU(), f.minV()).subtract(origin);
        Vector3f u = InfinitySet.axisU(f.facing()), v = InfinitySet.axisV(f.facing());
        Vector3f inward = new Vector3f(-f.facing().getStepX(), -f.facing().getStepY(), -f.facing().getStepZ());
        float[] c = color(f.color());
        shader.safeGetUniform("Eye").set(eye.x, eye.y, eye.z);
        shader.safeGetUniform("Origin").set((float) corner.x, (float) corner.y, (float) corner.z);
        shader.safeGetUniform("AxisU").set(u.x, u.y, u.z);
        shader.safeGetUniform("AxisV").set(v.x, v.y, v.z);
        shader.safeGetUniform("Inward").set(inward.x, inward.y, inward.z);
        shader.safeGetUniform("Size").set((float) (f.maxU() - f.minU() + 1), (float) (f.maxV() - f.minV() + 1));
        shader.safeGetUniform("ColorFront").set(c[0], c[1], c[2]);
        shader.safeGetUniform("ColorDeep").set(c[3], c[4], c[5]);
        shader.safeGetUniform("Rainbow").set(isRainbow(f.color()) ? 1.0F : 0.0F);
        shader.safeGetUniform("Time").set(seconds);
    }

    /** The frame's faces as quads (positions relative to {@code origin}). */
    private static MeshData mesh(InfinitySet.Frame f, Vec3 origin) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (BlockPos pos : f.blocks()) {
            int u = InfinitySet.u(pos, f.facing()), v = InfinitySet.v(pos, f.facing());
            vertex(b, InfinitySet.corner(f, u, v), origin);
            vertex(b, InfinitySet.corner(f, u + 1, v), origin);
            vertex(b, InfinitySet.corner(f, u + 1, v + 1), origin);
            vertex(b, InfinitySet.corner(f, u, v + 1), origin);
        }
        return b.build();
    }

    private static void vertex(BufferBuilder b, Vec3 p, Vec3 origin) {
        b.addVertex((float) (p.x - origin.x), (float) (p.y - origin.y), (float) (p.z - origin.z));
    }

    // ---- finding the infinity blocks near the camera ----

    private static InfinitySet scan(ClientLevel level, int centerX, int centerZ) {
        Map<BlockPos, BlockState> found = new HashMap<>();
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
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(InfinitySet::ours)) {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (InfinitySet.ours(state)) {
                                    found.put(new BlockPos(baseX + x, baseY + y, baseZ + z), state);
                                }
                            }
                        }
                    }
                }
            }
        }
        return found.isEmpty() ? InfinitySet.EMPTY : InfinitySet.build(level, found);
    }
}
