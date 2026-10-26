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
            "hermit_crab", "crab", "stingray", "octopus", "lobster", "shrimp", null, null, null, null, null, null, null};
    private static final int PUFFERFISH = 7, SEA_TURTLE = 13, DOLPHIN = 16, MANTA = 12, JELLYFISH = 15, STINGRAY = 22;
    private static final int CLEANER = 35;
    private static final int COD = 26, SALMON = 27, TROPICAL = 28, TADPOLE = 29, SQUID = 30, GLOW_SQUID = 31, AXOLOTL = 32;
    private static final String[] AXOLOTL_TEX = {"lucy", "wild", "gold", "cyan", "blue"};
    /** The sixteen dye colours (a tropical fish's body and pattern colours). */
    private static final int[] DYES = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};

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
            Object[][] layers = {{"puffer", ModelLayers.PUFFERFISH_SMALL}, {"turtle", ModelLayers.TURTLE}, {"dolphin", ModelLayers.DOLPHIN},
                    {"cod", ModelLayers.COD}, {"salmon", ModelLayers.SALMON}, {"tropical_a", ModelLayers.TROPICAL_FISH_SMALL},
                    {"tropical_b", ModelLayers.TROPICAL_FISH_LARGE}, {"tropical_a_pattern", ModelLayers.TROPICAL_FISH_SMALL_PATTERN},
                    {"tropical_b_pattern", ModelLayers.TROPICAL_FISH_LARGE_PATTERN}, {"tadpole", ModelLayers.TADPOLE},
                    {"squid", ModelLayers.SQUID}, {"glow_squid", ModelLayers.GLOW_SQUID}, {"axolotl", ModelLayers.AXOLOTL}};
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
            Matrix4f view = new Matrix4f(event.getModelViewMatrix());
            // each tank in turn, the farthest first: its water, its fish and decorations into it, then its depth (so a
            // nearer tank's water covers a farther one's fish, as glass in front would)
            List<AquariumRenderer.VanillaJob> order = new ArrayList<>(jobs);
            order.sort((a, b) -> Double.compare(b.centre().distanceToSqr(cam), a.centre().distanceToSqr(cam)));
            for (AquariumRenderer.VanillaJob job : order) {
                AquariumRenderer.drawPicture(job, view);
                int lightKind = job.light();
                MultiBufferSource lit = lightKind == 0 ? buffers : new Tinted(buffers, LIGHT_TINT[lightKind]);
                // its fish and decorations, in its water's fog (as the aquariums': the water's colour, deeper the farther in)
                float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
                float[] fogColour = RenderSystem.getShaderFogColor().clone();
                com.mojang.blaze3d.shaders.FogShape fogShape = RenderSystem.getShaderFogShape();
                waterFog(job, cam);
                try {
                    for (int d = 0; d < job.decorCount(); d++) {
                        decoration(level, job, d, poseStack, lit);
                    }
                    for (int i = 0; i < job.fishCount(); i++) {
                        creature(level, job, i, night, poseStack, lit);
                    }
                    buffers.endBatch();
                } finally {
                    RenderSystem.setShaderFogStart(fogStart);
                    RenderSystem.setShaderFogEnd(fogEnd);
                    RenderSystem.setShaderFogColor(fogColour[0], fogColour[1], fogColour[2], fogColour[3]);
                    RenderSystem.setShaderFogShape(fogShape);
                }
                frame(job.frames(), poseStack, buffers); // (after its fish: over them where it's in front of them, as the glass's edge)
                buffers.endBatch();
                depth(job.faces(), poseStack);
            }
            jobs.clear();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /**
     * The aquariums' water fog for this tank: from where the view goes into it (the nearest of its box), fading to the
     * water's colour (the aquariums': lighter teal, the ocean's deeper blue; dimmed at night) as the aquariums do.
     */
    private static void waterFog(AquariumRenderer.VanillaJob job, Vec3 cam) {
        Vec3 mn = Vec3.atLowerCornerOf(job.min());
        Vec3 mx = job.centre().scale(2.0).subtract(mn);
        double dx = Math.max(Math.max(mn.x - cam.x, cam.x - mx.x), 0.0);
        double dy = Math.max(Math.max(mn.y - cam.y, cam.y - mx.y), 0.0);
        double dz = Math.max(Math.max(mn.z - cam.z, cam.z - mx.z), 0.0);
        float into = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float k = job.ocean() ? 0.22F : 0.3F; // (as thick as the aquariums' water)
        float r, g, b;
        int lightKind = job.light();
        if (lightKind == 1) { // (the tank's light: as the aquariums' water in it)
            r = 0.02F; g = 0.075F; b = 0.21F;
        } else if (lightKind == 4) {
            r = 0.01F; g = 0.045F; b = 0.155F;
        } else if (lightKind == 2) {
            r = 0.36F; g = 0.2F; b = 0.21F;
        } else if (lightKind == 3) {
            r = 0.14F; g = 0.11F; b = 0.38F;
        } else if (job.ocean()) {
            r = 0.025F; g = 0.24F; b = 0.41F;
        } else {
            r = 0.085F; g = 0.375F; b = 0.47F;
        }
        float night = (float) AquariumRenderer.night();
        r *= 1.0F - 0.76F * night;
        g *= 1.0F - 0.7F * night;
        b *= 1.0F - 0.5F * night;
        RenderSystem.setShaderFogStart(into);
        RenderSystem.setShaderFogEnd(into + 2.4F / k);
        RenderSystem.setShaderFogColor(r, g, b, 1.0F);
        RenderSystem.setShaderFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
    }

    // ---- the fish and sea creatures ----

    private static void creature(ClientLevel level, AquariumRenderer.VanillaJob job, int i, boolean night, PoseStack poseStack,
                                 MultiBufferSource buffers) {
        float[] f = job.fish();
        int o = i * 16;
        int kind = Math.round(f[o + 7]);
        if (kind < 0 || kind >= MODEL.length && kind != CLEANER) {
            return;
        }
        double x = job.min().getX() + f[o], y = job.min().getY() + f[o + 1], z = job.min().getZ() + f[o + 2];
        float size = f[o + 3];
        Vec3 fwd = new Vec3(f[o + 4], f[o + 5], f[o + 6]), up = new Vec3(f[o + 8], f[o + 9], f[o + 10]);
        float wave = f[o + 11], beat = f[o + 12], bend = f[o + 13], squeeze = f[o + 14], walk = f[o + 15];
        float[] sp = AquariumRenderer.species(kind);
        boolean crawler = AquariumRenderer.crawler(kind);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
        if (kind == JELLYFISH && night || kind == GLOW_SQUID) {
            light = LightTexture.FULL_BRIGHT; // (glowing in the dark)
        }
        // how big: as long as the aquariums' own (its body and tail), as wide for the rays, the bell for a jellyfish
        float length = crawler ? 2.0F * Math.max(sp[0], sp[2]) * size : (2.0F * sp[0] + 1.6F * sp[3]) * size;
        String name = kind == CLEANER ? "shrimp" : MODEL[kind];
        if (kind >= COD && kind != CLEANER) {
            mob(job, i, kind, x, y, z, fwd, up, size, sp, wave, beat, squeeze, walk, light, poseStack, buffers);
            return;
        }
        if (name == null) { // the game's own: pufferfish, sea turtle, dolphin
            ModelPart m;
            ResourceLocation tex;
            float modelLength;
            if (kind == PUFFERFISH) {
                m = game("puffer");
                tex = PUFFER_TEX;
                modelLength = 4.0F / 16.0F;
                float tt = LavaLampRenderer.seconds() * 6.0F + i; // (its little fins whirring)
                if (m.hasChild("right_fin")) m.getChild("right_fin").zRot = -0.4F + 0.4F * Mth.sin(tt);
                if (m.hasChild("left_fin")) m.getChild("left_fin").zRot = 0.4F - 0.4F * Mth.sin(tt);
                if (m.hasChild("tail_fin")) m.getChild("tail_fin").yRot = 0.4F * Mth.sin(wave);
            } else if (kind == SEA_TURTLE) {
                m = game("turtle");
                tex = TURTLE_TEX;
                modelLength = 1.5F;
                // (its front flippers sweeping down and back together, the back ones steering)
                float stroke = LavaLampRenderer.seconds() * 1.4F + i;
                float paddle = 0.6F * Mth.sin(stroke);
                if (m.hasChild("right_front_leg")) { m.getChild("right_front_leg").zRot = paddle; m.getChild("right_front_leg").yRot = 0.4F * Mth.cos(stroke); }
                if (m.hasChild("left_front_leg")) { m.getChild("left_front_leg").zRot = -paddle; m.getChild("left_front_leg").yRot = -0.4F * Mth.cos(stroke); }
                if (m.hasChild("right_hind_leg")) m.getChild("right_hind_leg").yRot = 0.25F * Mth.sin(stroke * 0.5F + 1.0F);
                if (m.hasChild("left_hind_leg")) m.getChild("left_hind_leg").yRot = -0.25F * Mth.sin(stroke * 0.5F + 1.0F);
            } else {
                m = game("dolphin");
                tex = DOLPHIN_TEX;
                modelLength = 1.9F;
                ModelPart body = m.hasChild("body") ? m.getChild("body") : m;
                // (its flukes beating up and down, the body following through)
                body.xRot = 0.06F * Mth.sin(wave - 0.6F) * beat;
                if (body.hasChild("tail")) {
                    ModelPart tail = body.getChild("tail");
                    tail.xRot = 0.35F * Mth.sin(wave) * beat;
                    if (tail.hasChild("tail_fin")) tail.getChild("tail_fin").xRot = 0.3F * Mth.sin(wave - 0.8F) * beat;
                }
            }
            float scale = length / modelLength;
            draw(poseStack, buffers, m, RenderType.entityCutoutNoCull(tex), x, y, z, fwd, up, scale, 1.0F, light,
                    kind == SEA_TURTLE ? 0.15 : kind == DOLPHIN ? 0.3 : 0.08, 0.0, 0xFFFFFFFF);
            return;
        }
        int idx = ownIndex(name);
        ModelPart m = own(name);
        ModelPart body = m.hasChild("body") ? m.getChild("body") : m;
        animate(body, kind, crawler, sp, wave, beat, bend, squeeze, walk, i);
        float modelLength = (kind == MANTA || kind == STINGRAY || kind == JELLYFISH ? AquaModels.WIDTH_PX[idx] : AquaModels.LENGTH_PX[idx]) / 16.0F;
        if (kind == MANTA || kind == STINGRAY) {
            length = 2.0F * sp[2] * size; // (the rays: as wide as the aquariums' own)
        } else if (kind == JELLYFISH) {
            length = 2.0F * sp[0] * size;
        }
        float scale = length / Math.max(modelLength, 0.05F);
        float squash = kind == JELLYFISH ? 1.0F - 0.25F * squeeze : 1.0F; // (a jellyfish's bell squeezing)
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/entity/aqua/" + (kind == CLEANER ? "cleaner_shrimp" : name) + ".png");
        draw(poseStack, buffers, m, RenderType.entityCutoutNoCull(texture), x, y, z, fwd, up, scale, squash, light, 0.0, 0.0, 0xFFFFFFFF);
    }

    /** The game's own models for its water mobs: cod, salmon, tropical fish (its two shapes, six patterns, sixteen colours), tadpole, squid, glow squid and axolotl (its five colours). */
    private static void mob(AquariumRenderer.VanillaJob job, int i, int kind, double x, double y, double z, Vec3 fwd, Vec3 up, float size,
                            float[] sp, float wave, float beat, float squeeze, float walk, int light, PoseStack poseStack, MultiBufferSource buffers) {
        Random rnd = new Random(job.min().asLong() * 31L + i * 7919L + kind);
        float t = LavaLampRenderer.seconds() + i * 1.37F;
        float a = 0.35F + 0.65F * beat;
        float length = AquariumRenderer.crawler(kind) ? 2.0F * Math.max(sp[0], sp[2]) * size : (2.0F * sp[0] + 1.6F * sp[3]) * size;
        switch (kind) {
            case COD -> {
                ModelPart m = game("cod");
                tail(m, "tail_fin", 0.55F * a * Mth.sin(wave - 1.2F));
                fins(m, t, 0.25F);
                draw(poseStack, buffers, m, RenderType.entityCutoutNoCull(mc("textures/entity/fish/cod.png")), x, y, z, fwd, up,
                        length / (15.0F / 16.0F), 1.0F, light, 0.125, 3.5, 0xFFFFFFFF);
            }
            case SALMON -> {
                ModelPart m = game("salmon");
                if (m.hasChild("body_back")) {
                    ModelPart back = m.getChild("body_back");
                    back.yRot = 0.32F * a * Mth.sin(wave - 0.9F);
                    tail(back, "back_fin", 0.4F * a * Mth.sin(wave - 1.8F));
                }
                m.yRot = -0.06F * a * Mth.sin(wave);
                fins(m, t, 0.25F);
                draw(poseStack, buffers, m, RenderType.entityCutoutNoCull(mc("textures/entity/fish/salmon.png")), x, y, z, fwd, up,
                        length / (25.0F / 16.0F), 1.0F, light, 0.25, 9.5, 0xFFFFFFFF);
            }
            case TROPICAL -> {
                boolean big = rnd.nextBoolean();
                int pattern = 1 + rnd.nextInt(6), bodyC = rnd.nextInt(16), patC = rnd.nextInt(16);
                if (patC == bodyC) patC = (patC + 5) % 16;
                String shape = big ? "b" : "a";
                float scale = length / ((big ? 11.0F : 10.0F) / 16.0F);
                double lift = big ? 0.31 : 0.125, centre = big ? 2.5 : 2.0;
                for (String layer : new String[] {"tropical_" + shape, "tropical_" + shape + "_pattern"}) {
                    ModelPart m = game(layer);
                    tail(m, "tail", 0.6F * a * Mth.sin(wave - 1.0F));
                    fins(m, t, 0.3F);
                    boolean pat = layer.endsWith("pattern");
                    ResourceLocation tex = mc("textures/entity/fish/tropical_" + shape + (pat ? "_pattern_" + pattern : "") + ".png");
                    draw(poseStack, buffers, m, RenderType.entityCutoutNoCull(tex), x, y, z, fwd, up, scale, 1.0F, light, lift, centre,
                            0xFF000000 | DYES[pat ? patC : bodyC]);
                }
            }
            case TADPOLE -> {
                ModelPart m = game("tadpole");
                tail(m, "tail", 0.75F * a * Mth.sin(wave * 1.4F - 1.0F));
                m.yRot = -0.12F * a * Mth.sin(wave * 1.4F);
                draw(poseStack, buffers, m, RenderType.entityCutoutNoCull(mc("textures/entity/tadpole/tadpole.png")), x, y, z, fwd, up,
                        length / (10.0F / 16.0F), 1.0F, light, 0.125, 2.0, 0xFFFFFFFF);
            }
            case SQUID, GLOW_SQUID -> {
                // standing on end: its body (the mantle) forward, the tentacles trailing behind; squeezed as it surges,
                // the tentacles drawn together, then spreading and rippling as it glides
                ModelPart m = game(kind == SQUID ? "squid" : "glow_squid");
                if (m.hasChild("body")) {
                    ModelPart body = m.getChild("body");
                    body.xScale = body.zScale = 1.0F - 0.14F * squeeze;
                    body.yScale = 1.0F + 0.06F * squeeze;
                }
                for (int k = 0; k < 8; k++) {
                    if (!m.hasChild("tentacle" + k)) continue;
                    m.getChild("tentacle" + k).xRot = 0.08F + 0.62F * (1.0F - squeeze) + 0.08F * Mth.sin(t * 2.2F + k * 0.8F);
                }
                float scale = length / (33.0F / 16.0F);
                Vec3 f = fwd.lengthSqr() < 1.0e-8 ? new Vec3(1, 0, 0) : fwd.normalize();
                Vec3 o = f.scale(-0.47 * scale); // (the model's middle on the squid's)
                String tex = kind == SQUID ? "textures/entity/squid/squid.png" : "textures/entity/squid/glow_squid.png";
                draw(poseStack, buffers, m, RenderType.entityCutoutNoCull(mc(tex)), x + o.x, y + o.y, z + o.z, up, f, scale, 1.0F, light,
                        0.0, 0.0, 0xFFFFFFFF);
            }
            default -> { // the axolotl: waddling on the sand, its body swaying, its tail sweeping, its gills waving
                int v = rnd.nextInt(100);
                String colour = AXOLOTL_TEX[v < 30 ? 0 : v < 55 ? 1 : v < 75 ? 2 : v < 98 ? 3 : 4];
                ModelPart m = game("axolotl");
                ModelPart body = m.hasChild("body") ? m.getChild("body") : m;
                body.yRot = 0.12F * Mth.sin(walk);
                for (String leg : new String[] {"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"}) {
                    if (!body.hasChild(leg)) continue;
                    ModelPart l = body.getChild(leg);
                    boolean right = leg.startsWith("right");
                    float ph = walk + (leg.contains("front") == right ? 0.0F : 3.14F);
                    l.xRot = 0.7F * Mth.sin(ph);
                    l.yRot = 0.0F;
                    l.zRot = (right ? 1.0F : -1.0F) * (0.95F - 0.25F * Math.max(Mth.cos(ph), 0.0F));
                }
                if (body.hasChild("tail")) body.getChild("tail").yRot = -0.2F * Mth.sin(walk) + 0.25F * Mth.sin(wave * 0.6F);
                if (body.hasChild("head")) {
                    ModelPart head = body.getChild("head");
                    head.yRot = -0.1F * Mth.sin(walk);
                    if (head.hasChild("top_gills")) head.getChild("top_gills").xRot = -0.15F + 0.2F * Mth.sin(t * 2.4F);
                    if (head.hasChild("left_gills")) head.getChild("left_gills").yRot = 0.3F + 0.25F * Mth.sin(t * 2.4F + 1.0F);
                    if (head.hasChild("right_gills")) head.getChild("right_gills").yRot = -0.3F - 0.25F * Mth.sin(t * 2.4F + 1.0F);
                }
                draw(poseStack, buffers, m, RenderType.entityCutoutNoCull(mc("textures/entity/axolotl/axolotl_" + colour + ".png")), x, y, z,
                        fwd, up, length / (27.0F / 16.0F), 1.0F, light, -0.04, 4.5, 0xFFFFFFFF);
            }
        }
    }

    /** A game model's tail part, swinging. */
    private static void tail(ModelPart parent, String name, float yRot) {
        if (parent.hasChild(name)) parent.getChild(name).yRot = yRot;
    }

    /** A game fish's side fins, fluttering (round their own resting angle). */
    private static void fins(ModelPart m, float t, float amount) {
        for (String side : new String[] {"right_fin", "left_fin"}) {
            if (!m.hasChild(side)) continue;
            ModelPart fin = m.getChild(side);
            float sg = side.startsWith("right") ? 1.0F : -1.0F;
            fin.zRot = fin.getInitialPose().zRot + sg * amount * Mth.sin(t * 4.0F + (sg > 0 ? 0.0F : 1.3F));
        }
    }

    /**
     * Its moving parts, from the aquariums' own swimming (how far round its tail beat is, how hard, how it's turning;
     * a jellyfish's squeeze; how far a bottom creature has walked).
     */
    private static void animate(ModelPart body, int kind, boolean crawler, float[] sp, float wave, float beat, float bend, float squeeze,
                                float walk, int index) {
        float t = LavaLampRenderer.seconds() + index * 1.37F;
        // a fish: a wave running down its body to the tail, each part after the one before and swinging more; turning,
        // the whole body curving round the turn
        ModelPart mid = body.hasChild("mid") ? body.getChild("mid") : null;
        ModelPart rear = mid != null && mid.hasChild("rear") ? mid.getChild("rear") : null;
        ModelPart tailHolder = rear != null ? rear : body;
        float big = sp[0] > 0.6F ? 0.55F : sp[0] > 0.3F ? 0.75F : 1.0F; // (the big ones: a slower, gentler sweep)
        float a = (0.35F + 0.65F * beat) * big;
        if (!crawler && kind != JELLYFISH) {
            body.yRot = -0.07F * a * Mth.sin(wave);
            if (mid != null) mid.yRot = 0.16F * a * Mth.sin(wave - 0.8F) + bend * 0.1F;
            if (rear != null) rear.yRot = 0.28F * a * Mth.sin(wave - 1.6F) + bend * 0.12F;
            if (tailHolder.hasChild("tail")) tailHolder.getChild("tail").yRot = 0.5F * a * Mth.sin(wave - 2.4F) + bend * 0.12F;
            // the side fins: rowing gently, out and back, the two a little out of step; spread more as it slows
            for (String side : new String[] {"right_fin", "left_fin"}) {
                if (!body.hasChild(side)) continue;
                float sg = side.startsWith("right") ? -1.0F : 1.0F;
                ModelPart fin = body.getChild(side);
                float row = Mth.sin(t * 3.2F + (sg > 0 ? 1.1F : 0.0F));
                fin.yRot = fin.getInitialPose().yRot + sg * (0.25F * (1.0F - beat) + 0.3F * row);
                fin.zRot = sg * -0.2F * Mth.sin(t * 3.2F + (sg > 0 ? 2.2F : 1.1F));
            }
            // the sunfish's tall fins sculling from side to side (how it swims)
            for (String fin : new String[] {"dorsal_fin", "anal_fin"}) {
                if (body.hasChild(fin)) body.getChild(fin).zRot = 0.45F * Mth.sin(wave * 0.5F + (fin.startsWith("anal") ? 3.14F : 0.0F));
            }
        }
        // the rays' wings: a slow, deep flap, the tips following after (a ripple out along the wing)
        boolean stingray = kind == STINGRAY;
        float flap = stingray ? t * 1.6F : t * 1.1F, reach = stingray ? 0.16F : 0.42F;
        for (String side : new String[] {"right_wing", "left_wing"}) {
            if (!body.hasChild(side)) continue;
            float sg = side.startsWith("right") ? 1.0F : -1.0F;
            ModelPart wing = body.getChild(side);
            wing.zRot = sg * reach * Mth.sin(flap);
            if (wing.hasChild(side + "_tip")) wing.getChild(side + "_tip").zRot = sg * reach * 1.1F * Mth.sin(flap - 1.0F);
        }
        if (!crawler && kind != JELLYFISH && body.hasChild("tail") && (kind == MANTA || stingray)) {
            body.getChild("tail").yRot = 0.15F * Mth.sin(t * 1.3F); // (the whip of a tail trailing, swaying)
        }
        // a jellyfish: its tentacles and arms trailing in slow waves, pulled in with each squeeze
        if (kind == JELLYFISH) {
            for (int k = 0; k < 8; k++) {
                for (String n : new String[] {"tentacle" + k, "arm" + k}) {
                    if (!body.hasChild(n)) continue;
                    ModelPart te = body.getChild(n);
                    float slow = n.startsWith("arm") ? 0.7F : 1.0F;
                    te.xRot = te.getInitialPose().xRot + 0.22F * Mth.sin(t * 1.3F * slow + k * 0.9F) - 0.35F * squeeze;
                    te.zRot = te.getInitialPose().zRot + 0.18F * Mth.cos(t * 1.05F * slow + k * 1.3F);
                }
            }
        }
        if (crawler) {
            // walking: the legs in two sets stepping in turn (each lifted as it swings forward, pushing back on the sand)
            for (int k = 0; k < 8; k++) {
                for (String side : new String[] {"right", "left"}) {
                    String n = side + "_leg" + k;
                    if (!body.hasChild(n)) continue;
                    ModelPart leg = body.getChild(n);
                    float ph = walk + (k % 2) * 3.14F + (side.equals("left") ? 3.14F : 0.0F);
                    float lift = Math.max(Mth.sin(ph), 0.0F);
                    float out = Math.signum(leg.getInitialPose().zRot);
                    leg.yRot = leg.getInitialPose().yRot + 0.35F * Mth.cos(ph);
                    leg.zRot = leg.getInitialPose().zRot + out * 0.3F * lift;
                }
            }
            // the claws: held up, opening and closing now and then
            for (String side : new String[] {"right", "left"}) {
                if (!body.hasChild(side + "_claw")) continue;
                ModelPart claw = body.getChild(side + "_claw");
                float pinch = Mth.sin(t * 2.3F + (side.equals("left") ? 1.7F : 0.0F));
                claw.xRot = claw.getInitialPose().xRot - 0.12F - 0.1F * Math.max(pinch, 0.0F) + 0.05F * Mth.sin(walk * 0.5F);
                claw.yRot = claw.getInitialPose().yRot + (side.equals("left") ? 0.08F : -0.08F) * Mth.sin(t * 0.9F);
            }
            // an octopus's arms curling and uncurling as it creeps, the tips most
            for (int k = 0; k < 8; k++) {
                if (!body.hasChild("tentacle" + k)) continue;
                ModelPart arm = body.getChild("tentacle" + k);
                float ph = walk * 0.6F + k * 0.8F;
                arm.xRot = arm.getInitialPose().xRot + 0.15F * Mth.sin(ph);
                if (arm.hasChild("tip" + k)) {
                    ModelPart tip = arm.getChild("tip" + k);
                    tip.xRot = tip.getInitialPose().xRot + 0.35F * Mth.sin(ph - 0.9F) + 0.1F * Mth.sin(t * 1.5F + k);
                    tip.yRot = tip.getInitialPose().yRot + 0.2F * Mth.sin(t * 0.8F + k * 1.7F);
                }
            }
            // a lobster's or shrimp's tail: tucking and flicking now and then
            if (body.hasChild("tail")) {
                ModelPart tail = body.getChild("tail");
                float flick = (float) Math.pow(Math.max(Mth.sin(t * 0.7F), 0.0F), 8.0);
                tail.xRot = tail.getInitialPose().xRot + 0.1F * Mth.sin(walk * 0.5F) + 0.45F * flick;
            }
        }
    }

    /**
     * A model at (x, y, z) facing fwd with its top towards up (as the aquariums' fish lean into their turns), at that
     * size (as the game draws mobs). lift: how far (in the model's blocks) its middle is above its origin; centre: how
     * far (in its pixels) its middle is behind its origin; colour: its tint.
     */
    private static void draw(PoseStack poseStack, MultiBufferSource buffers, ModelPart part, RenderType type, double x, double y, double z,
                             Vec3 fwd, Vec3 up, float scale, float squash, int light, double lift, double centre, int colour) {
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
        poseStack.translate(0.0F, -1.5F, (float) (-centre / 16.0)); // (its middle, along it, on the fish's)
        part.render(poseStack, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, underwater(colour));
        poseStack.popPose();
    }

    /** Each light's colour on what's in the tank (daylight, moonlight, sunset, reef: as the aquariums' own). */
    private static final float[][] LIGHT_TINT = {{1.0F, 1.0F, 1.0F}, {0.42F, 0.55F, 1.0F}, {1.0F, 0.8F, 0.62F}, {0.86F, 0.76F, 1.0F}, {0.32F, 0.45F, 0.95F}};

    /** Buffers whose vertex colours are all tinted (the tank's light on its fish and decorations, blocks included). */
    private record Tinted(MultiBufferSource inner, float[] tint) implements MultiBufferSource {
        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType type) {
            return new TintedConsumer(inner.getBuffer(type), tint);
        }
    }

    private record TintedConsumer(com.mojang.blaze3d.vertex.VertexConsumer inner, float[] tint) implements com.mojang.blaze3d.vertex.VertexConsumer {
        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor((int) (r * tint[0]), (int) (g * tint[1]), (int) (b * tint[2]), a);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
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
        float t = LavaLampRenderer.seconds();
        List<Piece> pieces = new ArrayList<>();
        switch (type) {
            case 0 -> { // rocks: a big one and two small ones by it (as the aquariums' own: about 0.45 across)
                Block[] stones = {Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE, Blocks.STONE, Blocks.TUFF};
                Block stone = stones[random.nextInt(stones.length)];
                double ca = Math.cos(Math.toRadians(yaw)), sa = Math.sin(Math.toRadians(yaw));
                double[][] at = {{0, 0, 0.3, 0.22}, {0.14, 0.07, 0.17, 0.13}, {-0.12, -0.08, 0.16, 0.12}};
                for (double[] r : at) {
                    double ox = (r[0] * ca - r[1] * sa) * sz, oz = (r[0] * sa + r[1] * ca) * sz;
                    pieces.add(new Piece(random.nextFloat() < 0.4F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : stone.defaultBlockState(),
                            x + ox, y - 0.02 * sz, z + oz, (float) r[2] * sz, (float) r[3] * sz, (float) r[2] * sz * 0.85F,
                            Axis.YP.rotationDegrees(yaw + random.nextFloat() * 40.0F)));
                }
            }
            case 3 -> castle(pieces, x, y, z, turn, 0.11F * sz);
            case 4 -> { // coral: a coral block with a coral on it
                Block[] blocks = {Blocks.BRAIN_CORAL_BLOCK, Blocks.TUBE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK};
                Block[] fans = {Blocks.BRAIN_CORAL, Blocks.TUBE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL, Blocks.BUBBLE_CORAL};
                int c = random.nextInt(blocks.length);
                pieces.add(new Piece(blocks[c].defaultBlockState(), x, y - 0.01 * sz, z, 0.15F * sz, 0.1F * sz, turn));
                pieces.add(new Piece(fans[c].defaultBlockState(), x, y + 0.09 * sz, z, 0.24F * sz, 0.24F * sz, turn));
            }
            case 5 -> pieces.add(new Piece(Blocks.CHEST.defaultBlockState(), x, y - 0.01 * sz, z, 0.32F * sz, 0.27F * sz, 0.32F * sz,
                    Axis.YP.rotationDegrees(180.0F - yaw)));
            case 7 -> pieces.add(new Piece(Blocks.DECORATED_POT.defaultBlockState(), x, y - 0.01 * sz, z, 0.3F * sz, 0.33F * sz, 0.3F * sz,
                    Axis.YP.rotationDegrees(180.0F - yaw)));
            case 8 -> ship(pieces, x, y, z, yaw, 0.1F * sz);
            case 14 -> { // an air stone: a little round stone, a stream of bubbles rising from it to the surface
                pieces.add(new Piece(Blocks.POLISHED_TUFF.defaultBlockState(), x, y - 0.005, z, 0.13F, 0.045F, 0.13F, Axis.YP.rotationDegrees(yaw)));
                pieces.add(new Piece(Blocks.POLISHED_TUFF.defaultBlockState(), x, y + 0.04, z, 0.1F, 0.012F, 0.1F, Axis.YP.rotationDegrees(yaw + 45.0F)));
                bubbles(level, job, d, x, y + 0.05, z, poseStack, buffers);
            }
            case 12 -> { // seagrass: a little clump (as tall as the aquariums' own), swaying
                int n = 3 + random.nextInt(2);
                for (int i = 0; i < n; i++) {
                    float s = (0.3F + random.nextFloat() * 0.25F) * sz;
                    float sway = 0.14F * Mth.sin(t * 1.2F + i * 1.7F + d);
                    double bx = x + (random.nextFloat() - 0.5) * 0.16 * sz, bz = z + (random.nextFloat() - 0.5) * 0.16 * sz;
                    float spin = random.nextFloat() * 360.0F;
                    // kept inside the tank: its leaves (out to the corners of its cross, leaning as it sways) short of the
                    // glass, its top under the water's surface
                    double side = room(job, bx, y, bz), up = roomAbove(job, bx, y, bz);
                    s = (float) Math.max(0.05, Math.min(s, Math.min((side - 0.03) / (0.8 * 0.71 + 0.15), up - 0.04)));
                    pieces.add(new Piece(Blocks.SEAGRASS.defaultBlockState(), bx, y, bz, s * 0.8F, s, s * 0.8F,
                            Axis.YP.rotationDegrees(spin).mul(Axis.ZP.rotation(sway))));
                }
            }
            case 13 -> { // kelp: up most of the water (its size its height; never on up through a tunnel's air), swaying more higher up
                BlockPos base = BlockPos.containing(x, y, z);
                int room = 0;
                while (room < 24 && job.blocks().contains(base.above(room + 1))) room++;
                float height = Math.min(sz, room + 0.75F);
                int n = Math.max(1, Math.round(height / 0.9F));
                double px = x, py = y, pz = z;
                double dx = Math.cos(Math.toRadians(yaw)), dz = Math.sin(Math.toRadians(yaw));
                for (int k = 0; k < n; k++) {
                    float lean = (k + 1) * 0.045F * Mth.sin(t * 0.6F + d) + 0.03F * Mth.sin(t * 1.1F + k);
                    Quaternionf rot = Axis.YP.rotationDegrees(90.0F - yaw).mul(Axis.XP.rotation(lean));
                    pieces.add(new Piece(k == n - 1 ? Blocks.KELP.defaultBlockState() : Blocks.KELP_PLANT.defaultBlockState(), px, py, pz, 0.7F, 0.9F, 0.7F, rot));
                    px += dx * Math.sin(lean) * 0.9;
                    pz += dz * Math.sin(lean) * 0.9;
                    py += Math.cos(lean) * 0.9;
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

    private static final ResourceLocation BUBBLE = mc("textures/particle/bubble.png");

    /** An air stone's stream of bubbles: rising, wobbling more as they go, a little bigger near the top, up to the surface. */
    private static void bubbles(ClientLevel level, AquariumRenderer.VanillaJob job, int d, double x, double y, double z, PoseStack poseStack,
                                MultiBufferSource buffers) {
        double top = roomAbove(job, x, y, z) - 0.06;
        if (top < 0.1) return;
        float t = LavaLampRenderer.seconds();
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
        com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(BUBBLE));
        Quaternionf face = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        Random r = new Random(job.min().asLong() * 13L + d);
        for (int k = 0; k < 16; k++) {
            double speed = 0.5 + 0.18 * r.nextDouble();
            double rise = ((t * speed + r.nextDouble() * top + k * 0.11) % top + top) % top;
            double up = rise / top;
            double wob = 0.012 + 0.035 * up;
            double bx = x + wob * Math.sin(t * 4.0 + k * 1.7 + rise * 6.0), bz = z + wob * Math.cos(t * 3.4 + k + rise * 5.0);
            float size = (float) ((0.022 + 0.016 * r.nextDouble()) * (1.0 + 0.5 * up));
            poseStack.pushPose();
            poseStack.translate(bx, y + rise, bz);
            poseStack.mulPose(face);
            poseStack.scale(size, size, size);
            PoseStack.Pose pose = poseStack.last();
            float[][] corners = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
            for (float[] c : corners) {
                vc.addVertex(pose, c[0], c[1], 0.0F).setColor(underwater(0xFFFFFFFF)).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
            }
            poseStack.popPose();
        }
    }

    /** How far from (x, z) to the tank's glass, round it at this height (in the block it's in, and the ones beside it). */
    private static double room(AquariumRenderer.VanillaJob job, double x, double y, double z) {
        BlockPos at = BlockPos.containing(x, y, z);
        double fx = x - at.getX(), fz = z - at.getZ();
        double r = 1.0;
        if (!job.blocks().contains(at.west())) r = Math.min(r, fx);
        if (!job.blocks().contains(at.east())) r = Math.min(r, 1.0 - fx);
        if (!job.blocks().contains(at.north())) r = Math.min(r, fz);
        if (!job.blocks().contains(at.south())) r = Math.min(r, 1.0 - fz);
        // (and the corners: a diagonal neighbour missing)
        for (int i = -1; i <= 1; i += 2) {
            for (int k = -1; k <= 1; k += 2) {
                if (!job.blocks().contains(at.offset(i, 0, k))) {
                    double ex = i < 0 ? fx : 1.0 - fx, ez = k < 0 ? fz : 1.0 - fz;
                    r = Math.min(r, Math.max(ex, ez));
                }
            }
        }
        return r;
    }

    /** How far up from (x, y, z) the water goes (to the tank's top, or a tunnel's air). */
    private static double roomAbove(AquariumRenderer.VanillaJob job, double x, double y, double z) {
        BlockPos at = BlockPos.containing(x, y, z);
        int n = 0;
        while (n < 24 && job.blocks().contains(at.above(n + 1))) n++;
        return at.getY() + 1 + n - y;
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
        for (int h = 2; h <= 4; h++) put.accept(rail, new double[] {0, h, 1, 1, 1, 1});
        put.accept(Blocks.STRIPPED_OAK_LOG.defaultBlockState(), new double[] {0, 4.4, 1, 3.4, 0.3, 0.3});
        put.accept(Blocks.WHITE_WOOL.defaultBlockState(), new double[] {0, 3.4, 1.25, 2.8, 1.0, 0.1});
        put.accept(Blocks.WHITE_WOOL.defaultBlockState(), new double[] {-0.6, 2.6, 1.25, 1.6, 0.8, 0.1});
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

    private static final ResourceLocation FRAME = ResourceLocation.withDefaultNamespace("block/white_concrete");

    /** The dark frame round a tank's edges (the aquariums' near-black), both sides. */
    private static void frame(float[] f, PoseStack poseStack, MultiBufferSource buffers) {
        if (f.length == 0) {
            return;
        }
        var sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(FRAME);
        float u = (sprite.getU0() + sprite.getU1()) * 0.5F, v = (sprite.getV0() + sprite.getV1()) * 0.5F;
        var vc = buffers.getBuffer(RenderType.entitySolid(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < f.length / 12; i++) {
            for (int k : new int[] {0, 1, 2, 3, 3, 2, 1, 0}) {
                vc.addVertex(pose, f[i * 12 + k * 3], f[i * 12 + k * 3 + 1], f[i * 12 + k * 3 + 2]).setColor(0xFF17171A).setUv(u, v)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
            }
        }
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
