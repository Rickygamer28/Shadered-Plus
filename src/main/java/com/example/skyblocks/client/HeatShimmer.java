package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Heat shimmer: over fire, lit campfires, lit furnaces and lava's surface, the air wavers - the frame so far copied,
 * then sheets of hot air (turned to face you) drawn over it seeing it a little bent (shaders/core/heat_shimmer).
 * Looked for round the player twice a second. Not with a shader pack (packs bring their own effects).
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
public final class HeatShimmer {
    private static final int RANGE = 14, UP = 6, MAX = 160;
    /** The sheets: x, bottom, z (the middle of their foot), height, half their width. */
    private static final List<float[]> SHEETS = new ArrayList<>();
    private static TextureTarget copy;
    private static int failures;

    private HeatShimmer() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.getGameTime() % 10 != 0) {
            return;
        }
        SHEETS.clear();
        BlockPos at = mc.player.blockPosition();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), above = new BlockPos.MutableBlockPos();
        List<float[]> found = new ArrayList<>();
        for (int dy = -UP; dy <= UP; dy++) {
            for (int dx = -RANGE; dx <= RANGE; dx++) {
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    float[] sheet = null;
                    if (s.getBlock() instanceof BaseFireBlock) {
                        sheet = new float[] {p.getX() + 0.5F, p.getY() + 0.3F, p.getZ() + 0.5F, 1.6F, 0.55F};
                    } else if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) {
                        sheet = new float[] {p.getX() + 0.5F, p.getY() + 0.45F, p.getZ() + 0.5F, 1.8F, 0.5F};
                    } else if (s.getBlock() instanceof AbstractFurnaceBlock && s.getValue(AbstractFurnaceBlock.LIT)) {
                        sheet = new float[] {p.getX() + 0.5F, p.getY() + 1.0F, p.getZ() + 0.5F, 1.0F, 0.4F};
                    } else if (s.getFluidState().is(FluidTags.LAVA) && ((p.getX() + p.getZ()) & 1) == 0) {
                        above.set(p.getX(), p.getY() + 1, p.getZ());
                        if (level.getBlockState(above).isAir()) {
                            sheet = new float[] {p.getX() + 0.5F, p.getY() + 0.85F, p.getZ() + 0.5F, 1.4F, 0.85F};
                        }
                    }
                    if (sheet != null) {
                        found.add(sheet);
                    }
                }
            }
        }
        Vec3 e = mc.player.position();
        found.sort((a, b) -> Double.compare(dist(a, e), dist(b, e)));
        SHEETS.addAll(found.subList(0, Math.min(found.size(), MAX)));
    }

    private static double dist(float[] s, Vec3 e) {
        double x = s[0] - e.x, y = s[1] - e.y, z = s[2] - e.z;
        return x * x + y * y + z * z;
    }

    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHEETS.isEmpty() || FxShaders.shimmer == null || failures > 4
                || com.example.skyblocks.client.iris.IrisCompat.shaderPackInUse()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (!copyColour(main)) {
                failures++;
                return;
            }
            main.bindWrite(false);
            Vec3 cam = event.getCamera().getPosition();
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (float[] s : SHEETS) {
                float cx = (float) (s[0] - cam.x), cz = (float) (s[2] - cam.z), y0 = (float) (s[1] - cam.y), y1 = y0 + s[3];
                float len = (float) Math.sqrt(cx * cx + cz * cz);
                if (len < 0.3F) {
                    continue; // (standing in it)
                }
                float rx = -cz / len * s[4], rz = cx / len * s[4];
                b.addVertex(cx - rx, y0, cz - rz).setUv(-1, 0);
                b.addVertex(cx + rx, y0, cz + rz).setUv(1, 0);
                b.addVertex(cx + rx, y1, cz + rz).setUv(1, 1);
                b.addVertex(cx - rx, y1, cz - rz).setUv(-1, 1);
            }
            MeshData mesh = b.build();
            if (mesh == null) {
                return;
            }
            RenderSystem.setShaderTexture(0, copy.getColorTextureId()); // (the shader's Sampler0 is set from this as it's drawn)
            RenderSystem.setShader(() -> FxShaders.shimmer);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            BufferUploader.drawWithShader(mesh);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
        } catch (RuntimeException ex) {
            if (failures++ == 0) {
                SkyBlocksMod.LOGGER.warn("Shadered+ heat shimmer: couldn't draw", ex);
            }
        }
    }

    /** The frame so far, copied (its colour) to draw the shimmer from. */
    private static boolean copyColour(RenderTarget main) {
        int w = Math.max(main.width, 1), h = Math.max(main.height, 1);
        if (copy == null) {
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // (anything earlier's errors let go)
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        boolean ok = GL11.glGetError() == GL11.GL_NO_ERROR;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        return ok;
    }
}
