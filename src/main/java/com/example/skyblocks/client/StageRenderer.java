package com.example.skyblocks.client;

import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.StageBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A stage: bulbs along its outer edges, chasing round (faster and brighter with the music); when a song starts, smoke
 * billows off its edges for a moment.
 */
public class StageRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    private static final BlockState WARM = Blocks.YELLOW_CONCRETE.defaultBlockState(), WHITE = Blocks.WHITE_CONCRETE.defaultBlockState(),
            OFF = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static float lastMusic;
    private static long smokeUntil, checkedAt = -1;

    public StageRenderer(BlockEntityRendererProvider.Context context) {}

    /** (once a tick, for every stage: has a song just started?) */
    private static void listen(ClientLevel level) {
        long now = level.getGameTime();
        if (now == checkedAt) return;
        checkedAt = now;
        MusicReactor.tick();
        float music = MusicReactor.music();
        if (lastMusic < 0.05F && music > 0.15F) {
            smokeUntil = now + 40;
        }
        lastMusic = music;
    }

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!(be.getLevel() instanceof ClientLevel level)) {
            return;
        }
        listen(level);
        BlockPos pos = be.getBlockPos();
        float t = (level.getGameTime() + partialTick) / 20.0F;
        float beat = MusicReactor.bass();
        float speed = 4.0F + MusicReactor.music() * 10.0F;
        int index = (pos.getX() + pos.getZ()) * 4; // (bulb numbering running on round the stage)
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof StageBlock) {
                continue; // (an inside edge: no bulbs)
            }
            for (int i = 0; i < 4; i++) {
                // (on the side's face, standing out from it a little, halfway up)
                float along = 2.0F + i * 4.0F, out = 0.7F;
                float x, z;
                switch (d) {
                    case NORTH -> { x = along; z = -out / 2; }
                    case SOUTH -> { x = 16 - along; z = 16 + out / 2; }
                    case WEST -> { x = -out / 2; z = 16 - along; }
                    default -> { x = 16 + out / 2; z = along; }
                }
                boolean on = ((int) (t * speed) + index + i) % 3 != 0 || beat > 0.6F;
                BlockState bulb = on ? ((index + i) % 2 == 0 ? WARM : WHITE) : OFF;
                float r = 0.7F;
                BlockSkin.box(bulb, level, pos, poseStack, buffers, on ? LightTexture.FULL_BRIGHT : light, overlay,
                        x - (d.getAxis() == Direction.Axis.X ? out / 2 : r), 3.3F, z - (d.getAxis() == Direction.Axis.Z ? out / 2 : r),
                        x + (d.getAxis() == Direction.Axis.X ? out / 2 : r), 4.7F, z + (d.getAxis() == Direction.Axis.Z ? out / 2 : r));
            }
        }
        // the smoke when a song starts: off the stage's outer edges
        long now = level.getGameTime();
        if (now < smokeUntil && now != be.clientTick && !Minecraft.getInstance().isPaused()) {
            be.clientTick = now;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(pos.relative(d)).getBlock() instanceof StageBlock || level.random.nextInt(3) != 0) continue;
                double px = pos.getX() + 0.5 + d.getStepX() * 0.55 + (level.random.nextDouble() - 0.5) * (d.getStepX() == 0 ? 0.9 : 0.1);
                double pz = pos.getZ() + 0.5 + d.getStepZ() * 0.55 + (level.random.nextDouble() - 0.5) * (d.getStepZ() == 0 ? 0.9 : 0.1);
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, pos.getY() + 0.5, pz, d.getStepX() * 0.03, 0.01, d.getStepZ() * 0.03);
                level.addParticle(ParticleTypes.CLOUD, px, pos.getY() + 0.4, pz, d.getStepX() * 0.06, 0.0, d.getStepZ() * 0.06);
            }
        }
    }
}
