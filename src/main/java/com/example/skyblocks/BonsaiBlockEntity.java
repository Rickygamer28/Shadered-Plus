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
    /** The sapling's real tree (BonsaiTrees.grow): its blocks, from the ground up; null if it couldn't be grown. */
    @Nullable
    public java.util.Map<BlockPos, BlockState> natural;
    /** Changes whenever the real tree does (clients: build its picture again). */
    public int naturalVersion;

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
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            natural = BonsaiTrees.grow(server, sapling, worldPosition.asLong() * 31 + BuiltInRegistries.ITEM.getKey(sapling.getItem()).hashCode());
            naturalVersion++;
        }
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
        natural = null;
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
        if (natural != null) { // (its blocks: a palette, and each one's place (x, y, z packed) and palette number)
            java.util.List<BlockState> palette = new java.util.ArrayList<>();
            java.util.Map<BlockState, Integer> index = new java.util.HashMap<>();
            int[] at = new int[natural.size()], which = new int[natural.size()];
            int i = 0;
            for (var e : natural.entrySet()) {
                BlockPos p = e.getKey();
                at[i] = (p.getX() + 512 & 1023) | (p.getY() - BonsaiTrees.GROUND + 512 & 1023) << 10 | (p.getZ() + 512 & 1023) << 20;
                which[i] = index.computeIfAbsent(e.getValue(), st -> {
                    palette.add(st);
                    return palette.size() - 1;
                });
                i++;
            }
            net.minecraft.nbt.ListTag pal = new net.minecraft.nbt.ListTag();
            palette.forEach(st -> pal.add(NbtUtils.writeBlockState(st)));
            tag.put("treePalette", pal);
            tag.putIntArray("treeAt", at);
            tag.putIntArray("treeBlock", which);
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
        if (tag.contains("treeAt")) {
            net.minecraft.nbt.ListTag pal = tag.getList("treePalette", net.minecraft.nbt.Tag.TAG_COMPOUND);
            BlockState[] palette = new BlockState[pal.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = NbtUtils.readBlockState(blocks, pal.getCompound(i));
            }
            int[] at = tag.getIntArray("treeAt"), which = tag.getIntArray("treeBlock");
            java.util.Map<BlockPos, BlockState> m = new java.util.HashMap<>();
            for (int i = 0; i < Math.min(at.length, which.length); i++) {
                if (which[i] >= 0 && which[i] < palette.length) {
                    m.put(new BlockPos((at[i] & 1023) - 512, ((at[i] >> 10) & 1023) - 512 + BonsaiTrees.GROUND, ((at[i] >> 20) & 1023) - 512), palette[which[i]]);
                }
            }
            natural = m;
        } else {
            natural = null;
        }
        naturalVersion++;
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
