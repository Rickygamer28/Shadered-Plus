package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/**
 * A koi pond (client/KoiPondRenderer draws its water). A vanilla koi pond also keeps the blocks its rim and floor are
 * made of (chosen in its menu, client/VanillaPondScreen, for the whole pond at once; shown by client/PondModel).
 */
public class KoiPondBlockEntity extends BlockEntity {
    public static final ModelProperty<BlockState> RIM = new ModelProperty<>();
    public static final ModelProperty<BlockState> FLOOR = new ModelProperty<>();
    @Nullable private BlockState rim, floor;

    public KoiPondBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KOI_POND.get(), pos, state);
    }

    @Nullable public BlockState rim() { return rim; }
    @Nullable public BlockState floor() { return floor; }

    public void setMaterials(@Nullable BlockState rim, @Nullable BlockState floor) {
        if (this.rim == rim && this.floor == floor) {
            return;
        }
        this.rim = rim;
        this.floor = floor;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (rim != null) tag.put("rim", NbtUtils.writeBlockState(rim));
        if (floor != null) tag.put("floor", NbtUtils.writeBlockState(floor));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        BlockState r0 = rim, f0 = floor;
        var blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
        rim = tag.contains("rim") ? NbtUtils.readBlockState(blocks, tag.getCompound("rim")) : null;
        floor = tag.contains("floor") ? NbtUtils.readBlockState(blocks, tag.getCompound("floor")) : null;
        if (level != null && level.isClientSide && (r0 != rim || f0 != floor)) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE); // (redraw the chunk)
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag);
        tag.putBoolean("pond", true); // (never empty: the game skips an empty update, so going back to the defaults wouldn't show)
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {
        ModelData.Builder b = ModelData.builder();
        if (rim != null) b.with(RIM, rim);
        if (floor != null) b.with(FLOOR, floor);
        return b.end();
    }

    /** What its renderer draws (1.20.1: the block entity says, not the renderer). */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB; // (the whole pond, and visitors flying in over it)
    }
}
