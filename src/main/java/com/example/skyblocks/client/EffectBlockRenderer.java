package com.example.skyblocks.client;

import com.example.skyblocks.EffectBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The effect block: its sides that can be seen (not against a solid block), drawn with shaders/core/effect_block and
 * its settings - each block drawn at once with its own. A side's texture places are where it is on the group's
 * picture: blocks from the group's anchor, along the side's two axes, so joined blocks show one effect across them.
 */
public class EffectBlockRenderer implements BlockEntityRenderer<EffectBlockEntity> {
    static ShaderInstance shader;
    private static EffectBlockEntity.Settings current = EffectBlockEntity.Settings.DEFAULT;
    private static float time;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerEffectBlockShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "effect_block"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    private static void setUp() {
        if (shader == null) {
            return;
        }
        EffectBlockEntity.Settings s = current;
        MusicReactor.tick();
        shader.safeGetUniform("Time").set(time);
        shader.safeGetUniform("Effect").set((float) s.effect());
        shader.safeGetUniform("Count").set((float) s.count());
        shader.safeGetUniform("Speed").set(s.speed() / 100.0F);
        shader.safeGetUniform("Size").set(s.size() / 100.0F);
        shader.safeGetUniform("Bright").set(s.brightness() / 100.0F);
        shader.safeGetUniform("Pixel").set(s.pixel() ? 1.0F : 0.0F);
        shader.safeGetUniform("Boost").set(Math.min(MusicReactor.bass() * 1.5F, 1.0F));
        for (int i = 0; i < EffectBlockEntity.MAX_COLOURS; i++) {
            int c = s.colours()[i];
            shader.safeGetUniform("Col" + i).set(((c >> 16) & 255) / 255.0F, ((c >> 8) & 255) / 255.0F, (c & 255) / 255.0F);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType SIDES = create("skyblocks_effect_block", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1024,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_effect_block_settings", EffectBlockRenderer::setUp, () -> {}))
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public EffectBlockRenderer(BlockEntityRendererProvider.Context context) {}

    /** Seconds for the effects (the same for every block, so joined ones keep together). */
    static float seconds(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0.0F : ((mc.level.getGameTime() % 240000L) + partialTick) / 20.0F;
    }

    @Override
    public void render(EffectBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (shader == null || level == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        int faces = 0;
        for (Direction d : Direction.values()) {
            if (Block.shouldRenderFace(state, level, pos, d, pos.relative(d))) {
                faces |= 1 << d.ordinal();
            }
        }
        if (faces == 0) {
            return;
        }
        BlockPos a = be.anchor();
        current = be.settings();
        time = seconds(partialTick);
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        cube(source.getBuffer(Types.SIDES), poseStack.last().pose(), faces, pos.getX() - a.getX(), pos.getY() - a.getY(), pos.getZ() - a.getZ());
        source.endBatch(Types.SIDES);
    }

    /** As an item: every side, its default look. */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers) {
        if (shader == null) {
            return;
        }
        current = EffectBlockEntity.Settings.DEFAULT;
        time = seconds(0.0F);
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        cube(source.getBuffer(Types.SIDES), poseStack.last().pose(), 63, 0, 0, 0);
        source.endBatch(Types.SIDES);
    }

    /** The shown sides of a block {@code dx, dy, dz} from its group's anchor. */
    private static void cube(VertexConsumer vc, Matrix4f m, int faces, int dx, int dy, int dz) {
        for (Direction d : Direction.values()) {
            if ((faces & (1 << d.ordinal())) == 0) {
                continue;
            }
            // (a little light and shade by side, like a block's: still glowing, but its shape shows)
            float shade = switch (d) {
                case UP -> 1.0F;
                case DOWN -> 0.72F;
                case NORTH, SOUTH -> 0.9F;
                default -> 0.82F;
            };
            int s = (int) (shade * 255.0F);
            int faceCode = d.ordinal() * 40;
            for (int corner = 0; corner < 4; corner++) {
                float cu = (corner == 1 || corner == 2) ? 1.0F : 0.0F, cv = corner >= 2 ? 1.0F : 0.0F;
                float x, y, z, u, v;
                switch (d) {
                    case DOWN -> { x = cu; y = 0.0F; z = cv; u = dx + cu; v = dz + cv; }
                    case UP -> { x = cu; y = 1.0F; z = cv; u = dx + cu; v = dz + cv; }
                    case NORTH -> { x = cu; y = cv; z = 0.0F; u = dx + cu; v = dy + cv; }
                    case SOUTH -> { x = cu; y = cv; z = 1.0F; u = dx + cu; v = dy + cv; }
                    case WEST -> { x = 0.0F; y = cv; z = cu; u = dz + cu; v = dy + cv; }
                    default -> { x = 1.0F; y = cv; z = cu; u = dz + cu; v = dy + cv; }
                }
                vc.addVertex(m, x, y, z).setUv(u, v).setColor(s, s, s, faceCode);
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
