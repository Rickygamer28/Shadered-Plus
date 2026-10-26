package com.example.skyblocks.client;

import com.example.skyblocks.HologramBlockEntity;
import com.example.skyblocks.HologramProjectorBlock;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The hologram: the item's own model, drawn through shaders/core/hologram (one colour, see-through, scan lines,
 * flicker, bright edges) turning slowly over the projector, and a faint cone of light up from the lens.
 */
public class HologramRenderer implements BlockEntityRenderer<HologramBlockEntity> {
    static ShaderInstance shader;
    /** This hologram's colour (each is drawn at once, so each can have its own). */
    private static final float[] COLOUR = {0.3F, 0.9F, 1.0F};

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "hologram"), DefaultVertexFormat.NEW_ENTITY), s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        private static final java.util.Map<ResourceLocation, RenderType> BY_TEXTURE = new java.util.HashMap<>();

        /** The hologram look, on a texture (the block atlas for most items; their own for chests, shields, heads ...). */
        static RenderType holo(ResourceLocation texture) {
            return BY_TEXTURE.computeIfAbsent(texture, tex -> create("skyblocks_hologram", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 262144,
                    false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTextureState(new TextureStateShard(tex, false, false))
                            .setTexturingState(new TexturingStateShard("skyblocks_hologram_look", () -> {
                                if (shader != null) {
                                    shader.safeGetUniform("HoloColor").set(COLOUR[0], COLOUR[1], COLOUR[2]);
                                    shader.safeGetUniform("HoloTime").set(LavaLampRenderer.seconds());
                                }
                            }, () -> {}))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(CULL)
                            .createCompositeState(false)));
        }

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private static java.lang.reflect.Field stateField, textureField;
    private static java.lang.reflect.Method cutoutMethod;
    private static boolean reflectionTried;

    /** The texture a render type draws with (looked up once how; empty if it can't be found). */
    @SuppressWarnings("unchecked")
    static java.util.Optional<ResourceLocation> textureOf(RenderType type) {
        try {
            if (!reflectionTried) {
                reflectionTried = true;
                Class<?> composite = Class.forName("net.minecraft.client.renderer.RenderType$CompositeRenderType");
                stateField = composite.getDeclaredField("state");
                stateField.setAccessible(true);
                textureField = Class.forName("net.minecraft.client.renderer.RenderType$CompositeState").getDeclaredField("textureState");
                textureField.setAccessible(true);
                cutoutMethod = Class.forName("net.minecraft.client.renderer.RenderStateShard$EmptyTextureStateShard").getDeclaredMethod("cutoutTexture");
                cutoutMethod.setAccessible(true);
            }
            if (stateField == null || !stateField.getDeclaringClass().isInstance(type)) {
                return java.util.Optional.empty();
            }
            Object state = stateField.get(type);
            Object texture = textureField.get(state);
            return (java.util.Optional<ResourceLocation>) cutoutMethod.invoke(texture);
        } catch (ReflectiveOperationException | RuntimeException e) {
            stateField = null;
            return java.util.Optional.empty();
        }
    }

    /** Passes a block-format vertex on, adding the overlay the hologram's format also has. */
    private record WithOverlay(VertexConsumer inner) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { inner.addVertex(x, y, z); inner.setUv1(0, 10); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { inner.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { inner.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { inner.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { inner.setNormal(x, y, z); return this; }
    }

    /** Writes nothing (for the enchantment glint and other parts that aren't the item's own textured faces). */
    private static final VertexConsumer NOTHING = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    public HologramRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HologramBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        int c = com.example.skyblocks.GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds());
        float r = ((c >> 16) & 255) / 255.0F, g = ((c >> 8) & 255) / 255.0F, b = (c & 255) / 255.0F;
        ItemStack stack = be.shown();
        int size = be.getBlockState().hasProperty(HologramProjectorBlock.SIZE) ? be.getBlockState().getValue(HologramProjectorBlock.SIZE) : 1;
        float scale = new float[] {0.7F, 1.1F, 1.7F}[size];
        float t = LavaLampRenderer.seconds();
        float centre = 0.29F + 0.36F * scale + 0.03F * (float) Math.sin(t * 1.3);
        // the cone of light from the lens up to the hologram
        float top = stack.isEmpty() ? 0.55F : centre;
        float spread = stack.isEmpty() ? 0.06F : 0.22F * scale;
        cone(buffers.getBuffer(RenderType.lightning()), poseStack.last().pose(), 4.6F / 16.0F, top, 1.6F / 16.0F, spread, r, g, b,
                stack.isEmpty() ? 0.12F : 0.2F);
        if (stack.isEmpty() || shader == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, centre, 0.5);
        poseStack.mulPose(Axis.YP.rotation(t * 0.7F));
        float s = scale * (stack.getItem() instanceof BlockItem ? 1.0F : 0.9F);
        poseStack.scale(s, s, s);
        COLOUR[0] = r;
        COLOUR[1] = g;
        COLOUR[2] = b;
        // whatever the item draws with: the hologram look on that same texture (chests, shields and heads have their own)
        java.util.Set<RenderType> used = new java.util.LinkedHashSet<>();
        MultiBufferSource holo = type -> {
            if (type.format() == DefaultVertexFormat.BLOCK) { // (a block model drawn as an item part: on the block atlas)
                RenderType h = Types.holo(TextureAtlas.LOCATION_BLOCKS);
                used.add(h);
                return new WithOverlay(buffers.getBuffer(h));
            }
            if (type.format() != DefaultVertexFormat.NEW_ENTITY) {
                return NOTHING;
            }
            RenderType h = Types.holo(textureOf(type).orElse(TextureAtlas.LOCATION_BLOCKS));
            used.add(h);
            return buffers.getBuffer(h);
        };
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY,
                poseStack, holo, be.getLevel(), (int) be.getBlockPos().asLong());
        poseStack.popPose();
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            for (RenderType h : used) {
                source.endBatch(h); // (now, with this hologram's colour)
            }
        }
    }

    /** A soft cone of light: bright at the lens, fading upwards. */
    private static void cone(VertexConsumer vc, Matrix4f m, float y0, float y1, float r0, float r1, float r, float g, float b, float a) {
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2.0 / n, a1 = (i + 1) * Math.PI * 2.0 / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vc.addVertex(m, 0.5F + c0 * r0, y0, 0.5F + s0 * r0).setColor(r, g, b, a);
            vc.addVertex(m, 0.5F + c1 * r0, y0, 0.5F + s1 * r0).setColor(r, g, b, a);
            vc.addVertex(m, 0.5F + c1 * r1, y1, 0.5F + s1 * r1).setColor(r, g, b, 0.0F);
            vc.addVertex(m, 0.5F + c0 * r1, y1, 0.5F + s0 * r1).setColor(r, g, b, 0.0F);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(HologramBlockEntity be) {
        return true;
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox(HologramBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.5);
    }
}
