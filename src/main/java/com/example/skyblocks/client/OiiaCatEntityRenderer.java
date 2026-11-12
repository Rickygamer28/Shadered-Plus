package com.example.skyblocks.client;

import com.example.skyblocks.OiiaCatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer;
import com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The OIIA cat companion, drawn with the OIIA model by Shadered's model renderer (as OiiaCatRenderer draws the block),
 * in pieces (body, head, legs, tail: tools/models/oiia_cat/split_walk.py) posed for what it's doing - walking, running,
 * sitting up, stalking, pouncing, eating, scratching - easing between poses; napping or in a box it's the curled-up loaf
 * (breathing); petted, the loaf does the OIIA spin.
 */
public class OiiaCatEntityRenderer extends EntityRenderer<OiiaCatEntity> {
    /** Render test only: every cat walks on the spot (so its stepping shows in a still picture). */
    static boolean testWalk;
    /** Where its back paws are (model units, z): what it tips about, rearing up or bending down. */
    private static final float BACK_PAWS = -14.5F;
    /** A bit smaller than the block's cat. */
    private static final float SIZE = 0.75F;

    public OiiaCatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.3F;
    }

    @Override
    public void render(OiiaCatEntity cat, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        super.render(cat, yaw, partialTick, poseStack, buffers, light); // (its name tag)
        if (!(cat.clientModel instanceof RenderableModel)) {
            cat.clientModel = OiiaCatModel.create(OiiaCatModel.MODEL);
        }
        if (!(cat.clientSpinModel instanceof RenderableModel)) {
            cat.clientSpinModel = OiiaCatModel.create(OiiaCatModel.SPIN_MODEL);
        }
        boolean spin = cat.spinning();
        byte mood = cat.mood();
        // (curled up: napping, in a box; the loaf is the spin model)
        boolean loaf = spin || mood == OiiaCatEntity.NAPPING || mood == OiiaCatEntity.BOXED;
        if (!((loaf ? cat.clientSpinModel : cat.clientModel) instanceof RenderableModel model)) {
            return; // (Shadered hasn't loaded the model yet)
        }
        float age = cat.tickCount + partialTick;
        float body = Mth.rotLerp(partialTick, cat.yBodyRotO, cat.yBodyRot);
        PoseStack pose = new PoseStack();
        pose.last().pose().set(poseStack.last().pose());
        pose.last().normal().set(poseStack.last().normal());
        // (the model faces +z: turned to face the way the cat does)
        pose.mulPose(Axis.YP.rotationDegrees(-body));
        float scale = OiiaCatModel.SCALE * SIZE;
        if (spin) {
            pose.scale(scale, scale, scale);
            pose.translate(0, OiiaCatModel.FEET, 0);
            OiiaCatModel.animate(pose, cat.spinSeconds(partialTick));
        } else if (loaf) {
            boolean napping = mood == OiiaCatEntity.NAPPING;
            // (asleep: slow deep breaths, settled a little lower; in a box, peering out: quicker, shallower)
            float breathe = napping ? 1.0F + 0.035F * Mth.sin(age * 0.075F) : 1.0F + 0.015F * Mth.sin(age * 0.16F);
            pose.scale(scale * (2.0F - breathe) * 0.5F + scale * 0.5F, scale * breathe * (napping ? 0.92F : 1.0F), scale);
            // (the loaf rests on the ground: the spin model floats SPIN_BOTTOM above its feet)
            pose.translate(0, OiiaCatModel.FEET - OiiaCatModel.SPIN_BOTTOM, 0);
            if (!napping) {
                // (in a box: turning its head about, now and then)
                pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(age * 0.021F) * 14.0F + Mth.sin(age * 0.057F) * 6.0F));
            }
        } else if (parts(cat, pose, scale, age, partialTick)) {
            OiiaCatCollar.draw(cat, pose, buffers, light, false);
            return; // (drawn in pieces)
        } else {
            pose.scale(scale, scale, scale);
            pose.translate(0, OiiaCatModel.FEET, 0);
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        model.modelMatrix = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z).mul(new Matrix4f(pose.last().pose()));
        AssimpRenderer.getInstance().addModel(model);
    }

    // ---- the cat in pieces: a pose for what it's doing, eased into, with its gait on top ----

    /** A pose's numbers (degrees; lift in model units): what the cat eases towards, and is at. */
    private static final int PITCH = 0, LIFT = 1, ROLL = 2, FL = 3, FR = 4, BL = 5, BR = 6, HEAD_YAW = 7, HEAD_PITCH = 8, TAIL_YAW = 9,
            TAIL_LIFT = 10, GAIT = 11, N = 12;

    /** Per cat (client): where its pose is now, and when it was last drawn. */
    private static final class Pose {
        final float[] at = new float[N];
        long lastNanos;
        boolean fresh = true;
    }

    private static final java.util.Map<OiiaCatEntity, Pose> POSES = new java.util.WeakHashMap<>();

    /** What it's doing: the pose it eases towards (not its gait, nor its breathing: those go on top). */
    private static float[] target(OiiaCatEntity cat, float age, float partialTick, float speed, boolean air) {
        float[] t = new float[N];
        t[GAIT] = 1.0F;
        // its head turned to what it's looking at (as far as a neck goes), its tail held up and swaying gently
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, cat.yHeadRotO, cat.yHeadRot) - Mth.rotLerp(partialTick, cat.yBodyRotO, cat.yBodyRot));
        t[HEAD_YAW] = Mth.clamp(headYaw, -38.0F, 38.0F);
        t[HEAD_PITCH] = Mth.clamp(Mth.lerp(partialTick, cat.xRotO, cat.getXRot()), -22.0F, 25.0F);
        t[TAIL_LIFT] = -10.0F - 8.0F * speed;
        byte mood = cat.mood();
        if (mood == OiiaCatEntity.SCRATCHING) {
            // up on its back legs against the post, front paws on it, back legs braced, looking up at it
            t[PITCH] = -42.0F;
            t[FL] = t[FR] = -55.0F;
            t[BL] = t[BR] = 42.0F;
            t[HEAD_YAW] = 0.0F;
            t[HEAD_PITCH] = 18.0F;
            t[TAIL_LIFT] = 30.0F;
            t[GAIT] = 0.0F;
        } else if (mood == OiiaCatEntity.EATING) {
            // leaning in over the bowl, head down
            t[PITCH] = 9.0F;
            t[LIFT] = -1.0F;
            t[FL] = t[FR] = -9.0F;
            t[BL] = t[BR] = -9.0F;
            t[HEAD_YAW] = 0.0F;
            t[HEAD_PITCH] = 24.0F;
            t[TAIL_LIFT] = 4.0F;
            t[GAIT] = 0.0F;
        } else if (cat.isInSittingPose() || mood == OiiaCatEntity.PERCHED) {
            // sat up: front legs straight, back legs tucked under, tail down round it
            t[PITCH] = -30.0F;
            t[LIFT] = -1.5F;
            t[FL] = t[FR] = 30.0F;
            t[BL] = t[BR] = -48.0F;
            t[HEAD_PITCH] += 26.0F;
            t[TAIL_LIFT] = 34.0F;
            t[TAIL_YAW] = 25.0F;
            t[GAIT] = 0.0F;
        } else if (mood == OiiaCatEntity.PLAYING) {
            if (air) {
                // a pounce: stretched out, front paws reaching, back legs pushing off
                t[PITCH] = -8.0F;
                t[FL] = t[FR] = -50.0F;
                t[BL] = t[BR] = 45.0F;
                t[TAIL_LIFT] = 10.0F;
                t[GAIT] = 0.0F;
            } else if (speed < 0.25F) {
                // stalking: low to the ground, tail twitching
                t[LIFT] = -3.5F;
                t[PITCH] = 4.0F;
                t[FL] = t[FR] = -4.0F;
                t[BL] = t[BR] = -4.0F;
                t[TAIL_LIFT] = 6.0F;
            }
        }
        return t;
    }

    /**
     * The cat in its pieces (body, head, four legs, tail), posed for what it's doing - stood, walking (a trot), running
     * (a bounding gallop), sat up, stalking, pouncing, eating, scratching - and easing from one pose to the next; its
     * head turns to what it looks at, its tail sways. False while the pieces haven't loaded (then it's the whole model).
     */
    private static boolean parts(OiiaCatEntity cat, PoseStack pose, float scale, float age, float partialTick) {
        int n = OiiaCatModel.PARTS.length;
        Object[] parts = cat.clientParts instanceof Object[] had && had.length == n ? had : new Object[n];
        cat.clientParts = parts;
        for (int i = 0; i < n; i++) {
            if (!(parts[i] instanceof RenderableModel)) {
                parts[i] = OiiaCatModel.create(OiiaCatModel.part(i));
                if (parts[i] == null) {
                    return false;
                }
            }
        }
        float walk = cat.walkAnimation.position(partialTick), speed = Math.min(1.0F, cat.walkAnimation.speed(partialTick) * 1.4F);
        if (testWalk) {
            walk = age * 0.35F;
            speed = 1.0F;
        }
        boolean air = !cat.onGround() && !cat.isInSittingPose();
        // ease towards the pose (quicker into a pounce)
        Pose p = POSES.computeIfAbsent(cat, c -> new Pose());
        float[] target = target(cat, age, partialTick, speed, air);
        long now = System.nanoTime();
        float dt = p.fresh ? 1.0F : Math.min(0.25F, (now - p.lastNanos) / 1.0E9F);
        p.lastNanos = now;
        float k = p.fresh ? 1.0F : 1.0F - (float) Math.exp(-dt * (air ? 14.0F : 7.0F));
        for (int i = 0; i < N; i++) {
            p.at[i] += (target[i] - p.at[i]) * k;
        }
        p.fresh = false;
        float[] a = p.at;
        byte mood = cat.mood();
        float gait = a[GAIT] * speed;
        // running: a bounding gallop (front pair, then back pair); walking: a trot (diagonal pairs)
        boolean gallop = speed > 0.8F && !testWalk;
        float phase = walk * (gallop ? 0.75F : 0.9F);
        float bob = gallop ? Mth.sin(phase * 2.0F) * 1.6F : Math.abs(Mth.sin(phase)) * 1.2F;
        float rock = gallop ? Mth.sin(phase) * 5.0F : 0.0F;
        // (stalking: a little wiggle of the back end, as before a pounce)
        float wiggle = mood == OiiaCatEntity.PLAYING && a[LIFT] < -2.0F ? Mth.sin(age * 1.1F) * 2.5F : 0.0F;
        pose.translate(0, Math.abs(bob) * 0.012F * gait, 0);
        pose.mulPose(Axis.ZP.rotationDegrees((gallop ? 0.0F : Mth.sin(phase) * 2.5F * gait) + a[ROLL]));
        pose.scale(scale, scale, scale);
        pose.translate(0, OiiaCatModel.FEET + a[LIFT], 0);
        // the whole cat tipped about its back paws (sat up, reared up, leaning in), and rocking as it gallops
        float pitch = a[PITCH] + rock * gait;
        if (mood == OiiaCatEntity.SCRATCHING) {
            pitch += Mth.sin(age * 0.5F) * 3.0F;
        } else if (mood == OiiaCatEntity.EATING) {
            pitch += Mth.sin(age * 0.6F) * 2.0F;
        }
        pose.translate(0, 0, BACK_PAWS);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(wiggle));
        pose.translate(0, 0, -BACK_PAWS);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Matrix4f world = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z).mul(new Matrix4f(pose.last().pose()));
        float swing = (gallop ? 40.0F : 30.0F) * gait;
        float[] legPhase = gallop ? new float[] {0.0F, 0.35F, Mth.PI, Mth.PI + 0.35F} : new float[] {0.0F, Mth.PI, Mth.PI, 0.0F};
        for (int i = 0; i < n; i++) {
            float[] pv = OiiaCatModel.PIVOTS[i];
            Matrix4f m = new Matrix4f(world);
            if (i >= 1 && i <= 4) {
                float angle = a[FL + i - 1] + Mth.sin(phase + legPhase[i - 1]) * swing;
                if (mood == OiiaCatEntity.SCRATCHING && i <= 2) {
                    // (front paws scratching in turn: up, and a pull down the post)
                    float s = age * 0.9F + (i == 1 ? 0.0F : Mth.PI);
                    angle += Mth.sin(s) * 22.0F + Math.max(0.0F, Mth.sin(s)) * 8.0F;
                }
                m.translate(pv[0], pv[1], pv[2]).rotateX(angle * Mth.DEG_TO_RAD).translate(-pv[0], -pv[1], -pv[2]);
            } else if (i == 5) {
                // the tail: a slow sway, more as it walks, a quick twitch when it's stalking
                float sway = a[TAIL_YAW] + Mth.sin(age * 0.07F) * 9.0F + Mth.sin(phase * 0.5F) * 14.0F * gait;
                if (mood == OiiaCatEntity.PLAYING && a[LIFT] < -2.0F) {
                    sway += Mth.sin(age * 2.3F) * 6.0F;
                }
                float lift = a[TAIL_LIFT] + Mth.sin(age * 0.05F) * 3.0F;
                m.translate(pv[0], pv[1], pv[2]).rotateY(sway * Mth.DEG_TO_RAD).rotateX(lift * Mth.DEG_TO_RAD).translate(-pv[0], -pv[1], -pv[2]);
            } else if (i == 6) {
                // the head: turned to what it looks at; nodding with its steps; chewing at the bowl
                float nod = Mth.sin(phase * 2.0F) * 2.5F * gait;
                if (mood == OiiaCatEntity.EATING) {
                    nod += Mth.sin(age * 0.9F) * 6.0F;
                }
                m.translate(pv[0], pv[1], pv[2]).rotateY(a[HEAD_YAW] * Mth.DEG_TO_RAD).rotateX((a[HEAD_PITCH] + nod) * Mth.DEG_TO_RAD)
                        .translate(-pv[0], -pv[1], -pv[2]);
            }
            RenderableModel part = (RenderableModel) parts[i];
            part.modelMatrix = m;
            AssimpRenderer.getInstance().addModel(part);
        }
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(OiiaCatEntity cat) {
        return ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "textures/item/oiia_cat.png");
    }
}
