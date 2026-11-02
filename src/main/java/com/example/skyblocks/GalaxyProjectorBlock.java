package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A galaxy projector: a little dome lamp throwing a slowly turning sky on the walls, floor and ceiling round it - a
 * galaxy (nebulae and stars), a starry night, or the light of the sea (client/GalaxyProjectorRenderer). Right-click: the
 * next sky; sneak + right-click: on / off.
 */
public class GalaxyProjectorBlock extends BaseEntityBlock {
    public static final MapCodec<GalaxyProjectorBlock> CODEC = simpleCodec(GalaxyProjectorBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int MODES = 3;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, MODES - 1);
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 8, 12);

    public GalaxyProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(MODE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, MODE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                level.setBlock(pos, state.cycle(LIT), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, state.getValue(LIT) ? 0.7F : 1.0F);
            } else {
                int mode = (state.getValue(MODE) + 1) % MODES;
                level.setBlock(pos, state.setValue(MODE, mode).setValue(LIT, true), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.0F + 0.2F * mode);
                player.displayClientMessage(Component.translatable("message.skyblocks.galaxy_projector." + mode).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GalaxyProjectorBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.galaxy_projector").withStyle(ChatFormatting.GRAY));
    }
}
