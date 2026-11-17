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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The prism: a clear crystal floating over its stand, turning slowly (shaders/core/prism_crystal: light off its faces
 * and bent through it, split into its colours), and what light does going through it, as a prism splits it: a white
 * beam in, and out the far side a fan of colours, red to violet, spreading as it goes - a sheet of colours through the
 * air and a rainbow where it lands on the floor or a wall (shaders/core/prism_light). Which way it goes follows where
 * the light comes from: a spotlight or beam light shone on it (SpotlightRenderer tells it), a lamp near it (anything
 * giving off light, in sight of it), or the sun - by day, not in rain, where it can reach it (through glass too);
 * whichever's strongest. As the crystal turns, the fan goes slowly round the light's way.
 */
public class PrismRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance crystalShader, lightShader;
    /** The crystal's middle (from the block's corner) and the box round it (half sizes). */
    private static final float CY = 0.55F, HX = 0.22F, HY = 0.29F;
    /** How far its light reaches (blocks); one turn takes this long (ticks). */
    private static final int REACH = 14, TURN_TICKS = 2400;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerPrismShaders(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "prism_crystal"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> crystalShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "prism_light"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> lightShader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CRYSTAL = create("skyblocks_prism_crystal", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> crystalShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        /** The light it throws: added onto what it falls on (and, faintly, the air). */
        static final RenderType LIGHT = create("skyblocks_prism_light", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> lightShader))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY) // (adding light - see prism_light.fsh)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** Spotlight beams shone on prisms lately: which way it was going, its colour, and when. */
    private record Beam(Vec3 dir, int colour, long at) {}

    private static final Map<BlockPos, Beam> BEAMS = new HashMap<>();

    /** A spotlight's beam has hit the prism at pos (its direction, its colour). */
    public static void shone(BlockPos pos, Vec3 dir, int colour, long gameTime) {
        BEAMS.put(pos.immutable(), new Beam(dir.normalize(), colour & 0xFFFFFF, gameTime));
        if (BEAMS.size() > 64) {
            BEAMS.entrySet().removeIf(e -> gameTime - e.getValue().at() > 40);
        }
    }

    /** Per prism: the lamp it last found near it (looked for once a second), how it was turned when it stopped turning. */
    private static final class State {
        long lookedAt = -1000L; // (not looked yet; not Long.MIN_VALUE - now minus that overflows)
        Vec3 lamp;
        float lampStrength;
        boolean wasTurning = true;
        float stoppedAt = Float.NaN;
    }

    private static final Map<SimpleRenderedBlockEntity, State> STATES = new WeakHashMap<>();

    public PrismRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || crystalShader == null || !(state.getBlock() instanceof PrismBlock)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        State s = STATES.computeIfAbsent(be, k -> new State());
        long now = level.getGameTime();
        float time = now + partialTick;
        long seed = pos.asLong() * 0x9E3779B97F4A7C15L;

        // how it's turned: slowly round, or held where it was when it was stopped
        boolean turning = !state.hasProperty(PrismBlock.TURNING) || state.getValue(PrismBlock.TURNING);
        float running = (float) ((time % TURN_TICKS) / TURN_TICKS * Math.PI * 2.0) + (seed >>> 40 & 1023) / 163.0F;
        if (!turning && s.wasTurning) {
            s.stoppedAt = running;
        }
        s.wasTurning = turning;
        float turn = turning ? running : Float.isNaN(s.stoppedAt) ? (seed >>> 40 & 1023) / 163.0F : s.stoppedAt;
        float bob = 0.012F * Mth.sin(time * 0.06F);
        Vec3 middle = new Vec3(pos.getX() + 0.5, pos.getY() + CY + bob, pos.getZ() + 0.5);

        // the light going in (which way it's travelling), its strength and colour
        Vec3 in = null;
        float strength = 0.0F;
        int tint = 0xFFFFFF;
        Beam beam = BEAMS.get(pos);
        if (beam != null && now - beam.at() <= 3) {
            in = beam.dir();
            strength = 1.0F;
            tint = beam.colour();
        }
        if (now - s.lookedAt >= 20 || now < s.lookedAt) {
            findLamp(level, pos, middle, s);
            s.lookedAt = now;
        }
        // (where the light comes from, for its beam in: a spotlight draws its own)
        Vec3 source = null;
        boolean sunIn = false;
        if (s.lamp != null && s.lampStrength > strength) {
            in = middle.subtract(s.lamp).normalize();
            strength = s.lampStrength;
            tint = 0xFFFFFF;
            source = s.lamp;
        }
        float sun = sunlit(level, pos, partialTick) ? 0.8F * (1.0F - level.getRainLevel(partialTick)) : 0.0F;
        if (sun > strength) {
            in = sunDirection(level, partialTick).scale(-1);
            strength = sun;
            tint = 0xFFFFFF;
            // (the sunbeam in: from as far up towards the sun as there's open air, at most 6 blocks)
            Vec3 up = middle.subtract(in.scale(6));
            BlockHitResult roof = level.clip(new ClipContext(middle.subtract(in.scale(0.4)), up, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            source = roof.getType() == HitResult.Type.BLOCK ? roof.getLocation() : up;
            sunIn = true; // (a sunbeam: faint)
        }
        if (strength <= 0.02F) {
            in = null;
            strength = 0.0F;
        }

        // the crystal
        int packed = LevelRendererLight.at(level, pos);
        float bright = Math.max(LightTexture.block(packed), LightTexture.sky(packed) * Math.max(0.0F, ((level instanceof net.minecraft.client.multiplayer.ClientLevel cl ? cl.getSkyDarken(partialTick) : 1.0F) - 0.2F) / 0.8F)) / 15.0F;
        Vec3 toLight = in == null ? new Vec3(0.3, 0.9, 0.3).normalize() : in.scale(-1);
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        float fb = bob, fs = strength, fbright = bright, fturn = turn;
        Vec3 fl = toLight;
        // the light thrown out, worked out now (where each rainbow lands)
        List<float[]> verts = new ArrayList<>();
        if (in != null && Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(middle) < 64 * 64) {
            cast(level, pos, middle, in, source, sunIn ? 0.3F : 1.0F, turn, strength, tint, time, verts);
        }
        Runnable draw = () -> {
            ElevatorRenderer.drawNow(Types.CRYSTAL, vc -> box(vc, m, fb, fl, fs, fbright, fturn));
            if (!verts.isEmpty() && lightShader != null) {
                ElevatorRenderer.drawNow(Types.LIGHT, vc -> {
                    for (float[] p : verts) {
                        vc.addVertex(m, p[0], p[1], p[2]).setUv(p[3], p[4]).setColor((int) p[5], (int) p[6], (int) p[7], (int) p[8]);
                    }
                });
            }
        };
        if (!LatePass.later(src -> draw.run())) {
            draw.run();
        }
    }

    /** The box round the crystal (only its faces towards the eye), each corner carrying what the shader needs. */
    private static void box(VertexConsumer vc, Matrix4f m, float bob, Vec3 toLight, float strength, float bright, float turn) {
        Vector3f centre = m.transformPosition(0.5F, CY + bob, 0.5F, new Vector3f());
        float ex = -centre.x, ey = -centre.y, ez = -centre.z;
        int r = Mth.clamp(Math.round((float) (toLight.x * 0.5 + 0.5) * 255), 0, 255), g = Mth.clamp(Math.round((float) (toLight.y * 0.5 + 0.5) * 255), 0, 255),
                b = Mth.clamp(Math.round((float) (toLight.z * 0.5 + 0.5) * 255), 0, 255), a = Mth.clamp(Math.round(strength * 255), 0, 255);
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        float[] towards = {-ex - HX, ex - HX, -ey - HY, ey - HY, -ez - HX, ez - HX};
        float frac = Mth.clamp(bright, 0.0F, 1.0F) * 0.9F;
        for (int f = 0; f < 6; f++) {
            if (towards[f] > 0) {
                continue;
            }
            for (int c : faces[f]) {
                float x = ((c & 1) * 2 - 1) * HX, y = (((c >> 1) & 1) * 2 - 1) * HY, z = (((c >> 2) & 1) * 2 - 1) * HX;
                vc.addVertex(m, 0.5F + x, CY + bob + y, 0.5F + z).setColor(r, g, b, a).setUv(c + frac, turn);
            }
        }
    }

    /** How far the light's turned going through (radians), how wide the colours fan out, how thick the fan is. */
    private static final float BEND = 0.6F, FAN = 0.30F, THICK = 0.035F;
    private static final int BANDS = 16;

    /**
     * Light through the crystal as a prism splits it: a white beam in (from a lamp or the sun - a spotlight draws its
     * own), and out the far side one fan of colours, turned from the light's way (red the least, violet the most) and
     * spreading as it goes: a sheet of colours in the air, a soft glow round each band, and a rainbow where it lands.
     * Which way it's turned goes round the light's way as the crystal turns. Each band's own way followed to where it
     * lands, so the rainbow lies on whatever it reaches - the floor, a wall, round a corner, part on each.
     */
    private static void cast(Level level, BlockPos pos, Vec3 middle, Vec3 in, Vec3 source, float beamIn, float turn, float strength, int tint,
                             float time, List<float[]> out) {
        Vec3 e1 = Math.abs(in.y) < 0.9 ? in.cross(new Vec3(0, 1, 0)).normalize() : in.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 e2 = in.cross(e1).normalize();
        // (the axis it's turned round: across the light's way, going round as the crystal turns)
        Vec3 axis = e1.scale(Math.cos(turn)).add(e2.scale(Math.sin(turn)));
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int cr = tint >> 16 & 255, cg = tint >> 8 & 255, cb = tint & 255;
        float shimmer = 0.94F + 0.06F * Mth.sin(time * 0.15F);

        // the white beam in, to the crystal's near side
        Vec3 into = middle.subtract(in.scale(0.16));
        if (source != null) {
            Vec3 side = in.cross(cam.subtract(into));
            if (side.lengthSqr() > 1e-8) {
                side = side.normalize();
                float a = strength * 0.55F * beamIn;
                put(out, pos, source.subtract(side.scale(0.045)), -1, -1, cr, cg, cb, a * 0.3F);
                put(out, pos, source.add(side.scale(0.045)), -1, 1, cr, cg, cb, a * 0.3F);
                put(out, pos, into.add(side.scale(0.035)), -1, 1, cr, cg, cb, a);
                put(out, pos, into.subtract(side.scale(0.035)), -1, -1, cr, cg, cb, a);
            }
        }

        // each band's edges: their ways, where they land (or how far they go)
        Vec3 exit = middle.add(rotate(in, axis, BEND).scale(0.16));
        Vec3[] way = new Vec3[BANDS + 1], end = new Vec3[BANDS + 1];
        BlockHitResult[] hits = new BlockHitResult[BANDS + 1];
        for (int k = 0; k <= BANDS; k++) {
            way[k] = rotate(in, axis, BEND + ((float) k / BANDS - 0.5F) * FAN);
            Vec3 from = exit.add(way[k].scale(0.05));
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(way[k].scale(REACH)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                // (its own stand in the way: on past it)
                Vec3 past = hit.getLocation().add(way[k].scale(0.02));
                hit = level.clip(new ClipContext(past, from.add(way[k].scale(REACH)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                        CollisionContext.empty()));
            }
            boolean landed = hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(pos);
            hits[k] = landed ? hit : null;
            end[k] = landed ? hit.getLocation() : from.add(way[k].scale(REACH));
        }
        // (the beam's width as it leaves: the colours start out together)
        float start = 0.05F;
        for (int k = 0; k < BANDS; k++) {
            float h0 = (float) k / BANDS, h1 = (float) (k + 1) / BANDS, hm = (h0 + h1) * 0.5F;
            // (the reds and violets at the very ends fade out)
            float edge = Mth.clamp(Math.min(hm, 1.0F - hm) * BANDS * 0.7F, 0.0F, 1.0F);
            float bright = strength * shimmer * edge;
            Vec3 a0 = exit.add(rotate(in, axis, BEND).cross(axis).scale((h0 - 0.5F) * start)), a1 = exit.add(rotate(in, axis, BEND).cross(axis).scale((h1 - 0.5F) * start));
            float d0 = (float) end[k].distanceTo(exit), d1 = (float) end[k + 1].distanceTo(exit);
            // the sheet of colours through the air: bright out of the crystal, fading as it goes
            float far = bright * 0.12F / (1.0F + (d0 + d1) * 0.05F);
            put(out, pos, a0, h0, 0, cr, cg, cb, bright * 0.75F);
            put(out, pos, a1, h1, 0, cr, cg, cb, bright * 0.75F);
            put(out, pos, end[k + 1], h1, 0, cr, cg, cb, far);
            put(out, pos, end[k], h0, 0, cr, cg, cb, far);
            // a soft glow round the band, facing the eye (so the fan shows from any side)
            Vec3 mid = end[k].add(end[k + 1]).scale(0.5);
            Vec3 dir = mid.subtract(exit);
            Vec3 side = dir.cross(cam.subtract(exit));
            if (side.lengthSqr() > 1e-8) {
                side = side.normalize();
                float w = (float) Math.max(end[k].distanceTo(end[k + 1]) * 0.8, 0.03) + (d0 + d1) * 0.5F * THICK;
                float glow = bright * 0.16F;
                put(out, pos, exit.subtract(side.scale(0.02)), hm, -1, cr, cg, cb, glow * 2.0F);
                put(out, pos, exit.add(side.scale(0.02)), hm, 1, cr, cg, cb, glow * 2.0F);
                put(out, pos, mid.add(side.scale(w)), hm, 1, cr, cg, cb, glow * 0.25F);
                put(out, pos, mid.subtract(side.scale(w)), hm, -1, cr, cg, cb, glow * 0.25F);
            }
            // where it lands: the band's stretch of the rainbow on what it reached (both edges on the same flat face)
            if (hits[k] != null && hits[k + 1] != null && hits[k].getDirection() == hits[k + 1].getDirection()
                    && end[k].distanceTo(end[k + 1]) < 2.0) {
                Vec3 n = Vec3.atLowerCornerOf(hits[k].getDirection().getNormal());
                Vec3 v = axis.subtract(n.scale(axis.dot(n)));
                if (v.lengthSqr() < 1e-6) {
                    v = n.cross(end[k + 1].subtract(end[k]));
                }
                if (v.lengthSqr() > 1e-8) {
                    v = v.normalize();
                    float t0 = 0.05F + d0 * THICK, t1 = 0.05F + d1 * THICK;
                    float land = Mth.clamp(bright * 1.4F / (1.0F + (d0 + d1) * 0.04F), 0.0F, 1.0F);
                    Vec3 p0 = end[k].add(n.scale(0.012)), p1 = end[k + 1].add(n.scale(0.012));
                    put(out, pos, p0.subtract(v.scale(t0)), h0, -1, cr, cg, cb, land);
                    put(out, pos, p1.subtract(v.scale(t1)), h1, -1, cr, cg, cb, land);
                    put(out, pos, p1.add(v.scale(t1)), h1, 1, cr, cg, cb, land);
                    put(out, pos, p0.add(v.scale(t0)), h0, 1, cr, cg, cb, land);
                }
            }
        }
    }

    private static void put(List<float[]> out, BlockPos pos, Vec3 p, float u, float v, int r, int g, int b, float a) {
        out.add(new float[] {(float) (p.x - pos.getX()), (float) (p.y - pos.getY()), (float) (p.z - pos.getZ()), u, v, r, g, b,
                Mth.clamp(a, 0.0F, 1.0F) * 255});
    }

    /** The brightest lamp in sight within 7 blocks (anything giving off light 8 or more), and how strong it is here. */
    private static void findLamp(Level level, BlockPos pos, Vec3 middle, State s) {
        s.lamp = null;
        s.lampStrength = 0.0F;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -4; dy <= 5; dy++) {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockState st = level.getBlockState(p);
                    int glow = st.isAir() ? 0 : st.getLightEmission(level, p);
                    if (glow < 8) {
                        continue;
                    }
                    Vec3 lamp = Vec3.atCenterOf(p);
                    float dist = (float) lamp.distanceTo(middle);
                    float strength = glow / 15.0F * Mth.clamp(1.15F - dist / 8.0F, 0.0F, 1.0F) * 0.9F;
                    if (strength <= s.lampStrength) {
                        continue;
                    }
                    // (in sight: nothing solid between them)
                    BlockHitResult hit = level.clip(new ClipContext(middle, lamp, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
                    if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(p) && !hit.getBlockPos().equals(pos)) {
                        continue;
                    }
                    s.lamp = lamp;
                    s.lampStrength = strength;
                }
            }
        }
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

    /** v turned by angle (radians) round the unit axis k. */
    private static Vec3 rotate(Vec3 v, Vec3 k, float angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1 - c)));
    }

    /** (The light round a block, packed as the light map wants it.) */
    private static final class LevelRendererLight {
        static int at(Level level, BlockPos pos) {
            return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos);
        }
    }

    @Override
    public AABB getRenderBoundingBox(SimpleRenderedBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(REACH);
    }

    @Override
    public boolean shouldRenderOffScreen(SimpleRenderedBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
