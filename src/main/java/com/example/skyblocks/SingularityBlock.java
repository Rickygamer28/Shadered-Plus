package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The singularity: a black hole held in a containment frame that stores items (SingularityBlockEntity). Right-click:
 * what it holds (take things out, mark things to be destroyed). Sneak + right-click: vacuum mode on / off. A dye:
 * its disk's colour. A comparator reads how full it is. Broken, everything in it goes with the item.
 */
public class SingularityBlock extends BaseEntityBlock {
    public static final MapCodec<SingularityBlock> CODEC = simpleCodec(SingularityBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(0, 14, 0, 16, 16, 16),
            Block.box(0, 2, 0, 2, 14, 2), Block.box(14, 2, 0, 16, 14, 2), Block.box(0, 2, 14, 2, 14, 16), Block.box(14, 2, 14, 16, 14, 16),
            Block.box(4, 4, 4, 12, 12, 12));

    public SingularityBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return open(state, level, pos, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (!now.is(this) && level.getBlockEntity(pos) instanceof SingularityBlockEntity be) {
            be.unform(); // (its chamber's frames go dark)
        }
        super.onRemove(state, level, pos, now, moving);
    }

    /** Right-click (here or on its chamber's frame): what it holds; sneaking, vacuum mode on / off. */
    public InteractionResult open(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof SingularityBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                be.toggleVacuum();
                player.displayClientMessage(Component.translatable(be.vacuum() ? "message.skyblocks.singularity.vacuum_on" : "message.skyblocks.singularity.vacuum_off")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                level.playSound(null, pos, be.vacuum() ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.7F, 1.4F);
            } else if (player instanceof ServerPlayer sp) {
                sp.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SingularityMenu(id, inventory, pos),
                        Component.translatable("block.skyblocks.singularity")), buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof DyeItem dye && level.getBlockEntity(pos) instanceof SingularityBlockEntity be) {
            if (!level.isClientSide) {
                be.setColour(dye.getDyeColor().getTextureDiffuseColor());
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // ---- what it holds goes with it ----

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof SingularityBlockEntity s && be.getLevel() != null) {
            return List.of(s.asItem(be.getLevel().registryAccess()));
        }
        return List.of(new ItemStack(this));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // (creative: no drops - but not losing what it held either)
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof SingularityBlockEntity be && !be.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, be.asItem(level.registryAccess()));
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SingularityBlockEntity be ? be.signal() : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof SingularityBlockEntity be)) {
            return;
        }
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        double reach = 0.5 + be.fullness() * 1.2;
        int n = be.vacuum() ? 3 : 1;
        for (int i = 0; i < n; i++) {
            if (random.nextInt(be.vacuum() ? 1 : 3) == 0) { // (dust falling in, from far round in vacuum mode)
                double a = random.nextDouble() * Math.PI * 2.0, r = (be.vacuum() ? 3.0 : reach) * (0.7 + random.nextDouble() * 0.6);
                level.addParticle(ParticleTypes.PORTAL, cx, cy, cz, Math.cos(a) * r, (random.nextDouble() - 0.5) * r * 0.4, Math.sin(a) * r);
            }
        }
        if (random.nextInt(140) == 0) {
            level.playLocalSound(cx, cy, cz, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3F + be.fullness() * 0.3F, 0.5F, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SingularityBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.SINGULARITY.get(), SingularityBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.singularity").withStyle(ChatFormatting.GRAY));
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            long total = 0;
            int kinds = 0;
            for (Tag t : tag.getList("items", Tag.TAG_COMPOUND)) {
                total += ((CompoundTag) t).getLong("count");
                kinds++;
            }
            if (kinds > 0) {
                tooltip.add(Component.translatable("tooltip.skyblocks.singularity.holds", SingularityMenu.amount(total), kinds)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }
}
