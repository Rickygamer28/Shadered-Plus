package com.example.skyblocks.client;

import com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer;
import com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Cats inside an Island Painting must be drawn while the painting's window is open, but drawing each one on its own
 * (AssimpRenderer.renderDirect) costs a full upload and a GPU sync per cat. Instead they are collected here and drawn
 * together with Shadered's batched renderer: whatever Shadered had already queued this frame is set aside, our cats are
 * queued and drawn in one go, and Shadered's queue is put back (to be drawn at its usual time). Falls back to drawing
 * one by one if Shadered's queue can't be reached.
 */
public final class IslandCatBatch {
    private static final List<RenderableModel> PENDING = new ArrayList<>();
    private static boolean looked, usable;
    private static Field opaqueField, transparentField, modelsField;

    /** A cat whose modelMatrix is in world coordinates, to draw at the next flush. */
    static void add(RenderableModel model) {
        PENDING.add(model);
    }

    /**
     * While drawing into a mirror: turns a cat's matrix (world position = camera + its pose, which is relative to the
     * mirrored eye) into one that Shadered's camera turn and position bring to where the mirror sees it.
     */
    @org.jetbrains.annotations.Nullable
    public static org.joml.Matrix4f viewFix;

    /** Draws the collected cats now (called by the painting while its window is open). */
    public static void flush(float partialTick) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<RenderableModel> cats = new ArrayList<>(PENDING);
        PENDING.clear();
        if (viewFix != null) {
            List<org.joml.Matrix4f> original = new ArrayList<>();
            for (RenderableModel cat : cats) {
                original.add(cat.modelMatrix);
                cat.modelMatrix = new org.joml.Matrix4f(viewFix).mul(cat.modelMatrix);
            }
            try {
                drawAll(cats, partialTick);
            } finally {
                for (int i = 0; i < cats.size(); i++) {
                    cats.get(i).modelMatrix = original.get(i);
                }
            }
            return;
        }
        drawAll(cats, partialTick);
    }

    private static void drawAll(List<RenderableModel> cats, float partialTick) {
        AssimpRenderer renderer = AssimpRenderer.getInstance();
        if (!look()) {
            drawOneByOne(renderer, cats);
            return;
        }
        Object savedOpaque, savedTransparent, savedModels;
        try {
            savedOpaque = copyOpaque(opaqueField.get(renderer));
            savedTransparent = new ArrayList<>((Collection<?>) transparentField.get(renderer));
            savedModels = new ArrayList<>((Collection<?>) modelsField.get(renderer));
            ((Map<?, ?>) opaqueField.get(renderer)).clear();
            ((Collection<?>) transparentField.get(renderer)).clear();
            ((Collection<?>) modelsField.get(renderer)).clear();
        } catch (ReflectiveOperationException | RuntimeException e) {
            usable = false;
            drawOneByOne(renderer, cats);
            return;
        }
        try {
            for (RenderableModel cat : cats) {
                renderer.addModel(cat);
            }
            Minecraft mc = Minecraft.getInstance();
            renderer.render(partialTick, mc.level == null ? 0 : (int) mc.level.getGameTime());
        } finally {
            restore(renderer, savedOpaque, savedTransparent, savedModels);
        }
    }

    /**
     * Runs {@code draw} with Shadered's model queue set aside, and returns the models it queued (taken back out, so
     * they aren't drawn in Shadered's pass later; the queue is put back as it was).
     */
    @SuppressWarnings("unchecked")
    public static List<RenderableModel> capture(Runnable draw) {
        AssimpRenderer renderer = AssimpRenderer.getInstance();
        if (!look()) {
            draw.run();
            return List.of();
        }
        Object savedOpaque, savedTransparent, savedModels;
        try {
            savedOpaque = copyOpaque(opaqueField.get(renderer));
            savedTransparent = new ArrayList<>((Collection<?>) transparentField.get(renderer));
            savedModels = new ArrayList<>((Collection<?>) modelsField.get(renderer));
            ((Map<?, ?>) opaqueField.get(renderer)).clear();
            ((Collection<?>) transparentField.get(renderer)).clear();
            ((Collection<?>) modelsField.get(renderer)).clear();
        } catch (ReflectiveOperationException | RuntimeException e) {
            usable = false;
            draw.run();
            return List.of();
        }
        List<RenderableModel> taken = new ArrayList<>();
        try {
            draw.run();
        } finally {
            try {
                taken.addAll((Collection<RenderableModel>) modelsField.get(renderer));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // nothing taken
            }
            restore(renderer, savedOpaque, savedTransparent, savedModels);
        }
        return taken;
    }

    /** True if cats have been collected and not drawn yet. */
    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /** Moves the collected cats into {@code out} (to draw later). */
    public static void drainTo(List<RenderableModel> out) {
        out.addAll(PENDING);
        PENDING.clear();
    }

    /** Adds cats taken earlier with {@link #drainTo}, to draw at the next flush. */
    public static void addAll(List<RenderableModel> cats) {
        PENDING.addAll(cats);
    }

    /** Throws away cats collected but not drawn (e.g. a painting that failed half-way). */
    public static void discard() {
        PENDING.clear();
    }

    private static boolean look() {
        if (!looked) {
            looked = true;
            try {
                opaqueField = AssimpRenderer.class.getDeclaredField("opaqueEntries");
                transparentField = AssimpRenderer.class.getDeclaredField("transparentEntries");
                modelsField = AssimpRenderer.class.getDeclaredField("models");
                opaqueField.setAccessible(true);
                transparentField.setAccessible(true);
                modelsField.setAccessible(true);
                usable = Map.class.isAssignableFrom(opaqueField.getType())
                        && Collection.class.isAssignableFrom(transparentField.getType())
                        && Collection.class.isAssignableFrom(modelsField.getType());
            } catch (ReflectiveOperationException | RuntimeException e) {
                usable = false;
            }
        }
        return usable;
    }

    /** opaqueEntries is material -> buffer -> entries: copy every level (the original is cleared and refilled). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object copyOpaque(Object value) {
        Map<Object, Object> copy = new HashMap<>();
        for (Map.Entry<?, ?> material : ((Map<?, ?>) value).entrySet()) {
            Map<Object, Object> buffers = new HashMap<>();
            for (Map.Entry<?, ?> buffer : ((Map<?, ?>) material.getValue()).entrySet()) {
                buffers.put(buffer.getKey(), new ArrayList<>((Collection) buffer.getValue()));
            }
            copy.put(material.getKey(), buffers);
        }
        return copy;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restore(AssimpRenderer renderer, Object opaque, Object transparent, Object models) {
        try {
            Map target = (Map) opaqueField.get(renderer);
            target.clear();
            target.putAll((Map) opaque);
            Collection t = (Collection) transparentField.get(renderer);
            t.clear();
            t.addAll((Collection) transparent);
            Collection m = (Collection) modelsField.get(renderer);
            m.clear();
            m.addAll((Collection) models);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            usable = false;
        }
    }

    private static void drawOneByOne(AssimpRenderer renderer, List<RenderableModel> cats) {
        // renderDirect (Shadered 1.20.1) wants view-space matrices; the collected ones are in world coordinates
        net.minecraft.client.Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 camera = cam.getPosition();
        org.joml.Matrix4f view = new org.joml.Matrix4f().rotationX((float) Math.toRadians(cam.getXRot()))
                .rotateY((float) Math.toRadians(cam.getYRot() + 180.0F))
                .translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
        for (RenderableModel cat : cats) {
            cat.modelMatrix = new org.joml.Matrix4f(view).mul(cat.modelMatrix);
            renderer.renderDirect(cat, null, false);
        }
    }

    private IslandCatBatch() {}
}
