package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A bamboo water spout (client/BambooSpoutRenderer draws its tube and water): on the client, its clack and splashes. */
public class BambooSpoutBlockEntity extends BlockEntity {
    private double lastPhase = -1.0;

    public BambooSpoutBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BAMBOO_SPOUT.get(), pos, state);
    }

    /** Where its water lands, a little way out over the pond in front (world position). */
    public static double[] pourAt(BlockPos pos, Direction facing) {
        double out = 0.5 + 7.5 / 16.0;
        return new double[] {pos.getX() + 0.5 + facing.getStepX() * out, pos.getY() - 1.5 / 16.0, pos.getZ() + 0.5 + facing.getStepZ() * out};
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, BambooSpoutBlockEntity be) {
        double seconds = (level.getGameTime() % 1_728_000L) / 20.0;
        double p = BambooSpoutBlock.phase(pos, seconds);
        double last = be.lastPhase;
        be.lastPhase = p;
        if (last < 0.0) {
            return;
        }
        boolean wrapped = p < last;
        Direction facing = state.getValue(BambooSpoutBlock.FACING);
        double[] at = pourAt(pos, facing);
        if (crossed(last, p, wrapped, BambooSpoutBlock.TIP + 0.3)) { // (the water pouring out)
            level.playLocalSound(at[0], at[1], at[2], SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.22F, 1.5F + level.random.nextFloat() * 0.2F, false);
        }
        if (p > BambooSpoutBlock.POUR - 0.1 && p < BambooSpoutBlock.BACK) { // (splashing into the pond)
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.SPLASH, at[0] + (level.random.nextDouble() - 0.5) * 0.15, at[1] + 0.02,
                        at[2] + (level.random.nextDouble() - 0.5) * 0.15, 0.0, 0.05, 0.0);
            }
        }
        if (crossed(last, p, wrapped, BambooSpoutBlock.CLACK)) { // (knocking on its stone: the hollow clack)
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, SoundEvents.BAMBOO_WOOD_TRAPDOOR_CLOSE, SoundSource.BLOCKS,
                    0.8F, 1.75F + level.random.nextFloat() * 0.1F, false);
        }
        if (level.random.nextInt(100) == 0) { // (the trickle)
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.12F, 1.6F, false);
        }
    }

    private static boolean crossed(double last, double now, boolean wrapped, double mark) {
        return wrapped ? (last < mark || now >= mark) : (last < mark && now >= mark);
    }

    /** What its renderer draws (1.20.1: the block entity says, not the renderer). */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(1.0);
    }
}
