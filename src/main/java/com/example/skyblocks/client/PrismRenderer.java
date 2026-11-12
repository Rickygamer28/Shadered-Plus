package com.example.skyblocks.client;

import com.example.skyblocks.PrismBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The prism: its glass (a triangle-ended bar, apex up, on the stand of its model), and what light does going through
 * it - comes out the far side bent down towards its base and spread into a fan, red bent least and violet most, striped
 * across whatever it lands on. The light: a spotlight's beam shone on it (SpotlightRenderer tells it), else the sun -
 * by day, not in rain, if the sun can reach it (through glass too).
 */
public class PrismRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    private static final ResourceLocation GLASS = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/block/prism_glass.png");

    abstract static class Types extends RenderType {
        /** The rainbow: light added onto what it falls on (and, faintly, the air it goes through). */
        static final RenderType RAINBOW = create("skyblocks_prism_rainbow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, true,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** Spotlight beams shone on prisms lately: where it came from (its direction) and its colour, and when. */
    private record Beam(Vec3 dir, int colour, long at) {}

    private static final Map<BlockPos, Beam> BEAMS = new HashMap<>();

    /** A spotlight's beam has hit the prism at pos (its direction, its colour). */
    public static void shone(BlockPos pos, Vec3 dir, int colour, long gameTime) {
        int c = colour & 0xFFFFFF;
        if ((c >> 16 & 255) > 200 && (c >> 8 & 255) > 200 && (c & 255) > 200) {
            c = -1; // (white light: a plain rainbow)
        }
        BEAMS.put(pos.immutable(), new Beam(dir.normalize(), c, gameTime));
        if (BEAMS.size() > 64) {
            BEAMS.entrySet().removeIf(e -> gameTime - e.getValue().at() > 40);
        }
    }

    /** The rainbow's lines worked out (cached a moment: they only change as the light does). */
    private static final class Fan {
        long at = -1000;
        Vec3 dir;
        float strength;
        int tint = -1;
        final List<float[]> air = new ArrayList<>(), land = new ArrayList<>();
    }

    private static final Map<SimpleRenderedBlockEntity, Fan> FANS = new WeakHashMap<>();
    private static final int RAYS = 28, REACH = 14;
    /** How far the light's bent (radians), the fan's spread, and how wide its stripe is (blocks). */
    private static final float BEND = 0.5F, SPREAD = 0.32F, WIDTH = 0.42F;

    public PrismRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(PrismBlock.AXIS)) {
            return;
        }
        boolean alongX = state.getValue(PrismBlock.AXIS) == Direction.Axis.X;
        glass(poseStack, buffers, light, overlay, alongX);

        // the light going in
        BlockPos pos = be.getBlockPos();
        long now = level.getGameTime();
        Vec3 dir = null;
        float strength = 0;
        int tint = -1;
        Beam beam = BEAMS.get(pos);
        if (beam != null && now - beam.at() <= 3) {
            dir = beam.dir();
            strength = 0.75F;
            tint = beam.colour();
        } else if (sunlit(level, pos, partialTick)) {
            dir = sunDirection(level, partialTick).scale(-1); // (from the sun, down towards the prism)
            strength = 0.55F * (1.0F - level.getRainLevel(partialTick));
        }
        if (dir == null || strength <= 0.02F) {
            return;
        }
        Fan fan = FANS.computeIfAbsent(be, k -> new Fan());
        if (now - fan.at > 10 || now < fan.at || fan.dir == null || fan.dir.distanceToSqr(dir) > 0.0004 || fan.tint != tint) {
            cast(level, pos, dir, alongX, fan);
            fan.at = now;
            fan.dir = dir;
            fan.tint = tint;
        }
        fan.strength = strength;
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        VertexConsumer vc = source.getBuffer(Types.RAINBOW);
        Matrix4f m = poseStack.last().pose();
        float shimmer = 0.93F + 0.07F * Mth.sin((now + partialTick) * 0.13F);
        for (float[] q : fan.air) {
            emit(vc, m, q, strength * 0.09F * shimmer, tint);
        }
        for (float[] q : fan.land) {
            emit(vc, m, q, strength * shimmer, tint);
        }
        source.endBatch(Types.RAINBOW);
    }

    /** A quad: 4 corners (x y z, from the block), their hues (0 red .. 1 violet), how strong at each corner. */
    private static void emit(VertexConsumer vc, Matrix4f m, float[] q, float strength, int tint) {
        for (int i = 0; i < 4; i++) {
            int rgb = hue(q[12 + i]);
            float r = (rgb >> 16 & 255) / 255.0F, g = (rgb >> 8 & 255) / 255.0F, b = (rgb & 255) / 255.0F;
            if (tint != -1) {
                // (a coloured beam: its rainbow leans to its colour)
                r = r * 0.55F + ((tint >> 16) & 255) / 255.0F * 0.45F;
                g = g * 0.55F + ((tint >> 8) & 255) / 255.0F * 0.45F;
                b = b * 0.55F + (tint & 255) / 255.0F * 0.45F;
            }
            float a = Mth.clamp(strength * q[16 + i], 0.0F, 1.0F);
            vc.addVertex(m, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(r, g, b, a);
        }
    }

    /** Red (0) .. orange, yellow, green, blue .. violet (1). */
    private static int hue(float t) {
        float[][] stops = {{1.0F, 0.18F, 0.15F}, {1.0F, 0.55F, 0.1F}, {1.0F, 0.95F, 0.2F}, {0.25F, 0.95F, 0.3F}, {0.2F, 0.55F, 1.0F}, {0.45F, 0.25F, 1.0F},
                {0.72F, 0.25F, 0.95F}};
        float f = Mth.clamp(t, 0.0F, 1.0F) * (stops.length - 1);
        int i = Math.min((int) f, stops.length - 2);
        float k = f - i;
        int r = (int) (Mth.lerp(k, stops[i][0], stops[i + 1][0]) * 255), g = (int) (Mth.lerp(k, stops[i][1], stops[i + 1][1]) * 255),
                b = (int) (Mth.lerp(k, stops[i][2], stops[i + 1][2]) * 255);
        return r << 16 | g << 8 | b;
    }

    /** Where the sun is (a unit vector, towards it). */
    private static Vec3 sunDirection(Level level, float partialTick) {
        float a = level.getSunAngle(partialTick);
        return new Vec3(-Math.sin(a), Math.cos(a), 0).normalize();
    }

    /** Whether the sun's on the prism: day, up, and nothing that stops light between them (glass doesn't). */
    private static boolean sunlit(Level level, BlockPos pos, float partialTick) {
        if (!level.dimensionType().hasSkyLight() || level.isRaining() && level.getRainLevel(partialTick) > 0.6F) {
            return false;
        }
        Vec3 sun = sunDirection(level, partialTick);
        if (sun.y < 0.08) {
            return false;
        }
        Vec3 from = Vec3.atCenterOf(pos).add(0, 0.05, 0);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockPos last = pos;
        for (float d = 0.6F; d < 48.0F; d += 0.4F) {
            Vec3 p = from.add(sun.scale(d));
            at.set(p.x, p.y, p.z);
            if (at.equals(last)) {
                continue;
            }
            last = at.immutable();
            if (!level.isLoaded(at) || at.getY() >= level.getMaxBuildHeight()) {
                return true;
            }
            BlockState st = level.getBlockState(at);
            if (!st.isAir() && st.getLightBlock(level, at) >= 15) {
                return false;
            }
        }
        return true;
    }

    /** The fan's rays from the prism (bent and spread round its long edge), to where each lands; quads between them. */
    private static void cast(Level level, BlockPos pos, Vec3 in, boolean alongX, Fan fan) {
        fan.air.clear();
        fan.land.clear();
        Vec3 axis = alongX ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        // (light along the prism's edge doesn't go through its faces: the part across it does)
        Vec3 across = in.subtract(axis.scale(in.dot(axis)));
        if (across.lengthSqr() < 0.05) {
            return;
        }
        // bent towards its base (down): whichever way round the edge takes it lower
        Vec3 plus = rotate(in, axis, BEND), minus = rotate(in, axis, -BEND);
        float sign = plus.y < minus.y ? 1.0F : -1.0F;
        Vec3 centre = Vec3.atLowerCornerOf(pos).add(0.5, 5.5 / 16, 0.5);
        Vec3[] hits = new Vec3[RAYS];
        Direction[] faces = new Direction[RAYS];
        Vec3[] dirs = new Vec3[RAYS];
        for (int i = 0; i < RAYS; i++) {
            float t = i / (float) (RAYS - 1);
            Vec3 d = rotate(in, axis, sign * (BEND + SPREAD * (t - 0.5F)));
            dirs[i] = d;
            Vec3 from = centre.add(d.scale(0.3));
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(d.scale(REACH)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(pos)) {
                hits[i] = hit.getLocation();
                faces[i] = hit.getDirection();
            }
        }
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        for (int i = 0; i + 1 < RAYS; i++) {
            float h0 = i / (float) (RAYS - 1), h1 = (i + 1) / (float) (RAYS - 1);
            Vec3 a = centre.add(dirs[i].scale(0.3)), b = centre.add(dirs[i + 1].scale(0.3));
            Vec3 ea = hits[i] != null ? hits[i] : a.add(dirs[i].scale(4)), eb = hits[i + 1] != null ? hits[i + 1] : b.add(dirs[i + 1].scale(4));
            // (the fan through the air: bright at the prism, fading out)
            fan.air.add(quad(origin, a, b, eb, ea, h0, h1, h1, h0, 1, 1, hits[i + 1] != null ? 0.6F : 0, hits[i] != null ? 0.6F : 0));
            if (hits[i] == null || hits[i + 1] == null || faces[i] != faces[i + 1] || hits[i].distanceToSqr(hits[i + 1]) > 1.0) {
                continue;
            }
            // (where it lands: a stripe of that colour, as wide as the prism's long, along its edge's way on that surface)
            Vec3 n = Vec3.atLowerCornerOf(faces[i].getNormal());
            Vec3 w = axis.subtract(n.scale(axis.dot(n)));
            if (w.lengthSqr() < 0.01) {
                w = new Vec3(n.z, n.x, n.y); // (the edge points straight at the surface: any way across it)
            }
            w = w.normalize().scale(WIDTH * 0.5);
            Vec3 lift = n.scale(0.012);
            Vec3 p0 = hits[i].add(lift), p1 = hits[i + 1].add(lift);
            float fade0 = 1.0F - (float) hits[i].distanceTo(centre) / (REACH + 1), fade1 = 1.0F - (float) hits[i + 1].distanceTo(centre) / (REACH + 1);
            fan.land.add(quad(origin, p0.subtract(w), p1.subtract(w), p1.add(w), p0.add(w), h0, h1, h1, h0, fade0, fade1, fade1, fade0));
        }
    }

    private static float[] quad(Vec3 o, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float ha, float hb, float hc, float hd, float sa, float sb, float sc, float sd) {
        return new float[] {(float) (a.x - o.x), (float) (a.y - o.y), (float) (a.z - o.z), (float) (b.x - o.x), (float) (b.y - o.y), (float) (b.z - o.z),
                (float) (c.x - o.x), (float) (c.y - o.y), (float) (c.z - o.z), (float) (d.x - o.x), (float) (d.y - o.y), (float) (d.z - o.z),
                ha, hb, hc, hd, sa, sb, sc, sd};
    }

    /** v turned by angle (radians) round the unit axis k. */
    private static Vec3 rotate(Vec3 v, Vec3 k, float angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1 - c)));
    }

    /** The glass: a bar with triangular ends, apex up, along the prism's axis (3 .. 13 pixels), from 2.5 to 9 up. */
    private static void glass(PoseStack ps, MultiBufferSource buffers, int light, int overlay, boolean alongX) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GLASS));
        PoseStack.Pose pose = ps.last();
        float a0 = 3.0F / 16, a1 = 13.0F / 16, y0 = 2.5F / 16, y1 = 9.0F / 16, half = 3.2F / 16;
        // the triangle's corners across the axis: (across, up)
        float[][] tri = {{-half, y0}, {half, y0}, {0, y1}};
        for (int s = 0; s < 3; s++) {
            float[] p = tri[s], q = tri[(s + 1) % 3];
            float nx = q[1] - p[1], ny = -(q[0] - p[0]);
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            nx /= len;
            ny /= len;
            // (a long face: from p to q, the length of the bar)
            quadGlass(vc, pose, alongX, a0, a1, p, q, -nx, -ny, light, overlay);
        }
        // the ends
        for (int e = 0; e < 2; e++) {
            float along = e == 0 ? a0 : a1, n = e == 0 ? -1 : 1;
            float[][] c = {tri[0], tri[1], tri[2], tri[2]};
            float[][] uv = {{0, 1}, {1, 1}, {0.5F, 0}, {0.5F, 0}};
            for (int i = 0; i < 4; i++) {
                Vector3f p = place(alongX, along, c[i][0], c[i][1]);
                Vector3f nn = alongX ? new Vector3f(n, 0, 0) : new Vector3f(0, 0, n);
                vc.addVertex(pose, p.x, p.y, p.z).setColor(255, 255, 255, 200).setUv(uv[i][0], uv[i][1]).setOverlay(overlay).setLight(light)
                        .setNormal(pose, nn.x, nn.y, nn.z);
            }
        }
    }

    private static void quadGlass(VertexConsumer vc, PoseStack.Pose pose, boolean alongX, float a0, float a1, float[] p, float[] q, float nx, float ny, int light,
                                  int overlay) {
        Vector3f[] c = {place(alongX, a0, p[0], p[1]), place(alongX, a1, p[0], p[1]), place(alongX, a1, q[0], q[1]), place(alongX, a0, q[0], q[1])};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        Vector3f n = alongX ? new Vector3f(0, ny, nx) : new Vector3f(nx, ny, 0);
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, c[i].x, c[i].y, c[i].z).setColor(255, 255, 255, 200).setUv(uv[i][0], uv[i][1]).setOverlay(overlay).setLight(light)
                    .setNormal(pose, n.x, n.y, n.z);
        }
    }

    /** A point of the glass: along its axis, across it (from the middle), up. */
    private static Vector3f place(boolean alongX, float along, float across, float up) {
        return alongX ? new Vector3f(along, up, 0.5F + across) : new Vector3f(0.5F + across, up, along);
    }

    @Override
    public AABB getRenderBoundingBox(SimpleRenderedBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(REACH);
    }

    @Override
    public boolean shouldRenderOffScreen(SimpleRenderedBlockEntity be) {
        return true;
    }
}
