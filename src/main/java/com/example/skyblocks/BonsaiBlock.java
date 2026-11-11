package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A bonsai pot. Plant a sapling in it (any mod's: BonsaiTrees works out its tree); the little tree grows slowly (a stage about every in-game day; bone meal hurries
 * it) and, once it's big enough, shears shape it: upright, round, windswept, cascading over the pot's edge, or cloud
 * pruned. The tree is drawn by client/BonsaiRenderer from the sapling's own wood and leaves.
 */
public class BonsaiBlock extends BaseEntityBlock implements BonemealableBlock {
    public static final MapCodec<BonsaiBlock> CODEC = simpleCodec(BonsaiBlock::new);

    public enum Kind implements StringRepresentable {
        NONE("none", null), OAK("oak", Items.OAK_SAPLING), SPRUCE("spruce", Items.SPRUCE_SAPLING), BIRCH("birch", Items.BIRCH_SAPLING),
        JUNGLE("jungle", Items.JUNGLE_SAPLING), ACACIA("acacia", Items.ACACIA_SAPLING), DARK_OAK("dark_oak", Items.DARK_OAK_SAPLING),
        MANGROVE("mangrove", Items.MANGROVE_PROPAGULE), CHERRY("cherry", Items.CHERRY_SAPLING), AZALEA("azalea", Items.AZALEA),
        /** Any other sapling (another mod's): which, and its tree, kept by the BonsaiBlockEntity. */
        OTHER("other", null);

        private final String name;
        @Nullable
        public final Item sapling;

        Kind(String name, @Nullable Item sapling) {
            this.name = name;
            this.sapling = sapling;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        @Nullable
        static Kind of(ItemStack stack) {
            if (stack.is(Items.FLOWERING_AZALEA)) {
                return AZALEA;
            }
            for (Kind k : values()) {
                if (k.sapling != null && stack.is(k.sapling)) {
                    return k;
                }
            }
            return null;
        }
    }

    public enum Style implements StringRepresentable {
        UPRIGHT("upright"), ROUND("round"), WINDSWEPT("windswept"), CASCADE("cascade"), CLOUDS("clouds"),
        /** The sapling's own tree, as it really grows, in miniature (BonsaiTrees.grow). */
        NATURAL("natural");

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final EnumProperty<Style> STYLE = EnumProperty.create("style", Style.class);
    /** 0 a sapling .. 4 full grown (flowering, for the cherry and azalea). */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 4);
    /** About one stage an in-game day: random ticks come about every 68 seconds. */
    private static final int GROW_CHANCE = 17;
    private static final VoxelShape SHAPE = Block.box(3, 0, 4, 13, 4, 12);

