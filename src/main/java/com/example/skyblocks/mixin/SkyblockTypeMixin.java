package com.example.skyblocks.mixin;

import com.example.skyblocks.SkyNames;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds our skies to Shadered's SkyblockType enum (SKYBLOCKS_JUPITER, ...), right when the enum is created,
 * so values(), valueOf(), saving/loading and every loop over the sky types include them.
 */
@Mixin(value = SkyblockType.class, remap = false)
public abstract class SkyblockTypeMixin {
    @Shadow
    @Final
    @Mutable
    private static SkyblockType[] $VALUES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void skyblocks$addOurSkies(CallbackInfo ci) {
        List<SkyblockType> values = new ArrayList<>(Arrays.asList($VALUES));
        for (int i = 0; i < SkyNames.NAMES.length; i++) {
            values.add(SkyblockTypeInvoker.skyblocks$create(
                    SkyNames.enumName(i), values.size(), "skyblock_type.skyblocks." + SkyNames.NAMES[i]));
        }
        $VALUES = values.toArray(new SkyblockType[0]);
    }
}
