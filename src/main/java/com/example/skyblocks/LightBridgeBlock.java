package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of hard light, only ever put there by a light bridge emitter and taken away when its bridge goes: a thin plate
 * to walk on (OFFSET pixels up its block, for ramps and arches), a piece of a wall of light, or the inside of a light
 * tunnel (nothing to bump into). Drawn by the emitter (client/LightBridgeRenderer), not by itself.
 */
public class LightBridgeBlock extends Block {
    public static final MapCodec<LightBridgeBlock> CODEC = simpleCodec(LightBridgeBlock::new);

    public enum Kind implements StringRepresentable {
        FLOOR, WALL_X, WALL_Z, WALL, TUNNEL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final IntegerProperty OFFSET = IntegerProperty.create("offset", 0, 14);
    private static final VoxelShape[] PLATES = new VoxelShape[15];
    private static final VoxelShape WALL_X = Block.box(0, 0, 5.5, 16, 16, 10.5), WALL_Z = Block.box(5.5, 0, 0, 10.5, 16, 16);

    static {
        for (int i = 0; i < PLATES.length; i++) {
            PLATES[i] = Block.box(0, i, 0, 16, i + 2, 16);
        }
    }

    public LightBridgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.FLOOR).setValue(OFFSET, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, OFFSET);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(KIND)) {
            case FLOOR -> PLATES[state.getValue(OFFSET)];
            case WALL_X -> WALL_X;
            case WALL_Z -> WALL_Z;
            case WALL -> Shapes.block();
            case TUNNEL -> Shapes.empty();
        };
    }

    @Override
    protected boolean canBeReplaced(BlockState state, net.minecraft.world.item.context.BlockPlaceContext context) {
        return false;
    }
}
