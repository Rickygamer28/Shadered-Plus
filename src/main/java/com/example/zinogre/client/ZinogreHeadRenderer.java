package com.example.zinogre.client;

import com.example.zinogre.ZinogreHeadBlock;
import com.example.zinogre.ZinogreHeadBlockEntity;
import com.example.zinogre.ZinogreMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.assimp.AssimpModel;
import com.noodlegamer76.shadered.client.assimp.McModel;
import com.noodlegamer76.shadered.client.assimp.load.AssimpModels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a placed Zinogre head: the head of the model (a static mesh from assets/zinogre/models/zinogre_head.bin, cut
 * from the model by the converter) with the textures of its look.
 *
 * Unlike the mob it is drawn the vanilla way (triangles into the normal entity render types, with the textures
 * Shadered loaded from the GLB): that gets Minecraft's own lighting and works wherever block entities are drawn,
 * Sky Islands included.
 */
public class ZinogreHeadRenderer implements BlockEntityRenderer<ZinogreHeadBlockEntity> {
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/zinogre_head.bin");
    /** Model units -> blocks: the head is about 45 units long, so about 0.85 blocks. */
    private static final float SCALE = 0.019F;

    /** The head mesh (shared by all heads): vertex arrays and triangles per material. */
    record Mesh(float[] pos, float[] uv, float[] normal, List<int[]> triangles, int[] materials) {}
    @Nullable
    private static Mesh mesh;
    private static boolean failed;

    public ZinogreHeadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ZinogreHeadBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ZinogreHeadBlock block)) {
            return;
        }
        McModel textures = AssimpModels.getModel(block.stygian ? ZinogreRenderer.STYGIAN_MODEL : ZinogreRenderer.MODEL);
        Mesh head = mesh();
        if (textures == null || head == null) {
            Debug.once("head-missing", "Zinogre head not drawn: textures loaded {}, mesh loaded {}", textures != null, head != null);
            return;
        }
        List<AssimpModel> parts = textures.getModels();

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        float yaw = RotationSegment.convertToDegrees(state.getValue(ZinogreHeadBlock.ROTATION));
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw)); // the head faces +Z, like a mob with that yaw
        poseStack.scale(SCALE, SCALE, SCALE);
        PoseStack.Pose last = poseStack.last();
        for (int p = 0; p < head.triangles().size(); p++) {
            int material = head.materials()[p];
            if (material >= parts.size()) {
                continue;
            }
            ResourceLocation texture = parts.get(material).getMaterial().getDiffuseTexture();
            VertexConsumer out = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
            int[] tris = head.triangles().get(p);
            for (int i = 0; i < tris.length; i += 3) {
                // a triangle as a quad with its last corner twice (the entity render types draw quads)
                vertex(out, last, head, tris[i], packedLight);
                vertex(out, last, head, tris[i + 1], packedLight);
                vertex(out, last, head, tris[i + 2], packedLight);
                vertex(out, last, head, tris[i + 2], packedLight);
            }
        }
        poseStack.popPose();
        Debug.once("head", "Zinogre head drawn (vanilla way) with {} parts", head.triangles().size());
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Mesh m, int v, int light) {
        out.addVertex(pose, m.pos()[v * 3], m.pos()[v * 3 + 1], m.pos()[v * 3 + 2])
                .setColor(255, 255, 255, 255)
                .setUv(m.uv()[v * 2], m.uv()[v * 2 + 1])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, m.normal()[v * 3], m.normal()[v * 3 + 1], m.normal()[v * 3 + 2]);
    }

    @Nullable
    static Mesh mesh() {
        if (mesh == null && !failed) {
            try {
                ByteBuffer b = ZinogreData.readResource(FILE);
                if (b.getInt() != 0x3144485A) { // "ZHD1"
                    throw new IOException("not a Zinogre head file");
                }
                int n = b.getInt();
                float[] pos = ZinogreData.floats(b, n * 3);
                float[] uv = ZinogreData.floats(b, n * 2);
                float[] normal = ZinogreData.floats(b, n * 3);
                ZinogreData.floats(b, n); // baked shade: not needed, Minecraft lights it
                int count = b.getInt();
                List<int[]> triangles = new ArrayList<>();
                int[] materials = new int[count];
                for (int p = 0; p < count; p++) {
                    materials[p] = b.get() & 0xFF;
                    int[] indices = new int[b.getInt()];
                    b.asIntBuffer().get(indices);
                    b.position(b.position() + indices.length * 4);
                    triangles.add(indices);
                }
                mesh = new Mesh(pos, uv, normal, triangles, materials);
            } catch (IOException | RuntimeException e) {
                ZinogreMod.LOGGER.error("Couldn't load the Zinogre head {}", FILE, e);
                failed = true;
            }
        }
        return mesh;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(ZinogreHeadBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.5);
    }
}
