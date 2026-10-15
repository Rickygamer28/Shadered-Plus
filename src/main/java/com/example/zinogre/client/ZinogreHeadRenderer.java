package com.example.zinogre.client;

import com.example.zinogre.ZinogreHeadBlock;
import com.example.zinogre.ZinogreHeadBlockEntity;
import com.example.zinogre.ZinogreMod;
import com.example.zinogre.ZinogreWallHeadBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.assimp.AssimpModel;
import com.noodlegamer76.shadered.client.assimp.McModel;
import com.noodlegamer76.shadered.client.assimp.load.AssimpModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
    /** The same head with its mouth closed (same vertices and placement), for wearing. */
    private static final ResourceLocation CLOSED_FILE = ResourceLocation.fromNamespaceAndPath(ZinogreMod.MOD_ID, "models/zinogre_head_closed.bin");

    /** The head mesh (shared by all heads): vertex arrays and triangles per material. */
    record Mesh(float[] pos, float[] uv, float[] normal, List<int[]> triangles, int[] materials) {}
    @Nullable
    private static Mesh mesh, closedMesh;
    private static boolean failed, closedFailed;

    public ZinogreHeadRenderer(BlockEntityRendererProvider.Context context) {}

    /** Head size on the stand / on the plaque (model units -> blocks). */
    private static final float STAND_SCALE = 0.02F, WALL_SCALE = 0.022F;
    /** The head mesh: about 45 units long and 44 tall, centred in x and z, standing on y = 0, facing +Z. */
    private static final float HEAD_HALF_LENGTH = 22.5F, HEAD_HALF_HEIGHT = 22.0F;
    private static final ResourceLocation WOOD = ResourceLocation.withDefaultNamespace("block/dark_oak_planks");

    @Override
    public void render(ZinogreHeadBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        if (state.getBlock() instanceof ZinogreHeadBlock block) {
            // on the floor: a small wooden stand (base and upright board), the head mounted on the board
            float yaw = RotationSegment.convertToDegrees(state.getValue(ZinogreHeadBlock.ROTATION));
            poseStack.mulPose(Axis.YP.rotationDegrees(-yaw)); // looks the way it was placed, like skulls
            box(poseStack, buffer, packedLight, -7, 0, -7, 7, 2, 1);
            box(poseStack, buffer, packedLight, -6, 2, -7, 6, 15, -5);
            poseStack.translate(0.0F, 8.5F / 16.0F - HEAD_HALF_HEIGHT * STAND_SCALE, -5.0F / 16.0F + HEAD_HALF_LENGTH * STAND_SCALE);
            poseStack.scale(STAND_SCALE, STAND_SCALE, STAND_SCALE);
            drawHead(poseStack, buffer, packedLight, block.stygian);
        } else if (state.getBlock() instanceof ZinogreWallHeadBlock block) {
            // on a wall: a small wooden mount against the wall, the head sticking out of it
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.getValue(ZinogreWallHeadBlock.FACING).toYRot()));
            box(poseStack, buffer, packedLight, -4, 4, -8, 4, 12, -7); // a small mount, mostly hidden behind the head
            poseStack.translate(0.0F, 0.5F - HEAD_HALF_HEIGHT * WALL_SCALE, -7.0F / 16.0F + HEAD_HALF_LENGTH * WALL_SCALE);
            poseStack.scale(WALL_SCALE, WALL_SCALE, WALL_SCALE);
            drawHead(poseStack, buffer, packedLight, block.stygian);
        }
        poseStack.popPose();
    }

    /**
     * Draws the head mesh with the current pose (model units, facing +Z, standing on y = 0) and the textures of its
     * look. Returns false when the model isn't loaded (yet).
     */
    static boolean drawHead(PoseStack poseStack, MultiBufferSource buffer, int packedLight, boolean stygian) {
        return drawHead(poseStack, buffer, packedLight, stygian, mesh());
    }

    /** The same with the mouth closed (falls back to the open one if that file is missing). */
    static boolean drawClosedHead(PoseStack poseStack, MultiBufferSource buffer, int packedLight, boolean stygian) {
        Mesh closed = closedMesh();
        return drawHead(poseStack, buffer, packedLight, stygian, closed != null ? closed : mesh());
    }

    private static boolean drawHead(PoseStack poseStack, MultiBufferSource buffer, int packedLight, boolean stygian, @Nullable Mesh head) {
        McModel textures = AssimpModels.getModel(stygian ? ZinogreRenderer.STYGIAN_MODEL : ZinogreRenderer.MODEL);
        if (textures == null || head == null) {
            Debug.once("head-missing", "Zinogre head not drawn: textures loaded {}, mesh loaded {}", textures != null, head != null);
            return false;
        }
        List<AssimpModel> parts = textures.getModels();
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
        return true;
    }

    /** Draws the head inside a unit cube (centred, about one block big, facing -Z): for the item and when worn. */
    static boolean drawHeadInCube(PoseStack poseStack, MultiBufferSource buffer, int packedLight, boolean stygian) {
        float scale = 0.95F / (2 * HEAD_HALF_LENGTH);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F)); // items and worn heads face -Z
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0F, -HEAD_HALF_HEIGHT, 0.0F);
        boolean drawn = drawHead(poseStack, buffer, packedLight, stygian);
        poseStack.popPose();
        return drawn;
    }

    /** A dark oak box, in pixels (1/16 block) of the current pose. */
    private static void box(PoseStack poseStack, MultiBufferSource buffer, int light, float x0, float y0, float z0, float x1, float y1, float z1) {
        TextureAtlasSprite wood = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WOOD);
        VertexConsumer out = buffer.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();
        float a = x0 / 16, b = y0 / 16, c = z0 / 16, d = x1 / 16, e = y1 / 16, f = z1 / 16;
        // each face: 4 corners (counter-clockwise from outside), normal, and the face size for the texture
        face(out, pose, wood, light, a, b, f, d, b, f, d, e, f, a, e, f, 0, 0, 1, x1 - x0, y1 - y0);   // +Z
        face(out, pose, wood, light, d, b, c, a, b, c, a, e, c, d, e, c, 0, 0, -1, x1 - x0, y1 - y0);  // -Z
        face(out, pose, wood, light, d, b, f, d, b, c, d, e, c, d, e, f, 1, 0, 0, z1 - z0, y1 - y0);   // +X
        face(out, pose, wood, light, a, b, c, a, b, f, a, e, f, a, e, c, -1, 0, 0, z1 - z0, y1 - y0);  // -X
        face(out, pose, wood, light, a, e, f, d, e, f, d, e, c, a, e, c, 0, 1, 0, x1 - x0, z1 - z0);   // top
        face(out, pose, wood, light, a, b, c, d, b, c, d, b, f, a, b, f, 0, -1, 0, x1 - x0, z1 - z0);  // bottom
    }

    private static void face(VertexConsumer out, PoseStack.Pose pose, TextureAtlasSprite sprite, int light,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, float width, float height) {
        float u1 = Math.min(width, 16) / 16, v1 = Math.min(height, 16) / 16;
        corner(out, pose, sprite, light, x0, y0, z0, 0, v1, nx, ny, nz);
        corner(out, pose, sprite, light, x1, y1, z1, u1, v1, nx, ny, nz);
        corner(out, pose, sprite, light, x2, y2, z2, u1, 0, nx, ny, nz);
        corner(out, pose, sprite, light, x3, y3, z3, 0, 0, nx, ny, nz);
    }

    private static void corner(VertexConsumer out, PoseStack.Pose pose, TextureAtlasSprite sprite, int light,
                               float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(sprite.getU(u), sprite.getV(v))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
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
            mesh = load(FILE);
            failed = mesh == null;
        }
        return mesh;
    }

    @Nullable
    static Mesh closedMesh() {
        if (closedMesh == null && !closedFailed) {
            closedMesh = load(CLOSED_FILE);
            closedFailed = closedMesh == null;
        }
        return closedMesh;
    }

    @Nullable
    private static Mesh load(ResourceLocation file) {
        try {
            ByteBuffer b = ZinogreData.readResource(file);
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
            return new Mesh(pos, uv, normal, triangles, materials);
        } catch (IOException | RuntimeException e) {
            ZinogreMod.LOGGER.error("Couldn't load the Zinogre head {}", file, e);
            return null;
        }
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(ZinogreHeadBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.5);
    }
}
