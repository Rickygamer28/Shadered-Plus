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
        // the materials are drawn as the real 3D parts they came from
        String[][] parts = {{"thunder_horn", "horn", "0"}, {"zinogre_claw", "claw", "0"}, {"zinogre_shell", "shell", "0"}, {"zinogre_tail", "tail", "0"},
                {"electro_fur", "fur", "0"}, {"stygian_horn", "horn", "1"}, {"stygian_claw", "claw", "1"}, {"stygian_shell", "shell", "1"},
                {"stygian_tail", "tail", "1"}, {"stygian_fur", "fur", "1"}};
        for (String[] p : parts) {
            net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer[] renderer = new net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer[1];
            event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                @Override
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer[0] == null) {
                        renderer[0] = new ZinogrePartItemRenderer(p[1], p[2].equals("1"));
                    }
                    return renderer[0];
                }
            }, net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, p[0])));
        }
        // the armor is worn as its 3D model (see ZinogreArmorModels)
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

    /** The Zinogre bows bend as they're drawn, like the vanilla bow. */
    @SubscribeEvent
    public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (net.minecraft.world.item.Item bow : new net.minecraft.world.item.Item[] {
                    com.example.zinogre.ZinogreWeapons.THUNDERFANG_BOW.get(), com.example.zinogre.ZinogreWeapons.DUSKFANG_BOW.get()}) {
                net.minecraft.client.renderer.item.ItemProperties.register(bow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"),
                        (stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0.0F
                                : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F);
                net.minecraft.client.renderer.item.ItemProperties.register(bow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"),
                        (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            }
            for (net.minecraft.world.item.Item crossbow : new net.minecraft.world.item.Item[] {
                    com.example.zinogre.ZinogreWeapons.THUNDERFANG_CROSSBOW.get(), com.example.zinogre.ZinogreWeapons.DUSKFANG_CROSSBOW.get()}) {
                net.minecraft.client.renderer.item.ItemProperties.register(crossbow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"),
                        (stack, level, entity, seed) -> entity == null || net.minecraft.world.item.CrossbowItem.isCharged(stack) ? 0.0F
                                : (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks())
                                / net.minecraft.world.item.CrossbowItem.getChargeDuration(stack, entity));
                net.minecraft.client.renderer.item.ItemProperties.register(crossbow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"),
                        (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                                && !net.minecraft.world.item.CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
                net.minecraft.client.renderer.item.ItemProperties.register(crossbow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("charged"),
                        (stack, level, entity, seed) -> net.minecraft.world.item.CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
                net.minecraft.client.renderer.item.ItemProperties.register(crossbow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("firework"),
                        (stack, level, entity, seed) -> {
                            net.minecraft.world.item.component.ChargedProjectiles loaded = stack.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
                            return loaded != null && loaded.contains(net.minecraft.world.item.Items.FIREWORK_ROCKET) ? 1.0F : 0.0F;
                        });
            }
        });
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
