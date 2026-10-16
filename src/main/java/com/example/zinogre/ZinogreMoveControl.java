package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Walking for a big beast: it turns at a limited rate, speeding up into a turn and easing out of it (an Alpha slower
 * still, see ZinogreEntity#turnTowards) and, when what it wants is well off to
 * the side or behind, slows right down to turn on the spot. Vanilla turns up to 90 degrees a tick while walking on at
 * full speed, which makes a mob this size overshoot each step of its path and swing round in circles.
 */
public class ZinogreMoveControl extends MoveControl {
    private final ZinogreEntity zinogre;

    public ZinogreMoveControl(ZinogreEntity zinogre) {
        super(zinogre);
        this.zinogre = zinogre;
    }

    @Override
    public void tick() {
        if (operation != Operation.MOVE_TO) {
            super.tick();
            return;
        }
        operation = Operation.WAIT;
        double dx = wantedX - mob.getX(), dy = wantedY - mob.getY(), dz = wantedZ - mob.getZ();
        if (dx * dx + dy * dy + dz * dz < 2.5E-7) {
            mob.setZza(0.0F);
            return;
        }
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        zinogre.turnTowards(want, zinogre.isAlpha() ? 12.0F : 25.0F);
        float off = Math.abs(Mth.wrapDegrees(want - mob.getYRot()));
        float speed = (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        if (off > 45.0F) {
            speed *= off > 100.0F ? 0.1F : 0.35F; // turning: barely moves on until it faces the way
        }
        mob.setSpeed(speed);
        // jumps up what it can't step up, like vanilla
        BlockPos pos = mob.blockPosition();
        BlockState state = mob.level().getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(mob.level(), pos);
        if (dy > mob.maxUpStep() && dx * dx + dz * dz < Math.max(1.0F, mob.getBbWidth())
                || !shape.isEmpty() && mob.getY() < shape.max(net.minecraft.core.Direction.Axis.Y) + pos.getY()
                && !state.is(BlockTags.DOORS) && !state.is(BlockTags.FENCES)) {
            mob.getJumpControl().jump();
            operation = Operation.JUMPING;
        }
    }
}
