package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The heaviest blocks (aquariums, infinity cubes) worked out at half size in the normal view - a quarter of the
 * pixels - then put on screen smoothly scaled up. Their faces are drawn at full size for that (half_composite), so
 * their outlines, and anything in front of them, stay sharp; only what's seen inside them is a little softer.
 * <p>
 * The half-size picture gets the world's depth so far (halved), so what's hidden behind blocks isn't worked out. Not
 * with a shader pack (its own buffers), nor in mirrors (their pictures have their own cheaper look).
 */
public final class HalfRes {
    private static TextureTarget target;
    private static ShaderInstance composite;
    private static boolean broken;
    /** Set round the normal view's drawing of the heavy blocks: they may use the half-size picture. */
    static boolean mainView;

    private HalfRes() {}

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerHalfRes(RegisterShadersEvent event) throws IOException {
            // (only the position is read: it's the first part of every format the heavy blocks use)
            com.example.skyblocks.client.ShaderLoad.register(event, new ResourceLocation(SkyBlocksMod.MOD_ID, "half_composite"), DefaultVertexFormat.POSITION, s -> composite = s);
        }
    }

    /**
     * The half-size picture bound to draw into (cleared, with the world's depth so far), if it can be used now;
     * false: draw as usual, into the screen.
     */
    static boolean begin() {
        if (broken || composite == null || !mainView || MirrorRenderer.isRenderingReflection()
                || com.example.skyblocks.client.iris.IrisIslandTarget.active()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int w = Math.max(1, (main.width + 1) / 2), h = Math.max(1, (main.height + 1) / 2);
        try {
            if (target == null) {
                target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
                if (main.isStencilEnabled()) {
                    target.enableStencil(); // (the same depth format as the screen's, for copying it)
                }
                target.setFilterMode(GL11.GL_NEAREST);
            } else if (target.width != w || target.height != h) {
                target.resize(w, h, Minecraft.ON_OSX);
                target.setFilterMode(GL11.GL_NEAREST);
            }
            for (int i = 0; i < 8 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {
                // (earlier errors, not ours)
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                broken = true;
                SkyBlocksMod.LOGGER.info("Shadered+: half-size drawing of aquariums and infinity cubes is off (depth copy failed: {})", error);
                main.bindWrite(true);
                return false;
            }
            target.bindWrite(true);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
            return true;
        } catch (RuntimeException e) {
            broken = true;
            SkyBlocksMod.LOGGER.warn("Shadered+: half-size drawing of aquariums and infinity cubes is off", e);
            main.bindWrite(true);
            return false;
        }
    }

    /**
     * Back to the screen, and the half-size picture put on it over the faces drawn ({@code shapes}: their outlines,
     * drawn with whatever shader is set), as the blocks themselves would have been: in front of the block faces.
     */
    static void finish(List<Runnable> shapes, boolean writeDepth) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        main.bindWrite(true);
        if (shapes.isEmpty()) {
            return;
        }
        int timing = WorldTiming.start(WorldTiming.HALF_RES);
        ShaderInstance shader = composite;
        // (through the game's shader textures: drawing sets every Sampler0..11 from them, so a sampler set straight on
        // the shader was replaced by the block atlas - the tanks showed block textures)
        int before = RenderSystem.getShaderTexture(0);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        shader.safeGetUniform("HalfSize").set((float) target.width, (float) target.height);
        shader.safeGetUniform("MainSize").set((float) main.width, (float) main.height);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(writeDepth);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2.0F, -20.0F); // (where the blocks were drawn: over their own faces)
        try {
            for (Runnable shape : shapes) {
                RenderSystem.setShader(() -> shader);
                shape.run();
            }
        } finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.enableCull();
            RenderSystem.setShaderTexture(0, before);
            WorldTiming.stop(timing);
        }
    }
}
