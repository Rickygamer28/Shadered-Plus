package com.example.skyblocks.client.tv;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.ModItems;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TabletPayload;
import com.example.skyblocks.TabletState;
import com.example.skyblocks.TelevisionBlock;
import com.example.skyblocks.TelevisionBlockEntity;
import com.example.skyblocks.TelevisionPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The screens playing on this computer: televisions (by the bottom left block of each) and tablets held by anyone
 * near (by the tablet's id) - kept going every frame, let go when gone or far away.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TvPlayers {
    /** How far away a screen stops playing (blocks). */
    private static final double FAR = 96, FAR_TABLET = 48;
    private static final Map<Object, VideoPlayer> PLAYERS = new HashMap<>();
    private static Level level;
    /** Where playing just failed, and when (not tried again for a while). */
    private static final Map<Object, Long> FAILED = new HashMap<>();

    /** A television's player (started if it has something to play), or null. */
    @Nullable
    public static VideoPlayer get(TelevisionBlockEntity be) {
        BlockPos key = be.getBlockPos().immutable();
        VideoPlayer p = PLAYERS.get(key);
        if (p == null && !be.source().isEmpty() && be.getLevel() != null && System.currentTimeMillis() - FAILED.getOrDefault(key, 0L) > 5000) {
            p = new VideoPlayer(Long.toHexString(key.asLong()));
            PLAYERS.put(key, p);
        }
        return p;
    }

    /** Whether videos can't play here (never: kept for the renderer's message). */
    public static boolean broken() {
        return false;
    }

    @Nullable
    public static VideoPlayer existing(BlockPos pos) {
        return PLAYERS.get(pos);
    }

    /** A tablet's player, if it's playing. */
    @Nullable
    public static VideoPlayer tablet(UUID id) {
        return PLAYERS.get(id);
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            releaseAll();
            level = mc.level;
        }
        if (mc.level == null) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // the tablets held near: started, and where they are
        Set<UUID> held = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e.distanceToSqr(cam) > FAR_TABLET * FAR_TABLET) {
                continue;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = living.getItemInHand(hand);
                TabletState s = stack.is(ModItems.TABLET.get()) ? stack.get(ModComponents.TABLET.get()) : null;
                if (s == null || s.source().isEmpty() || held.contains(s.id())) {
                    continue;
                }
                held.add(s.id());
                VideoPlayer p = PLAYERS.get(s.id());
                if (p == null && System.currentTimeMillis() - FAILED.getOrDefault(s.id(), 0L) > 5000) {
                    p = new VideoPlayer("tablet_" + s.id().toString().replace("-", ""));
                    PLAYERS.put(s.id(), p);
                }
                if (p == null) {
                    continue;
                }
                // (the one holding it asks for its playlist's next; nobody else can)
                boolean mine = e == mc.player;
                int h = hand == InteractionHand.OFF_HAND ? 1 : 0;
                VideoPlayer.Sender send = mine ? (a, t, v) -> PacketDistributor.sendToServer(new TabletPayload(h, a, t, v)) : (a, t, v) -> { };
                try {
                    p.update(s.view(mc.level), send, e.getEyePosition(pt).add(0, -0.3, 0), 16.0F, pt);
                } catch (Throwable ex) {
                    SkyBlocksMod.LOGGER.error("Shadered+ tablet: playing failed", ex);
                    FAILED.put(s.id(), System.currentTimeMillis());
                    p.release();
                    PLAYERS.remove(s.id());
                }
            }
        }
        for (Iterator<Map.Entry<Object, VideoPlayer>> it = PLAYERS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Object, VideoPlayer> e = it.next();
            if (e.getKey() instanceof UUID id) {
                if (!held.contains(id)) { // (put away, or out of reach)
                    e.getValue().release();
                    it.remove();
                }
                continue;
            }
            BlockPos pos = (BlockPos) e.getKey();
            BlockState st = mc.level.getBlockState(pos);
            if (st.getBlock() instanceof com.example.skyblocks.ProjectorBlock && mc.level.getBlockEntity(pos) instanceof TelevisionBlockEntity pbe
                    && !pbe.source().isEmpty() && pos.getCenter().distanceToSqr(cam) < FAR * FAR) {
                // (a projector: its sound from where its picture lands)
                net.minecraft.world.phys.BlockHitResult hit = com.example.skyblocks.ProjectorBlock.screen(mc.level, pos,
                        st.getValue(com.example.skyblocks.ProjectorBlock.FACING));
                Vec3 centre = hit != null ? hit.getLocation() : pos.getCenter();
                e.getValue().speakers(speakersNear(mc, centre));
                try {
                    e.getValue().update(pbe, (a, t, v) -> PacketDistributor.sendToServer(new TelevisionPayload(pos, a, t, v)), centre, 32.0F, pt);
                } catch (Throwable ex) {
                    SkyBlocksMod.LOGGER.error("Shadered+ projector: playing failed", ex);
                    FAILED.put(pos, System.currentTimeMillis());
                    e.getValue().release();
                    it.remove();
                }
                continue;
            }
            if (!(mc.level.getBlockEntity(pos) instanceof TelevisionBlockEntity be) || be.source().isEmpty() || !(st.getBlock() instanceof TelevisionBlock)
                    || pos.getCenter().distanceToSqr(cam) > FAR * FAR || !TelevisionBlock.isAnchor(mc.level, pos, st.getValue(TelevisionBlock.FACING))) {
                e.getValue().release();
                it.remove();
                continue;
            }
            com.example.skyblocks.TvWall wall = com.example.skyblocks.TvWall.of(mc.level, pos);
            Vec3 centre = wall.centre();
            int[] size = {wall.width(), wall.height()};
            e.getValue().speakers(speakersNear(mc, centre)); // (the speakers within 24 blocks of it play it too)
            try {
                e.getValue().update(be, (a, t, v) -> PacketDistributor.sendToServer(new TelevisionPayload(pos, a, t, v)), centre,
                        20.0F + 2.0F * Math.max(size[0], size[1]), pt);
            } catch (Throwable ex) {
                SkyBlocksMod.LOGGER.error("Shadered+ television: playing failed", ex);
                FAILED.put(pos, System.currentTimeMillis());
                e.getValue().release();
                it.remove();
            }
        }
    }

    private static java.util.List<Vec3> speakersNear(Minecraft mc, Vec3 centre) {
        java.util.List<Vec3> speakers = new java.util.ArrayList<>();
        for (Iterator<BlockPos> sp = com.example.skyblocks.SpeakerBlockEntity.LOADED.iterator(); sp.hasNext(); ) {
            BlockPos spk = sp.next();
            if (!(mc.level.getBlockEntity(spk) instanceof com.example.skyblocks.SpeakerBlockEntity)) {
                sp.remove();
            } else if (spk.getCenter().distanceToSqr(centre) < 24 * 24 && speakers.size() < 8) {
                speakers.add(spk.getCenter());
            }
        }
        return speakers;
    }

    /** How hard the beat of the screens playing within this distance hits (the hardest); -1 none playing there. */
    public static float beatNear(Vec3 pos, double distance) {
        float best = -1;
        for (VideoPlayer p : PLAYERS.values()) {
            if (p.state() == VideoPlayer.State.PLAYING && p.at().distanceToSqr(pos) < distance * distance) {
                best = Math.max(best, p.beat());
            }
        }
        return best;
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
