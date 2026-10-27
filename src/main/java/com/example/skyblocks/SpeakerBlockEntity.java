package com.example.skyblocks;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The speaker's light colour (set with a dye) or RGB mode (cycling through the rainbow, faster on the beat), and the
 * list of speakers loaded on the client (client/MusicReactor carries jukebox music through them).
 */
public class SpeakerBlockEntity extends BlockEntity {
    public static final Set<BlockPos> CLIENT_SPEAKERS = Collections.synchronizedSet(new HashSet<>());
    public static final int DEFAULT_COLOUR = 0xFF4DCC;

    private int colour = DEFAULT_COLOUR;
    private boolean rgb;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    public int colour() { return colour; }
    public boolean rgb() { return rgb; }

    public void setColour(int colour) {
        this.colour = colour & 0xFFFFFF;
        this.rgb = false;
        changed();
    }

    public void setRgb(boolean rgb) {
        this.rgb = rgb;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("colour", colour);
        tag.putBoolean("rgb", rgb);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("colour")) {
            colour = tag.getInt("colour") & 0xFFFFFF;
            rgb = tag.getBoolean("rgb");
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
