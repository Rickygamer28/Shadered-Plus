package com.example.skyblocks.client;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Shaders painted onto items (ModComponents.ShaderPaint): over each painted pixel of the item's flat model, front and
 * back, a little quad drawn with shaders/core/shader_paint in that pixel's style. Drawn wherever the item is
 * (mixin/ItemRendererPaintMixin: after the item's own model) - in hand, in the inventory, dropped, on the altar.
 */
public final class ShaderPaintRender {
    static ShaderInstance shader;

    private ShaderPaintRender() {}

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerShaderPaint(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "shader_paint"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType PAINT = create("skyblocks_shader_paint", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 8192,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setCullState(NO_CULL)
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(false));

        static RenderType live(int style, ResourceLocation picture) {
            return create("skyblocks_shader_paint_" + style, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 2048, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTextureState(new TextureStateShard(picture, false, false))
                            .setCullState(NO_CULL)
                            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                            .createCompositeState(false));
        }

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** The item's texture: its first quad's sprite (an item's flat model: its layer0). */
    @Nullable
    static TextureAtlasSprite sprite(BakedModel model) {
        try {
            List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create(42L), ModelData.EMPTY, null);
            if (!quads.isEmpty()) {
                return quads.get(0).getSprite();
            }
        } catch (RuntimeException ignored) {
            // (a model that needs a world: nothing to paint on)
        }
        return null;
    }

    private static Field imageField;

    /** The sprite's pixels (as loaded), or null. */
    @Nullable
    static NativeImage image(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        try {
            if (imageField == null) {
                for (Field f : SpriteContents.class.getDeclaredFields()) {
                    if (f.getType() == NativeImage.class) {
                        f.setAccessible(true);
                        imageField = f;
                        break;
                    }
                }
            }
            return imageField == null ? null : (NativeImage) imageField.get(contents);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** A sprite pixel's colour (ARGB; 0 if unknown). */
    static int argb(TextureAtlasSprite sprite, int x, int y) {
        NativeImage img = image(sprite);
        if (img == null || x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
            return 0;
        }
        int abgr = img.getPixelRGBA(x, y);
        return (abgr & 0xFF00FF00) | (abgr & 0xFF) << 16 | (abgr >> 16) & 0xFF;
    }

    static boolean opaque(TextureAtlasSprite sprite, int x, int y) {
        return (argb(sprite, x, y) >>> 24) > 16;
    }

    private static boolean drawing;
    private static final RenderType[] LIVE_TYPES = new RenderType[com.example.skyblocks.ShaderPaintStyles.count()];

    /** What a style's drawn with: shader paint's own shader, or (a live style) that over its real shader's picture. */
    static RenderType type(int style) {
        if (!ShaderCapture.live(style)) {
            return Types.PAINT;
        }
        ResourceLocation picture = ShaderCapture.texture(style); // (also: keep it drawn)
        if (LIVE_TYPES[style] == null) {
            LIVE_TYPES[style] = Types.live(style, picture);
        }
        return LIVE_TYPES[style];
    }

    /** Called after an item's model is drawn (pose: the model's own 0 .. 1 cube); gui: in a screen (flat, far from the eye). */
    public static void afterModel(ItemStack stack, BakedModel model, PoseStack pose, @Nullable MultiBufferSource buffers, boolean gui) {
        if (drawing || buffers == null || shader == null || stack.isEmpty()) {
            return;
        }
        ModComponents.ShaderPaint paint = stack.get(ModComponents.SHADER_PAINT.get());
        if (paint == null) {
            return;
        }
        drawing = true;
        try {
            draw(DrawNow.now(buffers), pose.last().pose(), paint, gui ? 0.04F : 0.0012F);
        } finally {
            drawing = false;
        }
    }

    /** The painted pixels over a flat item (its front at z 8.5 / 16, its back at 7.5 / 16), lifted off them by `lift`. */
    static void draw(MultiBufferSource.BufferSource source, Matrix4f m, ModComponents.ShaderPaint paint, float lift) {
        float pw = 1.0F / paint.w(), ph = 1.0F / paint.h();
        float front = 8.5F / 16.0F + lift, back = 7.5F / 16.0F - lift;
        boolean[] has = new boolean[256];
        for (byte b : paint.pixels()) {
            has[b & 255] = true;
        }
        for (int style = 1; style < 256; style++) {
            if (!has[style] || style >= com.example.skyblocks.ShaderPaintStyles.count()) {
                continue;
            }
            RenderType type = type(style);
            VertexConsumer vc = source.getBuffer(type);
            for (int y = 0; y < paint.h(); y++) {
                int x = 0;
                while (x < paint.w()) {
                    if (paint.at(x, y) != style) {
                        x++;
                        continue;
                    }
                    int end = x + 1;
                    while (end < paint.w() && paint.at(end, y) == style) {
                        end++;
                    }
                    float x0 = x * pw, x1 = end * pw, y0 = 1.0F - (y + 1) * ph, y1 = 1.0F - y * ph;
                    float u0 = x / (float) paint.w(), u1 = end / (float) paint.w(), v0 = y / (float) paint.h(), v1 = (y + 1) / (float) paint.h();
                    for (float z : new float[] {front, back}) {
                        vertex(vc, m, x0, y0, z, u0, v1, style, paint.w());
                        vertex(vc, m, x1, y0, z, u1, v1, style, paint.w());
                        vertex(vc, m, x1, y1, z, u1, v0, style, paint.w());
                        vertex(vc, m, x0, y1, z, u0, v0, style, paint.w());
                    }
                    x = end;
                }
            }
            source.endBatch(type);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int style, int w) {
        vc.addVertex(m, x, y, z).setUv(u, v).setColor(style, Math.min(255, w), 0, 255);
    }

    /** A flat square of one style (the editor's swatches and canvas): x, y .. x + size, y + size on screen. */
    static void square(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h, int style, float u0, float v0, float u1, float v1, int texels) {
        vc.addVertex(m, x, y + h, 0).setUv(u0, v1).setColor(style, Math.min(255, texels), 0, 255);
        vc.addVertex(m, x + w, y + h, 0).setUv(u1, v1).setColor(style, Math.min(255, texels), 0, 255);
        vc.addVertex(m, x + w, y, 0).setUv(u1, v0).setColor(style, Math.min(255, texels), 0, 255);
        vc.addVertex(m, x, y, 0).setUv(u0, v0).setColor(style, Math.min(255, texels), 0, 255);
    }
}
