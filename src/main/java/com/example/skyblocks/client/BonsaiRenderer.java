package com.example.skyblocks.client;

import com.example.skyblocks.BonsaiBlock;
import com.example.skyblocks.BonsaiBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * The bonsai's little tree, from its own kind's bark and leaves: trunk and branches as tapering boxes, the foliage as
 * blocky clouds - grown to its age, shaped to its style (BonsaiBlock).
 */
public class BonsaiRenderer implements BlockEntityRenderer<BonsaiBlockEntity> {
    /** A trunk or branch: from, to (pixels, from the soil's middle; +z the pot's front), thickness at each end. */
    private record Seg(float x0, float y0, float z0, float x1, float y1, float z1, float w0, float w1) {}

    /** A cloud of leaves: middle, size (pixels). */
    private record Pad(float x, float y, float z, float sx, float sy, float sz) {}

    private record Tree(Seg[] trunk, Pad[] pads) {}

    private static Seg s(float x0, float y0, float z0, float x1, float y1, float z1, float w0, float w1) {
        return new Seg(x0, y0, z0, x1, y1, z1, w0, w1);
    }

    private static Pad p(float x, float y, float z, float sx, float sy, float sz) {
        return new Pad(x, y, z, sx, sy, sz);
    }

    /** One for each BonsaiBlock.Style. */
    private static final Tree[] TREES = {
            // upright: a straight trunk, branches in tiers
            new Tree(new Seg[] {s(0, 0, 0, 0, 5, 0, 1.7F, 1.2F), s(0, 5, 0, 0.2F, 9.2F, 0, 1.2F, 0.7F), s(0, 3.8F, 0, -3, 5.4F, 0.2F, 0.7F, 0.45F),
                    s(0, 5.8F, 0, 2.8F, 7, 0.5F, 0.6F, 0.4F), s(0.1F, 7.8F, 0, -1.6F, 9.2F, -1, 0.5F, 0.35F)},
                    new Pad[] {p(-3.6F, 6, 0.2F, 3.8F, 1.6F, 3.2F), p(3.1F, 7.6F, 0.5F, 3.2F, 1.5F, 3), p(0.2F, 10.4F, 0, 3.6F, 2.2F, 3.4F),
                            p(-1.8F, 9.6F, -1.2F, 2.2F, 1.3F, 2.2F)}),
            // round: one big crown
            new Tree(new Seg[] {s(0, 0, 0, 0.3F, 5, 0, 1.6F, 1.1F), s(0.3F, 5, 0, -1.2F, 7.4F, 0.4F, 0.6F, 0.4F), s(0.3F, 5, 0, 1.8F, 7.2F, -0.4F, 0.6F, 0.4F)},
                    new Pad[] {p(0.3F, 8, 0, 7, 4.4F, 6), p(0.3F, 10.4F, 0, 4.6F, 1.8F, 4.2F)}),
            // windswept: leaning hard to one side
            new Tree(new Seg[] {s(0, 0, 0, 1.5F, 3, 0, 1.6F, 1.25F), s(1.5F, 3, 0, 4, 5.5F, 0.2F, 1.25F, 0.85F), s(4, 5.5F, 0.2F, 5.8F, 7, 0.2F, 0.85F, 0.5F),
                    s(1.5F, 3, 0, -1, 4.2F, 0.6F, 0.55F, 0.4F)},
                    new Pad[] {p(6.2F, 7.6F, 0.2F, 3.8F, 1.5F, 3.2F), p(3.6F, 6.6F, 0.6F, 3, 1.3F, 2.6F), p(-1.6F, 4.6F, 0.6F, 2.4F, 1.1F, 2.2F)}),
            // cascade: up, then over the pot's front edge and down
            new Tree(new Seg[] {s(0, 0, 0, 0.5F, 2.6F, 0.8F, 1.6F, 1.25F), s(0.5F, 2.6F, 0.8F, 0.5F, 2.9F, 3.6F, 1.25F, 1), s(0.5F, 2.9F, 3.6F, 0.3F, 0, 5.4F, 1, 0.8F),
                    s(0.3F, 0, 5.4F, 0, -2.2F, 6.1F, 0.8F, 0.55F)},
                    new Pad[] {p(0.5F, 3.8F, 1, 2.8F, 1.3F, 2.6F), p(0.3F, 1, 5.3F, 2.8F, 1.3F, 2.4F), p(0, -2.4F, 6.3F, 3.2F, 1.4F, 2.6F)}),
            // cloud pruned: a twisting trunk, round puffs on bare branches
            new Tree(new Seg[] {s(0, 0, 0, 1, 3, 0, 1.5F, 1.15F), s(1, 3, 0, -1, 6, 0.2F, 1.15F, 0.85F), s(-1, 6, 0.2F, 0.5F, 9, 0, 0.85F, 0.55F),
                    s(1, 3, 0, 3.6F, 3.9F, 0.5F, 0.5F, 0.35F), s(-1, 6, 0.2F, -3.6F, 6.6F, -0.5F, 0.45F, 0.3F)},
                    new Pad[] {p(3.9F, 4.5F, 0.5F, 2.6F, 2, 2.6F), p(-3.9F, 7.1F, -0.5F, 2.6F, 2, 2.6F), p(0.5F, 10.1F, 0, 3, 2.4F, 3)})};

