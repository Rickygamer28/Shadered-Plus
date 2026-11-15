package com.example.skyblocks.client;

import com.example.skyblocks.RoomWindowBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A room window's glass (shaders/core/room_window): the room behind it worked out in the shader, from the way you look
 * through it. Each window's room its own (its colours and things from where it is), lit at night - not all at once,
 * nor all night: people go to bed.
 */
public class RoomWindowRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "room_window"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType GLASS = create("skyblocks_room_window", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1024, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public RoomWindowRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || shader == null || !state.hasProperty(RoomWindowBlock.FACING)) {
            return;
        }
        Direction out = state.getValue(RoomWindowBlock.FACING);
        BlockPos pos = be.getBlockPos();
        BlockPos front = pos.relative(out);
        if (level.getBlockState(front).isSolidRender(level, front)) {
            return; // (bricked up)
        }
        int hash = (int) (Mth.getSeed(pos) >>> 7);
        int seed = Math.floorMod(hash, 251);
        int room = state.getValue(RoomWindowBlock.ROOM);
        if (room == 0) {
            room = 1 + Math.floorMod(hash >>> 9, RoomWindowBlock.ROOMS);
        }
        // how light it is outside: the sun's height (night 0, day 1)
        float time = level.getTimeOfDay(partialTick);
        float sun = Mth.cos(time * Mth.TWO_PI);
        float day = Mth.clamp(sun * 2.2F + 0.45F, 0.0F, 1.0F) * (1.0F - level.getRainLevel(partialTick) * 0.35F);
        boolean lights = switch (state.getValue(RoomWindowBlock.LIGHTS)) {
            case 1 -> true;
            case 2 -> false;
            default -> {
                // (on once it's getting dark; off again at bedtime - each its own; a few never on)
                long dayTime = Math.floorMod(level.getDayTime(), 24000L);
                int bed = 18500 + Math.floorMod(hash >>> 13, 4500);
                boolean dark = day < 0.55F;
                yield dark && Math.floorMod(hash >>> 3, 7) != 0 && !(dayTime > bed && dayTime < 23200);
            }
        };
        int facing = out.get2DDataValue() & 3;
        int b = facing | (lights ? 4 : 0);
        int a = Math.round(day * 255);
        // the window's face: its corners, across from the left as you look at it
        float nx = out.getStepX(), nz = out.getStepZ();
        float ax = nz, az = -nx; // (across: up x out, as the shader has it)
        float cx = 0.5F + nx * 0.5F, cz = 0.5F + nz * 0.5F;
        float x0 = cx - ax * 0.5F, z0 = cz - az * 0.5F;
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(Types.GLASS);
        corner(vc, m, x0, z0, ax, az, nx, nz, 0, 0, seed, room, b, a);
        corner(vc, m, x0, z0, ax, az, nx, nz, 1, 0, seed, room, b, a);
        corner(vc, m, x0, z0, ax, az, nx, nz, 1, 1, seed, room, b, a);
        corner(vc, m, x0, z0, ax, az, nx, nz, 0, 1, seed, room, b, a);
    }

    private static void corner(VertexConsumer vc, Matrix4f m, float x0, float z0, float ax, float az, float nx, float nz, float u, float v, int seed,
                               int room, int b, int a) {
        vc.addVertex(m, x0 + ax * u + nx * 0.0005F, v, z0 + az * u + nz * 0.0005F).setColor(seed, room, b, a).setUv(u, v);
    }
}
