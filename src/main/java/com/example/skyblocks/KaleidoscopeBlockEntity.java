package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A kaleidoscope: its symmetry (3 to 12-fold), its colours, and how far its glass has turned (kept as "so far at that
 * tick", so it stops where it is when powered and carries on from there).
 */
public class KaleidoscopeBlockEntity extends BlockEntity {
    public static final int[] FOLDS = {3, 4, 5, 6, 8, 12};
    public static final String[] PALETTES = {"jewels", "cathedral", "ocean", "sunset", "autumn", "frost"};
    private int symmetry = 3;
    private int palette;
    private boolean powered;
    private double baseTurn;
    private long since;

    public KaleidoscopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KALEIDOSCOPE.get(), pos, state);
    }

    /** Seconds of turning. */
    public double turn(long gameTime, float partialTick) {
        return baseTurn + (powered ? 0.0 : (gameTime + partialTick - since) / 20.0);
    }

    public int symmetry() {
        return symmetry;
    }

    public int palette() {
        return palette;
    }

    public void cycleSymmetry() {
        symmetry = (symmetry + 1) % FOLDS.length;
        changed();
    }

    public void cyclePalette() {
        palette = (palette + 1) % PALETTES.length;
        changed();
    }

    public void setPowered(boolean on) {
        if (on != powered) {
            long now = level == null ? since : level.getGameTime();
            baseTurn = turn(now, 0.0F) % 100000.0;
            since = now;
            powered = on;
            changed();
        }
    }

    /** The same settings and turning as another (kaleidoscopes side by side are one). */
    public void copyFrom(KaleidoscopeBlockEntity other) {
        symmetry = other.symmetry;
        palette = other.palette;
        powered = other.powered;
        baseTurn = other.baseTurn;
        since = other.since;
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
        tag.putInt("symmetry", symmetry);
        tag.putInt("palette", palette);
        tag.putBoolean("powered", powered);
        tag.putDouble("turn", baseTurn);
        tag.putLong("since", since);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        symmetry = tag.contains("symmetry") ? Math.floorMod(tag.getInt("symmetry"), FOLDS.length) : 3;
        palette = Math.floorMod(tag.getInt("palette"), PALETTES.length);
        powered = tag.getBoolean("powered");
        baseTurn = tag.getDouble("turn");
        since = tag.getLong("since");
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
