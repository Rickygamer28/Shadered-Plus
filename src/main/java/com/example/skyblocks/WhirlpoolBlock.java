package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.example.skyblocks.compat.ItemInteractionResult;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * The whirlpool singularity: a vortex in a frame that stores fluids (WhirlpoolBlockEntity). Right-click: what it holds
 * (void fluids, drain mode); with a bucket or any fluid container: fills or empties it. Sneak + right-click: drain mode
 * on / off. A comparator reads how full it is. Broken, everything in it goes with the item.
 */
public class WhirlpoolBlock extends BaseEntityBlock {
    /** In its containment tank: no frame of its own (the tank its frame). */
    public static final BooleanProperty IN_TANK = BooleanProperty.create("in_tank");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(0, 14, 0, 16, 16, 16),
            Block.box(0, 2, 0, 2, 14, 2), Block.box(14, 2, 0, 16, 14, 2), Block.box(0, 2, 14, 2, 14, 16), Block.box(14, 2, 14, 16, 14, 16),
            Block.box(4, 4, 4, 12, 12, 12));
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);

    public WhirlpoolBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(IN_TANK, false));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(IN_TANK);
    }


    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(IN_TANK) ? CORE : SHAPE;
    }

    protected com.example.skyblocks.compat.ItemInteractionResult useItemOn121(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return useBucket(level, pos, player, hand) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** A bucket (or any fluid container) in hand: fills or empties the whirlpool at {@code pos}. */
    public static boolean useBucket(Level level, BlockPos pos, Player player, InteractionHand hand) {
        return FluidUtil.getFluidHandler(player.getItemInHand(hand)).isPresent() && FluidUtil.interactWithFluidHandler(player, hand, level, pos, null);
    }

    protected net.minecraft.world.InteractionResult useWithoutItem121(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return open(level, pos, player);
    }

    /** Right-click (here or on its tank's frame): what it holds; sneaking, drain mode on / off. */
    public static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof WhirlpoolBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                be.toggleDrain();
                player.displayClientMessage(Component.translatable(be.drain() ? "message.skyblocks.whirlpool.drain_on" : "message.skyblocks.whirlpool.drain_off")
                        .withStyle(ChatFormatting.AQUA), true);
                level.playSound(null, pos, be.drain() ? SoundEvents.CONDUIT_ACTIVATE : SoundEvents.CONDUIT_DEACTIVATE, SoundSource.BLOCKS, 0.7F, 1.3F);
            }
        } else if (level.isClientSide) {
            com.example.skyblocks.client.WhirlpoolScreen.open(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (!now.is(this) && level.getBlockEntity(pos) instanceof WhirlpoolBlockEntity be) {
            be.unform();
        }
        super.onRemove(state, level, pos, now, moving);
    }

    // ---- what it holds goes with it ----

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof WhirlpoolBlockEntity w && be.getLevel() != null) {
            return List.of(w.asItem());
        }
        return List.of(new ItemStack(this));
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof WhirlpoolBlockEntity be && !be.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, be.asItem());
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WhirlpoolBlockEntity be ? be.signal() : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof WhirlpoolBlockEntity be) || be.isEmpty() || be.still()) {
            return;
        }
        if (state.getValue(IN_TANK) && be.tank() > 0) {
            // in its tank: spray off the funnel's lip, flung out over the pool (as WhirlpoolRenderer draws it)
            int r = (be.tank() - 1) / 2;
            float full = be.fullness(), width = 2 * r - 1;
            double lo = -(r - 1), surface = lo + width * (0.3 + 0.6 * full), rim = width * (0.25 + 0.17 * full);
            for (int i = 0; i < 1 + r / 2; i++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                level.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.5 + Math.cos(a) * rim, pos.getY() + surface + 0.05,
                        pos.getZ() + 0.5 + Math.sin(a) * rim, Math.cos(a) * 0.08, 0.1, Math.sin(a) * 0.08);
            }
            if (random.nextInt(60) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + surface, pos.getZ() + 0.5, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.5F, 0.7F, false);
            }
            return;
        }
        if (state.getValue(IN_TANK)) {
            return;
        }
        if (random.nextInt(4) == 0) { // (spray off its rim)
            double a = random.nextDouble() * Math.PI * 2.0, r = 0.2 + be.fullness() * 0.2;
            level.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 0.68, pos.getZ() + 0.5 + Math.sin(a) * r, 0, 0, 0);
        }
        if (random.nextInt(120) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.3F, 0.8F, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WhirlpoolBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.WHIRLPOOL.get(), WhirlpoolBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.whirlpool").withStyle(ChatFormatting.GRAY));
        CompoundTag tag = net.minecraft.world.item.BlockItem.getBlockEntityData(stack); // (1.20.1: the item's BlockEntityTag)
        if (tag != null) {
            long total = 0;
            int kinds = 0;
            for (Tag t : tag.getList("fluids", Tag.TAG_COMPOUND)) {
                total += ((CompoundTag) t).getLong("amount");
                kinds++;
            }
            if (kinds > 0) {
                tooltip.add(Component.translatable("tooltip.skyblocks.whirlpool.holds", buckets(total), kinds).withStyle(ChatFormatting.AQUA));
            }
        }
    }

    /** An amount of fluid for showing: "250 mB", "12.5 B", "4,096 B". */
    public static String buckets(long mb) {
        if (mb < 1000) {
            return mb + " mB";
        }
        double b = mb / 1000.0;
        return b < 100 ? String.format(java.util.Locale.ROOT, "%.1f B", b) : String.format(java.util.Locale.ROOT, "%,d B", (long) b);
    }

    /** 1.20.1: one use() - an item first (useItemOn121), then the empty-hand use (useWithoutItem121), as 1.21 does. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                                      net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                      net.minecraft.world.phys.BlockHitResult hit) {
        com.example.skyblocks.compat.ItemInteractionResult r = useItemOn121(player.getItemInHand(hand), state, level, pos, player, hand, hit);
        if (r != com.example.skyblocks.compat.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return r.result();
        }
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? useWithoutItem121(state, level, pos, player, hit) : net.minecraft.world.InteractionResult.PASS;
    }
}
