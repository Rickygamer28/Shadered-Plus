package com.example.skyblocks.client;

import com.example.skyblocks.PhaseBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A phase block, drawn (shaders/core/phase_block): its panel, and, as it opens, burning away - holes eating through it
 * with glowing orange edges until nothing's left; closing, the same backwards. The faces it shares with its own wall
 * (phase blocks the same, open or shut) or with solid blocks aren't drawn.
 */
public class PhaseBlockRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance shader;
    /** How long opening or closing takes (ticks). */
    private static final float TAKES = 22.0F;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/block/phase_block.png");

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerPhaseShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "phase_block"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType PANEL = create("skyblocks_phase_block", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1024, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(TEXTURE, false, false))
                        .setCullState(CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public PhaseBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(be.getLevel() instanceof ClientLevel level) || shader == null || !(state.getBlock() instanceof PhaseBlock)) {
            return;
        }
        boolean open = state.getValue(PhaseBlock.OPEN);
        float now = level.getGameTime() + partialTick;
        // (when it last changed: clientA 1 open / 0 shut as last seen, clientB when; never seen - as it is, done)
        if (be.clientTick == 0) {
            be.clientTick = 1;
            be.clientA = open ? 1 : 0;
            be.clientB = -1000.0F;
        } else if ((be.clientA > 0.5F) != open) {
            be.clientA = open ? 1 : 0;
            be.clientB = now;
        }
        float t = Mth.clamp((now - be.clientB) / TAKES, 0.0F, 1.0F);
        float gone = open ? t : 1.0F - t;
        if (gone >= 0.999F) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        int hash = (int) ((pos.asLong() * 0x9E3779B97F4A7C15L) >>> 56) & 255;
        float day = Math.max(0.0F, (level.getSkyDarken(partialTick) - 0.2F) / 0.8F);
        int[] faces = new int[6];
        for (Direction d : Direction.values()) {
            BlockPos q = pos.relative(d);
            BlockState next = level.getBlockState(q);
            boolean hidden = next.getBlock() instanceof PhaseBlock ? next.getValue(PhaseBlock.OPEN) == open : next.isSolidRender(level, q);
            if (hidden) {
                faces[d.ordinal()] = -1;
                continue;
            }
            int packed = LevelRenderer.getLightColor(level, q);
            float lit = Math.max(LightTexture.block(packed), LightTexture.sky(packed) * day) / 15.0F;
            // (lit like a block: a little darker on its sides, darker still underneath)
            float shade = d == Direction.UP ? 1.0F : d == Direction.DOWN ? 0.5F : d.getAxis() == Direction.Axis.Z ? 0.8F : 0.6F;
            faces[d.ordinal()] = Mth.clamp(Math.round((0.12F + 0.88F * lit) * shade * 255), 0, 255);
        }
        int a = Mth.clamp(Math.round(gone * 255), 0, 255);
        Runnable draw = () -> ElevatorRenderer.drawNow(Types.PANEL, vc -> {
            for (Direction d : Direction.values()) {
                if (faces[d.ordinal()] >= 0) {
                    face(vc, m, d, faces[d.ordinal()], hash, d.ordinal() * 40, a);
                }
            }
        });
        if (!LatePass.later(src -> draw.run())) {
            draw.run();
        }
    }

    /** One face of the block, out the way d: its corners, the texture across it, light / which block / which face / how gone. */
    private static void face(VertexConsumer vc, Matrix4f m, Direction d, int lit, int hash, int side, int gone) {
        float[][] c = switch (d) {
            case UP -> new float[][] {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case DOWN -> new float[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
            case NORTH -> new float[][] {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}};
            case SOUTH -> new float[][] {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}};
            case WEST -> new float[][] {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}};
            default -> new float[][] {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}};
        };
        float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(m, c[i][0], c[i][1], c[i][2]).setUv(uv[i][0], uv[i][1]).setColor(lit, hash, side, gone);
        }
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
