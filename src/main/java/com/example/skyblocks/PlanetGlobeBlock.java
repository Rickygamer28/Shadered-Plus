package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Planet Globe: a little planet turning over a stand (client/PlanetGlobeRenderer, shaders/core/planet_globe).
 * Right-click it for the next kind of planet; sneak and right-click to give it rings or take them away.
 */
public class PlanetGlobeBlock extends Block implements EntityBlock {
    public static final String[] LOOKS = {"ocean", "gas_giant", "ice", "lava", "desert", "alien", "moon", "toxic"};
    private static final VoxelShape SHAPE = Shapes.or(box(4, 0, 4, 12, 1, 12), box(7, 1, 7, 9, 7, 9), box(3, 7, 3, 13, 18, 13));

    public PlanetGlobeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.or(box(4, 0, 4, 12, 1, 12), box(7, 1, 7, 9, 7, 9));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity globe)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                globe.rings = !globe.rings;
            } else {
                globe.look = (globe.look + 1) % LOOKS.length;
            }
            globe.changed();
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.8F + globe.look * 0.1F);
            player.displayClientMessage(Component.translatable("message.skyblocks.planet_globe",
                    Component.translatable("planet.skyblocks." + LOOKS[globe.look]),
                    Component.translatable(globe.rings ? "message.skyblocks.planet_globe.rings" : "message.skyblocks.planet_globe.no_rings")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** Which planet (LOOKS), with rings or not. */
    public static class Entity extends BlockEntity {
        public int look;
        public boolean rings;

        public Entity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.PLANET_GLOBE.get(), pos, state);
        }

        void changed() {
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("look", look);
            tag.putBoolean("rings", rings);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            look = Math.floorMod(tag.getInt("look"), LOOKS.length);
            rings = tag.getBoolean("rings");
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return saveWithoutMetadata(registries);
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
