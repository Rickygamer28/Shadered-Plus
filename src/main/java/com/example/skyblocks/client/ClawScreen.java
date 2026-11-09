package com.example.skyblocks.client;

import com.example.skyblocks.ClawMachineBlockEntity;
import com.example.skyblocks.ClawPayloads;
import com.example.skyblocks.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Playing a claw machine: the case seen from the front, its prizes on the floor. 20 seconds to steer the claw (left
 * and right, back and forth), then it drops - or Space drops it sooner. If it catches something it lifts it (it may slip)
 * and carries it to the chute: that plushie is yours.
 */
public class ClawScreen extends Screen {
    private static final int W = 200, H = 160;
    private static final float CHUTE = 0.18F;

    private enum Phase { AIM, DROP, GRAB, LIFT, CARRY, RELEASE, DONE }

    private record Prize(float x, float z, int kind, float turn) {}

    private final BlockPos pos;
    private final RandomSource random = RandomSource.create();
    private final List<Prize> prizes = new ArrayList<>();
    private Phase phase = Phase.AIM;
    private float clawX = 0.5F, clawZ = 0.5F, depth, x0c, z0c, d0;
    private int timer = 20 * 20, phaseT, holding = -1, won = -1;
    private Prize held;
    private boolean kl, kr, ku, kd, open = true, sent, slipped;
    private int left, top;

