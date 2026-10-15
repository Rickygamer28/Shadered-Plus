package com.example.zinogre.client;

import com.example.zinogre.ZinogreHeadBlock;
import com.example.zinogre.ZinogreHeadBlockEntity;
import com.example.zinogre.ZinogreMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.assimp.AssimpMaterial;
import com.noodlegamer76.shadered.client.assimp.AssimpModel;
import com.noodlegamer76.shadered.client.assimp.McModel;
import com.noodlegamer76.shadered.client.assimp.load.AssimpModels;
import com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer;
import com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a placed Zinogre head with Shadered's model renderer: the head of the model (a static mesh from
 * assets/zinogre/models/zinogre_head.bin, cut from the model by the converter) with the textures of its look.
 */
public class ZinogreHeadRenderer implements BlockEntityRenderer<ZinogreHeadBlockEntity> {
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/zinogre_head.bin");
    /** Model units -> blocks: the head is about 45 units long, so about 0.85 blocks. */
    private static final float SCALE = 0.019F;

    /** The head mesh: one buffer per material (shared by all heads). */
    private record Part(int material, VertexBuffer buffer) {}
    @Nullable
    private static List<Part> parts;
    private static boolean failed;

    /** One placed head's model copy (own materials for its light). */
    private record HeadModel(McModel source, RenderableModel model, List<AssimpMaterial> materials) {}

    public ZinogreHeadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ZinogreHeadBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ZinogreHeadBlock block)) {
            return;
        }
        McModel textures = AssimpModels.getModel(block.stygian ? ZinogreRenderer.STYGIAN_MODEL : ZinogreRenderer.MODEL);
        List<Part> mesh = parts();
        if (textures == null || mesh == null) {
            return;
        }
        HeadModel head = be.clientState instanceof HeadModel h && h.source() == textures ? h : null;
        if (head == null) {
            head = create(textures, mesh);
            be.clientState = head;
            if (head == null) {
                return;
            }
        }
        // light where it stands (Shadered's model shader has no world lighting)
        float block = LightTexture.block(packedLight) / 15.0F;
        float sky = LightTexture.sky(packedLight) / 15.0F;
        float daylight = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getSkyDarken(partialTick) : 1.0F;
        float v = 0.18F + 0.82F * Math.max(block, sky * daylight);
        for (AssimpMaterial material : head.materials()) {
            material.setColor(v, v, v, 1.0F);
        }

        PoseStack pose = new PoseStack();
        pose.last().pose().set(poseStack.last().pose());
        pose.translate(0.5F, 0.0F, 0.5F);
        float yaw = RotationSegment.convertToDegrees(state.getValue(ZinogreHeadBlock.ROTATION));
        pose.mulPose(Axis.YP.rotationDegrees(-yaw)); // the head faces +Z, like a mob with that yaw
        pose.scale(SCALE, SCALE, SCALE);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        head.model().modelMatrix = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z).mul(pose.last().pose());
        AssimpRenderer.getInstance().addModel(head.model());
    }

    @Nullable
    private static HeadModel create(McModel textures, List<Part> mesh) {
        List<AssimpModel> loaded = textures.getModels();
        List<AssimpMaterial> materials = new ArrayList<>();
        for (AssimpModel part : loaded) {
            AssimpMaterial m = part.getMaterial(), copy = new AssimpMaterial();
            copy.setDiffuseTexture(m.getDiffuseTexture());
            copy.setNormalTexture(m.getNormalTexture());
            copy.setSpecularTexture(m.getSpecularTexture());
            copy.setEmissiveTexture(m.getEmissiveTexture());
            copy.setTransparent(m.useAlpha());
            copy.setBlendMode(m.getBlendMode());
            materials.add(copy);
        }
        RenderableModel model = new RenderableModel();
        for (Part part : mesh) {
            if (part.material() >= materials.size()) {
                return null;
            }
            AssimpModel piece = new AssimpModel(part.buffer(), materials.get(part.material()));
            piece.instances.add(new AssimpModel.MeshInstanceTransform(new Matrix4f(), new Matrix4f()));
            model.add(piece);
        }
        return new HeadModel(textures, model, materials);
    }

    @Nullable
    private static List<Part> parts() {
        if (parts == null && !failed) {
            try {
                ByteBuffer b = ZinogreData.readResource(FILE);
                if (b.getInt() != 0x3144485A) { // "ZHD1"
                    throw new IOException("not a Zinogre head file");
                }
                int n = b.getInt();
                float[] pos = ZinogreData.floats(b, n * 3);
                float[] uv = ZinogreData.floats(b, n * 2);
                float[] normal = ZinogreData.floats(b, n * 3);
                float[] shade = ZinogreData.floats(b, n);
                int count = b.getInt();
                List<Part> list = new ArrayList<>();
                for (int p = 0; p < count; p++) {
                    int material = b.get() & 0xFF;
                    int[] indices = new int[b.getInt()];
                    b.asIntBuffer().get(indices);
                    b.position(b.position() + indices.length * 4);
                    list.add(new Part(material, ZinogreData.upload(pos, uv, normal, shade, null, null, indices)));
                }
                parts = list;
            } catch (IOException | RuntimeException e) {
                ZinogreMod.LOGGER.error("Couldn't load the Zinogre head {}", FILE, e);
                failed = true;
            }
        }
        return parts;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
