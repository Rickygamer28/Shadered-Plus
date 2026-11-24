package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SpinPadBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * A spin pad (SpinPadBlockEntity): its plate turning with the cat on it, and once it's going, an aura (shaders/core/spin_aura,
 * adding light): a hypnotic spiral of the pad's sky wheeling round on the floor, wider the faster it spins, and round the
 * cat, comet streaks of colour whipping round with it and rings of light rising off the plate.
 */
public class SpinPadRenderer implements BlockEntityRenderer<SpinPadBlockEntity> {
    private static final ResourceLocation PLATE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/block/spin_pad_plate.png");
    /** The ring round the cat's mesh: steps round it. */
    private static final int AROUND = 48;

    static net.minecraft.client.renderer.ShaderInstance shader;

    @net.neoforged.fml.common.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,
            value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @net.neoforged.bus.api.SubscribeEvent
        public static void register(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "spin_aura"),
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR, sh -> shader = sh);
        }
    }

    private static final java.util.Map<String, RenderType> VORTEX = new java.util.HashMap<>();

    /** The vortex drawn with a sky's picture (Shadered's, by name), brought up to date. */
    @org.jetbrains.annotations.Nullable
    private static RenderType vortexType(String sky) {
        int index;
        try {
            index = com.noodlegamer76.shadered.client.util.SkyblockType.valueOf(sky).ordinal();
        } catch (RuntimeException e) {
            return null;
        }
        try {
            // (the vortex shows only a band across the middle of the picture: spin_aura.fsh)
            SkyUsage.ensureBand(com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(com.noodlegamer76.shadered.client.util.SkyblockType.values()[index]), 0.35F, 0.65F);
        } catch (RuntimeException ignored) {
            // (drawn as it is)
        }
        return VORTEX.computeIfAbsent(sky, k -> VortexTypes.of(index, ShaderPaintRender.picture(index)));
    }

    abstract static class VortexTypes extends RenderType {
        static RenderType of(int sky, ResourceLocation picture) {
            return create("skyblocks_spin_aura_" + sky, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 8192, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTextureState(new TextureStateShard(picture, false, false))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY) // (adding light: its colour as it is, alpha too - see spin_aura.fsh)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }

        private VortexTypes() {
            super("", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private static void ring(VertexConsumer vc, Matrix4f m, float a, float r, float y, float u, float v, int speed, int turn, int alpha) {
        vc.addVertex(m, 0.5F + Mth.cos(a) * r, y, 0.5F + Mth.sin(a) * r).setUv(u, v).setColor(speed, turn, 255, alpha);
    }

    public SpinPadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SpinPadBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.master() != null) {
            return; // (one of a big pad's other pads: its master draws the big plate over it)
        }
        int n = Math.max(1, be.side());
        float turn = be.turn(partialTick);
        float s = be.clientSpeed;
        float c = n / 2.0F; // (its middle: a big pad's, over its whole square)
        // with a shader pack: the plate and cat now, in the pack's picture; the aura (a shader of its own: the pack would
        // hide it) drawn over the pack's image afterwards (iris/IrisDefer - this called again, drawing() then: the aura only)
        boolean auraOnly = com.example.skyblocks.client.iris.IrisDefer.drawing();
        if (!auraOnly && com.example.skyblocks.client.iris.IrisCompat.shaderPackInUse() && s >= 0.03F) {
            com.example.skyblocks.client.iris.IrisDefer.deferPart(be, partialTick, pose);
        }
        if (!auraOnly) {
            drawPlateAndCat(be, pose, buffers, light, n, c, turn);
        }
        if (s < 0.03F || (!auraOnly && com.example.skyblocks.client.iris.IrisCompat.shaderPackInUse())) {
            return;
        }
        // the aura (shaders/core/spin_aura): the spiral on the floor and the streaks round the cat - wider, taller and
        // brighter the faster it spins (a big pad's: all of it as big as the pad)
        // (off the screen - it's drawn even then, it reaches past its pad - nothing: nor its sky's picture brought up to date)
        net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BlockPos bp = be.getBlockPos();
        float reach = (0.5F + 2.2F * s) * n, height = (1.3F + 0.6F * s + 0.3F) * n;
        float mx = (float) (bp.getX() + c - cam.x), mz = (float) (bp.getZ() + c - cam.z), my = (float) (bp.getY() - cam.y);
        if (SkyUsage.rectOfBox(mx - reach, my, mz - reach, mx + reach, my + height, mz + reach) == SkyUsage.OFF) {
            return;
        }
        RenderType aura = vortexType(be.sky());
        if (aura == null || shader == null) {
            return;
        }
        pose.pushPose();
        pose.translate(c - 0.5F * n, 0, c - 0.5F * n);
        pose.scale(n, n, n);
        Matrix4f m = pose.last().pose();
        float turns = turn / 360.0F;
        int sp = Math.round(Mth.clamp(s, 0.0F, 1.0F) * 255), gTurn = Math.round((turns - (float) Math.floor(turns)) * 255) & 255;
        int alpha = Math.round(Mth.clamp(s * 2.5F, 0.0F, 1.0F) * 255);
        VertexConsumer vc = buffers.getBuffer(aura);
        float disc = 0.5F + 2.2F * s, y = 0.02F / n; // (on the ground round the pad)
        vc.addVertex(m, 0.5F - disc, y, 0.5F - disc).setUv(0, 0).setColor(sp, gTurn, 0, alpha);
        vc.addVertex(m, 0.5F - disc, y, 0.5F + disc).setUv(0, 1).setColor(sp, gTurn, 0, alpha);
        vc.addVertex(m, 0.5F + disc, y, 0.5F + disc).setUv(1, 1).setColor(sp, gTurn, 0, alpha);
        vc.addVertex(m, 0.5F + disc, y, 0.5F - disc).setUv(1, 0).setColor(sp, gTurn, 0, alpha);
        float radius = 0.55F + 0.2F * s, bottom = 0.3F, top = bottom + 0.9F + 0.6F * s;
        for (int i = 0; i < AROUND; i++) {
            float a0 = i * Mth.TWO_PI / AROUND, a1 = (i + 1) * Mth.TWO_PI / AROUND;
            float u0 = i / (float) AROUND, u1 = (i + 1) / (float) AROUND;
            ring(vc, m, a0, radius, bottom, u0, 0, sp, gTurn, alpha);
            ring(vc, m, a1, radius, bottom, u1, 0, sp, gTurn, alpha);
            ring(vc, m, a1, radius, top, u1, 1, sp, gTurn, alpha);
            ring(vc, m, a0, radius, top, u0, 1, sp, gTurn, alpha);
        }
        pose.popPose();
    }

    private static void drawPlateAndCat(SpinPadBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, int n, float c, float turn) {
        // the plate, turning
        pose.pushPose();
        pose.translate(c, 4.02F / 16.0F, c);
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
        VertexConsumer plate = buffers.getBuffer(RenderType.entityCutout(be.style() == 2 ? VINYL : PLATE));
        float r = n * 0.5F - 1.0F / 16.0F;
        plateVertex(plate, pose, -r, -r, 0, 0, light);
        plateVertex(plate, pose, -r, r, 0, 1, light);
        plateVertex(plate, pose, r, r, 1, 1, light);
        plateVertex(plate, pose, r, -r, 1, 0, light);
        pose.popPose();
        // the cats in its screen: one cat, as big as the cube root of how many, sat on the plate turning with it
        if (be.catCount() > 0) {
            drawCat(be, pose, c, be.catSize(), turn);
        }
        extras(be, pose, buffers, light, n, c, turn);
    }

    // ---- its looks (disco, vinyl) and its gauge ----

    private static final ResourceLocation VINYL = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/block/spin_pad_vinyl.png");
    private static final ResourceLocation METAL = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
    private static final float PLATE_Y = 4.02F / 16.0F;

    private static void extras(SpinPadBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, int n, float c, float turn) {
        float s = Mth.clamp(be.clientSpeed, 0.0F, 1.0F);
        float t = (float) LavaLampRenderer.seconds();
        float r = n * 0.5F - 1.0F / 16.0F;
        if (be.style() == 1) {
            disco(pose, buffers, n, c, r, turn, s, t);
        } else if (be.style() == 2) {
            vinyl(be, pose, buffers, light, n, c, r, s, t);
        }
        if (be.gauge()) {
            gauge(be, pose, buffers, n, c, s, t);
        }
    }

    private static int hue(float h, float sat, float val) {
        return Mth.hsvToRgb(((h % 1.0F) + 1.0F) % 1.0F, sat, val);
    }

    /** A flat glowing quad (both ways round), given its corners. */
    private static void glow(VertexConsumer v, Matrix4f m, float[][] p, int rgb, float a0, float a1) {
        float r = (rgb >> 16 & 255) / 255.0F, g = (rgb >> 8 & 255) / 255.0F, b = (rgb & 255) / 255.0F;
        float[] alpha = {a0, a0, a1, a1};
        int[] order = {0, 1, 2, 3, 3, 2, 1, 0};
        for (int i : order) {
            v.addVertex(m, p[i][0], p[i][1], p[i][2]).setColor(r, g, b, alpha[i]);
        }
    }

    /**
     * Disco: the plate's a floor of lights, colours sweeping round it from its middle, and spotlights from the pad's
     * corners swinging about overhead - all faster and brighter the faster it spins.
     */
    private static void disco(PoseStack pose, MultiBufferSource buffers, int n, float c, float r, float turn, float s, float t) {
        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.translate(c, PLATE_Y + 0.004F, c);
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
        Matrix4f m = pose.last().pose();
        int cells = 6 * n;
        float cell = 2 * r / cells, bright = 0.3F + 0.7F * s;
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                float gx = -r + (i + 0.5F) * cell, gz = -r + (j + 0.5F) * cell;
                float ang = (float) Math.atan2(gz, gx), d = Mth.sqrt(gx * gx + gz * gz) / r;
                int rgb = hue(ang / Mth.TWO_PI + d * 0.35F - t * (0.15F + 0.9F * s), 0.85F, 1.0F);
                float x0 = gx - cell * 0.38F, x1 = gx + cell * 0.38F, z0 = gz - cell * 0.38F, z1 = gz + cell * 0.38F;
                float on = ((i + j) & 1) == 0 ? bright : bright * 0.35F;
                glow(v, m, new float[][] {{x0, 0, z0}, {x1, 0, z0}, {x1, 0, z1}, {x0, 0, z1}}, rgb, on, on);
            }
        }
        pose.popPose();
        if (s < 0.05F) {
            return;
        }
        Matrix4f w = pose.last().pose();
        float[][] corners = {{c - r, c - r}, {c + r, c - r}, {c - r, c + r}, {c + r, c + r}};
        for (int k = 0; k < 4; k++) {
            float bx = corners[k][0], bz = corners[k][1];
            float tx = bx + Mth.sin(t * (0.8F + s) + k * 1.7F) * 2.2F * n, tz = bz + Mth.cos(t * (0.7F + s) + k * 2.3F) * 2.2F * n;
            float ty = 6.5F * n;
            int rgb = hue(k * 0.25F + t * 0.12F, 0.8F, 1.0F);
            float a = 0.45F * s;
            for (int q = 0; q < 2; q++) {
                float dx = q == 0 ? 0.06F : 0.0F, dz = q == 0 ? 0.0F : 0.06F;
                float wx = q == 0 ? 0.55F : 0.0F, wz = q == 0 ? 0.0F : 0.55F;
                glow(v, w, new float[][] {{bx - dx, PLATE_Y, bz - dz}, {bx + dx, PLATE_Y, bz + dz}, {tx + wx, ty, tz + wz}, {tx - wx, ty, tz - wz}}, rgb, a, 0.0F);
            }
        }
    }

    /**
     * Vinyl: the plate's a record (its own picture, turning), a shine lying still across it as it turns, a tonearm on it
     * from the pad's corner, and music notes floating up while it spins.
     */
    private static void vinyl(SpinPadBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, int n, float c, float r, float s, float t) {
        // the shine: two faint wedges, still while the record turns under them
        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float y = PLATE_Y + 0.005F, rr = r * 0.95F, rin = r * 0.37F;
        for (float base : new float[] {0.7F, 0.7F + Mth.PI}) {
            for (int i = 0; i < 4; i++) {
                float a0 = base - 0.22F + i * 0.11F, a1 = a0 + 0.11F;
                float alpha = 0.07F * (1.0F - Math.abs(i - 1.5F) / 2.0F);
                glow(v, m, new float[][] {{c + Mth.cos(a0) * rin, y, c + Mth.sin(a0) * rin}, {c + Mth.cos(a1) * rin, y, c + Mth.sin(a1) * rin},
                        {c + Mth.cos(a1) * rr, y, c + Mth.sin(a1) * rr}, {c + Mth.cos(a0) * rr, y, c + Mth.sin(a0) * rr}}, 0xFFFFFF, alpha, alpha);
            }
        }
        // the tonearm: a post at the pad's corner, its arm reaching onto the record, its head on a groove
        VertexConsumer metal = buffers.getBuffer(RenderType.entitySolid(METAL));
        float k = Math.max(1.0F, n * 0.8F);
        float px = c + r * 0.86F, pz = c - r * 0.86F;
        float post = 0.07F * k, top = PLATE_Y + 0.17F * k;
        cuboid(metal, pose.last(), px - post, PLATE_Y - 0.02F, pz - post, px + post, top, pz + post, light, 0xFF9A9EAA);
        float hx = c + r * 0.2F, hz = c + r * 0.12F, hy = PLATE_Y + 0.035F * k;
        float dx = hx - px, dz = hz - pz, len = Mth.sqrt(dx * dx + dz * dz);
        pose.pushPose();
        pose.translate(px, top - 0.02F * k, pz);
        pose.mulPose(Axis.YP.rotation((float) -Math.atan2(dz, dx)));
        pose.mulPose(Axis.ZP.rotation((float) -Math.atan2(top - 0.02F * k - hy, len)));
        float th = 0.018F * k;
        cuboid(metal, pose.last(), 0, -th, -th, len * 1.02F, th, th, light, 0xFFC8CCD8);
        cuboid(metal, pose.last(), len * 0.92F, -th * 2.0F, -th * 2.6F, len * 1.06F, th * 0.6F, th * 2.6F, light, 0xFF4A4E5C);
        pose.popPose();
        // music notes rising while it spins (more the faster)
        long now = System.currentTimeMillis();
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (s > 0.1F && level != null && now - be.clientNoteAt > (long) ((1.5F - s * 1.1F) * 1000)) {
            be.clientNoteAt = now;
            BlockPos bp = be.getBlockPos();
            net.minecraft.util.RandomSource rnd = level.random;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, bp.getX() + c + (rnd.nextFloat() - 0.5F) * r * 1.6F,
                    bp.getY() + 0.6F + be.catSize() * 0.8F, bp.getZ() + c + (rnd.nextFloat() - 0.5F) * r * 1.6F, rnd.nextFloat(), 0.0, 0.0);
        }
    }

    /** A box, from (x0, y0, z0) to (x1, y1, z1), its picture stretched over each face, shaded by which way it faces. */
    private static void cuboid(VertexConsumer v, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int light, int argb) {
        float[][][] faces = {
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {0, 1, 0}},
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {0, -1, 0}},
                {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {0, 0, -1}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {0, 0, 1}},
                {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {-1, 0, 0}},
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {1, 0, 0}}};
        float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (float[][] f : faces) {
            for (int i = 0; i < 4; i++) {
                v.addVertex(p, f[i][0], f[i][1], f[i][2]).setColor(argb).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light).setNormal(p, f[4][0], f[4][1], f[4][2]);
            }
        }
    }

    /**
     * Its gauge: rings on the ground round the pad lighting up as it speeds up (green, to amber, to red), and - close to
     * it - a little readout floating over the cat: its turns a minute, the energy it's making, a bar of its speed.
     */
    private static void gauge(SpinPadBlockEntity be, PoseStack pose, MultiBufferSource buffers, int n, float c, float s, float t) {
        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int lit = hue(0.33F - 0.33F * s, 0.85F, 1.0F);
        int segs = 32;
        for (int ring = 0; ring < 3; ring++) {
            float R = n * 0.5F + 0.32F + 0.24F * ring, w = 0.06F;
            float upTo = s * (1.0F - 0.15F * ring);
            for (int i = 0; i < segs; i++) {
                float a0 = i * Mth.TWO_PI / segs + 0.03F, a1 = (i + 1) * Mth.TWO_PI / segs - 0.03F;
                boolean on = (i + 0.5F) / segs < upTo;
                float alpha = on ? 0.75F * (0.7F + 0.3F * Mth.sin(t * 6.0F + i * 0.4F)) : 0.12F;
                float y = 0.015F;
                glow(v, m, new float[][] {{c + Mth.cos(a0) * (R - w), y, c + Mth.sin(a0) * (R - w)}, {c + Mth.cos(a1) * (R - w), y, c + Mth.sin(a1) * (R - w)},
                        {c + Mth.cos(a1) * (R + w), y, c + Mth.sin(a1) * (R + w)}, {c + Mth.cos(a0) * (R + w), y, c + Mth.sin(a0) * (R + w)}}, on ? lit : 0xB0B8D0, alpha, alpha);
            }
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.phys.Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(net.minecraft.world.phys.Vec3.atLowerCornerOf(be.getBlockPos()).add(c, 1.0, c)) > 16.0 * 16.0) {
            return;
        }
        pose.pushPose();
        pose.translate(c, PLATE_Y + Math.max(1, be.catSize()) * 0.95F + 0.55F + 0.15F * n, c);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.022F, -0.022F, 0.022F);
        Matrix4f hm = pose.last().pose();
        int bright = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        panel(buffers, hm, -42, -16, 42, 17, 0xC00A1420, bright);
        panel(buffers, hm, -42, -16, 42, -15, 0xFF5EE6F0, bright);
        net.minecraft.client.gui.Font font = mc.font;
        String rpm = be.rpm() + " rpm", fe = String.format(java.util.Locale.ROOT, "%,d FE/t", be.lastFe());
        font.drawInBatch(rpm, -font.width(rpm) / 2.0F, -12, 0xFF000000 | lit, false, hm, buffers, net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, 0, bright);
        font.drawInBatch(fe, -font.width(fe) / 2.0F, -1, 0xFF7FE0FF, false, hm, buffers, net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, 0, bright);
        for (int i = 0; i < 20; i++) {
            float x0 = -36 + i * 3.6F;
            boolean on = (i + 0.5F) / 20 < s;
            panel(buffers, hm, x0, 10, x0 + 2.8F, 13, on ? 0xFF000000 | hue(0.33F - 0.33F * i / 20.0F, 0.85F, 1.0F) : 0xFF26303C, bright);
        }
        pose.popPose();
    }

    private static void panel(MultiBufferSource buffers, Matrix4f m, float x0, float y0, float x1, float y1, int argb, int light) {
        // (its buffer got each time: drawing writing in between ends it)
        VertexConsumer v = buffers.getBuffer(RenderType.textBackground());
        v.addVertex(m, x0, y0, 0).setColor(argb).setLight(light);
        v.addVertex(m, x0, y1, 0).setColor(argb).setLight(light);
        v.addVertex(m, x1, y1, 0).setColor(argb).setLight(light);
        v.addVertex(m, x1, y0, 0).setColor(argb).setLight(light);
    }

    /** The cat in its screen, as a loaf on the plate (size k: k blocks across), turned with it (Shadered's model renderer). */
    private static void drawCat(SpinPadBlockEntity be, PoseStack given, float c, int k, float turn) {
        if (com.example.skyblocks.client.island.IslandRendering.inIsland) {
            return;
        }
        if (!(be.clientCatModel instanceof com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel)) {
            be.clientCatModel = OiiaCatModel.create(OiiaCatModel.SPIN_MODEL);
        }
        if (!(be.clientCatModel instanceof com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel model)) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.last().pose().set(given.last().pose());
        pose.translate(c, 4.0F / 16.0F, c);
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        float scale = OiiaCatModel.SCALE * k;
        pose.scale(scale, scale, scale);
        pose.translate(0, OiiaCatModel.FEET, 0);
        float offsetY = OiiaCatModel.animate(pose, 0);
        pose.translate(0, -(OiiaCatModel.SPIN_BOTTOM + offsetY), 0); // (the loaf's underside down on the plate, not floating)
        net.minecraft.world.phys.Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        model.modelMatrix = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z).mul(new Matrix4f(pose.last().pose()));
        if (com.example.skyblocks.client.island.IslandRendering.inWindow || com.example.skyblocks.client.island.IslandRendering.collectCats) {
            IslandCatBatch.add(model);
        } else {
            com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer.getInstance().addModel(model);
        }
    }

    private static void plateVertex(VertexConsumer vc, PoseStack pose, float x, float z, float u, float v, int light) {
        vc.addVertex(pose.last(), x, 0, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 1, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(SpinPadBlockEntity be) {
        return be.clientSpeed > 0.03F || be.side() > 1 || be.style() != 0;
    }

    @Override
    public AABB getRenderBoundingBox(SpinPadBlockEntity be) {
        BlockPos p = be.getBlockPos();
        int n = Math.max(1, be.side());
        return new AABB(p.getX() - 2.5 * n, p.getY(), p.getZ() - 2.5 * n, p.getX() + 3.5 * n, p.getY() + 3 * n + (be.style() == 1 ? 5 * n : 0), p.getZ() + 3.5 * n);
    }
}
