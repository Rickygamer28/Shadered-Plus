package com.example.skyblocks.mixin;

import com.example.skyblocks.SkyNames;
import com.example.skyblocks.SkyTypes;
import com.noodlegamer76.shadered.client.util.SkyblockRegistry;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.item.SkyblockItemTypes;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Links each of our sky types to its item type, like Shadered does for its own (drops, pick block, Sky Emitter...). */
@Mixin(value = SkyblockRegistry.class, remap = false)
public abstract class SkyblockRegistryMixin {
    @Shadow
    @Final
    private static Map<SkyblockType, SkyblockItemTypes> TYPE_TO_ITEM;

    @Shadow
    @Final
    private static Map<SkyblockItemTypes, SkyblockType> ITEM_TO_TYPE;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void skyblocks$registerOurSkies(CallbackInfo ci) {
        for (int i = 0; i < SkyNames.NAMES.length; i++) {
            TYPE_TO_ITEM.put(SkyTypes.type(i), SkyTypes.itemType(i));
            ITEM_TO_TYPE.put(SkyTypes.itemType(i), SkyTypes.type(i));
        }
    }
}
