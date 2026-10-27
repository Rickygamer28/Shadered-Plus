package com.example.skyblocks.client;

import com.example.skyblocks.SkyNames;
import com.example.skyblocks.SkyBlocksMod;
import com.noodlegamer76.shadered.client.renderer.ComplexPassRenderer;
import com.noodlegamer76.shadered.client.renderer.complexpasses.SkyboxRenderPass;
import com.noodlegamer76.shadered.client.util.RenderStage;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import com.noodlegamer76.shadered.client.util.skyblock.SkyboxTranslation;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers our skies with Shadered's renderer, the same way Shadered registers its own
 * (SkyblockRenderer): one SkyblockBatchData + one SkyboxRenderPass per sky.
 *
 * Every frame the skybox pass draws the sky (textures/environment/<name>/front|back|left|right|top|bottom.png)
 * into its own screen-sized target and registers that target in SkyblockRenderer.DATA_LIST. Shadered's own
 * renderers (skyblock, Illusorite ore, Sky Emitter) add blocks of our sky types to this batch data through the
 * mixins, and Shadered's SkyblockRenderPass then draws them with its skyblock shaders (including filter passes).
 */
public final class ShaderedSkies {
    private static final SkyblockBatchData[] DATA = new SkyblockBatchData[SkyNames.NAMES.length];
    private static final SkyboxRenderPass[] PASSES = new SkyboxRenderPass[SkyNames.NAMES.length];
    private static boolean registered;

    static {
        for (int i = 0; i < DATA.length; i++) {
            DATA[i] = new SkyblockBatchData();
            ResourceLocation folder = new ResourceLocation(
                    SkyBlocksMod.MOD_ID, "textures/environment/" + SkyNames.NAMES[i]);
            // Default translation = the face layout tools/build_shadered_faces.py writes.
            SkyboxRenderPass shaderPass = ShaderSky.passFor(SkyNames.NAMES[i], folder, DATA[i]); // (drawn by a shader, no pictures)
            PASSES[i] = shaderPass != null ? shaderPass : new SkyboxRenderPass(folder, DATA[i], new SkyboxTranslation());
        }
    }

    /** Called once from client setup. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ComplexPassRenderer renderer = ComplexPassRenderer.getInstance();
        for (SkyboxRenderPass pass : PASSES) {
            renderer.add(RenderStage.AFTER_SKY, pass);
        }
    }

    public static SkyblockBatchData data(int skyIndex) {
        return DATA[clamp(skyIndex)];
    }

    /** GL id of the sky's screen-sized target, or 0 before it has been drawn once. */
    public static int skyboxTexture(int skyIndex) {
        SkyboxRenderPass pass = PASSES[clamp(skyIndex)];
        return pass.getSkyboxTarget() != null ? pass.getSkyboxTarget().getColorTextureId() : 0;
    }

    public static int count() {
        return PASSES.length;
    }

    private static int clamp(int i) {
        return Math.max(0, Math.min(PASSES.length - 1, i));
    }

    private ShaderedSkies() {}
}
