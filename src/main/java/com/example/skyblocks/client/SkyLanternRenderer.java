package com.example.skyblocks.client;

import com.example.skyblocks.ShaderPaintStyles;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SkyLanternBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;

/**
 * The sky lantern's sky - its picture inside the glass, as the infusion altar's paint shows a sky (shaders/core/sky_paint)
 * - and its light on the room round it: every face it can see, tinted the sky's colour, fading with distance. The colour
 * is the sky's picture shrunk to a few pixels and averaged, every few ticks, and blended over a couple of seconds: the
 * picture is the sky as seen the way you're looking, so it changes as you turn - blended, the light holds steady (and
 * settles on the sky's colour all round).
 */
public class SkyLanternRenderer implements BlockEntityRenderer<SkyLanternBlockEntity> {
    abstract static class Types extends RenderType {
        /**
         * A sky's light on the blocks round the lantern: their colours tinted towards it (multiplied: a lit white wall
         * turns the sky's colour, which light added on top of it couldn't do).
         */
        static final TransparencyStateShard TINT = new TransparencyStateShard("skyblocks_tint", () -> {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.DST_COLOR,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        }, () -> {
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        });

        static final RenderType GLOW = create("skyblocks_sky_lantern_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
                    CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TINT)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                            .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** Each sky's colour, as a light (blended over time: see the top), and when it's next looked at. */
    private static final class SkyColour {
        float r = 1, g = 1, b = 1;
        boolean known;
        long next;
    }

    private static final Map<Integer, SkyColour> COLOURS = new HashMap<>();
    private static int readFbo = -1, smallFbo = -1, smallTex = -1;
    private static boolean sampleFailed;
    /** How far its light reaches (blocks), and how strong it is right by it. */
    private static final int REACH = 7;
    private static final float STRENGTH = 0.8F;

    private static final class Lit {
        long at = -1000;
        boolean hanging;
        /** Each lit face: four corners (x y z, from the lantern's block) and how strong the light is at each. */
        final List<float[]> faces = new ArrayList<>();
    }

    private static final Map<SkyLanternBlockEntity, Lit> LIT = new WeakHashMap<>();

    public SkyLanternRenderer(BlockEntityRendererProvider.Context context) {}

