package com.example.skyblocks;

import com.example.skyblocks.island.ScanIslandPayload;
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
 * Shadered+ for Shadered 1.3.7 - 1.4.2 (Forge 1.20.1): the OIIA Cat (and Big OIIA Cat) and the Sky Islands (scanner,
 * pedestal, painting). The six Shadered+ skies need Shadered 1.4.3+ (the 1.20.1 branch).
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
        modEventBus.addListener(SkyBlocksMod::addToShaderedTab);
        modEventBus.addListener(SkyBlocksMod::commonSetup);
    }

    /** Our items go in Shadered's creative tab. */
    private static void addToShaderedTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(SHADERED_TAB)) {
            return;
        }
        event.accept(ModItems.OIIA_CAT.get());
        event.accept(ModItems.ISLAND_SCANNER.get());
        event.accept(ModItems.ISLAND_PAINTING.get());
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        NETWORK.messageBuilder(ScanIslandPayload.class, 0, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScanIslandPayload::encode)
                .decoder(ScanIslandPayload::decode)
                .consumerMainThread(ScanIslandPayload::handle)
                .add();
    }
}
