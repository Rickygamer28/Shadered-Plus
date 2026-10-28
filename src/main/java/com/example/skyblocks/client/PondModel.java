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
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/** The vanilla koi pond's model with its rim and floor made of the blocks chosen (KoiPondBlockEntity). */
public class PondModel extends BakedModelWrapper<BakedModel> {
    private static final ResourceLocation RIM = new ResourceLocation("block/mossy_stone_bricks");
    private static final ResourceLocation FLOOR = new ResourceLocation("block/gravel");

    public PondModel(BakedModel model) {
        super(model);
    }

    /** A block's look on one side (its own texture for that face). */
    static TextureAtlasSprite faceOf(BlockState state, @Nullable Direction face) {
        BakedQuad q = quadOf(state, face);
        return q != null ? q.getSprite() : Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getParticleIcon(ModelData.EMPTY);
    }

    /** A block's own quad for that side (its texture, and whether it's tinted: grass, leaves...). */
    @Nullable
    static BakedQuad quadOf(BlockState state, @Nullable Direction face) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        Direction[] tries = face == null ? new Direction[] {Direction.NORTH, null, Direction.UP} : new Direction[] {face, Direction.NORTH, null, Direction.UP};
        for (Direction d : tries) {
            List<BakedQuad> qs = model.getQuads(state, d, RandomSource.create(42L), ModelData.EMPTY, null);
            if (!qs.isEmpty()) {
                return qs.get(0);
            }
        }
        return null;
    }

    /** The tint numbers the pond's own colour handler takes: the rim's block's tint + RIM_TINT, the floor's + FLOOR_TINT. */
    public static final int RIM_TINT = 100, FLOOR_TINT = 200;

    /** The quad with that block's look: its texture, and (if it has one) its tint, through the pond's colour handler. */
    private static BakedQuad as(BakedQuad q, BlockState block, int tintBase) {
        BakedQuad src = quadOf(block, q.getDirection());
        BakedQuad out = HearthModel.retexture(q, src != null ? src.getSprite() : faceOf(block, q.getDirection()));
        int tint = src != null && src.getTintIndex() >= 0 ? tintBase + src.getTintIndex() : -1;
        return new BakedQuad(out.getVertices(), tint, out.getDirection(), out.getSprite(), out.isShade());
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
                out.add(as(q, rim, RIM_TINT));
            } else if (floor != null && name.equals(FLOOR)) {
                out.add(as(q, floor, FLOOR_TINT));
            } else {
                out.add(q);
            }
        }
        return out;
    }
}
