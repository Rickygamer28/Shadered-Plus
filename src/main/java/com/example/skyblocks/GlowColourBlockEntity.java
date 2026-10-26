package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A colour (set with a dye) or RGB mode (cycling through the rainbow): the lava lamp's wax, the fires' flames. */
public class GlowColourBlockEntity extends BlockEntity {
    private int colour;
    private boolean rgb;
    /** (Client only: how lit the fire looks now, 0..1, easing on and off; -1 = not yet drawn.) */
    public float shownLit = -1.0F;
    public long shownLitTime;

    public GlowColourBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int defaultColour) {
        super(type, pos, state);
        this.colour = defaultColour;
    }

    public static GlowColourBlockEntity lavaLamp(BlockPos pos, BlockState state) {
        return new GlowColourBlockEntity(ModBlockEntities.LAVA_LAMP.get(), pos, state, 0xFF6A1A);
    }

    public static GlowColourBlockEntity fire(BlockPos pos, BlockState state) {
        return new GlowColourBlockEntity(ModBlockEntities.FIRE.get(), pos, state, 0xFF8A1E);
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("colour", colour);
        tag.putBoolean("rgb", rgb);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("colour")) {
            colour = tag.getInt("colour") & 0xFFFFFF;
            rgb = tag.getBoolean("rgb");
        }
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

    /** The colour to show now (RGB mode: cycling, each block a little different). */
    public static int shown(GlowColourBlockEntity be, double seconds) {
        return shown(be, seconds, true);
    }

    /** (eachOwn false: every block in step.) */
    public static int shown(GlowColourBlockEntity be, double seconds, boolean eachOwn) {
        if (!be.rgb) {
            return be.colour;
        }
        float hue = (float) ((seconds * 0.07 + (eachOwn ? (be.worldPosition.hashCode() & 255) / 255.0 : 0.0)) % 1.0);
        return net.minecraft.util.Mth.hsvToRgb(hue, 0.85F, 1.0F);
    }
}
