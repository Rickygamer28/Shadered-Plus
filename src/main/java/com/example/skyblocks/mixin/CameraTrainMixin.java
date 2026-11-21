package com.example.skyblocks.mixin;

import com.example.skyblocks.client.TrainCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The third-person camera on a train: further out, round the car you're in (client/TrainCamera). */
@Mixin(Camera.class)
public abstract class CameraTrainMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void skyblocks$trainCamera(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick, CallbackInfo ci) {
        if (!detached || entity == null) {
            return;
        }
        Vec3 p = TrainCamera.place((Camera) (Object) this, level, entity, getPosition(), partialTick);
        if (p != null) {
            setPosition(p);
        }
    }
}
