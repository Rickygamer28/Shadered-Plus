// Adapted from Shadered Compat Fix (github.com/Rickygamer28/shadered-compat-fix, version 13), MIT License.
package com.example.skyblocks.client.iris;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft's GlStateManager remembers the OpenGL state it last set (enabled capabilities, blend and depth
 * functions, bound textures, ...) and skips calls it thinks are already in effect. Renderers that change
 * OpenGL state directly (Sodium's and Iris's) leave that memory out of date, so later calls Shadered relies
 * on can be silently skipped. This re-reads the real state from OpenGL into GlStateManager's cache.
 */
public final class GlCacheSync {
    private static boolean initialized;
    private static Object[] textures;
    private static Field bindingField;
    private static Field activeTextureField;
    /** GlStateManager's BooleanState objects (cached glEnable/glDisable state for one capability). */
    private static final List<Object> booleanStates = new ArrayList<>();
    private static Field booleanCapField;
    private static Field booleanEnabledField;
    private static Object blend;
    private static Object depth;
    private static Object cull;
    private static Object colorMask;

    private GlCacheSync() {}

    private static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Field texturesField = GlStateManager.class.getDeclaredField("TEXTURES");
            texturesField.setAccessible(true);
            textures = (Object[]) texturesField.get(null);
            bindingField = textures[0].getClass().getDeclaredField("binding");
            bindingField.setAccessible(true);
            activeTextureField = GlStateManager.class.getDeclaredField("activeTexture");
            activeTextureField.setAccessible(true);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            textures = null;
            com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Couldn't access GlStateManager's texture cache", e);
        }
        try {
            for (Field field : GlStateManager.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive() || field.getType().isArray()) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(null);
                if (value == null) {
                    continue;
                }
                switch (field.getName()) {
                    case "BLEND" -> blend = value;
                    case "DEPTH" -> depth = value;
                    case "CULL" -> cull = value;
                    case "COLOR_MASK" -> colorMask = value;
                    default -> { }
                }
                collectBooleanStates(value);
            }
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Couldn't access GlStateManager's state cache", e);
        }
    }

    private static boolean isBooleanState(Object value) {
        return value.getClass().getSimpleName().equals("BooleanState");
    }

    private static void collectBooleanStates(Object holder) throws ReflectiveOperationException {
        if (isBooleanState(holder)) {
            addBooleanState(holder);
            return;
        }
        if (!holder.getClass().getName().startsWith(GlStateManager.class.getName())) {
            return;
        }
        for (Field field : holder.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(holder);
            if (value != null && isBooleanState(value)) {
                addBooleanState(value);
            }
        }
    }

    private static void addBooleanState(Object state) throws ReflectiveOperationException {
        if (booleanCapField == null) {
            booleanCapField = state.getClass().getDeclaredField("state");
            booleanCapField.setAccessible(true);
            booleanEnabledField = state.getClass().getDeclaredField("enabled");
            booleanEnabledField.setAccessible(true);
        }
        booleanStates.add(state);
    }

    /**
     * Makes GlStateManager forget which textures it thinks are bound (without asking OpenGL, which stalls the driver),
     * so the next binds really happen; leaves texture unit 0 active.
     */
    public static void forgetTextures() {
        init();
        if (textures == null) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            return;
        }
        try {
            for (Object unit : textures) {
                bindingField.setInt(unit, -1);
            }
            activeTextureField.setInt(null, -1);
        } catch (ReflectiveOperationException | RuntimeException e) {
            textures = null;
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    /** Makes GlStateManager's per-unit texture bindings and active unit match OpenGL's actual state. */
    public static void syncTextures() {
        init();
        if (textures == null) {
            return;
        }
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            for (int unit = 0; unit < textures.length; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                bindingField.setInt(textures[unit], GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            }
            GL13.glActiveTexture(active);
            activeTextureField.setInt(null, active - GL13.GL_TEXTURE0);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            GL13.glActiveTexture(active);
            textures = null;
            com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Couldn't sync GlStateManager's texture cache", e);
        }
    }

    /** Syncs everything GlStateManager caches: capabilities, blend/depth/cull settings, color mask, textures. */
    public static void syncAll() {
        init();
        syncTextures();
        try {
            for (Object state : booleanStates) {
                int capability = booleanCapField.getInt(state);
                booleanEnabledField.setBoolean(state, GL11.glIsEnabled(capability));
            }
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            booleanStates.clear();
            com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Couldn't sync GlStateManager's capability cache", e);
        }
        setInt(blend, "srcRgb", GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        setInt(blend, "dstRgb", GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));
        setInt(blend, "srcAlpha", GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));
        setInt(blend, "dstAlpha", GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        setInt(depth, "func", GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        setBoolean(depth, "mask", GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        setInt(cull, "mode", GL11.glGetInteger(GL11.GL_CULL_FACE_MODE));
        if (colorMask != null) {
            ByteBuffer mask = MemoryUtil.memAlloc(4);
            try {
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
                setBoolean(colorMask, "red", mask.get(0) != 0);
                setBoolean(colorMask, "green", mask.get(1) != 0);
                setBoolean(colorMask, "blue", mask.get(2) != 0);
                setBoolean(colorMask, "alpha", mask.get(3) != 0);
            }
            finally {
                MemoryUtil.memFree(mask);
            }
        }
    }

    private static void setInt(Object holder, String name, int value) {
        if (holder == null) {
            return;
        }
        try {
            Field field = holder.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(holder, value);
        }
        catch (ReflectiveOperationException | RuntimeException ignored) {
            // Field names differ in this version; that part of the cache stays as it was.
        }
    }

    private static void setBoolean(Object holder, String name, boolean value) {
        if (holder == null) {
            return;
        }
        try {
            Field field = holder.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(holder, value);
        }
        catch (ReflectiveOperationException | RuntimeException ignored) {
            // Field names differ in this version; that part of the cache stays as it was.
        }
    }
}
