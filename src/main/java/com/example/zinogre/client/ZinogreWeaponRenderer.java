package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

/**
 * The Zinogre weapons drawn as their 3D meshes (models/weapons/SET_KIND_STATE.bin, made by tools/make_weapon_meshes.py)
 * with the set's atlas texture; the glowing parts at full brightness. Bows and crossbows show their drawing frames
 * while you draw them, and a loaded crossbow its bolt or rocket.
 */
public class ZinogreWeaponRenderer extends BlockEntityWithoutLevelRenderer {
    private static final Map<String, Optional<ZinogreHeadRenderer.Mesh>> MESHES = new HashMap<>();
    private final String set, kind;
    private final ResourceLocation texture;

    public ZinogreWeaponRenderer(String set, String kind) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.set = set;
        this.kind = kind;
        this.texture = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "textures/item/" + set + "_atlas.png");
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer,
                             int packedLight, int packedOverlay) {
        String state = state(stack);
        String name = set + "_" + kind + "_" + state;
        ZinogreHeadRenderer.Mesh mesh = MESHES.computeIfAbsent(name, n -> Optional.ofNullable(
                ZinogreHeadRenderer.load(ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/weapons/" + n + ".bin")))).orElse(null);
        if (mesh != null) {
            ZinogreHeadRenderer.drawWithTexture(poseStack, buffer, packedLight, mesh, texture);
        }
    }

    private String state(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean drawing = player != null && player.isUsingItem() && player.getUseItem() == stack;
        switch (kind) {
            case "bow" -> {
                if (!drawing) return "default";
                float pull = (stack.getUseDuration(player) - player.getUseItemRemainingTicks()) / 20.0F;
                return pull >= 0.9F ? "pulling_2" : pull >= 0.65F ? "pulling_1" : "pulling_0";
            }
            case "crossbow" -> {
                if (CrossbowItem.isCharged(stack)) {
                    ChargedProjectiles loaded = stack.get(DataComponents.CHARGED_PROJECTILES);
                    return loaded != null && loaded.contains(Items.FIREWORK_ROCKET) ? "firework" : "arrow";
                }
                if (!drawing) return "standby";
                float pull = (float) (stack.getUseDuration(player) - player.getUseItemRemainingTicks()) / CrossbowItem.getChargeDuration(stack, player);
                return pull >= 1.0F ? "pulling_2" : pull >= 0.58F ? "pulling_1" : "pulling_0";
            }
            default -> {
                return "default";
            }
        }
    }
}
