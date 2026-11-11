package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The sapling planted in a bonsai pot (any mod's), and the tree it makes (BonsaiTrees) - worked out on the server,
 * sent to clients to draw: its log, leaves, flowering leaves, falling particle, fruit.
 */
public class BonsaiBlockEntity extends BlockEntity {
    private ItemStack sapling = ItemStack.EMPTY;
    @Nullable
    public BlockState log, leaves, flowering;
    @Nullable
    public ResourceLocation particle;
    public ItemStack fruit = ItemStack.EMPTY;
    public float fruitChance;

    public BonsaiBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BONSAI.get(), pos, state);
    }

    public ItemStack sapling() {
        return sapling;
    }

    public void plant(ItemStack stack) {
        sapling = stack.copyWithCount(1);
        resolve();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Its tree, worked out again (data files may have changed); a pot from before this kept a kind, not a sapling. */
    private void resolve() {
        if (level == null) {
            return;
        }
        if (sapling.isEmpty()) {
            BonsaiBlock.Kind kind = getBlockState().getValue(BonsaiBlock.KIND);
            if (kind.sapling == null) {
                log = leaves = flowering = null;
                fruit = ItemStack.EMPTY;
                return;
            }
            sapling = new ItemStack(kind.sapling);
        }
        BonsaiTrees.Tree t = BonsaiTrees.resolve(level, sapling);
        log = t.log();
        leaves = t.leaves();
        flowering = t.flowering();
        particle = t.particle();
        fruit = t.fruit();
        fruitChance = t.fruitChance();
    }

    public void clear() {
        sapling = ItemStack.EMPTY;
        log = leaves = flowering = null;
        particle = null;
        fruit = ItemStack.EMPTY;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && getBlockState().getValue(BonsaiBlock.KIND) != BonsaiBlock.Kind.NONE) {
            resolve();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!sapling.isEmpty()) {
            tag.put("sapling", sapling.save(registries));
        }
        if (log != null) tag.put("log", NbtUtils.writeBlockState(log));
        if (leaves != null) tag.put("leaves", NbtUtils.writeBlockState(leaves));
        if (flowering != null) tag.put("flowering", NbtUtils.writeBlockState(flowering));
        if (particle != null) tag.putString("particle", particle.toString());
        if (!fruit.isEmpty()) {
            tag.put("fruit", fruit.save(registries));
            tag.putFloat("fruitChance", fruitChance);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sapling = tag.contains("sapling") ? ItemStack.parseOptional(registries, tag.getCompound("sapling")) : ItemStack.EMPTY;
        var blocks = BuiltInRegistries.BLOCK.asLookup();
        log = tag.contains("log") ? NbtUtils.readBlockState(blocks, tag.getCompound("log")) : null;
        leaves = tag.contains("leaves") ? NbtUtils.readBlockState(blocks, tag.getCompound("leaves")) : null;
        flowering = tag.contains("flowering") ? NbtUtils.readBlockState(blocks, tag.getCompound("flowering")) : null;
        particle = tag.contains("particle") ? ResourceLocation.tryParse(tag.getString("particle")) : null;
        fruit = tag.contains("fruit") ? ItemStack.parseOptional(registries, tag.getCompound("fruit")) : ItemStack.EMPTY;
        fruitChance = tag.getFloat("fruitChance");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
