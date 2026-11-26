package com.example.skyblocks.client;

import com.example.skyblocks.PowerCoreBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A power core (PowerCoreBlockEntity): what it holds, in its skin, in 3D - a box (just inside its frame alone; filling its
 * core chamber in one) traced through by shaders/core/energy_core - easing to how full it is; and
 * its light, coloured (ShaderLightRenderer: the star's warm, the storm's pale violet, the plasma's purple, the atom's blue).
 */
public class PowerCoreRenderer implements BlockEntityRenderer<PowerCoreBlockEntity> {
    static ShaderInstance shader;
    /** Each skin's light. */
    private static final int[] LIGHT = {0xFFA040, 0x9A9CFF, 0xC070FF, 0x60C8FF};

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "energy_core"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CORE = create("skyblocks_power_core", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /**
     * The boxes to draw this frame (x, y, z from the camera; u, v; colour) - drawn after the coloured lights have been laid
     * over the picture (LatePass): laid over it, they'd tint what's in front by what's behind (a glowing star showing the
     * frames behind it as a stripe of its own colour).
     */
    private static final java.util.List<float[]> VERTS = new java.util.ArrayList<>();
    private static boolean queued;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class Frames {
        private Frames() {}

        @SubscribeEvent
        public static void onStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
            if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY && !MirrorRenderer.isRenderingReflection()) {
                VERTS.clear();
                queued = false;
            }
        }
    }

    private static void drawAll(MultiBufferSource.BufferSource source) {
        queued = false;
        if (VERTS.isEmpty() || shader == null) {
            VERTS.clear();
            return;
        }
        VertexConsumer vc = source.getBuffer(Types.CORE);
        Matrix4f identity = new Matrix4f();
        for (float[] v : VERTS) {
            vc.addVertex(identity, v[0], v[1], v[2]).setColor((int) v[5], (int) v[6], (int) v[7], 255).setUv(v[3], v[4]);
        }
        VERTS.clear();
        source.endBatch(Types.CORE);
    }

    public PowerCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PowerCoreBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        links(be, pose, buffers);
        arcs(be, pose, buffers);
        readout(be, pose, buffers, light);
        if (shader == null) {
            return;
        }
        float target = be.fullness();
        if (be.shownFull < 0) {
            be.shownFull = target;
        }
        be.shownFull += (target - be.shownFull) * 0.08F;
        float full = Mth.clamp(be.shownFull, 0.0F, 1.0F);
        int chamber = be.chamber();
        // the box it's traced through: just inside its frame alone; in a chamber, the room inside it
        float half = chamber > 0 ? (chamber - 1) / 2.0F - 0.55F : 0.37F;
        int skin = be.skin();
        ShaderLightRenderer.add(pose, 0.5F, 0.5F, 0.5F, chamber > 0 ? chamber + 1.0F + 3.0F * full : 3.0F + 4.0F * full,
                LIGHT[Math.floorMod(skin, LIGHT.length)]);
        int r = skin & 255, g = Math.round(full * 255), b = chamber > 0 ? 255 : 0;
        drawBox(pose, buffers, half, r, g, b);
        if (chamber > 0) {
            veins(be, pose, buffers, chamber); // (after the box: getting another buffer would end its one)
            bands(be, pose, buffers, chamber);
        }
    }

    /**
     * A box traced through by energy_core (r its skin, g how full, b whether in a chamber), {@code half} across round the
     * block's middle - drawn now, or after the coloured lights (LatePass). (The star forge's star too.)
     */
    static void drawBox(PoseStack pose, MultiBufferSource buffers, float half, int r, int g, int b) {
        if (shader == null) {
            return;
        }
        Matrix4f m = pose.last().pose();
        org.joml.Vector3f centre = m.transformPosition(0.5F, 0.5F, 0.5F, new org.joml.Vector3f());
        // (the eye, from the middle: the pose is the block's place from the camera)
        float ex = -centre.x, ey = -centre.y, ez = -centre.z;
        boolean late = queued || LatePass.later(PowerCoreRenderer::drawAll);
        queued = late;
        VertexConsumer vc = late ? null : buffers.getBuffer(Types.CORE);
        org.joml.Vector3f at = new org.joml.Vector3f();

        // each face of the box turned away from the eye - its far side (from inside: all of them): the near side would be
        // cut off by the screen's near plane as you came close (and the ray's traced from the eye in any case) - its
        // corners numbered x + 2y + 4z
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        float[] towards = {-ex - half, ex - half, -ey - half, ey - half, -ez - half, ez - half};
        for (int f = 0; f < 6; f++) {
            if (towards[f] > 0) {
                continue;
            }
            for (int c : faces[f]) {
                float x = ((c & 1) * 2 - 1) * half, y = (((c >> 1) & 1) * 2 - 1) * half, z = (((c >> 2) & 1) * 2 - 1) * half;
                if (vc != null) {
                    vc.addVertex(m, 0.5F + x, 0.5F + y, 0.5F + z).setColor(r, g, b, 255).setUv(c, half);
                } else if (VERTS.size() < 65536) {
                    m.transformPosition(0.5F + x, 0.5F + y, 0.5F + z, at);
                    VERTS.add(new float[] {at.x, at.y, at.z, c, half, r, g, b});
                }
            }
        }
    }

    /**
     * Its energy veins: light pulsing along the outside of its chamber's frame edges - in from the corners towards the
     * middle of each edge while it's charging, out from the middles to the corners while it's giving out, faster the
     * more flows; still and dim with nothing flowing.
     */
    private static void veins(PowerCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, int chamber) {
        long in = be.rateIn(), out = be.rateOut(), net = in - out;
        boolean flowing = in > 0 || out > 0;
        boolean charging = net >= 0;
        float t = (float) LavaLampRenderer.seconds();
        float speed = 0.5F + 2.5F * Mth.clamp((float) Math.log10(1.0 + Math.abs(net) + Math.max(in, out) * 0.2) / 5.0F, 0.0F, 1.0F);
        int rgb = LIGHT[Math.floorMod(be.skin(), LIGHT.length)];
        float cr = (rgb >> 16 & 255) / 255.0F, cg = (rgb >> 8 & 255) / 255.0F, cb = (rgb & 255) / 255.0F;
        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float e = (chamber - 1) / 2.0F, len = e + 0.5F;
        float[] signs = {-1.0F, 1.0F};
        for (int axis = 0; axis < 3; axis++) {
            int b1 = (axis + 1) % 3, b2 = (axis + 2) % 3;
            for (float s1 : signs) {
                for (float s2 : signs) {
                    // the edge along this axis at (s1 e, s2 e): a strip on each of its two outer faces
                    for (int face = 0; face < 2; face++) {
                        int nAxis = face == 0 ? b1 : b2, cAxis = face == 0 ? b2 : b1;
                        // (on the formed frames' surfaces: the capacitor columns up its sides, the girders across)
                        float nOff = (face == 0 ? s1 : s2) * (e + (axis == 1 ? 0.381F : 0.256F)), cOff = (face == 0 ? s2 : s1) * e;
                        // (dim, all along it)
                        strip(v, m, axis, nAxis, cAxis, nOff, cOff, -len, len, 0.05F, cr, cg, cb, flowing ? 0.25F : 0.14F);
                        if (!flowing) {
                            continue;
                        }
                        for (int k = 0; k < 3; k++) {
                            float u = (t * speed / len + k / 3.0F) % 1.0F;
                            float from = charging ? u : 1.0F - u; // (0 at the corners, 1 at the middle)
                            float fade = Mth.sin((float) Math.PI * u);
                            for (float half : signs) {
                                float c = half * (1.0F - from) * len;
                                strip(v, m, axis, nAxis, cAxis, nOff, cOff, c - 0.22F, c + 0.22F, 0.09F, cr, cg, cb, 0.9F * fade);
                                strip(v, m, axis, nAxis, cAxis, nOff, cOff, c - 0.4F, c + 0.4F, 0.2F, cr, cg, cb, 0.25F * fade);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Its capacitor columns' bands (one round each column block up its four upright edges): lit from the bottom up as it
     * fills - the top one lit means it's full - the last lit one breathing while it charges.
     */
    private static void bands(PowerCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, int chamber) {
        int e = (chamber - 1) / 2, n = chamber - 2;
        if (n <= 0) {
            return;
        }
        double fill = be.capacity() <= 0 ? 0.0 : (double) be.energy() / be.capacity();
        int lit = (int) Math.ceil(fill * n - 1.0E-6);
        int rgb = LIGHT[Math.floorMod(be.skin(), LIGHT.length)];
        float cr = (rgb >> 16 & 255) / 255.0F, cg = (rgb >> 8 & 255) / 255.0F, cb = (rgb & 255) / 255.0F;
        float t = (float) LavaLampRenderer.seconds();
        boolean charging = be.rateIn() > be.rateOut();
        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float w = 0.41F, h = 1.0F / 16.0F;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int i = 0; i < n; i++) {
                    float y = -e + 1 + i;
                    boolean on = i < lit;
                    float a = on ? (charging && i == lit - 1 ? 0.55F + 0.4F * Mth.sin(t * 4.0F) : 0.9F) : 0.0F;
                    if (a <= 0.0F) {
                        continue;
                    }
                    float x = sx * e, z = sz * e;
                    for (int side = 0; side < 4; side++) {
                        int nAxis = side < 2 ? 0 : 2, cAxis = side < 2 ? 2 : 0;
                        float nOff = (side < 2 ? x : z) + (side % 2 == 0 ? -w : w), cOff = side < 2 ? z : x;
                        strip(v, m, 1, nAxis, cAxis, nOff, cOff, y - h, y + h, w, cr, cg, cb, a);
                    }
                }
            }
        }
    }

    /**
     * Energy moving through its links: a flickering arc from its chamber's nearest side to each block it's taking from
     * (green) or giving to (orange), thicker the more is flowing.
     */
    /** (The render test: every link drawn as if energy moved through it.) */
    public static boolean testArcs;

    private static void arcs(PowerCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers) {
        if (be.links().isEmpty()) {
            return;
        }
        int r = be.chamber() > 0 ? (be.chamber() - 1) / 2 : 0;
        BlockPos o = be.getBlockPos();
        long tick = System.currentTimeMillis() / 70; // (it jumps about ~14 times a second)
        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(o.getX(), o.getY(), o.getZ());
        for (PowerCoreBlockEntity.Link l : be.links()) {
            if (!testArcs && (l.mode() == PowerCoreBlockEntity.OFF || l.flow() <= 0)) {
                continue;
            }
            float tx = l.pos().getX() - o.getX() + 0.5F, ty = l.pos().getY() - o.getY() + 0.5F, tz = l.pos().getZ() - o.getZ() + 0.5F;
            float edge = r + 0.5F; // (its chamber's outer faces, from its middle)
            float sx = 0.5F + Mth.clamp(tx - 0.5F, -edge, edge), sy = 0.5F + Mth.clamp(ty - 0.5F, -edge, edge), sz = 0.5F + Mth.clamp(tz - 0.5F, -edge, edge);
            int c = PowerCoreScreen.MODE_COLOUR[testArcs && l.mode() == 0 ? (l.canTake() ? 2 : 1) : l.mode()];
            float cr = (c >> 16 & 255) / 255.0F, cg = (c >> 8 & 255) / 255.0F, cb = (c & 255) / 255.0F;
            float width = 0.04F + 0.04F * Math.min(1.0F, (float) Math.log10(1 + l.flow()) / 7.0F);
            java.util.Random rnd = new java.util.Random(l.pos().asLong() * 31 + tick);
            float dx = tx - sx, dy = ty - sy, dz = tz - sz, len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            int n = Math.max(3, (int) (len * 4));
            float px = sx, py = sy, pz = sz;
            for (int i = 1; i <= n; i++) {
                float f = i / (float) n, j = i == n ? 0.0F : 0.22F * Mth.sin(f * Mth.PI);
                float qx = sx + dx * f + (rnd.nextFloat() - 0.5F) * j, qy = sy + dy * f + (rnd.nextFloat() - 0.5F) * j, qz = sz + dz * f + (rnd.nextFloat() - 0.5F) * j;
                ribbon(v, m, cam, px, py, pz, qx, qy, qz, width * 3.5F, cr, cg, cb, 0.3F);
                ribbon(v, m, cam, px, py, pz, qx, qy, qz, width, Math.min(1, cr + 0.4F), Math.min(1, cg + 0.4F), Math.min(1, cb + 0.4F), 0.85F);
                px = qx;
                py = qy;
                pz = qz;
            }
        }
    }

    /** A flat strip from one point to another, turned to face you. */
    private static void ribbon(VertexConsumer v, Matrix4f m, Vec3 cam, float ax, float ay, float az, float bx, float by, float bz, float w, float r, float g,
                               float b, float a) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float vx = (float) cam.x - ax, vy = (float) cam.y - ay, vz = (float) cam.z - az;
        float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-5F) {
            return;
        }
        sx *= w / sl;
        sy *= w / sl;
        sz *= w / sl;
        v.addVertex(m, ax - sx, ay - sy, az - sz).setColor(r, g, b, a);
        v.addVertex(m, ax + sx, ay + sy, az + sz).setColor(r, g, b, a);
        v.addVertex(m, bx + sx, by + sy, bz + sz).setColor(r, g, b, a);
        v.addVertex(m, bx - sx, by - sy, bz - sz).setColor(r, g, b, a);
        v.addVertex(m, bx - sx, by - sy, bz - sz).setColor(r, g, b, a);
        v.addVertex(m, bx + sx, by + sy, bz + sz).setColor(r, g, b, a);
        v.addVertex(m, ax + sx, ay + sy, az + sz).setColor(r, g, b, a);
        v.addVertex(m, ax - sx, ay - sy, az - sz).setColor(r, g, b, a);
    }

    /**
     * A readout plate on each side of its chamber you're outside of, low in the middle: how full (a bar in its colour and
     * the percent), how much it holds, and energy in or out each tick.
     */
    private static void readout(PowerCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light) {
        if (be.chamber() <= 0) {
            return;
        }
        int r = (be.chamber() - 1) / 2;
        Minecraft mc = Minecraft.getInstance();
        BlockPos o = be.getBlockPos();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition().subtract(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        double[] facing = {cam.z, cam.x, -cam.z, -cam.x};
        double full = be.capacity() <= 0 ? 0 : Math.min(1.0, (double) be.energy() / be.capacity());
        long flow = be.rateIn() - be.rateOut();
        String pct = String.format(java.util.Locale.ROOT, full >= 0.9995 ? "100%%" : "%.1f%%", full * 100);
        String rate = (flow > 0 ? "+" : flow < 0 ? "-" : "") + PowerCoreBlockEntity.fe(Math.abs(flow)) + "/t";
        int rateColour = flow > 0 ? 0xFF8FEFA0 : flow < 0 ? 0xFFFF8A7A : 0xFF8A96AA;
        int accent = LIGHT[Math.floorMod(be.skin(), LIGHT.length)] | 0xFF000000;
        net.minecraft.client.gui.Font font = mc.font;
        float pw = 96, ph = 24, scale = 0.016F;
        for (int side = 0; side < 4; side++) {
            if (facing[side] < r + 0.5) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.5F, -r + 0.5F, 0.5F);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * 90.0F));
            pose.translate(0.0F, 0.0F, r + 0.5F + 0.012F);
            pose.scale(scale, -scale, scale);
            Matrix4f m = pose.last().pose();
            VertexConsumer bg = buffers.getBuffer(RenderType.textBackground());
            float x0 = -pw / 2, x1 = pw / 2, y0 = -ph / 2, y1 = ph / 2;
            plate(bg, m, x0 - 1, y0 - 1, x1 + 1, y1 + 1, -0.4F, 0xFF4A3A26, light);
            plate(bg, m, x0, y0, x1, y1, -0.3F, 0xFF8A6A3A, light);
            plate(bg, m, x0 + 1, y0 + 1, x1 - 1, y1 - 1, -0.2F, 0xFF121620, light);
            plate(bg, m, x0 + 4, y0 + 13, x1 - 4, y0 + 16, -0.15F, 0xFF0B0F17, light);
            plate(bg, m, x0 + 4, y0 + 13, x0 + 4 + (pw - 8) * (float) full, y0 + 16, -0.1F, accent, light);
            font.drawInBatch(pct, x0 + 4, y0 + 3, 0xFFE6EDF7, false, m, buffers, net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, 0, light);
            font.drawInBatch(rate, x1 - 4 - font.width(rate), y0 + 3, rateColour, false, m, buffers,
                    net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, 0, light);
            pose.popPose();
        }
    }

    /** A flat rectangle (a plate) at depth z, seen from both sides. */
    private static void plate(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb, int light) {
        v.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
        v.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
    }

    /**
     * Its links page open: each block near it that holds energy outlined in the world - green taking from, orange giving
     * to, grey off - the one the mouse is on in the list pulsing, a little larger.
     */
    private static void links(PowerCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers) {
        if (!PowerCoreScreen.linksOpen(be.getBlockPos())) {
            return;
        }
        VertexConsumer v = buffers.getBuffer(RenderType.lines());
        BlockPos o = be.getBlockPos();
        BlockPos hover = PowerCoreScreen.hoveredLink();
        float t = (float) LavaLampRenderer.seconds();
        for (PowerCoreBlockEntity.Link l : be.links()) {
            int c = PowerCoreScreen.MODE_COLOUR[l.mode()];
            boolean on = l.pos().equals(hover);
            double grow = on ? 0.04 + 0.03 * Math.sin(t * 8) : 0.002;
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(l.pos().subtract(o)).inflate(grow);
            net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, v, box, (c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F,
                    on ? 1.0F : 0.75F);
        }
    }

    /** A glowing strip on a face: along {@code axis} from a to b, {@code w} wide across {@code cAxis}, at nOff out along nAxis. */
    private static void strip(VertexConsumer v, Matrix4f m, int axis, int nAxis, int cAxis, float nOff, float cOff, float a, float b, float w,
                              float r, float g, float bl, float alpha) {
        float[][] p = new float[4][3];
        float[] along = {a, b, b, a}, across = {-w, -w, w, w};
        for (int i = 0; i < 4; i++) {
            p[i][axis] = along[i];
            p[i][cAxis] = cOff + across[i];
            p[i][nAxis] = nOff;
        }
        int[] order = {0, 1, 2, 3, 3, 2, 1, 0}; // (both ways round: seen from either side)
        for (int i : order) {
            v.addVertex(m, 0.5F + p[i][0], 0.5F + p[i][1], 0.5F + p[i][2]).setColor(r, g, bl, alpha);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PowerCoreBlockEntity be) {
        return be.chamber() > 0;
    }

    @Override
    public AABB getRenderBoundingBox(PowerCoreBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(Math.max(1, (be.chamber() - 1) / 2.0) + be.chamber() + 7.0); // (as far as its light reaches)
    }
}
