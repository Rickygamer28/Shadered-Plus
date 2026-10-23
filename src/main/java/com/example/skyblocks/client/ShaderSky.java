package com.example.skyblocks.client;

import com.example.skyblocks.ModFluids;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SkyNames;
import com.example.skyblocks.SkyTypes;
import com.example.skyblocks.island.ShaderedSkyItems;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.noodlegamer76.shadered.client.renderer.ComplexPassRenderer;
import com.noodlegamer76.shadered.client.renderer.SkyblockRenderer;
import com.noodlegamer76.shadered.client.renderer.complexpasses.SkyboxRenderPass;
import com.noodlegamer76.shadered.client.util.RenderStage;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import com.noodlegamer76.shadered.client.util.skyblock.SkyboxTranslation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Skies drawn by a shader: Shadered skies like the others (sky block, Illusorite ores, Sky Liquid, Sky Emitter, portals,
 * islands, the item in hand...), except that their screen-sized picture is drawn by a shader instead of six pictures.
 * <ul>
 * <li>chaos: Draconic Evolution's chaos shader (adapted from Draconic Evolution / BrandonsCore by brandon3055, credited
 * in the tooltip and CREDITS.md)</li>
 * <li>synthwave: Shadered+'s own (sunset, striped sun, wireframe mountains, stars, moving neon grid)</li>
 * </ul>
 */
public final class ShaderSky {
    static final ResourceLocation NOISE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/effect/chaos_noise.png");
    private static ShaderInstance chaos;
    private static ShaderInstance synthwave;

    private ShaderSky() {}

    /** The pass for a sky drawn by a shader, or null if the sky is made of pictures. */
    @org.jetbrains.annotations.Nullable
    public static SkyboxRenderPass passFor(String name, ResourceLocation folder, SkyblockBatchData batch) {
        return switch (name) {
            case SkyNames.CHAOS -> new Pass(folder, batch, () -> chaos, NOISE, null);
            case SkyNames.SYNTHWAVE -> new Pass(folder, batch, () -> synthwave, MusicReactor.HISTORY, MusicReactor::apply); // (reacts to music)
            default -> null;
        };
    }

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "chaos_sky"), DefaultVertexFormat.POSITION), s -> chaos = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "synthwave_sky"), DefaultVertexFormat.POSITION), s -> synthwave = s);
        }
    }

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
    public static final class Tooltips {
        private Tooltips() {}

        /** The credit, on the chaos sky's items (sky block, liquid bucket). */
        @SubscribeEvent
        public static void onTooltip(ItemTooltipEvent event) {
            if (isChaos(event.getItemStack())) {
                event.getToolTip().add(Component.translatable("tooltip.skyblocks.draconic_credit")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            }
        }

        private static boolean isChaos(ItemStack stack) {
            int index = SkyNames.chaosIndex();
            if (index < 0 || stack.isEmpty()) {
                return false;
            }
            SkyblockType chaos = SkyTypes.type(index);
            ShaderedSkyItems.Sky sky = ShaderedSkyItems.sky(stack);
            if (sky != null) {
                return sky.type() == chaos;
            }
            ModFluids.SkyLiquid liquid = ModFluids.of(chaos);
            return liquid != null && liquid.bucket != null && stack.is(liquid.bucket.get());
        }
    }

    /** A shader sky's pass: draws its shader into the sky's screen-sized picture (as SkyboxRenderPass does). */
    public static final class Pass extends SkyboxRenderPass {
        private final SkyblockBatchData batch;
        private final java.util.function.Supplier<ShaderInstance> shaderSource;
        @org.jetbrains.annotations.Nullable
        private final ResourceLocation texture;
        @org.jetbrains.annotations.Nullable
        private final java.util.function.Consumer<ShaderInstance> setup;
        private TextureTarget target;

        Pass(ResourceLocation folder, SkyblockBatchData batch, java.util.function.Supplier<ShaderInstance> shader,
             @org.jetbrains.annotations.Nullable ResourceLocation texture,
             @org.jetbrains.annotations.Nullable java.util.function.Consumer<ShaderInstance> setup) {
            super(folder, batch, new SkyboxTranslation());
            this.batch = batch;
            this.shaderSource = shader;
            this.texture = texture;
            this.setup = setup;
        }

        @Override
        public void render(RenderStage stage, PoseStack poseStack, int renderTick, float partialTick) {
            // (what SkyPassMixin does at the start of Shadered's own sky passes)
            if (stage == RenderStage.AFTER_SKY) {
                SkyUsage.skyStage(renderTick, partialTick);
            }
            ShaderInstance shader = shaderSource.get();
            if (!SkyUsage.recentlyUsed(batch) || shader == null) {
                return;
            }
            if ((Object) this instanceof SkyUsage.Pass tracked) { // (added to SkyboxRenderPass by SkyPassMixin)
                tracked.skyblocks$setDrawnFrame(SkyUsage.frame());
            }
            ComplexPassRenderer renderer = ComplexPassRenderer.getInstance();
            if (target == null) {
                target = new TextureTarget(renderer.getPreviousWidth(), renderer.getPreviousHeight(), false, Minecraft.ON_OSX);
            } else if (target.width != renderer.getPreviousWidth() || target.height != renderer.getPreviousHeight()) {
                target.resize(renderer.getPreviousWidth(), renderer.getPreviousHeight(), Minecraft.ON_OSX);
            }
            SkyblockRenderer.DATA_LIST.put(batch, target.getColorTextureId());
            target.bindWrite(true);
            if (setup != null) {
                setup.accept(shader); // (its own uniforms / texture brought up to date)
            }
            target.bindWrite(true);
            draw(shader, texture, poseStack.last().pose(), (renderTick % 240000) + partialTick);
            renderer.getRenderBuffer().bindWrite(true);
        }

        @Override
        public TextureTarget getSkyboxTarget() {
            SkyUsage.mark(batch);
            return target;
        }

        /** A cube around the camera turned by the view (like Shadered's skyboxes), every pixel coloured by the shader. */
        private static void draw(ShaderInstance shader, @org.jetbrains.annotations.Nullable ResourceLocation texture, Matrix4f view,
                                 float ticks) {
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelView.mul(view);
            float far = Math.max(16.0F, Minecraft.getInstance().gameRenderer.getRenderDistance());
            modelView.scale(far);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            try {
                shader.safeGetUniform("Time").set(ticks);
                if (texture != null) {
                    RenderSystem.setShaderTexture(0, texture);
                }
                RenderSystem.setShader(() -> shader);
                BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                // the six faces of a cube from -1 to 1, scaled to the render distance like Shadered's skyboxes: the view
                // bobbing's small sideways / up-down shift (in the projection) must be tiny next to it, or the whole
                // picture swims as you walk
                quad(b, -1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1);
                quad(b, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1, 1);
                quad(b, -1, -1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1);
                quad(b, 1, -1, -1, 1, -1, 1, 1, 1, 1, 1, 1, -1);
                quad(b, -1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1);
                quad(b, -1, -1, -1, -1, -1, 1, 1, -1, 1, 1, -1, -1);
                MeshData mesh = b.build();
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
            } finally {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.enableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
            }
        }

        private static void quad(BufferBuilder b, float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3) {
            b.addVertex(x0, y0, z0);
            b.addVertex(x1, y1, z1);
            b.addVertex(x2, y2, z2);
            b.addVertex(x3, y3, z3);
        }
    }
}
