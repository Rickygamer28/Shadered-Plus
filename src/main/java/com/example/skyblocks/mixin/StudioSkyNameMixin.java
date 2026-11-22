package com.example.skyblocks.mixin;

import com.example.skyblocks.StudioSkiesStore;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Sky Studio's skies go by the names their makers gave them (StudioSkies): their sky, their sky block. */
@Mixin(ClientLanguage.class)
public abstract class StudioSkyNameMixin {
    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
    private void skyblocks$studioName(String key, String fallback, CallbackInfoReturnable<String> cir) {
        String name = StudioSkiesStore.nameFor(key);
        if (name != null) {
            cir.setReturnValue(name);
        }
    }
}
