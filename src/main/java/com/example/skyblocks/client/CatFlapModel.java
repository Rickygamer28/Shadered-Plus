package com.example.skyblocks.client;

import com.example.skyblocks.CatFlapBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/** The cat flap built of the block it was given: its oak planks swapped for that block's faces (and tint); the flap stays. */
public class CatFlapModel extends BakedModelWrapper<BakedModel> {
    private static final ResourceLocation PLANKS = ResourceLocation.withDefaultNamespace("block/oak_planks");
    /** The tint numbers the flap's colour handler takes: the block's own tint + this. */
    public static final int TINT = 100;

    public CatFlapModel(BakedModel model) {
        super(model);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        BlockState material = data.get(CatFlapBlockEntity.MATERIAL);
        if (material == null) {
            return quads;
        }
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            if (q.getSprite().contents().name().equals(PLANKS)) {
                BakedQuad src = PondModel.quadOf(material, q.getDirection());
                BakedQuad re = HearthModel.retexture(q, src != null ? src.getSprite() : PondModel.faceOf(material, q.getDirection()));
                int tint = src != null && src.getTintIndex() >= 0 ? TINT + src.getTintIndex() : -1;
                out.add(new BakedQuad(re.getVertices(), tint, re.getDirection(), re.getSprite(), re.isShade()));
            } else {
                out.add(q);
            }
        }
        return out;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        BlockState material = data.get(CatFlapBlockEntity.MATERIAL);
        if (material == null) {
            return super.getRenderTypes(state, rand, data);
        }
        // (a glass or leaves flap: drawn as that block is, and the flap with it)
        return ChunkRenderTypeSet.union(super.getRenderTypes(state, rand, data),
                net.minecraft.client.Minecraft.getInstance().getBlockRenderer().getBlockModel(material).getRenderTypes(material, rand, ModelData.EMPTY));
    }
}
