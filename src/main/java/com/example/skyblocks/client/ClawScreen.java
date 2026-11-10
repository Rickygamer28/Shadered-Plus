package com.example.skyblocks.client;

import static com.example.skyblocks.ClawMachineBlockEntity.CHUTE_X0;
import static com.example.skyblocks.ClawMachineBlockEntity.CHUTE_Z1;
import static com.example.skyblocks.ClawMachineBlockEntity.FLOOR;
import static com.example.skyblocks.ClawMachineBlockEntity.TOP;
import static com.example.skyblocks.ClawMachineBlockEntity.X0;
import static com.example.skyblocks.ClawMachineBlockEntity.X1;
import static com.example.skyblocks.ClawMachineBlockEntity.Z0;
import static com.example.skyblocks.ClawMachineBlockEntity.Z1;

import com.example.skyblocks.ClawMachineBlock;
import com.example.skyblocks.ClawMachineBlockEntity;
import com.example.skyblocks.ClawMachineBlockEntity.Prize;
import com.example.skyblocks.ClawPayloads;
import com.example.skyblocks.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Playing a claw machine - in the machine itself: you look at it through its glass and steer its claw there (drawn by
 * ClawMachineRenderer from this). Twenty seconds to line it up, then it drops: down onto whatever plushie is under it
 * (it stops on the first it meets), closes - a good grip if it's right over the middle of one, a weak one near the edge,
 * nothing if it's beside it - lifts (a weak grip can slip), carries it over to the chute and lets go. Whatever falls down
 * the chute is yours. Only this screen's words are drawn flat: the time left, the keys, how it went.
 */
public class ClawScreen extends Screen {
    /** The go being played here, for the renderer: null when nobody here is playing. */
    @Nullable
    static ClawScreen active;

    /** The claw hub's height parked at the top, and the lowest it can go (its prongs on the floor). */
    static final float PARK = TOP - 3.4F, LOWEST = FLOOR + 2.8F, PRONG = 2.6F;
    private static final float CHUTE_X = (CHUTE_X0 + X1) / 2, CHUTE_Z = (Z0 + CHUTE_Z1) / 2;
    private static final int TIME = 20 * 20;

    private enum Phase { AIM, DROP, GRAB, RISE, CARRY, RELEASE, FALL, DONE }

    final BlockPos pos;
    private final RandomSource random = RandomSource.create();
    private Phase phase = Phase.AIM;
    private int timer = TIME, phaseTicks;
    /** The claw: across, front to back, the hub's height (pixels); how open (1 open, 0 shut); and a tick ago. */
    float x, z, y, open = 1.0F, ox, oz, oy, oOpen = 1.0F;
    /** The prize it has hold of (its id), how firmly, whether it'll slip, where it's falling (slipped, or down the chute). */
    int holding = -1;
    private int target = -1, slipAt = -1;
    private float targetY;
    int falling = -1;
    float fallY, oFallY, fallX, fallZ;
    private int hidden = -1, wonKind = -1;
    private boolean slipped, wasMoving;
    private boolean hidGui, hudWas;

    public ClawScreen(BlockPos pos) {
        super(Component.translatable("block.skyblocks.claw_machine"));
        this.pos = pos;
        reset();
    }

    private void reset() {
        x = ox = CHUTE_X;
        z = oz = CHUTE_Z;
        y = oy = PARK;
        open = oOpen = 1.0F;
        phase = Phase.AIM;
        timer = TIME;
        phaseTicks = 0;
        holding = target = slipAt = falling = hidden = -1;
        wonKind = -1;
        slipped = false;
    }

