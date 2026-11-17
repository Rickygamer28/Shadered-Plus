package com.example.skyblocks.client;

import com.example.skyblocks.ModFluids;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Draws Aquarium Water (ModFluids.AQUARIUM_WATER) as the aquariums draw their water (shaders/core/aquarium_water):
 * the more water you look through to what's behind it, the deeper its blue (lighter near the top, darker down deep),
 * the tank's moving light-cells on the surface and on the floor under it, by day teal, by night deep blue. Its own
 * chunk-drawn faces are see-through (nothing drawn); the blocks near the camera are looked up every few ticks (only
 * chunk sections that can hold it) and their faces worked out then, each face with how deep it is under the surface
 * and how thick the water is behind it; each frame only places those faces.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class AquariumWaterRenderer {
    private static final int RADIUS = 8;
    private static final int MAX_BLOCKS = 40000;
    private static final int RESCAN_TICKS = 4;
    /** (How far to count water up, down or across, in blocks.) */
    private static final int REACH = 32;

    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerAquariumWaterShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "aquarium_water"),
                    DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    /** Set before each draw: what the shader needs this frame. */
    private static float time, day, inWater, hasDepth;
    private static float camX, camY, camZ;
    private static final Matrix4f invViewProj = new Matrix4f();
    private static int depthTexture;
    private static TextureTarget copy;

    abstract static class Types extends RenderType {
        /** Premultiplied: the water's colour times how much it covers, plus its light, over what's behind. */
        static final TransparencyStateShard PREMULTIPLIED = new TransparencyStateShard("skyblocks_aquarium_water_blend", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });

        static final RenderType WATER = create("skyblocks_aquarium_water", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1 << 16,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_aquarium_water_uniforms", () -> {
                            if (shader == null) {
                                return;
                            }
                            RenderSystem.setShaderTexture(0, depthTexture);
                            shader.safeGetUniform("Time").set(time);
                            shader.safeGetUniform("Day").set(day);
                            shader.safeGetUniform("InWater").set(inWater);
                            shader.safeGetUniform("HasDepth").set(hasDepth);
                            shader.safeGetUniform("CamPos").set(camX, camY, camZ);
                            shader.safeGetUniform("InvViewProj").set(invViewProj);
                            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
                            shader.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
                        }, () -> {}))
                        .setTransparencyState(PREMULTIPLIED)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    // ---- finding the water and working out its faces ----

    private static final List<BlockPos> FOUND = new ArrayList<>();
    private static long lastScan = Long.MIN_VALUE, foundHash, builtHash = 1L, builtChanges = -1L;
    private static ClientLevel lastLevel;
    private static int lastChunkX, lastChunkZ, originX, originY, originZ;
    /** Per vertex: x, y, z (relative to the origin), how deep under the surface, how thick the water behind, then top flag + outward normal. */
    private static float[] geometry = new float[0];
    private static ByteBufferBuilder memory;

    private static boolean isWater(FluidState fluid) {
        return !fluid.isEmpty() && fluid.getFluidType() == ModFluids.AQUARIUM_WATER_TYPE.get();
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        // after the solid world and the creatures in it (what's behind and in the water), before glass and the like
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || shader == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        int chunkX = SectionPos.blockToSectionCoord(camera.x), chunkZ = SectionPos.blockToSectionCoord(camera.z);
        long now = level.getGameTime();
        if (level != lastLevel || now - lastScan >= RESCAN_TICKS || now < lastScan || chunkX != lastChunkX || chunkZ != lastChunkZ) {
            scan(level, chunkX, chunkZ, Math.min(RADIUS, mc.options.getEffectiveRenderDistance()));
            long changes = SkyLiquidRenderer.changes();
            if (level != lastLevel || chunkX != lastChunkX || chunkZ != lastChunkZ || foundHash != builtHash || changes != builtChanges) {
                build(level, chunkX, chunkZ, camera.y);
                builtHash = foundHash;
                builtChanges = changes;
            }
            lastLevel = level;
            lastScan = now;
            lastChunkX = chunkX;
            lastChunkZ = chunkZ;
        }
        if (geometry.length == 0) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        time = (float) (((now % 72000L) + pt) / 20.0);
        day = Math.max(0.0F, Math.min(1.0F, (level.getSkyDarken(pt) - 0.2F) / 0.8F));
        inWater = isWater(level.getFluidState(BlockPos.containing(camera))) ? 1.0F : 0.0F;
        // (the camera's place, kept small for the shader: the light-cells repeat every 1100 blocks)
        camX = (float) (camera.x - Math.floor(camera.x / 1100.0) * 1100.0);
        camY = (float) camera.y;
        camZ = (float) (camera.z - Math.floor(camera.z / 1100.0) * 1100.0);
        float ox = (float) (originX - camera.x), oy = (float) (originY - camera.y), oz = (float) (originZ - camera.z);
        float[] d = geometry;
        boolean defer = com.example.skyblocks.client.iris.IrisIslandTarget.deferSky();
        if (defer) {
            // with a shader pack: drawn at the end of the frame (the pack's last pass would paint over it now), without
            // the world's depth - how thick the water is behind each face stands in for it
            com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(Types.WATER, c -> {
                hasDepth = 0.0F;
                depthTexture = 0;
                invViewProj.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).invert();
                emit(c, d, ox, oy, oz);
            });
            return;
        }
        invViewProj.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).invert();
        hasDepth = copyDepth() ? 1.0F : 0.0F;
        depthTexture = hasDepth > 0.5F ? copy.getDepthTextureId() : 0;
        if (memory == null) {
            memory = new ByteBufferBuilder(1 << 18);
        }
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(memory);
        emit(buffers.getBuffer(Types.WATER), d, ox, oy, oz);
        buffers.endBatch();
    }

    private static void emit(VertexConsumer c, float[] d, float ox, float oy, float oz) {
        for (int i = 0; i < d.length; i += 9) {
            c.addVertex(d[i] + ox, d[i + 1] + oy, d[i + 2] + oz).setUv(d[i + 3], d[i + 4])
                    .setColor((int) d[i + 5], (int) d[i + 6], (int) d[i + 7], (int) d[i + 8]);
        }
    }

    /** The world's depth so far (solid blocks, creatures), for how much water is in front of it. */
    private static boolean copyDepth() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = Math.max(main.width, 1), h = Math.max(main.height, 1);
        if (copy == null) {
            copy = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
        }
        if (main.isStencilEnabled() && !copy.isStencilEnabled()) {
            copy.enableStencil(); // (the same depth format as the main one's, or it can't be copied)
        }
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // (anything earlier's errors let go)
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        boolean ok = GL11.glGetError() == GL11.GL_NO_ERROR;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        return ok;
    }

    private static void scan(ClientLevel level, int centerX, int centerZ, int radius) {
        FOUND.clear();
        foundHash = 17L;
        for (int ring = 0; ring <= radius && FOUND.size() < MAX_BLOCKS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunk(centerX + dx, centerZ + dz, false);
                    if (chunk != null && !scanChunk(chunk)) {
                        return;
                    }
                }
            }
        }
    }

    /** False once the cap is reached. */
    private static boolean scanChunk(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> isWater(state.getFluidState()))) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        FluidState fluid = section.getFluidState(x, y, z);
                        if (isWater(fluid)) {
                            long at = BlockPos.asLong(baseX + x, baseY + y, baseZ + z);
                            foundHash = foundHash * 31L + (at ^ ((long) fluid.getAmount() << 58) ^ (fluid.isSource() ? 1L << 63 : 0L));
                            FOUND.add(BlockPos.of(at));
                            if (FOUND.size() >= MAX_BLOCKS) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private static float[] out = new float[9 * 4096];
    private static int size;

    private static void build(ClientLevel level, int chunkX, int chunkZ, double cameraY) {
        originX = chunkX << 4;
        originZ = chunkZ << 4;
        originY = (int) Math.floor(cameraY);
        size = 0;
        for (BlockPos pos : FOUND) {
            block(level, pos, pos.getX() - originX, pos.getY() - originY, pos.getZ() - originZ);
        }
        geometry = java.util.Arrays.copyOf(out, size);
    }

    /** How many blocks of the water in a row from pos (pos counted) going one way (at most REACH). */
    private static int run(BlockGetter level, BlockPos pos, Direction way) {
        BlockPos.MutableBlockPos p = pos.mutable();
        int n = 0;
        while (n < REACH && isWater(level.getFluidState(p))) {
            n++;
            p.move(way);
        }
        return n;
    }

    /** One block's open faces: its sloped top unless more water is on it, and its sides and bottom against air or see-through blocks. */
    private static void block(BlockGetter level, BlockPos pos, float x, float y, float z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean covered = isWater(level.getFluidState(p.setWithOffset(pos, Direction.UP)));
        float hNW = covered ? 1 : corner(level, pos, -1, -1);
        float hNE = covered ? 1 : corner(level, pos, 1, -1);
        float hSE = covered ? 1 : corner(level, pos, 1, 1);
        float hSW = covered ? 1 : corner(level, pos, -1, 1);
        float above = run(level, pos.above(), Direction.UP); // (how deep this block is under the surface)
        if (!covered) {
            float thick = run(level, pos, Direction.DOWN);
            face(x, y + hNW, z, x + 1, y + hNE, z, x + 1, y + hSE, z + 1, x, y + hSW, z + 1,
                    0, 0, 0, 0, thick, true, 0, 1, 0);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            p.setWithOffset(pos, d);
            if (isWater(level.getFluidState(p)) || level.getBlockState(p).isSolidRender(level, p)) {
                continue;
            }
            float thick = run(level, pos, d.getOpposite());
            // (the top corners at the surface's height there, the bottom ones that much deeper)
            switch (d) {
                case NORTH -> face(x, y, z, x + 1, y, z, x + 1, y + hNE, z, x, y + hNW, z,
                        above + hNW, above + hNE, above, above, thick, false, 0, 0, -1);
                case SOUTH -> face(x + 1, y, z + 1, x, y, z + 1, x, y + hSW, z + 1, x + 1, y + hSE, z + 1,
                        above + hSE, above + hSW, above, above, thick, false, 0, 0, 1);
                case WEST -> face(x, y, z + 1, x, y, z, x, y + hNW, z, x, y + hSW, z + 1,
                        above + hSW, above + hNW, above, above, thick, false, -1, 0, 0);
                default -> face(x + 1, y, z, x + 1, y, z + 1, x + 1, y + hSE, z + 1, x + 1, y + hNE, z,
                        above + hNE, above + hSE, above, above, thick, false, 1, 0, 0);
            }
        }
        p.setWithOffset(pos, Direction.DOWN);
        if (!isWater(level.getFluidState(p)) && !level.getBlockState(p).isSolidRender(level, p)) {
            float deep = above + 1;
            face(x, y, z, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z, deep, deep, deep, deep, run(level, pos, Direction.UP), false, 0, -1, 0);
        }
    }

    /** The surface's height at a corner, like Minecraft's water (averaged over the blocks round it). */
    private static float corner(BlockGetter level, BlockPos pos, int dx, int dz) {
        float total = 0;
        int weight = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 4; i++) {
            p.set(pos.getX() + ((i & 1) != 0 ? dx : 0), pos.getY(), pos.getZ() + ((i & 2) != 0 ? dz : 0));
            if (isWater(level.getFluidState(p.above()))) {
                return 1.0F;
            }
            FluidState fluid = level.getFluidState(p);
            if (isWater(fluid)) {
                float h = fluid.getHeight(level, p);
                if (h >= 0.8F) {
                    total += h * 10.0F;
                    weight += 10;
                } else {
                    total += h;
                    weight++;
                }
            } else if (!level.getBlockState(p).isSolid()) {
                weight++;
            }
        }
        return weight == 0 ? 0.0F : total / weight;
    }

    private static void face(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float u0, float u1, float u2, float u3, float thick, boolean top,
                             int nx, int ny, int nz) {
        vertex(x0, y0, z0, u0, thick, top, nx, ny, nz);
        vertex(x1, y1, z1, u1, thick, top, nx, ny, nz);
        vertex(x2, y2, z2, u2, thick, top, nx, ny, nz);
        vertex(x3, y3, z3, u3, thick, top, nx, ny, nz);
    }

    private static void vertex(float x, float y, float z, float under, float thick, boolean top, int nx, int ny, int nz) {
        if (size + 9 > out.length) {
            out = java.util.Arrays.copyOf(out, out.length * 2);
        }
        out[size++] = x;
        out[size++] = y;
        out[size++] = z;
        out[size++] = under;
        out[size++] = thick;
        out[size++] = top ? 255 : 0;
        out[size++] = 127 + nx * 127;
        out[size++] = 127 + ny * 127;
        out[size++] = 127 + nz * 127;
    }

    private AquariumWaterRenderer() {}
}
