package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * 1.20.1: block entities are given a pose that already holds the camera's turn (and view bobbing), and the
 * model-view is plain; in 1.21.1 the pose is only moved to the block and the model-view turns. The shaders of the
 * blocks ported from 1.21.1 (lava lamps, fires, the jellyfish tank, visualizer, hologram, neon sign, koi pond) work
 * out rays and positions in the world's axes, so they're handed the turn as ViewRot and take it back out.
 * <p>
 * The turn: the world's view while the world is drawn, a mirror's while its picture is, none for items in hand and
 * the GUI.
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ViewState {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Matrix4f LEVEL = new Matrix4f();
    private static boolean inLevel;
    @Nullable
    private static Matrix4f override;

    private ViewState() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onStageFirst(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY && !MirrorRenderer.isRenderingReflection()
                && !com.example.skyblocks.client.iris.IrisCompat.renderingShadowPass()) {
            LEVEL.set(event.getPoseStack().last().pose());
            inLevel = true;
        }
    }

    // (not at AFTER_LEVEL: block entities' see-through batches are drawn later than that; the world's turn holds
    // until the hand or the GUI is drawn, or the next frame starts)
    @SubscribeEvent
    public static void onViewStateHand(net.minecraftforge.client.event.RenderHandEvent event) {
        inLevel = false;
    }

    @SubscribeEvent
    public static void onViewStateGui(net.minecraftforge.client.event.RenderGuiEvent.Pre event) {
        inLevel = false;
    }

    @SubscribeEvent
    public static void onViewStateFrame(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) {
            inLevel = false;
        }
    }

    /** While a mirror's picture is drawn: its view (the turn in the poses it gives). null: back to the world's. */
    static void mirror(@Nullable Matrix4f view) {
        override = view == null ? null : new Matrix4f(view);
    }

    static Matrix4f current() {
        return override != null ? override : inLevel ? LEVEL : IDENTITY;
    }

    /** The shader, with the turn now in the poses as its ViewRot (for a render type's shader state). */
    static ShaderInstance upload(ShaderInstance shader) {
        if (shader != null) {
            shader.safeGetUniform("ViewRot").set(current());
            long now = System.currentTimeMillis();
            if (now - lastLog > 10000) { // (temporary: for checking the 1.20.1 port)
                lastLog = now;
                SkyBlocksMod.LOGGER.info("Shadered+ view check: {} drawn {} (mirror {}), turn used:\n{}model-view:\n{}", shader.getName(),
                        inLevel ? "in the world" : "outside the world", override != null, current(),
                        com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
                int[] vp = new int[4];
                org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, vp);
                java.nio.ByteBuffer mask = org.lwjgl.BufferUtils.createByteBuffer(16);
                org.lwjgl.opengl.GL11.glGetBooleanv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK, mask);
                StackTraceElement[] st = new Throwable().getStackTrace();
                StringBuilder who = new StringBuilder();
                for (int i = 1; i < Math.min(st.length, 22); i++) {
                    who.append("\n    ").append(st[i]);
                }
                SkyBlocksMod.LOGGER.info("Shadered+ view check GL: draw framebuffer {}, main target {}, viewport {}x{} at {},{}, colour mask {}{}{}{}, "
                                + "depth test {}, depth func {}, depth mask {}, blend {}, cull {}, GL error {}\nprojection:\n{}called from:{}",
                        org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                        net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().frameBufferId, vp[2], vp[3], vp[0], vp[1],
                        mask.get(0), mask.get(1), mask.get(2), mask.get(3),
                        org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST), org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC),
                        org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK), org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND),
                        org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE), org.lwjgl.opengl.GL11.glGetError(),
                        com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(), who);
            }
        }
        return shader;
    }

    private static long lastLog;
}
