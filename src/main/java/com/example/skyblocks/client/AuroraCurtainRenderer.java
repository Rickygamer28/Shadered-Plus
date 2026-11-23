package com.example.skyblocks.client;

import com.example.skyblocks.AuroraCurtainBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * The Aurora Curtain's light (shaders/core/aurora_curtain): a sheet hanging from the rail down to the floor (or 8
 * blocks), folded in slow waves - the waves worked out from the world's position along it, so curtains side by side
 * hang as one - its upper colour at the top fading into its lower colour at the bottom.
 */
public class AuroraCurtainRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    private static final float TOP = 14.0F / 16.0F;
    private static final int ACROSS = 8, MAX = 8;

    public AuroraCurtainRenderer(BlockEntityRendererProvider.Context context) {}

    /** How far it hangs: down to the first block in the way (at most MAX). */
    private static int length(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 1; i <= MAX; i++) {
            p.setY(pos.getY() - i);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.getCollisionShape(level, p).isEmpty()) {
                return i;
            }
        }
        return MAX;
    }

    private static float wave(float along, float down, float t) {
        float deep = 0.35F + 0.65F * Math.min(down / 3.0F, 1.0F);
        return (0.16F * Mth.sin(along * 1.3F + t * 0.035F) + 0.06F * Mth.sin(along * 3.1F - t * 0.06F + down * 0.7F)) * deep;
    }

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || FxShaders.aurora == null || !state.hasProperty(AuroraCurtainBlock.AXIS)) {
            return;
        }
        float t = (level.getGameTime() % 240000L) + partialTick;
        boolean alongX = state.getValue(AuroraCurtainBlock.AXIS) == Direction.Axis.X;
        float hang = length(level, be.getBlockPos()) - 1 + TOP;
        int bottom = state.getValue(AuroraCurtainBlock.BOTTOM).getFireworkColor(), top = state.getValue(AuroraCurtainBlock.TOP).getFireworkColor();
        int a = Mth.clamp(Math.round(hang / 16.0F * 255.0F), 1, 255);
        int rows = Math.max(2, Math.round(hang * 2));
        float base = alongX ? be.getBlockPos().getX() : be.getBlockPos().getZ();
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buffers.getBuffer(FxShaders.Types.AURORA);
        for (int i = 0; i < ACROSS; i++) {
            float s0 = i / (float) ACROSS, s1 = (i + 1) / (float) ACROSS;
            for (int j = 0; j < rows; j++) {
                float d0 = hang * j / rows, d1 = hang * (j + 1) / rows;
                vertex(vc, m, alongX, s0, d0, base, t, hang, top, bottom, a);
                vertex(vc, m, alongX, s1, d0, base, t, hang, top, bottom, a);
                vertex(vc, m, alongX, s1, d1, base, t, hang, top, bottom, a);
                vertex(vc, m, alongX, s0, d1, base, t, hang, top, bottom, a);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, boolean alongX, float s, float down, float base, float t, float hang, int top, int bottom, int a) {
        float along = base + s;
        float off = wave(along, down, t);
        float y = TOP - down;
        float k = down / hang;
        int r = Math.round(Mth.lerp(k, top >> 16 & 255, bottom >> 16 & 255)), g = Math.round(Mth.lerp(k, top >> 8 & 255, bottom >> 8 & 255)),
                b = Math.round(Mth.lerp(k, top & 255, bottom & 255));
        float x = alongX ? s : 0.5F + off, z = alongX ? 0.5F + off : s;
        vc.addVertex(m, x, y, z).setUv(along, down).setColor(r, g, b, a);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SimpleRenderedBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.4, 0.0, 0.4).expandTowards(0, -MAX, 0);
    }
}
