package com.example.skyblocks.client;

import com.example.skyblocks.DraconicEffectBlock;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.iris.IrisCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * Draws the Draconic effect blocks' faces (DraconicEffectBlock) with their shaders, adapted from Draconic Evolution
 * and BrandonsCore by brandon3055 (credited in the blocks' tooltips). Faces of the same effect are drawn together; with
 * a shader pack they go into Shadered+'s overlay like the infinity blocks.
 */
public final class DraconicEffectRenderer {
    private static final int SCAN_RADIUS = 4; // chunks
    private static final int RESCAN_TICKS = 20;
    private static final double RANGE = 128.0;
    private static final ResourceLocation CHAOS_TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/effect/chaos_noise.png");

    private record Face(BlockPos pos, Direction side, int tier) {}

    private static final Map<DraconicEffectBlock.Kind, ShaderInstance> SHADERS = new EnumMap<>(DraconicEffectBlock.Kind.class);
    private static final Map<DraconicEffectBlock.Kind, List<Face>> FACES = new EnumMap<>(DraconicEffectBlock.Kind.class);
    private static boolean dirty = true;
    private static ClientLevel lastLevel;
    private static long lastScan;
    private static int lastChunkX, lastChunkZ;

    private DraconicEffectRenderer() {}

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            for (DraconicEffectBlock.Kind kind : DraconicEffectBlock.Kind.values()) {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, kind.shader), DefaultVertexFormat.POSITION_TEX_COLOR),
                        shader -> SHADERS.put(kind, shader));
            }
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
            if (level == null || SHADERS.isEmpty()) {
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
                draw(cam, view, projection, event.getCamera(), partialTick, !overlay, shapes);
                InfinityMirrorRenderer.recordShapes(overlay, shapes, 0.0F);
            });
        }
    }

    /** A block changed on the client: rescan if it's one of ours or next to one. */
    static void blockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState.getBlock() instanceof DraconicEffectBlock || newState.getBlock() instanceof DraconicEffectBlock) {
            dirty = true;
            return;
        }
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            if (level.getBlockState(n.setWithOffset(pos, d)).getBlock() instanceof DraconicEffectBlock) {
                dirty = true;
                return;
            }
        }
    }

    /** Inside a mirror's picture: the effect blocks seen from the mirrored eye (its model-view and projection set). */
    static void drawInMirror(double ex, double ey, double ez, Matrix4f modelView, Matrix4f projection) {
        Minecraft mc = Minecraft.getInstance();
        if (FACES.isEmpty() || mc.level == null) {
            return;
        }
        drawFaces(new Vec3(ex, ey, ez), modelView, projection, mc.gameRenderer.getMainCamera(), 0.0F, true, new ArrayList<>());
    }

    private static void draw(Vec3 cam, Matrix4f view, Matrix4f projection, Camera camera, float partialTick, boolean writeDepth,
                             List<Runnable> shapes) {
        drawFaces(cam, view, projection, camera, partialTick, writeDepth, shapes);
    }

    private static void drawFaces(Vec3 cam, Matrix4f modelView, Matrix4f projection, Camera camera, float partialTick, boolean writeDepth,
                                  List<Runnable> shapes) {
        Minecraft mc = Minecraft.getInstance();
        float ticks = (float) (mc.level.getGameTime() % 240000L) + partialTick;
        Vec3 eye = cam.add(new Vec3(InfinityMirrorRenderer.eyeOf(projection, modelView)));
        FrustumIntersection seen = new FrustumIntersection(new Matrix4f(projection).mul(modelView));
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(writeDepth);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2.0F, -20.0F); // over the block's own faces
        try {
            for (Map.Entry<DraconicEffectBlock.Kind, List<Face>> e : FACES.entrySet()) {
                ShaderInstance shader = SHADERS.get(e.getKey());
                if (shader == null) {
                    continue;
                }
                DraconicEffectBlock.Kind kind = e.getKey();
                List<Face> shown = new ArrayList<>();
                for (Face f : e.getValue()) {
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
                    continue;
                }
                // (each effect's speed as Draconic Evolution runs it)
                shader.safeGetUniform("Time").set(switch (kind) {
                    case CHAOS -> ticks;
                    case ENERGY_CRYSTAL -> ticks / 50.0F;
                    default -> ticks / 20.0F;
                });
                if (kind == DraconicEffectBlock.Kind.CHAOS) {
                    shader.safeGetUniform("Yaw").set((float) Math.toRadians(camera.getYRot()));
                    shader.safeGetUniform("Pitch").set((float) -Math.toRadians(camera.getXRot()));
                    shader.safeGetUniform("Alpha").set(0.0F);
                    RenderSystem.setShaderTexture(0, CHAOS_TEXTURE);
                }
                RenderSystem.setShader(() -> shader);
                MeshData mesh = mesh(kind, shown, cam);
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                shapes.add(() -> {
                    MeshData shape = mesh(kind, shown, cam);
                    if (shape != null) {
                        BufferUploader.drawWithShader(shape);
                    }
                });
            }
        } finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.enableCull();
        }
    }

    private static MeshData mesh(DraconicEffectBlock.Kind kind, List<Face> faces, Vec3 cam) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Face f : faces) {
            float[] c = kind.colour(f.tier());
            float x = (float) (f.pos().getX() - cam.x), y = (float) (f.pos().getY() - cam.y), z = (float) (f.pos().getZ() - cam.z);
            switch (f.side()) {
                case UP -> quad(b, c, x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1);
                case DOWN -> quad(b, c, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z, x, y, z);
                case NORTH -> quad(b, c, x + 1, y, z, x, y, z, x, y + 1, z, x + 1, y + 1, z);
                case SOUTH -> quad(b, c, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1);
                case WEST -> quad(b, c, x, y, z, x, y, z + 1, x, y + 1, z + 1, x, y + 1, z);
                case EAST -> quad(b, c, x + 1, y, z + 1, x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1);
            }
        }
        return b.build();
    }

    /** A face's corners in order bottom-left, bottom-right, top-right, top-left (as seen from outside). */
    private static void quad(BufferBuilder b, float[] c, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        b.addVertex(x0, y0, z0).setUv(0, 1).setColor(c[0], c[1], c[2], c[3]);
        b.addVertex(x1, y1, z1).setUv(1, 1).setColor(c[0], c[1], c[2], c[3]);
        b.addVertex(x2, y2, z2).setUv(1, 0).setColor(c[0], c[1], c[2], c[3]);
        b.addVertex(x3, y3, z3).setUv(0, 0).setColor(c[0], c[1], c[2], c[3]);
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
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof DraconicEffectBlock)) {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!(state.getBlock() instanceof DraconicEffectBlock block)) {
                                    continue;
                                }
                                BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                                int tier = state.getValue(DraconicEffectBlock.TIER);
                                for (Direction d : Direction.values()) {
                                    n.setWithOffset(pos, d);
                                    BlockState next = level.getBlockState(n);
                                    if (next.getBlock() == block || next.isSolidRender(level, n)) {
                                        continue; // (inside a group of the same effect, or covered)
                                    }
                                    FACES.computeIfAbsent(block.kind, k -> new ArrayList<>()).add(new Face(pos, d, tier));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
