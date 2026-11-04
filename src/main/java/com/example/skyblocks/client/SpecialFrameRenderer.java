package com.example.skyblocks.client;

import com.example.skyblocks.ContainmentFrameBlock;
import com.example.skyblocks.SingularityBlockEntity;
import com.example.skyblocks.SpecialFrameBlock;
import com.example.skyblocks.SpecialFrameBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * The special frames' faces: a display frame shows what its singularity holds (how many items, how many kinds of how
 * many it can, how full) on its screen; a port shows the item it's set to; a monitor its item and how many of it.
 */
public class SpecialFrameRenderer implements BlockEntityRenderer<SpecialFrameBlockEntity> {
    /** The face's screen sits on the frame's plate, this far out from the block's middle (just past its glow). */
    private static final float FACE = 0.3855F;

    public SpecialFrameRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SpecialFrameBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        SpecialFrameBlock.Kind kind = be.kind();
        Level level = be.getLevel();
        if (kind == null || level == null || kind == SpecialFrameBlock.Kind.REDSTONE) {
            return;
        }
        ItemStack item = kind == SpecialFrameBlock.Kind.DISPLAY ? ItemStack.EMPTY : be.filter();
        if (kind != SpecialFrameBlock.Kind.DISPLAY && item.isEmpty()) {
            return;
        }
        SingularityBlockEntity s = kind == SpecialFrameBlock.Kind.DISPLAY ? be.singularity() : null;
        for (Direction d : Direction.values()) {
            BlockPos n = be.getBlockPos().relative(d);
            BlockState ns = level.getBlockState(n);
            if (ns.getBlock() instanceof ContainmentFrameBlock || ns.isSolidRender(level, n)) {
                continue; // (a side nobody sees)
            }
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            if (d.getAxis().isHorizontal()) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-d.toYRot()));
            } else {
                poseStack.mulPose(Axis.XP.rotationDegrees(d == Direction.UP ? -90.0F : 90.0F));
            }
            poseStack.translate(0.0, 0.0, FACE);
            if (kind == SpecialFrameBlock.Kind.DISPLAY) {
                screen(poseStack, buffers, s);
            } else {
                int faceLight = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, n);
                icon(poseStack, buffers, item, faceLight, overlay, kind == SpecialFrameBlock.Kind.MONITOR ? be.count() : -2);
            }
            poseStack.popPose();
        }
    }

    /** A display's screen: the total, kinds out of the most it can, and a bar of how full it is. */
    private static void screen(PoseStack poseStack, MultiBufferSource buffers, SingularityBlockEntity s) {
        Font font = Minecraft.getInstance().font;
        float px = 0.5F / 56.0F; // (the screen is half a block across: 56 of the font's pixels)
        poseStack.scale(px, -px, px);
        Matrix4f m = poseStack.last().pose();
        int bright = LightTexture.FULL_BRIGHT;
        if (s == null) {
            draw(font, m, buffers, "- - -", 0, -4, 0xFF2E7A55, bright);
            return;
        }
        String total = shortNumber(s.total());
        String kinds = s.kinds() + "/" + shortNumber(s.maxKinds());
        draw(font, m, buffers, total, 0, -19, 0xFF7CFFB8, bright);
        draw(font, m, buffers, kinds, 0, -6, 0xFF49C98A, bright);
        // how full: a bar (green, going red near the top)
        float full = s.fullness();
        int colour = full > 0.85F ? 0xFFFF6A4A : full > 0.6F ? 0xFFFFC83D : 0xFF3CCF7A;
        rect(m, buffers, -22, 8, 22, 14, 0xFF10251A, bright, 0.0F);
        rect(m, buffers, -21, 9, -21 + 42 * full, 13, colour, bright, 0.3F);
    }

    private static void draw(Font font, Matrix4f m, MultiBufferSource buffers, String text, float cx, float y, int colour, int light) {
        // (shrunk to fit the screen if it's long)
        int w = font.width(text);
        float k = w > 50 ? 50.0F / w : 1.0F;
        Matrix4f mm = new Matrix4f(m).translate(cx, y + 4.0F, 0.5F).scale(k, k, 1.0F);
        font.drawInBatch(text, -w / 2.0F, -4.0F, colour, false, mm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
    }

    private static void rect(Matrix4f m, MultiBufferSource buffers, float x0, float y0, float x1, float y1, int argb, int light, float z) {
        VertexConsumer v = buffers.getBuffer(RenderType.textBackground());
        v.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
        v.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
    }

    /** A port's item, a monitor's item and how many of it there are ({@code count} -2: none shown; -1: not formed). */
    private static void icon(PoseStack poseStack, MultiBufferSource buffers, ItemStack item, int light, int overlay, long count) {
        Minecraft mc = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(0.0, count > -2 ? 0.07 : 0.0, 0.01);
        poseStack.scale(0.3F, 0.3F, 0.02F);
        mc.getItemRenderer().renderStatic(item, ItemDisplayContext.GUI, light, overlay, poseStack, buffers, mc.level, 0);
        poseStack.popPose();
        if (count > -2) {
            float px = 0.5F / 56.0F;
            poseStack.pushPose();
            poseStack.translate(0.0, 0.0, 0.03);
            poseStack.scale(px, -px, px);
            draw(mc.font, poseStack.last().pose(), buffers, count < 0 ? "-" : shortNumber(count), 0, 12, 0xFFFFD36B, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
    }

    static String shortNumber(long n) {
        if (n < 10_000) {
            return Long.toString(n);
        }
        String[] units = {"K", "M", "B", "T"};
        double v = n;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return (v < 10 ? String.format(java.util.Locale.ROOT, "%.1f", v) : String.format(java.util.Locale.ROOT, "%.0f", v)) + units[u];
    }

    @Override
    public boolean shouldRenderOffScreen(SpecialFrameBlockEntity be) {
        return false;
    }
}
