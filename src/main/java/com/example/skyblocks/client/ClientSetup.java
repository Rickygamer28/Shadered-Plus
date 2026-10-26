package com.example.skyblocks.client;

import net.minecraft.client.Minecraft;

import com.example.skyblocks.ModBlockEntities;
import com.example.skyblocks.OiiaCatBlockEntity;
import com.example.skyblocks.ModItems;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.island.IslandScannerRenderer;
import com.example.skyblocks.client.island.SkyIslandRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    /** Add our skies' skybox passes to Shadered's renderer (Shadered adds its own in its client setup). */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ShaderedSkies::register);
        event.enqueueWork(SkyUsage::report);
        // The Island Painting's window needs a stencil buffer (client/island/IslandWindow).
        event.enqueueWork(() -> net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().enableStencil());
        // Sky Liquids' own surface is fully see-through and cut out (nothing drawn, nothing hidden): all you see is
        // their Shadered sky, drawn by client/SkyLiquidRenderer
        event.enqueueWork(() -> {
            for (com.example.skyblocks.ModFluids.SkyLiquid liquid : com.example.skyblocks.ModFluids.ALL) {
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(liquid.source.get(),
                        net.minecraft.client.renderer.RenderType.cutout());
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(liquid.flowing.get(),
                        net.minecraft.client.renderer.RenderType.cutout());
            }
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.OIIA_CAT.get(), OiiaCatRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WATER_CAUSTICS.get(), WaterCausticsRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DISCO_BALL.get(), DiscoBallRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPEAKER.get(), SpeakerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LAVA_LAMP.get(), LavaLampRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FIRE.get(), WallFireRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BRICK_HEARTH.get(), ClientSetup::hearthRenderer);
        event.registerBlockEntityRenderer(ModBlockEntities.VISUALIZER.get(), VisualizerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HOLOGRAM.get(), HologramRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.JELLYFISH_TANK.get(), JellyTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.POND_WATERFALL.get(), PondWaterfallRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.KOI_POND.get(), KoiPondRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.NEON_SIGN.get(), NeonSignRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FOG_MACHINE.get(), FogMachineRenderer::new);
        BlockEntityRendererProvider<OiiaCatBlockEntity> big = OiiaCatRenderer::new;
        event.registerBlockEntityRenderer(ModBlockEntities.BIG_OIIA_CAT.get(), big);
        event.registerBlockEntityRenderer(ModBlockEntities.SKY_ISLAND.get(), SkyIslandRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ISLAND_SCANNER.get(), IslandScannerRenderer::new);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private DiscoBallItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new DiscoBallItemRenderer();
                }
                return renderer;
            }
        }, ModItems.DISCO_BALL.get());
        net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions liquidLook =
                new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            private final net.minecraft.resources.ResourceLocation still =
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/sky_liquid_still");
            private final net.minecraft.resources.ResourceLocation flowing =
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/sky_liquid_flow");

            @Override
            public net.minecraft.resources.ResourceLocation getStillTexture() {
                return still;
            }

            @Override
            public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                return flowing;
            }

            /** Under the surface: deep space purple, and you can't see far. */
            @Override
            public org.joml.Vector3f modifyFogColor(net.minecraft.client.Camera camera, float partialTick,
                                                     net.minecraft.client.multiplayer.ClientLevel level, int renderDistance,
                                                     float darkenWorldAmount, org.joml.Vector3f fluidFogColor) {
                return new org.joml.Vector3f(0.06F, 0.02F, 0.12F);
            }

            @Override
            public void modifyFogRender(net.minecraft.client.Camera camera, net.minecraft.client.renderer.FogRenderer.FogMode mode,
                                        float renderDistance, float partialTick, float nearDistance, float farDistance,
                                        com.mojang.blaze3d.shaders.FogShape shape) {
                com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(-4.0F);
                com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(24.0F);
            }
        };
        for (com.example.skyblocks.ModFluids.SkyLiquid liquid : com.example.skyblocks.ModFluids.ALL) {
            event.registerFluidType(liquidLook, liquid.type.get());
            final com.noodlegamer76.shadered.client.util.SkyblockType sky = liquid.sky;
            event.registerItem(new IClientItemExtensions() {
                private SkyLiquidBucketRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new SkyLiquidBucketRenderer(sky);
                    }
                    return renderer;
                }
            }, liquid.bucket.get());
        }
        event.registerItem(new IClientItemExtensions() {
            private OiiaCatItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new OiiaCatItemRenderer();
                }
                return renderer;
            }
        }, ModItems.OIIA_CAT.get());
        event.registerItem(new IClientItemExtensions() {
            private com.example.skyblocks.client.island.SkyIslandItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.example.skyblocks.client.island.SkyIslandItemRenderer();
                }
                return renderer;
            }
        }, ModItems.SKY_ISLAND.get());
        event.registerItem(new IClientItemExtensions() {
            private FireTornadoItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new FireTornadoItemRenderer();
                }
                return renderer;
            }
        }, ModItems.FIRE_TORNADO.get());
        event.registerItem(new IClientItemExtensions() {
            private JellyTankItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new JellyTankItemRenderer();
                }
                return renderer;
            }
        }, ModItems.JELLYFISH_TANK.get());
        for (boolean big : new boolean[] {false, true}) {
            event.registerItem(new IClientItemExtensions() {
                private LavaLampItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new LavaLampItemRenderer(big);
                    }
                    return renderer;
                }
            }, big ? ModItems.BIG_LAVA_LAMP.get() : ModItems.LAVA_LAMP.get());
        }
    }

    /** The shared models of the Sky Liquids (any sky without its own model files falls back to these). */
    private static final net.minecraft.client.resources.model.ModelResourceLocation BUCKET_MODEL =
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "item/sky_liquid_bucket"));
    private static final net.minecraft.client.resources.model.ModelResourceLocation LIQUID_MODEL =
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/sky_liquid"));

    private static final net.minecraft.client.resources.model.ModelResourceLocation CAUSTICS_OVERLAY =
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/water_caustics_overlay"));

    private static final net.minecraft.client.resources.model.ModelResourceLocation RAIN_OVERLAY =
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/rain_glass_overlay"));

    @SubscribeEvent
    public static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(BUCKET_MODEL);
        event.register(LIQUID_MODEL);
        event.register(CAUSTICS_OVERLAY);
        event.register(RAIN_OVERLAY);
    }

    /** The hearth is a glow-colour block entity too: the fires' renderer draws it. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static net.minecraft.client.renderer.blockentity.BlockEntityRenderer<com.example.skyblocks.BrickHearthBlockEntity> hearthRenderer(
            net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
        return (net.minecraft.client.renderer.blockentity.BlockEntityRenderer) new WallFireRenderer(ctx);
    }

    /** The brick hearth's model shows the stone and wood chosen for it (client/HearthModel). */
    @SubscribeEvent
    public static void hearthModel(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (net.minecraft.world.level.block.state.BlockState state : com.example.skyblocks.ModBlocks.BRICK_HEARTH.get().getStateDefinition().getPossibleStates()) {
            var key = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
            net.minecraft.client.resources.model.BakedModel m = models.get(key);
            if (m != null) {
                models.put(key, new HearthModel(m));
            }
        }
    }

    /** The vanilla koi pond's model shows the rim and floor chosen for it (client/PondModel). */
    @SubscribeEvent
    public static void pondModel(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (net.minecraft.world.level.block.state.BlockState state : com.example.skyblocks.ModBlocks.VANILLA_KOI_POND.get().getStateDefinition().getPossibleStates()) {
            var key = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
            net.minecraft.client.resources.model.BakedModel m = models.get(key);
            if (m != null) {
                models.put(key, new PondModel(m));
            }
        }
    }

    /** The water caustics block's model also draws the block put under its water (client/WaterCausticsModel). */
    @SubscribeEvent
    public static void causticsModel(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        net.minecraft.client.resources.model.BakedModel overlay = models.get(CAUSTICS_OVERLAY);
        if (overlay == null) {
            return;
        }
        for (net.minecraft.world.level.block.state.BlockState state : com.example.skyblocks.ModBlocks.WATER_CAUSTICS.get().getStateDefinition().getPossibleStates()) {
            var key = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
            net.minecraft.client.resources.model.BakedModel water = models.get(key);
            if (water != null) {
                models.put(key, new WaterCausticsModel(water, overlay));
            }
        }
        net.minecraft.client.resources.model.BakedModel rain = models.get(RAIN_OVERLAY);
        if (rain != null) { // (rain glass: the same, with the rain textures)
            for (net.minecraft.world.level.block.state.BlockState state : com.example.skyblocks.ModBlocks.RAIN_GLASS.get().getStateDefinition().getPossibleStates()) {
                var key = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
                net.minecraft.client.resources.model.BakedModel glass = models.get(key);
                if (glass != null) {
                    models.put(key, new WaterCausticsModel(glass, rain));
                }
            }
        }
    }

    @SubscribeEvent
    public static void particles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.example.skyblocks.ModParticles.FOG.get(), FogParticle.Provider::new);
    }

    /** The vanilla koi pond's rim and floor: the colour of the blocks chosen for them (grass, leaves... as the biome has them). */
    @SubscribeEvent
    public static void pondColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if (tint < PondModel.RIM_TINT || level == null || pos == null
                    || !(level.getBlockEntity(pos) instanceof com.example.skyblocks.KoiPondBlockEntity pond)) {
                return -1;
            }
            net.minecraft.world.level.block.state.BlockState block = tint < PondModel.FLOOR_TINT ? pond.rim() : pond.floor();
            return block == null ? -1 : event.getBlockColors().getColor(block, level, pos, tint % 100);
        }, com.example.skyblocks.ModBlocks.VANILLA_KOI_POND.get());
    }

    /** The vanilla aquariums' water: the biome's water colour, as any water. */
    @SubscribeEvent
    public static void aquariumWater(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> level != null && pos != null ? net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level, pos) : 0x3F76E4,
                com.example.skyblocks.ModBlocks.VANILLA_AQUARIUM.get(), com.example.skyblocks.ModBlocks.VANILLA_OCEAN_AQUARIUM.get());
    }

    @SubscribeEvent
    public static void aquariumWaterItem(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? 0xFF3F76E4 : 0xFFFFFFFF,
                com.example.skyblocks.ModItems.VANILLA_AQUARIUM.get(), com.example.skyblocks.ModItems.VANILLA_OCEAN_AQUARIUM.get());
    }

    /** Tinted blocks under the water (grass, leaves...) keep their colour. */
    @SubscribeEvent
    public static void causticsColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> level != null && pos != null
                && level.getBlockEntity(pos) instanceof com.example.skyblocks.HologramBlockEntity holo ? holo.colour() : 0x45E0FF,
                com.example.skyblocks.ModBlocks.HOLOGRAM_PROJECTOR.get());
        event.register((state, level, pos, tint) -> level != null && pos != null
                && level.getBlockEntity(pos) instanceof com.example.skyblocks.NeonSignBlockEntity sign ? sign.background() : com.example.skyblocks.NeonSignBlockEntity.DEFAULT_BACKGROUND,
                com.example.skyblocks.ModBlocks.NEON_SIGN.get());
        event.register((state, level, pos, tint) -> {
            if (level != null && pos != null && level.getBlockEntity(pos) instanceof com.example.skyblocks.WaterCausticsBlockEntity be
                    && be.inside() != null) {
                return Minecraft.getInstance().getBlockColors().getColor(be.inside(), level, pos, tint);
            }
            return -1;
        }, com.example.skyblocks.ModBlocks.WATER_CAUSTICS.get(), com.example.skyblocks.ModBlocks.RAIN_GLASS.get());
    }

    /** A sky added by another mod has no model files of ours: give its bucket and liquid the shared models. */
    @SubscribeEvent
    public static void fallbackModels(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        net.minecraft.client.resources.model.BakedModel missing = models.get(net.minecraft.client.resources.model.ModelBakery.MISSING_MODEL_VARIANT);
        net.minecraft.client.resources.model.BakedModel bucket = models.get(BUCKET_MODEL);
        net.minecraft.client.resources.model.BakedModel liquidModel = models.get(LIQUID_MODEL);
        for (com.example.skyblocks.ModFluids.SkyLiquid liquid : com.example.skyblocks.ModFluids.ALL) {
            var item = net.minecraft.client.resources.model.ModelResourceLocation.inventory(liquid.bucket.getId());
            if (bucket != null && (models.get(item) == null || models.get(item) == missing)) {
                models.put(item, bucket);
            }
            if (liquidModel != null) {
                for (net.minecraft.world.level.block.state.BlockState state : liquid.block.get().getStateDefinition().getPossibleStates()) {
                    var key = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
                    if (models.get(key) == null || models.get(key) == missing) {
                        models.put(key, liquidModel);
                    }
                }
            }
        }
    }

    private ClientSetup() {}
}
