package com.example.skyblocks.client;

import com.example.skyblocks.OiiaCatBlockEntity;
import com.example.skyblocks.client.island.IslandRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer;
import com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Same approach as Shadered's MaxwellRenderer: hands the model, with its world matrix, to Shadered's model renderer.
 * Draws the small cat and the big cat (from the big cat's bottom-centre block, at the cube's size).
 */
public class OiiaCatRenderer implements BlockEntityRenderer<OiiaCatBlockEntity> {

    public OiiaCatRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(OiiaCatBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        int size = be.size();
        if (size <= 0) {
            return; // big cat whose size hasn't reached this client yet
        }
        boolean powered = be.powered();
        double center = be.centerOffset();

        // Without a signal: the standing cat. With a signal: the loaf pose, spinning (like the original animation).
        if (!(be.clientModel instanceof RenderableModel)) {
            be.clientModel = OiiaCatModel.create(OiiaCatModel.MODEL);
        }
        if (!(be.clientSpinModel instanceof RenderableModel)) {
            be.clientSpinModel = OiiaCatModel.create(OiiaCatModel.SPIN_MODEL);
        }
        // Inside a Sky Island a fresh model each time: the same cat can be drawn twice in a frame (the real one and the
        // scanner preview's copy), and a queued model is drawn with the matrix it has last.
        Object chosen = IslandRendering.inIsland
                ? OiiaCatModel.create(powered ? OiiaCatModel.SPIN_MODEL : OiiaCatModel.MODEL)
                : powered ? be.clientSpinModel : be.clientModel;
        if (!(chosen instanceof RenderableModel model)) {
            return; // Shadered hasn't loaded the model (yet)
        }

        // Start from the pose this renderer was given (camera-relative), so copies inside Sky Islands are drawn in
        // the miniature, at its size, rather than at their stored world position.
        PoseStack pose = new PoseStack();
        pose.last().pose().set(poseStack.last().pose());
        pose.last().normal().set(poseStack.last().normal());
        pose.translate(center, 0, center);
        pose.mulPose(Axis.YP.rotationDegrees(yaw(be.facing())));
        float scale = OiiaCatModel.SCALE * size;
        pose.scale(scale, scale, scale);
        pose.translate(0, OiiaCatModel.FEET, 0);

        // Spin only while powered by redstone, starting from the first frame each time the signal turns on.
        float seconds = 0;
        if (powered && be.getLevel() != null) {
            long now = be.getLevel().getGameTime();
            if (be.animationStart < 0) {
                be.animationStart = now;
            }
            seconds = (now - be.animationStart + partialTick) / 20.0F;
        } else {
            be.animationStart = -1;
        }
        float offsetY = OiiaCatModel.animate(pose, seconds); // 0 = the first frame = standing still

        Matrix4f relative = new Matrix4f(pose.last().pose());
        if (IslandRendering.inWindow) {
            // inside an Island Painting: draw now, while the window is open (a queued model is drawn later, outside it)
            model.modelMatrix = relative;
            AssimpRenderer.getInstance().renderDirect(model, null, false);
        } else {
            // Shadered's queued model renderer works in world coordinates (like MaxwellRenderer). On 1.20.1 the pose we
            // get starts with the camera's rotation: undo it, then move from camera-relative to world coordinates.
            net.minecraft.client.Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 camera = cam.getPosition();
            Matrix4f view = new Matrix4f().rotationX((float) Math.toRadians(cam.getXRot()))
                    .rotateY((float) Math.toRadians(cam.getYRot() + 180.0F));
            model.modelMatrix = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z)
                    .mul(view.invert()).mul(relative);
            AssimpRenderer.getInstance().addModel(model);
        }

        // How high the cat's lowest point is above the floor, in blocks (the loaf floats and bobs while spinning).
        float lift = powered ? (OiiaCatModel.SPIN_BOTTOM + offsetY) * scale : 0;
        renderShadow(be, poseStack, buffer, Math.max(0, lift), size, (float) center);
    }

    private static final ResourceLocation SHADOW = new ResourceLocation("textures/misc/shadow.png");
    private static final RenderType SHADOW_TYPE = RenderType.entityShadow(SHADOW);
    /** Same size as a cat's (ocelot's) entity shadow. */
    private static final float SHADOW_RADIUS = 0.4F;

    /**
     * A round shadow on the floor under the cat, drawn the same way Minecraft draws entity shadows (same texture,
     * render type and vertex values). Fainter the higher the cat floats; off when "Entity Shadows" is off or there
     * is no solid floor.
     */
    private static void renderShadow(OiiaCatBlockEntity be, PoseStack poseStack, MultiBufferSource buffer, float lift,
                                     float size, float center) {
        Minecraft mc = Minecraft.getInstance();
        Level level = be.getLevel();
        if (level == null || !mc.options.entityShadows().get()) {
            return;
        }
        BlockPos below = be.getBlockPos().below();
        // (a copy inside a Sky Island has no real floor under its stored position: it always gets its shadow)
        if (!IslandRendering.inIsland && !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return;
        }
        float alpha = 0.5F * Math.max(0, 1 - lift / size * 2.0F);
        if (alpha <= 0.01F) {
            return;
        }
        VertexConsumer consumer = buffer.getBuffer(SHADOW_TYPE);
        PoseStack.Pose last = poseStack.last();
        float y = 0.002F; // just above the floor (the bottom of our block)
        float r = SHADOW_RADIUS * size;
        shadowVertex(consumer, last, center - r, y, center - r, 0, 0, alpha);
        shadowVertex(consumer, last, center - r, y, center + r, 0, 1, alpha);
        shadowVertex(consumer, last, center + r, y, center + r, 1, 1, alpha);
        shadowVertex(consumer, last, center + r, y, center - r, 1, 0, alpha);
    }

    private static void shadowVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                                     float u, float v, float alpha) {
        consumer.vertex(pose.pose(), x, y, z)
                .color(1.0F, 1.0F, 1.0F, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** The model faces +Z (south); turn it to face the block's FACING. */
    static float yaw(Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0F;
            case EAST -> 90.0F;
            case WEST -> -90.0F;
            default -> 0.0F;
        };
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
