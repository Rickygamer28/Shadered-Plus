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

    static IslandMesh build(IslandView island) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        Map<RenderType, QuadCollector> collectors = new LinkedHashMap<>();
        for (RenderType type : RenderType.chunkBufferLayers()) {
            collectors.put(type, new QuadCollector());
        }
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
                    try {
                        renderBlock(blocks, island, collectors, pose, random, state, new BlockPos(x, y, z));
                    } catch (RuntimeException e) {
                        // a (modded) block whose renderer needs a real world: left out of the miniature,
                        // including anything it wrote before failing
                        for (QuadCollector collector : collectors.values()) {
                            collector.rollback();
                        }
                    }
                }
            }
        }
        IslandMesh mesh = new IslandMesh();
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
    void render(PoseStack.Pose pose, MultiBufferSource buffer) {
        Matrix4f matrix = pose.pose();
        for (Map.Entry<RenderType, int[]> entry : layers.entrySet()) {
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
    }

    @Override
    public void close() {
        layers.clear();
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
