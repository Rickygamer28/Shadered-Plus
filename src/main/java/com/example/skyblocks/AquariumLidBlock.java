package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An aquarium lid: a black hood over a tank with a lamp in it. Click it to change the tank's light (daylight,
 * moonlight, sunset, reef: as in the aquarium's menu) without opening the menu.
 */
public class AquariumLidBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);
    /** The light it gives the tank under it: 0 as the aquarium's menu has it, 1 daylight, 2 moonlight, 3 sunset, 4 reef. */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty LIGHT =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("light", 0, 4);

    public AquariumLidBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIGHT, 0));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockState(pos.below()).getBlock() instanceof AquariumBlock tank) || tank.deep()) {
            return InteractionResult.PASS;
        }
        // (only the tank under it: its lid's own light, kept on the lid; from the menu's to moonlight, then round)
        int light = state.getValue(LIGHT);
        int next = light == 0 ? 2 : light % 4 + 1;
        if (level.isClientSide) {
            com.example.skyblocks.client.ClientAccess.showAquariumLight(next - 1);
        } else {
            level.setBlock(pos, state.setValue(LIGHT, next), Block.UPDATE_ALL);
        }
        level.playSound(player, pos, net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON, net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 1.2F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.aquarium_lid").withStyle(ChatFormatting.GRAY));
    }
}
