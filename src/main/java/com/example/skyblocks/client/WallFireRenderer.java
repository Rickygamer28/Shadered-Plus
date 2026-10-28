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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The wall fire's glass front: one quad over the opening, the flames drawn in shaders/core/wall_fire. Its texture
 * coordinates: across, the world position along the strip (so side-by-side fires make one long flame); up, the height
 * in the block. The vertex colour carries the flame colour, its alpha on / off.
 */
public class WallFireRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance shader;

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            com.example.skyblocks.client.ShaderLoad.register(event, new ResourceLocation(SkyBlocksMod.MOD_ID, "wall_fire"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType FIRE = create("skyblocks_wall_fire", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_wall_fire_time", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("FireTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("ItemMode").set(0.0F);
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
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_hearth_fire_time", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("FireTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("ItemMode").set(0.0F);
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

        /** The fire tornado as an item: drawn at once with its own matrix (see drawTornadoItem). */
        static final RenderType ITEM = create("skyblocks_fire_item", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_fire_item", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("FireTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("Boost").set(0.0F);
                                shader.safeGetUniform("ItemMode").set(1.0F);
                                shader.safeGetUniform("LampInv").set(ITEM_INV);
                            }
                        }, () -> {}))
                        .setTransparencyState(new TransparencyStateShard("skyblocks_premultiplied_item", () -> {
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

    static final Matrix4f ITEM_INV = new Matrix4f();

    /** How lit the fire looks now: easing towards on / off (about a second to catch, a bit less to die down). */
    private static float litShown(GlowColourBlockEntity be, boolean lit) {
        long now = System.nanoTime();
        if (be.shownLit < 0.0F) {
            be.shownLit = lit ? 1.0F : 0.0F;
        } else {
            float dt = Math.min((now - be.shownLitTime) / 1.0e9F, 0.25F);
            be.shownLit = lit ? Math.min(be.shownLit + dt / 1.1F, 1.0F) : Math.max(be.shownLit - dt / 0.8F, 0.0F);
        }
        be.shownLitTime = now;
        return be.shownLit;
    }

    /** The tornado's box of fire (block units), each vertex with its corner (the shader works out where it is). */
    private static void tornadoBox(VertexConsumer vc, Matrix4f m, int r, int g, int b, float lit) {
        float x0 = 2.0F / 16.0F, y0 = 3.3F / 16.0F, z0 = 2.0F / 16.0F, x1 = 14.0F / 16.0F, y1 = 29.0F / 16.0F, z1 = 14.0F / 16.0F;
        int[][] faces = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 4, 6, 2}, {1, 3, 7, 5}, {2, 6, 7, 3}, {0, 1, 5, 4}};
        for (int[] f : faces) {
            for (int k : f) {
                vc.vertex(m, (k & 1) != 0 ? x1 : x0, (k & 2) != 0 ? y1 : y0, (k & 4) != 0 ? z1 : z0)
                        .uv(k, 320.0F + lit).color(r, g, b, 32 + 16).endVertex(); // (+ how lit, 0..1)
            }
        }
    }

    /** The fire tornado as an item: its whirl of fire, drawn now with this item's matrix. */
    static void drawTornadoItem(PoseStack poseStack, MultiBufferSource buffers, int colour) {
        if (shader == null) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert();
        tornadoBox(buffers.getBuffer(Types.ITEM), m, (colour >> 16) & 255, (colour >> 8) & 255, colour & 255, 1.0F);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(Types.ITEM);
        }
    }

    /** A quad facing -z (north) from xa (its left as seen from the north) to xb; uv across sa..sb, up = the height + flags. */
    private static void quad(RenderType type, MultiBufferSource buffers, Matrix4f m, float xa, float xb, float y0, float y1, float z,
                             float sa, float sb, float flags, int r, int g, int b, int a) {
        VertexConsumer vc = buffers.getBuffer(type);
        vc.vertex(m, xa, y0, z).uv(sa, y0 + flags).color(r, g, b, a).endVertex();
        vc.vertex(m, xb, y0, z).uv(sb, y0 + flags).color(r, g, b, a).endVertex();
        vc.vertex(m, xb, y1, z).uv(sb, y1 + flags).color(r, g, b, a).endVertex();
        vc.vertex(m, xa, y1, z).uv(sa, y1 + flags).color(r, g, b, a).endVertex();
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
            // the whirl's box inside the case (and its glass, drawn even when off)
            m = poseStack.last().pose();
            tornadoBox(buffers.getBuffer(Types.FREE), m, r, g, b, litShown(be, state.getValue(com.example.skyblocks.LavaLampBlock.LIT)));
            return;
        }
        if (!state.hasProperty(com.example.skyblocks.BrickHearthBlock.FACING)
                || state.getValue(com.example.skyblocks.BrickHearthBlock.LEFT)
                || state.getValue(com.example.skyblocks.BrickHearthBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
            return; // (the hearth: only its main block draws the fire)
        }
        float lit = litShown(be, state.getValue(com.example.skyblocks.BrickHearthBlock.LIT));
        if (lit <= 0.0F) {
            return;
        }
        float k = 4.0F * Math.round(lit * 63.0F); // (how lit, in 63ths, carried in uv.x as 4 * that)
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
                k + 26.0F / 16.0F, k + 6.0F / 16.0F, 10.0F, r, g, b, fi * 64 + 32 + 16);
        poseStack.popPose();
    }
}
