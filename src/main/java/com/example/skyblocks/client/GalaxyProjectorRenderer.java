package com.example.skyblocks.client;

import com.example.skyblocks.GalaxyProjectorBlock;
import com.example.skyblocks.GalaxyProjectorBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.iris.IrisCompat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The galaxy projector's light on the room: a box round its reach, drawn from the inside (its far faces), where the
 * shader (shaders/core/galaxy_projector) finds what's already drawn at each pixel - from a copy of the depth - and lights
 * it with the sky in the direction from the projector: only surfaces turned towards it, and not where a block stands in
 * between (a little map of the solid blocks round it, made here every two seconds). Added on top of the picture, the
 * brighter the darker it is round the projector.
 */
public class GalaxyProjectorRenderer implements BlockEntityRenderer<GalaxyProjectorBlockEntity> {
    /** How far the light reaches (blocks). */
    static final int RANGE = 8;
    private static final int SIZE = RANGE * 2 + 1;
    static ShaderInstance shader;
    private static TextureTarget depth;
    private static final Map<BlockPos, Grid> GRIDS = new HashMap<>();
    private static long frame;

    // the projector being drawn now (for the shader's uniforms)
    private static final Matrix4f INV = new Matrix4f();
    private static float cx, cy, cz, gx, gy, gz, mode, strength;
    private static int gridTexture;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerGalaxyProjectorRenderer(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "galaxy_projector"), DefaultVertexFormat.POSITION, s -> shader = s);
        }
    }

    /** The solid blocks round a projector (red: solid), a slice per height side by side. */
    private static final class Grid {
        final DynamicTexture texture = new DynamicTexture(SIZE * SIZE, SIZE, false);
        long built = Long.MIN_VALUE;
        long seen;
    }

    abstract static class Types extends RenderType {
        static final RenderType LIGHT = create("skyblocks_galaxy_projector", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_galaxy_projector_light", GalaxyProjectorRenderer::setUp, () -> {}))
                        .setTransparencyState(new TransparencyStateShard("skyblocks_galaxy_projector_add", () -> {
                            RenderSystem.enableBlend();
                            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                            RenderSystem.enableCull();
                            GL11.glCullFace(GL11.GL_FRONT); // (the box's far side: there even with the eye inside it)
                        }, () -> {
                            GL11.glCullFace(GL11.GL_BACK);
                            RenderSystem.disableBlend();
                            RenderSystem.defaultBlendFunc();
                        }))
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** Right before drawing: a copy of the depth so far, and this projector's uniforms. */
    private static void setUp() {
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        int w = Math.max(vp[2], 1), h = Math.max(vp[3], 1);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        if (depth == null) {
            depth = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            depth.enableStencil(); // (the screen's depth format, for copying it)
        } else if (depth.width != w || depth.height != h) {
            depth.resize(w, h, Minecraft.ON_OSX);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depth.frameBufferId);
        GL30.glBlitFramebuffer(vp[0], vp[1], vp[0] + w, vp[1] + h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        RenderSystem.setShaderTexture(0, depth.getDepthTextureId());
        RenderSystem.setShaderTexture(1, gridTexture);
        if (shader != null) {
            shader.safeGetUniform("InvViewProj").set(INV);
            shader.safeGetUniform("Center").set(cx, cy, cz);
            shader.safeGetUniform("GridMin").set(gx, gy, gz);
            shader.safeGetUniform("Mode").set(mode);
            shader.safeGetUniform("Strength").set(strength);
            shader.safeGetUniform("Range").set((float) RANGE);
            shader.safeGetUniform("GalTime").set(LavaLampRenderer.seconds());
        }
    }

    public GalaxyProjectorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GalaxyProjectorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        Minecraft mc = Minecraft.getInstance();
        if (shader == null || !(be.getLevel() instanceof ClientLevel level) || !state.getValue(GalaxyProjectorBlock.LIT)
                || MirrorRenderer.isRenderingReflection() || IrisCompat.shaderPackInUse()
                || buffers != mc.renderBuffers().bufferSource()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        long gameTime = level.getGameTime();
        Grid grid = GRIDS.computeIfAbsent(pos.immutable(), p -> new Grid());
        grid.seen = gameTime;
        if (gameTime - grid.built >= 40 || gameTime < grid.built) {
            build(level, pos, grid);
            grid.built = gameTime;
        }
        if (gameTime != frame) { // (forget the ones not drawn for a while)
            frame = gameTime;
            GRIDS.entrySet().removeIf(e -> {
                boolean old = gameTime - e.getValue().seen > 200 || gameTime < e.getValue().seen;
                if (old) e.getValue().texture.close();
                return old;
            });
        }
        // brighter the darker it is round it (a projector's light washes out in daylight)
        float day = Math.max(0.0F, Math.min(1.0F, (level.getSkyDarken(partialTick) - 0.2F) / 0.8F));
        float around = Math.max(LightTexture.block(light) / 15.0F * 0.6F, LightTexture.sky(light) / 15.0F * day);
        strength = Math.max(0.12F, 1.0F - around * 0.9F);
        mode = state.getValue(GalaxyProjectorBlock.MODE);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        cx = (float) (pos.getX() + 0.5 - cam.x);
        cy = (float) (pos.getY() + 0.45 - cam.y);
        cz = (float) (pos.getZ() + 0.5 - cam.z);
        gx = (float) (pos.getX() - RANGE - cam.x);
        gy = (float) (pos.getY() - RANGE - cam.y);
        gz = (float) (pos.getZ() - RANGE - cam.z);
        INV.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).invert();
        gridTexture = grid.texture.getId();
        // the box round its reach (in the block's space), drawn at once with this projector's settings
        VertexConsumer vc = buffers.getBuffer(Types.LIGHT);
        Matrix4f m = poseStack.last().pose();
        float lo = 0.5F - RANGE - 0.5F, hi = 0.5F + RANGE + 0.5F;
        int[][] faces = {{4, 5, 7, 6}, {1, 0, 2, 3}, {5, 1, 3, 7}, {0, 4, 6, 2}, {6, 7, 3, 2}, {0, 1, 5, 4}};
        for (int[] f : faces) {
            for (int k : f) {
                vc.addVertex(m, (k & 1) != 0 ? hi : lo, (k & 2) != 0 ? hi : lo, (k & 4) != 0 ? hi : lo);
            }
        }
        mc.renderBuffers().bufferSource().endBatch(Types.LIGHT);
    }

    /** Which blocks round the projector are solid (light doesn't pass). */
    private static void build(ClientLevel level, BlockPos pos, Grid grid) {
        NativeImage img = grid.texture.getPixels();
        if (img == null) {
            return;
        }
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    q.set(pos.getX() - RANGE + x, pos.getY() - RANGE + y, pos.getZ() - RANGE + z);
                    BlockState s = level.getBlockState(q);
                    boolean solid = !(x == RANGE && y == RANGE && z == RANGE) && s.canOcclude() && s.isSolidRender(level, q);
                    img.setPixelRGBA(x + SIZE * z, y, solid ? 0xFF0000FF : 0xFF000000);
                }
            }
        }
        grid.texture.upload();
    }

    @Override
    public AABB getRenderBoundingBox(GalaxyProjectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(RANGE + 1);
    }

    @Override
    public boolean shouldRenderOffScreen(GalaxyProjectorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
