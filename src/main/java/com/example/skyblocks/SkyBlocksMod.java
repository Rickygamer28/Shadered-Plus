package com.example.skyblocks;

import com.example.skyblocks.island.IslandMobsPayload;
import com.example.skyblocks.island.ScanIslandPayload;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import com.noodlegamer76.shadered.item.SkyblockHolderBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

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
            Registries.CREATIVE_MODE_TAB, new ResourceLocation("shadered", "shadered_tab"));

    private static final String PROTOCOL = "1";
    /** Client to server messages (the Island Scanner's menu). */
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public SkyBlocksMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.TYPES.register(modEventBus);
        ModParticles.TYPES.register(modEventBus);
        ModFluids.TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModFluids.RECIPES.register(modEventBus);
        modEventBus.addListener(SkyBlocksMod::addToShaderedTab);
        modEventBus.addListener(SkyBlocksMod::commonSetup);
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

    private static void commonSetup(FMLCommonSetupEvent event) {
        NETWORK.messageBuilder(ScanIslandPayload.class, 0, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScanIslandPayload::encode)
                .decoder(ScanIslandPayload::decode)
                .consumerMainThread(ScanIslandPayload::handle)
                .add();
        NETWORK.messageBuilder(com.example.skyblocks.island.IslandMobsPayload.class, 1, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.example.skyblocks.island.IslandMobsPayload::encode)
                .decoder(com.example.skyblocks.island.IslandMobsPayload::decode)
                .consumerMainThread(com.example.skyblocks.island.IslandMobsPayload::handle)
                .add();
        NETWORK.messageBuilder(com.example.skyblocks.portal.PortalSkiesPayload.class, 2, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.example.skyblocks.portal.PortalSkiesPayload::encode)
                .decoder(com.example.skyblocks.portal.PortalSkiesPayload::decode)
                .consumerMainThread(com.example.skyblocks.portal.PortalSkiesPayload::handle)
                .add();
        NETWORK.messageBuilder(FogAreaPayload.class, 3, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(FogAreaPayload::encode)
                .decoder(FogAreaPayload::decode)
                .consumerMainThread(FogAreaPayload::handle)
                .add();
        NETWORK.messageBuilder(NeonSignPayload.class, 4, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(NeonSignPayload::encode)
                .decoder(NeonSignPayload::decode)
                .consumerMainThread(NeonSignPayload::handle)
                .add();
        NETWORK.messageBuilder(PondMaterialsPayload.class, 5, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(PondMaterialsPayload::encode)
                .decoder(PondMaterialsPayload::decode)
                .consumerMainThread(PondMaterialsPayload::handle)
                .add();
    }
}
