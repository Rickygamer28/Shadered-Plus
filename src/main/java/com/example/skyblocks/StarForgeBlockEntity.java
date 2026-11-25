package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The star forge: forge frames along the edges of a cube (5 or 7 across, its sides open or glass) round its core - a
 * star burns in it, fed fuel; what it's fed sets its colour: red (coal), yellow (blaze), blue (soul and ghast tears),
 * white (nether stars, end crystals, dragon's breath) - hotter works faster, and makes more energy, given to what's
 * beside its frames (a power core, say). Smelting: a whole stack at once (any furnace recipe). Forging: alloys (star gold
 * from yellow up, nebula steel from blue up) and transmuting one thing into a rarer one (the hotter the star, the more it
 * can do). A singularity beside its frames feeds it what it can work and takes what it makes.
 */
public class StarForgeBlockEntity extends MachineBlockEntity {
    public static final String[] CLASSES = {"red", "yellow", "blue", "white"};
    public static final int FUEL = 0, IN = 1, OUT = 10, SLOTS = 9;
    /** Energy a tick it makes at each colour; how much faster it works. */
    private static final int[] MAKES = {80, 200, 500, 1200};
    private static final float[] PACE = {1.0F, 1.6F, 2.5F, 4.0F};
    private static final int BASE_TIME = 200;

    /** A fuel: its colour and how long it burns. */
    public record Fuel(int colour, int ticks) {}

    public static final Map<Item, Fuel> FUELS = Map.ofEntries(Map.entry(Items.COAL, new Fuel(0, 1600)), Map.entry(Items.CHARCOAL, new Fuel(0, 1600)),
            Map.entry(Items.COAL_BLOCK, new Fuel(0, 16000)), Map.entry(Items.DRIED_KELP_BLOCK, new Fuel(0, 4000)),
            Map.entry(Items.BLAZE_POWDER, new Fuel(1, 1200)), Map.entry(Items.BLAZE_ROD, new Fuel(1, 2400)), Map.entry(Items.MAGMA_CREAM, new Fuel(1, 1600)),
            Map.entry(Items.FIRE_CHARGE, new Fuel(1, 1200)), Map.entry(Items.SOUL_SAND, new Fuel(2, 800)), Map.entry(Items.SOUL_SOIL, new Fuel(2, 800)),
            Map.entry(Items.GHAST_TEAR, new Fuel(2, 6000)), Map.entry(Items.ECHO_SHARD, new Fuel(2, 8000)), Map.entry(Items.NETHER_STAR, new Fuel(3, 100000)),
            Map.entry(Items.END_CRYSTAL, new Fuel(3, 20000)), Map.entry(Items.DRAGON_BREATH, new Fuel(3, 6000)));

    /** An alloy: a and b, one each, make it - from a star of at least {@code colour}. */
    public record Alloy(Item a, Item b, java.util.function.Supplier<Item> out, int colour) {}

    /** A transmutation: {@code need} of one make one of another - from a star of at least {@code colour}. */
    public record Transmute(Item from, int need, Item to, int colour) {}

    public static final List<Alloy> ALLOYS = List.of(new Alloy(Items.GOLD_INGOT, Items.GLOWSTONE_DUST, () -> ModItems.STAR_GOLD_INGOT.get(), 1),
            new Alloy(Items.IRON_INGOT, Items.AMETHYST_SHARD, () -> ModItems.NEBULA_STEEL_INGOT.get(), 2));
    public static final List<Transmute> TRANSMUTES = List.of(new Transmute(Items.COBBLESTONE, 1, Items.COBBLED_DEEPSLATE, 0),
            new Transmute(Items.SAND, 1, Items.RED_SAND, 0), new Transmute(Items.ROTTEN_FLESH, 2, Items.LEATHER, 0),
            new Transmute(Items.COBBLED_DEEPSLATE, 4, Items.OBSIDIAN, 1), new Transmute(Items.QUARTZ, 1, Items.AMETHYST_SHARD, 1),
            new Transmute(Items.OBSIDIAN, 1, Items.CRYING_OBSIDIAN, 2), new Transmute(Items.COPPER_INGOT, 2, Items.IRON_INGOT, 2),
            new Transmute(Items.IRON_INGOT, 2, Items.GOLD_INGOT, 3), new Transmute(Items.COAL_BLOCK, 9, Items.DIAMOND, 3));

    private int burn, burnTotal, colour, mode;
    /** Powered: with no fuel burning, energy piped in keeps its star lit - the colour picked on its screen, at this much FE/t (x its size). */
    public static final int[] COST = {200, 500, 1250, 3000};
    private static final int POWER_CAP = 600_000;
    private final Battery power = new Battery(POWER_CAP, () -> {});
    private int powerColour;
    private boolean powered;
    private int shownGlow = -1;
    /** (The client: how big its star's shown, easing.) */
    public float clientFull;
    private int progress, time;
    private final List<ItemStack> working = new ArrayList<>(), making = new ArrayList<>();
    private int feedIn = 20;
    private final Battery made = new Battery(4_000_000, () -> {});

    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return items.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot >= OUT) {
                return stack;
            }
            if (slot == FUEL && !FUELS.containsKey(stack.getItem())) {
                return stack;
            }
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= OUT ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < OUT && (slot != FUEL || FUELS.containsKey(stack.getItem()));
        }
    };

    private final IEnergyStorage output = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return size == 0 ? 0 : power.receiveEnergy(amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return made.extractEnergy(amount, simulate);
        }

        @Override
        public int getEnergyStored() {
            return made.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return made.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return size > 0;
        }
    };

    public StarForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STAR_FORGE.get(), pos, state, 1 + 2 * SLOTS);
    }

    @Override
    public MachineFrameBlock.Kind kind() {
        return MachineFrameBlock.Kind.FORGE;
    }

    @Override
    protected int capacityFor(int size) {
        return 1;
    }

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        return slot == FUEL ? FUELS.containsKey(stack.getItem()) : true;
    }

    @Override
    public IEnergyStorage energyHandler() {
        return output;
    }

    @Override
    public IItemHandler itemHandler() {
        return automation;
    }

    public boolean lit() {
        return burn > 0 || powered;
    }

    /** Burning on power, not fuel. */
    public boolean powered() {
        return powered;
    }

    public int powerColour() {
        return powerColour;
    }

    /** What its powered star (the colour picked) uses, FE/t. */
    public int powerCost() {
        return COST[powerColour] * Math.max(1, size);
    }

    public void cyclePowerColour() {
        powerColour = (powerColour + 1) % COST.length;
        setChanged();
        sync();
    }

    public int colour() {
        return colour;
    }

    public int mode() {
        return mode;
    }

    /** How much of its fuel's left (0..1). */
    public float fuelLeft() {
        if (powered) {
            return (float) power.getEnergyStored() / POWER_CAP;
        }
        return burnTotal <= 0 ? 0.0F : (float) burn / burnTotal;
    }

    public float progress() {
        return time <= 0 ? 0.0F : (float) progress / time;
    }

    public int makes() {
        return burn > 0 ? MAKES[colour] * Math.max(1, size) : 0;
    }

    public int stored() {
        return made.getEnergyStored();
    }

    public List<ItemStack> making() {
        return making;
    }

    /** Its frames: girders, plinths and chimneys, solar walls - their veins lit in its star's colour. */
    @Override
    protected BlockState shapeFrame(BlockPos frame, BlockState s) {
        int dx = frame.getX() - worldPosition.getX(), dy = frame.getY() - worldPosition.getY(), dz = frame.getZ() - worldPosition.getZ();
        int r = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        return FramePart.shape(s, dx, dy, dz, r, true).setValue(MachineFrameBlock.GLOW, glowNow());
    }

    private int glowNow() {
        return lit() ? colour + 1 : 0;
    }

    /** Its star changed (lit, out, another colour): its frames' veins follow. */
    private void relight(net.minecraft.server.level.ServerLevel level) {
        int g = glowNow();
        for (BlockPos p : frames()) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof MachineFrameBlock && s.getValue(MachineFrameBlock.FORMED) && s.getValue(MachineFrameBlock.GLOW) != g) {
                level.setBlock(p, s.setValue(MachineFrameBlock.GLOW, g), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    public void toggleMode() {
        mode = 1 - mode;
        setChanged();
        sync();
    }

    /** Its chamber: forge frames along the edges of a cube round it (5 or 7 across), its sides open or glass, room inside. */
    @Nullable
    @Override
    protected Found detect() {
        for (int sz : new int[] {7, 5}) {
            List<BlockPos> found = chamber(sz);
            if (found != null) {
                return new Found(sz == 7 ? 2 : 1, found);
            }
        }
        return null;
    }

    @Nullable
    private List<BlockPos> chamber(int sz) {
        int r = (sz - 1) / 2;
        List<BlockPos> found = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int rims = (Math.abs(dx) == r ? 1 : 0) + (Math.abs(dy) == r ? 1 : 0) + (Math.abs(dz) == r ? 1 : 0);
                    BlockPos p = worldPosition.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (rims >= 2) {
                        if (!isFrame(p)) {
                            return null;
                        }
                        found.add(p);
                    } else if (rims == 1) {
                        if (s.getBlock() instanceof MachineFrameBlock && isFrame(p)) {
                            found.add(p);
                        } else if (!s.isAir() && !s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) && !s.is(ModBlocks.TANK_GLASS.get())) {
                            return null;
                        }
                    } else if (!s.isAir()) {
                        return null;
                    }
                }
            }
        }
        return found;
    }

    @Override
    protected void work(ServerLevel level) {
        // its star: fed when it runs out
        if (burn <= 0) {
            ItemStack fuel = items.getStackInSlot(FUEL);
            Fuel f = FUELS.get(fuel.getItem());
            if (f != null) {
                fuel.shrink(1);
                items.setStackInSlot(FUEL, fuel);
                burn = burnTotal = f.ticks;
                if (colour != f.colour) {
                    colour = f.colour;
                    level.playSound(null, worldPosition, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.8F, 0.6F + 0.2F * colour);
                }
                sync();
            }
        }
        // no fuel burning: power piped in keeps it lit, as the colour picked (making no energy of its own)
        boolean was = powered;
        powered = burn <= 0 && power.getEnergyStored() >= powerCost();
        if (powered) {
            power.take(powerCost());
            if (colour != powerColour) {
                colour = powerColour;
                level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6F, 0.8F + 0.2F * colour);
                sync();
            }
        }
        if (powered != was) {
            sync();
        }
        if (burn > 0) {
            burn--;
            made.add(MAKES[colour] * size);
            if (burn == 0) {
                sync();
            }
            if (burn % 40 == 0) {
                sync();
            }
        }
        if (made.getEnergyStored() > 0) {
            giveEnergy(made, 20_000 * size);
        }
        // a singularity beside it: fed from, given to
        if (--feedIn <= 0) {
            feedIn = 20;
            singularity(level);
        }
        if (glowNow() != shownGlow) {
            shownGlow = glowNow();
            relight(level);
        }
        // its work
        if (time == 0) {
            if (lit()) {
                start(level);
            }
        } else if (lit()) {
            progress++;
            if (progress % 20 == 0) {
                sync();
            }
            if (progress >= time) {
                finish(level);
            }
        }
    }

    private boolean room(List<ItemStack> out) {
        net.neoforged.neoforge.items.ItemStackHandler copy = new net.neoforged.neoforge.items.ItemStackHandler(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            copy.setStackInSlot(i, items.getStackInSlot(OUT + i).copy());
        }
        for (ItemStack s : out) {
            ItemStack left = s.copy();
            for (int i = 0; i < SLOTS && !left.isEmpty(); i++) {
                left = copy.insertItem(i, left, false);
            }
            if (!left.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** What it'll work next: a stack smelted, or an alloy or a transmutation (forging). */
    private void start(ServerLevel level) {
        List<ItemStack> in = new ArrayList<>(), out = new ArrayList<>();
        if (mode == 0) {
            for (int i = IN; i < IN + SLOTS && in.isEmpty(); i++) {
                ItemStack s = items.getStackInSlot(i);
                if (s.isEmpty()) {
                    continue;
                }
                var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(s), level);
                if (recipe.isEmpty()) {
                    continue;
                }
                ItemStack result = recipe.get().value().getResultItem(level.registryAccess());
                if (result.isEmpty()) {
                    continue;
                }
                int n = s.getCount();
                List<ItemStack> o = split(result.getItem(), result.getCount() * n);
                if (room(o)) {
                    in.add(items.extractItem(i, n, false));
                    out.addAll(o);
                }
            }
        } else {
            for (Alloy a : ALLOYS) {
                if (colour < a.colour || !in.isEmpty()) {
                    continue;
                }
                int ia = find(a.a), ib = find(a.b);
                if (ia < 0 || ib < 0) {
                    continue;
                }
                int n = Math.min(64, Math.min(count(a.a), count(a.b)));
                List<ItemStack> o = split(a.out.get(), n);
                if (room(o)) {
                    in.add(take(a.a, n));
                    in.add(take(a.b, n));
                    out.addAll(o);
                }
            }
            for (Transmute t : TRANSMUTES) {
                if (colour < t.colour || !in.isEmpty()) {
                    continue;
                }
                int n = Math.min(64, count(t.from) / t.need);
                if (n <= 0) {
                    continue;
                }
                List<ItemStack> o = split(t.to, n);
                if (room(o)) {
                    in.add(take(t.from, n * t.need));
                    out.addAll(o);
                }
            }
        }
        if (in.isEmpty()) {
            return;
        }
        working.clear();
        working.addAll(in);
        making.clear();
        making.addAll(out);
        progress = 0;
        time = Math.max(20, (int) (BASE_TIME / (PACE[colour] * (size >= 2 ? 1.5F : 1.0F))));
        stream(level, in.get(0), false);
        setChanged();
        sync();
    }

    private void finish(ServerLevel level) {
        if (!room(making)) {
            progress = time; // (no room: waits)
            return;
        }
        for (ItemStack s : making) {
            ItemStack left = s.copy();
            for (int i = 0; i < SLOTS && !left.isEmpty(); i++) {
                left = items.insertItem(OUT + i, left, false);
            }
        }
        if (!making.isEmpty()) {
            stream(level, making.get(0), true);
        }
        working.clear();
        making.clear();
        progress = time = 0;
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 1.3F);
        setChanged();
        sync();
    }

    /** (What it's working, seen flying into its star - or what it made, out of it - from one of its frames.) */
    private void stream(ServerLevel level, ItemStack item, boolean out) {
        List<BlockPos> f = frames();
        if (f.isEmpty()) {
            return;
        }
        BlockPos from = f.get(level.random.nextInt(f.size()));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(level, new net.minecraft.world.level.ChunkPos(worldPosition),
                new SingularityPayloads.Stream(from, worldPosition, item.copyWithCount(1), out));
    }

    private static List<ItemStack> split(Item item, int count) {
        List<ItemStack> out = new ArrayList<>();
        int max = new ItemStack(item).getMaxStackSize();
        while (count > 0) {
            int n = Math.min(max, count);
            out.add(new ItemStack(item, n));
            count -= n;
        }
        return out;
    }

    private int find(Item item) {
        for (int i = IN; i < IN + SLOTS; i++) {
            if (items.getStackInSlot(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private int count(Item item) {
        int n = 0;
        for (int i = IN; i < IN + SLOTS; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private ItemStack take(Item item, int n) {
        int got = 0;
        for (int i = IN; i < IN + SLOTS && got < n; i++) {
            if (items.getStackInSlot(i).is(item)) {
                got += items.extractItem(i, n - got, false).getCount();
            }
        }
        return new ItemStack(item, got);
    }

    /** Whether it could work this (smelt it, or forge it at its colour). */
    private boolean workable(ServerLevel level, ItemStack s) {
        if (mode == 0) {
            return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(s), level).isPresent();
        }
        for (Alloy a : ALLOYS) {
            if (colour >= a.colour && (s.is(a.a) || s.is(a.b))) {
                return true;
            }
        }
        for (Transmute t : TRANSMUTES) {
            if (colour >= t.colour && s.is(t.from)) {
                return true;
            }
        }
        return false;
    }

    /** A singularity beside its frames: what it makes goes into it; with nothing to work, it takes a stack it can work. */
    private void singularity(ServerLevel level) {
        List<Direction> sides = new ArrayList<>();
        List<BlockPos> at = outlets(sides);
        for (int i = 0; i < at.size(); i++) {
            BlockPos q = at.get(i);
            var block = level.getBlockState(q).getBlock();
            if (!(block instanceof SingularityBlock) && !(block instanceof ContainmentFrameBlock) && !(block instanceof SpecialFrameBlock)) {
                continue;
            }
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, q, sides.get(i));
            if (h == null) {
                continue;
            }
            for (int k = OUT; k < OUT + SLOTS; k++) {
                ItemStack s = items.getStackInSlot(k);
                if (s.isEmpty()) {
                    continue;
                }
                ItemStack left = s.copy();
                for (int j = 0; j < h.getSlots() && !left.isEmpty(); j++) {
                    left = h.insertItem(j, left, false);
                }
                items.setStackInSlot(k, left);
            }
            boolean empty = true;
            for (int k = IN; k < IN + SLOTS; k++) {
                empty &= items.getStackInSlot(k).isEmpty();
            }
            if (empty && lit()) {
                for (int j = 0; j < Math.min(h.getSlots(), 128); j++) {
                    ItemStack s = h.getStackInSlot(j);
                    if (!s.isEmpty() && workable(level, s)) {
                        ItemStack got = h.extractItem(j, 64, false);
                        items.setStackInSlot(IN, got);
                        break;
                    }
                }
            }
            return;
        }
    }

    @Override
    public void dropHeld() {
        if (level != null) {
            for (ItemStack s : working) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s);
            }
            working.clear();
        }
    }

    @Override
    protected void saveMore(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("burn", burn);
        tag.putInt("power", power.getEnergyStored());
        tag.putInt("power_colour", powerColour);
        tag.putBoolean("powered", powered);
        tag.putInt("burn_total", burnTotal);
        tag.putInt("colour", colour);
        tag.putInt("mode", mode);
        tag.putInt("progress", progress);
        tag.putInt("time", time);
        tag.putInt("made", made.getEnergyStored());
        tag.put("working", list(working, registries));
        tag.put("making", list(making, registries));
    }

    private static ListTag list(List<ItemStack> stacks, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                l.add(s.save(registries));
            }
        }
        return l;
    }

    @Override
    protected void loadMore(CompoundTag tag, HolderLookup.Provider registries) {
        burn = tag.getInt("burn");
        power.set(Math.min(POWER_CAP, tag.getInt("power")));
        powerColour = Math.floorMod(tag.getInt("power_colour"), COST.length);
        powered = tag.getBoolean("powered");
        burnTotal = tag.getInt("burn_total");
        colour = Math.floorMod(tag.getInt("colour"), CLASSES.length);
        mode = tag.getInt("mode") & 1;
        progress = tag.getInt("progress");
        time = tag.getInt("time");
        made.set(tag.getInt("made"));
        working.clear();
        making.clear();
        for (Tag t : tag.getList("working", Tag.TAG_COMPOUND)) {
            working.add(ItemStack.parseOptional(registries, (CompoundTag) t));
        }
        for (Tag t : tag.getList("making", Tag.TAG_COMPOUND)) {
            making.add(ItemStack.parseOptional(registries, (CompoundTag) t));
        }
    }
}
