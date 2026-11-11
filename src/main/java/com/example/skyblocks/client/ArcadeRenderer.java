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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The arcade cabinet's screen (on its upper half's front): whoever's playing's game - or, nobody playing, a demo - on
 * an old tube (shaders/core/arcade_screen), the best score or the player over it; and its control panel's stick and
 * six buttons, moving with what the player holds.
 */
public class ArcadeRenderer implements BlockEntityRenderer<ArcadeBlockEntity> {
    static ShaderInstance shader;
    private static float time, seed, live, cellsW = 32, cellsH = 24;
    /** The picture of someone's game, filled for each screen just before it's drawn (they're drawn one at a time). */
    private static net.minecraft.client.renderer.texture.DynamicTexture picture;
    private static int pictureW, pictureH;
    /** The cabinets with someone playing (for the big screens to find). */
    public static final java.util.Set<BlockPos> LIVE = new java.util.HashSet<>();
    /** The glass, in the upper half's pixels, its front facing north. */
    private static final float X0 = 2.5F / 16, X1 = 13.5F / 16, Y0 = 1.0F + 2.0F / 16, Y1 = 1.0F + 11.0F / 16, Z = 2.45F / 16;
    /** (as the shader's: how long each demo runs) */
    static final float CYCLE = 18.0F;

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
                                shader.safeGetUniform("Cells").set(cellsW, cellsH);
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

    /** Someone's playing on it (heard from lately, with a picture). */
    static boolean playing(ArcadeBlockEntity be, Level level) {
        return be.livePicture != null && level.getGameTime() - be.liveAt < 40L;
    }

    /** The tube's time, for a screen at this place (each a little out of step with the others). */
    static float timeAt(BlockPos pos, Level level, float partialTick) {
        float sd = (float) ((pos.asLong() * 31L) & 1023L) / 1023.0F;
        return ((level.getGameTime() % 240000L) + partialTick) / 20.0F + sd * CYCLE * 2.0F;
    }

    /**
     * A tube, from (x0, y0) to (x1, y1) at depth z of the pose (seen from -z, its right towards -x): the game being
     * played at {@code source}, or - none - the demo.
     */
    static void drawTube(PoseStack poseStack, MultiBufferSource buffers, ArcadeBlockEntity source, BlockPos at, Level level, float partialTick,
            float x0, float y0, float x1, float y1, float z) {
        seed = (float) ((at.asLong() * 31L) & 1023L) / 1023.0F;
        time = timeAt(at, level, partialTick);
        boolean on = source != null && playing(source, level);
        live = on ? 1.0F : 0.0F;
        if (on) showPicture(source.livePicture, source.liveW, source.liveH);
        MultiBufferSource.BufferSource now = DrawNow.now(buffers);
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = now.getBuffer(Types.SCREEN);
        vc.addVertex(m, x1, y0, z).setUv(0.0F, 0.0F);
        vc.addVertex(m, x0, y0, z).setUv(1.0F, 0.0F);
        vc.addVertex(m, x0, y1, z).setUv(1.0F, 1.0F);
        vc.addVertex(m, x1, y1, z).setUv(0.0F, 1.0F);
        now.endBatch(Types.SCREEN);
    }

    @Override
    public void render(ArcadeBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (shader == null || level == null || !(be.getBlockState().getBlock() instanceof ArcadeBlock)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(ArcadeBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F))); // (as the model is turned)
        poseStack.translate(-0.5F, 0.0F, -0.5F);

        boolean playing = playing(be, level);
        drawTube(poseStack, buffers, be, be.getBlockPos(), level, partialTick, X0, Y0, X1, Y1, Z);
        panel(be, poseStack, buffers, light, playing);

