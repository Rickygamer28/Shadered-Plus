package com.example.skyblocks.client.island;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The name of a renamed Sky Island: a small brass plaque on the bottom edge of a painting's frame (like a museum
 * label), or a floating name above a pedestal while you look at it (like a name tag).
 */
final class IslandPlaque {
    private static final ResourceLocation PLAQUE =
            ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/block/island_painting_plaque.png");
    /** Text size: a sign's (9 pixel high letters = 0.094 blocks), smaller for long names. */
    private static final float TEXT_SCALE = 1.0F / 96.0F;
    private static final float MIN_TEXT_SCALE = 1.0F / 200.0F;
    private static final float PAD_X = 0.04F;
    private static final float PAD_Y = 0.025F;
    /** Where the plaque's middle is: on the frame's 1 pixel bottom bar. */
    private static final float CENTER_Y = 0.5F / 16.0F;
    private static final int TEXT_COLOR = 0x3A2618;

    /**
     * On a painting {@code width} blocks wide, from its bottom corner block (pose at that block's corner). Works in a
     * north-facing painting's space like IslandWindow: the painting spans x = 0..width, its frame's front is at
     * z = 15/16 and the viewer is on the -z side.
     */
    static void renderOnPainting(Component name, Direction facing, int width, PoseStack poseStack,
                                 MultiBufferSource buffer, int light) {
        Font font = Minecraft.getInstance().font;
        FormattedCharSequence text = name.getVisualOrderText();
        int textWidth = Math.max(1, font.width(text));
        float maxWidth = width - 4.0F / 16.0F;
        float scale = Math.min(TEXT_SCALE, (maxWidth - 2 * PAD_X) / textWidth);
        if (scale < MIN_TEXT_SCALE) {
            // very long name: cut it to what fits at the smallest size
            scale = MIN_TEXT_SCALE;
            String cut = font.plainSubstrByWidth(name.getString(), (int) ((maxWidth - 2 * PAD_X) / scale) - font.width("...")) + "...";
            text = Component.literal(cut).withStyle(name.getStyle()).getVisualOrderText();
            textWidth = font.width(text);
        }
        float halfW = textWidth * scale / 2 + PAD_X;
        float halfH = 8 * scale / 2 + PAD_Y;

        poseStack.pushPose();
        float yRot = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        // to the plaque's middle, just in front of the frame, turned to face the viewer (-z)
        poseStack.translate(width / 2.0F, CENTER_Y, 15.0F / 16.0F - 0.004F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        VertexConsumer plate = buffer.getBuffer(RenderType.entityCutoutNoCull(PLAQUE));
        PoseStack.Pose pose = poseStack.last();
        float edge = 0.012F;
        quad(plate, pose, -halfW - edge, -halfH - edge, halfW + edge, halfH + edge, -0.001F, 0.32F, light); // dark rim
        quad(plate, pose, -halfW, -halfH, halfW, halfH, 0.0F, 1.0F, light);

        poseStack.translate(0.0F, 0.0F, 0.002F);
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(text, -textWidth / 2.0F, -4.0F, TEXT_COLOR, false, poseStack.last().pose(), buffer,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        poseStack.popPose();
    }

    /** Above a pedestal's island, facing the camera like a name tag; {@code y} is the island's top. */
    static void renderAbovePedestal(Component name, float y, PoseStack poseStack, MultiBufferSource buffer, int light) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        poseStack.pushPose();
        poseStack.translate(0.5F, y + 0.3F, 0.5F);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        float x = -font.width(name) / 2.0F;
        int background = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(name, x, 0, 0x20FFFFFF, false, poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH,
                background, light);
        font.drawInBatch(name, x, 0, -1, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, light);
        poseStack.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
                             float shade, int light) {
        int c = (int) (shade * 255.0F);
        vertex(vc, pose, x0, y0, z, 0.0F, 1.0F, c, light);
        vertex(vc, pose, x1, y0, z, 1.0F, 1.0F, c, light);
        vertex(vc, pose, x1, y1, z, 1.0F, 0.0F, c, light);
        vertex(vc, pose, x0, y1, z, 0.0F, 0.0F, c, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int c, int light) {
        vc.addVertex(pose, x, y, z).setColor(c, c, c, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private IslandPlaque() {}
}
