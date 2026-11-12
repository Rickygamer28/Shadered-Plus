package com.example.skyblocks.client;

import com.example.skyblocks.GeodeLampBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Vector3f;

/**
 * The geode lamp's crystals - faceted, glowing from inside, their colour shifting with the angle you see them at, the odd
 * sparkle (shaders/core/geode_crystal) - and the spots of light they throw: wherever a ray from the crystals meets a wall
 * (worked out again now and then), a soft patch of rippling, refracted light in the crystal's colour, bigger and fainter
 * the further it lands (shaders/core/geode_spot, added onto the wall's colours) - and the room round it washed in
 * the crystals' coloured light, fading with distance (every face it can see).
 */
public class GeodeLampRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance crystalShader, spotShader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerGeodeShaders(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "geode_crystal"), DefaultVertexFormat.POSITION_COLOR_NORMAL,
                    s -> crystalShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "geode_spot"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> spotShader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CRYSTALS = create("skyblocks_geode_crystals", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS, 4096,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> crystalShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(CULL)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
        /** The lamp's coloured glow on the blocks round it: added onto their colours, fading with distance. */
        static final RenderType GLOW = create("skyblocks_geode_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192,
                false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(false));
        static final RenderType SPOTS = create("skyblocks_geode_spots", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> spotShader))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** Each crystal kind's colours: amethyst, aqua, rose, citrine; rainbow is every hue round. */
    private static final int[][] COLOURS = {{0xB46CFF, 0xE29BFF, 0x7E4BE0}, {0x4FE3FF, 0x8CFFF0, 0x2D9BFF}, {0xFF6FB0, 0xFFA3C8, 0xE0457E},
            {0xFFC23A, 0xFFE58A, 0xF08A1C}};

    static int colour(int crystal, int i) {
        if (crystal >= COLOURS.length) {
            return Mth.hsvToRgb((i * 0.137F) % 1.0F, 0.6F, 1.0F) & 0xFFFFFF;
        }
        int[] c = COLOURS[crystal];
        return c[Math.floorMod(i, c.length)];
    }

    /** The crystals: hexagonal prisms with pointed tips, in a cluster (each: base x, z, height, radius, lean x, lean z), in pixels. */
    private static final float[][] CLUSTER = {{0, 0, 10, 1.9F, 0, 0}, {-2.6F, 1.2F, 7, 1.5F, -0.35F, 0.12F}, {2.4F, -1.4F, 7.5F, 1.4F, 0.3F, -0.2F},
            {1.6F, 2.6F, 5.5F, 1.2F, 0.18F, 0.38F}, {-1.8F, -2.4F, 6, 1.3F, -0.22F, -0.35F}, {3.4F, 1.6F, 4.2F, 1.0F, 0.45F, 0.2F},
            {-3.6F, -0.8F, 4.5F, 1.0F, -0.48F, -0.1F}, {0.4F, -3.4F, 4, 0.9F, 0.05F, -0.45F}};
    private static final float BED = 3.5F;

    private record Spot(Vec3 at, Vector3f normal, float size, float strength, int colour, int seed) {}

    private static final class Spots {
        long at = -1000;
        int crystal = -1;
        final List<Spot> list = new ArrayList<>();
        /** The glow: each lit face's four corners (x y z, relative to the lamp) and their colours (ARGB, the alpha how strong). */
        final List<float[]> glow = new ArrayList<>();
    }

    private static final Map<SimpleRenderedBlockEntity, Spots> SPOTS = new WeakHashMap<>();
    private static final int RAYS = 56;
    private static final double REACH = 7.5;

    public GeodeLampRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || crystalShader == null || spotShader == null || !(be.getBlockState().getBlock() instanceof GeodeLampBlock)) {
            return;
        }
        int crystal = be.getBlockState().getValue(GeodeLampBlock.CRYSTAL);
        BlockPos pos = be.getBlockPos();
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        // the crystals, turned by where the lamp is (no two alike)
        poseStack.pushPose();
        poseStack.translate(0.5F, BED / 16.0F, 0.5F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(Math.floorMod(pos.hashCode(), 360)));
        poseStack.scale(1 / 16.0F, 1 / 16.0F, 1 / 16.0F);
        crystals(source.getBuffer(Types.CRYSTALS), poseStack.last(), crystal);
        poseStack.popPose();
        source.endBatch(Types.CRYSTALS);
        // the spots of light on the walls round it
        Spots spots = SPOTS.computeIfAbsent(be, b -> new Spots());
        long now = level.getGameTime();
        if (now - spots.at > 40 || now < spots.at - 40 || spots.crystal != crystal) {
            cast(level, pos, crystal, spots);
            spots.at = now + Math.floorMod(pos.hashCode(), 20); // (lamps take turns)
            spots.crystal = crystal;
        }
        if (!spots.glow.isEmpty()) {
            VertexConsumer vc = source.getBuffer(Types.GLOW);
            PoseStack.Pose p = poseStack.last();
            float flicker = 0.92F + 0.08F * Mth.sin((level.getGameTime() + partialTick) * 0.13F + pos.hashCode());
            for (float[] f : spots.glow) {
                for (int c = 0; c < 4; c++) {
                    int argb = Float.floatToRawIntBits(f[12 + c]);
                    int alpha = (int) (((argb >>> 24) & 255) * flicker);
                    vc.addVertex(p, f[c * 3], f[c * 3 + 1], f[c * 3 + 2]).setColor((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, alpha);
                }
            }
            source.endBatch(Types.GLOW);
        }
        if (!spots.list.isEmpty()) {
            VertexConsumer vc = source.getBuffer(Types.SPOTS);
            PoseStack.Pose p = poseStack.last();
            for (Spot s : spots.list) {
                spot(vc, p, pos, s);
            }
            source.endBatch(Types.SPOTS);
        }
    }

    private static void crystals(VertexConsumer vc, PoseStack.Pose p, int crystal) {
        for (int i = 0; i < CLUSTER.length; i++) {
            float[] c = CLUSTER[i];
            int col = colour(crystal, i);
            Vector3f base = new Vector3f(c[0], 0, c[1]);
            Vector3f axis = new Vector3f(c[4], 1, c[5]).normalize();
            Vector3f u = new Vector3f(axis).cross(0, 0, 1).normalize(), v = new Vector3f(axis).cross(u).normalize();
            float h = c[2], r = c[3], body = h * 0.72F;
            Vector3f tip = new Vector3f(axis).mul(h).add(base);
            for (int k = 0; k < 6; k++) {
                float a0 = k * Mth.TWO_PI / 6, a1 = (k + 1) * Mth.TWO_PI / 6;
                Vector3f o0 = new Vector3f(u).mul(Mth.cos(a0) * r).add(new Vector3f(v).mul(Mth.sin(a0) * r));
                Vector3f o1 = new Vector3f(u).mul(Mth.cos(a1) * r).add(new Vector3f(v).mul(Mth.sin(a1) * r));
                Vector3f b0 = new Vector3f(base).add(o0).sub(new Vector3f(axis).mul(1.5F)), b1 = new Vector3f(base).add(o1).sub(new Vector3f(axis).mul(1.5F));
                Vector3f t0 = new Vector3f(base).add(o0).add(new Vector3f(axis).mul(body)), t1 = new Vector3f(base).add(o1).add(new Vector3f(axis).mul(body));
                Vector3f side = new Vector3f(o0).add(o1).normalize();
                quad(vc, p, b0, b1, t1, t0, side, col);
                // the tip's facet, a triangle (as a quad with its last corner twice)
                Vector3f facet = new Vector3f(t1).sub(t0).cross(new Vector3f(tip).sub(t0)).normalize();
                if (facet.dot(side) < 0) {
                    facet.negate();
                }
                quad(vc, p, t0, t1, tip, tip, facet, col);
            }
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f n, int col) {
        // (anticlockwise seen from outside: a, b go round the crystal one way - flipped here if need be)
        Vector3f cross = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        boolean flip = cross.dot(n) < 0;
        Vector3f[] pts = flip ? new Vector3f[] {d, c, b, a} : new Vector3f[] {a, b, c, d};
        for (Vector3f q : pts) {
            vc.addVertex(p, q.x, q.y, q.z).setColor((col >> 16) & 255, (col >> 8) & 255, col & 255, 255).setNormal(p, n.x, n.y, n.z);
        }
    }

    /** Rays out from the crystals: where each meets a block, a spot. */
    /** How far the coloured glow reaches (blocks), and how strong it is right by the lamp. */
    private static final int GLOW_REACH = 6;
    private static final float GLOW_STRENGTH = 0.55F;

    /**
     * The glow: every face of a block round the lamp that faces it and that it can see, lit in the crystals' colour,
     * fading with distance (worked out at each corner, so it fades smoothly across the faces). Rainbow crystals light
     * each way round a different colour.
     */
    private static void glow(Level level, BlockPos pos, int crystal, Spots spots) {
        spots.glow.clear();
        Vec3 lamp = Vec3.atLowerCornerOf(pos).add(0.5, 0.6, 0.5);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(), next = new BlockPos.MutableBlockPos();
        for (int dx = -GLOW_REACH; dx <= GLOW_REACH; dx++) {
            for (int dy = -GLOW_REACH; dy <= GLOW_REACH; dy++) {
                for (int dz = -GLOW_REACH; dz <= GLOW_REACH; dz++) {
                    if (dx * dx + dy * dy + dz * dz > (GLOW_REACH + 1) * (GLOW_REACH + 1) || dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    net.minecraft.world.level.block.state.BlockState st = level.getBlockState(at);
                    if (st.isAir() || !st.isSolidRender(level, at)) {
                        continue;
                    }
                    for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                        next.setWithOffset(at, d);
                        if (level.getBlockState(next).isSolidRender(level, next)) {
                            continue;
                        }
                        // (the face's middle, and whether it faces the lamp, and the lamp can see it)
                        Vec3 mid = Vec3.atCenterOf(at).add(d.getStepX() * 0.5, d.getStepY() * 0.5, d.getStepZ() * 0.5);
                        Vec3 to = lamp.subtract(mid);
                        if (to.x * d.getStepX() + to.y * d.getStepY() + to.z * d.getStepZ() <= 0.05) {
                            continue;
                        }
                        BlockHitResult hit = level.clip(new ClipContext(lamp, mid.add(d.getStepX() * 0.02, d.getStepY() * 0.02, d.getStepZ() * 0.02),
                                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
                        if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(at) && !hit.getBlockPos().equals(pos)) {
                            continue;
                        }
                        spots.glow.add(face(at, d, pos, lamp, crystal));
                    }
                }
            }
        }
    }

    private static float[] face(BlockPos at, net.minecraft.core.Direction d, BlockPos lamp, Vec3 centre, int crystal) {
        // the face's four corners, just off it
        float[][] corners = new float[4][];
        float o = 0.003F;
        float x0 = at.getX(), y0 = at.getY(), z0 = at.getZ();
        switch (d) {
            case UP -> corners = new float[][] {{x0, y0 + 1 + o, z0}, {x0 + 1, y0 + 1 + o, z0}, {x0 + 1, y0 + 1 + o, z0 + 1}, {x0, y0 + 1 + o, z0 + 1}};
            case DOWN -> corners = new float[][] {{x0, y0 - o, z0}, {x0, y0 - o, z0 + 1}, {x0 + 1, y0 - o, z0 + 1}, {x0 + 1, y0 - o, z0}};
            case NORTH -> corners = new float[][] {{x0, y0, z0 - o}, {x0, y0 + 1, z0 - o}, {x0 + 1, y0 + 1, z0 - o}, {x0 + 1, y0, z0 - o}};
            case SOUTH -> corners = new float[][] {{x0, y0, z0 + 1 + o}, {x0 + 1, y0, z0 + 1 + o}, {x0 + 1, y0 + 1, z0 + 1 + o}, {x0, y0 + 1, z0 + 1 + o}};
            case WEST -> corners = new float[][] {{x0 - o, y0, z0}, {x0 - o, y0, z0 + 1}, {x0 - o, y0 + 1, z0 + 1}, {x0 - o, y0 + 1, z0}};
            case EAST -> corners = new float[][] {{x0 + 1 + o, y0, z0}, {x0 + 1 + o, y0 + 1, z0}, {x0 + 1 + o, y0 + 1, z0 + 1}, {x0 + 1 + o, y0, z0 + 1}};
        }
        float[] f = new float[16];
        for (int c = 0; c < 4; c++) {
            float cx = corners[c][0], cy = corners[c][1], cz = corners[c][2];
            double dist = Math.sqrt((cx - centre.x) * (cx - centre.x) + (cy - centre.y) * (cy - centre.y) + (cz - centre.z) * (cz - centre.z));
            float fade = (float) Math.max(0.0, 1.0 - dist / (GLOW_REACH + 0.5));
            // (the light's colour: the crystals', or for rainbow, by which way round the lamp it is)
            int i = crystal >= COLOURS.length ? (int) Math.floor((Math.atan2(cz - centre.z, cx - centre.x) / (Math.PI * 2) + 0.5) * 12) : 0;
            int col = crystal >= COLOURS.length ? Mth.hsvToRgb((i / 12.0F + 0.02F) % 1.0F, 0.75F, 1.0F) & 0xFFFFFF : COLOURS[crystal][0];
            int alpha = Mth.clamp((int) (fade * fade * GLOW_STRENGTH * 255), 0, 255);
            f[c * 3] = cx - lamp.getX();
            f[c * 3 + 1] = cy - lamp.getY();
            f[c * 3 + 2] = cz - lamp.getZ();
            f[12 + c] = Float.intBitsToFloat(alpha << 24 | col);
        }
        return f;
    }

    private static void cast(Level level, BlockPos pos, int crystal, Spots spots) {
        glow(level, pos, crystal, spots);
        spots.list.clear();
        Vec3 from = Vec3.atLowerCornerOf(pos).add(0.5, 0.6, 0.5);
        float golden = Mth.PI * (3 - Mth.sqrt(5));
        for (int i = 0; i < RAYS; i++) {
            float y = 1 - (i + 0.5F) / RAYS * 1.45F; // (mostly up and round: little goes down through the geode)
            float rr = Mth.sqrt(Math.max(0, 1 - y * y)), a = golden * i + Math.floorMod(pos.hashCode(), 7);
            Vec3 dir = new Vec3(Mth.cos(a) * rr, y, Mth.sin(a) * rr);
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(REACH)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().equals(pos)) {
                continue;
            }
            double d = hit.getLocation().distanceTo(from);
            if (d < 0.8) {
                continue;
            }
            Vector3f n = hit.getDirection().step();
            float facing = (float) Math.max(0.25, -dir.dot(new Vec3(n.x, n.y, n.z)));
            float size = (float) Mth.clamp(d * 0.16, 0.22, 0.9);
            float strength = (float) Math.max(0.0, 1.0 - d / REACH) * facing;
            spots.list.add(new Spot(hit.getLocation().add(n.x * 0.004, n.y * 0.004, n.z * 0.004), n, size, strength, colour(crystal, i * 3 + 1), i));
        }
    }

    private static void spot(VertexConsumer vc, PoseStack.Pose p, BlockPos lamp, Spot s) {
        Vector3f n = s.normal;
        Vector3f a = Math.abs(n.y) > 0.5F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f b = new Vector3f(n).cross(a);
        float ox = (float) (s.at.x - lamp.getX()), oy = (float) (s.at.y - lamp.getY()), oz = (float) (s.at.z - lamp.getZ());
        int alpha = Mth.clamp((int) (s.strength * 255), 0, 255);
        float[][] c = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] q : c) {
            float x = ox + (a.x * q[0] + b.x * q[1]) * s.size, y = oy + (a.y * q[0] + b.y * q[1]) * s.size, z = oz + (a.z * q[0] + b.z * q[1]) * s.size;
            // (the spot's corner, and its seed for the shader's own sparkle: u = corner + 4 * seed)
            vc.addVertex(p, x, y, z).setUv(q[0] + 4 * s.seed, q[1]).setColor((s.colour >> 16) & 255, (s.colour >> 8) & 255, s.colour & 255, alpha);
        }
    }

    @Override
    public AABB getRenderBoundingBox(SimpleRenderedBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(REACH);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
