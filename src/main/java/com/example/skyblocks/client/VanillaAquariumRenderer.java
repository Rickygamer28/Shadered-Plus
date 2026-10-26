package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.VanillaAquariumBlock;
import com.example.skyblocks.client.iris.IrisCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The vanilla aquariums' insides: every set of touching vanilla aquarium blocks is one tank (its glass, water and sand
 * are its block model); in it the game's own fish and sea animals swim about (its own models and textures), each in
 * a stretch of the tank's water, and the game's own seagrass, kelp, sea pickles and (in the ocean one) coral grow on
 * the bottom. Drawn before the water, so it's seen through it.
 */
public final class VanillaAquariumRenderer {
    private static final int SCAN_RADIUS = 4; // chunks
    private static final int RESCAN_TICKS = 20;
    private static final double RANGE = 96.0;

    // the kinds of animal
    private static final int TROPICAL = 0, COD = 1, SALMON = 2, PUFFER = 3, AXOLOTL = 4, TADPOLE = 5, SQUID = 6, GLOW_SQUID = 7, TURTLE = 8, DOLPHIN = 9;
    /** Per kind: how big it's drawn, how far its body reaches from its middle (blocks), how much of a tank's room it takes. */
    private static final float[] SCALE = {0.75F, 0.6F, 0.55F, 0.7F, 0.5F, 1.0F, 0.55F, 0.55F, 0.45F, 0.55F};
    private static final float[] REACH = {0.22F, 0.25F, 0.35F, 0.15F, 0.3F, 0.08F, 0.35F, 0.35F, 0.45F, 0.6F};
    private static final float[] COST = {0.5F, 0.6F, 0.8F, 0.7F, 1.0F, 0.25F, 2.0F, 2.0F, 3.0F, 5.0F};
    /** Per kind: the tank room it needs (blocks across x/z, and up). */
    private static final int[][] NEEDS = {{1, 1}, {1, 1}, {2, 1}, {1, 1}, {2, 1}, {1, 1}, {2, 2}, {2, 2}, {3, 2}, {4, 2}};
    private static final int[] NORMAL = {TROPICAL, TROPICAL, TROPICAL, TROPICAL, COD, COD, SALMON, PUFFER, AXOLOTL, TADPOLE, TADPOLE};
    private static final int[] OCEAN = {TROPICAL, TROPICAL, COD, COD, COD, SALMON, PUFFER, SQUID, GLOW_SQUID, TURTLE, DOLPHIN};

    private static final ResourceLocation COD_TEX = mc("textures/entity/fish/cod.png"), SALMON_TEX = mc("textures/entity/fish/salmon.png"),
            PUFFER_TEX = mc("textures/entity/fish/pufferfish.png"), TADPOLE_TEX = mc("textures/entity/tadpole/tadpole.png"),
            SQUID_TEX = mc("textures/entity/squid/squid.png"), GLOW_SQUID_TEX = mc("textures/entity/squid/glow_squid.png"),
            TURTLE_TEX = mc("textures/entity/turtle/big_sea_turtle.png"), DOLPHIN_TEX = mc("textures/entity/dolphin.png"),
            FISH_A = mc("textures/entity/fish/tropical_a.png"), FISH_B = mc("textures/entity/fish/tropical_b.png");
    private static final ResourceLocation[] AXOLOTLS = {mc("textures/entity/axolotl/axolotl_lucy.png"), mc("textures/entity/axolotl/axolotl_wild.png"),
            mc("textures/entity/axolotl/axolotl_gold.png"), mc("textures/entity/axolotl/axolotl_cyan.png"), mc("textures/entity/axolotl/axolotl_blue.png")};
    private static final ResourceLocation[] PATTERNS_A = new ResourceLocation[6], PATTERNS_B = new ResourceLocation[6];

