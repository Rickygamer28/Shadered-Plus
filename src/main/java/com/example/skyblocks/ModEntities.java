package com.example.skyblocks;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entities: the OIIA cat companion, and its yarn ball. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyBlocksMod.MOD_ID);

    public static final Supplier<EntityType<OiiaCatEntity>> OIIA_CAT = TYPES.register("oiia_cat",
            () -> EntityType.Builder.<OiiaCatEntity>of(OiiaCatEntity::new, MobCategory.CREATURE).sized(0.5F, 0.6F).clientTrackingRange(10)
                    .build("oiia_cat"));

    public static final Supplier<EntityType<YarnBallEntity>> YARN_BALL = TYPES.register("yarn_ball",
            () -> EntityType.Builder.<YarnBallEntity>of(YarnBallEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(8)
                    .updateInterval(1).build("yarn_ball"));

    /** A line of sky drawn in the air with the sky wand. */
    public static final Supplier<EntityType<SkyTrailEntity>> SKY_TRAIL = TYPES.register("sky_trail",
            () -> EntityType.Builder.<SkyTrailEntity>of(SkyTrailEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(10)
                    .updateInterval(2).fireImmune().build("sky_trail"));

    /** An elevator's cabin: a floor riding up and down its shaft. */
    public static final Supplier<EntityType<ElevatorCabinEntity>> ELEVATOR_CABIN = TYPES.register("elevator_cabin",
            () -> EntityType.Builder.<ElevatorCabinEntity>of(ElevatorCabinEntity::new, MobCategory.MISC).sized(2.9F, ElevatorCabinEntity.PLATE)
                    .clientTrackingRange(16).updateInterval(10).fireImmune().build("elevator_cabin"));
    /** A train (TrainEntity): its locomotive and cars, along a line of train rails. */
    public static final Supplier<EntityType<TrainEntity>> TRAIN = TYPES.register("train",
            () -> EntityType.Builder.<TrainEntity>of(TrainEntity::new, MobCategory.MISC).sized(2.0F, 2.6F)
                    .clientTrackingRange(16).updateInterval(20).fireImmune().build("train"));

    static void attributes(EntityAttributeCreationEvent event) {
        event.put(OIIA_CAT.get(), OiiaCatEntity.createAttributes().build());
    }

    private ModEntities() {}
}
