package com.example.skyblocks.client;

import java.util.Map;
import org.joml.Matrix4f;

/**
 * The OIIA cat's skeleton (models/complex/oiia_cat_rigged.glb, made by tools/models/oiia_cat/rig.py; skinned by
 * OiiaCatMesh): its bones and where their joints are, and a cat's bones posed from code - each turned about its joint.
 */
final class OiiaCatRig {
    /** The bones and their joints (model units): the same as rig.py's. */
    static final String BODY = "body", HIPS = "hips", HEAD = "head", EAR_L = "ear_l", EAR_R = "ear_r", TAIL = "tail", TAIL_MID = "tail_mid",
            TAIL_TIP = "tail_tip";
    static final float[] HIPS_AT = {0.0F, 15.0F, -2.0F}, HEAD_AT = {1.6F, 22.45F, 13.0F}, EAR_L_AT = {4.8F, 33.0F, 19.5F},
            EAR_R_AT = {-3.4F, 33.0F, 19.5F}, TAIL_AT = {0.0F, 22.12F, -22.5F}, TAIL_MID_AT = {0.0F, 20.6F, -29.5F}, TAIL_TIP_AT = {0.0F, 18.0F, -36.5F};
    /** Front left, front right, back left, back right: the legs (at the hip) and their paws (at the knee). */
    static final String[] LEGS = {"leg_fl", "leg_fr", "leg_bl", "leg_br"}, PAWS = {"paw_fl", "paw_fr", "paw_bl", "paw_br"};
    static final float[][] LEGS_AT = {{8.10F, 6.20F, 16.90F}, {-5.60F, 6.20F, 17.40F}, {6.00F, 7.60F, -13.60F}, {-7.20F, 7.60F, -15.30F}};
    static final float[][] PAWS_AT = {{8.10F, 2.6F, 16.90F}, {-5.60F, 2.6F, 17.40F}, {6.00F, 3.0F, -13.60F}, {-7.20F, 3.0F, -15.30F}};

    private OiiaCatRig() {}

    /** The bones' matrices for one cat: each, model space at rest -> posed (by the mesh's bone order). */
    static final class Posed {
        private final Map<String, Integer> bones;
        private final Matrix4f[] out;

        Posed(Map<String, Integer> bones) {
            this.bones = bones;
            out = new Matrix4f[bones.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = new Matrix4f();
            }
        }

        Matrix4f[] matrices() {
            return out;
        }

        /** This bone's matrix. */
        void set(String bone, Matrix4f m) {
            Integer i = bones.get(bone);
            if (i != null && i >= 0 && i < out.length) {
                out[i].set(m);
            }
        }

        /** A turn about a joint: yaw (about y), then pitch (about x), degrees. */
        static Matrix4f turn(float[] at, float yaw, float pitch) {
            return new Matrix4f().translation(at[0], at[1], at[2]).rotateY(yaw * net.minecraft.util.Mth.DEG_TO_RAD).rotateX(pitch * net.minecraft.util.Mth.DEG_TO_RAD)
                    .translate(-at[0], -at[1], -at[2]);
        }
    }
}
