package com.example.skyblocks.island;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.ModItems;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.example.skyblocks.ModBlockEntities;
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
    public static final MapCodec<SkyIslandBlock> CODEC = simpleCodec(SkyIslandBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 3, 13);

    public SkyIslandBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
    public net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
                                                               BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                               net.minecraft.world.InteractionHand hand,
                                                               net.minecraft.world.phys.BlockHitResult hit) {
        if (IslandTravel.isTravelItem(stack) && level.getBlockEntity(pos) instanceof SkyIslandBlockEntity island
                && island.island() != null) {
            if (!level.isClientSide) {
                IslandTravel.travel(level, player, island.island(), stack);
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemStack itemFor(@Nullable BlockEntity be) {
        ItemStack stack = new ItemStack(ModItems.SKY_ISLAND.get());
        if (be instanceof SkyIslandBlockEntity island && island.island() != null) {
            stack.set(ModComponents.ISLAND.get(), island.islandWithMobs()); // with the mobs on it now
            if (island.name() != null) {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, island.name());
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
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return itemFor(level.getBlockEntity(pos));
    }

    /** Ticks the mobs living on the island (both sides: the server moves them, clients follow along). */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.SKY_ISLAND.get(), SkyIslandBlockEntity::tick);
    }
}
