package com.example.skyblocks;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * A spin pad: a turntable for OIIA cats - put in its screen (SpinPadMenu: right-click), or the cat block set on it, or a
 * tame cat that hops on. The cat spins - faster and faster the longer it's on (a live one: pet it for a boost), an aura
 * round it (client/SpinPadRenderer) - and the spinning makes Forge Energy (FE), more the faster it spins and the more
 * cats spin, passed on to whatever's next to it that takes energy (cables, machines, batteries), or kept. A redstone
 * signal stops it (and lets a live cat go). The aura's sky: right-click the pad with a sky block.
 *
 * The big spin pad: spin pads laid as a square, 2, 3 or 4 a side, join into one big pad - its north-west corner the one
 * that works it (the master; the others pass to it: their screens, their energy). It takes as many cats as its side
 * cubed (8, 27, 64), spins them as one big cat (the cube root of how many it holds, across), and makes as much energy
 * as one pad does per cat.
 */
public class SpinPadBlockEntity extends BlockEntity {
    /** FE made each tick at full speed; how much it keeps; how much it passes on each tick, at most. */
    public static final int MAX_FE = 320, CAPACITY = 250_000, PUSH = 4096;
    /** The big pad's largest side. */
    public static final int MAX_SIDE = 4;
    /** How quickly it speeds up with a cat on (to full: 60 s), slows down without, and what a pet adds. */
    private static final float SPEED_UP = 1.0F / 1200.0F, SLOW_DOWN = 1.0F / 160.0F, PET = 0.06F;

