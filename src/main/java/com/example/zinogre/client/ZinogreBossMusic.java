package com.example.zinogre.client;

import com.example.zinogre.ZinogreEntity;
import com.example.zinogre.ZinogreMod;
import com.example.zinogre.ZinogreSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Plays the boss music, on a loop, while a living Alpha Zinogre is close by (fading in over two seconds, and out over
 * four once it's dead or left behind). Vanilla's background music is held off meanwhile. It's on the Music volume.
 */
@EventBusSubscriber(modid = ZinogreMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ZinogreBossMusic {
    /** How close an Alpha has to be for its music to play (blocks). */
    private static final double RANGE = 64.0;
    private static final float FADE_IN = 1.0F / 40, FADE_OUT = 1.0F / 80;

    @Nullable
    private static Track track;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean fight = mc.player != null && mc.level != null && !mc.player.isSpectator()
                && !mc.level.getEntitiesOfClass(ZinogreEntity.class, mc.player.getBoundingBox().inflate(RANGE),
                        z -> z.isAlpha() && z.isAlive() && z.deathTime == 0).isEmpty();
        if (track != null && (track.isStopped() || !mc.getSoundManager().isActive(track))) {
            track = null;
        }
        if (fight && track == null) {
            track = new Track();
            mc.getSoundManager().play(track);
        }
        if (track != null) {
            track.fight = fight;
        }
    }

    /** No vanilla music while the boss music is on. */
    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        if (track != null) {
            event.setMusic(null);
        }
    }

    private static final class Track extends AbstractTickableSoundInstance {
        boolean fight = true;
        private float fade;

        Track() {
            super(ZinogreSounds.BOSS_MUSIC.get(), SoundSource.MUSIC, RandomSource.create());
            looping = true;
            delay = 0;
            relative = true; // not from anywhere: the same in both ears wherever you look
            attenuation = SoundInstance.Attenuation.NONE;
            volume = 0.001F;
        }

        @Override
        public void tick() {
            fade = fight ? Math.min(1.0F, fade + FADE_IN) : fade - FADE_OUT;
            if (fade <= 0.0F) {
                stop();
                return;
            }
            volume = Math.max(0.001F, fade);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    private ZinogreBossMusic() {}
}
