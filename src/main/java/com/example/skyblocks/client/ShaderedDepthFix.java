package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.noodlegamer76.shadered.client.renderer.ComplexPassRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Island Painting needs a stencil buffer on the main screen (ClientSetup), which changes how the screen stores
 * depth. Shadered copies the screen's depth into its own buffers before drawing its sky blocks, and that copy only
 * works between buffers that store depth the same way: without this, it silently failed and Shadered's sky blocks were
 * drawn through walls. So Shadered's buffers get a stencil too (once each; it stays through resizes).
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShaderedDepthFix {
    private static boolean broken;

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (broken || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        try {
            ComplexPassRenderer renderer = ComplexPassRenderer.getInstance();
            match(renderer.getRenderBuffer());
            match(renderer.getWriteBuffer());
            match(renderer.getExtraBuffer());
        } catch (RuntimeException | LinkageError e) {
            broken = true; // a Shadered version without these buffers: nothing to fix
        }
    }

    private static void match(RenderTarget target) {
        if (target != null && !target.isStencilEnabled()) {
            target.enableStencil();
        }
    }

    private ShaderedDepthFix() {}
}
