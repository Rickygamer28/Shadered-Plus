package com.example.skyblocks.client;

import com.example.skyblocks.ShaderPaintStyles;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SkyLanternBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
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
import net.minecraft.world.level.block.LanternBlock;
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
 * The sky lantern's sky - its picture inside the glass, as the infusion altar's paint shows a sky (shaders/core/sky_paint)
 * - and its light on the room round it: every face it can see, washed in the sky's own colours (the average of its
 * picture, worked out on the graphics card as it's drawn: shaders/core/sky_glow), fading with distance.
 */
public class SkyLanternRenderer implements BlockEntityRenderer<SkyLanternBlockEntity> {
    static ShaderInstance glowShader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerSkyGlow(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "sky_glow"), DefaultVertexFormat.POSITION_COLOR,
                    s -> glowShader = s);
        }
    }

    abstract static class Types extends RenderType {
        /**
         * A sky's light on the blocks round the lantern: their colours tinted towards it (multiplied: a lit white wall
         * turns the sky's colour, which light added on top of it couldn't do).
         */
        static final TransparencyStateShard TINT = new TransparencyStateShard("skyblocks_tint", () -> {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.DST_COLOR,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        }, () -> {
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        });

        static RenderType glow(int sky, ResourceLocation picture) {
            return create("skyblocks_sky_glow_" + sky, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> glowShader))
                            .setTextureState(new TextureStateShard(picture, false, false))
                            .setTransparencyState(TINT)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                            .createCompositeState(false));
        }

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private static final Map<Integer, RenderType> GLOWS = new HashMap<>();
    /** How far its light reaches (blocks), and how strong it is right by it. */
    private static final int REACH = 7;
    private static final float STRENGTH = 0.8F;

    private static final class Lit {
        long at = -1000;
        boolean hanging;
        /** Each lit face: four corners (x y z, from the lantern's block) and how strong the light is at each. */
        final List<float[]> faces = new ArrayList<>();
    }

    private static final Map<SkyLanternBlockEntity, Lit> LIT = new WeakHashMap<>();

    public SkyLanternRenderer(BlockEntityRendererProvider.Context context) {}

    /** The lantern's style (ShaderPaintStyles): its sky and pass, by index; 0 if they aren't skies here. */
    static int style(SkyLanternBlockEntity be) {
        try {
            int sky = SkyblockType.valueOf(be.sky()).ordinal();
            int pass = SkyblockPass.valueOf(be.pass()).ordinal();
            return ShaderPaintStyles.of(sky, pass);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public void render(SkyLanternBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(LanternBlock.HANGING)) {
            return;
        }
        boolean hanging = state.getValue(LanternBlock.HANGING);
        int style = style(be);
        RenderType sky = ShaderPaintRender.type(style);
        if (sky == null) {
            return;
        }
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        // the sky inside the glass: each side of it a window of the sky's picture
        float y0 = (hanging ? 2.0F : 1.0F) / 16.0F, y1 = y0 + 8.0F / 16.0F, a = 5.02F / 16.0F, b = 10.98F / 16.0F;
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = source.getBuffer(sky);
        box(vc, m, a, y0, a, b, y1, b, style);
        source.endBatch(sky);
        // its light on the room
        if (glowShader == null) {
            return;
        }
        Lit lit = LIT.computeIfAbsent(be, k -> new Lit());
        long now = level.getGameTime();
        if (now - lit.at > 40 || now < lit.at - 40 || lit.hanging != hanging) {
            light(level, be.getBlockPos(), hanging, lit);
            lit.at = now + Math.floorMod(be.getBlockPos().hashCode(), 20); // (lanterns take turns)
            lit.hanging = hanging;
        }
        if (lit.faces.isEmpty()) {
            return;
        }
        int skyIndex = ShaderPaintStyles.sky(style);
        RenderType glow = GLOWS.computeIfAbsent(skyIndex, k -> Types.glow(k, ShaderPaintRender.picture(k)));
        VertexConsumer g = source.getBuffer(glow);
        PoseStack.Pose p = poseStack.last();
        float flicker = 0.94F + 0.06F * Mth.sin((now + partialTick) * 0.11F + be.getBlockPos().hashCode());
        for (float[] f : lit.faces) {
            for (int c = 0; c < 4; c++) {
                g.addVertex(p, f[c * 3], f[c * 3 + 1], f[c * 3 + 2]).setColor(255, 255, 255, Mth.clamp((int) (f[12 + c] * flicker * 255), 0, 255));
            }
        }
        source.endBatch(glow);
    }

    /** A box of sky (all six sides, each the whole picture), seen from inside or out. */
    private static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int style) {
        float[][][] faces = {
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}},
                {{x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}},
                {{x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}},
                {{x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}},
                {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (float[][] f : faces) {
            for (int i = 0; i < 4; i++) {
                ShaderPaintRender.vertex(vc, m, f[i][0], f[i][1], f[i][2], uv[i][0], uv[i][1], style, 16);
            }
        }
    }

    /**
     * Its light: every face of a block round it that faces it and that it can see, fading with distance (worked out at
     * each corner, so it fades smoothly across the faces).
     */
    private static void light(Level level, BlockPos pos, boolean hanging, Lit lit) {
        lit.faces.clear();
        Vec3 centre = Vec3.atLowerCornerOf(pos).add(0.5, hanging ? 0.38 : 0.31, 0.5);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(), next = new BlockPos.MutableBlockPos();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dy = -REACH; dy <= REACH; dy++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    if (dx * dx + dy * dy + dz * dz > (REACH + 1) * (REACH + 1) || dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState st = level.getBlockState(at);
                    if (st.isAir() || !st.isSolidRender(level, at)) {
                        continue;
                    }
                    for (Direction d : Direction.values()) {
                        next.setWithOffset(at, d);
                        if (level.getBlockState(next).isSolidRender(level, next)) {
                            continue;
                        }
                        Vec3 mid = Vec3.atCenterOf(at).add(d.getStepX() * 0.5, d.getStepY() * 0.5, d.getStepZ() * 0.5);
                        Vec3 to = centre.subtract(mid);
                        if (to.x * d.getStepX() + to.y * d.getStepY() + to.z * d.getStepZ() <= 0.05) {
                            continue; // (facing away)
                        }
                        BlockHitResult hit = level.clip(new ClipContext(centre, mid.add(d.getStepX() * 0.02, d.getStepY() * 0.02, d.getStepZ() * 0.02),
                                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
                        if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(at) && !hit.getBlockPos().equals(pos)) {
                            continue; // (something in the way)
                        }
                        lit.faces.add(face(at, d, pos, centre));
                    }
                }
            }
        }
    }

    private static float[] face(BlockPos at, Direction d, BlockPos lantern, Vec3 centre) {
        float o = 0.003F, x0 = at.getX(), y0 = at.getY(), z0 = at.getZ();
        float[][] c = switch (d) {
            case UP -> new float[][] {{x0, y0 + 1 + o, z0}, {x0 + 1, y0 + 1 + o, z0}, {x0 + 1, y0 + 1 + o, z0 + 1}, {x0, y0 + 1 + o, z0 + 1}};
            case DOWN -> new float[][] {{x0, y0 - o, z0}, {x0, y0 - o, z0 + 1}, {x0 + 1, y0 - o, z0 + 1}, {x0 + 1, y0 - o, z0}};
            case NORTH -> new float[][] {{x0, y0, z0 - o}, {x0, y0 + 1, z0 - o}, {x0 + 1, y0 + 1, z0 - o}, {x0 + 1, y0, z0 - o}};
            case SOUTH -> new float[][] {{x0, y0, z0 + 1 + o}, {x0 + 1, y0, z0 + 1 + o}, {x0 + 1, y0 + 1, z0 + 1 + o}, {x0, y0 + 1, z0 + 1 + o}};
            case WEST -> new float[][] {{x0 - o, y0, z0}, {x0 - o, y0, z0 + 1}, {x0 - o, y0 + 1, z0 + 1}, {x0 - o, y0 + 1, z0}};
            case EAST -> new float[][] {{x0 + 1 + o, y0, z0}, {x0 + 1 + o, y0 + 1, z0}, {x0 + 1 + o, y0 + 1, z0 + 1}, {x0 + 1 + o, y0, z0 + 1}};
        };
        float[] f = new float[16];
        for (int i = 0; i < 4; i++) {
            float cx = c[i][0], cy = c[i][1], cz = c[i][2];
            double dist = Math.sqrt((cx - centre.x) * (cx - centre.x) + (cy - centre.y) * (cy - centre.y) + (cz - centre.z) * (cz - centre.z));
            float fade = (float) Math.max(0.0, 1.0 - dist / (REACH + 0.5));
            f[i * 3] = cx - lantern.getX();
            f[i * 3 + 1] = cy - lantern.getY();
            f[i * 3 + 2] = cz - lantern.getZ();
            f[12 + i] = fade * (0.35F + 0.65F * fade) * STRENGTH;
        }
        return f;
    }

    @Override
    public AABB getRenderBoundingBox(SkyLanternBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(REACH + 1);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
