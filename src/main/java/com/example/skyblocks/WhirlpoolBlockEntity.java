package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The whirlpool singularity: a vortex that keeps fluids - each kind its own band swirling down it, the funnel wider and
 * deeper the fuller it is. Buckets and any mod's pipes fill and empty it (a fluid handler). Built round with a
 * containment tank (tank frames along the edges of a 5, 7 or 9 cube, glass on its sides), it holds far more, and the tank
 * fills up with what it holds, in layers by how heavy each fluid is. Drain mode: it drains the fluid source blocks round
 * it. Fluids marked void (or in a marked tag) are taken in and gone.
 */
public class WhirlpoolBlockEntity extends BlockEntity {
    /** One kind of fluid kept, and how much (mB). */
    public static final class Entry {
        public final FluidStack fluid; // (amount 1: what it is)
        public long amount;

        Entry(FluidStack fluid, long amount) {
            this.fluid = fluid.copyWithAmount(1);
            this.amount = amount;
        }
    }

    public static final int[] TANKS = {9, 7, 5};
    public static final int MAX_TAGS = 16;
    /** How far round it source blocks are drained in drain mode (further in a tank: + its size). */
    public static final int DRAIN_RANGE = 8;

    private final List<Entry> entries = new ArrayList<>();
    private final Set<Fluid> voided = new HashSet<>();
    private final List<TagKey<Fluid>> voidTags = new ArrayList<>();
    private boolean drain;
    /** Its containment tank: how many blocks across (0: none). */
    private int tank;
    private long total;
    private int lastSignal = -1;
    /** (Drain mode, server: source blocks found, waiting to be drained; which layer of its range is looked at next.) */
    private final java.util.ArrayDeque<BlockPos> sources = new java.util.ArrayDeque<>();
    private int scanLayer;
    /** (Client: when what it holds last changed - its surface ripples; how full it looks now, easing.) */
    public long rippleAt;
    public float shownFull = -1.0F;

