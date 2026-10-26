package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.iris.IrisCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The vanilla aquariums: tanks just like the aquariums - their water, light, sand and frame, their fish chosen and
 * swimming (schooling, dodging, resting at night), their decorations - drawn by client/AquariumRenderer; here each
 * fish and decoration is drawn as a Minecraft-style model instead (the game's own pufferfish, sea turtle and dolphin;
 * blocky models of the rest, client/AquaModels; the game's own blocks for the castle, ship, rocks, pot, chest, coral,
 * seagrass and kelp), into the water's picture, then the tank's depth.
 */
public final class VanillaAquariumRenderer {
    /** Each kind of the aquariums' creatures (0-25): its blocky model, or null for the game's own. */
    private static final String[] MODEL = {"clownfish", "blue_tang", "yellow_tang", "neon_tetra", "angelfish", "goldfish", "betta", null,
            "guppy", "discus", "shark", "tuna", "manta_ray", null, "grouper", "jellyfish", null, "sardine", "ocean_sunfish", "basking_shark",
            "hermit_crab", "crab", "stingray", "octopus", "lobster", "shrimp"};
    private static final int PUFFERFISH = 7, SEA_TURTLE = 13, DOLPHIN = 16, MANTA = 12, JELLYFISH = 15, STINGRAY = 22;

    private static final ResourceLocation PUFFER_TEX = mc("textures/entity/fish/pufferfish.png"),
            TURTLE_TEX = mc("textures/entity/turtle/big_sea_turtle.png"), DOLPHIN_TEX = mc("textures/entity/dolphin.png");

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static Map<String, ModelPart> models;
    private static final Map<String, ModelPart> OWN_MODELS = new HashMap<>();

    private VanillaAquariumRenderer() {}

    private static ModelPart game(String name) {
        if (models == null) {
            models = new HashMap<>();
            var set = Minecraft.getInstance().getEntityModels();
            Object[][] layers = {{"puffer", ModelLayers.PUFFERFISH_SMALL}, {"turtle", ModelLayers.TURTLE}, {"dolphin", ModelLayers.DOLPHIN}};
            for (Object[] l : layers) {
                models.put((String) l[0], set.bakeLayer((ModelLayerLocation) l[1]));
            }
            ModelPart turtle = models.get("turtle");
            if (turtle.hasChild("egg_belly")) {
                turtle.getChild("egg_belly").visible = false;
            }
        }
        return models.get(name);
    }

    private static ModelPart own(String name) {
        return OWN_MODELS.computeIfAbsent(name, AquaModels::build);
    }

    private static int ownIndex(String name) {
        return java.util.Arrays.asList(AquaModels.NAMES).indexOf(name);
    }

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {}

