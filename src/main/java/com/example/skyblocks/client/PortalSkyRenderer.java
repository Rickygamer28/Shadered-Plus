package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.portal.PortalSkies;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Draws portals that were given a Shadered sky (portal/PortalSkies) with that sky: quads just outside the portal's own
 * faces (a nether portal's two sides, an end portal's top and bottom, an end gateway's six sides) with Shadered's render
 * type for the sky, drawn before the portal itself so they cover it. Also knows each sky's colour for the portal
 * particles (mixin/PortalParticlesMixin).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class PortalSkyRenderer {
    /** Just outside the portal's own faces (so they are hidden behind ours). */
    private static final float OUT = 0.01F;
    private static final double RANGE = 128.0;

    /** Dimension id -> block position -> sky (what the server told us). */
    private static final Map<String, Map<Long, SkyblockType>> SKIES = new HashMap<>();
    private static ByteBufferBuilder memory;

    private PortalSkyRenderer() {}

    /** A PortalSkiesPayload arrived. */
    public static void apply(CompoundTag data) {
        Map<Long, SkyblockType> map = SKIES.computeIfAbsent(data.getString("dimension"), k -> new HashMap<>());
        if (data.getBoolean("full")) {
            map.clear();
        }
        long[] positions = data.getLongArray("positions");
        ListTag names = data.getList("skies", Tag.TAG_STRING);
        for (int i = 0; i < positions.length && i < names.size(); i++) {
            SkyblockType sky = names.getString(i).isEmpty() ? null : PortalSkies.sky(names.getString(i));
            if (sky == null) {
                map.remove(positions[i]);
            } else {
                map.put(positions[i], sky);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SKIES.clear();
    }

    /** The sky a portal block shows, or null. */
    @Nullable
    public static SkyblockType skyAt(Level level, BlockPos pos) {
        Map<Long, SkyblockType> map = SKIES.get(level.dimension().location().toString());
        return map == null ? null : map.get(pos.asLong());
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        // like the Sky Liquids: after the solid world, before see-through things (the nether portal is one)
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Map<SkyblockType, List<float[]>> faces = collect(level, event.getCamera().getPosition());
        if (!faces.isEmpty()) {
            draw(faces, com.example.skyblocks.client.iris.IrisIslandTarget.deferSky());
        }
    }

    /**
     * Inside a mirror's picture (bound, with the mirror's model-view set): the portals' skies seen from the mirrored
     * eye at (ex, ey, ez).
     */
    public static void drawInMirror(ClientLevel level, double ex, double ey, double ez) {
        Map<SkyblockType, List<float[]>> faces = collect(level, new Vec3(ex, ey, ez));
        if (!faces.isEmpty()) {
            draw(faces, false);
        }
    }

    /** Each sky's quads over the portals near {@code camera} (relative to it). */
    private static Map<SkyblockType, List<float[]>> collect(ClientLevel level, Vec3 camera) {
        Map<SkyblockType, List<float[]>> faces = new LinkedHashMap<>();
        Map<Long, SkyblockType> map = SKIES.get(level.dimension().location().toString());
        if (map == null || map.isEmpty()) {
            return faces;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, SkyblockType> entry : map.entrySet()) {
            pos.set(entry.getKey());
            if (pos.distToCenterSqr(camera) > RANGE * RANGE || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!PortalSkies.isPortal(state)) {
                continue;
            }
            float x = (float) (pos.getX() - camera.x), y = (float) (pos.getY() - camera.y), z = (float) (pos.getZ() - camera.z);
            faces(level, pos, state, x, y, z, faces.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()));
        }
        return faces;
    }

    private static void draw(Map<SkyblockType, List<float[]>> faces, boolean defer) {
        if (memory == null) {
            memory = new ByteBufferBuilder(1 << 16);
        }
        MultiBufferSource.BufferSource buffers = defer ? null : MultiBufferSource.immediate(memory);
        for (Map.Entry<SkyblockType, List<float[]>> entry : faces.entrySet()) {
            // a resting sky gets its picture drawn first (see SkyUsage)
            SkyUsage.ensureFresh(com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(entry.getKey()));
            RenderType type = SkyLiquidRenderer.skyType(entry.getKey());
            if (type == null) {
                continue;
            }
            List<float[]> quads = entry.getValue();
            if (defer) {
                // with a shader pack: drawn at the end of the frame (client/iris/IrisIslandTarget)
                com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(type, c -> emit(c, null, quads));
            } else {
                emit(buffers.getBuffer(type), null, quads);
            }
        }
        if (buffers != null) {
            buffers.endBatch();
        }
    }

    /**
     * In a Sky Island (its blocks in {@code island}, island coordinates): the skies of its portals, with the pose that
     * places the island. {@code mayDefer}: with a shader pack, drawn at the end of the frame (not inside a painting's
     * window, which draws into its own target).
     */
    public static void drawInIsland(net.minecraft.world.level.BlockGetter island, Map<BlockPos, SkyblockType> skies,
                                    com.mojang.blaze3d.vertex.PoseStack poseStack, MultiBufferSource buffer, boolean mayDefer) {
        if (skies.isEmpty()) {
            return;
        }
        Map<SkyblockType, List<float[]>> faces = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, SkyblockType> e : skies.entrySet()) {
            BlockPos pos = e.getKey();
            BlockState state = island.getBlockState(pos);
            if (PortalSkies.isPortal(state)) {
                faces(island, pos, state, pos.getX(), pos.getY(), pos.getZ(), faces.computeIfAbsent(e.getValue(), k -> new ArrayList<>()));
            }
        }
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        boolean defer = mayDefer && com.example.skyblocks.client.iris.IrisIslandTarget.deferSky();
        for (Map.Entry<SkyblockType, List<float[]>> entry : faces.entrySet()) {
            SkyUsage.ensureFresh(com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(entry.getKey()));
            RenderType type = SkyLiquidRenderer.skyType(entry.getKey());
            if (type == null) {
                continue;
            }
            List<float[]> quads = entry.getValue();
            if (defer) {
                com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(type, c -> emit(c, pose, quads));
            } else {
                emit(buffer.getBuffer(type), pose, quads);
            }
        }
    }

    /** The quads covering one portal block (4 corners x xyz each), relative to the camera. */
    private static void faces(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state, float x, float y, float z, List<float[]> out) {
        PortalSkies.Kind kind = PortalSkies.kind(state);
        if (kind == PortalSkies.Kind.SHEET) {
            // the sheet's two sides: where its shape is (vanilla: 6/16 to 10/16 thick), on the axis it doesn't run along
            boolean alongX = PortalSkies.axis(state) == Direction.Axis.X;
            float a = 6.0F / 16.0F, b = 10.0F / 16.0F, y0 = 0, y1 = 1;
            net.minecraft.world.phys.shapes.VoxelShape shape = state.getShape(level, pos);
            if (!shape.isEmpty()) {
                net.minecraft.world.phys.AABB box = shape.bounds();
                a = (float) (alongX ? box.minZ : box.minX);
                b = (float) (alongX ? box.maxZ : box.maxX);
                y0 = (float) box.minY;
                y1 = (float) box.maxY;
            }
            a -= OUT;
            b += OUT;
            if (alongX) {
                out.add(quad(x, y + y0, z + a, x + 1, y + y0, z + a, x + 1, y + y1, z + a, x, y + y1, z + a));
                out.add(quad(x, y + y0, z + b, x + 1, y + y0, z + b, x + 1, y + y1, z + b, x, y + y1, z + b));
            } else {
                out.add(quad(x + a, y + y0, z, x + a, y + y0, z + 1, x + a, y + y1, z + 1, x + a, y + y1, z));
                out.add(quad(x + b, y + y0, z, x + b, y + y0, z + 1, x + b, y + y1, z + 1, x + b, y + y1, z));
            }
        } else if (kind == PortalSkies.Kind.FLOOR && state.getBlock() instanceof net.minecraft.world.level.block.EndPortalBlock) {
            // where the end portal draws its starfield: 12/16 up, and its bottom at 6/16
            float top = 0.75F + OUT, bottom = 0.375F - OUT;
            out.add(quad(x, y + top, z, x + 1, y + top, z, x + 1, y + top, z + 1, x, y + top, z + 1));
            out.add(quad(x, y + bottom, z, x + 1, y + bottom, z, x + 1, y + bottom, z + 1, x, y + bottom, z + 1));
        } else if (kind == PortalSkies.Kind.FLOOR) {
            // a modded flat portal: its surface at the top of its shape (the Twilight Forest's: 13/16 up)
            net.minecraft.world.phys.shapes.VoxelShape shape = state.getShape(level, pos);
            float top = (shape.isEmpty() ? 1.0F : (float) shape.bounds().maxY) + OUT;
            out.add(quad(x, y + top, z, x + 1, y + top, z, x + 1, y + top, z + 1, x, y + top, z + 1));
        } else {
            // end gateway: the whole cube
            float lo = -OUT, hi = 1 + OUT;
            float x0 = x + lo, x1 = x + hi, y0 = y + lo, y1 = y + hi, z0 = z + lo, z1 = z + hi;
            out.add(quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1));
            out.add(quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1));
            out.add(quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0));
            out.add(quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1));
            out.add(quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0));
            out.add(quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0));
        }
    }

    private static float[] quad(float... corners) {
        return corners;
    }

    /** Each quad from both sides (it shows from either side of the portal). */
    private static void emit(VertexConsumer c, @Nullable Matrix4f pose, List<float[]> quads) {
        for (float[] q : quads) {
            vertex(c, pose, q[0], q[1], q[2], 0, 0);
            vertex(c, pose, q[3], q[4], q[5], 1, 0);
            vertex(c, pose, q[6], q[7], q[8], 1, 1);
            vertex(c, pose, q[9], q[10], q[11], 0, 1);
            vertex(c, pose, q[9], q[10], q[11], 0, 1);
            vertex(c, pose, q[6], q[7], q[8], 1, 1);
            vertex(c, pose, q[3], q[4], q[5], 1, 0);
            vertex(c, pose, q[0], q[1], q[2], 0, 0);
        }
    }

    private static void vertex(VertexConsumer c, @Nullable Matrix4f pose, float x, float y, float z, float u, float v) {
        (pose == null ? c.addVertex(x, y, z) : c.addVertex(pose, x, y, z)).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
    }

    // ---- particle colours ----

    /** Each sky's colour (from its sky pictures' colourful parts) for the portal's particles; others: purple. */
    private static final Map<String, float[]> COLORS = Map.ofEntries(
            Map.entry("SPACE", new float[] {0.73F, 0.44F, 1.0F}),
            Map.entry("ECLIPSE", new float[] {1.0F, 0.5F, 0.15F}),
            Map.entry("END", new float[] {0.35F, 0.8F, 0.7F}),
            Map.entry("FOREST", new float[] {0.53F, 0.69F, 1.0F}),
            Map.entry("STORMY", new float[] {0.75F, 0.78F, 0.85F}),
            Map.entry("LIGHT", new float[] {1.0F, 1.0F, 1.0F}),
            Map.entry("MIMIC", new float[] {0.8F, 0.8F, 0.8F}),
            Map.entry("OCEAN", new float[] {0.49F, 0.76F, 1.0F}),
            Map.entry("IRIDIA", new float[] {0.84F, 0.04F, 1.0F}),
            Map.entry("END_SKY", new float[] {0.55F, 0.4F, 0.75F}),
            Map.entry("SKYBLOCKS_SYNTHWAVE_OUTRUN", new float[] {0F, 0.85F, 1F}),
            Map.entry("SKYBLOCKS_SYNTHWAVE_NIGHT", new float[] {0.3F, 0.45F, 1F}),
            Map.entry("SKYBLOCKS_SYNTHWAVE_MIAMI", new float[] {1F, 0.3F, 0.7F}),
            Map.entry("SKYBLOCKS_DARKSYNTH", new float[] {1F, 0.05F, 0.08F}),
            Map.entry("SKYBLOCKS_SYNTHWAVE_TERMINAL", new float[] {0.15F, 1F, 0.3F}),
            Map.entry("SKYBLOCKS_VAPORWAVE", new float[] {1F, 0.55F, 0.85F}),
            Map.entry("SKYBLOCKS_AURORA", new float[] {0.3F, 1.0F, 0.6F}),
            Map.entry("SKYBLOCKS_UNDERWATER", new float[] {0.25F, 0.7F, 0.9F}),
            Map.entry("SKYBLOCKS_SYNTHWAVE", new float[] {1.0F, 0.3F, 0.75F}),
            Map.entry("SKYBLOCKS_CHAOS", new float[] {1.0F, 0.3F, 0.12F}),
            Map.entry("SKYBLOCKS_JUPITER", new float[] {0.9F, 0.8F, 1.0F}),
            Map.entry("SKYBLOCKS_CAT", new float[] {0.71F, 0.79F, 1.0F}),
            Map.entry("SKYBLOCKS_TWILIGHT", new float[] {0.23F, 0.63F, 1.0F}),
            Map.entry("SKYBLOCKS_HELL", new float[] {1.0F, 0.2F, 0.16F}),
            Map.entry("SKYBLOCKS_NEBULA", new float[] {0.69F, 0.71F, 1.0F}),
            Map.entry("SKYBLOCKS_GOLDEN_HOUR", new float[] {1.0F, 0.8F, 0.5F}),
            Map.entry("SKYBLOCKS_SCADU_ALTUS", new float[] {0.4F, 0.73F, 1.0F}),
            Map.entry("SKYBLOCKS_CERULEAN_COAST", new float[] {0.92F, 0.97F, 1.0F}),
            Map.entry("SKYBLOCKS_GRAVESITE_PLAIN", new float[] {0.87F, 0.93F, 1.0F}),
            Map.entry("SKYBLOCKS_MOUNTAINTOPS", new float[] {0.92F, 0.89F, 1.0F}),
            Map.entry("SKYBLOCKS_ALTUS_PLATEAU_NIGHT", new float[] {0.41F, 0.69F, 1.0F}),
            Map.entry("SKYBLOCKS_ALTUS_PLATEAU_SUNSET", new float[] {1.0F, 0.61F, 0.29F}),
            Map.entry("SKYBLOCKS_ALTUS_PLATEAU", new float[] {1.0F, 0.83F, 0.13F}),
            Map.entry("SKYBLOCKS_DIVINE_TOWER", new float[] {1.0F, 0.92F, 0.63F}),
            Map.entry("SKYBLOCKS_CAELID", new float[] {1.0F, 0.33F, 0.12F}),
            Map.entry("SKYBLOCKS_SIOFRA_RIVER", new float[] {0.44F, 0.47F, 1.0F}),
            Map.entry("SKYBLOCKS_LIMGRAVE_SUNSET", new float[] {1.0F, 0.57F, 0.37F}),
            Map.entry("SKYBLOCKS_LIMGRAVE_RAINY", new float[] {0.91F, 1.0F, 0.96F}),
            Map.entry("SKYBLOCKS_ACADEMY_OF_RAYA_LUCARIA", new float[] {0.36F, 0.9F, 1.0F}),
            Map.entry("SKYBLOCKS_LIURNIA_SUNSET", new float[] {1.0F, 0.75F, 0.71F}),
            Map.entry("SKYBLOCKS_LIURNIA_RAINY", new float[] {0.83F, 0.95F, 1.0F}),
            Map.entry("SKYBLOCKS_LIURNIA", new float[] {0.76F, 0.78F, 1.0F}));
    private static final float[] DEFAULT_COLOR = {0.6F, 0.3F, 1.0F};

    public static float[] color(SkyblockType sky) {
        return COLORS.getOrDefault(sky.name(), DEFAULT_COLOR);
    }
}
