package com.example.skyblocks.client;

import com.example.skyblocks.BrickHearthBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/** The brick hearth's model with its stone and wood swapped for the blocks chosen (BrickHearthBlockEntity). */
public class HearthModel extends BakedModelWrapper<BakedModel> {
    private static final ResourceLocation BRICKS = ResourceLocation.fromNamespaceAndPath("skyblocks", "block/hearth_bricks");
    private static final ResourceLocation MANTEL = ResourceLocation.withDefaultNamespace("block/dark_oak_planks");

    public HearthModel(BakedModel model) {
        super(model);
    }

    /** A block's look: the texture on its side. */
    static TextureAtlasSprite sideOf(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        for (Direction d : new Direction[] {Direction.NORTH, null, Direction.UP}) {
            List<BakedQuad> qs = model.getQuads(state, d, RandomSource.create(42L), ModelData.EMPTY, null);
            if (!qs.isEmpty()) {
                return qs.get(0).getSprite();
            }
        }
        return model.getParticleIcon(ModelData.EMPTY);
    }

    /** The same quad on another texture (the same place in it). */
    static BakedQuad retexture(BakedQuad q, TextureAtlasSprite to) {
        TextureAtlasSprite from = q.getSprite();
        int[] v = q.getVertices().clone();
        int stride = v.length / 4;
        for (int i = 0; i < 4; i++) {
            int o = i * stride + 4; // (position 3, colour 1, then the texture coordinates)
            float u = (Float.intBitsToFloat(v[o]) - from.getU0()) / (from.getU1() - from.getU0());
            float w = (Float.intBitsToFloat(v[o + 1]) - from.getV0()) / (from.getV1() - from.getV0());
            v[o] = Float.floatToRawIntBits(to.getU0() + u * (to.getU1() - to.getU0()));
            v[o + 1] = Float.floatToRawIntBits(to.getV0() + w * (to.getV1() - to.getV0()));
        }
        return new BakedQuad(v, q.getTintIndex(), q.getDirection(), to, q.isShade());
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        BlockState stone = data.get(BrickHearthBlockEntity.STONE), wood = data.get(BrickHearthBlockEntity.WOOD);
        if (stone == null && wood == null) {
            return quads;
        }
        TextureAtlasSprite s = stone == null ? null : sideOf(stone), w = wood == null ? null : sideOf(wood);
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            ResourceLocation name = q.getSprite().contents().name();
            if (s != null && name.equals(BRICKS)) {
                out.add(retexture(q, s));
            } else if (w != null && name.equals(MANTEL)) {
                out.add(retexture(q, w));
            } else {
                out.add(q);
            }
        }
        return out;
    }
}
