package com.example.skyblocks;

import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, SkyBlocksMod.MOD_ID);

    /** The fog machine's fog (client/FogParticle). */
    public static final Supplier<SimpleParticleType> FOG = TYPES.register("fog", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
