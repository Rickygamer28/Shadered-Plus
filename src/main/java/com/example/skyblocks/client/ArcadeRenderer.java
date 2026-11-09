package com.example.skyblocks.client;

import com.example.skyblocks.ArcadeBlock;
import com.example.skyblocks.ArcadeBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The arcade cabinet's screen (on its upper half's front): a demo playing on an old tube (shaders/core/arcade_screen),
 * and over it the best score at the game being shown, and who got it.
 */
public class ArcadeRenderer implements BlockEntityRenderer<ArcadeBlockEntity> {
    static ShaderInstance shader;
    private static float time, seed, live;
    /** The picture of someone's game, filled for each cabinet just before it's drawn (they're drawn at once). */
    private static net.minecraft.client.renderer.texture.DynamicTexture picture;
    /** The glass, in the upper half's pixels, its front facing north. */
    private static final float X0 = 2.5F / 16, X1 = 13.5F / 16, Y0 = 1.0F + 2.0F / 16, Y1 = 1.0F + 11.0F / 16, Z = 3.5F / 16;
    /** (as the shader's: how long each demo runs) */
    private static final float CYCLE = 18.0F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerArcadeShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "arcade_screen"), DefaultVertexFormat.POSITION_TEX, s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType SCREEN = create("skyblocks_arcade_screen", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_arcade_screen_time", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("Time").set(time);
                                shader.safeGetUniform("Seed").set(seed);
                                shader.safeGetUniform("Power").set(1.0F);
                                shader.safeGetUniform("Live").set(live);
                            }
                            if (picture != null) {
                                com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, picture.getId());
                            }
                        }, () -> {}))
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private final Font font;

    public ArcadeRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(ArcadeBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (shader == null || level == null || !(be.getBlockState().getBlock() instanceof ArcadeBlock)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(ArcadeBlock.FACING);
        // (each cabinet a little out of step with the others)
        seed = (float) ((be.getBlockPos().asLong() * 31L) & 1023L) / 1023.0F;
        time = ((level.getGameTime() % 240000L) + partialTick) / 20.0F + seed * CYCLE * 2.0F;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F))); // (as the model is turned)
        poseStack.translate(-0.5F, 0.0F, -0.5F);

        boolean playing = be.liveCells != null && level.getGameTime() - be.liveAt < 40L;
        live = playing ? 1.0F : 0.0F;
        if (playing) {
            showPicture(be.liveCells);
        }
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = source.getBuffer(Types.SCREEN);
        // (seen from the front, its right is towards -x)
        vc.addVertex(m, X1, Y0, Z).setUv(0.0F, 0.0F);
        vc.addVertex(m, X0, Y0, Z).setUv(1.0F, 0.0F);
        vc.addVertex(m, X0, Y1, Z).setUv(1.0F, 1.0F);
        vc.addVertex(m, X1, Y1, Z).setUv(0.0F, 1.0F);
        source.endBatch(Types.SCREEN);

        // the best score at the game being shown; "insert coin" blinking at the foot
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(be.getBlockPos().getCenter());
        if (distance < 24.0 * 24.0 && playing) { // (who's playing, and their score)
            String name = be.liveName.length() > 12 ? be.liveName.substring(0, 12) : be.liveName;
            text(poseStack, buffers, name + "  " + be.liveScore, Y1 - 0.045F, 0xFFE9F27A, 0.004F);
        } else if (distance < 24.0 * 24.0) {
            int game = Math.floorMod((int) Math.floor(time / CYCLE), 3);
            int best = be.best(game);
            String top = best > 0 ? "HI " + best + "  " + be.bestBy(game) : (game == 0 ? "SNAKE" : game == 1 ? "BREAKOUT" : "TETRIS");
            text(poseStack, buffers, top, Y1 - 0.06F, 0xFFE9F27A, 0.0055F);
            if (((int) (time * 1.5F) & 1) == 0) {
                text(poseStack, buffers, "INSERT COIN", Y0 + 0.035F, 0xFFFFFFFF, 0.0045F);
            }
        }
        poseStack.popPose();
    }

    /** Puts a game's cells (colours of the palette) into the picture the shader shows. */
    private static void showPicture(byte[] cells) {
        int w = com.example.skyblocks.ArcadeFramePayload.WIDTH, h = com.example.skyblocks.ArcadeFramePayload.HEIGHT;
        if (picture == null) {
            picture = new net.minecraft.client.renderer.texture.DynamicTexture(w, h, false);
        }
        com.mojang.blaze3d.platform.NativeImage image = picture.getPixels();
        if (image == null) {
            return;
        }
        int[] palette = com.example.skyblocks.ArcadeBlockEntity.PALETTE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = palette[cells[y * w + x] & 15];
                // (the picture's pixels are ABGR)
                image.setPixelRGBA(x, y, 0xFF000000 | ((rgb & 255) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 255));
            }
        }
        picture.upload();
    }

    /** A line written across the glass's middle, its top at {@code y}. */
    private void text(PoseStack poseStack, MultiBufferSource buffers, String line, float y, int colour, float scale) {
        float room = (X1 - X0) * 0.92F;
        float w = font.width(line) * scale;
        if (w > room) {
            scale *= room / w;
        }
        poseStack.pushPose();
        poseStack.translate((X0 + X1) / 2.0F, y, Z - 0.003F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(line, -font.width(line) / 2.0F, 0.0F, colour, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ArcadeBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0.0, 1.0, 0.0);
    }
}
