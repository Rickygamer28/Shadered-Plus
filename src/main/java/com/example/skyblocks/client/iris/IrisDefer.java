package com.example.skyblocks.client.iris;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * With a shader pack on, Shadered+'s block entities that draw with shaders of their own (lava lamps, tanks, black holes,
 * whirlpools ...) aren't drawn into the pack's buffers - the pack overwrites or relights what such shaders write, and
 * holds every draw to the end, with the last one's uniforms: they came out invisible, or wrong. They're noted down
 * instead (mixin/BlockEntityTimingMixin), and once the world's block entities are done drawn into the cats' target
 * (IrisIslandTarget), each right away with its own uniforms, laid over the pack's image where nothing is in front.
 * In the pack's shadow pass they're skipped (no shadow of their own).
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class IrisDefer {
    private record Job(BlockEntity be, float partialTick, Matrix4f pose, Matrix3f normal, int light, Matrix4f modelView, Matrix4f projection) {}

    private static final List<Job> JOBS = new ArrayList<>();
    /** (The render test: as before this, to compare.) */
    private static final boolean NO_DEFER = System.getenv("SKYBLOCKS_NO_DEFER") != null;
    private static MultiBufferSource.BufferSource own;
    private static boolean drawing;

    private IrisDefer() {}

    /** Whether the block entities drawn now are drawn later (true: the caller skips drawing it now). */
    public static boolean defer(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        if (drawing || NO_DEFER || !IrisCompat.shaderPackInUse() || !com.example.skyblocks.client.DrawNow.isIris(buffers) || !kind(be)) {
            return false;
        }
        if (IrisCompat.renderingShadowPass()) {
            return true; // (no shadow)
        }
        BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be);
        if (renderer == null || !renderer.shouldRender(be, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())) {
            return true; // (too far: not drawn, as the game wouldn't)
        }
        if (JOBS.size() < 4096 && be.getLevel() != null) {
            JOBS.add(new Job(be, partialTick, new Matrix4f(pose.last().pose()), new Matrix3f(pose.last().normal()),
                    LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos()),
                    new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix())));
        }
        return true;
    }

    /** Ours, drawing with shaders of their own. */
    private static boolean kind(BlockEntity be) {
        if (be.getBlockState().getBlock() instanceof com.example.skyblocks.TeslaCoilBlock) {
            return false; // (its arcs are the game's lightning: the pack draws them)
        }
        return be instanceof com.example.skyblocks.GlowColourBlockEntity
                || be instanceof com.example.skyblocks.SmallTankBlockEntity
                || be instanceof com.example.skyblocks.WhirlpoolBlockEntity
                || be instanceof com.example.skyblocks.KaleidoscopeBlockEntity
                || be instanceof com.example.skyblocks.SolarSystemBlockEntity
                || be instanceof com.example.skyblocks.SingularityBlockEntity
                || be instanceof com.example.skyblocks.HologramBlockEntity
                || be instanceof com.example.skyblocks.KoiPondBlockEntity
                || be instanceof com.example.skyblocks.NeonSignBlockEntity;
    }

    /** Whether the noted-down ones are being drawn now (in the cats' target). */
    public static boolean drawing() {
        return drawing;
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || JOBS.isEmpty()) {
            return;
        }
        List<Job> jobs = new ArrayList<>(JOBS);
        JOBS.clear();
        if (!IrisCompat.shaderPackInUse()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (own == null) {
            own = MultiBufferSource.immediate(new BufferBuilder(1 << 20));
        }
        IrisIslandTarget.drawInCatTarget(() -> {
            // (1.20.1: each with the model-view and projection it was noted down with - its pose holds the camera's turn)
            Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            VertexSorting savedSorting = RenderSystem.getVertexSorting();
            PoseStack stack = RenderSystem.getModelViewStack();
            stack.pushPose();
            drawing = true;
            try {
                for (Job job : jobs) {
                    if (job.be().isRemoved()) {
                        continue;
                    }
                    BlockEntityRenderer<BlockEntity> renderer = mc.getBlockEntityRenderDispatcher().getRenderer(job.be());
                    if (renderer == null) {
                        continue;
                    }
                    RenderSystem.setProjectionMatrix(job.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
                    stack.setIdentity();
                    stack.mulPoseMatrix(job.modelView());
                    RenderSystem.applyModelViewMatrix();
                    PoseStack ps = new PoseStack();
                    ps.last().pose().set(job.pose());
                    ps.last().normal().set(job.normal());
                    try {
                        renderer.render(job.be(), job.partialTick(), ps, own, job.light(), OverlayTexture.NO_OVERLAY);
                    } catch (RuntimeException e) {
                        warnOnce(e);
                    }
                    own.endBatch(); // (each with its own uniforms)
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(true);
                    RenderSystem.enableCull();
                }
            } finally {
                drawing = false;
                stack.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            }
        });
    }

    private static boolean warned;

    private static void warnOnce(RuntimeException e) {
        if (!warned) {
            warned = true;
            SkyBlocksMod.LOGGER.warn("Shadered+: a block drawn over the shader pack failed", e);
        }
    }
}
