package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

/**
 * The singularity's and the whirlpool's skins besides their own (shaders/core/storage_core): a box round the middle of
 * the block (or filling its chamber or tank), traced through in 3D - the Dyson sphere, the tesseract, the item galaxy
 * (the singularity's), the water planet, the bubbles, the fluid helix (the whirlpool's) - in the colours of what they
 * hold (each item's or fluid's picture, averaged), drawn at once with them handed to the shader.
 */
public final class StorageSkins {
    public static final int DYSON = 0, TESSERACT = 1, GALAXY = 2, PLANET = 3, BUBBLES = 4, HELIX = 5, LAYERS = 6;
    static ShaderInstance shader;
    private static final float[][] COLS = new float[10][4];
    /** (the item galaxy's items: where each one's picture is on the block atlas - u0 v0 u1 v1; none: all 0) */
    private static final float[][] SPRITES = new float[10][4];
    private static final float[][] TOPS = new float[10][4]; // (a block's top: its item drawn as a little cube)
    private static int count, glowBits;
    /** (The layers: which of the fluids are gases, hanging from the top - a bit each; set just before drawing them.) */
    public static int gasBits;

    private StorageSkins() {}

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "storage_core"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType SKIN = create("skyblocks_storage_skin", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setTexturingState(new TexturingStateShard("skyblocks_storage_skin_colours", StorageSkins::colours, () -> {}))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private static void colours() {
        if (shader == null) {
            return;
        }
        for (int i = 0; i < COLS.length; i++) {
            shader.safeGetUniform("C" + i).set(COLS[i][0], COLS[i][1], COLS[i][2], COLS[i][3]);
        }
        for (int i = 0; i < SPRITES.length; i++) {
            shader.safeGetUniform("U" + i).set(SPRITES[i][0], SPRITES[i][1], SPRITES[i][2], SPRITES[i][3]);
            shader.safeGetUniform("V" + i).set(TOPS[i][0], TOPS[i][1], TOPS[i][2], TOPS[i][3]);
        }
        shader.safeGetUniform("Info").set((float) count, (float) glowBits, (float) gasBits, 0.0F);
    }

    public static boolean ready() {
        return shader != null;
    }

    /**
     * A skin, drawn now: in a box round (0.5, 0.5, 0.5) of the pose, {@code half} blocks each way; its colours (RGB) and
     * how much of the whole each is; which of them glow (a bit each).
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int skin, float full, boolean chamber, float half, int[] rgb, float[] shares, int glow) {
        draw(pose, buffers, skin, full, chamber, half, rgb, shares, glow, null);
    }

    /** As above, with the items' pictures for the item galaxy (where each is on the block atlas: u0 v0 u1 v1). */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int skin, float full, boolean chamber, float half, int[] rgb, float[] shares, int glow,
                            float[][] sprites) {
        if (shader == null || rgb.length == 0) {
            return;
        }
        for (int i = 0; i < SPRITES.length; i++) {
            float[] sp = sprites != null && i < sprites.length ? sprites[i] : null;
            for (int k = 0; k < 4; k++) {
                SPRITES[i][k] = sp != null ? sp[k] : 0.0F;
                TOPS[i][k] = sp != null && sp.length >= 8 ? sp[4 + k] : 0.0F;
            }
        }
        count = Math.min(rgb.length, COLS.length);
        glowBits = glow;
        for (int i = 0; i < COLS.length; i++) {
            int c = i < count ? rgb[i] : 0;
            COLS[i][0] = (c >> 16 & 255) / 255.0F;
            COLS[i][1] = (c >> 8 & 255) / 255.0F;
            COLS[i][2] = (c & 255) / 255.0F;
            COLS[i][3] = i < count && i < shares.length ? shares[i] : 0.0F;
        }
        Matrix4f m = pose.last().pose();
        org.joml.Vector3f centre = m.transformPosition(0.5F, 0.5F, 0.5F, new org.joml.Vector3f());
        float ex = -centre.x, ey = -centre.y, ez = -centre.z;
        MultiBufferSource.BufferSource now = DrawNow.now(buffers);
        VertexConsumer vc = now.getBuffer(Types.SKIN);
        int r = skin & 255, g = Math.round(Math.max(0.0F, Math.min(1.0F, full)) * 255), b = chamber ? 255 : 0;
        // (the box's faces turned away from the eye: the ray's traced from the eye through them - see PowerCoreRenderer)
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        float[] towards = {-ex - half, ex - half, -ey - half, ey - half, -ez - half, ez - half};
        for (int f = 0; f < 6; f++) {
            if (towards[f] > 0) {
                continue;
            }
            for (int c : faces[f]) {
                float x = ((c & 1) * 2 - 1) * half, y = (((c >> 1) & 1) * 2 - 1) * half, z = (((c >> 2) & 1) * 2 - 1) * half;
                vc.addVertex(m, 0.5F + x, 0.5F + y, 0.5F + z).setColor(r, g, b, 255).setUv(c, half);
            }
        }
        now.endBatch(Types.SKIN);
    }

