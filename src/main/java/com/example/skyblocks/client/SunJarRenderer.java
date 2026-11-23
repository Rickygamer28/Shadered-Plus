package com.example.skyblocks.client;

import com.example.skyblocks.SunJarBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/** The sun in a sun jar (shaders/core/sun_orb): a little sun, boiling, its flares licking out, bobbing gently, turned to face you. */
public class SunJarRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "sun_orb"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType ORB = create("skyblocks_sun_orb", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
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

    public SunJarRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || shader == null || !state.hasProperty(SunJarBlock.SUN) || !state.getValue(SunJarBlock.SUN)) {
            return;
        }
        ShaderLightRenderer.add(pose, 0.5F, 0.35F, 0.5F, 13.0F, 0xFF9838); // (its light: a warm orange)
        float t = level.getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5F, 0.31F + 0.015F * Mth.sin(t * 0.05F), 0.5F);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float s = 0.34F;
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(Types.ORB);
        int phase = 0;
        vertex(vc, m, -s, -s, -1, -1, phase);
        vertex(vc, m, s, -s, 1, -1, phase);
        vertex(vc, m, s, s, 1, 1, phase);
        vertex(vc, m, -s, s, -1, 1, phase);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float u, float v, int phase) {
        vc.addVertex(m, x, y, 0).setColor(phase & 7, 0, 0, 255).setUv(u, v);
    }

    /** (drawn - and so its light given - whenever anything its light reaches is on screen, not only the block itself) */
    @Override
    public boolean shouldRenderOffScreen(SimpleRenderedBlockEntity be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SimpleRenderedBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(13.0);
    }
}