    @Nullable
    private ClawMachineBlockEntity machine() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof ClawMachineBlockEntity be ? be : null;
    }

    @Override
    protected void init() {
        active = this;
        if (!hudWas) {
            hudWas = true;
            hidGui = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
            lookAtMachine();
            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6F, 0.6F);
        }
    }

    /** Turns you to look in through the glass. */
    private void lookAtMachine() {
        var player = minecraft.player;
        if (player == null || !(minecraft.level.getBlockState(pos).getBlock() instanceof ClawMachineBlock)) return;
        Direction facing = minecraft.level.getBlockState(pos).getValue(ClawMachineBlock.FACING);
        Vec3 look = Vec3.atBottomCenterOf(pos).add(0.0, 1.05, 0.0).subtract(player.getEyePosition());
        double flat = Math.sqrt(look.x * look.x + look.z * look.z);
        if (flat < 0.05) {
            look = new Vec3(-facing.getStepX(), look.y, -facing.getStepZ());
            flat = 1.0;
        }
        player.setYRot((float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F);
        player.setXRot((float) (-Mth.atan2(look.y, flat) * Mth.RAD_TO_DEG));
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
    }

    @Override
    public void removed() {
        if (active == this) active = null;
        if (hudWas) minecraft.options.hideGui = hidGui;
        send(); // (so the others see it go back to resting)
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // (nothing over the world: you watch the machine itself)
    }

    private boolean down(net.minecraft.client.KeyMapping key, int arrow) {
        long w = minecraft.getWindow().getWindow();
        return InputConstants.isKeyDown(w, arrow) || (key.getKey().getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(w, key.getKey().getValue()));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean go = key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || minecraft.options.keyJump.matches(key, scan);
        if (go && phase == Phase.AIM) {
            drop();
            return true;
        }
        if (go && phase == Phase.DONE) {
            if (minecraft.player != null && minecraft.player.getAbilities().instabuild) {
                reset();
                sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6F, 0.6F);
            } else {
                onClose();
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void drop() {
        phase = Phase.DROP;
        phaseTicks = 0;
        // what's under it: the highest plushie whose middle is near enough to catch, if any
        ClawMachineBlockEntity be = machine();
        target = -1;
        targetY = LOWEST;
        float best = 99.0F;
        if (be != null) {
            for (Prize p : be.prizes()) {
                float d = Mth.sqrt((p.x() - x) * (p.x() - x) + (p.z() - z) * (p.z() - z));
                if (d > 2.6F) continue;
                float score = d - (p.top() ? 2.0F : 0.0F);
                if (score < best) {
                    best = score;
                    target = p.id();
                }
            }
            Prize p = target >= 0 ? be.prize(target) : null;
            if (p != null) {
                targetY = Math.max(LOWEST, ClawMachineBlockEntity.topOf(p) + 1.0F);
            }
        }
        sound(SoundEvents.PISTON_CONTRACT, 1.8F, 0.35F);
    }

    @Override
    public void tick() {
        ox = x;
        oz = z;
        oy = y;
        oOpen = open;
        oFallY = fallY;
        phaseTicks++;
        ClawMachineBlockEntity be = machine();
        if (be == null || minecraft.player == null || minecraft.player.distanceToSqr(Vec3.atCenterOf(pos)) > 8 * 8) {
            onClose();
            return;
        }
        switch (phase) {
            case AIM -> {
                float sp = 0.24F, dx = 0, dz = 0;
                if (down(minecraft.options.keyLeft, GLFW.GLFW_KEY_LEFT)) dx += sp; // (seen from the front, its left is +x)
                if (down(minecraft.options.keyRight, GLFW.GLFW_KEY_RIGHT)) dx -= sp;
                if (down(minecraft.options.keyUp, GLFW.GLFW_KEY_UP)) dz += sp;
                if (down(minecraft.options.keyDown, GLFW.GLFW_KEY_DOWN)) dz -= sp;
                x = Mth.clamp(x + dx, X0 + 1.4F, X1 - 1.4F);
                z = Mth.clamp(z + dz, Z0 + 1.4F, Z1 - 1.4F);
                boolean moving = dx != 0 || dz != 0;
                if (moving && (!wasMoving || phaseTicks % 8 == 0)) sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.9F, 0.25F);
                wasMoving = moving;
                if (--timer <= 0) drop();
            }
            case DROP -> {
                y = Math.max(targetY, y - 0.32F);
                if (y <= targetY) {
                    phase = Phase.GRAB;
                    phaseTicks = 0;
                    sound(SoundEvents.CHAIN_STEP, 1.4F, 0.6F);
                }
            }
            case GRAB -> {
                open = Math.max(0.0F, 1.0F - phaseTicks / 10.0F);
                if (phaseTicks >= 12) {
                    grab(be);
                    phase = Phase.RISE;
                    phaseTicks = 0;
                    sound(SoundEvents.PISTON_EXTEND, 1.8F, 0.35F);
                }
            }
            case RISE -> {
                y = Math.min(PARK, y + 0.26F);
                if (holding >= 0 && phaseTicks == slipAt) { // (a weak grip: it slips back out)
                    Prize p = be.prize(holding);
                    if (p != null) {
                        falling = holding;
                        fallX = p.x();
                        fallZ = p.z();
                        fallY = oFallY = y - PRONG + 0.4F;
                    }
                    holding = -1;
                    slipped = true;
                    open = 0.35F;
                    sound(SoundEvents.WOOL_FALL, 1.0F, 0.8F);
                }
                if (y >= PARK) {
                    phase = Phase.CARRY;
                    phaseTicks = 0;
                }
            }
            case CARRY -> {
                float dx = CHUTE_X - x, dz = CHUTE_Z - z, d = Mth.sqrt(dx * dx + dz * dz);
                if (d <= 0.22F) {
                    x = CHUTE_X;
                    z = CHUTE_Z;
                    phase = Phase.RELEASE;
                    phaseTicks = 0;
                } else {
                    x += dx / d * 0.22F;
                    z += dz / d * 0.22F;
                }
            }
            case RELEASE -> {
                open = Math.min(1.0F, open + 0.12F);
                if (phaseTicks == 3 && holding >= 0) {
                    falling = holding;
                    fallX = x;
                    fallZ = z;
                    fallY = oFallY = y - PRONG + 0.4F;
                    hidden = holding;
                    holding = -1;
                    sound(SoundEvents.WOOL_PLACE, 1.2F, 0.8F);
                }
                if (phaseTicks >= 10) {
                    phase = Phase.FALL;
                    phaseTicks = 0;
                }
            }
            case FALL -> {
                if (phaseTicks >= 16 || falling < 0) {
                    finish(be);
                }
            }
            case DONE -> {}
        }
        // a plushie falling: back onto the heap if it slipped, down the chute if it was let go there
        if (falling >= 0) {
            float floor = falling == hidden ? FLOOR - 6.0F : baseOf(be, falling);
            fallY = Math.max(floor, fallY - 0.25F - (fallY > floor ? 0.06F * phaseTicks : 0));
            if (fallY <= floor && falling != hidden) falling = -1;
        }
        if (phaseTicks % 2 == 0 || phase == Phase.DONE && phaseTicks == 1) send();
    }

    private static float baseOf(ClawMachineBlockEntity be, int id) {
        Prize p = be.prize(id);
        return FLOOR + (p != null && p.top() ? ClawMachineBlockEntity.STACKED : 0.0F);
    }

    /** Closing on it: how close the claw is to the plushie's middle decides how good the grip is. */
    private void grab(ClawMachineBlockEntity be) {
        holding = -1;
        slipAt = -1;
        Prize p = target >= 0 ? be.prize(target) : null;
        if (p == null) {
            return;
        }
        float d = Mth.sqrt((p.x() - x) * (p.x() - x) + (p.z() - z) * (p.z() - z));
        float chance = Mth.clamp(0.95F - d * 0.3F, 0.12F, 0.9F) + (p.top() ? 0.05F : 0.0F);
        float roll = random.nextFloat();
        if (roll < chance) {
            holding = p.id();
            if (roll > chance * 0.55F) { // (a weak grip: it'll slip on the way up)
                slipAt = 6 + random.nextInt(30);
            }
        }
    }

    private void finish(ClawMachineBlockEntity be) {
        phase = Phase.DONE;
        phaseTicks = 0;
        int won = hidden >= 0 && be.prize(hidden) != null ? hidden : -1;
        wonKind = won >= 0 ? be.prize(won).kind() : -1;
        PacketDistributor.sendToServer(new ClawPayloads.Play(pos, won));
        if (won >= 0) {
            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 0.8F);
            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5F, 0.8F);
        } else {
            sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.8F);
        }
    }

    /** Whether the renderer should leave this prize out of the heap (it's in the claw, or falling, or won). */
    boolean leaveOut(int id) {
        return id == holding || id == falling || id == hidden;
    }

    private void send() {
        int nx = Math.round(Mth.clamp((x - X0) / (X1 - X0), 0, 1) * 1000);
        int nz = Math.round(Mth.clamp((z - Z0) / (Z1 - Z0), 0, 1) * 1000);
        int depth = Math.round(Mth.clamp((PARK - y) / (PARK - LOWEST), 0, 1) * 1000) + (open < 0.5F ? 2000 : 0);
        if (active != this) {
            nx = Math.round((CHUTE_X - X0) / (X1 - X0) * 1000);
            nz = Math.round((CHUTE_Z - Z0) / (Z1 - Z0) * 1000);
            depth = 0;
        }
        PacketDistributor.sendToServer(new ClawPayloads.State(pos, nx, nz, depth, active == this ? holding : -1));
    }

    private void sound(SoundEvent s, float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        // the time left (a bar that empties), the keys, how it went
        if (phase == Phase.AIM) {
            int w = 120, left = cx - w / 2, top = 16;
            g.fill(left - 2, top - 2, left + w + 2, top + 8, 0xB0101020);
            float t = timer / (float) TIME;
            int fill = (int) (w * t);
            int col = t > 0.5F ? 0xFF5BFF6A : t > 0.25F ? 0xFFFFE74A : 0xFFFF5A5A;
            g.fill(left, top, left + fill, top + 6, col);
            g.drawCenteredString(font, String.valueOf(timer / 20 + 1), cx, top + 12, 0xFFFFFFFF);
            g.drawCenteredString(font, Component.translatable("screen.skyblocks.claw.help"), cx, height - 30, 0xFFFFFFFF);
        } else if (phase == Phase.DONE) {
            Component msg = wonKind >= 0 && wonKind < ModItems.PLUSHIES.size()
                    ? Component.translatable("screen.skyblocks.claw.won", new ItemStack(ModItems.PLUSHIES.get(wonKind).get()).getHoverName())
                    : Component.translatable(slipped ? "screen.skyblocks.claw.slipped" : "screen.skyblocks.claw.missed");
            int w = Math.max(160, font.width(msg) + 24);
            g.fill(cx - w / 2, 22, cx + w / 2, 46, 0xC0101020);
            g.drawCenteredString(font, msg, cx, 30, wonKind >= 0 ? 0xFF5BFF6A : 0xFFFF9A9A);
            boolean creative = minecraft.player != null && minecraft.player.getAbilities().instabuild;
            g.drawCenteredString(font, Component.translatable(creative ? "screen.skyblocks.claw.again_creative" : "screen.skyblocks.claw.again"),
                    cx, height - 30, 0xFFDDDDDD);
            if (wonKind >= 0) {
                g.pose().pushPose();
                g.pose().translate(cx, 66, 0);
                g.pose().scale(2.0F, 2.0F, 1.0F);
                g.renderItem(new ItemStack(ModItems.PLUSHIES.get(wonKind).get()), -8, -8);
                g.pose().popPose();
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
