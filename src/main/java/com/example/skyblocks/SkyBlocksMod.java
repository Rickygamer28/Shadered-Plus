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
        SingularityMenu.MENUS.register(modEventBus);
        TABS.register(modEventBus);
        modEventBus.addListener(SkyBlocksMod::addToShaderedTab);
        modEventBus.addListener(SkyBlocksMod::registerCapabilities);
        modEventBus.addListener(SkyBlocksMod::registerPayloads);
    }

    /** Shadered+'s own creative tab, right after Shadered's: everything, grouped (see {@link #fillTab}). */
    public static final net.neoforged.neoforge.registries.DeferredRegister<CreativeModeTab> TABS =
            net.neoforged.neoforge.registries.DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final java.util.function.Supplier<CreativeModeTab> TAB = TABS.register("shadered_plus", () -> CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.skyblocks.shadered_plus"))
            .icon(() -> new net.minecraft.world.item.ItemStack(ModItems.EFFECT_BLOCK.get()))
            .withTabsBefore(SHADERED_TAB)
            .displayItems((parameters, output) -> fillTab(output::accept))
            .build());

    /** Our skies and their liquids, in Shadered's tab with its own (Illusorite ores for our skies are added by Shadered). */
    private static void addToShaderedTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(SHADERED_TAB)) {
            return;
        }
        skies(event::accept);
    }

    private static void skies(java.util.function.Consumer<net.minecraft.world.item.ItemStack> out) {
        for (int i = 0; i < ModItems.SKY_ITEMS.size(); i++) {
            out.accept(SkyblockHolderBlockItem.create(SkyTypes.type(i), SkyblockPass.NORMAL, ModItems.SKY_ITEMS.get(i).get()));
        }
        for (ModFluids.SkyLiquid liquid : ModFluids.ALL) {
            out.accept(new net.minecraft.world.item.ItemStack(liquid.bucket.get()));
        }
    }

    /** Shadered+'s tab: like with like - lights, music, fire and water, aquariums, ponds, space, mirrors, islands, skies. */
    private static void fillTab(java.util.function.Consumer<net.minecraft.world.item.ItemStack> out) {
        java.util.function.Consumer<net.minecraft.world.level.ItemLike> add = i -> out.accept(new net.minecraft.world.item.ItemStack(i));
        // lights and decoration
        for (net.minecraft.world.level.ItemLike i : new net.minecraft.world.level.ItemLike[] {ModItems.EFFECT_BLOCK.get(), ModItems.EFFECT_GLASS.get(), ModItems.NEON_SIGN.get(),
                ModItems.LAVA_LAMP.get(), ModItems.BIG_LAVA_LAMP.get(), ModItems.PLASMA_BALL.get(), ModItems.TESLA_COIL.get(),
                ModItems.KALEIDOSCOPE.get(), ModItems.HOLOGRAM_PROJECTOR.get(), ModItems.GALAXY_PROJECTOR.get(), ModItems.WEATHER_GLOBE.get(),
                // music and parties
                ModItems.DANCE_FLOOR.get(), ModItems.DISCO_BALL.get(), ModItems.FOG_MACHINE.get(), ModItems.SPEAKER.get(),
                ModItems.MUSIC_VISUALIZER.get(), ModItems.MUSIC_BOX.get(),
                // fire and water
                ModItems.BRICK_HEARTH.get(), ModItems.FIRE_TORNADO.get(), ModItems.MAGMA_BASIN.get(), ModItems.WATER_CAUSTICS.get(),
                ModItems.RAIN_GLASS.get(),
                // aquariums and tanks
                ModItems.AQUARIUM.get(), ModItems.OCEAN_AQUARIUM.get(), ModItems.DEEP_SEA_AQUARIUM.get(), ModItems.REEF_AQUARIUM.get(),
                ModItems.JUNGLE_AQUARIUM.get(), ModItems.VANILLA_AQUARIUM.get(), ModItems.VANILLA_OCEAN_AQUARIUM.get(),
                ModItems.AQUARIUM_STAND.get(), ModItems.AQUARIUM_LID.get(), ModItems.FISHBOWL.get(), ModItems.TALL_TANK.get(),
                ModItems.SLIM_TANK.get(), ModItems.JELLYFISH_TANK.get(),
                // ponds
                ModItems.KOI_POND.get(), ModItems.VANILLA_KOI_POND.get(), ModItems.POND_STEPPING_STONE.get(), ModItems.POND_BRIDGE.get(),
                ModItems.BAMBOO_SPOUT.get(),
                // space
                ModItems.ORRERY.get(), ModItems.PLANETARIUM.get(), ModItems.BLACK_HOLE.get(), ModItems.BIG_BLACK_HOLE.get(),
                ModItems.SINGULARITY.get(), ModItems.CONTAINMENT_FRAME.get(), ModItems.PORT_FRAME.get(), ModItems.DISPLAY_FRAME.get(),
                ModItems.MONITOR_FRAME.get(), ModItems.REDSTONE_FRAME.get(), ModItems.WHIRLPOOL.get(), ModItems.TANK_FRAME.get(),
                ModItems.TANK_GLASS.get(),
                // mirrors and portals
                ModItems.MIRROR.get(), ModItems.ROUND_MIRROR.get(), ModItems.CRACKED_MIRROR.get(), ModItems.VANITY_MIRROR.get(),
                ModItems.INFINITY_MIRROR.get(), ModItems.INFINITY_CUBE.get(), ModItems.PORTAL_MIRROR.get(),
                // sky islands, the cat
                ModItems.ISLAND_SCANNER.get(), ModItems.ISLAND_PAINTING.get(), ModItems.OIIA_CAT.get()}) {
            add.accept(i);
        }
        skies(out);
    }

    /** Hoppers and pipes (any mod's) put things into the singularity and take them out. */
    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ModBlockEntities.SINGULARITY.get(),
                (be, side) -> be.handler);
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ModBlockEntities.CONTAINMENT_FRAME.get(),
                (be, side) -> be.handler());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ModBlockEntities.SPECIAL_FRAME.get(),
                (be, side) -> be.handler());
        // (the whirlpool's fluids: buckets, pipes; on its tank's frames too)
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, ModBlockEntities.WHIRLPOOL.get(),
                (be, side) -> be.handler);
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, ModBlockEntities.TANK_FRAME.get(),
                (be, side) -> be.handler());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ScanIslandPayload.TYPE, ScanIslandPayload.STREAM_CODEC, ScanIslandPayload::handle)
                .playToServer(FogAreaPayload.TYPE, FogAreaPayload.STREAM_CODEC, FogAreaPayload::handle)
                .playToServer(NeonSignPayload.TYPE, NeonSignPayload.STREAM_CODEC, NeonSignPayload::handle)
                .playToServer(EffectBlockPayload.TYPE, EffectBlockPayload.STREAM_CODEC, EffectBlockPayload::handle)
                .playToServer(PondMaterialsPayload.TYPE, PondMaterialsPayload.STREAM_CODEC, PondMaterialsPayload::handle)
                .playToServer(SolarSystemClickPayload.TYPE, SolarSystemClickPayload.STREAM_CODEC, SolarSystemClickPayload::handle)
                .playToServer(PortalCrossPayload.TYPE, PortalCrossPayload.STREAM_CODEC, PortalCrossPayload::handle)
                .playToServer(WhirlpoolPayload.TYPE, WhirlpoolPayload.STREAM_CODEC, WhirlpoolPayload::handle)
                .playToServer(SingularityPayloads.Action.TYPE, SingularityPayloads.Action.STREAM_CODEC, SingularityPayloads.Action::handle)
                .playToClient(SingularityPayloads.Contents.TYPE, SingularityPayloads.Contents.STREAM_CODEC, SingularityPayloads.Contents::handle)
                .playToClient(PortalListPayload.TYPE, PortalListPayload.STREAM_CODEC, PortalListPayload::handle)
                .playToClient(IslandMobsPayload.TYPE, IslandMobsPayload.STREAM_CODEC, IslandMobsPayload::handle)
                .playToClient(com.example.skyblocks.portal.PortalSkiesPayload.TYPE,
                        com.example.skyblocks.portal.PortalSkiesPayload.STREAM_CODEC,
                        com.example.skyblocks.portal.PortalSkiesPayload::handle);
    }
}
