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
        boolean loaf = spin || cat.mood() == OiiaCatEntity.NAPPING || cat.isInSittingPose();
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
            boolean napping = cat.mood() == OiiaCatEntity.NAPPING;
            float breathe = napping ? 1.0F + 0.03F * Mth.sin(age * 0.09F) : 1.0F;
            pose.scale(scale, scale * breathe * (napping ? 0.92F : 1.0F), scale);
            // (the loaf rests on the ground: the spin model floats SPIN_BOTTOM above its feet)
            pose.translate(0, OiiaCatModel.FEET - OiiaCatModel.SPIN_BOTTOM, 0);
        } else {
            float walk = cat.walkAnimation.position(partialTick), speed = Math.min(1.0F, cat.walkAnimation.speed(partialTick));
            float hop = cat.mood() == OiiaCatEntity.PLAYING && !cat.onGround() ? 0.08F : 0.0F;
            pose.translate(0, Math.abs(Mth.sin(walk * 0.7F)) * 0.06F * speed + hop, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(walk * 0.7F) * 9.0F * speed)); // (a waddle)
            pose.mulPose(Axis.XP.rotationDegrees(4.0F * speed)); // (leaning into it)
            pose.scale(scale, scale, scale);
            pose.translate(0, OiiaCatModel.FEET, 0);
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        model.modelMatrix = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z).mul(new Matrix4f(pose.last().pose()));
        AssimpRenderer.getInstance().addModel(model);
    }

    @Override
    public ResourceLocation getTextureLocation(OiiaCatEntity cat) {
        return ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "textures/item/oiia_cat.png");
    }
}
