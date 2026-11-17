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
 * and bent through it, split into its colours), and what light does going through it - thrown out in all directions
 * as little rainbows, red at one end, violet at the other, scattered over the floor and walls round it and drifting
 * as it turns (shaders/core/prism_light), faint rays of colour through the air to them. The light: a spotlight's beam
 * shone on it (SpotlightRenderer tells it), a lamp near it (anything giving off light, in sight of it), or the sun -
 * by day, not in rain, where the sun can reach it (through glass too); whichever's strongest.
 */
public class PrismRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance crystalShader, lightShader;
    /** The crystal's middle (from the block's corner) and the box round it (half sizes). */
    private static final float CY = 0.55F, HX = 0.22F, HY = 0.29F;
    /** How many rainbows, how far they reach (blocks); one turn takes this long (ticks). */
    private static final int SPOTS = 34, REACH = 12, TURN_TICKS = 1600;

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
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
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
        long lookedAt = Long.MIN_VALUE;
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
        if (s.lamp != null && s.lampStrength > strength) {
            in = middle.subtract(s.lamp).normalize();
            strength = s.lampStrength;
            tint = 0xFFFFFF;
        }
        float sun = sunlit(level, pos, partialTick) ? 0.8F * (1.0F - level.getRainLevel(partialTick)) : 0.0F;
        if (sun > strength) {
            in = sunDirection(level, partialTick).scale(-1);
            strength = sun;
            tint = 0xFFFFFF;
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
            cast(level, pos, middle, in, turn, strength, tint, seed, time, verts);
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

    /**
     * Light through the crystal, out every way: each ray turned from the light's own way by its own angle (some only a
     * little, some right back out the side it came in), round the way the crystal's turned - so they drift as it turns.
     * Each a little rainbow where it lands, longer the further it went; a faint ray of colour to it.
     */
    private static void cast(Level level, BlockPos pos, Vec3 middle, Vec3 in, float turn, float strength, int tint, long seed, float time, List<float[]> out) {
        Vec3 e1 = Math.abs(in.y) < 0.9 ? in.cross(new Vec3(0, 1, 0)).normalize() : in.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 e2 = in.cross(e1).normalize();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        java.util.Random rnd = new java.util.Random(seed);
        int cr = tint >> 16 & 255, cg = tint >> 8 & 255, cb = tint & 255;
        for (int k = 0; k < SPOTS; k++) {
            float az = rnd.nextFloat() * Mth.TWO_PI, bend = 0.12F + rnd.nextFloat() * rnd.nextFloat() * 2.4F, power = 0.45F + rnd.nextFloat() * 0.55F;
            float phase = rnd.nextFloat() * 100.0F;
            Vec3 axis = e1.scale(Math.cos(az + turn)).add(e2.scale(Math.sin(az + turn)));
            Vec3 d = rotate(in, axis, bend);
            // (the way the colours spread: the way bending more takes it)
            Vec3 spread = rotate(in, axis, bend + 0.05F).subtract(d);
            Vec3 from = middle.add(d.scale(0.25));
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(d.scale(REACH)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().equals(pos)) {
                continue;
            }
            Vec3 at = hit.getLocation();
            float dist = (float) at.distanceTo(middle);
            Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            Vec3 u = spread.subtract(n.scale(spread.dot(n)));
            if (u.lengthSqr() < 1e-8) {
                u = Math.abs(n.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            }
            u = u.normalize();
            Vec3 v = n.cross(u).normalize();
            float len = 0.06F + dist * 0.032F, wid = 0.035F + dist * 0.009F;
            // (fainter the further it went and the more it's spread; twinkling a little)
            float twinkle = 0.85F + 0.15F * Mth.sin(time * 0.21F + phase);
            float bright = Mth.clamp(strength * power * twinkle * 1.15F / (1.0F + dist * 0.12F), 0.0F, 1.0F);
            Vec3 p = at.add(n.scale(0.012));
            Vec3 a0 = p.subtract(u.scale(len)).subtract(v.scale(wid)), a1 = p.add(u.scale(len)).subtract(v.scale(wid)),
                    a2 = p.add(u.scale(len)).add(v.scale(wid)), a3 = p.subtract(u.scale(len)).add(v.scale(wid));
            put(out, pos, a0, -1, -1, cr, cg, cb, bright);
            put(out, pos, a1, 1, -1, cr, cg, cb, bright);
            put(out, pos, a2, 1, 1, cr, cg, cb, bright);
            put(out, pos, a3, -1, 1, cr, cg, cb, bright);
            // the ray to it, faint, facing the eye: red on one side, violet the other
            float rayBright = bright * 0.09F;
            if (rayBright > 0.004F) {
                Vec3 side = d.cross(cam.subtract(from));
                if (side.lengthSqr() > 1e-8) {
                    side = side.normalize();
                    float s0 = 0.012F, s1 = Math.max(wid, 0.03F);
                    put(out, pos, from.subtract(side.scale(s0)), 2, -1, cr, cg, cb, rayBright);
                    put(out, pos, from.add(side.scale(s0)), 2, 1, cr, cg, cb, rayBright);
                    put(out, pos, p.add(side.scale(s1)), 3, 1, cr, cg, cb, rayBright);
                    put(out, pos, p.subtract(side.scale(s1)), 3, -1, cr, cg, cb, rayBright);
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
