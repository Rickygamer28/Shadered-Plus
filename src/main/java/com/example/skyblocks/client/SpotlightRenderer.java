package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SpotlightBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

/**
 * The spotlight: its lamp turning smoothly to the nearest player in reach (a slow sweep with nobody near), a soft cone
 * of light to where it lands and a pool of light there - brighter in a fog machine's fog, pulsing a little with music.
 */
public class SpotlightRenderer implements BlockEntityRenderer<SpotlightBlockEntity> {
    static ShaderInstance shader;
    private static final double REACH = 24.0;
    private static final BlockState HOUSING = Blocks.BLACK_CONCRETE.defaultBlockState(), LENS = Blocks.SEA_LANTERN.defaultBlockState();

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerSpotlightShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "spotlight_beam"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CONE = create("skyblocks_spotlight", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1024,
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

    public SpotlightRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SpotlightBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!(be.getLevel() instanceof ClientLevel level)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Vec3 lamp = new Vec3(pos.getX() + 0.5, pos.getY() + 11.0 / 16, pos.getZ() + 0.5);
        // what it follows: the nearest player in reach - else a slow sweep
        Player target = null;
        double best = REACH * REACH;
        for (Player p : level.players()) {
            double d = p.distanceToSqr(lamp);
            if (d < best && !p.isSpectator()) {
                best = d;
                target = p;
            }
        }
        float t = (level.getGameTime() + partialTick) / 20.0F;
        float wantYaw, wantPitch;
        if (target != null) {
            Vec3 at = target.getPosition(partialTick).add(0, 0.9, 0).subtract(lamp);
            wantYaw = (float) Mth.atan2(at.x, at.z);
            wantPitch = (float) Mth.atan2(at.y, Math.sqrt(at.x * at.x + at.z * at.z));
        } else {
            wantYaw = t * 0.4F;
            wantPitch = -0.55F;
        }
        // (turning smoothly, at most so fast)
        long now = System.nanoTime();
        float dt = be.turnedAt == 0 ? 1.0F : Math.min(0.2F, (now - be.turnedAt) / 1.0e9F);
        be.turnedAt = now;
        float dy = Mth.wrapDegrees((wantYaw - be.yaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        be.yaw += Mth.clamp(dy, -3.0F * dt, 3.0F * dt);
        be.pitch += Mth.clamp(wantPitch - be.pitch, -2.0F * dt, 2.0F * dt);

        // the lamp's housing, turned that way (on the stand of its model)
        poseStack.pushPose();
        poseStack.translate(0.5F, 11.0F / 16, 0.5F);
        poseStack.mulPose(Axis.YP.rotation(be.yaw));
        poseStack.mulPose(Axis.XP.rotation(-be.pitch));
        poseStack.translate(-0.5F, -11.0F / 16, -0.5F);
        BlockSkin.box(HOUSING, level, pos, poseStack, buffers, light, overlay, 5.5F, 8.5F, 5.0F, 10.5F, 13.5F, 11.5F);
        BlockSkin.box(HOUSING, level, pos, poseStack, buffers, light, overlay, 5.0F, 8.0F, 11.5F, 11.0F, 14.0F, 12.5F);
        BlockSkin.box(be.on() ? LENS : HOUSING, level, pos, poseStack, buffers, be.on() ? LightTexture.FULL_BRIGHT : light, overlay,
                6.0F, 9.0F, 12.4F, 10.0F, 13.0F, 12.8F);
        poseStack.popPose();
        if (!be.on()) {
            return;
        }

        // the cone and the pool of light, after Shadered's sky blocks and models (as the lasers)
        Vec3 dir = new Vec3(Math.sin(be.yaw) * Math.cos(be.pitch), Math.sin(be.pitch), Math.cos(be.yaw) * Math.cos(be.pitch));
        Vec3 from = lamp.add(dir.scale(0.8));
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(REACH)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 to = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : from.add(dir.scale(REACH));
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int c = be.colour();
        boolean fog = com.example.skyblocks.FogMachineBlockEntity.fogNear(pos, 24.0);
        MusicReactor.tick();
        float strength = (fog ? 0.75F : 0.28F) * (0.85F + MusicReactor.bass() * 0.3F);
        float len = (float) to.distanceTo(from);
        float endR = 0.25F + len * 0.09F;
        List<float[]> verts = new ArrayList<>();
        ribbon(verts, pos, from, to, cam, 0.12F, endR, c, strength);
        if (hit.getType() == HitResult.Type.BLOCK) {
            spot(verts, pos, to.add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.02)), hit.getDirection(), endR, c, Math.min(1.0F, strength * 1.6F));
        }
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        java.util.function.Consumer<MultiBufferSource.BufferSource> draw = source -> {
            VertexConsumer out = source.getBuffer(Types.CONE);
            for (float[] p : verts) {
                out.addVertex(m, p[0], p[1], p[2]).setUv(p[3], p[4]).setColor((int) p[5], (int) p[6], (int) p[7], (int) p[8]);
            }
            source.endBatch(Types.CONE);
        };
        if (shader != null && !LatePass.later(draw)) {
            draw.accept(DrawNow.now(buffers));
        }
    }

    /** A strip from a to b facing the camera, widening from r0 to r1. */
    private static void ribbon(List<float[]> out, BlockPos pos, Vec3 a, Vec3 b, Vec3 cam, float r0, float r1, int c, float strength) {
        Vec3 d = b.subtract(a);
        Vec3 sa = d.cross(cam.subtract(a)).normalize(), sb = d.cross(cam.subtract(b)).normalize();
        put(out, pos, a.subtract(sa.scale(r0)), 0, -1, c, strength);
        put(out, pos, a.add(sa.scale(r0)), 0, 1, c, strength);
        put(out, pos, b.add(sb.scale(r1)), 1, 1, c, strength);
        put(out, pos, b.subtract(sb.scale(r1)), 1, -1, c, strength);
    }

    /** The pool of light, flat on the face it lands on. */
    private static void spot(List<float[]> out, BlockPos pos, Vec3 p, net.minecraft.core.Direction face, float r, int c, float strength) {
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 u = Math.abs(n.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 v = n.cross(u).normalize();
        u = v.cross(n).normalize();
        put(out, pos, p.subtract(u.scale(r)).subtract(v.scale(r)), 2, -1, c, strength);
        put(out, pos, p.add(u.scale(r)).subtract(v.scale(r)), 4, -1, c, strength);
        put(out, pos, p.add(u.scale(r)).add(v.scale(r)), 4, 1, c, strength);
        put(out, pos, p.subtract(u.scale(r)).add(v.scale(r)), 2, 1, c, strength);
    }

    private static void put(List<float[]> out, BlockPos pos, Vec3 p, float u, float v, int c, float strength) {
        out.add(new float[] {(float) (p.x - pos.getX()), (float) (p.y - pos.getY()), (float) (p.z - pos.getZ()), u, v,
                (c >> 16) & 255, (c >> 8) & 255, c & 255, Mth.clamp(strength, 0.0F, 1.0F) * 255});
    }

    @Override
    public boolean shouldRenderOffScreen(SpotlightBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(SpotlightBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(REACH);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
