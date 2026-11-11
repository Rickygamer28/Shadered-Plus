package com.example.skyblocks.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A wisp of a hot spring's steam: rises slowly off the water, swaying, spreading and thinning away. */
public class SteamParticle extends TextureSheetParticle {
    private final float startSize, endSize, sway;

    protected SteamParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        xd = dx;
        yd = dy;
        zd = dz;
        lifetime = 50 + random.nextInt(50);
        gravity = -0.0006F; // (warm: it keeps rising)
        friction = 0.97F;
        hasPhysics = false;
        startSize = 0.12F + random.nextFloat() * 0.08F;
        endSize = 0.55F + random.nextFloat() * 0.35F;
        quadSize = startSize;
        sway = random.nextFloat() * Mth.TWO_PI;
        float shade = 0.92F + random.nextFloat() * 0.08F;
        setColor(shade, shade, shade);
        alpha = 0.0F;
        roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float life = age / (float) lifetime;
        xd += Math.sin(sway + age * 0.08F) * 0.0004;
        zd += Math.cos(sway * 1.3F + age * 0.07F) * 0.0004;
        quadSize = Mth.lerp(life, startSize, endSize);
        alpha = 0.26F * Math.min(1.0F, age / 10.0F) * (1.0F - life) * (1.0F - life * 0.3F);
        oRoll = roll;
        roll += 0.01F;
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
            return new SteamParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
