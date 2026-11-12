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
 * Skies painted onto items (ModComponents.ShaderPaint): over each painted pixel of the item's flat model, front, back
 * and edges, that pixel's sky (SkyblockType: Shadered's and ours) - its picture, as its sky blocks show it, but fixed to
 * the item (shaders/core/sky_paint), so it's the sky you picked whatever's behind - through its look (SkyblockPass:
 * normal, inverted, posterised, blueprint, game boy...). Drawn wherever the item is
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
        if (sky < 0 || sky >= skies.length || pass >= passes.length) {
            return null;
        }
        if (shader == null) {
            return null;
        }
        try {
            // (its sky's picture brought up to date first, all of it, as sky liquids and paintings do: a sky nothing else
            // shows isn't drawn every frame, and its picture could be out of date)
            SkyUsage.ensureFresh(com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(skies[sky]));
        } catch (RuntimeException ignored) {
            // (drawn as it is)
        }
        return TYPES.computeIfAbsent(sky, k -> {
            ResourceLocation picture = ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "sky_paint/" + k);
            net.minecraft.client.Minecraft.getInstance().getTextureManager().register(picture, new Picture(skies[k]));
            return Types.of(k, picture);
        });
    }

    // ---- the skies, fixed to the item: shaders/core/sky_paint over each sky's picture ----

    static net.minecraft.client.renderer.ShaderInstance shader;
    private static final java.util.Map<Integer, RenderType> TYPES = new java.util.HashMap<>();
    private static java.lang.reflect.Method pictureOf;

    @net.neoforged.fml.common.EventBusSubscriber(modid = com.example.skyblocks.SkyBlocksMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,
            value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @net.neoforged.bus.api.SubscribeEvent
        public static void registerSkyPaint(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "sky_paint"),
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static RenderType of(int sky, ResourceLocation picture) {
            return create("skyblocks_sky_paint_" + sky, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 2048, false, false, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTextureState(new TextureStateShard(picture, false, false))
                            .setCullState(NO_CULL)
                            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                            .createCompositeState(false));
        }

        private Types() {
            super("", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** A sky's picture as a texture: Shadered's (or ours) for that sky, as its sky blocks use it. */
    private static final class Picture extends net.minecraft.client.renderer.texture.AbstractTexture {
        private final SkyblockType sky;

        Picture(SkyblockType sky) {
            this.sky = sky;
        }

        @Override
        public int getId() {
            try {
                if (pictureOf == null) {
                    pictureOf = ModRenderTypes.class.getDeclaredMethod("getSkyboxTextureId", SkyblockType.class);
                    pictureOf.setAccessible(true);
                }
                return (int) pictureOf.invoke(null, sky);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return 0;
            }
        }

        @Override
        public void load(net.minecraft.server.packs.resources.ResourceManager manager) {}

        @Override
        public void releaseId() {}
    }

    /** A style's name: its sky (and its pass, unless it's the plain one). */
    static Component name(int style) {
        SkyblockType[] skies = skies();
        int sky = ShaderPaintStyles.sky(style), pass = ShaderPaintStyles.pass(style);
        if (style <= 0 || sky < 0 || sky >= skies.length || pass >= SkyblockPass.values().length) {
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
    public static boolean drawTexture(ItemStack stack, BakedModel model, PoseStack pose, @Nullable MultiBufferSource buffers, int light, int overlay) {
        if (buffers == null || stack.isEmpty()) {
            return false;
        }
        ModComponents.ItemTexture tex = stack.get(ModComponents.ITEM_TEXTURE.get());
        ModComponents.ShaderPaint paint = stack.get(ModComponents.SHADER_PAINT.get());
        if (tex == null && paint == null) {
            return false;
        }
        // (painted, not redrawn: its own texture, built the same way - so the painted pixels can be left out of it)
        TextureAtlasSprite sprite = tex == null ? sprite(model) : null;
        if (tex == null && (sprite == null || model.isCustomRenderer())) {
            return false;
        }
        int w = tex != null ? tex.w() : sprite.contents().width(), h = tex != null ? tex.h() : sprite.contents().height();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose p = pose.last();
        float pw = 1.0F / w, ph = 1.0F / h, z0 = 7.5F / 16.0F, z1 = 8.5F / 16.0F;
        boolean fits = paint != null && paint.w() == w && paint.h() == h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = tex != null ? tex.at(x, y) : argb(sprite, x, y);
                if ((c >>> 24) <= 16 || fits && paint.at(x, y) != 0) {
                    continue; // (nothing there; or a sky's painted there, its own faces cover it all)
                }
                c |= 0xFF000000;
                float x0 = x * pw, x1 = (x + 1) * pw, y0 = 1.0F - (y + 1) * ph, y1 = 1.0F - y * ph;
                box(vc, p, x0, y0, z0, x1, y1, z1, c, light, overlay, empty(tex, sprite, x - 1, y, w, h), empty(tex, sprite, x + 1, y, w, h),
                        empty(tex, sprite, x, y + 1, w, h), empty(tex, sprite, x, y - 1, w, h));
            }
        }
        return true;
    }

    private static boolean empty(@Nullable ModComponents.ItemTexture tex, @Nullable TextureAtlasSprite sprite, int x, int y, int w, int h) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return true;
        }
        return ((tex != null ? tex.at(x, y) : argb(sprite, x, y)) >>> 24) <= 16;
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
            // (what's solid, for the edges: its redrawn texture, or its own)
            ModComponents.ItemTexture tex = stack.get(ModComponents.ITEM_TEXTURE.get());
            TextureAtlasSprite sprite = tex == null ? sprite(model) : null;
            java.util.function.BiPredicate<Integer, Integer> solid = tex != null ? (x, y) -> (tex.at(x, y) >>> 24) > 16
                    : sprite != null ? (x, y) -> opaque(sprite, x, y) : (x, y) -> paint.at(x, y) != 0;
            draw(DrawNow.now(buffers), pose.last().pose(), paint, gui ? 0.04F : 0.0012F, solid);
        } finally {
            drawing = false;
        }
    }

    /** The painted pixels over a flat item (its front at z 8.5 / 16, its back at 7.5 / 16), lifted off them by `lift`. */
    static void draw(MultiBufferSource.BufferSource source, Matrix4f m, ModComponents.ShaderPaint paint, float lift,
                     java.util.function.BiPredicate<Integer, Integer> solid) {
        float pw = 1.0F / paint.w(), ph = 1.0F / paint.h();
        float front = 8.5F / 16.0F + lift, back = 7.5F / 16.0F - lift;
        java.util.TreeSet<Integer> has = new java.util.TreeSet<>();
        for (int y = 0; y < paint.h(); y++) {
            for (int x = 0; x < paint.w(); x++) {
                if (paint.at(x, y) != 0) {
                    has.add(paint.at(x, y));
                }
            }
        }
        for (int style : has) {
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
                    // (the front, and the back wound the other way: the skies' render types don't draw a face's back)
                    vertex(vc, m, x0, y0, front, u0, v1, style, paint.w());
                    vertex(vc, m, x1, y0, front, u1, v1, style, paint.w());
                    vertex(vc, m, x1, y1, front, u1, v0, style, paint.w());
                    vertex(vc, m, x0, y1, front, u0, v0, style, paint.w());
                    vertex(vc, m, x0, y1, back, u0, v0, style, paint.w());
                    vertex(vc, m, x1, y1, back, u1, v0, style, paint.w());
                    vertex(vc, m, x1, y0, back, u1, v1, style, paint.w());
                    vertex(vc, m, x0, y0, back, u0, v1, style, paint.w());
                    x = end;
                }
            }
            // the edges: each painted pixel's sides where the item ends (nothing solid next to it)
            float zb = back, zf = front;
            for (int y = 0; y < paint.h(); y++) {
                for (int x = 0; x < paint.w(); x++) {
                    if (paint.at(x, y) != style) {
                        continue;
                    }
                    float x0 = x * pw - lift, x1 = (x + 1) * pw + lift, y0 = 1.0F - (y + 1) * ph - lift, y1 = 1.0F - y * ph + lift;
                    float u0 = x / (float) paint.w(), u1 = (x + 1) / (float) paint.w(), v0 = y / (float) paint.h(), v1 = (y + 1) / (float) paint.h();
                    // (each side carries on the sky at the front's edge, across the item's thickness - as the game's
                    // own item edges stretch their edge pixels: a whole pixel of sky squeezed into a sliver came out
                    // as one blurred colour, a line)
                    float in = 0.25F / paint.w(), inV = 0.25F / paint.h();
                    if (!solid.test(x - 1, y)) {
                        side(vc, m, x0, y0, zb, x0, y0, zf, x0, y1, zf, x0, y1, zb, u0 + in, v0, u0 + in, v1, style, paint.w());
                    }
                    if (!solid.test(x + 1, y)) {
                        side(vc, m, x1, y0, zb, x1, y0, zf, x1, y1, zf, x1, y1, zb, u1 - in, v0, u1 - in, v1, style, paint.w());
                    }
                    if (!solid.test(x, y + 1)) {
                        side(vc, m, x0, y0, zb, x1, y0, zb, x1, y0, zf, x0, y0, zf, u0, v1 - inV, u1, v1 - inV, style, paint.w());
                    }
                    if (!solid.test(x, y - 1)) {
                        side(vc, m, x0, y1, zb, x1, y1, zb, x1, y1, zf, x0, y1, zf, u0, v0 + inV, u1, v0 + inV, style, paint.w());
                    }
                }
            }
            source.endBatch(type);
        }
    }

    /** An edge face, both ways round (whichever side of it you see). */
    private static void side(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float u0, float v0, float u1, float v1, int style, int w) {
        vertex(vc, m, ax, ay, az, u0, v0, style, w);
        vertex(vc, m, bx, by, bz, u1, v0, style, w);
        vertex(vc, m, cx, cy, cz, u1, v1, style, w);
        vertex(vc, m, dx, dy, dz, u0, v1, style, w);
        vertex(vc, m, dx, dy, dz, u0, v1, style, w);
        vertex(vc, m, cx, cy, cz, u1, v1, style, w);
        vertex(vc, m, bx, by, bz, u1, v0, style, w);
        vertex(vc, m, ax, ay, az, u0, v0, style, w);
    }

    static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int style, int w) {
        // (the look in red, the texture's width in green: shaders/core/sky_paint)
        vc.addVertex(m, x, y, z).setUv(u, v).setColor(ShaderPaintStyles.pass(style), Math.min(255, w), 0, 255);
    }

    /** A flat square of one style (the editor's swatches and canvas): x, y .. x + size, y + size on screen. */
    static void square(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h, int style, float u0, float v0, float u1, float v1, int texels) {
        // (both ways round: whichever way a screen's matrix turns it, one of them faces you)
        vertex(vc, m, x, y + h, 0, u0, v1, style, texels);
        vertex(vc, m, x + w, y + h, 0, u1, v1, style, texels);
        vertex(vc, m, x + w, y, 0, u1, v0, style, texels);
        vertex(vc, m, x, y, 0, u0, v0, style, texels);
        vertex(vc, m, x, y, 0, u0, v0, style, texels);
        vertex(vc, m, x + w, y, 0, u1, v0, style, texels);
        vertex(vc, m, x + w, y + h, 0, u1, v1, style, texels);
        vertex(vc, m, x, y + h, 0, u0, v1, style, texels);
    }
}
