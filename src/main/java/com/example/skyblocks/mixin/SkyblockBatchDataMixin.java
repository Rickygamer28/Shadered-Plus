package com.example.skyblocks.mixin;

import com.example.skyblocks.client.SkyUsage;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers when a block of this sky was last queued (see SkyUsage). */
@Mixin(SkyblockBatchData.class)
public abstract class SkyblockBatchDataMixin implements SkyUsage.Tracked {
    @Unique
    private long skyblocks$lastUsed;

    @Override
    public long skyblocks$lastUsed() {
        return skyblocks$lastUsed;
    }

    @Override
    public void skyblocks$markUsed() {
        long now = System.nanoTime();
        skyblocks$lastUsed = now == 0 ? 1 : now;
    }

    @Inject(method = "add", at = @At("HEAD"), require = 0)
    private void skyblocks$used(SkyblockPass pass, BlockPos pos, Matrix4f pose, boolean invert, float alpha, CallbackInfo ci) {
        skyblocks$markUsed();
    }
}
