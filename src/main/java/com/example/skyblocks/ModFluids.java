package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Sky Liquids: one liquid (fluid, block, bucket) for every Shadered sky type, made automatically from Shadered's
 * SkyblockType list (Shadered's skies, ours, and any an addon adds), like Shadered makes an Illusorite ore for each.
 * They behave like water (flow, swim in them, buckets, infinite sources) and show their sky (client/SkyLiquidRenderer).
 * Names: {@code <sky>_liquid} (Space -> space_liquid, Nebula -> nebula_liquid) and {@code <sky>_liquid_bucket}.
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, SkyBlocksMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, SkyBlocksMod.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, SkyBlocksMod.MOD_ID);

    /** Bucket + any Shadered sky block = that sky's liquid bucket. */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<SkyLiquidBucketRecipe>> BUCKET_RECIPE =
            RECIPES.register("sky_liquid_bucket", () -> new SimpleCraftingRecipeSerializer<>(SkyLiquidBucketRecipe::new));

    /** One sky's liquid. */
    public static final class SkyLiquid {
        public final SkyblockType sky;
        public final String name;
        public DeferredHolder<FluidType, FluidType> type;
        public DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        public DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        public DeferredBlock<LiquidBlock> block;
        public DeferredItem<BucketItem> bucket;

        SkyLiquid(SkyblockType sky, String name) {
            this.sky = sky;
            this.name = name;
        }

        /** The sky's name ("Space", "Nebula"...). */
        public Component skyName() {
            return Component.translatable(sky.getTranslationKey());
        }

        /** Like water: spreads 7 blocks (4 to find a slope), one step every 5 ticks. */
        BaseFlowingFluid.Properties properties() {
            return new BaseFlowingFluid.Properties(type, source, flowing)
                    .block(block)
                    .bucket(bucket)
                    .slopeFindDistance(4)
                    .levelDecreasePerBlock(1)
                    .tickRate(5)
                    .explosionResistance(100.0F);
        }
    }

    public static final List<SkyLiquid> ALL;
    private static final Map<SkyblockType, SkyLiquid> BY_SKY = new EnumMap<>(SkyblockType.class);
    private static Map<FluidType, SkyLiquid> byType;

    static {
        List<SkyLiquid> all = new ArrayList<>();
        for (SkyblockType sky : SkyblockType.values()) {
            String name = sky.name().toLowerCase(Locale.ROOT).replaceFirst("^skyblocks_", "") + "_liquid";
            SkyLiquid liquid = new SkyLiquid(sky, name);
            liquid.type = TYPES.register(name, () -> new SkyFluidType(liquid));
            liquid.source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(liquid.properties()));
            liquid.flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(liquid.properties()));
            liquid.block = ModBlocks.BLOCKS.register(name, () -> new SkyLiquidBlock(liquid, liquid.source.get(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.COLOR_BLACK)));
            liquid.bucket = ModItems.ITEMS.register(name + "_bucket", () -> new SkyLiquidBucketItem(liquid, liquid.source.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
            all.add(liquid);
            BY_SKY.put(sky, liquid);
        }
        ALL = Collections.unmodifiableList(all);
    }

    /** The liquid of a sky, or null. */
    public static SkyLiquid of(SkyblockType sky) {
        return sky == null ? null : BY_SKY.get(sky);
    }

    /** The Sky Liquid a fluid type belongs to, or null if it isn't one. */
    public static SkyLiquid of(FluidType type) {
        if (byType == null) {
            Map<FluidType, SkyLiquid> map = new IdentityHashMap<>();
            for (SkyLiquid liquid : ALL) {
                map.put(liquid.type.get(), liquid);
            }
            byType = map;
        }
        return byType.get(type);
    }

    /** Water-like, named after its sky ("Nebula Liquid"). */
    static final class SkyFluidType extends FluidType {
        private final SkyLiquid liquid;

        SkyFluidType(SkyLiquid liquid) {
            super(Properties.create()
                    .descriptionId("block.skyblocks." + liquid.name)
                    .canSwim(true)
                    .canDrown(true)
                    .canPushEntity(true)
                    .supportsBoating(true)
                    .canExtinguish(true)
                    .canHydrate(true)
                    .canConvertToSource(true)
                    .fallDistanceModifier(0.0F)
                    .motionScale(0.014D)
                    .pathType(PathType.WATER)
                    .adjacentPathType(PathType.WATER_BORDER)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                    .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH));
            this.liquid = liquid;
        }

        @Override
        public Component getDescription() {
            return Component.translatable("block.skyblocks.sky_liquid", liquid.skyName());
        }

        @Override
        public Component getDescription(net.neoforged.neoforge.fluids.FluidStack stack) {
            return getDescription();
        }
    }

    static final class SkyLiquidBlock extends LiquidBlock {
        private final SkyLiquid liquid;

        SkyLiquidBlock(SkyLiquid liquid, FlowingFluid fluid, Properties properties) {
            super(fluid, properties);
            this.liquid = liquid;
        }

        @Override
        public net.minecraft.network.chat.MutableComponent getName() {
            return Component.translatable("block.skyblocks.sky_liquid", liquid.skyName());
        }
    }

    public static final class SkyLiquidBucketItem extends BucketItem {
        public final SkyLiquid liquid;

        SkyLiquidBucketItem(SkyLiquid liquid, Fluid fluid, Properties properties) {
            super(fluid, properties);
            this.liquid = liquid;
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.translatable("item.skyblocks.sky_liquid_bucket", liquid.skyName());
        }
    }

    private ModFluids() {}
}
