package com.example.skyblocks.mixin;

import com.example.skyblocks.client.PortalSkyRenderer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A portal showing a Shadered sky gives off particles in that sky's colour (client/PortalSkyRenderer). Works for any
 * portal (vanilla or modded): while the client level lets a block give off its particles (animateTick), the block's
 * position is noted, and particles it adds are made in the sky's colour if that block is a portal with a sky.
 */
@Mixin(ClientLevel.class)
public abstract class PortalParticlesMixin {
    @Unique
    private static BlockPos skyblocks$animating;

    @Redirect(method = "doAnimateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;animateTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"),
            require = 0)
    private void skyblocks$noteBlock(Block block, BlockState state, Level level, BlockPos pos, RandomSource random) {
        skyblocks$animating = PortalSkyRenderer.skyAt(level, pos) != null ? pos.immutable() : null;
        try {
            block.animateTick(state, level, pos, random);
        } finally {
            skyblocks$animating = null;
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void skyblocks$colouredParticle(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
                                            CallbackInfo ci) {
        BlockPos pos = skyblocks$animating;
        if (pos == null) {
            return;
        }
        ClientLevel level = (ClientLevel) (Object) this;
        SkyblockType sky = PortalSkyRenderer.skyAt(level, pos);
        if (sky == null) {
            return;
        }
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        Particle particle = mc.particleEngine.createParticle(options, x, y, z, dx, dy, dz);
        if (particle != null) {
            float[] c = PortalSkyRenderer.color(sky);
            float f = 0.4F + level.random.nextFloat() * 0.6F; // brighter and darker ones, like the vanilla purple
            particle.setColor(c[0] * f, c[1] * f, c[2] * f);
        }
    }
}
