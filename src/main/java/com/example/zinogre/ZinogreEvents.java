package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Lightning hatches Zinogre eggs within 2 blocks of where it strikes. */
@EventBusSubscriber(modid = ZinogreMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ZinogreEvents {
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LightningBolt bolt) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos at = bolt.blockPosition();
        // looked up now, hatched next tick (not while the level is adding the bolt)
        level.getServer().execute(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -2, -2), at.offset(2, 2, 2))) {
                if (level.getBlockState(pos).getBlock() instanceof ZinogreEggBlock egg) {
                    ZinogreEggBlock.hatch(level, pos.immutable(), egg);
                }
            }
        });
    }

    private ZinogreEvents() {}
}
