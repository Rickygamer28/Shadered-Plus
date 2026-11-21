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

    private static final float GAUGE = 0.297F, RAIL_W = 0.047F, RAIL_LO = -0.006F, RAIL_HI = 0.12F;

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
        // the rails and the glowing strip: lengths between each point and the next
        for (int i = 0; i + 1 < n; i++) {
            for (int r = -1; r <= 1; r += 2) {
                float c = r * GAUGE;
                Vec3 a0 = off(s.pts[i], side[i], up[i], c - RAIL_W, RAIL_HI), a1 = off(s.pts[i], side[i], up[i], c + RAIL_W, RAIL_HI);
                Vec3 b0 = off(s.pts[i + 1], side[i + 1], up[i + 1], c - RAIL_W, RAIL_HI), b1 = off(s.pts[i + 1], side[i + 1], up[i + 1], c + RAIL_W, RAIL_HI);
                Vec3 a0l = off(s.pts[i], side[i], up[i], c - RAIL_W, RAIL_LO), a1l = off(s.pts[i], side[i], up[i], c + RAIL_W, RAIL_LO);
                Vec3 b0l = off(s.pts[i + 1], side[i + 1], up[i + 1], c - RAIL_W, RAIL_LO), b1l = off(s.pts[i + 1], side[i + 1], up[i + 1], c + RAIL_W, RAIL_LO);
                quad(vc, o, a0, a1, b1, b0, up[i], 1, bl[i], sl[i], s.cum[i]);
                quad(vc, o, a1l, b1l, b1, a1, side[i], 1, bl[i], sl[i], s.cum[i]);
                quad(vc, o, b0l, a0l, a0, b0, side[i].scale(-1), 1, bl[i], sl[i], s.cum[i]);
            }
            Vec3 g0 = off(s.pts[i], side[i], up[i], -0.0625F, -0.04F), g1 = off(s.pts[i], side[i], up[i], 0.0625F, -0.04F);
            Vec3 h0 = off(s.pts[i + 1], side[i + 1], up[i + 1], -0.0625F, -0.04F), h1 = off(s.pts[i + 1], side[i + 1], up[i + 1], 0.0625F, -0.04F);
            quad(vc, o, g0, g1, h1, h0, up[i], glowMat, 15, 15, s.cum[i]);
        }
        // sleepers, every 0.6 blocks
        float total = s.length();
        int count = Math.max(1, Math.round(total / 0.6F));
        for (int k = 0; k < count; k++) {
            float at = (k + 0.5F) * total / count;
            int i = Math.min(n - 1, Math.round(at / Math.max(total, 1e-3F) * (n - 1)));
            Vec3 p = s.pts[i].add(up[i].scale(-0.053));
            Vec3 t = s.tan[i].scale(0.0625), sd = side[i].scale(0.47), u = up[i].scale(0.047);
            sleeper(vc, o, p, t, sd, u, bl[i], sl[i], at);
        }
    }

    private static Vec3 off(Vec3 p, Vec3 side, Vec3 up, float across, float height) {
        return p.add(side.scale(across)).add(up.scale(height));
    }

    private static void sleeper(VertexConsumer vc, Vec3 o, Vec3 c, Vec3 t, Vec3 s, Vec3 u, int bl, int sl, float v) {
        Vec3[] q = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            q[i] = c.add(t.scale((i & 1) == 0 ? -1 : 1)).add(u.scale((i & 2) == 0 ? -1 : 1)).add(s.scale((i & 4) == 0 ? -1 : 1));
        }
        Vec3 un = u.normalize(), tn = t.normalize(), sn = s.normalize();
        quad(vc, o, q[2], q[3], q[7], q[6], un, 0, bl, sl, v);
        quad(vc, o, q[1], q[5], q[7], q[3], tn, 0, bl, sl, v);
        quad(vc, o, q[0], q[2], q[6], q[4], tn.scale(-1), 0, bl, sl, v);
        quad(vc, o, q[4], q[6], q[7], q[5], sn, 0, bl, sl, v);
        quad(vc, o, q[0], q[1], q[3], q[2], sn.scale(-1), 0, bl, sl, v);
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
            });
        }
        if (!pack && event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            drawPreview();
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
