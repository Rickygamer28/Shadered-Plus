package com.example.skyblocks.client;

import com.example.skyblocks.SingularityBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
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
        ItemStreams.render(be.getBlockPos(), poseStack, buffers); // (what its ports are moving: flying in or out)
        if (be.skin() != 0 && StorageSkins.ready()) {
            // (one of its other skins: the Dyson sphere, the tesseract, the item galaxy - StorageSkins)
            float full = be.fullness();
            float half = be.chamber() > 0 ? (be.chamber() - 1) / 2.0F - 0.55F : 0.42F; // (alone: inside its frame - how full it is shows in the skin itself)
            int skin = be.skin() == 1 ? StorageSkins.DYSON : be.skin() == 2 ? StorageSkins.TESSERACT : StorageSkins.GALAXY;
            int[] rgb;
            float[][] sprites = null;
            if (skin == StorageSkins.GALAXY) {
                rgb = StorageSkins.itemColours(be.shown());
                sprites = new float[][] {{-1, -1, -1, -1}}; // (the shader draws only its dust and heart: the items, as themselves, below)
                if (rgb.length == 0) {
                    rgb = new int[] {be.colour()};
                }
            } else {
                rgb = new int[] {be.colour()};
            }
            float[] shares = new float[rgb.length];
            java.util.Arrays.fill(shares, 1.0F / rgb.length);
            StorageSkins.draw(poseStack, buffers, skin, full, be.chamber() > 0, half, rgb, shares, 0, sprites);
            if (skin == StorageSkins.GALAXY) {
                galaxyItems(be, poseStack, buffers, half, full, partialTick);
            }
            return;
        }
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

    /**
     * The item galaxy's items: each kind it holds once (the most-held first, and largest), its own model, strewn along
     * the galaxy's spiral arms (the shader's dust) and going round with them - the inner ones a little faster, drifting
     * along the arms - each tumbling slowly, with a soft glow of its own colour behind it and a faint trail of light. (A
     * sky block, whose model is its sky drawn elsewhere, shows as a little cube of its sky's picture.)
     */
    private static void galaxyItems(SingularityBlockEntity be, PoseStack pose, MultiBufferSource buffers, float half, float full, float partialTick) {
        List<ItemStack> kinds = be.shown();
        if (kinds.isEmpty() || be.getLevel() == null) {
            return;
        }
        // (the shader's time and disk: so they lie on its arms)
        float t = ((be.getLevel().getGameTime() % 24000L) + partialTick) / 20.0F;
        org.joml.Vector3f n = new org.joml.Vector3f(0.22F, 1.0F, 0.14F).normalize();
        org.joml.Vector3f e1 = new org.joml.Vector3f(n).cross(0, 0, 1).normalize(), e2 = new org.joml.Vector3f(n).cross(e1);
        float arms = full < 0.5F ? 2.0F : 3.0F, spin = t * 0.08F;
        int count = Math.min(kinds.size(), 32);
        int[] colours = StorageSkins.itemColours(kinds);
        float[][] at = new float[count][];
        for (int k = 0; k < count; k++) {
            float h1 = ghash(k * 1.7F + 0.3F), h2 = ghash(k * 3.1F + 1.1F), h3 = ghash(k * 5.7F + 2.3F);
            // (spread out along the arms evenly-ish: the k-th along its arm, a little jitter)
            float along = (k / (int) arms + h1 * 0.6F) / Math.max(1.0F, count / arms + 0.6F);
            float r = 0.2F + 0.7F * along;
            float drift = t * 0.02F / (0.3F + r); // (inner ones ahead, a little)
            float ang = (float) Math.log(r + 0.06F) * 2.6F + (k % (int) arms) * Mth.TWO_PI / arms + (h2 - 0.5F) * 0.22F + spin + drift;
            float lift = (h3 - 0.5F) * 0.1F * (1.1F - r);
            at[k] = new float[] {(e1.x * Mth.cos(ang) + e2.x * Mth.sin(ang)) * r + n.x * lift, (e1.y * Mth.cos(ang) + e2.y * Mth.sin(ang)) * r + n.y * lift,
                    (e1.z * Mth.cos(ang) + e2.z * Mth.sin(ang)) * r + n.z * lift, ang, r};
        }
        // (first their glows and trails - soft puffs, blended (so they work under shader packs too), all at once: then the items)
        org.joml.Vector3f[] cam = MachineRenderers.camera();
        com.mojang.blaze3d.vertex.VertexConsumer glow = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
        PoseStack.Pose gp = pose.last();
        for (int k = 0; k < count; k++) {
            int c = k < colours.length ? colours[k] : 0xB0A0FF;
            float cr = (c >> 16 & 255) / 255.0F, cg = (c >> 8 & 255) / 255.0F, cb = (c & 255) / 255.0F;
            float x = 0.5F + at[k][0] * half, y = 0.5F + at[k][1] * half, z = 0.5F + at[k][2] * half;
            float mx = Math.max(0.05F, Math.max(cr, Math.max(cg, cb)));
            cr = cr / mx * 0.8F + 0.2F; cg = cg / mx * 0.8F + 0.2F; cb = cb / mx * 0.8F + 0.2F; // (its colour at full brightness: never dark)
            puff(glow, gp, cam, x, y, z, 0.2F * half * size(k), cr, cg, cb, 0.55F);
            for (int j = 1; j <= 6; j++) { // (its trail: back along its arm)
                float ang = at[k][3] - j * 0.06F, r = at[k][4];
                float tx = 0.5F + (e1.x * Mth.cos(ang) + e2.x * Mth.sin(ang)) * r * half, ty = 0.5F + (e1.y * Mth.cos(ang) + e2.y * Mth.sin(ang)) * r * half;
                float tz = 0.5F + (e1.z * Mth.cos(ang) + e2.z * Mth.sin(ang)) * r * half;
                puff(glow, gp, cam, tx, ty, tz, 0.045F * half * (1.0F - j / 7.0F), cr, cg, cb, 0.35F * (1.0F - j / 7.0F));
            }
        }
        var renderer = Minecraft.getInstance().getItemRenderer();
        for (int k = 0; k < count; k++) {
            ItemStack stack = kinds.get(k);
            float h1 = ghash(k * 1.7F + 0.3F), h2 = ghash(k * 3.1F + 1.1F);
            float s = 0.13F * half * size(k);
            pose.pushPose();
            pose.translate(0.5F + at[k][0] * half, 0.5F + at[k][1] * half, 0.5F + at[k][2] * half);
            pose.mulPose(com.mojang.math.Axis.YP.rotation(t * (0.25F + 0.3F * h1) + k * 2.0F));
            pose.mulPose(com.mojang.math.Axis.XP.rotation(0.35F * Mth.sin(t * (0.2F + 0.3F * h2) + k)));
            pose.scale(s, s, s);
            if (com.example.skyblocks.SkyLiquidBucketRecipe.skyOf(stack) != null) {
                skyCube(stack, pose, buffers);
            } else {
                renderer.renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                        be.getLevel(), k);
            }
            pose.popPose();
        }
    }

    /** A soft round glow facing you: bright in the middle, fading to nothing at its edge (added light: never darkens). */
    private static void puff(com.mojang.blaze3d.vertex.VertexConsumer v, PoseStack.Pose p, org.joml.Vector3f[] cam, float x, float y, float z, float size, float r,
                             float g, float b, float a) {
        int n = 14;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * size, s0 = Mth.sin(a0) * size, c1 = Mth.cos(a1) * size, s1 = Mth.sin(a1) * size;
            v.addVertex(p, x, y, z).setColor(r, g, b, a);
            v.addVertex(p, x, y, z).setColor(r, g, b, a);
            v.addVertex(p, x + cam[0].x() * c1 + cam[1].x() * s1, y + cam[0].y() * c1 + cam[1].y() * s1, z + cam[0].z() * c1 + cam[1].z() * s1).setColor(r, g, b, 0.0F);
            v.addVertex(p, x + cam[0].x() * c0 + cam[1].x() * s0, y + cam[0].y() * c0 + cam[1].y() * s0, z + cam[0].z() * c0 + cam[1].z() * s0).setColor(r, g, b, 0.0F);
        }
    }

    /** How big the k-th (by how many it holds) is: the most-held largest. */
    private static float size(int k) {
        return 1.15F - 0.45F * Math.min(1.0F, k / 24.0F);
    }

    /** A sky block in the galaxy: a cube of its sky's picture (its model is its sky, drawn elsewhere - here it'd be flat). */
    private static void skyCube(ItemStack stack, PoseStack pose, MultiBufferSource buffers) {
        var sprite = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0).getParticleIcon();
        com.mojang.blaze3d.vertex.VertexConsumer v = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(
                net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        float h = 0.25F;
        float[][] faces = {
                {-h, -h, h, h, -h, h, h, h, h, -h, h, h, 0, 0, 1}, {h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0, 0, -1},
                {h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1, 0, 0}, {-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1, 0, 0},
                {-h, h, h, h, h, h, h, h, -h, -h, h, -h, 0, 1, 0}, {-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, 0, -1, 0}};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                v.addVertex(p, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).setColor(255, 255, 255, 255)
                        .setUv(sprite.getU(uv[i][0]), sprite.getV(uv[i][1])).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(p, f[12], f[13], f[14]);
            }
        }
    }

    private static float ghash(float x) {
        float s = Mth.sin(x * 127.1F) * 43758.5453F;
        return s - Mth.floor(s);
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

    @Override
    public AABB getRenderBoundingBox(SingularityBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(Math.max(Math.max(be.reach(), be.shownGrow), be.chamber() / 2.0) + 0.1);
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
