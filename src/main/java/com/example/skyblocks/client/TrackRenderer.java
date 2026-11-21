package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TrackNetwork;
import com.example.skyblocks.TrackPayloads;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws smooth track (TrackNetwork, the client's copy): each piece built once into a buffer on the graphics card -
 * concrete sleepers, two steel rails, a glowing strip between them, all following its curve, its light as it was
 * (built again every so often) - and drawn each frame with the track shader. A switch's way not taken glows dimmer. With a
 * shader pack on, laid over the pack's image with the block entities (iris/IrisDefer). And the Track Tool's piece to be.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TrackRenderer {
    static ShaderInstance shader;
    /** The track's changed: build it all again. */
    static boolean dirty = true;

    private record Mesh(VertexBuffer vb, Vec3 origin, Vec3 mid, double radius) {}

    private static final Map<Integer, Mesh> MESHES = new HashMap<>();
    private static ByteBufferBuilder bytes;
    private static int lastVersion = Integer.MIN_VALUE;
    private static Level lastLevel;
    private static int age;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "track"), DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
                    s -> shader = s);
        }
    }

    private TrackRenderer() {}

    // ---- building ----

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (mc.level == null) {
                TrackNetwork.client = new TrackNetwork();
            }
            dirty = true;
        }
        if (mc.level == null) {
            clear();
            return;
        }
        TrackNetwork net = TrackNetwork.client;
        age++;
        if (dirty || net.version != lastVersion || age % 200 == 0) {
            dirty = false;
            lastVersion = net.version;
            rebuild(mc.level, net);
        }
        if (age % 5 == 0) {
            lamps(mc.level, net);
        }
    }

    private static void clear() {
        for (Mesh m : MESHES.values()) {
            m.vb.close();
        }
        MESHES.clear();
    }

    private static void rebuild(Level level, TrackNetwork net) {
        clear();
        if (bytes == null) {
            bytes = new ByteBufferBuilder(1 << 18);
        }
        for (TrackNetwork.Seg s : net.segs.values()) {
            Vec3 o = Vec3.atLowerCornerOf(BlockPos.containing(s.pts[0]));
            BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            build(bb, level, net, s, o);
            MeshData mesh = bb.build();
            if (mesh == null) {
                continue;
            }
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(mesh);
            VertexBuffer.unbind();
            Vec3 mid = s.pts[s.pts.length / 2];
            MESHES.put(s.id, new Mesh(vb, o, mid, s.length() / 2.0 + 2.0));
        }
        // a signal post by each switch (its lamp drawn as it changes: drawLamps)
        for (TrackNetwork.Node n : net.nodes.values()) {
            if (!n.isSwitch()) {
                continue;
            }
            Vec3 o = Vec3.atLowerCornerOf(BlockPos.containing(n.pos));
            BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            Vec3 side = lampSide(n), up = new Vec3(0, 1, 0);
            Vec3 base = n.pos.add(side.scale(LAMP_OUT));
            BlockPos lb = BlockPos.containing(base.add(0, 1, 0));
            Light L = new Light(level.getBrightness(LightLayer.BLOCK, lb), level.getBrightness(LightLayer.SKY, lb));
            cube(bb, o, base, n.dir, side, up, 0.0F, 0.0F, 1.15F, 0.06F, 0.06F, 1.25F, 6, L, 0.0F);
            cube(bb, o, base, n.dir, side, up, 0.0F, 0.0F, 2.55F, 0.16F, 0.16F, 0.22F, 6, L, 0.0F);
            cube(bb, o, base, n.dir, side, up, 0.0F, 0.0F, -0.05F, 0.22F, 0.22F, 0.05F, 0, L, 0.0F);
            MeshData mesh = bb.build();
            if (mesh != null) {
                VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(mesh);
                VertexBuffer.unbind();
                MESHES.put(-n.id, new Mesh(vb, o, base, 4.0));
            }
        }
    }

    /** How far out from a switch its signal stands (clear of trains going by), and which side. */
    private static final double LAMP_OUT = 2.0;

    private static Vec3 lampSide(TrackNetwork.Node n) {
        Vec3 s = n.dir.cross(new Vec3(0, 1, 0));
        return s.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : s.normalize();
    }

    /** Switches with a train on them or coming over them (red), by node. */
    private static final java.util.Set<Integer> RED = new java.util.HashSet<>();

    private static void lamps(Level level, TrackNetwork net) {
        RED.clear();
        com.example.skyblocks.TrainSignals.Occupancy occ = com.example.skyblocks.TrainSignals.others(level, null, true);
        for (TrackNetwork.Node n : net.nodes.values()) {
            if (n.isSwitch() && occ.near(n.pos)) {
                RED.add(n.id);
            }
        }
    }

    /** Whether a piece is a switch's way not taken. */
    private static boolean notTaken(TrackNetwork net, TrackNetwork.Seg s) {
        for (int end = 0; end < 2; end++) {
            TrackNetwork.Node n = net.nodes.get(s.node(end));
            if (n != null && n.side(s.sideAt(end)).size() > 1 && n.taken(s.sideAt(end)) != s.id) {
                return true;
            }
        }
        return false;
    }

    private static final float GAUGE = 0.72F;

    /**
     * A piece's mesh: a gravel bed (sloped sides, a bottom where it's off the ground), rails with a foot, a web and a
     * head, a glowing strip down the middle, concrete sleepers every 0.6 blocks with base plates and clips. Parts:
     * 0 sleeper, 1 rail head, 2 strip, 3 strip dimmed (a switch's way not taken), 4 rail sides, 5 gravel, 6 plates and clips.
     */
    private static void build(VertexConsumer vc, Level level, TrackNetwork net, TrackNetwork.Seg s, Vec3 o) {
        int n = s.pts.length;
        Vec3[] side = new Vec3[n], up = new Vec3[n];
        int[] bl = new int[n], sl = new int[n];
        for (int i = 0; i < n; i++) {
            Vec3 t = s.tan[i];
            Vec3 sd = t.cross(new Vec3(0, 1, 0));
            sd = sd.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : sd.normalize();
            side[i] = sd;
            up[i] = sd.cross(t).normalize();
            BlockPos b = BlockPos.containing(s.pts[i].add(0, 0.3, 0));
            bl[i] = level.getBrightness(LightLayer.BLOCK, b);
            sl[i] = level.getBrightness(LightLayer.SKY, b);
        }
        int glowMat = notTaken(net, s) ? 3 : 2;
        for (int i = 0; i + 1 < n; i++) {
            int j = i + 1;
            Light L = new Light(bl[i], sl[i]);
            // the gravel bed: its top, its sloping sides, its bottom
            strip(vc, o, s, side, up, i, j, -1.15F, -0.07F, 1.15F, -0.07F, 5, L);
            strip(vc, o, s, side, up, i, j, 1.15F, -0.07F, 1.45F, -0.3F, 5, L);
            strip(vc, o, s, side, up, i, j, -1.45F, -0.3F, -1.15F, -0.07F, 5, L);
            strip(vc, o, s, side, up, i, j, 1.45F, -0.3F, -1.45F, -0.3F, 5, L);
            // the rails: foot, web, head
            for (int r = -1; r <= 1; r += 2) {
                float c = r * GAUGE;
                strip(vc, o, s, side, up, i, j, c - 0.075F, 0.0F, c + 0.075F, 0.0F, 6, L);
                strip(vc, o, s, side, up, i, j, c + 0.075F, 0.0F, c + 0.075F, -0.012F, 6, L);
                strip(vc, o, s, side, up, i, j, c - 0.075F, -0.012F, c - 0.075F, 0.0F, 6, L);
                strip(vc, o, s, side, up, i, j, c + 0.018F, 0.075F, c + 0.018F, 0.0F, 4, L);
                strip(vc, o, s, side, up, i, j, c - 0.018F, 0.0F, c - 0.018F, 0.075F, 4, L);
                strip(vc, o, s, side, up, i, j, c - 0.042F, 0.125F, c + 0.042F, 0.125F, 1, L);
                strip(vc, o, s, side, up, i, j, c + 0.042F, 0.125F, c + 0.042F, 0.075F, 4, L);
                strip(vc, o, s, side, up, i, j, c - 0.042F, 0.075F, c - 0.042F, 0.125F, 4, L);
                strip(vc, o, s, side, up, i, j, c - 0.018F, 0.075F, c - 0.042F, 0.075F, 4, L);
                strip(vc, o, s, side, up, i, j, c + 0.042F, 0.075F, c + 0.018F, 0.075F, 4, L);
            }
            strip(vc, o, s, side, up, i, j, -0.06F, -0.045F, 0.06F, -0.045F, glowMat, new Light(15, 15));
        }
        // sleepers, every 0.6 blocks: concrete, a plate under each rail, clips either side of its foot
        float total = s.length();
        int count = Math.max(1, Math.round(total / 0.6F));
        for (int k = 0; k < count; k++) {
            float at = (k + 0.5F) * total / count;
            int i = Math.min(n - 1, Math.round(at / Math.max(total, 1e-3F) * (n - 1)));
            Vec3 t = s.tan[i], sd = side[i], u = up[i];
            Light L = new Light(bl[i], sl[i]);
            Vec3 p = s.pts[i];
            cube(vc, o, p, t, sd, u, 0.0F, 0.0F, -0.06F, 0.08F, 1.0F, 0.05F, 0, L, at);
            for (int r = -1; r <= 1; r += 2) {
                float c = r * GAUGE;
                cube(vc, o, p, t, sd, u, 0.0F, c, -0.006F, 0.065F, 0.11F, 0.006F, 6, L, at);
                cube(vc, o, p, t, sd, u, 0.0F, c - 0.1F, 0.01F, 0.03F, 0.025F, 0.02F, 6, L, at);
                cube(vc, o, p, t, sd, u, 0.0F, c + 0.1F, 0.01F, 0.03F, 0.025F, 0.02F, 6, L, at);
            }
        }
    }

    private record Light(int block, int sky) {}

    /** A strip between two points across the track (across, height), from point i to point j. */
    private static void strip(VertexConsumer vc, Vec3 o, TrackNetwork.Seg s, Vec3[] side, Vec3[] up, int i, int j, float a0, float h0, float a1, float h1,
            int mat, Light L) {
        Vec3 p0 = off(s.pts[i], side[i], up[i], a0, h0), p1 = off(s.pts[i], side[i], up[i], a1, h1);
        Vec3 q0 = off(s.pts[j], side[j], up[j], a0, h0), q1 = off(s.pts[j], side[j], up[j], a1, h1);
        // (its face's way out: across the strip turned up a quarter)
        float da = a1 - a0, dh = h1 - h0;
        Vec3 nrm = side[i].scale(-dh).add(up[i].scale(da));
        nrm = nrm.lengthSqr() < 1e-9 ? up[i] : nrm.normalize();
        quad(vc, o, p0, p1, q1, q0, nrm, mat, L.block(), L.sky(), s.cum[i]);
    }

    private static Vec3 off(Vec3 p, Vec3 side, Vec3 up, float across, float height) {
        return p.add(side.scale(across)).add(up.scale(height));
    }

    /** A little box on the track: its middle (along, across, up), half its size each way. */
    private static void cube(VertexConsumer vc, Vec3 o, Vec3 p, Vec3 t, Vec3 s, Vec3 u, float along, float across, float height, float ha, float hc,
            float hh, int mat, Light L, float v) {
        Vec3 c = p.add(t.scale(along)).add(s.scale(across)).add(u.scale(height));
        Vec3 tt = t.scale(ha), ss = s.scale(hc), uu = u.scale(hh);
        Vec3[] q = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            q[i] = c.add(tt.scale((i & 1) == 0 ? -1 : 1)).add(uu.scale((i & 2) == 0 ? -1 : 1)).add(ss.scale((i & 4) == 0 ? -1 : 1));
        }
        int b = L.block(), k = L.sky();
        quad(vc, o, q[2], q[3], q[7], q[6], u, mat, b, k, v);
        quad(vc, o, q[1], q[5], q[7], q[3], t, mat, b, k, v);
        quad(vc, o, q[0], q[2], q[6], q[4], t.scale(-1), mat, b, k, v);
        quad(vc, o, q[4], q[6], q[7], q[5], s, mat, b, k, v);
        quad(vc, o, q[0], q[1], q[3], q[2], s.scale(-1), mat, b, k, v);
    }

    private static void quad(VertexConsumer vc, Vec3 o, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 n, int mat, int bl, int sl, float v) {
        int g = bl * 17, bb = sl * 17;
        float nx = (float) n.x, ny = (float) n.y, nz = (float) n.z;
        vc.addVertex((float) (a.x - o.x), (float) (a.y - o.y), (float) (a.z - o.z)).setUv(0, v).setColor(mat, g, bb, 255).setNormal(nx, ny, nz);
        vc.addVertex((float) (b.x - o.x), (float) (b.y - o.y), (float) (b.z - o.z)).setUv(1, v).setColor(mat, g, bb, 255).setNormal(nx, ny, nz);
        vc.addVertex((float) (c.x - o.x), (float) (c.y - o.y), (float) (c.z - o.z)).setUv(1, v + 0.5F).setColor(mat, g, bb, 255).setNormal(nx, ny, nz);
        vc.addVertex((float) (d.x - o.x), (float) (d.y - o.y), (float) (d.z - o.z)).setUv(0, v + 0.5F).setColor(mat, g, bb, 255).setNormal(nx, ny, nz);
    }

    // ---- drawing ----

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || shader == null || MirrorRenderer.isRenderingReflection()) {
            return;
        }
        boolean pack = com.example.skyblocks.client.iris.IrisCompat.shaderPackInUse();
        if (!pack && event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            draw(new Matrix4f(event.getModelViewMatrix()), new Matrix4f(event.getProjectionMatrix()));
        }
        if (pack && event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            com.example.skyblocks.client.iris.IrisDefer.extra(() -> {
                draw(new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix()));
                drawPreview();
                drawLamps();
            });
        }
        if (!pack && event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            drawPreview();
            drawLamps();
        }
    }

    private static void draw(Matrix4f view, Matrix4f projection) {
        if (MESHES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float sky = mc.level.getSkyDarken(1.0F);
        shader.safeGetUniform("SkyLight").set(sky);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        double far = mc.options.getEffectiveRenderDistance() * 16.0 + 16.0;
        for (Mesh m : MESHES.values()) {
            if (m.mid.distanceTo(cam) > far + m.radius) {
                continue;
            }
            Matrix4f mv = new Matrix4f(view).translate((float) (m.origin.x - cam.x), (float) (m.origin.y - cam.y), (float) (m.origin.z - cam.z));
            m.vb.bind();
            m.vb.drawWithShader(mv, projection, shader);
        }
        VertexBuffer.unbind();
        RenderSystem.enableCull();
    }

    /** The switches' signal lamps: green, clear; red, a train on it or coming. Glowing discs, facing you. */
    private static void drawLamps() {
        Minecraft mc = Minecraft.getInstance();
        if (TrainRenderer.glowShader == null || mc.level == null) {
            return;
        }
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        Vec3 left = new Vec3(camera.getLeftVector()), upv = new Vec3(camera.getUpVector());
        java.util.List<float[]> lamps = new java.util.ArrayList<>();
        for (TrackNetwork.Node n : TrackNetwork.client.nodes.values()) {
            if (!n.isSwitch()) {
                continue;
            }
            Vec3 p = n.pos.add(lampSide(n).scale(LAMP_OUT)).add(0, 2.55, 0);
            if (p.distanceToSqr(cam) > 128 * 128) {
                continue;
            }
            boolean red = RED.contains(n.id);
            lamps.add(new float[] {(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z), red ? 1 : 0});
        }
        if (lamps.isEmpty()) {
            return;
        }
        ElevatorRenderer.drawNow(TrainRenderer.Types.GLOW, vc -> {
            for (float[] l : lamps) {
                int r = l[3] > 0 ? 255 : 50, gc = l[3] > 0 ? 50 : 255, b = l[3] > 0 ? 40 : 90;
                float sz = 0.35F;
                for (int i = 0; i < 4; i++) {
                    double a = (i == 0 || i == 3) ? -1 : 1, c = (i < 2) ? -1 : 1;
                    Vec3 q = left.scale(a * sz).add(upv.scale(c * sz));
                    vc.addVertex(l[0] + (float) q.x, l[1] + (float) q.y, l[2] + (float) q.z).setUv((float) a, 0.0F).setColor(r, gc, b, 255);
                }
            }
        });
    }

    /** The Track Tool's piece to be: a line along it, green if it'll do, red if not. */
    private static void drawPreview() {
        TrackPayloads.Resolved r = TrackTool.preview;
        if (r == null || TrainRenderer.glowShader == null) {
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3[] pts = r.plan().pts();
        boolean ok = r.plan().problem() == null;
        int cr = ok ? 80 : 255, cg = ok ? 255 : 70, cb = ok ? 120 : 60;
        ElevatorRenderer.drawNow(TrainRenderer.Types.GLOW, vc -> {
            for (int i = 0; i + 1 < pts.length; i++) {
                Vec3 a = pts[i].subtract(cam), b = pts[i + 1].subtract(cam);
                Vec3 t = pts[i + 1].subtract(pts[i]);
                Vec3 s = t.cross(new Vec3(0, 1, 0));
                s = s.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : s.normalize().scale(0.5);
                float sa = i * 0.5F;
                vc.addVertex((float) (a.x - s.x), (float) a.y + 0.05F, (float) (a.z - s.z)).setUv(-1, sa).setColor(cr, cg, cb, 230);
                vc.addVertex((float) (a.x + s.x), (float) a.y + 0.05F, (float) (a.z + s.z)).setUv(1, sa).setColor(cr, cg, cb, 230);
                vc.addVertex((float) (b.x + s.x), (float) b.y + 0.05F, (float) (b.z + s.z)).setUv(1, sa + 0.5F).setColor(cr, cg, cb, 230);
                vc.addVertex((float) (b.x - s.x), (float) b.y + 0.05F, (float) (b.z - s.z)).setUv(-1, sa + 0.5F).setColor(cr, cg, cb, 230);
            }
        });
    }
}