    /** How big the tree is at each age (0 a sprout .. 3, 4 full). */
    private static final float[] GROW = {0.3F, 0.5F, 0.74F, 1.0F, 1.0F};
    /** The top of the soil, in pixels. */
    private static final float SOIL = 3.5F;

    public BonsaiRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BonsaiBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof BonsaiBlock) || state.getValue(BonsaiBlock.KIND) == BonsaiBlock.Kind.NONE) {
            return;
        }
        BonsaiBlock.Kind kind = state.getValue(BonsaiBlock.KIND);
        int age = state.getValue(BonsaiBlock.AGE);
        Tree tree = TREES[state.getValue(BonsaiBlock.STYLE).ordinal()];
        float g = GROW[age];
        Minecraft mc = Minecraft.getInstance();
        // (the planted sapling's own tree - any mod's - as the server worked it out; by its kind until that's here)
        BlockState log = be.log != null ? be.log : kind == BonsaiBlock.Kind.AZALEA ? net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState()
                : BonsaiBlock.log(kind);
        BlockState leaves = be.leaves != null ? be.leaves : BonsaiBlock.leaves(kind, false);
        BlockState flowering = be.leaves != null ? be.flowering : kind == BonsaiBlock.Kind.AZALEA ? BonsaiBlock.leaves(kind, true) : null;
        TextureAtlasSprite bark = sprite(log, Direction.NORTH), barkEnd = sprite(log, Direction.UP);
        TextureAtlasSprite leaf = sprite(leaves, Direction.UP);
        TextureAtlasSprite flower = flowering != null && age == 4 ? sprite(flowering, Direction.UP) : leaf;
        int tint = tint(leaves, be);
        // (the leaves' solid core: a plain colour like the leaves', darker - their colour if tinted, else their map colour)
        int coreColour = shade(tint != 0xFFFFFF ? tint : leaves.getMapColor(be.getLevel(), be.getBlockPos()).col, 0.45F);

        poseStack.pushPose();
        poseStack.translate(0.5F, SOIL / 16.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw(state.getValue(BonsaiBlock.FACING))));
        poseStack.scale(1 / 16.0F, 1 / 16.0F, 1 / 16.0F);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        VertexConsumer core = buffers.getBuffer(RenderType.entitySolid(WHITE));
        float wg = 0.45F + 0.55F * g; // (thickness grows slower than height)
        for (Seg seg : tree.trunk) {
            branch(vc, pose, bark, barkEnd, seg.x0 * g, seg.y0 * g, seg.z0 * g, seg.x1 * g, seg.y1 * g, seg.z1 * g, seg.w0 * wg, seg.w1 * wg, light, overlay);
        }
        float pg = 0.35F + 0.65F * g;
        for (int i = 0; i < tree.pads.length; i++) {
            Pad pad = tree.pads[i];
            cloud(vc, core, coreColour, pose, i % 2 == 1 ? flower : leaf, pad.x * g, pad.y * g, pad.z * g, pad.sx * pg, pad.sy * pg, pad.sz * pg, tint,
                    light, overlay, age == 4 ? 1.08F : 1.0F);
        }
        // a fruit tree, grown: a few of its fruit hanging under the leaves
        if (age == 4 && !be.fruit.isEmpty()) {
            for (int i = 0; i < Math.min(3, tree.pads.length); i++) {
                Pad pad = tree.pads[i];
                poseStack.pushPose();
                poseStack.translate(pad.x + (i == 1 ? -0.6F : 0.7F), pad.y - pad.sy * 0.5F - 1.1F, pad.z + 0.5F);
                poseStack.mulPose(Axis.YP.rotationDegrees(40 + i * 70));
                poseStack.scale(3.2F, 3.2F, 3.2F);
                mc.getItemRenderer().renderStatic(be.fruit, net.minecraft.world.item.ItemDisplayContext.FIXED, light, overlay, poseStack, buffers,
                        be.getLevel(), (int) be.getBlockPos().asLong() + i);
                poseStack.popPose();
            }
        }
        poseStack.popPose();
    }

    private static final net.minecraft.resources.ResourceLocation WHITE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "textures/misc/white.png");

    /**
     * The leaves' colour here, as they'd be tinted as a block (with the tint their own model asks for: any mod's); a tinted
     * texture with no colour given (a mod colouring it some other way) takes the block's map colour; untinted, white.
     */
    private static int tint(BlockState leaves, BonsaiBlockEntity be) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(leaves);
        int index = -1;
        try {
            RandomSource r = RandomSource.create(42L);
            for (Direction d : new Direction[] {Direction.UP, Direction.NORTH, null}) {
                for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(leaves, d, r,
                        net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null)) {
                    if (q.isTinted()) {
                        index = q.getTintIndex();
                        break;
                    }
                }
                if (index >= 0) {
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // (no quads to ask: untinted)
        }
        var colours = Minecraft.getInstance().getBlockColors();
        if (index < 0) {
            // (no quad says it's tinted - some mods' leaf models don't - but the block may still have a colour of its own)
            int c = colours.getColor(leaves, be.getLevel(), be.getBlockPos(), 0);
            return c == -1 ? 0xFFFFFF : c & 0xFFFFFF;
        }
        int c = colours.getColor(leaves, be.getLevel(), be.getBlockPos(), index);
        return c == -1 ? leaves.getMapColor(be.getLevel(), be.getBlockPos()).col : c & 0xFFFFFF;
    }

    private static int shade(int c, float f) {
        return (int) (((c >> 16) & 255) * f) << 16 | (int) (((c >> 8) & 255) * f) << 8 | (int) ((c & 255) * f);
    }

    private static TextureAtlasSprite sprite(BlockState state, Direction side) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        try {
            java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads = model.getQuads(state, side, RandomSource.create(42L),
                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
            if (!quads.isEmpty()) {
                return quads.get(0).getSprite();
            }
        } catch (RuntimeException ignored) {
            // (its particle texture, then)
        }
        return model.getParticleIcon();
    }

    /** The model's front (+z) turned to the block's facing. */
    private static float yaw(Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0F;
            case EAST -> 90.0F;
            case WEST -> -90.0F;
            default -> 0.0F;
        };
    }

    /** A tapering four-sided box from one point to another (bark down its sides, end grain on its ends). */
    private static void branch(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite bark, TextureAtlasSprite end, float x0, float y0, float z0,
                               float x1, float y1, float z1, float w0, float w1, int light, int overlay) {
        Vector3f a = new Vector3f(x0, y0, z0), b = new Vector3f(x1, y1, z1);
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1.0E-3F) {
            return;
        }
        d.div(len);
        Vector3f up = Math.abs(d.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f u = new Vector3f(d).cross(up).normalize();
        Vector3f v = new Vector3f(u).cross(d).normalize();
        Vector3f[] s = corners(a, u, v, w0 / 2), e = corners(b, u, v, w1 / 2);
        // (the bark at half scale: fine grain on a little trunk)
        float vl = Math.min(1.0F, len / 8.0F);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float uw = Math.min(1.0F, Math.max(w0, w1) / 8.0F);
            quad(vc, pose, bark, light, overlay, 0xFFFFFF, s[i], s[j], e[j], e[i], 0, uw, 0, vl);
        }
        float cw = Math.min(1.0F, w1 / 16.0F) * 0.5F;
        quad(vc, pose, end, light, overlay, 0xFFFFFF, e[0], e[1], e[2], e[3], 0.5F - cw, 0.5F + cw, 0.5F - cw, 0.5F + cw);
        float sw = Math.min(1.0F, w0 / 16.0F) * 0.5F;
        quad(vc, pose, end, light, overlay, 0xFFFFFF, s[3], s[2], s[1], s[0], 0.5F - sw, 0.5F + sw, 0.5F - sw, 0.5F + sw);
    }

    private static Vector3f[] corners(Vector3f c, Vector3f u, Vector3f v, float r) {
        return new Vector3f[] {new Vector3f(c).add(new Vector3f(u).mul(r)).add(new Vector3f(v).mul(r)),
                new Vector3f(c).add(new Vector3f(u).mul(-r)).add(new Vector3f(v).mul(r)),
                new Vector3f(c).add(new Vector3f(u).mul(-r)).add(new Vector3f(v).mul(-r)),
                new Vector3f(c).add(new Vector3f(u).mul(r)).add(new Vector3f(v).mul(-r))};
    }

    /**
     * A cloud of leaves round (x, y, z): a few overlapping boxes (none sharing a face's plane with another, so nothing
     * flickers), rounded in a blocky way; behind the leaves a darker solid core, so the holes in the leaf texture show
     * deep foliage rather than straight through the little cloud.
     */
    private static void cloud(VertexConsumer vc, VertexConsumer core, int coreColour, PoseStack.Pose pose, TextureAtlasSprite leaf, float x, float y, float z,
                              float sx, float sy, float sz, int tint, int light, int overlay, float full) {
        sx *= full;
        sz *= full;
        float[][] boxes = {{0, 0, sx * 0.8F, sy, sz * 0.8F}, {-0.1F, 0, sx, sy * 0.62F, sz * 0.56F}, {-0.12F, 0, sx * 0.56F, sy * 0.6F, sz},
                {0.55F, 0, sx * 0.5F, sy * 0.35F, sz * 0.5F}};
        for (float[] b : boxes) {
            float cy = y + sy * b[0];
            box(vc, pose, leaf, x, cy, z, b[2], b[3], b[4], tint, light, overlay);
            box(core, pose, leaf, x, cy, z, b[2] * 0.68F, b[3] * 0.66F, b[4] * 0.68F, coreColour, light, overlay);
        }
    }

    private static void box(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, float cx, float cy, float cz, float sx, float sy, float sz,
                            int tint, int light, int overlay) {
        float x0 = cx - sx / 2, x1 = cx + sx / 2, y0 = cy - sy / 2, y1 = cy + sy / 2, z0 = cz - sz / 2, z1 = cz + sz / 2;
        // (the whole leaf texture on each face, however small: a miniature tree's leaves are miniature too)
        float ux = 1.0F, uy = 1.0F, uz = 1.0F, ox = 0.0F, oz = 0.0F;
        Vector3f[] c = {new Vector3f(x0, y0, z0), new Vector3f(x1, y0, z0), new Vector3f(x1, y0, z1), new Vector3f(x0, y0, z1),
                new Vector3f(x0, y1, z0), new Vector3f(x1, y1, z0), new Vector3f(x1, y1, z1), new Vector3f(x0, y1, z1)};
        quad(vc, pose, sp, light, overlay, tint, c[4], c[7], c[6], c[5], ox, ox + ux, oz, oz + uz); // up
        quad(vc, pose, sp, light, overlay, tint, c[0], c[1], c[2], c[3], ox, ox + ux, oz, oz + uz); // down
        quad(vc, pose, sp, light, overlay, tint, c[0], c[4], c[5], c[1], ox, ox + ux, 0, uy); // north
        quad(vc, pose, sp, light, overlay, tint, c[2], c[6], c[7], c[3], ox, ox + ux, 0, uy); // south
        quad(vc, pose, sp, light, overlay, tint, c[3], c[7], c[4], c[0], oz, oz + uz, 0, uy); // west
        quad(vc, pose, sp, light, overlay, tint, c[1], c[5], c[6], c[2], oz, oz + uz, 0, uy); // east
    }

    /** Four corners (a b c d round the face), the part of the sprite (fractions) across a..b and along b..c. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sp, int light, int overlay, int tint, Vector3f a, Vector3f b,
                             Vector3f c, Vector3f d, float u0, float u1, float v0, float v1) {
        Vector3f n = new Vector3f(c).sub(b).cross(new Vector3f(a).sub(b));
        if (n.lengthSquared() < 1.0E-8F) {
            return;
        }
        n.normalize();
        float shade = 0.72F + 0.28F * (n.y * 0.5F + 0.5F);
        int r = (int) (((tint >> 16) & 255) * shade), g = (int) (((tint >> 8) & 255) * shade), bl = (int) ((tint & 255) * shade);
        float su0 = sp.getU(u0), su1 = sp.getU(u1), sv0 = sp.getV(v0), sv1 = sp.getV(v1);
        vertex(vc, pose, a, r, g, bl, su0, sv1, light, overlay, n);
        vertex(vc, pose, b, r, g, bl, su0, sv0, light, overlay, n);
        vertex(vc, pose, c, r, g, bl, su1, sv0, light, overlay, n);
        vertex(vc, pose, d, r, g, bl, su1, sv1, light, overlay, n);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vector3f p, int r, int g, int b, float u, float v, int light, int overlay, Vector3f n) {
        vc.addVertex(pose, p.x, p.y, p.z).setColor(r, g, b, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, n.x, n.y, n.z);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(BonsaiBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.25);
    }
}
