package com.example.skyblocks.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the Sky Island's mini fish (client-only copies that never tick) know they are in water, so their renderers
 * draw them swimming instead of flopping on their side.
 */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("wasTouchingWater")
    void skyblocks$setWasTouchingWater(boolean value);
}
