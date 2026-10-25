package com.example.skyblocks.client;

import com.example.skyblocks.NeonSignBlock;
import com.example.skyblocks.NeonSignBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * A neon sign's glowing text: a soft halo of the colour round each letter, then the bright tube over it (nearly white
 * in the middle). Flickering signs stutter now and then, pulsing ones breathe, rainbow ones shift colour along the text.
 */
public class NeonSignRenderer implements BlockEntityRenderer<NeonSignBlockEntity> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private final Font font;

    public NeonSignRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    private static int mix(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(k, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    /** How brightly it glows now (0..1). */
    private static float glow(NeonSignBlockEntity sign, double t) {
        double seed = (sign.getBlockPos().hashCode() & 1023) * 0.37;
        return switch (sign.mode()) {
            case NeonSignBlockEntity.FLICKER -> {
                double w = Math.floor(t * 12.0 + seed);
                double h = Math.abs(Math.sin(w * 12.9898 + seed) * 43758.5453) % 1.0;
                double burst = Math.sin(t * 0.7 + seed) > 0.55 ? 1.0 : 0.0; // (now and then it stutters)
                yield (float) (burst > 0.5 && h < 0.45 ? 0.15 + 0.3 * h : 1.0);
            }
            case NeonSignBlockEntity.PULSE -> (float) (0.45 + 0.55 * (0.5 + 0.5 * Math.sin(t * 2.2 + seed)));
            default -> 1.0F;
        };
    }

    @Override
    public void render(NeonSignBlockEntity sign, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        String text = sign.text();
        if (text.isBlank() || !sign.getBlockState().hasProperty(NeonSignBlock.FACING)) {
            return;
        }
        double t = LavaLampRenderer.seconds();
        Direction facing = sign.getBlockState().getValue(NeonSignBlock.FACING);
        float bright = glow(sign, t);
        String[] lines = text.split("\\|", 3);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        }));
        poseStack.translate(0.0, 0.0, 0.42); // (just in front of the panel, at the back of the block)
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F)); // (the text faces out, the way the sign faces)
        float s = sign.size() / 64.0F;
        poseStack.scale(s, -s, s);
        float lineH = 10.0F;
        float top = -lines.length * lineH * 0.5F + 1.0F;
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            float x0 = -font.width(line) * 0.5F;
            float y = top + li * lineH;
            int base = sign.colour();
            if (sign.mode() == NeonSignBlockEntity.RAINBOW) {
                base = Mth.hsvToRgb((float) ((t * 0.15 + li * 0.1) % 1.0), 0.9F, 1.0F);
            }
            int dim = mix(0x202020, base, 0.35F);
            int tube = bright > 0.5F ? mix(base, 0xFFFFFF, 0.45F * bright) : mix(dim, base, bright);
            int haloA = (int) (bright * 70.0F);
            // the halo: the colour, faint, all round each letter (two rings)
            if (haloA > 4) {
                for (int ring = 0; ring < 2; ring++) {
                    float r = ring == 0 ? 0.7F : 1.4F;
                    int a = ring == 0 ? haloA : haloA / 2;
                    for (int k = 0; k < 8; k++) {
                        double ang = k * Math.PI / 4.0;
                        poseStack.pushPose();
                        poseStack.translate((float) Math.cos(ang) * r, (float) Math.sin(ang) * r, -0.05F - ring * 0.05F);
                        font.drawInBatch(line, x0, y, (a << 24) | base, false, poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
                        poseStack.popPose();
                    }
                }
            }
            // the tube
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, -0.15F);
            font.drawInBatch(line, x0, y, 0xFF000000 | tube, false, poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(NeonSignBlockEntity sign) {
        return sign.size() > 2; // (big text reaches past its block)
    }
}
