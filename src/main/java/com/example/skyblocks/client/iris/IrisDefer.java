package com.example.skyblocks.client.iris;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * With a shader pack on, Shadered+'s block entities that draw with shaders of their own (lava lamps, tanks, black holes,
 * whirlpools ...) aren't drawn into the pack's buffers - the pack overwrites or relights what such shaders write, and
 * holds every draw to the end, with the last one's uniforms: they came out invisible, or wrong. They're noted down
 * instead (mixin/BlockEntityTimingMixin), and once the world's block entities are done drawn into the cats' target
 * (IrisIslandTarget), each right away with its own uniforms, laid over the pack's image where nothing is in front.
 * In the pack's shadow pass they're skipped (no shadow of their own).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class IrisDefer {
    private record Job(BlockEntity be, float partialTick, Matrix4f pose, Matrix3f normal, int light) {}

    private static final List<Job> JOBS = new ArrayList<>();
    private static MultiBufferSource.BufferSource own;
    private static boolean drawing;

    private IrisDefer() {}

    /** Whether the block entities drawn now are drawn later (true: the caller skips drawing it now). */
    public static boolean defer(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        if (drawing || !IrisCompat.shaderPackInUse() || !com.example.skyblocks.client.DrawNow.isIris(buffers) || !kind(be)) {
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
                    LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())));
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
                || be instanceof com.example.skyblocks.EffectBlockEntity
                || be instanceof com.example.skyblocks.WeatherGlobeBlockEntity
                || be instanceof com.example.skyblocks.LavaPillarBlockEntity
                || be instanceof com.example.skyblocks.LightBridgeEmitterBlockEntity
                || be instanceof com.example.skyblocks.ArcadeBlockEntity
                || be.getBlockState().getBlock() instanceof com.example.skyblocks.BigScreenBlock
                || be.getBlockState().getBlock() instanceof com.example.skyblocks.WaterfallBlock
                || be instanceof com.example.skyblocks.SolarSystemBlockEntity
                || be instanceof com.example.skyblocks.SingularityBlockEntity
                || be instanceof com.example.skyblocks.HologramBlockEntity
                || be instanceof com.example.skyblocks.KoiPondBlockEntity
                || be instanceof com.example.skyblocks.NeonSignBlockEntity
                || be instanceof com.example.skyblocks.PowerCoreBlockEntity
                || be instanceof com.example.skyblocks.DarkFountainBlockEntity
                || be instanceof com.example.skyblocks.FilterBlockEntity
                || be.getBlockState().getBlock() instanceof com.example.skyblocks.BottomlessPitBlock
                || be.getBlockState().getBlock() instanceof com.example.skyblocks.RoomWindowBlock
                || be.getBlockState().getBlock() instanceof com.example.skyblocks.MoonJarBlock
                || be.getBlockState().getBlock() instanceof com.example.skyblocks.SunJarBlock
                || be instanceof com.example.skyblocks.TimeCrystalBlockEntity
                || be instanceof com.example.skyblocks.StationBlockEntity
                || be instanceof com.example.skyblocks.ElevatorBaseBlockEntity
                || be instanceof com.example.skyblocks.HourglassBlockEntity
                || be.getBlockState().getBlock() instanceof com.example.skyblocks.PrismBlock
                || be.getBlockState().getBlock() instanceof com.example.skyblocks.PhaseBlock
                || be instanceof com.example.skyblocks.ChalkRuneBlockEntity;
    }

    /**
     * A part of a block entity drawn later, over the pack's image (its renderer drew the rest now, into the pack's
     * buffers; when it's called again, drawing() is true: then only that part) - the spin pad's aura, say.
     */
    public static void deferPart(BlockEntity be, float partialTick, PoseStack pose) {
        if (drawing || !IrisCompat.shaderPackInUse() || IrisCompat.renderingShadowPass() || be.getLevel() == null || JOBS.size() >= 4096) {
            return;
        }
        JOBS.add(new Job(be, partialTick, new Matrix4f(pose.last().pose()), new Matrix3f(pose.last().normal()),
                LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())));
    }

    /** Something else drawn over the pack's image with the block entities (trains: client/TrainRenderer), now camera-relative. */
    private static final List<Runnable> EXTRA = new ArrayList<>();

    public static void extra(Runnable draw) {
        if (!drawing && IrisCompat.shaderPackInUse() && !IrisCompat.renderingShadowPass() && EXTRA.size() < 256) {
            EXTRA.add(draw);
        }
    }

    /** Whether the noted-down ones are being drawn now (in the cats' target). */
    public static boolean drawing() {
        return drawing;
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || (JOBS.isEmpty() && EXTRA.isEmpty())) {
            return;
        }
        List<Job> jobs = new ArrayList<>(JOBS);
        JOBS.clear();
        List<Runnable> extra = new ArrayList<>(EXTRA);
        EXTRA.clear();
        if (!IrisCompat.shaderPackInUse()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (own == null) {
            own = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 20));
        }
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        IrisIslandTarget.drawInCatTarget(() -> {
            Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            VertexSorting savedSorting = RenderSystem.getVertexSorting();
            Matrix4fStack stack = RenderSystem.getModelViewStack();
            stack.pushMatrix();
            drawing = true;
            try {
                RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
                stack.set(view);
                RenderSystem.applyModelViewMatrix();
                for (Runnable r : extra) {
                    try {
                        r.run();
                    } catch (RuntimeException e) {
                        warnOnce(e);
                    }
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(true);
                    RenderSystem.enableCull();
                }
                for (Job job : jobs) {
                    if (job.be().isRemoved()) {
                        continue;
                    }
                    BlockEntityRenderer<BlockEntity> renderer = mc.getBlockEntityRenderDispatcher().getRenderer(job.be());
                    if (renderer == null) {
                        continue;
                    }
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
                stack.popMatrix();
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
