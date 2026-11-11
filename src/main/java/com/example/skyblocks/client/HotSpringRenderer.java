package com.example.skyblocks.client;

import com.example.skyblocks.HotSpringBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hot spring's water: its animated milky blue surface (textures/block/hot_spring_water, rippling and bubbling), see
 * through to the pale stone, glowing faintly of its own; reaching across into the next spring where they join.
 */
public class HotSpringRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    private static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "block/hot_spring_water");

    public HotSpringRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof HotSpringBlock)) {
            return;
        }
        boolean n = state.getValue(HotSpringBlock.NORTH), s = state.getValue(HotSpringBlock.SOUTH);
        boolean e = state.getValue(HotSpringBlock.EAST), w = state.getValue(HotSpringBlock.WEST);
        float x0 = w ? 0.0F : 2.0F / 16.0F, x1 = e ? 1.0F : 14.0F / 16.0F, z0 = n ? 0.0F : 2.0F / 16.0F, z1 = s ? 1.0F : 14.0F / 16.0F;
        float y = HotSpringBlock.SURFACE;
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
        // (a little light of its own: the warm water glows softly at night)
        int glow = LightTexture.pack(Math.max(LightTexture.block(light), 9), LightTexture.sky(light));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = poseStack.last();
        // (the texture's position by world position, so joined springs are one water)
        v(vc, pose, sp, x0, y, z0, glow, overlay);
        v(vc, pose, sp, x0, y, z1, glow, overlay);
        v(vc, pose, sp, x1, y, z1, glow, overlay);
        v(vc, pose, sp, x1, y, z0, glow, overlay);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float x, float y, float z, int light, int overlay) {
        vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 214).setUv(sp.getU(x), sp.getV(z)).setOverlay(overlay).setLight(light)
                .setNormal(pose, 0, 1, 0);
    }
}
