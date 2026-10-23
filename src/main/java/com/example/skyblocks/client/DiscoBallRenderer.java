package com.example.skyblocks.client;

import com.example.skyblocks.DiscoBallBlock;
import com.example.skyblocks.DiscoBallBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The disco ball: a spinning ball of little mirror tiles that glint as they turn, and its light: 96 beams traced from
 * the ball through the world every frame, each leaving a soft spot of light where it lands (stretched when it lands at
 * a slant, bigger further away), with faint beams through the air. Glass lets the light through. Music playing nearby
 * spins it faster and makes the spots flash brighter with the bass.
 */
public class DiscoBallRenderer implements BlockEntityRenderer<DiscoBallBlockEntity> {
    private static final int BEAMS = 96;
    private static final double RANGE = 24.0;
    private static final float RADIUS = 0.3F;
    private static final int RINGS = 10, SEGMENTS = 20;
    private static final Vec3[] DIRECTIONS = new Vec3[BEAMS];
    private static final Map<DiscoBallBlockEntity, double[]> SPIN = new WeakHashMap<>();

    static {
        // spread evenly over a sphere (a Fibonacci spiral)
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < BEAMS; i++) {
            double y = 1.0 - (i + 0.5) / BEAMS * 2.0;
            double r = Math.sqrt(1.0 - y * y);
            DIRECTIONS[i] = new Vec3(Math.cos(golden * i) * r, y, Math.sin(golden * i) * r);
        }
    }

    public DiscoBallRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public boolean shouldRenderOffScreen(DiscoBallBlockEntity be) {
        return true; // (its spots land far outside its block)
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(DiscoBallBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(RANGE);
    }

    @Override
    public void render(DiscoBallBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        int mode = be.getBlockState().getBlock() instanceof DiscoBallBlock ? be.getBlockState().getValue(DiscoBallBlock.MODE) : 0;
        int animation = be.getBlockState().getBlock() instanceof DiscoBallBlock ? be.getBlockState().getValue(DiscoBallBlock.ANIMATION) : 0;
        MusicReactor.tick();
        float bass = MusicReactor.bass();
        // the spin, faster with the bass (kept per ball, so a change in speed doesn't make it jump)
        double[] spin = SPIN.computeIfAbsent(be, b -> new double[] {(b.getBlockPos().hashCode() & 1023) * 0.01, System.nanoTime()});
        long now = System.nanoTime();
        double dt = Math.min(0.25, (now - spin[1]) / 1.0e9);
        spin[1] = now;
        if (mode > 0 && !Minecraft.getInstance().isPaused()) {
            double gameSeconds = (level.getGameTime() + partialTick) / 20.0;
            spin[0] += dt * 0.6 * (1.0 + 1.5 * bass) * spinSpeed(animation, gameSeconds);
        }
        float angle = (float) spin[0];
        double seconds = (level.getGameTime() % 1_728_000L + partialTick) / 20.0;

        BlockPos pos = be.getBlockPos();
        Vec3 centre = Vec3.atCenterOf(pos).add(0.0, -0.1, 0.0);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Matrix4f pose = poseStack.last().pose();
        Vector3f local = new Vector3f((float) (centre.x - pos.getX()), (float) (centre.y - pos.getY()), (float) (centre.z - pos.getZ()));
        Vector3f toCam = new Vector3f((float) (cam.x - centre.x), (float) (cam.y - centre.y), (float) (cam.z - centre.z)).normalize();
        drawBall(buffers.getBuffer(Types.BALL), pose, local, toCam, angle, mode, seconds, true);
        if (mode == 0) {
            return;
        }
        VertexConsumer glow = buffers.getBuffer(Types.GLOW);
        float beamStrength = com.example.skyblocks.FogMachineBlockEntity.fogNear(pos, 20.0) ? 0.45F : 0.1F; // (beams show in fog)
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        for (int i = 0; i < BEAMS; i++) {
            Vec3 d0 = DIRECTIONS[i];
            Vec3 dir = new Vec3(d0.x * cos - d0.z * sin, d0.y, d0.x * sin + d0.z * cos);
            Vec3 start = centre.add(dir.scale(RADIUS + 0.02));
            BlockHitResult hit = level.clip(new ClipContext(start, centre.add(dir.scale(RANGE)), ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 p = hit.getLocation();
            double dist = start.distanceTo(p);
            float strength = (float) ((1.0 - dist / RANGE) * (0.75 + 0.7 * bass)) * light(animation, i, d0, dir, seconds, bass);
            if (strength <= 0.003F) {
                continue;
            }
            float r, g, b;
            if (mode == 1) {
                r = 1.0F; g = 0.97F; b = 0.9F;
            } else {
                float[] c = hsv((float) (i * 0.618 + seconds * 0.08) % 1.0F);
                r = c[0]; g = c[1]; b = c[2];
            }
            Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            if (mode == 2 && animation == 5) { // (colour waves round the room)
                float[] c = hsv((float) (((Math.atan2(dir.z, dir.x) / (Math.PI * 2) + 1.0) + seconds * 0.15) % 1.0));
                r = c[0]; g = c[1]; b = c[2];
            }
            float sizeScale = animation == 7 ? 0.6F + 0.9F * (float) Math.pow(0.5 + 0.5 * Math.sin(seconds * 4.0), 2.0) + bass : 1.0F;
            spot(glow, pose, p.subtract(Vec3.atLowerCornerOf(pos)), n, dir, dist * sizeScale, r, g, b, strength);
            beam(glow, pose, start.subtract(Vec3.atLowerCornerOf(pos)), p.subtract(Vec3.atLowerCornerOf(pos)),
                    cam.subtract(Vec3.atLowerCornerOf(pos)), r, g, b, strength * beamStrength);
        }
    }

    /** How fast (and which way) the ball turns, for each animation. */
    private static double spinSpeed(int animation, double t) {
        return switch (animation) {
            case 4 -> Math.cos(t * 0.35) * 1.4;           // swinging back and forth
            case 8 -> 3.5;                                // fast
            case 1 -> 0.5;                                // (strobe: slow, the flashes do the work)
            default -> 1.0;
        };
    }

    /**
     * How bright beam i is now, for each animation (d0: its direction on the ball before turning, dir: after).
     * 0 classic, 1 strobe, 2 sweep up and down, 3 twinkle, 4 swing + breathe, 5 waves round the room, 6 spiral,
     * 7 pulsing spots, 8 fast spin.
     */
    private static float light(int animation, int i, Vec3 d0, Vec3 dir, double t, float bass) {
        switch (animation) {
            case 1: { // strobe: all flashing together (harder with the bass)
                double rate = bass > 0.2F ? 10.0 : 6.0;
                return ((t * rate) % 1.0) < 0.35 ? 1.25F : 0.0F;
            }
            case 2: { // a band of light sweeping up and down the room
                double band = Math.sin(t * 1.4) * 0.9;
                double d = Math.abs(d0.y - band);
                return (float) Math.max(0.0, 1.0 - d / 0.28) * 1.3F;
            }
            case 3: { // spots fading in and out at random
                double phase = t * 0.7 + hash(i) * 10.0;
                double f = phase % 1.0;
                double on = hash(i * 31 + (int) Math.floor(phase)) > 0.45 ? 1.0 : 0.0;
                return (float) (on * Math.sin(f * Math.PI)) * 1.2F;
            }
            case 4: { // breathing
                return 0.35F + 0.65F * (float) (0.5 + 0.5 * Math.sin(t * 1.6));
            }
            case 5: { // a wave of light going round
                double az = Math.atan2(dir.z, dir.x);
                return 0.25F + 0.85F * (float) Math.pow(0.5 + 0.5 * Math.sin(az * 3.0 - t * 2.5), 2.0);
            }
            case 6: { // a spiral of lit spots climbing round
                double az = Math.atan2(d0.z, d0.x);
                double s = Math.sin(az * 2.0 + d0.y * 6.0 - t * 3.0);
                return s > 0.55 ? 1.3F : 0.05F;
            }
            default:
                return 1.0F;
        }
    }

    private static double hash(int n) {
        double x = Math.sin(n * 12.9898) * 43758.5453;
        return x - Math.floor(x);
    }

    /** The ball: dark behind, little mirror tiles in front, each glinting when it catches a light towards you. */
    static void drawBall(VertexConsumer out, Matrix4f pose, Vector3f c, Vector3f toCam, float angle, int mode, double seconds, boolean rod) {
        Vector3f[] lights = {new Vector3f(0.3F, 0.9F, 0.3F).normalize(), new Vector3f(-0.8F, 0.2F, 0.4F).normalize(),
                new Vector3f(0.5F, -0.3F, -0.8F).normalize()};
        // the hanging rod
        if (rod) box(out, pose, c.x - 0.02F, c.y + RADIUS, c.z - 0.02F, c.x + 0.02F, 1.0F, c.z + 0.02F, 0.15F, 0.15F, 0.17F);
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        for (int i = 0; i < RINGS; i++) {
            float lat0 = (float) (-Math.PI / 2 + Math.PI * i / RINGS), lat1 = (float) (-Math.PI / 2 + Math.PI * (i + 1) / RINGS);
            for (int j = 0; j < SEGMENTS; j++) {
                float lon0 = (float) (2 * Math.PI * j / SEGMENTS) + angle, lon1 = (float) (2 * Math.PI * (j + 1) / SEGMENTS) + angle;
                Vector3f[] q = {sphere(lat0, lon0), sphere(lat0, lon1), sphere(lat1, lon1), sphere(lat1, lon0)};
                Vector3f mid = new Vector3f(q[0]).add(q[1]).add(q[2]).add(q[3]).normalize();
                // grout behind
                quad(out, pose, c, q, RADIUS * 0.97F, 1.0F, mid, 0.05F, 0.05F, 0.06F);
                // the tile: a bit smaller, silver, glinting
                Vector3f reflect = new Vector3f(mid).mul(2.0F * mid.dot(toCam)).sub(toCam);
                float glint = 0.0F;
                for (Vector3f l : lights) {
                    glint = Math.max(glint, (float) Math.pow(Math.max(0.0F, reflect.dot(l)), 40.0));
                }
                float base = 0.35F + 0.3F * (float) ((i * 7919 + j * 104729) % 97) / 97.0F;
                float r = base, g = base, b = base * 1.05F;
                if (mode == 2) {
                    float[] h = hsv((float) ((j * 0.05 + i * 0.11 + seconds * 0.1) % 1.0));
                    r += glint * h[0] * 1.2F; g += glint * h[1] * 1.2F; b += glint * h[2] * 1.2F;
                } else {
                    r += glint * 1.2F; g += glint * 1.2F; b += glint * 1.2F;
                }
                if (mode == 0) {
                    r *= 0.6F; g *= 0.6F; b *= 0.6F;
                }
                quad(out, pose, c, q, RADIUS, 0.86F, mid, Math.min(r, 1.0F), Math.min(g, 1.0F), Math.min(b, 1.0F));
            }
        }
    }

    private static Vector3f sphere(float lat, float lon) {
        return new Vector3f((float) (Math.cos(lat) * Math.cos(lon)), (float) Math.sin(lat), (float) (Math.cos(lat) * Math.sin(lon)));
    }

    /** One tile: its corners on the sphere, shrunk towards its middle by {@code shrink}. */
    private static void quad(VertexConsumer out, Matrix4f pose, Vector3f c, Vector3f[] q, float radius, float shrink, Vector3f mid,
                             float r, float g, float b) {
        for (int k = 3; k >= 0; k--) { // (wound to face outwards)
            Vector3f v = new Vector3f(q[k]).sub(mid).mul(shrink).add(mid).mul(radius).add(c);
            out.addVertex(pose, v.x, v.y, v.z).setColor(r, g, b, 1.0F);
        }
    }

    private static void box(VertexConsumer out, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b) {
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0}, {x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1},
                {x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1}, {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0}};
        for (float[] f : faces) {
            for (int k = 0; k < 4; k++) {
                out.addVertex(pose, f[k * 3], f[k * 3 + 1], f[k * 3 + 2]).setColor(r, g, b, 1.0F);
            }
        }
    }

    /** A soft round spot of light lying on the surface (normal n) where a beam (dir) landed. */
    private static void spot(VertexConsumer out, Matrix4f pose, Vec3 p, Vec3 n, Vec3 dir, double dist, float r, float g, float b, float strength) {
        Vec3 along = dir.subtract(n.scale(dir.dot(n)));
        Vec3 u = along.lengthSqr() > 1.0e-6 ? along.normalize() : (Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0).cross(n).normalize() : new Vec3(1, 0, 0));
        Vec3 v = n.cross(u);
        double size = 0.08 + dist * 0.025;
        double stretch = 1.0 / Math.max(Math.abs(dir.dot(n)), 0.35); // (longer when it lands at a slant)
        Vec3 c = p.add(n.scale(0.012));
        int segments = 12;
        for (int k = 0; k < segments; k++) {
            double a0 = Math.PI * 2 * k / segments, a1 = Math.PI * 2 * (k + 1) / segments;
            Vec3 e0 = c.add(u.scale(Math.cos(a0) * size * stretch)).add(v.scale(Math.sin(a0) * size));
            Vec3 e1 = c.add(u.scale(Math.cos(a1) * size * stretch)).add(v.scale(Math.sin(a1) * size));
            out.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, b, strength);
            out.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, b, strength);
            out.addVertex(pose, (float) e1.x, (float) e1.y, (float) e1.z).setColor(r, g, b, 0.0F);
            out.addVertex(pose, (float) e0.x, (float) e0.y, (float) e0.z).setColor(r, g, b, 0.0F);
        }
    }

    /** A faint beam through the air from the ball to its spot, always turned towards you. */
    private static void beam(VertexConsumer out, Matrix4f pose, Vec3 s, Vec3 e, Vec3 cam, float r, float g, float b, float strength) {
        Vec3 axis = e.subtract(s);
        Vec3 side = axis.cross(cam.subtract(s));
        if (side.lengthSqr() < 1.0e-8) {
            return;
        }
        side = side.normalize();
        Vec3 sw = side.scale(0.015), ew = side.scale(0.06 + axis.length() * 0.012);
        out.addVertex(pose, (float) (s.x - sw.x), (float) (s.y - sw.y), (float) (s.z - sw.z)).setColor(r, g, b, strength);
        out.addVertex(pose, (float) (s.x + sw.x), (float) (s.y + sw.y), (float) (s.z + sw.z)).setColor(r, g, b, strength);
        out.addVertex(pose, (float) (e.x + ew.x), (float) (e.y + ew.y), (float) (e.z + ew.z)).setColor(r, g, b, strength * 0.3F);
        out.addVertex(pose, (float) (e.x - ew.x), (float) (e.y - ew.y), (float) (e.z - ew.z)).setColor(r, g, b, strength * 0.3F);
    }

    private static float[] hsv(float h) {
        float r = Math.abs(h * 6.0F - 3.0F) - 1.0F, g = 2.0F - Math.abs(h * 6.0F - 2.0F), b = 2.0F - Math.abs(h * 6.0F - 4.0F);
        return new float[] {0.35F + 0.65F * clamp(r), 0.35F + 0.65F * clamp(g), 0.35F + 0.65F * clamp(b)};
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    /** (A subclass only to reach RenderType's building blocks.) */
    abstract static class Types extends RenderType {
        /** The ball: plain colours, solid, full bright. */
        static final RenderType BALL = create("skyblocks_disco_ball", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192,
                false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
        /** The light: added on top (glowing), not hiding anything behind it. */
        static final RenderType GLOW = create("skyblocks_disco_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536,
                false, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }
}
