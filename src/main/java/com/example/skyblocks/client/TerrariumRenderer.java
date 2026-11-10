package com.example.skyblocks.client;

import com.example.skyblocks.TerrariumBlock;
import com.example.skyblocks.TerrariumBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * The terrarium's two geckos: they wander to somewhere new, stop and look round, flick their tongues, and walk on -
 * legs stepping, tails swaying. Desert: a spotted leopard gecko; jungle: a green crested gecko (textures/block/gecko_*).
 */
public class TerrariumRenderer implements BlockEntityRenderer<TerrariumBlockEntity> {
    private static final float FLOOR = 3.0F, MIN = 4.0F, MAX = 12.0F, SIZE = 1.0F;
    public TerrariumRenderer(BlockEntityRendererProvider.Context context) {}

    /** Where its geckos may go: its own tank and, joined, the tanks next to it (pixels of its block). */
    private static float[] bounds(TerrariumBlockEntity be) {
        net.minecraft.world.level.block.state.BlockState s = be.getBlockState();
        float x0 = s.getValue(TerrariumBlock.WEST) ? MIN - 16 : MIN, x1 = s.getValue(TerrariumBlock.EAST) ? MAX + 16 : MAX;
        float z0 = s.getValue(TerrariumBlock.NORTH) ? MIN - 16 : MIN, z1 = s.getValue(TerrariumBlock.SOUTH) ? MAX + 16 : MAX;
        return new float[] {x0, x1, z0, z1};
    }

