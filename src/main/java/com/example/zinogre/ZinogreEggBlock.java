package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A Zinogre egg (dropped by the Alpha bosses). It has to be placed on the ground; when lightning strikes it (a storm, a
 * Channeling trident, a lightning rod next to it...) it hatches into a Zinogre (Stygian from the Stygian egg), tamed
 * by whoever placed the egg. See ZinogreEvents for the lightning.
 */
public class ZinogreEggBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 13.0, 13.0);

    public final boolean stygian;

    public ZinogreEggBlock(boolean stygian, Properties properties) {
        super(properties);
        this.stygian = stygian;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Only on solid ground. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighbor,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == net.minecraft.core.Direction.DOWN && !canSurvive(state, level, pos)
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof ZinogreEggBlockEntity egg) {
            egg.owner = placer.getUUID();
            egg.setChanged();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.zinogre.zinogre_egg.hint").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZinogreEggBlockEntity(pos, state);
    }

    /** Lightning struck it: the egg cracks open and a Zinogre comes out, tamed by whoever placed the egg. */
    static void hatch(ServerLevel level, BlockPos pos, ZinogreEggBlock block) {
        UUID owner = level.getBlockEntity(pos) instanceof ZinogreEggBlockEntity egg ? egg.owner : null;
        level.removeBlock(pos, false);
        ZinogreEntity zinogre = (block.stygian ? ZinogreMod.STYGIAN_ZINOGRE : ZinogreMod.ZINOGRE).get().create(level);
        if (zinogre == null) {
            return;
        }
        zinogre.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        if (owner != null) {
            zinogre.setTame(true, true);
            zinogre.setOwnerUUID(owner);
        }
        zinogre.hatched();
        level.addFreshEntity(zinogre);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 60, 0.6, 0.6, 0.6, 0.2);
        level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 20, 0.4, 0.3, 0.4, 0.05);
        level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.BLOCKS, 2.0F, 0.6F);
        if (owner != null && level.getPlayerByUUID(owner) instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(block.stygian ? "message.zinogre.hatched_stygian" : "message.zinogre.hatched"), true);
        }
    }
}
