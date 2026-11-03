package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The singularity: a black hole that keeps what falls into it. Any number of any item (each kind one entry with its
 * count), items dropped near it pulled in round its disk, hoppers and pipes put things in and take them out (an item
 * handler: a slot per kind, and an empty one to put new kinds in); the more it holds, the bigger it grows. Items marked
 * to be destroyed are crushed to nothing instead of kept. Vacuum mode: it pulls in dropped items from much further.
 */
public class SingularityBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    /** One kind of item kept, and how many. */
    public static final class Entry {
        public final ItemStack item; // (count 1: what it is)
        public long count;

        Entry(ItemStack item, long count) {
            this.item = item.copyWithCount(1);
            this.count = count;
        }
    }

    /** At most this many kinds (plenty: a big storage block, not a whole storage system). */
    public static final int MAX_KINDS = 1024;
    /** How far dropped items are pulled in from: always, and in vacuum mode. */
    public static final double PULL = 2.5, VACUUM_PULL = 8.0;
    /** How full it looks / what a comparator reads: this many items fills it (log scale). */
    private static final double FULL = 1.0e6;

    private final List<Entry> entries = new ArrayList<>();
    private final Set<Item> destroy = new HashSet<>();
    private boolean vacuum;
    private int colour = 0x9A6BFF;
    private long total;
    /** Changes since the menus last saw it (server), for sending them the new contents. */
    private int version;
    /** (Client: the kinds it holds most of, to show orbiting it; and the total.) */
    private final List<ItemStack> shown = new ArrayList<>();
    private int lastSignal = -1;
    /** (Client: how full it looks now, easing; when that was worked out.) */
    public float shownGrow;
    public long shownGrowAt;

    public SingularityBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SINGULARITY.get(), pos, state);
    }

    public List<Entry> entries() {
        return entries;
    }

    public Set<Item> destroyed() {
        return destroy;
    }

    public boolean vacuum() {
        return vacuum;
    }

    public int colour() {
        return colour;
    }

    public long total() {
        return total;
    }

    public int version() {
        return version;
    }

    public List<ItemStack> shown() {
        return shown;
    }

    /** How full it is, 0..1 (on a log scale: it keeps growing as you fill it, without ever really being full). */
    public float fullness() {
        return (float) Math.min(1.0, Math.log10(1.0 + total) / Math.log10(1.0 + FULL));
    }

    public int signal() {
        return total <= 0 ? 0 : Math.max(1, (int) Math.floor(fullness() * 15.0));
    }

    // ---- putting in and taking out ----

    @Nullable
    private Entry find(ItemStack stack) {
        for (Entry e : entries) {
            if (ItemStack.isSameItemSameComponents(e.item, stack)) {
                return e;
            }
        }
        return null;
    }

    /** Whether it would take any of this (destroyed items: always). */
    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (destroy.contains(stack.getItem()) || find(stack) != null || entries.size() < MAX_KINDS);
    }

    /** Puts in as much of {@code stack} as it can (all of it, unless there are too many kinds); returns what's left. */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (destroy.contains(stack.getItem())) {
            if (!simulate) {
                changed(false);
            }
            return ItemStack.EMPTY; // (crushed to nothing)
        }
        Entry e = find(stack);
        if (e == null && entries.size() >= MAX_KINDS) {
            return stack;
        }
        if (!simulate) {
            if (e == null) {
                entries.add(new Entry(stack, stack.getCount()));
            } else {
                e.count += stack.getCount();
            }
            total += stack.getCount();
            changed(true);
        }
        return ItemStack.EMPTY;
    }

    /** Takes out up to {@code amount} of the kind {@code like} (its own stack size at most). */
    public ItemStack extract(ItemStack like, int amount, boolean simulate) {
        Entry e = find(like);
        if (e == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int n = (int) Math.min(Math.min(amount, e.item.getMaxStackSize()), e.count);
        ItemStack out = e.item.copyWithCount(n);
        if (!simulate) {
            e.count -= n;
            total -= n;
            if (e.count <= 0) {
                entries.remove(e);
            }
            changed(true);
        }
        return out;
    }

    public void toggleDestroy(Item item) {
        if (!destroy.remove(item)) {
            destroy.add(item);
        }
        changed(false);
    }

    public void toggleVacuum() {
        vacuum = !vacuum;
        changed(true);
    }

    public void setColour(int colour) {
        this.colour = colour & 0xFFFFFF;
        changed(true);
    }

    private void changed(boolean looks) {
        version++;
        setChanged();
        if (level != null && !level.isClientSide) {
            if (looks) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
            int s = signal();
            if (s != lastSignal) {
                lastSignal = s;
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    // ---- pulling in what's dropped near it ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, SingularityBlockEntity be) {
        if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.hashCode()) % 2 != 0) {
            return;
        }
        double reach = be.vacuum ? VACUUM_PULL : PULL;
        Vec3 c = Vec3.atCenterOf(pos);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(c, c).inflate(reach),
                e -> e.isAlive() && !e.getItem().isEmpty());
        boolean ate = false;
        for (ItemEntity item : items) {
            Vec3 to = c.subtract(item.position().add(0.0, item.getBbHeight() * 0.5, 0.0));
            double d = to.length();
            if (d > reach || !be.accepts(item.getItem())) {
                continue;
            }
            if (d < 0.55) { // over the edge: in
                ItemStack left = be.insert(item.getItem().copy(), false);
                if (left.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(left);
                }
                ate = true;
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 6, 0.15, 0.15, 0.15, 0.02);
                continue;
            }
            // spiralling in: round the hole and inwards, faster the nearer it gets
            Vec3 in = to.normalize();
            Vec3 round = new Vec3(-in.z, 0.0, in.x).normalize();
            double pull = 0.06 + 0.5 / Math.max(d * d, 0.6);
            Vec3 v = in.scale(Math.min(pull, 0.6)).add(round.scale(Math.min(0.25, 0.35 / Math.max(d, 0.8))));
            item.setDeltaMovement(item.getDeltaMovement().scale(0.5).add(v.scale(0.6)));
            item.setNoGravity(d < reach * 0.9);
            item.hurtMarked = true;
        }
        if (ate && level.getRandom().nextInt(3) == 0) {
            level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.25F, 0.4F + level.getRandom().nextFloat() * 0.3F);
        }
    }

    // ---- the item handler (hoppers, pipes) ----

    public final IItemHandler handler = new IItemHandler() {
        @Override
        public int getSlots() {
            return entries.size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= entries.size()) {
                return ItemStack.EMPTY;
            }
            Entry e = entries.get(slot);
            return e.item.copyWithCount((int) Math.min(e.count, Integer.MAX_VALUE));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return insert(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= entries.size()) {
                return ItemStack.EMPTY;
            }
            return extract(entries.get(slot).item, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(stack);
        }
    };

    // ---- saving, and what the client sees ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag t = new CompoundTag();
            t.put("item", e.item.save(registries));
            t.putLong("count", e.count);
            list.add(t);
        }
        tag.put("items", list);
        saveLooks(tag);
    }

    private void saveLooks(CompoundTag tag) {
        ListTag gone = new ListTag();
        for (Item item : destroy) {
            gone.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString()));
        }
        tag.put("destroy", gone);
        tag.putBoolean("vacuum", vacuum);
        tag.putInt("colour", colour);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) {
            entries.clear();
            total = 0;
            for (Tag t : tag.getList("items", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                ItemStack item = ItemStack.parseOptional(registries, c.getCompound("item"));
                long count = c.getLong("count");
                if (!item.isEmpty() && count > 0) {
                    entries.add(new Entry(item, count));
                    total += count;
                }
            }
        }
        if (tag.contains("total")) { // (the client: only the total and the biggest few)
            total = tag.getLong("total");
            shown.clear();
            for (Tag t : tag.getList("shown", Tag.TAG_COMPOUND)) {
                ItemStack s = ItemStack.parseOptional(registries, (CompoundTag) t);
                if (!s.isEmpty()) {
                    shown.add(s);
                }
            }
        }
        destroy.clear();
        for (Tag t : tag.getList("destroy", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(destroy::add);
            }
        }
        vacuum = tag.getBoolean("vacuum");
        if (tag.contains("colour")) {
            colour = tag.getInt("colour") & 0xFFFFFF;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveLooks(tag);
        tag.putLong("total", total);
        // (the kinds it holds most of: they're seen going round it)
        List<Entry> top = new ArrayList<>(entries);
        top.sort((a, b) -> Long.compare(b.count, a.count));
        ListTag list = new ListTag();
        for (int i = 0; i < Math.min(6, top.size()); i++) {
            list.add(top.get(i).item.save(registries));
        }
        tag.put("shown", list);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Itself as an item, keeping everything it holds (broken, it doesn't spill out: it goes with it, like a shulker box). */
    public ItemStack asItem(HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(ModItems.SINGULARITY.get());
        if (!entries.isEmpty() || !destroy.isEmpty() || vacuum || colour != 0x9A6BFF) {
            net.minecraft.world.item.BlockItem.setBlockEntityData(stack, ModBlockEntities.SINGULARITY.get(), saveWithoutMetadata(registries));
        }
        return stack;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
