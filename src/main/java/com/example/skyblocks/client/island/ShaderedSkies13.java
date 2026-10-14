package com.example.skyblocks.client.island;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.renderer.ComplexPassRenderer;
import com.noodlegamer76.shadered.client.renderer.SkyblockRenderer;
import com.noodlegamer76.shadered.client.util.RenderableComplexPass;
import com.noodlegamer76.shadered.client.renderer.complexpasses.SkyblockRenderPass;
import com.noodlegamer76.shadered.client.util.skyblock.SkyBoxRenderer;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import com.noodlegamer76.shadered.client.util.skyblock.SkyboxTranslation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Shadered's skies for the Island Painting's window, on Shadered 1.3.7 - 1.4.2, which keep them in two different ways:
 * <ul>
 *   <li>1.4.0 - 1.4.2: SkyblockRenderer.DATA_LIST maps each sky's queue to its picture (a texture drawn earlier in the
 *       frame), like newer Shadered.</li>
 *   <li>1.3.7: each sky has its own SkyblockRenderPass that draws its picture only when it draws its blocks; here the
 *       picture is drawn the same way (same textures, layout, rotation and shader, read from the pass) into our own
 *       target when the painting needs it.</li>
 * </ul>
 * Looked up by reflection, so one jar works with all of them. End, End Sky and Mimic blocks (special passes) aren't
 * supported and are left out of paintings.
 */
final class ShaderedSkies13 {
    private static boolean looked;
    @Nullable
    private static Map<SkyblockBatchData, Integer> dataList;
    /** 1.3.7: the passes of the plain skies, by their queue. */
    private static final Map<SkyblockBatchData, SkyblockRenderPass> PASSES = new HashMap<>();
    private static Field batchField, speedField, shaderField;
    @Nullable
    private static TextureTarget target;
    /** 1.3.7: pictures drawn this frame (frame key -> queue -> texture). */
    private static final Map<SkyblockBatchData, Integer> drawn = new HashMap<>();
    private static long drawnFrame = Long.MIN_VALUE;

