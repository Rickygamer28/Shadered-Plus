package com.example.skyblocks.client;

import com.example.skyblocks.KaleidoscopeBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The kaleidoscope's windows: a quad on each side inside the brass frame (the block's model), looked into in
 * shaders/core/kaleidoscope - each drawn at once with how far its glass has turned. Sides against a solid block aren't
 * drawn. Kaleidoscopes side by side join: on each side, the ones next to each other show one big window across them
 * (no frame between; the pattern, its depth and its mirrors the size of all of them), as the lowest of them is set.
 */
public class KaleidoscopeRenderer implements BlockEntityRenderer<KaleidoscopeBlockEntity> {
    static ShaderInstance shader;
    private static float turn, itemMode;
    private static final Matrix4f ITEM_INV = new Matrix4f();
    private static final float W0 = 2.0F / 16.0F, W1 = 14.0F / 16.0F, OUT = 0.002F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerKaleidoscopeShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "kaleidoscope"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    private static void setUp() {
        if (shader != null) {
            MusicReactor.tick();
            shader.safeGetUniform("Turn").set(turn);
            shader.safeGetUniform("Boost").set(Math.min(MusicReactor.bass() * 1.5F, 1.0F));
            shader.safeGetUniform("ItemMode").set(itemMode);
            shader.safeGetUniform("LampInv").set(ITEM_INV);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType WINDOWS = create("skyblocks_kaleidoscope", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_kaleidoscope_turn", KaleidoscopeRenderer::setUp, () -> {}))
                        .setCullState(NO_CULL) // (the shader keeps the side facing you: items' matrices can turn them inside out)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public KaleidoscopeRenderer(BlockEntityRendererProvider.Context context) {}

    // ---- joining ----

    /** One side's joined window: its blocks' span across the side (u, v: the side's own axes), the lowest block. */
    private record Region(int minU, int minV, int maxU, int maxV, BlockPos master, java.util.Set<BlockPos> blocks) {}
    private static final java.util.Map<Long, Region> REGIONS = new java.util.HashMap<>();
    private static long regionsAt;

    /** A block changed: the joined windows are worked out again. */
    public static void blockChanged(net.minecraft.world.level.block.state.BlockState oldState, net.minecraft.world.level.block.state.BlockState newState) {
        if (oldState.getBlock() instanceof com.example.skyblocks.KaleidoscopeBlock || newState.getBlock() instanceof com.example.skyblocks.KaleidoscopeBlock
                || !REGIONS.isEmpty() && oldState.isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                        != newState.isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            REGIONS.clear();
        }
    }

    private static final Direction[][] AXES = {
            {Direction.EAST, Direction.SOUTH}, {Direction.EAST, Direction.SOUTH}, // down, up: u = x, v = z
            {Direction.EAST, Direction.UP}, {Direction.EAST, Direction.UP},       // north, south: u = x, v = y
            {Direction.SOUTH, Direction.UP}, {Direction.SOUTH, Direction.UP}};    // west, east: u = z, v = y

    private static int along(BlockPos p, Direction d) {
        return p.get(d.getAxis());
    }

    private static boolean shows(ClientLevel level, BlockPos p, Direction face) {
        if (!(level.getBlockState(p).getBlock() instanceof com.example.skyblocks.KaleidoscopeBlock)) {
            return false;
        }
        BlockPos n = p.relative(face);
        return !level.getBlockState(n).isSolidRender(level, n);
    }

    private static Region region(ClientLevel level, BlockPos pos, Direction face) {
        long now = level.getGameTime();
        if (now - regionsAt > 40 || now < regionsAt) {
            REGIONS.clear();
            regionsAt = now;
        }
        long key = pos.asLong() * 6 + face.ordinal();
        Region r = REGIONS.get(key);
        if (r != null) {
            return r;
        }
        Direction u = AXES[face.ordinal()][0], v = AXES[face.ordinal()][1];
        java.util.Set<BlockPos> blocks = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        blocks.add(pos.immutable());
        queue.add(pos.immutable());
        int minU = along(pos, u), maxU = minU, minV = along(pos, v), maxV = minV;
        BlockPos master = pos.immutable();
        while (!queue.isEmpty() && blocks.size() < 256) {
            BlockPos p = queue.poll();
            for (Direction d : new Direction[] {u, u.getOpposite(), v, v.getOpposite()}) {
                BlockPos n = p.relative(d);
                int nu = along(n, u), nv = along(n, v);
                if (blocks.contains(n) || Math.abs(nu - along(pos, u)) > 31 || Math.abs(nv - along(pos, v)) > 31 || !shows(level, n, face)) {
                    continue;
                }
                blocks.add(n);
                queue.add(n);
                minU = Math.min(minU, nu);
                maxU = Math.max(maxU, nu);
                minV = Math.min(minV, nv);
                maxV = Math.max(maxV, nv);
                if (n.asLong() < master.asLong()) {
                    master = n;
                }
            }
        }
        r = new Region(minU, minV, maxU, maxV, master, blocks);
        for (BlockPos p : blocks) {
            REGIONS.put(p.asLong() * 6 + face.ordinal(), r);
        }
        return r;
    }

    @Override
    public void render(KaleidoscopeBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null || !(be.getLevel() instanceof ClientLevel level)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        itemMode = 0.0F;
        for (Direction face : Direction.values()) {
            if (!shows(level, pos, face)) {
                continue;
            }
            Region r = region(level, pos, face);
            KaleidoscopeBlockEntity master = level.getBlockEntity(r.master()) instanceof KaleidoscopeBlockEntity m ? m : be;
            Direction u = AXES[face.ordinal()][0], v = AXES[face.ordinal()][1];
            int pu = along(pos, u), pv = along(pos, v);
            int joined = (r.blocks().contains(pos.relative(u.getOpposite())) ? 1 : 0) | (r.blocks().contains(pos.relative(u)) ? 2 : 0)
                    | (r.blocks().contains(pos.relative(v.getOpposite())) ? 4 : 0) | (r.blocks().contains(pos.relative(v)) ? 8 : 0);
            // (where the joined window's middle is from this block's, in half blocks; its size in blocks)
            int offU = (r.minU() + r.maxU() + 1) - (2 * pu + 1), offV = (r.minV() + r.maxV() + 1) - (2 * pv + 1);
            int[] regionCode = {128 + offU, 128 + offV, r.maxU() - r.minU() + 1, r.maxV() - r.minV() + 1};
            BlockPos mp = r.master();
            int seed = (int) (Math.floorMod(BlockPos.asLong(mp.getX(), mp.getY(), mp.getZ()) * 0x9E3779B97F4A7C15L >>> 40, 64L));
            turn = (float) (master.turn(level.getGameTime(), partialTick) % 100000.0);
            window(source.getBuffer(Types.WINDOWS), poseStack.last().pose(), face.ordinal(), joined, regionCode, master.symmetry(), master.palette(), seed);
            source.endBatch(Types.WINDOWS);
        }
    }

    /** The kaleidoscope as an item: all six windows, with this item's matrix. */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers) {
        if (shader == null) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert();
        Minecraft mc = Minecraft.getInstance();
        turn = mc.level == null ? 0.0F : (mc.level.getGameTime() % 72000L) / 20.0F;
        itemMode = 1.0F;
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        VertexConsumer vc = source.getBuffer(Types.WINDOWS);
        for (int face = 0; face < 6; face++) {
            window(vc, m, face, 0, new int[] {128, 128, 1, 1}, 3, 0, 7);
        }
        source.endBatch(Types.WINDOWS);
        itemMode = 0.0F;
    }

    /**
     * One side's window (the same places as the shader's vertex stage works out): to the block's edge where it joins
     * the next one ({@code joined}: 1 before it across, 2 after, 4 below, 8 above), and the joined window it's part of.
     */
    private static void window(VertexConsumer vc, Matrix4f m, int face, int joined, int[] region, int symmetry, int palette, int seed) {
        int settings = symmetry + 8 * palette + 64 * seed;
        {
            for (int corner = 0; corner < 4; corner++) {
                boolean hiU = corner == 1 || corner == 2, hiV = corner >= 2;
                float cx = hiU ? ((joined & 2) != 0 ? 1.0F : W1) : ((joined & 1) != 0 ? 0.0F : W0);
                float cy = hiV ? ((joined & 8) != 0 ? 1.0F : W1) : ((joined & 4) != 0 ? 0.0F : W0);
                float x, y, z;
                switch (face) {
                    case 0 -> { x = cx; y = -OUT; z = cy; }
                    case 1 -> { x = cx; y = 1.0F + OUT; z = cy; }
                    case 2 -> { x = cx; y = cy; z = -OUT; }
                    case 3 -> { x = cx; y = cy; z = 1.0F + OUT; }
                    case 4 -> { x = -OUT; y = cy; z = cx; }
                    default -> { x = 1.0F + OUT; y = cy; z = cx; }
                }
                vc.addVertex(m, x, y, z).setUv(joined * 24 + face * 4 + corner, settings).setColor(region[0], region[1], region[2], region[3]);
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
