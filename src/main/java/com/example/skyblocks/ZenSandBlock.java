package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Zen sand: fine pale sand you rake patterns into (ZenRakeItem) - straight lines or waves the way you're facing,
 * smoothed flat again, or rings round a stone (sneak-raking a block makes it the middle of a ring that spreads onto
 * the zen sand round it).
 */
public class ZenSandBlock extends Block {
    public enum Pattern implements StringRepresentable {
        SMOOTH("smooth"), LINES_X("lines_x"), LINES_Z("lines_z"), WAVES_X("waves_x"), WAVES_Z("waves_z"),
        RING("ring"), RING_N("ring_n"), RING_S("ring_s"), RING_E("ring_e"), RING_W("ring_w"),
        RING_NE("ring_ne"), RING_NW("ring_nw"), RING_SE("ring_se"), RING_SW("ring_sw");

        private final String name;

        Pattern(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public boolean ring() {
            return ordinal() >= RING.ordinal();
        }

        /** The part of a ring for the block at (dx, dz) from its middle. */
        static Pattern ringPart(int dx, int dz) {
            if (dx == 0 && dz == 0) {
                return RING;
            }
            if (dx == 0) {
                return dz < 0 ? RING_N : RING_S;
            }
            if (dz == 0) {
                return dx > 0 ? RING_E : RING_W;
            }
            return dz < 0 ? (dx > 0 ? RING_NE : RING_NW) : (dx > 0 ? RING_SE : RING_SW);
        }
    }

    public static final EnumProperty<Pattern> PATTERN = EnumProperty.create("pattern", Pattern.class);

    public ZenSandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PATTERN, Pattern.SMOOTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PATTERN);
    }

    /** Lines (or, raking the same block again, waves; again, smooth) along an axis. */
    static Pattern next(Pattern now, Direction.Axis axis, boolean again) {
        Pattern lines = axis == Direction.Axis.X ? Pattern.LINES_X : Pattern.LINES_Z;
        Pattern waves = axis == Direction.Axis.X ? Pattern.WAVES_X : Pattern.WAVES_Z;
        if (!again) {
            return now == waves ? waves : lines; // (dragging the rake over waves keeps them)
        }
        return now == lines ? waves : now == waves ? Pattern.SMOOTH : lines;
    }

    /** A ring round pos: pos its middle, the zen sand round it the ring's outer parts. */
    static void ring(Level level, BlockPos pos) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                BlockPos at = pos.offset(dx, 0, dz);
                BlockState there = level.getBlockState(at);
                if (there.getBlock() instanceof ZenSandBlock && (dx == 0 && dz == 0 || there.getValue(PATTERN) != Pattern.RING)) {
                    level.setBlock(at, there.setValue(PATTERN, Pattern.ringPart(dx, dz)), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.zen_sand").withStyle(ChatFormatting.GRAY));
    }
}
