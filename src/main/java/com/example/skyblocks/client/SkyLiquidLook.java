package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Vector3f;

/**
 * How the Sky Liquids look (client): their own textures are fully see-through (all you see is their Shadered sky, drawn
 * by SkyLiquidRenderer); under the surface: deep space purple, and you can't see far.
 */
public final class SkyLiquidLook implements IClientFluidTypeExtensions {
    public static final SkyLiquidLook INSTANCE = new SkyLiquidLook();

    private final ResourceLocation still = new ResourceLocation(SkyBlocksMod.MOD_ID, "block/sky_liquid_still");
    private final ResourceLocation flowing = new ResourceLocation(SkyBlocksMod.MOD_ID, "block/sky_liquid_flow");

    private SkyLiquidLook() {}

    @Override
    public ResourceLocation getStillTexture() {
        return still;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return flowing;
    }

    @Override
    public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                   float darkenWorldAmount, Vector3f fluidFogColor) {
        return new Vector3f(0.06F, 0.02F, 0.12F);
    }

    @Override
    public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick,
                                float nearDistance, float farDistance, FogShape shape) {
        RenderSystem.setShaderFogStart(-4.0F);
        RenderSystem.setShaderFogEnd(24.0F);
    }
}
