package com.example.skyblocks.client;

import static com.example.skyblocks.ClawMachineBlockEntity.FLOOR;
import static com.example.skyblocks.ClawMachineBlockEntity.PRIZE_SCALE;
import static com.example.skyblocks.ClawMachineBlockEntity.STACKED;
import static com.example.skyblocks.ClawMachineBlockEntity.TOP;
import static com.example.skyblocks.ClawMachineBlockEntity.X0;
import static com.example.skyblocks.ClawMachineBlockEntity.X1;
import static com.example.skyblocks.ClawMachineBlockEntity.Z0;
import static com.example.skyblocks.ClawMachineBlockEntity.Z1;

import com.example.skyblocks.ClawMachineBlock;
import com.example.skyblocks.ClawMachineBlockEntity;
import com.example.skyblocks.ClawMachineBlockEntity.Prize;
import com.example.skyblocks.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Inside the claw machine's glass: its plushies (each where it lies), the bridge across the rails and the carriage on it,
 * the cable, and the claw - a hub with three hinged prongs that open and close. Played here, it follows ClawScreen; played
 * by someone else, what they send; nobody playing, it rests over the chute, swaying a little.
 */
public class ClawMachineRenderer implements BlockEntityRenderer<ClawMachineBlockEntity> {
    private static final BlockState CHROME = Blocks.IRON_BLOCK.defaultBlockState(), DARK = Blocks.GRAY_CONCRETE.defaultBlockState(),
            GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final float RAIL = TOP - 0.4F;

