package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A block whose faces show one of Draconic Evolution's animated shaders (adapted, with credit: see the tooltip and
 * assets/skyblocks/shaders/core/draconic_*), drawn by client/DraconicEffectRenderer. Right-click to change its colour
 * or strength (not the chaos block's).
 */
public class DraconicEffectBlock extends Block {
    public static final MapCodec<DraconicEffectBlock> CODEC = simpleCodec(p -> new DraconicEffectBlock(p, Kind.CHAOS));
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 2);

    /** The effects: the shader each one draws with, and per tier the colour (rgb) and strength (a) given to it. */
    public enum Kind {
        CHAOS("draconic_chaos", false, new float[][] {{1, 1, 1, 1}, {1, 1, 1, 1}, {1, 1, 1, 1}}),
        REACTOR_CORE("draconic_reactor", true, new float[][] {{1, 1, 1, 0.23F}, {1, 1, 1, 0.77F}, {1, 1, 1, 1.0F}}),
        REACTOR_SHIELD("draconic_reactor_shield", true, new float[][] {{1, 1, 1, 0.4F}, {1, 1, 1, 0.7F}, {1, 1, 1, 1.0F}}),
        ENERGY_CRYSTAL("draconic_crystal", true, new float[][] {{0.0F, 0.2F, 0.3F, 1}, {0.47F, 0.0F, 0.58F, 1}, {1.0F, 0.4F, 0.1F, 1}}),
        ENERGY_SHIELD("draconic_shield", true, new float[][] {{0.2F, 0.8F, 1.0F, 1}, {0.7F, 0.3F, 1.0F, 1}, {1.0F, 0.35F, 0.15F, 1}});

        public final String shader;
        public final boolean tiers;
        private final float[][] colours;

        Kind(String shader, boolean tiers, float[][] colours) {
            this.shader = shader;
            this.tiers = tiers;
            this.colours = colours;
        }

        public float[] colour(int tier) {
            return colours[Math.floorMod(tier, colours.length)];
        }
    }

    public final Kind kind;

    public DraconicEffectBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(TIER, kind.tiers ? 1 : 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIER);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!kind.tiers) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            int tier = Math.floorMod(state.getValue(TIER) + (player.isShiftKeyDown() ? -1 : 1), 3);
            level.setBlock(pos, state.setValue(TIER, tier), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 0.8F + tier * 0.3F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (kind.tiers) {
            tooltip.add(Component.translatable("tooltip.skyblocks.draconic_tiers").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.skyblocks.draconic_credit").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
