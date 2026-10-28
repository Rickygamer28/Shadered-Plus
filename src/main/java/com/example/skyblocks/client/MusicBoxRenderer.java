package com.example.skyblocks.client;

import com.example.skyblocks.MusicBoxBlock;
import com.example.skyblocks.MusicBoxBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The music box's moving parts: its lid swinging open while it plays (and shut again), the little dancer rising out
 * of it and turning on her toes, and its key turning in its side.
 */
public class MusicBoxRenderer implements BlockEntityRenderer<MusicBoxBlockEntity> {
    public static final net.minecraft.resources.ResourceLocation LID = model("block/music_box_lid"), DANCER = model("block/music_box_dancer"),
            KEY = model("block/music_box_key");

    private static ResourceLocation model(String path) {
        return (new ResourceLocation(SkyBlocksMod.MOD_ID, path));
    }

    private long lastNanos;

    public MusicBoxRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MusicBoxBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof MusicBoxBlock) || be.getLevel() == null) {
            return;
        }
        boolean playing = state.getValue(MusicBoxBlock.PLAYING);
        // (the lid eased open and shut: by the time gone since the last frame)
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, 0.1F);
        lastNanos = now;
        be.open += ((playing ? 1.0F : 0.0F) - be.open) * Math.min(1.0F, dt * (playing ? 3.5F : 5.0F));
        float open = be.open < 0.002F ? 0.0F : be.open;
        float ease = open * open * (3.0F - 2.0F * open);
        float ticks = (be.getLevel().getGameTime() - be.start()) + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-((state.getValue(MusicBoxBlock.FACING).get2DDataValue() + 2) % 4) * 90.0F));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        // the lid, on its hinge at the back
        poseStack.pushPose();
        poseStack.translate(0.5F, 5.0F / 16.0F, 12.0F / 16.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(105.0F * ease));
        poseStack.translate(-0.5F, -5.0F / 16.0F, -12.0F / 16.0F);
        draw(LID, state, poseStack, buffers, light);
        poseStack.popPose();
        // the dancer, rising as the lid opens and turning while the tune plays
        if (ease > 0.01F) {
            poseStack.pushPose();
            poseStack.translate(0.0F, (ease - 1.0F) * 4.5F / 16.0F, 0.0F);
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(playing ? ticks * 4.0F : 0.0F));
            poseStack.translate(-0.5F, 0.0F, -0.5F);
            draw(DANCER, state, poseStack, buffers, light);
            poseStack.popPose();
        }
        // the key in its side, turning slowly
        poseStack.pushPose();
        poseStack.translate(13.5F / 16.0F, 2.5F / 16.0F, 0.5F);
        poseStack.mulPose(Axis.XP.rotationDegrees(playing ? -ticks * 2.0F : 0.0F));
        poseStack.translate(-13.5F / 16.0F, -2.5F / 16.0F, -0.5F);
        draw(KEY, state, poseStack, buffers, light);
        poseStack.popPose();
        poseStack.popPose();
    }

    private static void draw(ResourceLocation name, BlockState state, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(poseStack.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                state, Minecraft.getInstance().getModelManager().getModel(name), 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderType.cutout());
    }
}
