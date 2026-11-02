package com.example.skyblocks.client;

import com.example.skyblocks.HologramBlockEntity;
import com.example.skyblocks.HologramProjectorBlock;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The hologram: the item's own model (or a player with their skin, or a mob in a cage of light), drawn through shaders/core/hologram (one colour, see-through, scan lines,
 * flicker, bright edges) turning slowly over the projector, and a faint cone of light up from the lens.
 */
public class HologramRenderer implements BlockEntityRenderer<HologramBlockEntity> {
    static ShaderInstance shader;
    /** This hologram's colour (each is drawn at once, so each can have its own). */
    private static final float[] COLOUR = {0.3F, 0.9F, 1.0F};

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            com.example.skyblocks.client.ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "hologram"), DefaultVertexFormat.NEW_ENTITY, s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        private static final java.util.Map<ResourceLocation, RenderType> BY_TEXTURE = new java.util.HashMap<>();

        /** The hologram look, on a texture (the block atlas for most items; their own for chests, shields, heads ...). */
        static RenderType holo(ResourceLocation texture) {
            return BY_TEXTURE.computeIfAbsent(texture, tex -> create("skyblocks_hologram", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 262144,
                    false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTextureState(new TextureStateShard(tex, false, false))
                            .setTexturingState(new TexturingStateShard("skyblocks_hologram_look", () -> {
                                if (shader != null) {
                                    shader.safeGetUniform("HoloColor").set(COLOUR[0], COLOUR[1], COLOUR[2]);
                                    shader.safeGetUniform("HoloTime").set(LavaLampRenderer.seconds());
                                }
                            }, () -> {}))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(CULL)
                            .createCompositeState(false)));
        }

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    private static java.lang.reflect.Field stateField, textureField;
    private static java.lang.reflect.Method cutoutMethod;
    private static boolean reflectionTried;

    /** The texture a render type draws with (looked up once how; empty if it can't be found). */
    @SuppressWarnings("unchecked")
    static java.util.Optional<ResourceLocation> textureOf(RenderType type) {
        try {
            if (!reflectionTried) {
                reflectionTried = true;
                Class<?> composite = Class.forName("net.minecraft.client.renderer.RenderType$CompositeRenderType");
                stateField = composite.getDeclaredField("state");
                stateField.setAccessible(true);
                textureField = Class.forName("net.minecraft.client.renderer.RenderType$CompositeState").getDeclaredField("textureState");
                textureField.setAccessible(true);
                cutoutMethod = Class.forName("net.minecraft.client.renderer.RenderStateShard$EmptyTextureStateShard").getDeclaredMethod("cutoutTexture");
                cutoutMethod.setAccessible(true);
            }
            if (stateField == null || !stateField.getDeclaringClass().isInstance(type)) {
                return java.util.Optional.empty();
            }
            Object state = stateField.get(type);
            Object texture = textureField.get(state);
            return (java.util.Optional<ResourceLocation>) cutoutMethod.invoke(texture);
        } catch (ReflectiveOperationException | RuntimeException e) {
            stateField = null;
            return java.util.Optional.empty();
        }
    }

    /** Passes a block-format vertex on, adding the overlay the hologram's format also has. */
    private record WithOverlay(VertexConsumer inner) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { inner.addVertex(x, y, z); inner.setUv1(0, 10); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { inner.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { inner.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { inner.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { inner.setNormal(x, y, z); return this; }
    }

    /** Writes nothing (for the enchantment glint and other parts that aren't the item's own textured faces). */
    private static final VertexConsumer NOTHING = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    /**
     * Passes quads on to the hologram, each checked: on the block atlas a quad's texture is a small part of it; one
     * stretched over the whole atlas (a block drawn its own way, through its own shader - Shadered's blocks: their quads'
     * texture coordinates run 0..1) would show every texture in the game, so it's given the item's own texture instead.
     */
    private static final class AtlasGuard implements VertexConsumer {
        private final VertexConsumer inner;
        private final net.minecraft.client.renderer.texture.TextureAtlasSprite fallback;
        private final float[][] v = new float[4][16];
        private final boolean[][] set = new boolean[4][3]; // (overlay, light, normal given)
        private int n = -1;

        AtlasGuard(VertexConsumer inner, net.minecraft.client.renderer.texture.TextureAtlasSprite fallback) {
            this.inner = inner;
            this.fallback = fallback;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (n == 3) flush();
            n++;
            java.util.Arrays.fill(v[n], 0.0F);
            java.util.Arrays.fill(set[n], false);
            v[n][0] = x; v[n][1] = y; v[n][2] = z;
            v[n][3] = v[n][4] = v[n][5] = v[n][6] = 255.0F;
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { if (n >= 0) { v[n][3] = r; v[n][4] = g; v[n][5] = b; v[n][6] = a; } return this; }
        @Override public VertexConsumer setUv(float u, float w) { if (n >= 0) { v[n][7] = u; v[n][8] = w; } return this; }
        @Override public VertexConsumer setUv1(int u, int w) { if (n >= 0) { v[n][9] = u; v[n][10] = w; set[n][0] = true; } return this; }
        @Override public VertexConsumer setUv2(int u, int w) { if (n >= 0) { v[n][11] = u; v[n][12] = w; set[n][1] = true; } return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { if (n >= 0) { v[n][13] = x; v[n][14] = y; v[n][15] = z; set[n][2] = true; } return this; }

        /** The quad so far, on (its texture made the item's own if it spans the atlas). */
        void flush() {
            if (n < 0) return;
            float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, w0 = Float.MAX_VALUE, w1 = -Float.MAX_VALUE;
            for (int i = 0; i <= n; i++) {
                u0 = Math.min(u0, v[i][7]); u1 = Math.max(u1, v[i][7]);
                w0 = Math.min(w0, v[i][8]); w1 = Math.max(w1, v[i][8]);
            }
            boolean whole = u1 - u0 > 0.3F || w1 - w0 > 0.3F;
            for (int i = 0; i <= n; i++) {
                float u = v[i][7], w = v[i][8];
                if (whole && fallback != null) {
                    float fu = (u - u0) / Math.max(u1 - u0, 1.0e-6F), fw = (w - w0) / Math.max(w1 - w0, 1.0e-6F);
                    u = fallback.getU0() + fu * (fallback.getU1() - fallback.getU0());
                    w = fallback.getV0() + fw * (fallback.getV1() - fallback.getV0());
                }
                VertexConsumer c = inner.addVertex(v[i][0], v[i][1], v[i][2]).setColor((int) v[i][3], (int) v[i][4], (int) v[i][5], (int) v[i][6]).setUv(u, w);
                if (set[i][0]) c.setUv1((int) v[i][9], (int) v[i][10]);
                if (set[i][1]) c.setUv2((int) v[i][11], (int) v[i][12]);
                if (set[i][2]) c.setNormal(v[i][13], v[i][14], v[i][15]);
            }
            n = -1;
        }
    }

    public HologramRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HologramBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        int c = com.example.skyblocks.GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds());
        float r = ((c >> 16) & 255) / 255.0F, g = ((c >> 8) & 255) / 255.0F, b = (c & 255) / 255.0F;
        ItemStack stack = be.shown();
        int size = be.getBlockState().hasProperty(HologramProjectorBlock.SIZE) ? be.getBlockState().getValue(HologramProjectorBlock.SIZE) : 1;
        float scale = new float[] {0.7F, 1.1F, 1.7F}[size];
        float t = LavaLampRenderer.seconds();
        float centre = 0.29F + 0.36F * scale + 0.03F * (float) Math.sin(t * 1.3);
        // the cone of light from the lens up to the hologram
        float top = !be.showing() ? 0.55F : centre;
        float spread = !be.showing() ? 0.06F : 0.22F * scale;
        cone(buffers.getBuffer(RenderType.lightning()), poseStack.last().pose(), 4.6F / 16.0F, top, 1.6F / 16.0F, spread, r, g, b,
                !be.showing() ? 0.12F : 0.2F);
        if (!be.showing() || shader == null) {
            return;
        }
        COLOUR[0] = r;
        COLOUR[1] = g;
        COLOUR[2] = b;
        // whatever it draws with: the hologram look on that same texture (chests, shields, heads, skins: their own)
        java.util.Set<RenderType> used = new java.util.LinkedHashSet<>();
        java.util.List<AtlasGuard> guards = new java.util.ArrayList<>();
        net.minecraft.client.renderer.texture.TextureAtlasSprite own = stack.isEmpty() ? null
                : Minecraft.getInstance().getItemRenderer().getModel(stack, be.getLevel(), null, 0).getParticleIcon();
        MultiBufferSource holo = type -> {
            if (type.format() == DefaultVertexFormat.BLOCK) { // (a block model drawn as an item part: on the block atlas)
                RenderType h = Types.holo(TextureAtlas.LOCATION_BLOCKS);
                used.add(h);
                AtlasGuard guard = new AtlasGuard(new WithOverlay(buffers.getBuffer(h)), own);
                guards.add(guard);
                return guard;
            }
            if (type.format() != DefaultVertexFormat.NEW_ENTITY) {
                return NOTHING;
            }
            java.util.Optional<ResourceLocation> tex = textureOf(type);
            RenderType h = Types.holo(tex.orElse(TextureAtlas.LOCATION_BLOCKS));
            used.add(h);
            if (tex.isPresent() && !tex.get().equals(TextureAtlas.LOCATION_BLOCKS)) {
                return buffers.getBuffer(h); // (its own texture: as it is)
            }
            AtlasGuard guard = new AtlasGuard(buffers.getBuffer(h), own);
            guards.add(guard);
            return guard;
        };
        poseStack.pushPose();
        if (be.mode() == HologramBlockEntity.PLAYER) {
            drawPlayer(be, poseStack, holo, scale, t);
        } else if (be.mode() == HologramBlockEntity.MOB) {
            drawMob(be, poseStack, holo, buffers, scale, t, partialTick, r, g, b);
        } else {
            poseStack.translate(0.5, centre, 0.5);
            poseStack.mulPose(Axis.YP.rotation(t * 0.7F));
            float s = scale * (stack.getItem() instanceof BlockItem ? 1.0F : 0.9F);
            poseStack.scale(s, s, s);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY,
                    poseStack, holo, be.getLevel(), (int) be.getBlockPos().asLong());
        }
        for (AtlasGuard guard : guards) {
            guard.flush();
        }
        poseStack.popPose();
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            for (RenderType h : used) {
                source.endBatch(h); // (now, with this hologram's colour)
            }
        }
    }

    // ---- a player: their own skin, standing on the beam, turning slowly, looking about ----

    private static net.minecraft.client.model.PlayerModel<?> wide, slim;
    private static final java.util.Map<java.util.UUID, java.util.concurrent.CompletableFuture<net.minecraft.client.resources.PlayerSkin>> SKINS = new java.util.HashMap<>();

    private static net.minecraft.client.resources.PlayerSkin skinOf(com.mojang.authlib.GameProfile profile) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getPlayerByUUID(profile.getId()) instanceof net.minecraft.client.player.AbstractClientPlayer p) {
            return p.getSkin(); // (they're here: the skin as the game already has it)
        }
        var future = SKINS.computeIfAbsent(profile.getId(), id -> mc.getSkinManager().getOrLoad(profile));
        return future.getNow(net.minecraft.client.resources.DefaultPlayerSkin.get(profile));
    }

    private static void drawPlayer(HologramBlockEntity be, PoseStack poseStack, MultiBufferSource holo, float scale, float t) {
        com.mojang.authlib.GameProfile profile = be.profile();
        if (profile == null) {
            return;
        }
        var skin = skinOf(profile);
        boolean thin = skin.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        var models = Minecraft.getInstance().getEntityModels();
        if (wide == null) {
            wide = new net.minecraft.client.model.PlayerModel<>(models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
            slim = new net.minecraft.client.model.PlayerModel<>(models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM), true);
        }
        net.minecraft.client.model.PlayerModel<?> model = thin ? slim : wide;
        // a gentle idle: looking about, arms swaying a little, breathing
        model.head.xRot = 0.08F * (float) Math.sin(t * 0.37);
        model.head.yRot = 0.5F * (float) Math.sin(t * 0.23);
        model.body.xRot = 0.0F;
        model.rightArm.xRot = 0.12F * (float) Math.sin(t * 1.1);
        model.leftArm.xRot = -0.12F * (float) Math.sin(t * 1.1);
        model.rightArm.zRot = 0.06F + 0.03F * (float) Math.sin(t * 1.7);
        model.leftArm.zRot = -0.06F - 0.03F * (float) Math.sin(t * 1.7);
        model.rightLeg.xRot = model.leftLeg.xRot = 0.0F;
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        float s = scale * 0.5F; // (medium: a player about a block tall)
        poseStack.translate(0.5, 0.33, 0.5);
        poseStack.mulPose(Axis.YP.rotation(t * 0.45F));
        poseStack.scale(s, s, s);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.renderToBuffer(poseStack, holo.getBuffer(RenderType.entityTranslucent(skin.texture())), 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }

    // ---- a mob: the real mob's model, turning, in a cage of light ----

    private static final java.util.Map<HologramBlockEntity, net.minecraft.world.entity.Entity> MOBS = new java.util.WeakHashMap<>();

    private static void drawMob(HologramBlockEntity be, PoseStack poseStack, MultiBufferSource holo, MultiBufferSource buffers,
                                float scale, float t, float partialTick, float r, float g, float b) {
        var level = be.getLevel();
        if (level == null) {
            return;
        }
        String id = be.mob();
        net.minecraft.world.entity.Entity e = MOBS.get(be);
        if (e == null || !net.minecraft.world.entity.EntityType.getKey(e.getType()).toString().equals(id)) {
            e = net.minecraft.world.entity.EntityType.byString(id).map(type -> type.create(level)).orElse(null);
            if (e == null) {
                return;
            }
            MOBS.put(be, e);
        }
        e.setPos(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5);
        e.tickCount = (int) (t * 20.0F);
        e.setYRot(0.0F);
        e.setXRot(0.0F);
        if (e instanceof net.minecraft.world.entity.LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = 0.0F;
            living.yHeadRot = living.yHeadRotO = 25.0F * (float) Math.sin(t * 0.4);
        }
        float big = Math.max(e.getBbHeight(), e.getBbWidth() * 1.8F); // (most mobs are longer than their box is wide)
        float fit = scale * 0.62F / Math.max(big, 0.3F);
        float cage = scale * 0.42F;
        float mid = 0.33F + cage;
        // the cage: the edges of a box of light round the mob
        cageBox(buffers.getBuffer(RenderType.lines()), poseStack.last(), 0.5F, mid, 0.5F, cage, r, g, b);
        poseStack.translate(0.5, mid - e.getBbHeight() * fit * 0.5F, 0.5);
        poseStack.mulPose(Axis.YP.rotation(t * 0.45F));
        poseStack.scale(fit, fit, fit);
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        try {
            renderer.render(e, 0.0F, partialTick, poseStack, holo, 0xF000F0);
        } catch (RuntimeException ex) {
            MOBS.remove(be); // (a mob that can't be drawn here: nothing, rather than a crash)
        }
    }

    private static void cageBox(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz, float h, float r, float g, float b) {
        float[][] c = new float[8][];
        for (int k = 0; k < 8; k++) {
            c[k] = new float[] {cx + ((k & 1) != 0 ? h : -h), cy + ((k & 2) != 0 ? h : -h), cz + ((k & 4) != 0 ? h : -h)};
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            float[] a = c[e[0]], z = c[e[1]];
            float nx = z[0] - a[0], ny = z[1] - a[1], nz = z[2] - a[2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            vc.addVertex(pose, a[0], a[1], a[2]).setColor(r, g, b, 0.7F).setNormal(pose, nx / len, ny / len, nz / len);
            vc.addVertex(pose, z[0], z[1], z[2]).setColor(r, g, b, 0.7F).setNormal(pose, nx / len, ny / len, nz / len);
        }
    }

    /** A soft cone of light: bright at the lens, fading upwards. */
    private static void cone(VertexConsumer vc, Matrix4f m, float y0, float y1, float r0, float r1, float r, float g, float b, float a) {
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2.0 / n, a1 = (i + 1) * Math.PI * 2.0 / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vc.addVertex(m, 0.5F + c0 * r0, y0, 0.5F + s0 * r0).setColor(r, g, b, a);
            vc.addVertex(m, 0.5F + c1 * r0, y0, 0.5F + s1 * r0).setColor(r, g, b, a);
            vc.addVertex(m, 0.5F + c1 * r1, y1, 0.5F + s1 * r1).setColor(r, g, b, 0.0F);
            vc.addVertex(m, 0.5F + c0 * r1, y1, 0.5F + s0 * r1).setColor(r, g, b, 0.0F);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(HologramBlockEntity be) {
        return true;
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox(HologramBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.5);
    }
}
