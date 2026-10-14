package com.example.skyblocks.client;

import com.example.skyblocks.BigOiiaCatBlock;
import com.example.skyblocks.BigOiiaCatBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderHighlightEvent;

/** Looking at any block of a big cat outlines the whole cat (instead of just that block). */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BigOiiaCatOutline {

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos pos = event.getTarget().getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BigOiiaCatBlock)) {
            return;
        }
        BlockPos master = BigOiiaCatBlock.findMaster(level, pos, state);
        if (master == null || !(level.getBlockEntity(master) instanceof BigOiiaCatBlockEntity be)
                || be.size() < BigOiiaCatBlock.MIN_SIZE) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        AABB box = BigOiiaCatBlock.catBox(master, be.size()).move(-cam.x, -cam.y, -cam.z);
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(event.getPoseStack(), lines, box, 0.0F, 0.0F, 0.0F, 0.4F);
        event.setCanceled(true);
    }

    private BigOiiaCatOutline() {}
}
