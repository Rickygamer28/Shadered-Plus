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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
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
    static final double TOP_Y = 13.25 / 16.0, RING = 0.46;
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
        // the bolts: each one's shape wanders smoothly (its big bends slowly, its small ones quicker), and those
        // crackling into the air fade in, flicker a while and fade out, coming back somewhere else
        float t = LavaLampRenderer.seconds();
        long seed = pos.asLong() * 0x9E3779B97F4A7C15L;
        List<Arc> arcs = new ArrayList<>();
        int free = 4 + Math.round(boost * 5.0F);
        for (int i = 0; i < free; i++) {
            double hi = h(seed, i, 1);
            double period = 0.8 + 0.7 * hi;
            double ph = t / period + h(seed, i, 2);
            long cycle = (long) Math.floor(ph);
            double u = ph - cycle;
            double fade = smooth(0.0, 0.15, u) * (1.0 - smooth(0.65, 1.0, u));
            double a = Math.PI * 2.0 * h(seed + cycle * 7919L, i, 3) + 0.5 * noise(t * 0.6 + i * 3.7);
            double y = -0.25 + 1.1 * h(seed + cycle * 7919L, i, 4) + 0.15 * noise(t * 0.5 + i * 5.1 + 20.0);
            double hr = Math.sqrt(Math.max(0.05, 1.0 - Math.min(y * y, 0.95)));
            Vec3 dir = new Vec3(Math.cos(a) * hr, y, Math.sin(a) * hr).normalize();
            double len = (0.6 + 0.9 * h(seed + cycle * 7919L, i, 5) + boost * 0.9) * (0.9 + 0.1 * noise(t * 1.3 + i));
            arcs.add(new Arc(start(top, dir), top.add(dir.scale(len + RING)), i * 31.0 + cycle * 0.0, fade));
        }
        // to the players near (a hand's height), and the mobs near
        for (Player p : be.getLevel().players()) {
            Vec3 hand = p.getEyePosition(partialTick).add(0.0, -0.55, 0.0).add(p.getViewVector(partialTick).scale(0.25)).subtract(origin);
            if (hand.distanceTo(top) < PLAYER_REACH && !p.isSpectator()) {
                double key = 500.0 + p.getId() * 13.0;
                arcs.add(new Arc(start(top, hand.subtract(top)), hand, key, 1.0));
                arcs.add(new Arc(start(top, hand.subtract(top)), hand, key + 77.0, 0.5 + 0.5 * noise(t * 0.8 + key)));
            }
        }
        int mobs = 0;
        for (LivingEntity e : be.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(MOB_REACH), e -> !(e instanceof Player) && e.isAlive())) {
            Vec3 at = e.getPosition(partialTick).add(0.0, e.getBbHeight() * 0.6, 0.0).subtract(origin);
            if (at.distanceTo(top) < MOB_REACH && mobs++ < 3) {
                arcs.add(new Arc(start(top, at.subtract(top)), at, 900.0 + e.getId() * 17.0, 1.0));
            }
        }
        // to metal blocks near (any metal: iron, copper, gold, chains, anvils... and other mods' metals), coming and going
        for (Vec3 at : metalNear(be.getLevel(), pos, gameTime)) {
            Vec3 target = at.subtract(origin);
            double key = 2000.0 + (long) (at.x * 7.0 + at.y * 131.0 + at.z * 977.0) % 4096;
            double on = smooth(-0.6, 0.1, noise(t * 0.55 + key)); // (on most of the time)
            arcs.add(new Arc(start(top, target.subtract(top)), target, key, on));
        }
        // between coils (drawn by one of each pair), coming and going
        for (Map.Entry<BlockPos, Long> e : COILS.entrySet()) {
            BlockPos o = e.getKey();
            if (o.asLong() <= pos.asLong() || o.distSqr(pos) > COIL_REACH * COIL_REACH) {
                continue;
            }
            Vec3 otherTop = Vec3.atLowerCornerOf(o).subtract(origin).add(0.5, TOP_Y, 0.5);
            Vec3 d = otherTop.subtract(top);
            double key = 1300.0 + (o.asLong() & 1023L);
            double on = smooth(-0.2, 0.3, noise(t * 0.7 + key));
            arcs.add(new Arc(start(top, d), start(otherTop, d.scale(-1.0)), key, on));
        }

        VertexConsumer vc = buffers.getBuffer(Types.ARC);
        Matrix4f m = poseStack.last().pose();
        int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
        int cr = (r + 2 * 255) / 3, cg = (g + 2 * 255) / 3, cb = (b + 2 * 255) / 3;
        for (Arc arc : arcs) {
            if (arc.fade < 0.02) continue;
            double len = arc.a.distanceTo(arc.b);
            List<Vec3> pts = bolt(arc.a, arc.b, arc.key, t, Math.min(0.17 * len, 0.6), Math.min(6, 4 + (int) Math.ceil(len)));
            double flick = (0.78 + 0.22 * noise(t * 9.0 + arc.key)) * arc.fade;
            ribbon(vc, m, pts, cam, 0.09F * (1.0F + boost * 0.4F), r, g, b, (int) (38 * flick));
            ribbon(vc, m, pts, cam, 0.032F, r, g, b, (int) (115 * flick));
            ribbon(vc, m, pts, cam, 0.011F, cr, cg, cb, (int) (255 * flick));
            // a branch or two off the longer ones, swaying with it
            int branches = len > 0.8 ? 1 + (int) (2.0 * h((long) arc.key, 7, 8)) : 0;
            for (int k = 0; k < branches && pts.size() > 6; k++) {
                int at = 2 + (int) ((pts.size() - 5) * h((long) arc.key, k, 9));
                Vec3 from = pts.get(at);
                Vec3 along = arc.b.subtract(arc.a).normalize();
                Vec3 dir = along.add(0.7 * noise(t * 0.9 + arc.key + k * 5.0), 0.7 * noise(t * 0.8 + arc.key + k * 9.0 + 3.0), 0.7 * noise(t * 0.85 + arc.key + k * 13.0 + 6.0)).normalize();
                List<Vec3> bp = bolt(from, from.add(dir.scale(len * (0.22 + 0.15 * h((long) arc.key, k, 10)))), arc.key + 40.0 + k, t, 0.05 * len, 3);
                ribbon(vc, m, bp, cam, 0.028F, r, g, b, (int) (65 * flick));
                ribbon(vc, m, bp, cam, 0.008F, cr, cg, cb, (int) (170 * flick));
            }
        }
        // the ring glowing where they leave it
        ring(vc, m, top, cam, r, g, b, (int) (50 + 60 * boost));
    }

    // ---- the metal blocks near a coil ----

    private static final double METAL_REACH = 4.5;
    private static final int METAL_MAX = 4;
    private static final Map<BlockPos, long[]> SCANNED = new HashMap<>();
    private static final Map<BlockPos, List<Vec3>> METAL = new HashMap<>();
    private static final Map<BlockState, Boolean> IS_METAL = new java.util.IdentityHashMap<>();
    /** Words in a block's name, or its tags', that make it metal. */
    private static final java.util.Set<String> METAL_WORDS = java.util.Set.of("iron", "steel", "copper", "gold", "golden", "tin", "bronze",
            "aluminum", "aluminium", "lead", "silver", "nickel", "zinc", "brass", "titanium", "platinum", "osmium", "uranium", "cobalt",
            "netherite", "invar", "electrum", "constantan", "metal", "metallic", "tungsten", "chrome", "chromium", "signalum", "lumium",
            "enderium", "manasteel", "terrasteel", "elementium", "refined", "anvil", "chain", "lantern", "rail", "bars", "cauldron", "hopper");

    /** The nearest metal blocks round a coil (the closest point of each to its ring), looked for again every second. */
    private static List<Vec3> metalNear(net.minecraft.world.level.Level level, BlockPos pos, long gameTime) {
        long[] scanned = SCANNED.get(pos);
        List<Vec3> found = METAL.get(pos);
        if (scanned != null && found != null && gameTime - scanned[0] < 20 && gameTime >= scanned[0]) {
            return found;
        }
        if (SCANNED.size() > 512) {
            SCANNED.clear();
            METAL.clear();
        }
        Vec3 top = Vec3.atLowerCornerOf(pos).add(0.5, TOP_Y, 0.5);
        List<Vec3> list = new ArrayList<>();
        List<Double> dist = new ArrayList<>();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int r = (int) Math.ceil(METAL_REACH);
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    q.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState st = level.getBlockState(q);
                    if (st.isAir() || st.getBlock() instanceof TeslaCoilBlock || !isMetal(st)) continue;
                    // the block's closest point to the ring (its shape's box)
                    net.minecraft.world.phys.shapes.VoxelShape shape = st.getShape(level, q);
                    AABB box = shape.isEmpty() ? new AABB(q) : shape.bounds().move(q);
                    Vec3 c = new Vec3(Math.max(box.minX, Math.min(top.x, box.maxX)), Math.max(box.minY, Math.min(top.y, box.maxY)),
                            Math.max(box.minZ, Math.min(top.z, box.maxZ)));
                    double d = c.distanceTo(top);
                    if (d > METAL_REACH || d < 0.3) continue;
                    int i = 0;
                    while (i < dist.size() && dist.get(i) < d) i++;
                    if (i < METAL_MAX) {
                        list.add(i, c);
                        dist.add(i, d);
                        if (list.size() > METAL_MAX) {
                            list.remove(METAL_MAX);
                            dist.remove(METAL_MAX);
                        }
                    }
                }
            }
        }
        SCANNED.put(pos.immutable(), new long[] {gameTime});
        METAL.put(pos.immutable(), list);
        return list;
    }

    /**
     * Whether a block is metal: by the sound it makes (metal, copper, chains, anvils, lanterns, netherite - most mods'
     * metal blocks use these), by its name (iron_block, steel_casing, tin_block, ... - not ores), or by its tags
     * (c:storage_blocks/silver and the like).
     */
    static boolean isMetal(BlockState st) {
        Boolean known = IS_METAL.get(st);
        if (known != null) {
            return known;
        }
        boolean metal = false;
        net.minecraft.world.level.block.SoundType sound = st.getSoundType();
        if (sound == net.minecraft.world.level.block.SoundType.METAL || sound == net.minecraft.world.level.block.SoundType.COPPER
                || sound == net.minecraft.world.level.block.SoundType.ANVIL || sound == net.minecraft.world.level.block.SoundType.CHAIN
                || sound == net.minecraft.world.level.block.SoundType.LANTERN || sound == net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK
                || sound == net.minecraft.world.level.block.SoundType.COPPER_BULB || sound == net.minecraft.world.level.block.SoundType.COPPER_GRATE) {
            metal = true;
        }
        if (!metal) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock());
            metal = metalName(id.getPath());
        }
        if (!metal) {
            metal = st.getTags().anyMatch(tag -> {
                String path = tag.location().getPath();
                return path.startsWith("storage_blocks/") && metalName(path.substring("storage_blocks/".length()));
            });
        }
        IS_METAL.put(st, metal);
        return metal;
    }

    private static boolean metalName(String path) {
        String[] words = path.split("[_/]");
        boolean metal = false;
        for (String w : words) {
            if (w.equals("ore") || w.equals("ores") || w.equals("nugget") || w.equals("glass") || w.equals("wool")) {
                return false; // (an ore isn't a block of metal; nor golden glass, ...)
            }
            if (METAL_WORDS.contains(w)) {
                metal = true;
            }
        }
        return metal;
    }

    private static Vec3 start(Vec3 top, Vec3 toward) {
        Vec3 h = new Vec3(toward.x, 0.0, toward.z);
        if (h.lengthSqr() < 1e-6) {
            h = new Vec3(1.0, 0.0, 0.0);
        }
        return top.add(h.normalize().scale(RING)).add(0.0, toward.y > 0 ? 0.08 : -0.04, 0.0);
    }

    /** One bolt: a target to reach for, its own key (for its shape), how strongly it shows now. */
    private record Arc(Vec3 a, Vec3 b, double key, double fade) {}

    /**
     * A jagged line from a to b: the middle of each piece pushed aside, again and again, by amounts that drift
     * smoothly with time - the big bends slowly, the small kinks quicker (the bolt seems to writhe, not jump).
     */
    private static List<Vec3> bolt(Vec3 a, Vec3 b, double key, float t, double jag, int levels) {
        List<Vec3> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        double amount = jag, speed = 1.6;
        for (int l = 0; l < levels; l++) {
            List<Vec3> next = new ArrayList<>(pts.size() * 2);
            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 p = pts.get(i), q = pts.get(i + 1);
                next.add(p);
                double s = key * 1.37 + l * 11.3 + i * 3.17;
                next.add(p.add(q).scale(0.5).add(noise(t * speed + s) * amount, noise(t * speed + s + 57.1) * amount, noise(t * speed + s + 113.7) * amount));
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
            amount *= 0.55;
            speed *= 1.65;
        }
        return pts;
    }

    /** Smooth noise, -1..1. */
    private static double noise(double x) {
        double i = Math.floor(x), f = x - i;
        double u = f * f * (3.0 - 2.0 * f);
        return lerp(hash((long) i), hash((long) i + 1), u);
    }

    private static double lerp(double a, double b, double u) {
        return a + (b - a) * u;
    }

    private static double hash(long n) {
        n = (n ^ (n >>> 33)) * 0xff51afd7ed558ccdL;
        n = (n ^ (n >>> 33)) * 0xc4ceb9fe1a85ec53L;
        n ^= n >>> 33;
        return ((n >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    /** A number 0..1 from three. */
    private static double h(long seed, int i, int salt) {
        return hash(seed * 31L + i * 1000003L + salt * 7919L) * 0.5 + 0.5;
    }

    private static double smooth(double e0, double e1, double x) {
        double u = Math.max(0.0, Math.min(1.0, (x - e0) / (e1 - e0)));
        return u * u * (3.0 - 2.0 * u);
    }

    /** A strip along the points, turned to face the camera. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, List<Vec3> pts, Vec3 cam, float width, int r, int g, int b, int a) {
        for (int i = 0; i < pts.size() - 1; i++) {
            Vec3 p = pts.get(i), q = pts.get(i + 1);
            Vec3 seg = q.subtract(p);
            Vec3 side = seg.cross(p.add(q).scale(0.5).subtract(cam));
            if (side.lengthSqr() < 1e-10) continue;
            // (thinner right by the camera: a bolt to your hand shouldn't fill the screen)
            double toCam = p.add(q).scale(0.5).distanceTo(cam);
            side = side.normalize().scale(width * 0.5 * Math.min(1.0, 0.25 + toCam / 1.6));
            // (ends tapering off)
            float ta = i == 0 ? 0.5F : 1.0F, tb = i == pts.size() - 2 ? 0.3F : 1.0F;
            vc.vertex(m, (float) (p.x - side.x * ta), (float) (p.y - side.y * ta), (float) (p.z - side.z * ta)).color(r, g, b, a).endVertex();
            vc.vertex(m, (float) (p.x + side.x * ta), (float) (p.y + side.y * ta), (float) (p.z + side.z * ta)).color(r, g, b, a).endVertex();
            vc.vertex(m, (float) (q.x + side.x * tb), (float) (q.y + side.y * tb), (float) (q.z + side.z * tb)).color(r, g, b, a).endVertex();
            vc.vertex(m, (float) (q.x - side.x * tb), (float) (q.y - side.y * tb), (float) (q.z - side.z * tb)).color(r, g, b, a).endVertex();
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

    /** (1.20.1: the block entity's getRenderBoundingBox says - kept for reference.) */
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
