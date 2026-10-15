package com.example.zinogre.client;

import com.example.zinogre.ZinogreEntity;
import com.noodlegamer76.shadered.client.assimp.AssimpMaterial;
import com.noodlegamer76.shadered.client.assimp.AssimpModel;
import com.noodlegamer76.shadered.client.assimp.McModel;
import com.noodlegamer76.shadered.client.assimp.anim.Animation;
import com.noodlegamer76.shadered.client.assimp.anim.Animator;
import com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One Zinogre's copy of the model and its animation player.
 *
 * Shadered's Animator advances every playing clip by (game time in seconds x clip speed) each time it draws, which
 * makes the clip time jump around. So the clips are played as copies whose speed is 0, and their times and weights are
 * set here every frame instead: idle / walk / run blend by how fast the mob moves (walk and run are driven by the
 * distance walked, so the feet keep pace with the ground), and the attack fades in over everything while it plays.
 *
 * The model parts are copies too (same mesh on the graphics card, own material), so each Zinogre can be tinted by
 * the light where it stands (Shadered's model shader has no world lighting) and flash red when hurt.
 */
final class ZinogreAnimState {
    /** Ground speed of the walk and run clips at the model's scale, in blocks per second. */
    private static final float WALK_CLIP_SPEED = 0.56F;
    private static final float RUN_CLIP_SPEED = 2.8F;
    /** Seconds to fade the attack in and out. */
    private static final float ATTACK_FADE = 0.15F;

    final RenderableModel model;
    private final McModel source;
    private final List<AssimpMaterial> materials = new ArrayList<>();
    private final Clip idle, walk, run, attack;
    private float attackWeight;
    private float lastTime = -1;

    private record Clip(Animator.ActiveAnimation active, float ticksPerSecond, float duration) {
        float seconds() {
            return duration / ticksPerSecond;
        }

        void set(float seconds, float weight) {
            float t = seconds * ticksPerSecond;
            active.setTime(Math.max(0.0F, Math.min(t, duration)));
            active.setWeight(weight);
        }

        void loop(float seconds, float weight) {
            float length = seconds();
            set(((seconds % length) + length) % length, weight);
        }
    }

    private ZinogreAnimState(McModel source, RenderableModel model, Clip idle, Clip walk, Clip run, Clip attack) {
        this.source = source;
        this.model = model;
        this.idle = idle;
        this.walk = walk;
        this.run = run;
        this.attack = attack;
        for (AssimpModel part : model.getModel()) {
            materials.add(part.getMaterial());
        }
    }

    /** Whether this copy was made from the model Shadered has loaded now (resource reloads replace it). */
    boolean isFor(McModel loaded) {
        return loaded == source;
    }

    @Nullable
    static ZinogreAnimState create(McModel source) {
        if (source.getRig() == null) {
            return null;
        }
        Animator animator = new Animator(source);
        String[] names = {"idle", "walk", "run", "attack"};
        float[] tps = new float[names.length];
        float[] durations = new float[names.length];
        for (int i = 0; i < names.length; i++) {
            Animation original = source.getAnimations().get(names[i]);
            if (original == null || original.duration <= 0) {
                return null;
            }
            Animation copy = new Animation();
            copy.name = original.name;
            copy.duration = original.duration;
            copy.ticksPerSecond = 0.0F; // never advanced by Shadered: the time is set every frame
            copy.looping = original.looping;
            copy.tracks.putAll(original.tracks);
            tps[i] = original.ticksPerSecond > 0 ? original.ticksPerSecond : 25.0F;
            durations[i] = original.duration;
            animator.play(copy, 0.0F);
        }
        List<Animator.ActiveAnimation> active = animator.getActiveAnimations();

        RenderableModel model = new RenderableModel();
        for (AssimpModel part : source.getModels()) {
            AssimpModel copy = new AssimpModel(part.getVertexBuffer(), copyOf(part.getMaterial()));
            copy.instances.addAll(part.instances);
            model.add(copy);
        }
        model.animator = animator;
        return new ZinogreAnimState(source, model,
                new Clip(active.get(0), tps[0], durations[0]), new Clip(active.get(1), tps[1], durations[1]),
                new Clip(active.get(2), tps[2], durations[2]), new Clip(active.get(3), tps[3], durations[3]));
    }

    private static AssimpMaterial copyOf(AssimpMaterial m) {
        AssimpMaterial copy = new AssimpMaterial();
        copy.setDiffuseTexture(m.getDiffuseTexture());
        copy.setNormalTexture(m.getNormalTexture());
        copy.setSpecularTexture(m.getSpecularTexture());
        copy.setEmissiveTexture(m.getEmissiveTexture());
        copy.setColor(m.red(), m.green(), m.blue(), m.alpha());
        copy.setTransparent(m.useAlpha());
        copy.setBlendMode(m.getBlendMode());
        return copy;
    }

    /** Sets the clip times and weights and the light tint for this frame. */
    void update(ZinogreEntity entity, float partialTick, int packedLight) {
        float now = (entity.tickCount + partialTick) / 20.0F;
        boolean first = lastTime < 0;
        float dt = first ? 0.0F : Mth.clamp(now - lastTime, 0.0F, 0.25F);
        lastTime = now;

        // Minecraft's walk animation: speed ~ blocks per second / 5 (up to 1), position ~ 4 x distance walked
        float speed = entity.walkAnimation.speed(partialTick) * 5.0F;
        float distance = entity.walkAnimation.position(partialTick) / 4.0F;
        if (entity.deathTime > 0) {
            speed = 0.0F;
        }
        float moving = smooth(0.12F, 0.5F, speed);
        float running = smooth(1.3F, 2.3F, speed);

        float sinceAttack = (entity.tickCount - entity.attackStartTick + partialTick) / 20.0F;
        boolean attacking = sinceAttack >= 0 && sinceAttack < attack.seconds() - ATTACK_FADE && entity.deathTime <= 0;
        attackWeight = first ? (attacking ? 1.0F : 0.0F)
                : Mth.clamp(attackWeight + (attacking ? dt : -dt) / ATTACK_FADE, 0.0F, 1.0F);
        float rest = 1.0F - attackWeight;

        idle.loop(now + entity.getId() * 1.37F, rest * (1.0F - moving) + 1.0e-4F);
        walk.loop(distance / WALK_CLIP_SPEED, rest * moving * (1.0F - running));
        run.loop(distance / RUN_CLIP_SPEED, rest * moving * running);
        attack.set(Mth.clamp(sinceAttack, 0.0F, attack.seconds()), attackWeight);

        tint(entity, partialTick, packedLight);
    }

    /** Darker in the dark (block or sky light where it stands), red while hurt. */
    private void tint(ZinogreEntity entity, float partialTick, int packedLight) {
        float block = LightTexture.block(packedLight) / 15.0F;
        float sky = LightTexture.sky(packedLight) / 15.0F;
        Minecraft mc = Minecraft.getInstance();
        float daylight = mc.level != null ? mc.level.getSkyDarken(partialTick) : 1.0F;
        float light = Math.max(block, sky * daylight);
        float v = 0.18F + 0.82F * light;
        boolean hurt = entity.hurtTime > 0 || entity.deathTime > 0;
        float r = v, g = hurt ? v * 0.55F : v, b = hurt ? v * 0.55F : v;
        for (AssimpMaterial material : materials) {
            material.setColor(r, g, b, 1.0F);
        }
    }

    private static float smooth(float from, float to, float x) {
        float t = Mth.clamp((x - from) / (to - from), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
