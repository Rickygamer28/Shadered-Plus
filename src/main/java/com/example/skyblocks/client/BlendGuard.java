package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * The HUD starts from a known blending state.
 * <p>
 * 1.20.1: a core shader with its own blending (in its .json) is remembered as the last one used, and the next shader
 * without any turns blending off when it's used - Minecraft's memory of the state not knowing. The first thing the HUD
 * draws is the vignette: it darkens the screen's edges by blending, and drawn without blending it covers the whole
 * screen - a black screen with the hotbar and crosshair on top. So: before the HUD, that memory set back to "no
 * blending of its own", and OpenGL's blending made to agree with what Minecraft thinks it is.
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlendGuard {
    static final BlendMode OPAQUE = new BlendMode();
    /** (render test: off, to see the black screen it stops) */
    static boolean off;
    private static Field lastApplied, lastShader;
    private static boolean looked;
    private static final Set<String> told = new HashSet<>();

    private BlendGuard() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHudStart(RenderGuiEvent.Pre event) {
        try {
            BlendMode mode = lastApplied();
            boolean left = mode != null && !mode.isOpaque();
            boolean glOn = GL11.glIsEnabled(GL11.GL_BLEND);
            if (left || RenderTest.ON) {
                String shader = lastShaderName();
                String key = left + " " + shader + " " + glOn;
                if (told.add(key) && told.size() < 40) {
                    SkyBlocksMod.LOGGER.info("Shadered+: before the HUD - a shader's own blending left on: {}, last shader {}, blending in OpenGL {}{}",
                            left, shader, glOn, off ? " (not set back: test)" : "");
                }
            }
            if (off) {
                return;
            }
            if (left) {
                OPAQUE.apply(); // (remembered as "no blending of its own" again; turns blending off, through Minecraft)
            }
            // OpenGL and Minecraft's memory of it agreeing: blending on, the usual way, as the HUD expects
            RenderSystem.disableBlend();
            GL11.glDisable(GL11.GL_BLEND);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        } catch (RuntimeException e) {
            if (told.add("error")) {
                SkyBlocksMod.LOGGER.warn("Shadered+: couldn't check the blending before the HUD", e);
            }
        }
    }

    /** After one of Shadered+'s shaders with blending of its own is used (mixin/ShaderBlendResetMixin). */
    public static void afterOwnShader() {
        if (!off) {
            OPAQUE.apply();
        }
    }

    @Nullable
    private static BlendMode lastApplied() {
        look();
        try {
            return lastApplied == null ? null : (BlendMode) lastApplied.get(null);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static String lastShaderName() {
        look();
        try {
            Object s = lastShader == null ? null : lastShader.get(null);
            return s instanceof ShaderInstance i ? i.getName() : String.valueOf(s);
        } catch (IllegalAccessException e) {
            return "?";
        }
    }

    /** (found by type: the names differ in a played game) */
    private static void look() {
        if (looked) {
            return;
        }
        looked = true;
        lastApplied = staticOfType(BlendMode.class, BlendMode.class);
        lastShader = staticOfType(ShaderInstance.class, ShaderInstance.class);
    }

    @Nullable
    private static Field staticOfType(Class<?> owner, Class<?> type) {
        for (Field f : owner.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
                try {
                    f.setAccessible(true);
                    return f;
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
