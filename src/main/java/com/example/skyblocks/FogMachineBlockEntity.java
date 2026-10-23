package com.example.skyblocks;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Puffs the fog on the client, and notes which fog machines are going (for the disco ball's beams). */
public class FogMachineBlockEntity extends BlockEntity {
    /** Fog machines going on this client: position -> when it last puffed (System.nanoTime). */
    public static final Map<BlockPos, Long> ACTIVE = new HashMap<>();

    public FogMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FOG_MACHINE.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FogMachineBlockEntity be) {
        if (!state.getValue(FogMachineBlock.ON)) {
            ACTIVE.remove(pos);
            return;
        }
        ACTIVE.put(pos.immutable(), System.nanoTime());
        Direction facing = state.getValue(FogMachineBlock.FACING);
        RandomSource random = level.getRandom();
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.5 + facing.getStepX() * 0.55 + (random.nextDouble() - 0.5) * 0.2;
            double y = pos.getY() + 0.35 + (random.nextDouble() - 0.5) * 0.15;
            double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.55 + (random.nextDouble() - 0.5) * 0.2;
            double speed = 0.08 + random.nextDouble() * 0.08;
            double spread = 0.035;
            level.addParticle(ModParticles.FOG.get(), x, y, z,
                    facing.getStepX() * speed + (random.nextDouble() - 0.5) * spread, -0.004,
                    facing.getStepZ() * speed + (random.nextDouble() - 0.5) * spread);
        }
    }

    /** Whether a fog machine has been going within {@code range} blocks of pos in the last couple of seconds. */
    public static boolean fogNear(BlockPos pos, double range) {
        long now = System.nanoTime();
        ACTIVE.values().removeIf(t -> now - t > 3_000_000_000L);
        for (BlockPos p : ACTIVE.keySet()) {
            if (p.distSqr(pos) <= range * range) {
                return true;
            }
        }
        return false;
    }
}
