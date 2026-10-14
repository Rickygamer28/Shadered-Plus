package com.example.skyblocks.client.island;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.island.FrameStyle;
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
 * The name of a renamed Sky Island: a small plaque on the bottom edge of a painting's frame (like a museum label),
 * styled to match the frame (brass, gold, dark wood, prismarine, marble, blackstone), or a floating name above a
 * pedestal while you look at it (like a name tag).
 */
final class IslandPlaque {
    private static final ResourceLocation RIM =
            new ResourceLocation(SkyBlocksMod.MOD_ID, "textures/block/island_painting_plaque_rim.png");

    /** Each frame style's plaque: its plate texture, letter colour, rim colour and the two little rivets' colour. */
    private record Look(ResourceLocation plate, int text, int rim, int rivet) {
        static Look of(String style, int text, int rim, int rivet) {
            return new Look(new ResourceLocation(SkyBlocksMod.MOD_ID,
                    "textures/block/island_painting_plaque_" + style + ".png"), text, rim, rivet);
        }
    }

    private static final java.util.Map<FrameStyle, Look> LOOKS = new java.util.EnumMap<>(java.util.Map.of(
            FrameStyle.OAK, Look.of("oak", 0x3A2618, 0x4A301A, 0x7A5A2E),            // brass, brown letters
            FrameStyle.GOLD, Look.of("gold", 0x5A3A00, 0x96640F, 0xFFF0AA),          // polished gold
            FrameStyle.DARK_OAK, Look.of("dark_oak", 0xE8C060, 0x1A1008, 0xD6AA40),  // dark wood, gilded letters
            FrameStyle.PRISMARINE, Look.of("prismarine", 0x0E3A32, 0x245248, 0xBEF0E4),
            FrameStyle.QUARTZ, Look.of("quartz", 0x3A3632, 0xA09890, 0xD6AA40),      // marble, gold rivets
            FrameStyle.BLACKSTONE, Look.of("blackstone", 0xE6B84A, 0xD6AA40, 0x5A505E)));
    /** Text size: a sign's (9 pixel high letters = 0.094 blocks), smaller for long names. */
    private static final float TEXT_SCALE = 1.0F / 96.0F;
    private static final float MIN_TEXT_SCALE = 1.0F / 200.0F;
    private static final float PAD_X = 0.04F;
    private static final float PAD_Y = 0.025F;
    /** Where the plaque's middle is: on the frame's 1 pixel bottom bar. */
    private static final float CENTER_Y = 0.5F / 16.0F;

    /**
     * On a painting {@code width} blocks wide, from its bottom corner block (pose at that block's corner). Works in a
     * north-facing painting's space like IslandWindow: the painting spans x = 0..width, its frame's front is at
     * z = 15/16 and the viewer is on the -z side.
     */
    static void renderOnPainting(Component name, FrameStyle style, Direction facing, int width, PoseStack poseStack,
                                 MultiBufferSource buffer, int light) {
        Look look = LOOKS.getOrDefault(style, LOOKS.get(FrameStyle.OAK));
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

        PoseStack.Pose pose = poseStack.last();
        float edge = 0.012F;
        VertexConsumer rim = buffer.getBuffer(RenderType.entityCutoutNoCull(RIM));
        quad(rim, pose, -halfW - edge, -halfH - edge, halfW + edge, halfH + edge, -0.001F, look.rim, light);
        // two little rivets near the ends
        float r = 0.009F, rx = halfW - 0.02F;
        quad(rim, pose, -rx - r, -r, -rx + r, r, 0.001F, look.rivet, light);
        quad(rim, pose, rx - r, -r, rx + r, r, 0.001F, look.rivet, light);
        VertexConsumer plate = buffer.getBuffer(RenderType.entityCutoutNoCull(look.plate));
        quad(plate, pose, -halfW, -halfH, halfW, halfH, 0.0F, 0xFFFFFF, light);

        poseStack.translate(0.0F, 0.0F, 0.002F);
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(text, -textWidth / 2.0F, -4.0F, look.text, false, poseStack.last().pose(), buffer,
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

    /** A rectangle tinted with an 0xRRGGBB colour (white = the texture as it is). */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
                             int rgb, int light) {
        vertex(vc, pose, x0, y0, z, 0.0F, 1.0F, rgb, light);
        vertex(vc, pose, x1, y0, z, 1.0F, 1.0F, rgb, light);
        vertex(vc, pose, x1, y1, z, 1.0F, 0.0F, rgb, light);
        vertex(vc, pose, x0, y1, z, 0.0F, 0.0F, rgb, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int rgb, int light) {
        vc.vertex(pose.pose(), x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), 0.0F, 0.0F, 1.0F).endVertex();
    }

    private IslandPlaque() {}
}
