package com.example.skyblocks.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A puff of fog: shoots out, slows down, spreads and sinks to the floor, drifting there until it thins away. */
public class FogParticle extends TextureSheetParticle {
    @org.jetbrains.annotations.Nullable
    private final net.minecraft.world.phys.AABB bounds;
    private final float startSize;
    private float endSize;

    protected FogParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        xd = dx;
        yd = dy;
        zd = dz;
        lifetime = 180 + random.nextInt(140);
        gravity = 0.0015F;
        friction = 0.965F;
        hasPhysics = true;
        startSize = 0.35F + random.nextFloat() * 0.2F;
        endSize = 1.6F + random.nextFloat() * 1.0F;
        quadSize = startSize;
        float shade = 0.85F + random.nextFloat() * 0.15F;
        setColor(shade, shade, Math.min(1.0F, shade + 0.05F));
        alpha = 0.0F;
        roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        pickSprite(sprites);
        bounds = com.example.skyblocks.FogParticleBounds.next;
        if (bounds != null && dx * dx + dz * dz < 0.0009) {
            gravity = 0.0F; // (the fog filling the area floats where it is, drifting a little)
            endSize = Math.min(endSize, 1.2F + random.nextFloat() * 0.8F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (bounds != null && !bounds.inflate(0.3).contains(x, y, z)) {
            age = Math.max(age, lifetime - 15); // (out of the area: thins away quickly)
            xd *= 0.5;
            zd *= 0.5;
        }
        float life = age / (float) lifetime;
        quadSize = Mth.lerp(Math.min(1.0F, life * 2.5F), startSize, endSize);
        alpha = 0.32F * Math.min(1.0F, age / 12.0F) * (1.0F - life);
        oRoll = roll;
        roll += 0.004F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new FogParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
