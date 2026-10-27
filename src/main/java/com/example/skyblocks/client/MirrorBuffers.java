package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Where mobs and block entities in a mirror put their vertices: every kind of render type gets its own buffer (kept
 * from picture to picture), so things are gathered up and drawn once per kind when the picture's batch ends.
 * <p>
 * Drawn straight away instead (one shared buffer, as before), every switch from one kind to another drew what was
 * gathered so far: a pond block's lily pad, its frog and its water were three draws each, for every block of it, in
 * every mirror - most of the time mirrors spent on block entities. See-through kinds are drawn after the others.
 */
final class MirrorBuffers extends MultiBufferSource.BufferSource {
    /** At most this many kinds get their own buffer (mob textures each make a kind); the rest share one, as before. */
    private static final int MAX_KINDS = 64;
    private final LinkedHashMap<RenderType, ByteBufferBuilder> kinds;

    private MirrorBuffers(ByteBufferBuilder shared, LinkedHashMap<RenderType, ByteBufferBuilder> kinds) {
        super(shared, kinds);
        this.kinds = kinds;
    }

    static MirrorBuffers create() {
        return new MirrorBuffers(new ByteBufferBuilder(1 << 18), new LinkedHashMap<>());
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (!kinds.containsKey(type) && kinds.size() < MAX_KINDS && type.canConsolidateConsecutiveGeometry()) {
            ByteBufferBuilder memory = new ByteBufferBuilder(1 << 14); // (grows as needed)
            if (type.toString().contains("translucent")) {
                kinds.putLast(type, memory); // (drawn last)
            } else {
                kinds.putFirst(type, memory);
            }
        }
        return super.getBuffer(type);
    }
}
