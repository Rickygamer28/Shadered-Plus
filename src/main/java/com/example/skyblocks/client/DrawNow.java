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
        return buffers != null && buffers.getClass().getName().startsWith("net.irisshaders.");
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

    /** A source that draws on endBatch: {@code buffers} itself if it does, ours instead of Iris's, else null. */
    @Nullable
    public static MultiBufferSource.BufferSource of(MultiBufferSource buffers) {
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            return source;
        }
        if (isIris(buffers)) {
            if (own == null) {
                own = MultiBufferSource.immediate(new BufferBuilder(1 << 18));
            }
            return own;
        }
        return null;
    }
}
