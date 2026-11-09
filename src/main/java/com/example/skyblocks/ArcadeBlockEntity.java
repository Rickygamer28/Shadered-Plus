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
    public static final String[] GAMES = {"snake", "breakout", "tetris", "pacman", "pong", "frogger", "minesweeper", "fighter"};
    /** The colours of a game's little picture on the screen (ArcadeFramePayload's cells). */
    public static final int[] PALETTE = {0x070B16, 0x3CC85A, 0xBEFF8C, 0xFF3A4A, 0xFFFFFF, 0x9AD8FF, 0xFF4D5E, 0xFF9A2E,
            0xFFE74A, 0x5BFF6A, 0x4DA6FF, 0xB45BFF, 0x00E5FF, 0x3A4A6A, 0x1A2238, 0xFF7AD9};
    private final int[] best = new int[GAMES.length];
    private final String[] bestBy = java.util.Collections.nCopies(GAMES.length, "").toArray(new String[0]);
    /** Client only: what someone is playing on it now (the picture, the game, their score and name; when it came). */
    public byte[] liveCells;
    public int liveGame, liveScore;
    public String liveName = "";
    public long liveAt = Long.MIN_VALUE / 2;

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
