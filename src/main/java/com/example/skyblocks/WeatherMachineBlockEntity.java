package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The weather machine: its base with weather masts stacked on it (2-3 small, 4-5 medium, 6 or more large), a dish on
 * top. Set it to clear, rain or thunder: it takes a burst of energy to turn the weather, then a little all the while to
 * hold it (the weather is the whole world's, as always in Minecraft). In a thunderstorm, with open sky over its dish, it
 * draws lightning down to its dish - more often the bigger it is - and catches lightning striking near it (out to 16, 32
 * or 64 blocks): each strike is a burst of energy, given to what's beside it and its masts (a power core, say), the
 * rest kept for its own use.
 */
public class WeatherMachineBlockEntity extends MachineBlockEntity {
    public static final String[] MODES = {"off", "clear", "rain", "thunder"};
    /** Energy to turn the weather to each, and a tick to hold it (both less the bigger it is). */
    private static final int[] TURN = {0, 300_000, 500_000, 1_200_000};
    private static final int[] HOLD = {0, 20, 40, 80};
    private static final float[] THRIFT = {1.0F, 1.0F, 0.75F, 0.5F};
    /** Energy a strike gives, and how far it catches them, at each size. */
    private static final int[] STRIKE = {0, 150_000, 300_000, 600_000};
    private static final int[] REACH = {0, 16, 32, 64};
    private static final int MAX_MASTS = 8;

    /** (The formed ones in each world: lightning near one goes to it.) */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE = new HashMap<>();

    private int mode, masts;
    private int charged;
    private boolean holding;
    private long harvested;
    private final Battery caught = new Battery(20_000_000, () -> {});
    private int nextStrike = 200, holdTick;

    /** (The client: when its dish was last struck; when it last let off a cloud.) */
    public long clientStruckAt, clientCloudAt;

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

    public WeatherMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WEATHER_MACHINE.get(), pos, state, 0);
    }

    @Override
    public MachineFrameBlock.Kind kind() {
        return MachineFrameBlock.Kind.WEATHER;
    }

    @Override
    protected int capacityFor(int size) {
        return 2_000_000 * Math.max(1, size);
    }

    @Override
    public IEnergyStorage energyHandler() {
        return input;
    }

    @Nullable
    @Override
    public IItemHandler itemHandler() {
        return null;
    }

    public int mode() {
        return mode;
    }

    public int masts() {
        return masts;
    }

    public boolean holding() {
        return holding;
    }

    public long harvested() {
        return harvested;
    }

    public int reach() {
        return REACH[size];
    }

    /** How far through turning the weather (0..1; 1 holding it). */
    public float charge() {
        if (holding) {
            return 1.0F;
        }
        int need = turnCost();
        return need <= 0 ? 0.0F : Math.min(1.0F, (float) charged / need);
    }

    private int turnCost() {
        return (int) (TURN[mode] * THRIFT[size]);
    }

    public int holdCost() {
        return (int) Math.ceil(HOLD[mode] * THRIFT[size]);
    }

    /** Where its dish is: over its top mast. */
    public BlockPos dish() {
        return worldPosition.above(masts + 1);
    }

    public void setMode(int mode) {
        mode = Math.floorMod(mode, MODES.length);
        if (mode != this.mode) {
            this.mode = mode;
            charged = 0;
            holding = false;
            setChanged();
            sync();
        }
    }

    /** Its masts: stacked straight up from it, two or more. */
    @Nullable
    @Override
    protected Found detect() {
        List<BlockPos> found = new ArrayList<>();
        for (int i = 1; i <= MAX_MASTS && isFrame(worldPosition.above(i)); i++) {
            found.add(worldPosition.above(i));
        }
        int n = found.size();
        masts = n;
        return n < 2 ? null : new Found(n >= 6 ? 3 : n >= 4 ? 2 : 1, found);
    }

    @Override
    protected void formedChanged() {
        if (level == null) {
            return;
        }
        Set<BlockPos> set = ACTIVE.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        if (size > 0) {
            set.add(worldPosition.immutable());
        } else {
            set.remove(worldPosition);
            holding = false;
            charged = 0;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            Set<BlockPos> set = ACTIVE.get(level.dimension());
            if (set != null) {
                set.remove(worldPosition);
            }
        }
    }

    private boolean matches(ServerLevel level) {
        return switch (mode) {
            case 1 -> !level.isRaining();
            case 2 -> level.isRaining() && !level.isThundering();
            case 3 -> level.isRaining() && level.isThundering();
            default -> true;
        };
    }

    private void apply(ServerLevel level) {
        switch (mode) {
            case 1 -> level.setWeatherParameters(12000, 0, false, false);
            case 2 -> level.setWeatherParameters(0, 12000, true, false);
            case 3 -> level.setWeatherParameters(0, 12000, true, true);
            default -> {}
        }
    }

    @Override
    protected void work(ServerLevel level) {
        ACTIVE.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(worldPosition.immutable());
        // the weather: turning it (a burst of energy, drawn in over a few seconds), then holding it
        if (mode != 0) {
            if (holding) {
                if (energy.take(holdCost())) {
                    if (++holdTick >= 200 || !matches(level)) {
                        holdTick = 0;
                        apply(level); // (kept going: its time to change put off again)
                    }
                } else {
                    holding = false; // (out of energy: the weather goes its own way)
                    sync();
                }
            } else {
                int need = turnCost();
                int take = Math.min(Math.min(energy.getEnergyStored(), 25_000 * size), need - charged);
                if (take > 0 && energy.take(take)) {
                    charged += take;
                    if (charged % 100_000 < take) {
                        sync();
                    }
                }
                if (charged >= need) {
                    charged = 0;
                    holding = true;
                    holdTick = 0;
                    apply(level);
                    level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.5F, 0.7F);
                    level.blockEvent(worldPosition, getBlockState().getBlock(), 3, 0);
                    sync();
                }
            }
        }
        // a thunderstorm: lightning drawn down to its dish now and then (more often the bigger it is)
        if (level.isThundering() && level.canSeeSky(dish()) && --nextStrike <= 0) {
            nextStrike = (900 + level.random.nextInt(1500)) / size;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(dish()));
                bolt.setVisualOnly(true);
                bolt.getPersistentData().putBoolean("skyblocks_caught", true);
                level.addFreshEntity(bolt);
                struck(level);
            }
        }
        // what it's caught: given to what's beside it, the rest kept for itself
        if (caught.getEnergyStored() > 0) {
            giveEnergy(caught, 50_000 * size);
            int top = Math.min(caught.getEnergyStored(), energy.getMaxEnergyStored() - energy.getEnergyStored());
            if (top > 0 && caught.take(top)) {
                energy.add(top);
            }
        }
    }

    /** A strike on its dish: its energy caught. */
    void struck(ServerLevel level) {
        caught.add(STRIKE[size]);
        harvested += STRIKE[size];
        level.blockEvent(worldPosition, getBlockState().getBlock(), 4, 0);
        setChanged();
        sync();
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (level != null && level.isClientSide && (id == 3 || id == 4)) {
            clientStruckAt = System.currentTimeMillis();
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** Lightning striking near a formed weather machine (open sky over its dish) goes to its dish instead, harmless. */
    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onJoin(EntityJoinLevelEvent event) {
            if (!(event.getEntity() instanceof LightningBolt bolt) || !(event.getLevel() instanceof ServerLevel level)
                    || bolt.getPersistentData().getBoolean("skyblocks_caught")) {
                return;
            }
            Set<BlockPos> set = ACTIVE.get(level.dimension());
            if (set == null || set.isEmpty()) {
                return;
            }
            WeatherMachineBlockEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : new ArrayList<>(set)) {
                if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof WeatherMachineBlockEntity m) || m.size == 0) {
                    set.remove(p);
                    continue;
                }
                double d = Vec3.atCenterOf(m.dish()).distanceTo(bolt.position());
                if (d <= m.reach() && d < bestD && level.canSeeSky(m.dish())) {
                    best = m;
                    bestD = d;
                }
            }
            if (best != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(best.dish()));
                bolt.setVisualOnly(true);
                bolt.getPersistentData().putBoolean("skyblocks_caught", true);
                best.struck(level);
            }
        }
    }

    @Override
    protected void saveMore(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("mode", mode);
        tag.putInt("masts", masts);
        tag.putInt("charged", charged);
        tag.putBoolean("holding", holding);
        tag.putLong("harvested", harvested);
        tag.putInt("caught", caught.getEnergyStored());
    }

    @Override
    protected void loadMore(CompoundTag tag, HolderLookup.Provider registries) {
        mode = Math.floorMod(tag.getInt("mode"), MODES.length);
        masts = tag.getInt("masts");
        charged = tag.getInt("charged");
        holding = tag.getBoolean("holding");
        harvested = tag.getLong("harvested");
        caught.set(tag.getInt("caught"));
    }
}