    public ClawScreen(BlockPos pos) {
        super(Component.translatable("block.skyblocks.claw_machine"));
        this.pos = pos;
        for (int i = 0; i < 11; i++) { // (the prizes, scattered on the floor - none on the chute)
            float x, z;
            do {
                x = 0.12F + random.nextFloat() * 0.8F;
                z = 0.12F + random.nextFloat() * 0.8F;
            } while (x < CHUTE + 0.1F && z < CHUTE + 0.1F);
            prizes.add(new Prize(x, z, random.nextInt(ModItems.PLUSHIES.size()), random.nextFloat()));
        }
        prizes.sort((a, b) -> Float.compare(b.z(), a.z())); // (the back ones drawn first)
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void phase(Phase p) {
        phase = p;
        phaseT = 0;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof ClawMachineBlockEntity) || mc.player == null
                || mc.player.distanceToSqr(pos.getCenter()) > 8 * 8) {
            onClose();
            return;
        }
        x0c = clawX;
        z0c = clawZ;
        d0 = depth;
        phaseT++;
        switch (phase) {
            case AIM -> {
                float sp = 0.012F;
                if (kl) clawX -= sp;
                if (kr) clawX += sp;
                if (ku) clawZ += sp;
                if (kd) clawZ -= sp;
                clawX = Mth.clamp(clawX, 0.08F, 0.92F);
                clawZ = Mth.clamp(clawZ, 0.08F, 0.92F);
                if (--timer <= 0) phase(Phase.DROP);
            }
            case DROP -> {
                depth = Math.min(1.0F, depth + 0.03F);
                if (depth >= 1.0F) phase(Phase.GRAB);
            }
            case GRAB -> {
                open = false;
                if (phaseT == 8) {
                    // the closest prize under the claw - caught if it's close enough, more surely the closer
                    Prize best = null;
                    float bestD = 1;
                    for (Prize p : prizes) {
                        float d = Mth.sqrt((p.x() - clawX) * (p.x() - clawX) + (p.z() - clawZ) * (p.z() - clawZ));
                        if (d < bestD) {
                            bestD = d;
                            best = p;
                        }
                    }
                    if (best != null && bestD < 0.09F && random.nextFloat() < 0.95F - bestD * 6.0F) {
                        held = best;
                        holding = best.kind();
                        prizes.remove(best);
                    }
                    phase(Phase.LIFT);
                }
            }
            case LIFT -> {
                depth = Math.max(0.0F, depth - 0.025F);
                if (held != null && !slipped && depth < 0.5F && random.nextFloat() < 0.012F) { // (it slips!)
                    slipped = true;
                    prizes.add(new Prize(Mth.clamp(clawX + (random.nextFloat() - 0.5F) * 0.1F, 0.1F, 0.9F),
                            Mth.clamp(clawZ + (random.nextFloat() - 0.5F) * 0.1F, 0.1F, 0.9F), held.kind(), random.nextFloat()));
                    prizes.sort((a, b) -> Float.compare(b.z(), a.z()));
                    held = null;
                    holding = -1;
                }
                if (depth <= 0.0F) phase(Phase.CARRY);
            }
            case CARRY -> {
                clawX += Mth.clamp(CHUTE - 0.04F - clawX, -0.012F, 0.012F);
                clawZ += Mth.clamp(CHUTE - 0.04F - clawZ, -0.012F, 0.012F);
                if (Math.abs(clawX - (CHUTE - 0.04F)) < 0.002F && Math.abs(clawZ - (CHUTE - 0.04F)) < 0.002F) phase(Phase.RELEASE);
            }
            case RELEASE -> {
                open = true;
                if (phaseT == 10) {
                    won = holding;
                    holding = -1;
                    held = null;
                    finish();
                    phase(Phase.DONE);
                }
            }
            case DONE -> { }
        }
        if (phaseT % 4 == 0 || phase == Phase.DONE && phaseT == 1) {
            PacketDistributor.sendToServer(new ClawPayloads.State(pos, Math.round(clawX * 1000), Math.round(clawZ * 1000),
                    Math.round(depth * 1000) + (open ? 0 : 2000), holding));
        }
    }

    private void finish() {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new ClawPayloads.Play(pos, won));
        }
    }

    @Override
    public void removed() {
        if (!sent && phase != Phase.AIM) { // (closed mid-go: it still counts, as nothing won)
            won = -1;
            finish();
        }
        super.removed();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) kl = true;
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) kr = true;
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) ku = true;
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) kd = true;
        if ((key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) && phase == Phase.AIM) phase(Phase.DROP);
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) kl = false;
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) kr = false;
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) ku = false;
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) kd = false;
        return true;
    }

    // ================= drawing: the case from the front, its floor going back and up =================

    /** Where a point of the floor (x across, z back) is on the screen, and how big things are there. */
    private float sx(float x, float z) {
        float k = 1.0F - z * 0.35F;
        return left + W / 2.0F + (x - 0.5F) * (W - 30) * k;
    }

    private float floorY(float z) {
        return top + H - 22 - z * 46;
    }

    private float scaleAt(float z) {
        return 1.0F - z * 0.35F;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int l = left, t = top;
        // the cabinet round the glass
        g.fill(l - 10, t - 26, l + W + 10, t + H + 24, 0xFF2A1450);
        g.fill(l - 8, t - 24, l + W + 8, t - 6, 0xFFFF7AD9);
        g.drawCenteredString(font, Component.translatable("block.skyblocks.claw_machine"), l + W / 2, t - 19, 0xFFFFFFFF);
        // inside: the back wall, the floor, the chute
        g.fillGradient(l, t, l + W, t + H, 0xFF1A1030, 0xFF3A2A6A);
        float fb = floorY(1.0F), ff = floorY(0.0F);
        g.fillGradient(l, (int) fb, l + W, t + H, 0xFF5A3A8A, 0xFF7A5AAA);
        int chX0 = (int) sx(0.0F, 0.0F), chX1 = (int) sx(CHUTE, 0.0F);
        g.fill(chX0, (int) floorY(CHUTE), chX1, (int) ff + 4, 0xFF0A0614);
        g.renderOutline(chX0, (int) floorY(CHUTE), chX1 - chX0, (int) (ff + 4 - floorY(CHUTE)), 0xFFFFE74A);
        // the claw's shadow on the floor, then the prizes back to front with the claw among them
        float cx = Mth.lerp(pt, x0c, clawX), cz = Mth.lerp(pt, z0c, clawZ), cd = Mth.lerp(pt, d0, depth);
        float shadowW = 14 * scaleAt(cz);
        g.fill((int) (sx(cx, cz) - shadowW / 2), (int) floorY(cz) - 2, (int) (sx(cx, cz) + shadowW / 2), (int) floorY(cz) + 1, 0x60000000);
        boolean clawDrawn = false;
        for (Prize p : prizes) {
            if (!clawDrawn && p.z() < cz) {
                drawClaw(g, cx, cz, cd);
                clawDrawn = true;
            }
            drawPrize(g, p.kind(), sx(p.x(), p.z()), floorY(p.z()), scaleAt(p.z()) * 1.6F);
        }
        if (!clawDrawn) drawClaw(g, cx, cz, cd);
        // the glass's shine and frame
        g.fill(l + 6, t + 4, l + 9, t + H - 30, 0x30FFFFFF);
        g.renderOutline(l - 1, t - 1, W + 2, H + 2, 0xFF8A6ADA);
        // how long is left, how to play, how it went
        if (phase == Phase.AIM) {
            g.drawString(font, "TIME " + (timer / 20 + 1), l + 4, t + 4, 0xFFFFE74A, false);
            g.drawCenteredString(font, Component.translatable("screen.skyblocks.claw.help"), l + W / 2, t + H + 8, 0xFFDDDDDD);
        } else if (phase == Phase.DONE) {
            Component msg = won >= 0
                    ? Component.translatable("screen.skyblocks.claw.won", new ItemStack(ModItems.PLUSHIES.get(won).get()).getHoverName())
                    : Component.translatable(slipped ? "screen.skyblocks.claw.slipped" : "screen.skyblocks.claw.missed");
            g.fill(l, t + H / 2 - 14, l + W, t + H / 2 + 14, 0xC0000000);
            g.drawCenteredString(font, msg, l + W / 2, t + H / 2 - 4, won >= 0 ? 0xFF5BFF6A : 0xFFFF9A9A);
            g.drawCenteredString(font, Component.translatable("screen.skyblocks.claw.again"), l + W / 2, t + H + 8, 0xFFAAAAAA);
        }
    }

    private void drawPrize(GuiGraphics g, int kind, float x, float y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y - 8 * scale, 100);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(new ItemStack(ModItems.PLUSHIES.get(kind).get()), -8, -8);
        g.pose().popPose();
    }

    private void drawClaw(GuiGraphics g, float x, float z, float d) {
        float k = scaleAt(z);
        float px = sx(x, z);
        float rail = top + 6;
        float bottom = floorY(z) - 6 * k;
        float clawY = Mth.lerp(d, rail + 14, bottom - 10 * k);
        // the rail and carriage, the cable
        g.fill(left, (int) rail - 2, left + W, (int) rail + 1, 0xFF9A9AAA);
        g.fill((int) (px - 6), (int) rail - 3, (int) (px + 6), (int) rail + 4, 0xFFCFCFDF);
        g.fill((int) px - 1, (int) rail + 4, (int) px + 1, (int) clawY, 0xFFBFBFCF);
        // the claw: a hub and three prongs, open or closed
        float spread = open ? 7 * k : 3 * k;
        g.fill((int) (px - 4 * k), (int) clawY, (int) (px + 4 * k), (int) (clawY + 4 * k), 0xFFE0E0F0);
        for (int s = -1; s <= 1; s++) {
            float tip = px + s * spread;
            g.fill((int) Math.min(px + s * 2, tip), (int) (clawY + 3 * k), (int) Math.max(px + s * 2, tip) + 1, (int) (clawY + 5 * k), 0xFFC0C0D0);
            g.fill((int) tip, (int) (clawY + 4 * k), (int) tip + 2, (int) (clawY + 11 * k), 0xFFC0C0D0);
        }
        if (holding >= 0) {
            drawPrize(g, holding, px + 1, clawY + 20 * k, k * 1.6F);
        }
    }
}
