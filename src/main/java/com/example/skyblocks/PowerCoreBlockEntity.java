package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * The power core: Forge Energy kept in something you can watch fill - a pocket star swelling from an ember to a white-hot
 * sun, a thundercloud crackling more and more, a plasma globe sprouting tendrils, an atom gaining shells of electrons
 * (its skin: client/PowerCoreRenderer). Cables and machines put energy in and take it out (an energy handler, on it and
 * on its frames). Built round with a core chamber (core frames along the edges of a 5, 7 or 9 cube, open or glass sides,
 * nothing inside) it holds far more, and what it holds grows to fill the chamber. Push mode: it pushes its energy out
 * into whatever's next to it (and next to its chamber's frames).
 */
public class PowerCoreBlockEntity extends BlockEntity {
    public static final int[] CHAMBERS = {9, 7, 5};
    public static final String[] SKINS = {"star", "storm", "plasma", "atom"};

    private long energy;
    private int skin;
    private boolean push;
    /** Its core chamber: how many blocks across (0: none). */
    private int chamber;
    private int lastSignal = -1;
    private boolean relink, syncPending;
    /** (Server, push mode: where to push - beside it, and beside its chamber's frames - worked out when it's formed.) */
    private final List<BlockPos> outlets = new ArrayList<>();
    private final List<Direction> outletSides = new ArrayList<>();
    private boolean outletsKnown;
    /** Energy in and out: counted over a second, then shown as FE per tick (its screen). */
    private long inCount, outCount;
    private long rateIn, rateOut;
    /** (Client: how full it looks, easing towards what it is.) */
    public float shownFull = -1.0F;

