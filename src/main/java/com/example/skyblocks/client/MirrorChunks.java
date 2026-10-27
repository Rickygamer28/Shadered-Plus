package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * A mirror's blocks without Sodium (with Sodium, its own chunk meshes are drawn instead: MirrorRenderer).
 * <p>
 * The game's chunk drawing goes through the sections the main view sees, every one of them, whatever the mirror shows
 * (a mirror next to a lot of blocks drew them all again: slow), and none behind you (holes of sky in the reflection).
 * Here each mirror gets its own list: the sections of the game's chunk graph (all round you, not only in front) that are
 * in that mirror's view, found once per picture and drawn layer by layer - see-through blocks too, far to near. A
 * section the main view hasn't needed yet (behind you, never looked at) is built the way the game builds them, a few a
 * frame, so the mirror has it.
 * <p>
 * The game's private parts are found by their types (not names), and if anything doesn't fit, mirrors fall back to the
 * main view's own list.
 */
final class MirrorChunks {
    private static boolean looked, broken;
    private static Field visibleField, graphField, dispatcherField;
    private static Method inFrustum;
    private static int builtThisFrame;
    private static long buildFrame = -1;
    private static final int BUILDS_PER_FRAME = 4;

    private MirrorChunks() {}

    private static void look() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            for (Field f : LevelRenderer.class.getDeclaredFields()) {
                Class<?> t = f.getType();
                String n = t.getName();
                if (n.endsWith("SectionOcclusionGraph")) {
                    graphField = f;
                } else if (t == SectionRenderDispatcher.class) {
                    dispatcherField = f;
                } else if (f.getName().equals("visibleSections")) {
                    visibleField = f;
                }
            }
            if (visibleField == null) { // (by type: the one list of sections)
                for (Field f : LevelRenderer.class.getDeclaredFields()) {
                    if (it.unimi.dsi.fastutil.objects.ObjectArrayList.class.isAssignableFrom(f.getType())) {
                        visibleField = f;
                        break;
                    }
                }
            }
            if (graphField != null) {
                for (Method m : graphField.getType().getDeclaredMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 2 && p[0] == Frustum.class && List.class.isAssignableFrom(p[1])) {
                        inFrustum = m;
                        inFrustum.setAccessible(true);
                        break;
                    }
                }
                graphField.setAccessible(true);
            }
            if (visibleField != null) visibleField.setAccessible(true);
            if (dispatcherField != null) dispatcherField.setAccessible(true);
            broken = visibleField == null;
            if (inFrustum == null) {
                SkyBlocksMod.LOGGER.info("Shadered+ mirrors: the chunk graph wasn't found; mirrors use the main view's chunks");
            }
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            SkyBlocksMod.LOGGER.warn("Shadered+ mirrors: couldn't reach the game's chunk lists", e);
        }
    }

    /**
     * The sections in a mirror's view ({@code frustum}: set up for the mirror's eye), nearest the camera first (the
     * game's order).
     */
    @SuppressWarnings("unchecked")
    static List<SectionRenderDispatcher.RenderSection> sections(Frustum frustum) {
        look();
        List<SectionRenderDispatcher.RenderSection> out = new ArrayList<>();
        if (broken) {
            return out;
        }
        LevelRenderer lr = Minecraft.getInstance().levelRenderer;
        try {
            if (inFrustum != null) {
                Object graph = graphField.get(lr);
                if (graph != null) {
                    inFrustum.invoke(graph, frustum, out);
                    return out;
                }
            }
            for (SectionRenderDispatcher.RenderSection s : (List<SectionRenderDispatcher.RenderSection>) visibleField.get(lr)) {
                if (frustum.isVisible(s.getBoundingBox())) {
                    out.add(s);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            SkyBlocksMod.LOGGER.warn("Shadered+ mirrors: couldn't list the chunk sections", e);
            out.clear();
        }
        return out;
    }

    /** Whether mirrors draw their blocks here (false: the game's own way, as before). */
    static boolean works() {
        look();
        return !broken;
    }

    /** Sections in a mirror's view the game hasn't built yet (out of the main view): a few built a frame, as the game would. */
    static void build(List<SectionRenderDispatcher.RenderSection> sections, long frame) {
        if (dispatcherField == null || broken) {
            return;
        }
        if (buildFrame != frame) {
            buildFrame = frame;
            builtThisFrame = 0;
        }
        try {
            SectionRenderDispatcher dispatcher = null;
            RenderRegionCache cache = null;
            for (SectionRenderDispatcher.RenderSection s : sections) {
                if (builtThisFrame >= BUILDS_PER_FRAME) {
                    return;
                }
                if (!s.isDirty() || !s.hasAllNeighbors()) {
                    continue;
                }
                if (dispatcher == null) {
                    dispatcher = (SectionRenderDispatcher) dispatcherField.get(Minecraft.getInstance().levelRenderer);
                    cache = new RenderRegionCache();
                    if (dispatcher == null) {
                        return;
                    }
                }
                s.rebuildSectionAsync(dispatcher, cache);
                s.setNotDirty();
                builtThisFrame++;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            dispatcherField = null; // (left to the main view: no more building here)
        }
    }

    /**
     * One layer of the sections (positions relative to the mirror's eye at (x, y, z)), as the game draws a layer -
     * without re-sorting see-through blocks for the mirror's eye (they keep the main view's order: re-sorting for
     * every mirror and back every frame would be slow).
     */
    static void draw(RenderType type, List<SectionRenderDispatcher.RenderSection> sections, double x, double y, double z, Matrix4f modelView,
                     Matrix4f projection) {
        if (sections.isEmpty()) {
            return;
        }
        boolean mipped = type == RenderType.cutoutMipped();
        TextureAtlas atlas = mipped ? Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS) : null;
        if (atlas != null) {
            atlas.setBlurMipmap(false, Minecraft.getInstance().options.mipmapLevels().get() > 0); // (as the game: no flickering leaves)
        }
        type.setupRenderState();
        try {
            ShaderInstance shader = RenderSystem.getShader();
            if (shader == null) {
                return;
            }
            Window window = Minecraft.getInstance().getWindow();
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, window);
            shader.apply();
            Uniform offset = shader.CHUNK_OFFSET;
            boolean forward = type != RenderType.translucent();
            int n = sections.size();
            for (int k = 0; k < n; k++) {
                SectionRenderDispatcher.RenderSection s = sections.get(forward ? k : n - 1 - k); // (see-through: far to near)
                if (s.getCompiled().isEmpty(type)) {
                    continue;
                }
                VertexBuffer buffer = s.getBuffer(type);
                BlockPos origin = s.getOrigin();
                if (offset != null) {
                    offset.set((float) (origin.getX() - x), (float) (origin.getY() - y), (float) (origin.getZ() - z));
                    offset.upload();
                }
                buffer.bind();
                buffer.draw();
            }
            if (offset != null) {
                offset.set(0.0F, 0.0F, 0.0F);
            }
            shader.clear();
            VertexBuffer.unbind();
        } finally {
            type.clearRenderState();
            if (atlas != null) {
                atlas.restoreLastBlurMipmap();
            }
        }
    }

    /** Whether the box is (partly) on the front side of the plane n . p = k (the mirror's wall). */
    static boolean inFront(AABB box, double nx, double ny, double nz, double k) {
        double px = nx > 0 ? box.maxX : box.minX, py = ny > 0 ? box.maxY : box.minY, pz = nz > 0 ? box.maxZ : box.minZ;
        return nx * px + ny * py + nz * pz - k > 0.0;
    }
}
