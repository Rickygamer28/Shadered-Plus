package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A dark fountain (DarkFountainBlockEntity): made by cutting the ground open with the rift knife, closed the same way.
 * Nothing to stand on or bump into: only the darkness pouring up (client/DarkFountainRenderer).
 */
public class DarkFountainBlock extends BaseEntityBlock {
    public static final MapCodec<DarkFountainBlock> CODEC = simpleCodec(DarkFountainBlock::new);

    public DarkFountainBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block2.TEAR;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DarkFountainBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.DARK_FOUNTAIN.get(), DarkFountainBlockEntity::serverTick);
    }

    /** Sparks of light carried up the column, and ink splashing at its foot. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof DarkFountainBlockEntity be)) {
            return;
        }
        float height = be.column(level.getGameTime()) * DarkFountainBlockEntity.HEIGHT;
        for (int i = 0; i < 3 && height > 2; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = DarkFountainBlockEntity.RADIUS * (0.9 + random.nextDouble() * 0.25);
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + random.nextDouble() * Math.min(height, 12),
                    pos.getZ() + 0.5 + Math.sin(a) * r, 0, 0.25 + random.nextDouble() * 0.3, 0);
        }
        if (be.tear(level.getGameTime()) > 0.3F) {
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.SQUID_INK, pos.getX() + 0.5 + Math.cos(a) * 1.3, pos.getY() + 0.1, pos.getZ() + 0.5 + Math.sin(a) * 1.3,
                        Math.cos(a) * 0.05, 0.12 + random.nextDouble() * 0.1, Math.sin(a) * 0.05);
            }
        }
    }

    /** (its outline: the tear on the ground, so the knife can find it) */
    private static final class Block2 {
        static final VoxelShape TEAR = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 2, 16);
    }
}