    public WhirlpoolBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WHIRLPOOL.get(), pos, state);
    }

    public List<Entry> entries() {
        return entries;
    }

    public Set<Fluid> voided() {
        return voided;
    }

    public List<TagKey<Fluid>> voidTags() {
        return voidTags;
    }

    public boolean drain() {
        return drain;
    }

    public int tank() {
        return tank;
    }

    public long total() {
        return total;
    }

    /** How much it holds in all (mB): 256 buckets; in a tank 5, 7, 9 across, 4096, 16384, 65536. */
    public long capacity() {
        return tank >= 9 ? 65_536_000L : tank >= 7 ? 16_384_000L : tank >= 5 ? 4_096_000L : 256_000L;
    }

    /** How many kinds of fluid: 8; in a tank 16, 32, 64. */
    public int maxKinds() {
        return tank >= 9 ? 64 : tank >= 7 ? 32 : tank >= 5 ? 16 : 8;
    }

    public float fullness() {
        return (float) Math.min(1.0, (double) total / capacity());
    }

    public int signal() {
        return total <= 0 ? 0 : Math.max(1, (int) Math.floor(fullness() * 15.0));
    }

    /** Whether this fluid is taken in and gone (marked, or in a marked tag). */
    public boolean voids(FluidStack stack) {
        if (voided.contains(stack.getFluid())) {
            return true;
        }
        for (TagKey<Fluid> tag : voidTags) {
            if (stack.getFluid().is(tag)) {
                return true;
            }
        }
        return false;
    }

    public void toggleVoid(Fluid fluid) {
        if (!voided.remove(fluid)) {
            voided.add(fluid);
        }
        changed(true);
    }

    /** Adds a fluid tag ("c:water", "#minecraft:lava") to the void filter, or takes it out. */
    public void toggleVoidTag(String text) {
        String t = text.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        ResourceLocation id = ResourceLocation.tryParse(t);
        if (id == null) {
            return;
        }
        TagKey<Fluid> tag = TagKey.create(Registries.FLUID, id);
        if (!voidTags.remove(tag)) {
            if (voidTags.size() >= MAX_TAGS) {
                return;
            }
            voidTags.add(tag);
        }
        changed(true);
    }

    public void toggleDrain() {
        drain = !drain;
        sources.clear();
        changed(true);
    }

    // ---- filling and emptying ----

    @Nullable
    private Entry find(FluidStack stack) {
        for (Entry e : entries) {
            if (FluidStack.isSameFluidSameComponents(e.fluid, stack)) {
                return e;
            }
        }
        return null;
    }

    /** Puts in as much of this as it can; how much it took. */
    public int fill(FluidStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (voids(stack)) {
            return stack.getAmount(); // (gone)
        }
        Entry e = find(stack);
        if (e == null && entries.size() >= maxKinds()) {
            return 0;
        }
        int n = (int) Math.min(stack.getAmount(), capacity() - total);
        if (n <= 0) {
            return 0;
        }
        if (!simulate) {
            if (e == null) {
                entries.add(new Entry(stack, n));
            } else {
                e.amount += n;
            }
            total += n;
            changed(true);
        }
        return n;
    }

    /** Takes out up to {@code amount} of this fluid. */
    public FluidStack drain(FluidStack like, int amount, boolean simulate) {
        Entry e = find(like);
        if (e == null || amount <= 0) {
            return FluidStack.EMPTY;
        }
        int n = (int) Math.min(amount, e.amount);
        FluidStack out = e.fluid.copyWithAmount(n);
        if (!simulate) {
            e.amount -= n;
            total -= n;
            if (e.amount <= 0) {
                entries.remove(e);
            }
            changed(true);
        }
        return out;
    }

    public final IFluidHandler handler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return entries.size() + 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank < 0 || tank >= entries.size()) {
                return FluidStack.EMPTY;
            }
            Entry e = entries.get(tank);
            return e.fluid.copyWithAmount((int) Math.min(e.amount, Integer.MAX_VALUE));
        }

        @Override
        public int getTankCapacity(int tank) {
            return (int) Math.min(capacity(), Integer.MAX_VALUE);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return WhirlpoolBlockEntity.this.fill(resource, action.simulate());
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return WhirlpoolBlockEntity.this.drain(resource, resource.getAmount(), action.simulate());
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return entries.isEmpty() ? FluidStack.EMPTY : WhirlpoolBlockEntity.this.drain(entries.get(0).fluid, maxDrain, action.simulate());
        }
    };

    private void changed(boolean looks) {
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

    // ---- the containment tank ----

    /** Whether a tank this size is round it: tank frames along every edge, glass on its sides, nothing inside. */
    private boolean tankFits(int size) {
        int r = (size - 1) / 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int rims = (Math.abs(dx) == r ? 1 : 0) + (Math.abs(dy) == r ? 1 : 0) + (Math.abs(dz) == r ? 1 : 0);
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (rims >= 2) {
                        if (!(s.getBlock() instanceof TankFrameBlock)) {
                            return false;
                        }
                        if (level.getBlockEntity(p) instanceof TankFrameBlockEntity f && f.master() != null && !f.master().equals(worldPosition)
                                && level.getBlockEntity(f.master()) instanceof WhirlpoolBlockEntity other && other.tank > 0) {
                            return false; // (another one's)
                        }
                    } else if (rims == 1) {
                        if (!s.is(Tags.Blocks.GLASS_BLOCKS) && !(s.getBlock() instanceof TankFrameBlock)) {
                            return false; // (its sides: glass)
                        }
                    } else if (!s.isAir()) {
                        return false; // (inside: room for what it holds)
                    }
                }
            }
        }
        return true;
    }

    private void setFrames(int size, boolean formed) {
        int r = (size - 1) / 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int rims = (Math.abs(dx) == r ? 1 : 0) + (Math.abs(dy) == r ? 1 : 0) + (Math.abs(dz) == r ? 1 : 0);
                    if (rims < 1) {
                        continue;
                    }
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (!(s.getBlock() instanceof TankFrameBlock) || !(level.getBlockEntity(p) instanceof TankFrameBlockEntity f)) {
                        continue;
                    }
                    if (formed) {
                        f.setMaster(worldPosition);
                    } else if (f.master() == null || f.master().equals(worldPosition)) {
                        f.setMaster(null);
                    } else {
                        continue;
                    }
                    if (s.getValue(TankFrameBlock.FORMED) != formed) {
                        level.setBlock(p, s.setValue(TankFrameBlock.FORMED, formed), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private void checkTank() {
        int found = 0;
        if (tank > 0 && tankFits(tank)) {
            found = tank;
        } else {
            for (int size : TANKS) {
                if (tankFits(size)) {
                    found = size;
                    break;
                }
            }
        }
        if (found != tank) {
            if (tank > 0) {
                setFrames(tank, false);
            }
            boolean grew = found > tank;
            tank = found;
            if (tank > 0) {
                setFrames(tank, true);
            }
            BlockState s = level.getBlockState(worldPosition);
            if (s.getBlock() instanceof WhirlpoolBlock && s.getValue(WhirlpoolBlock.IN_TANK) != (tank > 0)) {
                level.setBlock(worldPosition, s.setValue(WhirlpoolBlock.IN_TANK, tank > 0), Block.UPDATE_CLIENTS);
            }
            // (out of its tank with more than it can hold alone: it keeps it all, but takes no more till it's below)
            level.playSound(null, worldPosition, grew ? SoundEvents.CONDUIT_ACTIVATE : SoundEvents.CONDUIT_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
            changed(true);
        } else if (tank > 0) {
            setFrames(tank, true); // (frames swapped in since: linked too)
        }
    }

    public void unform() {
        if (tank > 0 && level != null && !level.isClientSide) {
            setFrames(tank, false);
            tank = 0;
        }
    }

    // ---- drain mode: the source blocks round it drained, in streams ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, WhirlpoolBlockEntity be) {
        long time = level.getGameTime() + pos.hashCode();
        if (time % 20 == 0) {
            be.checkTank();
        }
        if (!be.drain || !(level instanceof ServerLevel server)) {
            return;
        }
        int range = DRAIN_RANGE + be.tank;
        if (time % 2 == 0) {
            be.scanLayer(server, range);
        }
        if (time % 4 == 0) {
            for (int i = 0; i < 2 && !be.sources.isEmpty(); i++) {
                be.drainSource(server, be.sources.poll());
            }
        }
    }

    /** Looks through one layer of its range for source blocks (the whole range, a layer a turn). */
    private void scanLayer(ServerLevel server, int range) {
        if (sources.size() > 64) {
            return;
        }
        int r = tank > 0 ? (tank - 1) / 2 : 0;
        scanLayer = Math.floorMod(scanLayer, range * 2 + 1);
        int dy = scanLayer - range;
        scanLayer++;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (Math.abs(dx) <= r && Math.abs(dy) <= r && Math.abs(dz) <= r) {
                    continue; // (its own tank)
                }
                p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                if (!server.isLoaded(p)) {
                    continue;
                }
                BlockState s = server.getBlockState(p);
                if (s.getBlock() instanceof LiquidBlock && s.getFluidState().isSource()) {
                    sources.add(p.immutable());
                }
            }
        }
    }

    private void drainSource(ServerLevel server, BlockPos p) {
        BlockState s = server.getBlockState(p);
        FluidState fs = s.getFluidState();
        if (!(s.getBlock() instanceof LiquidBlock) || !fs.isSource()) {
            return;
        }
        FluidStack stack = new FluidStack(fs.getType(), 1000);
        if (fill(stack, true) < 1000) {
            return; // (no room)
        }
        server.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        fill(stack, false);
        // a stream from where it was to the vortex
        Vec3 from = Vec3.atCenterOf(p), to = Vec3.atCenterOf(worldPosition);
        BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, fs.createLegacyBlock());
        for (int k = 0; k <= 10; k++) {
            double t = k / 10.0;
            Vec3 at = from.lerp(to, t).add(0.0, Math.sin(t * Math.PI) * from.distanceTo(to) * 0.25, 0.0);
            server.sendParticles(particle, at.x, at.y, at.z, 2, 0.08, 0.08, 0.08, 0.0);
        }
        if (server.getRandom().nextInt(3) == 0) {
            server.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.4F, 0.8F + server.getRandom().nextFloat() * 0.3F);
        }
    }

    // ---- saving, and what the client sees ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        save(tag, registries);
    }

    private void save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag t = new CompoundTag();
            t.put("fluid", e.fluid.save(registries));
            t.putLong("amount", e.amount);
            list.add(t);
        }
        tag.put("fluids", list);
        ListTag gone = new ListTag();
        for (Fluid f : voided) {
            gone.add(StringTag.valueOf(BuiltInRegistries.FLUID.getKey(f).toString()));
        }
        tag.put("void", gone);
        ListTag tags = new ListTag();
        for (TagKey<Fluid> t : voidTags) {
            tags.add(StringTag.valueOf(t.location().toString()));
        }
        tag.put("voidTags", tags);
        tag.putBoolean("drain", drain);
        tag.putInt("tank", tank);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        long before = total;
        entries.clear();
        total = 0;
        for (Tag t : tag.getList("fluids", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            FluidStack f = FluidStack.parseOptional(registries, c.getCompound("fluid"));
            long amount = c.getLong("amount");
            if (!f.isEmpty() && amount > 0) {
                entries.add(new Entry(f, amount));
                total += amount;
            }
        }
        if (total != before) {
            rippleAt = System.currentTimeMillis();
        }
        voided.clear();
        for (Tag t : tag.getList("void", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) {
                BuiltInRegistries.FLUID.getOptional(id).ifPresent(voided::add);
            }
        }
        voidTags.clear();
        for (Tag t : tag.getList("voidTags", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null && voidTags.size() < MAX_TAGS) {
                voidTags.add(TagKey.create(Registries.FLUID, id));
            }
        }
        drain = tag.getBoolean("drain");
        tank = tag.getInt("tank");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        save(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Itself as an item, keeping what it holds. */
    public ItemStack asItem(HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(ModItems.WHIRLPOOL.get());
        if (!entries.isEmpty() || !voided.isEmpty() || !voidTags.isEmpty() || drain) {
            CompoundTag tag = saveWithoutMetadata(registries);
            tag.remove("tank");
            net.minecraft.world.item.BlockItem.setBlockEntityData(stack, ModBlockEntities.WHIRLPOOL.get(), tag);
        }
        return stack;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
