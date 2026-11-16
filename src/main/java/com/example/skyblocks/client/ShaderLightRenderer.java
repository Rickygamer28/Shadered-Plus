package com.example.skyblocks.client;

import com.example.skyblocks.ShaderLightBlock;
import com.example.skyblocks.ShaderLightBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The shader lights (ShaderLightBlock, from The Valley Mod by D0nkey100): each one met among the block entities this
 * frame is kept (where it is from the camera, its reach and colour); once the world's drawn (LatePass, first), the
 * nearest eight colour the picture - a copy of it and its depth, and one sheet over the screen with shaders/core/shader_light.
 * With a shader pack on (no late pass) they only give their ordinary light.
 */
public class ShaderLightRenderer implements BlockEntityRenderer<ShaderLightBlockEntity> {
    static ShaderInstance shader;
    private static TextureTarget copy;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "shader_light"), DefaultVertexFormat.POSITION,
                    s -> shader = s);
        }
    }

    /** A light this frame: from the camera (the world's way round), its reach, its colour. */
    private record Light(float x, float y, float z, float reach, int colour) {
        float far() {
            return x * x + y * y + z * z;
        }
    }

    private static final List<Light> LIGHTS = new ArrayList<>();
    private static boolean queued;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class Frames {
        private Frames() {}

        @SubscribeEvent
        public static void onStage(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY && !MirrorRenderer.isRenderingReflection()) {
                LIGHTS.clear();
                queued = false;
            }
        }
    }

    public ShaderLightRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ShaderLightBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null || MirrorRenderer.isRenderingReflection() || com.example.skyblocks.client.iris.IrisCompat.renderingShadowPass()) {
            return;
        }
        Vector3f at = pose.last().pose().transformPosition(0.5F, 0.5F, 0.5F, new Vector3f());
        int reach = be.getBlockState().hasProperty(ShaderLightBlock.LEVEL) ? be.getBlockState().getValue(ShaderLightBlock.LEVEL) : 12;
        if (LIGHTS.size() < 256) {
            LIGHTS.add(new Light(at.x, at.y, at.z, reach, be.colour()));
        }
        if (!queued) {
            queued = LatePass.first(ShaderLightRenderer::drawAll);
        }
    }

    private static void drawAll(MultiBufferSource.BufferSource source) {
        queued = false;
        if (LIGHTS.isEmpty() || shader == null) {
            LIGHTS.clear();
            return;
        }
        LIGHTS.sort(Comparator.comparingDouble(Light::far));
        int count = Math.min(LIGHTS.size(), 8);
        Matrix4f[] places = {new Matrix4f().zero(), new Matrix4f().zero()};
        Matrix4f[] colours = {new Matrix4f().zero(), new Matrix4f().zero()};
        for (int i = 0; i < count; i++) {
            Light l = LIGHTS.get(i);
            places[i / 4].setColumn(i % 4, new Vector4f(l.x(), l.y(), l.z(), l.reach()));
            colours[i / 4].setColumn(i % 4, new Vector4f(((l.colour() >> 16) & 255) / 255.0F, ((l.colour() >> 8) & 255) / 255.0F,
                    (l.colour() & 255) / 255.0F, 1.0F));
        }
        LIGHTS.clear();
        source.endBatch();
        if (!copyScreen()) {
            return;
        }
        Matrix4f inverse = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).invert();
        shader.safeGetUniform("InvViewProj").set(inverse);
        shader.safeGetUniform("LightsA").set(places[0]);
        shader.safeGetUniform("LightsB").set(places[1]);
        shader.safeGetUniform("ColoursA").set(colours[0]);
        shader.safeGetUniform("ColoursB").set(colours[1]);
        shader.safeGetUniform("LightCount").set((float) count);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.setShaderTexture(1, copy.getDepthTextureId());
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(-1, -1, 0);
        b.addVertex(1, -1, 0);
        b.addVertex(1, 1, 0);
        b.addVertex(-1, 1, 0);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** Copies the picture drawn so far and its depth (false if it can't be). */
    private static boolean copyScreen() {
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        int w = Math.max(vp[2], 1), h = Math.max(vp[3], 1);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (copy == null) {
            copy = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
        }
        if (main.isStencilEnabled() && !copy.isStencilEnabled()) {
            copy.enableStencil(); // (the same depth format as the picture's, or its depth can't be copied)
        }
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // (anything earlier's errors let go)
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(vp[0], vp[1], vp[0] + w, vp[1] + h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        boolean ok = GL11.glGetError() == GL11.GL_NO_ERROR;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        return ok;
    }

    @Override
    public boolean shouldRenderOffScreen(ShaderLightBlockEntity be) {
        return true; // (its light reaches past the block)
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public AABB getRenderBoundingBox(ShaderLightBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(15.0);
    }
}
