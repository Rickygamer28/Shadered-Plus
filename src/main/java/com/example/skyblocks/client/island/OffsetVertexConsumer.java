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
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x + dx, y + dy, z + dz);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
