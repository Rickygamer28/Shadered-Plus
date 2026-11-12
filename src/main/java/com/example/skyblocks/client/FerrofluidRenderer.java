package com.example.skyblocks.client;

import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The ferrofluid display's fluid: a surface over the dish, raised into spikes on a honeycomb - by a redstone signal (the
 * stronger, the taller, all alike) or by music nearby (each ring of spikes to a band: bass in the middle, treble at the
 * edge) - easing up and down; with nothing pulling, a pool heaving slowly. Drawn glossy black (shaders/core/ferrofluid:
 * the room's light reflected in it, bright glints, strong at grazing angles).
 */
public class FerrofluidRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "ferrofluid"), DefaultVertexFormat.POSITION_COLOR_NORMAL,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType FLUID = create("skyblocks_ferrofluid", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.TRIANGLES, 65536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** The surface's grid (cells a side), over the dish's inside (pixels), at rest at height LEVEL. */
    private static final int N = 44;
    private static final float X0 = 2.5F, X1 = 13.5F, LEVEL = 4.1F;
    /** The spikes' honeycomb spacing (pixels), and how tall the tallest get. */
    private static final float SPACING = 1.55F, TALL = 3.4F;

    public FerrofluidRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || shader == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        float t = level.getGameTime() + partialTick;
        // what pulls the spikes up: the redstone signal; music nearby (its loudness falls off with distance, as heard)
        float redstone = level.getBestNeighborSignal(pos) / 15.0F;
        MusicReactor.tick();
        float music = Mth.clamp(MusicReactor.music() * 1.6F, 0.0F, 1.0F);
        float[] bands = new float[4];
        for (int b = 0; b < 4; b++) {
            bands[b] = Mth.clamp((MusicReactor.band(b * 4) + MusicReactor.band(b * 4 + 1) + MusicReactor.band(b * 4 + 2) + MusicReactor.band(b * 4 + 3)) * 0.4F,
                    0.0F, 1.0F);
        }
        // (eased: the fluid takes a moment to rise, and to settle)
        float want = Math.max(redstone, music);
        float dt = be.clientTick == 0 ? 1.0F : Mth.clamp((t - be.clientTick), 0.0F, 4.0F);
        be.clientTick = (long) t;
        be.clientA += (want - be.clientA) * Mth.clamp(dt * (want > be.clientA ? 0.25F : 0.08F), 0.0F, 1.0F);
        float pull = be.clientA;

        float[] h = new float[(N + 1) * (N + 1)];
        float step = (X1 - X0) / N, rowStep = SPACING * 0.8660254F;
        for (int iz = 0; iz <= N; iz++) {
            for (int ix = 0; ix <= N; ix++) {
                float x = X0 + ix * step, z = X0 + iz * step;
                float cx = x - 8.0F, cz = z - 8.0F;
                float edge = Math.min(Math.min(x - X0, X1 - x), Math.min(z - X0, X1 - z));
                // the pool: a meniscus up the dish's walls, a slow heave
                float y = LEVEL + 0.45F * (float) Math.exp(-edge * 1.6F) + 0.12F * Mth.sin(t * 0.05F + cx * 0.7F) * Mth.cos(t * 0.04F + cz * 0.6F);
                if (pull > 0.01F && edge > 0.6F) {
                    // the nearest spike of the honeycomb
                    int row = Math.round(cz / rowStep);
                    float best = Float.MAX_VALUE, sx = 0, sz = 0;
                    for (int r = row - 1; r <= row + 1; r++) {
                        float off = (r & 1) == 0 ? 0.0F : SPACING * 0.5F;
                        int col = Math.round((cx - off) / SPACING);
                        for (int c = col - 1; c <= col + 1; c++) {
                            float px = c * SPACING + off, pz = r * rowStep;
                            float d = (cx - px) * (cx - px) + (cz - pz) * (cz - pz);
                            if (d < best) {
                                best = d;
                                sx = px;
                                sz = pz;
                            }
                        }
                    }
                    float ring = (float) Math.sqrt(sx * sx + sz * sz) / 5.5F; // (0 middle .. 1 edge)
                    float amp = music > redstone ? bands[Mth.clamp((int) (ring * 4), 0, 3)] * 0.7F + music * 0.3F : redstone;
                    amp *= pull / Math.max(want, 0.01F) * 1.0F;
                    amp *= Mth.clamp(1.25F - ring * 0.55F, 0.0F, 1.0F) * Mth.clamp((edge - 0.6F) * 0.9F, 0.0F, 1.0F);
                    amp *= 0.92F + 0.08F * Mth.sin(t * 0.3F + sx * 3.1F + sz * 1.7F);
                    float r01 = (float) Math.sqrt(best) / (SPACING * 0.55F);
                    if (r01 < 1.0F) {
                        y += TALL * amp * (float) Math.pow(1.0F - r01, 1.7F);
                    }
                    y -= 0.25F * amp; // (the pool drawn down into its spikes)
                }
                h[iz * (N + 1) + ix] = y;
            }
        }
        // how lit it is where it stands (the shader's light comes in as the vertex colour)
        float lit = Math.max(LightTexture.block(light), LightTexture.sky(light) * Math.max(0.2F, 1.0F - level.getSkyDarken() / 11.0F)) / 15.0F;
        int bright = (int) (Mth.clamp(lit, 0.08F, 1.0F) * 255);
        VertexConsumer vc = buffers.getBuffer(Types.FLUID);
        Matrix4f m = poseStack.last().pose();
        for (int iz = 0; iz < N; iz++) {
            for (int ix = 0; ix < N; ix++) {
                tri(vc, m, h, ix, iz, ix + 1, iz, ix, iz + 1, step, bright);
                tri(vc, m, h, ix + 1, iz, ix + 1, iz + 1, ix, iz + 1, step, bright);
            }
        }
    }

    private static void tri(VertexConsumer vc, Matrix4f m, float[] h, int x0, int z0, int x1, int z1, int x2, int z2, float step, int bright) {
        corner(vc, m, h, x0, z0, step, bright);
        corner(vc, m, h, x1, z1, step, bright);
        corner(vc, m, h, x2, z2, step, bright);
    }

    private static void corner(VertexConsumer vc, Matrix4f m, float[] h, int ix, int iz, float step, int bright) {
        float y = height(h, ix, iz);
        // (its normal from the slope round it)
        float dx = (height(h, ix + 1, iz) - height(h, ix - 1, iz)) / (2 * step), dz = (height(h, ix, iz + 1) - height(h, ix, iz - 1)) / (2 * step);
        float len = (float) Math.sqrt(dx * dx + 1 + dz * dz);
        vc.addVertex(m, (X0 + ix * step) / 16.0F, y / 16.0F, (X0 + iz * step) / 16.0F).setColor(bright, bright, bright, 255)
                .setNormal(-dx / len, 1 / len, -dz / len);
    }

    private static float height(float[] h, int ix, int iz) {
        ix = Mth.clamp(ix, 0, N);
        iz = Mth.clamp(iz, 0, N);
        return h[iz * (N + 1) + ix];
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
