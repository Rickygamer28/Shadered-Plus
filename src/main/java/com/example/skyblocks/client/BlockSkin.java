package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A box made of another block (a weather globe's base, a lava lamp pillar's foot and cap): each side with that block's
 * own texture for that side, the part of it where the box is (as a model element's would), tinted as the block would be.
 */
public final class BlockSkin {
    private BlockSkin() {}

    /** The box from (x0, y0, z0) to (x1, y1, z1), in pixels of its block. */
    public static void box(BlockState skin, ClientLevel level, BlockPos pos, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
                           float x0, float y0, float z0, float x1, float y1, float z1) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(skin);
        RandomSource random = RandomSource.create(42L);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose last = poseStack.last();
        float a = x0 / 16, b = y0 / 16, c = z0 / 16, d = x1 / 16, e = y1 / 16, f = z1 / 16;
        for (Direction dir : Direction.values()) {
            java.util.List<BakedQuad> quads;
            try {
                quads = model.getQuads(skin, dir, random, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            } catch (RuntimeException ex) {
                quads = java.util.List.of();
            }
            TextureAtlasSprite sprite = quads.isEmpty() ? model.getParticleIcon() : quads.get(0).getSprite();
            int tint = -1;
            if (!quads.isEmpty() && quads.get(0).isTinted()) {
                tint = 0xFF000000 | mc.getBlockColors().getColor(skin, level, pos, quads.get(0).getTintIndex());
            }
            // (four corners: x y z, then u v in pixels - as a model element's faces take their texture; a box reaching
            // outside its block takes the texture from the sprite's corner rather than off the edge of it)
            float[] ux = uv(x0, x1), uy = uv(y0, y1), uz = uv(z0, z1);
            float ux0 = ux[0], ux1 = ux[1], uy0 = uy[0], uy1 = uy[1], uz0 = uz[0], uz1 = uz[1];
            float[][] v = switch (dir) {
                case UP -> new float[][] {{a, e, c, ux0, uz0}, {a, e, f, ux0, uz1}, {d, e, f, ux1, uz1}, {d, e, c, ux1, uz0}};
                case DOWN -> new float[][] {{a, b, c, ux0, 16 - uz0}, {d, b, c, ux1, 16 - uz0}, {d, b, f, ux1, 16 - uz1}, {a, b, f, ux0, 16 - uz1}};
                case NORTH -> new float[][] {{a, b, c, 16 - ux0, 16 - uy0}, {a, e, c, 16 - ux0, 16 - uy1}, {d, e, c, 16 - ux1, 16 - uy1}, {d, b, c, 16 - ux1, 16 - uy0}};
                case SOUTH -> new float[][] {{a, b, f, ux0, 16 - uy0}, {d, b, f, ux1, 16 - uy0}, {d, e, f, ux1, 16 - uy1}, {a, e, f, ux0, 16 - uy1}};
                case WEST -> new float[][] {{a, b, c, uz0, 16 - uy0}, {a, b, f, uz1, 16 - uy0}, {a, e, f, uz1, 16 - uy1}, {a, e, c, uz0, 16 - uy1}};
                default -> new float[][] {{d, b, c, 16 - uz0, 16 - uy0}, {d, e, c, 16 - uz0, 16 - uy1}, {d, e, f, 16 - uz1, 16 - uy1}, {d, b, f, 16 - uz1, 16 - uy0}};
            };
            float shade = switch (dir) {
                case UP -> 1.0F;
                case DOWN -> 0.5F;
                case NORTH, SOUTH -> 0.8F;
                default -> 0.6F;
            };
            int r = (int) (((tint >> 16) & 255) * shade), g = (int) (((tint >> 8) & 255) * shade), bl = (int) ((tint & 255) * shade);
            int colour = 0xFF000000 | (r << 16) | (g << 8) | bl;
            for (float[] p : v) {
                vc.addVertex(last, p[0], p[1], p[2]).setColor(colour).setUv(sprite.getU(p[3] / 16.0F), sprite.getV(p[4] / 16.0F))
                        .setOverlay(overlay).setLight(light).setNormal(last, dir.getStepX(), dir.getStepY(), dir.getStepZ());
            }
        }
    }

    /** Where on its sprite (pixels, 0-16) a box's side from lo to hi takes its texture. */
    private static float[] uv(float lo, float hi) {
        if (lo >= 0.0F && hi <= 16.0F) {
            return new float[] {lo, hi};
        }
        float size = hi - lo;
        if (size >= 16.0F) {
            return new float[] {0.0F, 16.0F};
        }
        float start = lo - (float) Math.floor(lo / 16.0F) * 16.0F; // (the same place in the next block over)
        if (start + size > 16.0F) {
            start = 16.0F - size;
        }
        return new float[] {start, start + size};
    }
}
