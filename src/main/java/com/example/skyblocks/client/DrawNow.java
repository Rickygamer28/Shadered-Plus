package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;

/**
 * A buffer source that draws when told to (endBatch), for renderers that set a shader's uniforms for one thing and draw
 * it right then. The game's own is one; with Iris installed, block entities get Iris's instead (it keeps everything to
 * draw later, sorted - even with no shader pack on, once one has been on), and things drawn from it come out with the
 * last one's uniforms, or not at all: those get a source of our own, drawn at once.
 */
public final class DrawNow {
    private static MultiBufferSource.BufferSource own;

    private DrawNow() {}

    /** Whether this is Iris's buffer source (everything drawn later, together). */
    public static boolean isIris(MultiBufferSource buffers) {
        if (buffers == null) {
            return false;
        }
        String n = buffers.getClass().getName();
        return n.startsWith("net.irisshaders.") || n.startsWith("net.coderbot.");
    }

    /** {@code buffers}, or (Iris's) ours that draws on endBatch. */
    public static MultiBufferSource into(MultiBufferSource buffers) {
        return isIris(buffers) ? of(buffers) : buffers;
    }

    /** If {@code buffers} is ours: everything still in it drawn now. */
    public static void finish(MultiBufferSource buffers) {
        if (buffers != null && buffers == own) {
            own.endBatch();
        }
    }

    /**
     * A source that draws on endBatch: ours instead of Iris's (which may be a game buffer source itself, that only
     * pretends to draw on endBatch - so it's looked for first), {@code buffers} itself if it draws, else null.
     */
    @Nullable
    public static MultiBufferSource.BufferSource of(MultiBufferSource buffers) {
        if (isIris(buffers)) {
            return own();
        }
        return buffers instanceof MultiBufferSource.BufferSource source ? source : null;
    }

    /** A source that draws on endBatch, whatever {@code buffers} is: it, if it does; else ours. */
    public static MultiBufferSource.BufferSource now(MultiBufferSource buffers) {
        MultiBufferSource.BufferSource s = of(buffers);
        if (s == null) {
            // (the game's own - unless Iris has put its own in its place for the level's drawing)
            s = of(net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource());
        }
        return s != null ? s : own();
    }

    private static MultiBufferSource.BufferSource own() {
        if (own == null) {
            own = MultiBufferSource.immediate(new BufferBuilder(1 << 18));
        }
        return own;
    }
}
