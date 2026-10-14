package com.example.skyblocks.island;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Travelling with an ender pearl: right-clicking a Sky Island (pedestal or painting) with an ender pearl teleports the
 * player to where that island was scanned, on top of where the scanner stood (any dimension). The pearl is used up
 * (not in creative).
 */
public final class IslandTravel {

    public static boolean isTravelItem(ItemStack stack) {
        return stack.is(Items.ENDER_PEARL);
    }

    /** Server side: teleports the player; tells them why not if the island doesn't know its place. */
    public static void travel(Level level, Player player, IslandData island, ItemStack pearl) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel from)) {
            return;
        }
        if (!island.hasOrigin()) {
            player.displayClientMessage(Component.translatable("message.skyblocks.island_no_origin"), true);
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(island.dimension());
        ServerLevel target = id == null ? null : serverPlayer.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.skyblocks.island_no_dimension"), true);
            return;
        }
        BlockPos spot = safeSpot(target, island.origin());
        from.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        serverPlayer.teleportTo(target, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(),
                serverPlayer.getYRot(), serverPlayer.getXRot());
        target.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (!player.getAbilities().instabuild) {
            pearl.shrink(1);
        }
    }

    /** On top of where the scanner stood, or the first place above it with room for a player. */
    private static BlockPos safeSpot(ServerLevel level, BlockPos scanner) {
        for (int dy = 1; dy <= 24; dy++) {
            BlockPos feet = scanner.above(dy);
            if (free(level, feet) && free(level, feet.above())) {
                return feet;
            }
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, scanner);
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private IslandTravel() {}
}
