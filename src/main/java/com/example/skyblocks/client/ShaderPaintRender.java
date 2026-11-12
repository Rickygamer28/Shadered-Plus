package com.example.skyblocks.client;

import com.example.skyblocks.ModComponents;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.ModRenderTypes;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.example.skyblocks.ShaderPaintStyles;
import org.joml.Matrix4f;

/**
 * Skies painted onto items (ModComponents.ShaderPaint): over each painted pixel of the item's flat model, front and
 * back, a little quad drawn as Shadered draws its sky blocks - that pixel's sky (SkyblockType: Shadered's and ours),
 * through its pass (SkyblockPass: normal, inverted, posterised, blueprint, game boy...). Drawn wherever the item is
 * (mixin/ItemRendererPaintMixin: after the item's own model) - in hand, in the inventory, dropped, on the altar.
 */
public final class ShaderPaintRender {
    private ShaderPaintRender() {}

    /** The skies, in Shadered's order (ours added to its list). */
    static SkyblockType[] skies() {
        return SkyblockType.values();
    }

    /** What a painted pixel's drawn with: its sky's render type, through its pass; null if there's no such sky here. */
    @Nullable
    static RenderType type(int style) {
        if (style <= 0) {
            return null;
        }
        SkyblockType[] skies = skies();
        int sky = ShaderPaintStyles.sky(style), pass = ShaderPaintStyles.pass(style);
        SkyblockPass[] passes = SkyblockPass.values();
        if (sky >= skies.length || pass >= passes.length) {
            return null;
        }
        try {
            return ModRenderTypes.getSkyblockRenderType(skies[sky], passes[pass]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A style's name: its sky (and its pass, unless it's the plain one). */
    static Component name(int style) {
        SkyblockType[] skies = skies();
        int sky = ShaderPaintStyles.sky(style), pass = ShaderPaintStyles.pass(style);
        if (style <= 0 || sky >= skies.length || pass >= SkyblockPass.values().length) {
            return Component.literal("?");
        }
        Component name = skies[sky].getDisplayName();
        return pass == 0 ? name : Component.empty().append(name).append(" (").append(SkyblockPass.values()[pass].getDisplayName()).append(")");
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
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "textures/misc/white.png");

    /**
     * Instead of the item's own model, the texture drawn for it at the altar, if it has one: each pixel a little block
     * (its front and back, and its sides where nothing's next to it), as Minecraft builds a flat item from its texture.
     * False: it hasn't (draw its own).
     */
    public static boolean drawTexture(ItemStack stack, PoseStack pose, @Nullable MultiBufferSource buffers, int light, int overlay) {
        if (buffers == null || stack.isEmpty()) {
            return false;
        }
        ModComponents.ItemTexture tex = stack.get(ModComponents.ITEM_TEXTURE.get());
        if (tex == null) {
            return false;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose p = pose.last();
        float pw = 1.0F / tex.w(), ph = 1.0F / tex.h(), z0 = 7.5F / 16.0F, z1 = 8.5F / 16.0F;
        for (int y = 0; y < tex.h(); y++) {
            for (int x = 0; x < tex.w(); x++) {
                int c = tex.at(x, y);
                if ((c >>> 24) <= 16) {
                    continue;
                }
                c |= 0xFF000000;
                float x0 = x * pw, x1 = (x + 1) * pw, y0 = 1.0F - (y + 1) * ph, y1 = 1.0F - y * ph;
                box(vc, p, x0, y0, z0, x1, y1, z1, c, light, overlay, (tex.at(x - 1, y) >>> 24) <= 16, (tex.at(x + 1, y) >>> 24) <= 16,
                        (tex.at(x, y + 1) >>> 24) <= 16, (tex.at(x, y - 1) >>> 24) <= 16);
            }
        }
        return true;
    }

    private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int c, int light, int overlay,
                            boolean west, boolean east, boolean down, boolean up) {
        face(vc, p, c, light, overlay, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(vc, p, c, light, overlay, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        if (west) face(vc, p, c, light, overlay, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        if (east) face(vc, p, c, light, overlay, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        if (down) face(vc, p, c, light, overlay, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        if (up) face(vc, p, c, light, overlay, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
    }

    private static void face(VertexConsumer vc, PoseStack.Pose p, int c, int light, int overlay, float nx, float ny, float nz, float... v) {
        for (int i = 0; i < 12; i += 3) {
            vc.addVertex(p, v[i], v[i + 1], v[i + 2]).setColor(c).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
        }
    }

    /** Called after an item's model is drawn (pose: the model's own 0 .. 1 cube); gui: in a screen (flat, far from the eye). */
    public static void afterModel(ItemStack stack, BakedModel model, PoseStack pose, @Nullable MultiBufferSource buffers, boolean gui) {
        if (drawing || buffers == null || stack.isEmpty()) {
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
            if (!has[style]) {
                continue;
            }
            RenderType type = type(style);
            if (type == null) {
                continue;
            }
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
        // (the skies' render types take an entity's vertex: the sky itself is worked out from where it is on screen)
        vc.addVertex(m, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(0, 0, 1);
    }

    /** A flat square of one style (the editor's swatches and canvas): x, y .. x + size, y + size on screen. */
    static void square(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h, int style, float u0, float v0, float u1, float v1, int texels) {
        vertex(vc, m, x, y + h, 0, u0, v1, style, texels);
        vertex(vc, m, x + w, y + h, 0, u1, v1, style, texels);
        vertex(vc, m, x + w, y, 0, u1, v0, style, texels);
        vertex(vc, m, x, y, 0, u0, v0, style, texels);
    }
}
