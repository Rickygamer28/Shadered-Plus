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
    private static final int[] NORMAL_GAME = {TROPICAL, TROPICAL, COD, SALMON, PUFFER, AXOLOTL, TADPOLE};
    private static final int[] OCEAN_GAME = {TROPICAL, COD, COD, SALMON, PUFFER, SQUID, GLOW_SQUID, TURTLE, DOLPHIN};
    /** The aquariums' own creatures (blocky models: AquaModels): kind CUSTOM + their number there. */
    private static final int CUSTOM = 100;
    private static final String[] NORMAL_OWN = {"clownfish", "blue_tang", "yellow_tang", "neon_tetra", "angelfish", "goldfish", "betta", "guppy",
            "discus", "hermit_crab", "crab", "octopus", "shrimp"};
    private static final String[] OCEAN_OWN = {"shark", "tuna", "manta_ray", "grouper", "jellyfish", "sardine", "ocean_sunfish", "basking_shark",
            "crab", "stingray", "octopus", "lobster", "shrimp"};
    /** Per own creature: how much of a tank's room it takes, the room it needs (across, up), most in one tank, a school of how many. */
    private static final Map<String, float[]> OWN = new HashMap<>();

    static {
        Object[][] t = {
            {"clownfish", 0.8F, 1, 1, 8, 1}, {"blue_tang", 1.0F, 1, 1, 6, 1}, {"yellow_tang", 1.0F, 1, 1, 6, 1}, {"neon_tetra", 0.4F, 1, 1, 16, 5},
            {"angelfish", 1.2F, 1, 1, 4, 1}, {"goldfish", 1.0F, 1, 1, 8, 1}, {"betta", 0.9F, 1, 1, 2, 1}, {"guppy", 0.4F, 1, 1, 12, 4},
            {"discus", 1.2F, 1, 1, 4, 1}, {"shark", 14.0F, 5, 2, 3, 1}, {"tuna", 6.0F, 2, 1, 6, 2}, {"manta_ray", 10.0F, 4, 2, 3, 1},
            {"grouper", 8.0F, 3, 2, 3, 1}, {"jellyfish", 3.0F, 1, 2, 5, 1}, {"sardine", 0.5F, 1, 1, 24, 6}, {"ocean_sunfish", 16.0F, 4, 3, 2, 1},
            {"basking_shark", 30.0F, 6, 3, 2, 1}, {"hermit_crab", 0.6F, 1, 1, 3, 1}, {"crab", 0.7F, 1, 1, 3, 1}, {"stingray", 6.0F, 3, 1, 2, 1},
            {"octopus", 1.0F, 1, 1, 2, 1}, {"lobster", 3.0F, 2, 1, 2, 1}, {"shrimp", 0.3F, 1, 1, 4, 2}};
        for (Object[] r : t) {
            OWN.put((String) r[0], new float[] {(Float) r[1], (Integer) r[2], (Integer) r[3], (Integer) r[4], (Integer) r[5]});
        }
    }

    private static int own(String name) {
        return CUSTOM + java.util.Arrays.asList(AquaModels.NAMES).indexOf(name);
    }

    private static String ownName(int kind) {
        return AquaModels.NAMES[kind - CUSTOM];
    }

    private static float cost(int kind) {
        return kind >= CUSTOM ? OWN.get(ownName(kind))[0] : COST[kind];
    }

    private static int[] needs(int kind) {
        return kind >= CUSTOM ? new int[] {(int) OWN.get(ownName(kind))[1], (int) OWN.get(ownName(kind))[2]} : NEEDS[kind];
    }

    private static int cap(int kind) {
        return kind >= CUSTOM ? (int) OWN.get(ownName(kind))[3] : kind >= TURTLE ? 2 : kind == AXOLOTL ? 3 : 99;
    }

    private static int school(int kind, Random random) {
        if (kind >= CUSTOM) {
            int n = (int) OWN.get(ownName(kind))[4];
            return n <= 1 ? 1 : n - 1 + random.nextInt(3);
        }
        return kind == COD || kind == TADPOLE ? 2 + random.nextInt(3) : 1;
    }

    private static final Map<String, ModelPart> OWN_MODELS = new HashMap<>();

    private static ModelPart ownModel(String name) {
        return OWN_MODELS.computeIfAbsent(name, AquaModels::build);
    }

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

    /** frames: the dark frame round the tank's edges (like the aquariums'), 4 corners (x, y, z) a strip; light: at which block each is. */
    private record Tank(List<Animal> animals, List<Plant> plants, double cx, double cy, double cz, float[] frames, long[] frameLight) {}

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
                frames(level, tank, poseStack, buffers);
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
        Set<BlockPos> sandy = new java.util.HashSet<>(); // (the blocks with sand: on the real bottom, not over a tunnel)
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
                                    BlockPos bp = new BlockPos(baseX + x, baseY + y, baseZ + z);
                                    tiles.put(bp, s.getBlock());
                                    if (!s.getValue(VanillaAquariumBlock.DOWN)) sandy.add(bp);
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
            TANKS.add(tank(blocks, ((VanillaAquariumBlock) kind).ocean(), sandy));
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

    private static Tank tank(List<BlockPos> blocks, boolean ocean, Set<BlockPos> sandy) {
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
        List<BlockPos> sandList = new ArrayList<>();
        for (BlockPos p : blocks) if (sandy.contains(p)) sandList.add(p);
        // the animals: as many as the tank has room for
        List<Animal> animals = new ArrayList<>();
        float budget = Math.min(blocks.size() * 1.1F, ocean ? 120.0F : 40.0F);
        List<Integer> poolList = new ArrayList<>();
        for (int k : ocean ? OCEAN_GAME : NORMAL_GAME) poolList.add(k);
        for (String n : ocean ? OCEAN_OWN : NORMAL_OWN) poolList.add(own(n));
        Map<Integer, Integer> count = new HashMap<>();
        for (int tries = 0; tries < 300 && budget > 0.2F && animals.size() < 48; tries++) {
            int kind = poolList.get(random.nextInt(poolList.size()));
            if (cost(kind) > budget + 0.3F || count.getOrDefault(kind, 0) >= cap(kind)) {
                continue;
            }
            // (the ones on the bottom start from a block with sand: walking on the real bottom, not a tunnel's roof)
            boolean low = kind == AXOLOTL || (kind >= CUSTOM && !AquaModels.MOVES[kind - CUSTOM].equals("swim") && !AquaModels.MOVES[kind - CUSTOM].equals("drift"));
            List<BlockPos> seeds = low && !sandList.isEmpty() ? sandList : blocks;
            int[] box = grow(in, seeds.get(random.nextInt(seeds.size())));
            if (!fits(box, kind) && low) {
                continue;
            }
            if (!fits(box, kind)) {
                box = main;
                if (!fits(box, kind)) {
                    continue;
                }
            }
            int group = school(kind, random); // (cod, tadpoles, tetras, guppies, sardines... in little schools)
            float[] phase = new float[6];
            for (int k = 0; k < 6; k++) phase[k] = random.nextFloat() * 6.2832F;
            float speed = 0.8F + 0.4F * random.nextFloat();
            for (int g = 0; g < group && budget > 0.0F; g++) {
                float[] ph = phase.clone();
                ph[0] += g * 0.35F; // (each a little behind the one before)
                ph[1] += g * 0.5F;
                animals.add(animal(kind, box, random, ph, speed));
                budget -= cost(kind);
                count.merge(kind, 1, Integer::sum);
            }
        }
        // the plants on the bottom
        List<Plant> plants = new ArrayList<>();
        for (BlockPos p : blocks) {
            if (!sandy.contains(p) || random.nextFloat() > 0.45F) { // (plants only in the sand)
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
        // the frame round the tank's edges (as the aquariums have): along every edge of an outside face where the tank
        // ends (not where it carries on, flat or round a corner)
        List<float[]> strips = new ArrayList<>();
        List<Long> lights = new ArrayList<>();
        final float W = 1.0F / 16.0F, OUT = 0.0015F;
        for (BlockPos p : blocks) {
            for (Direction side : Direction.values()) {
                if (in.contains(p.relative(side))) {
                    continue;
                }
                for (Direction e : Direction.values()) {
                    if (e.getAxis() == side.getAxis()) {
                        continue;
                    }
                    BlockPos next = p.relative(e);
                    if (in.contains(next) && !in.contains(next.relative(side))) {
                        continue;
                    }
                    strips.add(strip(p, side, e, W, OUT));
                    lights.add(p.relative(side).asLong());
                }
            }
        }
        float[] frames = new float[strips.size() * 12];
        long[] frameLight = new long[strips.size()];
        for (int i = 0; i < strips.size(); i++) {
            System.arraycopy(strips.get(i), 0, frames, i * 12, 12);
            frameLight[i] = lights.get(i);
        }
        return new Tank(animals, plants, cx, cy, cz, frames, frameLight);
    }

    /** A strip of frame on the face of block p towards side, along its edge towards e, w wide (4 corners: x, y, z). */
    private static float[] strip(BlockPos p, Direction side, Direction e, float w, float out) {
        // the face's plane, and its two directions across
        Direction a = e, b = null;
        for (Direction d : Direction.values()) {
            if (d.getAxis() != side.getAxis() && d.getAxis() != e.getAxis() && d.getAxisDirection() == Direction.AxisDirection.POSITIVE) b = d;
        }
        float[] c = new float[12];
        float[][] corners = new float[4][];
        // in the face's own coordinates: along e (0 at the edge .. w in), along b (0..1)
        float[][] uv = {{0, 0}, {0, 1}, {w, 1}, {w, 0}};
        for (int i = 0; i < 4; i++) {
            float[] q = {0.5F, 0.5F, 0.5F};
            set(q, side, 0.5F + out);
            set(q, a, 0.5F - uv[i][0]);
            setAbs(q, b, uv[i][1]);
            corners[i] = q;
        }
        for (int i = 0; i < 4; i++) {
            c[i * 3] = p.getX() + corners[i][0];
            c[i * 3 + 1] = p.getY() + corners[i][1];
            c[i * 3 + 2] = p.getZ() + corners[i][2];
        }
        return c;
    }

    /** q's coordinate along d's axis = 0.5 + d's sign * k (k from the middle). */
    private static void set(float[] q, Direction d, float k) {
        q[d.getAxis().ordinal()] = 0.5F + d.getAxisDirection().getStep() * k;
    }

    private static void setAbs(float[] q, Direction d, float v) {
        q[d.getAxis().ordinal()] = v;
    }

    private static int volume(int[] b) {
        return (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1);
    }

    private static boolean fits(int[] b, int kind) {
        int wx = b[3] - b[0] + 1, h = b[4] - b[1] + 1, wz = b[5] - b[2] + 1;
        int[] need = needs(kind);
        return Math.min(wx, wz) >= need[0] && h >= need[1];
    }

    private static Animal animal(int kind, int[] b, Random random, float[] phase, float speed) {
        float reach = kind >= CUSTOM ? AquaModels.REACH[kind - CUSTOM] : REACH[kind];
        String move = kind >= CUSTOM ? AquaModels.MOVES[kind - CUSTOM] : "swim";
        double x0 = b[0] + 0.1 + reach, x1 = b[3] + 0.9 - reach;
        double z0 = b[2] + 0.1 + reach, z1 = b[5] + 0.9 - reach;
        double y0 = b[1] + 2.0 / 16.0 + 0.08, y1 = b[4] + 14.0 / 16.0 - 0.18; // (above the sand, under the surface)
        if (move.equals("crawl")) {
            y0 -= 0.08;
            y1 = y0; // (walking on the sand)
        } else if (move.equals("glide")) {
            y1 = Math.min(y1, y0 + 0.3); // (low over the bottom)
        } else if (kind == AXOLOTL) {
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
                0xFF000000 | dyes[random.nextInt(16)].getTextureDiffuseColor(),
                (kind >= CUSTOM ? AquaModels.SCALES[kind - CUSTOM] : SCALE[kind]) * (0.85F + 0.3F * random.nextFloat()),
                x0, y0, z0, x1, y1, z1, phase, speed);
    }

    // ---- drawing ----

    private static final ResourceLocation FRAME = ResourceLocation.withDefaultNamespace("block/white_concrete");
    private static final int FRAME_COLOUR = 0xFF17171A; // (the aquariums' frame: near black)

    private static void frames(ClientLevel level, Tank tank, PoseStack poseStack, MultiBufferSource buffers) {
        float[] f = tank.frames();
        if (f.length == 0) {
            return;
        }
        var sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(FRAME);
        float u = (sprite.getU0() + sprite.getU1()) * 0.5F, v = (sprite.getV0() + sprite.getV1()) * 0.5F;
        var vc = buffers.getBuffer(net.minecraft.client.renderer.Sheets.solidBlockSheet());
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < f.length / 12; i++) {
            int light = LevelRenderer.getLightColor(level, BlockPos.of(tank.frameLight()[i]));
            for (int k : new int[] {0, 1, 2, 3, 3, 2, 1, 0}) { // (both sides: whichever way it's wound)
                vc.addVertex(pose, f[i * 12 + k * 3], f[i * 12 + k * 3 + 1], f[i * 12 + k * 3 + 2]).setColor(FRAME_COLOUR).setUv(u, v)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
            }
        }
    }

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
        String move = a.kind() >= CUSTOM ? AquaModels.MOVES[a.kind() - CUSTOM] : "swim";
        float s = a.speed() * (move.equals("crawl") ? 0.35F : move.equals("drift") ? 0.3F : a.kind() >= CUSTOM && AquaModels.REACH[a.kind() - CUSTOM] > 0.4F ? 0.6F
                : a.kind() >= SQUID && a.kind() < CUSTOM ? 0.6F : a.kind() == AXOLOTL ? 0.5F : 1.0F);
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
        if (a.kind() >= CUSTOM) {
            own(a, move, index, t, x, y, z, yaw, pitch, light, poseStack, buffers);
            return;
        }
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

    /** One of the aquariums' own creatures (a blocky model), moving as it does. */
    private static void own(Animal a, String move, int index, float t, double x, double y, double z, float yaw, float pitch, int light,
                            PoseStack poseStack, MultiBufferSource buffers) {
        String name = ownName(a.kind());
        ModelPart m = ownModel(name);
        float beat = (float) Math.sin(t * 7.0 + index * 1.3);
        ModelPart body = m.hasChild("body") ? m.getChild("body") : m;
        if (body.hasChild("tail")) {
            ModelPart tail = body.getChild("tail");
            if (move.equals("crawl")) tail.xRot = 0.15F * beat;
            else tail.yRot = (a.scale() > 1.2F ? 0.25F : 0.45F) * (float) Math.sin(t * (a.scale() > 1.2F ? 3.0 : 7.0) + index);
        }
        if (body.hasChild("right_fin")) body.getChild("right_fin").yRot = -0.5F - 0.25F * beat;
        if (body.hasChild("left_fin")) body.getChild("left_fin").yRot = 0.5F + 0.25F * beat;
        float flap = (float) Math.sin(t * (move.equals("glide") ? 2.0 : 1.6) + index);
        if (body.hasChild("right_wing")) body.getChild("right_wing").zRot = 0.35F * flap;
        if (body.hasChild("left_wing")) body.getChild("left_wing").zRot = -0.35F * flap;
        for (int k = 0; k < 8; k++) {
            if (body.hasChild("tentacle" + k)) { // (jellyfish trailing, octopus curling)
                ModelPart te = body.getChild("tentacle" + k);
                if (move.equals("drift")) te.xRot = 0.2F * (float) Math.sin(t * 1.8 + k * 1.3 + index);
                else te.xRot = 0.15F * (float) Math.sin(t * 2.0 + k + index);
            }
            for (String side : new String[] {"right", "left"}) {
                if (body.hasChild(side + "_leg" + k)) { // (walking: the legs stepping in turn)
                    body.getChild(side + "_leg" + k).xRot = 0.35F * (float) Math.sin(t * 6.0 + k * 2.1 + (side.equals("left") ? 3.14 : 0));
                }
            }
        }
        for (String side : new String[] {"right", "left"}) {
            if (body.hasChild(side + "_claw")) body.getChild(side + "_claw").xRot = 0.15F * (float) Math.sin(t * 1.3 + index + (side.equals("left") ? 1.5 : 0));
        }
        if (move.equals("drift")) {
            pitch = 0.0F;
            yaw = (float) (t * 6.0 + index * 40.0); // (turning slowly as it pulses along)
        } else if (move.equals("crawl") || move.equals("glide")) {
            pitch = 0.0F;
        }
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/entity/aqua/" + name + ".png");
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.XP.rotation(pitch));
        float sc = a.scale();
        float squeeze = move.equals("drift") ? 1.0F + 0.08F * (float) Math.sin(t * 2.4 + index) : 1.0F; // (a jellyfish's bell pulsing)
        poseStack.scale(-sc / squeeze, -sc * squeeze, sc / squeeze);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        RenderType type = RenderType.entityTranslucent(texture); // (the fins a little see-through)
        m.render(poseStack, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
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
