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
 * The OIIA cat companion: the rigged OIIA model (skinned by OiiaCatMesh) on its skeleton (OiiaCatRig), posed for what
 * it's doing and sprung between poses with some weight. Walking, its paws land one at a time and plant (no sliding);
 * galloping, it bounds, its back flexing; it steps round turning on the spot; its head stays level as its body moves;
 * its tail follows through; it lands with a squash, stretches on waking, shifts its weight, swivels its ears, tilts its
 * head; and the moods' own poses (sat, lying, asleep and dreaming, stalking, pouncing, eating, scratching, grooming,
 * watching fish, at the window). Petted, it does the OIIA spin.
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
        // (in the window perch: the loaf, sat neatly on the hammock looking out)
        boolean loaf = mood == OiiaCatEntity.WINDOW && !spin;
        if (!((spin && OiiaCatMesh.get() == null || loaf ? cat.clientSpinModel : cat.clientModel) instanceof RenderableModel model)) {
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

    // ---- the cat in pieces: a pose for what it's doing, sprung into, with its gait and its life on top ----

    /** A pose's numbers (degrees; lift in model units): what the cat moves towards, and where it is. */
    private static final int PITCH = 0, LIFT = 1, ROLL = 2, FL = 3, FR = 4, BL = 5, BR = 6, HEAD_YAW = 7, HEAD_PITCH = 8, TAIL_YAW = 9,
            TAIL_LIFT = 10, GAIT = 11, SIT = 12, HIPS = 13, PAW_FL = 14, PAW_FR = 15, PAW_BL = 16, PAW_BR = 17, TAIL_CURL = 18, EARS = 19,
            BREATH = 20, HEAD_ROLL = 21, EAR_SPLAY = 22, N = 23;
    /**
     * How quickly each number follows its pose (a spring's natural frequency, per second; critically damped but the
     * tail, which overshoots a little and settles): the body weighty, the legs quick, the head between, the tail
     * lagging behind, the ears quick.
     */
    private static final float[] OMEGA = new float[N], DAMPING = new float[N];

    static {
        java.util.Arrays.fill(OMEGA, 9.0F);
        java.util.Arrays.fill(DAMPING, 1.0F);
        for (int i : new int[] {FL, FR, BL, BR, PAW_FL, PAW_FR, PAW_BL, PAW_BR}) OMEGA[i] = 15.0F;
        for (int i : new int[] {HEAD_YAW, HEAD_PITCH, HEAD_ROLL}) OMEGA[i] = 11.0F;
        for (int i : new int[] {TAIL_YAW, TAIL_LIFT, TAIL_CURL}) {
            OMEGA[i] = 5.0F;
            DAMPING[i] = 0.7F;
        }
        OMEGA[EARS] = OMEGA[EAR_SPLAY] = 14.0F;
        OMEGA[GAIT] = 7.0F;
        OMEGA[BREATH] = 2.0F;
    }

    /** Per cat (client): where its pose is and how fast it's moving, its stride, what it's just done. */
    private static final class Pose {
        final float[] at = new float[N], vel = new float[N];
        long lastNanos;
        boolean fresh = true;
        /** Through its stride (cycles), and where the walk was. */
        float cycle, lastWalk;
        /** Its body turning (degrees a second), and where it last faced. */
        float yawRate, lastYaw;
        /** Its tail's follow-through, segment by segment (a spring each, following the one before). */
        final float[] swing = new float[3], swingVel = new float[3];
        float tailFall, tailFallVel;
        boolean wasAir;
        byte lastMood;
        float stretchStart = -1000.0F, landStart = -1000.0F, landHard;
    }

    private static final java.util.Map<OiiaCatEntity, Pose> POSES = new java.util.WeakHashMap<>();

    /** A test cat's stride and speed (named "anim:walk:0.25", "anim:gallop:0.5", "anim:up", "anim:down", "anim:stretch:0.3"). */
    private static String[] test(OiiaCatEntity cat) {
        net.minecraft.network.chat.Component name = cat.getCustomName();
        String n = name == null ? "" : name.getString();
        return n.startsWith("anim:") ? n.split(":") : null;
    }

    /** What it's doing: the pose it moves towards (not its stride, nor its breathing, nor its tail's swing: those go on top). */
    private static float[] target(OiiaCatEntity cat, Pose p, float age, float partialTick, float speed, boolean air, float vy, String[] test) {
        float[] t = new float[N];
        t[GAIT] = 1.0F;
        t[BREATH] = 0.45F;
        float seed = (cat.getId() * 0.618F) % 1.0F;
        // its head turned to what it's looking at (a little of it in a tilt), its ears up
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, cat.yHeadRotO, cat.yHeadRot) - Mth.rotLerp(partialTick, cat.yBodyRotO, cat.yBodyRot));
        t[HEAD_YAW] = Mth.clamp(headYaw, -55.0F, 55.0F);
        t[HEAD_PITCH] = Mth.clamp(Mth.lerp(partialTick, cat.xRotO, cat.getXRot()), -30.0F, 30.0F);
        t[HEAD_ROLL] = -t[HEAD_YAW] * 0.12F;
        // its tail: up in a soft curve as it walks about, lower stood still, streaming out behind at a run
        float run = Mth.clamp((speed - 0.6F) / 0.3F, 0.0F, 1.0F);
        t[TAIL_LIFT] = Mth.lerp(run, 14.0F + 26.0F * Math.min(1.0F, speed * 2.0F), 2.0F);
        t[TAIL_CURL] = Mth.lerp(run, 16.0F, 4.0F);
        byte mood = cat.mood();
        boolean idle = speed < 0.05F && !air;
        if (mood == OiiaCatEntity.SCRATCHING) {
            // up on its back legs against the post, front paws high on it, back legs braced, its back in a long stretch
            t[PITCH] = -42.0F;
            t[HIPS] = 6.0F;
            t[FL] = t[FR] = -62.0F;
            t[BL] = t[BR] = 40.0F;
            t[PAW_FL] = t[PAW_FR] = 12.0F;
            t[PAW_BL] = t[PAW_BR] = -10.0F;
            t[HEAD_YAW] = 0.0F;
            t[HEAD_PITCH] = 26.0F;
            t[HEAD_ROLL] = 0.0F;
            t[TAIL_LIFT] = 34.0F;
            t[TAIL_CURL] = 20.0F;
            t[EARS] = 8.0F;
            t[GAIT] = 0.0F;
        } else if (mood == OiiaCatEntity.EATING) {
            // crouched over the bowl, front legs a little apart and bent, head right down, ears relaxed, tail low
            t[PITCH] = 9.0F;
            t[LIFT] = -1.0F;
            t[FL] = t[FR] = -10.0F;
            t[PAW_FL] = t[PAW_FR] = 14.0F;
            t[BL] = t[BR] = -6.0F;
            t[PAW_BL] = t[PAW_BR] = 12.0F;
            t[HEAD_YAW] = 0.0F;
            t[HEAD_PITCH] = 34.0F;
            t[HEAD_ROLL] = 0.0F;
            t[TAIL_LIFT] = -6.0F;
            t[TAIL_YAW] = 18.0F;
            t[TAIL_CURL] = 14.0F;
            t[EARS] = 12.0F;
            t[EAR_SPLAY] = 6.0F;
            t[GAIT] = 0.0F;
        } else if (mood == OiiaCatEntity.NAPPING || mood == OiiaCatEntity.BOXED || mood == OiiaCatEntity.WINDOW) {
            // lying down: legs folded under (a loaf), tail wrapped round; asleep, curled with its head down on its paws;
            // in a box or the window, head up looking about (the window: tail hanging over the side)
            boolean asleep = mood == OiiaCatEntity.NAPPING;
            t[LIFT] = -5.6F;
            t[PITCH] = asleep ? 2.0F : 0.0F;
            t[FL] = t[FR] = -72.0F;
            t[PAW_FL] = t[PAW_FR] = 76.0F;
            t[BL] = t[BR] = -58.0F;
            t[PAW_BL] = t[PAW_BR] = 84.0F;
            t[HIPS] = -4.0F;
            t[ROLL] = asleep ? 4.0F : 0.0F;
            t[TAIL_YAW] = 58.0F;
            t[TAIL_CURL] = 42.0F;
            t[TAIL_LIFT] = -14.0F;
            t[HEAD_YAW] = asleep ? 22.0F : t[HEAD_YAW];
            t[HEAD_PITCH] = asleep ? 24.0F : t[HEAD_PITCH] * 0.6F - 4.0F;
            t[HEAD_ROLL] = asleep ? 10.0F : t[HEAD_ROLL];
            t[EARS] = asleep ? 16.0F : 0.0F;
            t[EAR_SPLAY] = asleep ? 10.0F : 0.0F;
            t[BREATH] = asleep ? 1.7F : 0.6F;
            t[GAIT] = 0.0F;
            if (mood == OiiaCatEntity.WINDOW) {
                // (a true loaf on the thin hammock: front paws tucked back under the chest, hind feet folded flat under
                // the haunches - nothing poking down through the fleece)
                t[FL] = t[FR] = 72.0F;
                t[PAW_FL] = t[PAW_FR] = 18.0F;
                t[BL] = t[BR] = -50.0F;
                t[PAW_BL] = t[PAW_BR] = 140.0F;
                t[LIFT] = -4.6F;
                t[TAIL_YAW] = 10.0F * Mth.sin(age * 0.06F);
                t[TAIL_CURL] = 14.0F;
                t[TAIL_LIFT] = -58.0F;
                t[EARS] = -6.0F;
                if (cat.chattering(age)) {
                    // (chattering at a bird: a quick run of little jaw-flutters, ears pricked, tail lashing)
                    t[HEAD_PITCH] += 5.0F * Mth.sin(age * 2.6F);
                    t[EARS] = -14.0F;
                    t[TAIL_YAW] += 14.0F * Mth.sin(age * 1.3F);
                }
            }
            if (asleep) {
                // (dreaming: now and then a paw twitches, an ear flicks, the tail tip stirs)
                float dream = blip(age * 0.006F + seed * 3.0F, 0.03F);
                t[PAW_FR] -= dream * 30.0F;
                t[FR] -= dream * 10.0F;
                t[EARS] += blip(age * 0.009F + seed, 0.02F) * 14.0F;
            }
        } else if (mood == OiiaCatEntity.FISHING) {
            // sat by the water, leaning in, eyes on the fish, tail tip twitching; a quick swat with a front paw now and then
            sit(t);
            t[PITCH] = 4.0F;
            t[EARS] = -10.0F;
            t[TAIL_YAW] += 14.0F * Mth.sin(age * 0.9F);
            float swat = cat.swat(age);
            if (swat >= 0) {
                // (a scoop: the paw up and out, a flick down, back)
                float up = Mth.sin(Math.min(1.0F, swat * 1.4F) * Mth.PI);
                t[FR] = -110.0F * up + 6.0F * (1 - up);
                t[PAW_FR] = -50.0F * up + 25.0F * Mth.sin(swat * Mth.PI * 2.0F) * up;
                t[PITCH] = 4.0F + 8.0F * up;
                t[HEAD_PITCH] += 8.0F * up;
            }
        } else if (mood == OiiaCatEntity.GREETING) {
            // nose to nose with another cat: up on its toes, leaning in, neck stretched, ears forward, tail straight up
            t[PITCH] = 5.0F;
            t[LIFT] = 0.4F;
            t[HEAD_PITCH] = -2.0F;
            t[HEAD_ROLL] = 0.0F;
            t[FL] = t[FR] = -6.0F;
            t[TAIL_LIFT] = 64.0F;
            t[TAIL_CURL] = 26.0F;
            t[TAIL_YAW] = 0.0F;
            t[EARS] = -10.0F;
            t[GAIT] = 0.0F;
        } else if (mood == OiiaCatEntity.GROOMING) {
            // sat by the other cat, leaning over to it, licking its head in long strokes (down the stroke slow, up quick)
            sit(t);
            t[PITCH] = 8.0F;
            float stroke = age * 0.42F;
            t[HEAD_PITCH] = 18.0F + 14.0F * (Mth.sin(stroke) + 0.3F * Mth.sin(stroke * 2.0F));
            t[HEAD_ROLL] = 8.0F * Mth.sin(stroke * 0.5F);
            t[EARS] = 6.0F;
            t[BREATH] = 0.6F;
        } else if (mood == OiiaCatEntity.GROOMED) {
            // sat, being groomed: chin up into it, eyes half shut (ears back and out), a slow purr, leaning in
            sit(t);
            t[HEAD_PITCH] = -16.0F;
            t[HEAD_ROLL] = 9.0F;
            t[ROLL] = 4.0F;
            t[EARS] = 18.0F;
            t[EAR_SPLAY] = 14.0F;
            t[BREATH] = 1.1F;
        } else if (cat.spinning() || cat.isInSittingPose() || mood == OiiaCatEntity.PERCHED) {
            sit(t);
            t[HEAD_PITCH] += 4.0F;
        } else if (mood == OiiaCatEntity.PLAYING && !air && speed < 0.3F) {
            // stalking: belly to the ground, shoulders up, legs bent, ears forward, eyes fixed, tail low and twitching
            t[LIFT] = -3.2F;
            t[PITCH] = 2.0F;
            t[HIPS] = -3.0F;
            t[FL] = t[FR] = -18.0F;
            t[PAW_FL] = t[PAW_FR] = 36.0F;
            t[BL] = t[BR] = -16.0F;
            t[PAW_BL] = t[PAW_BR] = 34.0F;
            t[HEAD_PITCH] = Math.min(t[HEAD_PITCH], 4.0F) - 4.0F;
            t[HEAD_ROLL] = 0.0F;
            t[TAIL_LIFT] = -6.0F;
            t[TAIL_CURL] = 8.0F;
            t[EARS] = -14.0F;
        }
        // in the air (a pounce, a hop up a block): reaching up and out going up, reaching down for the ground coming down
        if (air && mood != OiiaCatEntity.SCRATCHING) {
            float up = Mth.clamp(vy * 4.0F, -1.0F, 1.0F);
            float rising = Math.max(0.0F, up), falling = Math.max(0.0F, -up);
            t[PITCH] = -10.0F * rising + 8.0F * falling;
            t[LIFT] = 0.0F;
            t[HIPS] = 10.0F * rising - 6.0F * falling;
            t[FL] = t[FR] = -58.0F * rising - 30.0F * falling - 20.0F * (1 - rising - falling);
            t[PAW_FL] = t[PAW_FR] = -18.0F * rising + 10.0F * falling;
            t[BL] = t[BR] = 52.0F * rising - 22.0F * falling;
            t[PAW_BL] = t[PAW_BR] = -22.0F * rising + 30.0F * falling;
            t[TAIL_LIFT] = 6.0F + 20.0F * falling;
            t[TAIL_CURL] = 2.0F;
            t[EARS] = -8.0F;
            t[GAIT] = 0.0F;
        }
        // just woken: a big stretch - front legs out long, rump up (a bow), then a lean forward stretching the back legs out
        float stretch = (age - p.stretchStart) / 70.0F;
        if (stretch >= 0.0F && stretch < 1.0F && !air && mood == OiiaCatEntity.AWAKE) {
            float bow = Mth.sin(Math.min(1.0F, stretch / 0.55F) * Mth.PI), lean = Mth.sin(Mth.clamp((stretch - 0.45F) / 0.55F, 0.0F, 1.0F) * Mth.PI);
            t[PITCH] = 16.0F * bow - 6.0F * lean;
            t[LIFT] = -1.5F * bow;
            t[FL] = t[FR] = -62.0F * bow + 6.0F * lean;
            t[PAW_FL] = t[PAW_FR] = -8.0F * bow;
            t[HIPS] = 8.0F * bow - 4.0F * lean;
            t[BL] = t[BR] = 8.0F * bow + 55.0F * lean;
            t[PAW_BL] = t[PAW_BR] = -15.0F * lean;
            t[HEAD_PITCH] = -18.0F * bow + 6.0F * lean;
            t[HEAD_ROLL] = 0.0F;
            t[TAIL_LIFT] = 48.0F * bow + 20.0F * lean;
            t[EARS] = 14.0F * bow;
            t[GAIT] = 0.0F;
        }
        // stood about, awake: shifting its weight now and then, and now and then a curious tilt of the head
        if (idle && mood == OiiaCatEntity.AWAKE && !cat.isInSittingPose() && stretch >= 1.0F) {
            t[ROLL] += 1.6F * Mth.sin(age * 0.023F + seed * 6.0F);
            t[PITCH] += 1.2F * Mth.sin(age * 0.017F + seed * 4.0F);
        }
        if (idle && (mood == OiiaCatEntity.AWAKE || mood == OiiaCatEntity.PERCHED)) {
            float tilt = blip(age * 0.0045F + seed * 7.0F, 0.12F);
            t[HEAD_ROLL] += tilt * (seed > 0.5F ? 16.0F : -16.0F);
            t[HEAD_PITCH] -= tilt * 4.0F;
        }
        if (test != null && test.length >= 2 && test[1].equals("sit")) {
            sit(t);
        }
        return t;
    }

    /** Sat down: rump down on the ground (its hips tipped down), chest up, front legs straight and together, back legs tucked, tail curled round its feet. */
    private static void sit(float[] t) {
        t[SIT] = -7.0F;
        t[HIPS] = -42.0F;
        t[LIFT] = -0.5F;
        t[PITCH] = -2.0F;
        t[FL] = t[FR] = 7.0F;
        t[PAW_FL] = t[PAW_FR] = -4.0F;
        t[BL] = t[BR] = -14.0F;
        t[PAW_BL] = t[PAW_BR] = 44.0F;
        t[TAIL_YAW] = 62.0F;
        t[TAIL_CURL] = 40.0F;
        t[TAIL_LIFT] = 26.0F;
        t[GAIT] = 0.0F;
    }

    /** A short event now and then (period: one per unit of t): its strength 0 .. 1 .. 0 over the first `length` of each. */
    private static float blip(float t, float length) {
        float f = t - (float) Math.floor(t);
        return f < length ? Mth.sin(f / length * Mth.PI) : 0.0F;
    }

    /** One leg through its stride (cycle 0 .. 1): on the ground the first `duty` of it, sweeping back; then lifted and swung forward. [leg, paw] */
    private static float[] stride(float cycle, float duty, float reach, float lift) {
        float c = cycle - (float) Math.floor(cycle);
        if (c < duty) {
            float u = c / duty;
            // (planted: the leg sweeps back at an even pace; the paw gives a little as it takes the weight)
            return new float[] {Mth.lerp(u, -reach, reach), 8.0F * Mth.sin(u * Mth.PI)};
        }
        float u = (c - duty) / (1.0F - duty);
        float e = u * u * (3.0F - 2.0F * u);
        // (lifted: the paw folds up and the leg swings through, reaching a little past its mark before it lands)
        return new float[] {Mth.lerp(e, reach, -reach * 1.08F), lift * Mth.sin(u * Mth.PI)};
    }

    /**
     * The cat on its skeleton (OiiaCatRig: chest and hips, head and ears, legs and paws, a tail in three), posed for what
     * it's doing and sprung into it; its stride (a walk, its feet one at a time, or a gallop) laid on top with its body
     * bobbing, swaying and flexing with it and its head held steady; its tail following through as it turns; landing
     * with a squash; a stretch on waking; breathing, ears swivelling, weight shifting. False while the model hasn't
     * loaded (then it's the whole model, still).
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
        Pose p = POSES.computeIfAbsent(cat, c -> new Pose());
        String[] test = test(cat);
        float walk = cat.walkAnimation.position(partialTick), speed = Math.min(1.0F, cat.walkAnimation.speed(partialTick) * 1.4F);
        float vy = (float) (cat.getY() - cat.yo);
        boolean air = !cat.onGround() && !cat.isInSittingPose() && !cat.isPassenger() && Math.abs(vy) > 0.02F;
        long now = System.nanoTime();
        float dt = p.fresh ? 0.05F : Math.min(0.1F, (now - p.lastNanos) / 1.0E9F);
        p.lastNanos = now;
        // (the test scene's cats: a stride, an air pose, a stretch, held still for a picture)
        float testCycle = -1;
        if (test != null && test.length >= 2) {
            switch (test[1]) {
                case "walk" -> speed = 0.45F;
                case "gallop" -> speed = 1.0F;
                case "up" -> {
                    air = true;
                    vy = 0.3F;
                }
                case "down" -> {
                    air = true;
                    vy = -0.3F;
                }
                case "stretch" -> p.stretchStart = age - 70.0F * (test.length >= 3 ? Float.parseFloat(test[2]) : 0.3F);
                default -> {}
            }
            if (test.length >= 3 && (test[1].equals("walk") || test[1].equals("gallop"))) {
                testCycle = Float.parseFloat(test[2]);
            }
        } else if (testWalk) {
            speed = 0.45F;
            testCycle = age * 0.05F;
        }
        // what it's just done: woken (a stretch), landed (a squash)
        byte mood = cat.mood();
        if (p.lastMood == OiiaCatEntity.NAPPING && mood == OiiaCatEntity.AWAKE && !p.fresh) {
            p.stretchStart = age;
        }
        p.lastMood = mood;
        if (p.wasAir && !air && !p.fresh) {
            p.landStart = age;
            p.landHard = Mth.clamp(-p.tailFall * 0.04F + 0.6F, 0.4F, 1.0F);
        }
        p.wasAir = air;
        // its stride: on with the distance walked (so its paws don't slide), a gallop's strides longer
        float run = Mth.clamp((speed - 0.62F) / 0.28F, 0.0F, 1.0F);
        float stepped = walk - p.lastWalk;
        p.lastWalk = walk;
        if (stepped < 0.0F || stepped > 3.0F || p.fresh) {
            stepped = 0.0F;
        }
        p.cycle += stepped * Mth.lerp(run, 0.16F, 0.12F);
        // its body turning: stepping round on the spot, the tail swinging out
        float bodyYaw = Mth.rotLerp(partialTick, cat.yBodyRotO, cat.yBodyRot);
        float turned = p.fresh ? 0.0F : Mth.wrapDegrees(bodyYaw - p.lastYaw);
        p.lastYaw = bodyYaw;
        p.yawRate += (turned / Math.max(dt, 0.001F) - p.yawRate) * Math.min(1.0F, dt * 8.0F);
        float turning = speed < 0.15F && !air ? Mth.clamp((Math.abs(p.yawRate) - 25.0F) / 120.0F, 0.0F, 1.0F) : 0.0F;
        p.cycle += Math.abs(turned) * 0.006F * turning;
        float cycle = testCycle >= 0 ? testCycle : p.cycle;

        // the pose, sprung towards (quicker in the air and landing)
        float[] target = target(cat, p, age, partialTick, speed, air, vy, test);
        float quick = air || age - p.landStart < 10.0F ? 1.7F : 1.0F;
        if (p.fresh || test != null) {
            System.arraycopy(target, 0, p.at, 0, N);
            java.util.Arrays.fill(p.vel, 0.0F);
        } else {
            int steps = Math.max(1, (int) Math.ceil(dt / 0.008F));
            float h = dt / steps;
            for (int s = 0; s < steps; s++) {
                for (int i = 0; i < N; i++) {
                    float w = OMEGA[i] * quick;
                    p.vel[i] += (w * w * (target[i] - p.at[i]) - 2.0F * DAMPING[i] * w * p.vel[i]) * h;
                    p.at[i] += p.vel[i] * h;
                }
            }
        }
        // the tail's follow-through: swinging out against a turn, each segment after the one before; up as it falls
        float[] pull = {Mth.clamp(-p.yawRate * 0.12F, -38.0F, 38.0F), 0, 0};
        pull[1] = p.swing[0];
        pull[2] = p.swing[1];
        float fallTarget = air ? Mth.clamp(-vy * 90.0F, -25.0F, 35.0F) : 0.0F;
        if (test != null || p.fresh) {
            java.util.Arrays.fill(p.swing, 0.0F);
            p.tailFall = fallTarget;
        } else {
            for (int i = 0; i < 3; i++) {
                float w = 6.5F - i;
                p.swingVel[i] += (w * w * (pull[i] - p.swing[i]) - 2.0F * 0.45F * w * p.swingVel[i]) * dt;
                p.swing[i] += p.swingVel[i] * dt;
            }
            p.tailFallVel += (49.0F * (fallTarget - p.tailFall) - 2.0F * 0.5F * 7.0F * p.tailFallVel) * dt;
            p.tailFall += p.tailFallVel * dt;
        }
        p.fresh = false;
        float[] a = p.at;
        float gait = Mth.clamp(a[GAIT], 0.0F, 1.0F) * Math.min(1.0F, speed * 1.6F);
        float walkW = gait * (1.0F - run), runW = gait * run;
        float seed = (cat.getId() * 0.618F) % 1.0F;
        float tau = Mth.TWO_PI;

        // breathing (deep and slow asleep), a squash on landing
        float breath = Mth.sin(age * (a[BREATH] > 1.0F ? 0.07F : 0.13F)) * a[BREATH];
        float land = age - p.landStart < 14.0F ? Mth.sin((age - p.landStart) / 14.0F * Mth.PI) * p.landHard : 0.0F;
        // the body with the stride: walking, a dip as each paw lands and a gentle roll from side to side; galloping, a
        // rocking bound (front up as the back legs drive, then down), the back flexing and stretching, a float between
        float bob = walkW * (-0.22F * Mth.cos(cycle * tau * 2.0F)) + runW * (1.3F * Math.max(0.0F, Mth.sin(cycle * tau + 0.4F)));
        float roll = walkW * 2.4F * Mth.sin(cycle * tau) + a[ROLL];
        float rock = runW * 7.0F * Mth.sin(cycle * tau);
        float flex = runW * 13.0F * Mth.sin(cycle * tau + 1.3F);
        float hipSway = walkW * 5.0F * Mth.sin(cycle * tau + 0.5F) + turning * 6.0F * Mth.sin(cycle * tau);
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.scale(scale, scale, scale);
        pose.translate(0, OiiaCatModel.FEET + a[LIFT] + bob + breath * 0.15F - land * 1.6F, 0);
        // the whole cat tipped back about its front paws (sitting), then about its back paws (reared up, leaning in)
        float pitch = a[PITCH] + rock + land * 3.0F;
        if (mood == OiiaCatEntity.SCRATCHING) {
            pitch += Mth.sin(age * 0.5F) * 3.0F;
        } else if (mood == OiiaCatEntity.EATING) {
            pitch += Mth.sin(age * 0.6F) * 1.2F;
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
        // it gallops, swaying with its walk, a wiggle as it gets ready to pounce, breathing
        Matrix4f body = new Matrix4f();
        bones.set(OiiaCatRig.BODY, body);
        float wiggle = mood == OiiaCatEntity.PLAYING && a[LIFT] < -2.0F && !air ? Mth.sin(age * 1.15F) * 8.0F * blipHold(age * 0.02F + seed) : 0.0F;
        float hipsPitch = a[HIPS] + flex + breath * 0.8F + land * 4.0F;
        Matrix4f hips = OiiaCatRig.Posed.turn(OiiaCatRig.HIPS_AT, wiggle + hipSway, hipsPitch);
        bones.set(OiiaCatRig.HIPS, hips);
        // the legs: each through its stride - walking one at a time (back left, front left, back right, front right),
        // galloping back pair then front pair - planted and sweeping back, then lifted, folded and swung through; bent
        // as it lands
        float[] walkAt = {0.25F, 0.75F, 0.0F, 0.5F}, runAt = {0.58F, 0.46F, 0.0F, 0.11F};
        float reachWalk = 13.0F + 16.0F * Math.min(1.0F, speed * 1.8F) + 10.0F * turning, reachRun = 40.0F;
        float stepW = Math.max(walkW, turning * 0.6F);
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float[] w = stride(cycle + walkAt[i], 0.62F, reachWalk, front ? 48.0F : 58.0F);
            float[] r = stride(cycle + runAt[i], 0.38F, reachRun, front ? 70.0F : 66.0F);
            float legAngle = a[FL + i] + w[0] * stepW + r[0] * runW + (front ? -1.0F : 1.0F) * land * 10.0F;
            float pawAngle = a[PAW_FL + i] + w[1] * stepW + r[1] * runW + land * (front ? 22.0F : 28.0F);
            if (mood == OiiaCatEntity.SCRATCHING && front) {
                // (front paws scratching in turn: up, and a pull down the post, claws curling)
                float s = age * 0.9F + (i == 0 ? 0.0F : Mth.PI);
                legAngle += Mth.sin(s) * 22.0F + Math.max(0.0F, Mth.sin(s)) * 8.0F;
                pawAngle += Math.max(0.0F, -Mth.sin(s)) * 35.0F;
            }
            Matrix4f parent = front ? body : hips;
            Matrix4f leg = new Matrix4f(parent).mul(OiiaCatRig.Posed.turn(OiiaCatRig.LEGS_AT[i], 0.0F, legAngle));
            bones.set(OiiaCatRig.LEGS[i], leg);
            bones.set(OiiaCatRig.PAWS[i], new Matrix4f(leg).mul(OiiaCatRig.Posed.turn(OiiaCatRig.PAWS_AT[i], 0.0F, pawAngle)));
        }
        // the tail: its pose, a slow wave running down it, swaying with its walk, following through as it turns, up as it
        // falls; a flick of the tip now and then; a quick twitch stalking
        float wave = a[TAIL_YAW] + Mth.sin(age * 0.05F + seed * 5.0F) * 9.0F + walkW * 10.0F * Mth.sin(cycle * tau - 0.8F);
        float lift = a[TAIL_LIFT] + Mth.sin(age * 0.04F) * 3.0F + p.tailFall;
        float curlSide = Math.signum(a[TAIL_YAW] + 0.001F);
        float flick = blip(age * 0.011F + seed * 2.0F, 0.06F) * 22.0F * (seed > 0.3F ? 1 : -1);
        float twitch = mood == OiiaCatEntity.PLAYING && a[LIFT] < -2.0F ? Mth.sin(age * 2.3F) * 12.0F : 0.0F;
        Matrix4f tail = new Matrix4f(hips).mul(OiiaCatRig.Posed.turn(OiiaCatRig.TAIL_AT, wave + p.swing[0], lift));
        bones.set(OiiaCatRig.TAIL, tail);
        Matrix4f mid = new Matrix4f(tail).mul(OiiaCatRig.Posed.turn(OiiaCatRig.TAIL_MID_AT,
                a[TAIL_CURL] * curlSide + Mth.sin(age * 0.05F + seed * 5.0F - 0.9F) * 10.0F + p.swing[1] * 0.8F, lift * 0.25F - 4.0F));
        bones.set(OiiaCatRig.TAIL_MID, mid);
        bones.set(OiiaCatRig.TAIL_TIP, new Matrix4f(mid).mul(OiiaCatRig.Posed.turn(OiiaCatRig.TAIL_TIP_AT,
                a[TAIL_CURL] * curlSide + Mth.sin(age * 0.05F + seed * 5.0F - 1.8F) * 13.0F + p.swing[2] * 0.7F + flick + twitch,
                lift * 0.2F + a[TAIL_CURL] * 0.35F)));
        // the head: turned to what it looks at; held steady against the body's bobbing and rocking (as a cat's is);
        // nodding a little with its steps; chewing at the bowl
        float steady = -(rock + land * 3.0F) * 0.8F - (walkW * -0.22F * Mth.cos(cycle * tau * 2.0F)) * 2.0F;
        float nod = walkW * 1.6F * Mth.sin(cycle * tau * 2.0F + 0.6F);
        if (mood == OiiaCatEntity.EATING) {
            nod += Mth.sin(age * 0.9F) * 6.0F;
        }
        Matrix4f head = new Matrix4f(body).mul(OiiaCatRig.Posed.turn(OiiaCatRig.HEAD_AT, a[HEAD_YAW] - hipSway * 0.3F, a[HEAD_PITCH] + nod + steady,
                a[HEAD_ROLL] - roll * 0.6F));
        bones.set(OiiaCatRig.HEAD, head);
        // the ears: up (or as the pose has them); each swivelling round now and then to a sound, on its own; a quick flick
        float flickL = blip(age * 0.0045F + seed, 0.025F), flickR = blip(age * 0.0039F + seed + 0.37F, 0.025F);
        float turnL = swivel(age * 0.004F + seed * 3.0F), turnR = swivel(age * 0.0033F + seed * 5.0F + 0.5F);
        float ears = a[EARS] * 0.35F, splay = a[EAR_SPLAY];
        bones.set(OiiaCatRig.EAR_L, new Matrix4f(head).mul(OiiaCatRig.Posed.turn(OiiaCatRig.EAR_L_AT, -flickL * 7.0F + turnL * 16.0F + splay,
                ears + flickL * 3.0F, -splay * 0.6F)));
        bones.set(OiiaCatRig.EAR_R, new Matrix4f(head).mul(OiiaCatRig.Posed.turn(OiiaCatRig.EAR_R_AT, flickR * 7.0F - turnR * 16.0F - splay,
                ears + flickR * 3.0F, splay * 0.6F)));
        int overlay = net.minecraft.client.renderer.texture.OverlayTexture.pack(0, net.minecraft.client.renderer.texture.OverlayTexture.v(cat.hurtTime > 0 || cat.deathTime > 0));
        mesh.render(pose.last(), buffers, bones.matrices(), light, overlay);
        return true;
    }

    /** An ear turned round to a sound: mostly not, now and then for a few seconds (0 .. 1, eased). */
    private static float swivel(float t) {
        float f = t - (float) Math.floor(t);
        if (f > 0.2F) {
            return 0.0F;
        }
        float u = f / 0.2F;
        return Mth.clamp(Math.min(u, 1.0F - u) * 8.0F, 0.0F, 1.0F);
    }

    /** Held on for a while, now and then (0 .. 1, eased in and out): how long a pounce's wiggle lasts. */
    private static float blipHold(float t) {
        float f = t - (float) Math.floor(t);
        return f < 0.5F ? Mth.clamp(Math.min(f, 0.5F - f) * 12.0F, 0.0F, 1.0F) : 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(OiiaCatEntity cat) {
        return ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "textures/item/oiia_cat.png");
    }
}
