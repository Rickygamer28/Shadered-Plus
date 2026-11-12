package com.example.skyblocks.client;

import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SkyClockBlock;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The sky clock's face (shaders/core/sky_clock): the world's time of day, moon phase, rain and thunder handed to the
 * shader as the face's vertex colour, which draws the sky as it is now.
 */
public class SkyClockRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "sky_clock"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType FACE = create("skyblocks_sky_clock", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public SkyClockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || shader == null || !state.hasProperty(SkyClockBlock.FACING)) {
            return;
        }
        Direction out = state.getValue(SkyClockBlock.FACING);
        float angle = switch (out) {
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
        // (the sky's state as colour: where the sun is, the moon's phase, rain, thunder)
        float sun = level.getTimeOfDay(partialTick);
        float moon = level.getMoonPhase() / 8.0F;
        float rain = level.getRainLevel(partialTick), thunder = level.getThunderLevel(partialTick);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-angle));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(Types.FACE);
        // (the face: the inside of the rim, just in front of the back plate; seen from the south, its top left first)
        float x0 = 2.0F / 16, x1 = 14.0F / 16, y0 = 2.0F / 16, y1 = 14.0F / 16, z = 1.55F / 16;
        int r = Mth.clamp((int) (sun * 255), 0, 255), g = Mth.clamp((int) (moon * 255), 0, 255);
        int b = Mth.clamp((int) (rain * 255), 0, 255), a = Mth.clamp((int) (thunder * 255), 0, 255);
        vc.addVertex(m, x0, y1, z).setUv(0, 0).setColor(r, g, b, a);
        vc.addVertex(m, x0, y0, z).setUv(0, 1).setColor(r, g, b, a);
        vc.addVertex(m, x1, y0, z).setUv(1, 1).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z).setUv(1, 0).setColor(r, g, b, a);
        poseStack.popPose();
    }
}
