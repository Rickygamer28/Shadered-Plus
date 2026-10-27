package com.example.skyblocks.client;

import com.example.skyblocks.FogMachineBlock;
import com.example.skyblocks.FogMachineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** The outline of the fog machine's area, when shown (while its menu is open, following the menu's values). */
public class FogMachineRenderer implements BlockEntityRenderer<FogMachineBlockEntity> {
    public FogMachineRenderer(BlockEntityRendererProvider.Context context) {}

    private static final net.minecraft.resources.ResourceLocation FOG =
            new net.minecraft.resources.ResourceLocation(com.example.skyblocks.SkyBlocksMod.MOD_ID, "textures/particle/fog.png");

    @Override
    public void render(FogMachineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (com.example.skyblocks.client.island.IslandRendering.inIsland) {
            if (be.getBlockState().getValue(FogMachineBlock.ON)) {
                islandFog(be, partialTick, poseStack, buffers); // (no particles in an island: soft layers of fog instead)
            }
            return;
        }
        AABB area;
        if (Minecraft.getInstance().screen instanceof FogMachineScreen screen && screen.pos().equals(be.getBlockPos())) {
            if (!screen.showArea()) {
                return;
            }
            Direction facing = be.getBlockState().getValue(FogMachineBlock.FACING);
            area = FogMachineBlockEntity.area(be.getBlockPos(), facing, screen.value(0), screen.value(1), screen.value(2), screen.value(3), screen.value(4));
        } else {
            if (!be.showArea()) {
                return;
            }
            area = be.area();
        }
        area = area.move(-be.getBlockPos().getX(), -be.getBlockPos().getY(), -be.getBlockPos().getZ());
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), area, 0.75F, 0.85F, 1.0F, 1.0F);
    }

    /** In an island: the fog area filled with a few soft, slowly drifting layers of fog. */
    private static void islandFog(FogMachineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        AABB area = be.area().move(-be.getBlockPos().getX(), -be.getBlockPos().getY(), -be.getBlockPos().getZ());
        com.mojang.blaze3d.vertex.VertexConsumer v = buffers.getBuffer(RenderType.entityTranslucent(FOG));
        org.joml.Matrix4f pose = poseStack.last().pose();
        float time = ((Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() % 24000L : 0L) + partialTick) / 20.0F;
        int layers = Math.max(2, Math.min(5, (int) (area.getYsize() * 1.5)));
        float cell = 1.6F;
        for (int l = 0; l < layers; l++) {
            float y = (float) (area.minY + 0.2 + (area.getYsize() - 0.3) * Math.pow((l + 0.5) / layers, 1.4));
            float drift = time * 0.08F * (l % 2 == 0 ? 1 : -1);
            for (float x = (float) area.minX; x < area.maxX; x += cell) {
                for (float z = (float) area.minZ; z < area.maxZ; z += cell) {
                    float x1 = (float) Math.min(x + cell, area.maxX), z1 = (float) Math.min(z + cell, area.maxZ);
                    float u0 = (float) (Math.sin(x * 1.7 + z * 2.3 + l) * 0.15 + drift), w0 = (float) (Math.cos(x * 2.1 - z * 1.3 + l) * 0.15);
                    int a = (int) (90 * (1.0 - (double) l / layers));
                    quad(v, pose, x, y, z, x1, z1, u0, w0, a);
                }
            }
        }
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer v, org.joml.Matrix4f pose, float x0, float y, float z0, float x1, float z1,
                             float u, float w, int alpha) {
        int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        float[][] corners = {{x0, z0, 0, 0}, {x0, z1, 0, 1}, {x1, z1, 1, 1}, {x1, z0, 1, 0}};
        for (float[] c : corners) {
            v.vertex(pose, c[0], y, c[1]).color(235, 235, 245, alpha).uv(c[2] * 0.9F + 0.05F + u * 0.1F, c[3] * 0.9F + 0.05F + w * 0.1F)
                    .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light).normal(0.0F, 1.0F, 0.0F).endVertex();
        }
        for (int i = corners.length - 1; i >= 0; i--) { // (seen from below too)
            float[] c = corners[i];
            v.vertex(pose, c[0], y, c[1]).color(235, 235, 245, alpha).uv(c[2] * 0.9F + 0.05F + u * 0.1F, c[3] * 0.9F + 0.05F + w * 0.1F)
                    .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light).normal(0.0F, -1.0F, 0.0F).endVertex();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(FogMachineBlockEntity be) {
        return true;
    }

        public AABB getRenderBoundingBox(FogMachineBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(FogMachineBlockEntity.MAX_REACH + 1, FogMachineBlockEntity.MAX_HEIGHT + 1, FogMachineBlockEntity.MAX_REACH + 1);
    }
}
