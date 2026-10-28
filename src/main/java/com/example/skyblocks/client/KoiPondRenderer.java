package com.example.skyblocks.client;

import com.example.skyblocks.KoiPondBlock;
import com.example.skyblocks.KoiPondBlockEntity;
import com.example.skyblocks.ModBlocks;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The koi pond's water surface: one quad per block, its texture coordinates the world position across it (so joined
 * ponds are one water, and each block's koi and lily pads are its own), the alpha which sides carry on into another
 * pond. shaders/core/koi_pond draws everything under (and on) the water.
 */
public class KoiPondRenderer implements BlockEntityRenderer<KoiPondBlockEntity> {
    static ShaderInstance shader;

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerKoiPondRenderer(RegisterShadersEvent event) throws java.io.IOException {
            com.example.skyblocks.client.ShaderLoad.register(event, new ResourceLocation(SkyBlocksMod.MOD_ID, "koi_pond"), DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, s -> shader = s);
        }
    }

    private static int pondTiming = -1;

    abstract static class Types extends RenderType {
        static final RenderType WATER = create("skyblocks_koi_pond", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTextureState(new TextureStateShard(KoiPondData.TEXTURE, false, false))
                        .setTexturingState(new TexturingStateShard("skyblocks_koi_pond_time", () -> {
                            pondTiming = WorldTiming.start(WorldTiming.KOI_PONDS);
                            if (shader != null) {
                                shader.safeGetUniform("PondTime").set(LavaLampRenderer.seconds());
                                net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
                                float day = level == null ? 1.0F : (level.getSkyDarken(1.0F) - 0.2F) / 0.8F; // (1 day .. 0 night)
                                shader.safeGetUniform("Daylight").set(Math.max(0.0F, Math.min(1.0F, day)));
                                float pt = net.minecraft.client.Minecraft.getInstance().getFrameTime();
                                net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                                shader.safeGetUniform("Rain").set(level == null ? 0.0F : PondLife.rain(level, BlockPos.containing(cam), pt));
                                net.minecraft.world.phys.Vec3 moon = level == null ? new net.minecraft.world.phys.Vec3(0, -1, 0) : PondLife.moon(level, pt);
                                shader.safeGetUniform("MoonDir").set((float) moon.x, (float) moon.y, (float) moon.z);
                            }
                        }, () -> {
                            WorldTiming.stop(pondTiming);
                            pondTiming = -1;
                        }))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(NO_CULL) // (both sides: from inside it too, standing on the pond)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private final VanillaPondRenderer vanilla;

    public KoiPondRenderer(BlockEntityRendererProvider.Context context) {
        vanilla = new VanillaPondRenderer(context);
    }

    /**
     * A pond (of one kind: the two never join): the rectangle round it (min x, max x, min z, max z), the rectangles it
     * is made of (any shape: the koi each keep to one; min x, max x, min z, max z, the biggest first), how many blocks,
     * the block that draws the koi and visitors for the whole pond (the vanilla one), and its row in KoiPondData's
     * shapes texture (the shader one).
     */
    record Pond(int[] rect, int[][] parts, int blocks, long anchor, int row) {}

    /** Each pond, found once and kept a second (ponds rarely change). */
    private static final java.util.Map<Long, Pond> PONDS = new java.util.HashMap<>();
    /** Each block's neighbours that are pond too, and the stepping stones on it and round it: found with the ponds, kept as long. */
    private static final java.util.Map<Long, Integer> ROUNDS = new java.util.HashMap<>();
    private static long pondsAt;

    /** A block changed on the client: a pond or stepping stone came or went - find the ponds again straight away. */
    static void blockChanged(BlockState oldState, BlockState newState) {
        if (oldState.getBlock() instanceof KoiPondBlock || newState.getBlock() instanceof KoiPondBlock
                || oldState.getBlock() instanceof com.example.skyblocks.SteppingStoneBlock || newState.getBlock() instanceof com.example.skyblocks.SteppingStoneBlock) {
            pondsAt = 0L;
        }
    }

    static Pond pond(net.minecraft.world.level.Level level, BlockPos start, net.minecraft.world.level.block.Block kind) {
        long now = System.nanoTime();
        if (now - pondsAt > 1_000_000_000L) {
            PONDS.clear();
            ROUNDS.clear();
            pondsAt = now;
        }
        Pond known = PONDS.get(start.asLong());
        if (known != null) {
            return known;
        }
        int[] rect = {start.getX(), start.getX(), start.getZ(), start.getZ()};
        if (level == null) {
            return new Pond(rect, new int[][] {rect}, 1, start.asLong(), 0);
        }
        java.util.List<BlockPos> members = new java.util.ArrayList<>();
        java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>(java.util.List.of(start));
        java.util.Set<Long> seen = new java.util.HashSet<>(java.util.List.of(start.asLong()));
        BlockPos anchor = start;
        while (!todo.isEmpty() && members.size() < 4096) {
            BlockPos q = todo.poll();
            members.add(q);
            rect[0] = Math.min(rect[0], q.getX());
            rect[1] = Math.max(rect[1], q.getX());
            rect[2] = Math.min(rect[2], q.getZ());
            rect[3] = Math.max(rect[3], q.getZ());
            if (q.getX() < anchor.getX() || (q.getX() == anchor.getX() && q.getZ() < anchor.getZ())) {
                anchor = q;
            }
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos n = q.relative(d);
                if (seen.add(n.asLong()) && level.getBlockState(n).getBlock() == kind) {
                    todo.add(n);
                }
            }
        }
        // its shape: a few rectangles
        int w = rect[1] - rect[0] + 1, h = rect[3] - rect[2] + 1;
        boolean[][] in = new boolean[h][w];
        for (BlockPos q : members) {
            in[q.getZ() - rect[2]][q.getX() - rect[0]] = true;
        }
        int[][] local = KoiPondData.cover(in);
        int[][] parts = new int[Math.max(local.length, 1)][];
        for (int i = 0; i < local.length; i++) {
            parts[i] = new int[] {rect[0] + local[i][0], rect[0] + local[i][1], rect[2] + local[i][2], rect[2] + local[i][3]};
        }
        if (local.length == 0) {
            parts[0] = rect;
        }
        int row = kind == ModBlocks.KOI_POND.get() ? KoiPondData.row(local, members.size()) : 0;
        Pond pond = new Pond(rect, parts, members.size(), anchor.asLong(), row);
        for (BlockPos q : members) {
            PONDS.put(q.asLong(), pond);
        }
        return pond;
    }

    /** What the game checks to see if a pond is in view: the whole pond, and the air over it the ducks fly in. */
    public net.minecraft.world.phys.AABB getRenderBoundingBox(KoiPondBlockEntity be) {
        Pond pond = pond(be.getLevel(), be.getBlockPos(), be.getBlockState().getBlock());
        int y = be.getBlockPos().getY();
        return new net.minecraft.world.phys.AABB(pond.rect()[0] - 1, y, pond.rect()[2] - 1, pond.rect()[1] + 2, y + 4, pond.rect()[3] + 2);
    }

    @Override
    public boolean shouldRenderOffScreen(KoiPondBlockEntity be) {
        return true; // (its box reaches above the block)
    }

    @Override
    public void render(KoiPondBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (state.getBlock() instanceof KoiPondBlock && !MirrorRenderer.isRenderingReflection()) { // (the main view's job)
            PondLife.saw(pond(be.getLevel(), be.getBlockPos(), state.getBlock()), be.getBlockPos()); // (its dragonflies, fireflies)
        }
        if (state.getBlock() == ModBlocks.VANILLA_KOI_POND.get()) {
            vanilla.render(be, poseStack, buffers, light);
            return;
        }
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
        Pond pond = pond(be.getLevel(), p, state.getBlock());
        int[] rect = pond.rect();
        int packed = Math.min(p.getX() - rect[0], 63) | Math.min(rect[1] - p.getX(), 63) << 6
                | Math.min(p.getZ() - rect[2], 63) << 12 | Math.min(rect[3] - p.getZ(), 63) << 18;
        int cr = packed & 255, cg = (packed >> 8) & 255, cb = (packed >> 16) & 255;
        // and in the second texture coordinates: its shape's row in KoiPondData's texture, and which of the eight
        // blocks round this one are pond too (for the frogs: only on the pond's own lily pads)
        int round = 0, stones = 0;
        Integer known = ROUNDS.get(p.asLong());
        if (known != null) {
            round = known & 255;
            stones = known >>> 8;
        } else if (be.getLevel() != null) {
            if (com.example.skyblocks.SteppingStoneBlock.on(be.getLevel(), p)) {
                stones |= 1; // (stepping stones on this block and those round it: frogs sit on them)
            }
            for (int i = 0; i < 8; i++) {
                int dx = ROUND[i * 2], dz = ROUND[i * 2 + 1];
                if (be.getLevel().getBlockState(p.offset(dx, 0, dz)).getBlock() == state.getBlock()) {
                    round |= 1 << i;
                    if (com.example.skyblocks.SteppingStoneBlock.on(be.getLevel(), p.offset(dx, 0, dz))) {
                        stones |= 2 << i;
                    }
                }
            }
        }
        if (known == null) {
            ROUNDS.put(p.asLong(), round | stones << 8);
        }
        int word = pond.row() | round << 8 | stones << 16;
        int k0 = word & 0xFFFF, k1 = (word >>> 16) & 0xFFFF;
        Matrix4f m = poseStack.last().pose();
        KoiPondData.ensure();
        VertexConsumer vc = buffers.getBuffer(Types.WATER);
        // a box over the water: its top, and its sides where the pond ends (alpha: how high, for the shader)
        v(vc, m, x0, y1, z0, bx, bz, cr, cg, cb, 255, k0, k1);
        v(vc, m, x0, y1, z1, bx, bz, cr, cg, cb, 255, k0, k1);
        v(vc, m, x1, y1, z1, bx, bz, cr, cg, cb, 255, k0, k1);
        v(vc, m, x1, y1, z0, bx, bz, cr, cg, cb, 255, k0, k1);
        // and its bottom, on the water (seen from inside the box, standing on the pond: where the view meets the water)
        v(vc, m, x0, y0, z0, bx, bz, cr, cg, cb, 0, k0, k1);
        v(vc, m, x1, y0, z0, bx, bz, cr, cg, cb, 0, k0, k1);
        v(vc, m, x1, y0, z1, bx, bz, cr, cg, cb, 0, k0, k1);
        v(vc, m, x0, y0, z1, bx, bz, cr, cg, cb, 0, k0, k1);
        if (!n) {
            v(vc, m, x1, y0, z0, bx, bz, cr, cg, cb, 0, k0, k1); v(vc, m, x0, y0, z0, bx, bz, cr, cg, cb, 0, k0, k1);
            v(vc, m, x0, y1, z0, bx, bz, cr, cg, cb, 255, k0, k1); v(vc, m, x1, y1, z0, bx, bz, cr, cg, cb, 255, k0, k1);
        }
        if (!s) {
            v(vc, m, x0, y0, z1, bx, bz, cr, cg, cb, 0, k0, k1); v(vc, m, x1, y0, z1, bx, bz, cr, cg, cb, 0, k0, k1);
            v(vc, m, x1, y1, z1, bx, bz, cr, cg, cb, 255, k0, k1); v(vc, m, x0, y1, z1, bx, bz, cr, cg, cb, 255, k0, k1);
        }
        if (!w) {
            v(vc, m, x0, y0, z0, bx, bz, cr, cg, cb, 0, k0, k1); v(vc, m, x0, y0, z1, bx, bz, cr, cg, cb, 0, k0, k1);
            v(vc, m, x0, y1, z1, bx, bz, cr, cg, cb, 255, k0, k1); v(vc, m, x0, y1, z0, bx, bz, cr, cg, cb, 255, k0, k1);
        }
        if (!e) {
            v(vc, m, x1, y0, z1, bx, bz, cr, cg, cb, 0, k0, k1); v(vc, m, x1, y0, z0, bx, bz, cr, cg, cb, 0, k0, k1);
            v(vc, m, x1, y1, z0, bx, bz, cr, cg, cb, 255, k0, k1); v(vc, m, x1, y1, z1, bx, bz, cr, cg, cb, 255, k0, k1);
        }
    }

    /** The eight blocks round one (x, z): west, east, north, south, then north-west, north-east, south-west, south-east. */
    private static final int[] ROUND = {-1, 0, 1, 0, 0, -1, 0, 1, -1, -1, 1, -1, -1, 1, 1, 1};

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float bx, float bz, int r, int g, int b, int a, int k0, int k1) {
        // (the UV kept a hair inside the block, so the shader always knows which block an edge pixel belongs to)
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(bx + Mth.clamp(x, 0.001F, 0.999F), bz + Mth.clamp(z, 0.001F, 0.999F))
                .uv2(k0, k1).endVertex();
    }
}
