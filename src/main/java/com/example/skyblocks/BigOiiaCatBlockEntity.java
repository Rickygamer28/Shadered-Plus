package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The big cat's master part (bottom centre of the cube): remembers the cube's size, which way the cat faces and
 * whether it has a redstone signal (synced to clients), and draws the cat at that size.
 */
public class BigOiiaCatBlockEntity extends OiiaCatBlockEntity {
    private int size;
    private Direction facing = Direction.NORTH;
    private boolean powered;
    /** Server only, not saved: set once the cat is being taken apart, so it is only taken apart (and dropped) once. */
    boolean dismantled;

    public BigOiiaCatBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIG_OIIA_CAT.get(), pos, state);
    }

    void setup(int size, Direction facing) {
        this.size = size;
        this.facing = facing;
        sync();
    }

    void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            sync();
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Direction facing() {
        return facing;
    }

    @Override
    public boolean powered() {
        return powered;
    }

    /** The cube's centre is on a block edge for even sizes, in the middle of the master block for odd ones. */
    @Override
    public double centerOffset() {
        return size / 2.0 - size / 2;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        size = tag.getInt("size");
        facing = Direction.from2DDataValue(tag.getInt("facing"));
        powered = tag.getBoolean("powered");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("size", size);
        tag.putInt("facing", facing.get2DDataValue());
        tag.putBoolean("powered", powered);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
