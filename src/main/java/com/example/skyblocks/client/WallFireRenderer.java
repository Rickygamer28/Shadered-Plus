package com.example.skyblocks.client;

import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
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
        /** Free flames (the hearth): see-through round the flames, drawn after the solid blocks. */
        static final RenderType FREE = create("skyblocks_hearth_fire", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_hearth_fire_time", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("FireTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("Boost").set(Math.min(MusicReactor.bass() * 1.2F, 1.0F));
                            }
                        }, () -> {}))
                        .setTransparencyState(new TransparencyStateShard("skyblocks_premultiplied", () -> {
                            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                            com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                        }, () -> {
                            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                        }))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public WallFireRenderer(BlockEntityRendererProvider.Context context) {}

    /** A quad facing -z (north) from xa (its left as seen from the north) to xb; uv across sa..sb, up = the height + flags. */
    private static void quad(RenderType type, MultiBufferSource buffers, Matrix4f m, float xa, float xb, float y0, float y1, float z,
                             float sa, float sb, float flags, int r, int g, int b, int a) {
        VertexConsumer vc = buffers.getBuffer(type);
        vc.addVertex(m, xa, y0, z).setColor(r, g, b, a).setUv(sa, y0 + flags);
        vc.addVertex(m, xb, y0, z).setColor(r, g, b, a).setUv(sb, y0 + flags);
        vc.addVertex(m, xb, y1, z).setColor(r, g, b, a).setUv(sb, y1 + flags);
        vc.addVertex(m, xa, y1, z).setColor(r, g, b, a).setUv(sa, y1 + flags);
    }


    public net.minecraft.world.phys.AABB getRenderBoundingBox(GlowColourBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0);
    }

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (shader == null) {
            return;
        }
        int c = GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds(), false);
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        Matrix4f m;
        if (state.getBlock() instanceof com.example.skyblocks.FireTornadoBlock) {
            if (!state.getValue(com.example.skyblocks.LavaLampBlock.LIT)) {
                return;
            }
            // the whirl's box inside the case: uv = (which corner, the tornado's code), the shader works out the rest
            m = poseStack.last().pose();
            VertexConsumer vc = buffers.getBuffer(Types.FREE);
            float x0 = 2.6F / 16.0F, y0 = 3.3F / 16.0F, z0 = 2.6F / 16.0F, x1 = 13.4F / 16.0F, y1 = 29.0F / 16.0F, z1 = 13.4F / 16.0F;
            int[][] faces = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}, {2, 6, 7, 3}, {0, 1, 5, 4}};
            for (int[] f : faces) {
                for (int k : f) {
                    vc.addVertex(m, (k & 1) != 0 ? x1 : x0, (k & 2) != 0 ? y1 : y0, (k & 4) != 0 ? z1 : z0)
                            .setColor(r, g, b, 32 + 16).setUv(k, 320.0F);
                }
            }
            return;
        }
        if (!state.hasProperty(com.example.skyblocks.BrickHearthBlock.FACING)
                || state.getValue(com.example.skyblocks.BrickHearthBlock.LEFT)
                || state.getValue(com.example.skyblocks.BrickHearthBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                || !state.getValue(com.example.skyblocks.BrickHearthBlock.LIT)) {
            return; // (the hearth: only its main block draws the fire)
        }
        Direction facing = state.getValue(com.example.skyblocks.BrickHearthBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        }));
        poseStack.translate(-0.5, 0.0, -0.5);
        m = poseStack.last().pose();
        // the shader's frame: alpha = facing * 64 + on * 32 + 16; uv = (across the hearth, height in the block + 10)
        int fi = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        // the opening: the flames are marched behind it, among the logs
        quad(Types.FREE, buffers, m, 26.0F / 16.0F, 6.0F / 16.0F, 4.2F / 16.0F, 21.7F / 16.0F, 4.6F / 16.0F,
                26.0F / 16.0F, 6.0F / 16.0F, 10.0F, r, g, b, fi * 64 + 32 + 16);
        poseStack.popPose();
    }
}
