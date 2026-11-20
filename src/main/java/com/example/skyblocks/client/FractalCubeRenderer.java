package com.example.skyblocks.client;

import com.example.skyblocks.FractalCubeBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A fractal cube's fractal (shaders/core/fractal_cube): traced in a box inside its glass case - a bulb, a sponge, a
 * pyramid or a crystal, slowly turning and changing shape, glowing in colours that drift, brighter to music.
 */
public class FractalCubeRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance shader;
    /** The box's middle (from the block's corner), its half size. */
    private static final float CY = 0.5F, HALF = 0.36F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerFractalShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "fractal_cube"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType FRACTAL = create("skyblocks_fractal_cube", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public FractalCubeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(be.getLevel() instanceof ClientLevel level) || shader == null || !(state.getBlock() instanceof FractalCubeBlock)) {
            return;
        }
        int kind = state.getValue(FractalCubeBlock.KIND);
        int packed = LevelRenderer.getLightColor(level, be.getBlockPos());
        float day = Math.max(0.0F, (level.getSkyDarken(partialTick) - 0.2F) / 0.8F);
        float lit = Math.max(LightTexture.block(packed), LightTexture.sky(packed) * day) / 15.0F;
        // (to music: a smoothed beat, so it swells rather than flickers)
        be.clientA += (MusicReactor.bass() - be.clientA) * 0.2F;
        int pulse = Mth.clamp(Math.round(be.clientA * 255), 0, 255), bright = Mth.clamp(Math.round(lit * 255), 0, 255);
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        Runnable draw = () -> ElevatorRenderer.drawNow(Types.FRACTAL, vc -> {
            Vector3f centre = m.transformPosition(0.5F, CY, 0.5F, new Vector3f());
            float ex = -centre.x, ey = -centre.y, ez = -centre.z;
            int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
            float[] towards = {-ex - HALF, ex - HALF, -ey - HALF, ey - HALF, -ez - HALF, ez - HALF};
            for (int f = 0; f < 6; f++) {
                // (only the box's faces towards the eye - unless the eye's in it)
                if (towards[f] > 0 && !(Math.abs(ex) < HALF && Math.abs(ey) < HALF && Math.abs(ez) < HALF)) {
                    continue;
                }
                for (int c : faces[f]) {
                    float x = ((c & 1) * 2 - 1) * HALF, y = (((c >> 1) & 1) * 2 - 1) * HALF, z = (((c >> 2) & 1) * 2 - 1) * HALF;
                    vc.addVertex(m, 0.5F + x, CY + y, 0.5F + z).setUv(c, kind).setColor(pulse, bright, 0, 255);
                }
            }
        });
        if (!LatePass.later(src -> draw.run())) {
            draw.run();
        }
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
