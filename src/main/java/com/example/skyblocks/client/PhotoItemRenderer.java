package com.example.skyblocks.client;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A photo as an item: a white card with the picture on it (wider margin at the bottom), held up in your hand, in the
 * inventory, on the ground. While the picture's still coming from the server, a blank grey-blue one.
 */
public class PhotoItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/misc/white.png");
    private static final int CARD = 0xFFF4F0E6, EDGE = 0xFFD8D2C4, BLANK = 0xFF8C9AAE;

    public PhotoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        ModComponents.Photo photo = stack.get(ModComponents.PHOTO.get());
        ResourceLocation tex = photo == null ? null : PhotoCache.get(photo.id());
        PoseStack.Pose pose = poseStack.last();
        float x0 = 1.5F / 16, x1 = 14.5F / 16, y0 = 0.5F / 16, y1 = 15.5F / 16, zf = 8.4F / 16, zb = 7.6F / 16;
        VertexConsumer card = buffers.getBuffer(RenderType.entitySolid(WHITE));
        // the card: front, back, and its edges
        quad(card, pose, x0, y1, zf, x0, y0, zf, x1, y0, zf, x1, y1, zf, CARD, 0, 0, 1, light, overlay);
        quad(card, pose, x1, y1, zb, x1, y0, zb, x0, y0, zb, x0, y1, zb, CARD, 0, 0, -1, light, overlay);
        quad(card, pose, x0, y1, zb, x0, y1, zf, x1, y1, zf, x1, y1, zb, EDGE, 0, 1, 0, light, overlay);
        quad(card, pose, x0, y0, zf, x0, y0, zb, x1, y0, zb, x1, y0, zf, EDGE, 0, -1, 0, light, overlay);
        quad(card, pose, x0, y1, zb, x0, y0, zb, x0, y0, zf, x0, y1, zf, EDGE, -1, 0, 0, light, overlay);
        quad(card, pose, x1, y1, zf, x1, y0, zf, x1, y0, zb, x1, y1, zb, EDGE, 1, 0, 0, light, overlay);
        // the picture (4:3), a little in front of the card
        float px0 = 2.5F / 16, px1 = 13.5F / 16, py1 = 14.5F / 16, py0 = py1 - (px1 - px0) * 0.75F, z = zf + 0.002F;
        if (tex == null) {
            quad(card, pose, px0, py1, z, px0, py0, z, px1, py0, z, px1, py1, z, BLANK, 0, 0, 1, light, overlay);
            return;
        }
        VertexConsumer pic = buffers.getBuffer(RenderType.entitySolid(tex));
        v(pic, pose, px0, py1, z, 0, 0, light, overlay);
        v(pic, pose, px0, py0, z, 0, 1, light, overlay);
        v(pic, pose, px1, py0, z, 1, 1, light, overlay);
        v(pic, pose, px1, py1, z, 1, 0, light, overlay);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                             float cz, float dx, float dy, float dz, int colour, float nx, float ny, float nz, int light, int overlay) {
        vc.addVertex(pose, ax, ay, az).setColor(colour).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, bx, by, bz).setColor(colour).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, cx, cy, cz).setColor(colour).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, dx, dy, dz).setColor(colour).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, int overlay) {
        vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
    }
}
