package com.example.skyblocks.client;

import com.example.skyblocks.ModFluids;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noodlegamer76.shadered.client.util.ModRenderTypes;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the Sky Liquids (ModFluids): their surface (smooth slopes like water: corner heights averaged between blocks)
 * and their sides where they are open, with Shadered's render type for their sky, which shows that sky's picture
 * (drawn by Shadered every frame) at each screen pixel: the liquid is a window into its sky, like a sky block. The
 * liquid blocks near the camera are looked up again every few ticks (only chunk sections whose palette can hold one),
 * and their faces worked out then; each frame only places those faces.
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SkyLiquidRenderer {
    /** How far around the camera, in chunks (at most the render distance). */
    private static final int RADIUS = 6;
    /** At most this many liquid blocks are drawn (the nearest chunks are searched first). */
    private static final int MAX_BLOCKS = 20000;
    private static final int RESCAN_TICKS = 4;

    private static final Map<ModFluids.SkyLiquid, List<BlockPos>> FOUND = new LinkedHashMap<>();
    private static int foundCount;
    private static long lastScan = Long.MIN_VALUE;
    private static ClientLevel lastLevel;
    private static int lastChunkX, lastChunkZ;
    private static BufferBuilder memory;

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
            buildGeometry(level, chunkX, chunkZ, camera.y);
            lastLevel = level;
            lastScan = time;
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
        }
        if (GEOMETRY.isEmpty()) {
            return;
        }
        if (memory == null) {
            memory = new BufferBuilder(1 << 18);
        }
        // a liquid's sky that was resting gets its picture drawn first (see SkyUsage)
        for (ModFluids.SkyLiquid liquid : GEOMETRY.keySet()) {
            SkyUsage.ensureFresh(com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(liquid.sky));
        }
        // the faces were worked out when the blocks were looked up (every few ticks): just place them
        float ox = (float) (originX - camera.x), oy = (float) (originY - camera.y), oz = (float) (originZ - camera.z);
        // (1.20.1: the view's rotation is in the stage's pose, not in RenderSystem's model-view matrix)
        Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
        // With a shader pack the pack's final pass would paint over anything drawn into the world now: kept and drawn
        // at the end of the frame instead (client/iris/IrisIslandTarget), depth-tested against the final depth.
        boolean defer = com.example.skyblocks.client.iris.IrisIslandTarget.deferSky();
        MultiBufferSource.BufferSource buffers = defer ? null : MultiBufferSource.immediate(memory);
        for (Map.Entry<ModFluids.SkyLiquid, float[]> entry : GEOMETRY.entrySet()) {
            RenderType sky = skyType(entry.getKey().sky);
            if (sky == null) {
                continue;
            }
            float[] d = entry.getValue();
            if (defer) {
                com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(sky, c -> emit(c, view, d, ox, oy, oz));
            } else {
                emit(buffers.getBuffer(sky), view, d, ox, oy, oz);
            }
        }
        if (buffers != null) {
            buffers.endBatch();
        }
    }

    private static void emit(VertexConsumer c, Matrix4f view, float[] d, float ox, float oy, float oz) {
        for (int i = 0; i < d.length; i += 5) {
            c.vertex(view, d[i] + ox, d[i + 1] + oy, d[i + 2] + oz).color(255, 255, 255, 255).uv(d[i + 3], d[i + 4])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 1.0F, 0.0F).endVertex();
        }
    }

    /** The liquid faces found by the last search, per liquid (x, y, z relative to the origin, u, v per vertex). */
    private static final Map<ModFluids.SkyLiquid, float[]> GEOMETRY = new LinkedHashMap<>();
    private static int originX, originY, originZ;

    /** Works out the faces of the blocks just found, relative to the camera's chunk. */
    private static void buildGeometry(ClientLevel level, int chunkX, int chunkZ, double cameraY) {
        GEOMETRY.clear();
        originX = chunkX << 4;
        originZ = chunkZ << 4;
        originY = (int) Math.floor(cameraY);
        FloatCollector collector = new FloatCollector();
        for (Map.Entry<ModFluids.SkyLiquid, List<BlockPos>> entry : FOUND.entrySet()) {
            collector.size = 0;
            for (BlockPos pos : entry.getValue()) {
                drawBlock(level, pos, pos.getX() - originX, pos.getY() - originY, pos.getZ() - originZ, collector);
            }
            if (collector.size > 0) {
                GEOMETRY.put(entry.getKey(), java.util.Arrays.copyOf(collector.data, collector.size));
            }
        }
    }

    /** Keeps only position and texture of the vertices drawBlock makes. */
    private static final class FloatCollector implements VertexConsumer {
        float[] data = new float[5 * 1024];
        int size;
        private int current = -1;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            if (size + 5 > data.length) {
                data = java.util.Arrays.copyOf(data, data.length * 2);
            }
            current = size;
            data[size++] = (float) x;
            data[size++] = (float) y;
            data[size++] = (float) z;
            data[size++] = 0;
            data[size++] = 0;
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            if (current >= 0) {
                data[current + 3] = u;
                data[current + 4] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    /** Shadered's render type for a sky (null if Shadered doesn't have one for it). */
    @Nullable
    public static RenderType skyType(SkyblockType sky) {
        try {
            return ModRenderTypes.getSkyblockRenderType(sky, SkyblockPass.NORMAL);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The Sky Liquid in this fluid state, or null. */
    @Nullable
    public static ModFluids.SkyLiquid liquid(FluidState fluid) {
        return fluid.isEmpty() ? null : ModFluids.of(fluid.getFluidType());
    }

    /**
     * One Sky Liquid block's faces at (x, y, z): the sloped top (unless more of the same liquid is on top) and the
     * sides and bottom that aren't against more of it or a solid block. Also used for Sky Islands (IslandMesh).
     */
    public static void drawBlock(BlockGetter level, BlockPos pos, float x, float y, float z, VertexConsumer c) {
        ModFluids.SkyLiquid self = liquid(level.getFluidState(pos));
        if (self == null) {
            return;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean covered = liquid(level.getFluidState(p.setWithOffset(pos, Direction.UP))) == self;
        // the surface's height at each corner, worked out like Minecraft's water (smooth slopes between blocks)
        float hNW = covered ? 1 : corner(level, pos, self, -1, -1);
        float hNE = covered ? 1 : corner(level, pos, self, 1, -1);
        float hSE = covered ? 1 : corner(level, pos, self, 1, 1);
        float hSW = covered ? 1 : corner(level, pos, self, -1, 1);
        if (!covered) {
            face(c, x, y + hNW, z, x + 1, y + hNE, z, x + 1, y + hSE, z + 1, x, y + hSW, z + 1);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            p.setWithOffset(pos, d);
            if (liquid(level.getFluidState(p)) == self || level.getBlockState(p).isSolidRender(level, p)) {
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
        if (liquid(level.getFluidState(p)) != self && !level.getBlockState(p).isSolidRender(level, p)) {
            face(c, x, y, z, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z);
        }
    }

    /**
     * The surface height at one corner of a liquid block (dx, dz = which corner, -1 or 1): the average of the (up to)
     * four blocks around that corner, like Minecraft's water: full sources weigh more, open air next to it pulls the
     * corner down, the same liquid on top of any of them makes it full height.
     */
    private static float corner(BlockGetter level, BlockPos pos, ModFluids.SkyLiquid self, int dx, int dz) {
        float total = 0;
        int weight = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 4; i++) {
            p.set(pos.getX() + ((i & 1) != 0 ? dx : 0), pos.getY(), pos.getZ() + ((i & 2) != 0 ? dz : 0));
            if (liquid(level.getFluidState(p.above())) == self) {
                return 1.0F;
            }
            FluidState fluid = level.getFluidState(p);
            if (liquid(fluid) == self) {
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
        c.vertex(x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0.0F, 1.0F, 0.0F).endVertex();
    }

    private static void scan(ClientLevel level, int centerX, int centerZ, int radius) {
        FOUND.clear();
        foundCount = 0;
        // nearest rings first, so the cap drops the far ones
        for (int ring = 0; ring <= radius && foundCount < MAX_BLOCKS; ring++) {
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
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> liquid(state.getFluidState()) != null)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        ModFluids.SkyLiquid liquid = liquid(section.getFluidState(x, y, z));
                        if (liquid != null) {
                            FOUND.computeIfAbsent(liquid, l -> new ArrayList<>()).add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                            if (++foundCount >= MAX_BLOCKS) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private SkyLiquidRenderer() {}
}
