package com.example.skyblocks.client;

import com.example.skyblocks.KoiPondBlockEntity;
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

/** The vanilla koi pond's model with its rim and floor made of the blocks chosen (KoiPondBlockEntity). */
public class PondModel extends BakedModelWrapper<BakedModel> {
    private static final ResourceLocation RIM = ResourceLocation.withDefaultNamespace("block/mossy_stone_bricks");
    private static final ResourceLocation FLOOR = ResourceLocation.withDefaultNamespace("block/gravel");

    public PondModel(BakedModel model) {
        super(model);
    }

    /** A block's look on one side (its own texture for that face). */
    static TextureAtlasSprite faceOf(BlockState state, @Nullable Direction face) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        Direction[] tries = face == null ? new Direction[] {Direction.NORTH, null, Direction.UP} : new Direction[] {face, Direction.NORTH, null, Direction.UP};
        for (Direction d : tries) {
            List<BakedQuad> qs = model.getQuads(state, d, RandomSource.create(42L), ModelData.EMPTY, null);
            if (!qs.isEmpty()) {
                return qs.get(0).getSprite();
            }
        }
        return model.getParticleIcon(ModelData.EMPTY);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        BlockState rim = data.get(KoiPondBlockEntity.RIM), floor = data.get(KoiPondBlockEntity.FLOOR);
        if (rim == null && floor == null) {
            return quads;
        }
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            ResourceLocation name = q.getSprite().contents().name();
            if (rim != null && name.equals(RIM)) {
                out.add(HearthModel.retexture(q, faceOf(rim, q.getDirection())));
            } else if (floor != null && name.equals(FLOOR)) {
                out.add(HearthModel.retexture(q, faceOf(floor, q.getDirection())));
            } else {
                out.add(q);
            }
        }
        return out;
    }
}
