package com.example.skyblocks.client.island;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Moves vertices by a fixed offset. Minecraft's fluid renderer places vertices inside the 16x16x16 chunk section
 * (block coordinates & 15); this puts them back at their island position.
 */
final class OffsetVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float dx, dy, dz;

    OffsetVertexConsumer(VertexConsumer delegate, float dx, float dy, float dz) {
        this.delegate = delegate;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x + dx, y + dy, z + dz);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}
