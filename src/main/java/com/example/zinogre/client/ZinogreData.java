package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.noodlegamer76.shadered.mixin.BufferBuilderMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Zinogre's skeleton, animation clips and mesh, from our own file (assets/zinogre/models/zinogre_mesh.bin, made by
 * the same converter as the GLB). The GLB only provides the textures, through Shadered's loader.
 *
 * Two things in Shadered's model path don't work for this model, so they are done here instead:
 * - its mesh upload never writes bone ids and weights (its extra vertex elements all use element id 0, the
 *   position's, which Minecraft's BufferBuilder then skips), so the mesh is uploaded here with a copy of that vertex
 *   layout whose elements have ids of their own;
 * - the skeleton it loads poses the model tilted, so the bones are posed here (see {@link ZinogreAnimState}) and only
 *   the final bone matrices are handed to Shadered's renderer.
 */
final class ZinogreData {
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/zinogre_mesh.bin");

    private static VertexFormatElement tangent, boneIds, boneWeights;
    private static VertexFormat format;
    @Nullable
    private static ZinogreData loaded;
    private static boolean failed;

    /** Joint parents (-1 for the root; a parent always comes before its children). */
    final int[] parents;
    /** Inverse bind matrix per joint. */
    final Matrix4f[] inverseBind;
    /** The mesh pieces, uploaded: the body (group 0) and every state of every breakable part. */
    final List<Piece> pieces;
    final Map<String, Clip> clips;

    /**
     * A piece of the mesh: group 0 is always drawn; groups 1-6 are the breakable parts (left horn, right horn, tail,
     * tail-end fur, front legs, shoulders) and 7 the eyelids; state 0 intact, 1 broken (wound), 2 healed stump.
     * material = index into the textures GLB's materials (body, fur, translucent fur, eyes, damage layer).
     */
    record Piece(int group, int state, int material, VertexBuffer buffer) {}

    /** An animation clip: per frame and joint the local translation and rotation (tx ty tz qx qy qz qw). */
    record Clip(float fps, int frames, float[] data) {
        float seconds() {
            return (frames - 1) / fps;
        }
    }

    private ZinogreData(int[] parents, Matrix4f[] inverseBind, List<Piece> pieces, Map<String, Clip> clips) {
        this.parents = parents;
        this.inverseBind = inverseBind;
        this.pieces = pieces;
        this.clips = clips;
    }

    int joints() {
        return parents.length;
    }

    /** The data (loaded and uploaded on first use), or null if the file is missing or broken. */
    @Nullable
    static ZinogreData get() {
        if (loaded == null && !failed) {
            try {
                loaded = load(read());
            } catch (IOException | RuntimeException e) {
                ZinogreMod.LOGGER.error("Couldn't load the Zinogre model data {}", FILE, e);
                failed = true;
            }
        }
        return loaded;
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

    private static ZinogreData load(ByteBuffer b) {
        setupFormat();
        if (b.getInt() != 0x33534D5A) { // "ZMS3"
            throw new IllegalStateException("not a Zinogre model file");
        }
        int jointCount = b.getInt();
        int[] parents = new int[jointCount];
        Matrix4f[] inverseBind = new Matrix4f[jointCount];
        for (int j = 0; j < jointCount; j++) {
            string(b);
            parents[j] = b.getInt();
            float[] m = floats(b, 16);
            inverseBind[j] = new Matrix4f().set(m); // column-major
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

        int pieceCount = b.getInt();
        List<Piece> pieces = new ArrayList<>();
        for (int p = 0; p < pieceCount; p++) {
            int group = b.get() & 0xFF;
            int state = b.get() & 0xFF;
            int material = b.get() & 0xFF;
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
                put(builder, boneIds, joints[v * 4], joints[v * 4 + 1], joints[v * 4 + 2], joints[v * 4 + 3]);
                put(builder, boneWeights, weights[v * 4], weights[v * 4 + 1], weights[v * 4 + 2], weights[v * 4 + 3]);
            }
            MeshData mesh = builder.build();
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
            pieces.add(new Piece(group, state, material, buffer));
        }

        int clipCount = b.getInt();
        Map<String, Clip> clips = new HashMap<>();
        for (int c = 0; c < clipCount; c++) {
            String name = string(b);
            float fps = b.getFloat();
            int frames = b.getInt();
            clips.put(name, new Clip(fps, frames, floats(b, frames * jointCount * 7)));
        }
        return new ZinogreData(parents, inverseBind, pieces, clips);
    }

    private static String string(ByteBuffer b) {
        byte[] bytes = new byte[b.getShort() & 0xFFFF];
        b.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
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
