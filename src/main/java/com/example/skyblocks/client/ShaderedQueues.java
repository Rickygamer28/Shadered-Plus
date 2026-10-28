package com.example.skyblocks.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.noodlegamer76.shadered.client.renderer.ComplexPassRenderer;
import com.noodlegamer76.shadered.client.renderer.SkyblockRenderer;
import com.noodlegamer76.shadered.client.util.RenderCube;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.Map;

/**
 * Shadered's sky blocks (and anything else using its sky cubes: Illusorite ores, other mods' Shadered blocks) don't
 * draw when their renderer runs: they queue a cube for Shadered's own pass later in the frame. To draw them somewhere
 * else right away (a mirror's picture), note the queues, let the renderers run, take out what they added and draw it.
 */
public final class ShaderedQueues {
    private ShaderedQueues() {}

    /** How many sky cubes each sky has queued so far this frame, per pass. */
    public static Map<SkyblockBatchData, Map<SkyblockPass, Integer>> sizes() {
        Map<SkyblockBatchData, Map<SkyblockPass, Integer>> sizes = new HashMap<>();
        for (SkyblockBatchData data : SkyblockRenderer.DATA_LIST.keySet()) {
            Map<SkyblockPass, Integer> perPass = new HashMap<>();
            for (SkyblockPass pass : SkyblockPass.values()) {
                SkyblockBatchData.PassData passData = data.get(pass);
                perPass.put(pass, passData == null ? 0 : passData.getPositions().size());
            }
            sizes.put(data, perPass);
        }
        return sizes;
    }

    /** Moves the sky cubes queued since {@code before} out of Shadered's queues (sky -> its cubes). */
    public static Map<SkyblockBatchData, SkyblockBatchData> takeSince(Map<SkyblockBatchData, Map<SkyblockPass, Integer>> before) {
        Map<SkyblockBatchData, SkyblockBatchData> taken = new HashMap<>();
        for (SkyblockBatchData data : SkyblockRenderer.DATA_LIST.keySet()) {
            Map<SkyblockPass, Integer> sizes = before.get(data);
            for (SkyblockPass pass : SkyblockPass.values()) {
                SkyblockBatchData.PassData passData = data.get(pass);
                if (passData == null) {
                    continue;
                }
                int from = sizes == null ? 0 : sizes.getOrDefault(pass, 0);
                int to = passData.getPositions().size();
                if (to <= from) {
                    continue;
                }
                SkyblockBatchData mine = taken.computeIfAbsent(data, d -> new SkyblockBatchData());
                for (int i = from; i < to; i++) {
                    mine.add(pass, passData.getPositions().get(i), passData.getPose().get(i),
                            passData.getInverts().get(i), passData.getAlphas().get(i));
                }
                passData.getPositions().subList(from, to).clear();
                passData.getPose().subList(from, to).clear();
                passData.getAlphas().subList(from, to).clear();
                passData.getInverts().subList(from, to).clear();
                passData.getInvertedIndices().removeIf(index -> index >= from);
            }
        }
        return taken;
    }

    /** Draws taken sky cubes now, into whatever is bound, the way Shadered's pass draws them. */
    public static void draw(Map<SkyblockBatchData, SkyblockBatchData> skies) {
        if (skies.isEmpty()) {
            return;
        }
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (com.example.skyblocks.client.MirrorRenderer.isRenderingReflection()) {
            SkyUsage.inMirror(skies); // (only the part the mirror reads, at the next sky stage)
        } else {
            for (SkyblockBatchData data : skies.keySet()) {
                SkyUsage.ensureFresh(data); // a resting sky: draw its picture first
            }
        }
        // (drawing a sky's picture uses Shadered's own framebuffer)
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        int passDepth = ComplexPassRenderer.getInstance().getRenderBuffer().getDepthTextureId();
        for (Map.Entry<SkyblockBatchData, SkyblockBatchData> entry : skies.entrySet()) {
            Integer sky = SkyblockRenderer.DATA_LIST.get(entry.getKey());
            if (sky == null) {
                continue;
            }
            for (SkyblockPass pass : SkyblockPass.values()) {
                SkyblockBatchData.PassData data = entry.getValue().get(pass);
                ShaderInstance shader = pass.getShader();
                if (data == null || shader == null) {
                    continue;
                }
                shader.setSampler("Skybox", sky);
                shader.setSampler("PassDepth", passDepth);
                RenderCube.renderSkyBlocks(data, false, shader);
            }
        }
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
}
