package com.example.skyblocks.client;

import com.example.skyblocks.PowerCoreBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A power core (PowerCoreBlockEntity): what it holds, in its skin (shaders/core/energy_core), on a square turned to face
 * you in its middle - small in its own frame, filling its core chamber when it's in one - easing to how full it is; and
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

    public PowerCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PowerCoreBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
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
        // how big: a little under its frame alone; in a chamber, the room inside it
        float half = chamber > 0 ? (chamber - 1) / 2.0F - 0.55F : 0.46F;
        int skin = be.skin();
        ShaderLightRenderer.add(pose, 0.5F, 0.5F, 0.5F, chamber > 0 ? chamber + 1.0F + 3.0F * full : 3.0F + 4.0F * full,
                LIGHT[Math.floorMod(skin, LIGHT.length)]);
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(Types.CORE);
        int r = skin & 255, g = Math.round(full * 255), b = chamber > 0 ? 255 : 0;
        vc.addVertex(m, -half, -half, 0).setColor(r, g, b, 255).setUv(-1, -1);
        vc.addVertex(m, half, -half, 0).setColor(r, g, b, 255).setUv(1, -1);
        vc.addVertex(m, half, half, 0).setColor(r, g, b, 255).setUv(1, 1);
        vc.addVertex(m, -half, half, 0).setColor(r, g, b, 255).setUv(-1, 1);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(PowerCoreBlockEntity be) {
        return be.chamber() > 0;
    }

    @Override
    public AABB getRenderBoundingBox(PowerCoreBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(Math.max(1, (be.chamber() - 1) / 2.0));
    }
}
