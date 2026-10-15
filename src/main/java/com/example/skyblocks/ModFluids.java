package com.example.skyblocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Space Liquid: behaves like water (flows, swim in it, bucket, makes infinite sources), and its surface shows
 * Shadered's Space sky (client/SpaceLiquidSky queues a sky cube for each liquid block, cut to the liquid's height).
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, SkyBlocksMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, SkyBlocksMod.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> SPACE_TYPE = TYPES.register("space_liquid",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.skyblocks.space_liquid")
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
                    .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SPACE = FLUIDS.register("space_liquid",
            () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_SPACE = FLUIDS.register("flowing_space_liquid",
            () -> new BaseFlowingFluid.Flowing(properties()));

    /** Like water: spreads 7 blocks (4 to find a slope), one tick step every 5 ticks. */
    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(SPACE_TYPE, SPACE, FLOWING_SPACE)
                .block(ModBlocks.SPACE_LIQUID)
                .bucket(ModItems.SPACE_LIQUID_BUCKET)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(100.0F);
    }

    private ModFluids() {}
}
