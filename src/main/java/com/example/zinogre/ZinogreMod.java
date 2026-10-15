package com.example.zinogre;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Zinogre (personal build): one mob, drawn with Shadered's model renderer from assets/zinogre/models/complex/zinogre.glb.
 * Spawned with its spawn egg only (no natural spawning). Tamable with meat.
 */
@Mod(ZinogreMod.MOD_ID)
public class ZinogreMod {
    public static final String MOD_ID = "zinogre";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    /** About 2.6 blocks wide and 2.4 tall (the body; the tail reaches further back). */
    public static final DeferredHolder<EntityType<?>, EntityType<ZinogreEntity>> ZINOGRE = ENTITIES.register("zinogre",
            () -> EntityType.Builder.of(ZinogreEntity::new, MobCategory.CREATURE)
                    .sized(2.6F, 2.4F)
                    .clientTrackingRange(10)
                    .build("zinogre"));

    /** The Stygian Zinogre: the same mob (same class) with its own type, textures and tougher attributes. */
    public static final DeferredHolder<EntityType<?>, EntityType<ZinogreEntity>> STYGIAN_ZINOGRE = ENTITIES.register("stygian_zinogre",
            () -> EntityType.Builder.of(ZinogreEntity::new, MobCategory.CREATURE)
                    .sized(2.6F, 2.4F)
                    .clientTrackingRange(10)
                    .build("stygian_zinogre"));

    public static final DeferredItem<DeferredSpawnEggItem> SPAWN_EGG = ITEMS.register("zinogre_spawn_egg",
            () -> new DeferredSpawnEggItem(ZINOGRE, 0x2E3B4E, 0x8FE3FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> STYGIAN_SPAWN_EGG = ITEMS.register("stygian_zinogre_spawn_egg",
            () -> new DeferredSpawnEggItem(STYGIAN_ZINOGRE, 0x16161C, 0xD6283A, new Item.Properties()));

    public ZinogreMod(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(ZinogreMod::attributes);
        modEventBus.addListener(ZinogreMod::creativeTab);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ZINOGRE.get(), ZinogreEntity.createAttributes(false).build());
        event.put(STYGIAN_ZINOGRE.get(), ZinogreEntity.createAttributes(true).build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(SPAWN_EGG.get());
            event.accept(STYGIAN_SPAWN_EGG.get());
        }
    }
}