        // who's playing and their score; nobody: the best score at the game being shown, "insert coin" blinking
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(be.getBlockPos().getCenter());
        if (distance < 24.0 * 24.0 && playing) {
            String name = be.liveName.length() > 12 ? be.liveName.substring(0, 12) : be.liveName;
            text(poseStack, buffers, name + "  " + be.liveScore, Y1 - 0.045F, 0xFFE9F27A, 0.004F);
        } else if (distance < 24.0 * 24.0) {
            int game = Math.floorMod((int) Math.floor(time / CYCLE), 3);
            int best = be.best(game);
            String top = best > 0 ? "HI " + best + "  " + be.bestBy(game) : (game == 0 ? "SNAKE" : game == 1 ? "BREAKOUT" : "TETRIS");
            text(poseStack, buffers, top, Y1 - 0.06F, 0xFFE9F27A, 0.0055F);
            if (((int) (time * 1.5F) & 1) == 0) {
                text(poseStack, buffers, "INSERT COIN", Y0 + 0.07F, 0xFFFFFFFF, 0.0045F);
            }
        }
        poseStack.popPose();
    }

    // ---- the control panel: a stick and six buttons, as the player works them ----

    /** The buttons (the model's pixels, before the panel's tilt): where each one's ring sits, its colour; and which of
     *  the player's keys (ArcadeScreen.inputBits) presses it - the back row 1 2 and the rage key, the front row 3 4 and start. */
    private static final float[][] BUTTONS = {{7.2F, 2.8F}, {5.2F, 2.8F}, {3.2F, 2.8F}, {7.8F, 1.2F}, {5.8F, 1.2F}, {3.8F, 1.2F}};
    private static final String[] BUTTON_COLOURS = {"lime_concrete", "red_concrete", "blue_concrete", "red_concrete", "blue_concrete", "yellow_concrete"};

    private static TextureAtlasSprite sprite(String block) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.withDefaultNamespace("block/" + block));
    }

    private void panel(ArcadeBlockEntity be, PoseStack poseStack, MultiBufferSource buffers, int light, boolean playing) {
        // (easing towards what's held: quick, but not a jump)
        int in = playing ? be.liveInput : 0;
        long now = System.nanoTime();
        float dt = be.pushedAt == 0 ? 1 : Math.min(0.25F, (now - be.pushedAt) / 1.0E9F);
        be.pushedAt = now;
        float k = 1 - (float) Math.exp(-dt * 30);
        float wantX = ((in & 1) != 0 ? 1 : 0) - ((in & 2) != 0 ? 1 : 0), wantY = ((in & 4) != 0 ? 1 : 0) - ((in & 8) != 0 ? 1 : 0);
        be.stickX += (wantX - be.stickX) * k;
        be.stickY += (wantY - be.stickY) * k;
        for (int i = 0; i < 6; i++) be.pushed[i] += (((in >> (4 + i)) & 1) - be.pushed[i]) * k;

        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS));
        poseStack.pushPose();
        // (the panel's tilt, as the model has it)
        poseStack.translate(8 / 16.0F, 13 / 16.0F, 4.5F / 16.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-22.5F));
        poseStack.translate(-8 / 16.0F, -13 / 16.0F, -4.5F / 16.0F);
        TextureAtlasSprite black = sprite("black_concrete"), chrome = sprite("iron_block"), red = sprite("red_concrete");
        box(poseStack, vc, black, light, 10.6F, 13.0F, 1.4F, 13.0F, 13.25F, 3.8F);
        // the stick: tipped over the way it's pushed (the player's left: +x; away from them: +z)
        poseStack.pushPose();
        poseStack.translate(11.8F / 16, 13.25F / 16, 2.6F / 16);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-22.0F * be.stickX));
        poseStack.mulPose(Axis.XP.rotationDegrees(22.0F * be.stickY));
        poseStack.translate(-11.8F / 16, -13.25F / 16, -2.6F / 16);
        box(poseStack, vc, chrome, light, 11.55F, 13.25F, 2.35F, 12.05F, 15.2F, 2.85F);
        box(poseStack, vc, red, light, 11.1F, 15.1F, 1.9F, 12.5F, 16.1F, 3.3F);
        box(poseStack, vc, red, light, 11.3F, 14.9F, 2.1F, 12.3F, 16.3F, 3.1F);
        poseStack.popPose();
        for (int i = 0; i < 6; i++) {
            float x = BUTTONS[i][0], z = BUTTONS[i][1], down = be.pushed[i] * 0.4F;
            TextureAtlasSprite cap = sprite(BUTTON_COLOURS[i]);
            box(poseStack, vc, chrome, light, x, 13.0F, z, x + 1.6F, 13.2F, z + 1.6F);
            box(poseStack, vc, cap, light, x + 0.25F, 13.2F - down, z + 0.25F, x + 1.35F, 13.7F - down, z + 1.35F);
            box(poseStack, vc, cap, light, x + 0.45F, 13.7F - down, z + 0.45F, x + 1.15F, 13.8F - down, z + 1.15F);
        }
        poseStack.popPose();
    }

    /** A box (the model's pixels), the block's texture on each face. */
    private static void box(PoseStack poseStack, VertexConsumer vc, TextureAtlasSprite sp, int light,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        PoseStack.Pose pose = poseStack.last();
        float a = x0 / 16, b = y0 / 16, c = z0 / 16, d = x1 / 16, e = y1 / 16, f = z1 / 16;
        float u0 = sp.getU(0), u1 = sp.getU((x1 - x0) / 16), v0 = sp.getV(0), v1 = sp.getV((z1 - z0) / 16), vh = sp.getV((y1 - y0) / 16);
        float uz = sp.getU((z1 - z0) / 16);
        quad(pose, vc, light, 0, 1, 0, a, e, c, a, e, f, d, e, f, d, e, c, u0, v0, u1, v1); // (up)
        quad(pose, vc, light, 0, -1, 0, a, b, f, a, b, c, d, b, c, d, b, f, u0, v0, u1, v1); // (down)
        quad(pose, vc, light, 0, 0, -1, d, b, c, a, b, c, a, e, c, d, e, c, u0, v0, u1, vh); // (north)
        quad(pose, vc, light, 0, 0, 1, a, b, f, d, b, f, d, e, f, a, e, f, u0, v0, u1, vh); // (south)
        quad(pose, vc, light, -1, 0, 0, a, b, c, a, b, f, a, e, f, a, e, c, u0, v0, uz, vh); // (west)
        quad(pose, vc, light, 1, 0, 0, d, b, f, d, b, c, d, e, c, d, e, f, u0, v0, uz, vh); // (east)
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, int light, float nx, float ny, float nz,
            float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
            float u0, float v0, float u1, float v1) {
        vertex(pose, vc, light, nx, ny, nz, x0, y0, z0, u0, v1);
        vertex(pose, vc, light, nx, ny, nz, x1, y1, z1, u1, v1);
        vertex(pose, vc, light, nx, ny, nz, x2, y2, z2, u1, v0);
        vertex(pose, vc, light, nx, ny, nz, x3, y3, z3, u0, v0);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, int light, float nx, float ny, float nz, float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /** Puts a game's picture (0xRRGGBB, w x h) into the texture the tube shows. */
    static void showPicture(int[] rgb, int w, int h) {
        if (picture == null || pictureW != w || pictureH != h) {
            if (picture != null) picture.close();
            picture = new net.minecraft.client.renderer.texture.DynamicTexture(w, h, false);
            pictureW = w;
            pictureH = h;
        }
        cellsW = w;
        cellsH = h;
        com.mojang.blaze3d.platform.NativeImage image = picture.getPixels();
        if (image == null) {
            return;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = rgb[y * w + x];
                // (the picture's pixels are ABGR)
                image.setPixelRGBA(x, y, 0xFF000000 | ((c & 255) << 16) | (c & 0xFF00) | ((c >> 16) & 255));
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