    /** Its energy: nothing put in from outside (it makes its own), taken out by anything. */
    public final Buffer energy = new Buffer();
    private float speed;
    private boolean occupied;
    private String sky = "SKYBLOCKS_SYNTHWAVE", pass = "NORMAL";
    private float sentSpeed = -1;
    private boolean sentOccupied;
    private int lastFe;
    /** How its plate looks: 0 plain, 1 a disco floor, 2 a record on a turntable; whether its speed gauge floats over it. */
    public static final String[] STYLES = {"plain", "disco", "vinyl"};
    private int style;
    private boolean gauge = true;
    /** (The client: when it last let off a music note - a record.) */
    public long clientNoteAt;
    /** Its cats (OIIA cat blocks, as items): one alone, side cubed as a big pad's master. */
    public final net.neoforged.neoforge.items.ItemStackHandler cats = new net.neoforged.neoforge.items.ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return maxCats();
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return stack.is(ModItems.OIIA_CAT.get()) && master == null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            changed();
        }
    };
    /** A big pad's master: how many a side (0: not one). A big pad's other pads: where its master is. */
    private int side;
    @Nullable
    private BlockPos master;
    private int syncIn;
    /** (Client: its cats, as the server last said.) */
    public int clientCats;
    /** (Client: Shadered's model of the cat drawn on it.) */
    public Object clientCatModel;

    /** Client: how far the OIIA spin's got (seconds of its animation, which plays faster as it speeds up), and the plate's turn. */
    public double clientSpin, clientSpinO, clientTurn, clientTurnO;
    public float clientSpeed;

    public SpinPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPIN_PAD.get(), pos, state);
    }

    public static final class Buffer extends EnergyStorage {
        Buffer() {
            super(CAPACITY, 0, PUSH);
        }

        void make(int fe) {
            this.energy = Math.min(this.capacity, this.energy + Math.max(0, fe));
        }

        void set(int fe) {
            this.energy = Math.max(0, Math.min(this.capacity, fe));
        }

        void resize(int capacity, int push) {
            this.capacity = capacity;
            this.maxExtract = push;
            this.energy = Math.min(this.energy, capacity);
        }
    }

    /** The spin pad at pos, if there's one. */
    @Nullable
    public static SpinPadBlockEntity at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SpinPadBlockEntity be ? be : null;
    }

    public float speed() {
        return speed;
    }

    /** How many a side, as a big pad's master; 1 alone (0: one of a big pad's other pads). */
    public int side() {
        return master != null ? 0 : Math.max(1, side);
    }

    @Nullable
    public BlockPos master() {
        return master;
    }

    /** The pad that works it: its big pad's master, or itself. */
    public SpinPadBlockEntity worker() {
        if (master != null && level != null && level.getBlockEntity(master) instanceof SpinPadBlockEntity m && m.side >= 2) {
            return m;
        }
        return this;
    }

    public int maxCats() {
        int n = Math.max(1, side);
        return n * n * n;
    }

    /** How many cats it's spinning (in its screen; or one on it, set or live). */
    public int catCount() {
        return level != null && level.isClientSide ? clientCats : cats.getStackInSlot(0).getCount();
    }

    /** The cat it shows: the cube root of how many it holds, across (1 alone). */
    public int catSize() {
        int c = catCount();
        int k = 1;
        while ((k + 1) * (k + 1) * (k + 1) <= c) {
            k++;
        }
        return Math.min(Math.max(1, side), k);
    }

    public boolean occupied() {
        return occupied;
    }

    public int lastFe() {
        return lastFe;
    }

    public int style() {
        return style;
    }

    public boolean gauge() {
        return gauge;
    }

    /** Its next look (plain, disco, vinyl). */
    public void cycleStyle() {
        style = (style + 1) % STYLES.length;
        syncLook();
    }

    public void toggleGauge() {
        gauge = !gauge;
        syncLook();
    }

    private void syncLook() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public String sky() {
        return sky;
    }

    public String pass() {
        return pass;
    }

    public void setSky(String sky, String pass) {
        this.sky = sky;
        this.pass = pass;
        changed();
    }

    /** The cat on it petted: a little push. */
    public void boost() {
        speed = Math.min(1.0F, speed + PET);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            sentSpeed = speed;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Where a cat on the pad stands (its middle, on the plate). */
    public AABB top() {
        return new AABB(worldPosition.getX() + 0.15, worldPosition.getY(), worldPosition.getZ() + 0.15, worldPosition.getX() + 0.85,
                worldPosition.getY() + 1.2, worldPosition.getZ() + 0.85);
    }

    /** Whether a big pad's square starts here (its north-west corner): how many a side, or 0. */
    private static int squareAt(Level level, BlockPos pos) {
        if (isPad(level, pos.west()) || isPad(level, pos.north())) {
            return 0;
        }
        int w = 1, d = 1;
        while (w <= MAX_SIDE && isPad(level, pos.east(w))) {
            w++;
        }
        while (d <= MAX_SIDE && isPad(level, pos.south(d))) {
            d++;
        }
        if (w != d || w < 2 || w > MAX_SIDE) {
            return 0;
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                if (!isPad(level, pos.offset(x, 0, z))) {
                    return 0;
                }
            }
        }
        return w;
    }

    private static boolean isPad(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof SpinPadBlock;
    }

    /** (Server, now and then: a big pad formed here, or broken up; a big pad's other pad still in its square.) */
    private void checkSquare() {
        if (master != null) {
            if (!(level.getBlockEntity(master) instanceof SpinPadBlockEntity m) || m.side < 2
                    || worldPosition.getX() - master.getX() >= m.side || worldPosition.getZ() - master.getZ() >= m.side
                    || worldPosition.getX() < master.getX() || worldPosition.getZ() < master.getZ() || worldPosition.getY() != master.getY()) {
                master = null;
                changed();
            }
            return;
        }
        int n = squareAt(level, worldPosition);
        if (n != side && !(n < 2 && side < 2)) {
            side = n >= 2 ? n : 0;
            for (int x = 0; x < Math.max(n, 1); x++) {
                for (int z = 0; z < Math.max(n, 1); z++) {
                    if ((x != 0 || z != 0) && level.getBlockEntity(worldPosition.offset(x, 0, z)) instanceof SpinPadBlockEntity p) {
                        // (its cats into the master's screen; it works for the master now)
                        net.minecraft.world.item.ItemStack theirs = p.cats.extractItem(0, 64, false);
                        if (!theirs.isEmpty()) {
                            net.minecraft.world.item.ItemStack left = cats.insertItem(0, theirs, false);
                            if (!left.isEmpty()) {
                                net.minecraft.world.Containers.dropItemStack(level, p.worldPosition.getX() + 0.5, p.worldPosition.getY() + 0.5,
                                        p.worldPosition.getZ() + 0.5, left);
                            }
                        }
                        p.master = side >= 2 ? worldPosition : null;
                        p.changed();
                    }
                }
            }
            dropExtraCats();
            changed();
        }
    }

    /** More cats than it can hold now (a big pad broken up): the rest dropped. */
    private void dropExtraCats() {
        net.minecraft.world.item.ItemStack in = cats.getStackInSlot(0);
        if (in.getCount() > maxCats()) {
            net.minecraft.world.item.ItemStack extra = in.copyWithCount(in.getCount() - maxCats());
            cats.setStackInSlot(0, in.copyWithCount(maxCats()));
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, extra);
        }
    }

    /** Its cats dropped (it's broken). */
    public void dropCats() {
        net.minecraft.world.item.ItemStack in = cats.getStackInSlot(0);
        if (!in.isEmpty() && level != null) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, in.copy());
            cats.setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    /** Its energy for cables: its big pad's (the master's), or its own. */
    public IEnergyStorage energyHere() {
        return worker().energy;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpinPadBlockEntity be) {
        if ((level.getGameTime() + pos.hashCode()) % 20 == 0) {
            be.checkSquare();
        }
        if (be.master != null) {
            // (one of a big pad's other pads: its master does the work)
            be.speed = 0.0F;
            be.occupied = false;
            be.lastFe = 0;
            return;
        }
        boolean stopped = level.hasNeighborSignal(pos);
        List<OiiaCatEntity> cats = stopped ? List.of() : level.getEntitiesOfClass(OiiaCatEntity.class, be.top(), c -> c.isAlive() && !c.isBaby());
        OiiaCatEntity cat = cats.isEmpty() ? null : cats.get(0);
        // (an OIIA cat block set on it spins too - on the pad, not a block above it: client/OiiaCatRenderer)
        boolean block = !stopped && be.side < 2 && level.getBlockState(pos.above()).is(ModBlocks.OIIA_CAT.get());
        int inside = stopped ? 0 : be.cats.getStackInSlot(0).getCount();
        if (be.side >= 2) {
            cat = null; // (a big pad: its cats are in its screen)
        }
        be.occupied = cat != null || block || inside > 0;
        if (block || inside > 0) {
            be.speed = Math.min(1.0F, be.speed + SPEED_UP);
        } else if (cat != null) {
            cat.holdOnPad(pos);
            be.speed = Math.min(1.0F, be.speed + SPEED_UP * (cat.isTame() ? 1.0F : 0.5F));
        } else {
            be.speed = Math.max(0.0F, be.speed - SLOW_DOWN);
        }
        // its energy: as much as it's turning (MAX_FE at full spin); passed on to its neighbours
        // (each cat spinning makes a pad's worth: one on it, or as many as are in its screen)
        int spinning = Math.max(inside, (block || cat != null) ? 1 : 0);
        be.lastFe = be.occupied || be.speed > 0 ? Math.round(MAX_FE * Math.max(1, spinning) * turnRate(be.speed) / turnRate(1.0F)) : 0;
        int n = Math.max(1, be.side);
        be.energy.resize(CAPACITY * n * n * n, PUSH * n * n * n);
        be.energy.make(be.lastFe);
        // passed on from every pad of it (a big pad: round its whole square, and under it)
        for (int x = 0; x < n && be.energy.getEnergyStored() > 0; x++) {
            for (int z = 0; z < n && be.energy.getEnergyStored() > 0; z++) {
                BlockPos p = pos.offset(x, 0, z);
                for (Direction d : Direction.values()) {
                    if (be.energy.getEnergyStored() <= 0) {
                        break;
                    }
                    BlockPos q = p.relative(d);
                    if (n > 1 && q.getY() == pos.getY() && q.getX() >= pos.getX() && q.getX() < pos.getX() + n && q.getZ() >= pos.getZ() && q.getZ() < pos.getZ() + n) {
                        continue; // (its own pads)
                    }
                    IEnergyStorage next = level.getCapability(Capabilities.EnergyStorage.BLOCK, q, d.getOpposite());
                    if (next != null && next.canReceive()) {
                        int given = next.receiveEnergy(Math.min(PUSH * n * n * n, be.energy.getEnergyStored()), false);
                        be.energy.extractEnergy(given, false);
                    }
                }
            }
        }
        if (be.occupied != be.sentOccupied) {
            be.sentOccupied = be.occupied;
            be.changed();
        }
        if (be.lastFe > 0 || cat != null) {
            be.setChanged();
        }
        // (the client told of its speed when it's changed enough to see; and twice a second while it's making energy, for
        // its screen)
        if (Math.abs(be.speed - be.sentSpeed) > 0.02F || (be.speed == 0) != (be.sentSpeed == 0) || (be.lastFe > 0 && ++be.syncIn >= 10)) {
            be.syncIn = 0;
            be.changed();
        }
    }

    /**
     * How fast it turns at a speed (0 .. 1): degrees a tick - slowly at first, faster and faster (up to three turns a
     * second at full speed). The cat on it turns with it; the energy it makes goes with it.
     */
    public static float turnRate(float speed) {
        return speed <= 0.001F ? 0.0F : 2.0F + 52.0F * (float) Math.pow(speed, 1.4);
    }

    /** Its spin as turns a minute. */
    public int rpm() {
        return Math.round(turnRate(speed) * 20.0F * 60.0F / 360.0F);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SpinPadBlockEntity be) {
        be.clientSpeed += (be.speed - be.clientSpeed) * 0.1F;
        be.clientSpinO = be.clientSpin;
        be.clientTurnO = be.clientTurn;
        float s = be.clientSpeed;
        // (a live cat's OIIA spin plays at its own pace at first, up to 6x as fast at full speed)
        be.clientSpin += 0.05 * (s > 0.02F ? 0.8 + s * 5.2 : 0.0);
        be.clientTurn += turnRate(s); // degrees a tick: the plate, and the cat block on it
    }

    /** Client: the OIIA spin's time on this pad now (seconds of its animation). */
    public float spinSeconds(float partialTick) {
        return (float) (clientSpinO + (clientSpin - clientSpinO) * partialTick);
    }

    public float turn(float partialTick) {
        return (float) (clientTurnO + (clientTurn - clientTurnO) * partialTick);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("speed", speed);
        tag.putInt("energy", energy.getEnergyStored());
        tag.putString("sky", sky);
        tag.putString("pass", pass);
        tag.putBoolean("occupied", occupied);
        tag.put("cats", cats.serializeNBT(registries));
        tag.putInt("side", side);
        if (master != null) {
            tag.putLong("master", master.asLong());
        }
        tag.putInt("cat_count", cats.getStackInSlot(0).getCount());
        tag.putInt("last_fe", lastFe);
        tag.putInt("style", style);
        tag.putBoolean("gauge", gauge);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        speed = tag.getFloat("speed");
        energy.set(tag.getInt("energy"));
        if (tag.contains("sky")) {
            sky = tag.getString("sky");
            pass = tag.getString("pass");
        }
        occupied = tag.getBoolean("occupied");
        if (tag.contains("cats")) {
            cats.deserializeNBT(registries, tag.getCompound("cats"));
        }
        side = tag.getInt("side");
        master = tag.contains("master") ? BlockPos.of(tag.getLong("master")) : null;
        clientCats = tag.getInt("cat_count");
        lastFe = tag.getInt("last_fe");
        style = Math.floorMod(tag.getInt("style"), STYLES.length);
        gauge = !tag.contains("gauge") || tag.getBoolean("gauge");
        int n = Math.max(1, side);
        energy.resize(CAPACITY * n * n * n, PUSH * n * n * n);
        if (tag.contains("energy")) {
            energy.set(tag.getInt("energy"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
