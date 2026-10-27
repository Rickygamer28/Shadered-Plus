package com.example.skyblocks.client;

import com.example.skyblocks.NeonSignBlock;
import com.example.skyblocks.NeonSignBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A neon sign's glowing tubes: one quad per letter (a little bigger, for the glow), traced in shaders/core/neon_sign
 * with our own single-stroke font (tools/make_neon_font.py), added on top of what's behind. Flickering signs stutter
 * now and then, pulsing ones breathe, rainbow ones shift colour along the text.
 */
public class NeonSignRenderer implements BlockEntityRenderer<NeonSignBlockEntity> {
    static ShaderInstance shader;
    /** The letters the font has, and how wide each is (font units; letters are 6 tall). */
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz!?.,:-+'/#<>=()_*$&@~%^\";";
    private static final float[] WIDTHS = {4F, 4.0F, 3.89F, 4.0F, 4F, 4F, 4.4F, 4F, 2.7F, 4F, 4F, 3.8F, 4.8F, 4F, 4.4F, 3.9F, 4.6F, 4F, 4.0F, 4.4F, 4.0F, 4.2F, 5F, 4F, 4.2F, 4F, 4.0F, 3.4F, 4F, 3.99F, 4.2F, 4F, 3.98F, 4F, 4.0F, 4.0F, 3.6F, 3.6F, 3.53F, 3.6F, 3.8F, 3.54F, 3.6F, 3.6F, 1F, 2.2F, 3.6F, 2.07F, 4.8F, 3.6F, 3.8F, 3.6F, 3.6F, 2.96F, 3.6F, 3F, 3.6F, 3.6F, 4.6F, 3.6F, 3.6F, 3.6F, 1F, 3.99F, 0.6F, 0.8F, 0.6F, 3.2F, 3.8F, 0.6F, 3.6F, 4.4F, 3.6F, 3.6F, 3.8F, 1.3F, 1.6F, 4F, 3.4F, 4.0F, 3.8F, 4.8F, 3.6F, 4F, 3.6F, 1.8F, 0.8F};
    private static final float GAP = 1.3F, SPACE = 2.8F, MARGIN = 2.4F, LINE = 10.0F;

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(SkyBlocksMod.MOD_ID, "neon_sign"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType NEON = create("skyblocks_neon_sign", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 8192,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public NeonSignRenderer(BlockEntityRendererProvider.Context context) {}

    private static float width(char c) {
        if (c == ' ') {
            return SPACE;
        }
        int i = CHARS.indexOf(c);
        if (i < 0) {
            i = CHARS.indexOf(Character.toUpperCase(c));
        }
        return i < 0 ? SPACE : WIDTHS[i] + GAP;
    }

    private static float lineWidth(String s) {
        float w = 0.0F;
        for (int i = 0; i < s.length(); i++) {
            w += width(s.charAt(i));
        }
        return Math.max(w - GAP, 0.0F);
    }

    /** How brightly it glows now (0..1). */
    private static float glow(NeonSignBlockEntity sign, double t) {
        double seed = (sign.getBlockPos().hashCode() & 1023) * 0.37;
        return switch (sign.mode()) {
            case NeonSignBlockEntity.FLICKER -> {
                double w = Math.floor(t * 12.0 + seed);
                double h = Math.abs(Math.sin(w * 12.9898 + seed) * 43758.5453) % 1.0;
                boolean stutter = Math.sin(t * 0.7 + seed) > 0.55;
                yield (float) (stutter && h < 0.45 ? 0.12 + 0.3 * h : 1.0);
            }
            case NeonSignBlockEntity.PULSE -> (float) (0.4 + 0.6 * (0.5 + 0.5 * Math.sin(t * 2.2 + seed)));
            case NeonSignBlockEntity.BLINK -> (float) (0.06 + 0.94 * smooth(-0.15, 0.15, Math.sin(t * 3.0 + seed)));
            default -> 1.0F;
        };
    }

    private static double smooth(double a, double b, double x) {
        double k = Math.max(0.0, Math.min(1.0, (x - a) / (b - a)));
        return k * k * (3.0 - 2.0 * k);
    }

    /** How many signs facing the same way carry on from this one in a direction (the joined panel's reach). */
    private static int reach(NeonSignBlockEntity sign, Direction d) {
        var level = sign.getLevel();
        if (level == null) {
            return 0;
        }
        Direction f = sign.getBlockState().getValue(NeonSignBlock.FACING);
        int n = 0;
        net.minecraft.core.BlockPos p = sign.getBlockPos().relative(d);
        while (n < 32) {
            var st = level.getBlockState(p);
            if (!(st.getBlock() instanceof NeonSignBlock) || st.getValue(NeonSignBlock.FACING) != f) {
                break;
            }
            n++;
            p = p.relative(d);
        }
        return n;
    }

    /** A rounded dash of tube (code 1 in the shader), from its middle (cx, cy), along x or along y. */
    private static void dash(VertexConsumer vc, Matrix4f m, float cx, float cy, boolean vertical, int r, int g, int b) {
        float L = 1.0F + MARGIN, M = MARGIN;
        if (!vertical) {
            vc.vertex(m, cx - L, cy - M, 0.0F).uv(-L, -M).color(r, g, b, 1).endVertex();
            vc.vertex(m, cx + L, cy - M, 0.0F).uv(L, -M).color(r, g, b, 1).endVertex();
            vc.vertex(m, cx + L, cy + M, 0.0F).uv(L, M).color(r, g, b, 1).endVertex();
            vc.vertex(m, cx - L, cy + M, 0.0F).uv(-L, M).color(r, g, b, 1).endVertex();
        } else {
            vc.vertex(m, cx - M, cy - L, 0.0F).uv(-L, -M).color(r, g, b, 1).endVertex();
            vc.vertex(m, cx + M, cy - L, 0.0F).uv(-L, M).color(r, g, b, 1).endVertex();
            vc.vertex(m, cx + M, cy + L, 0.0F).uv(L, M).color(r, g, b, 1).endVertex();
            vc.vertex(m, cx - M, cy + L, 0.0F).uv(L, -M).color(r, g, b, 1).endVertex();
        }
    }

    @Override
    public void render(NeonSignBlockEntity sign, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        String text = sign.text();
        if (shader == null || text.isBlank() || !sign.getBlockState().hasProperty(NeonSignBlock.FACING)) {
            return;
        }
        double t = LavaLampRenderer.seconds();
        Direction facing = sign.getBlockState().getValue(NeonSignBlock.FACING);
        float bright = glow(sign, t);
        String[] lines = text.split("\\|", 3);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        }));
        poseStack.translate(0.0, 0.0, 0.43); // (just in front of the panel, at the back of the block)
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F)); // (facing out, the way the sign faces)
        float u = sign.size() * 0.016F;
        poseStack.scale(u, u, u);
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(Types.NEON);
        int mode = sign.mode();
        float total = lines.length * LINE;
        float widest = 0.0F;
        int chars = 0;
        for (String line : lines) {
            widest = Math.max(widest, lineWidth(line));
            chars += line.length();
        }
        // scrolling: the text runs right to left across the whole row of joined signs (fading in and out at its ends)
        boolean scroll = mode == NeonSignBlockEntity.SCROLL;
        float winL = -(reach(sign, facing.getClockWise()) + 0.47F) / u, winR = (reach(sign, facing.getCounterClockWise()) + 0.47F) / u;
        float shift = 0.0F;
        if (scroll) {
            float span = widest + (winR - winL) + 6.0F;
            shift = (float) (winR + 3.0F + widest * 0.5F - (t * 9.0) % span);
        }
        // typing: the letters come on one at a time, stay a while, blink twice, and it starts again
        int shown = Integer.MAX_VALUE;
        float typeBright = 1.0F;
        if (mode == NeonSignBlockEntity.TYPING) {
            double per = 0.14, hold = 2.6, cycle = chars * per + hold + 1.2;
            double ph = t % cycle;
            shown = (int) Math.floor(ph / per);
            if (ph > chars * per + hold) {
                typeBright = Math.sin((ph - chars * per - hold) * Math.PI * 2.0 / 0.6) > 0.0 ? 1.0F : 0.08F;
            }
        }
        int index = 0;
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            float x = -lineWidth(line) * 0.5F + shift;
            float base = total * 0.5F - (li + 1) * LINE + 2.5F; // (each line's baseline, the block centred)
            for (int ci = 0; ci < line.length(); ci++, index++) {
                char c = line.charAt(ci);
                float w = width(c);
                if (c != ' ' && c < 128 && index < shown) {
                    int col = sign.colour();
                    if (mode == NeonSignBlockEntity.RAINBOW) {
                        col = Mth.hsvToRgb((float) ((t * 0.12 + (li * 7 + ci) * 0.06) % 1.0), 0.9F, 1.0F);
                    }
                    float k = bright * typeBright;
                    if (scroll) {
                        float mid = x + w * 0.5F;
                        k *= (float) (smooth(winL, winL + 4.0, mid) * smooth(winR, winR - 4.0, mid));
                    }
                    if (k > 0.01F) {
                        int r = (int) (((col >> 16) & 255) * k), g = (int) (((col >> 8) & 255) * k), b = (int) ((col & 255) * k);
                        float gw = w - GAP;
                        float x0 = -MARGIN, x1 = gw + MARGIN, y0 = -2.0F - MARGIN, y1 = 6.5F + MARGIN;
                        quad(vc, m, x + x0, base + y0, x + x1, base + y1, x0, y0, x1, y1, r, g, b, c);
                    }
                }
                x += w;
            }
        }
        // the outline: a frame of tube dashes round the text, lights chasing round it like a marquee
        if (sign.outline()) {
            float hw = scroll ? (winR - winL) * 0.5F - 1.5F : widest * 0.5F + 3.5F;
            float cxm = scroll ? (winR + winL) * 0.5F : 0.0F;
            float hh = total * 0.5F + 2.2F;
            float step = 3.2F;
            int nx = Math.max(1, Math.round(2 * hw / step)), ny = Math.max(1, Math.round(2 * hh / step));
            float sx = 2 * hw / nx, sy = 2 * hh / ny;
            int col0 = sign.colour();
            int k = 0;
            int chase = (int) Math.floor(t * 7.0);
            int perim = 2 * (nx + ny);
            for (int side = 0; side < 4; side++) {
                int n = side % 2 == 0 ? nx : ny;
                for (int i = 0; i < n; i++, k++) {
                    float x, y;
                    boolean vertical = side % 2 == 1;
                    switch (side) {
                        case 0 -> { x = cxm - hw + (i + 0.5F) * sx; y = hh; }
                        case 1 -> { x = cxm + hw; y = hh - (i + 0.5F) * sy; }
                        case 2 -> { x = cxm + hw - (i + 0.5F) * sx; y = -hh; }
                        default -> { x = cxm - hw; y = -hh + (i + 0.5F) * sy; }
                    }
                    int col = mode == NeonSignBlockEntity.RAINBOW ? Mth.hsvToRgb((float) ((t * 0.12 + k / (float) perim) % 1.0), 0.9F, 1.0F) : col0;
                    float lit = Math.floorMod(k - chase, 3) == 0 ? 1.0F : 0.28F;
                    float kk = lit * (mode == NeonSignBlockEntity.TYPING ? 1.0F : bright);
                    dash(vc, m, x, y, vertical, (int) (((col >> 16) & 255) * kk), (int) (((col >> 8) & 255) * kk), (int) ((col & 255) * kk));
                }
            }
        }
        poseStack.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float px0, float py0, float px1, float py1,
                             float u0, float v0, float u1, float v1, int r, int g, int b, int code) {
        vc.vertex(m, px0, py0, 0.0F).uv(u0, v0).color(r, g, b, code).endVertex();
        vc.vertex(m, px1, py0, 0.0F).uv(u1, v0).color(r, g, b, code).endVertex();
        vc.vertex(m, px1, py1, 0.0F).uv(u1, v1).color(r, g, b, code).endVertex();
        vc.vertex(m, px0, py1, 0.0F).uv(u0, v1).color(r, g, b, code).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(NeonSignBlockEntity sign) {
        return sign.size() > 2 || sign.outline() || sign.mode() == NeonSignBlockEntity.SCROLL; // (reaching past its block)
    }
}
