package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SonarItem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A sonar's pulse (SonarItem): a glowing ring sweeping out from where it was sent, over everything round (the world's
 * depth: shaders/core/sonar_wave - not with a shader pack), a faint grid of blocks left behind it fading; and what the
 * sonar's set to find lighting up as the ring passes it, seen through walls - blocks outlined, mobs glowing (as the
 * Glowing effect: mixin/SonarGlowMixin), each in its colour - for a while, then fading.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SonarPulse {
    /** How far it reaches, how fast the ring goes (blocks a second), how long what it finds stays lit after (seconds). */
    private static final float REACH = 40.0F, SPEED = 18.0F, STAYS = 8.0F;
    private static final int MOST_BLOCKS = 4000;

    static ShaderInstance waveShader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerSonarShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "sonar_wave"), DefaultVertexFormat.POSITION,
                    s -> waveShader = s);
        }
    }

    abstract static class Types extends RenderType {
        /** Outlines seen through everything. */
        static final RenderType OUTLINE = create("skyblocks_sonar_outline", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1 << 16,
                false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(2.5)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /** A block it found: where, its colour, when the ring reaches it (seconds after it was sent). */
    private record Found(BlockPos pos, int colour, float reached) {}

    private static Vec3 origin;
    private static long startedAt;
    private static ClientLevel pulseLevel;
    private static final List<Found> FOUND = new ArrayList<>();
    private static List<SonarItem.Entry> mobEntries = List.of();
    /** Mobs lit up: their id, their colour, until when (game time). */
    private static final Map<Integer, long[]> GLOWING = new ConcurrentHashMap<>();
    private static TextureTarget copy;

    private SonarPulse() {}

    /** (For the render test.) */
    public static String debug() {
        return "origin " + origin + ", found " + FOUND.size() + (FOUND.isEmpty() ? "" : " first " + FOUND.get(0)) + ", glowing " + GLOWING.size()
                + ", mob entries " + mobEntries + ", drew " + drew;
    }

    private static int drew;

    // ---- what a list's entries find ----

    private static ResourceLocation id(String s) {
        return ResourceLocation.tryParse(s.contains(":") ? s : "minecraft:" + s);
    }

    /** Whether a block or mob entry names something there is. */
    public static boolean valid(String s, boolean mob) {
        boolean tag = s.startsWith("#");
        ResourceLocation rl = id(tag ? s.substring(1) : s);
        if (mob) {
            for (MobCategory c : MobCategory.values()) {
                if (c.getName().equals(s)) {
                    return true;
                }
            }
            return rl != null && (tag ? BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE, rl)).isPresent()
                    : BuiltInRegistries.ENTITY_TYPE.containsKey(rl));
        }
        return rl != null && (tag ? BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, rl)).isPresent() : BuiltInRegistries.BLOCK.containsKey(rl));
    }

    private static Predicate<BlockState> blockTest(String s) {
        if (s.startsWith("#")) {
            ResourceLocation rl = id(s.substring(1));
            if (rl == null) {
                return st -> false;
            }
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, rl);
            return st -> st.is(tag);
        }
        ResourceLocation rl = id(s);
        Block block = rl != null && BuiltInRegistries.BLOCK.containsKey(rl) ? BuiltInRegistries.BLOCK.get(rl) : null;
        return st -> block != null && st.is(block);
    }

    /** The colour a mob lights up in, or -1. */
    private static int mobColour(Entity e) {
        EntityType<?> type = e.getType();
        for (SonarItem.Entry entry : mobEntries) {
            String s = entry.id();
            boolean hit;
            if (s.startsWith("#")) {
                ResourceLocation rl = id(s.substring(1));
                hit = rl != null && type.is(TagKey.create(Registries.ENTITY_TYPE, rl));
            } else {
                MobCategory category = null;
                for (MobCategory c : MobCategory.values()) {
                    if (c.getName().equals(s)) {
                        category = c;
                    }
                }
                ResourceLocation rl = id(s);
                hit = category != null ? type.getCategory() == category : rl != null && rl.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
            }
            if (hit) {
                return entry.colour();
            }
        }
        return -1;
    }

    // ---- sending one ----

    /** Sends a pulse from the player: finds what the list names round them (the blocks now; mobs as the ring reaches them). */
    public static void start(Player player, List<SonarItem.Entry> entries) {
        if (!(player.level() instanceof ClientLevel level)) {
            return;
        }
        origin = player.getEyePosition();
        startedAt = level.getGameTime();
        pulseLevel = level;
        FOUND.clear();
        GLOWING.clear();
        List<SonarItem.Entry> blocks = new ArrayList<>(), mobs = new ArrayList<>();
        for (SonarItem.Entry e : entries) {
            (e.mob() ? mobs : blocks).add(e);
        }
        mobEntries = mobs;
        if (blocks.isEmpty()) {
            return;
        }
        List<Predicate<BlockState>> tests = new ArrayList<>();
        for (SonarItem.Entry e : blocks) {
            tests.add(blockTest(e.id()));
        }
        Predicate<BlockState> any = st -> {
            for (Predicate<BlockState> t : tests) {
                if (t.test(st)) {
                    return true;
                }
            }
            return false;
        };
        int r = (int) REACH;
        int cx = SectionPos.blockToSectionCoord(origin.x), cz = SectionPos.blockToSectionCoord(origin.z);
        int chunks = (r >> 4) + 1;
        int minY = (int) Math.floor(origin.y) - r, maxY = (int) Math.floor(origin.y) + r;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length && FOUND.size() < MOST_BLOCKS; i++) {
                    LevelChunkSection section = sections[i];
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    if (section == null || section.hasOnlyAir() || baseY + 15 < minY || baseY > maxY || !section.maybeHas(any)) {
                        continue;
                    }
                    int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState st = section.getBlockState(x, y, z);
                                if (!any.test(st)) {
                                    continue;
                                }
                                double d = origin.distanceTo(new Vec3(bx + x + 0.5, baseY + y + 0.5, bz + z + 0.5));
                                if (d > REACH) {
                                    continue;
                                }
                                int colour = blocks.get(0).colour();
                                for (int k = 0; k < tests.size(); k++) {
                                    if (tests.get(k).test(st)) {
                                        colour = blocks.get(k).colour();
                                        break;
                                    }
                                }
                                FOUND.add(new Found(new BlockPos(bx + x, baseY + y, bz + z), colour, (float) (d / SPEED)));
                            }
                        }
                    }
                }
            }
        }
    }

    /** For mixin/SonarGlowMixin: whether a mob's lit up by a pulse, and its colour (-1: not). */
    public static int glow(Entity e) {
        if (GLOWING.isEmpty()) {
            return -1;
        }
        long[] g = GLOWING.get(e.getId());
        if (g == null || e.level() != pulseLevel || pulseLevel == null || pulseLevel.getGameTime() > g[1]) {
            return -1;
        }
        return (int) g[0];
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (origin == null || level == null || level != pulseLevel) {
            if (level != pulseLevel) {
                origin = null;
                FOUND.clear();
                GLOWING.clear();
            }
            return;
        }
        float age = (level.getGameTime() - startedAt) / 20.0F;
        if (age > REACH / SPEED + STAYS) {
            origin = null;
            FOUND.clear();
            GLOWING.clear();
            return;
        }
        // mobs the ring's reached light up (until what it found fades)
        float ring = Math.min(age * SPEED, REACH);
        if (!mobEntries.isEmpty() && age * SPEED <= REACH + SPEED) {
            long until = startedAt + (long) ((REACH / SPEED + STAYS) * 20);
            for (Entity e : level.getEntities((Entity) null, new AABB(origin, origin).inflate(ring), e -> !(e instanceof Player p && p.isLocalPlayer()))) {
                if (!GLOWING.containsKey(e.getId()) && e.position().distanceTo(origin) <= ring) {
                    int c = mobColour(e);
                    if (c >= 0) {
                        GLOWING.put(e.getId(), new long[] {c, until});
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || origin == null || pulseLevel == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float age = (pulseLevel.getGameTime() - startedAt + pt) / 20.0F;
        Vec3 cam = event.getCamera().getPosition();
        boolean pack = com.example.skyblocks.client.iris.IrisIslandTarget.deferSky();
        if (!pack && waveShader != null && age * SPEED < REACH + 12.0F) {
            wave(event, cam, age);
        }
        if (FOUND.isEmpty()) {
            return;
        }
        // what it found, outlined through walls - from when the ring reached it, fading at the end
        float end = REACH / SPEED + STAYS;
        float fadeAll = Math.min(1.0F, (end - age) / 2.5F);
        List<float[]> boxes = new ArrayList<>();
        for (Found f : FOUND) {
            float since = age - f.reached();
            if (since < 0) {
                continue;
            }
            // (a bright flash as the ring passes, settling)
            float a = Math.min(1.0F, 0.55F + 0.45F * (float) Math.exp(-since * 3.0)) * fadeAll;
            boxes.add(new float[] {f.pos().getX() - (float) cam.x, f.pos().getY() - (float) cam.y, f.pos().getZ() - (float) cam.z, f.colour(), a});
        }
        drew = boxes.size();
        if (boxes.isEmpty()) {
            return;
        }
        java.util.function.Consumer<MultiBufferSource.BufferSource> draw = src -> {
            PoseStack pose = new PoseStack();
            VertexConsumer vc = src.getBuffer(Types.OUTLINE);
            for (float[] b : boxes) {
                int c = (int) b[3];
                LevelRenderer.renderLineBox(pose, vc, new AABB(b[0] + 0.002, b[1] + 0.002, b[2] + 0.002, b[0] + 0.998, b[1] + 0.998, b[2] + 0.998),
                        (c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F, b[4]);
            }
            src.endBatch(Types.OUTLINE);
        };
        if (!LatePass.later(draw)) {
            draw.accept(Minecraft.getInstance().renderBuffers().bufferSource());
        }
    }

    /** The ring: drawn over the whole screen, lighting what's at its distance from where it was sent (the world's depth). */
    private static void wave(RenderLevelStageEvent event, Vec3 cam, float age) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!copyDepth(main)) {
            return;
        }
        Matrix4f inv = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
        float radius = age * SPEED;
        float fade = 1.0F - Math.max(0.0F, Math.min(1.0F, (radius - REACH * 0.8F) / (REACH * 0.2F + 12.0F)));
        waveShader.safeGetUniform("InvViewProj").set(inv);
        waveShader.safeGetUniform("Origin").set((float) (origin.x - cam.x), (float) (origin.y - cam.y), (float) (origin.z - cam.z));
        waveShader.safeGetUniform("CamFract").set((float) (cam.x - Math.floor(cam.x)), (float) (cam.y - Math.floor(cam.y)), (float) (cam.z - Math.floor(cam.z)));
        waveShader.safeGetUniform("Radius").set(radius);
        waveShader.safeGetUniform("Fade").set(fade);
        waveShader.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
        RenderSystem.setShader(() -> waveShader);
        RenderSystem.setShaderTexture(0, copy.getDepthTextureId());
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(-1, -1, 0);
        b.addVertex(1, -1, 0);
        b.addVertex(1, 1, 0);
        b.addVertex(-1, 1, 0);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static boolean copyDepth(RenderTarget main) {
        int w = Math.max(main.width, 1), h = Math.max(main.height, 1);
        if (copy == null) {
            copy = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
        }
        if (main.isStencilEnabled() && !copy.isStencilEnabled()) {
            copy.enableStencil();
        }
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // (anything earlier's errors let go)
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        boolean ok = GL11.glGetError() == GL11.GL_NO_ERROR;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        return ok;
    }
}
