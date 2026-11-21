package com.example.skyblocks.mixin;

import com.example.skyblocks.TrainCollision;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Things moving bump into trains' walls and stand on their floors (TrainCollision), as into blocks. */
@Mixin(Entity.class)
public abstract class TrainCollideMixin {
    @ModifyArg(method = "collide", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;"),
            index = 4, require = 1)
    private List<VoxelShape> skyblocks$trainWalls(Entity entity, Vec3 move, AABB box, Level level, List<VoxelShape> shapes) {
        if (entity == null) {
            return shapes;
        }
        List<VoxelShape> walls = TrainCollision.shapes(entity, box.expandTowards(move).inflate(0.5));
        if (walls.isEmpty()) {
            return shapes;
        }
        List<VoxelShape> all = new ArrayList<>(shapes.size() + walls.size());
        all.addAll(shapes);
        all.addAll(walls);
        return all;
    }
}
