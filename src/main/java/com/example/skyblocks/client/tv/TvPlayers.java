package com.example.skyblocks.client.tv;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TelevisionBlock;
import com.example.skyblocks.TelevisionBlockEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;

/** The televisions playing on this computer (by the bottom left block of each): kept going every frame, let go far away. */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TvPlayers {
    /** How far away a television stops playing (blocks). */
    private static final double FAR = 96;
    private static final Map<BlockPos, VideoPlayer> PLAYERS = new HashMap<>();
    private static Level level;
    /** Where playing just failed, and when (not tried again for a while). */
    private static final Map<BlockPos, Long> FAILED = new HashMap<>();
    private static boolean broken;

    /** Its player (started if it has something to play), or null. */
    @Nullable
    public static VideoPlayer get(TelevisionBlockEntity be) {
        VideoPlayer p = PLAYERS.get(be.getBlockPos());
        if (p == null && !be.source().isEmpty() && !broken && be.getLevel() != null
                && System.currentTimeMillis() - FAILED.getOrDefault(be.getBlockPos(), 0L) > 5000) {
            try {
                p = new VideoPlayer(be.getBlockPos().immutable());
            } catch (Throwable e) {
                broken = true; // (FFmpeg not loadable on this computer: shown on the screen, not tried again)
                SkyBlocksMod.LOGGER.error("Shadered+ television: can't play videos on this computer", e);
                return null;
            }
            PLAYERS.put(be.getBlockPos().immutable(), p);
        }
        return p;
    }

    /** Whether videos can't play here (FFmpeg failed to load). */
    public static boolean broken() {
        return broken;
    }

    @Nullable
    public static VideoPlayer existing(BlockPos pos) {
        return PLAYERS.get(pos);
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            releaseAll();
            level = mc.level;
        }
        if (mc.level == null || PLAYERS.isEmpty()) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Iterator<Map.Entry<BlockPos, VideoPlayer>> it = PLAYERS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, VideoPlayer> e = it.next();
            BlockPos pos = e.getKey();
            BlockState st = mc.level.getBlockState(pos);
            if (!(mc.level.getBlockEntity(pos) instanceof TelevisionBlockEntity be) || be.source().isEmpty() || !(st.getBlock() instanceof TelevisionBlock)
                    || pos.getCenter().distanceToSqr(cam) > FAR * FAR || !TelevisionBlock.isAnchor(mc.level, pos, st.getValue(TelevisionBlock.FACING))) {
                e.getValue().release();
                it.remove();
                continue;
            }
            Direction facing = st.getValue(TelevisionBlock.FACING);
            int[] size = TelevisionBlock.size(mc.level, pos, facing);
            Vec3 centre = Vec3.atLowerCornerOf(pos).add(0.5, size[1] / 2.0, 0.5)
                    .add(Vec3.atLowerCornerOf(TelevisionBlock.right(facing).getNormal()).scale((size[0] - 1) / 2.0));
            try {
                e.getValue().update(be, centre, 20.0F + 2.0F * Math.max(size[0], size[1]), pt);
            } catch (Throwable ex) {
                SkyBlocksMod.LOGGER.error("Shadered+ television: playing failed", ex);
                FAILED.put(pos, System.currentTimeMillis());
                e.getValue().release();
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        releaseAll();
    }

    private static void releaseAll() {
        PLAYERS.values().forEach(VideoPlayer::release);
        PLAYERS.clear();
        FAILED.clear();
    }

    private TvPlayers() {}
}
