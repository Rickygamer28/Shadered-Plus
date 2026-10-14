package com.example.skyblocks.mixin;

import com.example.skyblocks.SkyItemSupplier;
import com.example.skyblocks.SkyNames;
import com.noodlegamer76.shadered.item.SkyblockItemTypes;
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

/** Adds an item type for each of our skies to Shadered's SkyblockItemTypes enum (item: skyblocks:<name>_sky_block). */
@Mixin(value = SkyblockItemTypes.class, remap = false)
public abstract class SkyblockItemTypesMixin {
    @Shadow
    @Final
    @Mutable
    private static SkyblockItemTypes[] $VALUES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void skyblocks$addOurItems(CallbackInfo ci) {
        List<SkyblockItemTypes> values = new ArrayList<>(Arrays.asList($VALUES));
        for (int i = 0; i < SkyNames.NAMES.length; i++) {
            values.add(SkyblockItemTypesInvoker.skyblocks$create(
                    SkyNames.enumName(i), values.size(), "item.skyblocks." + SkyNames.itemId(i), new SkyItemSupplier(i)));
        }
        $VALUES = values.toArray(new SkyblockItemTypes[0]);
    }
}
