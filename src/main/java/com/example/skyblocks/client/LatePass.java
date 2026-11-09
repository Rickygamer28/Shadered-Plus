package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Light drawn over the world without hiding what's behind it (party lasers): not among the block entities, where
 * Shadered's sky blocks and models (the cats, the disco ball) drawn after them came out over it, but in Shadered's pass
 * once those are drawn (as the black holes are) - or, if that pass didn't run, at the end of the world's drawing. Each
 * with the view and projection it had among the block entities.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class LatePass {
    private record Job(Matrix4f modelView, Matrix4f projection, Consumer<MultiBufferSource.BufferSource> draw) {}

    private static final List<Job> JOBS = new ArrayList<>();

    private LatePass() {}

    /** Whether it'll be drawn later (true), or should be now (false: a shader pack, a mirror's reflection). */
    public static boolean later(Consumer<MultiBufferSource.BufferSource> draw) {
        if (com.example.skyblocks.client.iris.IrisCompat.shaderPackInUse() || MirrorRenderer.isRenderingReflection() || JOBS.size() >= 1024) {
            return false;
        }
        JOBS.add(new Job(new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix()), draw));
        return true;
    }

    /** Draws what's waiting, into whatever picture is being drawn now. */
    public static void drawPending() {
        if (JOBS.isEmpty()) {
            return;
        }
        List<Job> jobs = new ArrayList<>(JOBS);
        JOBS.clear();
        MultiBufferSource.BufferSource main = Minecraft.getInstance().renderBuffers().bufferSource();
        main.endBatch();
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.vertex.VertexSorting savedSorting = RenderSystem.getVertexSorting();
        org.joml.Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
            for (Job job : jobs) {
                RenderSystem.setProjectionMatrix(job.projection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
                stack.set(job.modelView);
                RenderSystem.applyModelViewMatrix();
                try {
                    job.draw.accept(main);
                } catch (RuntimeException ignored) {
                    // (left out this frame)
                }
                main.endBatch();
            }
        } finally {
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        }
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            JOBS.clear(); // (anything a frame left behind)
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER && !MirrorRenderer.isRenderingReflection()) {
            drawPending();
        }
    }
}
