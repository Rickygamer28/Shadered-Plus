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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
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

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "neon_sign"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType NEON = create("skyblocks_neon_sign", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 8192,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
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
            default -> 1.0F;
        };
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
        float total = lines.length * LINE;
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            float x = -lineWidth(line) * 0.5F;
            float base = total * 0.5F - (li + 1) * LINE + 2.5F; // (each line's baseline, the block centred)
            for (int ci = 0; ci < line.length(); ci++) {
                char c = line.charAt(ci);
                float w = width(c);
                if (c != ' ' && c < 128) {
                    int col = sign.colour();
                    if (sign.mode() == NeonSignBlockEntity.RAINBOW) {
                        col = Mth.hsvToRgb((float) ((t * 0.12 + (li * 7 + ci) * 0.06) % 1.0), 0.9F, 1.0F);
                    }
                    int r = (int) (((col >> 16) & 255) * bright), g = (int) (((col >> 8) & 255) * bright), b = (int) ((col & 255) * bright);
                    float gw = w - GAP;
                    float x0 = -MARGIN, x1 = gw + MARGIN, y0 = -2.0F - MARGIN, y1 = 6.5F + MARGIN;
                    quad(vc, m, x + x0, base + y0, x + x1, base + y1, x0, y0, x1, y1, r, g, b, c);
                }
                x += w;
            }
        }
        poseStack.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float px0, float py0, float px1, float py1,
                             float u0, float v0, float u1, float v1, int r, int g, int b, int code) {
        vc.addVertex(m, px0, py0, 0.0F).setUv(u0, v0).setColor(r, g, b, code);
        vc.addVertex(m, px1, py0, 0.0F).setUv(u1, v0).setColor(r, g, b, code);
        vc.addVertex(m, px1, py1, 0.0F).setUv(u1, v1).setColor(r, g, b, code);
        vc.addVertex(m, px0, py1, 0.0F).setUv(u0, v1).setColor(r, g, b, code);
    }

    @Override
    public boolean shouldRenderOffScreen(NeonSignBlockEntity sign) {
        return sign.size() > 2; // (big text reaches past its block)
    }
}
