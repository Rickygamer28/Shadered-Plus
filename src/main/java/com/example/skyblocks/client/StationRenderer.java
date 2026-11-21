package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.StationBlock;
import com.example.skyblocks.StationBlockEntity;
import com.example.skyblocks.TrainEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A station's hologram board, over its post: its name, and whether a train's here, coming, or not - the board's
 * colour by that (station_board: scanlines, a bright frame, a flicker), seen from both sides.
 */
public class StationRenderer implements BlockEntityRenderer<StationBlockEntity> {
    static ShaderInstance boardShader;
    private static final int[] COLOUR = {0x5FD8FF, 0xFFC040, 0x60FF90};

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "station_board"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> boardShader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType BOARD = create("skyblocks_station_board", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> boardShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public StationRenderer(BlockEntityRendererProvider.Context context) {}

    /** 0 no train, 1 one coming, 2 one here (looked again now and then: clientA). */
    private static int status(StationBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return 0;
        }
        if (level.getGameTime() - be.clientTick >= 10 || be.clientTick > level.getGameTime()) {
            be.clientTick = level.getGameTime();
            int st = 0;
            Vec3 c = com.example.skyblocks.TrainRoute.stopPoint(level, be.getBlockPos());
            if (c != null) {
                for (TrainEntity t : level.getEntitiesOfClass(TrainEntity.class, new AABB(be.getBlockPos()).inflate(256.0, 64.0, 256.0))) {
                    boolean goingHere = t.end().distanceToSqr(c) < 2.5;
                    // (here: stopped alongside it - any of its cars by it)
                    if (!t.moving() && (t.inside(c.add(0, 1.5, 0)) >= 0 || t.end().distanceToSqr(c) < 2.5)) {
                        st = 2;
                        break;
                    }
                    if (goingHere && t.moving()) {
                        st = 1;
                    }
                }
            }
            be.clientA = st;
        }
        return (int) be.clientA;
    }

    @Override
    public void render(StationBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (boardShader == null) {
            return;
        }
        int st = status(be);
        int colour = COLOUR[st];
        float facing = -be.getBlockState().getValue(StationBlock.FACING).toYRot();
        PoseStack ps = new PoseStack();
        ps.last().pose().set(pose.last().pose());
        ps.translate(0.5F, 1.55F, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(facing));
        Matrix4f m = new Matrix4f(ps.last().pose());
        String name = be.name();
        Component line = Component.translatable("block.skyblocks.station.status." + (st == 2 ? "here" : st == 1 ? "coming" : "none"));
        Runnable draw = () -> {
            ElevatorRenderer.drawNow(Types.BOARD, vc -> board(vc, m, colour));
            text(m, name, line, colour);
        };
        if (com.example.skyblocks.client.iris.IrisDefer.drawing() || !LatePass.later(src -> draw.run())) {
            draw.run();
        }
    }

    private static void board(VertexConsumer vc, Matrix4f m, int colour) {
        int r = colour >> 16 & 255, g = colour >> 8 & 255, b = colour & 255;
        float w = 1.1F, h = 0.36F;
        vc.addVertex(m, -w, -h, 0).setUv(0, 1).setColor(r, g, b, 255);
        vc.addVertex(m, w, -h, 0).setUv(1, 1).setColor(r, g, b, 255);
        vc.addVertex(m, w, h, 0).setUv(1, 0).setColor(r, g, b, 255);
        vc.addVertex(m, -w, h, 0).setUv(0, 0).setColor(r, g, b, 255);
    }

    /** Its name and status, on both its sides. */
    private static void text(Matrix4f m, String name, Component line, int colour) {
        Font font = Minecraft.getInstance().font;
        MultiBufferSource.BufferSource src = DrawNow.now(null);
        for (int side = 0; side < 2; side++) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(m);
            if (side == 1) {
                ps.mulPose(Axis.YP.rotationDegrees(180));
            }
            ps.translate(0, 0, 0.01F);
            ps.scale(0.022F, -0.022F, 0.022F);
            float nw = font.width(name);
            float sc = Math.min(1.0F, 90.0F / Math.max(nw, 1));
            ps.pushPose();
            ps.translate(0, -9, 0);
            ps.scale(sc, sc, 1);
            font.drawInBatch(name, -nw / 2.0F, -4, 0xFFFFFF, false, ps.last().pose(), src, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            ps.popPose();
            font.drawInBatch(line, -font.width(line) / 2.0F, 4, colour, false, ps.last().pose(), src, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        src.endBatch();
    }

    @Override
    public boolean shouldRenderOffScreen(StationBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(StationBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.5, 0.0, 1.5).expandTowards(0, 1.5, 0);
    }
}
