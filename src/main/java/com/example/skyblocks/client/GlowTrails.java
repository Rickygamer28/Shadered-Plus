package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SkyGlow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Glow trails (SkyGlow): behind an arrow or trident in flight, or a player gliding on elytra, a ribbon of its sky -
 * a window into it (SkyWindow) turned to face you, narrowing away to nothing at its tail, a soft glow along its edges.
 * Once the arrow stops (or the glide ends), the trail draws in after it and is gone.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
public final class GlowTrails {
    private static final int LENGTH = 26, GLIDE_LENGTH = 40;
    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static final Map<Integer, String> PENDING = new HashMap<>();
    private static final SkyWindow.Quads QUADS = new SkyWindow.Quads();

    private GlowTrails() {}

    private static final class Trail {
        final String sky;
        final float width;
        final ArrayDeque<Vec3> points = new ArrayDeque<>();
        boolean live = true;

        Trail(String sky, float width) {
            this.sky = sky;
            this.width = width;
        }
    }

    /** An arrow or trident (its id) trails this sky (sent by the server: SkyGlow.Trail). */
    public static void track(int id, String sky) {
        PENDING.put(id, sky);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            TRAILS.clear();
            PENDING.clear();
            return;
        }
        // (arrows and tridents)
        for (Iterator<Map.Entry<Integer, String>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, String> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            if (entity != null) {
                TRAILS.computeIfAbsent(e.getKey(), k -> new Trail(e.getValue(), entity instanceof ThrownTrident ? 0.34F : 0.24F));
                it.remove();
            }
        }
        // (players gliding on glowing elytra: their own trails, by -id - 1)
        for (Player p : level.players()) {
            ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
            String sky = chest.is(Items.ELYTRA) ? SkyGlow.of(chest) : null;
            if (sky != null && p.isFallFlying()) {
                TRAILS.computeIfAbsent(-p.getId() - 1, k -> new Trail(sky, 0.7F)).live = true;
            }
        }
        for (Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Trail> e = it.next();
            Trail trail = e.getValue();
            int id = e.getKey();
            Entity entity = level.getEntity(id < 0 ? -id - 1 : id);
            boolean moving = entity != null && entity.isAlive() && entity.getDeltaMovement().lengthSqr() > 0.004
                    && (id >= 0 || entity instanceof Player p && p.isFallFlying());
            Vec3 at = entity == null ? null : id < 0 ? entity.position().add(0, 0.6, 0) : entity.position();
            if (moving && (trail.points.isEmpty() || trail.points.peekLast().distanceToSqr(at) > 1.0E-4)) {
                trail.points.addLast(at);
                while (trail.points.size() > (id < 0 ? GLIDE_LENGTH : LENGTH)) {
                    trail.points.removeFirst();
                }
            } else if (!trail.points.isEmpty()) {
                trail.points.removeFirst(); // (stopped: the trail draws in)
                if (!trail.points.isEmpty()) {
                    trail.points.removeFirst();
                }
            }
            trail.live = moving;
            if (trail.points.isEmpty() && (entity == null || !entity.isAlive() || id < 0)) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || TRAILS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        List<float[][]> glows = new ArrayList<>();
        for (Map.Entry<Integer, Trail> e : TRAILS.entrySet()) {
            Trail trail = e.getValue();
            if (trail.points.size() < 2) {
                continue;
            }
            List<Vec3> pts = new ArrayList<>(trail.points);
            int id = e.getKey();
            Entity entity = level.getEntity(id < 0 ? -id - 1 : id);
            if (trail.live && entity != null) {
                Vec3 head = entity.getPosition(pt);
                pts.add(id < 0 ? head.add(0, 0.6, 0) : head);
            }
            SkyblockType sky = SkyGlow.sky(trail.sky);
            RenderType window = sky == null ? null : SkyWindow.type(sky.name(), "NORMAL");
            if (window == null) {
                continue;
            }
            float[][] in = sides(pts, cam, trail.width, 1.0F), out = sides(pts, cam, trail.width, 1.8F);
            QUADS.clear();
            for (int i = 0; i + 1 < in.length; i++) {
                QUADS.add(in[i][0], in[i][1], in[i][2], in[i][3], in[i][4], in[i][5], in[i + 1][3], in[i + 1][4], in[i + 1][5],
                        in[i + 1][0], in[i + 1][1], in[i + 1][2]);
            }
            SkyWindow.draw(buffers, window, m, QUADS.data, QUADS.count);
            buffers.endBatch(window);
            glows.add(in);
            glows.add(out);
        }
        if (!glows.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            for (int g = 0; g < glows.size(); g += 2) {
                edge(vc, m, glows.get(g), glows.get(g + 1));
            }
            buffers.endBatch(RenderType.lightning());
        }
    }

