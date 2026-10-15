package com.example.skyblocks.client;

import com.example.skyblocks.ModFluids;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noodlegamer76.shadered.client.util.ModRenderTypes;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
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

/**
 * Draws Space Liquid: its surface (smooth slopes like water: corner heights averaged between blocks), and its sides
 * where it is open, with Shadered's Space sky render type, which shows Shadered's Space sky picture (drawn every frame)
 * at each screen pixel: the liquid is a window into space, exactly like a Space sky block. The liquid blocks near the
 * camera are looked up again every few ticks (only chunk sections whose palette can hold the liquid are searched).
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
        // after the solid world (so it is hidden behind blocks) and before see-through things like water and glass
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
        RenderType sky = skyType();
        if (sky == null) {
            return;
        }
        if (memory == null) {
            memory = new ByteBufferBuilder(1 << 18);
        }
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(memory);
        VertexConsumer c = buffers.getBuffer(sky);
        for (BlockPos pos : FOUND) {
            FluidState fluid = level.getFluidState(pos);
            if (!ours(fluid)) {
                continue; // gone since the last search
            }
            drawBlock(level, pos, (float) (pos.getX() - camera.x), (float) (pos.getY() - camera.y),
                    (float) (pos.getZ() - camera.z), c);
        }
        buffers.endBatch();
    }

    private static ByteBufferBuilder memory;

    /**
     * One Space Liquid block's faces at (x, y, z): the sloped top (unless more liquid is on top) and the sides and
     * bottom that aren't against more liquid or a solid block. Also used for Sky Islands (client/island/IslandMesh).
     */
    public static void drawBlock(BlockGetter level, BlockPos pos, float x, float y, float z, VertexConsumer c) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean covered = ours(level.getFluidState(p.setWithOffset(pos, Direction.UP)));
        // the surface's height at each corner, worked out like Minecraft's water (smooth slopes between blocks)
        float hNW = covered ? 1 : corner(level, pos, -1, -1);
        float hNE = covered ? 1 : corner(level, pos, 1, -1);
        float hSE = covered ? 1 : corner(level, pos, 1, 1);
        float hSW = covered ? 1 : corner(level, pos, -1, 1);
        if (!covered) {
            face(c, x, y + hNW, z, x + 1, y + hNE, z, x + 1, y + hSE, z + 1, x, y + hSW, z + 1);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            p.setWithOffset(pos, d);
            if (ours(level.getFluidState(p)) || level.getBlockState(p).isSolidRender(level, p)) {
                continue;
            }
            switch (d) {
                case NORTH -> face(c, x, y, z, x + 1, y, z, x + 1, y + hNE, z, x, y + hNW, z);
                case SOUTH -> face(c, x + 1, y, z + 1, x, y, z + 1, x, y + hSW, z + 1, x + 1, y + hSE, z + 1);
                case WEST -> face(c, x, y, z + 1, x, y, z, x, y + hNW, z, x, y + hSW, z + 1);
                default -> face(c, x + 1, y, z, x + 1, y, z + 1, x + 1, y + hSE, z + 1, x + 1, y + hNE, z);
            }
        }
        p.setWithOffset(pos, Direction.DOWN);
        if (!ours(level.getFluidState(p)) && !level.getBlockState(p).isSolidRender(level, p)) {
            face(c, x, y, z, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z);
        }
    }

    /** Shadered's Space sky render type (null if Shadered doesn't have it). */
    @org.jetbrains.annotations.Nullable
    public static RenderType skyType() {
        try {
            return ModRenderTypes.getSkyblockRenderType(SkyblockType.SPACE, SkyblockPass.NORMAL);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean ours(FluidState fluid) {
        return fluid.getFluidType() == ModFluids.SPACE_TYPE.get();
    }

    /**
     * The surface height at one corner of a liquid block (dx, dz = which corner, -1 or 1): the average of the (up to)
     * four blocks around that corner, like Minecraft's water: full sources weigh more, open air next to it pulls the
     * corner down, liquid on top of any of them makes it full height.
     */
    private static float corner(BlockGetter level, BlockPos pos, int dx, int dz) {
        float total = 0;
        int weight = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 4; i++) {
            p.set(pos.getX() + ((i & 1) != 0 ? dx : 0), pos.getY(), pos.getZ() + ((i & 2) != 0 ? dz : 0));
            if (ours(level.getFluidState(p.above()))) {
                return 1.0F;
            }
            FluidState fluid = level.getFluidState(p);
            if (ours(fluid)) {
                float h = fluid.getHeight(level, p);
                if (h >= 0.8F) {
                    total += h * 10.0F;
                    weight += 10;
                } else {
                    total += h;
                    weight++;
                }
            } else if (!level.getBlockState(p).isSolid()) {
                weight++; // air or the like next to it: the corner dips
            }
        }
        return weight == 0 ? 0.0F : total / weight;
    }

    /** One face, drawn from both sides (so it shows from inside the liquid too). */
    private static void face(VertexConsumer c, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vertex(c, x0, y0, z0, 0, 0);
        vertex(c, x1, y1, z1, 1, 0);
        vertex(c, x2, y2, z2, 1, 1);
        vertex(c, x3, y3, z3, 0, 1);
        vertex(c, x3, y3, z3, 0, 1);
        vertex(c, x2, y2, z2, 1, 1);
        vertex(c, x1, y1, z1, 1, 0);
        vertex(c, x0, y0, z0, 0, 0);
    }

    private static void vertex(VertexConsumer c, float x, float y, float z, float u, float v) {
        c.addVertex(x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
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