    private static void tick(TerrariumBlockEntity be, RandomSource r) {
        float[] b = bounds(be);
        if (!be.placed) {
            be.placed = true;
            for (TerrariumBlockEntity.Gecko g : be.geckos) {
                g.x = g.px = g.tx = Mth.lerp(r.nextFloat(), MIN, MAX);
                g.z = g.pz = g.tz = Mth.lerp(r.nextFloat(), MIN, MAX);
                g.yaw = g.pyaw = r.nextFloat() * Mth.TWO_PI;
                g.pause = 20 + r.nextInt(40);
            }
        }
        for (TerrariumBlockEntity.Gecko g : be.geckos) {
            g.px = g.x;
            g.pz = g.z;
            g.pyaw = g.yaw;
            if (g.tongue > 0) g.tongue--;
            if (g.pause > 0) {
                g.pause--;
                if (r.nextInt(60) == 0) g.tongue = 6;
                if (r.nextInt(40) == 0) g.yaw += (r.nextFloat() - 0.5F) * 0.8F; // (looking round)
                if (g.pause == 0) {
                    g.tx = Mth.lerp(r.nextFloat(), b[0], b[1]);
                    g.tz = Mth.lerp(r.nextFloat(), b[2], b[3]);
                }
                continue;
            }
            float dx = g.tx - g.x, dz = g.tz - g.z;
            float dist = Mth.sqrt(dx * dx + dz * dz);
            if (dist < 0.3F) {
                g.pause = 30 + r.nextInt(90);
                continue;
            }
            float want = (float) Mth.atan2(dx, dz);
            float turn = Mth.wrapDegrees((want - g.yaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            g.yaw += Mth.clamp(turn, -0.25F, 0.25F);
            if (Math.abs(turn) < 0.8F) {
                float step = 0.12F;
                g.x += Mth.sin(g.yaw) * step;
                g.z += Mth.cos(g.yaw) * step;
                g.walked += step;
            }
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(TerrariumBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0, 0.0, 1.0);
    }

    @Override
    public void render(TerrariumBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!(be.getLevel() instanceof ClientLevel level) || !(be.getBlockState().getBlock() instanceof TerrariumBlock)) {
            return;
        }
        long now = level.getGameTime();
        if (now != be.tickedAt) {
            int steps = be.tickedAt < 0 ? 1 : (int) Math.min(5, now - be.tickedAt);
            be.tickedAt = now;
            for (int i = 0; i < steps; i++) tick(be, level.random);
        }
        int scene = be.getBlockState().getValue(TerrariumBlock.SCENE);
        TextureAtlasSprite sprite = net.minecraft.client.Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, scene == 0 ? "block/gecko_desert" : "block/gecko_jungle"));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        float t = now + partialTick;
        for (int i = 0; i < be.geckos.length; i++) {
            TerrariumBlockEntity.Gecko g = be.geckos[i];
            float x = Mth.lerp(partialTick, g.px, g.x), z = Mth.lerp(partialTick, g.pz, g.z);
            float yaw = g.pyaw + Mth.wrapDegrees((g.yaw - g.pyaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * partialTick;
            boolean walking = g.pause == 0;
            float stride = walking ? Mth.sin(g.walked * 2.2F) * 0.45F : 0.0F;
            float sway = Mth.sin(t * 0.15F + i * 2.0F) * (walking ? 0.5F : 0.2F) + (walking ? Mth.sin(g.walked * 2.2F) * 0.25F : 0.0F);
            float breathe = 1.0F + Mth.sin(t * 0.2F + i) * 0.02F;
            poseStack.pushPose();
            poseStack.translate(x / 16.0F, FLOOR / 16.0F, z / 16.0F);
            poseStack.mulPose(Axis.YP.rotation(yaw));
            poseStack.scale(SIZE, SIZE * breathe, SIZE);
            PoseStack.Pose pose = poseStack.last();
            // (+z is its front; pixels) body, head, snout, eyes, legs, tail
            box(vc, pose, sprite, -1.3F, 0.7F, -2.0F, 1.3F, 2.0F, 2.0F, BACK, SIDE, light, overlay);
            box(vc, pose, sprite, -1.15F, 0.8F, 2.0F, 1.15F, 2.1F, 4.0F, HEAD, SIDE, light, overlay);
            box(vc, pose, sprite, -0.85F, 0.8F, 4.0F, 0.85F, 1.7F, 4.7F, HEAD, SIDE, light, overlay);
            box(vc, pose, sprite, -1.35F, 1.35F, 3.0F, -1.1F, 1.85F, 3.5F, EYE, EYE, light, overlay);
            box(vc, pose, sprite, 1.1F, 1.35F, 3.0F, 1.35F, 1.85F, 3.5F, EYE, EYE, light, overlay);
            if (scene == 1) { // (a crested gecko's crests over its eyes and down its back)
                box(vc, pose, sprite, -1.2F, 2.1F, 2.4F, -0.8F, 2.4F, 3.8F, SIDE, SIDE, light, overlay);
                box(vc, pose, sprite, 0.8F, 2.1F, 2.4F, 1.2F, 2.4F, 3.8F, SIDE, SIDE, light, overlay);
            }
            if (g.tongue > 0) {
                box(vc, pose, sprite, -0.15F, 1.0F, 4.7F, 0.15F, 1.15F, 4.7F + g.tongue * 0.15F, TONGUE, TONGUE, light, overlay);
            }
            for (int leg = 0; leg < 4; leg++) {
                float side = leg % 2 == 0 ? -1 : 1, at = leg < 2 ? 1.3F : -1.3F;
                float swing = (leg == 0 || leg == 3 ? stride : -stride);
                float lift = Math.max(0.0F, (leg == 0 || leg == 3 ? 1 : -1) * Mth.cos(g.walked * 2.2F)) * (walking ? 0.3F : 0.0F);
                float in = side * 1.3F, out = side * 2.3F, toe = side * 2.9F;
                box(vc, pose, sprite, Math.min(in, out), 0.7F, at - 0.35F + swing * 0.5F, Math.max(in, out), 1.15F, at + 0.35F + swing * 0.5F,
                        BACK, SIDE, light, overlay);
                box(vc, pose, sprite, Math.min(out - side * 0.3F, toe), lift, at - 0.55F + swing, Math.max(out - side * 0.3F, toe), 0.75F + lift,
                        at + 0.55F + swing, BACK, BELLY, light, overlay);
            }
            // the tail: four pieces, thinner and thinner, swaying
            float tx = 0, tz = -2.0F, w = 1.0F, h = 0.95F;
            for (int k = 0; k < 4; k++) {
                float bend = sway * (k + 1) * 0.3F;
                float nx = tx + Mth.sin(bend) * 1.3F, nz = tz - Mth.cos(bend) * 1.3F;
                box(vc, pose, sprite, Math.min(tx, nx) - w, 0.7F, Math.min(tz, nz), Math.max(tx, nx) + w, 0.7F + h, Math.max(tz, nz), BACK, SIDE, light, overlay);
                tx = nx;
                tz = nz;
                w *= 0.72F;
                h *= 0.78F;
            }
            poseStack.popPose();
        }
    }

    /** Parts of the gecko's texture (pixels: x0, y0, x1, y1). */
    private static final float[] BACK = {0, 0, 8, 8}, SIDE = {8, 0, 16, 8}, BELLY = {0, 8, 8, 16}, HEAD = {8, 8, 16, 16},
            EYE = {15, 15, 16, 16}, TONGUE = {14, 15, 15, 16};

    /** A box (pixels): its top from {@code top}, its sides from {@code side}, underneath the belly. */
    private static void box(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float x0, float y0, float z0, float x1, float y1, float z1,
            float[] top, float[] side, int light, int overlay) {
        float a = x0 / 16, b = y0 / 16, c = z0 / 16, d = x1 / 16, e = y1 / 16, f = z1 / 16;
        float[] bottom = top == EYE || top == TONGUE ? top : BELLY;
        quad(vc, pose, sp, top, light, overlay, 0, 1, 0, a, e, c, a, e, f, d, e, f, d, e, c);
        quad(vc, pose, sp, bottom, light, overlay, 0, -1, 0, a, b, f, a, b, c, d, b, c, d, b, f);
        quad(vc, pose, sp, side, light, overlay, 0, 0, -1, d, e, c, d, b, c, a, b, c, a, e, c);
        quad(vc, pose, sp, side, light, overlay, 0, 0, 1, a, e, f, a, b, f, d, b, f, d, e, f);
        quad(vc, pose, sp, side, light, overlay, -1, 0, 0, a, e, c, a, b, c, a, b, f, a, e, f);
        quad(vc, pose, sp, side, light, overlay, 1, 0, 0, d, e, f, d, b, f, d, b, c, d, e, c);
    }

    /** Four corners (top-left, bottom-left, bottom-right, top-right of the texture part). */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float[] r, int light, int overlay, float nx, float ny, float nz,
            float... p) {
        float u0 = sp.getU(r[0] / 16), u1 = sp.getU(r[2] / 16), v0 = sp.getV(r[1] / 16), v1 = sp.getV(r[3] / 16);
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        float shade = ny > 0 ? 1.0F : ny < 0 ? 0.55F : nz != 0 ? 0.85F : 0.7F;
        int c = (int) (255 * shade);
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(c, c, c, 255).setUv(uv[i][0], uv[i][1])
                    .setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }
}
