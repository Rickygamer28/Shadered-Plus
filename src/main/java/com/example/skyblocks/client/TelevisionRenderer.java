package com.example.skyblocks.client;

import com.example.skyblocks.TelevisionBlock;
import com.example.skyblocks.TelevisionBlockEntity;
import com.example.skyblocks.client.tv.TvPlayers;
import com.example.skyblocks.client.tv.VideoPlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * A television wall's screen (drawn once, by its bottom left block): one dark glass across the whole wall, and on it
 * the video, as big as fits and in its own shape - or, while there's none, what it's doing (loading, why it can't play).
 */
public class TelevisionRenderer implements BlockEntityRenderer<TelevisionBlockEntity> {
    private static final float FRONT = 14.0F / 16, EDGE = 1.0F / 16;
    private final Font font;

    public TelevisionRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(TelevisionBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState st = be.getBlockState();
        if (level == null || !(st.getBlock() instanceof TelevisionBlock)) return;
        Direction facing = st.getValue(TelevisionBlock.FACING);
        BlockPos pos = be.getBlockPos();
        if (!TelevisionBlock.isAnchor(level, pos, facing)) return; // (drawn by the wall's bottom left one)
        int[] size = TelevisionBlock.size(level, pos, facing);
        int w = size[0], h = size[1];

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F))); // (as the model's turned: its front to the north)
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        // (the wall in this frame: x from 1 - w to 1 - its right towards -x - and y from 0 to h)
        Matrix4f m = poseStack.last().pose();
        float left = 1 - EDGE, right = 1 - w + EDGE, bottom = EDGE, top = h - EDGE;
        boolean on = !be.source().isEmpty();
        VertexConsumer glass = buffers.getBuffer(RenderType.textBackground());
        int g = on ? 0xFF050608 : 0xFF0C0D11;
        rect(glass, m, left, bottom, right, top, FRONT - 0.002F, g, on ? LightTexture.FULL_BRIGHT : light);
        if (!on) {
            // (off: a faint sheen across the glass)
            float sx = left - (left - right) * 0.18F, ex = left - (left - right) * 0.42F;
            sheen(glass, m, sx, ex, bottom, top, FRONT - 0.003F, light);
            poseStack.popPose();
            return;
        }
        VideoPlayer player = TvPlayers.get(be);
        float cx = 1 - w / 2.0F, cy = h / 2.0F, aw = w - 2 * EDGE, ah = h - 2 * EDGE;
        if (player != null && player.hasPicture()) {
            float ar = player.pictureWidth() / (float) Math.max(1, player.pictureHeight());
            float pw = Math.min(aw, ah * ar), ph = pw / ar;
            VertexConsumer v = buffers.getBuffer(RenderType.text(player.textureId()));
            float x0 = cx + pw / 2, x1 = cx - pw / 2, y0 = cy - ph / 2, y1 = cy + ph / 2, z = FRONT - 0.004F;
            int c = player.state() == VideoPlayer.State.PAUSED || player.state() == VideoPlayer.State.ENDED ? 0xFFB8B8B8 : 0xFFFFFFFF;
            v.addVertex(m, x0, y0, z).setColor(c).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, x1, y0, z).setColor(c).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, x1, y1, z).setColor(c).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
            v.addVertex(m, x0, y1, z).setColor(c).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
            if (player.state() == VideoPlayer.State.PAUSED) {
                pauseMark(buffers.getBuffer(RenderType.textBackground()), m, cx, cy, Math.min(pw, ph) * 0.12F, FRONT - 0.006F);
            }
        } else {
            String line, sub = "";
            int colour = 0xFFB8C2D2;
            if (player == null) {
                line = "Can't play videos on this computer";
                colour = 0xFFFF8A7A;
            } else if (player.state() == VideoPlayer.State.ERROR) {
                line = "Can't play this";
                sub = player.error();
                colour = 0xFFFF8A7A;
            } else {
                int dots = (int) (System.currentTimeMillis() / 400 % 4);
                line = "Loading" + ".".repeat(dots);
                sub = player.title();
            }
            float hgt = Math.min(ah * 0.09F, aw * 0.06F);
            text(poseStack, buffers, line, cx, cy + hgt * 0.2F, aw * 0.9F, hgt, colour);
            if (!sub.isEmpty()) {
                text(poseStack, buffers, sub, cx, cy - hgt * 1.3F, aw * 0.9F, hgt * 0.6F, 0xFF8A96AA);
            }
        }
        poseStack.popPose();
    }

    private static void rect(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float z, int c, int light) {
        v.addVertex(m, x0, y0, z).setColor(c).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(c).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(c).setLight(light);
        v.addVertex(m, x0, y1, z).setColor(c).setLight(light);
    }

    private static void sheen(VertexConsumer v, Matrix4f m, float xa, float xb, float y0, float y1, float z, int light) {
        float slant = (y1 - y0) * 0.35F, wdt = (xa - xb);
        int c = 0x10FFFFFF;
        v.addVertex(m, xa, y0, z).setColor(c).setLight(light);
        v.addVertex(m, xa - wdt * 0.35F, y0, z).setColor(c).setLight(light);
        v.addVertex(m, xb - slant * 0.2F, y1, z).setColor(c).setLight(light);
        v.addVertex(m, xb + wdt * 0.25F, y1, z).setColor(c).setLight(light);
    }

    /** Two bars in the middle: paused. */
    private static void pauseMark(VertexConsumer v, Matrix4f m, float cx, float cy, float s, float z) {
        int c = 0xC0FFFFFF;
        rect(v, m, cx + s * 0.55F, cy - s * 0.6F, cx + s * 0.2F, cy + s * 0.6F, z, c, LightTexture.FULL_BRIGHT);
        rect(v, m, cx - s * 0.2F, cy - s * 0.6F, cx - s * 0.55F, cy + s * 0.6F, z, c, LightTexture.FULL_BRIGHT);
    }

    private void text(PoseStack poseStack, MultiBufferSource buffers, String line, float x, float y, float room, float height, int colour) {
        float scale = height / 9.0F;
        if (font.width(line) * scale > room) scale = room / Math.max(1, font.width(line));
        poseStack.pushPose();
        poseStack.translate(x, y + 9 * scale, FRONT - 0.006F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(line, -font.width(line) / 2.0F, 0.0F, colour, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(TelevisionBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(TelevisionBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(TelevisionBlock.MAX_W, 0, TelevisionBlock.MAX_W).expandTowards(0, TelevisionBlock.MAX_H, 0);
    }
}
