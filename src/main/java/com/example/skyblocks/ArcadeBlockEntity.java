package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** An arcade cabinet: the best score in each of its games, and who got it. */
public class ArcadeBlockEntity extends BlockEntity {
    public static final String[] GAMES = {"snake", "breakout"};
    private final int[] best = new int[GAMES.length];
    private final String[] bestBy = {"", ""};

    public ArcadeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCADE.get(), pos, state);
    }

    public int best(int game) {
        return best[game];
    }

    public String bestBy(int game) {
        return bestBy[game];
    }

    /** A game played to its end: kept if it's the best yet. */
    public void scored(int game, int score, String name) {
        if (game >= 0 && game < GAMES.length && score > best[game]) {
            best[game] = score;
            bestBy[game] = name;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < GAMES.length; i++) {
            tag.putInt(GAMES[i], best[i]);
            tag.putString(GAMES[i] + "_by", bestBy[i]);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < GAMES.length; i++) {
            best[i] = tag.getInt(GAMES[i]);
            bestBy[i] = tag.getString(GAMES[i] + "_by");
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
}
