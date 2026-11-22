package com.example.skyblocks.mixin;

import com.example.skyblocks.ModSkyShapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Walls join Sky Walls (ModSkyShapes), as they join walls in the walls tag: the Sky Walls of a sky an addon adds, not
 * in any tag file, join too.
 */
@Mixin(WallBlock.class)
public abstract class WallSkyShapeMixin {
    @Inject(method = "connectsTo", at = @At("HEAD"), cancellable = true)
    private void skyblocks$skyWalls(BlockState state, boolean sideSolid, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof ModSkyShapes.Wall) {
            cir.setReturnValue(true);
        }
    }
}
