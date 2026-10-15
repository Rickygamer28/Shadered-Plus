package com.example.skyblocks.client;

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
        BlockEntityRendererProvider<OiiaCatBlockEntity> big = OiiaCatRenderer::new;
        event.registerBlockEntityRenderer(ModBlockEntities.BIG_OIIA_CAT.get(), big);
        event.registerBlockEntityRenderer(ModBlockEntities.SKY_ISLAND.get(), SkyIslandRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ISLAND_SCANNER.get(), IslandScannerRenderer::new);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
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
    }

    /** The shared models of the Sky Liquids (any sky without its own model files falls back to these). */
    private static final net.minecraft.client.resources.model.ModelResourceLocation BUCKET_MODEL =
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "item/sky_liquid_bucket"));
    private static final net.minecraft.client.resources.model.ModelResourceLocation LIQUID_MODEL =
            net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/sky_liquid"));

    @SubscribeEvent
    public static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(BUCKET_MODEL);
        event.register(LIQUID_MODEL);
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
