package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A tesla coil: lightning crackling from the ring on top into the air, jumping to you when you come near and between
 * coils near each other, wilder with music (client/TeslaCoilRenderer). Powered by redstone it zaps hostile mobs that
 * come close. Right-click: on / off; with a dye: the lightning's colour; sneak + right-click: RGB.
 */
public class TeslaCoilBlock extends BaseEntityBlock {
    public static final MapCodec<TeslaCoilBlock> CODEC = simpleCodec(TeslaCoilBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** How far it zaps hostile mobs when powered (blocks). */
    public static final double ZAP_RANGE = 4.5;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(6, 2, 6, 10, 12, 10), Block.box(2, 12, 2, 14, 15, 14));

    public TeslaCoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye) || !(level.getBlockEntity(pos) instanceof GlowColourBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            be.setColour(dye.getDyeColor().getTextureDiffuseColor());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                if (level.getBlockEntity(pos) instanceof GlowColourBlockEntity be) {
                    be.setRgb(!be.rgb());
                    player.displayClientMessage(Component.translatable(be.rgb() ? "message.skyblocks.rgb_on" : "message.skyblocks.rgb_off")
                            .withStyle(ChatFormatting.AQUA), true);
                }
            } else {
                level.setBlock(pos, state.cycle(LIT), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, state.getValue(LIT) ? 0.7F : 1.0F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(3) == 0) { // (the crackle)
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS,
                    0.06F + random.nextFloat() * 0.06F, 1.6F + random.nextFloat() * 0.4F, false);
        }
    }

    /** Powered by redstone: a jolt now and then for each hostile mob within reach. */
    private static void serverTick(Level level, BlockPos pos, BlockState state, GlowColourBlockEntity be) {
        if (!state.getValue(LIT) || level.getGameTime() % 25L != (pos.asLong() & 15L) || !level.hasNeighborSignal(pos)) {
            return;
        }
        AABB box = new AABB(pos).inflate(ZAP_RANGE);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.85, cz = pos.getZ() + 0.5;
        boolean zapped = false;
        for (Monster m : level.getEntitiesOfClass(Monster.class, box, m -> m.isAlive() && m.distanceToSqr(cx, cy, cz) < ZAP_RANGE * ZAP_RANGE)) {
            m.hurt(level.damageSources().lightningBolt(), 3.0F);
            zapped = true;
        }
        if (zapped) {
            level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.3F, 1.9F);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.TESLA_COIL.get(), TeslaCoilBlock::serverTick);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GlowColourBlockEntity(ModBlockEntities.TESLA_COIL.get(), pos, state, 0x9FD8FF);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.tesla_coil").withStyle(ChatFormatting.GRAY));
    }
}
