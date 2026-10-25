package com.example.skyblocks.client;

import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.LavaLampBlock;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A lava lamp's glass: a box round it, traced in shaders/core/lava_lamp (the round glass, the liquid, the wax blobs
 * rising and sinking). The vertex colour carries the wax colour (RGB mode worked out here) and, in its alpha, on / off.
 */
public class LavaLampRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "lava_lamp"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s);
        }
    }

    /** Seconds of game time (for the shaders' movement), kept small. */
    static float seconds() {
        Minecraft mc = Minecraft.getInstance();
        long t = mc.level == null ? 0L : mc.level.getGameTime();
        return (float) (((t % 72_000L) + mc.getTimer().getGameTimeDeltaPartialTick(false)) / 20.0);
    }

    abstract static class Types extends RenderType {
        static final RenderType LAMP = create("skyblocks_lava_lamp", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_lava_lamp_camera", () -> {
                            if (shader != null) {
                                Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                                shader.safeGetUniform("CamPos").set((float) cam.x, (float) cam.y, (float) cam.z);
                                shader.safeGetUniform("LampTime").set(seconds());
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

    public LavaLampRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null) {
            return;
        }
        boolean on = be.getBlockState().hasProperty(LavaLampBlock.LIT) && be.getBlockState().getValue(LavaLampBlock.LIT);
        int c = GlowColourBlockEntity.shown(be, seconds());
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255, a = on ? 255 : 40;
        VertexConsumer vc = buffers.getBuffer(Types.LAMP);
        if (be.getBlockState().getBlock() instanceof com.example.skyblocks.BigLavaLampBlock) {
            // the big lamp: the same lamp stretched (see the shader), its glass from 0.55 to 1.62 blocks up
            box(vc, poseStack.last().pose(), 0.5F - 0.175F * BIG_H, 0.305F * BIG_V + BIG_Y, 0.5F - 0.175F * BIG_H,
                    0.5F + 0.175F * BIG_H, 0.805F * BIG_V + BIG_Y, 0.5F + 0.175F * BIG_H, r, g, b, a, 1.0F);
        } else {
            box(vc, poseStack.last().pose(), 0.325F, 0.305F, 0.325F, 0.675F, 0.805F, 0.675F, r, g, b, a, 0.0F);
        }
    }

    /** The big lamp: the small one's shape scaled across, up, and moved up (the same numbers as the shader's). */
    static final float BIG_H = 1.55F, BIG_V = 2.2F, BIG_Y = -0.143F;

    public net.minecraft.world.phys.AABB getRenderBoundingBox(GlowColourBlockEntity be) {
        net.minecraft.core.BlockPos p = be.getBlockPos();
        return new net.minecraft.world.phys.AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 2, p.getZ() + 1);
    }

    /** A box's six faces, facing out. */
    static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int r, int g, int b, int a, float big) {
        float[][] f = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, // south
                {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}, // north
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}, // east
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, // west
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, // up
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, // down
        };
        for (float[] q : f) {
            for (int v = 0; v < 4; v++) {
                // uv: (big or not, the height in the lamp's block - to find the block it stands on)
                vc.addVertex(m, q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).setUv(big, q[v * 3 + 1]).setColor(r, g, b, a);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(GlowColourBlockEntity be) {
        return false;
    }
}
