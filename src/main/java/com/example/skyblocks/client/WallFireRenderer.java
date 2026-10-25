package com.example.skyblocks.client;

import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.WallFireBlock;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The wall fire's glass front: one quad over the opening, the flames drawn in shaders/core/wall_fire. Its texture
 * coordinates: across, the world position along the strip (so side-by-side fires make one long flame); up, the height
 * in the block. The vertex colour carries the flame colour, its alpha on / off.
 */
public class WallFireRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "wall_fire"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType FIRE = create("skyblocks_wall_fire", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_wall_fire_time", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("FireTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("Boost").set(Math.min(MusicReactor.bass() * 1.2F, 1.0F));
                            }
                        }, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public WallFireRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (shader == null || !state.hasProperty(WallFireBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(WallFireBlock.FACING);
        boolean lit = state.getValue(WallFireBlock.LIT);
        int c = GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds());
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255, a = lit ? 255 : 0;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        }));
        poseStack.translate(-0.5, 0.0, -0.5);
        // in the north-facing frame: the front at z = 10/16, the opening between the ends of the frame
        float x0 = state.getValue(WallFireBlock.RIGHT) ? 0.0F : 1.0F / 16.0F;
        float x1 = state.getValue(WallFireBlock.LEFT) ? 1.0F : 15.0F / 16.0F;
        float y0 = 4.5F / 16.0F, y1 = 12.0F / 16.0F, z = 10.05F / 16.0F;
        // along the strip in the world (the same for every fire in a row)
        BlockPos p = be.getBlockPos();
        float base = switch (facing) {
            case NORTH -> p.getX();
            case SOUTH -> -p.getX() - 1;
            case EAST -> p.getZ();
            default -> -p.getZ() - 1;
        };
        float s0 = (base % 4096) + x0, s1 = (base % 4096) + x1;
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(Types.FIRE);
        // facing north (towards -z): seen from the north, x grows to the left
        vc.addVertex(m, x1, y0, z).setColor(r, g, b, a).setUv(s1, y0);
        vc.addVertex(m, x0, y0, z).setColor(r, g, b, a).setUv(s0, y0);
        vc.addVertex(m, x0, y1, z).setColor(r, g, b, a).setUv(s0, y1);
        vc.addVertex(m, x1, y1, z).setColor(r, g, b, a).setUv(s1, y1);
        poseStack.popPose();
    }
}
