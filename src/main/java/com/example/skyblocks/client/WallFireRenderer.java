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
                                net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                                shader.safeGetUniform("CamPos").set((float) cam.x, (float) cam.y, (float) cam.z);
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
                                net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                                shader.safeGetUniform("CamPos").set((float) cam.x, (float) cam.y, (float) cam.z);
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

    /** A quad in the plane x = x, from za (its left as seen from outside) to zb. */
    private static void side(VertexConsumer vc, Matrix4f m, float x, float za, float zb, float y0, float y1, float sa, float sb,
                             float flags, int r, int g, int b, int a) {
        vc.addVertex(m, x, y0, za).setColor(r, g, b, a).setUv(sa, y0 + flags);
        vc.addVertex(m, x, y0, zb).setColor(r, g, b, a).setUv(sb, y0 + flags);
        vc.addVertex(m, x, y1, zb).setColor(r, g, b, a).setUv(sb, y1 + flags);
        vc.addVertex(m, x, y1, za).setColor(r, g, b, a).setUv(sa, y1 + flags);
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox(GlowColourBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0);
    }

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (shader == null || !state.hasProperty(WallFireBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(WallFireBlock.FACING);
        boolean lit = state.getValue(WallFireBlock.LIT);
        int c = GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds(), false);
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
        BlockPos p = be.getBlockPos();
        // along the fire in the world (the same for every fire in a row)
        float base = (switch (facing) {
            case NORTH -> p.getX();
            case SOUTH -> -p.getX() - 1;
            case EAST -> p.getZ();
            default -> -p.getZ() - 1;
        }) % 4096;
        Matrix4f m = poseStack.last().pose();
        // the shader's frame: alpha = facing * 64 + on * 32 + 16; uv = (along the fire, height in the block + 10 * flags)
        int fi = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        a = fi * 64 + (lit ? 32 : 0) + 16;
        if (state.getBlock() instanceof com.example.skyblocks.BrickHearthBlock) {
            if (lit) { // the opening: the flames are marched behind it, among the logs
                quad(Types.FREE, buffers, m, 26.0F / 16.0F, 6.0F / 16.0F, 4.2F / 16.0F, 21.7F / 16.0F, 4.6F / 16.0F,
                        26.0F / 16.0F, 6.0F / 16.0F, 10.0F, r, g, b, a);
            }
            poseStack.popPose();
            return;
        }
        // the glass box: the front glass between the end posts; the side glass at the ends of a row
        boolean left = state.getValue(WallFireBlock.LEFT), right = state.getValue(WallFireBlock.RIGHT);
        float flags = 10.0F * ((left ? 0 : 2) + (right ? 0 : 4));
        float x0 = right ? 0.0F : 0.5F / 16.0F, x1 = left ? 1.0F : 15.5F / 16.0F;
        float y0 = 5.6F / 16.0F, y1 = 13.4F / 16.0F;
        quad(Types.FIRE, buffers, m, x1, x0, y0, y1, 8.15F / 16.0F, base + x1, base + x0, flags, r, g, b, a);
        VertexConsumer vc = buffers.getBuffer(Types.FIRE);
        if (!left) { // (facing out to the left: +x)
            float sx = base + 15.85F / 16.0F;
            side(vc, m, 15.85F / 16.0F, 15.4F / 16.0F, 8.6F / 16.0F, y0, y1, sx, sx, flags, r, g, b, a);
        }
        if (!right) {
            float sx = base + 0.15F / 16.0F;
            side(vc, m, 0.15F / 16.0F, 8.6F / 16.0F, 15.4F / 16.0F, y0, y1, sx, sx, flags, r, g, b, a);
        }
        poseStack.popPose();
    }
}
