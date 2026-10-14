package com.example.skyblocks.mixin;

import com.noodlegamer76.shadered.item.SkyblockItemTypes;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Gives access to SkyblockItemTypes' private enum constructor (name, ordinal, translation key, item). */
@Mixin(value = SkyblockItemTypes.class, remap = false)
public interface SkyblockItemTypesInvoker {
    @Invoker("<init>")
    static SkyblockItemTypes skyblocks$create(String name, int ordinal, String translationKey, Supplier<Item> item) {
        throw new AssertionError();
    }
}
