package com.example.skyblocks.client;

import com.example.skyblocks.PlanetGlobeBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Matrix4f;

/**
 * The Planet Globe's planet: a square over the stand, turned to face you, the planet traced on it in the world's
 * directions (shaders/core/planet_globe) - so it turns on its own tilted axis and keeps its day side to the light.
 */
public class PlanetGlobeRenderer implements BlockEntityRenderer<PlanetGlobeBlock.Entity> {
    public PlanetGlobeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PlanetGlobeBlock.Entity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (FxShaders.planet == null) {
            return;
        }
        if (be.look == 3 || be.look == 5) {
            ShaderLightRenderer.add(ps, 0.5F, 0.78F, 0.5F, 6.0F, be.look == 3 ? 0xFF6A20 : 0x9A5CFF); // (a lava or alien world glows)
        }
        ps.pushPose();
        ps.translate(0.5F, 0.78F, 0.5F);
        ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float s = 0.68F;
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buffers.getBuffer(FxShaders.Types.PLANET);
        int seed = (int) (be.getBlockPos().asLong() * 31 & 255);
        int g = be.rings ? 255 : 0;
        vc.addVertex(m, -s, -s, 0).setUv(-1, -1).setColor(be.look, g, seed, 255);
        vc.addVertex(m, s, -s, 0).setUv(1, -1).setColor(be.look, g, seed, 255);
        vc.addVertex(m, s, s, 0).setUv(1, 1).setColor(be.look, g, seed, 255);
        vc.addVertex(m, -s, s, 0).setUv(-1, 1).setColor(be.look, g, seed, 255);
        ps.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PlanetGlobeBlock.Entity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.3, 0.0, 0.3).expandTowards(0, 0.6, 0);
    }
}
