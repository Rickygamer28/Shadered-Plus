package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.arcade.ArcadeGame.Sfx;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The arcade cabinet's sounds: played for whoever's playing (in their ears, on the Jukebox/Note Blocks volume), and
 * passed on with the cabinet's picture to everyone near it (who hear them from the cabinet). M turns them off and on
 * (remembered).
 */
public final class ArcadeSounds {
    private ArcadeSounds() {}

    private static boolean loaded, muted;
    /** When each sound last played (a burst of the same one in a frame or two: just once). */
    private static final long[] lastAt = new long[Sfx.values().length];
    /** What's played since the last picture went to the cabinet (for those near it). */
    private static final java.io.ByteArrayOutputStream sincePicture = new java.io.ByteArrayOutputStream();

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("skyblocks-arcade-sound.txt");
    }

    public static boolean muted() {
        if (!loaded) {
            loaded = true;
            try {
                muted = Files.exists(file()) && Files.readString(file()).trim().equals("off");
            } catch (Exception e) {
                muted = false;
            }
        }
        return muted;
    }

    public static void toggle() {
        muted = !muted();
        try {
            Files.writeString(file(), muted ? "off" : "on");
        } catch (Exception e) {
            SkyBlocksMod.LOGGER.warn("Couldn't save the arcade sound setting: {}", e.toString());
        }
        if (!muted) play(Sfx.SELECT, 1.0F, 1.0F);
    }

    private static ResourceLocation id(Sfx s) {
        return ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "arcade." + s.file());
    }

    /** The player's own game: in their ears. */
    public static void play(Sfx s, float pitch, float volume) {
        long now = System.nanoTime();
        if (now - lastAt[s.ordinal()] < 25_000_000L) return; // (25ms)
        lastAt[s.ordinal()] = now;
        if (sincePicture.size() < 24) sincePicture.write(s.ordinal());
        if (muted()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(new SimpleSoundInstance(id(s), SoundSource.RECORDS, s.volume * volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    public static void play(Sfx s) {
        play(s, 1.0F, 1.0F);
    }

    /** A music loop (on the Music volume), or none if the sound's off. */
    public static SoundInstance music(String name) {
        if (muted() || name == null) return null;
        SoundInstance s = new SimpleSoundInstance(ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "arcade." + name), SoundSource.MUSIC,
                0.55F, 1.0F, SoundInstance.createUnseededRandom(), true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        Minecraft mc = Minecraft.getInstance();
        mc.getMusicManager().stopPlaying(); // (the world's own music: quiet while the cabinet's plays)
        mc.getSoundManager().play(s);
        return s;
    }

    /** The sounds since the last picture, to go with it to the cabinet. */
    public static byte[] takeForPicture() {
        byte[] out = sincePicture.toByteArray();
        sincePicture.reset();
        return out;
    }

    /** Someone else's game: heard from their cabinet. */
    public static void playAt(BlockPos pos, byte[] ids) {
        if (muted() || ids.length == 0) return;
        Minecraft mc = Minecraft.getInstance();
        Sfx[] all = Sfx.values();
        for (byte b : ids) {
            int i = b & 255;
            if (i >= all.length) continue;
            Sfx s = all[i];
            mc.getSoundManager().play(new SimpleSoundInstance(id(s), SoundSource.RECORDS, s.volume * 0.7F, 1.0F,
                    SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, false));
        }
    }
}
