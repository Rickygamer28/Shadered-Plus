package com.example.skyblocks.client;

import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.LavaLampBlock;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The jellyfish tank's water: a box inside the case, each vertex carrying which corner it is (UV0.x) and how lit the
 * tank is (UV0.y), the colour the jellyfish's; shaders/core/jelly_tank traces the water, the jellyfish and the glass.
 */
public class JellyTankRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance shader;
    static final Matrix4f ITEM_INV = new Matrix4f();

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(SkyBlocksMod.MOD_ID, "jelly_tank"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType WATER = create("skyblocks_jelly_tank", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_jelly_tank", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("JellyTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("ItemMode").set(0.0F);
                            }
                        }, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));
        static final RenderType ITEM = create("skyblocks_jelly_tank_item", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_jelly_tank_item", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("JellyTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("ItemMode").set(1.0F);
                                shader.safeGetUniform("LampInv").set(ITEM_INV);
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

    public JellyTankRenderer(BlockEntityRendererProvider.Context context) {}

    /** The water's box (block units, 2 tall), each face facing out, each vertex with its corner. */
    static void box(VertexConsumer vc, Matrix4f m, int r, int g, int b, float lit) {
        float x0 = 2.0F / 16.0F, y0 = 3.3F / 16.0F, z0 = 2.0F / 16.0F, x1 = 14.0F / 16.0F, y1 = 29.0F / 16.0F, z1 = 14.0F / 16.0F;
        int[][] faces = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}, {2, 6, 7, 3}, {0, 1, 5, 4}};
        for (int[] f : faces) {
            for (int k : f) {
                vc.vertex(m, (k & 1) != 0 ? x1 : x0, (k & 2) != 0 ? y1 : y0, (k & 4) != 0 ? z1 : z0).uv(k, lit).color(r, g, b, 255).endVertex();
            }
        }
    }

    /** How lit the tank looks now (easing on and off). */
    private static float litShown(GlowColourBlockEntity be, boolean lit) {
        long now = System.nanoTime();
        if (be.shownLit < 0.0F) {
            be.shownLit = lit ? 1.0F : 0.0F;
        } else {
            float dt = Math.min((now - be.shownLitTime) / 1.0e9F, 0.25F);
            be.shownLit = lit ? Math.min(be.shownLit + dt / 0.8F, 1.0F) : Math.max(be.shownLit - dt / 0.8F, 0.0F);
        }
        be.shownLitTime = now;
        return be.shownLit;
    }

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null) {
            return;
        }
        int c = GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds());
        boolean lit = be.getBlockState().hasProperty(LavaLampBlock.LIT) && be.getBlockState().getValue(LavaLampBlock.LIT);
        box(buffers.getBuffer(Types.WATER), poseStack.last().pose(), (c >> 16) & 255, (c >> 8) & 255, c & 255, litShown(be, lit));
    }

    /** As an item: drawn now with this item's matrix. */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers, int colour) {
        if (shader == null) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert();
        box(buffers.getBuffer(Types.ITEM), m, (colour >> 16) & 255, (colour >> 8) & 255, colour & 255, 1.0F);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(Types.ITEM);
        }
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox(GlowColourBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0);
    }
}
