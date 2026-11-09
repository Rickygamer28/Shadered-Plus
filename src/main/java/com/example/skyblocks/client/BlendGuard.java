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
 * The HUD starts from Minecraft's usual blending state.
 * <p>
 * 1.20.1: each core shader has a blending of its own (in its .json), and Minecraft only sets it when it differs from
 * the one it remembers - and it only remembers a new one when blending goes from off to on or back. After one of
 * Shadered+'s shaders (which blend their own way) came straight after a shader without blending, it was the one
 * remembered: from then on every ordinary see-through shader set its own blend function again when used, over what
 * the code drawing with it had just asked for. The first thing the HUD draws is the vignette, which darkens the
 * screen's edges with a blend function of its own - drawn with the ordinary one instead, it covered the whole screen:
 * a black screen with the hotbar and crosshair on top.
 * <p>
 * So: Shadered+'s shaders put back what was remembered once they've drawn (mixin/ShaderBlendResetMixin), and before
 * the HUD, the remembered blending is the HUD's own shader's.
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlendGuard {
    /** (render test: off, to see the black screen it stops) */
    static boolean off;
    private static Field lastApplied, lastShader, blendField;
    private static boolean looked;
    private static final Set<String> told = new HashSet<>();

    private BlendGuard() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHudStart(RenderGuiEvent.Pre event) {
        try {
            BlendMode mode = lastApplied();
            BlendMode hud = blendOf(net.minecraft.client.renderer.GameRenderer.getPositionTexShader());
            boolean left = hud != null && !hud.equals(mode); // (not what the HUD's own shader would leave)
            boolean glOn = GL11.glIsEnabled(GL11.GL_BLEND);
            if (left || RenderTest.ON) {
                String shader = lastShaderName();
                String key = describe(mode) + " " + shader + " " + glOn + " " + off;
                if (told.add(key) && told.size() < 40) {
                    SkyBlocksMod.LOGGER.info("Shadered+: before the HUD - remembered blending {} (the HUD shader's: {}), last shader {}, blending in OpenGL {}{}",
                            describe(mode), left ? describe(hud) : "the same", shader, glOn, off ? " (not set back: test)" : "");
                }
            }
            if (off) {
                return;
            }
            if (left) {
                setLastApplied(hud);
                if (hud.isOpaque()) {
                    RenderSystem.disableBlend();
                    GL11.glDisable(GL11.GL_BLEND);
                } else {
                    // (OpenGL and Minecraft's memory of it agreeing: on, the usual way - as that shader's own)
                    RenderSystem.enableBlend();
                    GL11.glEnable(GL11.GL_BLEND);
                    RenderSystem.defaultBlendFunc();
                    GL14.glBlendEquation(GL14.GL_FUNC_ADD);
                    GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
                }
            }
        } catch (RuntimeException e) {
            if (told.add("error")) {
                SkyBlocksMod.LOGGER.warn("Shadered+: couldn't check the blending before the HUD", e);
            }
        }
    }

    /** The blending remembered before one of Shadered+'s own-blending shaders was used (mixin/ShaderBlendResetMixin). */
    @Nullable
    public static Object before() {
        return lastApplied();
    }

    /** ...and put back after it drew. */
    public static void after(@Nullable Object before) {
        if (!off && before instanceof BlendMode m) {
            setLastApplied(m);
        }
    }

    private static void setLastApplied(BlendMode m) {
        look();
        try {
            if (lastApplied != null) {
                lastApplied.set(null, m);
            }
        } catch (IllegalAccessException ignored) {
            // (left as it is)
        }
    }

    @Nullable
    private static BlendMode blendOf(@Nullable ShaderInstance shader) {
        if (shader == null) {
            return null;
        }
        look();
        try {
            return blendField == null ? null : (BlendMode) blendField.get(shader);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static String describe(@Nullable BlendMode m) {
        return m == null ? "none" : m.isOpaque() ? "off" : "on#" + Integer.toHexString(m.hashCode());
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
        for (Field f : ShaderInstance.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == BlendMode.class) {
                try {
                    f.setAccessible(true);
                    blendField = f;
                } catch (RuntimeException ignored) {
                    // (none)
                }
                break;
            }
        }
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
