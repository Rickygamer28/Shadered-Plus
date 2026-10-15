package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ZinogreMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ZinogreClient {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ZinogreMod.ZINOGRE.get(), ZinogreRenderer::new);
        event.registerEntityRenderer(ZinogreMod.STYGIAN_ZINOGRE.get(), ZinogreRenderer::new);
        event.registerBlockEntityRenderer(ZinogreMod.HEAD_BLOCK_ENTITY.get(), ZinogreHeadRenderer::new);
    }

    /** The head items are drawn as the 3D head (inventory, hand, worn). */
    @SubscribeEvent
    public static void clientExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        net.neoforged.neoforge.client.extensions.common.IClientItemExtensions head = new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private ZinogreHeadItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new ZinogreHeadItemRenderer();
                }
                return renderer;
            }
        };
        event.registerItem(head, ZinogreMod.HEAD_ITEM.get(), ZinogreMod.STYGIAN_HEAD_ITEM.get());
    }

    private ZinogreClient() {}

    /** Uploads the meshes between frames (not halfway through drawing something else) as soon as a world is open. */
    @EventBusSubscriber(modid = ZinogreMod.MOD_ID, value = Dist.CLIENT)
    public static final class Preload {
        private static boolean done;

        @SubscribeEvent
        public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!done && net.minecraft.client.Minecraft.getInstance().level != null) {
                done = true;
                ZinogreData.get();
                ZinogreHeadRenderer.mesh();
            }
        }

        private Preload() {}
    }
}
