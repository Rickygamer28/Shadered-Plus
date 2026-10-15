package com.example.skyblocks.client.island;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * The island's blocks as quads, made once by Minecraft's own block and fluid renderers and kept per terrain layer
 * (solid, cutout, translucent...). Every frame they are handed to the normal render buffers, the same way falling
 * blocks are drawn, so see-through blocks are depth-sorted, Fabulous graphics composite them correctly, and shader
 * packs (Iris) treat them like falling blocks.
 */
final class IslandMesh implements AutoCloseable {
    private final Map<RenderType, int[]> layers = new LinkedHashMap<>();
    /** Sky Liquids' surfaces, per sky, drawn with that sky (com.example.skyblocks.client.SkyLiquidRenderer). */
    private final Map<com.noodlegamer76.shadered.client.util.SkyblockType, int[]> skies = new LinkedHashMap<>();

    static IslandMesh build(IslandView island) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        Map<RenderType, QuadCollector> collectors = new LinkedHashMap<>();
        for (RenderType type : RenderType.chunkBufferLayers()) {
            collectors.put(type, new QuadCollector());
        }
        Map<com.noodlegamer76.shadered.client.util.SkyblockType, QuadCollector> skyCollectors = new LinkedHashMap<>();
        PoseStack pose = new PoseStack();
        for (int y = 0; y < island.height; y++) {
            for (int z = 0; z < island.size; z++) {
                for (int x = 0; x < island.size; x++) {
                    BlockState state = island.stateAt(x, y, z);
                    if (state.isAir()) {
                        continue;
                    }
                    for (QuadCollector collector : collectors.values()) {
                        collector.mark();
                    }
                    skyCollectors.values().forEach(QuadCollector::mark);
                    try {
                        BlockPos at = new BlockPos(x, y, z);
                        com.example.skyblocks.ModFluids.SkyLiquid liquid =
                                com.example.skyblocks.client.SkyLiquidRenderer.liquid(state.getFluidState());
                        if (liquid != null) {
                            QuadCollector collector = skyCollectors.computeIfAbsent(liquid.sky, k -> new QuadCollector());
                            collector.mark();
                            com.example.skyblocks.client.SkyLiquidRenderer.drawBlock(island, at, x, y, z, collector);
                            continue;
                        }
                        renderBlock(blocks, island, collectors, pose, random, state, at);
                    } catch (RuntimeException e) {
                        // a (modded) block whose renderer needs a real world: left out of the miniature,
                        // including anything it wrote before failing
                        for (QuadCollector collector : collectors.values()) {
                            collector.rollback();
                        }
                        skyCollectors.values().forEach(QuadCollector::rollback);
                    }
                }
            }
        }
        IslandMesh mesh = new IslandMesh();
        skyCollectors.forEach((sky, collector) -> {
            if (collector.vertexCount() > 0) {
                mesh.skies.put(sky, collector.toArray());
            }
        });
        collectors.forEach((type, collector) -> {
            if (collector.vertexCount() > 0) {
                mesh.layers.put(type, collector.toArray());
            }
        });
        return mesh;
    }

    private static void renderBlock(BlockRenderDispatcher blocks, IslandView island, Map<RenderType, QuadCollector> collectors,
                                    PoseStack pose, RandomSource random, BlockState state, BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            QuadCollector collector = collectors.get(ItemBlockRenderTypes.getRenderLayer(fluid));
            if (collector != null) {
                // the fluid renderer works inside a 16x16x16 section; move its vertices back into place
                blocks.renderLiquid(pos, island,
                        new OffsetVertexConsumer(collector, x - (x & 15), y - (y & 15), z - (z & 15)), state, fluid);
            }
        }
        if (state.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        BakedModel model = blocks.getBlockModel(state);
        long seed = state.getSeed(pos);
        random.setSeed(seed);
        for (RenderType type : model.getRenderTypes(state, random, ModelData.EMPTY)) {
            QuadCollector collector = collectors.get(type);
            if (collector == null) {
                continue;
            }
            pose.pushPose();
            try {
                pose.translate(x, y, z);
                random.setSeed(seed);
                blocks.renderBatched(state, pos, island, pose, collector, true, random, ModelData.EMPTY, type);
            } finally {
                pose.popPose();
            }
        }
    }

    /** Hands the quads to the render buffers; pose = island coordinates -> the block entity's camera-relative pose. */
    /**
     * Solid / cutout layers on the graphics card (uploaded on first use, then only drawn: no per-frame work for the
     * CPU); translucent blocks are still sent each frame so they sort with the rest of the world.
     */
    private final Map<RenderType, com.mojang.blaze3d.vertex.VertexBuffer> uploaded = new LinkedHashMap<>();
    private boolean uploadTried;

    private static boolean opaque(RenderType type) {
        return type != RenderType.translucent() && type != RenderType.tripwire();
    }

    private void upload() {
        uploadTried = true;
        for (Map.Entry<RenderType, int[]> entry : layers.entrySet()) {
            RenderType type = entry.getKey();
            if (!opaque(type)) {
                continue;
            }
            int[] d = entry.getValue();
            try (com.mojang.blaze3d.vertex.ByteBufferBuilder bytes =
                         new com.mojang.blaze3d.vertex.ByteBufferBuilder(d.length / QuadCollector.INTS * type.format().getVertexSize() + 64)) {
                com.mojang.blaze3d.vertex.BufferBuilder builder =
                        new com.mojang.blaze3d.vertex.BufferBuilder(bytes, type.mode(), type.format());
                PoseStack.Pose identity = new PoseStack().last();
                emit(d, identity, builder);
                com.mojang.blaze3d.vertex.MeshData data = builder.build();
                if (data == null) {
                    continue;
                }
                com.mojang.blaze3d.vertex.VertexBuffer vb =
                        new com.mojang.blaze3d.vertex.VertexBuffer(com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(data);
                com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                uploaded.put(type, vb);
            } catch (RuntimeException e) {
                // keep sending this layer each frame instead
            }
        }
    }

    private static void emit(int[] d, PoseStack.Pose pose, VertexConsumer consumer) {
        Matrix4f matrix = pose.pose();
        for (int i = 0; i < d.length; i += QuadCollector.INTS) {
            int normal = d[i + 7];
            consumer.addVertex(matrix, Float.intBitsToFloat(d[i]), Float.intBitsToFloat(d[i + 1]), Float.intBitsToFloat(d[i + 2]))
                    .setColor(d[i + 3])
                    .setUv(Float.intBitsToFloat(d[i + 4]), Float.intBitsToFloat(d[i + 5]))
                    .setLight(d[i + 6])
                    .setNormal(pose, (byte) normal / 127.0F, (byte) (normal >> 8) / 127.0F, (byte) (normal >> 16) / 127.0F);
        }
    }

    /** Draws the uploaded layers now, with the given pose (on top of the current model-view). */
    private void drawUploaded(PoseStack.Pose pose) {
        if (uploaded.isEmpty()) {
            return;
        }
        Matrix4f modelView = new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(pose.pose());
        Matrix4f projection = com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();
        for (Map.Entry<RenderType, com.mojang.blaze3d.vertex.VertexBuffer> entry : uploaded.entrySet()) {
            RenderType type = entry.getKey();
            type.setupRenderState();
            try {
                net.minecraft.client.renderer.ShaderInstance shader = com.mojang.blaze3d.systems.RenderSystem.getShader();
                if (shader != null) {
                    entry.getValue().bind();
                    entry.getValue().drawWithShader(modelView, projection, shader);
                    com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                }
            } finally {
                type.clearRenderState();
            }
        }
    }

    void render(PoseStack.Pose pose, MultiBufferSource buffer) {
        if (!uploadTried) {
            upload();
        }
        drawUploaded(pose);
        Matrix4f matrix = pose.pose();
        for (Map.Entry<RenderType, int[]> entry : layers.entrySet()) {
            if (uploaded.containsKey(entry.getKey())) {
                continue; // on the graphics card, drawn above
            }
            // like falling blocks: translucent goes through the "moving block" type (sorted, Fabulous-aware)
            RenderType type = entry.getKey() == RenderType.translucent() ? RenderType.translucentMovingBlock() : entry.getKey();
            VertexConsumer consumer = buffer.getBuffer(type);
            int[] d = entry.getValue();
            for (int i = 0; i < d.length; i += QuadCollector.INTS) {
                int normal = d[i + 7];
                consumer.addVertex(matrix, Float.intBitsToFloat(d[i]), Float.intBitsToFloat(d[i + 1]), Float.intBitsToFloat(d[i + 2]))
                        .setColor(d[i + 3])
                        .setUv(Float.intBitsToFloat(d[i + 4]), Float.intBitsToFloat(d[i + 5]))
                        .setLight(d[i + 6])
                        .setNormal(pose, (byte) normal / 127.0F, (byte) (normal >> 8) / 127.0F, (byte) (normal >> 16) / 127.0F);
            }
        }
        for (Map.Entry<com.noodlegamer76.shadered.client.util.SkyblockType, int[]> skyEntry : skies.entrySet()) {
            RenderType skyType = com.example.skyblocks.client.SkyLiquidRenderer.skyType(skyEntry.getKey());
            if (skyType == null) {
                continue;
            }
            VertexConsumer consumer = buffer.getBuffer(skyType);
            int[] d = skyEntry.getValue();
            for (int i = 0; i < d.length; i += QuadCollector.INTS) {
                consumer.addVertex(matrix, Float.intBitsToFloat(d[i]), Float.intBitsToFloat(d[i + 1]), Float.intBitsToFloat(d[i + 2]))
                        .setColor(255, 255, 255, 255)
                        .setUv(Float.intBitsToFloat(d[i + 4]), Float.intBitsToFloat(d[i + 5]))
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                        .setNormal(pose, 0.0F, 1.0F, 0.0F);
            }
        }
    }

    @Override
    public void close() {
        layers.clear();
        skies.clear();
        if (!uploaded.isEmpty()) {
            java.util.List<com.mojang.blaze3d.vertex.VertexBuffer> buffers = new java.util.ArrayList<>(uploaded.values());
            uploaded.clear();
            // graphics card memory can only be freed on the render thread
            com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(() -> buffers.forEach(com.mojang.blaze3d.vertex.VertexBuffer::close));
        }
    }

    /** Records vertices (position, colour, texture, light, normal) as they are produced by the block renderers. */
    static final class QuadCollector implements VertexConsumer {
        static final int INTS = 8;
        private int[] data = new int[INTS * 4096];
        private int size;
        private int current = -1;
        private int mark;

        void mark() {
            mark = size;
        }

        void rollback() {
            size = mark;
            current = -1;
        }

        int vertexCount() {
            return size / INTS;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (size + INTS > data.length) {
                data = java.util.Arrays.copyOf(data, data.length * 2);
            }
            current = size;
            size += INTS;
            data[current] = Float.floatToRawIntBits(x);
            data[current + 1] = Float.floatToRawIntBits(y);
            data[current + 2] = Float.floatToRawIntBits(z);
            data[current + 3] = 0xFFFFFFFF;
            data[current + 4] = 0;
            data[current + 5] = 0;
            data[current + 6] = 0x00F000F0;
            data[current + 7] = packNormal(0, 1, 0);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (current >= 0) {
                data[current + 3] = (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | (blue & 0xFF);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (current >= 0) {
                data[current + 4] = Float.floatToRawIntBits(u);
                data[current + 5] = Float.floatToRawIntBits(v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this; // overlay: not part of the terrain vertex format
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (current >= 0) {
                data[current + 6] = (u & 0xFFFF) | (v & 0xFFFF) << 16;
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (current >= 0) {
                data[current + 7] = packNormal(x, y, z);
            }
            return this;
        }

        private static int packNormal(float x, float y, float z) {
            return (((int) (x * 127.0F)) & 0xFF) | (((int) (y * 127.0F)) & 0xFF) << 8 | (((int) (z * 127.0F)) & 0xFF) << 16;
        }
    }
}
