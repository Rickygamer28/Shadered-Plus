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
        if (!PLAYERS.isEmpty()) {
            OwnSound.listener(); // (the screens' sound heard from where the game's is)
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // the tablets held near: started, and where they are
        Set<UUID> held = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e.distanceToSqr(cam) > FAR_TABLET * FAR_TABLET) {
                continue;
            }
            // (in a hand - and your own anywhere in your inventory: it keeps playing put away)
            int slots = e == mc.player ? 2 + mc.player.getInventory().getContainerSize() : 2;
            for (int h = 0; h < slots; h++) {
                ItemStack stack = h < 2 ? living.getItemInHand(h == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)
                        : mc.player.getInventory().getItem(h - 2);
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
                int slot = h;
                VideoPlayer.Sender send = mine ? (a, t, v) -> PacketDistributor.sendToServer(new TabletPayload(slot, a, t, v)) : (a, t, v) -> { };
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
                if (!held.contains(id)) { // (thrown away, or out of reach - for a moment: a stack moved isn't gone)
                    if (gone(id, "its tablet isn't held")) {
                        e.getValue().release();
                        it.remove();
                    }
                } else {
                    MISSING.remove(id);
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
                if (gone(pos, !(mc.level.getBlockEntity(pos) instanceof TelevisionBlockEntity b2) ? "its television is gone"
                        : b2.source().isEmpty() ? "it was stopped" : pos.getCenter().distanceToSqr(cam) > FAR * FAR ? "too far"
                        : "no longer its wall's first")) {
                    e.getValue().release();
                    it.remove();
                }
                continue;
            }
            MISSING.remove(pos);
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

    private static final Map<Object, Long> MISSING = new HashMap<>();

    /** Whether a player has been without its screen for over a second (then it's let go: true, once). */
    private static boolean gone(Object key, String why) {
        long now = System.currentTimeMillis();
        long since = MISSING.computeIfAbsent(key, k -> now);
        if (now - since < 1000 && !why.equals("it was stopped")) {
            return false;
        }
        MISSING.remove(key);
        SkyBlocksMod.LOGGER.info("Shadered+ television: let go of a player ({})", why);
        return true;
    }

    private static final float[] HEARD = new float[16];

    /** The 16 bands of what the screens near the camera play, as loud as each is heard here (0-1). */
    public static float[] bandsHeard() {
        java.util.Arrays.fill(HEARD, 0);
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return HEARD;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (VideoPlayer p : PLAYERS.values()) {
            if (p.state() != VideoPlayer.State.PLAYING) continue;
            float w = (float) Math.max(0, 1 - p.at().distanceTo(cam) / 32.0);
            float[] b = p.bands();
            for (int i = 0; i < 16; i++) HEARD[i] = Math.max(HEARD[i], b[i] * w);
        }
        return HEARD;
    }

    private static final float[] NEAR = new float[16];

    /** The 16 bands of what plays near a place (0-1, fainter further off; up to {@code dist} blocks). Shared: copy to keep. */
    public static float[] bandsNear(Vec3 at, double dist) {
        java.util.Arrays.fill(NEAR, 0);
        for (VideoPlayer p : PLAYERS.values()) {
            if (p.state() != VideoPlayer.State.PLAYING) continue;
            float w = (float) Math.max(0, 1 - p.at().distanceTo(at) / dist);
            if (w <= 0) continue;
            float[] b = p.bands();
            for (int i = 0; i < 16; i++) NEAR[i] = Math.max(NEAR[i], b[i] * w);
        }
        return NEAR;
    }

    /** The speakers within 24 blocks of a screen (up to 8): they play its sound too. */
    private static java.util.List<Vec3> speakersNear(Minecraft mc, Vec3 centre) {
        java.util.List<Vec3> speakers = new java.util.ArrayList<>();
        synchronized (com.example.skyblocks.SpeakerBlockEntity.CLIENT_SPEAKERS) {
            for (BlockPos spk : com.example.skyblocks.SpeakerBlockEntity.CLIENT_SPEAKERS) {
                if (spk.getCenter().distanceToSqr(centre) < 24 * 24 && speakers.size() < 8) {
                    speakers.add(spk.getCenter());
                }
            }
        }
        return speakers;
    }

    /** How hard the beat of the screens playing near the camera hits, fading with distance as their sound does (0-1). */
    public static float beatHeard() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return 0;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float best = 0;
        for (VideoPlayer p : PLAYERS.values()) {
            if (p.state() == VideoPlayer.State.PLAYING) {
                double d = p.at().distanceTo(cam);
                best = Math.max(best, p.beat() * (float) Math.max(0, 1 - d / 32.0));
            }
        }
        return best;
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
        MISSING.clear();
    }

    private TvPlayers() {}
}
