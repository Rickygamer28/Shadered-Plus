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
 * Clicking an orrery's or a planetarium's model, not only its base (the brass orrery's planets go out past its block, a
 * big planetarium is mostly hologram) - pointing at it, or standing in it: as if clicking the block itself. An empty
 * hand: the next speed (sneaking: real sizes); glass or an amethyst shard on a planetarium: its dome, its size.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class SolarSystemEvents {
    private SolarSystemEvents() {}

    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        boolean tool = SolarSystemBlock.isTool(stack);
        boolean hand = stack.isEmpty() && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND && event.getEntity().getOffhandItem().isEmpty();
        if ((!tool && !hand) || event.getLevel().getBlockState(event.getPos()).getBlock() instanceof SolarSystemBlock) {
            return; // (on the block itself: the block does it)
        }
        double block = event.getHitVec().getLocation().distanceTo(event.getEntity().getEyePosition());
        BlockPos pos = aimed(event.getLevel(), event.getEntity(), block, !tool);
        if (pos != null) {
            if (tool) {
                SolarSystemBlock.useTool(stack, event.getLevel(), pos, event.getEntity());
            } else {
                SolarSystemBlock.use(event.getLevel(), pos, event.getEntity());
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    /** An empty hand clicking at nothing (only the client hears of it): the model pointed at, told to the server. */
    @SubscribeEvent
    public static void onClickAir(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) {
            return;
        }
        BlockPos pos = aimed(event.getLevel(), event.getEntity(), Double.MAX_VALUE, true);
        if (pos != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new SolarSystemClickPayload(pos));
        }
    }

    /** (server) A player clicked a model at nothing behind it: checked, then as if they'd clicked its block. */
    public static void clicked(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        if (level.isLoaded(pos) && player.getMainHandItem().isEmpty() && aimed(level, player, Double.MAX_VALUE, true) != null
                && pos.equals(aimed(level, player, Double.MAX_VALUE, true))) {
            SolarSystemBlock.use(level, pos, player);
        }
    }

    @SubscribeEvent
    public static void onUseInAir(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!SolarSystemBlock.isTool(stack)) {
            return;
        }
        BlockPos pos = aimed(event.getLevel(), event.getEntity(), Double.MAX_VALUE, false);
        if (pos != null) {
            SolarSystemBlock.useTool(stack, event.getLevel(), pos, event.getEntity());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    /** The planetarium whose dome (or orrery whose model, with {@code orreries}) the player is in or pointing at (nearer than {@code before}), or null. */
    @Nullable
    private static BlockPos aimed(Level level, Player player, double before, boolean orreries) {
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
                    if (!(e instanceof SolarSystemBlockEntity be) || !(be.getBlockState().getBlock() instanceof SolarSystemBlock b) || (!b.hologram() && !orreries)) {
                        continue;
                    }
                    BlockPos p = be.getBlockPos();
                    Vec3 centre;
                    double r;
                    if (b.hologram()) {
                        if (orreries && !be.dome()) {
                            continue; // (an empty hand on one without its dome: what's behind it is clicked)
                        }
                        double s = be.scale();
                        centre = new Vec3(p.getX() + 0.5, p.getY() + 0.3 + 1.15 * s, p.getZ() + 0.5);
                        r = 1.38 * s * 1.08;
                    } else { // (the brass orrery: its planets' sweep, as the shader's)
                        centre = new Vec3(p.getX() + 0.5, p.getY() + 0.74, p.getZ() + 0.5);
                        r = 0.74 * 1.05;
                    }
                    Vec3 oc = eye.subtract(centre);
                    double bq = oc.dot(rd), cq = oc.lengthSqr() - r * r, h = bq * bq - cq;
                    if (h < 0.0) continue;
                    if (cq < 0.0 && orreries) {
                        continue; // (an empty hand from inside one: what's clicked is what's pointed at)
                    }
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
