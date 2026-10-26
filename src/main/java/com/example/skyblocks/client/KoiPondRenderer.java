package com.example.skyblocks.client;

import com.example.skyblocks.KoiPondBlock;
import com.example.skyblocks.KoiPondBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The koi pond's water surface: one quad per block, its texture coordinates the world position across it (so joined
 * ponds are one water, and each block's koi and lily pads are its own), the alpha which sides carry on into another
 * pond. shaders/core/koi_pond draws everything under (and on) the water.
 */
public class KoiPondRenderer implements BlockEntityRenderer<KoiPondBlockEntity> {
    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "koi_pond"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType WATER = create("skyblocks_koi_pond", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_koi_pond_time", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("PondTime").set(LavaLampRenderer.seconds());
                                net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
                                float day = level == null ? 1.0F : (level.getSkyDarken(1.0F) - 0.2F) / 0.8F; // (1 day .. 0 night)
                                shader.safeGetUniform("Daylight").set(Math.max(0.0F, Math.min(1.0F, day)));
                            }
                        }, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public KoiPondRenderer(BlockEntityRendererProvider.Context context) {}

    /** Each pond's rectangle (min x, max x, min z, max z), found once and kept a second (ponds rarely change). */
    private static final java.util.Map<Long, int[]> PONDS = new java.util.HashMap<>();
    private static long pondsAt;

    private static int[] pond(net.minecraft.world.level.Level level, BlockPos start) {
        long now = System.nanoTime();
        if (now - pondsAt > 1_000_000_000L) {
            PONDS.clear();
            pondsAt = now;
        }
        int[] known = PONDS.get(start.asLong());
        if (known != null || level == null) {
            return known != null ? known : new int[] {start.getX(), start.getX(), start.getZ(), start.getZ()};
        }
        int[] rect = {start.getX(), start.getX(), start.getZ(), start.getZ()};
        java.util.List<BlockPos> members = new java.util.ArrayList<>();
        java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>(java.util.List.of(start));
        java.util.Set<Long> seen = new java.util.HashSet<>(java.util.List.of(start.asLong()));
        while (!todo.isEmpty() && members.size() < 4096) {
            BlockPos q = todo.poll();
            members.add(q);
            rect[0] = Math.min(rect[0], q.getX());
            rect[1] = Math.max(rect[1], q.getX());
            rect[2] = Math.min(rect[2], q.getZ());
            rect[3] = Math.max(rect[3], q.getZ());
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos n = q.relative(d);
                if (seen.add(n.asLong()) && level.getBlockState(n).getBlock() instanceof KoiPondBlock) {
                    todo.add(n);
                }
            }
        }
        for (BlockPos q : members) {
            PONDS.put(q.asLong(), rect);
        }
        return rect;
    }

    @Override
    public boolean shouldRenderOffScreen(KoiPondBlockEntity be) {
        return true; // (its box reaches above the block)
    }

    @Override
    public void render(KoiPondBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (shader == null || !(state.getBlock() instanceof KoiPondBlock)) {
            return;
        }
        boolean n = state.getValue(KoiPondBlock.NORTH), s = state.getValue(KoiPondBlock.SOUTH);
        boolean e = state.getValue(KoiPondBlock.EAST), w = state.getValue(KoiPondBlock.WEST);
        float x0 = w ? 0.0F : 2.0F / 16.0F, x1 = e ? 1.0F : 14.0F / 16.0F, z0 = n ? 0.0F : 2.0F / 16.0F, z1 = s ? 1.0F : 14.0F / 16.0F;
        float y0 = 14.5F / 16.0F, y1 = 2.5F; // (the water .. well above the rim: ducks flying in and off fit in)
        BlockPos p = be.getBlockPos();
        float bx = Math.floorMod(p.getX(), 512), bz = Math.floorMod(p.getZ(), 512);
        // the whole pond's rectangle, packed into the colour: how far it reaches from here west, east, north, south
        int[] rect = pond(be.getLevel(), p);
        int packed = Math.min(p.getX() - rect[0], 63) | Math.min(rect[1] - p.getX(), 63) << 6
                | Math.min(p.getZ() - rect[2], 63) << 12 | Math.min(rect[3] - p.getZ(), 63) << 18;
        int cr = packed & 255, cg = (packed >> 8) & 255, cb = (packed >> 16) & 255;
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(Types.WATER);
        // a box over the water: its top, and its sides where the pond ends (alpha: how high, for the shader)
        v(vc, m, x0, y1, z0, bx, bz, cr, cg, cb, 255);
        v(vc, m, x0, y1, z1, bx, bz, cr, cg, cb, 255);
        v(vc, m, x1, y1, z1, bx, bz, cr, cg, cb, 255);
        v(vc, m, x1, y1, z0, bx, bz, cr, cg, cb, 255);
        if (!n) {
            v(vc, m, x1, y0, z0, bx, bz, cr, cg, cb, 0); v(vc, m, x0, y0, z0, bx, bz, cr, cg, cb, 0);
            v(vc, m, x0, y1, z0, bx, bz, cr, cg, cb, 255); v(vc, m, x1, y1, z0, bx, bz, cr, cg, cb, 255);
        }
        if (!s) {
            v(vc, m, x0, y0, z1, bx, bz, cr, cg, cb, 0); v(vc, m, x1, y0, z1, bx, bz, cr, cg, cb, 0);
            v(vc, m, x1, y1, z1, bx, bz, cr, cg, cb, 255); v(vc, m, x0, y1, z1, bx, bz, cr, cg, cb, 255);
        }
        if (!w) {
            v(vc, m, x0, y0, z0, bx, bz, cr, cg, cb, 0); v(vc, m, x0, y0, z1, bx, bz, cr, cg, cb, 0);
            v(vc, m, x0, y1, z1, bx, bz, cr, cg, cb, 255); v(vc, m, x0, y1, z0, bx, bz, cr, cg, cb, 255);
        }
        if (!e) {
            v(vc, m, x1, y0, z1, bx, bz, cr, cg, cb, 0); v(vc, m, x1, y0, z0, bx, bz, cr, cg, cb, 0);
            v(vc, m, x1, y1, z0, bx, bz, cr, cg, cb, 255); v(vc, m, x1, y1, z1, bx, bz, cr, cg, cb, 255);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float bx, float bz, int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z).setUv(bx + x, bz + z).setColor(r, g, b, a);
    }
}
