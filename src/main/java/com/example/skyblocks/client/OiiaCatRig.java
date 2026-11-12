package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.noodlegamer76.shadered.client.assimp.AssimpModel;
import com.noodlegamer76.shadered.client.assimp.McModel;
import com.noodlegamer76.shadered.client.assimp.anim.Animator;
import com.noodlegamer76.shadered.client.assimp.load.AssimpModels;
import com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The OIIA cat with a skeleton (models/complex/oiia_cat_rigged.glb, made by tools/models/oiia_cat/rig.py): one mesh,
 * its fur tied to the bones with weights that blend across each joint, so it bends there rather than coming apart.
 * Posed from code (not from an animation): each bone turned about its joint, Shadered's renderer skinning it on the
 * graphics card.
 */
final class OiiaCatRig {
    static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "models/complex/oiia_cat_rigged.glb");

    /** The bones and their joints (model units): the same as rig.py's. */
    static final String BODY = "body", HEAD = "head", LEG_FL = "leg_fl", LEG_FR = "leg_fr", LEG_BL = "leg_bl", LEG_BR = "leg_br", TAIL = "tail",
            TAIL_TIP = "tail_tip";
    static final float[] HEAD_AT = {1.6F, 22.45F, 13.0F}, TAIL_AT = {0.0F, 22.12F, -22.5F}, TAIL_TIP_AT = {0.0F, 19.7F, -33.5F};
    static final float[][] LEGS_AT = {{8.10F, 6.20F, 16.90F}, {-5.60F, 6.20F, 17.40F}, {6.00F, 7.60F, -13.60F}, {-7.20F, 7.60F, -15.30F}};
    static final String[] LEGS = {LEG_FL, LEG_FR, LEG_BL, LEG_BR};

    private OiiaCatRig() {}

    /** A fresh posable cat, or null while Shadered hasn't loaded the model (or it came without its skeleton). */
    @Nullable
    static RenderableModel create() {
        McModel model = AssimpModels.getModel(MODEL);
        if (model == null || model.getRig() == null || model.getRig().boneMap == null || model.getRig().boneMap.isEmpty()) {
            return null;
        }
        RenderableModel r = new RenderableModel();
        for (AssimpModel part : model.getModels()) {
            r.add(part);
        }
        r.animator = new Posed(model);
        return r;
    }

    /** Bones posed from code: each one's matrix (from its rest place to where it's turned) set straight. */
    static final class Posed extends Animator {
        private final Map<String, Integer> bones;
        private final Matrix4f[] out;

        Posed(McModel model) {
            super(model);
            bones = model.getRig().boneMap;
            out = new Matrix4f[bones.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = new Matrix4f();
            }
        }

        @Override
        public void update(float deltaSeconds) {
            // (posed by set(), not played)
        }

        @Override
        public Matrix4f[] getFinalMatrices() {
            return out;
        }

        /** This bone's matrix: model space at rest to model space posed. */
        void set(String bone, Matrix4f m) {
            Integer i = bones.get(bone);
            if (i != null && i >= 0 && i < out.length) {
                out[i].set(m);
            }
        }

        /** A turn about a joint: yaw (about y), then pitch (about x), degrees. */
        static Matrix4f turn(float[] at, float yaw, float pitch) {
            return new Matrix4f().translation(at[0], at[1], at[2]).rotateY(yaw * org.joml.Math.PI / 180.0F).rotateX(pitch * org.joml.Math.PI / 180.0F)
                    .translate(-at[0], -at[1], -at[2]);
        }
    }
}
