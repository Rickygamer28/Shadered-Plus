package com.example.zinogre.client;

import com.example.zinogre.ZinogreEntity;
import com.example.zinogre.ZinogreMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.assimp.McModel;
import com.noodlegamer76.shadered.client.assimp.load.AssimpModels;
import com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a Zinogre with Shadered's model renderer (like Shadered's Maxwell): the model from
 * assets/zinogre/models/complex/zinogre.glb, posed by {@link ZinogreAnimState}, handed over with its world matrix.
 */
public class ZinogreRenderer extends EntityRenderer<ZinogreEntity> {
    /** The textures (one GLB per look; the mesh, skeleton and clips are shared, see ZinogreData). */
    static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/complex/zinogre.glb");
    static final ResourceLocation STYGIAN_MODEL = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/complex/stygian_zinogre.glb");
    /** The glowing body textures for while it is charged. */
    static final ResourceLocation CHARGED_MODEL = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/complex/zinogre_charged.glb");
    static final ResourceLocation STYGIAN_CHARGED_MODEL = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/complex/stygian_zinogre_charged.glb");
    /** Model units -> blocks: about 2.5 blocks to the top of the head, about 7 blocks from nose to tail tip. */
    static final float SCALE = 0.018F;
    private static final ResourceLocation NO_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public ZinogreRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.8F;
        this.shadowStrength = 0.8F;
    }

    @Override
    public void render(ZinogreEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        McModel loaded = AssimpModels.getModel(entity.isStygian() ? STYGIAN_MODEL : MODEL);
        if (loaded == null) {
            return; // Shadered hasn't loaded the model (yet), or the file is missing from the jar
        }
        ZinogreAnimState state = entity.clientState instanceof ZinogreAnimState s && s.isFor(loaded) ? s : null;
        if (state == null) {
            state = ZinogreAnimState.create(loaded);
            entity.clientState = state;
            if (state == null) {
                return; // a model without the expected clips
            }
        }
        McModel charged = AssimpModels.getModel(entity.isStygian() ? STYGIAN_CHARGED_MODEL : CHARGED_MODEL);
        ResourceLocation chargedBody = charged != null && !charged.getModels().isEmpty()
                ? charged.getModels().get(0).getMaterial().getDiffuseTexture() : null;
        state.update(entity, partialTick, packedLight, chargedBody);

        // Start from the pose we were given (camera-relative, at the entity), on our own copy
        PoseStack pose = new PoseStack();
        pose.last().pose().set(poseStack.last().pose());
        pose.last().normal().set(poseStack.last().normal());
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw)); // the model faces +Z (south), like yaw 0
        if (entity.deathTime > 0) {
            // falls over onto its side, like vanilla mobs
            float f = Mth.sqrt(((float) entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F);
            pose.mulPose(Axis.ZP.rotationDegrees(Math.min(f, 1.0F) * 90.0F));
        }
        pose.scale(SCALE, SCALE, SCALE);

        // Shadered's model renderer works in world coordinates
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        state.model.modelMatrix = new Matrix4f()
                .translation((float) camera.x, (float) camera.y, (float) camera.z)
                .mul(pose.last().pose());
        AssimpRenderer.getInstance().addModel(state.model);

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight); // name tag
    }

    @Override
    public ResourceLocation getTextureLocation(ZinogreEntity entity) {
        return NO_TEXTURE;
    }
}
