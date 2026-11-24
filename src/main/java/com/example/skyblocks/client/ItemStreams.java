package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Items flying between a singularity's ports and its core: each stack a port moves (SingularityPayloads.Stream) shows as
 * a few copies of it spiralling in from the port and shrinking into the core - or, a port putting out, growing out of
 * the core and spiralling out to the port. Drawn by the singularity's renderer, from its middle.
 */
public final class ItemStreams {
    /** How long a copy takes to fly, in seconds; how many copies a stack shows, one after another. */
    private static final float FLIGHT = 1.3F, GAP = 0.18F;
    private static final int COPIES = 3, MAX = 160;

    private record Flight(BlockPos port, BlockPos core, ItemStack item, boolean out, long start, int seed) {}

    private static final List<Flight> FLIGHTS = new ArrayList<>();
    private static int seeds;

    private ItemStreams() {}

    public static void add(BlockPos port, BlockPos core, ItemStack item, boolean out) {
        if (item.isEmpty() || FLIGHTS.size() >= MAX) {
            return;
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < COPIES; i++) {
            FLIGHTS.add(new Flight(port.immutable(), core.immutable(), item, out, now + (long) (i * GAP * 1000), seeds++));
        }
    }

    /** The flights to this core, drawn round its middle ({@code poseStack} at its block's corner). */
    public static void render(BlockPos core, PoseStack poseStack, MultiBufferSource buffers) {
        if (FLIGHTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        Iterator<Flight> it = FLIGHTS.iterator();
        while (it.hasNext()) {
            Flight f = it.next();
            float age = (now - f.start) / 1000.0F;
            if (age > FLIGHT) {
                it.remove();
                continue;
            }
            if (age < 0 || !f.core.equals(core)) {
                continue;
            }
            // (s: 0 at the port, 1 in the core)
            float k = age / FLIGHT;
            float s = f.out ? 1.0F - k : k;
            s = s * s * (3.0F - 2.0F * s);
            float px = f.port.getX() - core.getX(), py = f.port.getY() - core.getY(), pz = f.port.getZ() - core.getZ();
            // spiralling round the line between them, the spiral closing as it nears the core
            float ang = s * 7.0F + f.seed * 2.3F, round = (1.0F - s) * 0.55F;
            double len = Math.sqrt(px * px + py * py + pz * pz);
            float sx = (float) (-pz / Math.max(len, 1e-3)), sz = (float) (px / Math.max(len, 1e-3));
            float x = px * (1.0F - s) + (float) Math.cos(ang) * round * sx;
            float y = py * (1.0F - s) + (float) Math.sin(ang) * round;
            float z = pz * (1.0F - s) + (float) Math.cos(ang) * round * sz;
            float size = 0.55F * (1.0F - 0.75F * s) * Math.min(1.0F, Math.min(age, FLIGHT - age) * 8.0F + 0.2F);
            poseStack.pushPose();
            poseStack.translate(0.5F + x, 0.5F + y, 0.5F + z);
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 360.0F + f.seed * 40));
            poseStack.mulPose(Axis.XP.rotationDegrees(age * 150.0F));
            poseStack.scale(size, size, size);
            mc.getItemRenderer().renderStatic(f.item, ItemDisplayContext.GROUND, 0xF000F0, OverlayTexture.NO_OVERLAY, poseStack, buffers, mc.level, f.seed);
            poseStack.popPose();
        }
    }
}
