package com.example.skyblocks.client;

import com.example.skyblocks.LightBridgeEmitterBlock;
import com.example.skyblocks.LightBridgeEmitterBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/** A light bridge: drawn by the emitter that keeps it (facing east or south), from its front to the other one's. */
public class LightBridgeRenderer implements BlockEntityRenderer<LightBridgeEmitterBlockEntity> {
    static ShaderInstance shader;
    private static float time, length;
    private static int colour;
    private static final float THICK = 2.0F / 16.0F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerLightBridgeShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "light_bridge"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    private static void setUp() {
        if (shader == null) {
            return;
        }
        shader.safeGetUniform("Time").set(time);
        shader.safeGetUniform("Length").set(length);
        shader.safeGetUniform("Rainbow").set(colour < 0 ? 1.0F : 0.0F);
        int c = Math.max(colour, 0);
        shader.safeGetUniform("Colour").set(((c >> 16) & 255) / 255.0F, ((c >> 8) & 255) / 255.0F, (c & 255) / 255.0F);
    }

    abstract static class Types extends RenderType {
        static final RenderType BRIDGE = create("skyblocks_light_bridge", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_light_bridge_settings", LightBridgeRenderer::setUp, () -> {}))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public LightBridgeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(LightBridgeEmitterBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        int len = be.length();
        if (shader == null || level == null || len <= 1) {
            return;
        }
        Direction f = be.getBlockState().getValue(LightBridgeEmitterBlock.FACING);
        time = ((level.getGameTime() % 240000L) + partialTick) / 20.0F;
        length = len;
        colour = be.colour();
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        VertexConsumer vc = source.getBuffer(Types.BRIDGE);
        Matrix4f m = poseStack.last().pose();
        boolean east = f == Direction.EAST;
        // (in "along, up, across": the plate from the emitter's front, 1, to the other's, length)
        float[][] quads = {
                {1, THICK, 0, len, THICK, 0, len, THICK, 1, 1, THICK, 1, 255},
                {1, 0.002F, 0, 1, 0.002F, 1, len, 0.002F, 1, len, 0.002F, 0, 150},
                {1, 0, 0, len, 0, 0, len, THICK, 0, 1, THICK, 0, 200},
                {1, 0, 1, 1, THICK, 1, len, THICK, 1, len, 0, 1, 200}};
        for (float[] q : quads) {
            int s = (int) q[12];
            for (int i = 0; i < 12; i += 3) {
                float a = q[i], up = q[i + 1], c = q[i + 2];
                float x = east ? a : c, z = east ? c : a;
                vc.addVertex(m, x, up, z).setUv(a, c).setColor(s, s, s, 255);
            }
        }
        source.endBatch(Types.BRIDGE);
    }

    @Override
    public boolean shouldRenderOffScreen(LightBridgeEmitterBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(LightBridgeEmitterBlockEntity be) {
        BlockPos p = be.getBlockPos();
        Direction f = be.getBlockState().getValue(LightBridgeEmitterBlock.FACING);
        return new AABB(p).expandTowards(f.getStepX() * (double) be.length(), 0.0, f.getStepZ() * (double) be.length());
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
