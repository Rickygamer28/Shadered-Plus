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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The terrarium's two geckos: they wander to somewhere new, stop and look round, flick their tongues, and walk on -
 * legs stepping, tails swaying. Desert: spotted yellow-orange; jungle: green with orange crests.
 */
public class TerrariumRenderer implements BlockEntityRenderer<TerrariumBlockEntity> {
    private static final float FLOOR = 3.0F, MIN = 3.5F, MAX = 12.5F, SIZE = 1.6F;
    private static final BlockState[][] SKIN = {
            {Blocks.YELLOW_CONCRETE.defaultBlockState(), Blocks.BLACK_CONCRETE.defaultBlockState(), Blocks.WHITE_CONCRETE.defaultBlockState()},
            {Blocks.LIME_CONCRETE.defaultBlockState(), Blocks.ORANGE_CONCRETE.defaultBlockState(), Blocks.LIME_TERRACOTTA.defaultBlockState()}};
    private static final BlockState EYE = Blocks.BLACK_CONCRETE.defaultBlockState(), TONGUE = Blocks.PINK_CONCRETE.defaultBlockState();

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
        BlockState body = SKIN[scene][0], mark = SKIN[scene][1], belly = SKIN[scene][2];
        float t = now + partialTick;
        for (int i = 0; i < be.geckos.length; i++) {
            TerrariumBlockEntity.Gecko g = be.geckos[i];
            float x = Mth.lerp(partialTick, g.px, g.x), z = Mth.lerp(partialTick, g.pz, g.z);
            float yaw = g.pyaw + Mth.wrapDegrees((g.yaw - g.pyaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * partialTick;
            boolean walking = g.pause == 0;
            float stride = walking ? Mth.sin(g.walked * 3.0F) * 0.35F : 0.0F;
            float sway = Mth.sin(t * 0.15F + i * 2.0F) * (walking ? 0.5F : 0.2F);
            poseStack.pushPose();
            poseStack.translate(x / 16.0F, FLOOR / 16.0F, z / 16.0F);
            poseStack.mulPose(Axis.YP.rotation(yaw));
            poseStack.scale(SIZE, SIZE, SIZE);
            var pos = be.getBlockPos();
            // (+z is its front) body, belly, spots / crest, head, eyes, legs, tail
            BlockSkin.box(body, level, pos, poseStack, buffers, light, overlay, -0.6F, 0.25F, -1.4F, 0.6F, 0.95F, 1.2F);
            BlockSkin.box(belly, level, pos, poseStack, buffers, light, overlay, -0.5F, 0.2F, -1.3F, 0.5F, 0.3F, 1.1F);
            if (scene == 0) {
                BlockSkin.box(mark, level, pos, poseStack, buffers, light, overlay, -0.3F, 0.95F, -0.8F, 0.0F, 1.0F, -0.5F);
                BlockSkin.box(mark, level, pos, poseStack, buffers, light, overlay, 0.1F, 0.95F, 0.0F, 0.4F, 1.0F, 0.3F);
                BlockSkin.box(mark, level, pos, poseStack, buffers, light, overlay, -0.4F, 0.95F, 0.7F, -0.1F, 1.0F, 1.0F);
            } else {
                for (int k = 0; k < 4; k++) {
                    float zz = -1.0F + k * 0.6F;
                    BlockSkin.box(mark, level, pos, poseStack, buffers, light, overlay, -0.1F, 0.95F, zz, 0.1F, 1.15F, zz + 0.3F);
                }
            }
            BlockSkin.box(body, level, pos, poseStack, buffers, light, overlay, -0.5F, 0.3F, 1.2F, 0.5F, 1.0F, 2.2F);
            BlockSkin.box(EYE, level, pos, poseStack, buffers, light, overlay, -0.55F, 0.7F, 1.7F, -0.45F, 0.85F, 1.9F);
            BlockSkin.box(EYE, level, pos, poseStack, buffers, light, overlay, 0.45F, 0.7F, 1.7F, 0.55F, 0.85F, 1.9F);
            if (g.tongue > 0) {
                BlockSkin.box(TONGUE, level, pos, poseStack, buffers, light, overlay, -0.1F, 0.45F, 2.2F, 0.1F, 0.55F, 2.2F + g.tongue * 0.12F);
            }
            for (int leg = 0; leg < 4; leg++) {
                float side = leg % 2 == 0 ? -1 : 1, front = leg < 2 ? 0.7F : -0.9F;
                float swing = (leg == 0 || leg == 3 ? stride : -stride);
                BlockSkin.box(body, level, pos, poseStack, buffers, light, overlay,
                        side < 0 ? -1.15F : 0.6F, 0.0F, front - 0.2F + swing, side < 0 ? -0.6F : 1.15F, 0.35F, front + 0.2F + swing);
            }
            // the tail: three pieces, thinner and thinner, swaying
            float tx = 0, tz = -1.4F, w = 0.4F;
            for (int k = 0; k < 3; k++) {
                float bend = sway * (k + 1) * 0.35F;
                float nx = tx + Mth.sin(bend) * 0.9F, nz = tz - Mth.cos(bend) * 0.9F;
                BlockSkin.box(body, level, pos, poseStack, buffers, light, overlay,
                        Math.min(tx, nx) - w, 0.35F, Math.min(tz, nz), Math.max(tx, nx) + w, 0.35F + w * 1.4F, Math.max(tz, nz));
                tx = nx;
                tz = nz;
                w *= 0.7F;
            }
            poseStack.popPose();
        }
    }
}
