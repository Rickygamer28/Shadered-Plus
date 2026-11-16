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
 * A spin pad (SpinPadBlockEntity): its plate turning with the cat on it, and once it's going, a vortex of sky whirling
 * up round the cat - arms of the pad's sky spiralling up a funnel (shaders/core/spin_vortex), taller and wider the faster it
 * spins - with a ring of light round the plate.
 */
public class SpinPadRenderer implements BlockEntityRenderer<SpinPadBlockEntity> {
    private static final ResourceLocation PLATE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/block/spin_pad_plate.png");
    /** The funnel's mesh: rings up it, steps round it. */
    private static final int RINGS = 16, AROUND = 40;

    static net.minecraft.client.renderer.ShaderInstance shader;

    @net.neoforged.fml.common.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,
            value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @net.neoforged.bus.api.SubscribeEvent
        public static void register(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "spin_vortex"),
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
            SkyUsage.ensureFresh(com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(com.noodlegamer76.shadered.client.util.SkyblockType.values()[index]));
        } catch (RuntimeException ignored) {
            // (drawn as it is)
        }
        return VORTEX.computeIfAbsent(sky, k -> VortexTypes.of(index, ShaderPaintRender.picture(index)));
    }

    abstract static class VortexTypes extends RenderType {
        static RenderType of(int sky, ResourceLocation picture) {
            return create("skyblocks_spin_vortex_" + sky, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 8192, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTextureState(new TextureStateShard(picture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }

        private VortexTypes() {
            super("", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private static void funnel(VertexConsumer vc, Matrix4f m, float a, float r, float y, float u, float v, int speed, int turn, int alpha) {
        vc.addVertex(m, 0.5F + Mth.cos(a) * r, y, 0.5F + Mth.sin(a) * r).setUv(u, v).setColor(speed, turn, 0, alpha);
    }

    public SpinPadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SpinPadBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float turn = be.turn(partialTick);
        float s = be.clientSpeed;
        // the plate, turning
        pose.pushPose();
        pose.translate(0.5F, 4.02F / 16.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
        Matrix4f pm = pose.last().pose();
        VertexConsumer plate = buffers.getBuffer(RenderType.entityCutout(PLATE));
        float r = 7.0F / 16.0F;
        plateVertex(plate, pose, -r, -r, 0, 0, light);
        plateVertex(plate, pose, -r, r, 0, 1, light);
        plateVertex(plate, pose, r, r, 1, 1, light);
        plateVertex(plate, pose, r, -r, 1, 0, light);
        pose.popPose();
        if (s < 0.03F) {
            return;
        }
        // the vortex (shaders/core/spin_vortex): a funnel round the cat, arms of the pad's sky spiralling up it -
        // taller and wider, and stronger, the faster it spins
        RenderType vortex = vortexType(be.sky());
        Matrix4f m = pose.last().pose();
        if (vortex != null && shader != null) {
            float height = 0.5F + 5.5F * s * s + 0.6F * s, spread = 0.25F + 2.2F * s * s;
            float turns = turn / 360.0F;
            int sp = Math.round(Mth.clamp(s, 0.0F, 1.0F) * 255), gTurn = Math.round((turns - (float) Math.floor(turns)) * 255) & 255;
            int alpha = Math.round(Mth.clamp(s * 3.0F, 0.0F, 1.0F) * 255);
            VertexConsumer vc = buffers.getBuffer(vortex);
            for (int j = 0; j < RINGS; j++) {
                float f0 = j / (float) RINGS, f1 = (j + 1) / (float) RINGS;
                float y0 = 0.28F + f0 * height, y1 = 0.28F + f1 * height;
                float r0 = 0.42F + spread * (float) Math.pow(f0, 1.5), r1 = 0.42F + spread * (float) Math.pow(f1, 1.5);
                for (int i = 0; i < AROUND; i++) {
                    float a0 = i * Mth.TWO_PI / AROUND, a1 = (i + 1) * Mth.TWO_PI / AROUND;
                    float u0 = i / (float) AROUND, u1 = (i + 1) / (float) AROUND;
                    funnel(vc, m, a0, r0, y0, u0, f0, sp, gTurn, alpha);
                    funnel(vc, m, a1, r0, y0, u1, f0, sp, gTurn, alpha);
                    funnel(vc, m, a1, r1, y1, u1, f1, sp, gTurn, alpha);
                    funnel(vc, m, a0, r1, y1, u0, f1, sp, gTurn, alpha);
                }
            }
        }
        // a ring of light round the plate
        VertexConsumer g = buffers.getBuffer(GlowTypes.GLOW);
        float a = 0.35F * s;
        for (int i = 0; i < 24; i++) {
            float a0 = i * Mth.TWO_PI / 24, a1 = (i + 1) * Mth.TWO_PI / 24;
            float r0 = 0.47F, r1 = 0.47F + 0.25F * s;
            float y = 4.05F / 16.0F;
            g.addVertex(m, 0.5F + Mth.cos(a0) * r0, y, 0.5F + Mth.sin(a0) * r0).setColor(0.7F, 0.85F, 1.0F, a);
            g.addVertex(m, 0.5F + Mth.cos(a0) * r1, y, 0.5F + Mth.sin(a0) * r1).setColor(0.7F, 0.85F, 1.0F, 0.0F);
            g.addVertex(m, 0.5F + Mth.cos(a1) * r1, y, 0.5F + Mth.sin(a1) * r1).setColor(0.7F, 0.85F, 1.0F, 0.0F);
            g.addVertex(m, 0.5F + Mth.cos(a1) * r0, y, 0.5F + Mth.sin(a1) * r0).setColor(0.7F, 0.85F, 1.0F, a);
        }
    }

    private static void plateVertex(VertexConsumer vc, PoseStack pose, float x, float z, float u, float v, int light) {
        vc.addVertex(pose.last(), x, 0, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 1, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(SpinPadBlockEntity be) {
        return be.clientSpeed > 0.03F;
    }

    @Override
    public AABB getRenderBoundingBox(SpinPadBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 2, p.getY(), p.getZ() - 2, p.getX() + 3, p.getY() + 7, p.getZ() + 3);
    }
}
