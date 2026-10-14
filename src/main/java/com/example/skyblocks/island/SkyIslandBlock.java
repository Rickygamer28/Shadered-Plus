package com.example.skyblocks.island;

import com.example.skyblocks.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A placed Sky Island: a small pedestal with the scanned island floating above it as a miniature, mobs included
 * (client/island/SkyIslandRenderer), 1 block wide.
 * Breaking it gives the item back with the island in it.
 */
public class SkyIslandBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 3, 13);

    public SkyIslandBlock(Properties properties) {
        super(properties);
    }


    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkyIslandBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Right-click with an ender pearl: travel to where the island was scanned. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level,
                                                     BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                     net.minecraft.world.InteractionHand hand,
                                                     net.minecraft.world.phys.BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (IslandTravel.isTravelItem(stack) && level.getBlockEntity(pos) instanceof SkyIslandBlockEntity island
                && island.island() != null) {
            if (!level.isClientSide) {
                IslandTravel.travel(level, player, island.island(), stack);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    /** A renamed Sky Island item keeps its name on the pedestal (the island itself comes in BlockEntityTag). */
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (stack.hasCustomHoverName() && level.getBlockEntity(pos) instanceof SkyIslandBlockEntity island) {
            island.setName(stack.getHoverName());
        }
    }

    private static ItemStack itemFor(@Nullable BlockEntity be) {
        ItemStack stack = new ItemStack(ModItems.SKY_ISLAND.get());
        if (be instanceof SkyIslandBlockEntity island && island.island() != null) {
            IslandData.set(stack, island.island());
            if (island.name() != null) {
                stack.setHoverName(island.name());
            }
        }
        return stack;
    }

    /** Drops the item with the island in it (no loot table: the island data comes from the block entity). */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(itemFor(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)));
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return itemFor(level.getBlockEntity(pos));
    }
}
