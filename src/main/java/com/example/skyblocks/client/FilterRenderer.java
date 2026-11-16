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
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "filter_glass"), DefaultVertexFormat.POSITION_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType FILTER = create("skyblocks_filter", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
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

    /** A face waiting to be drawn: its corners (from the camera, the world's way round) and its colour (dye, filter). */
    private record Face(float[] corners, int colour) {}

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
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).is(state.getBlock())) {
                continue; // (one pane with its neighbour)
            }
            float[] c = new float[12];
            float[][] corners = corners(d);
            for (int k = 0; k < 4; k++) {
                m.transformPosition(corners[k][0], corners[k][1], corners[k][2], v);
                c[k * 3] = v.x;
                c[k * 3 + 1] = v.y;
                c[k * 3 + 2] = v.z;
            }
            if (FACES.size() < 65536) {
                FACES.add(new Face(c, colour));
            }
        }
        if (!queued) {
            queued = LatePass.later(FilterRenderer::drawAll);
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
        VertexConsumer vc = source.getBuffer(Types.FILTER);
        Matrix4f identity = new Matrix4f();
        for (Face f : FACES) {
            float[] c = f.corners();
            int a = f.colour() >>> 24, r = (f.colour() >> 16) & 255, g = (f.colour() >> 8) & 255, b = f.colour() & 255;
            for (int k = 0; k < 4; k++) {
                vc.addVertex(identity, c[k * 3], c[k * 3 + 1], c[k * 3 + 2]).setColor(r, g, b, a);
            }
        }
        FACES.clear();
        source.endBatch(Types.FILTER);
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
