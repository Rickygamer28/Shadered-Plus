package com.example.skyblocks.client;

import com.example.skyblocks.ProjectorBlock;
import com.example.skyblocks.TelevisionBlockEntity;
import com.example.skyblocks.client.tv.Music;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A projector's picture: on the wall in front of it, as big as its distance makes it (about a block wide every two
 * blocks away), and its beam of light from the lens to the picture's corners - faint, brighter with a brighter beat.
 */
public class ProjectorRenderer implements BlockEntityRenderer<TelevisionBlockEntity> {
    private final Font font;

    public ProjectorRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(TelevisionBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState st = be.getBlockState();
        if (be.getLevel() == null || !(st.getBlock() instanceof ProjectorBlock) || be.source().isEmpty()) return;
        Direction f = st.getValue(ProjectorBlock.FACING);
        BlockPos pos = be.getBlockPos();
        BlockHitResult hit = ProjectorBlock.screen(be.getLevel(), pos, f);
        VideoPlayer player = TvPlayers.get(be);
        Vec3 lens = new Vec3(0.5 + f.getStepX() * 0.45, 3.5 / 16, 0.5 + f.getStepZ() * 0.45);
        if (hit == null || player == null) return;
        Vec3 c = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)).subtract(f.getStepX() * 0.012, 0, f.getStepZ() * 0.012);
        double dist = Math.max(1.0, hit.getLocation().distanceTo(Vec3.atLowerCornerOf(pos).add(lens)));
        float w = be.projection() > 0 ? be.projection() : (float) Math.min(24.0, dist * 0.55); // (as chosen, or by its distance)
        float ar = player.hasPicture() ? player.pictureWidth() / (float) Math.max(1, player.pictureHeight()) : 16.0F / 9;
        float h = w / ar;
        // (on the wall: right as you face it is the projector's right, up is up)
        Direction right = f.getClockWise();
        float rx = right.getStepX() * w / 2, rz = right.getStepZ() * w / 2;
        // (thrown a little upwards, as projectors are: its bottom edge where the beam's middle lands)
        float cx = (float) c.x, cy = (float) c.y + h * 0.5F, cz = (float) c.z;
        float[][] q = {{cx - rx, cy - h / 2, cz - rz}, {cx + rx, cy - h / 2, cz + rz}, {cx + rx, cy + h / 2, cz + rz}, {cx - rx, cy + h / 2, cz - rz}};
        Matrix4f m = pose.last().pose();
        Music music = player.music();
        if (music != null && player.state() != VideoPlayer.State.ERROR) {
            // (a song: its cover thrown up, the line being sung under it)
            ResourceLocation cover = player.cover();
            float s = h * 0.7F;
            float[][] cq = {{cx - right.getStepX() * s / 2, cy - s / 2 + h * 0.1F, cz - right.getStepZ() * s / 2},
                    {cx + right.getStepX() * s / 2, cy - s / 2 + h * 0.1F, cz + right.getStepZ() * s / 2},
                    {cx + right.getStepX() * s / 2, cy + s / 2 + h * 0.1F, cz + right.getStepZ() * s / 2},
                    {cx - right.getStepX() * s / 2, cy + s / 2 + h * 0.1F, cz - right.getStepZ() * s / 2}};
            if (cover != null) {
                quad(buffers.getBuffer(RenderType.text(cover)), m, cq, 0xFFFFFFFF);
            }
            String line = music.lineAt(player.position());
            text(pose, c, f, line.isEmpty() ? music.title + " - " + music.artist : line, cy - h * 0.38F, w * 0.9F, h * 0.07F, buffers);
            beam(buffers, m, lens, cq, 0.05F + player.beat() * 0.08F, music.colour);
            return;
        }
        if (player.hasPicture()) {
            int col = player.state() == VideoPlayer.State.PAUSED || player.state() == VideoPlayer.State.ENDED ? 0xFFB8B8B8 : 0xFFFFFFFF;
            quad(buffers.getBuffer(RenderType.text(player.textureId())), m, q, col);
            beam(buffers, m, lens, q, 0.045F + player.beat() * 0.06F, 0xFFDDE8FF);
        } else {
            String line = player.state() == VideoPlayer.State.ERROR ? "Can't play this: " + player.error() : "Loading...";
            text(pose, c, f, line, cy, w * 0.9F, h * 0.08F, buffers);
            beam(buffers, m, lens, q, 0.02F, 0xFFDDE8FF);
        }
    }

    /** A picture on the wall (corners: its bottom left, bottom right, top right, top left as you face it), both sides. */
    private static void quad(VertexConsumer v, Matrix4f m, float[][] q, int col) {
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int i = 0; i < 4; i++) v.addVertex(m, q[i][0], q[i][1], q[i][2]).setColor(col).setUv(uv[i][0], uv[i][1]).setLight(LightTexture.FULL_BRIGHT);
        for (int i = 3; i >= 0; i--) v.addVertex(m, q[i][0], q[i][1], q[i][2]).setColor(col).setUv(uv[i][0], uv[i][1]).setLight(LightTexture.FULL_BRIGHT);
    }

    /** The beam: four faint sides from the lens to the picture's edges, fading towards it (light added: never darkens). */
    private static void beam(MultiBufferSource buffers, Matrix4f m, Vec3 lens, float[][] q, float a, int colour) {
        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        float r = (colour >> 16 & 255) / 255.0F, g = (colour >> 8 & 255) / 255.0F, b = (colour & 255) / 255.0F;
        float lx = (float) lens.x, ly = (float) lens.y, lz = (float) lens.z;
        for (int i = 0; i < 4; i++) {
            float[] p0 = q[i], p1 = q[(i + 1) % 4];
            v.addVertex(m, lx, ly, lz).setColor(r, g, b, a * 2.2F);
            v.addVertex(m, lx, ly, lz).setColor(r, g, b, a * 2.2F);
            v.addVertex(m, p1[0], p1[1], p1[2]).setColor(r, g, b, a * 0.4F);
            v.addVertex(m, p0[0], p0[1], p0[2]).setColor(r, g, b, a * 0.4F);
            v.addVertex(m, lx, ly, lz).setColor(r, g, b, a * 2.2F);
            v.addVertex(m, lx, ly, lz).setColor(r, g, b, a * 2.2F);
            v.addVertex(m, p0[0], p0[1], p0[2]).setColor(r, g, b, a * 0.4F);
            v.addVertex(m, p1[0], p1[1], p1[2]).setColor(r, g, b, a * 0.4F);
        }
    }

    /** A line of text on the wall, its middle at the picture's, this high. */
    private void text(PoseStack pose, Vec3 c, Direction f, String line, float y, float room, float height, MultiBufferSource buffers) {
        float scale = height / 9.0F;
        if (font.width(line) * scale > room) scale = room / Math.max(1, font.width(line));
        pose.pushPose();
        pose.translate(c.x - f.getStepX() * 0.004, y + 9 * scale, c.z - f.getStepZ() * 0.004);
        pose.mulPose(Axis.YP.rotationDegrees(-f.getOpposite().toYRot())); // (reading the right way, facing back at the projector)
        pose.scale(scale, -scale, scale);
        font.drawInBatch(line, -font.width(line) / 2.0F, 0.0F, 0xFFE8EEFF, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
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
        return new AABB(be.getBlockPos()).inflate(ProjectorBlock.THROW + 12);
    }
}
