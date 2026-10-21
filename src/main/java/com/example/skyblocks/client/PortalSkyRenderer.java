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
        Map<Long, SkyblockType> map = level == null ? null : SKIES.get(level.dimension().location().toString());
        if (map == null || map.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Map<SkyblockType, List<float[]>> faces = new LinkedHashMap<>();
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
        if (faces.isEmpty()) {
            return;
        }
        boolean defer = com.example.skyblocks.client.iris.IrisIslandTarget.deferSky();
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
                com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(type, c -> emit(c, quads));
            } else {
                emit(buffers.getBuffer(type), quads);
            }
        }
        if (buffers != null) {
            buffers.endBatch();
        }
    }

    /** The quads covering one portal block (4 corners x xyz each), relative to the camera. */
    private static void faces(Level level, BlockPos pos, BlockState state, float x, float y, float z, List<float[]> out) {
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
        } else if (kind == PortalSkies.Kind.FLOOR) {
            // where the end portal draws its starfield: 12/16 up, and its bottom at 6/16
            float top = 0.75F + OUT, bottom = 0.375F - OUT;
            out.add(quad(x, y + top, z, x + 1, y + top, z, x + 1, y + top, z + 1, x, y + top, z + 1));
            out.add(quad(x, y + bottom, z, x + 1, y + bottom, z, x + 1, y + bottom, z + 1, x, y + bottom, z + 1));
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
    private static void emit(VertexConsumer c, List<float[]> quads) {
        for (float[] q : quads) {
            vertex(c, q[0], q[1], q[2], 0, 0);
            vertex(c, q[3], q[4], q[5], 1, 0);
            vertex(c, q[6], q[7], q[8], 1, 1);
            vertex(c, q[9], q[10], q[11], 0, 1);
            vertex(c, q[9], q[10], q[11], 0, 1);
            vertex(c, q[6], q[7], q[8], 1, 1);
            vertex(c, q[3], q[4], q[5], 1, 0);
            vertex(c, q[0], q[1], q[2], 0, 0);
        }
    }

    private static void vertex(VertexConsumer c, float x, float y, float z, float u, float v) {
        c.addVertex(x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
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
            Map.entry("SKYBLOCKS_JUPITER", new float[] {0.9F, 0.8F, 1.0F}),
            Map.entry("SKYBLOCKS_CAT", new float[] {0.71F, 0.79F, 1.0F}),
            Map.entry("SKYBLOCKS_TWILIGHT", new float[] {0.23F, 0.63F, 1.0F}),
            Map.entry("SKYBLOCKS_HELL", new float[] {1.0F, 0.2F, 0.16F}),
            Map.entry("SKYBLOCKS_NEBULA", new float[] {0.69F, 0.71F, 1.0F}),
            Map.entry("SKYBLOCKS_GOLDEN_HOUR", new float[] {1.0F, 0.8F, 0.5F}));
    private static final float[] DEFAULT_COLOR = {0.6F, 0.3F, 1.0F};

    public static float[] color(SkyblockType sky) {
        return COLORS.getOrDefault(sky.name(), DEFAULT_COLOR);
    }
}
