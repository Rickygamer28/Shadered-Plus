package com.example.skyblocks.client;

import com.example.skyblocks.ContainmentFrameBlock;
import com.example.skyblocks.SingularityBlockEntity;
import com.example.skyblocks.SpecialFrameBlock;
import com.example.skyblocks.SpecialFrameBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The special frames' faces: a display frame shows what its singularity holds (how many items, how many kinds of how
 * many it can, a bar of how many of those kinds are used) on its screen; a port shows the item it's set to; a monitor its item and how many of it.
 */
public class SpecialFrameRenderer implements BlockEntityRenderer<SpecialFrameBlockEntity> {
    /** The face's screen sits on the frame's plate, this far out from the block's middle (just past its glow). */
    private static final float FACE = 0.3855F;

    public SpecialFrameRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SpecialFrameBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        SpecialFrameBlock.Kind kind = be.kind();
        Level level = be.getLevel();
        if (kind == null || level == null || kind == SpecialFrameBlock.Kind.REDSTONE) {
            return;
        }
        ItemStack item = kind == SpecialFrameBlock.Kind.DISPLAY ? ItemStack.EMPTY : be.filter();
        if (kind != SpecialFrameBlock.Kind.DISPLAY && item.isEmpty()) {
            return;
        }
        SingularityBlockEntity s = kind == SpecialFrameBlock.Kind.DISPLAY ? be.singularity() : null;
        for (Direction d : Direction.values()) {
            BlockPos n = be.getBlockPos().relative(d);
            BlockState ns = level.getBlockState(n);
            if (ns.getBlock() instanceof ContainmentFrameBlock || ns.isSolidRender(level, n)) {
                continue; // (a side nobody sees)
            }
            int[] wall = kind == SpecialFrameBlock.Kind.DISPLAY ? wall(be.getBlockPos(), d, level) : null;
            if (wall != null && wall[0] < 0) {
                continue; // (part of a display wall drawn by its top left frame)
            }
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            if (d.getAxis().isHorizontal()) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-d.toYRot()));
            } else {
                poseStack.mulPose(Axis.XP.rotationDegrees(d == Direction.UP ? -90.0F : 90.0F));
            }
            poseStack.translate(0.0, 0.0, FACE);
            if (wall != null) {
                wallScreen(poseStack, buffers, s, wall[0], wall[1]);
            } else if (kind == SpecialFrameBlock.Kind.DISPLAY) {
                screen(poseStack, buffers, s);
            } else {
                int faceLight = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, n);
                icon(poseStack, buffers, item, faceLight, overlay, kind == SpecialFrameBlock.Kind.MONITOR ? be.count() : -2);
            }
            poseStack.popPose();
        }
    }

    // ---- display walls: display frames side by side on a face of the chamber, as one big screen ----

    /** (Worked out now and then, not each frame: where each display's face is in its wall.) */
    private static final java.util.Map<Long, int[]> WALLS = new java.util.HashMap<>();
    private static long wallsAt;

    private static Vector3f faceRight(Direction d) {
        return faceAxis(d, new Vector3f(1.0F, 0.0F, 0.0F));
    }

    private static Vector3f faceUp(Direction d) {
        return faceAxis(d, new Vector3f(0.0F, 1.0F, 0.0F));
    }

    private static Vector3f faceAxis(Direction d, Vector3f v) {
        org.joml.Quaternionf q = d.getAxis().isHorizontal() ? Axis.YP.rotationDegrees(-d.toYRot()) : Axis.XP.rotationDegrees(d == Direction.UP ? -90.0F : 90.0F);
        return q.transform(v);
    }

    private static boolean shows(Level level, BlockPos p, Direction d) {
        BlockState st = level.getBlockState(p);
        if (!(st.getBlock() instanceof SpecialFrameBlock b) || b.kind() != SpecialFrameBlock.Kind.DISPLAY) {
            return false;
        }
        BlockPos n = p.relative(d);
        BlockState ns = level.getBlockState(n);
        return !(ns.getBlock() instanceof ContainmentFrameBlock) && !ns.isSolidRender(level, n);
    }

    /**
     * Is this display's face (towards d) in a wall of them - a rectangle of two or more, all showing that way? Its top
     * left one draws the whole wall: for it, how many across and down; for the rest {-1, -1}; null for one on its own.
     */
    @org.jetbrains.annotations.Nullable
    private static int[] wall(BlockPos pos, Direction d, Level level) {
        long now = level.getGameTime();
        if (now - wallsAt > 20 || now < wallsAt) {
            WALLS.clear();
            wallsAt = now;
        }
        long key = pos.asLong() * 6 + d.ordinal();
        if (WALLS.containsKey(key)) {
            return WALLS.get(key);
        }
        Vector3f r = faceRight(d), u = faceUp(d);
        BlockPos dr = BlockPos.containing(Math.round(r.x), Math.round(r.y), Math.round(r.z)), du = BlockPos.containing(Math.round(u.x), Math.round(u.y), Math.round(u.z));
        // every display joined to this one across the face (up to 64), and where each is (across, up) from this one
        java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        todo.add(pos);
        seen.add(pos);
        int minA = 0, maxA = 0, minU = 0, maxU = 0;
        while (!todo.isEmpty() && seen.size() < 64) {
            BlockPos p = todo.poll();
            BlockPos o = p.subtract(pos);
            int a = o.getX() * dr.getX() + o.getY() * dr.getY() + o.getZ() * dr.getZ(), up = o.getX() * du.getX() + o.getY() * du.getY() + o.getZ() * du.getZ();
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minU = Math.min(minU, up);
            maxU = Math.max(maxU, up);
            for (BlockPos n : new BlockPos[] {p.offset(dr), p.subtract(dr), p.offset(du), p.subtract(du)}) {
                if (!seen.contains(n) && shows(level, n, d)) {
                    seen.add(n);
                    todo.add(n);
                }
            }
        }
        int w = maxA - minA + 1, h = maxU - minU + 1;
        int[] out;
        if (seen.size() < 2 || seen.size() != w * h) {
            out = null; // (on its own, or not a whole rectangle: each shows its own)
        } else if (minA == 0 && maxU == 0) {
            out = new int[] {w, h};
        } else {
            out = new int[] {-1, -1};
        }
        WALLS.put(key, out);
        return out;
    }

    /**
     * A display wall's screen, drawn from its top left frame: the totals and how full along its top, then a grid of what
     * the singularity holds most of - each item and how many - paging through them if they don't all fit, a faint line
     * sweeping down it.
     */
    private static void wallScreen(PoseStack poseStack, MultiBufferSource buffers, SingularityBlockEntity s, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        poseStack.pushPose();
        poseStack.translate((w - 1) / 2.0F, -(h - 1) / 2.0F, 0.075F);
        float px = 1.0F / 112.0F; // (112 of the font's pixels to a block)
        float sw = w * 112.0F - 20.0F, sh = h * 112.0F - 20.0F;
        int bright = LightTexture.FULL_BRIGHT;
        poseStack.pushPose();
        poseStack.scale(px, -px, px);
        Matrix4f m = poseStack.last().pose();
        rect(m, buffers, -sw / 2 - 4, -sh / 2 - 4, sw / 2 + 4, sh / 2 + 4, 0xFF1C1F2A, bright, -0.2F);
        rect(m, buffers, -sw / 2, -sh / 2, sw / 2, sh / 2, 0xFF07111C, bright, -0.1F);
        float t = (float) LavaLampRenderer.seconds();
        float sweep = (t * 0.25F % 1.0F) * sh - sh / 2;
        rect(m, buffers, -sw / 2, sweep, sw / 2, Math.min(sh / 2, sweep + 8), 0x1A78DCFF, bright, 0.05F);
        if (s == null) {
            draw(font, m, buffers, "- - -", 0, -4, 0xFF2E7A55, bright);
            poseStack.popPose();
            poseStack.popPose();
            return;
        }
        // its top: how many items, how many kinds of the most it can, a bar of that
        float top = -sh / 2 + 6;
        String head = shortNumber(s.total()) + " items  ·  " + s.kinds() + "/" + shortNumber(s.maxKinds()) + " kinds";
        int hw = font.width(head);
        float hk = Math.min(1.5F, (sw - 12) / Math.max(1, hw));
        Matrix4f hm = new Matrix4f(m).translate(-sw / 2 + 8, top, 0.5F).scale(hk, hk, 1.0F);
        font.drawInBatch(head, 0, 0, 0xFF7CFFB8, false, hm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);
        float full = Math.min(1.0F, (float) s.kinds() / Math.max(1, s.maxKinds()));
        int colour = full > 0.85F ? 0xFFFF6A4A : full > 0.6F ? 0xFFFFC83D : 0xFF3CCF7A;
        float barY = top + 9 * hk + 4;
        rect(m, buffers, -sw / 2 + 8, barY, sw / 2 - 8, barY + 4, 0xFF10251A, bright, 0.0F);
        rect(m, buffers, -sw / 2 + 8, barY, -sw / 2 + 8 + (sw - 16) * full, barY + 4, colour, bright, 0.3F);
        // the grid: cells about 50 pixels, as many as fit under the top
        float gridTop = barY + 10, gridH = sh / 2 - gridTop - 4;
        int cols = Math.max(1, (int) ((sw - 8) / 50)), rows = Math.max(1, (int) (gridH / 52));
        float cw = (sw - 8) / cols, ch = gridH / rows;
        List<ItemStack> items = s.shown();
        List<Long> counts = s.shownCounts();
        int cells = cols * rows, pages = Math.max(1, (items.size() + cells - 1) / cells);
        int page = (int) (t / 4.0F) % pages;
        for (int i = 0; i < cells; i++) {
            int k = page * cells + i;
            float cx = -sw / 2 + 4 + cw * (i % cols + 0.5F), cy = gridTop + ch * (i / cols + 0.5F);
            rect(m, buffers, cx - cw / 2 + 2, cy - ch / 2 + 2, cx + cw / 2 - 2, cy + ch / 2 - 2, 0xFF0F1D2E, bright, 0.0F);
            if (k >= items.size()) {
                continue;
            }
            long n = k < counts.size() ? counts.get(k) : 0;
            draw(font, m, buffers, shortNumber(n), cx, cy + ch / 2 - 15, 0xFFFFD36B, bright);
            // its share of the most held: a thin bar along the cell's bottom
            float share = counts.isEmpty() ? 0 : (float) n / Math.max(1L, counts.get(0));
            rect(m, buffers, cx - cw / 2 + 5, cy + ch / 2 - 5, cx - cw / 2 + 5 + (cw - 10) * share, cy + ch / 2 - 3, 0xFF5EE6F0, bright, 0.3F);
            itemsToDraw.add(new float[] {cx, cy - 6, Math.min(cw, ch) * 0.5F, k});
        }
        poseStack.popPose();
        // the items themselves, in the screen's plane (back in block units)
        for (float[] it : itemsToDraw) {
            poseStack.pushPose();
            poseStack.translate(it[0] * px, -it[1] * px, 0.01F);
            float size = it[2] * px;
            poseStack.scale(size, size, 0.02F);
            mc.getItemRenderer().renderStatic(items.get((int) it[3]), ItemDisplayContext.GUI, bright, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    poseStack, buffers, mc.level, 0);
            poseStack.popPose();
        }
        itemsToDraw.clear();
        poseStack.popPose();
    }

    private static final List<float[]> itemsToDraw = new java.util.ArrayList<>();

    /** A display's screen: the total, kinds out of the most it can, and a bar of how full it is. */
    private static void screen(PoseStack poseStack, MultiBufferSource buffers, SingularityBlockEntity s) {
        Font font = Minecraft.getInstance().font;
        float px = 0.5F / 56.0F; // (the screen is half a block across: 56 of the font's pixels)
        poseStack.scale(px, -px, px);
        Matrix4f m = poseStack.last().pose();
        int bright = LightTexture.FULL_BRIGHT;
        if (s == null) {
            draw(font, m, buffers, "- - -", 0, -4, 0xFF2E7A55, bright);
            return;
        }
        String total = shortNumber(s.total());
        String kinds = s.kinds() + "/" + shortNumber(s.maxKinds());
        draw(font, m, buffers, total, 0, -19, 0xFF7CFFB8, bright);
        draw(font, m, buffers, kinds, 0, -6, 0xFF49C98A, bright);
        // how full: a bar of the kinds used out of the most it can hold (its real limit: there's no limit on how many
        // of each), green, going red near the top
        float full = Math.min(1.0F, (float) s.kinds() / Math.max(1, s.maxKinds()));
        int colour = full > 0.85F ? 0xFFFF6A4A : full > 0.6F ? 0xFFFFC83D : 0xFF3CCF7A;
        rect(m, buffers, -22, 8, 22, 14, 0xFF10251A, bright, 0.0F);
        rect(m, buffers, -21, 9, -21 + 42 * full, 13, colour, bright, 0.3F);
    }

    private static void draw(Font font, Matrix4f m, MultiBufferSource buffers, String text, float cx, float y, int colour, int light) {
        // (shrunk to fit the screen if it's long)
        int w = font.width(text);
        float k = w > 50 ? 50.0F / w : 1.0F;
        Matrix4f mm = new Matrix4f(m).translate(cx, y + 4.0F, 0.5F).scale(k, k, 1.0F);
        font.drawInBatch(text, -w / 2.0F, -4.0F, colour, false, mm, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
    }

    private static void rect(Matrix4f m, MultiBufferSource buffers, float x0, float y0, float x1, float y1, int argb, int light, float z) {
        VertexConsumer v = buffers.getBuffer(RenderType.textBackground());
        v.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
        v.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
    }

    /** A port's item, a monitor's item and how many of it there are ({@code count} -2: none shown; -1: not formed). */
    private static void icon(PoseStack poseStack, MultiBufferSource buffers, ItemStack item, int light, int overlay, long count) {
        Minecraft mc = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(0.0, count > -2 ? 0.07 : 0.0, 0.01);
        poseStack.scale(0.3F, 0.3F, 0.02F);
        mc.getItemRenderer().renderStatic(item, ItemDisplayContext.GUI, light, overlay, poseStack, buffers, mc.level, 0);
        poseStack.popPose();
        if (count > -2) {
            float px = 0.5F / 56.0F;
            poseStack.pushPose();
            poseStack.translate(0.0, 0.0, 0.03);
            poseStack.scale(px, -px, px);
            draw(mc.font, poseStack.last().pose(), buffers, count < 0 ? "-" : shortNumber(count), 0, 12, 0xFFFFD36B, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
    }

    static String shortNumber(long n) {
        if (n < 10_000) {
            return Long.toString(n);
        }
        String[] units = {"K", "M", "B", "T"};
        double v = n;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return (v < 10 ? String.format(java.util.Locale.ROOT, "%.1f", v) : String.format(java.util.Locale.ROOT, "%.0f", v)) + units[u];
    }

    @Override
    public AABB getRenderBoundingBox(SpecialFrameBlockEntity be) {
        return be.kind() == SpecialFrameBlock.Kind.DISPLAY ? new AABB(be.getBlockPos()).inflate(7.0) : new AABB(be.getBlockPos());
    }

    @Override
    public boolean shouldRenderOffScreen(SpecialFrameBlockEntity be) {
        return false;
    }

    /** (Its writing and pictures: too small to read further off than this.) */
    @Override
    public int getViewDistance() {
        return 32;
    }
}
