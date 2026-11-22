package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sky Shapes: stairs, slabs, walls, panes, carpets and buttons of every Shadered sky, made automatically from
 * Shadered's SkyblockType list (like the Sky Liquids, ModFluids): a sky an addon adds gets its shapes too. Each shows
 * its sky on all its faces (drawn by client/SkyLiquidRenderer, like the liquids: the blocks themselves draw nothing).
 * Names: {@code <sky>_stairs}, {@code <sky>_slab}, ... ({@code space_stairs}, {@code nebula_wall}).
 */
public final class ModSkyShapes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, SkyBlocksMod.MOD_ID);

    /** Sky blocks (and glass panes, a button) in the crafting grid = that sky's shapes. */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<ShapeRecipe>> RECIPE =
            RECIPES.register("sky_shape", () -> new SimpleCraftingRecipeSerializer<>(ShapeRecipe::new));

    /** A Sky Brush and a sky block = the brush, dipped in that sky (SkyBrushItem). */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<SkyBrushItem.DipRecipe>> DIP_RECIPE =
            RECIPES.register("sky_brush_dip", () -> new SimpleCraftingRecipeSerializer<>(SkyBrushItem.DipRecipe::new));

    /** The kinds of shape. */
    public enum Kind {
        STAIRS("stairs"), SLAB("slab"), WALL("wall"), PANE("pane"), CARPET("carpet"), BUTTON("button");

        public final String id;

        Kind(String id) {
            this.id = id;
        }
    }

    /** One sky's shapes. */
    public static final class Shapes {
        public final SkyblockType sky;
        public final String prefix;
        public final Map<Kind, DeferredBlock<Block>> blocks = new EnumMap<>(Kind.class);
        public final Map<Kind, DeferredItem<BlockItem>> items = new EnumMap<>(Kind.class);

        Shapes(SkyblockType sky, String prefix) {
            this.sky = sky;
            this.prefix = prefix;
        }

        public Component skyName() {
            return Component.translatable(sky.getTranslationKey());
        }
    }

    /** A block that's one of the shapes: which sky, which kind. */
    public interface Shape {
        SkyblockType sky();

        Kind kind();
    }

    public static final List<Shapes> ALL;
    private static final Map<SkyblockType, Shapes> BY_SKY = new EnumMap<>(SkyblockType.class);

    static {
        List<Shapes> all = new ArrayList<>();
        for (SkyblockType sky : SkyblockType.values()) {
            String prefix = sky.name().toLowerCase(Locale.ROOT).replaceFirst("^skyblocks_", "");
            Shapes shapes = new Shapes(sky, prefix);
            for (Kind kind : Kind.values()) {
                String name = prefix + "_" + kind.id;
                DeferredBlock<Block> block = ModBlocks.BLOCKS.register(name, () -> make(shapes, kind));
                shapes.blocks.put(kind, block);
                shapes.items.put(kind, ModItems.ITEMS.register(name, () -> new ShapeItem(block.get(), new Item.Properties())));
            }
            all.add(shapes);
            BY_SKY.put(sky, shapes);
        }
        ALL = Collections.unmodifiableList(all);
    }

    /** (makes the class load, and with it the blocks and items register) */
    public static void init() {}

    /** A sky's shapes, or null. */
    public static Shapes of(SkyblockType sky) {
        return sky == null ? null : BY_SKY.get(sky);
    }

    /** Glassy, a little sturdier than glass; no tool needed. */
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.8F).sound(SoundType.AMETHYST)
                .noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false);
    }

    private static Block make(Shapes shapes, Kind kind) {
        SkyblockType sky = shapes.sky;
        return switch (kind) {
            case STAIRS -> new Stairs(sky, Blocks.GLASS.defaultBlockState(), properties());
            case SLAB -> new Slab(sky, properties());
            case WALL -> new Wall(sky, properties().forceSolidOn());
            case PANE -> new Pane(sky, properties());
            case CARPET -> new Carpet(sky, properties().strength(0.1F));
            case BUTTON -> new Button(sky, properties().noCollission().strength(0.5F));
        };
    }

    /** "Nebula Stairs": the shape's name with its sky's. */
    private static MutableComponent name(SkyblockType sky, Kind kind) {
        return Component.translatable("block.skyblocks.sky_" + kind.id, Component.translatable(sky.getTranslationKey()));
    }

    /** Drops itself (a double slab two). */
    private static List<ItemStack> drops(Block block, BlockState state) {
        int count = state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
        return List.of(new ItemStack(block.asItem(), count));
    }

    public static final class Stairs extends StairBlock implements Shape {
        private final SkyblockType sky;

        Stairs(SkyblockType sky, BlockState base, Properties properties) {
            super(base, properties);
            this.sky = sky;
        }

        @Override public SkyblockType sky() { return sky; }
        @Override public Kind kind() { return Kind.STAIRS; }
        @Override public MutableComponent getName() { return name(sky, kind()); }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
        @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return drops(this, state); }
    }

    public static final class Slab extends SlabBlock implements Shape {
        private final SkyblockType sky;

        Slab(SkyblockType sky, Properties properties) {
            super(properties);
            this.sky = sky;
        }

        @Override public SkyblockType sky() { return sky; }
        @Override public Kind kind() { return Kind.SLAB; }
        @Override public MutableComponent getName() { return name(sky, kind()); }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
        @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return drops(this, state); }
    }

    public static final class Wall extends WallBlock implements Shape {
        private final SkyblockType sky;

        Wall(SkyblockType sky, Properties properties) {
            super(properties);
            this.sky = sky;
        }

        @Override public SkyblockType sky() { return sky; }
        @Override public Kind kind() { return Kind.WALL; }
        @Override public MutableComponent getName() { return name(sky, kind()); }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
        @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return drops(this, state); }
    }

    public static final class Pane extends IronBarsBlock implements Shape {
        private final SkyblockType sky;

        Pane(SkyblockType sky, Properties properties) {
            super(properties);
            this.sky = sky;
        }

        @Override public SkyblockType sky() { return sky; }
        @Override public Kind kind() { return Kind.PANE; }
        @Override public MutableComponent getName() { return name(sky, kind()); }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
        @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return drops(this, state); }
    }

    public static final class Carpet extends CarpetBlock implements Shape {
        private final SkyblockType sky;

        Carpet(SkyblockType sky, Properties properties) {
            super(properties);
            this.sky = sky;
        }

        @Override public SkyblockType sky() { return sky; }
        @Override public Kind kind() { return Kind.CARPET; }
        @Override public MutableComponent getName() { return name(sky, kind()); }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
        @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return drops(this, state); }
    }

    public static final class Button extends ButtonBlock implements Shape {
        private final SkyblockType sky;

        Button(SkyblockType sky, Properties properties) {
            super(BlockSetType.STONE, 20, properties);
            this.sky = sky;
        }

        @Override public SkyblockType sky() { return sky; }
        @Override public Kind kind() { return Kind.BUTTON; }
        @Override public MutableComponent getName() { return name(sky, kind()); }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
        @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return drops(this, state); }
    }

    /** The item: named like its block. */
    public static final class ShapeItem extends BlockItem {
        ShapeItem(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            return getBlock().getName();
        }
    }

    /**
     * Sky blocks of one sky in the crafting grid, shaped like vanilla's: stairs (6 in a staircase: 4), a slab (3 in a
     * row: 6), a wall (6 in two rows: 6), carpet (2 side by side: 3), a pane (a sky block ringed by 8 glass panes: 8)
     * and a button (a sky block and a stone button: 1).
     */
    public static final class ShapeRecipe extends CustomRecipe {
        public ShapeRecipe(CraftingBookCategory category) {
            super(category);
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            return !result(input).isEmpty();
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            return result(input);
        }

        private static ItemStack result(CraftingInput input) {
            int w = input.width(), h = input.height();
            SkyblockType sky = null;
            int skies = 0, panes = 0, buttons = 0, other = 0;
            boolean[] isSky = new boolean[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    ItemStack stack = input.getItem(x, y);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    SkyblockType s = SkyLiquidBucketRecipe.skyOf(stack);
                    if (s != null) {
                        if (sky != null && s != sky) {
                            return ItemStack.EMPTY; // (two skies)
                        }
                        sky = s;
                        skies++;
                        isSky[y * w + x] = true;
                    } else if (stack.is(Items.GLASS_PANE)) {
                        panes++;
                    } else if (stack.is(Items.STONE_BUTTON)) {
                        buttons++;
                    } else {
                        other++;
                    }
                }
            }
            Shapes shapes = of(sky);
            if (shapes == null || other > 0) {
                return ItemStack.EMPTY;
            }
            if (panes == 8 && skies == 1 && buttons == 0 && w == 3 && h == 3 && isSky[4]) {
                return stack(shapes, Kind.PANE, 8);
            }
            if (buttons == 1 && skies == 1 && panes == 0) {
                return stack(shapes, Kind.BUTTON, 1);
            }
            if (panes > 0 || buttons > 0) {
                return ItemStack.EMPTY;
            }
            if (skies == 6 && w == 3 && h == 3 && (stairs(isSky, false) || stairs(isSky, true))) {
                return stack(shapes, Kind.STAIRS, 4);
            }
            if (skies == 3 && w == 3 && h == 1) {
                return stack(shapes, Kind.SLAB, 6);
            }
            if (skies == 6 && w == 3 && h == 2) {
                return stack(shapes, Kind.WALL, 6);
            }
            if (skies == 2 && w == 2 && h == 1) {
                return stack(shapes, Kind.CARPET, 3);
            }
            return ItemStack.EMPTY;
        }

        /** X.. / XX. / XXX (or mirrored). */
        private static boolean stairs(boolean[] g, boolean mirror) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    int cx = mirror ? 2 - x : x;
                    if (g[y * 3 + x] != (cx <= y)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static ItemStack stack(Shapes shapes, Kind kind, int count) {
            return new ItemStack(shapes.items.get(kind).get(), count);
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width * height >= 2;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return RECIPE.get();
        }
    }

    private ModSkyShapes() {}
}
