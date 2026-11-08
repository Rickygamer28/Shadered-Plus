package com.example.skyblocks.client;

import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.LavaLampBlock;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A plasma ball's globe: a box round it, traced in shaders/core/plasma_ball. Each vertex carries which corner of the
 * box it is and the ball's seed (UV), the plasma colour and on / off (colour), and which way and how near the
 * nearest player is (normal) - the filaments reach for them.
 */
public class PlasmaBallRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance shader;
    /** How near a player has to be for the filaments to reach for them (blocks from the globe's middle). */
    private static final double REACH = 4.0;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerPlasmaBallRenderer(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "plasma_ball"), DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, s -> shader = s);
        }
    }

    private static void premultipliedOn() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    private static void premultipliedOff() {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    abstract static class Types extends RenderType {
        static final RenderType GLOBE = create("skyblocks_plasma_ball", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS, 4096,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_plasma_ball_time", () -> {
                            if (shader != null) {
                                MusicReactor.tick(); // (the music's bands up to date, even with nothing else listening)
                                shader.safeGetUniform("PlasmaTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("Boost").set(Math.min(MusicReactor.bass() * 1.6F, 1.0F));
                                shader.safeGetUniform("ItemMode").set(0.0F);
                            }
                        }, () -> {}))
                        .setTransparencyState(new TransparencyStateShard("skyblocks_plasma_premultiplied", PlasmaBallRenderer::premultipliedOn, PlasmaBallRenderer::premultipliedOff))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));
        /** As an item: drawn at once with its own matrix (see drawItem). */
        static final RenderType ITEM = create("skyblocks_plasma_ball_item", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_plasma_ball_item", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("PlasmaTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("Boost").set(0.0F);
                                shader.safeGetUniform("ItemMode").set(1.0F);
                                shader.safeGetUniform("LampInv").set(ITEM_INV);
                            }
                        }, () -> {}))
                        .setTransparencyState(new TransparencyStateShard("skyblocks_plasma_premultiplied_item", PlasmaBallRenderer::premultipliedOn, PlasmaBallRenderer::premultipliedOff))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    static final Matrix4f ITEM_INV = new Matrix4f();

    public PlasmaBallRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null) {
            return;
        }
        boolean on = be.getBlockState().hasProperty(LavaLampBlock.LIT) && be.getBlockState().getValue(LavaLampBlock.LIT);
        int c = GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds());
        // the nearest player's hand (roughly: a bit below the eyes), within reach
        Vec3 centre = Vec3.atLowerCornerOf(be.getBlockPos()).add(0.5, 9.5 / 16.0, 0.5);
        float tx = 0.0F, ty = 0.0F, tz = 0.0F;
        if (be.getLevel() != null) {
            Player p = be.getLevel().getNearestPlayer(centre.x, centre.y, centre.z, REACH, false);
            if (p != null) {
                Vec3 hand = p.getEyePosition(partialTick).add(0.0, -0.45, 0.0).subtract(centre);
                double d = hand.length();
                if (d > 1.0e-3) {
                    float near = (float) Math.max(0.0, Math.min(1.0, 1.15 - (d - 0.6) / (REACH - 0.6)));
                    tx = (float) (hand.x / d) * near;
                    ty = (float) (hand.y / d) * near;
                    tz = (float) (hand.z / d) * near;
                }
            }
        }
        box(buffers.getBuffer(Types.GLOBE), poseStack.last().pose(), c, on ? 255 : 40, seed(be), tx, ty, tz);
    }

    private static float seed(GlowColourBlockEntity be) {
        long h = be.getBlockPos().asLong() * 0x9E3779B97F4A7C15L;
        return (float) ((h >>> 40) & 0xFFFF) / 65536.0F;
    }

    /** The globe as an item, drawn at once with this item's matrix. */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers, int colour) {
        if (shader == null) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert();
        buffers = DrawNow.into(buffers); // (Iris's: drawn later, together - this one drawn now, with its own matrix)
        box(buffers.getBuffer(Types.ITEM), m, colour, 255, 0.37F, 0.0F, 0.0F, 0.0F);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(Types.ITEM);
        }
    }

    /** The box round the globe: its six faces, facing out (the same numbers as the shader's). */
    static void box(VertexConsumer vc, Matrix4f m, int colour, int a, float seed, float tx, float ty, float tz) {
        float x0 = 2.5F / 16.0F, y0 = 4.0F / 16.0F, z0 = 2.5F / 16.0F, x1 = 13.5F / 16.0F, y1 = 15.0F / 16.0F, z1 = 13.5F / 16.0F;
        int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
        int[][] faces = {{4, 5, 7, 6}, {1, 0, 2, 3}, {5, 1, 3, 7}, {0, 4, 6, 2}, {6, 7, 3, 2}, {0, 1, 5, 4}};
        for (int[] f : faces) {
            for (int k : f) {
                vc.addVertex(m, (k & 1) != 0 ? x1 : x0, (k & 2) != 0 ? y1 : y0, (k & 4) != 0 ? z1 : z0)
                        .setUv(k, seed).setColor(r, g, b, a).setNormal(tx, ty, tz);
            }
        }
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox(GlowColourBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos());
    }

    @Override
    public boolean shouldRenderOffScreen(GlowColourBlockEntity be) {
        return false;
    }
}
