package com.example.skyblocks.mixin;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Gives access to SkyblockType's private enum constructor (name, ordinal, translation key). */
@Mixin(value = SkyblockType.class, remap = false)
public interface SkyblockTypeInvoker {
    @Invoker("<init>")
    static SkyblockType skyblocks$create(String name, int ordinal, String translationKey) {
        throw new AssertionError();
    }
}