    public BonsaiBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, Kind.NONE).setValue(STYLE, Style.UPRIGHT)
                .setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND, STYLE, AGE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(KIND) != Kind.NONE;
    }

    /** Growing; grown, a fruit tree drops a fruit on the soil now and then (not while a couple are lying there already). */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(AGE) < 4) {
            if (random.nextInt(GROW_CHANCE) == 0) {
                level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_ALL);
            }
            return;
        }
        if (level.getBlockEntity(pos) instanceof BonsaiBlockEntity be && !be.fruit.isEmpty() && random.nextFloat() < be.fruitChance) {
            net.minecraft.world.phys.AABB round = new net.minecraft.world.phys.AABB(pos).inflate(1.0);
            if (level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, round, i -> i.getItem().is(be.fruit.getItem())).size() < 2) {
                net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                        pos.getY() + 0.75, pos.getZ() + 0.3 + random.nextDouble() * 0.4, be.fruit.copy());
                drop.setDeltaMovement(0, 0.02, 0);
                level.addFreshEntity(drop);
                level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 0.5F, 1.2F);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (state.getValue(KIND) == Kind.NONE && BonsaiTrees.plantable(stack)) {
            if (!level.isClientSide) {
                Kind kind = Kind.of(stack);
                level.setBlock(pos, state.setValue(KIND, kind == null ? Kind.OTHER : kind).setValue(AGE, 0).setValue(STYLE, Style.UPRIGHT), Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof BonsaiBlockEntity be) {
                    be.plant(stack);
                    if (be.natural != null) { // (its own tree, to start with)
                        level.setBlock(pos, level.getBlockState(pos).setValue(STYLE, Style.NATURAL), Block.UPDATE_ALL);
                    }
                }
                level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.1F);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.getItem() instanceof ShearsItem && state.getValue(KIND) != Kind.NONE) {
            if (state.getValue(AGE) < 2) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.skyblocks.bonsai.small").withStyle(ChatFormatting.GRAY), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                Style style = Style.values()[(state.getValue(STYLE).ordinal() + 1) % Style.values().length];
                if (style == Style.NATURAL && !(level.getBlockEntity(pos) instanceof BonsaiBlockEntity be && be.natural != null)) {
                    style = Style.UPRIGHT; // (a tree that can't be grown here: only the shaped ones)
                }
                level.setBlock(pos, state.setValue(STYLE, style), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8F, 1.3F);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, leavesAt(level, pos, state)),
                            pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.05);
                }
                player.displayClientMessage(Component.translatable("message.skyblocks.bonsai.style",
                        Component.translatable("message.skyblocks.bonsai.style." + style.getSerializedName())), true);
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** An empty hand: what's growing here - the sapling, and the wood and leaves its tree has. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(KIND) == Kind.NONE || !(level.getBlockEntity(pos) instanceof BonsaiBlockEntity be) || be.log == null || be.leaves == null) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.skyblocks.bonsai.tree", be.sapling().getHoverName(),
                    be.log.getBlock().getName(), be.leaves.getBlock().getName()).withStyle(ChatFormatting.GRAY), true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The leaves of the tree in this pot (its own, any mod's; by its kind while that's not known yet). */
    static BlockState leavesAt(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof BonsaiBlockEntity be && be.leaves != null ? be.leaves : leaves(state.getValue(KIND), false);
    }

    /** The leaves a kind of tree has (for falling leaves and the bits shears snip off). */
    public static BlockState leaves(Kind kind, boolean flowering) {
        return switch (kind) {
            case SPRUCE -> Blocks.SPRUCE_LEAVES.defaultBlockState();
            case BIRCH -> Blocks.BIRCH_LEAVES.defaultBlockState();
            case JUNGLE -> Blocks.JUNGLE_LEAVES.defaultBlockState();
            case ACACIA -> Blocks.ACACIA_LEAVES.defaultBlockState();
            case DARK_OAK -> Blocks.DARK_OAK_LEAVES.defaultBlockState();
            case MANGROVE -> Blocks.MANGROVE_LEAVES.defaultBlockState();
            case CHERRY -> Blocks.CHERRY_LEAVES.defaultBlockState();
            case AZALEA -> flowering ? Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState() : Blocks.AZALEA_LEAVES.defaultBlockState();
            default -> Blocks.OAK_LEAVES.defaultBlockState();
        };
    }

    /** The wood a kind of tree has. */
    public static BlockState log(Kind kind) {
        return switch (kind) {
            case SPRUCE -> Blocks.SPRUCE_LOG.defaultBlockState();
            case BIRCH -> Blocks.BIRCH_LOG.defaultBlockState();
            case JUNGLE -> Blocks.JUNGLE_LOG.defaultBlockState();
            case ACACIA -> Blocks.ACACIA_LOG.defaultBlockState();
            case DARK_OAK -> Blocks.DARK_OAK_LOG.defaultBlockState();
            case MANGROVE -> Blocks.MANGROVE_LOG.defaultBlockState();
            case CHERRY -> Blocks.CHERRY_LOG.defaultBlockState();
            default -> Blocks.OAK_LOG.defaultBlockState();
        };
    }

    /**
     * A grown tree drops a leaf now and then: its own particle if it has one (a cherry's petals), else a bit of its own
     * leaves drifting down.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(KIND) == Kind.NONE || state.getValue(AGE) < 3) {
            return;
        }
        double x = pos.getX() + 0.25 + random.nextDouble() * 0.5, y = pos.getY() + 0.75, z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;
        net.minecraft.resources.ResourceLocation own = level.getBlockEntity(pos) instanceof BonsaiBlockEntity be ? be.particle : null;
        if (own != null) {
            if (random.nextInt(12) == 0 && net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(own)
                    instanceof net.minecraft.core.particles.SimpleParticleType type) {
                level.addParticle(type, x, y, z, 0, 0, 0);
            }
        } else if (random.nextInt(45) == 0) {
            level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, leavesAt(level, pos, state)), x, y, z, 0, 0, 0);
        }
    }

    // ---- bone meal ----

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(KIND) != Kind.NONE && state.getValue(AGE) < 4;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return random.nextFloat() < 0.35F;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(AGE, Math.min(4, state.getValue(AGE) + 1)), Block.UPDATE_ALL);
    }

    /** The pot, and the sapling back if one's planted. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        Kind kind = state.getValue(KIND);
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof BonsaiBlockEntity be
                && !be.sapling().isEmpty()) {
            drops.add(be.sapling().copy());
        } else if (kind.sapling != null) {
            drops.add(new ItemStack(kind.sapling));
        }
        return drops;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BonsaiBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.bonsai").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.bonsai.grow").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.bonsai.shears").withStyle(ChatFormatting.GRAY));
    }
}
