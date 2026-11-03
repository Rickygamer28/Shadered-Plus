package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Glass or an amethyst shard used on a planetarium's hologram (not only its base: a big one is mostly hologram) -
 * pointing at its dome, or standing in it: as if used on the planetarium itself.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class SolarSystemEvents {
    private SolarSystemEvents() {}

    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!SolarSystemBlock.isTool(stack) || event.getLevel().getBlockState(event.getPos()).getBlock() instanceof SolarSystemBlock) {
            return; // (on the planetarium itself: the block does it)
        }
        double block = event.getHitVec().getLocation().distanceTo(event.getEntity().getEyePosition());
        BlockPos pos = aimed(event.getLevel(), event.getEntity(), block);
        if (pos != null) {
            SolarSystemBlock.useTool(stack, event.getLevel(), pos, event.getEntity());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    @SubscribeEvent
    public static void onUseInAir(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!SolarSystemBlock.isTool(stack)) {
            return;
        }
        BlockPos pos = aimed(event.getLevel(), event.getEntity(), Double.MAX_VALUE);
        if (pos != null) {
            SolarSystemBlock.useTool(stack, event.getLevel(), pos, event.getEntity());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    /** The planetarium whose dome the player is in or pointing at (nearer than {@code before}), or null. */
    @Nullable
    private static BlockPos aimed(Level level, Player player, double before) {
        Vec3 eye = player.getEyePosition();
        Vec3 rd = player.getViewVector(1.0F);
        ChunkPos c = new ChunkPos(player.blockPosition());
        BlockPos best = null;
        double bestT = Math.min(before, 24.0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(c.x + dx, c.z + dz)) continue;
                LevelChunk chunk = level.getChunk(c.x + dx, c.z + dz);
                for (BlockEntity e : chunk.getBlockEntities().values()) {
                    if (!(e instanceof SolarSystemBlockEntity be) || !(be.getBlockState().getBlock() instanceof SolarSystemBlock b) || !b.hologram()) {
                        continue;
                    }
                    double s = be.scale();
                    BlockPos p = be.getBlockPos();
                    Vec3 centre = new Vec3(p.getX() + 0.5, p.getY() + 0.3 + 1.15 * s, p.getZ() + 0.5);
                    double r = 1.38 * s * 1.08;
                    Vec3 oc = eye.subtract(centre);
                    double bq = oc.dot(rd), cq = oc.lengthSqr() - r * r, h = bq * bq - cq;
                    if (h < 0.0) continue;
                    double t = cq < 0.0 ? 0.0 : -bq - Math.sqrt(h); // (inside it: right here)
                    if (t < 0.0 || t >= bestT) continue;
                    bestT = t;
                    best = p;
                }
            }
        }
        return best;
    }
}
