package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A machine's frame - the particle accelerator's ring, the weather machine's mast, the star forge's chamber: built round
 * (or on) its machine, it forms it (MachineBlockEntity). Formed, it works as the machine's own sides: right-click it for
 * the machine's screen; cables and hoppers on it reach the machine.
 */
public class MachineFrameBlock extends BaseEntityBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    /** Formed into something drawn whole (the particle accelerator's round tube): its own model hidden. */
    public static final BooleanProperty HIDDEN = BooleanProperty.create("hidden");
    /** A star forge's frames: its star's colour, lighting their veins (0 unlit; 1-4 red, yellow, blue, white). */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty GLOW =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("glow", 0, 4);

    /** Which machine it's a frame of. */
    public enum Kind {
        ACCELERATOR, WEATHER, FORGE
    }

    private final Kind kind;

    public MachineFrameBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(HIDDEN, false).setValue(FramePart.PART, FramePart.NONE)
                .setValue(FramePart.QUAD, 0).setValue(GLOW, 0));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new MachineFrameBlock(kind, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, HIDDEN, FramePart.PART, FramePart.QUAD, GLOW);
    }

    /** Put down: the machines near it look at their frames again. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && !old.is(state.getBlock())) {
            MachineBlockEntity.relinkNear(level, pos);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(FORMED) && level.getBlockEntity(pos) instanceof MachineFrameBlockEntity f && f.master() != null
                && level.getBlockState(f.master()).getBlock() instanceof MachineBlock) {
            return MachineBlock.open(level, f.master(), player);
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineFrameBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks." + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).getPath())
                .withStyle(ChatFormatting.GRAY));
    }
}
