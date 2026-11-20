package com.example.skyblocks.mixin;

import com.example.skyblocks.client.SonarPulse;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mobs a sonar pulse found glow (client/SonarPulse), as with the Glowing effect: seen through walls. */
@Mixin(Minecraft.class)
public abstract class SonarGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void skyblocks$sonarGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SonarPulse.glow(entity) >= 0) {
            cir.setReturnValue(true);
        }
    }
}
