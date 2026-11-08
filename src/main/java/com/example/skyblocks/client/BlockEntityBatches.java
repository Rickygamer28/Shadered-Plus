package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Block entities draw into the world's shared buffers, each kind's batch drawn when the next kind starts. The last
 * kind drawn is left waiting - and the game only draws it at the very end of the frame, after its view has been put
 * away: so whichever of ours came last (a fish tank, a black hole) turned up in the wrong place, or not at all. Drawn
 * here instead, right after the block entities, with their view still set.
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
public final class BlockEntityBatches {
    private BlockEntityBatches() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockEntitiesDrawn(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES && !MirrorRenderer.isRenderingReflection()) {
            Minecraft.getInstance().renderBuffers().bufferSource().endLastBatch();
        }
    }
}
