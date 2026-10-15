package com.example.skyblocks.client;

import com.example.skyblocks.ModFluids;
import com.example.skyblocks.SkyBlocksMod;
import com.noodlegamer76.shadered.client.renderer.SkyblockRenderer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws Shadered's Space sky in Space Liquid: every frame, a sky cube for each liquid block near the camera goes into
 * Shadered's Space queue (like a Space sky block does), cut to the liquid's height, so the liquid is a window into space
 * that flows down in steps. The liquid blocks near the camera are looked up again every few ticks (only chunk sections
 * whose palette can hold the liquid are searched).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SpaceLiquidSky {
    /** How far around the camera, in chunks (at most the render distance). */
    private static final int RADIUS = 6;
    /** At most this many liquid blocks get a sky (the nearest chunks are searched first). */
    private static final int MAX_BLOCKS = 20000;
    private static final int RESCAN_TICKS = 4;

    private static final List<BlockPos> FOUND = new ArrayList<>();
    private static long lastScan = Long.MIN_VALUE;
    private static ClientLevel lastLevel;
    private static int lastChunkX, lastChunkZ;

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        // before Shadered draws its sky cubes (after the block entities)
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        int chunkX = SectionPos.blockToSectionCoord(camera.x), chunkZ = SectionPos.blockToSectionCoord(camera.z);
        long time = level.getGameTime();
        if (level != lastLevel || time - lastScan >= RESCAN_TICKS || time < lastScan
                || chunkX != lastChunkX || chunkZ != lastChunkZ) {
            scan(level, chunkX, chunkZ, Math.min(RADIUS, mc.options.getEffectiveRenderDistance()));
            lastLevel = level;
            lastScan = time;
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
        }
        if (FOUND.isEmpty()) {
            return;
        }
        SkyblockBatchData space = SkyblockRenderer.getData(SkyblockType.SPACE);
        if (space == null) {
            return;
        }
        for (BlockPos pos : FOUND) {
            FluidState fluid = level.getFluidState(pos);
            if (fluid.getFluidType() != ModFluids.SPACE_TYPE.get()) {
                continue; // gone since the last search
            }
            float height = fluid.getHeight(level, pos);
            if (height <= 0.0F) {
                continue;
            }
            // the same camera-relative placement Shadered's sky block renderer gets, squashed to the liquid's height
            Matrix4f pose = new Matrix4f()
                    .translation((float) (pos.getX() - camera.x), (float) (pos.getY() - camera.y), (float) (pos.getZ() - camera.z))
                    .scale(1.0F, height, 1.0F);
            space.add(SkyblockPass.NORMAL, pos, pose, false, 1.0F);
        }
    }

    private static void scan(ClientLevel level, int centerX, int centerZ, int radius) {
        FOUND.clear();
        // nearest rings first, so the cap drops the far ones
        for (int ring = 0; ring <= radius && FOUND.size() < MAX_BLOCKS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunk(centerX + dx, centerZ + dz, false);
                    if (chunk != null) {
                        scanChunk(chunk);
                    }
                }
            }
        }
    }

    private static void scanChunk(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()
                    || !section.maybeHas(state -> state.getFluidState().getFluidType() == ModFluids.SPACE_TYPE.get())) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getFluidState(x, y, z).getFluidType() == ModFluids.SPACE_TYPE.get()) {
                            FOUND.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                            if (FOUND.size() >= MAX_BLOCKS) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private SpaceLiquidSky() {}
}
