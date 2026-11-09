package com.example.skyblocks.client;

import com.example.skyblocks.LaserProjectorBlock;
import com.example.skyblocks.LaserProjectorBlockEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The party laser's beams: each a ribbon turned to the camera from the lens to where it lands (stopped by blocks), with
 * a spot of light there; their pattern moving with the time and the music. Bright in a fog machine's fog, faint without.
 */
public class LaserProjectorRenderer implements BlockEntityRenderer<LaserProjectorBlockEntity> {
    static ShaderInstance shader;
    private static final double REACH = 24.0;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerLaserShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "laser_beam"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType BEAMS = create("skyblocks_laser_beams", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public LaserProjectorRenderer(BlockEntityRendererProvider.Context context) {}

    private static float[] hue(float h) {
        h = h - (float) Math.floor(h);
        float r = Mth.clamp(Math.abs(h * 6.0F - 3.0F) - 1.0F, 0.0F, 1.0F);
        float g = Mth.clamp(2.0F - Math.abs(h * 6.0F - 2.0F), 0.0F, 1.0F);
        float b = Mth.clamp(2.0F - Math.abs(h * 6.0F - 4.0F), 0.0F, 1.0F);
        return new float[] {r, g, b};
    }

    @Override
    public void render(LaserProjectorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (shader == null || level == null || !be.shining()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Direction facing = be.getBlockState().getValue(LaserProjectorBlock.FACING);
        Vector3f f = facing.step();
        Vector3f u = Math.abs(f.y()) > 0.5F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f v = new Vector3f(f).cross(u).normalize();
        u = new Vector3f(v).cross(f).normalize();
        // (its lens: 6 pixels out from the face it's fixed to)
        Vec3 lens = Vec3.atCenterOf(pos).add(f.x() * -0.125, f.y() * -0.125, f.z() * -0.125);

        MusicReactor.tick();
        float bass = MusicReactor.bass();
        float t = ((level.getGameTime() % 240000L) + partialTick) / 20.0F;
        int pattern = be.pattern();
        boolean fog = com.example.skyblocks.FogMachineBlockEntity.fogNear(pos, 24.0);
        float strength = fog ? 0.9F : 0.22F;
        int n = switch (pattern) {
            case 0 -> 9;
            case 1 -> 12;
            case 2 -> 24;
            case 3 -> 2;
            default -> 8 + Math.round(bass * 10.0F);
        };
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Matrix4f m = poseStack.last().pose();
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        VertexConsumer vc = source.getBuffer(Types.BEAMS);
        for (int i = 0; i < n; i++) {
            float k = n == 1 ? 0.5F : i / (float) (n - 1);
            float spread, around;
            switch (pattern) {
                case 0 -> { // a fan sweeping side to side, tilting
                    spread = (k - 0.5F) * 1.3F;
                    around = Mth.sin(t * 0.7F) * 0.5F;
                }
                case 1 -> { // a turning cone
                    spread = 0.45F;
                    around = i / (float) n * Mth.TWO_PI + t * 1.1F;
                }
                case 2 -> { // a tunnel, its width breathing
                    spread = 0.25F + 0.2F * Mth.sin(t * 0.6F);
                    around = i / (float) n * Mth.TWO_PI - t * 0.5F;
                }
                case 3 -> { // two beams scanning in figure eights
                    spread = 0.6F * Mth.sin(t * 0.9F + i * Mth.PI);
                    around = Mth.sin(t * 1.8F + i) * 1.2F;
                }
                default -> { // the music: a cone opening with the bass, spinning faster
                    spread = 0.15F + 0.55F * bass;
                    around = i / (float) n * Mth.TWO_PI + t * (0.6F + 3.0F * bass);
                }
            }
            Vector3f d;
            if (pattern == 0) {
                // (in the fan's plane: across, tilted by "around")
                d = new Vector3f(f).add(new Vector3f(u).mul(Mth.sin(spread))).add(new Vector3f(v).mul(Mth.sin(around))).normalize();
            } else {
                float r = Mth.sin(spread);
                d = new Vector3f(f).mul(Mth.cos(spread)).add(new Vector3f(u).mul(r * Mth.cos(around))).add(new Vector3f(v).mul(r * Mth.sin(around))).normalize();
            }
            Vec3 end = lens.add(d.x() * REACH, d.y() * REACH, d.z() * REACH);
            BlockHitResult hit = level.clip(new ClipContext(lens, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
            boolean landed = hit.getType() == HitResult.Type.BLOCK;
            Vec3 to = landed ? hit.getLocation() : end;
            float[] c = be.colour() < 0 ? hue(k * 0.8F + t * 0.07F)
                    : new float[] {((be.colour() >> 16) & 255) / 255.0F, ((be.colour() >> 8) & 255) / 255.0F, (be.colour() & 255) / 255.0F};
            float pulse = pattern == 4 ? 0.5F + bass : 1.0F;
            beam(vc, m, pos, lens, to, cam, c, strength * pulse);
            if (landed) {
                spot(vc, m, pos, to.add(hit.getDirection().getStepX() * 0.01, hit.getDirection().getStepY() * 0.01, hit.getDirection().getStepZ() * 0.01), cam, c, Math.min(1.0F, 0.9F * pulse));
            }
        }
        source.endBatch(Types.BEAMS);
    }

    private static void beam(VertexConsumer vc, Matrix4f m, BlockPos pos, Vec3 a, Vec3 b, Vec3 cam, float[] c, float strength) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(cam.subtract(a)).normalize().scale(0.06);
        Vec3 sideB = dir.cross(cam.subtract(b)).normalize().scale(0.06);
        int r = (int) (c[0] * 255), g = (int) (c[1] * 255), bl = (int) (c[2] * 255), al = (int) (Mth.clamp(strength, 0.0F, 1.0F) * 255);
        put(vc, m, pos, a.subtract(side), 0.0F, -1.0F, r, g, bl, al);
        put(vc, m, pos, a.add(side), 0.0F, 1.0F, r, g, bl, al);
        put(vc, m, pos, b.add(sideB), 1.0F, 1.0F, r, g, bl, al);
        put(vc, m, pos, b.subtract(sideB), 1.0F, -1.0F, r, g, bl, al);
    }

    private static void spot(VertexConsumer vc, Matrix4f m, BlockPos pos, Vec3 p, Vec3 cam, float[] c, float strength) {
        Vec3 toCam = cam.subtract(p).normalize();
        Vec3 x = toCam.cross(Math.abs(toCam.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize().scale(0.18);
        Vec3 y = toCam.cross(x).normalize().scale(0.18);
        int r = (int) (c[0] * 255), g = (int) (c[1] * 255), bl = (int) (c[2] * 255), al = (int) (Mth.clamp(strength, 0.0F, 1.0F) * 255);
        put(vc, m, pos, p.subtract(x).subtract(y), 2.0F, -1.0F, r, g, bl, al);
        put(vc, m, pos, p.add(x).subtract(y), 4.0F, -1.0F, r, g, bl, al);
        put(vc, m, pos, p.add(x).add(y), 4.0F, 1.0F, r, g, bl, al);
        put(vc, m, pos, p.subtract(x).add(y), 2.0F, 1.0F, r, g, bl, al);
    }

    private static void put(VertexConsumer vc, Matrix4f m, BlockPos pos, Vec3 p, float u, float v, int r, int g, int b, int a) {
        vc.addVertex(m, (float) (p.x - pos.getX()), (float) (p.y - pos.getY()), (float) (p.z - pos.getZ())).setUv(u, v).setColor(r, g, b, a);
    }

    @Override
    public boolean shouldRenderOffScreen(LaserProjectorBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(LaserProjectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(REACH);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
