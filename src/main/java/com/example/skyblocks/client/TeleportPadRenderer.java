package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TeleportPadBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/** A teleport pad's beam: a column of rising light in its colour while something charges on it, a burst when it goes. */
public class TeleportPadRenderer implements BlockEntityRenderer<TeleportPadBlockEntity> {
    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerTeleportShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "teleport_beam"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType BEAM = create("skyblocks_teleport_beam", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1024,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public TeleportPadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TeleportPadBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float glow = Mth.lerp(partialTick, be.oGlow, be.glow);
        float burst = be.flash > 0 ? (be.flash - partialTick) / 16.0F : 0.0F;
        float strength = Math.max(glow * 0.8F, burst);
        if (shader == null || strength <= 0.01F) {
            return;
        }
        float[] c = TeleportPadBlockEntity.colour(be.getBlockState());
        int r = (int) (c[0] * 255), g = (int) (c[1] * 255), b = (int) (c[2] * 255), a = (int) (Math.min(1.0F, strength) * 255);
        float radius = 0.38F + burst * 0.15F, height = 1.2F + 1.4F * Math.max(glow, burst), y0 = 3.0F / 16;
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        java.util.function.Consumer<MultiBufferSource.BufferSource> draw = source -> {
            VertexConsumer out = source.getBuffer(Types.BEAM);
            int n = 16;
            for (int k = 0; k < n; k++) {
                double t0 = Math.PI * 2 * k / n, t1 = Math.PI * 2 * (k + 1) / n;
                float x0 = 0.5F + (float) Math.cos(t0) * radius, z0 = 0.5F + (float) Math.sin(t0) * radius;
                float x1 = 0.5F + (float) Math.cos(t1) * radius, z1 = 0.5F + (float) Math.sin(t1) * radius;
                float u0 = (float) k / n, u1 = (float) (k + 1) / n;
                out.addVertex(m, x0, y0, z0).setUv(u0, 0).setColor(r, g, b, a);
                out.addVertex(m, x1, y0, z1).setUv(u1, 0).setColor(r, g, b, a);
                out.addVertex(m, x1, y0 + height, z1).setUv(u1, 1).setColor(r, g, b, a);
                out.addVertex(m, x0, y0 + height, z0).setUv(u0, 1).setColor(r, g, b, a);
            }
            source.endBatch(Types.BEAM);
        };
        if (!LatePass.later(draw)) {
            draw.accept(DrawNow.now(buffers));
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(TeleportPadBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0, 3, 0);
    }
}