    @SuppressWarnings("unchecked")
    private static void look() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            Field f = SkyblockRenderer.class.getField("DATA_LIST");
            dataList = (Map<SkyblockBatchData, Integer>) f.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            dataList = null;
        }
        if (dataList == null) {
            try {
                batchField = SkyblockRenderPass.class.getDeclaredField("batchData");
                batchField.setAccessible(true);
                speedField = SkyblockRenderPass.class.getDeclaredField("skyboxRotationSpeed");
                speedField.setAccessible(true);
                shaderField = SkyblockRenderPass.class.getDeclaredField("skyboxShader");
                shaderField.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                batchField = null;
            }
        }
    }

    /** Shadered's sky queues (where its sky block renderers put their cubes). */
    static List<SkyblockBatchData> queues() {
        look();
        if (dataList != null) {
            return new ArrayList<>(dataList.keySet());
        }
        List<SkyblockBatchData> list = new ArrayList<>();
        for (String name : new String[] {"spaceData", "stormyData", "oceanData", "endData", "endSkyData", "eclipseData",
                "iridiaData", "forestData", "lightData", "mimicData"}) {
            SkyblockBatchData data = queue(name);
            if (data != null) {
                list.add(data);
            }
        }
        return list;
    }

    /** One of SkyblockRenderer's sky queues by field name ("spaceData"...), or null. */
    @Nullable
    static SkyblockBatchData queue(String field) {
        try {
            Object value = SkyblockRenderer.class.getField(field).get(null);
            return value instanceof SkyblockBatchData data ? data : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The picture (texture id) of the sky whose queue this is, ready to sample now; null if it can't be drawn. */
    @Nullable
    static Integer picture(SkyblockBatchData queue) {
        look();
        if (dataList != null) {
            return dataList.get(queue);
        }
        if (batchField == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        long frame = mc.level == null ? 0 : mc.level.getGameTime() * 1000 + (long) (mc.getFrameTime() * 999);
        if (frame != drawnFrame) {
            drawnFrame = frame;
            drawn.clear();
        }
        Integer cached = drawn.get(queue);
        if (cached != null) {
            return cached;
        }
        SkyblockRenderPass pass = pass(queue);
        if (pass == null) {
            return null;
        }
        Integer texture = drawPicture(pass, queue);
        if (texture != null) {
            drawn.put(queue, texture);
        }
        return texture;
    }

    @Nullable
    private static SkyblockRenderPass pass(SkyblockBatchData queue) {
        SkyblockRenderPass pass = PASSES.get(queue);
        if (pass != null) {
            return pass;
        }
        try {
            for (List<RenderableComplexPass> passes : ComplexPassRenderer.getInstance().getComplexPass().values()) {
                for (RenderableComplexPass p : passes) {
                    if (p instanceof SkyblockRenderPass sky && batchField.get(sky) == queue) {
                        PASSES.put(queue, sky);
                        return sky;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
        return null;
    }

    /** Draws the sky's picture the way its pass does, into our own target (one target per sky drawn this frame). */
    @Nullable
    @SuppressWarnings("unchecked")
    private static Integer drawPicture(SkyblockRenderPass pass, SkyblockBatchData queue) {
        Minecraft mc = Minecraft.getInstance();
        // Shader mods (Oculus / Iris) draw the world into their own framebuffer, not the main one: remember what was
        // bound, and put exactly that back afterwards.
        int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try {
            ResourceLocation folder = pass.getTexturePath();
            SkyboxTranslation translation = pass.getTranslation();
            Vector3f speed = (Vector3f) speedField.get(pass);
            ShaderInstance shader = ((Supplier<ShaderInstance>) shaderField.get(pass)).get();
            if (shader == null || !shader.getName().startsWith("shadered")) {
                // Minecraft's own sky texture shader: while the world is drawn, shader mods hand out their replacement
                // from GameRenderer's getters, so take the original by name
                ShaderInstance plain = mc.gameRenderer.getShader("position_tex_color");
                if (plain != null) {
                    shader = plain;
                }
            }
            if (folder == null || shader == null) {
                return null;
            }
            final ShaderInstance skyShader = shader;
            TextureTarget into = target(queue);
            into.setClearColor(0, 0, 0, 0);
            into.clear(Minecraft.ON_OSX);
            into.bindWrite(true);

            Camera camera = mc.gameRenderer.getMainCamera();
            PoseStack pose = new PoseStack();
            pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            pose.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
            float ticks = (mc.level == null ? 0 : mc.level.getGameTime() % 2_000_000L) + mc.getFrameTime();
            if (speed != null && speed.lengthSquared() > 0) {
                Quaternionf rotation = new Quaternionf();
                rotation.mul(Axis.XP.rotationDegrees(ticks * speed.x));
                rotation.mul(Axis.YN.rotationDegrees(ticks * speed.y));
                rotation.mul(Axis.ZP.rotationDegrees(ticks * speed.z));
                pose.mulPose(rotation);
            }
            RenderSystem.setShader(() -> skyShader);
            SkyBoxRenderer.renderBlockSkybox(pose, folder, skyShader, translation);
            return into.getColorTextureId();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        } finally {
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static final Map<SkyblockBatchData, TextureTarget> TARGETS = new HashMap<>();

    private static TextureTarget target(SkyblockBatchData queue) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        TextureTarget t = TARGETS.get(queue);
        if (t == null) {
            t = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            TARGETS.put(queue, t);
        } else if (t.width != w || t.height != h) {
            t.resize(w, h, Minecraft.ON_OSX);
        }
        return t;
    }

    /** Depth texture Shadered's sky shaders compare against ("PassDepth"). */
    static int passDepth() {
        try {
            TextureTarget buffer = ComplexPassRenderer.getInstance().getRenderBuffer();
            if (buffer != null) {
                return buffer.getDepthTextureId();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return Minecraft.getInstance().getMainRenderTarget().getDepthTextureId();
    }

    private ShaderedSkies13() {}
}
