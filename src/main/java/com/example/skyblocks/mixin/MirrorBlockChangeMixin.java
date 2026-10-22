package com.example.skyblocks.mixin;

import com.example.skyblocks.client.MirrorRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mirror placed, broken or covered up turns into a mirror (or back) on the next frame, not up to half a second later. */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class MirrorBlockChangeMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), require = 0)
    private void skyblocks$mirrorChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        MirrorRenderer.blockChanged((ClientLevel) (Object) this, pos, oldState, newState);
    }
}
