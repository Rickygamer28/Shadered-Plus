package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.mixin.LevelRendererInvoker;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Mirrors (ModBlocks.MIRROR): each flat wall of mirror faces gets a live reflection, shared by all its faces: the world
 * drawn again from the camera mirrored in that wall, into a picture laid out like the screen, which the faces then show
 * at each pixel (mirror_surface shader). Everything shows in it: the sky, clouds, rain and snow, every block (see-through
 * ones too), mobs and you (with their shadows), block entities, particles, and the other mirrors - mirrors seen in
 * mirrors get their own pictures, the deeper ones drawn first so each picture shows the newest ones inside it.
 * <p>
 * Kept cheap: only walls facing the camera, in view and near; only the part of each picture where its mirror is on
 * screen is drawn; only the chunk sections, mobs, block entities and particles in that part are drawn (with Sodium its
 * own chunk meshes, the main view's chunk search taking the mirrors' views into account - MirrorCulling; without,
 * MirrorChunks lists each mirror's own sections); mirrors seen in mirrors a little less sharp each bounce, stopping when
 * they're tiny; and a frame draws as many pictures as fit a budget of pixels (all of them, unless there are a great
 * many), the rest keeping their latest picture, which still lines up (Shot).
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MirrorRenderer {
    private static final int SCAN_RADIUS = 4; // chunks
    private static final int RESCAN_TICKS = 10;
    private static final int MAX_WALLS = 256; // (sanity only: pictures take turns being redrawn, see PASSES_PER_FRAME)
    private static final double WALL_RANGE = 64.0;
    private static final double ENTITY_RANGE = 64.0;
    /** Just in front of the mirror block's face. */
    private static final float OUT = 0.0F;

    /** A flat wall of mirror faces: which way it faces and where it is on that axis. */
    record Wall(Direction facing, double coord, long portal) {
        /** A mirror's wall (a portal's: {@code portal} is its lowest pane's place, PortalMirrors.Portal.min). */
        Wall(Direction facing, double coord) {
            this(facing, coord, -1L);
        }

        double nx() { return facing.getStepX(); }
        double ny() { return facing.getStepY(); }
        double nz() { return facing.getStepZ(); }
        /** The plane: n . w = k. */
        double k() { return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? coord : -coord; }
        double distance(Vec3 p) { return nx() * p.x + ny() * p.y + nz() * p.z - k(); }
    }

    private static final Map<Wall, List<BlockPos>> WALLS = new HashMap<>();
    /**
     * A portal mirror's side, linked: what's seen through it is the world past {@code dest} (the linked portal's
     * plane, from its other side), turned {@code turns} quarter turns and shifted from {@code from} to {@code to}
     * (PortalMirrors): its picture is drawn from the eye moved that way, not mirrored.
     */
    private record Link(Wall dest, int turns, Vec3 from, Vec3 to) {}
    private static final Map<Wall, Link> LINKS = new HashMap<>();
    /** Portals linked to one too far away to draw (its world not loaded here): shown with their last picture (Snapshot). */
    private static final Map<Wall, List<BlockPos>> FAR_WALLS = new HashMap<>();
    /** The portal mirrors found round the camera (linked or not). */
    private static final List<com.example.skyblocks.PortalMirrors.Portal> PORTALS = new ArrayList<>();
    /** A portal seen this frame (Sodium: its chunk search without occlusion culling then, mixin/sodium). */
    private static volatile boolean portalInView;

    /** The wall whose front a node's picture shows: a mirror's own, a portal's linked one. */
    private static Wall viewWall(Wall w) {
        Link link = LINKS.get(w);
        return link == null ? w : link.dest();
    }

    /** (The render test: every portal drawn from its last pictures, as if its other end were far away.) */
    public static boolean forceFarPortals;

    /** The server's list of portal mirrors changed: the walls found again. */
    public static void portalsChanged() {
        dirty = true;
    }

    public static boolean portalInView() {
        return portalInView;
    }

    private static Matrix4f turn(int turns) {
        return new Matrix4f().rotationY(-turns * (float) (Math.PI / 2.0));
    }
    private static ClientLevel lastLevel;
    private static long lastScan = Long.MIN_VALUE;
    private static boolean dirty;
    private static int lastChunkX, lastChunkZ;
    /**
     * One reflection picture: the world seen through a chain of mirrors ({@code path}: the wall seen from the camera,
     * then the wall seen in that one's reflection, and so on). Every picture is drawn on its own turn and kept: a
     * mirror shows the latest pictures of the mirrors it sees, and each picture remembers how it was taken (Shot), so
     * an older one still lines up. That way a frame only ever draws a few pictures however many mirrors there are.
     */
    private static final class Node {
        final List<Wall> path;
        final Wall wall;
        final int depth;
        TextureTarget target;
        float scale;
        long usedFrame = -10, renderedFrame = -1000;
        boolean valid;
        float renderedArea;
        float[] renderedRect;
        /** The part of the screen its picture covers (fractions): only round its mirror, not the whole screen. */
        float[] picRect;
        /**
         * The part of the picture actually drawn (fractions of it, from its lower left corner): the picture is kept a
         * little big so it isn't made again at every step (and rounded up), but only its mirror's part is drawn.
         */
        float usedW = 1.0F, usedH = 1.0F;
        final Shot shot = new Shot();
        MirrorCulling.View view;
        // this frame
        float[] rect;
        float area;
        Chain chain;
        final List<Node> children = new ArrayList<>();
        /** Mirrors seen in this one past the limits (too many, too small): shown with the latest picture there is. */
        final List<Wall> overflow = new ArrayList<>();

        Node(List<Wall> path) {
            this.path = path;
            this.wall = path.get(path.size() - 1);
            this.depth = path.size();
        }
    }

    /** How a node's picture is seen: from the eye mirrored in its wall, with everything behind the wall cut off. */
    private static final class Chain {
        double rx, ry, rz;
        Matrix4f modelView, clipped;
        FrustumIntersection seen;
        boolean flipped;
    }

    /**
     * How a reflection picture was taken: positions relative to the mirrored eye -> the picture's clip space. The
     * mirror's face looks the picture up there (rather than at its own screen position), so a picture taken a few
     * frames ago still sits right on the mirror while the camera moves.
     */
    private static final class Shot {
        final Matrix4f projModel = new Matrix4f();
        double rx, ry, rz;
        /** How far into the picture it was drawn (fractions; the rest: left over from before): looked up only within. */
        float maxU = 1.0F, maxV = 1.0F;
    }

    /**
     * How much drawing a frame may do: pictures (their part on screen, at their sharpness) adding up to this many
     * screens' worth of pixels, and at most this many pictures. Within that every picture is drawn every frame; past it
     * they take turns (biggest and oldest first), keeping their latest picture meanwhile.
     */
    private static final float PIXEL_BUDGET = 0.8F;
    private static final int MAX_DRAWS = 16;
    /** What drawing any picture costs whatever its size (the sky, the lists of blocks and things in it...), in screens. */
    private static final float PICTURE_COST = 0.05F;
    /** Mirrors seen in mirrors at least this big on screen are drawn every frame too (smaller ones take turns: tiny, a few frames old doesn't show). */
    private static final float EVERY_FRAME_AREA = 0.03F;
    /** At most this many pictures in view (mirrors, and mirrors seen in mirrors), and this many mirrors seen per mirror. */
    private static final int MAX_NODES = 32, MAX_CHILDREN = 4;
    /** Mirrors seen in mirrors: each bounce's picture is this much less sharp than the one it's in... */
    private static final float BOUNCE_SCALE = 0.9F;
    /** ...and bounces stop when the picture would be smaller than this (pixels), or the mirror too small on screen. */
    private static final int MIN_BOUNCE_SIZE = 64;
    private static final float MIN_BOUNCE_AREA = 0.0015F;
    /** Block entities near the camera that mirrors show (gathered once a frame). */
    private static final List<BlockEntity> NEARBY_BLOCK_ENTITIES = new ArrayList<>();
    /** Mobs (and you) near the camera, where they are this frame: gathered once, not again for every picture. */
    private record Near(Entity entity, double x, double y, double z, float yRot) {}
    private static final List<Near> NEARBY_ENTITIES = new ArrayList<>();

    private static final Map<List<Wall>, Node> NODES = new HashMap<>();
    private static final List<MirrorCulling.View> LAST_VIEWS = new ArrayList<>();
    /** Mirrors whose face was drawn last frame (to notice one dropping out for a frame: the silver blink). */
    private static final Set<Wall> LAST_DRAWN = new HashSet<>();
    private static long skipLogWindow;
    private static int skipLogs;

    private static void skipped(String why, Wall wall) {
        long now = System.currentTimeMillis();
        if (now - skipLogWindow > 60_000) {
            skipLogWindow = now;
            skipLogs = 0;
        }
        if (skipLogs++ < 20) {
            SkyBlocksMod.LOGGER.info("Shadered+ mirror skipped a frame ({}): {} {} (frame {})", why, wall.facing(), wall.coord(), frame);
        }
    }
    private static long frame;
    private static MirrorBuffers entityBuffers;
    private static boolean rendering;
    private static boolean warnedSky, warnedFail;
    private static final boolean SODIUM = net.minecraftforge.fml.ModList.get().isLoaded("embeddium")
            || net.minecraftforge.fml.ModList.get().isLoaded("rubidium");

    private MirrorRenderer() {}

    /** A block changed on the client: rescan straight away if it's a mirror or next to one. */
    public static void blockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        InfinityMirrorRenderer.blockChanged(level, pos, oldState, newState);
        DanceFloorRenderer.blockChanged(oldState, newState);
        AquariumRenderer.blockChanged(oldState, newState);
        KaleidoscopeRenderer.blockChanged(oldState, newState);
        KoiPondRenderer.blockChanged(oldState, newState);
        boolean mirror = isMirror(oldState) || isMirror(newState)
                || com.example.skyblocks.PortalMirrors.isPortal(oldState) || com.example.skyblocks.PortalMirrors.isPortal(newState);
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

    /** The view of the mirror being drawn (positions relative to its eye), for skipping chunk sections it can't see. */
    private static FrustumIntersection chunkCull;
    private static double cullX, cullY, cullZ;

    /** Whether the chunk section (section coordinates) is in the view of the mirror being drawn. */
    public static boolean sectionInMirror(int sx, int sy, int sz) {
        FrustumIntersection cull = chunkCull;
        if (cull == null) {
            return true;
        }
        float x = (float) ((sx << 4) - cullX), y = (float) ((sy << 4) - cullY), z = (float) ((sz << 4) - cullZ);
        return cull.testAab(x - 1, y - 1, z - 1, x + 17, y + 17, z + 17);
    }

    private static void cullChunks(FrustumIntersection cull, double x, double y, double z) {
        chunkCull = cull;
        cullX = x;
        cullY = y;
        cullZ = z;
    }

    private static boolean stageRan;
    private static int stageRuns;

    /** A frame where mirrors were drawn last time but the stage event never came: logged (that's a silver frame). */
    @SubscribeEvent
    public static void onFrameEnd(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!stageRan && !LAST_DRAWN.isEmpty() && mc.level != null && !mc.isPaused() && mc.screen == null) {
            skipped("the world's cutout stage didn't run this frame", LAST_DRAWN.iterator().next());
        }
        stageRan = false;
        if (stageRuns > 1 && !LAST_DRAWN.isEmpty()) {
            skipped("the world's cutout stage ran " + stageRuns + " times this frame", LAST_DRAWN.iterator().next());
        }
        stageRuns = 0;
    }

    /** Set by "/shaderedplus mirrorpicture": the biggest mirror's picture is saved into the screenshots folder. */
    private static boolean dumpRequested;

    @SubscribeEvent
    public static void onClientCommands(net.minecraftforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("shaderedplus")
                .then(net.minecraft.commands.Commands.literal("mirrorpicture").executes(ctx -> {
                    dumpRequested = true;
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                            "Saving the mirror's pictures to the screenshots folder (shaderedplus_mirror_*.png)"), false);
                    return 1;
                })));
    }

    private static void dump(RenderTarget target, String unused, java.io.File file) {
        try {
            com.mojang.blaze3d.platform.NativeImage image = net.minecraft.client.Screenshot.takeScreenshot(target);
            file.getParentFile().mkdirs();
            net.minecraft.Util.ioPool().execute(() -> {
                try (image) {
                    image.writeToFile(file);
                } catch (java.io.IOException e) {
                    SkyBlocksMod.LOGGER.warn("Shadered+: couldn't save {}", file, e);
                }
            });
        } catch (RuntimeException e) {
            SkyBlocksMod.LOGGER.warn("Shadered+: couldn't read a portal's picture", e);
        }
    }

    private static void dump(RenderTarget target, String name) {
        try {
            com.mojang.blaze3d.platform.NativeImage image = net.minecraft.client.Screenshot.takeScreenshot(target);
            java.io.File dir = new java.io.File(Minecraft.getInstance().gameDirectory, "screenshots");
            dir.mkdirs();
            java.io.File file = new java.io.File(dir, name);
            net.minecraft.Util.ioPool().execute(() -> {
                try (image) {
                    image.writeToFile(file);
                    SkyBlocksMod.LOGGER.info("Shadered+: saved {}", file);
                } catch (java.io.IOException e) {
                    SkyBlocksMod.LOGGER.warn("Shadered+: couldn't save {}", file, e);
                }
            });
        } catch (RuntimeException e) {
            SkyBlocksMod.LOGGER.warn("Shadered+: couldn't read the mirror's picture", e);
        }
    }

    /** With a shader pack: how far behind the mirror the reflection is cut off (see chain()). */
    private static final float PACK_CLIP_MARGIN = 0.0F; // (none: a margin leaves a band in the corners)
    private static boolean packMargin;

    /** The faces are being drawn into the shader pack overlay (its depth is the world's own). */
    private static boolean overlayFaces;

    private static TextureTarget currentTarget;
    private static float[] currentRect;

    /**
     * With a shader pack, Iris binds its own framebuffers when Sodium starts drawing chunks: while a mirror draws, its
     * picture is bound again right after (mixin/sodium/SodiumMirrorIrisMixin).
     */
    public static void rebindPicture() {
        if (rendering && currentTarget != null) {
            currentTarget.bindWrite(true);
            if (currentRect != null) {
                scissor(currentTarget, currentRect);
            }
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
        int timing = WorldTiming.start(WorldTiming.MIRRORS);
        try {
            drawMirrors(event);
        } finally {
            WorldTiming.stop(timing);
        }
    }

    private static void drawMirrors(RenderLevelStageEvent event) {
        if (com.example.skyblocks.client.iris.IrisCompat.renderingShadowPass()) {
            return; // (the shader pack's shadow pass draws the world from the sun: no mirrors there)
        }
        stageRan = true;
        stageRuns++;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
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
        if (WALLS.isEmpty() && FAR_WALLS.isEmpty()) {
            portalInView = false;
            if (!LAST_DRAWN.isEmpty()) {
                skipped("no mirrors found (scan)", LAST_DRAWN.iterator().next());
                LAST_DRAWN.clear();
            }
            MirrorCulling.clear();
            return;
        }
        Matrix4f view = new Matrix4f(event.getPoseStack().last().pose()); // (1.20.1: the camera's turn is in the pose)
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        // in view: tested against this frame's own view (the game's culling frustum sometimes said a mirror right in
        // front of you wasn't visible for a frame: the silver blink)
        FrustumIntersection inView = new FrustumIntersection(new Matrix4f(projection).mul(view));
        List<Map.Entry<Wall, List<BlockPos>>> visible = new ArrayList<>();
        for (Map.Entry<Wall, List<BlockPos>> e : WALLS.entrySet()) {
            double d = e.getKey().distance(cam);
            if (d <= 0.05 || d >= WALL_RANGE) {
                continue;
            }
            AABB box = bounds(e.getKey(), e.getValue()).inflate(0.5).move(-cam.x, -cam.y, -cam.z);
            if (inView.testAab((float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ)) {
                visible.add(e);
            }
        }
        Matrix4f farless = infiniteFar(projection);
        // biggest on screen first (not nearest: a mirror's thin edge right next to you would win over the mirror)
        Matrix4f toScreen = new Matrix4f(projection).mul(view);
        Map<Wall, float[]> rects = new HashMap<>();
        for (Map.Entry<Wall, List<BlockPos>> e : visible) {
            rects.put(e.getKey(), screenRect(toScreen, bounds(e.getKey(), e.getValue()).move(-cam.x, -cam.y, -cam.z), FULL));
        }
        visible.removeIf(e -> rects.get(e.getKey()) == null);
        // (why a mirror shown last frame isn't this frame, if it's still in front: written to the log)
        for (Wall w : LAST_DRAWN) {
            if (rects.get(w) != null) {
                continue;
            }
            List<BlockPos> faces = WALLS.get(w);
            if (faces == null) {
                skipped("gone after a rescan", w);
                continue;
            }
            double d = w.distance(cam);
            float[] r = screenRect(toScreen, bounds(w, faces).move(-cam.x, -cam.y, -cam.z), FULL);
            if (r == null || d <= 0.05) {
                continue; // really off screen / behind it now
            }
            if (d >= WALL_RANGE) {
                continue;
            }
            if (!rects.containsKey(w)) {
                // (only when the box is wholly in front of the eye: then the screen rect is exact, and the in-view
                // test disagreeing with it means something is off with this frame's view)
                AABB box = bounds(w, faces).move(-cam.x, -cam.y, -cam.z);
                if (wholeInFront(toScreen, box)) {
                    skipped(String.format(java.util.Locale.ROOT, "in-view test said no; cam %.2f %.2f %.2f, view %s, proj %s",
                            cam.x, cam.y, cam.z, view, projection).replace('\n', ' '), w);
                }
                continue;
            }
            skipped("no screen rect", w);
        }
        // mirrors in view but hidden behind blocks (a wall, a hill between): no picture for them
        boolean irisNow = com.example.skyblocks.client.iris.IrisIslandTarget.active();
        if (!irisNow) {
            occlusion(mc.getMainRenderTarget(), visible, view, projection, cam);
            visible.removeIf(e -> {
                int[] o = OCCLUSION.get(e.getKey());
                return o != null && o[1] >= HIDDEN_FRAMES;
            });
        }
        visible.sort(Comparator.comparingDouble(e -> -area(rects.get(e.getKey()))));
        if (visible.size() > MAX_WALLS) {
            visible = visible.subList(0, MAX_WALLS);
        }
        RenderTarget main = mc.getMainRenderTarget();
        float partialTick = event.getPartialTick();
        // with a shader pack: what Iris has bound (its own framebuffers) is put back when the mirrors are done
        boolean iris = com.example.skyblocks.client.iris.IrisIslandTarget.active();
        Object irisPhase = iris ? com.example.skyblocks.client.iris.IrisCompat.phase() : null;
        packMargin = iris;
        int irisDraw = iris ? GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING) : 0;
        int irisRead = iris ? GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING) : 0;
        frame++;
        MirrorTiming.beginFrame();
        // (round you, and round where the portals in view look out: what's there shows in them)
        List<Vec3> around = new ArrayList<>();
        around.add(cam);
        for (Map.Entry<Wall, List<BlockPos>> e : visible) {
            Link link = LINKS.get(e.getKey());
            if (link != null && link.to().distanceToSqr(cam) > 16.0 * 16.0 && around.size() < 8) {
                around.add(link.to());
            }
        }
        gatherBlockEntities(level, around);
        NEARBY_ENTITIES.clear();
        for (Entity entity : level.entitiesForRendering()) {
            double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
            double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
            double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
            if (near(around, x, y, z, ENTITY_RANGE)) {
                NEARBY_ENTITIES.add(new Near(entity, x, y, z, Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
            }
        }

        // every picture in view this frame: the mirrors on screen, then (biggest first) the mirrors seen in them...
        List<Node> all = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        for (Map.Entry<Wall, List<BlockPos>> e : visible) {
            Node node = node(List.of(e.getKey()));
            node.rect = rects.get(e.getKey());
            node.area = area(node.rect);
            node.chain = chain(e.getKey(), cam.x, cam.y, cam.z, view, false, farless);
            all.add(node);
            queue.add(node);
        }
        while (!queue.isEmpty()) {
            Node parent = queue.poll();
            parent.children.clear();
            parent.overflow.clear();
            Chain c = parent.chain;
            Vec3 eye = new Vec3(c.rx, c.ry, c.rz);
            Matrix4f innerScreen = new Matrix4f(projection).mul(c.modelView);
            List<Node> found = new ArrayList<>();
            for (Map.Entry<Wall, List<BlockPos>> other : WALLS.entrySet()) {
                Wall w = other.getKey();
                double d = w.distance(eye);
                Wall seenPast = viewWall(parent.wall);
                if (w.equals(seenPast) || d <= 0.05 || d >= WALL_RANGE || !inFrontOf(seenPast, other.getValue())) {
                    continue;
                }
                AABB box = bounds(w, other.getValue()).move(-c.rx, -c.ry, -c.rz);
                if (!c.seen.testAab((float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ)) {
                    continue;
                }
                float[] rect = screenRect(innerScreen, box, parent.rect);
                if (rect == null) {
                    continue;
                }
                if (area(rect) < MIN_BOUNCE_AREA) {
                    parent.overflow.add(w);
                    continue;
                }
                List<Wall> path = new ArrayList<>(parent.path);
                path.add(w);
                Node child = node(path);
                child.rect = rect;
                child.area = area(rect);
                found.add(child);
            }
            found.sort(Comparator.comparingDouble(n -> -n.area));
            for (Node child : found) {
                if (parent.children.size() >= MAX_CHILDREN || all.size() >= MAX_NODES
                        || main.width * scaleFor(child) < MIN_BOUNCE_SIZE) {
                    parent.overflow.add(child.wall);
                    continue;
                }
                child.chain = chain(child.wall, c.rx, c.ry, c.rz, c.modelView, c.flipped, farless);
                parent.children.add(child);
                all.add(child);
                queue.add(child);
            }
        }

        boolean portals = false;
        for (Node node : all) {
            portals |= LINKS.containsKey(node.wall);
        }
        if (portals != portalInView) {
            portalInView = portals;
            MirrorCulling.scheduleTerrainUpdate(); // (Sodium: its chunk search again, with or without occlusion culling)
        }
        // ...which of them get a new picture this frame: the biggest mirror on screen always, then any without a
        // picture yet or that now cover much more of the screen, then the oldest (weighted by size)
        for (Node node : all) {
            prepare(node, main, false);
        }
        List<Node> order = new ArrayList<>(all);
        Node biggest = all.isEmpty() ? null : all.get(0);
        order.sort(Comparator.comparingDouble(n -> -priority(n, n == biggest)));
        // as many as fit the budget, most needed first: the biggest mirror, any without a picture yet or grown a lot,
        // then the rest (the mirrors themselves before mirrors seen in them, at the same size)
        List<Node> chosen = new ArrayList<>();
        float spent = 0.0F;
        for (Node node : order) {
            float sc = scaleFor(node);
            float cost = Math.max(area(grow(node.rect, RECT_MARGIN)), 0.002F) * sc * sc + PICTURE_COST;
            boolean must = node == biggest || !node.valid || node.depth == 1 || node.area >= EVERY_FRAME_AREA;
            if (chosen.size() < MAX_DRAWS && (spent + cost <= PIXEL_BUDGET || must)) {
                chosen.add(node);
                spent += cost;
            }
        }
        chosen.sort(Comparator.comparingInt(n -> -n.depth)); // (deeper first: a picture shows the newest ones inside it)
        MirrorTiming.counts(chosen.size(), all.size());
        for (Node node : chosen) {
            try {
                prepare(node, main, true); // (a new size only right before drawing: no frame without a picture)
                renderNode(mc, level, camera, node, projection, partialTick, true);
                node.valid = true;
                node.renderedFrame = frame;
                node.renderedArea = node.area;
                node.renderedRect = grow(node.rect, RECT_MARGIN);
            } catch (RuntimeException ex) {
                skipped("drawing failed: " + ex, node.wall);
                if (!warnedFail) {
                    warnedFail = true;
                    SkyBlocksMod.LOGGER.warn("Shadered+: a mirror reflection failed", ex);
                }
            } finally {
                MirrorTiming.end();
                restore(mc, main, camera, projection, partialTick);
            }
        }
        boolean dumpOverlay = false;
        if (dumpRequested) {
            dumpRequested = false;
            dumpOverlay = iris;
            SkyBlocksMod.LOGGER.info("Shadered+ mirror debug: shader pack {}, {} mirrors in view, {} pictures, {} drawn this frame, biggest valid {}",
                    iris, visible.size(), all.size(), chosen.size(), biggest != null && biggest.valid);
            if (biggest != null && biggest.valid) {
                dump(biggest.target, "shaderedplus_mirror_picture.png");
            }
        }
        // now and then, a picture through a portal from just in front of it, kept (and saved): what it shows once
        // the other end is too far away to draw
        try {
            snapshots(mc, level, camera, cam, main, projection, partialTick);
        } catch (RuntimeException ex) {
            skipped("portal picture failed: " + ex, LAST_DRAWN.isEmpty() ? new Wall(Direction.UP, 0) : LAST_DRAWN.iterator().next());
        }
        List<MirrorCulling.View> views = new ArrayList<>(SNAPSHOT_VIEWS);
        for (Node node : all) {
            if (node.valid && node.view != null && node.depth <= 2) {
                views.add(node.view);
            }
        }
        NODES.entrySet().removeIf(e -> {
            if (frame - e.getValue().usedFrame > 200) {
                if (e.getValue().target != null) { // (a mirror found but never needed has no picture)
                    e.getValue().target.destroyBuffers();
                }
                return true;
            }
            return false;
        });
        // (this frame's and the last frame's views: chunks a mirror just stopped seeing stay one more frame)
        List<MirrorCulling.View> kept = new ArrayList<>(views);
        kept.addAll(LAST_VIEWS);
        LAST_VIEWS.clear();
        LAST_VIEWS.addAll(views);
        MirrorCulling.set(kept, cam);
        PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        stack.mulPoseMatrix(view); // (the camera's turn; at this stage it isn't in RenderSystem's model-view)
        RenderSystem.applyModelViewMatrix();
        MirrorTiming.start(MirrorTiming.FACES);
        Set<Wall> drawnNow = new HashSet<>();
        // with a shader pack, the pack's final pass would paint over anything drawn into the world now: the faces go
        // into Shadered+'s overlay target instead (like Island Paintings), laid over the pack's image at the end of the
        // frame where nothing ended up in front of them
        com.example.skyblocks.client.iris.IrisIslandTarget.Scope overlay = null;
        if (iris) {
            overlay = com.example.skyblocks.client.iris.IrisIslandTarget.begin();
            overlayFaces = overlay != null;
        } else {
            mainDrawState(main);
        }
        try {
            for (Node node : all) {
                if (node.depth == 1) {
                    if (node.valid) {
                        drawFaces(node.wall, WALLS.get(node.wall), cam.x, cam.y, cam.z, node.target.getColorTextureId(), node.shot);
                        drawnNow.add(node.wall);
                    } else if (LAST_DRAWN.contains(node.wall)) {
                        skipped("no picture", node.wall);
                    }
                }
            }
            // every other mirror facing you that has a picture gets its face too, whatever the in-view test said (the
            // faces are cheap, and off-screen ones cost nothing): a wrong "not in view" can't flash the plain block
            for (Map.Entry<Wall, List<BlockPos>> e : WALLS.entrySet()) {
                Wall w = e.getKey();
                double d = w.distance(cam);
                if (drawnNow.contains(w) || d <= 0.05 || d >= WALL_RANGE) {
                    continue;
                }
                Node node = NODES.get(List.of(w));
                if (node != null && node.valid && node.target != null) {
                    drawFaces(w, e.getValue(), cam.x, cam.y, cam.z, node.target.getColorTextureId(), node.shot);
                    drawnNow.add(w);
                    node.usedFrame = frame;
                }
            }
            // portals whose other end is too far to draw: their last picture
            for (Map.Entry<Wall, List<BlockPos>> e : FAR_WALLS.entrySet()) {
                Wall w = e.getKey();
                double d = w.distance(cam);
                Link link = LINKS.get(w);
                if (d <= 0.05 || d >= WALL_RANGE || link == null) {
                    continue;
                }
                Snapshot snap = snapshot(level, w, link);
                if (snap != null) {
                    drawFarFaces(w, link, e.getValue(), cam, snap);
                }
            }
            LAST_DRAWN.clear();
            LAST_DRAWN.addAll(drawnNow);
            if (overlay != null && dumpOverlay) {
                dump(com.example.skyblocks.client.iris.IrisIslandTarget.overlayTarget(), "shaderedplus_mirror_overlay.png");
            }
            if (overlay != null) {
                // where the faces are, for the end-of-frame test against the final depth (hand, entities in front)
                List<Wall> shown = new ArrayList<>(drawnNow);
                com.example.skyblocks.client.iris.IrisIslandTarget.recordPictureDepth(() -> {
                    ShaderInstance depthShader = com.example.skyblocks.client.iris.IrisIslandTarget.pictureShader();
                    for (Wall w : shown) {
                        drawFaceShape(w, WALLS.get(w), cam.x, cam.y, cam.z, depthShader);
                    }
                });
            }
        } finally {
            MirrorTiming.end();
            overlayFaces = false;
            if (iris) {
                RenderSystem.depthMask(true);
                com.example.skyblocks.client.iris.IrisIslandTarget.end(overlay);
                // back to what Iris had bound (main counts as bound for it, then its real framebuffers)
                main.bindWrite(false);
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, irisRead);
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, irisDraw);
                RenderSystem.viewport(0, 0, main.width, main.height);
                BufferUploader.invalidate();
                com.example.skyblocks.client.iris.IrisCompat.phase(irisPhase); // (the mirrors' skies moved it)
                com.example.skyblocks.client.iris.MirrorIrisState.restore(); // (the real view's matrices for the pack)
            }
            stack.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static Node node(List<Wall> path) {
        Node node = NODES.computeIfAbsent(path, Node::new);
        node.usedFrame = frame;
        return node;
    }

    private static double priority(Node n, boolean biggest) {
        if (biggest) {
            return Double.MAX_VALUE;
        }
        if (!n.valid || n.area > n.renderedArea * 1.25F + 0.01F || !covers(n.renderedRect, n.rect)) {
            return 1.0E12 * (n.area + 0.001) / n.depth;
        }
        return (n.area + 0.002) * (frame - n.renderedFrame) / Math.sqrt(n.depth);
    }

    /**
     * How sharp a picture is (part of the screen's resolution).
     */
    private static float scaleFor(Node n) {
        // full sharpness for the mirrors themselves (only the part on screen is drawn, so a small mirror is cheap
        // anyway); bounces a bit less sharp each, and tiny ones at half
        if (n.depth == 1) {
            return 1.0F;
        }
        float tier = n.area < 0.004F ? 0.5F : 1.0F;
        return tier * (float) Math.pow(BOUNCE_SCALE, n.depth - 1);
    }

    /**
     * The node's picture, sized for this frame: only as big as its mirror's part of the screen (and a margin), at its
     * sharpness - not a whole screen's worth for every mirror. Its size changes only when it no longer fits or is far
     * too big (a new size loses the old picture, so only right before drawing it).
     */
    private static void prepare(Node n, RenderTarget main, boolean drawingNow) {
        if (!drawingNow) {
            return;
        }
        float scale = scaleFor(n);
        float[] want = grow(n.rect, RECT_MARGIN);
        int needW = Math.max(16, (int) Math.ceil((want[2] - want[0]) * main.width * scale));
        int needH = Math.max(16, (int) Math.ceil((want[3] - want[1]) * main.height * scale));
        if (n.target == null) {
            n.target = newTarget(roundUp(needW), roundUp(needH));
            n.valid = false;
        } else if (n.target.width < needW || n.target.height < needH || n.target.width > needW * 2 + 64 || n.target.height > needH * 2 + 64) {
            resize(n.target, roundUp(needW), roundUp(needH));
        }
        n.scale = scale;
        // (the picture laid over the screen from the wanted part's lower left corner: the wanted part is drawn, the
        // rest of the picture - there only so it needn't be made again at every step - isn't)
        float pw = n.target.width / (main.width * scale), ph = n.target.height / (main.height * scale);
        n.picRect = new float[] {want[0], want[1], want[0] + pw, want[1] + ph};
        n.usedW = Math.min(1.0F, (want[2] - want[0]) / pw);
        n.usedH = Math.min(1.0F, (want[3] - want[1]) / ph);
    }

    private static int roundUp(int v) {
        return (v + 31) & ~31;
    }

    /**
     * The projection with its far plane at infinity. Moving the near plane onto a mirror (oblique projection) tilts
     * the far plane in, more the farther the mirror is from the eye: things far away in a mirror (a big cat behind
     * you) got cut off unless you stood right at the mirror. With no far plane there is nothing to tilt in. (The game's
     * projection is a perspective times view bobbing, a turn and shift: its depth row is rebuilt from the w row.)
     */
    private static Matrix4f infiniteFar(Matrix4f p) {
        float near = 0.05F;
        Matrix4f m = new Matrix4f(p);
        m.m02(p.m03());
        m.m12(p.m13());
        m.m22(p.m23());
        m.m32(p.m33() - 2.0F * near);
        return m;
    }

    private static void resize(TextureTarget t, int w, int h) {
        if (t.width != w || t.height != h) {
            t.resize(w, h, Minecraft.ON_OSX);
            t.setFilterMode(GL11.GL_LINEAR);
        }
    }

    private static TextureTarget newTarget(int w, int h) {
        TextureTarget t = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        t.enableStencil(); // Island Paintings mark their window in the stencil buffer
        t.setFilterMode(GL11.GL_LINEAR);
        return t;
    }

    /**
     * Seeing {@code wall} from the eye at (ex, ey, ez) looking with {@code eyeView} (the real camera, or one already
     * mirrored): the eye mirrored in the wall's plane, the view turned inside out, and the projection's near plane
     * moved onto the wall (oblique projection) so nothing behind it shows.
     */
    private static Chain chain(Wall wall, double ex, double ey, double ez, Matrix4f eyeView, boolean eyeFlipped, Matrix4f projection) {
        Link link = LINKS.get(wall);
        if (link != null) {
            return portalChain(link, ex, ey, ez, eyeView, eyeFlipped, projection);
        }
        double nx = wall.nx(), ny = wall.ny(), nz = wall.nz();
        double dist = nx * ex + ny * ey + nz * ez - wall.k();
        Chain c = new Chain();
        c.rx = ex - 2 * dist * nx;
        c.ry = ey - 2 * dist * ny;
        c.rz = ez - 2 * dist * nz;
        Matrix4f reflect = new Matrix4f(
                (float) (1 - 2 * nx * nx), (float) (-2 * nx * ny), (float) (-2 * nx * nz), 0,
                (float) (-2 * ny * nx), (float) (1 - 2 * ny * ny), (float) (-2 * ny * nz), 0,
                (float) (-2 * nz * nx), (float) (-2 * nz * ny), (float) (1 - 2 * nz * nz), 0,
                0, 0, 0, 1);
        c.modelView = new Matrix4f(eyeView).mul(reflect);
        Matrix4f inverseTranspose = new Matrix4f(c.modelView).invert().transpose();
        // exactly the wall; with a shader pack a little behind it: the pack works out depth effects with the real
        // camera's projection, and right at a mirror's near plane that lost the ground next to the mirror
        float planeW = (float) (nx * c.rx + ny * c.ry + nz * c.rz - wall.k()) + (packMargin ? PACK_CLIP_MARGIN : 0.0F);
        // the wall's plane in view space: the whole plane (normal and distance) through the inverse transpose. With
        // a shader pack the view bobbing is in the model-view (a small shift while walking, not only a turn), and
        // carrying over just the normal put the cut a few centimetres off the wall: the ground at the mirror's base
        // was cut away (a line of sky along the bottom) while walking
        Vector4f plane = inverseTranspose.transform(new Vector4f((float) nx, (float) ny, (float) nz, planeW));
        c.clipped = oblique(new Matrix4f(projection), plane);
        c.seen = new FrustumIntersection(new Matrix4f(c.clipped).mul(c.modelView));
        c.flipped = !eyeFlipped;
        return c;
    }

    /**
     * Seeing through a portal: the eye moved as the portal moves things (turned and shifted onto the linked portal),
     * the view turned with it, the near plane on the linked portal's plane (only what's past it shows). Not mirrored.
     */
    private static Chain portalChain(Link link, double ex, double ey, double ez, Matrix4f eyeView, boolean eyeFlipped, Matrix4f projection) {
        Vec3 eye = com.example.skyblocks.PortalMirrors.rotate(new Vec3(ex, ey, ez).subtract(link.from()), link.turns()).add(link.to());
        Chain c = new Chain();
        c.rx = eye.x;
        c.ry = eye.y;
        c.rz = eye.z;
        c.modelView = new Matrix4f(eyeView).mul(turn(link.turns()).invert());
        Wall d = link.dest();
        Matrix4f inverseTranspose = new Matrix4f(c.modelView).invert().transpose();
        // (a hair past the linked portal's plane: its own pane, right on the plane, isn't in the picture)
        float planeW = (float) (d.nx() * c.rx + d.ny() * c.ry + d.nz() * c.rz - d.k() - 0.02);
        Vector4f plane = inverseTranspose.transform(new Vector4f((float) d.nx(), (float) d.ny(), (float) d.nz(), planeW));
        c.clipped = oblique(new Matrix4f(projection), plane);
        c.seen = new FrustumIntersection(new Matrix4f(c.clipped).mul(c.modelView));
        c.flipped = eyeFlipped;
        return c;
    }

    /** Draws a node's picture: sky, blocks, entities, the latest pictures of the mirrors it sees, see-through blocks. */
    private static void renderNode(Minecraft mc, ClientLevel level, Camera camera, Node node, Matrix4f projection, float partialTick,
                                   boolean withBlockEntities) {
        Vec3 cam = camera.getPosition();
        Chain c = node.chain;
        TextureTarget target = node.target;
        currentTarget = target;
        currentRect = new float[] {0.0F, 0.0F, node.usedW, node.usedH}; // (only the mirror's part of the picture)
        double rx = c.rx, ry = c.ry, rz = c.rz;
        // the picture's projections: the screen's, with its part of the screen filling the picture
        Matrix4f toPic = rectOnly(node.picRect);
        Matrix4f modelView = c.modelView, clipped = new Matrix4f(toPic).mul(c.clipped);
        projection = new Matrix4f(toPic).mul(projection);
        int frontFace = c.flipped ? GL11.GL_CW : GL11.GL_CCW; // a reflection turns everything inside out

        rendering = true;
        MirrorTiming.start(MirrorTiming.SKY);
        // the sky's colour first (what the main view is cleared to), then the sky itself on top
        float[] fog = RenderSystem.getShaderFogColor();
        target.setClearColor(fog[0], fog[1], fog[2], 0); // (alpha 0: "nothing drawn here", see mirror_surface.fsh)
        float[] drawRect = grow(node.rect, RECT_MARGIN); // (where the mirror is on screen: for skipping what's tiny in it)
        RenderSystem.disableScissor();
        target.clear(Minecraft.ON_OSX);
        rebindPicture(); // (and only the mirror's part from here on)
        GL11.glFrontFace(GL11.GL_CCW);
        try {
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            float far = mc.gameRenderer.getRenderDistance();
            // the sky dome is seen from inside: no face culling for it; the sky is drawn with whatever shader is set
            RenderSystem.disableCull();
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionShader);
            mc.levelRenderer.renderSky(poseOf(modelView), projection, partialTick, camera, false,
                    () -> FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_SKY, far, false, partialTick));
            rebindPicture();
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
        if (packMargin) {
            // the shader pack works out world positions (shadows, waving) from these: the mirror's, not the real view's
            com.example.skyblocks.client.iris.MirrorIrisState.mirror(modelView, clipped, rx, ry, rz, cam.x, cam.y, cam.z);
        }
        // blocks: the chunk meshes already on the graphics card (with Sodium: only the sections this mirror sees)
        LevelRendererInvoker chunks = (LevelRendererInvoker) mc.levelRenderer;
        RenderSystem.setProjectionMatrix(clipped, VertexSorting.DISTANCE_TO_ORIGIN);
        // only what shows in the mirror's part of the screen: chunk sections, mobs, block entities and particles outside
        // it are skipped (a small mirror then costs little)
        Matrix4f rectClip = clipped; // (already only the picture's part of the screen)
        FrustumIntersection inRect = new FrustumIntersection(new Matrix4f(rectClip).mul(modelView));
        net.minecraft.client.renderer.culling.Frustum frustum = new net.minecraft.client.renderer.culling.Frustum(modelView, rectClip);
        frustum.prepare(rx, ry, rz);
        Matrix4f toPicture = new Matrix4f(c.clipped).mul(modelView); // (to the screen: what's tiny there is skipped)
        cullChunks(inRect, rx, ry, rz);
        MirrorTiming.start(MirrorTiming.BLOCKS);
        // without Sodium: this mirror's own sections (all round you, in its view), not the main view's every one
        boolean own = !SODIUM && MirrorChunks.works();
        List<Object> sections = own ? MirrorChunks.sections(frustum) : List.of();
        if (own) {
            MirrorChunks.build(sections, frame);
        }
        for (RenderType layer : new RenderType[] {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout()}) {
            if (own) {
                MirrorChunks.draw(layer, sections, rx, ry, rz, modelView, clipped);
            } else {
                chunks.skyblocks$renderChunkLayer(layer, poseOf(modelView), rx, ry, rz, clipped);
            }
            rebindPicture(); // (with a shader pack, Iris binds its own framebuffers around chunk drawing)
        }
        // mobs in front of the wall, you included, with their shadows; block entities
        MirrorTiming.start(MirrorTiming.MOBS);
        renderEntities(mc, level, camera, node, modelView, inRect, toPicture, drawRect, rx, ry, rz, partialTick, true);
        rebindPicture();
        GL11.glFrontFace(frontFace);
        RenderSystem.setProjectionMatrix(clipped, VertexSorting.DISTANCE_TO_ORIGIN);
        // portals given a Shadered sky: their sky (drawn in the main view at its entity stage, so not by the chunks)
        MirrorTiming.start(MirrorTiming.PORTALS);
        try {
            PortalSkyRenderer.drawInMirror(level, rx, ry, rz, modelView);
        } catch (RuntimeException ignored) {
            // left out of the mirror
        }
        rebindPicture();
        GL11.glFrontFace(frontFace);
        RenderSystem.setProjectionMatrix(clipped, VertexSorting.DISTANCE_TO_ORIGIN);
        // the mirrors seen in this one: their latest pictures
        PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        stack.mulPoseMatrix(modelView);
        RenderSystem.applyModelViewMatrix();
        try {
            // infinity mirrors in view (their tunnel worked out from this mirror's eye)
            // (the aquariums, infinity mirrors and dance floors are traced pixel by pixel: in a mirror seen in mirrors two
            // deep and small on screen they'd be a few pixels each, for the cost of drawing them again)
            if (node.depth <= 2 || node.area >= 0.01F) {
                MirrorTiming.start(MirrorTiming.INFINITY);
                InfinityMirrorRenderer.drawInMirror(rx, ry, rz, modelView, clipped);
                MirrorTiming.start(MirrorTiming.DANCE_FLOORS);
                DanceFloorRenderer.drawInMirror(rx, ry, rz, modelView, clipped);
                MirrorTiming.start(MirrorTiming.AQUARIUMS);
                AquariumRenderer.drawInMirror(rx, ry, rz, modelView, clipped);
            }
            MirrorTiming.start(MirrorTiming.INSIDE);
            for (Node child : node.children) {
                Node shown = child.valid ? child : NODES.get(List.of(child.wall)); // (not drawn yet: that mirror's own picture)
                if (shown != null && shown.valid && shown.target != null) {
                    drawFaces(child.wall, WALLS.get(child.wall), rx, ry, rz, shown.target.getColorTextureId(), shown.shot);
                }
            }
            // where the bounces stop (too deep, too small, too many): the latest picture there is of that mirror, so the
            // innermost mirror doesn't flicker between a reflection and the plain block as the limit moves around
            for (Wall w : node.overflow) {
                List<Wall> path = new ArrayList<>(node.path);
                path.add(w);
                Node shown = NODES.get(path);
                if (shown == null || !shown.valid || shown.target == null) {
                    shown = NODES.get(List.of(w));
                }
                if (shown != null && shown.valid && shown.target != null) {
                    drawFaces(w, WALLS.get(w), rx, ry, rz, shown.target.getColorTextureId(), shown.shot);
                }
            }
        } finally {
            stack.popPose();
            RenderSystem.applyModelViewMatrix();
        }
        GL11.glFrontFace(frontFace);
        // particles that aren't see-through, then see-through blocks (glass, water, portals) after the entities, like the
        // main view (without Sodium: in the main view's order, not re-sorted for every mirror), see-through particles,
        // clouds, rain and snow
        MirrorTiming.start(MirrorTiming.SEE_THROUGH);
        Eye eye = eyeFor(mc, camera, node, partialTick);
        effects(mc, camera, eye, node, modelView, clipped, frustum, rx, ry, rz, partialTick, false);
        GL11.glFrontFace(frontFace);
        rebindPicture();
        RenderSystem.setProjectionMatrix(clipped, VertexSorting.DISTANCE_TO_ORIGIN);
        if (SODIUM) {
            chunks.skyblocks$renderChunkLayer(RenderType.translucent(), poseOf(modelView), rx, ry, rz, clipped);
            rebindPicture();
        } else if (own) {
            MirrorChunks.draw(RenderType.translucent(), sections, rx, ry, rz, modelView, clipped);
        }
        effects(mc, camera, eye, node, modelView, clipped, frustum, rx, ry, rz, partialTick, true);
        GL11.glFrontFace(frontFace);
        MirrorTiming.end();

        node.shot.projModel.set(projection).mul(modelView); // (the oblique cut only changes depth: x, y and w are the projection's)
        node.shot.maxU = node.usedW;
        node.shot.maxV = node.usedH;
        node.shot.rx = rx;
        node.shot.ry = ry;
        node.shot.rz = rz;
        Matrix4f culling = rectOnly(grow(node.rect, CHUNK_MARGIN)).mul(c.clipped).mul(modelView).translate((float) (cam.x - rx), (float) (cam.y - ry), (float) (cam.z - rz));
        node.view = new MirrorCulling.View(new FrustumIntersection(culling));
    }

    private static void renderEntities(Minecraft mc, ClientLevel level, Camera camera, Node node, Matrix4f modelView,
                                       FrustumIntersection seen, Matrix4f toPicture, float[] rect,
                                       double rx, double ry, double rz, float partialTick,
                                       boolean withBlockEntities) {
        Wall wall = viewWall(node.wall);
        if (entityBuffers == null) {
            entityBuffers = MirrorBuffers.create();
        }
        PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        RenderSystem.applyModelViewMatrix();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(mc.options.entityShadows().get());
        // (name tags turned to face you in the mirror, as particles are)
        dispatcher.overrideCameraOrientation(eyeFor(mc, camera, node, partialTick).rotation());
        MultiBufferSource.BufferSource buffers = entityBuffers; // (each kind gathered, drawn once: MirrorBuffers)
        // (1.20.1: renderers get the view in their pose, as in the world's own pass; Shadered's models undo the real
        // camera's turn from it and its passes turn it back, which leaves the mirror's view in place)
        PoseStack pose = poseOf(modelView);
        Vec3 cam = camera.getPosition();
        ViewState.mirror(modelView); // (the turn in these poses, for the shaders that take it out)
        try {
            for (Near near : NEARBY_ENTITIES) {
                Entity entity = near.entity();
                double x = near.x(), y = near.y(), z = near.z();
                double front = wall.distance(new Vec3(x, y, z));
                if (front < -0.5 || front > ENTITY_RANGE) {
                    continue;
                }
                if (!entity.noCulling) {
                    AABB box = entity.getBoundingBoxForCulling().inflate(0.5).move(-rx, -ry, -rz);
                    if (!seen.testAab((float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ)) {
                        continue; // not in this mirror's view
                    }
                }
                try {
                    dispatcher.render(entity, x - rx, y - ry, z - rz, near.yRot(),
                            partialTick, pose, buffers, dispatcher.getPackedLightCoords(entity, partialTick));
                } catch (RuntimeException ignored) {
                    // an entity that can't be drawn here is left out of the mirror
                }
            }
            // block entities in front of the wall, every mod's. Cats are drawn right away with the mirror's view; what
            // other renderers queue for Shadered's passes later in the frame (models, sky cubes) is taken out of the
            // queues and drawn now, inside the mirror, instead of turning up in the real world
            buffers.endBatch(); // (the mobs drawn now, for timing)
            MirrorTiming.start(MirrorTiming.BLOCK_ENTITIES);
            net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher blockEntities = mc.getBlockEntityRenderDispatcher();
            boolean wasDirect = com.example.skyblocks.client.island.IslandRendering.directCats;
            boolean wasCollecting = com.example.skyblocks.client.island.IslandRendering.collectCats;
            var skiesBefore = ShaderedQueues.sizes();
            List<com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel> models;
            // cats (and other Shadered models) all in one batch, turned to the mirror's view; detail by how far
            // things are from the mirrored eye
            com.example.skyblocks.client.island.IslandRendering.directCats = false;
            com.example.skyblocks.client.island.IslandRendering.collectCats = true;
            com.example.skyblocks.client.island.IslandRendering.viewer = new Vec3(rx, ry, rz);
            com.example.skyblocks.client.island.IslandRendering.inMirror = true;
            try {
                models = !withBlockEntities ? List.of() : IslandCatBatch.capture(() -> {
                    for (BlockEntity be : NEARBY_BLOCK_ENTITIES) {
                        BlockPos pos = be.getBlockPos();
                        if (wall.distance(Vec3.atCenterOf(pos)) < -0.5) {
                            continue;
                        }
                        if (blockEntities.getRenderer(be) == null) {
                            continue;
                        }
                        AABB box = be.getRenderBoundingBox().move(-rx, -ry, -rz);
                        if (!seen.testAab((float) box.minX, (float) box.minY, (float) box.minZ, (float) box.maxX, (float) box.maxY, (float) box.maxZ)) {
                            continue; // not in this mirror's view
                        }
                        float[] onScreen = screenRect(toPicture, box, rect);
                        if (onScreen == null || area(onScreen) < TINY * (node.depth >= 3 ? 8.0F : node.depth == 2 ? 2.0F : 1.0F)) {
                            continue; // a few pixels in the mirror: not worth drawing
                        }
                        pose.pushPose();
                        pose.translate(pos.getX() - rx, pos.getY() - ry, pos.getZ() - rz);
                        long started = System.nanoTime();
                        try {
                            blockEntities.render(be, partialTick, pose, buffers);
                            MirrorTiming.blockEntity(be.getClass().getSimpleName(), System.nanoTime() - started);
                        } catch (RuntimeException ignored) {
                            // left out of the mirror
                        } finally {
                            pose.popPose();
                        }
                    }
                });
            } finally {
                com.example.skyblocks.client.island.IslandRendering.directCats = wasDirect;
                com.example.skyblocks.client.island.IslandRendering.collectCats = wasCollecting;
                com.example.skyblocks.client.island.IslandRendering.viewer = null;
                com.example.skyblocks.client.island.IslandRendering.inMirror = false;
            }
            var skies = ShaderedQueues.takeSince(skiesBefore);
            buffers.endBatch();
            MirrorTiming.start(MirrorTiming.SHADERED_MODELS);
            int drawFramebuffer = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try {
                IslandCatBatch.flush(partialTick); // our cats: their matrices already carry the mirror's view
                if (!models.isEmpty()) {
                    // other Shadered models (Maxwell...) are placed in plain world coordinates and ignore the pose:
                    // move them to where the mirror sees them. Shadered then applies the main camera R*T(-cam), so
                    // the matrix given is T(cam) * R^-1 * mirrorView * T(-mirroredEye) * world
                    Matrix4f turn = new Matrix4f().rotationX((float) Math.toRadians(camera.getXRot()))
                            .rotateY((float) Math.toRadians(camera.getYRot() + 180.0F));
                    IslandCatBatch.viewFix = new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z)
                            .mul(turn.invert())
                            .mul(modelView)
                            .translate((float) -rx, (float) -ry, (float) -rz);
                    try {
                        IslandCatBatch.addAll(models);
                        IslandCatBatch.flush(partialTick);
                    } finally {
                        IslandCatBatch.viewFix = null;
                    }
                }
            } catch (RuntimeException ignored) {
                IslandCatBatch.discard(); // left out of the mirror
            } finally {
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            }
            MirrorTiming.start(MirrorTiming.SHADERED_SKIES);
            try {
                ShaderedQueues.draw(skies);
            } catch (RuntimeException ignored) {
                // sky blocks left out of the mirror
            }
        } finally {
            ViewState.mirror(null);
            dispatcher.setRenderShadow(true);
            dispatcher.overrideCameraOrientation(camera.rotation());
            stack.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** A pose stack whose pose is {@code m} (and its normal matrix to match). */
    private static PoseStack poseOf(Matrix4f m) {
        PoseStack pose = new PoseStack();
        pose.last().pose().set(m);
        pose.last().normal().set(new org.joml.Matrix3f(m).invert().transpose());
        return pose;
    }

    /**
     * The block entities near the camera, for mirrors (every mod's: what some queue for later is handled when drawing).
     */
    private static void gatherBlockEntities(ClientLevel level, List<Vec3> around) {
        NEARBY_BLOCK_ENTITIES.clear();
        Set<Long> chunks = new HashSet<>();
        for (int i = 0; i < around.size(); i++) {
            Vec3 c = around.get(i);
            double range = i == 0 ? ENTITY_RANGE : PORTAL_RANGE;
            int radius = (int) (range / 16) + 1;
            int cx = SectionPos.blockToSectionCoord(c.x), cz = SectionPos.blockToSectionCoord(c.z);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!chunks.add(net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz))) {
                        continue; // (already gone through, for somewhere else)
                    }
                    LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos p = be.getBlockPos();
                        if (be.isRemoved() || !near(around, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, ENTITY_RANGE)) {
                            continue;
                        }
                        NEARBY_BLOCK_ENTITIES.add(be);
                    }
                }
            }
        }
    }

    /** What's shown round where a portal looks out (as far as that). */
    private static final double PORTAL_RANGE = 40.0;

    private static boolean near(List<Vec3> around, double x, double y, double z, double range) {
        for (int i = 0; i < around.size(); i++) {
            double r = i == 0 ? range : PORTAL_RANGE;
            if (around.get(i).distanceToSqr(x, y, z) <= r * r) {
                return true;
            }
        }
        return false;
    }

    /**
     * The camera particles and name tags are turned to in a mirror: at the real camera, looking the way the mirrored
     * view looks in the world (the real view direction mirrored in each wall of the node's chain, first to last). A
     * particle turned to it, seen through the reflection, faces you - as it would in a real mirror.
     */
    static final class Eye extends Camera {
        void place(Camera real, Vec3 forward) {
            setPosition(real.getPosition());
            float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
            float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-forward.y, -1.0, 1.0)));
            setRotation(yaw, pitch);
        }
    }

    private static final Eye EYE = new Eye();
    private static Node eyeNode;
    private static long eyeFrame = -1;

    private static Eye eyeFor(Minecraft mc, Camera camera, Node node, float partialTick) {
        if (eyeNode == node && eyeFrame == frame) {
            return EYE;
        }
        org.joml.Vector3f look = camera.getLookVector();
        Vec3 f = new Vec3(look.x(), look.y(), look.z());
        for (Wall w : node.path) { // (mirrored in the first wall seen, then the next... - or turned, through a portal)
            Link link = LINKS.get(w);
            if (link != null) {
                f = com.example.skyblocks.PortalMirrors.rotate(f, link.turns());
                continue;
            }
            Vec3 n = new Vec3(w.nx(), w.ny(), w.nz());
            f = f.subtract(n.scale(2.0 * f.dot(n)));
        }
        try {
            if (mc.level != null && mc.getCameraEntity() != null) {
                EYE.setup(mc.level, mc.getCameraEntity(), false, false, partialTick); // (its entity and level, as the real camera's)
            }
        } catch (RuntimeException ignored) {
            // (only its place and turn matter)
        }
        EYE.place(camera, f);
        eyeNode = node;
        eyeFrame = frame;
        return EYE;
    }

    private static java.lang.reflect.Method snowAndRain;
    private static boolean snowAndRainLooked, warnedEffects;

    /**
     * Particles (the see-through kinds, or the rest), and with the see-through ones the clouds, rain and snow, into the
     * picture. They're worked out round the real camera, as the main view's (no rebuilding the clouds for every mirror,
     * the same raindrops as you see): the model-view takes them from there to the mirrored eye.
     */
    private static void effects(Minecraft mc, Camera camera, Eye eye, Node node, Matrix4f modelView, Matrix4f clipped,
                                net.minecraft.client.renderer.culling.Frustum frustum, double rx, double ry, double rz, float partialTick,
                                boolean seeThrough) {
        Vec3 cam = camera.getPosition();
        Matrix4f fromCamera = new Matrix4f(modelView).translate((float) (cam.x - rx), (float) (cam.y - ry), (float) (cam.z - rz));
        // (1.20.1: particles and clouds take their view as a pose; the model-view stays plain)
        com.mojang.blaze3d.vertex.PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(clipped, VertexSorting.DISTANCE_TO_ORIGIN);
        net.minecraft.client.renderer.LightTexture light = mc.gameRenderer.lightTexture();
        try {
            RenderSystem.disableCull(); // (particles, rain: flat, seen from either side)
            // (1.20.1: all particles at once - no see-through split - with the see-through blocks' pass)
            if (seeThrough && node.depth <= 3) { // (deeper bounces are tiny: every particle in the world gone through again for them isn't worth it)
                try {
                    mc.particleEngine.render(poseOf(fromCamera), mc.renderBuffers().bufferSource(), light, eye, partialTick, frustum);
                } catch (RuntimeException e) {
                    warnEffects(e);
                }
                rebindPicture();
            }
            if (!seeThrough) {
                return;
            }
            RenderSystem.enableCull();
            if (mc.options.getCloudsType() != net.minecraft.client.CloudStatus.OFF) {
                try {
                    mc.levelRenderer.renderClouds(poseOf(fromCamera), clipped, partialTick, cam.x, cam.y, cam.z);
                } catch (RuntimeException e) {
                    warnEffects(e);
                }
                rebindPicture();
            }
            if (!snowAndRainLooked) {
                snowAndRainLooked = true;
                try {
                    snowAndRain = net.minecraft.client.renderer.LevelRenderer.class.getDeclaredMethod("renderSnowAndRain",
                            net.minecraft.client.renderer.LightTexture.class, float.class, double.class, double.class, double.class);
                    snowAndRain.setAccessible(true);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    snowAndRain = null;
                }
            }
            if (snowAndRain != null && node.depth <= 2 && mc.level != null && mc.level.getRainLevel(partialTick) > 0.0F) {
                RenderSystem.depthMask(false);
                try {
                    stack.mulPoseMatrix(fromCamera); // (the rain is drawn with the model-view)
                    RenderSystem.applyModelViewMatrix();
                    snowAndRain.invoke(mc.levelRenderer, light, partialTick, cam.x, cam.y, cam.z);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    warnEffects(e);
                    snowAndRain = null;
                } finally {
                    RenderSystem.depthMask(true);
                }
                rebindPicture();
            }
        } finally {
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            stack.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void warnEffects(Exception e) {
        if (!warnedEffects) {
            warnedEffects = true;
            SkyBlocksMod.LOGGER.warn("Shadered+: couldn't draw particles, clouds or rain in a mirror", e);
        }
    }

    /**
     * Per mirror in view: {its occlusion query, frames in a row it was wholly hidden behind blocks, 1 while its query
     * is still being answered, the frame it was last asked about}. The answers are read a frame or two late (no waiting on the graphics card).
     */
    private static final Map<Wall, int[]> OCCLUSION = new HashMap<>();
    /** Hidden this many frames in a row: its picture isn't drawn (back the frame after it shows again). */
    private static final int HIDDEN_FRAMES = 2;

    /**
     * Asks the graphics card whether any of each mirror's face shows past the world's blocks drawn so far (the
     * answer read in a later frame), and reads the answers that are in.
     */
    private static void occlusion(RenderTarget main, List<Map.Entry<Wall, List<BlockPos>>> visible, Matrix4f view, Matrix4f projection, Vec3 cam) {
        try {
            OCCLUSION.keySet().removeIf(w -> {
                if (WALLS.containsKey(w)) {
                    return false;
                }
                org.lwjgl.opengl.GL15.glDeleteQueries(OCCLUSION.get(w)[0]);
                return true;
            });
            ShaderInstance position = net.minecraft.client.renderer.GameRenderer.getPositionShader();
            if (position == null) {
                return;
            }
            main.bindWrite(true);
            com.mojang.blaze3d.vertex.PoseStack stack = RenderSystem.getModelViewStack();
            stack.pushPose();
            stack.setIdentity();
            stack.mulPoseMatrix(view);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-2.0F, -20.0F); // (as the faces: in front of the mirror block's own face)
            try {
                for (Map.Entry<Wall, List<BlockPos>> e : visible) {
                    int[] o = OCCLUSION.computeIfAbsent(e.getKey(), w -> new int[] {org.lwjgl.opengl.GL15.glGenQueries(), 0, 0, 0});
                    if ((int) frame - o[3] > 1) {
                        o[1] = 0; // (out of view for a while: what it was then doesn't count)
                    }
                    o[3] = (int) frame;
                    if (o[2] == 1) {
                        if (org.lwjgl.opengl.GL15.glGetQueryObjecti(o[0], org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                            continue; // (not answered yet: ask again once it is)
                        }
                        boolean seen = org.lwjgl.opengl.GL15.glGetQueryObjecti(o[0], org.lwjgl.opengl.GL15.GL_QUERY_RESULT) != 0;
                        o[1] = seen ? 0 : o[1] + 1;
                        o[2] = 0;
                    }
                    BufferBuilder.RenderedBuffer mesh = faceMesh(e.getKey(), e.getValue(), cam.x, cam.y, cam.z);
                    if (mesh == null) {
                        continue;
                    }
                    RenderSystem.setShader(() -> position);
                    org.lwjgl.opengl.GL15.glBeginQuery(org.lwjgl.opengl.GL33.GL_ANY_SAMPLES_PASSED, o[0]);
                    BufferUploader.drawWithShader(mesh);
                    org.lwjgl.opengl.GL15.glEndQuery(org.lwjgl.opengl.GL33.GL_ANY_SAMPLES_PASSED);
                    o[2] = 1;
                }
            } finally {
                RenderSystem.polygonOffset(0.0F, 0.0F);
                RenderSystem.disablePolygonOffset();
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                stack.popPose();
                RenderSystem.applyModelViewMatrix();
            }
        } catch (RuntimeException | LinkageError ex) {
            // (no answers: every mirror in view is drawn)
            OCCLUSION.values().forEach(o -> o[1] = 0);
        }
    }

    /** Back to the main view after a reflection. */
    private static void restore(Minecraft mc, RenderTarget main, Camera camera, Matrix4f projection, float partialTick) {
        rendering = false;
        com.example.skyblocks.client.iris.MirrorIrisState.restore(); // (the real view's matrices for the shader pack)
        cullChunks(null, 0, 0, 0);
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
     * A picture is drawn a bit past the mirror's edges, so moving doesn't show the picture's undrawn border before
     * its next turn; and the chunks kept for mirrors reach well past them (the chunk list is made before the frame's
     * mirrors are worked out, from the last frame's): no missing (sky-coloured) patches while moving.
     */
    private static final float RECT_MARGIN = 0.04F, CHUNK_MARGIN = 0.2F;
    /** Block entities smaller than this part of the screen in a mirror (about 10 x 10 pixels at 1080p) are skipped. */
    private static final float TINY = 0.00005F;

    /** Clip space -> clip space where {@code rect} (fractions of the screen) fills the whole view. */
    private static Matrix4f rectOnly(float[] rect) {
        float cx = rect[0] + rect[2] - 1.0F, cy = rect[1] + rect[3] - 1.0F;
        float hx = Math.max(1.0E-4F, rect[2] - rect[0]), hy = Math.max(1.0E-4F, rect[3] - rect[1]);
        Matrix4f m = new Matrix4f();
        m.m00(1.0F / hx);
        m.m30(-cx / hx);
        m.m11(1.0F / hy);
        m.m31(-cy / hy);
        return m;
    }

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

    /**
     * Before the faces go onto the real view: the main framebuffer bound and the drawing state set straight in OpenGL
     * as well as through the game's state cache. Something drawn during the mirrors (other mods' renderers, Shadered's
     * passes) can change OpenGL state behind the cache's back; then the faces weren't drawn for a frame (silver flash).
     */
    static void mainDrawState(RenderTarget main) {
        main.bindWrite(true);
        GL11.glColorMask(true, true, true, true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.colorMask(true, true, true, true);
        GL11.glDepthMask(true);
        RenderSystem.depthMask(false);
        RenderSystem.depthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderSystem.disableDepthTest();
        RenderSystem.enableDepthTest();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        GL11.glDepthRange(0.0, 1.0);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        RenderSystem.enableScissor(0, 0, 1, 1);
        RenderSystem.disableScissor();
        GL11.glFrontFace(GL11.GL_CCW);
        com.mojang.blaze3d.vertex.VertexBuffer.unbind(); // (and the game forgets which one it had bound)
    }

    private static boolean wholeInFront(Matrix4f toScreen, AABB box) {
        Vector4f v = new Vector4f();
        for (int i = 0; i < 8; i++) {
            v.set((float) ((i & 1) == 0 ? box.minX : box.maxX), (float) ((i & 2) == 0 ? box.minY : box.maxY),
                    (float) ((i & 4) == 0 ? box.minZ : box.maxZ), 1.0F);
            toScreen.transform(v);
            if (v.w < 0.05F || Math.abs(v.x) > v.w || Math.abs(v.y) > v.w) {
                return false;
            }
        }
        return true;
    }

    /** Whether the picture drawn for {@code drawn} (with its margin) still covers {@code now}. */
    private static boolean covers(float[] drawn, float[] now) {
        if (drawn == null) {
            return false;
        }
        float m = RECT_MARGIN * 0.8F;
        return now[0] >= drawn[0] - m && now[1] >= drawn[1] - m && now[2] <= drawn[2] + m && now[3] <= drawn[3] + m;
    }

    /** The rect grown by {@code by} (fractions of the screen) on each side, kept on screen. */
    private static float[] grow(float[] r, float by) {
        return new float[] {Math.max(0, r[0] - by), Math.max(0, r[1] - by), Math.min(1, r[2] + by), Math.min(1, r[3] + by)};
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

    private static void drawFaces(Wall wall, List<BlockPos> faces, double cx, double cy, double cz, int texture, Shot shot) {
        drawFaces(wall, faces, cx, cy, cz, texture, shot, false);
    }

    /** ({@code flipped}: a picture read back from a file, upside down to the ones drawn here.) */
    private static void drawFaces(Wall wall, List<BlockPos> faces, double cx, double cy, double cz, int texture, Shot shot, boolean flipped) {
        ShaderInstance shader = MirrorShaders.surface;
        if (shader == null || faces == null || faces.isEmpty()) {
            return;
        }
        // positions here are relative to (cx, cy, cz); the picture was taken relative to the mirrored eye
        // (a portal's face: each point where the portal takes it, by the linked portal, as its picture saw it)
        Link link = LINKS.get(wall);
        Matrix4f texMat = new Matrix4f(shot.projModel);
        if (link == null) {
            texMat.translate((float) (cx - shot.rx), (float) (cy - shot.ry), (float) (cz - shot.rz));
        } else {
            Vec3 t = com.example.skyblocks.PortalMirrors.rotate(new Vec3(cx, cy, cz).subtract(link.from()), link.turns()).add(link.to())
                    .subtract(shot.rx, shot.ry, shot.rz);
            texMat.translate((float) t.x, (float) t.y, (float) t.z).mul(turn(link.turns()));
        }
        if (flipped) {
            texMat = new Matrix4f().scaling(1.0F, -1.0F, 1.0F).mul(texMat);
        }
        shader.safeGetUniform("MirrorTexMat").set(texMat);
        shader.safeGetUniform("MirrorTexMax").set(shot.maxU, shot.maxV);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        // (into the shader pack overlay: no depth written, the world's depth must stay as it was: the overlay's
        // end-of-frame test finds the faces against it)
        RenderSystem.depthMask(!overlayFaces);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        BufferBuilder.RenderedBuffer mesh = faceMesh(wall, faces, cx, cy, cz, true);
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

    /** The wall's faces as quads (positions relative to (cx, cy, cz)). */
    private static BufferBuilder.RenderedBuffer faceMesh(Wall wall, List<BlockPos> faces, double cx, double cy, double cz) {
        return faceMesh(wall, faces, cx, cy, cz, false);
    }

    /**
     * The wall's faces as quads; with {@code uv}, for the surface shader, each face's own place (0..1 across it) and
     * what it is: UV = (u + 4 * (kind + 4 * seed), v + 4 * edges) - which mirror, a number of its own (0..15), and which
     * of its sides join on to another face of the same mirror in this wall (1 before it across, 2 after, 4 below, 8
     * above): the vanity mirror's frame goes round the outside of the whole mirror, not each block.
     */
    private static BufferBuilder.RenderedBuffer faceMesh(Wall wall, List<BlockPos> faces, double cx, double cy, double cz, boolean uv) {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, uv ? DefaultVertexFormat.POSITION_TEX : DefaultVertexFormat.POSITION);
        Direction d = wall.facing();
        ClientLevel level = Minecraft.getInstance().level;
        Set<BlockPos> here = uv ? new HashSet<>(faces) : null;
        Direction across = d.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        Direction upward = d.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        for (BlockPos pos : faces) {
            float x = (float) (pos.getX() - cx), y = (float) (pos.getY() - cy), z = (float) (pos.getZ() - cz);
            // (on the wall's own plane: a block's face, or a pane's glass out from its wall)
            float plane = (float) (wall.coord() - (d.getAxis() == Direction.Axis.X ? cx : d.getAxis() == Direction.Axis.Y ? cy : cz));
            if (uv) {
                int kind = level == null ? 0 : Math.max(0, mirrorKind(level.getBlockState(pos)));
                int edges = 0;
                if (level != null) {
                    if (sameMirror(level, here, pos, pos.relative(across.getOpposite()), kind)) edges |= 1;
                    if (sameMirror(level, here, pos, pos.relative(across), kind)) edges |= 2;
                    if (sameMirror(level, here, pos, pos.relative(upward.getOpposite()), kind)) edges |= 4;
                    if (sameMirror(level, here, pos, pos.relative(upward), kind)) edges |= 8;
                }
                int seed = kind == PORTAL_KIND && level != null
                        ? level.getBlockState(pos).getValue(com.example.skyblocks.PortalMirrorBlock.COLOR).getId() // (a portal: its colour)
                        : (int) ((pos.asLong() * 0x9E3779B97F4A7C15L) >>> 60) & 15;
                faceU = 4.0F * (kind + 8 * seed);
                faceV = 4.0F * edges;
            }
            faceUv = uv;
            switch (d) {
                case UP -> quad(b, x, plane + OUT, z, x + 1, plane + OUT, z, x + 1, plane + OUT, z + 1, x, plane + OUT, z + 1);
                case DOWN -> quad(b, x, plane - OUT, z, x + 1, plane - OUT, z, x + 1, plane - OUT, z + 1, x, plane - OUT, z + 1);
                case NORTH -> quad(b, x, y, plane - OUT, x + 1, y, plane - OUT, x + 1, y + 1, plane - OUT, x, y + 1, plane - OUT);
                case SOUTH -> quad(b, x, y, plane + OUT, x + 1, y, plane + OUT, x + 1, y + 1, plane + OUT, x, y + 1, plane + OUT);
                case WEST -> quad(b, plane - OUT, y, z, plane - OUT, y, z + 1, plane - OUT, y + 1, z + 1, plane - OUT, y + 1, z);
                case EAST -> quad(b, plane + OUT, y, z, plane + OUT, y, z + 1, plane + OUT, y + 1, z + 1, plane + OUT, y + 1, z);
            }
        }
        return b.end();
    }

    /** The faces' shape only, with the given shader (the shader pack overlay's depth test of where they show). */
    private static void drawFaceShape(Wall wall, List<BlockPos> faces, double cx, double cy, double cz, ShaderInstance shader) {
        if (shader == null || faces == null || faces.isEmpty()) {
            return;
        }
        BufferBuilder.RenderedBuffer mesh = faceMesh(wall, faces, cx, cy, cz);
        if (mesh != null) {
            RenderSystem.setShader(() -> shader);
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static boolean faceUv;
    private static float faceU, faceV;

    private static boolean sameMirror(ClientLevel level, Set<BlockPos> here, BlockPos pos, BlockPos next, int kind) {
        return here.contains(next) && mirrorKind(level.getBlockState(next)) == kind;
    }

    private static void quad(BufferBuilder b, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        if (faceUv) {
            b.vertex(x0, y0, z0).uv(faceU, faceV).endVertex();
            b.vertex(x1, y1, z1).uv(faceU + 1.0F, faceV).endVertex();
            b.vertex(x2, y2, z2).uv(faceU + 1.0F, faceV + 1.0F).endVertex();
            b.vertex(x3, y3, z3).uv(faceU, faceV + 1.0F).endVertex();
            return;
        }
        b.vertex(x0, y0, z0).endVertex();
        b.vertex(x1, y1, z1).endVertex();
        b.vertex(x2, y2, z2).endVertex();
        b.vertex(x3, y3, z3).endVertex();
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
        int kind = mirrorKind(state);
        return kind >= 0 && kind != PORTAL_KIND;
    }

    private static boolean isMirrorOrPortal(BlockState state) {
        return mirrorKind(state) >= 0;
    }

    static final int PORTAL_KIND = 4;

    /** Which mirror: 0 the plain one, 1 round, 2 cracked, 3 vanity (framed with bulbs), 4 a portal mirror; -1 not a mirror. */
    static int mirrorKind(BlockState state) {
        if (state.is(ModBlocks.MIRROR.get())) return 0;
        if (state.is(ModBlocks.ROUND_MIRROR.get())) return 1;
        if (state.is(ModBlocks.CRACKED_MIRROR.get())) return 2;
        if (state.is(ModBlocks.VANITY_MIRROR.get())) return 3;
        if (state.is(ModBlocks.PORTAL_MIRROR.get())) return PORTAL_KIND;
        return -1;
    }

    private static void scan(ClientLevel level, int centerX, int centerZ) {
        WALLS.clear();
        FAR_WALLS.clear();
        LINKS.clear();
        PORTALS.clear();
        List<BlockPos> portalBlocks = new ArrayList<>();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        // (portal mirrors further out too: the one a portal near you is linked to can be a way off)
        int portalRadius = SCAN_RADIUS + 1; // (which one each links to: the server's list, PortalClient)
        for (int dx = -portalRadius; dx <= portalRadius; dx++) {
            for (int dz = -portalRadius; dz <= portalRadius; dz++) {
                boolean mirrorsToo = Math.abs(dx) <= SCAN_RADIUS && Math.abs(dz) <= SCAN_RADIUS;
                LevelChunk chunk = level.getChunkSource().getChunk(centerX + dx, centerZ + dz, false);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section == null || section.hasOnlyAir()
                            || !section.maybeHas(mirrorsToo ? MirrorRenderer::isMirrorOrPortal : com.example.skyblocks.PortalMirrors::isPortal)) {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState here = section.getBlockState(x, y, z);
                                if (com.example.skyblocks.PortalMirrors.isPortal(here)) {
                                    portalBlocks.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                                    continue;
                                }
                                if (!mirrorsToo || !isMirror(here)) {
                                    continue;
                                }
                                BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                                if (here.getBlock() instanceof com.example.skyblocks.RoundMirrorBlock) {
                                    // a pane on a wall: only its front, a little out from the wall
                                    Direction d = here.getValue(com.example.skyblocks.RoundMirrorBlock.FACING);
                                    double coord = pos.get(d.getAxis()) + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE
                                            ? com.example.skyblocks.RoundMirrorBlock.THICK : 1.0 - com.example.skyblocks.RoundMirrorBlock.THICK);
                                    WALLS.computeIfAbsent(new Wall(d, coord), w -> new ArrayList<>()).add(pos);
                                    continue;
                                }
                                for (Direction d : Direction.values()) {
                                    neighbour.setWithOffset(pos, d);
                                    BlockState next = level.getBlockState(neighbour);
                                    if ((isMirror(next) && !(next.getBlock() instanceof com.example.skyblocks.RoundMirrorBlock)) || next.isSolidRender(level, neighbour)) {
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
        linkPortals(level, portalBlocks);
    }

    // ---- portals' last pictures ----

    /**
     * What's at the other end of a portal, taken now and then while it's drawn live: five pictures from just in front
     * of the other end (straight out, right, left, up, down), each with how far away every pixel is (in its alpha). A
     * portal whose other end is too far to draw live is drawn from them (shaders/core/portal_far): its view rays
     * followed out through them, so walking past it you still see round things. Saved with the world's name, so
     * they're there next time.
     */
    private static final class Snapshot {
        final TextureTarget[] packed = new TextureTarget[5];
        final net.minecraft.client.renderer.texture.DynamicTexture[] loaded = new net.minecraft.client.renderer.texture.DynamicTexture[5];
        boolean fromDisk;
        long frame = -100000L, savedFrame = -100000L;
        /** Being taken: from this frame on, a picture a frame (the chunks it needs asked for first). */
        long startedFrame = -1L;
        int next;

        int texture(int k) {
            return loaded[k] != null ? loaded[k].getId() : packed[k] != null ? packed[k].getColorTextureId() : 0;
        }

        boolean complete() {
            for (int k = 0; k < 5; k++) {
                if (texture(k) == 0) return false;
            }
            return true;
        }

        void closeLoaded() {
            for (int k = 0; k < 5; k++) {
                if (loaded[k] != null) {
                    loaded[k].close();
                    loaded[k] = null;
                }
            }
        }
    }

    private static final Map<String, Snapshot> SNAPSHOTS = new HashMap<>();
    private static final Set<String> LOOKED_ON_DISK = new HashSet<>();
    private static final int SNAPSHOT_EVERY = 200, SAVE_EVERY = 3000, SNAPSHOT_SIZE = 256, SNAPSHOT_LEAD = 3;
    private static final float SNAPSHOT_FAR = 256.0F;
    private static TextureTarget snapshotScratch;
    private static ShaderInstance packShader, farShader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class PortalShaders {
        private PortalShaders() {}

        @SubscribeEvent
        public static void registerPortalShaders(net.minecraftforge.client.event.RegisterShadersEvent event) {
            ShaderLoad.register(event, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "portal_pack"),
                    DefaultVertexFormat.POSITION, sh -> packShader = sh);
            ShaderLoad.register(event, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "portal_far"),
                    DefaultVertexFormat.POSITION_TEX, sh -> farShader = sh);
        }
    }

    /** Where the pictures are taken from: just in front of the other end's middle. */
    private static Vec3 snapshotEye(Link link) {
        Wall d = link.dest();
        return link.to().add(d.nx() * 0.05, 0.0, d.nz() * 0.05);
    }

    /** Picture k's view: straight out, right, left, up, down (the same as portal_far's look()). */
    private static Matrix4f snapshotView(Link link, int k) {
        Wall d = link.dest();
        org.joml.Vector3f f = new org.joml.Vector3f((float) d.nx(), 0.0F, (float) d.nz());
        org.joml.Vector3f y = new org.joml.Vector3f(0.0F, 1.0F, 0.0F);
        org.joml.Vector3f r = new org.joml.Vector3f(f).cross(y).normalize();
        org.joml.Vector3f a, up;
        switch (k) {
            case 0 -> { a = f; up = y; }
            case 1 -> { a = r; up = y; }
            case 2 -> { a = new org.joml.Vector3f(r).negate(); up = y; }
            case 3 -> { a = y; up = new org.joml.Vector3f(f).negate(); }
            default -> { a = new org.joml.Vector3f(y).negate(); up = f; }
        }
        return new Matrix4f().lookAt(0.0F, 0.0F, 0.0F, a.x, a.y, a.z, up.x, up.y, up.z);
    }

    private static Matrix4f snapshotProjection() {
        return new Matrix4f().setPerspective((float) (Math.PI / 2.0), 1.0F, 0.05F, SNAPSHOT_FAR);
    }

    private static String snapshotKey(ClientLevel level, Wall wall, Link link) {
        return level.dimension().location().toString().replace(':', '_').replace('/', '_') + "_" + wall.portal() + "_" + link.dest().portal();
    }

    private static java.io.File snapshotFile(String key, int k) {
        Minecraft mc = Minecraft.getInstance();
        String world = mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().getWorldData().getLevelName()
                : mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "unknown";
        java.io.File dir = new java.io.File(mc.gameDirectory, "shaderedplus/portal_pictures/" + world.replaceAll("[^A-Za-z0-9._-]", "_"));
        return new java.io.File(dir, key + "_" + k + ".png");
    }

    /** A portal's pictures: in memory, or read from their files (null: none yet). */
    private static Snapshot snapshot(ClientLevel level, Wall wall, Link link) {
        String key = snapshotKey(level, wall, link);
        Snapshot snap = SNAPSHOTS.get(key);
        if (snap != null) {
            return snap.complete() ? snap : null;
        }
        if (!LOOKED_ON_DISK.add(key)) {
            return null;
        }
        Snapshot read = new Snapshot();
        read.fromDisk = true;
        try {
            for (int k = 0; k < 5; k++) {
                java.io.File file = snapshotFile(key, k);
                if (!file.isFile()) {
                    read.closeLoaded();
                    return null;
                }
                try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                    // (saved as read back from the graphics card, bottom row first: put back the same way)
                    read.loaded[k] = new net.minecraft.client.renderer.texture.DynamicTexture(com.mojang.blaze3d.platform.NativeImage.read(in));
                    read.loaded[k].setFilter(true, false);
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            read.closeLoaded();
            return null;
        }
        SNAPSHOTS.put(key, read);
        return read;
    }

    /** The views of the pictures being taken (for the chunk search: the chunks they need are drawn for them). */
    private static final List<MirrorCulling.View> SNAPSHOT_VIEWS = new ArrayList<>();

    /** A picture a frame for one portal being drawn live (each portal every so often); saved now and then. */
    private static void snapshots(Minecraft mc, ClientLevel level, Camera camera, Vec3 cam, RenderTarget main, Matrix4f projection, float partialTick) {
        SNAPSHOT_VIEWS.clear();
        if (packShader == null) {
            return;
        }
        // the one being taken, else the stalest of those drawn live this frame
        Wall wall = null;
        Snapshot snap = null;
        long oldest = Long.MAX_VALUE;
        for (Map.Entry<Wall, Link> e : LINKS.entrySet()) {
            Wall w = e.getKey();
            if (!WALLS.containsKey(w)) {
                continue;
            }
            Snapshot s = SNAPSHOTS.get(snapshotKey(level, w, e.getValue()));
            if (s != null && s.startedFrame >= 0) {
                wall = w;
                snap = s;
                break;
            }
            Node live = NODES.get(List.of(w));
            if (live == null || !live.valid || live.renderedFrame != frame) {
                continue; // (only while it's drawn live: the chunks it shows are there)
            }
            long at = s == null || s.fromDisk ? -1_000_000_000L : s.frame; // (none yet: oldest of all - without overflowing)
            if (frame - at >= SNAPSHOT_EVERY && at < oldest) {
                oldest = at;
                wall = w;
                snap = s;
            }
        }
        if (wall == null) {
            return;
        }
        Link link = LINKS.get(wall);
        String key = snapshotKey(level, wall, link);
        if (snap == null || snap.fromDisk) {
            if (snap != null) {
                snap.closeLoaded();
            }
            snap = new Snapshot();
            SNAPSHOTS.put(key, snap);
        }
        Vec3 eye = snapshotEye(link);
        Matrix4f proj = snapshotProjection();
        if (snap.startedFrame < 0) {
            snap.startedFrame = frame;
            snap.next = 0;
        }
        // (the chunk search sees what they'll show, a few frames ahead)
        for (int k = 0; k < 5; k++) {
            SNAPSHOT_VIEWS.add(new MirrorCulling.View(new FrustumIntersection(new Matrix4f(proj).mul(snapshotView(link, k))
                    .translate((float) (cam.x - eye.x), (float) (cam.y - eye.y), (float) (cam.z - eye.z)))));
        }
        if (frame - snap.startedFrame < SNAPSHOT_LEAD) {
            return;
        }
        int k = snap.next;
        if (snapshotScratch == null) {
            snapshotScratch = newTarget(SNAPSHOT_SIZE, SNAPSHOT_SIZE);
        }
        if (snap.packed[k] == null) {
            snap.packed[k] = new TextureTarget(SNAPSHOT_SIZE, SNAPSHOT_SIZE, false, Minecraft.ON_OSX);
            snap.packed[k].setFilterMode(GL11.GL_LINEAR);
        }
        Node n = new Node(List.of(wall));
        n.target = snapshotScratch;
        n.picRect = FULL;
        n.rect = FULL;
        n.area = 1.0F;
        n.scale = 1.0F;
        n.usedW = 1.0F;
        n.usedH = 1.0F;
        Chain c = new Chain();
        c.rx = eye.x;
        c.ry = eye.y;
        c.rz = eye.z;
        c.modelView = snapshotView(link, k);
        c.clipped = new Matrix4f(proj);
        c.seen = new FrustumIntersection(new Matrix4f(proj).mul(c.modelView));
        c.flipped = false;
        n.chain = c;
        try {
            renderNode(mc, level, camera, n, proj, partialTick, true);
            // packed: its colour, and how far each pixel is in the alpha
            snap.packed[k].bindWrite(true);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            packShader.safeGetUniform("Near").set(0.05F);
            packShader.safeGetUniform("Far").set(SNAPSHOT_FAR);
            RenderSystem.setShader(() -> packShader);
            RenderSystem.setShaderTexture(0, snapshotScratch.getColorTextureId());
            RenderSystem.setShaderTexture(1, snapshotScratch.getDepthTextureId());
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            b.addVertex(-1.0F, -1.0F, 0.0F);
            b.addVertex(1.0F, -1.0F, 0.0F);
            b.addVertex(1.0F, 1.0F, 0.0F);
            b.addVertex(-1.0F, 1.0F, 0.0F);
            BufferUploader.drawWithShader(b.buildOrThrow());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        } finally {
            MirrorTiming.end();
            restore(mc, main, camera, projection, partialTick);
        }
        snap.next++;
        if (snap.next >= 5) {
            snap.startedFrame = -1L;
            snap.frame = frame;
            if (frame - snap.savedFrame >= SAVE_EVERY) {
                snap.savedFrame = frame;
                for (int i = 0; i < 5; i++) {
                    save(snap.packed[i], snapshotFile(key, i));
                }
            }
        }
    }

    /** A packed picture to its file (as read back, bottom row first, its alpha kept). */
    private static void save(TextureTarget target, java.io.File file) {
        try {
            com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(target.width, target.height, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            file.getParentFile().mkdirs();
            net.minecraft.Util.ioPool().execute(() -> {
                try (image) {
                    image.writeToFile(file);
                } catch (java.io.IOException e) {
                    SkyBlocksMod.LOGGER.warn("Shadered+: couldn't save {}", file, e);
                }
            });
        } catch (RuntimeException e) {
            SkyBlocksMod.LOGGER.warn("Shadered+: couldn't read a portal's picture", e);
        }
    }

    /** A far portal's face, drawn from its other end's pictures (portal_far). */
    private static void drawFarFaces(Wall wall, Link link, List<BlockPos> faces, Vec3 cam, Snapshot snap) {
        ShaderInstance shader = farShader;
        if (shader == null) {
            return;
        }
        Vec3 eye = snapshotEye(link);
        // (camera-relative points -> carried through the portal, relative to where the pictures were taken)
        Vec3 t = com.example.skyblocks.PortalMirrors.rotate(cam.subtract(link.from()), link.turns()).add(link.to()).subtract(eye);
        Matrix4f portalMat = new Matrix4f().translation((float) t.x, (float) t.y, (float) t.z).mul(turn(link.turns()));
        shader.safeGetUniform("PortalMat").set(portalMat);
        shader.safeGetUniform("EyeQ").set((float) t.x, (float) t.y, (float) t.z);
        shader.safeGetUniform("Front").set((float) link.dest().nx(), 0.0F, (float) link.dest().nz());
        RenderSystem.setShader(() -> shader);
        for (int k = 0; k < 5; k++) {
            RenderSystem.setShaderTexture(k, snap.texture(k));
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(!overlayFaces);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        MeshData mesh = faceMesh(wall, faces, cam.x, cam.y, cam.z, true);
        if (mesh != null) {
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

    /** The portal mirrors found: each linked one's two sides become walls whose pictures look through the other. */
    private static void linkPortals(ClientLevel level, List<BlockPos> blocks) {
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos pos : blocks) {
            if (done.contains(pos)) {
                continue;
            }
            com.example.skyblocks.PortalMirrors.Portal p = com.example.skyblocks.PortalMirrors.component(level, pos);
            if (p == null) {
                continue;
            }
            done.addAll(p.blocks());
            PORTALS.add(p);
        }
        for (com.example.skyblocks.PortalMirrors.Portal a : PORTALS) {
            com.example.skyblocks.PortalMirrors.Portal b = PortalClient.partnerOf(a);
            if (b == null) {
                continue; // (none: its swirl shows)
            }
            boolean far = forceFarPortals || !level.hasChunkAt(BlockPos.containing(b.origin()));
            int turns = com.example.skyblocks.PortalMirrors.turns(a, b);
            double planeA = com.example.skyblocks.PortalMirrors.plane(a.min(), a.facing());
            double planeB = com.example.skyblocks.PortalMirrors.plane(b.min(), b.facing());
            Wall wall = new Wall(a.facing(), planeA, a.min().asLong());
            // (looking into its front: out of the front of the linked portal)
            (far ? FAR_WALLS : WALLS).put(wall, new ArrayList<>(a.blocks()));
            LINKS.put(wall, new Link(new Wall(b.facing(), planeB, b.min().asLong()), turns, a.origin(), b.origin()));
        }
    }
}