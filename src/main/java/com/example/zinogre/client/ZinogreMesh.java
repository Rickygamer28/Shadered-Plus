package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.noodlegamer76.shadered.client.assimp.McModel;
import com.noodlegamer76.shadered.mixin.BufferBuilderMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Zinogre's mesh on the graphics card, in Shadered's model vertex layout, with working bone weights.
 *
 * Shadered's own mesh upload never writes the bone ids and weights: its extra vertex elements (tangent, bone ids,
 * bone weights) all use element id 0 (the position's), so Minecraft's BufferBuilder skips them and skinned models
 * come out wrong. So the mesh is read from our own file (assets/zinogre/models/zinogre_mesh.bin, made by the same
 * converter as the GLB) and uploaded here with a copy of that layout whose elements have ids of their own. The GLB
 * still provides the skeleton, the animations and the textures (through Shadered's loader).
 */
final class ZinogreMesh {
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/zinogre_mesh.bin");

    private static VertexFormatElement tangent, boneIds, boneWeights;
    private static VertexFormat format;

    /** The uploaded parts (body, fur, translucent fur, eyes), for the model Shadered loaded last. */
    private static List<VertexBuffer> parts;
    private static McModel partsFor;
    private static boolean failed;

    private ZinogreMesh() {}

    /** The mesh parts for this loaded model (uploaded on first use, again after a resource reload), or null. */
    @Nullable
    static List<VertexBuffer> parts(McModel model) {
        if (model == partsFor) {
            return parts;
        }
        if (parts != null) {
            parts.forEach(VertexBuffer::close);
        }
        parts = null;
        partsFor = model;
        if (model.getRig() == null || model.getRig().boneMap == null) {
            return null;
        }
        try {
            parts = upload(read(), model.getRig().boneMap);
            failed = false;
        } catch (IOException | RuntimeException e) {
            if (!failed) {
                ZinogreMod.LOGGER.error("Couldn't load the Zinogre mesh {}", FILE, e);
            }
            failed = true;
        }
        return parts;
    }

    private static ByteBuffer read() throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FILE);
        if (resource.isEmpty()) {
            throw new IOException("missing " + FILE);
        }
        try (InputStream in = resource.get().open()) {
            return ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    private static List<VertexBuffer> upload(ByteBuffer b, Map<String, Integer> boneMap) {
        setupFormat();
        if (b.getInt() != 0x31534D5A) { // "ZMS1"
            throw new IllegalStateException("not a Zinogre mesh file");
        }
        int jointCount = b.getInt();
        int[] jointToBone = new int[jointCount];
        for (int j = 0; j < jointCount; j++) {
            byte[] name = new byte[b.getShort() & 0xFFFF];
            b.get(name);
            Integer bone = boneMap.get(new String(name, StandardCharsets.UTF_8));
            jointToBone[j] = bone != null ? bone : 0;
        }
        int n = b.getInt();
        float[] pos = floats(b, n * 3);
        float[] uv = floats(b, n * 2);
        float[] normal = floats(b, n * 3);
        float[] shade = floats(b, n);
        int[] joints = new int[n * 4];
        for (int i = 0; i < joints.length; i++) {
            joints[i] = b.getShort() & 0xFFFF;
        }
        float[] weights = floats(b, n * 4);

        int partCount = b.getInt();
        List<VertexBuffer> result = new ArrayList<>();
        for (int p = 0; p < partCount; p++) {
            int indexCount = b.getInt();
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, format);
            for (int k = 0; k < indexCount; k++) {
                int v = b.getInt();
                float s = shade[v];
                builder.addVertex(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2])
                        .setUv(uv[v * 2], uv[v * 2 + 1])
                        .setColor(s, s, s, 1.0F)
                        .setNormal(normal[v * 3], normal[v * 3 + 1], normal[v * 3 + 2]);
                put(builder, tangent, 1.0F, 0.0F, 0.0F, 1.0F);
                put(builder, boneIds, jointToBone[joints[v * 4]], jointToBone[joints[v * 4 + 1]],
                        jointToBone[joints[v * 4 + 2]], jointToBone[joints[v * 4 + 3]]);
                put(builder, boneWeights, weights[v * 4], weights[v * 4 + 1], weights[v * 4 + 2], weights[v * 4 + 3]);
            }
            MeshData mesh = builder.build();
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
            result.add(buffer);
        }
        return result;
    }

    private static float[] floats(ByteBuffer b, int count) {
        float[] out = new float[count];
        b.asFloatBuffer().get(out);
        b.position(b.position() + count * 4);
        return out;
    }

    private static void put(BufferBuilder builder, VertexFormatElement element, float x, float y, float z, float w) {
        long ptr = ((BufferBuilderMixin) (Object) builder).shadered$beginElement(element);
        if (ptr != -1L) {
            MemoryUtil.memPutFloat(ptr, x);
            MemoryUtil.memPutFloat(ptr + 4L, y);
            MemoryUtil.memPutFloat(ptr + 8L, z);
            MemoryUtil.memPutFloat(ptr + 12L, w);
        }
    }

    /** Same layout as Shadered's ModVertexFormat.PBR (so its model shader reads it), with unique element ids. */
    private static void setupFormat() {
        if (format != null) {
            return;
        }
        tangent = VertexFormatElement.register(freeId(), 4, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 4);
        boneIds = VertexFormatElement.register(freeId(), 5, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 4);
        boneWeights = VertexFormatElement.register(freeId(), 6, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 4);
        format = VertexFormat.builder()
                .add("Position", VertexFormatElement.POSITION)
                .add("UV0", VertexFormatElement.UV0)
                .add("Color", VertexFormatElement.COLOR)
                .add("Normal", VertexFormatElement.NORMAL)
                .add("Tangent", tangent)
                .add("BoneIDs", boneIds)
                .add("BoneWeights", boneWeights)
                .build();
    }

    private static int freeId() {
        for (int id = 0; id < VertexFormatElement.MAX_COUNT; id++) {
            if (VertexFormatElement.byId(id) == null) {
                return id;
            }
        }
        throw new IllegalStateException("no free vertex element ids");
    }
}
