package com.example.skyblocks.mixin;

import com.example.skyblocks.client.SonarPulse;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A mob a sonar pulse found glows in its entry's colour (client/SonarPulse). */
@Mixin(Entity.class)
public abstract class SonarColourMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void skyblocks$sonarColour(CallbackInfoReturnable<Integer> cir) {
        int c = SonarPulse.glow((Entity) (Object) this);
        if (c >= 0) {
            cir.setReturnValue(c);
        }
    }
}
