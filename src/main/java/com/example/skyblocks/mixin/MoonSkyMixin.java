package com.example.skyblocks.mixin;

import com.example.skyblocks.MoonJar;
import com.example.skyblocks.SunJar;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The moon caught in a jar (MoonJar): the sky draws it no size at all - the moon's quad is drawn 20 wide, the only 20
 * in drawing the sky. The same for the sun in a jar (SunJar): its quad's 30 wide, the only 30.
 */
@Mixin(value = LevelRenderer.class, remap = false)
public abstract class MoonSkyMixin {
    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 20.0F), require = 0)
    private float skyblocks$moonSize(float size) {
        return MoonJar.clientCaught ? 0.0F : size;
    }

    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 30.0F), require = 0)
    private float skyblocks$sunSize(float size) {
        return SunJar.clientCaught ? 0.0F : size;
    }
}
