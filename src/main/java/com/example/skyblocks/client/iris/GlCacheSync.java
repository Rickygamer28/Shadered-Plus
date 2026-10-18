package com.example.skyblocks.client.iris;

import com.mojang.blaze3d.platform.GlStateManager;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.lwjgl.opengl.GL13;

/**
 * Minecraft's GlStateManager remembers the textures it last bound and skips binds it thinks are already in effect.
 * Renderers that bind textures directly (Embeddium's and Oculus's) leave that memory out of date, so later binds can be
 * silently skipped. This makes it forget them (without asking OpenGL, which stalls the driver).
 * <p>
 * 1.20.1: fields have obfuscated names outside the development environment, so they are found by their shape: the
 * static array of texture states, its one int field (the binding), and the one non-final static int (the active unit).
 */
public final class GlCacheSync {
    private static boolean initialized;
    private static Object[] textures;
    private static Field bindingField;
    private static Field activeTextureField;

    private GlCacheSync() {}

    private static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            for (Field field : GlStateManager.class.getDeclaredFields()) {
                int mods = field.getModifiers();
                if (!Modifier.isStatic(mods)) {
                    continue;
                }
                if (field.getType().isArray() && field.getType().getComponentType().getSimpleName().equals("TextureState")) {
                    field.setAccessible(true);
                    textures = (Object[]) field.get(null);
                } else if (field.getType() == int.class && !Modifier.isFinal(mods)) {
                    field.setAccessible(true);
                    activeTextureField = field;
                }
            }
            if (textures != null && textures.length > 0) {
                for (Field field : textures[0].getClass().getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                        field.setAccessible(true);
                        bindingField = field;
                    }
                }
            }
            if (textures == null || bindingField == null || activeTextureField == null) {
                throw new NoSuchFieldException("texture cache fields not found");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            textures = null;
            com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Couldn't access GlStateManager's texture cache", e);
        }
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
}
