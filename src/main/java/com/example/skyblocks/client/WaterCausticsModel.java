package com.example.skyblocks.client;

import com.example.skyblocks.WaterCausticsBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * The water caustics block: on its own, its animated water texture; with a block put under the water, that block's model
 * with the animated light (see-through, translucent) a hair outside it. Baked into the chunk like any block model, so it
 * works with shader packs, mirrors and islands.
 */
public class WaterCausticsModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel overlay;

    public WaterCausticsModel(BakedModel water, BakedModel overlay) {
        super(water);
        this.overlay = overlay;
    }

    private static BakedModel modelOf(BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        if (Boolean.TRUE.equals(data.get(WaterCausticsBlockEntity.SKY))) {
            // a sky under the water: Shadered draws the sky (client/WaterCausticsRenderer), we add the light over it
            return renderType == null || renderType == RenderType.translucent()
                    ? overlay.getQuads(state, side, rand, ModelData.EMPTY, renderType) : List.of();
        }
        BlockState inside = data.get(WaterCausticsBlockEntity.INSIDE);
        if (inside == null) {
            return super.getQuads(state, side, rand, data, renderType);
        }
        List<BakedQuad> quads = new ArrayList<>();
        BakedModel model = modelOf(inside);
        if (renderType == null || model.getRenderTypes(inside, rand, ModelData.EMPTY).contains(renderType)) {
            quads.addAll(model.getQuads(inside, side, rand, ModelData.EMPTY, renderType));
        }
        if (renderType == null || renderType == RenderType.translucent()) {
            quads.addAll(overlay.getQuads(state, side, rand, ModelData.EMPTY, renderType));
        }
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        if (Boolean.TRUE.equals(data.get(WaterCausticsBlockEntity.SKY))) {
            return ChunkRenderTypeSet.of(RenderType.translucent());
        }
        BlockState inside = data.get(WaterCausticsBlockEntity.INSIDE);
        if (inside == null) {
            return super.getRenderTypes(state, rand, data);
        }
        return ChunkRenderTypeSet.union(modelOf(inside).getRenderTypes(inside, rand, ModelData.EMPTY), ChunkRenderTypeSet.of(RenderType.translucent()));
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState inside = data.get(WaterCausticsBlockEntity.INSIDE);
        return inside == null ? super.getParticleIcon(data) : modelOf(inside).getParticleIcon(ModelData.EMPTY);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }
}
