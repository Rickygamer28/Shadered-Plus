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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The singularity: its black hole (BlackHoleRenderer, kind 2: growing as it fills, its disk the dyed colour), with
 * the kinds it holds most of spiralling in round the disk - drawn before the hole, so it bends them too.
 */
public class SingularityRenderer implements BlockEntityRenderer<SingularityBlockEntity> {
    public SingularityRenderer(BlockEntityRendererProvider.Context context) {}

    /** How far it reaches now (easing towards how far it should: as it fills, or its chamber's formed). */
    private static float shownReach(SingularityBlockEntity be) {
        float want = be.reach();
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

    /** At most this many items seen in the disk at once (by its chamber's size: 5, 7, 9 across; and none). */
    private static int maxOrbiting(int chamber) {
        return chamber >= 9 ? 12 : chamber >= 7 ? 9 : chamber >= 5 ? 6 : 4;
    }

    @Override
    public void render(SingularityBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float reach = shownReach(be);
        float t = (float) LavaLampRenderer.seconds();
        // now and then, some of what it holds drifting slowly round in the disk - drawn right after the hole, in front
        // of it (not bent out of sight by it)
        BlackHoleRenderer.After items = (ps, bufs) -> drawItems(be, reach, ps, bufs);
        if (be.chamber() > 0) {
            // (in its containment chamber: as big as it reaches, its alpha a 255th at a time - as the shader reads it)
            float grow = Math.round(Math.min(1.0F, reach / BlackHoleRenderer.CHAMBER_REACH) * 255.0F) / 255.0F;
            BlackHoleRenderer.hole(be.getBlockPos(), poseStack, buffers, 3 + (be.chamber() - 5) / 2, grow, be.colour(), items);
        } else {
            float grow = Math.max(0.0F, Math.min(1.0F, (reach - 0.38F) / 0.92F));
            BlackHoleRenderer.hole(be.getBlockPos(), poseStack, buffers, 2, grow, be.colour(), items);
        }
    }

    /** Some of what it holds, now and then, going slowly round in the disk's plane and in (more the bigger it is - not too many: fewer further away). */
    private static void drawItems(SingularityBlockEntity be, float reach, PoseStack poseStack, MultiBufferSource buffers) {
        float t = (float) LavaLampRenderer.seconds();
        List<ItemStack> shown = be.shown();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceTo(Vec3.atCenterOf(be.getBlockPos()));
        // (one of each kind at most: each slot its own kind)
        int slots = Math.min(shown.size(), (int) Math.round(maxOrbiting(be.chamber()) * (0.4 + 0.6 * be.fullness()) * (dist > 64.0 ? 0.0 : dist > 32.0 ? 0.5 : 1.0)));
        if (!MirrorRenderer.isRenderingReflection() && !shown.isEmpty()) {
            Vector3f dn = new Vector3f(0.1F, 1.0F, 0.32F).normalize();
            Vector3f e1 = new Vector3f(dn).cross(0.0F, 0.0F, 1.0F).normalize(), e2 = new Vector3f(dn).cross(e1);
            float sizeScale = be.chamber() > 0 ? 0.9F + be.chamber() * 0.1F : 1.0F;
            for (int i = 0; i < slots; i++) {
                // each its own cycle: drifting in for most of it, then gone a while
                float h = hash(i * 7.31F + be.getBlockPos().asLong() % 1000);
                float period = 45.0F + 25.0F * h; // (very slowly round, a long while before it falls in)
                float cyc = (t + h * 100.0F) / period;
                int round = (int) Math.floor(cyc);
                float phase = cyc - round;
                float showFor = 0.55F + 0.25F * hash(round * 3.7F + i);
                if (phase > showFor) {
                    continue;
                }
                float p = phase / showFor; // (0: at the edge, 1: gone in)
                ItemStack stack = shown.get(i);
                float r = reach * (0.92F - 0.75F * p);
                float ang = t * 0.07F + i * 2.4F + h * 6.0F; // (about a minute and a half a turn)
                float x = (float) (Math.cos(ang) * r), y = (float) (Math.sin(ang) * r);
                float lift = (hash(i * 1.3F + round) - 0.5F) * reach * 0.08F * (1.0F - p);
                float size = 0.22F * sizeScale * (1.0F - p * 0.85F) * Math.min(1.0F, phase * 12.0F);
                poseStack.pushPose();
                poseStack.translate(0.5F + e1.x * x + e2.x * y + dn.x * lift, 0.5F + e1.y * x + e2.y * y + dn.y * lift, 0.5F + e1.z * x + e2.z * y + dn.z * lift);
                poseStack.mulPose(Axis.YP.rotation(t * 0.7F + i * 1.3F)); // (each tilted its own way, turning)
                poseStack.mulPose(Axis.XP.rotation(0.4F + h * 0.8F));
                poseStack.scale(size, size, size);
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, poseStack, buffers, be.getLevel(), i);
                poseStack.popPose();
            }
        }
    }

    private static float hash(float n) {
        float s = (float) Math.sin(n * 12.9898F + 4.1414F) * 43758.5453F;
        return s - (float) Math.floor(s);
    }

    /** (1.20.1: the block entity's getRenderBoundingBox says - kept for reference.) */
    public AABB getRenderBoundingBox(SingularityBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(Math.max(be.reach(), be.shownGrow) + 0.1);
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
