package com.example.skyblocks.client;

import com.example.skyblocks.SingularityBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The singularity: its black hole (BlackHoleRenderer, kind 2: growing as it fills, its disk the dyed colour), with
 * the kinds it holds most of spiralling in round the disk - drawn before the hole, so it bends them too.
 */
public class SingularityRenderer implements BlockEntityRenderer<SingularityBlockEntity> {
    public SingularityRenderer(BlockEntityRendererProvider.Context context) {}

    /** How full it looks (easing towards how full it is). */
    private static float shownGrow(SingularityBlockEntity be) {
        float want = be.fullness();
        long now = System.nanoTime();
        if (be.shownGrowAt == 0L) {
            be.shownGrow = want;
        } else {
            float dt = Math.min(0.25F, (now - be.shownGrowAt) / 1.0e9F);
            be.shownGrow += (want - be.shownGrow) * Math.min(1.0F, dt * 2.0F);
        }
        be.shownGrowAt = now;
        return be.shownGrow;
    }

    @Override
    public void render(SingularityBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float grow = shownGrow(be);
        float reach = BlackHoleRenderer.reach(2, grow);
        float t = (float) LavaLampRenderer.seconds();
        // what it holds, going round in the disk's plane and slowly in (each its own way round), shrinking as it goes
        List<ItemStack> shown = be.shown();
        if (!MirrorRenderer.isRenderingReflection()) {
            Vector3f dn = new Vector3f(0.1F, 1.0F, 0.32F).normalize();
            Vector3f e1 = new Vector3f(dn).cross(0.0F, 0.0F, 1.0F).normalize(), e2 = new Vector3f(dn).cross(e1);
            for (int i = 0; i < shown.size(); i++) {
                float phase = (t * 0.07F + i * 0.618F) % 1.0F; // (0: at the edge, 1: gone in)
                float r = reach * (0.85F - 0.65F * phase);
                float ang = t * (0.6F + 1.4F * phase) + i * 2.4F;
                float x = (float) (Math.cos(ang) * r), y = (float) (Math.sin(ang) * r);
                float size = 0.22F * (1.0F - phase * 0.8F) * (0.6F + 0.4F * grow);
                poseStack.pushPose();
                poseStack.translate(0.5F + e1.x * x + e2.x * y, 0.5F + e1.y * x + e2.y * y, 0.5F + e1.z * x + e2.z * y);
                poseStack.mulPose(Axis.YP.rotation(t * 1.3F + i));
                poseStack.mulPose(Axis.XP.rotation(t * 0.9F + i * 0.7F));
                poseStack.scale(size, size, size);
                Minecraft.getInstance().getItemRenderer().renderStatic(shown.get(i), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, poseStack, buffers, be.getLevel(), i);
                poseStack.popPose();
            }
        }
        BlackHoleRenderer.hole(be.getBlockPos(), poseStack, buffers, 2, grow, be.colour());
    }

    @Override
    public AABB getRenderBoundingBox(SingularityBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(BlackHoleRenderer.reach(2, Math.max(be.fullness(), be.shownGrow)) + 0.1);
    }

    @Override
    public boolean shouldRenderOffScreen(SingularityBlockEntity be) {
        return true; // (as the black holes: drawn after the rest)
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
