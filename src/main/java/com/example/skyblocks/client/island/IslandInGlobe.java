package com.example.skyblocks.client.island;

import com.example.skyblocks.island.IslandData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A Sky Island shown as a miniature inside something else (the weather globe): fitted into a box, its mobs walking about.
 * The decoded island is kept by the caller between frames (the returned object, handed back next time).
 */
public final class IslandInGlobe {
    /** Marks an island that couldn't be decoded or baked. */
    private record Broken(IslandData source) {}

    private IslandInGlobe() {}

    /**
     * Draws {@code data} with its ground's middle at the pose's origin, fitted into {@code width} across and
     * {@code height} up, and only what's inside a round column {@code clipR} out from its middle, up to {@code clipTop}
     * (cut round, like the globe's base); returns what to keep for next time.
     */
    @Nullable
    public static Object draw(@Nullable Object kept, IslandData data, Level level, BlockPos at, PoseStack poseStack, MultiBufferSource buffer,
                              float partialTick, float width, float height, float clipTop, float clipR) {
        if (kept instanceof Broken b && b.source.equals(data)) {
            return kept;
        }
        IslandView view = kept instanceof IslandView v && data.equals(v.source) ? v : null;
        if (view == null) {
            if (kept instanceof IslandView old) {
                old.close();
            }
            try {
                view = IslandView.decode(data, level, at);
            } catch (RuntimeException e) {
                view = null;
            }
            if (view == null) {
                return new Broken(data);
            }
        }
        int across = Math.max(1, view.contentWidth());
        int up = Math.max(1, view.highestLayer() - view.lowestLayer() + 1);
        float scale = Math.min(width / across, height / up);
        if (view.clip == null) { // (before its mesh is made: just the blocks and mobs inside the round column)
            final IslandView v = view;
            final float cx = v.contentCenterX(), cz = v.contentCenterZ(), low = v.lowestLayer();
            v.clip = p -> {
                double dx = (p.x - cx) * scale, y = (p.y - low) * scale, dz = (p.z - cz) * scale;
                return dx * dx + dz * dz < clipR * clipR && y < clipTop;
            };
        }
        IslandMesh mesh;
        try {
            mesh = view.mesh();
        } catch (RuntimeException e) {
            view.close();
            return new Broken(data);
        }
        double distance = Math.sqrt(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(at.getCenter()));
        boolean near = distance < 16.0;
        if (near) {
            view.tickTo(level.getGameTime());
        }
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-view.contentCenterX(), -view.lowestLayer(), -view.contentCenterZ());
        mesh.render(poseStack.last(), buffer);
        if (near) {
            view.renderBlockEntities(poseStack, buffer, partialTick);
            view.renderFigures(poseStack, buffer, partialTick);
        }
        poseStack.popPose();
        return view;
    }
}
