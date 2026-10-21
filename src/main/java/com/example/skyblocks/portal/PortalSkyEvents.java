package com.example.skyblocks.portal;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SkyLiquidBucketRecipe;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Right-clicking portals with a Shadered sky block (or an empty hand), and keeping clients up to date. */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortalSkyEvents {
    private PortalSkyEvents() {}

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!PortalSkies.isPortal(level.getBlockState(pos))) {
            return;
        }
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        SkyblockType sky = stack.isEmpty() ? null : SkyLiquidBucketRecipe.skyOf(stack);
        boolean remove = sky == null && event.getHand() == InteractionHand.MAIN_HAND && stack.isEmpty();
        if (sky == null && !remove) {
            return;
        }
        if (level.isClientSide) {
            // only undo a portal this client knows has a sky (an empty hand on a normal portal does nothing)
            if (remove && com.example.skyblocks.client.PortalSkyRenderer.skyAt(level, pos) == null) {
                return;
            }
        } else if (level instanceof ServerLevel server) {
            PortalSkies skies = PortalSkies.get(server);
            if (remove && skies.skyAt(pos) == null) {
                return;
            }
            List<BlockPos> portal = PortalSkies.wholePortal(server, pos);
            if (skies.set(server, portal, sky)) {
                if (sky != null && !player.getAbilities().instabuild) {
                    stack.shrink(1); // the sky block is used up
                }
                server.playSound(null, pos, sky != null ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_CLUSTER_BREAK,
                        SoundSource.BLOCKS, 1.0F, sky != null ? 1.2F : 0.8F);
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel server && server.getGameTime() % 40 == 0) {
            PortalSkies.get(server).prune(server);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sendAll(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sendAll(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sendAll(event.getEntity());
    }

    private static void sendAll(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel level) {
            PortalSkies.get(level).sendAll(level, serverPlayer);
        }
    }
}
