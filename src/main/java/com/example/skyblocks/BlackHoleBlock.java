package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A black hole: the world behind it seen bent round a black shadow (client/BlackHoleRenderer, shaders/core/black_hole).
 * The small one floats over a pedestal in a swirl of glowing dust (dyeable); the big one is three blocks across, with
 * a hot accretion disk. Purely decorative.
 */
public class BlackHoleBlock extends BaseEntityBlock {
    public static final MapCodec<BlackHoleBlock> CODEC = simpleCodec(p -> new BlackHoleBlock(p, false));
    private static final VoxelShape PEDESTAL = Block.box(4.0, 0.0, 4.0, 12.0, 5.0, 12.0);
    private static final VoxelShape CORE = Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);
    private final boolean big;

    public BlackHoleBlock(Properties properties, boolean big) {
        super(properties);
        this.big = big;
    }

    public boolean big() {
        return big;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return big ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return big ? CORE : PEDESTAL;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + (big ? 0.5 : 0.62), cz = pos.getZ() + 0.5;
        double reach = big ? 1.6 : 0.45;
        for (int i = 0; i < (big ? 3 : 1); i++) {
            if (random.nextInt(big ? 1 : 3) == 0) { // (dust falling in: portal particles drift from where they start to the middle)
                double a = random.nextDouble() * Math.PI * 2.0, r = reach * (0.7 + random.nextDouble() * 0.6);
                level.addParticle(ParticleTypes.PORTAL, cx, cy, cz, Math.cos(a) * r, (random.nextDouble() - 0.5) * r * 0.4, Math.sin(a) * r);
            }
        }
        if (random.nextInt(big ? 90 : 160) == 0) {
            level.playLocalSound(cx, cy, cz, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, big ? 0.6F : 0.3F, big ? 0.5F : 0.7F, false);
        }
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GlowColourBlockEntity.blackHole(pos, state, big);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(big ? "tooltip.skyblocks.big_black_hole" : "tooltip.skyblocks.black_hole").withStyle(ChatFormatting.GRAY));
    }
}
