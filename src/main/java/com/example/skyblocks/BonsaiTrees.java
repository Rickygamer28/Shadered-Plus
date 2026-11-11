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
            TreeConfiguration tc = treeOf(level, bi.getBlock());
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

    /** The tree a sapling grows (its tree grower's tree, read without growing it), or null. */
    @Nullable
    private static TreeConfiguration treeOf(Level level, Block sapling) {
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
                            if (cf != null && cf.config() instanceof TreeConfiguration tc) {
                                return tc;
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
