package com.example.skyblocks.mixin;

import com.example.skyblocks.TrainCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Things moving bump into trains' walls and stand on their floors (TrainCollision), as on blocks. */
@Mixin(Entity.class)
public abstract class TrainCollideMixin {
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void skyblocks$trainWalls(Vec3 move, CallbackInfoReturnable<Vec3> cir) {
        Vec3 was = cir.getReturnValue();
        Vec3 now = TrainCollision.collide((Entity) (Object) this, was);
        if (now != was) {
            cir.setReturnValue(now);
        }
    }
}