    /** A trail's sides at each point (x y z of both), turned to face the camera, narrowing to nothing at its tail. */
    private static float[][] sides(List<Vec3> p, Vec3 cam, float width, float scale) {
        int n = p.size();
        float[][] out = new float[n][6];
        Vector3f tan = new Vector3f(), view = new Vector3f(), side = new Vector3f();
        for (int i = 0; i < n; i++) {
            Vec3 a = p.get(Math.max(0, i - 1)), b = p.get(Math.min(n - 1, i + 1)), c = p.get(i);
            tan.set((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z));
            view.set((float) (cam.x - c.x), (float) (cam.y - c.y), (float) (cam.z - c.z));
            tan.cross(view, side);
            if (side.lengthSquared() < 1.0E-10F) {
                side.set(0, 1, 0);
            }
            float k = i / (float) (n - 1);
            side.normalize(width * scale * k * (2.0F - k) * 0.5F + 0.001F);
            out[i][0] = (float) c.x - side.x;
            out[i][1] = (float) c.y - side.y;
            out[i][2] = (float) c.z - side.z;
            out[i][3] = (float) c.x + side.x;
            out[i][4] = (float) c.y + side.y;
            out[i][5] = (float) c.z + side.z;
        }
        return out;
    }

    private static void edge(VertexConsumer vc, Matrix4f m, float[][] in, float[][] out) {
        for (int side = 0; side < 2; side++) {
            int o = side * 3;
            for (int i = 0; i + 1 < in.length; i++) {
                int a0 = (int) (120.0F * i / in.length), a1 = (int) (120.0F * (i + 1) / in.length);
                for (int pass = 0; pass < 2; pass++) {
                    int a = pass == 0 ? i : i + 1, b = pass == 0 ? i + 1 : i;
                    vc.addVertex(m, in[a][o], in[a][o + 1], in[a][o + 2]).setColor(235, 240, 255, a == i ? a0 : a1);
                    vc.addVertex(m, in[b][o], in[b][o + 1], in[b][o + 2]).setColor(235, 240, 255, b == i ? a0 : a1);
                    vc.addVertex(m, out[b][o], out[b][o + 1], out[b][o + 2]).setColor(200, 210, 255, 0);
                    vc.addVertex(m, out[a][o], out[a][o + 1], out[a][o + 2]).setColor(200, 210, 255, 0);
                }
            }
        }
    }

    /** (for the render test) */
    public static String debug() {
        StringBuilder b = new StringBuilder("pending " + PENDING.size() + ", trails " + TRAILS.size() + ":");
        TRAILS.forEach((id, t) -> b.append(' ').append(id).append('=').append(t.sky).append('/').append(t.points.size()).append(t.live ? " live" : ""));
        return b.toString();
    }

    /** Glowing items say which sky. */
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        String name = SkyGlow.of(event.getItemStack());
        SkyblockType sky = SkyGlow.sky(name);
        if (sky == null) {
            return;
        }
        Component skyName = Component.translatable(sky.getTranslationKey());
        event.getToolTip().add(Component.translatable("tooltip.skyblocks.sky_glow", skyName).withStyle(ChatFormatting.AQUA));
    }
}
