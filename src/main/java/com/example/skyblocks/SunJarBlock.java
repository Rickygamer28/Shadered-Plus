package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A sun jar (the moon jar's other half: MoonJarBlock). Empty, hold it up to the sun by day (right-click, looking straight
 * at it) and the sun's caught in it: gone from the sky (SunJar), blazing in the jar instead (client/SunJarRenderer).
 * Placed with the sun in it, it lights everything round it and melts the snow and ice near by; right-click it to let
 * the sun go, back up into the sky. SUN: it's in there.
 */
public class SunJarBlock extends BaseEntityBlock {
    public static final MapCodec<SunJarBlock> CODEC = simpleCodec(SunJarBlock::new);
    public static final BooleanProperty SUN = BooleanProperty.create("sun");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(4.5, 0, 4.5, 11.5, 10, 11.5), Block.box(5.5, 10, 5.5, 10.5, 13, 10.5));

    public SunJarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SUN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SUN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Whether this jar item has the sun in it. */
    public static boolean full(ItemStack stack) {
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        return stack.getItem() instanceof Jar && props != null && Boolean.TRUE.equals(props.get(SUN));
    }

    public static ItemStack fullJar() {
        ItemStack stack = new ItemStack(ModItems.SUN_JAR.get());
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(SUN, true));
        return stack;
    }

    /** Opened: the sun let go, up into the sky. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(SUN)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level instanceof ServerLevel server) {
            level.setBlock(pos, state.setValue(SUN, false), Block.UPDATE_ALL);
            SunJar.set(server.getServer(), false);
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 0.8F);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 0.8F);
            for (int i = 0; i < 40; i++) {
                server.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.6 + i * 0.6, pos.getZ() + 0.5, 2, 0.15, 0.2, 0.15, 0.01);
            }
            player.displayClientMessage(Component.translatable("message.skyblocks.sun_jar.released"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(SUN);
    }

    /** With the sun in it: now and then the snow and ice near by melt. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        for (int i = 0; i < 6; i++) {
            BlockPos at = pos.offset(random.nextInt(9) - 4, random.nextInt(5) - 2, random.nextInt(9) - 4);
            BlockState there = level.getBlockState(at);
            if (there.is(net.minecraft.world.level.block.Blocks.SNOW) || there.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
                level.removeBlock(at, false);
            } else if (there.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)) {
                level.removeBlock(at, false);
            } else if (there.is(net.minecraft.world.level.block.Blocks.ICE) || there.is(net.minecraft.world.level.block.Blocks.FROSTED_ICE)) {
                level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleRenderedBlockEntity(ModBlockEntities.SUN_JAR.get(), pos, state);
    }

    /** The jar as an item: held up to the sun, it catches it. */
    public static class Jar extends BlockItem {
        public Jar(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            return full(stack) ? Component.translatable("item.skyblocks.sun_jar.full") : super.getName(stack);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return full(stack) || super.isFoil(stack);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (full(stack)) {
                return InteractionResultHolder.pass(stack);
            }
            if (level.isClientSide || !(level instanceof ServerLevel server)) {
                return InteractionResultHolder.success(stack);
            }
            String problem = null;
            // where the sun is: it turns overhead east to west (the moon opposite it)
            float angle = level.getTimeOfDay(1.0F) * Mth.TWO_PI;
            Vec3 moon = new Vec3(-Mth.sin(angle), Mth.cos(angle), 0); // (the sun - named as in the moon jar)
            if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasFixedTime()) {
                problem = "no_sky";
            } else if (SunJar.caught(server.getServer())) {
                problem = "taken";
            } else if (moon.y < 0.05) {
                problem = "down";
            } else if (player.getLookAngle().dot(moon) < Math.cos(Math.toRadians(9))) {
                problem = "look";
            } else if (!level.canSeeSky(player.blockPosition().above())) {
                problem = "roof";
            }
            if (problem != null) {
                player.displayClientMessage(Component.translatable("message.skyblocks.sun_jar." + problem), true);
                return InteractionResultHolder.fail(stack);
            }
            SunJar.set(server.getServer(), true);
            ItemStack jar = fullJar();
            stack.consume(1, player);
            if (stack.isEmpty()) {
                player.setItemInHand(hand, jar);
            } else if (!player.getInventory().add(jar)) {
                player.drop(jar, false);
            }
            // (the sun pours down into the jar)
            Vec3 eye = player.getEyePosition(), at = eye.add(player.getLookAngle().scale(0.7));
            for (int i = 0; i < 48; i++) {
                Vec3 p = at.add(moon.scale(i * 0.9));
                server.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.9F);
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6F, 1.4F);
            player.displayClientMessage(Component.translatable("message.skyblocks.sun_jar.caught"), true);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable(full(stack) ? "tooltip.skyblocks.sun_jar.full" : "tooltip.skyblocks.sun_jar").withStyle(ChatFormatting.GRAY));
        }
    }
}