    /** The lantern's style (ShaderPaintStyles): its sky and pass, by index; 0 if they aren't skies here. */
    static int style(SkyLanternBlockEntity be) {
        try {
            int sky = SkyblockType.valueOf(be.sky()).ordinal();
            int pass = SkyblockPass.valueOf(be.pass()).ordinal();
            return ShaderPaintStyles.of(sky, pass);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public void render(SkyLanternBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(LanternBlock.HANGING)) {
            return;
        }
        boolean hanging = state.getValue(LanternBlock.HANGING);
        int style = style(be);
        RenderType sky = ShaderPaintRender.type(style);
        if (sky == null) {
            return;
        }
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        // the sky inside the glass: each side of it a window of the sky's picture
        float y0 = (hanging ? 2.0F : 1.0F) / 16.0F, y1 = y0 + 8.0F / 16.0F, a = 5.02F / 16.0F, b = 10.98F / 16.0F;
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = source.getBuffer(sky);
        box(vc, m, a, y0, a, b, y1, b, style);
        source.endBatch(sky);
        // its light on the room
        Lit lit = LIT.computeIfAbsent(be, k -> new Lit());
        long now = level.getGameTime();
        if (now - lit.at > 40 || now < lit.at - 40 || lit.hanging != hanging) {
            light(level, be.getBlockPos(), hanging, lit);
            lit.at = now + Math.floorMod(be.getBlockPos().hashCode(), 20); // (lanterns take turns)
            lit.hanging = hanging;
        }
        if (lit.faces.isEmpty()) {
            return;
        }
        SkyColour col = colour(ShaderPaintStyles.sky(style), now);
        if (!col.known) {
            return;
        }
        VertexConsumer g = source.getBuffer(Types.GLOW);
        PoseStack.Pose p = poseStack.last();
        float flicker = 0.96F + 0.04F * Mth.sin((now + partialTick) * 0.11F + be.getBlockPos().hashCode());
        for (float[] f : lit.faces) {
            for (int c = 0; c < 4; c++) {
                // (white - no change - far off; the sky's colour close by: multiplied onto what's there)
                float w = Mth.clamp(f[12 + c] * flicker, 0.0F, 1.0F);
                g.addVertex(p, f[c * 3], f[c * 3 + 1], f[c * 3 + 2]).setColor(1.0F + (col.r - 1.0F) * w, 1.0F + (col.g - 1.0F) * w, 1.0F + (col.b - 1.0F) * w, 1.0F);
            }
        }
        source.endBatch(Types.GLOW);
    }

    /** A sky's colour as a light: looked at every few ticks, blended in. */
    private static SkyColour colour(int sky, long now) {
        SkyColour c = COLOURS.computeIfAbsent(sky, k -> new SkyColour());
        if (now >= c.next || now < c.next - 100) {
            c.next = now + 5;
            float[] seen = sample(sky);
            if (seen != null) {
                // (a light's colour: the hue, its brightest part full; a grey sky barely tints, a colourful one strongly)
                float peak = Math.max(seen[0], Math.max(seen[1], seen[2]));
                float r = 1, g = 1, b = 1;
                if (peak > 0.02F) {
                    r = seen[0] / peak;
                    g = seen[1] / peak;
                    b = seen[2] / peak;
                    float grey = (r + g + b) / 3.0F;
                    r = Mth.clamp(grey + (r - grey) * 1.35F, 0.0F, 1.0F);
                    g = Mth.clamp(grey + (g - grey) * 1.35F, 0.0F, 1.0F);
                    b = Mth.clamp(grey + (b - grey) * 1.35F, 0.0F, 1.0F);
                }
                float k = c.known ? 0.12F : 1.0F;
                c.r += (r - c.r) * k;
                c.g += (g - c.g) * k;
                c.b += (b - c.b) * k;
                c.known = true;
            }
        }
        return c;
    }

    /**
     * A sky's picture, shrunk to 16 x 16 (the graphics card blits it down) and read back: its average colour (r g b, 0 .. 1),
     * or null if it couldn't be.
     */
    private static float[] sample(int sky) {
        if (sampleFailed) {
            return null;
        }
        int prevRead = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        java.nio.ByteBuffer buf = null;
        try {
            int id = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(ShaderPaintRender.picture(sky)).getId();
            if (id <= 0) {
                return null;
            }
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(id);
            int w = org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
            int h = org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
            if (w <= 0 || h <= 0) {
                return null;
            }
            if (readFbo < 0) {
                readFbo = org.lwjgl.opengl.GL30.glGenFramebuffers();
                smallFbo = org.lwjgl.opengl.GL30.glGenFramebuffers();
                smallTex = com.mojang.blaze3d.platform.GlStateManager._genTexture();
                com.mojang.blaze3d.platform.GlStateManager._bindTexture(smallTex);
                org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA8, 16, 16, 0, org.lwjgl.opengl.GL11.GL_RGBA,
                        org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST);
                org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST);
                org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, smallFbo);
                org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0,
                        org.lwjgl.opengl.GL11.GL_TEXTURE_2D, smallTex, 0);
            }
            if (scissor) {
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            }
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, readFbo);
            org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, id, 0);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, smallFbo);
            org.lwjgl.opengl.GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, 16, 16, org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_LINEAR);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, smallFbo);
            buf = org.lwjgl.system.MemoryUtil.memAlloc(16 * 16 * 4);
            org.lwjgl.opengl.GL11.glReadPixels(0, 0, 16, 16, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buf);
            float r = 0, g = 0, b = 0;
            for (int i = 0; i < 256; i++) {
                r += (buf.get(i * 4) & 255);
                g += (buf.get(i * 4 + 1) & 255);
                b += (buf.get(i * 4 + 2) & 255);
            }
            return new float[] {r / 256 / 255, g / 256 / 255, b / 256 / 255};
        } catch (RuntimeException e) {
            sampleFailed = true;
            SkyBlocksMod.LOGGER.warn("Shadered+ sky lantern: couldn't read its sky's colour; its light stays white", e);
            return null;
        } finally {
            if (buf != null) {
                org.lwjgl.system.MemoryUtil.memFree(buf);
            }
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, prevRead);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            if (scissor) {
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            }
        }
    }

    /** A box of sky (all six sides, each the whole picture), seen from inside or out. */
    private static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int style) {
        float[][][] faces = {
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}},
                {{x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}},
                {{x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}},
                {{x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}},
                {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (float[][] f : faces) {
            for (int i = 0; i < 4; i++) {
                ShaderPaintRender.vertex(vc, m, f[i][0], f[i][1], f[i][2], uv[i][0], uv[i][1], style, 16);
            }
        }
    }

    /**
     * Its light: every face of a block round it that faces it and that it can see, fading with distance (worked out at
     * each corner, so it fades smoothly across the faces).
     */
    private static void light(Level level, BlockPos pos, boolean hanging, Lit lit) {
        lit.faces.clear();
        Vec3 centre = Vec3.atLowerCornerOf(pos).add(0.5, hanging ? 0.38 : 0.31, 0.5);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(), next = new BlockPos.MutableBlockPos();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dy = -REACH; dy <= REACH; dy++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    if (dx * dx + dy * dy + dz * dz > (REACH + 1) * (REACH + 1) || dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState st = level.getBlockState(at);
                    if (st.isAir() || !st.isSolidRender(level, at)) {
                        continue;
                    }
                    for (Direction d : Direction.values()) {
                        next.setWithOffset(at, d);
                        if (level.getBlockState(next).isSolidRender(level, next)) {
                            continue;
                        }
                        Vec3 mid = Vec3.atCenterOf(at).add(d.getStepX() * 0.5, d.getStepY() * 0.5, d.getStepZ() * 0.5);
                        Vec3 to = centre.subtract(mid);
                        if (to.x * d.getStepX() + to.y * d.getStepY() + to.z * d.getStepZ() <= 0.05) {
                            continue; // (facing away)
                        }
                        BlockHitResult hit = level.clip(new ClipContext(centre, mid.add(d.getStepX() * 0.02, d.getStepY() * 0.02, d.getStepZ() * 0.02),
                                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
                        if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(at) && !hit.getBlockPos().equals(pos)) {
                            continue; // (something in the way)
                        }
                        lit.faces.add(face(at, d, pos, centre));
                    }
                }
            }
        }
    }

    private static float[] face(BlockPos at, Direction d, BlockPos lantern, Vec3 centre) {
        float o = 0.003F, x0 = at.getX(), y0 = at.getY(), z0 = at.getZ();
        float[][] c = switch (d) {
            case UP -> new float[][] {{x0, y0 + 1 + o, z0}, {x0 + 1, y0 + 1 + o, z0}, {x0 + 1, y0 + 1 + o, z0 + 1}, {x0, y0 + 1 + o, z0 + 1}};
            case DOWN -> new float[][] {{x0, y0 - o, z0}, {x0, y0 - o, z0 + 1}, {x0 + 1, y0 - o, z0 + 1}, {x0 + 1, y0 - o, z0}};
            case NORTH -> new float[][] {{x0, y0, z0 - o}, {x0, y0 + 1, z0 - o}, {x0 + 1, y0 + 1, z0 - o}, {x0 + 1, y0, z0 - o}};
            case SOUTH -> new float[][] {{x0, y0, z0 + 1 + o}, {x0 + 1, y0, z0 + 1 + o}, {x0 + 1, y0 + 1, z0 + 1 + o}, {x0, y0 + 1, z0 + 1 + o}};
            case WEST -> new float[][] {{x0 - o, y0, z0}, {x0 - o, y0, z0 + 1}, {x0 - o, y0 + 1, z0 + 1}, {x0 - o, y0 + 1, z0}};
            case EAST -> new float[][] {{x0 + 1 + o, y0, z0}, {x0 + 1 + o, y0 + 1, z0}, {x0 + 1 + o, y0 + 1, z0 + 1}, {x0 + 1 + o, y0, z0 + 1}};
        };
        float[] f = new float[16];
        for (int i = 0; i < 4; i++) {
            float cx = c[i][0], cy = c[i][1], cz = c[i][2];
            double dist = Math.sqrt((cx - centre.x) * (cx - centre.x) + (cy - centre.y) * (cy - centre.y) + (cz - centre.z) * (cz - centre.z));
            float fade = (float) Math.max(0.0, 1.0 - dist / (REACH + 0.5));
            f[i * 3] = cx - lantern.getX();
            f[i * 3 + 1] = cy - lantern.getY();
            f[i * 3 + 2] = cz - lantern.getZ();
            f[12 + i] = fade * (0.35F + 0.65F * fade) * STRENGTH;
        }
        return f;
    }

    /** Drawn even when the lantern's own bit of the world is out of sight: its light on the walls you can see stays on. */
    @Override
    public boolean shouldRenderOffScreen(SkyLanternBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(SkyLanternBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(REACH + 1);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