    static {
        for (int i = 0; i < 6; i++) {
            PATTERNS_A[i] = mc("textures/entity/fish/tropical_a_pattern_" + (i + 1) + ".png");
            PATTERNS_B[i] = mc("textures/entity/fish/tropical_b_pattern_" + (i + 1) + ".png");
        }
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    /** One animal: its kind and look, the stretch of water it swims in (its middle can go anywhere in it), its path. */
    private record Animal(int kind, int look, int pattern, int body, int patternColour, float scale,
                          double x0, double y0, double z0, double x1, double y1, double z1, float[] phase, float speed) {}

    /** A plant on the bottom: what, where (its base), how big, how it's turned. */
    private record Plant(BlockState state, double x, double y, double z, float width, float height, int turn) {}

    private record Tank(List<Animal> animals, List<Plant> plants, double cx, double cy, double cz) {}

    private static final List<Tank> TANKS = new ArrayList<>();
    private static ClientLevel lastLevel;
    private static long lastScan = Long.MIN_VALUE;
    private static int lastChunkX, lastChunkZ;
    private static Map<String, ModelPart> models;

    private VanillaAquariumRenderer() {}

    private static ModelPart model(String name) {
        if (models == null) {
            models = new HashMap<>();
            var set = Minecraft.getInstance().getEntityModels();
            Object[][] layers = {{"fishA", ModelLayers.TROPICAL_FISH_SMALL}, {"fishAP", ModelLayers.TROPICAL_FISH_SMALL_PATTERN},
                    {"fishB", ModelLayers.TROPICAL_FISH_LARGE}, {"fishBP", ModelLayers.TROPICAL_FISH_LARGE_PATTERN}, {"cod", ModelLayers.COD},
                    {"salmon", ModelLayers.SALMON}, {"puffer", ModelLayers.PUFFERFISH_SMALL}, {"axolotl", ModelLayers.AXOLOTL},
                    {"tadpole", ModelLayers.TADPOLE}, {"squid", ModelLayers.SQUID}, {"glow_squid", ModelLayers.GLOW_SQUID},
                    {"turtle", ModelLayers.TURTLE}, {"dolphin", ModelLayers.DOLPHIN}};
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

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || MirrorRenderer.isRenderingReflection()
                    || IrisCompat.renderingShadowPass()) {
                return;
            }
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            int chunkX = SectionPos.blockToSectionCoord(cam.x), chunkZ = SectionPos.blockToSectionCoord(cam.z);
            long time = level.getGameTime();
            if (level != lastLevel || time - lastScan >= RESCAN_TICKS || time < lastScan || chunkX != lastChunkX || chunkZ != lastChunkZ) {
                scan(level, chunkX, chunkZ);
                lastLevel = level;
                lastScan = time;
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
            }
            if (TANKS.isEmpty()) {
                return;
            }
            float t = LavaLampRenderer.seconds();
            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(new Matrix4f(event.getModelViewMatrix()));
            poseStack.translate(-cam.x, -cam.y, -cam.z);
            var modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            for (Tank tank : TANKS) {
                if (cam.distanceToSqr(tank.cx(), tank.cy(), tank.cz()) > RANGE * RANGE) {
                    continue;
                }
                for (Plant p : tank.plants()) {
                    plant(level, p, poseStack, buffers);
                }
                for (int i = 0; i < tank.animals().size(); i++) {
                    animal(level, tank.animals().get(i), i, t, poseStack, buffers);
                }
            }
            buffers.endBatch();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    // ---- finding the tanks ----

    private static void scan(ClientLevel level, int centerX, int centerZ) {
        TANKS.clear();
        Map<BlockPos, Block> tiles = new HashMap<>();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(centerX + dx, centerZ + dz, false);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof VanillaAquariumBlock)) {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState s = section.getBlockState(x, y, z);
                                if (s.getBlock() instanceof VanillaAquariumBlock) {
                                    tiles.put(new BlockPos(baseX + x, baseY + y, baseZ + z), s.getBlock());
                                }
                            }
                        }
                    }
                }
            }
        }
        // touching blocks of one kind -> a tank
        Set<BlockPos> done = new java.util.HashSet<>();
        for (Map.Entry<BlockPos, Block> e : tiles.entrySet()) {
            if (done.contains(e.getKey()) || TANKS.size() > 512) {
                continue;
            }
            Block kind = e.getValue();
            List<BlockPos> blocks = new ArrayList<>();
            ArrayDeque<BlockPos> todo = new ArrayDeque<>(List.of(e.getKey()));
            done.add(e.getKey());
            while (!todo.isEmpty() && blocks.size() < 8192) {
                BlockPos p = todo.poll();
                blocks.add(p);
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (tiles.get(n) == kind && done.add(n)) {
                        todo.add(n);
                    }
                }
            }
            TANKS.add(tank(blocks, ((VanillaAquariumBlock) kind).ocean()));
        }
    }

    /** The biggest box of the tank's blocks grown from one (each way in turn, while the whole new layer is tank). */
    private static int[] grow(Set<BlockPos> in, BlockPos from) {
        int[] b = {from.getX(), from.getY(), from.getZ(), from.getX(), from.getY(), from.getZ()};
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int side = 0; side < 6; side++) {
                int axis = side % 3;
                boolean up = side >= 3;
                int[] c = b.clone();
                if (up) c[axis + 3]++; else c[axis]--;
                if (c[axis + 3] - c[axis] > 24) continue;
                int[] layer = c.clone();
                if (up) layer[axis] = c[axis + 3]; else layer[axis + 3] = c[axis];
                boolean all = true;
                for (int x = layer[0]; x <= layer[3] && all; x++) {
                    for (int y = layer[1]; y <= layer[4] && all; y++) {
                        for (int z = layer[2]; z <= layer[5] && all; z++) {
                            all = in.contains(new BlockPos(x, y, z));
                        }
                    }
                }
                if (all) {
                    b = c;
                    grew = true;
                }
            }
        }
        return b;
    }

    private static Tank tank(List<BlockPos> blocks, boolean ocean) {
        Set<BlockPos> in = new java.util.HashSet<>(blocks);
        BlockPos min = blocks.get(0);
        double cx = 0, cy = 0, cz = 0;
        for (BlockPos p : blocks) {
            if (p.getX() < min.getX() || (p.getX() == min.getX() && (p.getY() < min.getY() || (p.getY() == min.getY() && p.getZ() < min.getZ())))) {
                min = p;
            }
            cx += p.getX() + 0.5;
            cy += p.getY() + 0.5;
            cz += p.getZ() + 0.5;
        }
        cx /= blocks.size();
        cy /= blocks.size();
        cz /= blocks.size();
        blocks.sort((a, b) -> Long.compare(a.asLong(), b.asLong())); // (the same order every time: the same fish)
        Random random = new Random(min.asLong() * 31L + (ocean ? 7 : 0));
        int[] main = grow(in, blocks.get(blocks.size() / 2));
        for (int i = 0; i < 6; i++) { // (the roomiest box among a few tries)
            int[] b = grow(in, blocks.get(random.nextInt(blocks.size())));
            if (volume(b) > volume(main)) main = b;
        }
        // the animals: as many as the tank has room for
        List<Animal> animals = new ArrayList<>();
        float budget = Math.min(blocks.size() * 1.1F, 40.0F);
        int[] pool = ocean ? OCEAN : NORMAL;
        int[] count = new int[10];
        for (int tries = 0; tries < 200 && budget > 0.2F && animals.size() < 40; tries++) {
            int kind = pool[random.nextInt(pool.length)];
            if (COST[kind] > budget + 0.3F || (kind >= TURTLE && count[kind] >= 2) || (kind == AXOLOTL && count[kind] >= 3)) {
                continue;
            }
            int[] box = grow(in, blocks.get(random.nextInt(blocks.size())));
            if (!fits(box, kind)) {
                box = main;
                if (!fits(box, kind)) {
                    continue;
                }
            }
            int group = kind == COD || kind == TADPOLE ? 2 + random.nextInt(3) : 1; // (cod and tadpoles in little schools)
            float[] phase = new float[6];
            for (int k = 0; k < 6; k++) phase[k] = random.nextFloat() * 6.2832F;
            float speed = 0.8F + 0.4F * random.nextFloat();
            for (int g = 0; g < group && budget > 0.0F; g++) {
                float[] ph = phase.clone();
                ph[0] += g * 0.35F; // (each a little behind the one before)
                ph[1] += g * 0.5F;
                animals.add(animal(kind, box, random, ph, speed));
                budget -= COST[kind];
                count[kind]++;
            }
        }
        // the plants on the bottom
        List<Plant> plants = new ArrayList<>();
        for (BlockPos p : blocks) {
            if (in.contains(p.below()) || random.nextFloat() > 0.45F) {
                continue;
            }
            int above = 0;
            while (in.contains(p.above(above + 1)) && above < 24) above++;
            double x = p.getX() + 0.5 + (random.nextFloat() - 0.5) * 0.4, y = p.getY() + 2.0 / 16.0, z = p.getZ() + 0.5 + (random.nextFloat() - 0.5) * 0.4;
            int turn = random.nextInt(4);
            float r = random.nextFloat();
            if (above >= 1 && r < 0.18F) { // kelp up most of the water
                int tall = 1 + random.nextInt(above);
                for (int k = 0; k < tall; k++) {
                    plants.add(new Plant(k == tall - 1 ? Blocks.KELP.defaultBlockState() : Blocks.KELP_PLANT.defaultBlockState(), x, y + k * 0.9, z, 0.7F, 0.9F, turn));
                }
            } else if (ocean && r < 0.62F) { // coral
                Block[] corals = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL,
                        Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN};
                plants.add(new Plant(corals[random.nextInt(corals.length)].defaultBlockState(), x, y, z, 0.65F, 0.65F, turn));
            } else if (r < 0.82F) {
                plants.add(new Plant(Blocks.SEAGRASS.defaultBlockState(), x, y, z, 0.7F, 0.7F, turn));
            } else {
                plants.add(new Plant(Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(3)), x, y, z, 0.8F, 0.8F, turn));
            }
        }
        return new Tank(animals, plants, cx, cy, cz);
    }

    private static int volume(int[] b) {
        return (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1);
    }

    private static boolean fits(int[] b, int kind) {
        int wx = b[3] - b[0] + 1, h = b[4] - b[1] + 1, wz = b[5] - b[2] + 1;
        return Math.min(wx, wz) >= NEEDS[kind][0] && Math.max(wx, wz) >= NEEDS[kind][0] && h >= NEEDS[kind][1];
    }

    private static Animal animal(int kind, int[] b, Random random, float[] phase, float speed) {
        float reach = REACH[kind];
        double x0 = b[0] + 0.1 + reach, x1 = b[3] + 0.9 - reach;
        double z0 = b[2] + 0.1 + reach, z1 = b[5] + 0.9 - reach;
        double y0 = b[1] + 2.0 / 16.0 + 0.08, y1 = b[4] + 14.0 / 16.0 - 0.18; // (above the sand, under the surface)
        if (kind == AXOLOTL) {
            y1 = Math.min(y1, y0 + 0.35); // (along the bottom)
        } else if (kind == TURTLE || kind == DOLPHIN || kind == SQUID || kind == GLOW_SQUID) {
            y0 += 0.2;
            y1 -= kind == DOLPHIN ? 0.25 : 0.1;
        }
        if (x0 > x1) x0 = x1 = (x0 + x1) * 0.5;
        if (z0 > z1) z0 = z1 = (z0 + z1) * 0.5;
        if (y0 > y1) y0 = y1 = (y0 + y1) * 0.5;
        DyeColor[] dyes = DyeColor.values();
        return new Animal(kind, random.nextInt(5), random.nextInt(6), 0xFF000000 | dyes[random.nextInt(16)].getTextureDiffuseColor(),
                0xFF000000 | dyes[random.nextInt(16)].getTextureDiffuseColor(), SCALE[kind] * (0.85F + 0.3F * random.nextFloat()),
                x0, y0, z0, x1, y1, z1, phase, speed);
    }

    // ---- drawing ----

    private static void plant(ClientLevel level, Plant p, PoseStack poseStack, MultiBufferSource buffers) {
        poseStack.pushPose();
        poseStack.translate(p.x(), p.y(), p.z());
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F * p.turn()));
        poseStack.scale(p.width(), p.height(), p.width());
        poseStack.translate(-0.5, 0.0, -0.5);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(p.x(), p.y() + 0.3, p.z()));
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(p.state(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static void animal(ClientLevel level, Animal a, int index, float t, PoseStack poseStack, MultiBufferSource buffers) {
        // where it is on its wandering path (long lazy curves through its stretch of water), and which way it's going
        float[] ph = a.phase();
        float s = a.speed() * (a.kind() >= SQUID ? 0.6F : a.kind() == AXOLOTL ? 0.5F : 1.0F);
        double hx = (a.x1() - a.x0()) * 0.5, hy = (a.y1() - a.y0()) * 0.5, hz = (a.z1() - a.z0()) * 0.5;
        float fx = (float) (0.16 * s / Math.max(hx, 0.5)), fz = (float) (0.16 * s / Math.max(hz, 0.5)), fy = 0.11F * s;
        double ux = 0.62 * Math.sin(t * fx + ph[0]) + 0.38 * Math.sin(t * fx * 1.73 + ph[1]);
        double uz = 0.62 * Math.sin(t * fz * 1.11 + ph[2]) + 0.38 * Math.sin(t * fz * 1.91 + ph[3]);
        double uy = 0.7 * Math.sin(t * fy + ph[4]) + 0.3 * Math.sin(t * fy * 2.3 + ph[5]);
        double vx = (0.62 * fx * Math.cos(t * fx + ph[0]) + 0.38 * fx * 1.73 * Math.cos(t * fx * 1.73 + ph[1])) * hx;
        double vz = (0.62 * fz * 1.11 * Math.cos(t * fz * 1.11 + ph[2]) + 0.38 * fz * 1.91 * Math.cos(t * fz * 1.91 + ph[3])) * hz;
        double vy = (0.7 * fy * Math.cos(t * fy + ph[4]) + 0.3 * fy * 2.3 * Math.cos(t * fy * 2.3 + ph[5])) * hy;
        double x = a.x0() + hx + hx * ux, y = a.y0() + hy + hy * uy, z = a.z0() + hz + hz * uz;
        double flat = Math.sqrt(vx * vx + vz * vz);
        float yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
        float pitch = (float) Mth.clamp(Math.atan2(vy, Math.max(flat, 1e-4)) * 0.8, -0.4, 0.4);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
        float wag = (float) Math.sin(t * 7.0 + index * 1.3);
        switch (a.kind()) {
            case TROPICAL -> {
                boolean big = a.look() >= 3;
                ModelPart body = model(big ? "fishB" : "fishA"), pattern = model(big ? "fishBP" : "fishAP");
                rot(body, 0.45F * wag, "tail");
                rot(pattern, 0.45F * wag, "tail");
                draw(poseStack, buffers, body, big ? FISH_B : FISH_A, x, y, z, yaw, pitch, a.scale(), light, a.body());
                draw(poseStack, buffers, pattern, (big ? PATTERNS_B : PATTERNS_A)[a.pattern()], x, y, z, yaw, pitch, a.scale(), light, a.patternColour());
            }
            case COD -> {
                ModelPart m = model("cod");
                rot(m, 0.45F * wag, "tail_fin");
                draw(poseStack, buffers, m, COD_TEX, x, y, z, yaw, pitch, a.scale(), light, 0xFFFFFFFF);
            }
            case SALMON -> {
                ModelPart m = model("salmon");
                rot(m, 0.35F * wag, "body_back");
                draw(poseStack, buffers, m, SALMON_TEX, x, y, z, yaw, pitch, a.scale(), light, 0xFFFFFFFF);
            }
            case PUFFER -> {
                ModelPart m = model("puffer");
                if (m.hasChild("right_fin")) m.getChild("right_fin").zRot = -0.3F + 0.3F * wag;
                if (m.hasChild("left_fin")) m.getChild("left_fin").zRot = 0.3F - 0.3F * wag;
                draw(poseStack, buffers, m, PUFFER_TEX, x, y, z, yaw, pitch * 0.4F, a.scale(), light, 0xFFFFFFFF);
            }
            case AXOLOTL -> {
                ModelPart m = model("axolotl");
                ModelPart body = m.hasChild("body") ? m.getChild("body") : m;
                rot(body, 0.4F * wag, "tail");
                draw(poseStack, buffers, m, AXOLOTLS[a.look()], x, y, z, yaw, pitch * 0.5F, a.scale(), light, 0xFFFFFFFF);
            }
            case TADPOLE -> {
                ModelPart m = model("tadpole");
                rot(m, 0.6F * (float) Math.sin(t * 11.0 + index), "tail");
                draw(poseStack, buffers, m, TADPOLE_TEX, x, y, z, yaw, pitch, a.scale(), light, 0xFFFFFFFF);
            }
            case SQUID, GLOW_SQUID -> {
                ModelPart m = model(a.kind() == SQUID ? "squid" : "glow_squid");
                float beat = (float) (0.5 + 0.5 * Math.sin(t * 2.2 + index)); // (tentacles opening and closing as it swims)
                for (int k = 0; k < 8; k++) {
                    if (m.hasChild("tentacle" + k)) m.getChild("tentacle" + k).xRot = 0.15F + 0.75F * beat;
                }
                draw(poseStack, buffers, m, a.kind() == SQUID ? SQUID_TEX : GLOW_SQUID_TEX, x, y - 0.3 * a.scale(), z, yaw, 0.0F, a.scale(),
                        a.kind() == GLOW_SQUID ? LightTexture.FULL_BRIGHT : light, 0xFFFFFFFF);
            }
            case TURTLE -> {
                ModelPart m = model("turtle");
                float paddle = 0.5F * wag;
                if (m.hasChild("right_front_leg")) m.getChild("right_front_leg").zRot = paddle;
                if (m.hasChild("left_front_leg")) m.getChild("left_front_leg").zRot = -paddle;
                draw(poseStack, buffers, m, TURTLE_TEX, x, y - 0.15, z, yaw, pitch * 0.5F, a.scale(), light, 0xFFFFFFFF);
            }
            case DOLPHIN -> {
                ModelPart m = model("dolphin");
                ModelPart body = m.hasChild("body") ? m.getChild("body") : m;
                if (body.hasChild("tail")) body.getChild("tail").xRot = 0.25F * (float) Math.sin(t * 4.0 + index);
                draw(poseStack, buffers, m, DOLPHIN_TEX, x, y - 0.2, z, yaw, pitch, a.scale(), light, 0xFFFFFFFF);
            }
            default -> {
            }
        }
    }

    private static void rot(ModelPart m, float yRot, String name) {
        if (m.hasChild(name)) {
            m.getChild(name).yRot = yRot;
        }
    }

    /** One of the game's models at (x, y, z), facing that way, tipped up or down, at that size (as the game draws mobs). */
    private static void draw(PoseStack poseStack, MultiBufferSource buffers, ModelPart part, ResourceLocation texture, double x, double y, double z,
                             float yaw, float pitch, float scale, int light, int colour) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.XP.rotation(pitch));
        poseStack.scale(-scale, -scale, scale);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        part.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY, colour);
        poseStack.popPose();
    }
}
