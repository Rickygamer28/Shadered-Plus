package com.example.skyblocks.client;

import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.VisualizerBlock;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The music visualizer's screen: one quad per block, its texture coordinates running 0..1 across and up the whole
 * joined screen, drawn by shaders/core/visualizer from the music's 16 bands and their last seconds (MusicReactor).
 * Vertex colour: the colour (black = rainbow); alpha: look * 32. UV: 0..1 across / up, plus 2 * blocks wide / high.
 */
public class VisualizerRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance shader;

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(SkyBlocksMod.MOD_ID, "visualizer"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType SCREEN = create("skyblocks_visualizer", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTextureState(new TextureStateShard(MusicReactor.HISTORY, true, false))
                        .setTexturingState(new TexturingStateShard("skyblocks_visualizer_music", () -> {
                            if (shader != null) {
                                for (int i = 0; i < 4; i++) {
                                    shader.safeGetUniform("Bands" + i).set(MusicReactor.band(i * 4), MusicReactor.band(i * 4 + 1),
                                            MusicReactor.band(i * 4 + 2), MusicReactor.band(i * 4 + 3));
                                }
                                shader.safeGetUniform("Music").set(MusicReactor.music());
                                shader.safeGetUniform("VisTime").set(LavaLampRenderer.seconds());
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

    public VisualizerRenderer(BlockEntityRendererProvider.Context context) {}

    private static int count(Level level, BlockPos pos, Direction d, Direction facing) {
        int n = 0;
        BlockPos p = pos.relative(d);
        while (n < 63) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof VisualizerBlock) || s.getValue(VisualizerBlock.FACING) != facing) {
                break;
            }
            n++;
            p = p.relative(d);
        }
        return n;
    }

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        Level level = be.getLevel();
        if (shader == null || level == null || !(state.getBlock() instanceof VisualizerBlock)) {
            return;
        }
        Direction facing = state.getValue(VisualizerBlock.FACING);
        BlockPos pos = be.getBlockPos();
        // where in the joined screen (from its left as you face it, and its bottom)
        int left = count(level, pos, facing.getClockWise(), facing), right = count(level, pos, facing.getCounterClockWise(), facing);
        int below = count(level, pos, Direction.DOWN, facing), above = count(level, pos, Direction.UP, facing);
        int w = Math.min(left + 1 + right, 64), h = Math.min(below + 1 + above, 64);
        int c = be.rgb() ? 0 : be.colour();
        if (c == 0 && !be.rgb()) {
            c = 0x010101;
        }
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255, a = state.getValue(VisualizerBlock.MODE) * 32;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        }));
        poseStack.translate(-0.5, 0.0, -0.5);
        Matrix4f m = poseStack.last().pose();
        // in the north-facing frame: the screen at z = 13.95/16; +x is to the left as you face it
        float x0 = state.getValue(VisualizerBlock.RIGHT) ? 0.0F : 1.0F / 16.0F, x1 = state.getValue(VisualizerBlock.LEFT) ? 1.0F : 15.0F / 16.0F;
        float y0 = state.getValue(VisualizerBlock.DOWN) ? 0.0F : 1.0F / 16.0F, y1 = state.getValue(VisualizerBlock.UP) ? 1.0F : 15.0F / 16.0F;
        float z = 13.95F / 16.0F;
        // uv: 0..1 across / up the whole screen, plus 2 * its width / height in blocks (the shader takes them apart)
        float u0 = (left + (1.0F - x1)) / w + 2 * w, u1 = (left + (1.0F - x0)) / w + 2 * w;
        float v0 = (below + y0) / h + 2 * h, v1 = (below + y1) / h + 2 * h;
        VertexConsumer vc = buffers.getBuffer(Types.SCREEN);
        vc.vertex(m, x1, y0, z).uv(u0, v0).color(r, g, b, a).endVertex();
        vc.vertex(m, x0, y0, z).uv(u1, v0).color(r, g, b, a).endVertex();
        vc.vertex(m, x0, y1, z).uv(u1, v1).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y1, z).uv(u0, v1).color(r, g, b, a).endVertex();
        poseStack.popPose();
    }
}
