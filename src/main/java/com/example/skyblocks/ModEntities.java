package com.example.skyblocks;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entities: the OIIA cat companion. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyBlocksMod.MOD_ID);

    public static final Supplier<EntityType<OiiaCatEntity>> OIIA_CAT = TYPES.register("oiia_cat",
            () -> EntityType.Builder.<OiiaCatEntity>of(OiiaCatEntity::new, MobCategory.CREATURE).sized(0.5F, 0.6F).clientTrackingRange(10)
                    .build("oiia_cat"));

    static void attributes(EntityAttributeCreationEvent event) {
        event.put(OIIA_CAT.get(), OiiaCatEntity.createAttributes().build());
    }

    private ModEntities() {}
}
