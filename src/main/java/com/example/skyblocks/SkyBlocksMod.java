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
        ModEntities.TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::attributes);
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SkyBlocksMod::giveGuide);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArcadeVersus::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArcadeVersus::loggedOut);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArcadeVersus::stopped);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MapHeights::tick);
    }

    /** The guide book, once, the first time a player joins a world with Shadered+. */
    private static void giveGuide(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        net.minecraft.world.entity.player.Player player = event.getEntity();
        // (kept where it outlives dying, so it's only ever once)
        net.minecraft.nbt.CompoundTag all = player.getPersistentData();
        net.minecraft.nbt.CompoundTag data = all.getCompound("PlayerPersisted");
        if (!data.getBoolean("skyblocks_guide_given")) {
            data.putBoolean("skyblocks_guide_given", true);
            all.put("PlayerPersisted", data);
            player.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.GUIDE_BOOK.get()));
        }
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

    /** "Shadered+: New": everything added since the last upload (GuideChapters.newest), right after the main tab. */
    public static final java.util.function.Supplier<CreativeModeTab> NEW_TAB = TABS.register("new", () -> CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.skyblocks.new"))
            .icon(() -> GuideChapters.newest().isEmpty() ? new net.minecraft.world.item.ItemStack(ModItems.GUIDE_BOOK.get()) : GuideChapters.newest().get(0))
            .withTabsBefore(ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(MOD_ID, "shadered_plus")))
            .displayItems((parameters, output) -> GuideChapters.newest().forEach(output::accept))
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

    /** Shadered+'s tab: like with like - the guide, then each of its chapters (lights, music, ... skies). */
    private static void fillTab(java.util.function.Consumer<net.minecraft.world.item.ItemStack> out) {
        out.accept(new net.minecraft.world.item.ItemStack(ModItems.GUIDE_BOOK.get()));
        for (GuideChapters.Chapter chapter : GuideChapters.all()) {
            if (!chapter.id().equals("new")) { // (they're in their own chapters too)
                chapter.items().forEach(out);
            }
        }
    }

    /** (the skies and their liquids, for the guide's last chapter) */
    static java.util.List<net.minecraft.world.item.ItemStack> skyStacks() {
        java.util.List<net.minecraft.world.item.ItemStack> list = new java.util.ArrayList<>();
        skies(list::add);
        return list;
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
        // (versus at the arcade: each frame's buttons passed straight on as they come - not waiting for the next tick)
        event.registrar("1").executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK)
                .playBidirectional(ArcadeNetPayload.TYPE, ArcadeNetPayload.STREAM_CODEC, ArcadeNetPayload::handle);
        event.registrar("1").playToServer(ScanIslandPayload.TYPE, ScanIslandPayload.STREAM_CODEC, ScanIslandPayload::handle)
                .playToServer(FogAreaPayload.TYPE, FogAreaPayload.STREAM_CODEC, FogAreaPayload::handle)
                .playToServer(NeonSignPayload.TYPE, NeonSignPayload.STREAM_CODEC, NeonSignPayload::handle)
                .playToServer(EffectBlockPayload.TYPE, EffectBlockPayload.STREAM_CODEC, EffectBlockPayload::handle)
                .playToServer(ArcadeScorePayload.TYPE, ArcadeScorePayload.STREAM_CODEC, ArcadeScorePayload::handle)
                .playToServer(LightBridgeSettingsPayload.TYPE, LightBridgeSettingsPayload.STREAM_CODEC, LightBridgeSettingsPayload::handle)
                .playToServer(ClawPayloads.Play.TYPE, ClawPayloads.Play.STREAM_CODEC, ClawPayloads.Play::handle)
                .playBidirectional(ClawPayloads.State.TYPE, ClawPayloads.State.STREAM_CODEC, ClawPayloads.State::handle)
                .playToServer(TrophyClaimPayload.TYPE, TrophyClaimPayload.STREAM_CODEC, TrophyClaimPayload::handle)
                .playBidirectional(ArcadeFramePayload.TYPE, ArcadeFramePayload.STREAM_CODEC, ArcadeFramePayload::handle)
                .playBidirectional(ArcadeVersusPayload.TYPE, ArcadeVersusPayload.STREAM_CODEC, ArcadeVersusPayload::handle)
                .playToServer(ArcadeGhostPayload.TYPE, ArcadeGhostPayload.STREAM_CODEC, ArcadeGhostPayload::handle)
                .playToServer(PhotoPayloads.Take.TYPE, PhotoPayloads.Take.STREAM_CODEC, PhotoPayloads.Take::handle)
                .playToServer(PhotoPayloads.Want.TYPE, PhotoPayloads.Want.STREAM_CODEC, PhotoPayloads.Want::handle)
                .playToClient(PhotoPayloads.Picture.TYPE, PhotoPayloads.Picture.STREAM_CODEC, PhotoPayloads.Picture::handle)
                .playToServer(PondMaterialsPayload.TYPE, PondMaterialsPayload.STREAM_CODEC, PondMaterialsPayload::handle)
                .playToServer(SolarSystemClickPayload.TYPE, SolarSystemClickPayload.STREAM_CODEC, SolarSystemClickPayload::handle)
                .playToServer(PortalCrossPayload.TYPE, PortalCrossPayload.STREAM_CODEC, PortalCrossPayload::handle)
                .playToServer(WhirlpoolPayload.TYPE, WhirlpoolPayload.STREAM_CODEC, WhirlpoolPayload::handle)
                .playToServer(SingularityPayloads.Action.TYPE, SingularityPayloads.Action.STREAM_CODEC, SingularityPayloads.Action::handle)
                .playToClient(SingularityPayloads.Contents.TYPE, SingularityPayloads.Contents.STREAM_CODEC, SingularityPayloads.Contents::handle)
                .playToClient(TeleportPadPayloads.Menu.TYPE, TeleportPadPayloads.Menu.STREAM_CODEC, TeleportPadPayloads.Menu::handle)
                .playToServer(TeleportPadPayloads.Action.TYPE, TeleportPadPayloads.Action.STREAM_CODEC, TeleportPadPayloads.Action::handle)
                .playToClient(PortalListPayload.TYPE, PortalListPayload.STREAM_CODEC, PortalListPayload::handle)
                .playToClient(IslandMobsPayload.TYPE, IslandMobsPayload.STREAM_CODEC, IslandMobsPayload::handle)
                .playToClient(com.example.skyblocks.portal.PortalSkiesPayload.TYPE,
                        com.example.skyblocks.portal.PortalSkiesPayload.STREAM_CODEC,
                        com.example.skyblocks.portal.PortalSkiesPayload::handle);
    }
}
