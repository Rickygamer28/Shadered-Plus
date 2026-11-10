package com.example.skyblocks.client;

import com.example.skyblocks.NeonTubeBlock;
import com.example.skyblocks.NeonTubeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Neon tubes: on each side of the space that has one, a glowing tube 1.5 pixels off the surface, from the middle of the
 * side out to each edge it joins at - straight on to the next tube on the same surface, into the corner where it meets
 * another side's tube in the same space, or over the edge to a tube on the next surface round. On its own: straight
 * across the way it was laid.
 */
public class NeonTubeRenderer implements BlockEntityRenderer<NeonTubeBlockEntity> {
    private static final float OFF = 1.5F / 16, CORE = 0.9F / 16, GLOW = 1.6F / 16;

    public NeonTubeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(NeonTubeBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !(state.getBlock() instanceof NeonTubeBlock)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        int rgb = neon(state.getValue(NeonTubeBlock.COLOR).getTextureDiffuseColor());
        Matrix4f m = poseStack.last().pose();
        VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
        for (Direction face : Direction.values()) {
            if (!NeonTubeBlock.has(state, face)) continue;
            // the tube's line on this side: the middle of the side, OFF out from the surface
            Vector3f c = new Vector3f(0.5F, 0.5F, 0.5F).add(face.getStepX() * (0.5F - OFF), face.getStepY() * (0.5F - OFF), face.getStepZ() * (0.5F - OFF));
            int joined = 0;
            for (Direction d : Direction.values()) {
                if (d.getAxis() == face.getAxis()) continue;
                float reach;
                if (NeonTubeBlock.has(level.getBlockState(pos.relative(d)), face)) {
                    reach = 0.5F; // (on to the next tube on the same surface)
                } else if (NeonTubeBlock.has(state, d)) {
                    reach = 0.5F - OFF; // (into the corner, to the tube on that side)
                } else if (NeonTubeBlock.has(level.getBlockState(pos.relative(d).relative(face)), d.getOpposite())) {
                    reach = 0.5F + OFF; // (over the edge, round to the next surface)
                } else {
                    continue;
                }
                arm(glow, m, c, d, reach, rgb);
                joined++;
            }
            if (joined == 0) { // (on its own: straight across, the way it was laid)
                Direction.Axis a = be.axis(face);
                if (a == null || a == face.getAxis()) {
                    a = face.getAxis() == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y;
                }
                arm(glow, m, c, Direction.fromAxisAndDirection(a, Direction.AxisDirection.POSITIVE), 0.42F, rgb);
                arm(glow, m, c, Direction.fromAxisAndDirection(a, Direction.AxisDirection.NEGATIVE), 0.42F, rgb);
            }
            box(glow, m, c, c, GLOW * 0.6F, rgb, 255); // (the joint)
        }
    }

    /** A tube from the middle {@code c} out along {@code d} for {@code reach}: its bright core and its glow. */
    private static void arm(VertexConsumer vc, Matrix4f m, Vector3f c, Direction d, float reach, int rgb) {
        Vector3f end = new Vector3f(c).add(d.getStepX() * reach, d.getStepY() * reach, d.getStepZ() * reach);
        box(vc, m, c, end, CORE / 2, 0xFFFFFF & mix(rgb, 0xFFFFFF, 0.55F), 255);
        box(vc, m, c, end, GLOW / 2, rgb, 90);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** A box round the line from a to b, {@code r} out from it each way. */
    private static void box(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, float r, int rgb, int alpha) {
        float x0 = Math.min(a.x, b.x) - r, y0 = Math.min(a.y, b.y) - r, z0 = Math.min(a.z, b.z) - r;
        float x1 = Math.max(a.x, b.x) + r, y1 = Math.max(a.y, b.y) + r, z1 = Math.max(a.z, b.z) + r;
        int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
        float[][] f = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] q : f) {
            for (int i = 0; i < 12; i += 3) {
                vc.addVertex(m, q[i], q[i + 1], q[i + 2]).setColor(cr, cg, cb, alpha);
            }
        }
    }

    /** A dye's colour as neon: as bright as it goes, its hue kept, a little white in it. */
    static int neon(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int max = Math.max(1, Math.max(r, Math.max(g, b)));
        r = Math.min(255, r * 255 / max + 30);
        g = Math.min(255, g * 255 / max + 30);
        b = Math.min(255, b * 255 / max + 30);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