        @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW) // (after the aquariums have drawn the water)
        public static void onStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || MirrorRenderer.isRenderingReflection()
                    || IrisCompat.renderingShadowPass()) {
                return;
            }
            ClientLevel level = Minecraft.getInstance().level;
            List<AquariumRenderer.VanillaJob> jobs = AquariumRenderer.VANILLA_JOBS;
            if (level == null || jobs.isEmpty()) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(new Matrix4f(event.getModelViewMatrix()));
            poseStack.translate(-cam.x, -cam.y, -cam.z);
            var modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            boolean night = AquariumRenderer.night() > 0.5;
            for (AquariumRenderer.VanillaJob job : jobs) {
                for (int d = 0; d < job.decorCount(); d++) {
                    decoration(level, job, d, poseStack, buffers);
                }
                for (int i = 0; i < job.fishCount(); i++) {
                    creature(level, job, i, night, poseStack, buffers);
                }
            }
            buffers.endBatch();
            // the tanks' depth (the water's picture was drawn without it, so their fish could go into it)
            for (AquariumRenderer.VanillaJob job : jobs) {
                depth(job.faces(), poseStack);
            }
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // ---- the fish and sea creatures ----

    private static void creature(ClientLevel level, AquariumRenderer.VanillaJob job, int i, boolean night, PoseStack poseStack,
                                 MultiBufferSource buffers) {
        float[] f = job.fish();
        int o = i * 16;
        int kind = Math.round(f[o + 7]);
        if (kind < 0 || kind >= MODEL.length) {
            return;
        }
        double x = job.min().getX() + f[o], y = job.min().getY() + f[o + 1], z = job.min().getZ() + f[o + 2];
        float size = f[o + 3];
        Vec3 fwd = new Vec3(f[o + 4], f[o + 5], f[o + 6]), up = new Vec3(f[o + 8], f[o + 9], f[o + 10]);
        float wave = f[o + 11], beat = f[o + 12], bend = f[o + 13], squeeze = f[o + 14], walk = f[o + 15];
        float[] sp = AquariumRenderer.species(kind);
        boolean crawler = kind >= 20;
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
        if (kind == JELLYFISH && night) {
            light = LightTexture.FULL_BRIGHT; // (glowing in the dark)
        }
        // how big: as long as the aquariums' own (its body and tail), as wide for the rays, the bell for a jellyfish
        float length = crawler ? 2.0F * Math.max(sp[0], sp[2]) * size : (2.0F * sp[0] + 1.6F * sp[3]) * size;
        String name = MODEL[kind];
        if (name == null) { // the game's own: pufferfish, sea turtle, dolphin
            ModelPart m;
            ResourceLocation tex;
            float modelLength;
            if (kind == PUFFERFISH) {
                m = game("puffer");
                tex = PUFFER_TEX;
                modelLength = 4.0F / 16.0F;
                if (m.hasChild("right_fin")) m.getChild("right_fin").zRot = -0.3F + 0.3F * Mth.sin(wave);
                if (m.hasChild("left_fin")) m.getChild("left_fin").zRot = 0.3F - 0.3F * Mth.sin(wave);
            } else if (kind == SEA_TURTLE) {
                m = game("turtle");
                tex = TURTLE_TEX;
                modelLength = 1.5F;
                float paddle = 0.5F * Mth.sin(wave * 0.5F);
                if (m.hasChild("right_front_leg")) m.getChild("right_front_leg").zRot = paddle;
                if (m.hasChild("left_front_leg")) m.getChild("left_front_leg").zRot = -paddle;
            } else {
                m = game("dolphin");
                tex = DOLPHIN_TEX;
                modelLength = 1.9F;
                ModelPart body = m.hasChild("body") ? m.getChild("body") : m;
                if (body.hasChild("tail")) body.getChild("tail").xRot = 0.3F * Mth.sin(wave) * beat;
            }
            float scale = length / modelLength;
            draw(poseStack, buffers, m, RenderType.entityCutoutNoCull(tex), x, y, z, fwd, up, scale, 1.0F, light,
                    kind == SEA_TURTLE ? 0.15 : kind == DOLPHIN ? 0.3 : 0.08);
            return;
        }
        int idx = ownIndex(name);
        ModelPart m = own(name);
        ModelPart body = m.hasChild("body") ? m.getChild("body") : m;
        // its moving parts, from the aquariums' own swimming: the tail beating, the side fins, the wings, the legs
        if (body.hasChild("tail")) {
            ModelPart tail = body.getChild("tail");
            if (crawler) tail.xRot = 0.15F * Mth.sin(walk);
            else tail.yRot = 0.5F * beat * Mth.sin(wave) + bend * 0.15F;
        }
        if (body.hasChild("right_fin")) body.getChild("right_fin").yRot = -0.6F - 0.3F * Mth.sin(wave * 0.7F);
        if (body.hasChild("left_fin")) body.getChild("left_fin").yRot = 0.6F + 0.3F * Mth.sin(wave * 0.7F);
        if (body.hasChild("right_wing")) body.getChild("right_wing").zRot = 0.4F * Mth.sin(wave * 0.4F);
        if (body.hasChild("left_wing")) body.getChild("left_wing").zRot = -0.4F * Mth.sin(wave * 0.4F);
        for (int k = 0; k < 16; k++) {
            if (body.hasChild("tentacle" + k)) { // (a jellyfish trailing, an octopus curling)
                ModelPart te = body.getChild("tentacle" + k);
                te.xRot = te.getInitialPose().xRot + (kind == JELLYFISH ? 0.35F * squeeze + 0.12F * Mth.sin(wave * 0.3F + k)
                        : 0.15F * Mth.sin(walk * 0.3F + k));
            }
            for (String side : new String[] {"right", "left"}) {
                if (body.hasChild(side + "_leg" + k)) { // (the legs stepping in turn, as far as it has walked)
                    ModelPart leg = body.getChild(side + "_leg" + k);
                    leg.xRot = leg.getInitialPose().xRot + 0.35F * Mth.sin(walk + k * 2.1F + (side.equals("left") ? 3.14F : 0.0F));
                }
            }
        }
        for (String side : new String[] {"right", "left"}) {
            if (body.hasChild(side + "_claw")) body.getChild(side + "_claw").xRot = 0.12F * Mth.sin(walk * 0.4F + (side.equals("left") ? 1.5F : 0.0F));
        }
        float modelLength = (kind == MANTA || kind == STINGRAY || kind == JELLYFISH ? AquaModels.WIDTH_PX[idx] : AquaModels.LENGTH_PX[idx]) / 16.0F;
        if (kind == MANTA || kind == STINGRAY) {
            length = 2.0F * sp[2] * size; // (the rays: as wide as the aquariums' own)
        } else if (kind == JELLYFISH) {
            length = 2.0F * sp[0] * size;
        }
        float scale = length / Math.max(modelLength, 0.05F);
        float squash = kind == JELLYFISH ? 1.0F - 0.18F * squeeze : 1.0F; // (a jellyfish's bell squeezing)
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/entity/aqua/" + name + ".png");
        draw(poseStack, buffers, m, RenderType.entityCutoutNoCull(texture), x, y, z, fwd, up, scale, squash, light, 0.0);
    }

    /**
     * A model at (x, y, z) facing fwd with its top towards up (as the aquariums' fish lean into their turns), at that
     * size (as the game draws mobs). lift: how far (in the model's blocks) its middle is above its origin.
     */
    private static void draw(PoseStack poseStack, MultiBufferSource buffers, ModelPart part, RenderType type, double x, double y, double z,
                             Vec3 fwd, Vec3 up, float scale, float squash, int light, double lift) {
        poseStack.pushPose();
        poseStack.translate(x, y - lift * scale, z);
        // turned so its front (-z) faces fwd and its top (+y, after the game's flip) faces up
        Vec3 f = fwd.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : fwd.normalize();
        Vec3 u = up.subtract(f.scale(up.dot(f)));
        u = u.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : u.normalize();
        Vec3 back = f.scale(-1.0);
        Vec3 side = u.cross(back);
        Matrix3f r = new Matrix3f((float) side.x, (float) side.y, (float) side.z, (float) u.x, (float) u.y, (float) u.z,
                (float) back.x, (float) back.y, (float) back.z);
        poseStack.mulPose(new Quaternionf().setFromNormalized(r));
        poseStack.scale(-scale, -scale * squash, scale);
        poseStack.translate(0.0F, -1.5F, 0.0F);
        part.render(poseStack, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, underwater(0xFFFFFFFF));
        poseStack.popPose();
    }

    /** A colour as seen through the water (a little blue-green, as the aquariums' water). */
    private static int underwater(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return (c & 0xFF000000) | (r * 205 / 255) << 16 | (g * 230 / 255) << 8 | (b * 240 / 255);
    }

    // ---- the decorations (where and how big the aquariums put them) ----

    private static void decoration(ClientLevel level, AquariumRenderer.VanillaJob job, int d, PoseStack poseStack, MultiBufferSource buffers) {
        float[] dd = job.decor();
        int pk = Math.round(dd[d * 4 + 3]);
        int type = pk % 16, turn16 = (pk / 16) % 16;
        float sz = (pk / 256) / 10.0F;
        double x = job.min().getX() + dd[d * 4], y = job.min().getY() + dd[d * 4 + 1], z = job.min().getZ() + dd[d * 4 + 2];
        int turn = Math.round(turn16 / 4.0F) % 4;
        float yaw = turn16 * 22.5F;
        Random random = new Random(job.min().asLong() * 7L + d * 131L);
        List<Piece> pieces = new ArrayList<>();
        switch (type) {
            case 0 -> { // rocks
                Block[] stones = {Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE, Blocks.STONE, Blocks.TUFF};
                int n = 1 + random.nextInt(3);
                for (int i = 0; i < n; i++) {
                    float s = (0.16F + random.nextFloat() * 0.18F) * sz;
                    pieces.add(new Piece(stones[random.nextInt(stones.length)].defaultBlockState(), x + (random.nextFloat() - 0.5) * 0.3 * sz, y - 0.03,
                            z + (random.nextFloat() - 0.5) * 0.3 * sz, s, s * (0.7F + random.nextFloat() * 0.4F), random.nextInt(4)));
                }
            }
            case 3 -> castle(pieces, x, y, z, turn, 0.2F * sz);
            case 4 -> { // coral: a coral block with a coral on it
                Block[] blocks = {Blocks.BRAIN_CORAL_BLOCK, Blocks.TUBE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK};
                Block[] fans = {Blocks.BRAIN_CORAL, Blocks.TUBE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL, Blocks.BUBBLE_CORAL};
                int c = random.nextInt(blocks.length);
                pieces.add(new Piece(blocks[c].defaultBlockState(), x, y - 0.02, z, 0.26F * sz, 0.22F * sz, turn));
                pieces.add(new Piece(fans[c].defaultBlockState(), x, y + 0.2 * sz, z, 0.4F * sz, 0.4F * sz, turn));
            }
            case 5 -> pieces.add(new Piece(Blocks.CHEST.defaultBlockState(), x, y - 0.04, z, 0.45F * sz, 0.45F * sz, turn));
            case 7 -> pieces.add(new Piece(Blocks.DECORATED_POT.defaultBlockState(), x, y - 0.02, z, 0.4F * sz, 0.4F * sz, turn));
            case 8 -> ship(pieces, x, y, z, yaw, 0.1F * sz);
            case 12 -> { // seagrass: a little clump
                int n = 2 + random.nextInt(2);
                for (int i = 0; i < n; i++) {
                    float s = (0.45F + random.nextFloat() * 0.25F) * sz;
                    pieces.add(new Piece(Blocks.SEAGRASS.defaultBlockState(), x + (random.nextFloat() - 0.5) * 0.25 * sz, y,
                            z + (random.nextFloat() - 0.5) * 0.25 * sz, s, s, random.nextInt(4)));
                }
            }
            case 13 -> { // kelp: up most of the water (its size is its height)
                int n = Math.max(1, Math.round(sz / 0.9F));
                for (int k = 0; k < n; k++) {
                    pieces.add(new Piece(k == n - 1 ? Blocks.KELP.defaultBlockState() : Blocks.KELP_PLANT.defaultBlockState(), x, y + k * 0.9, z, 0.7F, 0.9F, turn));
                }
            }
            default -> { // one of its own models
                String name = type == 1 ? "scallop" : type == 2 ? "starfish" : type == 6 ? "anemone" : type == 9 ? "anchor"
                        : type == 10 ? "diving_helmet" : "clam";
                ornament(level, name, x, y, z, yaw, AquaModels.SCALES[ownIndex(name)] * sz, d * 7.3F, poseStack, buffers);
            }
        }
        for (Piece p : pieces) {
            piece(level, p, poseStack, buffers);
        }
    }

    /** A little block of the game's own: the middle of its base at (x, y, z), its size each way, turned (any way). */
    private record Piece(BlockState state, double x, double y, double z, float sx, float sy, float sz, Quaternionf rot) {
        Piece(BlockState state, double x, double y, double z, float w, float h, int turn) {
            this(state, x, y, z, w, h, w, Axis.YP.rotationDegrees(90.0F * turn));
        }
    }

    /** One little block of a structure: at (i, h, k) of its little grid (each u big), the structure turned round (x, z). */
    private static void at(List<Piece> pieces, BlockState state, double x, double y, double z, int turn, double i, double h, double k, float u) {
        double ox = (i - 1) * u, oz = k * u - u;
        double rx = switch (turn) { case 1 -> -oz; case 2 -> -ox; case 3 -> oz; default -> ox; };
        double rz = switch (turn) { case 1 -> ox; case 2 -> -oz; case 3 -> -ox; default -> oz; };
        pieces.add(new Piece(state, x + rx, y + h * u, z + rz, u, u, turn));
    }

    /** A little castle of stone bricks: four towers, walls between, a keep with a flag. */
    private static void castle(List<Piece> pieces, double x, double y, double z, int turn, float u) {
        for (int[] t : new int[][] {{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
            for (int h = 0; h < 2; h++) at(pieces, Blocks.STONE_BRICKS.defaultBlockState(), x, y, z, turn, t[0], h, t[1], u);
            at(pieces, Blocks.STONE_BRICK_WALL.defaultBlockState(), x, y, z, turn, t[0], 2, t[1], u);
        }
        for (int[] w : new int[][] {{1, 0}, {0, 1}, {2, 1}, {1, 2}}) {
            at(pieces, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), x, y, z, turn, w[0], 0, w[1], u);
        }
        for (int h = 0; h < 3; h++) at(pieces, Blocks.STONE_BRICKS.defaultBlockState(), x, y, z, turn, 1, h, 1, u);
        at(pieces, Blocks.OAK_FENCE.defaultBlockState(), x, y, z, turn, 1, 3, 1, u);
        at(pieces, Blocks.RED_WOOL.defaultBlockState(), x, y, z, turn, 1.3, 3.5, 1, u * 0.5F);
    }

    /**
     * A sunken wooden ship, half in the sand and listing over: a dark keel and a pointed bow, planked sides (a hole
     * broken in one), its deck partly gone, broken railings, a cabin at the stern with a window, the mast with its
     * yard and a torn sail. Built in its own little grid (i across, h up, k along: the bow towards +k, each u big),
     * then turned, tipped and sunk as one.
     */
    private static void ship(List<Piece> pieces, double x, double y, double z, float yaw, float u) {
        Quaternionf rot = Axis.YP.rotationDegrees(180.0F - yaw).mul(Axis.ZP.rotation(0.32F)).mul(Axis.XP.rotation(-0.1F));
        double sink = 0.7 * u;
        java.util.function.BiConsumer<BlockState, double[]> put = (state, q) -> { // q: i, h, k, and its size across, up, along
            org.joml.Vector3f v = new org.joml.Vector3f((float) (q[0] * u), (float) (q[1] * u), (float) (q[2] * u));
            rot.transform(v);
            pieces.add(new Piece(state, x + v.x, y - sink + v.y, z + v.z, (float) (q[3] * u), (float) (q[4] * u), (float) (q[5] * u), rot));
        };
        BlockState keel = Blocks.DARK_OAK_PLANKS.defaultBlockState(), plank = Blocks.SPRUCE_PLANKS.defaultBlockState(),
                deck = Blocks.OAK_SLAB.defaultBlockState(), rail = Blocks.OAK_FENCE.defaultBlockState(), dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState bow = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH);
        // the bottom: the keel all along, the hull three wide in the middle, a pointed bow
        for (int k = -3; k <= 3; k++) put.accept(keel, new double[] {0, 0, k, 1, 1, 1});
        for (int k = -2; k <= 2; k++) {
            put.accept(keel, new double[] {-1, 0, k, 1, 1, 1});
            put.accept(keel, new double[] {1, 0, k, 1, 1, 1});
        }
        put.accept(bow, new double[] {0, 0, 4, 1, 1, 1});
        put.accept(bow, new double[] {0, 1, 4, 1, 1, 1});
        // the sides (a hole broken in the right one), the deck (some of it gone)
        for (int k = -3; k <= 3; k++) {
            put.accept(plank, new double[] {-1, 1, k, 1, 1, 1});
            if (k != 0 && k != 1) put.accept(plank, new double[] {1, 1, k, 1, 1, 1});
            if (k > -2 && k != 1) put.accept(deck, new double[] {0, 1, k, 1, 1, 1});
        }
        // broken railings along the sides
        for (int k = -1; k <= 3; k++) {
            if (k != 1) put.accept(rail, new double[] {-1, 2, k, 1, 1, 1});
            if (k == -1 || k == 3) put.accept(rail, new double[] {1, 2, k, 1, 1, 1});
        }
        // the cabin at the stern, its roof and a dark window
        for (int k = -3; k <= -2; k++) {
            for (int i = -1; i <= 1; i++) {
                put.accept(dark, new double[] {i, 2, k, 1, 1, 1});
                put.accept(Blocks.DARK_OAK_SLAB.defaultBlockState(), new double[] {i, 3, k, 1, 1, 1});
            }
        }
        put.accept(Blocks.BLACK_CONCRETE.defaultBlockState(), new double[] {0, 2.25, -1.45, 0.5, 0.5, 0.1});
        // the mast, its yard across, and what's left of the sail
        for (int h = 2; h <= 6; h++) put.accept(rail, new double[] {0, h, 1, 1, 1, 1});
        put.accept(Blocks.STRIPPED_OAK_LOG.defaultBlockState(), new double[] {0, 5.6, 1, 3.6, 0.3, 0.3});
        put.accept(Blocks.WHITE_WOOL.defaultBlockState(), new double[] {0, 4.3, 1.25, 3.0, 1.3, 0.1});
        put.accept(Blocks.WHITE_WOOL.defaultBlockState(), new double[] {-0.6, 3.2, 1.25, 1.7, 1.1, 0.1});
    }

    private static void piece(ClientLevel level, Piece p, PoseStack poseStack, MultiBufferSource buffers) {
        poseStack.pushPose();
        poseStack.translate(p.x(), p.y(), p.z());
        poseStack.mulPose(p.rot());
        poseStack.scale(p.sx(), p.sy(), p.sz());
        poseStack.translate(-0.5, 0.0, -0.5);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(p.x(), p.y() + 0.3, p.z()));
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(p.state(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /** One of the decorations of its own (the anemone swaying, the clam opening and shutting now and then). */
    private static void ornament(ClientLevel level, String name, double x, double y, double z, float yaw, float scale, float phase,
                                 PoseStack poseStack, MultiBufferSource buffers) {
        ModelPart m = own(name);
        ModelPart body = m.hasChild("body") ? m.getChild("body") : m;
        float t = LavaLampRenderer.seconds() + phase;
        for (int k = 0; k < 16; k++) {
            if (!body.hasChild("tentacle" + k)) break;
            ModelPart te = body.getChild("tentacle" + k);
            te.xRot = te.getInitialPose().xRot + 0.18F * (float) Math.sin(t * 1.4 + k * 0.7);
            te.zRot = te.getInitialPose().zRot + 0.18F * (float) Math.cos(t * 1.1 + k * 0.9);
        }
        if (body.hasChild("lid")) {
            float open = (float) Mth.clamp(Math.sin(t * 0.35) * 2.0 + 0.6, 0.0, 1.0); // (open most of the time, shutting now and then)
            body.getChild("lid").xRot = -0.15F - 0.85F * open;
        }
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y + 0.2, z));
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/entity/aqua/" + name + ".png");
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.scale(-scale, -scale, scale);
        poseStack.translate(0.0F, -1.5F, 0.0F);
        m.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY, underwater(0xFFFFFFFF));
        poseStack.popPose();
    }

    /** Writes a tank's outside faces into the depth (nothing to the colour), as the aquariums' own pictures do. */
    private static void depth(float[] f, PoseStack poseStack) {
        if (f.length == 0) {
            return;
        }
        com.mojang.blaze3d.vertex.BufferBuilder b = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION);
        Matrix4f m = poseStack.last().pose();
        for (int i = 0; i < f.length; i += 3) {
            b.addVertex(m, f[i], f[i + 1], f[i + 2]);
        }
        com.mojang.blaze3d.vertex.MeshData mesh = b.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        RenderSystem.disableCull();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2.0F, -20.0F); // (as the aquariums' pictures: over the blocks' own faces)
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionShader);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.colorMask(true, true, true, true);
    }
}
