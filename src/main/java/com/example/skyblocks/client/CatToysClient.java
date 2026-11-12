package com.example.skyblocks.client;

import com.example.skyblocks.CatToyItems;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * The cat toys as you see them in the world: a laser pointer's red dot where each player using one points it, and a
 * feather wand's feather dangling on its string in front of each player holding one (jumping when flicked).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CatToysClient {
    private static final ResourceLocation DOT = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/misc/laser_dot.png");
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/misc/white.png");
    private static final ItemStack FEATHER = new ItemStack(Items.FEATHER);

    private CatToysClient() {}

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        pose.translate(-cam.x, -cam.y, -cam.z);
        boolean any = false;
        for (Player player : level.players()) {
            if (player.distanceToSqr(cam) > 64 * 64) {
                continue;
            }
            if (player.isUsingItem() && player.getUseItem().getItem() instanceof CatToyItems.LaserPointer) {
                BlockHitResult hit = CatToyItems.dot(player, pt);
                if (hit != null) {
                    dot(pose, buffers, hit);
                    any = true;
                }
            }
            if (player.getMainHandItem().getItem() instanceof CatToyItems.FeatherWand || player.getOffhandItem().getItem() instanceof CatToyItems.FeatherWand) {
                feather(mc, level, camera, pose, buffers, player, pt);
                any = true;
            }
        }
        if (any) {
            buffers.endBatch();
        }
    }

    /** The dot, flat on whatever it hit, glowing. */
    private static void dot(PoseStack pose, MultiBufferSource buffers, BlockHitResult hit) {
        Direction face = hit.getDirection();
        Vec3 c = hit.getLocation().add(face.getStepX() * 0.01, face.getStepY() * 0.01, face.getStepZ() * 0.01);
        Vector3f n = face.step();
        Vector3f a = Math.abs(n.y) > 0.5F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f b = new Vector3f(n).cross(a);
        float s = 0.12F;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(DOT));
        PoseStack.Pose p = pose.last();
        float[][] corners = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                float[] q = corners[pass == 0 ? k : 3 - k];
                float x = (float) c.x + (a.x * q[0] + b.x * q[1]) * s, y = (float) c.y + (a.y * q[0] + b.y * q[1]) * s,
                        z = (float) c.z + (a.z * q[0] + b.z * q[1]) * s;
                vc.addVertex(p, x, y, z).setColor(-1).setUv(q[2], q[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL)
                        .setNormal(p, n.x, n.y, n.z);
            }
        }
    }

    /** The feather swaying on the ground in front of its holder, on a string up to the wand's tip. */
    private static void feather(Minecraft mc, ClientLevel level, Camera camera, PoseStack pose, MultiBufferSource buffers, Player player, float pt) {
        float t = level.getGameTime() + pt;
        Vec3 at = CatToyItems.feather(player, pt);
        float lift = 0.0F;
        if (player == mc.player) {
            float since = level.getGameTime() - CatToyItems.clientFlick + pt;
            if (since >= 0 && since < 10) {
                lift = Mth.sin(since / 10.0F * Mth.PI) * 0.7F; // (flicked up)
            }
        }
        Vec3 f = at.add(Mth.sin(t * 0.11F) * 0.25, 0.12 + lift + Math.abs(Mth.sin(t * 0.23F)) * 0.06, Mth.cos(t * 0.083F) * 0.2);
        // the wand's tip: in first person from the camera, else from the player's right hand
        Vec3 tip;
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vector3f look = camera.getLookVector(), left = camera.getLeftVector(), up = camera.getUpVector();
            tip = camera.getPosition().add(look.x * 0.9 - left.x * 0.42 + up.x * 0.15, look.y * 0.9 - left.y * 0.42 + up.y * 0.15,
                    look.z * 0.9 - left.z * 0.42 + up.z * 0.15);
        } else {
            float yaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
            tip = player.getPosition(pt).add(0, 1.55, 0).add(fwd.scale(1.0)).add(right.scale(0.35));
        }
        // the string: a gentle sag between them
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose p = pose.last();
        Vec3 prev = tip;
        int segs = 10;
        for (int i = 1; i <= segs; i++) {
            double k = i / (double) segs;
            Vec3 q = tip.lerp(f.add(0, 0.18, 0), k).add(0, -Math.sin(k * Math.PI) * 0.15, 0);
            Vec3 d = q.subtract(prev).normalize();
            lines.addVertex(p, (float) prev.x, (float) prev.y, (float) prev.z).setColor(0xFFF0F0F0).setNormal(p, (float) d.x, (float) d.y, (float) d.z);
            lines.addVertex(p, (float) q.x, (float) q.y, (float) q.z).setColor(0xFFF0F0F0).setNormal(p, (float) d.x, (float) d.y, (float) d.z);
            prev = q;
        }
        pose.pushPose();
        pose.translate(f.x, f.y, f.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(t * 4.0F));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.sin(t * 0.2F) * 20.0F));
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(f));
        mc.getItemRenderer().renderStatic(FEATHER, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
    }

    private static final class LightTexture {
        static final int FULL = 0x00F000F0;
    }
}
