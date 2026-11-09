package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A light bridge emitter: how far its bridge reaches (0: none; kept by the one of the pair facing east or south, which
 * puts the bridge's blocks down, takes them away, and draws it) and its colour (-1: rainbow).
 */
public class LightBridgeEmitterBlockEntity extends BlockEntity {
    public static final int MAX_LENGTH = 32;
    private int length;
    private int colour = -1;

    public LightBridgeEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIGHT_BRIDGE_EMITTER.get(), pos, state);
    }

    /** How far to the other emitter (the bridge spans 1 to length - 1), or 0. */
    public int length() {
        return length;
    }

    public int colour() {
        return colour;
    }

    public void setColour(int c) {
        colour = c;
        changed();
    }

    private Direction facing() {
        return getBlockState().getValue(LightBridgeEmitterBlock.FACING);
    }

    /** The emitter facing this one across its bridge, if there is one. */
    @Nullable
    public LightBridgeEmitterBlockEntity partner() {
        if (level == null) {
            return null;
        }
        Direction f = facing();
        for (int i = 1; i <= MAX_LENGTH; i++) {
            BlockPos p = worldPosition.relative(f, i);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof LightBridgeEmitterBlock) {
                return s.getValue(LightBridgeEmitterBlock.FACING) == f.getOpposite() && level.getBlockEntity(p) instanceof LightBridgeEmitterBlockEntity e ? e : null;
            }
            if (!s.isAir() && !(s.getBlock() instanceof LightBridgeBlock)) {
                return null;
            }
        }
        return null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LightBridgeEmitterBlockEntity be) {
        if (level.getGameTime() % 10L != Math.floorMod(pos.asLong(), 10L)) {
            return;
        }
        Direction f = state.getValue(LightBridgeEmitterBlock.FACING);
        if (f != Direction.EAST && f != Direction.SOUTH) {
            return; // (its partner looks after the bridge)
        }
        LightBridgeEmitterBlockEntity partner = be.partner();
        int reach = 0;
        if (partner != null && !level.hasNeighborSignal(pos) && !level.hasNeighborSignal(partner.getBlockPos())) {
            reach = Math.abs(partner.getBlockPos().get(f.getAxis()) - pos.get(f.getAxis()));
        }
        if (reach != be.length) {
            if (be.length > 0) {
                clearPath(level, pos, f);
            }
            for (int i = 1; i < reach; i++) {
                BlockPos p = pos.relative(f, i);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, ModBlocks.LIGHT_BRIDGE.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, pos, reach > 0 ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.6F);
            be.length = reach;
            be.changed();
        }
    }

    /** Takes away the bridge's blocks in front of an emitter. */
    public static void clearPath(Level level, BlockPos pos, Direction f) {
        for (int i = 1; i <= MAX_LENGTH; i++) {
            BlockPos p = pos.relative(f, i);
            if (!(level.getBlockState(p).getBlock() instanceof LightBridgeBlock)) {
                break;
            }
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
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
        tag.putInt("length", length);
        tag.putInt("colour", colour);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        length = tag.getInt("length");
        colour = tag.contains("colour") ? tag.getInt("colour") : -1;
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
