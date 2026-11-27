package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A speaker (or beat lamp): known to the screens playing near it on each computer (client/tv/TvPlayers). */
public class SpeakerBlockEntity extends BlockEntity {
    /** Every speaker loaded on this computer (looked at again before use: stale ones dropped). */
    public static final java.util.Set<BlockPos> LOADED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(state.getBlock() instanceof BeatLampBlock ? ModBlockEntities.BEAT_LAMP.get() : ModBlockEntities.SPEAKER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide && getBlockState().getBlock() instanceof SpeakerBlock) {
            LOADED.add(worldPosition.immutable());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(worldPosition);
    }
}
