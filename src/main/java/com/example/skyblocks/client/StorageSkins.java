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
    public static final int DYSON = 0, TESSERACT = 1, GALAXY = 2, PLANET = 3, BUBBLES = 4, HELIX = 5;
    static ShaderInstance shader;
    private static final float[][] COLS = new float[10][4];
    private static int count, glowBits;

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
        shader.safeGetUniform("Info").set((float) count, (float) glowBits, 0.0F, 0.0F);
    }

    public static boolean ready() {
        return shader != null;
    }

    /**
     * A skin, drawn now: in a box round (0.5, 0.5, 0.5) of the pose, {@code half} blocks each way; its colours (RGB) and
     * how much of the whole each is; which of them glow (a bit each).
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int skin, float full, boolean chamber, float half, int[] rgb, float[] shares, int glow) {
        if (shader == null || rgb.length == 0) {
            return;
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
