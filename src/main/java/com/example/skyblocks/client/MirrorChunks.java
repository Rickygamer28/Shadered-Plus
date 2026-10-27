package com.example.skyblocks.client;

import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;

/**
 * A mirror's own chunk sections without Sodium/Embeddium (1.21.1 draws each mirror's sections by itself). On 1.20.1
 * the game's chunk lists are different: mirrors draw the main view's chunk list (MirrorRenderer's fallback).
 */
final class MirrorChunks {
    private MirrorChunks() {}

    static boolean works() {
        return false;
    }

    static List<Object> sections(Frustum frustum) {
        return List.of();
    }

    static void build(List<Object> sections, long frame) {
    }

    static void draw(RenderType type, List<Object> sections, double x, double y, double z, Matrix4f modelView, Matrix4f projection) {
    }
}
