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
    /** (Kept only to carry old push mode over to links: every receiver beside its frames set to give.) */
    private boolean legacyPush;
    /** Its core chamber: how many blocks across (0: none). */
    private int chamber;
    private int lastSignal = -1;
    private boolean relink, syncPending;
    /** What each block it's linked to is set to (only those not off); saved. */
    private final java.util.Map<BlockPos, Integer> modes = new java.util.HashMap<>();
    /** The blocks near it that hold energy, as last found (server: scanned now and then; client: as sent). */
    private List<Link> links = new ArrayList<>();
    private boolean scanSoon = true;
    /** (Server: energy moved through each link since the last scan - sent as its flow, FE/t.) */
    private final java.util.Map<BlockPos, Long> moved = new java.util.HashMap<>();
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

    /** A block near it that holds energy: where, what it's set to, whether it can give / take energy, how much it holds. */
    public record Link(BlockPos pos, int mode, boolean canGive, boolean canTake, long stored, long cap, long flow) {}

    public static final int OFF = 0, TAKE = 1, GIVE = 2, REACH = 5, MAX_LINKS = 48;

    public List<Link> links() {
        return links;
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

    /** A link set (from its screen): {@code target} to off, take (it draws energy from it) or give (it sends energy to it). */
    public void setMode(BlockPos target, int mode) {
        int r = reachRadius();
        if (Math.abs(target.getX() - worldPosition.getX()) > r || Math.abs(target.getY() - worldPosition.getY()) > r
                || Math.abs(target.getZ() - worldPosition.getZ()) > r || mode < OFF || mode > GIVE) {
            return;
        }
        if (mode == OFF) {
            modes.remove(target);
        } else {
            modes.put(target.immutable(), mode);
        }
        for (int i = 0; i < links.size(); i++) {
            Link l = links.get(i);
            if (l.pos.equals(target)) {
                links.set(i, new Link(l.pos, mode, l.canGive, l.canTake, l.stored, l.cap, 0));
            }
        }
        setChanged();
        syncPending = true;
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
            scanSoon = true;
            changed(true);
        } else if (chamber > 0 && relink) {
            relink = false;
            setFrames(chamber, true); // (frames put in since: linked too)
            scanSoon = true;
        }
    }

    public void unform() {
        if (chamber > 0 && level != null && !level.isClientSide) {
            setFrames(chamber, false);
            chamber = 0;
        }
    }

    // ---- links: the blocks near it it takes energy from or gives energy to ----

    private int reachRadius() {
        return (chamber > 0 ? (chamber - 1) / 2 : 0) + REACH;
    }

    private static final int CHUNK = 1_000_000, MOST = 1 << 30;

    /** A block's energy, from whichever side shows it (inside first, then the side towards it, then any). */
    @Nullable
    private IEnergyStorage energyAt(BlockPos p) {
        IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, p, null);
        if (e != null) {
            return e;
        }
        Direction toward = Direction.getNearest(worldPosition.getX() - p.getX(), worldPosition.getY() - p.getY(), worldPosition.getZ() - p.getZ());
        e = level.getCapability(Capabilities.EnergyStorage.BLOCK, p, toward);
        if (e != null) {
            return e;
        }
        for (Direction d : Direction.values()) {
            e = level.getCapability(Capabilities.EnergyStorage.BLOCK, p, d);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    /** Finds what's near it that holds energy (up to 5 blocks out from its chamber; a machine's frames as the machine). */
    private void scan() {
        int r = chamber > 0 ? (chamber - 1) / 2 : 0, reach = r + REACH;
        List<Link> found = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) <= r) {
                        continue; // (itself, its chamber)
                    }
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (!s.hasBlockEntity() || s.getBlock() instanceof CoreFrameBlock) {
                        continue;
                    }
                    BlockPos at = p.immutable();
                    if (level.getBlockEntity(at) instanceof MachineFrameBlockEntity f && f.master() != null) {
                        at = f.master(); // (a machine's frame: the machine itself, once)
                    }
                    if (!seen.add(at)) {
                        continue;
                    }
                    IEnergyStorage e = energyAt(at);
                    if (e == null || (!e.canExtract() && !e.canReceive())) {
                        continue;
                    }
                    if (legacyPush && e.canReceive() && Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) == r + 1) {
                        modes.put(at, GIVE);
                    }
                    found.add(new Link(at, modes.getOrDefault(at, OFF), e.canExtract(), e.canReceive(), e.getEnergyStored(), e.getMaxEnergyStored(),
                            moved.getOrDefault(at, 0L) / 40));
                }
            }
        }
        legacyPush = false;
        moved.clear();
        // (linked ones first, then the nearest)
        found.sort((x, y) -> x.mode != OFF && y.mode == OFF ? -1 : x.mode == OFF && y.mode != OFF ? 1
                : Double.compare(x.pos.distSqr(worldPosition), y.pos.distSqr(worldPosition)));
        if (found.size() > MAX_LINKS) {
            found = new ArrayList<>(found.subList(0, MAX_LINKS));
        }
        // (a link to something gone: dropped)
        java.util.Set<BlockPos> there = new java.util.HashSet<>();
        for (Link l : found) {
            there.add(l.pos);
        }
        if (modes.keySet().removeIf(k -> !there.contains(k) && level.isLoaded(k))) {
            setChanged();
        }
        links = found;
        syncPending = true;
    }

    /** Each tick: from what it takes from, energy in; to what it gives to, energy out. */
    private void transfer() {
        for (Link l : links) {
            if (l.mode == OFF || !level.isLoaded(l.pos)) {
                continue;
            }
            IEnergyStorage e = energyAt(l.pos);
            if (e == null) {
                continue;
            }
            if (l.mode == GIVE && energy > 0 && e.canReceive()) {
                // (a sane offer, asked first: some take a huge one badly - adding it to what they hold overflows)
                int room = Math.max(0, e.getMaxEnergyStored() - e.getEnergyStored());
                int offer = (int) Math.min(energy, Math.min(Math.max(room, CHUNK), MOST));
                int can = e.receiveEnergy(offer, true);
                int took = can > 0 ? e.receiveEnergy(Math.min(can, offer), false) : 0;
                if (took > 0) {
                    moved.merge(l.pos, (long) took, Long::sum);
                    energy -= took;
                    outCount += took;
                    changed(false);
                }
            } else if (l.mode == TAKE && e.canExtract() && energy < capacity()) {
                int want = (int) Math.min(capacity() - energy, MOST);
                int can = e.extractEnergy(want, true);
                int got = can > 0 ? e.extractEnergy(Math.min(can, want), false) : 0;
                if (got > 0) {
                    moved.merge(l.pos, (long) got, Long::sum);
                    energy += got;
                    inCount += got;
                    changed(false);
                }
            }
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
        if (be.scanSoon || time % 40 == 0) {
            be.scanSoon = false;
            be.scan();
        }
        be.transfer();
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
        net.minecraft.nbt.ListTag set = new net.minecraft.nbt.ListTag();
        for (var e : modes.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("p", e.getKey().asLong());
            t.putByte("m", (byte) (int) e.getValue());
            set.add(t);
        }
        tag.put("links", set);
        tag.putInt("chamber", chamber);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, tag.getLong("energy"));
        skin = Math.floorMod(tag.getInt("skin"), SKINS.length);
        legacyPush = tag.getBoolean("push");
        modes.clear();
        for (net.minecraft.nbt.Tag t : tag.getList("links", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            modes.put(BlockPos.of(c.getLong("p")), (int) c.getByte("m"));
        }
        if (tag.contains("found")) { // (the client: what's near it, as found)
            List<Link> got = new ArrayList<>();
            for (net.minecraft.nbt.Tag t : tag.getList("found", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                got.add(new Link(BlockPos.of(c.getLong("p")), c.getByte("m"), c.getBoolean("g"), c.getBoolean("t"), c.getLong("s"), c.getLong("c"), c.getLong("f")));
            }
            links = got;
        }
        chamber = tag.getInt("chamber");
        rateIn = tag.getLong("rate_in");
        rateOut = tag.getLong("rate_out");
        scanSoon = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        save(tag);
        tag.putLong("rate_in", rateIn);
        tag.putLong("rate_out", rateOut);
        net.minecraft.nbt.ListTag found = new net.minecraft.nbt.ListTag();
        for (Link l : links) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", l.pos.asLong());
            c.putByte("m", (byte) l.mode);
            c.putBoolean("g", l.canGive);
            c.putBoolean("t", l.canTake);
            c.putLong("s", l.stored);
            c.putLong("c", l.cap);
            c.putLong("f", l.flow);
            found.add(c);
        }
        tag.put("found", found);
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
        if (energy > 0 || skin != 0) {
            CompoundTag tag = saveWithoutMetadata(registries);
            tag.remove("chamber");
            tag.remove("links");
            net.minecraft.world.item.BlockItem.setBlockEntityData(stack, ModBlockEntities.POWER_CORE.get(), tag);
        }
        return stack;
    }

    public boolean isEmpty() {
        return energy <= 0 && skin == 0;
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
