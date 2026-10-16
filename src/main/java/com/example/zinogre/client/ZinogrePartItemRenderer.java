package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A Zinogre material drawn as the real 3D part it came from (the horn, a claw, a piece of shell, the tail, a tuft of
 * fur), cut from the Zinogre model (models/parts/item_PART.bin, already fitted in the item's unit cube), with the
 * Zinogre's or the Stygian's textures.
 */
public class ZinogrePartItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final Map<String, Optional<ZinogreHeadRenderer.Mesh>> MESHES = new HashMap<>();
    private final String part;
    private final boolean stygian;

    public ZinogrePartItemRenderer(String part, boolean stygian) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.part = part;
        this.stygian = stygian;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer,
                             int packedLight, int packedOverlay) {
        ZinogreHeadRenderer.Mesh mesh = MESHES.computeIfAbsent(part, p -> Optional.ofNullable(
                ZinogreHeadRenderer.load(ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/parts/item_" + p + ".bin")))).orElse(null);
        if (mesh != null) {
            ZinogreHeadRenderer.drawHead(poseStack, buffer, packedLight, stygian, mesh);
        }
    }
}
