package com.example.skyblocks;

import com.example.skyblocks.island.ShaderedSkyItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A sky lantern: a lantern with a sky inside its glass (any Shadered sky - right-click it with a sky block to put that
 * sky in), lighting the room round it in that sky's colours (client/SkyLanternRenderer). Stands, or hangs from a chain.
 */
public class SkyLanternBlock extends LanternBlock implements EntityBlock {
    private static final VoxelShape STANDING_SHAPE = Shapes.or(box(4, 0, 4, 12, 11, 12), box(6, 11, 6, 10, 14.5, 10));
    private static final VoxelShape HANGING_SHAPE = Shapes.or(box(4, 1, 4, 12, 12, 12), box(6.5, 12, 6.5, 9.5, 16, 9.5));

    public SkyLanternBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HANGING) ? HANGING_SHAPE : STANDING_SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkyLanternBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.sky_lantern").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        ShaderedSkyItems.Sky sky = ShaderedSkyItems.sky(stack);
        if (sky == null || !(level.getBlockEntity(pos) instanceof SkyLanternBlockEntity lantern)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            lantern.setSky(sky.type().name(), sky.pass().name());
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("message.skyblocks.sky_lantern.sky", stack.getHoverName()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
