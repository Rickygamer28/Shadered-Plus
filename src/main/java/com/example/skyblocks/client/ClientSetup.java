package com.example.skyblocks.client;

import com.example.skyblocks.ModBlockEntities;
import com.example.skyblocks.OiiaCatBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.island.IslandScannerRenderer;
import com.example.skyblocks.client.island.SkyIslandRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;

@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
        // Shadered's sky blocks: ours replaces Shadered's (we load after Shadered) so our skies are drawn too
        event.registerBlockEntityRenderer(com.noodlegamer76.shadered.entity.block.InitBlockEntities.SKYBLOCK.get(),
                OurSkyblockRenderer::new);
    }

    /** The shared models of the Sky Liquids (any sky without its own model files falls back to these). */
    private static final net.minecraft.resources.ResourceLocation BUCKET_MODEL =
            new net.minecraft.resources.ResourceLocation(SkyBlocksMod.MOD_ID, "item/sky_liquid_bucket");
    private static final net.minecraft.resources.ResourceLocation LIQUID_MODEL =
            new net.minecraft.resources.ResourceLocation(SkyBlocksMod.MOD_ID, "block/sky_liquid");

    @SubscribeEvent
    public static void registerModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(BUCKET_MODEL);
        event.register(LIQUID_MODEL);
    }

    /** A sky added by another mod has no model files of ours: give its bucket and liquid the shared models. */
    @SubscribeEvent
    public static void fallbackModels(net.minecraftforge.client.event.ModelEvent.ModifyBakingResult event) {
        java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.client.resources.model.BakedModel> models = event.getModels();
        net.minecraft.client.resources.model.BakedModel missing = models.get(net.minecraft.client.resources.model.ModelBakery.MISSING_MODEL_LOCATION);
        net.minecraft.client.resources.model.BakedModel bucket = models.get(BUCKET_MODEL);
        net.minecraft.client.resources.model.BakedModel liquidModel = models.get(LIQUID_MODEL);
        for (com.example.skyblocks.ModFluids.SkyLiquid liquid : com.example.skyblocks.ModFluids.ALL) {
            var item = new net.minecraft.client.resources.model.ModelResourceLocation(liquid.bucket.getId(), "inventory");
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
