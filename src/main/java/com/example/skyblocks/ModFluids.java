package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sky Liquids: one liquid (fluid, block, bucket) for every Shadered sky type, made automatically from Shadered's
 * SkyblockType list (Shadered's skies, ours, and any an addon adds), like Shadered makes an Illusorite ore for each.
 * They behave like water (flow, swim in them, buckets, infinite sources) and show their sky (client/SkyLiquidRenderer).
 * Names: {@code <sky>_liquid} (Space -> space_liquid, Nebula -> nebula_liquid) and {@code <sky>_liquid_bucket}.
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, SkyBlocksMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, SkyBlocksMod.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, SkyBlocksMod.MOD_ID);

    /** Bucket + any Shadered sky block = that sky's liquid bucket. */
    public static final RegistryObject<SimpleCraftingRecipeSerializer<SkyLiquidBucketRecipe>> BUCKET_RECIPE =
            RECIPES.register("sky_liquid_bucket", () -> new SimpleCraftingRecipeSerializer<>(SkyLiquidBucketRecipe::new));

    /** One sky's liquid. */
    public static final class SkyLiquid {
        public final SkyblockType sky;
        public final String name;
        public RegistryObject<FluidType> type;
        public RegistryObject<ForgeFlowingFluid.Source> source;
        public RegistryObject<ForgeFlowingFluid.Flowing> flowing;
        public RegistryObject<LiquidBlock> block;
        public RegistryObject<BucketItem> bucket;

        SkyLiquid(SkyblockType sky, String name) {
            this.sky = sky;
            this.name = name;
        }

        /** The sky's name ("Space", "Nebula"...). */
        public Component skyName() {
            return Component.translatable(sky.getTranslationKey());
        }

        /** Like water: spreads 7 blocks (4 to find a slope), one step every 5 ticks. */
        ForgeFlowingFluid.Properties properties() {
            return new ForgeFlowingFluid.Properties(type, source, flowing)
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
            liquid.source = FLUIDS.register(name, () -> new ForgeFlowingFluid.Source(liquid.properties()));
            liquid.flowing = FLUIDS.register("flowing_" + name, () -> new ForgeFlowingFluid.Flowing(liquid.properties()));
            liquid.block = ModBlocks.BLOCKS.register(name, () -> new SkyLiquidBlock(liquid, liquid.source,
                    BlockBehaviour.Properties.copy(Blocks.WATER).mapColor(MapColor.COLOR_BLACK)));
            liquid.bucket = ModItems.ITEMS.register(name + "_bucket", () -> new SkyLiquidBucketItem(liquid, liquid.source,
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
                    .pathType(BlockPathTypes.WATER)
                    .adjacentPathType(BlockPathTypes.WATER_BORDER)
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
        public Component getDescription(net.minecraftforge.fluids.FluidStack stack) {
            return getDescription();
        }

        /** Its own surface isn't drawn (see client/SkyLiquidRenderer); under the surface: deep space purple fog. */
        @Override
        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
            consumer.accept(com.example.skyblocks.client.SkyLiquidLook.INSTANCE);
        }
    }

    static final class SkyLiquidBlock extends LiquidBlock {
        private final SkyLiquid liquid;

        SkyLiquidBlock(SkyLiquid liquid, java.util.function.Supplier<? extends FlowingFluid> fluid, Properties properties) {
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

        SkyLiquidBucketItem(SkyLiquid liquid, java.util.function.Supplier<? extends Fluid> fluid, Properties properties) {
            super(fluid, properties);
            this.liquid = liquid;
        }

        /**
         * The bucket's fill shows the sky (client/SkyLiquidBucketRenderer). Called from Item's constructor, before
         * {@link #liquid} is set: the sky is looked up when the renderer is first needed.
         */
        @Override
        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(com.example.skyblocks.client.SkyLiquidBucketRenderer.extensions(() -> liquid.sky));
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.translatable("item.skyblocks.sky_liquid_bucket", liquid.skyName());
        }
    }

    private ModFluids() {}
}
