package com.example.skyblocks.client;

import com.example.skyblocks.ElevatorBaseBlockEntity;
import com.example.skyblocks.ElevatorCabinEntity;
import com.example.skyblocks.ElevatorPayload;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * The floor buttons inside an elevator's cabin: a panel of glowing buttons on one side of it (the side with a wall
 * behind it on floor 1, if there's one) - a button for each floor, numbered from the bottom, floor 1 at the bottom left.
 * Look at one and click (either button): the cabin goes there. The floor it's at is lit, the one it's going to blinks,
 * the one you're looking at shows it. Drawn with the cabin (ElevatorRenderer); clicks worked out here, by where you look.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ElevatorButtons {
    /** The panel's distance from the shaft's middle; its lowest buttons' middle over the cabin's floor. */
    private static final float OUT = 1.32F, LOW = 1.0F;

    private ElevatorButtons() {}

    /** The panel's buttons, laid out: how many across, how big, how far apart. */
    record Layout(int side, int cols, int rows, float size, float gap) {
        /** Button i's middle: across (to your right as you face the panel), and up from the cabin's floor. */
        float u(int i) {
            return ((i % cols) - (cols - 1) / 2.0F) * (size + gap);
        }

        float v(int i) {
            return LOW + (i / cols) * (size + gap);
        }
    }

    static Layout layout(ElevatorBaseBlockEntity be) {
        int n = be.floors().length;
        int cols = Math.max(Math.min(n, 4), (n + 5) / 6);
        int rows = (n + cols - 1) / cols;
        float size = Math.min(0.2F, 1.9F / cols - 0.05F);
        return new Layout(side(be), cols, rows, size, Math.min(0.06F, size * 0.3F));
    }

    /** The panel's side (0 north, 1 south, 2 west, 3 east): one with a wall behind it on floor 1, north if none. */
    static int side(ElevatorBaseBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return 0;
        }
        if (level.getGameTime() - be.clientPanelAt < 40 && level.getGameTime() >= be.clientPanelAt) {
            return be.clientPanelSide;
        }
        be.clientPanelAt = level.getGameTime();
        BlockPos b = be.getBlockPos();
        int[][] out = {{0, -2}, {0, 2}, {-2, 0}, {2, 0}};
        int side = 0;
        for (int s = 0; s < 4; s++) {
            BlockPos p = new BlockPos(b.getX() + out[s][0], be.floors()[0] + 1, b.getZ() + out[s][1]);
            if (level.getBlockState(p).isSolidRender(level, p)) {
                side = s;
                break;
            }
        }
        be.clientPanelSide = side;
        return side;
    }

    /** Facing the panel from inside: its normal (into the cabin), and the way to your right. */
    static Vec3 normal(int side) {
        return switch (side) {
            case 1 -> new Vec3(0, 0, -1);
            case 2 -> new Vec3(1, 0, 0);
            case 3 -> new Vec3(-1, 0, 0);
            default -> new Vec3(0, 0, 1);
        };
    }

    static Vec3 right(int side) {
        return switch (side) {
            case 1 -> new Vec3(-1, 0, 0);
            case 2 -> new Vec3(0, 0, -1);
            case 3 -> new Vec3(0, 0, 1);
            default -> new Vec3(1, 0, 0);
        };
    }

    /** Which button a player's looking at (-1 none), in a cabin whose floor's top is at landing. */
    static int looking(Player player, ElevatorBaseBlockEntity be, float landing, float partialTick) {
        Layout l = layout(be);
        BlockPos b = be.getBlockPos();
        Vec3 n = normal(l.side()), r = right(l.side());
        Vec3 centre = new Vec3(b.getX() + 0.5, landing, b.getZ() + 0.5);
        Vec3 p0 = centre.subtract(n.scale(OUT));
        Vec3 eye = player.getEyePosition(partialTick), look = player.getViewVector(partialTick);
        double dn = look.dot(n);
        if (dn > -1e-4) {
            return -1;
        }
        double t = p0.subtract(eye).dot(n) / dn;
        if (t < 0.0 || t > 4.5) {
            return -1;
        }
        Vec3 hit = eye.add(look.scale(t));
        double u = hit.subtract(p0).dot(r), v = hit.y - landing;
        for (int i = 0; i < be.floors().length; i++) {
            if (Math.abs(u - l.u(i)) <= l.size() / 2 + 0.01 && Math.abs(v - l.v(i)) <= l.size() / 2 + 0.01) {
                return i;
            }
        }
        return -1;
    }

    /** A click: on a floor button (yours, in the cabin you're in or by), the cabin goes there. */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!(event.isUseItem() || event.isAttack())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        List<ElevatorCabinEntity> cabins = mc.level.getEntitiesOfClass(ElevatorCabinEntity.class, player.getBoundingBox().inflate(3.0));
        for (ElevatorCabinEntity cabin : cabins) {
            if (!(mc.level.getBlockEntity(cabin.base()) instanceof ElevatorBaseBlockEntity be)) {
                continue;
            }
            int i = looking(player, be, cabin.landing(1.0F), 1.0F);
            if (i < 0) {
                continue;
            }
            // (something nearer in the way - a block, a mob - is clicked instead)
            HitResult hit = mc.hitResult;
            if (hit != null && hit.getType() != HitResult.Type.MISS && !(hit instanceof net.minecraft.world.phys.EntityHitResult e && e.getEntity() == cabin)) {
                Layout l = layout(be);
                Vec3 at = new Vec3(be.getBlockPos().getX() + 0.5, cabin.landing(1.0F), be.getBlockPos().getZ() + 0.5).subtract(normal(l.side()).scale(OUT));
                if (hit.getLocation().distanceTo(player.getEyePosition()) + 0.05 < at.distanceTo(player.getEyePosition()) - 0.3) {
                    continue;
                }
            }
            PacketDistributor.sendToServer(new ElevatorPayload(be.getBlockPos(), ElevatorPayload.GO, i));
            player.playSound(SoundEvents.STONE_BUTTON_CLICK_ON, 0.5F, 1.6F);
            event.setCanceled(true);
            event.setSwingHand(true);
            return;
        }
    }

    /** The panel, drawn: its buttons (lit, blinking, looked at), and their numbers. */
    static void draw(Matrix4f m, ElevatorBaseBlockEntity be, float landingRel, float landingWorld, int at, int target, boolean moving, int accent,
                     float partialTick) {
        Layout l = layout(be);
        int n = be.floors().length;
        Minecraft mc = Minecraft.getInstance();
        int hover = mc.player != null ? looking(mc.player, be, landingWorld, partialTick) : -1;
        boolean blink = (System.currentTimeMillis() / 300) % 2 == 0;
        Vec3 nn = normal(l.side()), r = right(l.side());
        // (the panel from the base's corner: the shaft's middle is at 0.5, 0.5)
        float px = 0.5F - (float) nn.x * OUT, pz = 0.5F - (float) nn.z * OUT;
        int ar = (accent >> 16) & 255, ag = (accent >> 8) & 255, ab = accent & 255;
        ElevatorRenderer.drawNow(ElevatorRenderer.Types.BUTTONS, vc -> {
            // its backing
            float halfW = l.cols() * (l.size() + l.gap()) / 2.0F + 0.04F;
            float bot = landingRel + LOW - l.size() / 2 - 0.06F, top = landingRel + l.v(n - 1) + l.size() / 2 + 0.06F;
            rect(vc, m, px, pz, nn, r, 0.0F, halfW, bot, top, 0.004F, 12, 14, 22, 200);
            rect(vc, m, px, pz, nn, r, 0.0F, halfW + 0.012F, bot - 0.012F, top + 0.012F, 0.002F, ar, ag, ab, 230);
            for (int i = 0; i < n; i++) {
                float u = l.u(i), v = landingRel + l.v(i), h = l.size() / 2;
                boolean lit = i == at && !moving, going = i == target && moving;
                int fr = ar, fg = ag, fb = ab;
                int alpha = lit ? 255 : going ? (blink ? 255 : 90) : i == hover ? 200 : 70;
                rect(vc, m, px, pz, nn, r, u, h, v - h, v + h, 0.008F, fr, fg, fb, alpha);
                if (!(lit || (going && blink))) {
                    rect(vc, m, px, pz, nn, r, u, h - 0.018F, v - h + 0.018F, v + h - 0.018F, 0.01F, 16, 18, 28, 240);
                }
            }
        });
        // their numbers
        Font font = mc.font;
        MultiBufferSource.BufferSource src = DrawNow.now(null);
        float yaw = switch (l.side()) {
            case 1 -> 180.0F;
            case 2 -> 90.0F;
            case 3 -> -90.0F;
            default -> 0.0F;
        };
        for (int i = 0; i < n; i++) {
            float u = l.u(i), v = landingRel + l.v(i);
            boolean lit = (i == at && !moving) || (i == target && moving && blink);
            PoseStack ps = new PoseStack();
            ps.last().pose().set(m);
            ps.translate(px + (float) r.x * u + (float) nn.x * 0.014F, v, pz + (float) r.z * u + (float) nn.z * 0.014F);
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            float s = l.size() * 0.55F / 8.0F;
            ps.scale(s, -s, s);
            String text = String.valueOf(i + 1);
            font.drawInBatch(text, -font.width(text) / 2.0F + 0.5F, -3.5F, lit ? 0xFF101018 : 0xFF000000 | accent, false, ps.last().pose(), src,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        src.endBatch();
    }

    /** A rectangle on the panel's plane: centred u across, from v0 to v1 up, half-width hw, out from the panel by out. */
    private static void rect(VertexConsumer vc, Matrix4f m, float px, float pz, Vec3 n, Vec3 r, float u, float hw, float v0, float v1, float out,
                             int cr, int cg, int cb, int ca) {
        float x0 = px + (float) (r.x * (u - hw) + n.x * out), z0 = pz + (float) (r.z * (u - hw) + n.z * out);
        float x1 = px + (float) (r.x * (u + hw) + n.x * out), z1 = pz + (float) (r.z * (u + hw) + n.z * out);
        vc.addVertex(m, x0, v0, z0).setColor(cr, cg, cb, ca);
        vc.addVertex(m, x1, v0, z1).setColor(cr, cg, cb, ca);
        vc.addVertex(m, x1, v1, z1).setColor(cr, cg, cb, ca);
        vc.addVertex(m, x0, v1, z0).setColor(cr, cg, cb, ca);
    }
}
