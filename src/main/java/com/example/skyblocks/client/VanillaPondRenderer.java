package com.example.skyblocks.client;

import com.example.skyblocks.KoiPondBlock;
import com.example.skyblocks.KoiPondBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The vanilla koi pond: the game's own things, as they are. Its animated water (tinted for the biome, like any water),
 * lily pads, frogs (the frog model, the three kinds of frog) hopping on and off them, tropical fish in koi colours
 * swimming all through the pond, now and then a duck or two (the chicken model, in a mallard's colours) flying in for
 * a swim, or a little turtle swimming through. Each block draws its own water, lily pad and frog; one block of the
 * pond (its "anchor") draws the fish and the visitors for all of it.
 */
final class VanillaPondRenderer {
    private static final float SURF = 14.5F / 16.0F;
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation LILY_PAD = ResourceLocation.withDefaultNamespace("block/lily_pad");
    private static final ResourceLocation FLOWER_OUTER = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/lily_flower_outer");
    private static final ResourceLocation FLOWER_INNER = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/lily_flower_inner");
    private static final int LILY_PAD_COLOUR = 0xFF208030; // (as the game tints lily pads)
    private static final int WATER_TINT = 0xFF2E7466;
    private static final ResourceLocation[] FROGS = {
        ResourceLocation.withDefaultNamespace("textures/entity/frog/temperate_frog.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/frog/cold_frog.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/frog/warm_frog.png")};
    private static final ResourceLocation FISH_A = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a.png");
    private static final ResourceLocation FISH_B = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b.png");
    private static final ResourceLocation TURTLE = ResourceLocation.withDefaultNamespace("textures/entity/turtle/big_sea_turtle.png");
    private static final ResourceLocation[] DUCKS = {
        ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/entity/mallard_drake.png"),
        ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/entity/mallard_hen.png")};
    private static final ResourceLocation DUCKLING = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/entity/duckling.png");

    // koi colours (the game's dye colours): white, orange, red, black, yellow
    private static final int WHITE = 0xFFF9FFFE, ORANGE = 0xFFF9801D, RED = 0xFFB02E26, BLACK = 0xFF1D1D21, GOLD = 0xFFFED83D;
    /** Koi: (shape 0 small / 1 big, pattern 1..6, body colour, pattern colour). Kohaku, showa, ogon, sanke, orange... */
    private static final int[][] KOI = {
        {0, 1, WHITE, RED}, {0, 2, WHITE, ORANGE}, {0, 6, WHITE, RED}, {1, 3, WHITE, ORANGE}, {0, 1, BLACK, ORANGE},
        {1, 6, BLACK, ORANGE}, {0, 2, GOLD, GOLD}, {1, 1, GOLD, WHITE}, {0, 6, WHITE, BLACK}, {0, 4, ORANGE, WHITE},
        {1, 2, ORANGE, WHITE}, {0, 5, WHITE, ORANGE}};

    private final ModelPart frog, fishA, fishAPattern, fishB, fishBPattern, duck, turtle;
    private final ResourceLocation[] patternsA = new ResourceLocation[6], patternsB = new ResourceLocation[6];

    VanillaPondRenderer(BlockEntityRendererProvider.Context context) {
        frog = context.bakeLayer(ModelLayers.FROG);
        fishA = context.bakeLayer(ModelLayers.TROPICAL_FISH_SMALL);
        fishAPattern = context.bakeLayer(ModelLayers.TROPICAL_FISH_SMALL_PATTERN);
        fishB = context.bakeLayer(ModelLayers.TROPICAL_FISH_LARGE);
        fishBPattern = context.bakeLayer(ModelLayers.TROPICAL_FISH_LARGE_PATTERN);
        duck = context.bakeLayer(ModelLayers.CHICKEN);
        turtle = context.bakeLayer(ModelLayers.TURTLE);
        for (int i = 0; i < 6; i++) {
            patternsA[i] = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_" + (i + 1) + ".png");
            patternsB[i] = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_" + (i + 1) + ".png");
        }
        // (no egg under the turtle; the frog's throat not puffed out)
        if (turtle.hasChild("egg_belly")) {
            turtle.getChild("egg_belly").visible = false;
        }
        ModelPart throat = part(frog, "root", "body", "croaking_body");
        if (throat != null) {
            throat.visible = false;
        }
    }

    /** A part of a model down a path of names, if it's there. */
    private static ModelPart part(ModelPart root, String... path) {
        ModelPart p = root;
        for (String name : path) {
            if (!p.hasChild(name)) {
                return null;
            }
            p = p.getChild(name);
        }
        return p;
    }

    void render(KoiPondBlockEntity be, PoseStack poseStack, MultiBufferSource buffers, int light) {
        BlockState state = be.getBlockState();
        BlockPos pos = be.getBlockPos();
        float t = LavaLampRenderer.seconds();
        KoiPondRenderer.Pond pond = KoiPondRenderer.pond(be.getLevel(), pos, state.getBlock());
        boolean n = state.getValue(KoiPondBlock.NORTH), s = state.getValue(KoiPondBlock.SOUTH);
        boolean e = state.getValue(KoiPondBlock.EAST), w = state.getValue(KoiPondBlock.WEST);
        float x0 = w ? 0.0F : 2.0F / 16.0F, x1 = e ? 1.0F : 14.0F / 16.0F, z0 = n ? 0.0F : 2.0F / 16.0F, z1 = s ? 1.0F : 14.0F / 16.0F;

        // the koi and visitors (once for the whole pond)
        if (pos.asLong() == pond.anchor()) {
            creatures(pond, pos, poseStack, buffers, light, t);
        }
        // this block's lily pad, and a frog on it now and then (on a stepping stone instead, if there is one on it)
        boolean stone = be.getLevel() != null && com.example.skyblocks.SteppingStoneBlock.on(be.getLevel(), pos);
        if (stone && KoiPondBlock.frogTime(pos, t, true) >= 0.0) {
            frog(new float[] {0.5F, 0.5F, 0.13F, 0.0F, 0.0F}, pos, poseStack, buffers, light, t, true);
        }
        for (int k = 0; k < 2; k++) {
            float[] pad = pad(pos, k, t);
            if (pad != null) {
                boolean frogged = !stone && k == 0 && KoiPondBlock.frogTime(pos, t) >= 0.0;
                // (the bigger pads flower, as in the koi pond - not while a frog's on it)
                boolean flower = !frogged && KoiPondBlock.u01(KoiPondBlock.hashu(cx(pos), cz(pos), 100 + k)) > 0.72F;
                lilyPad(pad, poseStack, buffers, light, flower);
                if (frogged) {
                    frog(pad, pos, poseStack, buffers, light, t, false);
                }
            }
        }
        PondLife.sawVanillaWater(pos); // (rain rings, the moon on it)
        // the water (drawn last of all by the game, so what's under it shows through)
        int tint = WATER_TINT; // (the koi pond's own deep green, as the shader pond's)
        TextureAtlasSprite water = sprite(WATER);
        quad(buffers.getBuffer(Sheets.translucentCullBlockSheet()), poseStack.last(), water, x0, z0, x1, z1, SURF, tint, light);
    }

    private static TextureAtlasSprite sprite(ResourceLocation name) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(name);
    }

    /** A flat square (both sides) at height y, its texture the sprite's part over it (like a block's face). */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float x0, float z0, float x1, float z1, float y, int colour, int light) {
        float u0 = sp.getU0() + (sp.getU1() - sp.getU0()) * x0, u1 = sp.getU0() + (sp.getU1() - sp.getU0()) * x1;
        float v0 = sp.getV0() + (sp.getV1() - sp.getV0()) * z0, v1 = sp.getV0() + (sp.getV1() - sp.getV0()) * z1;
        vertex(vc, pose, x0, y, z0, u0, v0, colour, light, 1.0F);
        vertex(vc, pose, x0, y, z1, u0, v1, colour, light, 1.0F);
        vertex(vc, pose, x1, y, z1, u1, v1, colour, light, 1.0F);
        vertex(vc, pose, x1, y, z0, u1, v0, colour, light, 1.0F);
        vertex(vc, pose, x1, y, z0, u1, v0, colour, light, -1.0F);
        vertex(vc, pose, x1, y, z1, u1, v1, colour, light, -1.0F);
        vertex(vc, pose, x0, y, z1, u0, v1, colour, light, -1.0F);
        vertex(vc, pose, x0, y, z0, u0, v0, colour, light, -1.0F);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int colour, int light, float ny) {
        vc.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, ny, 0.0F);
    }

    // ---- lily pads and frogs ----

    private static int cx(BlockPos p) {
        return Math.floorMod(p.getX(), 512);
    }

    private static int cz(BlockPos p) {
        return Math.floorMod(p.getZ(), 512);
    }

    /**
     * Lily pad k (0 or 1) of a block, as in the koi pond: is there one (null if not), and {x, z in the block, radius,
     * how it's turned (radians)} - small, clear of the rims, drifting a little and slowly turning.
     */
    private static float[] pad(BlockPos pos, int k, float t) {
        int x = cx(pos), z = cz(pos);
        if (KoiPondBlock.u01(KoiPondBlock.hashu(x, z, k)) < 0.45F) {
            return null;
        }
        float hy = KoiPondBlock.u01(KoiPondBlock.hashu(x, z, 100 + k));
        float r = 0.08F + 0.07F * hy;
        float m = 0.165F + r;
        float hx = KoiPondBlock.u01(KoiPondBlock.hashu(x, z, 20 + k)), hz = KoiPondBlock.u01(KoiPondBlock.hashu(x, z, 30 + k));
        float px = m + (1.0F - 2.0F * m) * hx + 0.02F * Mth.sin(t * 0.07F + hy * 9.0F);
        float pz = m + (1.0F - 2.0F * m) * hz + 0.02F * Mth.cos(t * 0.05F + hx * 7.0F);
        float ang = KoiPondBlock.u01(KoiPondBlock.hashu(x, z, 40 + k)) * 6.2832F + t * 0.02F;
        // a frog sitting on it shifts round now and then, and the pad turns with it (as in the koi pond)
        float sway = 0.0F;
        double te = k == 0 ? KoiPondBlock.frogTime(pos, t) : -1.0;
        if (te >= 0.0) {
            float sit = (float) Math.max(0.0, Math.min(1.0, te < 6.0 ? te - 5.0 : te > 70.0 ? 71.0 - te : 1.0));
            sway = 0.5F * Mth.sin(t * 0.3F + hy * 9.0F) * sit * sit;
        }
        return new float[] {px, pz, r, ang + sway, sway};
    }

    private void lilyPad(float[] pad, PoseStack poseStack, MultiBufferSource buffers, int light, boolean flower) {
        poseStack.pushPose();
        poseStack.translate(pad[0], 0.0F, pad[1]);
        poseStack.mulPose(Axis.YP.rotation(pad[3]));
        float a = -pad[2] * 16.0F / 14.0F, b = -a; // (the pad fills about 14 of the texture's 16 pixels)
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();
        square(vc, pose, sprite(LILY_PAD), a, b, SURF + 0.004F, LILY_PAD_COLOUR, light);
        if (flower) { // a water lily on it: its outer petals, and the inner ones and the middle standing up a little
            square(vc, pose, sprite(FLOWER_OUTER), a, b, SURF + 0.012F, 0xFFFFFFFF, light);
            square(vc, pose, sprite(FLOWER_INNER), a, b, SURF + 0.03F, 0xFFFFFFFF, light);
        }
        poseStack.popPose();
    }

    /** A flat square from (a, a) to (b, b) at height y (both sides), the whole sprite on it. */
    private static void square(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float a, float b, float y, int colour, int light) {
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        vertex(vc, pose, a, y, a, u0, v0, colour, light, 1.0F);
        vertex(vc, pose, a, y, b, u0, v1, colour, light, 1.0F);
        vertex(vc, pose, b, y, b, u1, v1, colour, light, 1.0F);
        vertex(vc, pose, b, y, a, u1, v0, colour, light, 1.0F);
        vertex(vc, pose, b, y, a, u1, v0, colour, light, -1.0F);
        vertex(vc, pose, b, y, b, u1, v1, colour, light, -1.0F);
        vertex(vc, pose, a, y, b, u0, v1, colour, light, -1.0F);
        vertex(vc, pose, a, y, a, u0, v0, colour, light, -1.0F);
    }

    /**
     * A frog visiting the (first) lily pad, as in the koi pond: it leaps up out of the water onto it (facing it),
     * turns round to face the open water (the middle of its block - never the stone round it), sits a minute, then
     * hops off into the water.
     */
    private void frog(float[] pad, BlockPos pos, PoseStack poseStack, MultiBufferSource buffers, int light, float t, boolean stone) {
        double te = KoiPondBlock.frogTime(pos, t, stone);
        float seat = stone ? 1.0F + 2.0F / 16.0F : SURF + 0.005F; // (on its stepping stone, or its lily pad)
        if (te < 0.0) {
            return;
        }
        int kind = KoiPondBlock.frogKind(pos, t);
        float mx = 0.5F - pad[0], mz = 0.5F - pad[1];
        float len = (float) Math.sqrt(mx * mx + mz * mz);
        if (len < 0.01F) {
            mx = 0.0F;
            mz = 1.0F;
        } else {
            mx /= len;
            mz /= len;
        }
        float scale = 2.6F * pad[2]; // (sized to its lily pad: a little frog on a little pad - it fits on it)
        float hop = 0.9F * pad[2]; // (where it jumps to and from in the water: towards the middle of the block)
        float wx = pad[0] + mx * hop, wz = pad[1] + mz * hop;
        float x = pad[0], z = pad[1], y = seat;
        float turn = 0.0F; // (0 facing the water .. 1 facing the pad: turning round once it's on)
        float sway = pad[4]; // (turning with its pad)
        if (te < 6.0) { // leaping on
            float u = (float) (te - 5.0);
            x = wx + (pad[0] - wx) * u;
            z = wz + (pad[1] - wz) * u;
            y = (SURF - 0.14F) + (seat - SURF + 0.14F) * u * u + 0.3F * u * (1.0F - u);
            turn = 1.0F;
        } else if (te < 7.2) {
            float u = (float) ((te - 6.0) / 1.2);
            turn = 1.0F - u * u * (3.0F - 2.0F * u);
        } else if (te > 70.0) { // hopping off
            float u = (float) (te - 70.0);
            x = pad[0] + (wx - pad[0]) * u;
            z = pad[1] + (wz - pad[1]) * u;
            y = seat + (SURF - 0.14F - seat) * u * u + 0.3F * u * (1.0F - u);
        }
        float yaw = yawOf(mx, mz) + 180.0F * turn - (float) Math.toDegrees(sway); // (the pad's turn, the same way round)
        model(poseStack, buffers, frog, FROGS[kind], x, y, z, yaw, scale, light, 0xFFFFFFFF, false);
    }

    /** The game's yaw (degrees) for facing along (dx, dz). */
    private static float yawOf(float dx, float dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** One of the game's models, standing at (x, y, z) facing that way, at that size (as the game draws mobs). */
    private static void model(PoseStack poseStack, MultiBufferSource buffers, ModelPart part, ResourceLocation texture, float x, float y, float z,
                              float yaw, float scale, int light, int colour, boolean seeThrough) {
        model(poseStack, buffers, part, texture, x, y, z, yaw, scale, light, colour, seeThrough, 0.0F);
    }

    /** pitch: tipped forward (degrees: - its head down, as a duck dabbling), round its middle at the water. */
    private static void model(PoseStack poseStack, MultiBufferSource buffers, ModelPart part, ResourceLocation texture, float x, float y, float z,
                              float yaw, float scale, int light, int colour, boolean seeThrough, float pitch) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        if (pitch != 0.0F) {
            float pivot = 0.35F * scale;
            poseStack.translate(0.0F, pivot, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            poseStack.translate(0.0F, -pivot, 0.0F);
        }
        poseStack.scale(-scale, -scale, scale);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        RenderType type = seeThrough ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);
        part.render(poseStack, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, colour);
        poseStack.popPose();
    }

    // ---- the koi and the visitors (the whole pond's, drawn by its anchor block) ----

    private static float u(long anchor, int k) {
        BlockPos a = BlockPos.of(anchor);
        return KoiPondBlock.u01(KoiPondBlock.hashu(Math.floorMod(a.getX(), 512) + 1000, Math.floorMod(a.getZ(), 512) + 1000, k));
    }

    /**
     * A wandering path in a part of the pond (long, lazy curves; reach: how far the body reaches from where it is):
     * {x, z, dx, dz}, in world blocks.
     */
    private static float[] wander(long anchor, int seed, int[] part, float reach, float speed, float t) {
        float lox = part[0] + 0.125F + reach, hix = part[1] + 1 - 0.125F - reach;
        float loz = part[2] + 0.125F + reach, hiz = part[3] + 1 - 0.125F - reach;
        float midx = (part[0] + part[1] + 1) * 0.5F, midz = (part[2] + part[3] + 1) * 0.5F;
        lox = Math.min(lox, midx);
        hix = Math.max(hix, midx);
        loz = Math.min(loz, midz);
        hiz = Math.max(hiz, midz);
        float hx = (hix - lox) * 0.5F, hz = (hiz - loz) * 0.5F;
        float fx = (speed + 0.05F * u(anchor, seed)) / Math.max(hx, 0.6F), fz = (speed + 0.05F * u(anchor, seed + 1)) / Math.max(hz, 0.6F);
        float px = u(anchor, seed + 2) * 6.2832F, pz = u(anchor, seed + 3) * 6.2832F;
        float ux = 0.62F * Mth.sin(t * fx + px) + 0.38F * Mth.sin(t * fx * 1.73F + pz * 1.3F);
        float uz = 0.62F * Mth.sin(t * fz * 1.11F + pz) + 0.38F * Mth.sin(t * fz * 1.91F + px * 0.7F);
        float vx = (0.62F * fx * Mth.cos(t * fx + px) + 0.38F * fx * 1.73F * Mth.cos(t * fx * 1.73F + pz * 1.3F)) * hx;
        float vz = (0.62F * fz * 1.11F * Mth.cos(t * fz * 1.11F + pz) + 0.38F * fz * 1.91F * Mth.cos(t * fz * 1.91F + px * 0.7F)) * hz;
        float l = (float) Math.sqrt(vx * vx + vz * vz);
        return new float[] {lox + hx + hx * ux, loz + hz + hz * uz, l > 1e-5F ? vx / l : 1.0F, l > 1e-5F ? vz / l : 0.0F};
    }

    private void creatures(KoiPondRenderer.Pond pond, BlockPos anchorPos, PoseStack poseStack, MultiBufferSource buffers, int light, float t) {
        long anchor = pond.anchor();
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        float rain = level == null ? 0.0F : PondLife.rain(level, anchorPos, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        int[][] parts = pond.parts();
        int fish = Math.max(1, Math.min(24, (int) (pond.blocks() * 0.45F + 0.75F)));
        float ox = -anchorPos.getX(), oz = -anchorPos.getZ(); // (world blocks -> this block's own)
        // the koi: each keeps to one part of the pond (the bigger the part, the more koi in it), at one of three depths
        for (int i = 0; i < fish; i++) {
            float pick = u(anchor, 100 + i * 7) * pond.blocks();
            int[] part = parts[0];
            for (int[] p : parts) {
                part = p;
                pick -= (p[1] - p[0] + 1) * (p[3] - p[2] + 1);
                if (pick < 0.0F) {
                    break;
                }
            }
            int[] koi = KOI[(int) (u(anchor, 101 + i * 7) * KOI.length) % KOI.length];
            float size = 0.75F + 0.35F * u(anchor, 102 + i * 7);
            float scale = 0.8F * size * (koi[0] == 1 ? 0.8F : 1.0F); // (as long as the koi pond's koi)
            float[] at = wander(anchor, 200 + i * 9, part, 0.3F * size, 0.12F, t);
            float y = SURF - (i % 3 == 0 ? 3.0F : i % 3 == 1 ? 5.5F : 8.0F) / 16.0F - 0.1F * scale - 0.06F * rain; // (lower in the rain)
            ModelPart body = koi[0] == 0 ? fishA : fishB, pattern = koi[0] == 0 ? fishAPattern : fishBPattern;
            float wag = 0.45F * Mth.sin(t * 6.0F + i * 1.7F);
            for (ModelPart m : new ModelPart[] {body, pattern}) {
                if (m.hasChild("tail")) {
                    m.getChild("tail").yRot = wag;
                }
            }
            float yaw = yawOf(at[2], at[3]);
            model(poseStack, buffers, body, koi[0] == 0 ? FISH_A : FISH_B, at[0] + ox, y, at[1] + oz, yaw, scale, light, koi[2], false);
            model(poseStack, buffers, pattern, (koi[0] == 0 ? patternsA : patternsB)[koi[1] - 1], at[0] + ox, y, at[1] + oz, yaw, scale, light, koi[3], false);
        }
        int[] main = parts[0];
        int mainBlocks = (main[1] - main[0] + 1) * (main[3] - main[2] + 1);
        ducks(anchorPos, anchor, main, mainBlocks, poseStack, buffers, light, t, ox, oz);
        turtle(anchor, main, mainBlocks, poseStack, buffers, light, t, ox, oz);
    }

    /** A duck to draw this frame. */
    private static final class Duck {
        float x, z, y, yaw, wing, scale, dab, radius;
        boolean legs, tucked, free; // (free: swimming about, so it can ease out of the others' way)
        ResourceLocation texture;
    }

    // the family's own clock: she swims about 18 s, slows to a stop and rests about 10 s, then sets off again
    private static final float FAM_CYCLE = 32.0F, FAM_RUN = 21.55F;

    private static float famClock(float t) {
        float cyc = (float) Math.floor(t / FAM_CYCLE), c = t - cyc * FAM_CYCLE;
        float s = Math.min(c, 18.0F);
        if (c > 18.0F) { float u = Math.min(c, 21.0F) - 18.0F; s += u - 0.95F * u * u / 6.0F; }
        if (c > 21.0F) s += (Math.min(c, 29.0F) - 21.0F) * 0.05F;
        if (c > 29.0F) { float u = c - 29.0F; s += 0.05F * u + 0.95F * u * u / 6.0F; }
        return cyc * FAM_RUN + s;
    }

    /** How much she's resting (0 swimming .. 1 stopped): the ducklings scatter round her and play. */
    private static float famRest(float t) {
        float c = t - (float) Math.floor(t / FAM_CYCLE) * FAM_CYCLE;
        return smooth(19.5F, 22.0F, c) * (1.0F - smooth(28.5F, 31.0F, c));
    }

    /**
     * The ducks: now and then a visitor (two in a bigger pond) flies in, glides down and lands with its wings folding,
     * swims about a while, then runs across the water flapping and takes off. And in a pond big enough a mother duck
     * and her ducklings (three, four in a bigger pond) swim round in a line after her; when she stops for a rest they
     * scatter round her and play, paddling in little loops and tipping up to dabble. At dusk they swim to the edge
     * and hop out onto the bank (the ground beside the pond, if there's room on it) and sleep there huddled together,
     * heads tucked in, until morning (on the water at the pond's edge if there's no bank). Swimming ducks ease out of
     * each other's way.
     */
    private void ducks(BlockPos anchorPos, long anchor, int[] main, int mainBlocks, PoseStack poseStack, MultiBufferSource buffers, int light,
                       float t, float ox, float oz) {
        java.util.List<Duck> all = new java.util.ArrayList<>();
        // the visitors
        float ph = t / 140.0F + u(anchor, 50) * 7.0F;
        int epoch = (int) Math.floor(ph);
        float te = (ph - epoch) * 140.0F;
        if (u(anchor, 60 + epoch * 13) < 0.55F) {
            for (int d = 0; d < 2; d++) {
                if (d == 1 && mainBlocks < 8) {
                    break;
                }
                float land = 8.0F + 20.0F * u(anchor, 70 + epoch * 13 + d), leave = Math.min(land + 55.0F + 25.0F * u(anchor, 80 + epoch * 13 + d), 134.0F);
                if (te < land - 3.0F || te > leave + 3.0F) {
                    continue;
                }
                float height = 0.0F, ahead = 0.0F, flap = 0.0F;
                if (te < land) { // flying in: down out of the sky, gliding the last of it
                    float a = (land - te) / 3.0F;
                    height = 2.4F * a * a;
                    ahead = -2.6F * a;
                    flap = a > 0.35F ? 1.0F : 0.4F;
                } else if (te > leave) { // off: a run across the water, flapping, then up and away
                    float b = (te - leave) / 3.0F;
                    height = 2.4F * Math.max(0.0F, (b - 0.25F) / 0.75F) * Math.max(0.0F, (b - 0.25F) / 0.75F);
                    ahead = 2.6F * b * b;
                    flap = 1.0F;
                }
                float[] at = wander(anchor, 400 + d * 11, main, 0.3F, 0.07F, t);
                Duck k = new Duck();
                k.x = at[0] + at[2] * ahead;
                k.z = at[1] + at[3] * ahead;
                k.scale = 0.5F;
                float bob = height > 0.0F ? 0.0F : 0.012F * Mth.sin(t * 1.7F + d);
                k.y = SURF + height + bob - (height > 0.05F ? 0.0F : 4.5F / 16.0F * k.scale); // (swimming: its legs under the water)
                k.wing = flap > 0.0F ? (flap > 0.5F ? 0.2F + 1.0F * (0.5F + 0.5F * Mth.sin(t * 15.0F + d)) : 0.9F) : 0.0F;
                k.yaw = yawOf(at[2], at[3]);
                k.texture = DUCKS[u(anchor, 90 + epoch * 13 + d) < 0.5F ? 0 : 1];
                k.legs = height > 0.05F; // (drifting on the water: no legs to be seen; flying: legs)
                k.free = height < 0.05F;
                k.radius = 0.15F;
                all.add(k);
            }
        }
        int family = all.size();
        // the family
        int kids = mainBlocks >= 12 ? 4 : mainBlocks >= 6 ? 3 : 0;
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (kids > 0 && level != null) {
            float awake = smooth(0.1F, 0.35F, PondLife.day(level));
            float rest = famRest(t) * awake;
            float seed = u(anchor, 777) * 50.0F;
            int y = anchorPos.getY();
            float[] bank = bank(level, main, y);
            boolean onBank = bank != null;
            float bankX, bankZ, edgeX, edgeZ;
            if (onBank) {
                bankX = bank[0]; bankZ = bank[1]; edgeX = bank[2]; edgeZ = bank[3];
            } else { // (no bank: on the water in the pond's corner)
                edgeX = bankX = main[0] + 0.125F + 0.4F;
                edgeZ = bankZ = main[2] + 0.125F + 0.4F;
            }
            float[][] huddle = {{0.0F, 0.0F}, {-0.2F, 0.16F}, {0.2F, 0.14F}, {0.16F, -0.18F}, {-0.17F, -0.16F}};
            float tau = famClock(t);
            float[] mum = wander(anchor, 700, main, 0.3F, 0.05F, tau);
            // the ducklings a set distance apart behind her along the way she has swum (however slowly she goes: never
            // bunching up), found by going back along her way
            float[][] follow = new float[kids + 1][];
            follow[0] = mum;
            {
                float px0 = mum[0], pz0 = mum[1], walked = 0.0F;
                int next = 1;
                for (int st = 1; st <= 64 && next <= kids; st++) {
                    float[] p = wander(anchor, 700, main, 0.3F, 0.05F, tau - st * 0.6F);
                    float seg = (float) Math.hypot(p[0] - px0, p[1] - pz0);
                    while (next <= kids) {
                        float want = 0.19F + next * 0.17F;
                        if (walked + seg < want) break;
                        float uu = seg > 1.0e-5F ? (want - walked) / seg : 0.0F;
                        float fx = px0 + (p[0] - px0) * uu, fz = pz0 + (p[1] - pz0) * uu;
                        float hl = Math.max(seg, 1.0e-5F);
                        follow[next] = new float[] {fx, fz, (px0 - p[0]) / hl, (pz0 - p[1]) / hl};
                        next++;
                    }
                    walked += seg;
                    px0 = p[0];
                    pz0 = p[1];
                }
                for (int f = next; f <= kids; f++) { // (not far enough found: carried on straight behind)
                    float more = Math.max(0.19F + f * 0.17F - walked, 0.0F);
                    follow[f] = new float[] {px0 - mum[2] * more, pz0 - mum[3] * more, mum[2], mum[3]};
                }
            }
            for (int f = 0; f <= kids; f++) {
                float[] at = follow[f];
                float px = at[0], pz = at[1], dx = at[2], dz = at[3];
                float dab = 0.0F;
                if (f > 0 && rest > 0.0F) { // playing round her while she rests: paddling round her, evenly spaced
                    float ang = (float) f / kids * Mth.TWO_PI + seed + t * 0.3F;
                    float rad = 0.28F + 0.025F * Mth.sin(t * 0.7F + f * 1.9F);
                    float qx = mum[0] + Mth.cos(ang) * rad, qz = mum[1] + Mth.sin(ang) * rad;
                    float tx = -Mth.sin(ang) + 0.25F * Mth.cos(t * 1.3F + f), tz = Mth.cos(ang) + 0.25F * Mth.sin(t * 1.1F + f);
                    px += (qx - px) * rest;
                    pz += (qz - pz) * rest;
                    dx += (tx - dx) * rest;
                    dz += (tz - dz) * rest;
                    dab = rest * (float) Math.pow(Math.max(Mth.sin(t * 0.8F + f * 2.1F + seed), 0.0F), 10.0);
                } else if (f == 0) {
                    dab = rest * (float) Math.pow(Math.max(Mth.sin(t * 0.45F + seed + 1.0F), 0.0F), 14.0) * 0.8F;
                }
                Duck k = new Duck();
                k.scale = f == 0 ? 0.5F : 0.27F;
                k.radius = f == 0 ? 0.15F : 0.09F;
                k.texture = f == 0 ? DUCKS[1] : DUCKLING;
                float hx = huddle[f][0] * (onBank ? 1.0F : 0.8F), hz = huddle[f][1] * (onBank ? 1.0F : 0.8F);
                float sleepYaw = 40.0F + f * 125.0F;
                float bob = 0.012F * Mth.sin(t * (f == 0 ? 1.7F : 2.3F) + f);
                float water = SURF + bob * awake - 4.5F / 16.0F * k.scale;
                if (awake >= 0.5F || !onBank) { // swimming (to the edge, as night comes)
                    float kk = onBank ? (awake - 0.5F) * 2.0F : awake;
                    k.x = edgeX + hx + (px - edgeX - hx) * kk;
                    k.z = edgeZ + hz + (pz - edgeZ - hz) * kk;
                    k.y = water;
                    k.yaw = kk > 0.6F ? yawOf(dx, dz) : kk > 0.05F ? yawOf(px - k.x, pz - k.z) + 180.0F : sleepYaw;
                    k.free = kk > 0.6F;
                    k.dab = dab;
                    k.tucked = kk < 0.05F;
                } else { // hopping out onto the bank, then asleep there
                    float uu = Math.min(1.0F, (0.5F - awake) * 2.0F);
                    float hop = smooth(0.0F, 0.15F, uu), move = smooth(0.0F, 0.5F, uu);
                    k.x = edgeX + hx + (bankX - edgeX) * move;
                    k.z = edgeZ + hz + (bankZ - edgeZ) * move;
                    float ground = y + 1.0F - 4.5F / 16.0F * k.scale * 0.85F;
                    k.y = water + (ground - water) * hop + 0.25F * Mth.sin((float) Math.PI * hop);
                    k.yaw = uu < 0.5F ? yawOf(bankX - edgeX, bankZ - edgeZ) : sleepYaw;
                    k.tucked = uu > 0.5F;
                }
                all.add(k);
            }
        }
        // a visiting duck and the family keep out of each other's way: gently (the family moving aside for it)
        for (int i = 0; i < family; i++) {
            Duck a = all.get(i);
            if (!a.free) continue;
            for (int j = family; j < all.size(); j++) {
                Duck b = all.get(j);
                if (!b.free) continue;
                float ddx = b.x - a.x, ddz = b.z - a.z, dl = (float) Math.sqrt(ddx * ddx + ddz * ddz), m = a.radius + b.radius + 0.08F;
                if (dl >= m || dl < 1.0e-4F) continue;
                float push = (m - dl) * smooth(m, m * 0.4F, dl);
                b.x += ddx / dl * push * 0.8F; b.z += ddz / dl * push * 0.8F;
                a.x -= ddx / dl * push * 0.2F; a.z -= ddz / dl * push * 0.2F;
            }
        }
        for (Duck k : all) {
            if (duck.hasChild("right_wing")) duck.getChild("right_wing").zRot = k.wing;
            if (duck.hasChild("left_wing")) duck.getChild("left_wing").zRot = -k.wing;
            legs(k.legs);
            pose(k.tucked ? 1.0F : 0.0F);
            model(poseStack, buffers, duck, k.texture, k.x + ox, k.y, k.z + oz, k.yaw, k.scale, light, 0xFFFFFFFF, false, -70.0F * k.dab);
        }
        pose(0.0F);
        legs(true);
        if (duck.hasChild("right_wing")) duck.getChild("right_wing").zRot = 0.0F;
        if (duck.hasChild("left_wing")) duck.getChild("left_wing").zRot = 0.0F;
    }

    /** Asleep (1): its head turned right round and laid down on its back, bill tucked in; awake (0): as it is. */
    private void pose(float tuck) {
        for (String name : new String[] {"head", "beak", "red_thing"}) {
            if (duck.hasChild(name)) {
                ModelPart part = duck.getChild(name);
                part.yRot = 2.7F * tuck;
                part.xRot = 0.35F * tuck;
                part.y = part.getInitialPose().y + 1.5F * tuck;
            }
        }
    }

    /** Ponds' banks, found now and then: (bank x, z, the water's edge next to it x, z), or none. */
    private static final java.util.Map<Long, float[]> BANKS = new java.util.HashMap<>();
    private static long banksAt;

    private static float[] bank(net.minecraft.client.multiplayer.ClientLevel level, int[] main, int y) {
        long now = System.nanoTime();
        if (now - banksAt > 2_000_000_000L) {
            BANKS.clear();
            banksAt = now;
        }
        long key = BlockPos.asLong(main[0], y, main[2]) * 31L + main[1] * 7L + main[3];
        if (BANKS.containsKey(key)) {
            return BANKS.get(key);
        }
        float[] found = null;
        // round the pond's biggest part: the north side, west, south, east (nearest its first corner first)
        int w = main[1] - main[0] + 1, h = main[3] - main[2] + 1;
        outer:
        for (int side = 0; side < 4; side++) {
            int len = side % 2 == 0 ? w : h;
            for (int i = 0; i < len; i++) {
                int bx, bz;
                float ex, ez;
                if (side == 0) { bx = main[0] + i; bz = main[2] - 1; ex = bx + 0.5F; ez = main[2] + 0.4F; }
                else if (side == 1) { bx = main[0] - 1; bz = main[2] + i; ex = main[0] + 0.4F; ez = bz + 0.5F; }
                else if (side == 2) { bx = main[0] + i; bz = main[3] + 1; ex = bx + 0.5F; ez = main[3] + 0.6F; }
                else { bx = main[1] + 1; bz = main[2] + i; ex = main[1] + 0.6F; ez = bz + 0.5F; }
                BlockPos g = new BlockPos(bx, y, bz);
                BlockState gs = level.getBlockState(g);
                if (gs.getBlock() instanceof KoiPondBlock || !gs.isFaceSturdy(level, g, net.minecraft.core.Direction.UP)) {
                    continue;
                }
                if (level.getBlockState(g.above()).blocksMotion() || level.getBlockState(g.above(2)).blocksMotion()) {
                    continue;
                }
                found = new float[] {bx + 0.5F, bz + 0.5F, ex, ez};
                break outer;
            }
        }
        BANKS.put(key, found);
        return found;
    }

    private void legs(boolean shown) {
        for (String leg : new String[] {"right_leg", "left_leg"}) {
            if (duck.hasChild(leg)) {
                duck.getChild(leg).visible = shown;
            }
        }
    }

    /** Now and then a little turtle swims through, deep down (fading in from the murk and away again). */
    private void turtle(long anchor, int[] main, int mainBlocks, PoseStack poseStack, MultiBufferSource buffers, int light, float t, float ox, float oz) {
        float ph = t / 190.0F + u(anchor, 30) * 5.0F;
        int epoch = (int) Math.floor(ph);
        float te = (ph - epoch) * 190.0F;
        if (u(anchor, 40 + epoch * 7) > 0.5F || mainBlocks < 4) {
            return;
        }
        float here = smooth(10.0F, 15.0F, te) * (1.0F - smooth(155.0F, 160.0F, te));
        if (here <= 0.01F) {
            return;
        }
        float[] at = wander(anchor, 500, main, 0.3F, 0.05F, t);
        int alpha = (int) (here * 255.0F);
        model(poseStack, buffers, turtle, TURTLE, at[0] + ox, SURF - 0.6F, at[1] + oz, yawOf(at[2], at[3]), 0.2F, light,
                alpha << 24 | 0xFFFFFF, true);
    }

    private static float smooth(float a, float b, float x) {
        float k = Math.max(0.0F, Math.min(1.0F, (x - a) / (b - a)));
        return k * k * (3.0F - 2.0F * k);
    }
}
