package com.example.skyblocks.client;

import com.example.skyblocks.EffectBlockEntity;
import com.example.skyblocks.ModItems;
import com.example.skyblocks.ShaderPaintStyles;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * The real shader blocks as paint (ShaderPaintStyles from LIVE on): each one, while something painted with it is on
 * screen, drawn by its own renderer - its own shader - into a little picture of its own every frame (an effect block
 * running that effect, face on; the other blocks as their items, zoomed in on the shader part), which the painted
 * pixels then show (shaders/core/shader_paint, its live styles: the picture as a texture, skyblocks:shader_capture/N).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ShaderCapture {
    private static final int SIZE = 128;
    /** For each live style: the picture, its texture's name, when it was last wanted (ms). */
    private static final RenderTarget[] TARGETS = new RenderTarget[ShaderPaintStyles.count()];
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[ShaderPaintStyles.count()];
    private static final long[] WANTED = new long[ShaderPaintStyles.count()];

    /** The non-effect live styles: their block's item, and how far to zoom in (on the middle), and up or down (blocks). */
    private record Item(Supplier<? extends ItemLike> item, float zoom, float up) {}

    private static final Item[] ITEMS = {
            new Item(ModItems.KALEIDOSCOPE, 1.25F, 0.0F), new Item(ModItems.BLACK_HOLE, 1.5F, 0.0F), new Item(ModItems.PLASMA_BALL, 2.0F, -0.08F),
            new Item(ModItems.LAVA_LAMP, 2.4F, 0.0F), new Item(ModItems.LAVA_PILLAR, 1.6F, 0.0F), new Item(ModItems.FIRE_TORNADO, 1.5F, 0.0F),
            new Item(ModItems.JELLYFISH_TANK, 1.4F, 0.0F), new Item(ModItems.SINGULARITY, 1.6F, 0.0F), new Item(ModItems.WEATHER_GLOBE, 2.0F, -0.08F),
            new Item(ModItems.PLANETARIUM, 1.4F, 0.0F), new Item(ModItems.DISCO_BALL, 1.7F, 0.0F)};

    private ShaderCapture() {}

    public static boolean live(int style) {
        return style >= ShaderPaintStyles.LIVE && style < ShaderPaintStyles.count();
    }

    /** The picture of a live style, as a texture (asked for: it's kept drawn for a while). */
    public static ResourceLocation texture(int style) {
        WANTED[style] = System.currentTimeMillis();
        if (TEXTURES[style] == null) {
            ResourceLocation name = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "shader_capture/" + style);
            Minecraft.getInstance().getTextureManager().register(name, new Picture(style));
            TEXTURES[style] = name;
        }
        return TEXTURES[style];
    }

    private static RenderTarget target(int style) {
        if (TARGETS[style] == null) {
            TARGETS[style] = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
            TARGETS[style].setClearColor(0, 0, 0, 0);
            TARGETS[style].setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
        }
        return TARGETS[style];
    }

    /** A live style's picture as a texture: the picture's own. */
    private static final class Picture extends AbstractTexture {
        private final int style;

        Picture(int style) {
            this.style = style;
        }

        @Override
        public int getId() {
            return target(style).getColorTextureId();
        }

        @Override
        public void load(ResourceManager manager) {}

        @Override
        public void releaseId() {}
    }

    private static int dumped = -1;

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || com.example.skyblocks.client.iris.IrisCompat.renderingShadowPass()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean test = RenderTest.ON && dumped < 0 && mc.level.getGameTime() > 200;
        boolean any = test;
        for (int s = ShaderPaintStyles.LIVE; s < ShaderPaintStyles.count() && !any; s++) {
            any = now - WANTED[s] < 2000;
        }
        if (!any) {
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        mv.translate(0, 0, -11000);
        RenderSystem.applyModelViewMatrix();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        try {
            for (int s = ShaderPaintStyles.LIVE; s < ShaderPaintStyles.count(); s++) {
                if (!test && now - WANTED[s] >= 2000) {
                    continue;
                }
                RenderTarget t = target(s);
                t.clear(Minecraft.ON_OSX);
                t.bindWrite(true);
                try {
                    draw(mc, s, buffers);
                } catch (RuntimeException e) {
                    // (a block that can't draw itself here: its picture stays clear)
                }
                buffers.endBatch();
                if (test) {
                    dump(t, ShaderPaintStyles.NAMES[s]);
                }
            }
            if (test) {
                dumped = 1;
            }
        } finally {
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            main.bindWrite(true);
            Lighting.setupFor3DItems();
        }
    }

    private static void draw(Minecraft mc, int style, MultiBufferSource.BufferSource buffers) {
        PoseStack pose = new PoseStack();
        pose.translate(SIZE / 2.0F, SIZE / 2.0F, 150);
        pose.mulPose(new Matrix4f().scaling(1, -1, 1));
        int e = style - ShaderPaintStyles.LIVE;
        if (e < ShaderPaintStyles.EFFECTS) {
            // an effect block running this effect: its front face fills the picture
            pose.scale(SIZE, SIZE, SIZE);
            EffectBlockEntity.Settings d = EffectBlockEntity.Settings.DEFAULT;
            EffectBlockRenderer.drawItem(pose, buffers, new EffectBlockEntity.Settings(e, d.count(), d.colours(), d.speed(), d.size(), d.brightness(),
                    d.pixel(), d.blur()));
            return;
        }
        Item item = ITEMS[e - ShaderPaintStyles.EFFECTS];
        float scale = SIZE * item.zoom();
        pose.scale(scale, scale, scale);
        pose.translate(0, -item.up(), 0);
        Lighting.setupFor3DItems();
        mc.getItemRenderer().renderStatic(new ItemStack(item.item().get()), ItemDisplayContext.GUI, 0x00F000F0, OverlayTexture.NO_OVERLAY, pose, buffers,
                mc.level, 0);
    }

    /** The render test: each live style's picture saved, to look at. */
    private static void dump(RenderTarget t, String name) {
        try (NativeImage img = new NativeImage(SIZE, SIZE, false)) {
            RenderSystem.bindTexture(t.getColorTextureId());
            img.downloadTexture(0, false);
            img.flipY();
            java.io.File dir = new java.io.File(Minecraft.getInstance().gameDirectory, "screenshots");
            dir.mkdirs();
            img.writeToFile(new java.io.File(dir, "rendertest_capture_" + name + ".png"));
        } catch (Exception ex) {
            SkyBlocksMod.LOGGER.warn("shader capture dump failed: {}", ex.toString());
        }
    }
}
