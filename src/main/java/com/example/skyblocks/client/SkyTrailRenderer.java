package com.example.skyblocks.client;

import com.example.skyblocks.SkyTrailEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A line of sky drawn with the sky wand (SkyTrailEntity): a ribbon along its points, turned to face you wherever you
 * stand, rounded off at its ends - a window into its sky (SkyWindow) - with a soft glow along its edges. A fading line
 * thins away to nothing at the end of its minute.
 */
public class SkyTrailRenderer extends EntityRenderer<SkyTrailEntity> {
    /** Half the ribbon's width (blocks), and how far past it the glow reaches. */
    private static final float HALF = 0.075F, GLOW = 0.07F;
    private final SkyWindow.Quads quads = new SkyWindow.Quads();

    public SkyTrailRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public void render(SkyTrailEntity line, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float[] p = line.points();
        int n = p.length / 3;
        if (n < 2) {
            return;
        }
        RenderType type = SkyWindow.type(line.sky(), line.pass());
        if (type == null) {
            return;
        }
        float fade = 1.0F;
        if (!line.keep() && !line.drawing()) {
            float left = SkyTrailEntity.LIFE - (line.clientAge + partialTick);
            fade = Mth.clamp(left / SkyTrailEntity.FADE, 0.0F, 1.0F);
        }
        if (fade <= 0.0F) {
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 base = line.getPosition(partialTick);
        float ox = (float) (base.x - cam.x), oy = (float) (base.y - cam.y), oz = (float) (base.z - cam.z);
        // each point's sideways step: across the line, square to the way you see it
        float[] sx = new float[n], sy = new float[n], sz = new float[n], w = new float[n];
        Vector3f t = new Vector3f(), v = new Vector3f(), s = new Vector3f();
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            t.set(p[b * 3] - p[a * 3], p[b * 3 + 1] - p[a * 3 + 1], p[b * 3 + 2] - p[a * 3 + 2]);
            v.set(p[i * 3] + ox, p[i * 3 + 1] + oy, p[i * 3 + 2] + oz);
            t.cross(v, s);
            if (s.lengthSquared() < 1.0E-10F) {
                s.set(0, 1, 0);
            }
            s.normalize();
            sx[i] = s.x;
            sy[i] = s.y;
            sz[i] = s.z;
            // (rounded off at both ends; a line being drawn: its tip too)
            float end = Math.min(i, n - 1 - i) / 3.0F;
            w[i] = HALF * fade * (float) Math.sqrt(Mth.clamp(end + 0.15F, 0.0F, 1.0F));
        }
        Matrix4f m = pose.last().pose();
        quads.clear();
        for (int i = 0; i + 1 < n; i++) {
            int j = i + 1;
            quads.add(p[i * 3] - sx[i] * w[i], p[i * 3 + 1] - sy[i] * w[i], p[i * 3 + 2] - sz[i] * w[i],
                    p[i * 3] + sx[i] * w[i], p[i * 3 + 1] + sy[i] * w[i], p[i * 3 + 2] + sz[i] * w[i],
                    p[j * 3] + sx[j] * w[j], p[j * 3 + 1] + sy[j] * w[j], p[j * 3 + 2] + sz[j] * w[j],
                    p[j * 3] - sx[j] * w[j], p[j * 3 + 1] - sy[j] * w[j], p[j * 3 + 2] - sz[j] * w[j]);
        }
        SkyWindow.draw(buffers, type, m, quads.data, quads.count);
        // the glow along its edges: bright at the sky, fading outwards
        VertexConsumer g = buffers.getBuffer(GlowTypes.GLOW);
        float bright = 0.55F * fade;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i + 1 < n; i++) {
                int j = i + 1;
                float wi = w[i] * side, wj = w[j] * side, gi = (w[i] + GLOW * fade) * side, gj = (w[j] + GLOW * fade) * side;
                glow(g, m, p[i * 3] + sx[i] * wi, p[i * 3 + 1] + sy[i] * wi, p[i * 3 + 2] + sz[i] * wi, bright);
                glow(g, m, p[i * 3] + sx[i] * gi, p[i * 3 + 1] + sy[i] * gi, p[i * 3 + 2] + sz[i] * gi, 0.0F);
                glow(g, m, p[j * 3] + sx[j] * gj, p[j * 3 + 1] + sy[j] * gj, p[j * 3 + 2] + sz[j] * gj, 0.0F);
                glow(g, m, p[j * 3] + sx[j] * wj, p[j * 3 + 1] + sy[j] * wj, p[j * 3 + 2] + sz[j] * wj, bright);
            }
        }
    }

    private static void glow(VertexConsumer g, Matrix4f m, float x, float y, float z, float a) {
        g.addVertex(m, x, y, z).setColor(0.78F, 0.86F, 1.0F, a);
    }

    @Override
    public ResourceLocation getTextureLocation(SkyTrailEntity line) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
