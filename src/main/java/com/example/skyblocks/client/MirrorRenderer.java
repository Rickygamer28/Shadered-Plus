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
    private static final int MAX_WALLS = 4;
    private static final double WALL_RANGE = 64.0;
    private static final double ENTITY_RANGE = 48.0;
    /** The reflection's resolution, as a part of the screen's. */
    private static final float SCALE = 0.5F;
    /** Just in front of the mirror block's face. */
    private static final float OUT = 0.002F;

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
    private static int lastChunkX, lastChunkZ;
    /** A wall's reflection: this frame's picture, and last frame's (shown by mirrors seen inside this one). */
    private static final class Reflection {
        TextureTarget current, previous;
        long usedFrame = -10;
        boolean hasPrevious;
    }

    private static final Map<Wall, Reflection> REFLECTIONS = new HashMap<>();
    private static long frame;
    private static ByteBufferBuilder entityMemory;
    private static boolean rendering;
    private static boolean warnedSky;
    private static final boolean SODIUM = net.neoforged.fml.ModList.get().isLoaded("sodium");

    private MirrorRenderer() {}

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
        if (level != lastLevel || time - lastScan >= RESCAN_TICKS || time < lastScan || chunkX != lastChunkX || chunkZ != lastChunkZ) {
            scan(level, chunkX, chunkZ);
            lastLevel = level;
            lastScan = time;
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
            if (d > 0.05 && d < WALL_RANGE && (frustum == null || frustum.isVisible(bounds(e.getKey(), e.getValue())))) {
                visible.add(e);
            }
        }
        visible.sort(Comparator.comparingDouble(e -> e.getKey().distance(cam)));
        if (visible.size() > MAX_WALLS) {
            visible = visible.subList(0, MAX_WALLS);
        }
        List<MirrorCulling.View> views = new ArrayList<>();
        List<Map.Entry<Wall, List<BlockPos>>> drawn = new ArrayList<>();
        List<Reflection> drawnReflections = new ArrayList<>();
        RenderTarget main = mc.getMainRenderTarget();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        frame++;
        for (Map.Entry<Wall, List<BlockPos>> entry : visible) {
            Reflection reflection = reflection(entry.getKey(), main);
            try {
                views.add(renderReflection(mc, level, camera, entry.getKey(), view, projection, reflection, partialTick));
                drawn.add(entry);
                drawnReflections.add(reflection);
            } catch (RuntimeException ex) {
                SkyBlocksMod.LOGGER.debug("Mirror reflection failed", ex);
            } finally {
                restore(mc, main, camera, projection, partialTick);
            }
        }
        REFLECTIONS.entrySet().removeIf(e -> {
            if (frame - e.getValue().usedFrame > 200) {
                e.getValue().current.destroyBuffers();
                e.getValue().previous.destroyBuffers();
                return true;
            }
            return false;
        });
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

    /** The wall's reflection pictures, sized for this frame, with last frame's picture kept as "previous". */
    private static Reflection reflection(Wall wall, RenderTarget main) {
        int w = Math.max(1, (int) (main.width * SCALE)), h = Math.max(1, (int) (main.height * SCALE));
        Reflection r = REFLECTIONS.computeIfAbsent(wall, k -> {
            Reflection n = new Reflection();
            n.current = newTarget(w, h);
            n.previous = newTarget(w, h);
            return n;
        });
        TextureTarget swap = r.previous;
        r.previous = r.current;
        r.current = swap;
        r.hasPrevious = r.usedFrame == frame - 1 && r.previous.width == w && r.previous.height == h;
        r.usedFrame = frame;
        if (r.current.width != w || r.current.height != h) {
            r.current.resize(w, h, Minecraft.ON_OSX);
            r.current.setFilterMode(GL11.GL_LINEAR);
        }
        return r;
    }

    private static TextureTarget newTarget(int w, int h) {
        TextureTarget t = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        t.setFilterMode(GL11.GL_LINEAR);
        return t;
    }

    /** The world seen in the wall: drawn from the camera mirrored in its plane, with everything behind the wall cut off. */
    private static MirrorCulling.View renderReflection(Minecraft mc, ClientLevel level, Camera camera, Wall wall, Matrix4f view,
                                                       Matrix4f projection, Reflection reflection, float partialTick) {
        TextureTarget target = reflection.current;
        Vec3 cam = camera.getPosition();
        double nx = wall.nx(), ny = wall.ny(), nz = wall.nz();
        double dist = wall.distance(cam);
        // the mirrored camera, behind the wall
        double rx = cam.x - 2 * dist * nx, ry = cam.y - 2 * dist * ny, rz = cam.z - 2 * dist * nz;
        // reflection in the plane (through the origin; positions are relative to the mirrored camera)
        Matrix4f reflect = new Matrix4f(
                (float) (1 - 2 * nx * nx), (float) (-2 * nx * ny), (float) (-2 * nx * nz), 0,
                (float) (-2 * ny * nx), (float) (1 - 2 * ny * ny), (float) (-2 * ny * nz), 0,
                (float) (-2 * nz * nx), (float) (-2 * nz * ny), (float) (1 - 2 * nz * nz), 0,
                0, 0, 0, 1);
        Matrix4f modelView = new Matrix4f(view).mul(reflect);
        // cut off everything behind the wall (the near plane becomes the wall: oblique projection)
        Matrix4f inverseTranspose = new Matrix4f(modelView).invert().transpose();
        Vector4f normal = inverseTranspose.transform(new Vector4f((float) nx, (float) ny, (float) nz, 0));
        float planeW = (float) (nx * rx + ny * ry + nz * rz - wall.k()) - 0.01F;
        Matrix4f clipped = oblique(new Matrix4f(projection), new Vector4f(normal.x, normal.y, normal.z, planeW));

        rendering = true;
        // the sky's colour first (what the main view is cleared to), then the sky itself on top
        float[] fog = RenderSystem.getShaderFogColor();
        target.setClearColor(fog[0], fog[1], fog[2], 1);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        try {
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            float far = mc.gameRenderer.getRenderDistance();
            // the sky dome is seen from inside: no face culling for it (the reflection turns its sides around)
            RenderSystem.disableCull();
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
        GL11.glFrontFace(GL11.GL_CW); // a reflection turns everything inside out
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN, Math.max(mc.gameRenderer.getRenderDistance(), 32.0F), false, partialTick);
        // blocks: the chunk meshes already on the graphics card
        LevelRendererInvoker chunks = (LevelRendererInvoker) mc.levelRenderer;
        RenderSystem.setProjectionMatrix(clipped, VertexSorting.DISTANCE_TO_ORIGIN);
        chunks.skyblocks$renderSectionLayer(RenderType.solid(), rx, ry, rz, modelView, clipped);
        chunks.skyblocks$renderSectionLayer(RenderType.cutoutMipped(), rx, ry, rz, modelView, clipped);
        chunks.skyblocks$renderSectionLayer(RenderType.cutout(), rx, ry, rz, modelView, clipped);
        // entities in front of the wall, you included
        renderEntities(mc, level, camera, wall, modelView, rx, ry, rz, partialTick);
        // other mirrors seen in this one show this mirror's picture from the last frame (an endless mirror tunnel
        // without drawing the world again for every bounce)
        if (reflection.hasPrevious) {
            Matrix4fStack stack = RenderSystem.getModelViewStack();
            stack.pushMatrix();
            stack.set(modelView);
            RenderSystem.applyModelViewMatrix();
            try {
                for (Map.Entry<Wall, List<BlockPos>> other : WALLS.entrySet()) {
                    if (!other.getKey().equals(wall) && other.getKey().distance(new Vec3(rx, ry, rz)) > 0.05) {
                        drawFaces(other.getKey(), other.getValue(), rx, ry, rz, reflection.previous.getColorTextureId(),
                                target.width, target.height);
                    }
                }
            } finally {
                stack.popMatrix();
                RenderSystem.applyModelViewMatrix();
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
                                       double rx, double ry, double rz, float partialTick) {
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
                try {
                    dispatcher.render(entity, x - rx, y - ry, z - rz, Mth.lerp(partialTick, entity.yRotO, entity.getYRot()),
                            partialTick, pose, buffers, dispatcher.getPackedLightCoords(entity, partialTick));
                } catch (RuntimeException ignored) {
                    // an entity that can't be drawn here is left out of the mirror
                }
            }
            buffers.endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** Back to the main view after a reflection. */
    private static void restore(Minecraft mc, RenderTarget main, Camera camera, Matrix4f projection, float partialTick) {
        rendering = false;
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
            BufferUploader.drawWithShader(mesh);
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
    static Matrix4f oblique(Matrix4f p, Vector4f clip) {
        Vector4f q = new Vector4f(
                (Math.signum(clip.x) + p.m20()) / p.m00(),
                (Math.signum(clip.y) + p.m21()) / p.m11(),
                -1.0F,
                (1.0F + p.m22()) / p.m32());
        float scale = 2.0F / clip.dot(q);
        p.m02(clip.x * scale);
        p.m12(clip.y * scale);
        p.m22(clip.z * scale + 1.0F);
        p.m32(clip.w * scale);
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
