package com.example.skyblocks.client;

import com.example.skyblocks.ElevatorBaseBlockEntity;
import com.example.skyblocks.ElevatorCabinEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * An elevator (ElevatorBaseBlockEntity), drawn by its base: its cabin, wherever it is up the shaft, in its look - traced
 * through a box round it by shaders/core/elevator_cabin (a glass capsule, a hard-light platform, a bubble pod, a warp
 * cabin, a crystal cabin, a cloud) - with its floor buttons inside (ElevatorButtons) and its floor's number over it (an
 * arrow while it moves); and the shaft itself, by
 * shaders/core/elevator_shaft: for the bubble pod, the warp cabin and the cloud, its walls (the solid blocks round it)
 * turned to glowing water, deep space (stars streaking past as it moves), or open sky; the glass capsule's rings of
 * energy up the shaft; the crystal cabin's rail. Each part drawn right away, in order (as the time crystal's), after the
 * coloured lights (LatePass). The base draws it all: the cabin is seen anywhere up the shaft (shouldRenderOffScreen),
 * and with a shader pack goes over the pack's image as the base does (iris/IrisDefer).
 */
public class ElevatorRenderer implements BlockEntityRenderer<ElevatorBaseBlockEntity> {
    static ShaderInstance cabinShader, shaftShader, buttonShader;
    /** The box round the cabin: from its floor's top, its middle; and its half-size. */
    private static final float MID = 0.8F, HX = 1.5F, HY = 2.0F;
    private static final int[] LIGHT = {0x7CCBFF, 0x7CFFCF, 0xE7A0FF, 0xB09CFF, 0xC88CFF, 0xFFF0C8};
    /** Which looks turn the shaft's walls: the bubble pod (water), the warp cabin (space), the cloud (sky). */
    private static boolean walls(int skin) {
        return skin == 2 || skin == 3 || skin == 5;
    }

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "elevator_cabin"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> cabinShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "elevator_shaft"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shaftShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "elevator_button"), DefaultVertexFormat.POSITION_COLOR,
                    s -> buttonShader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CABIN = create("skyblocks_elevator_cabin", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> cabinShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        static final RenderType WALL = create("skyblocks_elevator_wall", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shaftShader))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        static final RenderType GLOW = create("skyblocks_elevator_glow", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shaftShader))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        /** The floor buttons inside the cabin (ElevatorButtons): flat colour. */
        static final RenderType BUTTONS = create("skyblocks_elevator_buttons", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> buttonShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public ElevatorRenderer(BlockEntityRendererProvider.Context context) {}

    /** Its cabin (found again now and then). */
    private static ElevatorCabinEntity cabin(ElevatorBaseBlockEntity be) {
        Level level = be.getLevel();
        ElevatorCabinEntity c = be.clientCabin;
        if ((c == null || c.isRemoved()) && level != null && level.getGameTime() % 10 == 0) {
            List<ElevatorCabinEntity> l = level.getEntitiesOfClass(ElevatorCabinEntity.class, be.shaft(), e -> e.base().equals(be.getBlockPos()));
            be.clientCabin = c = l.isEmpty() ? null : l.get(0);
        }
        return c != null && !c.isRemoved() ? c : null;
    }

    /** The shaft's walls: each solid block round it facing in, up its height (found again every couple of seconds). */
    private static int[] walls(ElevatorBaseBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return new int[0];
        }
        if (level.getGameTime() - be.clientWallsAt < 40 && level.getGameTime() >= be.clientWallsAt) {
            return be.clientWalls;
        }
        be.clientWallsAt = level.getGameTime();
        BlockPos b = be.getBlockPos();
        int top = be.top() - b.getY();
        it.unimi.dsi.fastutil.ints.IntArrayList out = new it.unimi.dsi.fastutil.ints.IntArrayList();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // side 0 north (z -2), 1 south (z +2), 2 west (x -2), 3 east (x +2); t: along it, -1 .. 1
        for (int y = 1; y <= top; y++) {
            for (int side = 0; side < 4; side++) {
                for (int t = -1; t <= 1; t++) {
                    int dx = side < 2 ? t : (side == 2 ? -2 : 2), dz = side < 2 ? (side == 0 ? -2 : 2) : t;
                    p.set(b.getX() + dx, b.getY() + y, b.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (s.isSolidRender(level, p)) {
                        out.add(side);
                        out.add(t);
                        out.add(y);
                    }
                }
            }
        }
        be.clientWalls = out.toIntArray();
        return be.clientWalls;
    }

    @Override
    public void render(ElevatorBaseBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (cabinShader == null || shaftShader == null || buttonShader == null) {
            return;
        }
        ElevatorCabinEntity cabin = cabin(be);
        BlockPos b = be.getBlockPos();
        float shown = cabin != null ? cabin.shownLanding(partialTick) : be.floors()[0];
        float landing = shown - b.getY();
        float vel = cabin != null ? cabin.velocity(partialTick - 1.0F) : 0.0F;
        boolean moving = cabin != null && cabin.moving();
        int skin = be.skin();
        int top = be.top() - b.getY();
        Matrix4f m = new Matrix4f(pose.last().pose());
        ShaderLightRenderer.add(pose, 0.5F, landing + 1.2F, 0.5F, 6.0F, LIGHT[skin]);
        int[] walls = walls(skin) ? walls(be) : new int[0];
        int floor = be.floorAt(shown) + 1;
        int target = cabin != null ? be.floorAt(cabin.target()) + 1 : floor;
        Runnable draw = () -> {
            if (walls.length > 0) {
                drawNow(Types.WALL, vc -> walls(vc, m, walls, skin, landing, vel));
            }
            if (skin == 0) {
                drawNow(Types.GLOW, vc -> rings(vc, m, top, landing, vel));
            } else if (skin == 4) {
                int side = ElevatorButtons.side(be);
                drawNow(Types.GLOW, vc -> rail(vc, m, top, vel, side));
            }
            // (the buttons before the cabin: its glass laid over them, seen from outside)
            ElevatorButtons.draw(m, be, landing, landing + b.getY(), floor - 1, target - 1, moving, com.example.skyblocks.client.ElevatorScreen.ACCENT[skin] & 0xFFFFFF,
                    partialTick);
            drawNow(Types.CABIN, vc -> cabinBox(vc, m, landing, skin, vel));
            label(m, landing, moving ? (vel > 0 ? "▲ " : "▼ ") + target : String.valueOf(floor), skin);
        };
        if (!LatePass.later(src -> draw.run())) {
            draw.run();
        }
    }

    static void drawNow(RenderType type, java.util.function.Consumer<VertexConsumer> emit) {
        com.mojang.blaze3d.vertex.BufferBuilder builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(type.mode(), type.format());
        emit.accept(builder);
        com.mojang.blaze3d.vertex.MeshData mesh = builder.build();
        if (mesh != null) {
            type.draw(mesh);
        }
    }

    /** How fast it's going, for the shaders (128: still; 40 a block a tick). */
    private static int velByte(float vel) {
        return Mth.clamp(Math.round(128 + vel * 40.0F), 0, 255);
    }

    /** The box round the cabin: its faces turned away from the eye (from inside it: all of them). */
    private static void cabinBox(VertexConsumer vc, Matrix4f m, float landing, int skin, float vel) {
        float cy = landing + MID;
        Vector3f centre = m.transformPosition(0.5F, cy, 0.5F, new Vector3f());
        float ex = -centre.x, ey = -centre.y, ez = -centre.z;
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        float[] towards = {-ex - HX, ex - HX, -ey - HY, ey - HY, -ez - HX, ez - HX};
        for (int f = 0; f < 6; f++) {
            if (towards[f] > 0) {
                continue;
            }
            for (int c : faces[f]) {
                float x = ((c & 1) * 2 - 1) * HX, y = (((c >> 1) & 1) * 2 - 1) * HY, z = (((c >> 2) & 1) * 2 - 1) * HX;
                vc.addVertex(m, 0.5F + x, cy + y, 0.5F + z).setColor(skin, 0, 0, 255).setUv(c, vel);
            }
        }
    }

    /** The walls: a sheet just in front of each solid block's face round the shaft. */
    private static void walls(VertexConsumer vc, Matrix4f m, int[] walls, int skin, float landing, float vel) {
        int vb = velByte(vel);
        float in = 1.5F - 0.004F;
        // (each look's pattern slides by as the cabin goes: how far, for so far up)
        float slide = skin == 3 ? landing * 1.6F : skin == 5 ? landing * 0.5F : landing * 0.25F;
        for (int i = 0; i + 2 < walls.length; i += 3) {
            int side = walls[i], t = walls[i + 1], y = walls[i + 2];
            float a0 = t - 0.5F, a1 = t + 0.5F, y0 = y, y1 = y + 1;
            float u0 = a0 + side * 7.0F, u1 = a1 + side * 7.0F; // (each wall its own part of the pattern)
            float v0 = y0 - slide, v1 = y1 - slide;
            switch (side) {
                case 0 -> quad(vc, m, 0.5F + a0, y0, 0.5F - in, 0.5F + a1, y0, 0.5F - in, 0.5F + a1, y1, 0.5F - in, 0.5F + a0, y1, 0.5F - in, u0, u1, v0, v1, skin, 0, vb);
                case 1 -> quad(vc, m, 0.5F + a0, y0, 0.5F + in, 0.5F + a1, y0, 0.5F + in, 0.5F + a1, y1, 0.5F + in, 0.5F + a0, y1, 0.5F + in, u0, u1, v0, v1, skin, 0, vb);
                case 2 -> quad(vc, m, 0.5F - in, y0, 0.5F + a0, 0.5F - in, y0, 0.5F + a1, 0.5F - in, y1, 0.5F + a1, 0.5F - in, y1, 0.5F + a0, u0, u1, v0, v1, skin, 0, vb);
                default -> quad(vc, m, 0.5F + in, y0, 0.5F + a0, 0.5F + in, y0, 0.5F + a1, 0.5F + in, y1, 0.5F + a1, 0.5F + in, y1, 0.5F + a0, u0, u1, v0, v1, skin, 0, vb);
            }
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float u0, float u1, float v0, float v1, int skin, int mode, int vb) {
        vc.addVertex(m, x0, y0, z0).setColor(skin, mode, vb, 255).setUv(u0, v0);
        vc.addVertex(m, x1, y1, z1).setColor(skin, mode, vb, 255).setUv(u1, v0);
        vc.addVertex(m, x2, y2, z2).setColor(skin, mode, vb, 255).setUv(u1, v1);
        vc.addVertex(m, x3, y3, z3).setColor(skin, mode, vb, 255).setUv(u0, v1);
    }

    /** The glass capsule's rings of energy, every two blocks up the shaft: brighter near the cabin, and as it speeds. */
    private static void rings(VertexConsumer vc, Matrix4f m, int top, float landing, float vel) {
        int vb = velByte(vel);
        for (int y = 2; y <= top; y += 2) {
            float d = Math.abs(y - (landing + 1.2F));
            int near = Mth.clamp(Math.round(255 - d * 18.0F), 40, 255);
            float s = 1.5F, yy = y + 0.01F;
            vc.addVertex(m, 0.5F - s, yy, 0.5F - s).setColor(0, 1, vb, near).setUv(-1, -1);
            vc.addVertex(m, 0.5F - s, yy, 0.5F + s).setColor(0, 1, vb, near).setUv(-1, 1);
            vc.addVertex(m, 0.5F + s, yy, 0.5F + s).setColor(0, 1, vb, near).setUv(1, 1);
            vc.addVertex(m, 0.5F + s, yy, 0.5F - s).setColor(0, 1, vb, near).setUv(1, -1);
        }
    }

    /** The crystal cabin's rail: a strip of crystal up the middle of one side of the shaft (its buttons' side). */
    private static void rail(VertexConsumer vc, Matrix4f m, int top, float vel, int side) {
        int vb = velByte(vel);
        net.minecraft.world.phys.Vec3 n = ElevatorButtons.normal(side), r = ElevatorButtons.right(side);
        float out = 1.5F - 0.02F, w = 0.2F;
        float cx = 0.5F - (float) n.x * out, cz = 0.5F - (float) n.z * out;
        float x0 = cx - (float) r.x * w, z0 = cz - (float) r.z * w, x1 = cx + (float) r.x * w, z1 = cz + (float) r.z * w;
        vc.addVertex(m, x0, 0.0F, z0).setColor(4, 2, vb, 255).setUv(-1, 0);
        vc.addVertex(m, x1, 0.0F, z1).setColor(4, 2, vb, 255).setUv(1, 0);
        vc.addVertex(m, x1, top + 1, z1).setColor(4, 2, vb, 255).setUv(1, top + 1);
        vc.addVertex(m, x0, top + 1, z0).setColor(4, 2, vb, 255).setUv(-1, top + 1);
    }

    /** The floor it's at (or going to, with an arrow) over the cabin, turned to face you. */
    private static void label(Matrix4f m, float landing, String text, int skin) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        PoseStack ps = new PoseStack();
        ps.last().pose().set(m);
        ps.translate(0.5F, landing + 3.05F, 0.5F);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(0.035F, -0.035F, 0.035F);
        MultiBufferSource.BufferSource src = DrawNow.now(null);
        int colour = com.example.skyblocks.client.ElevatorScreen.ACCENT[skin];
        font.drawInBatch(text, -font.width(text) / 2.0F, 0, colour, false, ps.last().pose(), src, Font.DisplayMode.NORMAL, 0x40000000,
                LightTexture.FULL_BRIGHT);
        src.endBatch();
    }

    @Override
    public boolean shouldRenderOffScreen(ElevatorBaseBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(ElevatorBaseBlockEntity be) {
        return be.shaft().inflate(1.0);
    }
}
