package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A terrarium; on the client, its two geckos (where they are, where they're going, what they're doing). */
public class TerrariumBlockEntity extends BlockEntity {
    /** Client only: one gecko - in pixels of the block, its heading (radians), where it's off to, a pause, its tongue. */
    public static final class Gecko {
        public float x, z, yaw, tx, tz, walked;
        public int pause, tongue;
        public float px, pz, pyaw; // (last tick's, to draw between)
    }

    public final Gecko[] geckos = {new Gecko(), new Gecko()};
    public long tickedAt = -1;
    public boolean placed;

    public TerrariumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERRARIUM.get(), pos, state);
    }
}
