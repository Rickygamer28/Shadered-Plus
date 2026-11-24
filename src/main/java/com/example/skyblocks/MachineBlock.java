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
        return open(level, pos, player);
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
