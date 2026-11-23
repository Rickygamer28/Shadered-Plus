package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Water Fountain: a stone basin with a spout, a jet of water leaping from it and falling back in eight arcs into
 * the pool - all in HD water (client/FountainRenderer, shaders/core/hd_water): waves, the sky reflected, caustics,
 * foam. (The Sky Fountain is the same, of sky liquid: SkyHolderBlock.) Its splashes and murmur are here.
 */
public class FountainBlock extends Block implements EntityBlock {
    public static final VoxelShape SHAPE = Shapes.or(box(0, 0, 0, 16, 2, 16), box(0, 2, 0, 16, 6, 2), box(0, 2, 14, 16, 6, 16),
            box(0, 2, 2, 2, 6, 14), box(14, 2, 2, 16, 6, 14), box(6, 2, 6, 10, 10, 10));

    public FountainBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleRenderedBlockEntity(ModBlockEntities.WATER_FOUNTAIN.get(), pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        splash(level, pos, random, false);
    }

    /** Splashes where the arcs land, and now and then the fountain's murmur. */
    public static void splash(Level level, BlockPos pos, RandomSource random, boolean sky) {
        for (int i = 0; i < 3; i++) {
            float a = random.nextInt(8) * Mth.TWO_PI / 8.0F + 0.2F;
            double x = pos.getX() + 0.5 + Mth.cos(a) * 0.32, z = pos.getZ() + 0.5 + Mth.sin(a) * 0.32;
            level.addParticle(sky ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SPLASH, x, pos.getY() + 0.36, z, 0.0, 0.05, 0.0);
        }
        if (random.nextInt(14) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS,
                    0.25F, 1.2F + random.nextFloat() * 0.3F, false);
        }
    }
}
