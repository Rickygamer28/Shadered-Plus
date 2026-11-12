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
 * on a skeleton (body, head, legs, tail: OiiaCatRig) posed for what it's doing - walking, running,
 * sitting up, stalking, pouncing, eating, scratching - easing between poses; napping or in a box it's the curled-up loaf
 * (breathing); petted, the loaf does the OIIA spin.
 */
public class OiiaCatEntityRenderer extends EntityRenderer<OiiaCatEntity> {
    /** Render test only: every cat walks on the spot (so its stepping shows in a still picture). */
    static boolean testWalk;
    /** Where its back paws are (model units, z): what it tips about, rearing up or bending down. */
    private static final float BACK_PAWS = -14.5F;
    /** Where its front paws are (model units, z): what it tips back about, sitting down (its rump going down, its front paws staying put). */
    private static final float FRONT_PAWS = 17.0F;
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
        // (everything's a pose of the skeleton now - the OIIA spin too, sat up and turning; the loaf model is only the
        // fallback while the rigged model isn't loaded)
        boolean loaf = false;
        if (!((spin && OiiaCatMesh.get() == null ? cat.clientSpinModel : cat.clientModel) instanceof RenderableModel model)) {
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
        if (spin && OiiaCatMesh.get() == null) {
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
        } else if (parts(cat, pose, scale, age, partialTick, buffers, light)) {
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
            TAIL_LIFT = 10, GAIT = 11, SIT = 12, HIPS = 13, PAW_FL = 14, PAW_FR = 15, PAW_BL = 16, PAW_BR = 17, TAIL_CURL = 18, EARS = 19,
            BREATH = 20, N = 21;

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
        t[BREATH] = 0.4F;
        // its head turned to what it's looking at, its tail held up and swaying, its ears up
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, cat.yHeadRotO, cat.yHeadRot) - Mth.rotLerp(partialTick, cat.yBodyRotO, cat.yBodyRot));
        t[HEAD_YAW] = Mth.clamp(headYaw, -40.0F, 40.0F);
        t[HEAD_PITCH] = Mth.clamp(Mth.lerp(partialTick, cat.xRotO, cat.getXRot()), -25.0F, 25.0F);
        t[TAIL_LIFT] = -12.0F - 8.0F * speed;
        t[TAIL_CURL] = 8.0F;
        byte mood = cat.mood();
        if (mood == OiiaCatEntity.SCRATCHING) {
            // up on its back legs against the post, front paws on it, back legs braced, looking up at it
            t[PITCH] = -42.0F;
            t[FL] = t[FR] = -55.0F;
            t[BL] = t[BR] = 42.0F;
            t[PAW_FL] = t[PAW_FR] = 15.0F;
            t[HEAD_YAW] = 0.0F;
            t[HEAD_PITCH] = 22.0F;
            t[TAIL_LIFT] = 30.0F;
            t[GAIT] = 0.0F;
        } else if (mood == OiiaCatEntity.EATING) {
            // leaning in over the bowl, head right down, ears relaxed
            t[PITCH] = 8.0F;
            t[FL] = t[FR] = -8.0F;
            t[BL] = t[BR] = -8.0F;
            t[HEAD_YAW] = 0.0F;
            t[HEAD_PITCH] = 30.0F;
            t[TAIL_LIFT] = 8.0F;
            t[EARS] = 12.0F;
            t[GAIT] = 0.0F;
        } else if (mood == OiiaCatEntity.NAPPING || mood == OiiaCatEntity.BOXED) {
            // lying down: legs folded under, tail wrapped round, head down (napping) or up looking about (in a box)
            boolean asleep = mood == OiiaCatEntity.NAPPING;
            t[LIFT] = -5.5F;
            t[FL] = t[FR] = -70.0F;
            t[PAW_FL] = t[PAW_FR] = 70.0F;
            t[BL] = t[BR] = -55.0F;
            t[PAW_BL] = t[PAW_BR] = 80.0F;
            t[HIPS] = -4.0F;
            t[TAIL_YAW] = 55.0F;
            t[TAIL_CURL] = 40.0F;
            t[TAIL_LIFT] = 26.0F;
            t[HEAD_YAW] = asleep ? 18.0F : t[HEAD_YAW];
            t[HEAD_PITCH] = asleep ? 16.0F : t[HEAD_PITCH] * 0.6F;
            t[EARS] = asleep ? 18.0F : 0.0F;
            t[BREATH] = asleep ? 1.6F : 0.6F;
            t[GAIT] = 0.0F;
        } else if (mood == OiiaCatEntity.WINDOW) {
            // lying in the window perch, looking out: legs tucked, head up following what's outside, tail hanging over
            // the side, swishing; chattering at something now and then (a quick run of little head bobs, ears forward)
            t[LIFT] = -5.5F;
            t[FL] = t[FR] = -70.0F;
            t[PAW_FL] = t[PAW_FR] = 70.0F;
            t[BL] = t[BR] = -55.0F;
            t[PAW_BL] = t[PAW_BR] = 80.0F;
            t[HIPS] = -4.0F;
            t[TAIL_YAW] = 14.0F * Mth.sin(age * 0.06F);
            t[TAIL_CURL] = 12.0F;
            t[TAIL_LIFT] = 58.0F;
            t[EARS] = -4.0F;
            t[BREATH] = 0.6F;
            t[GAIT] = 0.0F;
            if (cat.chattering(age)) {
                t[HEAD_PITCH] += 5.0F * Mth.sin(age * 2.6F);
                t[EARS] = -12.0F;
                t[TAIL_YAW] += 10.0F * Mth.sin(age * 1.3F);
            }
        } else if (mood == OiiaCatEntity.FISHING) {
            // sat by the water, eyes on the fish, tail tip twitching; a quick swat with a front paw now and then
            sit(t);
            t[EARS] = -8.0F;
            t[TAIL_YAW] += 12.0F * Mth.sin(age * 0.9F);
            float swat = cat.swat(age);
            if (swat >= 0) {
                float up = Mth.sin(swat * Mth.PI);
                t[FR] = -105.0F * up + 6.0F * (1 - up);
                t[PAW_FR] = -45.0F * up;
                t[PITCH] = 6.0F * up;
            }
        } else if (mood == OiiaCatEntity.GREETING) {
            // nose to nose with another cat: leaning in, neck stretched, ears forward, tail straight up (hello)
            t[PITCH] = 5.0F;
            t[HEAD_PITCH] = 2.0F;
            t[FL] = t[FR] = -4.0F;
            t[TAIL_LIFT] = -42.0F;
            t[TAIL_CURL] = 22.0F;
            t[EARS] = -8.0F;
            t[GAIT] = 0.0F;
        } else if (mood == OiiaCatEntity.GROOMING) {
            // sat by the other cat, leaning over to it, licking its head in long strokes
            sit(t);
            t[PITCH] = 7.0F;
            t[HEAD_PITCH] = 22.0F + 10.0F * Mth.sin(age * 0.45F);
            t[EARS] = 6.0F;
            t[BREATH] = 0.5F;
        } else if (mood == OiiaCatEntity.GROOMED) {
            // sat, being groomed: chin up into it, ears back and relaxed, a slow purr
            sit(t);
            t[HEAD_PITCH] = -12.0F;
            t[ROLL] = 3.0F;
            t[EARS] = 20.0F;
            t[BREATH] = 1.0F;
        } else if (cat.spinning() || cat.isInSittingPose() || mood == OiiaCatEntity.PERCHED) {
            sit(t);
            t[HEAD_PITCH] += 6.0F;
        } else if (mood == OiiaCatEntity.PLAYING) {
            if (air) {
                // a pounce: stretched out, front paws reaching, back legs pushing off
                t[PITCH] = -8.0F;
                t[HIPS] = 8.0F;
                t[FL] = t[FR] = -55.0F;
                t[PAW_FL] = t[PAW_FR] = -20.0F;
                t[BL] = t[BR] = 48.0F;
                t[PAW_BL] = t[PAW_BR] = -20.0F;
                t[TAIL_LIFT] = 10.0F;
                t[EARS] = -10.0F;
                t[GAIT] = 0.0F;
            } else if (speed < 0.25F) {
                // stalking: low to the ground, legs bent, ears forward, tail twitching
                t[LIFT] = -3.0F;
                t[PITCH] = 3.0F;
                t[FL] = t[FR] = -14.0F;
                t[PAW_FL] = t[PAW_FR] = 28.0F;
                t[BL] = t[BR] = -14.0F;
                t[PAW_BL] = t[PAW_BR] = 28.0F;
                t[TAIL_LIFT] = 6.0F;
                t[EARS] = -12.0F;
            }
        }
        return t;
    }

    /** Sat down: rump down on the ground (its hips tipped down), front legs straight, back legs tucked, tail curled round. */
    private static void sit(float[] t) {
        t[SIT] = -6.0F;
        t[HIPS] = -40.0F;
        t[LIFT] = -0.5F;
        t[FL] = t[FR] = 6.0F;
        t[BL] = t[BR] = -12.0F;
        t[PAW_BL] = t[PAW_BR] = 40.0F;
        t[TAIL_YAW] = 50.0F;
        t[TAIL_CURL] = 35.0F;
        t[TAIL_LIFT] = 30.0F;
        t[GAIT] = 0.0F;
    }

    /**
     * The cat on its skeleton (OiiaCatRig: chest and hips, head and ears, legs and paws, a tail in three), posed for what
     * it's doing - stood, walking (a trot, paws lifting), running (a bounding gallop, its back flexing), sat down,
     * lying down, stalking (with a wiggle), pouncing, eating, scratching - and easing from one pose to the next; its head
     * turns to what it looks at, its ears twitch, its tail sways in a wave. False while the model hasn't loaded (then
     * it's the whole model, still).
     */
    private static boolean parts(OiiaCatEntity cat, PoseStack pose, float scale, float age, float partialTick, MultiBufferSource buffers, int light) {
        OiiaCatMesh mesh = OiiaCatMesh.get();
        if (mesh == null) {
            return false;
        }
        if (!(cat.clientParts instanceof OiiaCatRig.Posed)) {
            cat.clientParts = new OiiaCatRig.Posed(mesh.bones);
        }
        OiiaCatRig.Posed bones = (OiiaCatRig.Posed) cat.clientParts;
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
        float k = p.fresh ? 1.0F : 1.0F - (float) Math.exp(-dt * (air ? 14.0F : 6.0F));
        for (int i = 0; i < N; i++) {
            p.at[i] += (target[i] - p.at[i]) * k;
        }
        p.fresh = false;
        float[] a = p.at;
        byte mood = cat.mood();
        float gait = a[GAIT] * speed;
        // running: a bounding gallop (front pair, then back pair), its back flexing; walking: a trot (diagonal pairs)
        boolean gallop = speed > 0.8F && !testWalk;
        float phase = walk * (gallop ? 0.75F : 0.9F);
        float bob = gallop ? Mth.sin(phase * 2.0F) * 1.6F : Math.abs(Mth.sin(phase)) * 1.2F;
        float rock = gallop ? Mth.sin(phase) * 5.0F : 0.0F;
        float breath = Mth.sin(age * (a[BREATH] > 1.0F ? 0.075F : 0.12F)) * a[BREATH];
        pose.translate(0, Math.abs(bob) * 0.012F * gait, 0);
        pose.mulPose(Axis.ZP.rotationDegrees((gallop ? 0.0F : Mth.sin(phase) * 2.0F * gait) + a[ROLL]));
        pose.scale(scale, scale, scale);
        pose.translate(0, OiiaCatModel.FEET + a[LIFT] + breath * 0.15F, 0);
        // the whole cat tipped back about its front paws (sitting), then about its back paws (reared up, leaning in)
        float pitch = a[PITCH] + rock * gait;
        if (mood == OiiaCatEntity.SCRATCHING) {
            pitch += Mth.sin(age * 0.5F) * 3.0F;
        } else if (mood == OiiaCatEntity.EATING) {
            pitch += Mth.sin(age * 0.6F) * 1.5F;
        }
        if (cat.spinning()) {
            // the OIIA spin (its own animation: turning, and a little bounce), sat up, about its middle
            OiiaCatModel.animate(pose, cat.spinSeconds(partialTick), true);
        }
        pose.translate(0, 0, FRONT_PAWS);
        pose.mulPose(Axis.XP.rotationDegrees(a[SIT]));
        pose.translate(0, 0, BACK_PAWS - FRONT_PAWS);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(0, 0, -BACK_PAWS);
        // the chest is where everything's measured from; the hips bend from it: the rump down to sit, the back flexing as
        // it gallops, a wiggle as it gets ready to pounce, breathing
        Matrix4f body = new Matrix4f();
        bones.set(OiiaCatRig.BODY, body);
        float wiggle = mood == OiiaCatEntity.PLAYING && a[LIFT] < -2.0F ? Mth.sin(age * 1.1F) * 7.0F : 0.0F;
        float hipsPitch = a[HIPS] + (gallop ? Mth.sin(phase + 1.2F) * 9.0F * gait : Mth.sin(phase * 2.0F) * 1.5F * gait) + breath;
        Matrix4f hips = OiiaCatRig.Posed.turn(OiiaCatRig.HIPS_AT, wiggle + Mth.sin(phase) * 3.0F * gait, hipsPitch);
        bones.set(OiiaCatRig.HIPS, hips);
        // the legs: swinging with the gait, the paw lifting (bending back) as the leg comes forward
        float swing = (gallop ? 40.0F : 28.0F) * gait;
        float[] legPhase = gallop ? new float[] {0.0F, 0.35F, Mth.PI, Mth.PI + 0.35F} : new float[] {0.0F, Mth.PI, Mth.PI, 0.0F};
        for (int i = 0; i < 4; i++) {
            float legAngle = a[FL + i] + Mth.sin(phase + legPhase[i]) * swing;
            float pawAngle = a[PAW_FL + i] + Math.max(0.0F, Mth.cos(phase + legPhase[i])) * 45.0F * gait;
            if (mood == OiiaCatEntity.SCRATCHING && i <= 1) {
                // (front paws scratching in turn: up, and a pull down the post, claws curling)
                float s = age * 0.9F + (i == 0 ? 0.0F : Mth.PI);
                legAngle += Mth.sin(s) * 22.0F + Math.max(0.0F, Mth.sin(s)) * 8.0F;
                pawAngle += Math.max(0.0F, -Mth.sin(s)) * 35.0F;
            }
            Matrix4f parent = i < 2 ? body : hips;
            Matrix4f leg = new Matrix4f(parent).mul(OiiaCatRig.Posed.turn(OiiaCatRig.LEGS_AT[i], 0.0F, legAngle));
            bones.set(OiiaCatRig.LEGS[i], leg);
            bones.set(OiiaCatRig.PAWS[i], new Matrix4f(leg).mul(OiiaCatRig.Posed.turn(OiiaCatRig.PAWS_AT[i], 0.0F, pawAngle)));
        }
        // the tail: a wave running down it (slow, more as it walks), curled more at the tip; a quick twitch stalking
        float sway = a[TAIL_YAW] + Mth.sin(age * 0.07F) * 10.0F + Mth.sin(phase * 0.5F) * 14.0F * gait;
        float twitch = mood == OiiaCatEntity.PLAYING && a[LIFT] < -2.0F ? Mth.sin(age * 2.3F) * 10.0F : 0.0F;
        float lift = a[TAIL_LIFT] + Mth.sin(age * 0.05F) * 3.0F;
        Matrix4f tail = new Matrix4f(hips).mul(OiiaCatRig.Posed.turn(OiiaCatRig.TAIL_AT, sway, lift));
        bones.set(OiiaCatRig.TAIL, tail);
        Matrix4f mid = new Matrix4f(tail).mul(OiiaCatRig.Posed.turn(OiiaCatRig.TAIL_MID_AT,
                a[TAIL_CURL] * Math.signum(a[TAIL_YAW] + 0.001F) + Mth.sin(age * 0.07F - 0.9F) * 9.0F, lift * 0.25F));
        bones.set(OiiaCatRig.TAIL_MID, mid);
        bones.set(OiiaCatRig.TAIL_TIP, new Matrix4f(mid).mul(OiiaCatRig.Posed.turn(OiiaCatRig.TAIL_TIP_AT,
                a[TAIL_CURL] * Math.signum(a[TAIL_YAW] + 0.001F) + Mth.sin(age * 0.07F - 1.8F) * 12.0F + twitch, lift * 0.2F)));
        // the head: turned to what it looks at; nodding with its steps; chewing at the bowl
        float nod = Mth.sin(phase * 2.0F) * 3.0F * gait;
        if (mood == OiiaCatEntity.EATING) {
            nod += Mth.sin(age * 0.9F) * 6.0F;
        }
        Matrix4f head = new Matrix4f(body).mul(OiiaCatRig.Posed.turn(OiiaCatRig.HEAD_AT, a[HEAD_YAW], a[HEAD_PITCH] + nod));
        bones.set(OiiaCatRig.HEAD, head);
        // the ears: up (or as the pose has them), and a quick flick now and then, one or the other
        float seed = (cat.getId() * 0.618F) % 1.0F;
        // (rarely: one every ten seconds or so, a soft one)
        float flickL = earFlick(age * 0.0045F + seed), flickR = earFlick(age * 0.0039F + seed + 0.37F);
        bones.set(OiiaCatRig.EAR_L, new Matrix4f(head).mul(OiiaCatRig.Posed.turn(OiiaCatRig.EAR_L_AT, -flickL * 7.0F, a[EARS] * 0.35F + flickL * 3.0F)));
        bones.set(OiiaCatRig.EAR_R, new Matrix4f(head).mul(OiiaCatRig.Posed.turn(OiiaCatRig.EAR_R_AT, flickR * 7.0F, a[EARS] * 0.35F + flickR * 3.0F)));
        int overlay = net.minecraft.client.renderer.texture.OverlayTexture.pack(0, net.minecraft.client.renderer.texture.OverlayTexture.v(cat.hurtTime > 0 || cat.deathTime > 0));
        mesh.render(pose.last(), buffers, bones.matrices(), light, overlay);
        return true;
    }

    /** An ear flick: mostly nothing, now and then a quick twitch out and back (0 .. 1). */
    private static float earFlick(float t) {
        float f = t - (float) Math.floor(t);
        return f < 0.025F ? Mth.sin(f / 0.025F * Mth.PI) : 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(OiiaCatEntity cat) {
        return ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "textures/item/oiia_cat.png");
    }
}
