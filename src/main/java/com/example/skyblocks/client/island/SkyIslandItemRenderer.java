package com.example.skyblocks.client.island;

import com.example.skyblocks.ModBlocks;
import com.example.skyblocks.island.IslandData;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Sky Island item in 3D: its pedestal with the island floating above it (in hand, in the inventory, in item frames
 * and on the ground). Mobs are shown too, except in inventories; chests, sky blocks and cats are left out here (they
 * are drawn by renderers meant for the world).
 */
public class SkyIslandItemRenderer extends BlockEntityWithoutLevelRenderer {
    /** Decoded islands, most recently used last (a few are kept so items don't re-decode every frame). */
    private static final Map<IslandData, IslandView> VIEWS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<IslandData, IslandView> eldest) {
            if (size() > 24) {
                eldest.getValue().close();
                return true;
            }
            return false;
        }
    };

    public SkyIslandItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer,
                             int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        poseStack.pushPose();
        // The pedestal is only 3 pixels tall with the island above it, so held in hand it sat below the view:
        // lift it so the whole thing is in sight.
        if (context.firstPerson()) {
            poseStack.translate(0.0F, 0.45F, 0.0F);
        } else if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            poseStack.translate(0.0F, 0.2F, 0.0F);
        }
        mc.getBlockRenderer().renderSingleBlock(ModBlocks.SKY_ISLAND.get().defaultBlockState(), poseStack, buffer,
                packedLight, packedOverlay);

        IslandData data = IslandData.get(stack);
        Level level = mc.level;
        IslandView view = data == null || level == null ? null : view(data, level);
        if (view != null) {
            try {
                IslandMesh mesh = view.mesh();
                view.tickTo(level.getGameTime());
                int wide = Math.max(view.contentWidth(), view.contentDepth());
                float scale = Math.min(0.9F / wide, 1.1F / view.contentHeight());
                poseStack.translate(0.5F, 0.25F, 0.5F);
                poseStack.scale(scale, scale, scale);
                poseStack.translate(-view.contentCenterX(), -view.lowestLayer(), -view.contentCenterZ());
                mesh.render(poseStack.last(), buffer);
                if (context != ItemDisplayContext.GUI) {
                    view.renderFigures(poseStack, buffer, mc.getFrameTime());
                }
            } catch (RuntimeException e) {
                VIEWS.remove(data); // try again later
            }
        }
        poseStack.popPose();
    }

    @Nullable
    private static IslandView view(IslandData data, Level level) {
        IslandView view = VIEWS.get(data);
        if (view == null) {
            try {
                view = IslandView.decode(data, level, net.minecraft.core.BlockPos.ZERO);
            } catch (RuntimeException e) {
                view = null;
            }
            if (view != null) {
                VIEWS.put(data, view);
            }
        }
        return view;
    }
}
