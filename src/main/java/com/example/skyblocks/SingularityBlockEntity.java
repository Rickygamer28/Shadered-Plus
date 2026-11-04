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
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
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
 * handler: a slot per kind, and an empty one to put new kinds in); the more it holds, the bigger it grows. Items in its
 * destroy filter (marked kinds, or item tags) are crushed to nothing instead of kept. Vacuum mode: it pulls in dropped
 * items round it (off: it only takes what's put in).
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
    public static final int MAX_KINDS = 27648;
    /** How far dropped items are pulled in from in vacuum mode (further in a containment chamber: + its size). */
    public static final double VACUUM_PULL = 8.0;
    /** The containment chambers it can be in the middle of: cubes this many blocks across, framed along their edges. */
    public static final int[] CHAMBERS = {9, 7, 5};
    /** At most this many tags in the destroy filter. */
    public static final int MAX_TAGS = 32;
    /** How full it looks / what a comparator reads: this many items fills it (log scale). */
    private static final double FULL = 1.0e6;

    private final List<Entry> entries = new ArrayList<>();
    private final Set<Item> destroy = new HashSet<>();
    private final List<TagKey<Item>> destroyTags = new ArrayList<>();
    private boolean vacuum;
    /** The containment chamber round it: how many blocks across (0: none). */
    private int chamber;
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

    public List<TagKey<Item>> destroyTags() {
        return destroyTags;
    }

    /** Whether this is crushed to nothing when put in (a marked kind, or in a marked tag). */
    public boolean destroys(ItemStack stack) {
        if (destroy.contains(stack.getItem())) {
            return true;
        }
        for (TagKey<Item> tag : destroyTags) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** Adds a tag ("c:cobblestones", "#minecraft:logs") to the destroy filter, or takes it out if it's there. */
    public void toggleDestroyTag(String text) {
        String t = text.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        ResourceLocation id = ResourceLocation.tryParse(t);
        if (id == null) {
            return;
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
        if (!destroyTags.remove(tag)) {
            if (destroyTags.size() >= MAX_TAGS) {
                return;
            }
            destroyTags.add(tag);
        }
        changed(false);
    }

    /** How many kinds it can hold: 1024; in a containment chamber 5, 7, 9 across, three times as many each: 3072, 9216, 27648. */
    public int maxKinds() {
        return chamber >= 9 ? 27648 : chamber >= 7 ? 9216 : chamber >= 5 ? 3072 : 1024;
    }

    public int chamber() {
        return chamber;
    }

    /** How far the black hole reaches (blocks from its middle): in a chamber, growing to fill it as it fills. */
    public float reach() {
        return chamber > 0 ? chamber * 0.5F * (0.45F + 0.55F * fullness()) : 0.38F + 0.92F * fullness();
    }

    // ---- the containment chamber ----

    /** Whether there's a chamber this size round it: frames along every edge of the cube, nothing solid inside. */
    private boolean chamberFits(int size) {
        int r = (size - 1) / 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int rims = (Math.abs(dx) == r ? 1 : 0) + (Math.abs(dy) == r ? 1 : 0) + (Math.abs(dz) == r ? 1 : 0);
                    if (rims == 1 || (dx == 0 && dy == 0 && dz == 0)) {
                        continue; // (its sides: open, glass, anything)
                    }
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (rims >= 2) {
                        if (!(s.getBlock() instanceof ContainmentFrameBlock)) {
                            return false;
                        }
                        if (level.getBlockEntity(p) instanceof ContainmentFrameBlockEntity f && f.master() != null && !f.master().equals(worldPosition)
                                && level.getBlockEntity(f.master()) instanceof SingularityBlockEntity other && other.chamber > 0) {
                            return false; // (another one's)
                        }
                    } else if (!s.getCollisionShape(level, p).isEmpty()) {
                        return false; // (inside: room for it)
                    }
                }
            }
        }
        return true;
    }

    /** Its chamber's frames: lit and linked to it (or dark again). */
    private void setFrames(int size, boolean formed) {
        int r = (size - 1) / 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int rims = (Math.abs(dx) == r ? 1 : 0) + (Math.abs(dy) == r ? 1 : 0) + (Math.abs(dz) == r ? 1 : 0);
                    if (rims < 2) {
                        continue;
                    }
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (!(s.getBlock() instanceof ContainmentFrameBlock) || !(level.getBlockEntity(p) instanceof ContainmentFrameBlockEntity f)) {
                        continue;
                    }
                    if (formed) {
                        f.setMaster(worldPosition);
                    } else if (f.master() == null || f.master().equals(worldPosition)) {
                        f.setMaster(null);
                    } else {
                        continue;
                    }
                    if (s.getValue(ContainmentFrameBlock.FORMED) != formed) {
                        level.setBlock(p, s.setValue(ContainmentFrameBlock.FORMED, formed), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** Looks for its chamber (the one it's in still there, else the biggest that fits). */
    private void checkChamber() {
        int found = 0;
        if (chamber > 0 && chamberFits(chamber)) {
            found = chamber;
        } else {
            for (int size : CHAMBERS) {
                if (chamberFits(size)) {
                    found = size;
                    break;
                }
            }
        }
        if (found != chamber) {
            if (chamber > 0) {
                setFrames(chamber, false);
            }
            boolean grew = found > chamber;
            chamber = found;
            if (chamber > 0) {
                setFrames(chamber, true);
            }
            // (in its chamber it loses its own frame: only the black hole)
            BlockState s = level.getBlockState(worldPosition);
            if (s.getBlock() instanceof SingularityBlock && s.getValue(SingularityBlock.IN_CHAMBER) != (chamber > 0)) {
                level.setBlock(worldPosition, s.setValue(SingularityBlock.IN_CHAMBER, chamber > 0), Block.UPDATE_CLIENTS);
            }
            level.playSound(null, worldPosition, grew ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, grew ? 0.6F : 0.8F);
            changed(true);
        }
    }

    /** It's gone: its chamber's frames dark again. */
    public void unform() {
        if (chamber > 0 && level != null && !level.isClientSide) {
            setFrames(chamber, false);
            chamber = 0;
        }
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

    /** Its kinds by item (thousands of kinds: not each looked through for every item put in); rebuilt when kinds come or go. */
    private final java.util.Map<Item, List<Entry>> index = new java.util.HashMap<>();
    private boolean indexDirty = true;

    @Nullable
    private Entry find(ItemStack stack) {
        if (indexDirty) {
            index.clear();
            for (Entry e : entries) {
                index.computeIfAbsent(e.item.getItem(), k -> new ArrayList<>(1)).add(e);
            }
            indexDirty = false;
        }
        List<Entry> same = index.get(stack.getItem());
        if (same != null) {
            for (Entry e : same) {
                if (ItemStack.isSameItemSameComponents(e.item, stack)) {
                    return e;
                }
            }
        }
        return null;
    }

    /** Whether it would take any of this (destroyed items: always). */
    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (destroys(stack) || find(stack) != null || entries.size() < maxKinds());
    }

    /** Puts in as much of {@code stack} as it can (all of it, unless there are too many kinds); returns what's left. */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (destroys(stack)) {
            if (!simulate) {
                changed(false);
            }
            return ItemStack.EMPTY; // (crushed to nothing)
        }
        Entry e = find(stack);
        if (e == null && entries.size() >= maxKinds()) {
            return stack;
        }
        if (!simulate) {
            if (e == null) {
                entries.add(new Entry(stack, stack.getCount()));
                indexDirty = true;
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
                indexDirty = true;
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

    // ---- pulling in what's dropped near it (vacuum mode only) ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, SingularityBlockEntity be) {
        if ((level.getGameTime() + pos.hashCode()) % 20 == 0) {
            be.checkChamber();
        }
        if (!be.vacuum || !(level instanceof ServerLevel server) || (level.getGameTime() + pos.hashCode()) % 2 != 0) {
            return;
        }
        double reach = VACUUM_PULL + be.chamber;
        double swallow = 0.55 + (be.chamber > 0 ? be.reach() * 0.25 : 0.0);
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
            if (d < swallow) { // over the edge: in
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
            // (slowly: a lazy spiral, faster only right at the end)
            Vec3 in = to.normalize();
            Vec3 round = new Vec3(-in.z, 0.0, in.x).normalize();
            double pull = 0.025 + 0.12 / Math.max(d * d, 0.6);
            Vec3 v = in.scale(Math.min(pull, 0.22)).add(round.scale(Math.min(0.06, 0.12 / Math.max(d, 1.0))));
            // (held up against its fall while it's pulled: no gravity switched off, nothing left floating when it stops)
            item.setDeltaMovement(item.getDeltaMovement().scale(0.5).add(v.scale(0.6)).add(0.0, d < reach * 0.9 ? 0.04 : 0.0, 0.0));
            if (item.isNoGravity()) {
                item.setNoGravity(false); // (left floating by an older version)
            }
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
        ListTag tags = new ListTag();
        for (TagKey<Item> t : destroyTags) {
            tags.add(StringTag.valueOf(t.location().toString()));
        }
        tag.put("destroyTags", tags);
        tag.putBoolean("vacuum", vacuum);
        tag.putInt("colour", colour);
        tag.putInt("chamber", chamber);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) {
            entries.clear();
            indexDirty = true;
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
        destroyTags.clear();
        for (Tag t : tag.getList("destroyTags", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null && destroyTags.size() < MAX_TAGS) {
                destroyTags.add(TagKey.create(Registries.ITEM, id));
            }
        }
        vacuum = tag.getBoolean("vacuum");
        chamber = tag.getInt("chamber");
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
        for (int i = 0; i < Math.min(12, top.size()); i++) {
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
        if (!entries.isEmpty() || !destroy.isEmpty() || !destroyTags.isEmpty() || vacuum || colour != 0x9A6BFF) {
            CompoundTag tag = saveWithoutMetadata(registries);
            tag.remove("chamber"); // (its chamber stays where it was)
            net.minecraft.world.item.BlockItem.setBlockEntityData(stack, ModBlockEntities.SINGULARITY.get(), tag);
        }
        return stack;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
