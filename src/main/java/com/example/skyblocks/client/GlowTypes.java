package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Plain coloured shapes (POSITION_COLOR quads, both sides): GLOW added on top of what's behind (light: edges, sparks,
 * halos), SHADE blended over it (ink, shadow) - neither hiding what's drawn after them.
 */
public abstract class GlowTypes extends RenderType {
    public static final RenderType GLOW = create("skyblocks_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    public static final RenderType SHADE = create("skyblocks_shade", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(false));

    private GlowTypes() {
        super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
    }
}
