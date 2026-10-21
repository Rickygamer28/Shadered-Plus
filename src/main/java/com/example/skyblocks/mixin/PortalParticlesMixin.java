package com.example.skyblocks.mixin;

import com.example.skyblocks.client.PortalSkyRenderer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A portal showing a Shadered sky gives off particles in that sky's colour (client/PortalSkyRenderer). */
@Mixin(value = {NetherPortalBlock.class, EndPortalBlock.class, EndGatewayBlock.class}, remap = false)
public abstract class PortalParticlesMixin {
    @Unique
    private static Level skyblocks$level;
    @Unique
    private static BlockPos skyblocks$pos;

    @Inject(method = "animateTick", at = @At("HEAD"), require = 0)
    private void skyblocks$which(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        skyblocks$level = level;
        skyblocks$pos = pos;
    }

    @Redirect(method = "animateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), require = 0)
    private void skyblocks$colouredParticle(Level level, ParticleOptions options, double x, double y, double z,
                                            double dx, double dy, double dz) {
        SkyblockType sky = skyblocks$pos == null ? null : PortalSkyRenderer.skyAt(level, skyblocks$pos);
        if (sky == null || level != skyblocks$level) {
            level.addParticle(options, x, y, z, dx, dy, dz);
            return;
        }
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
