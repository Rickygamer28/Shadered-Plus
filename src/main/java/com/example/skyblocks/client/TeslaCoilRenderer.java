package com.example.skyblocks.client;

import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.TeslaCoilBlock;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The tesla coil's lightning: jagged bolts (midpoint displacement, a few branches) from the ring on top, drawn as glowing
 * ribbons turned to the camera - a wide faint glow, a narrower one, a bright core - and redrawn in a new shape every
 * couple of ticks. Some crackle into the air; one jumps to each player near, some to mobs near, and one between coils
 * near each other. Louder bass: more of them, longer, brighter.
 */
public class TeslaCoilRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    /** Where the bolts start: the ring's middle (block units) and its radius. */
    static final double TOP_Y = 13.5 / 16.0, RING = 0.36;
    private static final double PLAYER_REACH = 5.0, MOB_REACH = 4.0, COIL_REACH = 8.0;
    /** The coils drawn lately (pos -> frame), for the arcs between them. */
    private static final Map<BlockPos, Long> COILS = new HashMap<>();
    private static long frame;

    abstract static class Types extends RenderType {
        static final RenderType ARC = create("skyblocks_tesla_arc", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192,
                false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public TeslaCoilRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(TeslaCoilBlock.LIT) || !be.getBlockState().getValue(TeslaCoilBlock.LIT)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = be.getBlockPos();
        long gameTime = be.getLevel().getGameTime();
        // (frames: a new one whenever the game time or the partial tick moves on)
        long now = gameTime * 1000L + (long) (partialTick * 999.0F);
        if (now != frame) {
            frame = now;
            COILS.values().removeIf(f -> f < now - 2000L);
        }
        COILS.put(pos.immutable(), now);
        MusicReactor.tick();
        float boost = Math.min(MusicReactor.bass() * 1.6F, 1.0F);
        int colour = GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds());
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        Vec3 top = new Vec3(0.5, TOP_Y, 0.5);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition().subtract(origin);
        // a new shape every 2 ticks (more often with music)
        long step = gameTime / (boost > 0.5F ? 1 : 2);
        Random rnd = new Random(pos.asLong() * 31L + step * 0x9E3779B97F4A7C15L);
        List<Vec3[]> arcs = new ArrayList<>();

        // into the air
        int free = 3 + rnd.nextInt(3) + Math.round(boost * 5.0F);
        for (int i = 0; i < free; i++) {
            double y = -0.25 + rnd.nextDouble() * 1.15;
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double hr = Math.sqrt(Math.max(0.0, 1.0 - Math.min(y * y, 0.95)));
            Vec3 dir = new Vec3(Math.cos(a) * hr, y, Math.sin(a) * hr).normalize();
            double len = 0.6 + rnd.nextDouble() * 0.9 + boost * (0.4 + rnd.nextDouble() * 0.8);
            arcs.add(new Vec3[] {start(top, dir), top.add(dir.scale(len + RING))});
        }
        // to the players near (a hand's height), and the mobs near
        AABB near = new AABB(pos).inflate(PLAYER_REACH);
        for (Player p : be.getLevel().players()) {
            Vec3 hand = p.getEyePosition(partialTick).add(0.0, -0.55, 0.0).add(p.getViewVector(partialTick).scale(0.25)).subtract(origin);
            if (hand.distanceTo(top) < PLAYER_REACH && !p.isSpectator()) {
                arcs.add(new Vec3[] {start(top, hand.subtract(top)), hand});
                if (rnd.nextInt(2) == 0) arcs.add(new Vec3[] {start(top, hand.subtract(top)), hand.add(rnd.nextGaussian() * 0.1, rnd.nextGaussian() * 0.1, rnd.nextGaussian() * 0.1)});
            }
        }
        int mobs = 0;
        for (LivingEntity e : be.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(MOB_REACH), e -> !(e instanceof Player) && e.isAlive())) {
            Vec3 at = e.getPosition(partialTick).add(0.0, e.getBbHeight() * 0.6, 0.0).subtract(origin);
            if (at.distanceTo(top) < MOB_REACH && mobs++ < 3) {
                arcs.add(new Vec3[] {start(top, at.subtract(top)), at});
            }
        }
        // between coils (drawn by one of each pair)
        for (Map.Entry<BlockPos, Long> e : COILS.entrySet()) {
            BlockPos o = e.getKey();
            if (o.asLong() <= pos.asLong() || o.distSqr(pos) > COIL_REACH * COIL_REACH) {
                continue;
            }
            Vec3 otherTop = Vec3.atLowerCornerOf(o).subtract(origin).add(0.5, TOP_Y, 0.5);
            if (rnd.nextInt(3) != 0) { // (on and off)
                Vec3 d = otherTop.subtract(top);
                arcs.add(new Vec3[] {start(top, d), start(otherTop, d.scale(-1.0))});
            }
        }

        VertexConsumer vc = buffers.getBuffer(Types.ARC);
        Matrix4f m = poseStack.last().pose();
        int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
        for (Vec3[] arc : arcs) {
            double len = arc[0].distanceTo(arc[1]);
            List<Vec3> pts = bolt(arc[0], arc[1], rnd, Math.min(0.18 * len, 0.6), Math.min(6, 3 + (int) Math.ceil(len)));
            float flick = 0.6F + 0.4F * rnd.nextFloat();
            ribbon(vc, m, pts, cam, 0.09F * (1.0F + boost * 0.5F), r, g, b, (int) (40 * flick));
            ribbon(vc, m, pts, cam, 0.035F, r, g, b, (int) (120 * flick));
            ribbon(vc, m, pts, cam, 0.012F, (r + 2 * 255) / 3, (g + 2 * 255) / 3, (b + 2 * 255) / 3, (int) (255 * flick));
            // a branch or two off the longer ones
            int branches = len > 0.8 ? 1 + rnd.nextInt(2) : 0;
            for (int k = 0; k < branches && pts.size() > 4; k++) {
                Vec3 from = pts.get(1 + rnd.nextInt(pts.size() - 3));
                Vec3 dir = arc[1].subtract(arc[0]).normalize().add(rnd.nextGaussian() * 0.6, rnd.nextGaussian() * 0.6, rnd.nextGaussian() * 0.6).normalize();
                List<Vec3> bp = bolt(from, from.add(dir.scale(len * (0.2 + 0.2 * rnd.nextDouble()))), rnd, 0.06 * len, 3);
                ribbon(vc, m, bp, cam, 0.03F, r, g, b, (int) (70 * flick));
                ribbon(vc, m, bp, cam, 0.008F, (r + 2 * 255) / 3, (g + 2 * 255) / 3, (b + 2 * 255) / 3, (int) (180 * flick));
            }
        }
        // the ring glowing where they leave it
        ring(vc, m, top, cam, r, g, b, (int) (50 + 60 * boost));
    }

    private static Vec3 start(Vec3 top, Vec3 toward) {
        Vec3 h = new Vec3(toward.x, 0.0, toward.z);
        if (h.lengthSqr() < 1e-6) {
            h = new Vec3(1.0, 0.0, 0.0);
        }
        return top.add(h.normalize().scale(RING)).add(0.0, toward.y > 0 ? 0.08 : -0.04, 0.0);
    }

    /** A jagged line from a to b: the middle of each piece pushed aside at random, again and again. */
    private static List<Vec3> bolt(Vec3 a, Vec3 b, Random rnd, double jag, int levels) {
        List<Vec3> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        double amount = jag;
        for (int l = 0; l < levels; l++) {
            List<Vec3> next = new ArrayList<>(pts.size() * 2);
            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 p = pts.get(i), q = pts.get(i + 1);
                next.add(p);
                next.add(p.add(q).scale(0.5).add(rnd.nextGaussian() * amount, rnd.nextGaussian() * amount, rnd.nextGaussian() * amount));
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
            amount *= 0.55;
        }
        return pts;
    }

    /** A strip along the points, turned to face the camera. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, List<Vec3> pts, Vec3 cam, float width, int r, int g, int b, int a) {
        for (int i = 0; i < pts.size() - 1; i++) {
            Vec3 p = pts.get(i), q = pts.get(i + 1);
            Vec3 seg = q.subtract(p);
            Vec3 side = seg.cross(p.add(q).scale(0.5).subtract(cam));
            if (side.lengthSqr() < 1e-10) continue;
            side = side.normalize().scale(width * 0.5);
            // (ends tapering off)
            float ta = i == 0 ? 0.5F : 1.0F, tb = i == pts.size() - 2 ? 0.3F : 1.0F;
            vc.addVertex(m, (float) (p.x - side.x * ta), (float) (p.y - side.y * ta), (float) (p.z - side.z * ta)).setColor(r, g, b, a);
            vc.addVertex(m, (float) (p.x + side.x * ta), (float) (p.y + side.y * ta), (float) (p.z + side.z * ta)).setColor(r, g, b, a);
            vc.addVertex(m, (float) (q.x + side.x * tb), (float) (q.y + side.y * tb), (float) (q.z + side.z * tb)).setColor(r, g, b, a);
            vc.addVertex(m, (float) (q.x - side.x * tb), (float) (q.y - side.y * tb), (float) (q.z - side.z * tb)).setColor(r, g, b, a);
        }
    }

    /** A soft glow round the ring. */
    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 top, Vec3 cam, int r, int g, int b, int a) {
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i <= 24; i++) {
            double ang = i / 24.0 * Math.PI * 2.0;
            pts.add(top.add(Math.cos(ang) * RING, 0.0, Math.sin(ang) * RING));
        }
        ribbon(vc, m, pts, cam, 0.12F, r, g, b, a);
    }

    @Override
    public AABB getRenderBoundingBox(GlowColourBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2.5);
    }

    @Override
    public boolean shouldRenderOffScreen(GlowColourBlockEntity be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
