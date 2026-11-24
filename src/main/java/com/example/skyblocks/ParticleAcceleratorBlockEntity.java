package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The particle accelerator: a ring of accelerator frames (7, 11 or 15 across, flat, round its collider) - give it energy
 * and something to smash, and two beams race round the ring, faster and faster; at full speed they collide in the middle,
 * the item's gone, and what comes of it lands in its output: rare things, rarer the bigger the ring. Run short of energy
 * with the beams going fast and it misfires: a flash, and the item's lost.
 */
public class ParticleAcceleratorBlockEntity extends MachineBlockEntity {
    /** The ring's half-width at each size (1 small .. 3 large). */
    public static final int[] RADIUS = {0, 3, 5, 7};
    /** Energy a tick to drive the beams (more as they go faster), and how much faster each tick, at each size. */
    private static final int[] DRIVE = {0, 500, 2000, 8000};
    private static final float[] ACCEL = {0, 1 / 240.0F, 1 / 320.0F, 1 / 400.0F};
    public static final int INPUT = 0, OUTPUTS = 9;

    private float speed;
    private int collisions;
    private ItemStack last = ItemStack.EMPTY;
    private float sentSpeed = -1;

    /** (The client: where the beams are round the ring, when it was last drawn, and its last flash - 1 a collision, 2 a misfire.) */
    public float clientTrack, clientSpeed;
    public long clientAt, flashAt;
    public int flashKind;

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
            return slot == INPUT ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == INPUT ? ItemStack.EMPTY : items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT;
        }
    };

    private final IEnergyStorage input = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return energy.receiveEnergy(amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return energy.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public ParticleAcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTICLE_ACCELERATOR.get(), pos, state, 1 + OUTPUTS);
    }

    @Override
    public MachineFrameBlock.Kind kind() {
        return MachineFrameBlock.Kind.ACCELERATOR;
    }

    @Override
    protected int capacityFor(int size) {
        return size >= 3 ? 64_000_000 : size == 2 ? 16_000_000 : 4_000_000;
    }

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        return slot == INPUT;
    }

    @Override
    public IEnergyStorage energyHandler() {
        return input;
    }

    @Override
    public IItemHandler itemHandler() {
        return automation;
    }

    public float speed() {
        return speed;
    }

    public int collisions() {
        return collisions;
    }

    public ItemStack last() {
        return last;
    }

    /** Energy a tick it takes now (driving the beams). */
    public int drive() {
        return size == 0 ? 0 : (int) (DRIVE[size] * (0.35F + speed));
    }

    /** The ring: every block flat round it at its size's half-width (the largest that's all accelerator frames). */
    @Nullable
    @Override
    protected Found detect() {
        for (int s = 3; s >= 1; s--) {
            int r = RADIUS[s];
            List<BlockPos> ring = new ArrayList<>();
            boolean ok = true;
            for (int dx = -r; dx <= r && ok; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos p = worldPosition.offset(dx, 0, dz);
                    if (!isFrame(p)) {
                        ok = false;
                        break;
                    }
                    ring.add(p);
                }
            }
            if (ok) {
                return new Found(s, ring);
            }
        }
        return null;
    }

    @Override
    protected void formedChanged() {
        speed = 0;
    }

    @Override
    protected void work(ServerLevel level) {
        ItemStack in = items.getStackInSlot(INPUT);
        if (!in.isEmpty() && room()) {
            int cost = drive();
            if (energy.take(cost)) {
                speed = Math.min(1.0F, speed + ACCEL[size]);
                if (speed >= 1.0F) {
                    collide(level, in);
                }
            } else if (speed > 0.3F) {
                misfire(level, in);
            } else {
                speed = Math.max(0.0F, speed - 0.01F);
            }
        } else {
            speed = Math.max(0.0F, speed - 0.008F);
        }
        if (Math.abs(speed - sentSpeed) > 0.02F || (speed == 0) != (sentSpeed == 0)) {
            sentSpeed = speed;
            sync();
        }
    }

    /** (Room in its output for what a collision makes: two slots free, or able to take more.) */
    private boolean room() {
        int free = 0;
        for (int i = 1; i <= OUTPUTS; i++) {
            if (items.getStackInSlot(i).isEmpty()) {
                free++;
            }
        }
        return free >= 2;
    }

    private void collide(ServerLevel level, ItemStack in) {
        in.shrink(1);
        items.setStackInSlot(INPUT, in);
        speed = 0.55F;
        collisions++;
        RandomSource r = level.random;
        int rolls = size == 3 ? 2 : size == 2 && r.nextFloat() < 0.4F ? 2 : 1;
        for (int i = 0; i < rolls; i++) {
            ItemStack got = roll(size, r);
            last = got.copy();
            ItemStack left = got;
            for (int k = 1; k <= OUTPUTS && !left.isEmpty(); k++) {
                left = items.insertItem(k, left, false);
            }
            if (!left.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, left);
            }
        }
        level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
        level.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.BLOCKS, 1.4F, 0.8F);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 1.4F);
        setChanged();
        sync();
    }

    private void misfire(ServerLevel level, ItemStack in) {
        in.shrink(1);
        items.setStackInSlot(INPUT, in);
        speed = 0.0F;
        level.blockEvent(worldPosition, getBlockState().getBlock(), 2, 0);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.7F, 1.6F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5, 20, 0.6, 0.4, 0.6, 0.02);
        setChanged();
        sync();
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (level != null && level.isClientSide && (id == 1 || id == 2)) {
            flashAt = System.currentTimeMillis();
            flashKind = id;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    // ---- what collisions make ----

    private record Drop(int weight, Item item, int min, int max) {}

    private static final Item[] DISCS = {Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS, Items.MUSIC_DISC_CHIRP,
            Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL, Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD,
            Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_11, Items.MUSIC_DISC_WAIT, Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_OTHERSIDE,
            Items.MUSIC_DISC_RELIC, Items.MUSIC_DISC_5, Items.MUSIC_DISC_CREATOR, Items.MUSIC_DISC_PRECIPICE};

    /** (Items.AIR in a list: a music disc, picked at random.) */
    private static final List<List<Drop>> TABLES = List.of(List.of(),
            List.of(new Drop(20, Items.GLOWSTONE_DUST, 2, 6), new Drop(20, Items.REDSTONE, 3, 8), new Drop(15, Items.LAPIS_LAZULI, 2, 6),
                    new Drop(15, Items.QUARTZ, 2, 5), new Drop(12, Items.AMETHYST_SHARD, 2, 4), new Drop(10, Items.GOLD_NUGGET, 4, 9),
                    new Drop(8, Items.EMERALD, 1, 2), new Drop(8, Items.PRISMARINE_CRYSTALS, 2, 4), new Drop(6, Items.EXPERIENCE_BOTTLE, 1, 3),
                    new Drop(4, Items.ENDER_PEARL, 1, 1), new Drop(3, Items.DIAMOND, 1, 1)),
            List.of(new Drop(15, Items.EMERALD, 1, 3), new Drop(12, Items.DIAMOND, 1, 2), new Drop(10, Items.ENDER_PEARL, 1, 3),
                    new Drop(10, Items.BLAZE_ROD, 1, 3), new Drop(8, Items.EXPERIENCE_BOTTLE, 3, 6), new Drop(8, Items.AMETHYST_SHARD, 4, 8),
                    new Drop(6, Items.ECHO_SHARD, 1, 1), new Drop(6, Items.SHULKER_SHELL, 1, 1), new Drop(5, Items.AIR, 1, 1),
                    new Drop(5, Items.GHAST_TEAR, 1, 2), new Drop(3, Items.NAUTILUS_SHELL, 1, 2), new Drop(3, Items.NETHERITE_SCRAP, 1, 1),
                    new Drop(2, Items.HEART_OF_THE_SEA, 1, 1)),
            List.of(new Drop(12, Items.DIAMOND, 2, 4), new Drop(10, Items.NETHERITE_SCRAP, 1, 2), new Drop(8, Items.ECHO_SHARD, 1, 3),
                    new Drop(8, Items.SHULKER_SHELL, 1, 2), new Drop(6, Items.AIR, 1, 1), new Drop(6, Items.EXPERIENCE_BOTTLE, 8, 16),
                    new Drop(5, Items.TOTEM_OF_UNDYING, 1, 1), new Drop(4, Items.HEART_OF_THE_SEA, 1, 1), new Drop(4, Items.ENCHANTED_GOLDEN_APPLE, 1, 1),
                    new Drop(3, Items.DRAGON_BREATH, 1, 3), new Drop(2, Items.TRIDENT, 1, 1), new Drop(3, Items.NETHER_STAR, 1, 1)));

    private static ItemStack roll(int size, RandomSource r) {
        List<Drop> table = TABLES.get(size);
        int total = 0;
        for (Drop d : table) {
            total += d.weight;
        }
        int pick = r.nextInt(total);
        for (Drop d : table) {
            pick -= d.weight;
            if (pick < 0) {
                Item item = d.item == Items.AIR ? DISCS[r.nextInt(DISCS.length)] : d.item;
                return new ItemStack(item, d.min + r.nextInt(d.max - d.min + 1));
            }
        }
        return new ItemStack(Items.GLOWSTONE_DUST);
    }

    /** Every drop at a size, for the guide and its screen's list (a music disc for any). */
    public static List<ItemStack> drops(int size) {
        List<ItemStack> out = new ArrayList<>();
        for (Drop d : TABLES.get(size)) {
            out.add(new ItemStack(d.item == Items.AIR ? Items.MUSIC_DISC_CAT : d.item));
        }
        return out;
    }

    @Override
    protected void saveMore(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putFloat("speed", speed);
        tag.putInt("collisions", collisions);
        if (!last.isEmpty()) {
            tag.put("last", last.save(registries));
        }
    }

    @Override
    protected void loadMore(CompoundTag tag, HolderLookup.Provider registries) {
        speed = tag.getFloat("speed");
        collisions = tag.getInt("collisions");
        last = tag.contains("last") ? ItemStack.parseOptional(registries, tag.getCompound("last")) : ItemStack.EMPTY;
    }
}
