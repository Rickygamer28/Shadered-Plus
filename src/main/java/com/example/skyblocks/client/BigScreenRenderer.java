package com.example.skyblocks.client;

import com.example.skyblocks.ArcadeBlockEntity;
import com.example.skyblocks.BigScreenBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A wall of big screens: one picture across all of them (drawn once, by the screen at its bottom left), showing the
 * nearest arcade cabinet that's being played - on the same old tube as the cabinets', only big. Nobody playing near:
 * the cabinets' demo.
 */
public class BigScreenRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    /** The biggest wall that's one screen (blocks); how near a cabinet must be to be shown (blocks). */
    static final int MAX_W = 16, MAX_H = 12, REACH = 32;
    private static final float FRONT = 13.0F / 16, EDGE = 0.1F;
    private final Font font;

    public BigScreenRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    private static boolean same(Level level, BlockPos pos, Direction facing) {
        BlockState st = level.getBlockState(pos);
        return st.getBlock() instanceof BigScreenBlock && st.getValue(BigScreenBlock.FACING) == facing;
    }

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState st = be.getBlockState();
        if (ArcadeRenderer.shader == null || level == null || !(st.getBlock() instanceof BigScreenBlock)) return;
        Direction facing = st.getValue(BigScreenBlock.FACING);
        Direction right = facing.getCounterClockWise(); // (as you look at it)
        BlockPos pos = be.getBlockPos();
        if (same(level, pos.relative(right.getOpposite()), facing) || same(level, pos.below(), facing)) return; // (not the bottom left: drawn by that one)
        int w = 1, h = 1;
        while (w < MAX_W && same(level, pos.relative(right, w), facing)) w++;
        while (h < MAX_H && same(level, pos.above(h), facing)) h++;
        Vec3 middle = Vec3.atLowerCornerOf(pos).add(0.5, h / 2.0, 0.5).add(Vec3.atLowerCornerOf(right.getNormal()).scale((w - 1) / 2.0));
        ArcadeBlockEntity source = nearest(level, middle);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F))); // (as the model's turned: its front to the north)
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        // (the wall, in this frame: from x = 1 - w to 1 - its right towards -x - and y 0 to h; the picture 4:3 in it)
        float aw = w - 2 * EDGE, ah = h - 2 * EDGE;
        float pw = Math.min(aw, ah * 4 / 3.0F), ph = pw * 3 / 4.0F;
        float cx = 1 - w / 2.0F, cy = h / 2.0F;
        ArcadeRenderer.drawTube(poseStack, buffers, source, pos, level, partialTick, cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2, FRONT - 0.004F);
        if (source != null && ArcadeRenderer.playing(source, level)) { // (whose game, under it)
            int game = source.liveGame;
            String title = game >= 0 && game < ArcadeBlockEntity.TITLES.length ? ArcadeBlockEntity.TITLES[game].toUpperCase(java.util.Locale.ROOT) : "";
            String name = source.liveName.length() > 16 ? source.liveName.substring(0, 16) : source.liveName;
            text(poseStack, buffers, title + "  -  " + name, cx, cy - ph / 2 + ph * 0.035F, pw * 0.85F, ph * 0.045F, 0xFFE9F27A);
        }
        poseStack.popPose();
    }

    /** The nearest cabinet someone's playing, near enough (and the ones that have stopped: forgotten). */
    private static ArcadeBlockEntity nearest(Level level, Vec3 at) {
        ArcadeBlockEntity best = null;
        double bestD = REACH * REACH;
        for (var it = ArcadeRenderer.LIVE.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            if (!(level.getBlockEntity(p) instanceof ArcadeBlockEntity be) || level.getGameTime() - be.liveAt > 200L) {
                it.remove();
                continue;
            }
            double d = p.getCenter().distanceToSqr(at);
            if (d < bestD && ArcadeRenderer.playing(be, level)) {
                bestD = d;
                best = be;
            }
        }
        return best;
    }

    private void text(PoseStack poseStack, MultiBufferSource buffers, String line, float x, float y, float room, float height, int colour) {
        float scale = height / 9.0F;
        if (font.width(line) * scale > room) scale = room / font.width(line);
        poseStack.pushPose();
        poseStack.translate(x, y + 9 * scale, FRONT - 0.008F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(line, -font.width(line) / 2.0F, 0.0F, colour, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SimpleRenderedBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(SimpleRenderedBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(MAX_W, 0, MAX_W).expandTowards(0, MAX_H, 0);
    }
}
