package com.example.skyblocks.client;

import com.example.skyblocks.FilterBlock;
import com.example.skyblocks.FilterBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The filter blocks (FilterBlock, from The Valley Mod by D0nkey100): each block's faces that aren't against another of
 * the same filter, kept as they're met among the block entities; then, once the world's drawn (LatePass: the sky blocks,
 * models and water in it too), a copy is made of the picture so far and every filter face is drawn over it with
 * shaders/core/filter_glass - each pixel the world behind it, through its filter.
 */
public class FilterRenderer implements BlockEntityRenderer<FilterBlockEntity> {
    static ShaderInstance shader;
    static final ResourceLocation LED = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/effect/filter_led.png");
    private static TextureTarget copy;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "filter_glass"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType FILTER = create("skyblocks_filter", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_filter_copy", () -> {
                            copyScreen();
                            RenderSystem.setShaderTexture(1, LED);
                            if (shader != null) {
                                Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                                shader.safeGetUniform("CamPos").set((float) (cam.x % 1024.0), (float) (cam.y % 1024.0), (float) (cam.z % 1024.0));
                            }
                        }, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL) // (from inside it too)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /**
     * A face waiting to be drawn: its corners (from the camera, the world's way round), their places on the face (u, v,
     * plus 4 x which of its edges get a frame: shaders/core/filter_glass) and its colour (dye, filter).
     */
    private record Face(float[] corners, float[] uvs, int colour) {}

    private static final List<Face> FACES = new ArrayList<>();
    private static boolean queued;

    /** A new frame: anything a frame left behind (it was never drawn) let go. */
    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class Frames {
        private Frames() {}

        @SubscribeEvent
        public static void onStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
            if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY && !MirrorRenderer.isRenderingReflection()) {
                FACES.clear();
                queued = false;
            }
        }
    }

    public FilterRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FilterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || shader == null || !(state.getBlock() instanceof FilterBlock filter)) {
            return;
        }
        if (com.example.skyblocks.client.iris.IrisCompat.renderingShadowPass()) {
            return;
        }
        int mode = filter.mode();
        int dye = be.colour();
        if (filter.dyeable()) {
            if (mode == 5 && dye >= 0) {
                mode = 21; // (a dyed blueprint)
            } else if (mode == 20 && dye < 0) {
                dye = 0xFFFFFF; // (an undyed tint: white)
            }
        }
        int colour = (Math.max(dye, 0) & 0xFFFFFF) | (mode & 255) << 24;
        BlockPos pos = be.getBlockPos();
        Matrix4f m = pose.last().pose();
        Vector3f v = new Vector3f();
        // Only the faces turned towards the camera: anything seen through the pane is seen through one of those (its far
        // faces drawn too, their frames would show through it). Standing inside a pane of this filter, the faces round you
        // are all turned away: then those, frameless (shaders/core/filter_glass: gl_FrontFacing).
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean inside = level.getBlockState(BlockPos.containing(cam)).is(state.getBlock());
        for (Direction d : Direction.values()) {
            // (not against another of the same filter - one pane - nor against a solid block: nothing to see there)
            if (!net.minecraft.world.level.block.Block.shouldRenderFace(state, level, pos, d, pos.relative(d))) {
                continue;
            }
            double toCamera = (cam.x - (pos.getX() + 0.5 + d.getStepX() * 0.5)) * d.getStepX()
                    + (cam.y - (pos.getY() + 0.5 + d.getStepY() * 0.5)) * d.getStepY()
                    + (cam.z - (pos.getZ() + 0.5 + d.getStepZ() * 0.5)) * d.getStepZ();
            if (toCamera <= 0 && !inside) {
                continue;
            }
            // the face's two directions along it: u, v (block coordinates); a frame on each edge with no more of the pane past it
            Direction.Axis[] along = alongFace(d.getAxis());
            int frame = 0;
            if (!level.getBlockState(pos.relative(Direction.fromAxisAndDirection(along[0], Direction.AxisDirection.NEGATIVE))).is(state.getBlock())) frame |= 1;
            if (!level.getBlockState(pos.relative(Direction.fromAxisAndDirection(along[0], Direction.AxisDirection.POSITIVE))).is(state.getBlock())) frame |= 2;
            if (!level.getBlockState(pos.relative(Direction.fromAxisAndDirection(along[1], Direction.AxisDirection.NEGATIVE))).is(state.getBlock())) frame |= 4;
            if (!level.getBlockState(pos.relative(Direction.fromAxisAndDirection(along[1], Direction.AxisDirection.POSITIVE))).is(state.getBlock())) frame |= 8;
            float[] c = new float[12];
            float[] uv = new float[8];
            float[][] corners = corners(d);
            for (int k = 0; k < 4; k++) {
                float[] p = corners[k];
                // (a hair inside the block: a neighbour's face in the same place, glass say, drawn over it rather than flickering)
                float x = p[0] - d.getStepX() * INSET, y = p[1] - d.getStepY() * INSET, z = p[2] - d.getStepZ() * INSET;
                m.transformPosition(x, y, z, v);
                c[k * 3] = v.x;
                c[k * 3 + 1] = v.y;
                c[k * 3 + 2] = v.z;
                uv[k * 2] = p[along[0].ordinal()] + 4.0F * (frame & 3);
                uv[k * 2 + 1] = p[along[1].ordinal()] + 4.0F * (frame >> 2);
            }
            if (FACES.size() < 65536) {
                FACES.add(new Face(c, uv, colour));
            }
        }
        if (!queued) {
            queued = LatePass.last(FilterRenderer::drawAll);
            if (!queued) {
                drawAll(buffers instanceof MultiBufferSource.BufferSource source ? source : Minecraft.getInstance().renderBuffers().bufferSource());
            }
        }
    }

    /** The faces waiting, all drawn now (over one copy of the picture). */
    private static void drawAll(MultiBufferSource.BufferSource source) {
        queued = false;
        if (FACES.isEmpty() || shader == null) {
            FACES.clear();
            return;
        }
        source.endBatch();
        // One filter (and dye) at a time, the farthest first, each over a fresh copy of the picture: a filter seen through
        // another shows through it with both applied (drawn all at once, the farther one's faces painted over the nearer).
        java.util.Map<Integer, List<Face>> groups = new java.util.HashMap<>();
        java.util.Map<Integer, Float> nearest = new java.util.HashMap<>();
        for (Face f : FACES) {
            groups.computeIfAbsent(f.colour(), k -> new ArrayList<>()).add(f);
            float[] c = f.corners();
            float near = Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                near = Math.min(near, c[k * 3] * c[k * 3] + c[k * 3 + 1] * c[k * 3 + 1] + c[k * 3 + 2] * c[k * 3 + 2]);
            }
            nearest.merge(f.colour(), near, Math::min);
        }
        FACES.clear();
        List<Integer> order = new ArrayList<>(groups.keySet());
        order.sort((a, b) -> Float.compare(nearest.get(b), nearest.get(a)));
        Matrix4f identity = new Matrix4f();
        for (int key : order) {
            VertexConsumer vc = source.getBuffer(Types.FILTER);
            for (Face f : groups.get(key)) {
                float[] c = f.corners(), uv = f.uvs();
                int a = f.colour() >>> 24, r = (f.colour() >> 16) & 255, g = (f.colour() >> 8) & 255, b = f.colour() & 255;
                for (int k = 0; k < 4; k++) {
                    vc.addVertex(identity, c[k * 3], c[k * 3 + 1], c[k * 3 + 2]).setUv(uv[k * 2], uv[k * 2 + 1]).setColor(r, g, b, a);
                }
            }
            source.endBatch(Types.FILTER); // (its copy made as it's drawn: the texturing state)
        }
    }

    private static final float INSET = 0.002F;

    /** The two axes along a face across this one (u, v). */
    private static Direction.Axis[] alongFace(Direction.Axis normal) {
        return switch (normal) {
            case X -> new Direction.Axis[] {Direction.Axis.Z, Direction.Axis.Y};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
        };
    }

    /** A block face's corners (block coordinates), facing out. */
    private static float[][] corners(Direction d) {
        return switch (d) {
            case DOWN -> new float[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
            case UP -> new float[][] {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case NORTH -> new float[][] {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}};
            case SOUTH -> new float[][] {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
            case WEST -> new float[][] {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}};
            case EAST -> new float[][] {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}};
        };
    }

    /** Copies the picture drawn so far (its colour) for the filters to look through, and hands it to the shader. */
    private static void copyScreen() {
        if (com.example.skyblocks.client.iris.IrisDefer.drawing()) {
            // with a shader pack: the pack's finished image (the last frame's)
            com.example.skyblocks.client.iris.IrisIslandTarget.wantLastFinal = true;
            int last = com.example.skyblocks.client.iris.IrisIslandTarget.lastFinalTexture();
            if (last != 0) {
                RenderSystem.setShaderTexture(0, last);
                return;
            }
        }
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        int w = Math.max(vp[2], 1), h = Math.max(vp[3], 1);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        if (copy == null) {
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(vp[0], vp[1], vp[0] + w, vp[1] + h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
    }

    @Override
    public boolean shouldRenderOffScreen(FilterBlockEntity be) {
        return true; // (a big pane of them: its faces drawn even when the block itself is just off screen)
    }
}
