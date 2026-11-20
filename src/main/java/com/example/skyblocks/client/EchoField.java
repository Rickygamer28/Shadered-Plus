package com.example.skyblocks.client;

import com.example.skyblocks.EchoFieldBlock;
import com.example.skyblocks.SimpleRenderedBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Echo fields (EchoFieldBlock): whatever moves near one - players, mobs, dropped items - leaves a trail of ghosts of
 * itself where it was, each drawn again just as it was then (turned, mid-step) but glowing and see-through, red to
 * violet from oldest to newest, fading away. Its block entity's renderer only says where the fields are; every few
 * ticks things in them that have moved are noted, and after the world's creatures are drawn, their ghosts are.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class EchoField {
    /** How long a ghost lasts, how often one's left (ticks), how far it must have moved (blocks), how many at most each. */
    private static final int LIFE = 32, EVERY = 3, MOST = 9, MOST_THINGS = 48;
    private static final double MOVED = 0.3;

    /** The fields in sight lately: where, how far they reach, when last seen. */
    private record Field_(BlockPos pos, int radius, long seen) {}

    private static final Map<BlockPos, Field_> FIELDS = new HashMap<>();

    private record Ghost(double x, double y, double z, float yRot, float xRot, float body, float head, float walkPos, float walkSpeed, long at) {}

    private static final class Trail {
        final ArrayDeque<Ghost> ghosts = new ArrayDeque<>();
        long lastAt;
        int count;
    }

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static ByteBufferBuilder memory;

    private EchoField() {}

    /** Says where a field is (its block entity's renderer, each frame it's in sight). */
    public static final class Renderer implements BlockEntityRenderer<SimpleRenderedBlockEntity> {
        public Renderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(SimpleRenderedBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            if (be.getLevel() != null && be.getBlockState().getBlock() instanceof EchoFieldBlock) {
                int r = EchoFieldBlock.RADII[be.getBlockState().getValue(EchoFieldBlock.SIZE)];
                FIELDS.put(be.getBlockPos(), new Field_(be.getBlockPos(), r, be.getLevel().getGameTime()));
            }
        }

        @Override
        public AABB getRenderBoundingBox(SimpleRenderedBlockEntity be) {
            return new AABB(be.getBlockPos()).inflate(12);
        }

        @Override
        public boolean shouldRenderOffScreen(SimpleRenderedBlockEntity be) {
            return true;
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            FIELDS.clear();
            TRAILS.clear();
            return;
        }
        long now = level.getGameTime();
        FIELDS.values().removeIf(f -> now - f.seen() > 20 || now < f.seen());
        TRAILS.values().forEach(t -> t.ghosts.removeIf(g -> now - g.at() > LIFE || now < g.at()));
        TRAILS.values().removeIf(t -> t.ghosts.isEmpty() && now - t.lastAt > LIFE);
        if (FIELDS.isEmpty() || now % EVERY != 0) {
            return;
        }
        for (Field_ f : FIELDS.values()) {
            Vec3 c = Vec3.atCenterOf(f.pos());
            for (Entity e : level.getEntities((Entity) null, new AABB(f.pos()).inflate(f.radius()), e -> e instanceof LivingEntity || e instanceof ItemEntity)) {
                if (e.distanceToSqr(c) > (double) f.radius() * f.radius() || TRAILS.size() >= MOST_THINGS && !TRAILS.containsKey(e.getId())) {
                    continue;
                }
                Trail trail = TRAILS.computeIfAbsent(e.getId(), k -> new Trail());
                Ghost last = trail.ghosts.peekLast();
                if (trail.lastAt == now || last != null && e.distanceToSqr(last.x(), last.y(), last.z()) < MOVED * MOVED) {
                    continue;
                }
                trail.lastAt = now;
                trail.count++;
                float body = e.getYRot(), head = e.getYHeadRot(), walkPos = 0, walkSpeed = 0;
                if (e instanceof LivingEntity le) {
                    body = le.yBodyRot;
                    head = le.yHeadRot;
                    walkPos = le.walkAnimation.position();
                    walkSpeed = le.walkAnimation.speed();
                }
                trail.ghosts.addLast(new Ghost(e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), body, head, walkPos, walkSpeed, now));
                while (trail.ghosts.size() > MOST) {
                    trail.ghosts.removeFirst();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || TRAILS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        if (memory == null) {
            memory = new ByteBufferBuilder(1 << 20);
        }
        MultiBufferSource.BufferSource out = MultiBufferSource.immediate(memory);
        for (Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Trail> entry = it.next();
            Entity e = level.getEntity(entry.getKey());
            if (e == null || e.isRemoved()) {
                it.remove();
                continue;
            }
            EntityRenderer<? super Entity> renderer = mc.getEntityRenderDispatcher().getRenderer(e);
            int i = 0, n = entry.getValue().ghosts.size();
            for (Ghost g : entry.getValue().ghosts) {
                float age = (now - g.at()) / LIFE;
                float fade = Math.max(0.0F, 1.0F - age);
                // (oldest red .. newest violet; the newest, right by the real one, faintest - it's still almost there)
                float hue = (float) i / Math.max(n - 1, 1) * 0.8F;
                float near = i == n - 1 ? 0.5F : 1.0F;
                i++;
                if (fade <= 0.01F || e.distanceToSqr(g.x(), g.y(), g.z()) < 0.04) {
                    continue;
                }
                int rgb = net.minecraft.util.Mth.hsvToRgb(hue, 0.75F, 1.0F);
                drawGhost(e, renderer, g, pose, cam, out, rgb, fade * fade * 0.6F * near);
            }
        }
        out.endBatch();
    }

    /** One ghost: the thing posed as it was, drawn glowing and see-through in a colour. */
    private static void drawGhost(Entity e, EntityRenderer<? super Entity> renderer, Ghost g, PoseStack pose, Vec3 cam, MultiBufferSource.BufferSource out,
                                  int rgb, float alpha) {
        float[] saved = pose(e, g);
        try {
            pose.pushPose();
            Vec3 off = renderer.getRenderOffset(e, 1.0F);
            pose.translate(g.x() - cam.x + off.x, g.y() - cam.y + off.y, g.z() - cam.z + off.z);
            renderer.render(e, g.yRot(), 1.0F, pose, ghostly(out, rgb, alpha), LightTexture.FULL_BRIGHT);
        } catch (RuntimeException ignored) {
            // (something it couldn't draw as a ghost: no ghost)
        } finally {
            pose.popPose();
            unpose(e, saved);
        }
    }

    /** Poses it as the ghost was; what it was, to put back. */
    private static float[] pose(Entity e, Ghost g) {
        float[] s = {e.getYRot(), e.yRotO, e.getXRot(), e.xRotO, 0, 0, 0, 0, 0, 0, 0};
        e.setYRot(g.yRot());
        e.yRotO = g.yRot();
        e.setXRot(g.xRot());
        e.xRotO = g.xRot();
        if (e instanceof LivingEntity le) {
            s[4] = le.yBodyRot;
            s[5] = le.yBodyRotO;
            s[6] = le.yHeadRot;
            s[7] = le.yHeadRotO;
            WalkAnimationState w = le.walkAnimation;
            s[8] = w.position();
            s[9] = w.speed();
            s[10] = walk(w, Float.NaN, Float.NaN);
            le.yBodyRot = le.yBodyRotO = g.body();
            le.yHeadRot = le.yHeadRotO = g.head();
            walk(w, g.walkPos(), g.walkSpeed());
        }
        return s;
    }

    private static void unpose(Entity e, float[] s) {
        e.setYRot(s[0]);
        e.yRotO = s[1];
        e.setXRot(s[2]);
        e.xRotO = s[3];
        if (e instanceof LivingEntity le) {
            le.yBodyRot = s[4];
            le.yBodyRotO = s[5];
            le.yHeadRot = s[6];
            le.yHeadRotO = s[7];
            walk(le.walkAnimation, s[8], s[9]);
            setOld(le.walkAnimation, s[10]);
        }
    }

    // ---- a walk's place in its step (WalkAnimationState's own fields: position, speed, speedOld) ----

    private static Field POSITION, SPEED, SPEED_OLD;
    private static boolean looked;

    private static void fields() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            POSITION = WalkAnimationState.class.getDeclaredField("position");
            SPEED = WalkAnimationState.class.getDeclaredField("speed");
            SPEED_OLD = WalkAnimationState.class.getDeclaredField("speedOld");
            POSITION.setAccessible(true);
            SPEED.setAccessible(true);
            SPEED_OLD.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            POSITION = null;
        }
    }

    /** Sets where it is in its step (NaN: leaves it); gives what speedOld was. */
    private static float walk(WalkAnimationState w, float position, float speed) {
        fields();
        if (POSITION == null) {
            return 0;
        }
        try {
            float old = SPEED_OLD.getFloat(w);
            if (!Float.isNaN(position)) {
                POSITION.setFloat(w, position);
                SPEED.setFloat(w, speed);
                SPEED_OLD.setFloat(w, speed);
            }
            return old;
        } catch (IllegalAccessException ex) {
            return 0;
        }
    }

    private static void setOld(WalkAnimationState w, float old) {
        if (POSITION != null) {
            try {
                SPEED_OLD.setFloat(w, old);
            } catch (IllegalAccessException ignored) {
                // (never)
            }
        }
    }

    // ---- drawing it as a ghost: each of its pictures glowing and see-through, in one colour ----

    private static final Map<RenderType, Optional<RenderType>> GHOST_TYPES = new HashMap<>();
    private static Method STATE, TEXTURE;
    private static Field TEXTURE_STATE;

    /** The ghost's way to draw what's drawn one way: the same picture, glowing, see-through (none: drawn not at all). */
    private static Optional<RenderType> ghostType(RenderType type) {
        return GHOST_TYPES.computeIfAbsent(type, t -> {
            String name = t.toString();
            if (name.contains("text") || name.contains("shadow") || name.contains("lines")) {
                return Optional.empty();
            }
            try {
                if (STATE == null) {
                    Class<?> composite = Class.forName("net.minecraft.client.renderer.RenderType$CompositeRenderType");
                    STATE = composite.getDeclaredMethod("state");
                    STATE.setAccessible(true);
                    TEXTURE_STATE = Class.forName("net.minecraft.client.renderer.RenderType$CompositeState").getDeclaredField("textureState");
                    TEXTURE_STATE.setAccessible(true);
                    TEXTURE = Class.forName("net.minecraft.client.renderer.RenderStateShard$EmptyTextureStateShard").getDeclaredMethod("cutoutTexture");
                    TEXTURE.setAccessible(true);
                }
                if (!STATE.getDeclaringClass().isInstance(t)) {
                    return Optional.empty();
                }
                Object textures = TEXTURE_STATE.get(STATE.invoke(t));
                @SuppressWarnings("unchecked")
                Optional<ResourceLocation> tex = (Optional<ResourceLocation>) TEXTURE.invoke(textures);
                return tex.map(RenderType::entityTranslucentEmissive);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return Optional.empty();
            }
        });
    }

    private static MultiBufferSource ghostly(MultiBufferSource.BufferSource out, int rgb, float alpha) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255, a = Math.round(alpha * 255);
        return type -> {
            Optional<RenderType> ghost = ghostType(type);
            return ghost.isPresent() ? new Tinted(out.getBuffer(ghost.get()), r, g, b, a) : NOTHING;
        };
    }

    /** Passes everything on, its colour tinted and faded (its own colour's brightness kept), fully lit. */
    private record Tinted(VertexConsumer to, int r, int g, int b, int a) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            to.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            float v = Math.max(red, Math.max(green, blue)) / 255.0F;
            to.setColor(Math.round(r * (0.4F + 0.6F * v)), Math.round(g * (0.4F + 0.6F * v)), Math.round(b * (0.4F + 0.6F * v)), a * alpha / 255);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            to.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            to.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            to.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16 & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            to.setNormal(x, y, z);
            return this;
        }
    }

    private static final VertexConsumer NOTHING = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };
}
