package com.example.skyblocks.client;

import com.example.skyblocks.PhaseBlock;
import com.example.skyblocks.PhaseBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
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
 * A phase block, drawn (shaders/core/phase_block): as the block it's set to look like (its own panel if none), each face
 * that block's picture, tinted as it would be; and, as it opens, going away as it's set to - burning, in pixels, wiped by
 * a scan line, fading as a hologram, or in a ripple - and coming back the same way, backwards. Opening while it's still
 * shutting (or the other way) it turns round from where it is. The faces it shares with its own wall (phase blocks the
 * same, open or shut) or with solid blocks aren't drawn.
 */
public class PhaseBlockRenderer implements BlockEntityRenderer<PhaseBlockEntity> {
    static ShaderInstance shader;
    /** How long opening or closing takes (ticks). */
    private static final float TAKES = 22.0F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerPhaseShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "phase_block"), DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType PANEL = create("skyblocks_phase_block", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, VertexFormat.Mode.QUADS, 1024, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                        .setCullState(CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** Its look, worked out once for the block it looks like: per face its picture's corners in the block atlas and its tint. */
    private static final class Look {
        BlockState of;
        final float[][] uv = new float[6][];
        final int[] tint = new int[6];
    }

    private static final Map<PhaseBlockEntity, Look> LOOKS = new WeakHashMap<>();

    public PhaseBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PhaseBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(be.getLevel() instanceof ClientLevel level) || shader == null || !(state.getBlock() instanceof PhaseBlock)) {
            return;
        }
        boolean open = state.getValue(PhaseBlock.OPEN);
        float now = level.getGameTime() + partialTick;
        // (clientA: 1 open / 0 shut as last seen, clientB: when it turned that way - set back so it carries on from where it was)
        if (be.clientTick == 0) {
            be.clientTick = 1;
            be.clientA = open ? 1 : 0;
            be.clientB = -1000.0F;
        } else if ((be.clientA > 0.5F) != open) {
            float was = Mth.clamp((now - be.clientB) / TAKES, 0.0F, 1.0F);
            float goneNow = be.clientA > 0.5F ? was : 1.0F - was;
            be.clientA = open ? 1 : 0;
            be.clientB = now - (open ? goneNow : 1.0F - goneNow) * TAKES;
        }
        float t = Mth.clamp((now - be.clientB) / TAKES, 0.0F, 1.0F);
        float gone = open ? t : 1.0F - t;
        if (gone >= 0.999F) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        boolean own = be.disguise().isAir();
        BlockState lookOf = own ? state : be.disguise();
        Look look = LOOKS.computeIfAbsent(be, k -> new Look());
        if (look.of != lookOf) {
            work(look, lookOf, level, pos);
        }
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        int seed = (int) ((pos.asLong() * 0x9E3779B97F4A7C15L) >>> 59) & 15;
        float day = Math.max(0.0F, (level.getSkyDarken(partialTick) - 0.2F) / 0.8F);
        int[] lit = new int[6];
        for (Direction d : Direction.values()) {
            BlockPos q = pos.relative(d);
            BlockState next = level.getBlockState(q);
            boolean hidden = next.getBlock() instanceof PhaseBlock ? next.getValue(PhaseBlock.OPEN) == open : next.isSolidRender(level, q);
            if (hidden) {
                lit[d.ordinal()] = -1;
                continue;
            }
            int packed = LevelRenderer.getLightColor(level, q);
            float l = Math.max(LightTexture.block(packed), LightTexture.sky(packed) * day) / 15.0F;
            // (lit like a block: a little darker on its sides, darker still underneath)
            float shade = d == Direction.UP ? 1.0F : d == Direction.DOWN ? 0.5F : d.getAxis() == Direction.Axis.Z ? 0.8F : 0.6F;
            lit[d.ordinal()] = Mth.clamp(Math.round((0.12F + 0.88F * l) * shade * 255), 0, 255);
        }
        int a = Mth.clamp(Math.round(gone * 255), 0, 255);
        int mode = be.mode();
        Runnable draw = () -> ElevatorRenderer.drawNow(Types.PANEL, vc -> {
            for (Direction d : Direction.values()) {
                int f = d.ordinal();
                if (lit[f] >= 0) {
                    // (which way it goes, which block and face (its own pattern), whether it's its own panel - one number)
                    int code = mode * 40 + (own ? 20 : 0) + (seed + f * 3) % 20;
                    face(vc, m, d, look.uv[f], look.tint[f], lit[f], a, code);
                }
            }
        });
        if (!LatePass.later(src -> draw.run())) {
            draw.run();
        }
    }

    /** Each face's picture of a block (its model's quad that way, else any, else its particle picture) and tint. */
    private static void work(Look look, BlockState of, ClientLevel level, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(of);
        RandomSource rnd = RandomSource.create(42L);
        for (Direction d : Direction.values()) {
            List<BakedQuad> quads = model.getQuads(of, d, rnd, ModelData.EMPTY, null);
            if (quads.isEmpty()) {
                quads = model.getQuads(of, null, rnd, ModelData.EMPTY, null);
            }
            BakedQuad quad = null;
            for (BakedQuad q : quads) {
                if (q.getDirection() == d) {
                    quad = q;
                    break;
                }
            }
            if (quad == null && !quads.isEmpty()) {
                quad = quads.get(0);
            }
            TextureAtlasSprite sprite = quad != null ? quad.getSprite() : model.getParticleIcon(ModelData.EMPTY);
            look.uv[d.ordinal()] = new float[] {sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()};
            look.tint[d.ordinal()] = quad != null && quad.isTinted() ? mc.getBlockColors().getColor(of, level, pos, quad.getTintIndex()) : -1;
        }
        look.of = of;
    }

    /** One face of the block, out the way d: its corners, its picture across it (and where on the face, for the shader). */
    private static void face(VertexConsumer vc, Matrix4f m, Direction d, float[] uv, int tint, int lit, int gone, int code) {
        float[][] c = switch (d) {
            case UP -> new float[][] {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case DOWN -> new float[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
            case NORTH -> new float[][] {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}};
            case SOUTH -> new float[][] {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}};
            case WEST -> new float[][] {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}};
            default -> new float[][] {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}};
        };
        float[][] local = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        int r = tint == -1 ? lit : (tint >> 16 & 255) * lit / 255, g = tint == -1 ? lit : (tint >> 8 & 255) * lit / 255, b = tint == -1 ? lit : (tint & 255) * lit / 255;
        float z = (code - 127) / 127.0F;
        for (int i = 0; i < 4; i++) {
            float u = uv[0] + (uv[2] - uv[0]) * local[i][0], v = uv[1] + (uv[3] - uv[1]) * local[i][1];
            vc.addVertex(m, c[i][0], c[i][1], c[i][2]).setUv(u, v).setColor(r, g, b, gone).setNormal(local[i][0] * 2 - 1, local[i][1] * 2 - 1, z);
        }
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
