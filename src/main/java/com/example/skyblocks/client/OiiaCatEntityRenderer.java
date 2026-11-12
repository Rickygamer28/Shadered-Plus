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
 * The OIIA cat companion, drawn with the OIIA model by Shadered's model renderer (as OiiaCatRenderer draws the block).
 * The model is all one piece, so it moves as a whole: a waddle and bob as it walks, a hop as it plays; sitting or
 * napping it's the curled-up loaf (napping, low and breathing); petted, the loaf does the OIIA spin.
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
        boolean loaf = spin || mood == OiiaCatEntity.NAPPING || mood == OiiaCatEntity.BOXED || mood == OiiaCatEntity.PERCHED || cat.isInSittingPose();
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
            OiiaCatCollar.draw(cat, pose, buffers, light, true);
        } else if (loaf) {
            boolean napping = cat.mood() == OiiaCatEntity.NAPPING;
            float breathe = napping ? 1.0F + 0.03F * Mth.sin(age * 0.09F) : 1.0F;
            pose.scale(scale, scale * breathe * (napping ? 0.92F : 1.0F), scale);
            // (the loaf rests on the ground: the spin model floats SPIN_BOTTOM above its feet)
            pose.translate(0, OiiaCatModel.FEET - OiiaCatModel.SPIN_BOTTOM, 0);
            OiiaCatCollar.draw(cat, pose, buffers, light, true);
        } else if (walkParts(cat, pose, scale, age, partialTick)) {
            OiiaCatCollar.draw(cat, pose, buffers, light, false);
            return; // (drawn in pieces, walking)
        } else {
            pose.scale(scale, scale, scale);
            pose.translate(0, OiiaCatModel.FEET, 0);
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        model.modelMatrix = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z).mul(new Matrix4f(pose.last().pose()));
        AssimpRenderer.getInstance().addModel(model);
    }

    /**
     * Standing or walking: the cat in its pieces, the legs stepping in a trot (diagonal pairs together, as fast as it's
     * going), the body bobbing a little with each step, the tail swaying (more as it walks, raised a bit). False while
     * the pieces haven't loaded (then it's the whole model, still).
     */
    private static boolean walkParts(OiiaCatEntity cat, PoseStack pose, float scale, float age, float partialTick) {
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
        float phase = walk * 0.9F;
        byte mood = cat.mood();
        boolean scratch = mood == OiiaCatEntity.SCRATCHING, eat = mood == OiiaCatEntity.EATING;
        if (scratch || eat) {
            speed = 0;
        }
        boolean hop = mood == OiiaCatEntity.PLAYING && !cat.onGround();
        pose.translate(0, Math.abs(Mth.sin(phase)) * 0.025F * speed + (hop ? 0.06F : 0.0F), 0);
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(phase) * 2.5F * speed)); // (a little roll from side to side)
        pose.scale(scale, scale, scale);
        pose.translate(0, OiiaCatModel.FEET, 0);
        if (scratch || eat) {
            // (up on its back legs against the post / head down in the bowl: the whole cat tipped about its back paws)
            float tip = scratch ? -42.0F + Mth.sin(age * 0.5F) * 3.0F : 9.0F + Mth.sin(age * 0.6F) * 3.0F;
            pose.translate(0, 0, BACK_PAWS);
            pose.mulPose(Axis.XP.rotationDegrees(tip));
            pose.translate(0, 0, -BACK_PAWS);
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Matrix4f world = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z).mul(new Matrix4f(pose.last().pose()));
        float swing = 30.0F * speed;
        for (int i = 0; i < n; i++) {
            float[] pv = OiiaCatModel.PIVOTS[i];
            Matrix4f m = new Matrix4f(world);
            if (i >= 1 && i <= 4) {
                // (front left with back right, front right with back left: a trot)
                float legPhase = i == 1 || i == 4 ? 0.0F : Mth.PI;
                float a = hop ? (i <= 2 ? -35.0F : 30.0F) : Mth.sin(phase + legPhase) * swing;
                if (scratch) {
                    // (front paws reaching up the post, scratching in turn; back legs braced)
                    a = i <= 2 ? -55.0F + Mth.sin(age * 0.9F + (i == 1 ? 0.0F : Mth.PI)) * 22.0F : 35.0F;
                }
                m.translate(pv[0], pv[1], pv[2]).rotateX(a * Mth.DEG_TO_RAD).translate(-pv[0], -pv[1], -pv[2]);
            } else if (i == 5) {
                float sway = Mth.sin(age * 0.07F) * 9.0F + Mth.sin(phase * 0.5F) * 14.0F * speed;
                float lift = -8.0F * speed + Mth.sin(age * 0.05F) * 3.0F;
                m.translate(pv[0], pv[1], pv[2]).rotateY(sway * Mth.DEG_TO_RAD).rotateX(lift * Mth.DEG_TO_RAD).translate(-pv[0], -pv[1], -pv[2]);
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
