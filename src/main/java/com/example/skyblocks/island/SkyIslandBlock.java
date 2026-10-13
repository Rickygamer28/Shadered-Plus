package com.example.skyblocks.island;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.ModItems;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A placed Sky Island: a small pedestal with the scanned island floating above it as a miniature, mobs included
 * (client/island/SkyIslandRenderer). Right-click (empty hand) changes how wide it is shown (1 to 4 blocks).
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

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // let the held item be used (e.g. placing a block next to it)
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SkyIslandBlockEntity island) {
            island.cycleWidth();
            player.displayClientMessage(Component.translatable("message.skyblocks.island_width",
                    island.displayWidth()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ItemStack itemFor(@Nullable BlockEntity be) {
        ItemStack stack = new ItemStack(ModItems.SKY_ISLAND.get());
        if (be instanceof SkyIslandBlockEntity island && island.island() != null) {
            stack.set(ModComponents.ISLAND.get(), island.island());
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
}
