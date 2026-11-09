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
            // (four corners: x y z, then u v in pixels - as a model element's faces take their texture)
            float[][] v = switch (dir) {
                case UP -> new float[][] {{a, e, c, x0, z0}, {a, e, f, x0, z1}, {d, e, f, x1, z1}, {d, e, c, x1, z0}};
                case DOWN -> new float[][] {{a, b, c, x0, 16 - z0}, {d, b, c, x1, 16 - z0}, {d, b, f, x1, 16 - z1}, {a, b, f, x0, 16 - z1}};
                case NORTH -> new float[][] {{a, b, c, 16 - x0, 16 - y0}, {a, e, c, 16 - x0, 16 - y1}, {d, e, c, 16 - x1, 16 - y1}, {d, b, c, 16 - x1, 16 - y0}};
                case SOUTH -> new float[][] {{a, b, f, x0, 16 - y0}, {d, b, f, x1, 16 - y0}, {d, e, f, x1, 16 - y1}, {a, e, f, x0, 16 - y1}};
                case WEST -> new float[][] {{a, b, c, z0, 16 - y0}, {a, b, f, z1, 16 - y0}, {a, e, f, z1, 16 - y1}, {a, e, c, z0, 16 - y1}};
                default -> new float[][] {{d, b, c, 16 - z0, 16 - y0}, {d, e, c, 16 - z0, 16 - y1}, {d, e, f, 16 - z1, 16 - y1}, {d, b, f, 16 - z1, 16 - y0}};
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
}
