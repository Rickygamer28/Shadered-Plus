package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The rigged OIIA cat (models/complex/oiia_cat_rigged.glb, tools/models/oiia_cat/rig.py) skinned here, on the CPU,
 * and drawn as Minecraft draws entities: each vertex moved by its bones' matrices (blended by its weights), with the
 * model's own texture, lit by the world's light. (Shadered's own skinning draws this model torn apart, so the cat is
 * skinned here instead.)
 */
final class OiiaCatMesh {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "models/complex/oiia_cat_rigged.glb");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "oiia_cat_rigged");

    private final float[] pos, nor, uv, weights;
    private final int[] joints, indices;
    /** Bone name -> its index (the skin's joint order). */
    final Map<String, Integer> bones = new HashMap<>();
    private final float[] skinned, skinnedNor;

    private OiiaCatMesh(float[] pos, float[] nor, float[] uv, int[] joints, float[] weights, int[] indices) {
        this.pos = pos;
        this.nor = nor;
        this.uv = uv;
        this.joints = joints;
        this.weights = weights;
        this.indices = indices;
        this.skinned = new float[pos.length];
        this.skinnedNor = new float[nor.length];
    }

    private static OiiaCatMesh loaded;
    private static boolean failed;

    /** The mesh (loaded the first time), or null if it couldn't be. */
    @Nullable
    static OiiaCatMesh get() {
        if (loaded == null && !failed) {
            try {
                loaded = load();
            } catch (Exception e) {
                failed = true;
                SkyBlocksMod.LOGGER.warn("OIIA cat rig: couldn't load {}: {}", MODEL, e.toString());
            }
        }
        return loaded;
    }

    private static OiiaCatMesh load() throws Exception {
        byte[] glb;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(MODEL)) {
            glb = in.readAllBytes();
        }
        ByteBuffer b = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        int jsonLen = b.getInt(12);
        JsonObject gltf = JsonParser.parseString(new String(glb, 20, jsonLen, StandardCharsets.UTF_8)).getAsJsonObject();
        int binStart = 20 + jsonLen + 8;
        JsonObject prim = gltf.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject();
        JsonObject attrs = prim.getAsJsonObject("attributes");
        float[] pos = floats(gltf, b, binStart, attrs.get("POSITION").getAsInt());
        float[] nor = floats(gltf, b, binStart, attrs.get("NORMAL").getAsInt());
        float[] uv = floats(gltf, b, binStart, attrs.get("TEXCOORD_0").getAsInt());
        int[] joints = ints(gltf, b, binStart, attrs.get("JOINTS_0").getAsInt());
        float[] weights = floats(gltf, b, binStart, attrs.get("WEIGHTS_0").getAsInt());
        int[] indices = ints(gltf, b, binStart, prim.get("indices").getAsInt());
        OiiaCatMesh mesh = new OiiaCatMesh(pos, nor, uv, joints, weights, indices);
        JsonArray nodes = gltf.getAsJsonArray("nodes");
        JsonArray skinJoints = gltf.getAsJsonArray("skins").get(0).getAsJsonObject().getAsJsonArray("joints");
        for (int i = 0; i < skinJoints.size(); i++) {
            mesh.bones.put(nodes.get(skinJoints.get(i).getAsInt()).getAsJsonObject().get("name").getAsString(), i);
        }
        // its texture: the picture inside the file
        JsonObject image = gltf.getAsJsonArray("images").get(0).getAsJsonObject();
        JsonObject view = gltf.getAsJsonArray("bufferViews").get(image.get("bufferView").getAsInt()).getAsJsonObject();
        int off = binStart + (view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0), len = view.get("byteLength").getAsInt();
        NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(glb, off, len));
        Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(img));
        return mesh;
    }

    private static int count(JsonObject acc) {
        return acc.get("count").getAsInt() * switch (acc.get("type").getAsString()) {
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT4" -> 16;
            default -> 1;
        };
    }

    private static int start(JsonObject gltf, JsonObject acc, int binStart) {
        JsonObject view = gltf.getAsJsonArray("bufferViews").get(acc.get("bufferView").getAsInt()).getAsJsonObject();
        return binStart + (view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0) + (acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0);
    }

    private static float[] floats(JsonObject gltf, ByteBuffer b, int binStart, int accessor) {
        JsonObject acc = gltf.getAsJsonArray("accessors").get(accessor).getAsJsonObject();
        int n = count(acc), at = start(gltf, acc, binStart);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = b.getFloat(at + i * 4);
        }
        return out;
    }

    private static int[] ints(JsonObject gltf, ByteBuffer b, int binStart, int accessor) {
        JsonObject acc = gltf.getAsJsonArray("accessors").get(accessor).getAsJsonObject();
        int n = count(acc), at = start(gltf, acc, binStart), type = acc.get("componentType").getAsInt();
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = switch (type) {
                case 5121 -> b.get(at + i) & 0xFF;
                case 5123 -> b.getShort(at + i * 2) & 0xFFFF;
                default -> b.getInt(at + i * 4);
            };
        }
        return out;
    }

    /** Skins the mesh with these bone matrices (by bone index: model space at rest -> posed) and draws it with this pose. */
    void render(PoseStack.Pose pose, MultiBufferSource buffers, Matrix4f[] boneMats, int light, int overlay) {
        int n = pos.length / 3;
        Matrix4f m = new Matrix4f();
        Matrix3f mn = new Matrix3f();
        for (int v = 0; v < n; v++) {
            float x = pos[v * 3], y = pos[v * 3 + 1], z = pos[v * 3 + 2];
            float nx = nor[v * 3], ny = nor[v * 3 + 1], nz = nor[v * 3 + 2];
            float ox = 0, oy = 0, oz = 0, onx = 0, ony = 0, onz = 0, total = 0;
            for (int k = 0; k < 4; k++) {
                float w = weights[v * 4 + k];
                if (w <= 0) {
                    continue;
                }
                int j = joints[v * 4 + k];
                Matrix4f bm = j < boneMats.length && boneMats[j] != null ? boneMats[j] : m.identity();
                ox += w * (bm.m00() * x + bm.m10() * y + bm.m20() * z + bm.m30());
                oy += w * (bm.m01() * x + bm.m11() * y + bm.m21() * z + bm.m31());
                oz += w * (bm.m02() * x + bm.m12() * y + bm.m22() * z + bm.m32());
                onx += w * (bm.m00() * nx + bm.m10() * ny + bm.m20() * nz);
                ony += w * (bm.m01() * nx + bm.m11() * ny + bm.m21() * nz);
                onz += w * (bm.m02() * nx + bm.m12() * ny + bm.m22() * nz);
                total += w;
            }
            if (total < 0.01F) {
                ox = x;
                oy = y;
                oz = z;
                onx = nx;
                ony = ny;
                onz = nz;
            } else if (total != 1.0F) {
                ox /= total;
                oy /= total;
                oz /= total;
            }
            float len = (float) Math.sqrt(onx * onx + ony * ony + onz * onz);
            if (len > 1.0E-6F) {
                onx /= len;
                ony /= len;
                onz /= len;
            }
            skinned[v * 3] = ox;
            skinned[v * 3 + 1] = oy;
            skinned[v * 3 + 2] = oz;
            skinnedNor[v * 3] = onx;
            skinnedNor[v * 3 + 1] = ony;
            skinnedNor[v * 3 + 2] = onz;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int t = 0; t + 2 < indices.length; t += 3) {
            // (a triangle, as a quad with its last corner twice)
            vertex(vc, pose, indices[t], light, overlay);
            vertex(vc, pose, indices[t + 1], light, overlay);
            vertex(vc, pose, indices[t + 2], light, overlay);
            vertex(vc, pose, indices[t + 2], light, overlay);
        }
    }

    private void vertex(VertexConsumer vc, PoseStack.Pose pose, int v, int light, int overlay) {
        vc.addVertex(pose, skinned[v * 3], skinned[v * 3 + 1], skinned[v * 3 + 2]).setColor(-1).setUv(uv[v * 2], uv[v * 2 + 1]).setOverlay(overlay)
                .setLight(light).setNormal(pose, skinnedNor[v * 3], skinnedNor[v * 3 + 1], skinnedNor[v * 3 + 2]);
    }
}
