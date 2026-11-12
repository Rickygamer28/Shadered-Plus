package com.example.skyblocks.client;

import com.example.skyblocks.CatWindowPerchBlock;
import com.example.skyblocks.OiiaCatEntity;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What a cat in a window perch is watching: while one lies in it, butterflies flutter about outside the window
 * (wandering loops, wings beating), and every so often a bird swoops past.
 */
public class CatWindowPerchRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    private static final ResourceLocation WINGS = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/entity/window_wings.png");

    public CatWindowPerchRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(CatWindowPerchBlock.FACING)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        if (level.getEntitiesOfClass(OiiaCatEntity.class, new AABB(pos).inflate(0.6), c -> c.mood() == OiiaCatEntity.WINDOW).isEmpty()) {
            return; // (no one's looking)
        }
        Direction in = state.getValue(CatWindowPerchBlock.FACING).getOpposite(), side = in.getClockWise();
        float t = level.getGameTime() + partialTick;
        int seed = pos.hashCode();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WINGS));
        Vec3 out = new Vec3(0.5 + in.getStepX() * 2.8, 0.55, 0.5 + in.getStepZ() * 2.8);
        int outLight = LevelRenderer.getLightColor(level, pos.relative(in, 3));
        // butterflies (by day; fewer, paler moths' colours by night)
        boolean day = level.isDay();
        for (int i = 0; i < (day ? 3 : 1); i++) {
            float ph = i * 2.17F + (seed & 15);
            float s = 1.0F + i * 0.23F;
            float across = 1.5F * Mth.sin(t * 0.021F * s + ph) + 0.4F * Mth.sin(t * 0.057F * s + ph * 1.7F);
            float deep = 0.8F * Mth.sin(t * 0.017F * s + ph * 0.6F) + i * 0.4F;
            float up = 0.45F * Mth.sin(t * 0.043F * s + ph * 1.3F) + 0.12F * Mth.sin(t * 0.31F + ph) + i * 0.25F - 0.25F;
            Vec3 p = out.add(side.getStepX() * across + in.getStepX() * deep, up, side.getStepZ() * across + in.getStepZ() * deep);
            // (heading the way it's drifting across)
            float vx = Mth.cos(t * 0.021F * s + ph) * side.getStepX() + Mth.cos(t * 0.017F * s + ph * 0.6F) * 0.5F * in.getStepX();
            float vz = Mth.cos(t * 0.021F * s + ph) * side.getStepZ() + Mth.cos(t * 0.017F * s + ph * 0.6F) * 0.5F * in.getStepZ();
            float yaw = (float) Math.atan2(vx, vz);
            float flap = 0.15F + 1.1F * (0.5F + 0.5F * Mth.sin(t * 1.5F + ph * 3.0F));
            creature(vc, poseStack, p, yaw, flap, 0.16F, day ? i % 3 : 2, outLight);
        }
        // a bird swooping past now and then (by day)
        int period = 360;
        float f = ((t + (seed & 255)) % period) / 70.0F;
        if (day && f < 1.0F) {
            float across = -4.5F + 9.0F * f;
            float up = 1.4F + 0.9F * Mth.sin(f * Mth.PI) - 1.2F * f * (1 - f);
            Vec3 p = out.add(side.getStepX() * across + in.getStepX() * 1.6, up, side.getStepZ() * across + in.getStepZ() * 1.6);
            float yaw = (float) Math.atan2(side.getStepX(), side.getStepZ());
            float flap = 0.4F + 0.9F * (0.5F + 0.5F * Mth.sin(t * 1.1F));
            creature(vc, poseStack, p, yaw, flap, 0.32F, 3, outLight);
        }
    }

    /** A butterfly (kind 0 .. 2) or a bird (3): its two wings, beating (flap: radians up from flat), the way it's heading. */
    private static void creature(VertexConsumer vc, PoseStack ps, Vec3 at, float yaw, float flap, float size, int kind, int light) {
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(new Quaternionf().rotationY(yaw));
        for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            ps.mulPose(new Quaternionf().rotationZ(side * flap));
            Matrix4f m = ps.last().pose();
            // (the wing: out from the body along x, the length of the body along z)
            float u0 = kind * 8 / 32.0F, u1 = (kind * 8 + 8) / 32.0F;
            float x1 = side * size;
            Vector3f n = new Vector3f(0, 1, 0);
            ps.last().normal().transform(n);
            vertex(vc, m, 0, 0, -size * 0.5F, u0, 0, light, n);
            vertex(vc, m, x1, 0, -size * 0.5F, u1, 0, light, n);
            vertex(vc, m, x1, 0, size * 0.5F, u1, 1, light, n);
            vertex(vc, m, 0, 0, size * 0.5F, u0, 1, light, n);
            ps.popPose();
        }
        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int light, Vector3f n) {
        vc.addVertex(m, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
    }

    @Override
    public AABB getRenderBoundingBox(SimpleRenderedBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(6);
    }

    @Override
    public boolean shouldRenderOffScreen(SimpleRenderedBlockEntity be) {
        return true;
    }
}
