package com.example.skyblocks;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What tree a sapling makes in a bonsai pot - from any mod: its bark (log) and leaves, flowering leaves when it's grown,
 * the petals or leaves that drift off it, and fruit it drops now and then. Worked out (in this order):
 * <ol>
 * <li>a data pack's entry for that sapling (data/&lt;ns&gt;/skyblocks_bonsai/*.json; Shadered+'s own are the vanilla trees);</li>
 * <li>the tree the sapling grows: its trunk and leaves, as the tree's own definition gives them;</li>
 * <li>its name: something_sapling -> something_log / _stem / _wood, something_leaves / _wart_block;</li>
 * <li>oak, if nothing else.</li>
 * </ol>
 * Flowering leaves, if not given: flowering_&lt;leaves&gt; in the same mod, if there is one.
 * <p>A file: {"trees": [{"sapling": "mod:maple_sapling", "log": "mod:maple_log", "leaves": "mod:red_maple_leaves",
 * "flowering": "...", "particle": "minecraft:cherry_leaves", "fruit": "minecraft:apple", "fruit_chance": 0.1}]} - every
 * field but "sapling" optional; entries for things not installed are skipped.
 */
public final class BonsaiTrees {
    private BonsaiTrees() {}

    /** A tree, worked out: log, leaves, flowering leaves (or null), a particle (or null: bits of its leaves), fruit. */
    public record Tree(BlockState log, BlockState leaves, @Nullable BlockState flowering, @Nullable ResourceLocation particle, ItemStack fruit,
                       float fruitChance) {}

    private record Entry(@Nullable Block log, @Nullable Block leaves, @Nullable Block flowering, @Nullable ResourceLocation particle,
                         @Nullable Item fruit, float fruitChance) {}

    private static final Map<Item, Entry> ENTRIES = new HashMap<>();

