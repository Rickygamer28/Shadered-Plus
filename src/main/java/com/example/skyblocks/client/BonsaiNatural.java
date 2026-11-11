package com.example.skyblocks.client;

import com.example.skyblocks.BonsaiBlockEntity;
import com.example.skyblocks.BonsaiTrees;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * A bonsai's natural style: the sapling's real tree (as the server grew it, BonsaiTrees.grow), drawn by Minecraft's own
 * block renderer from its real blocks - any mod's, with their own colours - made once into quads, then shrunk into the
 * pot each frame.
 */
final class BonsaiNatural {
    /** The quads of each layer (8 ints a vertex: position, colour, texture, light, normal) and the tree's size. */
    private final Map<RenderType, int[]> layers = new LinkedHashMap<>();
    final int version;
    float minX, maxX, minZ, maxZ, height;

    private BonsaiNatural(int version) {
        this.version = version;
    }

    private static final Map<BonsaiBlockEntity, BonsaiNatural> BUILT = new WeakHashMap<>(), SHAPED = new WeakHashMap<>();

    static BonsaiNatural of(BonsaiBlockEntity be) {
        BonsaiNatural n = BUILT.get(be);
        if (n == null || n.version != be.naturalVersion) {
            n = build(be.natural, be, be.naturalVersion);
            BUILT.put(be, n);
        }
        return n;
    }

    /** One of the shaped styles, built of the tree's own blocks (key: which shape, of which blocks). */
    static BonsaiNatural shaped(BonsaiBlockEntity be, int key, java.util.function.Supplier<Map<BlockPos, BlockState>> blocks) {
        BonsaiNatural n = SHAPED.get(be);
        if (n == null || n.version != key) {
            n = build(blocks.get(), be, key);
            SHAPED.put(be, n);
        }
        return n;
    }

    private static BonsaiNatural build(Map<BlockPos, BlockState> blocks, BonsaiBlockEntity be, int version) {
        BonsaiNatural mesh = new BonsaiNatural(version);
        if (blocks == null || blocks.isEmpty() || be.getLevel() == null) {
            return mesh;
        }
        mesh.minX = mesh.minZ = Float.MAX_VALUE;
        mesh.maxX = mesh.maxZ = -Float.MAX_VALUE;
        for (BlockPos p : blocks.keySet()) {
            mesh.minX = Math.min(mesh.minX, p.getX());
            mesh.maxX = Math.max(mesh.maxX, p.getX() + 1);
            mesh.minZ = Math.min(mesh.minZ, p.getZ());
            mesh.maxZ = Math.max(mesh.maxZ, p.getZ() + 1);
            mesh.height = Math.max(mesh.height, p.getY() + 1 - BonsaiTrees.GROUND);
        }
        View view = new View(blocks, be.getLevel(), be.getBlockPos());
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        Map<RenderType, Collector> collectors = new LinkedHashMap<>();
        PoseStack pose = new PoseStack();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockPos pos = e.getKey();
            BlockState state = e.getValue();
            if (state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            try {
                BakedModel model = dispatcher.getBlockModel(state);
                long seed = state.getSeed(pos);
                random.setSeed(seed);
                for (RenderType type : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                    Collector c = collectors.computeIfAbsent(type, t -> new Collector());
                    int mark = c.size;
                    pose.pushPose();
                    try {
                        pose.translate(pos.getX(), pos.getY() - BonsaiTrees.GROUND, pos.getZ());
                        random.setSeed(seed);
                        dispatcher.renderBatched(state, pos, view, pose, c, true, random, ModelData.EMPTY, type);
                    } catch (RuntimeException ex) {
                        c.size = mark; // (a block that needs a real world: left out)
                    } finally {
                        pose.popPose();
                    }
                }
            } catch (RuntimeException ignored) {
                // (left out)
            }
        }
        collectors.forEach((type, c) -> {
            if (c.size > 0) {
                mesh.layers.put(type, java.util.Arrays.copyOf(c.data, c.size));
            }
        });
        return mesh;
    }

    /** Draws it with this pose (the tree's ground-level block 0, 0, 0 at the pose's origin), at this light. */
    void render(PoseStack.Pose pose, MultiBufferSource buffers, int light) {
        Matrix4f m = pose.pose();
        for (Map.Entry<RenderType, int[]> e : layers.entrySet()) {
            RenderType type = e.getKey() == RenderType.translucent() ? RenderType.translucentMovingBlock() : e.getKey();
            VertexConsumer vc = buffers.getBuffer(type);
            int[] d = e.getValue();
            for (int i = 0; i < d.length; i += Collector.INTS) {
                int normal = d[i + 7];
                vc.addVertex(m, Float.intBitsToFloat(d[i]), Float.intBitsToFloat(d[i + 1]), Float.intBitsToFloat(d[i + 2])).setColor(d[i + 3])
                        .setUv(Float.intBitsToFloat(d[i + 4]), Float.intBitsToFloat(d[i + 5])).setLight(light)
                        .setNormal(pose, (byte) normal / 127.0F, (byte) (normal >> 8) / 127.0F, (byte) (normal >> 16) / 127.0F);
            }
        }
    }

    boolean empty() {
        return layers.isEmpty();
    }

    /** The tree's blocks, as a world to draw them from: tinted as at the pot (its biome), lit by the sky. */
    private static final class View implements BlockAndTintGetter {
        private final Map<BlockPos, BlockState> blocks;
        private final Level level;
        private final BlockPos pot;

        View(Map<BlockPos, BlockState> blocks, Level level, BlockPos pot) {
            this.blocks = blocks;
            this.level = level;
            this.pot = pot;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 512;
        }

        @Override
        public int getMinBuildHeight() {
            return -128;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            if (!shade) {
                return 1.0F;
            }
            return switch (direction) {
                case DOWN -> 0.5F;
                case UP -> 1.0F;
                case NORTH, SOUTH -> 0.8F;
                case WEST, EAST -> 0.6F;
            };
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return layer == LightLayer.SKY ? 15 : 0;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return Math.max(0, 15 - amount);
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return level.getBlockTint(pot, resolver);
        }
    }

    /** Records the block renderer's vertices. */
    private static final class Collector implements VertexConsumer {
        static final int INTS = 8;
        int[] data = new int[INTS * 1024];
        int size;
        private int current = -1;

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
            data[current + 7] = pack(0, 1, 0);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            if (current >= 0) data[current + 3] = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
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
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (current >= 0) data[current + 6] = (u & 0xFFFF) | (v & 0xFFFF) << 16;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (current >= 0) data[current + 7] = pack(x, y, z);
            return this;
        }

        private static int pack(float x, float y, float z) {
            return (((int) (x * 127.0F)) & 0xFF) | (((int) (y * 127.0F)) & 0xFF) << 8 | (((int) (z * 127.0F)) & 0xFF) << 16;
        }
    }
}
