package com.example.skyblocks.client;

import com.example.skyblocks.BottomlessPitBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * A bottomless pit's top (shaders/core/bottomless_pit): a window down a shaft as wide as the pit blocks round it
 * (their extent, found by walking from block to block, kept a couple of seconds), whose walls are the ground beside it.
 */
public class BottomlessPitRenderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "bottomless_pit"), DefaultVertexFormat.NEW_ENTITY,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType TOP = create("skyblocks_bottomless_pit", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1024, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, true))
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** A pit's hole, as last worked out: its extent (x0, z0, x1, z1, in the world), its walls' block, and when. */
    private record Hole(int x0, int z0, int x1, int z1, BlockState wall, long at) {}

    private static final Map<BlockPos, Hole> HOLES = new HashMap<>();
    private static Level holesOf;
    /** How far a hole is followed (blocks from the pit), and how many blocks at most. */
    private static final int REACH = 40, MOST = 1600;

    public BottomlessPitRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || shader == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        BlockPos above = pos.above();
        if (level.getBlockState(above).isSolidRender(level, above)) {
            return; // (covered over)
        }
        Hole hole = hole(level, pos);
        TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer().getBlockModel(hole.wall).getParticleIcon(ModelData.EMPTY);
        int packed = LevelRenderer.getLightColor(level, above);
        float lit = Math.max(LightTexture.block(packed), LightTexture.sky(packed) * Math.max(0.15F, 1.0F - level.getSkyDarken() / 11.0F)) / 15.0F;
        int bright = Math.round(Math.max(0.05F, lit) * 255);
        int r = clamp(hole.x0 - pos.getX()), g = clamp(hole.z0 - pos.getZ()), b = clamp(hole.x1 - pos.getX()), a = clamp(hole.z1 - pos.getZ());
        VertexConsumer vc = buffers.getBuffer(Types.TOP);
        Matrix4f m = pose.last().pose();
        int sx = sprite.getX(), sy = sprite.getY(), size = sprite.contents().width();
        vertex(vc, m, 0, 0, r, g, b, a, sx, sy, size, bright);
        vertex(vc, m, 0, 1, r, g, b, a, sx, sy, size, bright);
        vertex(vc, m, 1, 1, r, g, b, a, sx, sy, size, bright);
        vertex(vc, m, 1, 0, r, g, b, a, sx, sy, size, bright);
    }

    private static int clamp(int offset) {
        return Math.max(0, Math.min(255, offset + 128));
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float z, int r, int g, int b, int a, int sx, int sy, int size, int bright) {
        vc.addVertex(m, x, 1.0F, z).setColor(r, g, b, a).setUv(x, z).setUv1(sx, sy).setUv2(size, bright).setNormal(0.0F, 1.0F, 0.0F);
    }

    /** The hole a pit block is part of: every pit block joined to it on its level, as a rectangle round them all. */
    private static Hole hole(Level level, BlockPos pos) {
        long now = level.getGameTime();
        if (holesOf != level) {
            HOLES.clear();
            holesOf = level;
        }
        Hole known = HOLES.get(pos);
        if (known != null && now - known.at < 40 && now >= known.at) {
            return known;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(pos);
        seen.add(pos);
        int x0 = pos.getX(), z0 = pos.getZ(), x1 = pos.getX() + 1, z1 = pos.getZ() + 1;
        BlockState wall = null;
        while (!todo.isEmpty() && seen.size() < MOST) {
            BlockPos p = todo.poll();
            x0 = Math.min(x0, p.getX());
            z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX() + 1);
            z1 = Math.max(z1, p.getZ() + 1);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || Math.abs(n.getX() - pos.getX()) > REACH || Math.abs(n.getZ() - pos.getZ()) > REACH) {
                    continue;
                }
                BlockState st = level.getBlockState(n);
                if (st.getBlock() instanceof BottomlessPitBlock) {
                    seen.add(n);
                    todo.add(n);
                } else if (wall == null && !st.isAir()) {
                    wall = st; // (the ground beside the hole: its walls)
                }
            }
        }
        if (wall == null) {
            BlockState below = level.getBlockState(pos.below());
            wall = below.isAir() || below.getBlock() instanceof BottomlessPitBlock ? Blocks.STONE.defaultBlockState() : below;
        }
        Hole hole = new Hole(x0, z0, x1, z1, wall, now);
        for (BlockPos p : seen) {
            HOLES.put(p, hole); // (the same hole for all of them)
        }
        if (HOLES.size() > 20000) {
            HOLES.clear();
        }
        return hole;
    }
}
