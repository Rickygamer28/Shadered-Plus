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
    }
}
