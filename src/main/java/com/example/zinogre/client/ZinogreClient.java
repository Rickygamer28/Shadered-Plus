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
        event.registerEntityRenderer(ZinogreMod.ALPHA_ZINOGRE.get(), c -> new ZinogreRenderer(c, com.example.zinogre.ZinogreEntity.ALPHA_SIZE));
        event.registerEntityRenderer(ZinogreMod.ALPHA_STYGIAN_ZINOGRE.get(), c -> new ZinogreRenderer(c, com.example.zinogre.ZinogreEntity.ALPHA_SIZE));
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
        // the Stormhide armor is worn as its 3D model (see StormhideArmorModel)
        net.neoforged.neoforge.client.extensions.common.IClientItemExtensions stormhide = new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(net.minecraft.world.entity.LivingEntity entity,
                    net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.client.model.HumanoidModel<?> original) {
                boolean dusk = stack.getItem() instanceof com.example.zinogre.ZinogreItems.ZinogreArmor armor && armor.dusk;
                return ArmorModels.get(dusk, slot == net.minecraft.world.entity.EquipmentSlot.LEGS);
            }
        };
        event.registerItem(stormhide, com.example.zinogre.ZinogreItems.HELMET.get(), com.example.zinogre.ZinogreItems.CHESTPLATE.get(),
                com.example.zinogre.ZinogreItems.LEGGINGS.get(), com.example.zinogre.ZinogreItems.BOOTS.get(),
                com.example.zinogre.ZinogreItems.DUSK_HELMET.get(), com.example.zinogre.ZinogreItems.DUSK_CHESTPLATE.get(),
                com.example.zinogre.ZinogreItems.DUSK_LEGGINGS.get(), com.example.zinogre.ZinogreItems.DUSK_BOOTS.get());
    }

    @SubscribeEvent
    public static void layerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ZinogreArmorModels.STORMHIDE_OUTER, ZinogreArmorModels::createStormhideOuter);
        event.registerLayerDefinition(ZinogreArmorModels.STORMHIDE_INNER, ZinogreArmorModels::createStormhideInner);
        event.registerLayerDefinition(ZinogreArmorModels.DUSKHIDE_OUTER, ZinogreArmorModels::createDuskhideOuter);
        event.registerLayerDefinition(ZinogreArmorModels.DUSKHIDE_INNER, ZinogreArmorModels::createDuskhideInner);
    }

    /** The baked armor models of both sets (baked when first worn, again after a resource reload). */
    static final class ArmorModels {
        private static final net.minecraft.client.model.HumanoidModel<?>[] MODELS = new net.minecraft.client.model.HumanoidModel<?>[4];
        private static Object bakedFrom;

        static net.minecraft.client.model.HumanoidModel<?> get(boolean dusk, boolean leggings) {
            net.minecraft.client.model.geom.EntityModelSet set = net.minecraft.client.Minecraft.getInstance().getEntityModels();
            if (bakedFrom != set || MODELS[0] == null) {
                MODELS[0] = new net.minecraft.client.model.HumanoidModel<>(set.bakeLayer(ZinogreArmorModels.STORMHIDE_OUTER));
                MODELS[1] = new net.minecraft.client.model.HumanoidModel<>(set.bakeLayer(ZinogreArmorModels.STORMHIDE_INNER));
                MODELS[2] = new net.minecraft.client.model.HumanoidModel<>(set.bakeLayer(ZinogreArmorModels.DUSKHIDE_OUTER));
                MODELS[3] = new net.minecraft.client.model.HumanoidModel<>(set.bakeLayer(ZinogreArmorModels.DUSKHIDE_INNER));
                bakedFrom = set;
            }
            return MODELS[(dusk ? 2 : 0) + (leggings ? 1 : 0)];
        }
    }

    /** Worn heads: a layer on every living entity renderer whose model has a head (players included). */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        int added = 0;
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living
                    && living.getModel() instanceof net.minecraft.client.model.HeadedModel) {
                living.addLayer(new ZinogreWornHeadLayer(living));
                added++;
            }
        }
        for (net.minecraft.world.entity.EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer((net.minecraft.world.entity.EntityType) type) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer living
                    && living.getModel() instanceof net.minecraft.client.model.HeadedModel) {
                living.addLayer(new ZinogreWornHeadLayer(living));
                added++;
            }
        }
        ZinogreMod.LOGGER.info("Zinogre: worn head layer added to {} renderers", added);
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
                ZinogreHeadRenderer.closedMesh();
                ZinogreHeadRenderer.iconMesh();
            }
        }

        private Preload() {}
    }
}
