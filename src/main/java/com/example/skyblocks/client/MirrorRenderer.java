package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.mixin.LevelRendererInvoker;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Mirrors (ModBlocks.MIRROR): each flat wall of mirror faces gets one live reflection a frame, shared by all its faces:
 * the world drawn again from the camera mirrored in that wall, into a picture laid out like the screen (lower
 * resolution), which the faces then show at each pixel (mirror_surface shader). Kept cheap like Shadered's skies:
 * <ul>
 *   <li>only walls facing the camera, in view and near (at most {@link #MAX_WALLS} a frame, the nearest);</li>
 *   <li>the chunks drawn are the ones already on the graphics card for the main view (Sodium's own meshes when Sodium is
 *   installed; with Sodium the main view's chunk search also takes the mirrors' views into account, see
 *   MirrorCulling), solid and cutout only;</li>
 *   <li>sky, solid blocks, and entities near the wall (you included), no particles, clouds or shadows.</li>
 * </ul>
 * With a shader pack, mirrors show their plain block texture for now.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class MirrorRenderer {
    private static final int SCAN_RADIUS = 4; // chunks
    private static final int RESCAN_TICKS = 10;
    private static final int MAX_WALLS = 6; // the biggest on screen; each one is the whole world drawn again
    private static final double WALL_RANGE = 64.0;
    private static final double ENTITY_RANGE = 48.0;
    /** The reflection's resolution, as a part of the screen's: full for the nearest wall, half for the others. */
    private static final float NEAR_SCALE = 1.0F, FAR_SCALE = 0.5F;
    /** Just in front of the mirror block's face. */
    private static final float OUT = 0.0F;

    /** A flat wall of mirror faces: which way it faces and where it is on that axis. */
    record Wall(Direction facing, int coord) {
        double nx() { return facing.getStepX(); }
        double ny() { return facing.getStepY(); }
        double nz() { return facing.getStepZ(); }
        /** The plane: n . w = k. */
        double k() { return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? coord : -coord; }
        double distance(Vec3 p) { return nx() * p.x + ny() * p.y + nz() * p.z - k(); }
    }

    private static final Map<Wall, List<BlockPos>> WALLS = new HashMap<>();
    private static ClientLevel lastLevel;
    private static long lastScan = Long.MIN_VALUE;
    private static boolean dirty;
    private static int lastChunkX, lastChunkZ;
    /** A wall's reflection picture. */
    private static final class Reflection {
        TextureTarget current;
        long usedFrame = -10;
    }

    /** Pictures for mirrors seen inside the nearest mirror (a second bounce, drawn for real). */
    /** Spare pictures for mirrors seen in mirrors, by size (each bounce is drawn at half the size of the one it's in). */
    private static final Map<Long, ArrayDeque<TextureTarget>> SPARE = new HashMap<>();
    private static int spareForWidth, spareForHeight;
    /** Bounces stop when the picture would be smaller than this (it'd be a few pixels on screen anyway)... */
    private static final int MIN_BOUNCE_SIZE = 64;
    /** Each mirror seen in a mirror is drawn at this fraction of the size of the one it's in. */
    private static final float BOUNCE_SCALE = 0.8F;
    /** ...or after this many mirror-in-mirror pictures for one mirror, or in one frame (a hall of mirrors multiplies fast). */
    private static final int BOUNCES_PER_MIRROR = 6, BOUNCES_PER_FRAME = 12;
    private static int bouncesLeft, frameBouncesLeft;
    /** Block entities near the camera that mirrors show (gathered once a frame). */
    private static final List<BlockEntity> NEARBY_BLOCK_ENTITIES = new ArrayList<>();
    /** Second-bounce views this frame, so their chunks aren't culled either. */
    private static final List<MirrorCulling.View> NESTED_VIEWS = new ArrayList<>();

    private static final Map<Wall, Reflection> REFLECTIONS = new HashMap<>();
    private static long frame;
    private static ByteBufferBuilder entityMemory;
    private static boolean rendering;
    private static boolean warnedSky, warnedFail;
    private static final boolean SODIUM = net.neoforged.fml.ModList.get().isLoaded("sodium");

    private MirrorRenderer() {}

    /** A block changed on the client: rescan straight away if it's a mirror or next to one. */
    public static void blockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        boolean mirror = isMirror(oldState) || isMirror(newState);
        if (!mirror) {
            BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
            for (Direction d : Direction.values()) {
                if (isMirror(level.getBlockState(n.setWithOffset(pos, d)))) {
                    mirror = true;
                    break;
                }
            }
        }
        if (mirror) {
            dirty = true;
        }
    }

    public static boolean isRenderingReflection() {
        return rendering;
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        // after the solid world, before entities: drawing chunk layers below fires these stage events again
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || rendering) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || com.example.skyblocks.client.iris.IrisCompat.shaderPackInUse()) {
            MirrorCulling.clear();
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        int chunkX = SectionPos.blockToSectionCoord(cam.x), chunkZ = SectionPos.blockToSectionCoord(cam.z);
        long time = level.getGameTime();
        if (dirty || level != lastLevel || time - lastScan >= RESCAN_TICKS || time < lastScan || chunkX != lastChunkX || chunkZ != lastChunkZ) {
            scan(level, chunkX, chunkZ);
            lastLevel = level;
            lastScan = time;
            dirty = false;
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
        }
        if (WALLS.isEmpty()) {
            MirrorCulling.clear();
            return;
        }
        Frustum frustum = event.getFrustum();
        List<Map.Entry<Wall, List<BlockPos>>> visible = new ArrayList<>();
        for (Map.Entry<Wall, List<BlockPos>> e : WALLS.entrySet()) {
            double d = e.getKey().distance(cam);
            if (d > 0.05 && d < WALL_RANGE && (frustum == null || frustum.isVisible(bounds(e.getKey(), e.getValue()).inflate(0.5)))) {
                visible.add(e);
            }
        }
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        // biggest on screen first (not nearest: a mirror's thin edge right next to you would win over the mirror)
        Matrix4f toScreen = new Matrix4f(projection).mul(view);
        Map<Wall, float[]> rects = new HashMap<>();
        for (Map.Entry<Wall, List<BlockPos>> e : visible) {
            rects.put(e.getKey(), screenRect(toScreen, bounds(e.getKey(), e.getValue()).move(-cam.x, -cam.y, -cam.z), FULL));
        }
        visible.removeIf(e -> rects.get(e.getKey()) == null);
        visible.sort(Comparator.comparingDouble(e -> -area(rects.get(e.getKey()))));
        if (visible.size() > MAX_WALLS) {
            visible = visible.subList(0, MAX_WALLS);
        }
        List<MirrorCulling.View> views = new ArrayList<>();
        List<Map.Entry<Wall, List<BlockPos>>> drawn = new ArrayList<>();
        List<Reflection> drawnReflections = new ArrayList<>();
        RenderTarget main = mc.getMainRenderTarget();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        frame++;
        NESTED_VIEWS.clear();
        frameBouncesLeft = BOUNCES_PER_FRAME;
        gatherBlockEntities(level, cam);
        if (main.width != spareForWidth || main.height != spareForHeight) {
            SPARE.values().forEach(q -> q.forEach(TextureTarget::destroyBuffers));
            SPARE.clear();
            spareForWidth = main.width;
            spareForHeight = main.height;
        }
        for (int index = 0; index < visible.size(); index++) {
            Map.Entry<Wall, List<BlockPos>> entry = visible.get(index);
            bouncesLeft = Math.min(BOUNCES_PER_MIRROR, frameBouncesLeft); // every mirror gets its own share
            Reflection reflection = reflection(entry.getKey(), main, NEAR_SCALE);
            try {
                views.add(renderReflection(mc, level, camera, camera.getPosition(), view, true, entry.getKey(), projection,
                        reflection.current, partialTick, 0, rects.get(entry.getKey())));
            } catch (RuntimeException ex) {
                if (!warnedFail) {
                    warnedFail = true;
                    SkyBlocksMod.LOGGER.warn("Shadered+: a mirror reflection failed", ex);
                }
            } finally {
                // drawn either way (a failed frame keeps its last picture instead of flashing the plain block)
                drawn.add(entry);
                drawnReflections.add(reflection);
                restore(mc, main, camera, projection, partialTick);
            }
        }
        REFLECTIONS.entrySet().removeIf(e -> {
            if (frame - e.getValue().usedFrame > 200) {
                e.getValue().current.destroyBuffers();
                return true;
            }
            return false;
        });
        views.addAll(NESTED_VIEWS);
        MirrorCulling.set(views, cam);
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.set(view); // (the camera's turn; at this stage it may not be in RenderSystem's model-view yet)
        RenderSystem.applyModelViewMatrix();
        try {
            for (int i = 0; i < drawn.size(); i++) {
                drawFaces(drawn.get(i).getKey(), drawn.get(i).getValue(), cam.x, cam.y, cam.z,
                        drawnReflections.get(i).current.getColorTextureId(), main.width, main.height);
            }
        } finally {
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** The wall's reflection picture, sized for this frame. */
    private static Reflection reflection(Wall wall, RenderTarget main, float scale) {
        int w = Math.max(1, (int) (main.width * scale)), h = Math.max(1, (int) (main.height * scale));
        Reflection r = REFLECTIONS.computeIfAbsent(wall, k -> {
            Reflection n = new Reflection();
            n.current = newTarget(w, h);
            return n;
        });
        r.usedFrame = frame;
        resize(r.current, w, h);
        return r;
    }

    private static void resize(TextureTarget t, int w, int h) {
        if (t.width != w || t.height != h) {
            t.resize(w, h, Minecraft.ON_OSX);
            t.setFilterMode(GL11.GL_LINEAR);
        }
    }

    private static TextureTarget acquire(int w, int h) {
        ArrayDeque<TextureTarget> spare = SPARE.get(((long) w << 32) | h);
        TextureTarget t = spare == null ? null : spare.poll();
        return t != null ? t : newTarget(w, h);
    }

    private static void release(TextureTarget t) {
        SPARE.computeIfAbsent(((long) t.width << 32) | t.height, k -> new ArrayDeque<>()).push(t);
    }

    private static TextureTarget newTarget(int w, int h) {
        TextureTarget t = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        t.enableStencil(); // Island Paintings mark their window in the stencil buffer
        t.setFilterMode(GL11.GL_LINEAR);
        return t;
    }

    /**
     * The world seen in the wall from {@code eye} looking with {@code eyeView} (the real camera, or for a mirror seen in a
     * mirror the camera already mirrored once): drawn from the eye mirrored in the wall's plane, with everything behind
     * the wall cut off. {@code flipped}: whether this view is mirrored an odd number of times (faces turn inside out).
     * Mirrors seen in it get their own reflection drawn too, at half the size, and so on down.
     */
    private static MirrorCulling.View renderReflection(Minecraft mc, ClientLevel level, Camera camera, Vec3 eye, Matrix4f eyeView,
                                                       boolean flipped, Wall wall, Matrix4f projection, TextureTarget target,
                                                       float partialTick, int depth, float[] rect) {
        Vec3 cam = camera.getPosition();
        double nx = wall.nx(), ny = wall.ny(), nz = wall.nz();
        double dist = wall.distance(eye);
        // the mirrored eye, behind the wall
        double rx = eye.x - 2 * dist * nx, ry = eye.y - 2 * dist * ny, rz = eye.z - 2 * dist * nz;
        // reflection in the plane (through the origin; positions are relative to the mirrored eye)
        Matrix4f reflect = new Matrix4f(
                (float) (1 - 2 * nx * nx), (float) (-2 * nx * ny), (float) (-2 * nx * nz), 0,
                (float) (-2 * ny * nx), (float) (1 - 2 * ny * ny), (float) (-2 * ny * nz), 0,
                (float) (-2 * nz * nx), (float) (-2 * nz * ny), (float) (1 - 2 * nz * nz), 0,
                0, 0, 0, 1);
        Matrix4f modelView = new Matrix4f(eyeView).mul(reflect);
        // cut off everything behind the wall (the near plane becomes the wall: oblique projection)
        Matrix4f inverseTranspose = new Matrix4f(modelView).invert().transpose();
        Vector4f normal = inverseTranspose.transform(new Vector4f((float) nx, (float) ny, (float) nz, 0));
        float planeW = (float) (nx * rx + ny * ry + nz * rz - wall.k()); // exactly the wall: nothing in front of it is cut off
        Matrix4f clipped = oblique(new Matrix4f(projection), new Vector4f(normal.x, normal.y, normal.z, planeW));
        int frontFace = flipped ? GL11.GL_CW : GL11.GL_CCW; // a reflection turns everything inside out

        rendering = true;
        // the sky's colour first (what the main view is cleared to), then the sky itself on top
        float[] fog = RenderSystem.getShaderFogColor();
        target.setClearColor(fog[0], fog[1], fog[2], 1);
        // only the part of the picture where the mirror is on screen is drawn (and cleared)
        scissor(target, rect);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        scissor(target, rect);
        GL11.glFrontFace(GL11.GL_CCW);
        try {
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            float far = mc.gameRenderer.getRenderDistance();
            // the sky dome is seen from inside: no face culling for it (the reflection turns its sides around); the
            // sky is drawn with whatever shader is set, so set the one the main view uses for it
            RenderSystem.disableCull();
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionShader);
            mc.levelRenderer.renderSky(modelView, projection, partialTick, camera, false,
                    () -> FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_SKY, far, false, partialTick));
        } catch (RuntimeException e) {
            if (!warnedSky) {
                warnedSky = true;
                SkyBlocksMod.LOGGER.warn("Shadered+: couldn't draw the sky in a mirror", e);
            }
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        GL11.glFrontFace(frontFace);
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, Math.max(mc.gameRenderer.getRenderDistance(), 32.0F), false, partialTick);
        // blocks: the chunk meshes already on the graphics card
        LevelRendererInvoker chunks = (LevelRendererInvoker) mc.levelRenderer;
        RenderSystem.setProjectionMatrix(clipped, VertexSorting.DISTANCE_TO_ORIGIN);
        chunks.skyblocks$renderSectionLayer(RenderType.solid(), rx, ry, rz, modelView, clipped);
        chunks.skyblocks$renderSectionLayer(RenderType.cutoutMipped(), rx, ry, rz, modelView, clipped);
        chunks.skyblocks$renderSectionLayer(RenderType.cutout(), rx, ry, rz, modelView, clipped);
        // entities in front of the wall, you included
        FrustumIntersection seen = new FrustumIntersection(new Matrix4f(clipped).mul(modelView));
        if (depth <= 1) {
            renderEntities(mc, level, camera, wall, modelView, seen, rx, ry, rz, partialTick, depth == 0);
        }
        // mirrors seen in this one: their own reflection, from the mirrored eye
        // (no fixed depth: each bounce is a bit smaller than the one it's in, until it's too small to matter)
        int innerW = (int) (target.width * BOUNCE_SCALE), innerH = (int) (target.height * BOUNCE_SCALE);
        if (innerW >= MIN_BOUNCE_SIZE && innerH >= MIN_BOUNCE_SIZE && bouncesLeft > 0) {
            Vec3 mirroredEye = new Vec3(rx, ry, rz);
            Matrix4f innerScreen = new Matrix4f(projection).mul(modelView);
            Map<Wall, float[]> innerRects = new HashMap<>();
            List<Map.Entry<Wall, List<BlockPos>>> others = new ArrayList<>();
            for (Map.Entry<Wall, List<BlockPos>> other : WALLS.entrySet()) {
                double d = other.getKey().distance(mirroredEye);
                if (!other.getKey().equals(wall) && d > 0.05 && d < WALL_RANGE && inFrontOf(wall, other.getValue())) {
                    AABB box = bounds(other.getKey(), other.getValue()).move(-rx, -ry, -rz);
                    float[] innerRect = seen.testAab((float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ)
                            ? screenRect(innerScreen, box, rect) : null;
                    if (innerRect != null) {
                        others.add(other); // a mirror that shows up in this one
                        innerRects.put(other.getKey(), innerRect);
                    }
                }
            }
            others.sort(Comparator.comparingDouble(e -> -area(innerRects.get(e.getKey()))));
            for (int i = 0; i < others.size() && bouncesLeft > 0; i++) {
                bouncesLeft--;
                frameBouncesLeft--;
                TextureTarget inner = acquire(innerW, innerH);
                try {
                    MirrorCulling.View innerView = renderReflection(mc, level, camera, mirroredEye, modelView, !flipped,
                            others.get(i).getKey(), projection, inner, partialTick, depth + 1, innerRects.get(others.get(i).getKey()));
                    if (depth < 2) {
                        NESTED_VIEWS.add(innerView); // (deeper ones see much the same chunks)
                    }
                } catch (RuntimeException ex) {
                    release(inner);
                    continue;
                } finally {
                    // back to this reflection
                    rendering = true;
                    target.bindWrite(true);
                    scissor(target, rect);
                    GL11.glFrontFace(frontFace);
                    RenderSystem.setProjectionMatrix(clipped, VertexSorting.DISTANCE_TO_ORIGIN);
                    FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, Math.max(mc.gameRenderer.getRenderDistance(), 32.0F), false, partialTick);
                }
                Matrix4fStack stack = RenderSystem.getModelViewStack();
                stack.pushMatrix();
                stack.set(modelView);
                RenderSystem.applyModelViewMatrix();
                try {
                    drawFaces(others.get(i).getKey(), others.get(i).getValue(), rx, ry, rz, inner.getColorTextureId(),
                            target.width, target.height);
                } finally {
                    stack.popMatrix();
                    RenderSystem.applyModelViewMatrix();
                    release(inner);
                }
                GL11.glFrontFace(frontFace);
            }
        }
        // see-through blocks (glass, water, portals) after the entities, like the main view; only with Sodium: vanilla
        // would re-sort its see-through chunks for the mirror's camera and then again for the real one every frame
        if (SODIUM) {
            RenderSystem.setProjectionMatrix(clipped, VertexSorting.DISTANCE_TO_ORIGIN);
            chunks.skyblocks$renderSectionLayer(RenderType.translucent(), rx, ry, rz, modelView, clipped);
        }

        Matrix4f culling = new Matrix4f(clipped).mul(modelView).translate((float) (cam.x - rx), (float) (cam.y - ry), (float) (cam.z - rz));
        return new MirrorCulling.View(new FrustumIntersection(culling));
    }

    private static void renderEntities(Minecraft mc, ClientLevel level, Camera camera, Wall wall, Matrix4f modelView,
                                       FrustumIntersection seen, double rx, double ry, double rz, float partialTick,
                                       boolean withBlockEntities) {
        if (entityMemory == null) {
            entityMemory = new ByteBufferBuilder(1 << 18);
        }
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.set(modelView);
        RenderSystem.applyModelViewMatrix();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(entityMemory);
        PoseStack pose = new PoseStack();
        Vec3 cam = camera.getPosition();
        try {
            for (Entity entity : level.entitiesForRendering()) {
                double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
                double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
                double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
                double front = wall.distance(new Vec3(x, y, z));
                if (front < -0.5 || front > ENTITY_RANGE || cam.distanceToSqr(x, y, z) > ENTITY_RANGE * ENTITY_RANGE) {
                    continue;
                }
                if (!entity.noCulling) {
                    AABB box = entity.getBoundingBoxForCulling().inflate(0.5).move(-rx, -ry, -rz);
                    if (!seen.testAab((float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ)) {
                        continue; // not in this mirror's view
                    }
                }
                try {
                    dispatcher.render(entity, x - rx, y - ry, z - rz, Mth.lerp(partialTick, entity.yRotO, entity.getYRot()),
                            partialTick, pose, buffers, dispatcher.getPackedLightCoords(entity, partialTick));
                } catch (RuntimeException ignored) {
                    // an entity that can't be drawn here is left out of the mirror
                }
            }
            // block entities in front of the wall, every mod's. Cats are drawn right away with the mirror's view; what
            // other renderers queue for Shadered's passes later in the frame (models, sky cubes) is taken out of the
            // queues and drawn now, inside the mirror, instead of turning up in the real world
            net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher blockEntities = mc.getBlockEntityRenderDispatcher();
            boolean wasDirect = com.example.skyblocks.client.island.IslandRendering.directCats;
            var skiesBefore = ShaderedQueues.sizes();
            List<com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel> models;
            com.example.skyblocks.client.island.IslandRendering.directCats = true;
            try {
                models = !withBlockEntities ? List.of() : IslandCatBatch.capture(() -> {
                    for (BlockEntity be : NEARBY_BLOCK_ENTITIES) {
                        BlockPos pos = be.getBlockPos();
                        if (wall.distance(Vec3.atCenterOf(pos)) < -0.5) {
                            continue;
                        }
                        var renderer = blockEntities.getRenderer(be);
                        if (renderer == null) {
                            continue;
                        }
                        AABB box = renderer.getRenderBoundingBox(be).move(-rx, -ry, -rz);
                        if (!seen.testAab((float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ)) {
                            continue; // not in this mirror's view
                        }
                        pose.pushPose();
                        pose.translate(pos.getX() - rx, pos.getY() - ry, pos.getZ() - rz);
                        try {
                            blockEntities.render(be, partialTick, pose, buffers);
                        } catch (RuntimeException ignored) {
                            // left out of the mirror
                        } finally {
                            pose.popPose();
                        }
                    }
                });
            } finally {
                com.example.skyblocks.client.island.IslandRendering.directCats = wasDirect;
            }
            var skies = ShaderedQueues.takeSince(skiesBefore);
            buffers.endBatch();
            drawQueuedModels(mc, models, cam);
            try {
                ShaderedQueues.draw(skies);
            } catch (RuntimeException ignored) {
                // sky blocks left out of the mirror
            }
        } finally {
            dispatcher.setRenderShadow(true);
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /**
     * Models queued for Shadered's model pass while drawing a mirror (their matrix: world position = camera + the pose
     * the renderer was given, which is relative to the mirrored eye), drawn now with the mirror's view instead.
     */
    private static void drawQueuedModels(Minecraft mc, List<com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel> models, Vec3 cam) {
        if (models.isEmpty()) {
            return;
        }
        Camera main = mc.gameRenderer.getMainCamera();
        // Shadered's shader adds the camera's turn after the model matrix: take it back out
        Matrix4f undoTurn = new Matrix4f().rotationX(main.getXRot() * Mth.DEG_TO_RAD)
                .rotateY((main.getYRot() + 180.0F) * Mth.DEG_TO_RAD).invert();
        Matrix4f toView = undoTurn.mul(new Matrix4f(RenderSystem.getModelViewMatrix()))
                .translate((float) -cam.x, (float) -cam.y, (float) -cam.z);
        var renderer = com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer.getInstance();
        int drawFramebuffer = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        for (var model : models) {
            Matrix4f world = new Matrix4f(model.modelMatrix);
            model.modelMatrix = new Matrix4f(toView).mul(world);
            try {
                renderer.renderDirect(model, null, false);
            } catch (RuntimeException ignored) {
                // left out of the mirror
            } finally {
                model.modelMatrix = world;
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            }
        }
    }

    /**
     * The block entities near the camera, for mirrors (every mod's: what some queue for later is handled when drawing).
     */
    private static void gatherBlockEntities(ClientLevel level, Vec3 cam) {
        NEARBY_BLOCK_ENTITIES.clear();
        int radius = (int) (ENTITY_RANGE / 16) + 1;
        int cx = SectionPos.blockToSectionCoord(cam.x), cz = SectionPos.blockToSectionCoord(cam.z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.isRemoved() || Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(cam) > ENTITY_RANGE * ENTITY_RANGE) {
                        continue;
                    }
                    NEARBY_BLOCK_ENTITIES.add(be);
                }
            }
        }
    }

    /** Back to the main view after a reflection. */
    private static void restore(Minecraft mc, RenderTarget main, Camera camera, Matrix4f projection, float partialTick) {
        rendering = false;
        RenderSystem.disableScissor();
        GL11.glFrontFace(GL11.GL_CCW);
        main.bindWrite(true);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, Math.max(mc.gameRenderer.getRenderDistance(), 32.0F), false, partialTick);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** The wall's faces, showing its reflection (in front of the mirror blocks' own faces). */
    private static final float[] FULL = {0, 0, 1, 1};

    /**
     * Where the box shows up on screen ({@code toScreen}: positions relative to the eye -> clip space), as fractions
     * of the screen (x0, y0, x1, y1), cut down to {@code within}; null if it's not on screen there. Partly behind the
     * eye: all of {@code within}.
     */
    private static float[] screenRect(Matrix4f toScreen, AABB box, float[] within) {
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        Vector4f v = new Vector4f();
        for (int i = 0; i < 8; i++) {
            v.set((float) ((i & 1) == 0 ? box.minX : box.maxX), (float) ((i & 2) == 0 ? box.minY : box.maxY),
                    (float) ((i & 4) == 0 ? box.minZ : box.maxZ), 1.0F);
            toScreen.transform(v);
            if (v.w < 0.05F) {
                return within.clone();
            }
            float sx = (v.x / v.w) * 0.5F + 0.5F, sy = (v.y / v.w) * 0.5F + 0.5F;
            x0 = Math.min(x0, sx); y0 = Math.min(y0, sy); x1 = Math.max(x1, sx); y1 = Math.max(y1, sy);
        }
        float[] r = {Math.max(x0, within[0]), Math.max(y0, within[1]), Math.min(x1, within[2]), Math.min(y1, within[3])};
        return r[2] > r[0] && r[3] > r[1] ? r : null;
    }

    private static float area(float[] r) {
        return (r[2] - r[0]) * (r[3] - r[1]);
    }

    /** Draw only inside {@code rect} (fractions of the picture), with a pixel's margin. */
    private static void scissor(RenderTarget target, float[] rect) {
        int x = Math.max(0, (int) Math.floor(rect[0] * target.width) - 1);
        int y = Math.max(0, (int) Math.floor(rect[1] * target.height) - 1);
        int x1 = Math.min(target.width, (int) Math.ceil(rect[2] * target.width) + 1);
        int y1 = Math.min(target.height, (int) Math.ceil(rect[3] * target.height) + 1);
        RenderSystem.enableScissor(x, y, Math.max(1, x1 - x), Math.max(1, y1 - y));
    }

    /** Whether any of these mirror faces is on the front side of the wall (and so can show up in it). */
    private static boolean inFrontOf(Wall wall, List<BlockPos> faces) {
        for (BlockPos pos : faces) {
            if (wall.distance(Vec3.atCenterOf(pos)) > 0) {
                return true;
            }
        }
        return false;
    }

    private static void drawFaces(Wall wall, List<BlockPos> faces, double cx, double cy, double cz, int texture, int screenWidth, int screenHeight) {
        ShaderInstance shader = MirrorShaders.surface;
        if (shader == null) {
            return;
        }
        shader.safeGetUniform("MirrorScreenSize").set((float) screenWidth, (float) screenHeight);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        Direction d = wall.facing();
        for (BlockPos pos : faces) {
            float x = (float) (pos.getX() - cx), y = (float) (pos.getY() - cy), z = (float) (pos.getZ() - cz);
            switch (d) {
                case UP -> quad(b, x, y + 1 + OUT, z, x + 1, y + 1 + OUT, z, x + 1, y + 1 + OUT, z + 1, x, y + 1 + OUT, z + 1);
                case DOWN -> quad(b, x, y - OUT, z, x + 1, y - OUT, z, x + 1, y - OUT, z + 1, x, y - OUT, z + 1);
                case NORTH -> quad(b, x, y, z - OUT, x + 1, y, z - OUT, x + 1, y + 1, z - OUT, x, y + 1, z - OUT);
                case SOUTH -> quad(b, x, y, z + 1 + OUT, x + 1, y, z + 1 + OUT, x + 1, y + 1, z + 1 + OUT, x, y + 1, z + 1 + OUT);
                case WEST -> quad(b, x - OUT, y, z, x - OUT, y, z + 1, x - OUT, y + 1, z + 1, x - OUT, y + 1, z);
                case EAST -> quad(b, x + 1 + OUT, y, z, x + 1 + OUT, y, z + 1, x + 1 + OUT, y + 1, z + 1, x + 1 + OUT, y + 1, z);
            }
        }
        MeshData mesh = b.build();
        if (mesh != null) {
            // pulled towards the camera so the block's own face underneath never flickers through
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-2.0F, -20.0F);
            try {
                BufferUploader.drawWithShader(mesh);
            } finally {
                RenderSystem.polygonOffset(0.0F, 0.0F);
                RenderSystem.disablePolygonOffset();
            }
        }
        RenderSystem.enableCull();
    }

    private static void quad(BufferBuilder b, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        b.addVertex(x0, y0, z0);
        b.addVertex(x1, y1, z1);
        b.addVertex(x2, y2, z2);
        b.addVertex(x3, y3, z3);
    }

    /** A projection whose near plane is the given (view space) plane: Eric Lengyel's oblique near-plane clipping. */
    /**
     * Replaces the projection's near plane with {@code clip} (Lengyel's oblique near-plane clipping), for any
     * projection: while walking the game folds view bobbing (a small turn and shift) into the projection, so the
     * textbook shortcut that assumes a plain perspective matrix cut the reflection in the wrong place every step.
     */
    static Matrix4f oblique(Matrix4f p, Vector4f clip) {
        Matrix4f inverse = new Matrix4f(p).invert();
        Vector4f clipSpace = new Matrix4f(inverse).transpose().transform(new Vector4f(clip));
        // the far corner of the view on the plane's side
        Vector4f q = inverse.transform(new Vector4f(Math.signum(clipSpace.x), Math.signum(clipSpace.y), 1.0F, 1.0F));
        float wRow = p.m03() * q.x + p.m13() * q.y + p.m23() * q.z + p.m33() * q.w;
        float a = 2.0F * wRow / clip.dot(q);
        float r0 = a * clip.x - p.m03(), r1 = a * clip.y - p.m13(), r2 = a * clip.z - p.m23(), r3 = a * clip.w - p.m33();
        p.m02(r0);
        p.m12(r1);
        p.m22(r2);
        p.m32(r3);
        return p;
    }

    private static AABB bounds(Wall wall, List<BlockPos> faces) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : faces) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    // ---- finding the mirrors near the camera ----

    private static boolean isMirror(BlockState state) {
        return state.is(ModBlocks.MIRROR.get());
    }

    private static void scan(ClientLevel level, int centerX, int centerZ) {
        WALLS.clear();
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
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(MirrorRenderer::isMirror)) {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!isMirror(section.getBlockState(x, y, z))) {
                                    continue;
                                }
                                BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                                for (Direction d : Direction.values()) {
                                    neighbour.setWithOffset(pos, d);
                                    BlockState next = level.getBlockState(neighbour);
                                    if (isMirror(next) || next.isSolidRender(level, neighbour)) {
                                        continue; // a hidden face
                                    }
                                    int coord = pos.get(d.getAxis()) + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
                                    WALLS.computeIfAbsent(new Wall(d, coord), w -> new ArrayList<>()).add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
