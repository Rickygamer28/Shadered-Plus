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
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.example.skyblocks.SingularityMenu.TYPE.get(), SingularityScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.OIIA_CAT.get(), OiiaCatRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WATER_CAUSTICS.get(), WaterCausticsRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DISCO_BALL.get(), DiscoBallRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPEAKER.get(), SpeakerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LAVA_LAMP.get(), LavaLampRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PLASMA_BALL.get(), PlasmaBallRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SMALL_TANK.get(), SmallTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TESLA_COIL.get(), TeslaCoilRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SOLAR_SYSTEM.get(), SolarSystemRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GALAXY_PROJECTOR.get(), GalaxyProjectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MAGMA_BASIN.get(), MagmaBasinRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BLACK_HOLE.get(), BlackHoleRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SINGULARITY.get(), SingularityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPECIAL_FRAME.get(), SpecialFrameRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WHIRLPOOL.get(), WhirlpoolRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.KALEIDOSCOPE.get(), KaleidoscopeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.EFFECT_BLOCK.get(), EffectBlockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WEATHER_GLOBE.get(), WeatherGlobeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LAVA_PILLAR.get(), LavaPillarRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LIGHT_BRIDGE_EMITTER.get(), LightBridgeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LASER_PROJECTOR.get(), LaserProjectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ARCADE.get(), ArcadeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BIG_SCREEN.get(), BigScreenRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CLAW_MACHINE.get(), ClawMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TROPHY.get(), TrophyRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WATERFALL.get(), WaterfallRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STAGE.get(), StageRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPOTLIGHT.get(), SpotlightRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TERRARIUM.get(), TerrariumRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.NEON_TUBE.get(), NeonTubeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TELEPORT_PAD.get(), TeleportPadRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FIRE.get(), WallFireRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BRICK_HEARTH.get(), ClientSetup::hearthRenderer);
        event.registerBlockEntityRenderer(ModBlockEntities.VISUALIZER.get(), VisualizerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HOLOGRAM.get(), HologramRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.JELLYFISH_TANK.get(), JellyTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.KOI_POND.get(), KoiPondRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BAMBOO_SPOUT.get(), BambooSpoutRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MUSIC_BOX.get(), MusicBoxRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.NEON_SIGN.get(), NeonSignRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FOG_MACHINE.get(), FogMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BONSAI.get(), BonsaiRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HOT_SPRING.get(), HotSpringRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PHOTO_FRAME.get(), PhotoFrameRenderer::new);
        event.registerEntityRenderer(com.example.skyblocks.ModEntities.OIIA_CAT.get(), OiiaCatEntityRenderer::new);
        event.registerEntityRenderer(com.example.skyblocks.ModEntities.YARN_BALL.get(), YarnBallRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GEODE_LAMP.get(), GeodeLampRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.INFUSION_ALTAR.get(), InfusionAltarRenderer::new);
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
        event.registerItem(new IClientItemExtensions() {
            private PhotoItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new PhotoItemRenderer();
                }
                return renderer;
            }
        }, ModItems.PHOTO.get());
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
        event.registerItem(new IClientItemExtensions() {
            private SingularityItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new SingularityItemRenderer();
                }
                return renderer;
            }
        }, ModItems.SINGULARITY.get());
        event.registerItem(new IClientItemExtensions() {
            private KaleidoscopeItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new KaleidoscopeItemRenderer();
                }
                return renderer;
            }
        }, ModItems.KALEIDOSCOPE.get());
        event.registerItem(new IClientItemExtensions() {
            private WeatherGlobeItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new WeatherGlobeItemRenderer();
                }
                return renderer;
            }
        }, ModItems.WEATHER_GLOBE.get());
        event.registerItem(new IClientItemExtensions() {
            private LavaPillarItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new LavaPillarItemRenderer();
                }
                return renderer;
            }
        }, ModItems.LAVA_PILLAR.get());
        event.registerItem(new IClientItemExtensions() {
            private EffectBlockItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new EffectBlockItemRenderer();
                }
                return renderer;
            }
        }, ModItems.EFFECT_BLOCK.get(), ModItems.EFFECT_GLASS.get());
        for (boolean bigHole : new boolean[] {false, true}) {
            event.registerItem(new IClientItemExtensions() {
                private BlackHoleItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new BlackHoleItemRenderer(bigHole);
                    }
                    return renderer;
                }
            }, bigHole ? ModItems.BIG_BLACK_HOLE.get() : ModItems.BLACK_HOLE.get());
        }
        for (boolean holo : new boolean[] {false, true}) {
            event.registerItem(new IClientItemExtensions() {
                private SolarSystemItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new SolarSystemItemRenderer(holo);
                    }
                    return renderer;
                }
            }, holo ? ModItems.PLANETARIUM.get() : ModItems.ORRERY.get());
        }
        for (int shape = 0; shape < 3; shape++) {
            final int kind = shape;
            event.registerItem(new IClientItemExtensions() {
                private SmallTankItemRenderer renderer;

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) {
                        renderer = new SmallTankItemRenderer(kind);
                    }
                    return renderer;
                }
            }, kind == 0 ? ModItems.FISHBOWL.get() : kind == 1 ? ModItems.TALL_TANK.get() : ModItems.SLIM_TANK.get());
        }
        event.registerItem(new IClientItemExtensions() {
            private PlasmaBallItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new PlasmaBallItemRenderer();
                }
                return renderer;
            }
        }, ModItems.PLASMA_BALL.get());
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
        event.register(BambooSpoutRenderer.TUBE);
        event.register(MusicBoxRenderer.LID);
        event.register(MusicBoxRenderer.DANCER);
        event.register(MusicBoxRenderer.KEY);
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
        event.registerSpriteSet(com.example.skyblocks.ModParticles.STEAM.get(), SteamParticle.Provider::new);
    }

    /** An arcade cabinet's paint: its dye's colour (a little lighter), over its grey sides and trim. */
    @SubscribeEvent
    public static void arcadeColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            int c = state.getValue(com.example.skyblocks.ArcadeBlock.COLOR);
            if (tint != 0 || c == 0) return -1;
            int d = net.minecraft.world.item.DyeColor.byId(c - 1).getTextureDiffuseColor();
            int r = d >> 16 & 255, g = d >> 8 & 255, b = d & 255;
            return 0xFF000000 | (r + (255 - r) / 6) << 16 | (g + (255 - g) / 6) << 8 | (b + (255 - b) / 6);
        }, com.example.skyblocks.ModBlocks.ARCADE.get());
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

    /** The cat bed and bowl in their dye colour (tint 0), the cushion / inside a paler shade of it (tint 1). */
    @SubscribeEvent
    public static void catColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> catTint(state.getValue(com.example.skyblocks.CatBedBlock.COLOR), tint),
                com.example.skyblocks.ModBlocks.CAT_BED.get(), com.example.skyblocks.ModBlocks.CAT_BOWL.get());
    }

    @SubscribeEvent
    public static void catItemColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            var props = stack.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
            net.minecraft.world.item.DyeColor dye = props == null ? null : props.get(com.example.skyblocks.CatBedBlock.COLOR);
            if (dye == null) {
                dye = stack.is(com.example.skyblocks.ModItems.CAT_BOWL.get()) ? net.minecraft.world.item.DyeColor.LIGHT_BLUE : net.minecraft.world.item.DyeColor.RED;
            }
            return catTint(dye, tint);
        }, com.example.skyblocks.ModItems.CAT_BED.get(), com.example.skyblocks.ModItems.CAT_BOWL.get());
    }

    private static int catTint(net.minecraft.world.item.DyeColor dye, int tint) {
        int c = dye.getTextureDiffuseColor() & 0xFFFFFF;
        if (tint == 1) {
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            c = (r + (255 - r) * 3 / 5) << 16 | (g + (255 - g) * 3 / 5) << 8 | (b + (255 - b) * 3 / 5);
        }
        return 0xFF000000 | c;
    }

    @SubscribeEvent
    public static void neonItemColour(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> 0xFF000000 | neon(net.minecraft.world.item.DyeColor.PINK), com.example.skyblocks.ModItems.NEON_TUBE.get());
    }

    private static int neon(net.minecraft.world.item.DyeColor dye) {
        int c = dye.getTextureDiffuseColor();
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int max = Math.max(1, Math.max(r, Math.max(g, b)));
        // (as bright as it goes, keeping its hue; a little white in it)
        r = Math.min(255, r * 255 / max + 30);
        g = Math.min(255, g * 255 / max + 30);
        b = Math.min(255, b * 255 / max + 30);
        return (r << 16) | (g << 8) | b;
    }

    /** Light bridge emitters: their lens and strips in the bridge's colour (rainbow: a hue for where it is). */
    @SubscribeEvent
    public static void lightBridgeColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            int c = state.getValue(com.example.skyblocks.LightBridgeEmitterBlock.COLOR);
            if (c < 16) {
                return 0xFF000000 | neon(net.minecraft.world.item.DyeColor.byId(c));
            }
            float h = pos == null ? 0.5F : ((pos.getX() + pos.getZ()) * 0.11F) % 1.0F;
            return 0xFF000000 | net.minecraft.util.Mth.hsvToRgb(h < 0 ? h + 1 : h, 0.55F, 1.0F);
        }, com.example.skyblocks.ModBlocks.LIGHT_BRIDGE_EMITTER.get());
        event.register((state, level, pos, tint) -> {
            int c = state.getValue(com.example.skyblocks.TeleportPadBlock.COLOR);
            if (c < 16) {
                return 0xFF000000 | neon(net.minecraft.world.item.DyeColor.byId(c));
            }
            float h = pos == null ? 0.5F : ((pos.getX() * 0.07F + pos.getZ() * 0.13F) % 1.0F);
            return 0xFF000000 | net.minecraft.util.Mth.hsvToRgb(h < 0 ? h + 1 : h, 0.55F, 1.0F);
        }, com.example.skyblocks.ModBlocks.TELEPORT_PAD.get());
    }

    @SubscribeEvent
    public static void lightBridgeItemColour(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> 0xFFA8ECFF, com.example.skyblocks.ModItems.LIGHT_BRIDGE_EMITTER.get(), com.example.skyblocks.ModItems.TELEPORT_PAD.get());
    }

    /** Portal mirrors: their colour. */
    @SubscribeEvent
    public static void portalColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> state.getValue(com.example.skyblocks.PortalMirrorBlock.COLOR).getTextureDiffuseColor(),
                com.example.skyblocks.ModBlocks.PORTAL_MIRROR.get());
    }

    @SubscribeEvent
    public static void portalItemColour(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? net.minecraft.world.item.DyeColor.PURPLE.getTextureDiffuseColor() : 0xFFFFFFFF,
                com.example.skyblocks.ModItems.PORTAL_MIRROR.get());
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
