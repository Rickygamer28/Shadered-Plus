package com.example.skyblocks;

import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** The area the next fog particle has to stay in (set by the fog machine just before it adds one; client only). */
public final class FogParticleBounds {
    @Nullable
    public static AABB next;

    private FogParticleBounds() {}
}