    public PowerCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_CORE.get(), pos, state);
    }

    public long energy() {
        return energy;
    }

    public int skin() {
        return skin;
    }

    public boolean push() {
        return push;
    }

    public int chamber() {
        return chamber;
    }

    /** How much it holds (FE): a billion alone; in a chamber 5, 7, 9 across, 64 billion, 512 billion, 4 trillion. */
    public long capacity() {
        return chamber >= 9 ? 4_000_000_000_000L : chamber >= 7 ? 512_000_000_000L : chamber >= 5 ? 64_000_000_000L : 1_000_000_000L;
    }

    public float fullness() {
        return (float) Math.min(1.0, (double) energy / capacity());
    }

    public int signal() {
        return energy <= 0 ? 0 : Math.max(1, (int) Math.floor(fullness() * 15.0));
    }

    public long rateIn() {
        return rateIn;
    }

    public long rateOut() {
        return rateOut;
    }

    public void nextSkin() {
        setSkin(skin + 1);
    }

    public void setSkin(int skin) {
        this.skin = Math.floorMod(skin, SKINS.length);
        changed(true);
    }

    public void togglePush() {
        push = !push;
        changed(true);
    }

    public void relinkSoon() {
        relink = true;
    }

    // ---- energy in and out ----

    public long insert(long amount, boolean simulate) {
        long n = Math.max(0, Math.min(amount, capacity() - energy)); // (above its limit, out of a chamber: takes no more)
        if (n > 0 && !simulate) {
            energy += n;
            inCount += n;
            changed(false);
        }
        return n;
    }

    public long extract(long amount, boolean simulate) {
        long n = Math.max(0, Math.min(amount, energy));
        if (n > 0 && !simulate) {
            energy -= n;
            outCount += n;
            changed(false);
        }
        return n;
    }

    public final IEnergyStorage handler = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return (int) insert(toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return (int) extract(toExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(energy, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(capacity(), Integer.MAX_VALUE);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    private void changed(boolean looks) {
        setChanged();
        if (level != null && !level.isClientSide) {
            syncPending = true; // (sent at most every few ticks: how full it is shows too)
            int s = signal();
            if (s != lastSignal) {
                lastSignal = s;
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    // ---- the core chamber ----

    /** Whether a chamber this size is round it: core frames along every edge, its sides open or glass, nothing inside. */
    private boolean chamberFits(int size) {
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
                        if (!(s.getBlock() instanceof CoreFrameBlock)) {
                            return false;
                        }
                        if (level.getBlockEntity(p) instanceof CoreFrameBlockEntity f && f.master() != null && !f.master().equals(worldPosition)
                                && level.getBlockEntity(f.master()) instanceof PowerCoreBlockEntity other && other.chamber > 0) {
                            return false; // (another one's)
                        }
                    } else if (rims == 1) {
                        if (!s.isAir() && !s.is(Tags.Blocks.GLASS_BLOCKS) && !s.is(ModBlocks.TANK_GLASS.get()) && !(s.getBlock() instanceof CoreFrameBlock)) {
                            return false; // (its sides: open, or glass)
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
                    if (!(s.getBlock() instanceof CoreFrameBlock) || !(level.getBlockEntity(p) instanceof CoreFrameBlockEntity f)) {
                        continue;
                    }
                    if (formed) {
                        f.setMaster(worldPosition);
                    } else if (f.master() == null || f.master().equals(worldPosition)) {
                        f.setMaster(null);
                    } else {
                        continue;
                    }
                    // (formed, it's drawn as its part of the whole: a corner, an edge, a face)
                    BlockState next = FramePart.shape(s.setValue(CoreFrameBlock.FORMED, formed), dx, dy, dz, r, formed);
                    if (next != s) {
                        level.setBlock(p, next, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

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
            BlockState s = level.getBlockState(worldPosition);
            if (s.getBlock() instanceof PowerCoreBlock && s.getValue(PowerCoreBlock.IN_CHAMBER) != (chamber > 0)) {
                level.setBlock(worldPosition, s.setValue(PowerCoreBlock.IN_CHAMBER, chamber > 0), Block.UPDATE_CLIENTS);
            }
            level.playSound(null, worldPosition, grew ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
            outletsKnown = false;
            changed(true);
        } else if (chamber > 0 && relink) {
            relink = false;
            setFrames(chamber, true); // (frames put in since: linked too)
            outletsKnown = false;
        }
    }

    public void unform() {
        if (chamber > 0 && level != null && !level.isClientSide) {
            setFrames(chamber, false);
            chamber = 0;
        }
    }

    // ---- push mode ----

    /** Where it pushes to: beside it, and beside each of its chamber's frames - its edges, and any filling its sides (outside the chamber). */
    private void findOutlets() {
        outlets.clear();
        outletSides.clear();
        outletsKnown = true;
        int r = chamber > 0 ? (chamber - 1) / 2 : 0;
        if (r == 0) {
            for (Direction d : Direction.values()) {
                outlets.add(worldPosition.relative(d));
                outletSides.add(d.getOpposite());
            }
            return;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int rims = (Math.abs(dx) == r ? 1 : 0) + (Math.abs(dy) == r ? 1 : 0) + (Math.abs(dz) == r ? 1 : 0);
                    if (rims < 1) {
                        continue;
                    }
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    // (its edges; and frames filling its sides - beside those too, not only its edges)
                    if (rims == 1 && !(level.getBlockState(p).getBlock() instanceof CoreFrameBlock)) {
                        continue;
                    }
                    for (Direction d : Direction.values()) {
                        BlockPos n = p.relative(d);
                        int ox = n.getX() - worldPosition.getX(), oy = n.getY() - worldPosition.getY(), oz = n.getZ() - worldPosition.getZ();
                        if (Math.abs(ox) <= r && Math.abs(oy) <= r && Math.abs(oz) <= r) {
                            continue; // (the chamber itself)
                        }
                        outlets.add(n.immutable());
                        outletSides.add(d.getOpposite());
                    }
                }
            }
        }
    }

    private void pushOut() {
        if (!outletsKnown) {
            findOutlets();
        }
        int caps = 0;
        long gave = 0;
        for (int i = 0; i < outlets.size() && energy > 0; i++) {
            BlockPos n = outlets.get(i);
            if (!level.isLoaded(n)) {
                continue;
            }
            BlockState ns = level.getBlockState(n);
            if (ns.getBlock() instanceof CoreFrameBlock || ns.getBlock() instanceof PowerCoreBlock) {
                continue;
            }
            IEnergyStorage other = level.getCapability(Capabilities.EnergyStorage.BLOCK, n, outletSides.get(i));
            if (other == null || !other.canReceive()) {
                continue;
            }
            caps++;
            int offer = (int) Math.min(energy, Integer.MAX_VALUE);
            int took = other.receiveEnergy(offer, false);
            gave += took;
            if (took > 0) {
                energy -= took;
                outCount += took;
                changed(false);
            }
        }
        debugPush(caps, gave);
    }

    private void debugPush(int caps, long gave) {
        if (level.getGameTime() % 100 == 0) {
            SkyBlocksMod.LOGGER.info("[core push] {} chamber {} outlets {} receivers {} gave {}", worldPosition, chamber, outlets.size(), caps, gave);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowerCoreBlockEntity be) {
        if (be.syncPending && level.getGameTime() % 5 == 0) {
            be.syncPending = false;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        long time = level.getGameTime() + pos.hashCode();
        if (time % 20 == 0) {
            be.checkChamber();
            long in = be.inCount / 20, out = be.outCount / 20;
            if (in != be.rateIn || out != be.rateOut) {
                be.rateIn = in;
                be.rateOut = out;
                be.syncPending = true;
            }
            be.inCount = 0;
            be.outCount = 0;
        }
        if (be.push && be.energy > 0) {
            be.pushOut();
        }
    }

    // ---- saving, and what the client sees ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        save(tag);
    }

    private void save(CompoundTag tag) {
        tag.putLong("energy", energy);
        tag.putInt("skin", skin);
        tag.putBoolean("push", push);
        tag.putInt("chamber", chamber);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, tag.getLong("energy"));
        skin = Math.floorMod(tag.getInt("skin"), SKINS.length);
        push = tag.getBoolean("push");
        chamber = tag.getInt("chamber");
        rateIn = tag.getLong("rate_in");
        rateOut = tag.getLong("rate_out");
        outletsKnown = false;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        save(tag);
        tag.putLong("rate_in", rateIn);
        tag.putLong("rate_out", rateOut);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Itself as an item, keeping its energy and skin. */
    public ItemStack asItem(HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(ModItems.POWER_CORE.get());
        if (energy > 0 || skin != 0 || push) {
            CompoundTag tag = saveWithoutMetadata(registries);
            tag.remove("chamber");
            net.minecraft.world.item.BlockItem.setBlockEntityData(stack, ModBlockEntities.POWER_CORE.get(), tag);
        }
        return stack;
    }

    public boolean isEmpty() {
        return energy <= 0 && skin == 0 && !push;
    }

    /** An amount of energy for showing: "512 FE", "12.5 kFE", "3.20 GFE", "4.00 TFE". */
    public static String fe(long fe) {
        if (fe < 1000) {
            return fe + " FE";
        }
        String[] units = {"kFE", "MFE", "GFE", "TFE", "PFE"};
        double v = fe;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return String.format(java.util.Locale.ROOT, v < 10 ? "%.2f %s" : v < 100 ? "%.1f %s" : "%.0f %s", v, units[u]);
    }
}
