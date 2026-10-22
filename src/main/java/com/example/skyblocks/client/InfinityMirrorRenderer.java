package com.example.skyblocks.client;

import com.example.skyblocks.InfinityMirrorBlock;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Draws the infinity mirrors' fronts: each frame (infinity mirrors side by side on one wall, facing the same way, same
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

    /** One frame: its blocks (front faces showing), the rectangle around them on the wall, its colour. */
    private record Frame(Direction facing, int plane, int minU, int minV, int maxU, int maxV, int color, List<BlockPos> blocks) {}

    private static final List<Frame> FRAMES = new ArrayList<>();
    private static boolean dirty = true;
    private static ClientLevel lastLevel;
    private static long lastScan;
    private static int lastChunkX, lastChunkZ;

    private InfinityMirrorRenderer() {}

    /** A block changed on the client: rescan if it's an infinity mirror or next to one. */
    static void blockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState.getBlock() instanceof InfinityMirrorBlock || newState.getBlock() instanceof InfinityMirrorBlock) {
            dirty = true;
            return;
        }
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            if (level.getBlockState(n.setWithOffset(pos, d)).getBlock() instanceof InfinityMirrorBlock) {
                dirty = true;
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || MirrorRenderer.isRenderingReflection()
                || IrisCompat.renderingShadowPass()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        ShaderInstance shader = MirrorShaders.infinity;
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
        if (FRAMES.isEmpty()) {
            return;
        }
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        FrustumIntersection inView = new FrustumIntersection(new Matrix4f(projection).mul(view));
        List<Frame> shown = new ArrayList<>();
        for (Frame f : FRAMES) {
            Vec3 corner = corner(f, f.minU, f.minV);
            Vec3 far = corner(f, f.maxU + 1, f.maxV + 1);
            Direction facing = f.facing;
            double front = (facing.getAxis().choose(cam.x, cam.y, cam.z) - f.plane) * facing.getAxisDirection().getStep();
            if (front <= 0.0 || corner.distanceTo(cam) > RANGE + (f.maxU - f.minU + f.maxV - f.minV)) {
                continue; // seen from behind, or far away
            }
            if (inView.testAab((float) (Math.min(corner.x, far.x) - cam.x), (float) (Math.min(corner.y, far.y) - cam.y),
                    (float) (Math.min(corner.z, far.z) - cam.z), (float) (Math.max(corner.x, far.x) - cam.x),
                    (float) (Math.max(corner.y, far.y) - cam.y), (float) (Math.max(corner.z, far.z) - cam.z))) {
                shown.add(f);
            }
        }
        if (shown.isEmpty()) {
            return;
        }
        // the real eye (the model-view can hold a small shift: view bobbing with a shader pack)
        Vector3f eye = new Matrix4f(view).invert().transformPosition(new Vector3f());
        float seconds = seconds();
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
            // (into the shader pack overlay: the world's depth stays as it was, for the overlay's end-of-frame test)
            drawFrames(shader, shown, cam, eye, seconds, overlay == null);
            if (overlay != null) {
                // where the faces are, for the end-of-frame test against the final depth (hand, mobs in front)
                Vec3 at = cam;
                IrisIslandTarget.recordPictureDepth(() -> {
                    ShaderInstance depthShader = IrisIslandTarget.pictureShader();
                    if (depthShader == null) {
                        return;
                    }
                    for (Frame f : shown) {
                        MeshData mesh = mesh(f, at);
                        if (mesh != null) {
                            RenderSystem.setShader(() -> depthShader);
                            BufferUploader.drawWithShader(mesh);
                        }
                    }
                });
            }
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

    private static float seconds() {
        return (Util.getMillis() % 3_600_000L) / 1000.0F;
    }

    /** Draws the frames' faces with the tunnel shader (positions relative to {@code cam}; the view is already set). */
    private static void drawFrames(ShaderInstance shader, List<Frame> frames, Vec3 cam, Vector3f eye, float seconds, boolean writeDepth) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(writeDepth);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2.0F, -20.0F); // over the block's own front face
        try {
            for (Frame f : frames) {
                setUniforms(shader, f, cam, eye, seconds);
                RenderSystem.setShader(() -> shader);
                MeshData mesh = mesh(f, cam);
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
            }
        } finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.enableCull();
        }
    }

    /**
     * Inside a mirror's picture: the infinity mirrors it sees, drawn from the mirrored eye at (ex, ey, ez) with the
     * mirror's model-view (already set, with its projection). The tunnel is worked out from that eye, so it's seen in
     * the mirror the way it would be from there.
     */
    static void drawInMirror(double ex, double ey, double ez, Matrix4f modelView, Matrix4f projection) {
        ShaderInstance shader = MirrorShaders.infinity;
        if (shader == null || FRAMES.isEmpty()) {
            return;
        }
        Vec3 at = new Vec3(ex, ey, ez);
        FrustumIntersection seen = new FrustumIntersection(new Matrix4f(projection).mul(modelView));
        List<Frame> shown = new ArrayList<>();
        for (Frame f : FRAMES) {
            if (facesTowards(f, at) && inView(f, at, seen)) {
                shown.add(f);
            }
        }
        if (shown.isEmpty()) {
            return;
        }
        Vector3f eye = new Matrix4f(modelView).invert().transformPosition(new Vector3f());
        drawFrames(shader, shown, at, eye, seconds(), true);
    }

    private static boolean facesTowards(Frame f, Vec3 eye) {
        return (f.facing.getAxis().choose(eye.x, eye.y, eye.z) - f.plane) * f.facing.getAxisDirection().getStep() > 0.0;
    }

    private static boolean inView(Frame f, Vec3 cam, FrustumIntersection view) {
        Vec3 corner = corner(f, f.minU, f.minV);
        Vec3 far = corner(f, f.maxU + 1, f.maxV + 1);
        return view.testAab((float) (Math.min(corner.x, far.x) - cam.x), (float) (Math.min(corner.y, far.y) - cam.y),
                (float) (Math.min(corner.z, far.z) - cam.z), (float) (Math.max(corner.x, far.x) - cam.x),
                (float) (Math.max(corner.y, far.y) - cam.y), (float) (Math.max(corner.z, far.z) - cam.z));
    }

    private static void setUniforms(ShaderInstance shader, Frame f, Vec3 cam, Vector3f eye, float seconds) {
        Vec3 origin = corner(f, f.minU, f.minV).subtract(cam);
        Vector3f u = axisU(f.facing), v = axisV(f.facing);
        Vector3f inward = new Vector3f(-f.facing.getStepX(), -f.facing.getStepY(), -f.facing.getStepZ());
        float[] c = COLORS[Math.floorMod(f.color, COLORS.length)];
        shader.safeGetUniform("Eye").set(eye.x, eye.y, eye.z);
        shader.safeGetUniform("Origin").set((float) origin.x, (float) origin.y, (float) origin.z);
        shader.safeGetUniform("AxisU").set(u.x, u.y, u.z);
        shader.safeGetUniform("AxisV").set(v.x, v.y, v.z);
        shader.safeGetUniform("Inward").set(inward.x, inward.y, inward.z);
        shader.safeGetUniform("Size").set((float) (f.maxU - f.minU + 1), (float) (f.maxV - f.minV + 1));
        shader.safeGetUniform("ColorFront").set(c[0], c[1], c[2]);
        shader.safeGetUniform("ColorDeep").set(c[3], c[4], c[5]);
        shader.safeGetUniform("Rainbow").set(f.color == COLORS.length - 1 ? 1.0F : 0.0F);
        shader.safeGetUniform("Time").set(seconds);
    }

    /** The frame's faces as quads (positions relative to the camera). */
    private static MeshData mesh(Frame f, Vec3 cam) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (BlockPos pos : f.blocks) {
            int u = u(pos, f.facing), v = v(pos, f.facing);
            vertex(b, corner(f, u, v), cam);
            vertex(b, corner(f, u + 1, v), cam);
            vertex(b, corner(f, u + 1, v + 1), cam);
            vertex(b, corner(f, u, v + 1), cam);
        }
        return b.build();
    }

    private static void vertex(BufferBuilder b, Vec3 p, Vec3 cam) {
        b.addVertex((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
    }

    // ---- the wall's own coordinates: u across, v up (or along z on floors and ceilings) ----

    private static int u(BlockPos pos, Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? pos.getZ() : pos.getX();
    }

    private static int v(BlockPos pos, Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? pos.getZ() : pos.getY();
    }

    private static Vector3f axisU(Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
    }

    private static Vector3f axisV(Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
    }

    /** The point on the frame's wall at (u, v) in world coordinates. */
    private static Vec3 corner(Frame f, double u, double v) {
        return switch (f.facing.getAxis()) {
            case X -> new Vec3(f.plane, v, u);
            case Y -> new Vec3(u, f.plane, v);
            case Z -> new Vec3(u, v, f.plane);
        };
    }

    // ---- finding the infinity mirrors near the camera ----

    private record Wall(Direction facing, int plane, int color) {}

    private static void scan(ClientLevel level, int centerX, int centerZ) {
        FRAMES.clear();
        Map<Wall, Set<BlockPos>> walls = new HashMap<>();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
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
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof InfinityMirrorBlock)) {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!(state.getBlock() instanceof InfinityMirrorBlock)) {
                                    continue;
                                }
                                BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                                Direction facing = state.getValue(InfinityMirrorBlock.FACING);
                                neighbour.setWithOffset(pos, facing);
                                if (level.getBlockState(neighbour).isSolidRender(level, neighbour)) {
                                    continue; // its front is covered
                                }
                                int plane = pos.get(facing.getAxis()) + (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
                                walls.computeIfAbsent(new Wall(facing, plane, state.getValue(InfinityMirrorBlock.COLOR)), w -> new HashSet<>()).add(pos);
                            }
                        }
                    }
                }
            }
        }
        // joined ones (side by side on the wall) make one frame
        for (Map.Entry<Wall, Set<BlockPos>> e : walls.entrySet()) {
            Wall wall = e.getKey();
            Set<BlockPos> left = new HashSet<>(e.getValue());
            while (!left.isEmpty()) {
                BlockPos start = left.iterator().next();
                left.remove(start);
                List<BlockPos> blocks = new ArrayList<>();
                ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                queue.add(start);
                int minU = Integer.MAX_VALUE, minV = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, maxV = Integer.MIN_VALUE;
                while (!queue.isEmpty()) {
                    BlockPos p = queue.poll();
                    blocks.add(p);
                    int u = u(p, wall.facing), v = v(p, wall.facing);
                    minU = Math.min(minU, u);
                    maxU = Math.max(maxU, u);
                    minV = Math.min(minV, v);
                    maxV = Math.max(maxV, v);
                    for (Direction d : Direction.values()) {
                        if (d.getAxis() == wall.facing.getAxis()) {
                            continue;
                        }
                        BlockPos n = p.relative(d);
                        if (left.remove(n)) {
                            queue.add(n);
                        }
                    }
                }
                FRAMES.add(new Frame(wall.facing, wall.plane, minU, minV, maxU, maxV, wall.color, blocks));
            }
        }
    }
}
