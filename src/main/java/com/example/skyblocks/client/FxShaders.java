package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * The shaders of the fountains, the planet globe, the aurora curtain and the heat shimmer
 * (shaders/core: hd_water, planet_globe, aurora_curtain, heat_shimmer), and what they're drawn with.
 */
public final class FxShaders {
    static ShaderInstance water, planet, aurora, shimmer;

    private FxShaders() {}

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Load {
        private Load() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, id("hd_water"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> water = s);
            ShaderLoad.register(event, id("planet_globe"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> planet = s);
            ShaderLoad.register(event, id("aurora_curtain"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> aurora = s);
            ShaderLoad.register(event, id("heat_shimmer"), DefaultVertexFormat.POSITION_TEX, s -> shimmer = s);
        }
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, name);
    }

    abstract static class Types extends RenderType {
        /** HD water: see-through, drawn both ways round - and keeping its depth, so see-through blocks behind it stay behind. */
        static final RenderType WATER = create("skyblocks_hd_water", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder().setShaderState(new ShaderStateShard(() -> water)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
        static final RenderType PLANET = create("skyblocks_planet_globe", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                CompositeState.builder().setShaderState(new ShaderStateShard(() -> planet)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        /** The aurora: adding light. */
        static final RenderType AURORA = create("skyblocks_aurora_curtain", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 2048, false, true,
                CompositeState.builder().setShaderState(new ShaderStateShard(() -> aurora)).setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));

        /** (with a shader pack: blended over what's behind instead - packs don't keep added light) */
        static final RenderType AURORA_PACK = create("skyblocks_aurora_curtain_pack", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 2048, false, true,
                CompositeState.builder().setShaderState(new ShaderStateShard(() -> aurora)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }
}
