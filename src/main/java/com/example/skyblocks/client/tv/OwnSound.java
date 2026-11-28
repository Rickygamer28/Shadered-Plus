package com.example.skyblocks.client.tv;

import com.example.skyblocks.SkyBlocksMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTThreadLocalContext;

/**
 * The screens' own sound: a second OpenAL context on the game's sound device, so what they play never competes with
 * the game's own sounds for sources - where lots of things make sounds at once (a farm, many blocks and items), the
 * game can use every source it has and a tablet or television still gets its own. Made current only on this thread
 * and only while the screens' sound is being worked on (the game's own calls, before and after, go to its context as
 * ever). Where the sound library can't do that, everything goes to the game's context as before.
 */
final class OwnSound {
    private static final int AL_SOURCE_DISTANCE_MODEL = 0x200, AL_LINEAR_DISTANCE = 0xD003;
    private static long context, device;
    private static boolean unsupported;

    /** Into the screens' context (on this thread); what to give {@link #leave} after. */
    static long enter() {
        if (unsupported) {
            return -1;
        }
        try {
            long previous = EXTThreadLocalContext.alcGetThreadContext();
            if (previous == context && context != 0) {
                return -2; // (already in it)
            }
            long game = ALC10.alcGetCurrentContext();
            long dev = game == 0 ? 0 : ALC10.alcGetContextsDevice(game);
            if (dev == 0) {
                return -1; // (the game has no sound right now)
            }
            if (context == 0 || dev != device) {
                // (first time, or the game's sound was restarted - its device, and every context on it, gone)
                if (!ALC10.alcIsExtensionPresent(0, "ALC_EXT_thread_local_context") && !ALC10.alcIsExtensionPresent(dev, "ALC_EXT_thread_local_context")) {
                    unsupported = true;
                    SkyBlocksMod.LOGGER.info("Shadered+ television: its sound shares the game's (no ALC_EXT_thread_local_context)");
                    return -1;
                }
                context = ALC10.alcCreateContext(dev, (java.nio.IntBuffer) null);
                device = dev;
                if (context == 0) {
                    unsupported = true;
                    SkyBlocksMod.LOGGER.info("Shadered+ television: its sound shares the game's (no context of its own could be made)");
                    return -1;
                }
                if (!EXTThreadLocalContext.alcSetThreadContext(context)) {
                    context = 0;
                    return -1;
                }
                AL10.alEnable(AL_SOURCE_DISTANCE_MODEL);
                AL10.alDistanceModel(AL_LINEAR_DISTANCE);
                AL10.alGetError();
                SkyBlocksMod.LOGGER.info("Shadered+ television: its sound has a context of its own");
                return previous;
            }
            if (!EXTThreadLocalContext.alcSetThreadContext(context)) {
                context = 0; // (gone with its device: made again next time)
                return -1;
            }
            return previous;
        } catch (Throwable e) {
            unsupported = true;
            SkyBlocksMod.LOGGER.info("Shadered+ television: its sound shares the game's ({})", e.toString());
            return -1;
        }
    }

    static void leave(long previous) {
        if (previous >= 0) {
            EXTThreadLocalContext.alcSetThreadContext(previous);
        }
    }

    /** Its listener where the game's is: at the camera, facing as it does, as loud as the master volume. */
    static void listener() {
        long t = enter();
        if (t == -1) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            Camera c = mc.gameRenderer.getMainCamera();
            var p = c.getPosition();
            var look = c.getLookVector();
            var up = c.getUpVector();
            AL10.alListener3f(AL10.AL_POSITION, (float) p.x, (float) p.y, (float) p.z);
            AL10.alListenerfv(AL10.AL_ORIENTATION, new float[] {look.x(), look.y(), look.z(), up.x(), up.y(), up.z()});
            AL10.alListenerf(AL10.AL_GAIN, mc.options.getSoundSourceVolume(SoundSource.MASTER));
            AL10.alGetError();
        } finally {
            leave(t);
        }
    }

    private OwnSound() {}
}
