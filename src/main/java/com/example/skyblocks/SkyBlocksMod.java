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
        ModSkyShapes.init();
        ModSkyShapes.RECIPES.register(modEventBus);
        java.util.Objects.requireNonNull(SpinPadMenu.TYPE); // (its menu type put on the list before the list is registered)
        SingularityMenu.MENUS.register(modEventBus);
        TABS.register(modEventBus);
        modEventBus.addListener(SkyBlocksMod::addToShaderedTab);
        modEventBus.addListener(SkyBlocksMod::registerCapabilities);
        modEventBus.addListener(SkyBlocksMod::registerPayloads);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SkyBlocksMod::giveGuide);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArcadeVersus::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArcadeVersus::loggedOut);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ArcadeVersus::stopped);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BonsaiTrees::register);
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
        out.accept(new net.minecraft.world.item.ItemStack(ModItems.SKY_STUDIO.get()));
        out.accept(new net.minecraft.world.item.ItemStack(ModItems.SKY_BRUSH.get()));
        for (ModSkyShapes.Shapes shapes : ModSkyShapes.ALL) {
            out.accept(SkyBrushItem.with(new net.minecraft.world.item.ItemStack(ModItems.SKY_BRUSH.get()), shapes.sky));
        }
        for (ModSkyShapes.Kind kind : ModSkyShapes.Kind.values()) {
            for (ModSkyShapes.Shapes shapes : ModSkyShapes.ALL) {
                out.accept(new net.minecraft.world.item.ItemStack(shapes.items.get(kind).get()));
            }
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
        // (the spin pad's energy: cables and machines take it out)
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SPIN_PAD.get(),
                (be, side) -> be.energyHere());
        // (the power core's energy: on it, and on its chamber's frames)
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_CORE.get(),
                (be, side) -> be.handler);
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CORE_FRAME.get(),
                (be, side) -> be.handler());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // (versus at the arcade: each frame's buttons passed straight on as they come - not waiting for the next tick)
        event.registrar("1").executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK)
                .playBidirectional(ArcadeNetPayload.TYPE, ArcadeNetPayload.STREAM_CODEC, ArcadeNetPayload::handle);
        event.registrar("1").playToClient(SkyPaint.Set.TYPE, SkyPaint.Set.STREAM_CODEC, SkyPaint.Set::handle)
                .playToClient(SkyPaint.Chunk.TYPE, SkyPaint.Chunk.STREAM_CODEC, SkyPaint.Chunk::handle)
                .playToClient(StudioSkies.Sync.TYPE, StudioSkies.Sync.STREAM_CODEC, StudioSkies.Sync::handle)
                .playToServer(StudioSkies.Save.TYPE, StudioSkies.Save.STREAM_CODEC, StudioSkies.Save::handle)
                .playToServer(StudioSkies.Take.TYPE, StudioSkies.Take.STREAM_CODEC, StudioSkies.Take::handle);
        event.registrar("1").playToServer(ScanIslandPayload.TYPE, ScanIslandPayload.STREAM_CODEC, ScanIslandPayload::handle)
                .playToServer(FogAreaPayload.TYPE, FogAreaPayload.STREAM_CODEC, FogAreaPayload::handle)
                .playToServer(NeonSignPayload.TYPE, NeonSignPayload.STREAM_CODEC, NeonSignPayload::handle)
                .playToServer(ShaderPaintPayload.TYPE, ShaderPaintPayload.STREAM_CODEC, ShaderPaintPayload::handle)
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
                .playToServer(PowerCorePayload.TYPE, PowerCorePayload.STREAM_CODEC, PowerCorePayload::handle)
                .playToServer(TimeCrystalPayload.TYPE, TimeCrystalPayload.STREAM_CODEC, TimeCrystalPayload::handle)
                .playToServer(ElevatorPayload.TYPE, ElevatorPayload.STREAM_CODEC, ElevatorPayload::handle)
                .playToServer(HourglassPayload.TYPE, HourglassPayload.STREAM_CODEC, HourglassPayload::handle)
                .playToServer(SpotlightPayload.TYPE, SpotlightPayload.STREAM_CODEC, SpotlightPayload::handle)
                .playToServer(SonarPayload.TYPE, SonarPayload.STREAM_CODEC, SonarPayload::handle)
                .playToServer(PhasePayload.TYPE, PhasePayload.STREAM_CODEC, PhasePayload::handle)
                .playToServer(EraseTrailPayload.TYPE, EraseTrailPayload.STREAM_CODEC, EraseTrailPayload::handle)
                .playToClient(MoonJar.Payload.TYPE, MoonJar.Payload.STREAM_CODEC, MoonJar.Payload::handle)
                .playToClient(SunJar.Payload.TYPE, SunJar.Payload.STREAM_CODEC, SunJar.Payload::handle)
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