    // ---- colours: each item's or fluid's picture, averaged ----

    private static final Map<ResourceLocation, Integer> PICTURES = new HashMap<>();
    private static final Map<Item, Integer> ITEMS = new HashMap<>();
    private static final Map<Fluid, Integer> FLUIDS = new HashMap<>();

    /** A picture's colour: its opaque pixels, averaged (and a little brighter - the average of a texture's dull). */
    private static int picture(ResourceLocation sprite, int fallback) {
        return PICTURES.computeIfAbsent(sprite, rl -> {
            ResourceLocation file = rl.withPath("textures/" + rl.getPath() + ".png");
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(file);
                if (res.isEmpty()) {
                    return fallback;
                }
                try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    long r = 0, g = 0, b = 0, n = 0;
                    int w = img.getWidth(), h = Math.min(img.getHeight(), img.getWidth()); // (an animation's first frame)
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int abgr = img.getPixelRGBA(x, y);
                            if ((abgr >>> 24) > 100) {
                                r += abgr & 255;
                                g += abgr >> 8 & 255;
                                b += abgr >> 16 & 255;
                                n++;
                            }
                        }
                    }
                    if (n == 0) {
                        return fallback;
                    }
                    int cr = (int) (r / n), cg = (int) (g / n), cb = (int) (b / n);
                    int max = Math.max(cr, Math.max(cg, cb));
                    float lift = max < 1 ? 1.0F : Math.min(1.35F, 235.0F / max);
                    return Math.min(255, Math.round(cr * lift)) << 16 | Math.min(255, Math.round(cg * lift)) << 8 | Math.min(255, Math.round(cb * lift));
                }
            } catch (Exception e) {
                return fallback;
            }
        });
    }

    public static int itemColour(ItemStack stack) {
        return ITEMS.computeIfAbsent(stack.getItem(), item -> {
            int fallback = 0x808080 | (item.hashCode() & 0x7F7F7F);
            try {
                var model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
                return picture(model.getParticleIcon().contents().name(), fallback);
            } catch (RuntimeException e) {
                return fallback;
            }
        });
    }

    public static int fluidColour(FluidStack fluid) {
        return FLUIDS.computeIfAbsent(fluid.getFluid(), f -> {
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(f);
            int tint = ext.getTintColor(fluid);
            int base = picture(ext.getStillTexture(fluid), 0xFFFFFF);
            int r = (base >> 16 & 255) * (tint >> 16 & 255) / 255, g = (base >> 8 & 255) * (tint >> 8 & 255) / 255, b = (base & 255) * (tint & 255) / 255;
            return r << 16 | g << 8 | b;
        });
    }

    /**
     * Where each item's picture is on the block atlas (up to ten): u0 v0 u1 v1 - and, for one that shows as a block (its
     * item model a cube), its side's picture then its top's, so it's drawn as a little cube.
     */
    public static float[][] itemSprites(List<ItemStack> items) {
        int n = Math.min(10, items.size());
        float[][] out = new float[n][];
        for (int i = 0; i < n; i++) {
            try {
                ItemStack stack = items.get(i);
                var model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
                var sp = model.getParticleIcon();
                out[i] = new float[] {sp.getU0(), sp.getV0(), sp.getU1(), sp.getV1()};
                if (model.isGui3d() && stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                    var state = bi.getBlock().defaultBlockState();
                    var bm = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
                    var rand = net.minecraft.util.RandomSource.create(42L);
                    var side = faceSprite(bm.getQuads(state, net.minecraft.core.Direction.NORTH, rand), sp);
                    var top = faceSprite(bm.getQuads(state, net.minecraft.core.Direction.UP, rand), side);
                    out[i] = new float[] {side.getU0(), side.getV0(), side.getU1(), side.getV1(), top.getU0(), top.getV0(), top.getU1(), top.getV1()};
                }
            } catch (RuntimeException e) {
                out[i] = null;
            }
        }
        return out;
    }

    private static net.minecraft.client.renderer.texture.TextureAtlasSprite faceSprite(List<net.minecraft.client.renderer.block.model.BakedQuad> quads,
                                                                                         net.minecraft.client.renderer.texture.TextureAtlasSprite or) {
        return quads.isEmpty() ? or : quads.get(0).getSprite();
    }

    /** Where a fluid's still picture is on the block atlas: u0 v0 u1 v1 (the fluid helix flows with it). */
    public static float[] fluidSprite(FluidStack fluid) {
        try {
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
            var sp = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
            return new float[] {sp.getU0(), sp.getV0(), sp.getU1(), sp.getV1()};
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** (the colours of a list of items, up to ten) */
    public static int[] itemColours(List<ItemStack> items) {
        int n = Math.min(10, items.size());
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = itemColour(items.get(i));
        }
        return out;
    }
}
