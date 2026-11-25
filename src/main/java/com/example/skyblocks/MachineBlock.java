package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A machine's heart - the particle accelerator's collider, the weather machine's base, the star forge's core - built
 * round with its frames, it works (MachineBlockEntity and its kinds). Right-click (here or on a frame): its screen.
 */
public class MachineBlock extends BaseEntityBlock {
    /** Formed: built round with its frames (the star forge's core then hides in its star). */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(0, 14, 0, 16, 16, 16),
            Block.box(0, 2, 0, 2, 14, 2), Block.box(14, 2, 0, 16, 14, 2), Block.box(0, 2, 14, 2, 14, 16), Block.box(14, 2, 14, 16, 14, 16),
            Block.box(4, 4, 4, 12, 12, 12));
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);

    private final MachineFrameBlock.Kind kind;

    public MachineBlock(MachineFrameBlock.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    public MachineFrameBlock.Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new MachineBlock(kind, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind == MachineFrameBlock.Kind.FORGE && state.getValue(FORMED) ? CORE : SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (kind == MachineFrameBlock.Kind.ACCELERATOR && !state.getValue(FORMED) && hasFrames(player)) {
            return build(level, pos, player);
        }
        return open(level, pos, player);
    }

    /** Holding accelerator frames, not built yet: it builds its ring (not a frame put down against it). */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                                  net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (kind == MachineFrameBlock.Kind.ACCELERATOR && !state.getValue(FORMED) && stack.is(ModItems.ACCELERATOR_FRAME.get())) {
            build(level, pos, player);
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static boolean hasFrames(Player player) {
        return player.getAbilities().instabuild || player.getInventory().countItem(ModItems.ACCELERATOR_FRAME.get()) > 0;
    }

    /**
     * Builds the particle accelerator's ring round it from the frames you have: the biggest ring you've enough for
     * (sneaking: the smallest) - in creative, the large one (sneaking: the small one). Frames already there count;
     * anything else in the ring's way stops it (and says where).
     */
    private static InteractionResult build(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean creative = player.getAbilities().instabuild, small = player.isShiftKeyDown();
        int have = creative ? Integer.MAX_VALUE : player.getInventory().countItem(ModItems.ACCELERATOR_FRAME.get());
        net.minecraft.world.level.block.state.BlockState frame = ModBlocks.ACCELERATOR_FRAME.get().defaultBlockState();
        java.util.List<BlockPos> chosen = null;
        int chosenSize = 0, need = 0;
        BlockPos blocked = null;
        for (int i = 0; i < 3; i++) {
            int size = small ? 1 + i : 3 - i;
            int r = ParticleAcceleratorBlockEntity.RADIUS[size];
            java.util.List<BlockPos> missing = new java.util.ArrayList<>();
            BlockPos inWay = null;
            for (int dx = -r - 1; dx <= r + 1 && inWay == null; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    if (!ParticleAcceleratorBlockEntity.onRing(dx, dz, r)) {
                        continue;
                    }
                    BlockPos p = pos.offset(dx, 0, dz);
                    BlockState there = level.getBlockState(p);
                    if (there.is(ModBlocks.ACCELERATOR_FRAME.get())) {
                        continue;
                    }
                    if (!there.canBeReplaced()) {
                        inWay = p;
                        break;
                    }
                    missing.add(p);
                }
            }
            if (inWay != null) {
                blocked = blocked == null ? inWay : blocked;
                continue;
            }
            if (missing.size() <= have) {
                chosen = missing;
                chosenSize = size;
                break;
            }
            need = need == 0 ? missing.size() - have : Math.min(need, missing.size() - have); // (how many more it needs)
        }
        if (chosen == null) {
            if (blocked != null && need == 0) {
                player.displayClientMessage(Component.translatable("message.skyblocks.accelerator.blocked", blocked.getX(), blocked.getY(), blocked.getZ())
                        .withStyle(ChatFormatting.RED), true);
            } else {
                player.displayClientMessage(Component.translatable("message.skyblocks.accelerator.need", need, have).withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.CONSUME;
        }
        for (BlockPos p : chosen) {
            level.setBlock(p, frame, Block.UPDATE_ALL);
        }
        if (!creative) {
            int left = chosen.size();
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(ModItems.ACCELERATOR_FRAME.get())) {
                    int n = Math.min(left, s.getCount());
                    s.shrink(n);
                    left -= n;
                }
            }
        }
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.NETHERITE_BLOCK_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.9F);
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity be) {
            be.relinkSoon();
        }
        player.displayClientMessage(Component.translatable("message.skyblocks.accelerator.built",
                Component.translatable("screen.skyblocks.machine.size." + new String[] {"none", "small", "medium", "large"}[chosenSize]), chosen.size()), true);
        return InteractionResult.CONSUME;
    }

    /** Its screen (from here, or from one of its frames). */
    public static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            BlockPos at = pos.immutable();
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new MachineMenu(id, inv, at), be.getBlockState().getBlock().getName()),
                    buf -> buf.writeBlockPos(at));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (!now.is(this) && level.getBlockEntity(pos) instanceof MachineBlockEntity be) {
            be.unform();
            for (int i = 0; i < be.items.getSlots(); i++) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.items.getStackInSlot(i));
            }
            be.dropHeld();
        }
        super.onRemove(state, level, pos, now, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (kind) {
            case ACCELERATOR -> new ParticleAcceleratorBlockEntity(pos, state);
            case WEATHER -> new WeatherMachineBlockEntity(pos, state);
            case FORGE -> new StarForgeBlockEntity(pos, state);
        };
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (l, p, s, be) -> {
            if (be instanceof MachineBlockEntity m) {
                m.serverTick();
            }
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks." + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).getPath())
                .withStyle(ChatFormatting.GRAY));
    }
}
