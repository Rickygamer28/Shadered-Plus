package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Holds the client-side renderable model (like Shadered's MaxwellEntity); nothing is saved. */
public class OiiaCatBlockEntity extends BlockEntity {
    /** Client only: Shadered RenderableModels (walking cat / loaf pose), kept as Object so the server never loads client classes. */
    public Object clientModel;
    public Object clientSpinModel;

    /** Client only: game time when the current redstone signal started (-1 = not powered), to start the animation from frame 0. */
    public long animationStart = -1;

    public OiiaCatBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.OIIA_CAT.get(), pos, state);
    }

    protected OiiaCatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** How many times the small cat's size to draw it (0 = not set up yet: don't draw). */
    public int size() {
        return 1;
    }

    public Direction facing() {
        return getBlockState().getValue(OiiaCatBlock.FACING);
    }

    public boolean powered() {
        return getBlockState().getValue(OiiaCatBlock.POWERED);
    }

    /** Where the cat's centre is inside this block, on x and z (the small cat stands in the middle). */
    public double centerOffset() {
        return 0.5;
    }

    /** Around the cat's feet, reaching up and out past the cat (the spinning tail sticks out). */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        int size = Math.max(1, size());
        BlockPos pos = getBlockPos();
        double c = centerOffset();
        return new net.minecraft.world.phys.AABB(pos.getX() + c, pos.getY(), pos.getZ() + c,
                pos.getX() + c, pos.getY() + size, pos.getZ() + c).inflate(1.5 * size);
    }
}