    public ClawMachineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ClawMachineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!(be.getLevel() instanceof ClientLevel level) || !(be.getBlockState().getBlock() instanceof ClawMachineBlock)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(ClawMachineBlock.FACING);
        BlockPos pos = be.getBlockPos();
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F)));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        int inside = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos.above());
        inside = Math.max(inside & 0xFFFF, 11 << 4) | (inside & 0xFFFF0000); // (lit from above inside)

        // where the claw is: played here, played by someone else, or resting
        ClawScreen game = ClawScreen.active != null && ClawScreen.active.pos.equals(pos) ? ClawScreen.active : null;
        boolean remote = game == null && level.getGameTime() - be.liveAt < 30L;
        float t = (level.getGameTime() + partialTick) / 20.0F;
        float px, pz, py, open;
        int holding;
        if (game != null) {
            px = Mth.lerp(partialTick, game.ox, game.x);
            pz = Mth.lerp(partialTick, game.oz, game.z);
            py = Mth.lerp(partialTick, game.oy, game.y);
            open = Mth.lerp(partialTick, game.oOpen, game.open);
            holding = game.holding;
        } else if (remote) {
            px = Mth.lerp(be.liveX, X0, X1);
            pz = Mth.lerp(be.liveZ, Z0, Z1);
            py = Mth.lerp(be.liveDepth, ClawScreen.PARK, ClawScreen.LOWEST);
            open = be.liveOpen ? 1.0F : 0.0F;
            holding = be.liveHolding;
        } else {
            px = (ClawMachineBlockEntity.CHUTE_X0 + X1) / 2 + Mth.sin(t * 0.7F) * 0.08F;
            pz = (Z0 + ClawMachineBlockEntity.CHUTE_Z1) / 2;
            py = ClawScreen.PARK;
            open = 1.0F;
            holding = -1;
        }

        // the plushies
        Prize held = null;
        for (Prize p : be.prizes()) {
            if (p.id() == holding) held = p;
            if (p.id() == holding || game != null && game.leaveOut(p.id())) continue;
            prize(poseStack, buffers, level, p.kind(), p.x(), FLOOR + (p.top() ? STACKED : 0.0F), p.z(), p.turn(), p.top() ? 12.0F : 0.0F, inside, overlay);
        }
        if (game != null && game.falling >= 0) {
            Prize p = be.prize(game.falling);
            float fy = Mth.lerp(partialTick, game.oFallY, game.fallY);
            if (p != null && fy > FLOOR - 4.0F) {
                prize(poseStack, buffers, level, p.kind(), game.fallX, fy, game.fallZ, p.turn(), 0.0F, inside, overlay);
            }
        }

        // the bridge across the rails, the carriage, the cable, the hub
        box(poseStack, buffers, level, pos, inside, overlay, CHROME, X0 + 0.4F, RAIL - 0.9F, pz - 0.45F, X1 - 0.4F, RAIL - 0.2F, pz + 0.45F);
        box(poseStack, buffers, level, pos, inside, overlay, DARK, px - 1.1F, RAIL - 1.7F, pz - 1.1F, px + 1.1F, RAIL - 0.8F, pz + 1.1F);
        box(poseStack, buffers, level, pos, inside, overlay, GOLD, px - 0.5F, RAIL - 1.9F, pz - 0.5F, px + 0.5F, RAIL - 1.7F, pz + 0.5F);
        box(poseStack, buffers, level, pos, inside, overlay, DARK, px - 0.12F, py + 1.2F, pz - 0.12F, px + 0.12F, RAIL - 1.9F, pz + 0.12F);
        box(poseStack, buffers, level, pos, inside, overlay, CHROME, px - 0.9F, py + 0.3F, pz - 0.9F, px + 0.9F, py + 1.2F, pz + 0.9F);
        box(poseStack, buffers, level, pos, inside, overlay, GOLD, px - 0.6F, py, pz - 0.6F, px + 0.6F, py + 0.3F, pz + 0.6F);
        // three prongs, hinged at the hub: out and down, the tips curling in (open: splayed wide; shut: tips together)
        float splay = Mth.lerp(open, 4.0F, 30.0F);
        for (int k = 0; k < 3; k++) {
            poseStack.pushPose();
            poseStack.translate(px / 16.0F, py / 16.0F, pz / 16.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(k * 120.0F + 30.0F));
            poseStack.translate(0.0F, 0.0F, 0.55F / 16.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-splay)); // (out, away from the hub)
            // (in the prong's own pixels: hinged at 0, hanging down along -y, its tip turned back in)
            box(poseStack, buffers, level, pos, inside, overlay, CHROME, -0.22F, -1.8F, -0.18F, 0.22F, 0.0F, 0.18F);
            poseStack.translate(0.0F, -1.8F / 16.0F, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(38.0F)); // (the tip curls back in)
            box(poseStack, buffers, level, pos, inside, overlay, CHROME, -0.2F, -1.1F, -0.16F, 0.2F, 0.0F, 0.16F);
            poseStack.popPose();
        }
        // what it's holding, in its prongs
        if (held != null) {
            prize(poseStack, buffers, level, held.kind(), px, py - ClawScreen.PRONG + 0.4F, pz, held.turn(), 0.0F, inside, overlay);
        }
        poseStack.popPose();
    }

    /** A plushie standing with its bottom at {@code y} (pixels), turned {@code turn}, tipped {@code tip} degrees. */
    private static void prize(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level, int kind, float x, float y, float z, float turn, float tip,
            int light, int overlay) {
        if (kind < 0 || kind >= ModItems.PLUSHIES.size()) return;
        float s = PRIZE_SCALE;
        poseStack.pushPose();
        poseStack.translate(x / 16.0F, (y + 8.0F * 0.9F * s) / 16.0F, z / 16.0F); // (FIXED: the model's middle at the origin)
        poseStack.mulPose(Axis.YP.rotationDegrees(turn));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tip));
        poseStack.scale(s, s, s);
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(ModItems.PLUSHIES.get(kind).get()), ItemDisplayContext.FIXED,
                light, overlay, poseStack, buffers, level, 0);
        poseStack.popPose();
    }

    private static void box(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level, BlockPos pos, int light, int overlay, BlockState skin,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        BlockSkin.box(skin, level, pos, poseStack, buffers, light, overlay, x0, y0, z0, x1, y1, z1);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ClawMachineBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0.0, 1.0, 0.0);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
