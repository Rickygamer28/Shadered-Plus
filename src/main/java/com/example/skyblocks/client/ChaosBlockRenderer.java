package com.example.skyblocks.client;

import com.example.skyblocks.ChaosBlock;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.iris.IrisCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Draws the chaos blocks' faces with the chaos shader (adapted from Draconic Evolution / BrandonsCore by brandon3055,
 * credited in the block's tooltip and CREDITS.md). With a shader pack they go into Shadered+'s overlay like the
 * infinity blocks.
 */
public final class ChaosBlockRenderer {
    private static final int SCAN_RADIUS = 4; // chunks
    private static final int RESCAN_TICKS = 20;
    private static final double RANGE = 128.0;
    static final ResourceLocation CHAOS_TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/effect/chaos_noise.png");

    private record Face(BlockPos pos, Direction side) {}

    static ShaderInstance shader;
    private static final List<Face> FACES = new ArrayList<>();
    private static boolean dirty = true;
    private static ClientLevel lastLevel;
    private static long lastScan;
    private static int lastChunkX, lastChunkZ;

    private ChaosBlockRenderer() {}

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "draconic_chaos"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    s -> shader = s);
        }
    }

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || MirrorRenderer.isRenderingReflection()
                    || IrisCompat.renderingShadowPass()) {
                return;
            }
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || shader == null) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            int chunkX = SectionPos.blockToSectionCoord(cam.x), chunkZ = SectionPos.blockToSectionCoord(cam.z);
            long time = level.getGameTime();
            if (dirty || level != lastLevel || time - lastScan >= RESCAN_TICKS || time < lastScan || chunkX != lastChunkX || chunkZ != lastChunkZ) {
                scan(level, chunkX, chunkZ);
                dirty = false;
                lastLevel = level;
                lastScan = time;
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
            }
            if (FACES.isEmpty()) {
                return;
            }
            Matrix4f view = new Matrix4f(event.getModelViewMatrix());
            Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            InfinityMirrorRenderer.scoped(view, projection, overlay -> {
                List<Runnable> shapes = new ArrayList<>();
                drawFaces(cam, view, projection, event.getCamera(), partialTick, !overlay, shapes);
                InfinityMirrorRenderer.recordShapes(overlay, shapes, 0.0F);
            });
        }
    }

    /** A block changed on the client: rescan if it's a chaos block or next to one. */
    static void blockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState.getBlock() instanceof ChaosBlock || newState.getBlock() instanceof ChaosBlock) {
            dirty = true;
            return;
        }
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            if (level.getBlockState(n.setWithOffset(pos, d)).getBlock() instanceof ChaosBlock) {
                dirty = true;
                return;
            }
        }
    }

    /** Inside a mirror's picture: the chaos blocks seen from the mirrored eye (its model-view and projection set). */
    static void drawInMirror(double ex, double ey, double ez, Matrix4f modelView, Matrix4f projection) {
        Minecraft mc = Minecraft.getInstance();
        if (FACES.isEmpty() || mc.level == null || shader == null) {
            return;
        }
        drawFaces(new Vec3(ex, ey, ez), modelView, projection, mc.gameRenderer.getMainCamera(), 0.0F, true, new ArrayList<>());
    }

    /** Sets the shader's animation and the camera's turn (the chaos is a sphere around you, turned with your view). */
    static void setUniforms(ShaderInstance s, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        float ticks = mc.level == null ? 0.0F : (float) (mc.level.getGameTime() % 240000L) + partialTick;
        s.safeGetUniform("Time").set(ticks);
        s.safeGetUniform("Yaw").set((float) Math.toRadians(camera.getYRot()));
        s.safeGetUniform("Pitch").set((float) -Math.toRadians(camera.getXRot()));
        s.safeGetUniform("Alpha").set(0.0F);
    }

    private static void drawFaces(Vec3 cam, Matrix4f modelView, Matrix4f projection, Camera camera, float partialTick, boolean writeDepth,
                                  List<Runnable> shapes) {
        Vec3 eye = cam.add(new Vec3(InfinityMirrorRenderer.eyeOf(projection, modelView)));
        FrustumIntersection seen = new FrustumIntersection(new Matrix4f(projection).mul(modelView));
        List<Face> shown = new ArrayList<>();
        for (Face f : FACES) {
            Direction d = f.side();
            double plane = f.pos().get(d.getAxis()) + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
            if ((d.getAxis().choose(eye.x, eye.y, eye.z) - plane) * d.getAxisDirection().getStep() <= 0.0
                    || f.pos().distToCenterSqr(eye) > RANGE * RANGE
                    || !seen.testAab((float) (f.pos().getX() - cam.x), (float) (f.pos().getY() - cam.y), (float) (f.pos().getZ() - cam.z),
                    (float) (f.pos().getX() + 1 - cam.x), (float) (f.pos().getY() + 1 - cam.y), (float) (f.pos().getZ() + 1 - cam.z))) {
                continue;
            }
            shown.add(f);
        }
        if (shown.isEmpty()) {
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(writeDepth);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2.0F, -20.0F); // over the block's own faces
        try {
            setUniforms(shader, camera, partialTick);
            RenderSystem.setShaderTexture(0, CHAOS_TEXTURE);
            RenderSystem.setShader(() -> shader);
            MeshData mesh = mesh(shown, cam);
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            shapes.add(() -> {
                MeshData shape = mesh(shown, cam);
                if (shape != null) {
                    BufferUploader.drawWithShader(shape);
                }
            });
        } finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.enableCull();
        }
    }

    private static MeshData mesh(List<Face> faces, Vec3 cam) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Face f : faces) {
            face(b, null, f.side(), (float) (f.pos().getX() - cam.x), (float) (f.pos().getY() - cam.y), (float) (f.pos().getZ() - cam.z));
        }
        return b.build();
    }

    /** One face of the block at (x, y, z) (with {@code pose}: transformed by it), corners counter-clockwise from outside. */
    static void face(VertexConsumer b, Matrix4f pose, Direction side, float x, float y, float z) {
        switch (side) {
            case UP -> quad(b, pose, x, y + 1, z, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z);
            case DOWN -> quad(b, pose, x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1);
            case NORTH -> quad(b, pose, x + 1, y, z, x, y, z, x, y + 1, z, x + 1, y + 1, z);
            case SOUTH -> quad(b, pose, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1);
            case WEST -> quad(b, pose, x, y, z, x, y, z + 1, x, y + 1, z + 1, x, y + 1, z);
            case EAST -> quad(b, pose, x + 1, y, z + 1, x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1);
        }
    }

    private static void quad(VertexConsumer b, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vertex(b, pose, x0, y0, z0, 0, 1);
        vertex(b, pose, x1, y1, z1, 1, 1);
        vertex(b, pose, x2, y2, z2, 1, 0);
        vertex(b, pose, x3, y3, z3, 0, 0);
    }

    private static void vertex(VertexConsumer b, Matrix4f pose, float x, float y, float z, float u, float v) {
        (pose == null ? b.addVertex(x, y, z) : b.addVertex(pose, x, y, z)).setUv(u, v).setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void scan(ClientLevel level, int centerX, int centerZ) {
        FACES.clear();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(centerX + dx, centerZ + dz, false);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof ChaosBlock)) {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!(section.getBlockState(x, y, z).getBlock() instanceof ChaosBlock)) {
                                    continue;
                                }
                                BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                                for (Direction d : Direction.values()) {
                                    n.setWithOffset(pos, d);
                                    BlockState next = level.getBlockState(n);
                                    if (next.getBlock() instanceof ChaosBlock || next.isSolidRender(level, n)) {
                                        continue; // (between two chaos blocks, or covered)
                                    }
                                    FACES.add(new Face(pos, d));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
