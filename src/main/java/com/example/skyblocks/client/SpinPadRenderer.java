package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SpinPadBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * A spin pad (SpinPadBlockEntity): its plate turning with the cat on it, and once it's going, an aura (shaders/core/spin_aura,
 * adding light): a hypnotic spiral of the pad's sky wheeling round on the floor, wider the faster it spins, and round the
 * cat, comet streaks of colour whipping round with it and rings of light rising off the plate.
 */
public class SpinPadRenderer implements BlockEntityRenderer<SpinPadBlockEntity> {
    private static final ResourceLocation PLATE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/block/spin_pad_plate.png");
    /** The ring round the cat's mesh: steps round it. */
    private static final int AROUND = 48;

    static net.minecraft.client.renderer.ShaderInstance shader;

    @net.neoforged.fml.common.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,
            value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @net.neoforged.bus.api.SubscribeEvent
        public static void register(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "spin_aura"),
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR, sh -> shader = sh);
        }
    }

    private static final java.util.Map<String, RenderType> VORTEX = new java.util.HashMap<>();

    /** The vortex drawn with a sky's picture (Shadered's, by name), brought up to date. */
    @org.jetbrains.annotations.Nullable
    private static RenderType vortexType(String sky) {
        int index;
        try {
            index = com.noodlegamer76.shadered.client.util.SkyblockType.valueOf(sky).ordinal();
        } catch (RuntimeException e) {
            return null;
        }
        try {
            SkyUsage.ensureFresh(com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(com.noodlegamer76.shadered.client.util.SkyblockType.values()[index]));
        } catch (RuntimeException ignored) {
            // (drawn as it is)
        }
        return VORTEX.computeIfAbsent(sky, k -> VortexTypes.of(index, ShaderPaintRender.picture(index)));
    }

    abstract static class VortexTypes extends RenderType {
        static RenderType of(int sky, ResourceLocation picture) {
            return create("skyblocks_spin_aura_" + sky, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 8192, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTextureState(new TextureStateShard(picture, false, false))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY) // (adding light)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }

        private VortexTypes() {
            super("", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private static void ring(VertexConsumer vc, Matrix4f m, float a, float r, float y, float u, float v, int speed, int turn, int alpha) {
        vc.addVertex(m, 0.5F + Mth.cos(a) * r, y, 0.5F + Mth.sin(a) * r).setUv(u, v).setColor(speed, turn, 255, alpha);
    }

    public SpinPadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SpinPadBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.master() != null) {
            return; // (one of a big pad's other pads: its master draws the big plate over it)
        }
        int n = Math.max(1, be.side());
        float turn = be.turn(partialTick);
        float s = be.clientSpeed;
        float c = n / 2.0F; // (its middle: a big pad's, over its whole square)
        // the plate, turning
        pose.pushPose();
        pose.translate(c, 4.02F / 16.0F, c);
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
        VertexConsumer plate = buffers.getBuffer(RenderType.entityCutout(PLATE));
        float r = n * 0.5F - 1.0F / 16.0F;
        plateVertex(plate, pose, -r, -r, 0, 0, light);
        plateVertex(plate, pose, -r, r, 0, 1, light);
        plateVertex(plate, pose, r, r, 1, 1, light);
        plateVertex(plate, pose, r, -r, 1, 0, light);
        pose.popPose();
        // the cats in its screen: one cat, as big as the cube root of how many, sat on the plate turning with it
        if (be.catCount() > 0) {
            drawCat(be, pose, c, be.catSize(), turn);
        }
        if (s < 0.03F) {
            return;
        }
        // the aura (shaders/core/spin_aura): the spiral on the floor and the streaks round the cat - wider, taller and
        // brighter the faster it spins (a big pad's: all of it as big as the pad)
        RenderType aura = vortexType(be.sky());
        if (aura == null || shader == null) {
            return;
        }
        pose.pushPose();
        pose.translate(c - 0.5F * n, 0, c - 0.5F * n);
        pose.scale(n, n, n);
        Matrix4f m = pose.last().pose();
        float turns = turn / 360.0F;
        int sp = Math.round(Mth.clamp(s, 0.0F, 1.0F) * 255), gTurn = Math.round((turns - (float) Math.floor(turns)) * 255) & 255;
        int alpha = Math.round(Mth.clamp(s * 2.5F, 0.0F, 1.0F) * 255);
        VertexConsumer vc = buffers.getBuffer(aura);
        float disc = 0.5F + 2.2F * s, y = 0.02F / n; // (on the ground round the pad)
        vc.addVertex(m, 0.5F - disc, y, 0.5F - disc).setUv(0, 0).setColor(sp, gTurn, 0, alpha);
        vc.addVertex(m, 0.5F - disc, y, 0.5F + disc).setUv(0, 1).setColor(sp, gTurn, 0, alpha);
        vc.addVertex(m, 0.5F + disc, y, 0.5F + disc).setUv(1, 1).setColor(sp, gTurn, 0, alpha);
        vc.addVertex(m, 0.5F + disc, y, 0.5F - disc).setUv(1, 0).setColor(sp, gTurn, 0, alpha);
        float radius = 0.55F + 0.2F * s, bottom = 0.3F, top = bottom + 0.9F + 0.6F * s;
        for (int i = 0; i < AROUND; i++) {
            float a0 = i * Mth.TWO_PI / AROUND, a1 = (i + 1) * Mth.TWO_PI / AROUND;
            float u0 = i / (float) AROUND, u1 = (i + 1) / (float) AROUND;
            ring(vc, m, a0, radius, bottom, u0, 0, sp, gTurn, alpha);
            ring(vc, m, a1, radius, bottom, u1, 0, sp, gTurn, alpha);
            ring(vc, m, a1, radius, top, u1, 1, sp, gTurn, alpha);
            ring(vc, m, a0, radius, top, u0, 1, sp, gTurn, alpha);
        }
        pose.popPose();
    }

    /** The cat in its screen, as a loaf on the plate (size k: k blocks across), turned with it (Shadered's model renderer). */
    private static void drawCat(SpinPadBlockEntity be, PoseStack given, float c, int k, float turn) {
        if (com.example.skyblocks.client.island.IslandRendering.inIsland) {
            return;
        }
        if (!(be.clientCatModel instanceof com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel)) {
            be.clientCatModel = OiiaCatModel.create(OiiaCatModel.SPIN_MODEL);
        }
        if (!(be.clientCatModel instanceof com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel model)) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.last().pose().set(given.last().pose());
        pose.translate(c, 4.0F / 16.0F, c);
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        float scale = OiiaCatModel.SCALE * k;
        pose.scale(scale, scale, scale);
        pose.translate(0, OiiaCatModel.FEET, 0);
        OiiaCatModel.animate(pose, 0);
        net.minecraft.world.phys.Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        model.modelMatrix = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z).mul(new Matrix4f(pose.last().pose()));
        if (com.example.skyblocks.client.island.IslandRendering.inWindow || com.example.skyblocks.client.island.IslandRendering.collectCats) {
            IslandCatBatch.add(model);
        } else {
            com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer.getInstance().addModel(model);
        }
    }

    private static void plateVertex(VertexConsumer vc, PoseStack pose, float x, float z, float u, float v, int light) {
        vc.addVertex(pose.last(), x, 0, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 1, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(SpinPadBlockEntity be) {
        return be.clientSpeed > 0.03F || be.side() > 1;
    }

    @Override
    public AABB getRenderBoundingBox(SpinPadBlockEntity be) {
        BlockPos p = be.getBlockPos();
        int n = Math.max(1, be.side());
        return new AABB(p.getX() - 2.5 * n, p.getY(), p.getZ() - 2.5 * n, p.getX() + 3.5 * n, p.getY() + 2 * n, p.getZ() + 3.5 * n);
    }
}