    /** Can it go in a bonsai pot: a sapling (by the tag every tree mod uses), or one the data files name. */
    public static boolean plantable(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ItemTags.SAPLINGS) || ENTRIES.containsKey(stack.getItem()) || BonsaiBlock.Kind.of(stack) != null);
    }

    public static Tree resolve(Level level, ItemStack sapling) {
        Item item = sapling.getItem();
        Entry e = ENTRIES.get(item);
        BlockState log = null, leaves = null;
        if (item instanceof BlockItem bi) {
            ConfiguredFeature<?, ?> feature = featureOf(level, bi.getBlock());
            TreeConfiguration tc = feature != null && feature.config() instanceof TreeConfiguration t ? t : null;
            if (tc != null) {
                RandomSource r = RandomSource.create(1L);
                try {
                    log = tc.trunkProvider.getState(r, BlockPos.ZERO);
                    leaves = tc.foliageProvider.getState(r, BlockPos.ZERO);
                } catch (RuntimeException ignored) {
                    // (a provider that needs a real world: by name, then)
                }
            }
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String base = id.getPath().replaceAll("_(sapling|propagule|seedling|sprout|fungus)$", "");
        if (log == null || log.isAir()) {
            log = block(id.getNamespace(), base, "_log", "_stem", "_wood", "_hyphae");
        }
        if (leaves == null || leaves.isAir()) {
            leaves = block(id.getNamespace(), base, "_leaves", "_wart_block");
        }
        if (e != null && e.log != null) log = e.log.defaultBlockState();
        if (e != null && e.leaves != null) leaves = e.leaves.defaultBlockState();
        if (log == null) log = Blocks.OAK_LOG.defaultBlockState();
        if (leaves == null) leaves = Blocks.OAK_LEAVES.defaultBlockState();
        BlockState flowering = null;
        if (e != null && e.flowering != null) {
            flowering = e.flowering.defaultBlockState();
        } else {
            ResourceLocation lid = BuiltInRegistries.BLOCK.getKey(leaves.getBlock());
            flowering = block(lid.getNamespace(), "flowering_" + lid.getPath(), "");
        }
        return new Tree(log, leaves, flowering, e == null ? null : e.particle, e == null || e.fruit == null ? ItemStack.EMPTY : new ItemStack(e.fruit),
                e == null ? 0.0F : e.fruitChance);
    }

    /** The block ns:base+suffix, for the first suffix there is one; null if none. */
    @Nullable
    private static BlockState block(String ns, String base, String... suffixes) {
        for (String s : suffixes) {
            ResourceLocation rl = ResourceLocation.tryBuild(ns, base + s);
            if (rl != null) {
                Optional<Block> b = BuiltInRegistries.BLOCK.getOptional(rl);
                if (b.isPresent() && b.get() != Blocks.AIR) {
                    return b.get().defaultBlockState();
                }
            }
        }
        return null;
    }

    /** The tree a sapling grows (its tree grower's tree, as it's defined), or null. */
    @Nullable
    private static ConfiguredFeature<?, ?> featureOf(Level level, Block sapling) {
        try {
            for (Class<?> c = sapling.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!TreeGrower.class.isAssignableFrom(f.getType())) {
                        continue;
                    }
                    f.setAccessible(true);
                    Object grower = f.get(sapling);
                    if (grower == null) {
                        continue;
                    }
                    // (its plain tree first, then any others it has: mega, flowering...)
                    List<Field> options = new ArrayList<>();
                    for (Field g : TreeGrower.class.getDeclaredFields()) {
                        if (g.getType() == Optional.class) {
                            if (g.getName().equals("tree")) options.add(0, g);
                            else options.add(g);
                        }
                    }
                    for (Field g : options) {
                        g.setAccessible(true);
                        Optional<?> o = (Optional<?>) g.get(grower);
                        if (o != null && o.isPresent() && o.get() instanceof ResourceKey<?> key) {
                            @SuppressWarnings("unchecked")
                            ConfiguredFeature<?, ?> cf = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                                    .get((ResourceKey<ConfiguredFeature<?, ?>>) key);
                            if (cf != null) {
                                return cf;
                            }
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // (not a sapling we can read: by name)
        }
        return null;
    }

    // ---- the real tree, grown somewhere nobody sees ----

    /** The ground the tree's grown on (its trunk starts at 0, GROUND, 0); a tree bigger than this many blocks isn't kept. */
    public static final int GROUND = 64, MOST_BLOCKS = 6000;

    /**
     * The sapling's real tree, grown (as the sapling would grow it, with this seed) in a pretend world of grass with
     * nothing else in it: every block it placed, from the ground up. Null if it can't be grown that way (not a sapling
     * with a tree to read, a tree that needs a real world, or one too big for a pot).
     */
    @Nullable
    public static Map<BlockPos, BlockState> grow(net.minecraft.server.level.ServerLevel level, ItemStack sapling, long seed) {
        if (!(sapling.getItem() instanceof BlockItem bi)) {
            return null;
        }
        ConfiguredFeature<?, ?> feature = featureOf(level, bi.getBlock());
        if (feature == null) {
            return null;
        }
        Map<BlockPos, BlockState> placed = new HashMap<>();
        RandomSource random = RandomSource.create(seed);
        net.minecraft.world.level.WorldGenLevel fake = PretendWorld.of(level, placed, random);
        try {
            if (!feature.place(fake, level.getChunkSource().getGenerator(), random, new BlockPos(0, GROUND, 0))) {
                return null;
            }
        } catch (RuntimeException | LinkageError e) {
            SkyBlocksMod.LOGGER.debug("Couldn't grow {} for a bonsai: {}", BuiltInRegistries.ITEM.getKey(sapling.getItem()), e.toString());
            return null;
        }
        placed.keySet().removeIf(p -> p.getY() < GROUND); // (the dirt it put under itself)
        placed.values().removeIf(BlockState::isAir);
        return placed.isEmpty() || placed.size() > MOST_BLOCKS ? null : placed;
    }

    /** A world for growing one tree in: grass below GROUND, air above, everything it places kept. */
    private static final class PretendWorld implements java.lang.reflect.InvocationHandler {
        private final net.minecraft.server.level.ServerLevel level;
        private final Map<BlockPos, BlockState> placed;
        private final RandomSource random;

        private PretendWorld(net.minecraft.server.level.ServerLevel level, Map<BlockPos, BlockState> placed, RandomSource random) {
            this.level = level;
            this.placed = placed;
            this.random = random;
        }

        static net.minecraft.world.level.WorldGenLevel of(net.minecraft.server.level.ServerLevel level, Map<BlockPos, BlockState> placed, RandomSource random) {
            return (net.minecraft.world.level.WorldGenLevel) java.lang.reflect.Proxy.newProxyInstance(
                    net.minecraft.world.level.WorldGenLevel.class.getClassLoader(), new Class<?>[] {net.minecraft.world.level.WorldGenLevel.class},
                    new PretendWorld(level, placed, random));
        }

        private BlockState at(BlockPos pos) {
            BlockState s = placed.get(pos);
            if (s != null) {
                return s;
            }
            return pos.getY() < GROUND - 1 ? Blocks.DIRT.defaultBlockState() : pos.getY() == GROUND - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, java.lang.reflect.Method m, Object[] a) throws Throwable {
            String n = m.getName();
            int argc = a == null ? 0 : a.length;
            switch (n) {
                case "getBlockState":
                    if (argc == 1 && a[0] instanceof BlockPos p) return at(p.immutable());
                    break;
                case "getFluidState":
                    if (argc == 1 && a[0] instanceof BlockPos p) return at(p.immutable()).getFluidState();
                    break;
                case "isStateAtPosition":
                    return ((java.util.function.Predicate<BlockState>) a[1]).test(at(((BlockPos) a[0]).immutable()));
                case "isFluidAtPosition":
                    return ((java.util.function.Predicate<net.minecraft.world.level.material.FluidState>) a[1]).test(at(((BlockPos) a[0]).immutable()).getFluidState());
                case "setBlock":
                    placed.put(((BlockPos) a[0]).immutable(), (BlockState) a[1]);
                    return true;
                case "removeBlock":
                    placed.put(((BlockPos) a[0]).immutable(), Blocks.AIR.defaultBlockState());
                    return true;
                case "destroyBlock":
                    placed.put(((BlockPos) a[0]).immutable(), Blocks.AIR.defaultBlockState());
                    return true;
                case "getMinBuildHeight":
                    return 0;
                case "getHeight":
                    if (argc == 0) return 384;
                    if (argc == 3) return GROUND;
                    break;
                case "getHeightmapPos":
                    return new BlockPos(((BlockPos) a[1]).getX(), GROUND, ((BlockPos) a[1]).getZ());
                case "getRandom":
                    return random;
                case "getLevel":
                    return level;
                case "getServer":
                    return level.getServer();
                case "registryAccess":
                    return level.registryAccess();
                case "enabledFeatures":
                    return level.enabledFeatures();
                case "dimensionType":
                    return level.dimensionType();
                case "getBiome":
                    return level.getBiome(level.getSharedSpawnPos());
                case "getBiomeManager":
                    return level.getBiomeManager();
                case "getWorldBorder":
                    return level.getWorldBorder();
                case "getLevelData":
                    return level.getLevelData();
                case "getCurrentDifficultyAt":
                    return level.getCurrentDifficultyAt(BlockPos.ZERO);
                case "ensureCanWrite":
                case "hasChunk":
                case "hasChunkAt":
                case "isLoaded":
                    return true;
                case "isClientSide":
                    return false;
                case "getBlockEntity":
                    if (argc == 1) return null;
                    break;
                case "toString":
                    return "bonsai's pretend world";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == a[0];
                default:
                    break;
            }
            if (m.isDefault()) {
                return java.lang.reflect.InvocationHandler.invokeDefault(proxy, m, a);
            }
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == long.class) return 0L;
            if (r == float.class) return 0.0F;
            if (r == double.class) return 0.0;
            if (r == byte.class) return (byte) 0;
            if (r == short.class) return (short) 0;
            if (r == char.class) return (char) 0;
            if (r == Optional.class) return Optional.empty();
            return null;
        }
    }

    // ---- the data files ----

    static void register(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), "skyblocks_bonsai");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            ENTRIES.clear();
            for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
                try {
                    JsonArray trees = file.getValue().getAsJsonObject().getAsJsonArray("trees");
                    for (JsonElement t : trees) {
                        JsonObject o = t.getAsJsonObject();
                        Optional<Item> sapling = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(o.get("sapling").getAsString()));
                        if (sapling.isEmpty()) {
                            continue; // (that mod isn't here)
                        }
                        ENTRIES.put(sapling.get(), new Entry(blockOf(o, "log"), blockOf(o, "leaves"), blockOf(o, "flowering"),
                                o.has("particle") ? ResourceLocation.parse(o.get("particle").getAsString()) : null,
                                o.has("fruit") ? BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(o.get("fruit").getAsString())).orElse(null) : null,
                                o.has("fruit_chance") ? o.get("fruit_chance").getAsFloat() : 0.1F));
                    }
                } catch (RuntimeException ex) {
                    SkyBlocksMod.LOGGER.warn("Bad bonsai tree file {}: {}", file.getKey(), ex.toString());
                }
            }
        }

        @Nullable
        private static Block blockOf(JsonObject o, String key) {
            return o.has(key) ? BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(o.get(key).getAsString())).orElse(null) : null;
        }
    }
}
