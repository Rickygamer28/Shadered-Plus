package com.example.skyblocks;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps the list of speakers loaded on the client (client/MusicReactor carries jukebox music through them). */
public class SpeakerBlockEntity extends BlockEntity {
    public static final Set<BlockPos> CLIENT_SPEAKERS = Collections.synchronizedSet(new HashSet<>());

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            CLIENT_SPEAKERS.add(worldPosition.immutable());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            CLIENT_SPEAKERS.remove(worldPosition);
        }
    }
}
