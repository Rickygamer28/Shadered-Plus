package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A hot spring: a warm, milky blue pool over pale mineral stone, steaming (more on cold nights), the odd bubble rising.
 * Springs side by side join into one pool. Get in and sit a while: it heals you. The water is client/HotSpringRenderer.
 */
public class HotSpringBlock extends BaseEntityBlock {
    public static final MapCodec<HotSpringBlock> CODEC = simpleCodec(HotSpringBlock::new);
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    /** The water's surface, in blocks. */
    public static final float SURFACE = 13.0F / 16.0F;

    public HotSpringBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = Block.box(0, 0, 0, 16, 1, 16);
        if (!state.getValue(NORTH)) s = Shapes.or(s, Block.box(0, 0, 0, 16, 16, 2));
        if (!state.getValue(SOUTH)) s = Shapes.or(s, Block.box(0, 0, 14, 16, 16, 16));
        if (!state.getValue(WEST)) s = Shapes.or(s, Block.box(0, 0, 0, 2, 16, 16));
        if (!state.getValue(EAST)) s = Shapes.or(s, Block.box(14, 0, 0, 16, 16, 16));
        return s;
    }

    private BlockState connected(BlockState state, LevelAccessor level, BlockPos pos) {
        return state.setValue(NORTH, level.getBlockState(pos.north()).is(this)).setValue(SOUTH, level.getBlockState(pos.south()).is(this))
                .setValue(EAST, level.getBlockState(pos.east()).is(this)).setValue(WEST, level.getBlockState(pos.west()).is(this));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connected(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir.getAxis().isHorizontal() ? connected(state, level, pos) : state;
    }

    /** In the water: warmed through - healing slowly, any fire put out. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.getY() > pos.getY() + SURFACE) {
            return;
        }
        if (entity.isOnFire()) {
            entity.clearFire();
        }
        if (!level.isClientSide && entity instanceof LivingEntity living && (level.getGameTime() + entity.getId()) % 20 == 0) {
            MobEffectInstance now = living.getEffect(MobEffects.REGENERATION);
            if (now == null || now.getDuration() < 30) {
                living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 70, 0, true, false, true));
            }
        }
    }

    /** Steam (more at night and in cold places), a bubble now and then, a gentle burble. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        long day = level.getDayTime() % 24000L;
        boolean night = day > 12500L && day < 23500L;
        boolean cold = level.getBiome(pos).value().coldEnoughToSnow(pos);
        int puffs = 1 + (night ? 1 : 0) + (cold ? 2 : 0) + (level.isRaining() ? 1 : 0);
        for (int i = 0; i < puffs; i++) {
            if (random.nextInt(3) == 0) {
                level.addParticle(ModParticles.STEAM.get(), pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + SURFACE + 0.02,
                        pos.getZ() + 0.15 + random.nextDouble() * 0.7, (random.nextDouble() - 0.5) * 0.004, 0.012 + random.nextDouble() * 0.01,
                        (random.nextDouble() - 0.5) * 0.004);
            }
        }
        if (random.nextInt(9) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + SURFACE + 0.01,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.01, 0);
        }
        if (random.nextInt(260) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + SURFACE, pos.getZ() + 0.5, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    SoundSource.AMBIENT, 0.25F, 0.7F + random.nextFloat() * 0.2F, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleRenderedBlockEntity(ModBlockEntities.HOT_SPRING.get(), pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.hot_spring").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.hot_spring.heal").withStyle(ChatFormatting.GRAY));
    }
}
