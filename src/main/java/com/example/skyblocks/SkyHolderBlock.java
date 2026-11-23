package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.function.BiFunction;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A block showing a sky (SkyHolderBlockEntity): the Sky Rift (a crack in the air with the sky through it,
 * client/SkyRiftRenderer) and the Sky Beacon (a beam of the sky up into the heavens, client/SkyBeaconRenderer).
 * (The Sky Fountain keeps its sky the same way: FountainBlock.)
 * Right-click it with any sky block to change its sky (the sky block isn't used up).
 */
public class SkyHolderBlock extends BaseEntityBlock {
    public enum Kind { RIFT, BEACON }

    private final Kind kind;
    private final boolean rift;
    private final BiFunction<BlockPos, BlockState, BlockEntity> make;

    public SkyHolderBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        this.rift = kind == Kind.RIFT;
        this.make = switch (kind) {
            case RIFT -> SkyHolderBlockEntity::rift;
            case BEACON -> SkyHolderBlockEntity::beacon;
        };
    }

    public Kind kind() {
        return kind;
    }

    public static SkyHolderBlock rift(Properties properties) {
        return new SkyHolderBlock(properties, Kind.RIFT);
    }

    public static SkyHolderBlock beacon(Properties properties) {
        return new SkyHolderBlock(properties, Kind.BEACON);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new SkyHolderBlock(p, kind));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return rift ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    private static final VoxelShape RIFT = box(4, 0, 4, 12, 16, 12);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return rift ? RIFT : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return rift ? Shapes.empty() : Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return make.apply(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks." + name()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        SkyblockType sky = SkyLiquidBucketRecipe.skyOf(stack);
        if (sky == null || !(level.getBlockEntity(pos) instanceof SkyHolderBlockEntity holder)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            holder.setSky(sky);
            level.playSound(null, pos, switch (kind) {
                case RIFT -> SoundEvents.AMETHYST_BLOCK_RESONATE;
                case BEACON -> SoundEvents.BEACON_POWER_SELECT;
            }, SoundSource.BLOCKS, 1.0F, 1.1F);
            player.displayClientMessage(Component.translatable("message.skyblocks." + name() + ".sky", stack.getHoverName()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private String name() {
        return switch (kind) {
            case RIFT -> "sky_rift";
            case BEACON -> "sky_beacon";
        };
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (rift) {
            for (int i = 0; i < 3; i++) {
                double y = pos.getY() + 0.1 + random.nextDouble() * 1.4;
                level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, y,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.0, 0.0);
            }
        } else if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.1,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.03, 0.0);
        }
    }
}
