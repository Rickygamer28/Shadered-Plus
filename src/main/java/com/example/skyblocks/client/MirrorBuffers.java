package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<RenderType, BufferBuilder> kinds;
    private final List<RenderType> solid = new ArrayList<>(), seeThrough = new ArrayList<>();

    private MirrorBuffers(BufferBuilder shared, Map<RenderType, BufferBuilder> kinds) {
        super(shared, kinds);
        this.kinds = kinds;
    }

    static MirrorBuffers create() {
        return new MirrorBuffers(new BufferBuilder(1 << 18), new HashMap<>());
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (!kinds.containsKey(type) && kinds.size() < MAX_KINDS && type.canConsolidateConsecutiveGeometry()) {
            kinds.put(type, new BufferBuilder(1 << 12)); // (grows as needed)
            (type.toString().contains("translucent") ? seeThrough : solid).add(type);
        }
        return super.getBuffer(type);
    }

    @Override
    public void endBatch() {
        endLastBatch();
        for (RenderType type : solid) {
            endBatch(type);
        }
        for (RenderType type : seeThrough) { // (drawn last)
            endBatch(type);
        }
        super.endBatch();
    }
}
