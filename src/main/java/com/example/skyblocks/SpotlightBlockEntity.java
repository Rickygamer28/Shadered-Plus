package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A spotlight: its colour, on / off, and aimed one way (aimYaw, aimPitch) or following players; on the client, where its lamp points now (turning smoothly). */
public class SpotlightBlockEntity extends BlockEntity {
    private int colour = 0xFFF4D8;
    private boolean on = true;
    private boolean aimed;
    private float aimYaw, aimPitch;
    public float yaw, pitch = -0.6F;
    public long turnedAt;

    public SpotlightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPOTLIGHT.get(), pos, state);
    }

    public int colour() {
        return colour;
    }

    public boolean on() {
        return on;
    }

    public void setColour(int c) {
        colour = c;
        changed();
    }

    public void setOn(boolean o) {
        on = o;
        changed();
    }

    public boolean aimed() {
        return aimed;
    }

    public float aimYaw() {
        return aimYaw;
    }

    public float aimPitch() {
        return aimPitch;
    }

    /** Points it that way and keeps it there (yaw round from south, pitch up; radians). */
    public void aim(float yaw, float pitch) {
        aimed = true;
        aimYaw = yaw;
        aimPitch = Math.max(-1.5707F, Math.min(1.5707F, pitch));
        changed();
    }

    /** Back to following the nearest player. */
    public void follow() {
        aimed = false;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("colour", colour);
        tag.putBoolean("off", !on);
        if (aimed) {
            tag.putFloat("aim_yaw", aimYaw);
            tag.putFloat("aim_pitch", aimPitch);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        colour = tag.contains("colour") ? tag.getInt("colour") : 0xFFF4D8;
        on = !tag.getBoolean("off");
        aimed = tag.contains("aim_yaw");
        aimYaw = tag.getFloat("aim_yaw");
        aimPitch = tag.getFloat("aim_pitch");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
