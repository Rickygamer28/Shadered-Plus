package com.example.skyblocks.mixin;

import com.example.skyblocks.MoonJar;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The moon caught in a jar (MoonJar): the sky draws it no size at all - the moon's quad is drawn 20 wide, the only 20
 * in drawing the sky (the sun's 30).
 */
@Mixin(value = LevelRenderer.class, remap = false)
public abstract class MoonSkyMixin {
    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 20.0F), require = 0)
    private float skyblocks$moonSize(float size) {
        return MoonJar.clientCaught ? 0.0F : size;
    }
}
