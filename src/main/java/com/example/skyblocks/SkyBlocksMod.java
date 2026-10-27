package com.example.skyblocks;

import com.example.skyblocks.island.IslandMobsPayload;
import com.example.skyblocks.island.ScanIslandPayload;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import com.noodlegamer76.shadered.item.SkyblockHolderBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Shadered+: adds skies to Shadered as real Shadered sky types (see the mixins in skyblocks.mixins.json).
 * Everything that uses Shadered's SkyblockType (skyblocks, Illusorite ore veins, Illusorite, Sky Emitter,
 * filters, creative tab ore list...) then knows our skies too.
 */
@Mod(SkyBlocksMod.MOD_ID)
public class SkyBlocksMod {
    public static final String MOD_ID = "skyblocks";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Shadered's creative tab (shadered:shadered_tab). */
    public static final ResourceKey<CreativeModeTab> SHADERED_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("shadered", "shadered_tab"));

    public SkyBlocksMod(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.TYPES.register(modEventBus);
        ModParticles.TYPES.register(modEventBus);
        ModComponents.COMPONENTS.register(modEventBus);
        ModFluids.TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModFluids.RECIPES.register(modEventBus);
        modEventBus.addListener(SkyBlocksMod::addToShaderedTab);
        modEventBus.addListener(SkyBlocksMod::registerPayloads);
    }

    /** Our skyblock items, like Shadered lists its own (Illusorite ores for our skies are added by Shadered itself). */
    private static void addToShaderedTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(SHADERED_TAB)) {
            return;
        }
        for (int i = 0; i < ModItems.SKY_ITEMS.size(); i++) {
            event.accept(SkyblockHolderBlockItem.create(SkyTypes.type(i), SkyblockPass.NORMAL, ModItems.SKY_ITEMS.get(i).get()));
        }
        event.accept(ModItems.OIIA_CAT.get());
        event.accept(ModItems.ISLAND_SCANNER.get());
        event.accept(ModItems.ISLAND_PAINTING.get());
        event.accept(ModItems.MIRROR.get());
        event.accept(ModItems.INFINITY_MIRROR.get());
        event.accept(ModItems.WATER_CAUSTICS.get());
        event.accept(ModItems.RAIN_GLASS.get());
        event.accept(ModItems.LAVA_LAMP.get());
        event.accept(ModItems.BIG_LAVA_LAMP.get());
        event.accept(ModItems.FIRE_TORNADO.get());
        event.accept(ModItems.BRICK_HEARTH.get());
        event.accept(ModItems.MUSIC_VISUALIZER.get());
        event.accept(ModItems.HOLOGRAM_PROJECTOR.get());
        event.accept(ModItems.JELLYFISH_TANK.get());
        event.accept(ModItems.KOI_POND.get());
        event.accept(ModItems.VANILLA_KOI_POND.get());
        event.accept(ModItems.BAMBOO_SPOUT.get());
        event.accept(ModItems.MUSIC_BOX.get());
        event.accept(ModItems.POND_STEPPING_STONE.get());
        event.accept(ModItems.POND_BRIDGE.get());
        event.accept(ModItems.NEON_SIGN.get());
        event.accept(ModItems.DANCE_FLOOR.get());
        event.accept(ModItems.DISCO_BALL.get());
        event.accept(ModItems.SPEAKER.get());
        event.accept(ModItems.FOG_MACHINE.get());
        event.accept(ModItems.AQUARIUM.get());
        event.accept(ModItems.OCEAN_AQUARIUM.get());
        event.accept(ModItems.DEEP_SEA_AQUARIUM.get());
        event.accept(ModItems.AQUARIUM_STAND.get());
        event.accept(ModItems.AQUARIUM_LID.get());
        event.accept(ModItems.VANILLA_AQUARIUM.get());
        event.accept(ModItems.VANILLA_OCEAN_AQUARIUM.get());
        event.accept(ModItems.INFINITY_CUBE.get());
        for (ModFluids.SkyLiquid liquid : ModFluids.ALL) {
            event.accept(liquid.bucket.get());
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ScanIslandPayload.TYPE, ScanIslandPayload.STREAM_CODEC, ScanIslandPayload::handle)
                .playToServer(FogAreaPayload.TYPE, FogAreaPayload.STREAM_CODEC, FogAreaPayload::handle)
                .playToServer(NeonSignPayload.TYPE, NeonSignPayload.STREAM_CODEC, NeonSignPayload::handle)
                .playToServer(PondMaterialsPayload.TYPE, PondMaterialsPayload.STREAM_CODEC, PondMaterialsPayload::handle)
                .playToClient(IslandMobsPayload.TYPE, IslandMobsPayload.STREAM_CODEC, IslandMobsPayload::handle)
                .playToClient(com.example.skyblocks.portal.PortalSkiesPayload.TYPE,
                        com.example.skyblocks.portal.PortalSkiesPayload.STREAM_CODEC,
                        com.example.skyblocks.portal.PortalSkiesPayload::handle);
    }
}
