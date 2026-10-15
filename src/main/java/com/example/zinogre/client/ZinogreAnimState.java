package com.example.zinogre.client;

import com.example.zinogre.ZinogreEntity;
import com.noodlegamer76.shadered.client.assimp.AssimpMaterial;
import com.noodlegamer76.shadered.client.assimp.AssimpModel;
import com.noodlegamer76.shadered.client.assimp.McModel;
import com.noodlegamer76.shadered.client.assimp.anim.Animator;
import com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * One Zinogre's model copy and pose.
 *
 * The bones are posed here from our own clips (see {@link ZinogreData}): idle / walk / run blend by how fast the mob
 * moves (walk and run are driven by the distance walked, so the feet keep pace with the ground), and the attack fades
 * in over everything while it plays. The finished bone matrices go into an Animator without a skeleton of its own, so
 * Shadered's renderer just uploads them.
 *
 * The model parts share the uploaded mesh but each Zinogre has its own materials (Shadered's, i.e. its textures), so
 * it can be tinted by the light where it stands (Shadered's model shader has no world lighting) and flash red when hurt.
 */
final class ZinogreAnimState {
    /** Ground speed of the walk and run clips at the model's scale, in blocks per second. */
    private static final float WALK_CLIP_SPEED = 0.56F;
    private static final float RUN_CLIP_SPEED = 2.8F;
    /** Seconds to fade the attack in and out. */
    private static final float ATTACK_FADE = 0.15F;

    final RenderableModel model;
    private final McModel source;
    private final ZinogreData data;
    private final Matrix4f[] bones;
    private final List<AssimpMaterial> materials = new ArrayList<>();
    private final ZinogreData.Clip idle, walk, run, attack;
    private float attackWeight;
    private float lastTime = -1;

    // scratch space for posing
    private final float[] tx, ty, tz, qx, qy, qz, qw, wsum;
    private final Matrix4f[] global;
    private final Quaternionf q = new Quaternionf();
    private final Matrix4f local = new Matrix4f();

    private ZinogreAnimState(McModel source, ZinogreData data, RenderableModel model) {
        this.source = source;
        this.data = data;
        this.model = model;
        this.bones = model.animator.getFinalMatrices();
        this.idle = data.clips.get("idle");
        this.walk = data.clips.get("walk");
        this.run = data.clips.get("run");
        this.attack = data.clips.get("attack");
        int n = data.joints();
        tx = new float[n]; ty = new float[n]; tz = new float[n];
        qx = new float[n]; qy = new float[n]; qz = new float[n]; qw = new float[n]; wsum = new float[n];
        global = new Matrix4f[n];
        for (int i = 0; i < n; i++) {
            global[i] = new Matrix4f();
        }
        for (AssimpModel part : model.getModel()) {
            materials.add(part.getMaterial());
        }
    }

    /** Whether this copy was made with the textures Shadered has loaded now (resource reloads replace them). */
    boolean isFor(McModel loaded) {
        return loaded == source;
    }

    @Nullable
    static ZinogreAnimState create(McModel source) {
        ZinogreData data = ZinogreData.get();
        List<AssimpModel> loadedParts = source.getModels();
        if (data == null || data.parts.size() != loadedParts.size()) {
            return null;
        }
        for (String name : new String[] {"idle", "walk", "run", "attack"}) {
            if (!data.clips.containsKey(name)) {
                return null;
            }
        }
        RenderableModel model = new RenderableModel();
        for (int i = 0; i < loadedParts.size(); i++) {
            AssimpModel copy = new AssimpModel(data.parts.get(i), copyOf(loadedParts.get(i).getMaterial()));
            copy.instances.add(new AssimpModel.MeshInstanceTransform(new Matrix4f(), new Matrix4f()));
            model.add(copy);
        }
        // no skeleton and no clips: Shadered's update does nothing and just uploads the matrices we put in
        model.animator = new Animator(null, Map.of(), new Matrix4f[0], data.joints());
        return new ZinogreAnimState(source, data, model);
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

    /** Poses the bones and sets the light tint for this frame. */
    void update(ZinogreEntity entity, float partialTick, int packedLight) {
        float now = (entity.tickCount + partialTick) / 20.0F;
        boolean first = lastTime < 0;
        float dt = first ? 0.0F : Mth.clamp(now - lastTime, 0.0F, 0.25F);
        lastTime = now;

        // Minecraft's walk animation: speed ~ blocks per second / 5 (up to 1), position ~ 4 x distance walked
        float speed = entity.deathTime > 0 ? 0.0F : entity.walkAnimation.speed(partialTick) * 5.0F;
        float distance = entity.walkAnimation.position(partialTick) / 4.0F;
        float moving = smooth(0.12F, 0.5F, speed);
        float running = smooth(1.3F, 2.3F, speed);

        float sinceAttack = (entity.tickCount - entity.attackStartTick + partialTick) / 20.0F;
        boolean attacking = sinceAttack >= 0 && sinceAttack < attack.seconds() - ATTACK_FADE && entity.deathTime <= 0;
        attackWeight = first ? (attacking ? 1.0F : 0.0F)
                : Mth.clamp(attackWeight + (attacking ? dt : -dt) / ATTACK_FADE, 0.0F, 1.0F);
        float rest = 1.0F - attackWeight;

        clear();
        add(idle, now + entity.getId() * 1.37F, true, rest * (1.0F - moving));
        add(walk, distance / WALK_CLIP_SPEED, true, rest * moving * (1.0F - running));
        add(run, distance / RUN_CLIP_SPEED, true, rest * moving * running);
        add(attack, Mth.clamp(sinceAttack, 0.0F, attack.seconds()), false, attackWeight);
        if (wsum[0] <= 0) {
            add(idle, now, true, 1.0F);
        }
        finish();

        tint(entity, partialTick, packedLight);
    }

    private void clear() {
        Arrays.fill(tx, 0); Arrays.fill(ty, 0); Arrays.fill(tz, 0);
        Arrays.fill(qx, 0); Arrays.fill(qy, 0); Arrays.fill(qz, 0); Arrays.fill(qw, 0);
        Arrays.fill(wsum, 0);
    }

    /** Adds a clip's pose at the given time with the given weight (quaternions summed in the same hemisphere). */
    private void add(ZinogreData.Clip clip, float seconds, boolean loop, float weight) {
        if (weight <= 1.0e-4F) {
            return;
        }
        float length = clip.seconds();
        float t = loop ? ((seconds % length) + length) % length : Mth.clamp(seconds, 0.0F, length);
        float f = t * clip.fps();
        int i0 = Math.min((int) f, clip.frames() - 1);
        int i1 = Math.min(i0 + 1, clip.frames() - 1);
        float a = f - i0;
        int n = data.joints();
        float[] d = clip.data();
        for (int j = 0; j < n; j++) {
            int p = (i0 * n + j) * 7;
            int r = (i1 * n + j) * 7;
            float x0 = d[p + 3], y0 = d[p + 4], z0 = d[p + 5], w0 = d[p + 6];
            float x1 = d[r + 3], y1 = d[r + 4], z1 = d[r + 5], w1 = d[r + 6];
            if (x0 * x1 + y0 * y1 + z0 * z1 + w0 * w1 < 0) {
                x1 = -x1; y1 = -y1; z1 = -z1; w1 = -w1;
            }
            float x = x0 + (x1 - x0) * a, y = y0 + (y1 - y0) * a, z = z0 + (z1 - z0) * a, w = w0 + (w1 - w0) * a;
            float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
            x /= len; y /= len; z /= len; w /= len;
            if (wsum[j] > 0 && qx[j] * x + qy[j] * y + qz[j] * z + qw[j] * w < 0) {
                x = -x; y = -y; z = -z; w = -w;
            }
            qx[j] += x * weight; qy[j] += y * weight; qz[j] += z * weight; qw[j] += w * weight;
            tx[j] += (d[p] + (d[r] - d[p]) * a) * weight;
            ty[j] += (d[p + 1] + (d[r + 1] - d[p + 1]) * a) * weight;
            tz[j] += (d[p + 2] + (d[r + 2] - d[p + 2]) * a) * weight;
            wsum[j] += weight;
        }
    }

    /** Local poses -> model space -> final skinning matrices (model space x inverse bind). */
    private void finish() {
        int n = data.joints();
        for (int j = 0; j < n; j++) {
            float s = wsum[j];
            q.set(qx[j], qy[j], qz[j], qw[j]).normalize();
            local.translationRotate(tx[j] / s, ty[j] / s, tz[j] / s, q);
            int parent = data.parents[j];
            if (parent < 0) {
                global[j].set(local);
            } else {
                global[parent].mul(local, global[j]);
            }
            if (j < bones.length) {
                global[j].mul(data.inverseBind[j], bones[j]);
            }
        }
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
