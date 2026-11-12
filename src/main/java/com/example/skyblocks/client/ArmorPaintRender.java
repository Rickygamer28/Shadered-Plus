package com.example.skyblocks.client;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Skies painted onto armour as it's worn (ModComponents.ArmorPaint; or, without one, its icon's skies spread over it):
 * over each painted pixel of the armour's texture, wherever the worn model shows that pixel, the pixel's sky - drawn
 * just outside the armour, after it (mixin/HumanoidArmorLayerPaintMixin), for every armour stand, player and mob
 * wearing it. Pixels redrawn at the altar are drawn over it the same way. The editor's 3D armour
 * (client/ShaderPaintScreen) is drawn and clicked on with the same model walk.
 */
public final class ArmorPaintRender {
    private ArmorPaintRender() {}

    /** The slots armour's worn in (that this paints). */
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD};
    /** How far out from the armour the skies, and the redrawn pixels, are drawn (blocks). */
    static final float SKY_LIFT = 0.0035F, COLOUR_LIFT = 0.0018F;
    /** Over this much of the icon painted, its sky covers the worn armour; under it, it trims each piece's edges. */
    static final float FULL = 0.55F;

    /** Which armour slot an item is worn in, if it's armour this can paint (else null). */
    @Nullable
    public static EquipmentSlot slotOf(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            for (EquipmentSlot s : SLOTS) {
                if (s == slot) {
                    return slot;
                }
            }
        }
        return null;
    }

    /** Which parts of the humanoid armour model a slot shows (as the game shows them). */
    static void showFor(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {}
        }
    }

    static ModelPart[] parts(HumanoidModel<?> model) {
        return new ModelPart[] {model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
    }

    // ---- the model's faces (ModelPart's cubes, their polygons: through reflection, found by their types) ----

    /** A face of the model: its 4 corners (model units, blocks: x y z each), their texture places (u v, 0 .. 1), its normal. */
    interface Faces {
        void face(PoseStack.Pose pose, float[] p, float[] uv, float nx, float ny, float nz);
    }

    private static Field cubesF, childrenF;
    private static final Map<Class<?>, Field[]> FIELDS = new HashMap<>();
    private static boolean ready, broken;

    private static boolean reflect() {
        if (ready || broken) {
            return ready;
        }
        try {
            for (Field f : ModelPart.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (List.class.isAssignableFrom(f.getType()) && cubesF == null) {
                    f.setAccessible(true);
                    cubesF = f;
                } else if (Map.class.isAssignableFrom(f.getType()) && childrenF == null) {
                    f.setAccessible(true);
                    childrenF = f;
                }
            }
            ready = cubesF != null && childrenF != null;
            broken = !ready;
        } catch (RuntimeException e) {
            broken = true;
        }
        if (broken) {
            SkyBlocksMod.LOGGER.warn("Shadered+ armour paint: couldn't look inside the game's models; worn armour is drawn plain");
        }
        return ready;
    }

    /** A class's own (not static) field holding an array of objects. */
    @Nullable
    private static Field arrayOf(Class<?> c) {
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType().isArray() && !f.getType().getComponentType().isPrimitive()) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    @Nullable
    private static Field ofType(Class<?> c, Class<?> type, String name) {
        Field found = null;
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
                if (found == null || f.getName().equals(name)) {
                    found = f;
                }
            }
        }
        if (found != null) {
            found.setAccessible(true);
        }
        return found;
    }

    /** A polygon's fields: [vertices, normal]; a vertex's: [pos, u, v] (cached by class). */
    @Nullable
    private static Field[] fields(Class<?> c, boolean vertex) {
        Field[] f = FIELDS.get(c);
        if (f == null && !FIELDS.containsKey(c)) {
            try {
                if (vertex) {
                    Field pos = ofType(c, Vector3f.class, "pos");
                    List<Field> floats = new ArrayList<>();
                    Field u = null, v = null;
                    for (Field x : c.getDeclaredFields()) {
                        if (!Modifier.isStatic(x.getModifiers()) && x.getType() == float.class) {
                            x.setAccessible(true);
                            floats.add(x);
                            if (x.getName().equals("u")) u = x;
                            if (x.getName().equals("v")) v = x;
                        }
                    }
                    if (u == null && floats.size() >= 2) {
                        u = floats.get(0);
                        v = floats.get(1);
                    }
                    f = pos != null && u != null && v != null ? new Field[] {pos, u, v} : null;
                } else {
                    Field verts = arrayOf(c), normal = ofType(c, Vector3f.class, "normal");
                    f = verts != null && normal != null ? new Field[] {verts, normal} : null;
                }
            } catch (RuntimeException e) {
                f = null;
            }
            FIELDS.put(c, f);
        }
        return f;
    }

    /** Each face of a part and its children (if shown), placed as the part is drawn. */
    static void walk(PoseStack ps, ModelPart part, Faces out) {
        if (!part.visible || !reflect()) {
            return;
        }
        boolean pushed = false;
        try {
            List<?> cubes = (List<?>) cubesF.get(part);
            Map<?, ?> children = (Map<?, ?>) childrenF.get(part);
            if (cubes.isEmpty() && children.isEmpty()) {
                return;
            }
            ps.pushPose();
            pushed = true;
            part.translateAndRotate(ps);
            PoseStack.Pose pose = ps.last();
            for (Object cube : cubes) {
                Field polysF = cube == null ? null : polys(cube.getClass());
                Object[] polys = polysF == null ? null : (Object[]) polysF.get(cube);
                if (polys == null) {
                    continue;
                }
                for (Object poly : polys) {
                    Field[] pf = poly == null ? null : fields(poly.getClass(), false);
                    if (pf == null) {
                        continue;
                    }
                    Object[] verts = (Object[]) pf[0].get(poly);
                    Vector3f n = (Vector3f) pf[1].get(poly);
                    if (verts == null || verts.length != 4 || n == null) {
                        continue;
                    }
                    float[] p = new float[12], uv = new float[8];
                    boolean ok = true;
                    for (int k = 0; k < 4 && ok; k++) {
                        Field[] vf = verts[k] == null ? null : fields(verts[k].getClass(), true);
                        if (vf == null) {
                            ok = false;
                            break;
                        }
                        Vector3f pos = (Vector3f) vf[0].get(verts[k]);
                        p[k * 3] = pos.x() / 16.0F;
                        p[k * 3 + 1] = pos.y() / 16.0F;
                        p[k * 3 + 2] = pos.z() / 16.0F;
                        uv[k * 2] = vf[1].getFloat(verts[k]);
                        uv[k * 2 + 1] = vf[2].getFloat(verts[k]);
                    }
                    if (ok) {
                        out.face(pose, p, uv, n.x(), n.y(), n.z());
                    }
                }
            }
            for (Object child : children.values()) {
                if (child instanceof ModelPart c) {
                    walk(ps, c, out);
                }
            }
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException | IllegalArgumentException e) {
            broken = true;
            ready = false;
            SkyBlocksMod.LOGGER.warn("Shadered+ armour paint: couldn't read a model ({}); worn armour is drawn plain", e.toString());
        } finally {
            if (pushed) {
                ps.popPose();
            }
        }
    }

    private static final Map<Class<?>, Optional<Field>> POLYS = new HashMap<>();

    @Nullable
    private static Field polys(Class<?> cube) {
        return POLYS.computeIfAbsent(cube, c -> Optional.ofNullable(arrayOf(c))).orElse(null);
    }

    /**
     * A face, made sense of: its corners (placed, lifted out along its normal) in the order of its texture's corners -
     * 00 (least u, least v), 10, 01, 11 - and the pixels it shows (tx0 .. tx1, ty0 .. ty1 of a w x h texture). Null if
     * it's flat to nothing.
     */
    @Nullable
    static float[][] corners(PoseStack.Pose pose, float[] p, float[] uv, float nx, float ny, float nz, float lift, int w, int h, int[] rect) {
        float umin = Math.min(Math.min(uv[0], uv[2]), Math.min(uv[4], uv[6])), umax = Math.max(Math.max(uv[0], uv[2]), Math.max(uv[4], uv[6]));
        float vmin = Math.min(Math.min(uv[1], uv[3]), Math.min(uv[5], uv[7])), vmax = Math.max(Math.max(uv[1], uv[3]), Math.max(uv[5], uv[7]));
        rect[0] = Math.round(umin * w);
        rect[1] = Math.round(vmin * h);
        rect[2] = Math.round(umax * w);
        rect[3] = Math.round(vmax * h);
        if (rect[2] <= rect[0] || rect[3] <= rect[1]) {
            return null;
        }
        float um = (umin + umax) * 0.5F, vm = (vmin + vmax) * 0.5F;
        float[][] c = new float[4][];
        Matrix4f m = pose.pose();
        Vector3f t = new Vector3f();
        for (int k = 0; k < 4; k++) {
            int i = (uv[k * 2] > um ? 1 : 0) + (uv[k * 2 + 1] > vm ? 2 : 0);
            if (c[i] != null) {
                return null;
            }
            m.transformPosition(p[k * 3] + nx * lift, p[k * 3 + 1] + ny * lift, p[k * 3 + 2] + nz * lift, t);
            c[i] = new float[] {t.x, t.y, t.z};
        }
        return c;
    }

    /**
     * How far past its edges a face's paint reaches (in s, t), to meet its neighbours': each face's paint is lifted
     * off the armour along its own normal, so at the armour's edges two faces' paint would leave a sliver between them
     * where the armour itself shows (a line along every edge). [s, t]
     */
    static float[] overhang(PoseStack.Pose pose, float[][] c, float nx, float ny, float nz, float lift) {
        Vector3f d = pose.pose().transformDirection(nx * lift, ny * lift, nz * lift, new Vector3f());
        float out = d.length() * 1.6F;
        float ls = (float) Math.sqrt(sq(c[1][0] - c[0][0]) + sq(c[1][1] - c[0][1]) + sq(c[1][2] - c[0][2]));
        float lt = (float) Math.sqrt(sq(c[2][0] - c[0][0]) + sq(c[2][1] - c[0][1]) + sq(c[2][2] - c[0][2]));
        return new float[] {ls > 1.0E-6F ? out / ls : 0.0F, lt > 1.0E-6F ? out / lt : 0.0F};
    }

    private static float sq(float x) {
        return x * x;
    }

    /** Where (s, t: 0 .. 1 across, down) is on a face (its corners, from corners()). */
    static void at(float[][] c, float s, float t, float[] out) {
        for (int i = 0; i < 3; i++) {
            float top = c[0][i] + (c[1][i] - c[0][i]) * s, bottom = c[2][i] + (c[3][i] - c[2][i]) * s;
            out[i] = top + (bottom - top) * t;
        }
    }

    /** The style on a texture pixel, on a face showing pixels rect (tx0, ty0, tx1, ty1); 0: none. */
    interface Styles {
        int at(int x, int y, int[] rect);
    }

    /**
     * The skies over the shown parts of a model: each run of pixels of one style on each face, as a quad of its sky
     * (shaders/core/sky_paint, through the style's look), drawn now into buffers.
     */
    static void drawSkies(PoseStack ps, HumanoidModel<?> model, int w, int h, Styles styles, MultiBufferSource.BufferSource buffers) {
        Map<Integer, List<float[]>> quads = new LinkedHashMap<>();
        int[] rect = new int[4];
        float[] a = new float[3], b = new float[3], c = new float[3], d = new float[3];
        Faces faces = (pose, p, uv, nx, ny, nz) -> {
            float[][] k = corners(pose, p, uv, nx, ny, nz, SKY_LIFT, w, h, rect);
            if (k == null) {
                return;
            }
            float[] over = overhang(pose, k, nx, ny, nz, SKY_LIFT);
            int tw = rect[2] - rect[0], th = rect[3] - rect[1];
            for (int y = rect[1]; y < rect[3]; y++) {
                int x = rect[0];
                while (x < rect[2]) {
                    int s = styles.at(x, y, rect);
                    if (s == 0) {
                        x++;
                        continue;
                    }
                    int end = x + 1;
                    while (end < rect[2] && styles.at(end, y, rect) == s) {
                        end++;
                    }
                    float s0 = (x - rect[0]) / (float) tw, s1 = (end - rect[0]) / (float) tw, t0 = (y - rect[1]) / (float) th, t1 = (y + 1 - rect[1]) / (float) th;
                    // (at the face's edges: a little past them, over the armour's edge)
                    if (x == rect[0]) s0 -= over[0];
                    if (end == rect[2]) s1 += over[0];
                    if (y == rect[1]) t0 -= over[1];
                    if (y + 1 == rect[3]) t1 += over[1];
                    at(k, s0, t0, a);
                    at(k, s1, t0, b);
                    at(k, s1, t1, c);
                    at(k, s0, t1, d);
                    quads.computeIfAbsent(s, z -> new ArrayList<>()).add(new float[] {a[0], a[1], a[2], s0, t0, b[0], b[1], b[2], s1, t0,
                            c[0], c[1], c[2], s1, t1, d[0], d[1], d[2], s0, t1, tw});
                    x = end;
                }
            }
        };
        for (ModelPart part : parts(model)) {
            walk(ps, part, faces);
        }
        Matrix4f id = new Matrix4f();
        for (Map.Entry<Integer, List<float[]>> e : quads.entrySet()) {
            RenderType type = ShaderPaintRender.type(e.getKey());
            if (type == null) {
                continue;
            }
            VertexConsumer vc = buffers.getBuffer(type);
            for (float[] q : e.getValue()) {
                for (int i = 0; i < 4; i++) {
                    ShaderPaintRender.vertex(vc, id, q[i * 5], q[i * 5 + 1], q[i * 5 + 2], q[i * 5 + 3], q[i * 5 + 4], e.getKey(), (int) q[20]);
                }
            }
            buffers.endBatch(type);
        }
    }

    /** The shown parts of a model with a texture (armour's way: lit, cut out), just outside where they are; drawn now. */
    static void drawTextured(PoseStack ps, HumanoidModel<?> model, ResourceLocation texture, int light, MultiBufferSource.BufferSource buffers) {
        RenderType type = RenderType.armorCutoutNoCull(texture);
        VertexConsumer vc = buffers.getBuffer(type);
        Vector3f n = new Vector3f();
        int[] rect = new int[4];
        float[] at = new float[3];
        Faces faces = (pose, p, uv, nx, ny, nz) -> {
            float[][] c = corners(pose, p, uv, nx, ny, nz, COLOUR_LIFT, 1024, 1024, rect);
            if (c == null) {
                return;
            }
            // (stretched a little past its edges, as the skies are, so no sliver of the armour shows there)
            float[] over = overhang(pose, c, nx, ny, nz, COLOUR_LIFT);
            pose.normal().transform(nx, ny, nz, n);
            float um = (uv[0] + uv[2] + uv[4] + uv[6]) * 0.25F, vm = (uv[1] + uv[3] + uv[5] + uv[7]) * 0.25F;
            for (int k = 0; k < 4; k++) {
                float s = uv[k * 2] > um ? 1.0F + over[0] : -over[0], t = uv[k * 2 + 1] > vm ? 1.0F + over[1] : -over[1];
                at(c, s, t, at);
                vc.addVertex(at[0], at[1], at[2]).setColor(-1).setUv(uv[k * 2], uv[k * 2 + 1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(n.x, n.y, n.z);
            }
        };
        for (ModelPart part : parts(model)) {
            walk(ps, part, faces);
        }
        buffers.endBatch(type);
    }

    /**
     * The faces of a model on screen, to find which pixel the mouse is on: each its corners (00, 10, 01, 11: x y z) and
     * the pixels it shows (of a w x h texture) - 16 floats.
     */
    static List<float[]> faces(PoseStack ps, HumanoidModel<?> model, int w, int h) {
        List<float[]> out = new ArrayList<>();
        int[] rect = new int[4];
        Faces faces = (pose, p, uv, nx, ny, nz) -> {
            float[][] k = corners(pose, p, uv, nx, ny, nz, SKY_LIFT, w, h, rect);
            if (k != null) {
                out.add(new float[] {k[0][0], k[0][1], k[0][2], k[1][0], k[1][1], k[1][2], k[2][0], k[2][1], k[2][2], k[3][0], k[3][1], k[3][2],
                        rect[0], rect[1], rect[2], rect[3]});
            }
        };
        for (ModelPart part : parts(model)) {
            walk(ps, part, faces);
        }
        return out;
    }

    /** The pixel rectangles (tx0, ty0, tx1, ty1 of a w x h texture) the shown parts of a model use. */
    static List<int[]> rects(HumanoidModel<?> model, int w, int h) {
        List<int[]> out = new ArrayList<>();
        PoseStack ps = new PoseStack();
        Faces faces = (pose, p, uv, nx, ny, nz) -> {
            int[] rect = new int[4];
            if (corners(pose, p, uv, nx, ny, nz, 0.0F, w, h, rect) != null) {
                out.add(rect);
            }
        };
        for (ModelPart part : parts(model)) {
            walk(ps, part, faces);
        }
        return out;
    }

    static boolean border(int x, int y, int[] rect) {
        return x == rect[0] || y == rect[1] || x == rect[2] - 1 || y == rect[3] - 1;
    }

    // ---- the armour's own texture ----

    /** An armour texture's pixels (ARGB). */
    record Sheet(int w, int h, int[] argb) {
        boolean opaque(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h && (argb[y * w + x] >>> 24) > 16;
        }

        int at(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h ? argb[y * w + x] : 0;
        }
    }

    private static final Map<ResourceLocation, Optional<Sheet>> SHEETS = new HashMap<>();

    @Nullable
    static Sheet sheet(@Nullable ResourceLocation texture) {
        if (texture == null) {
            return null;
        }
        return SHEETS.computeIfAbsent(texture, rl -> {
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(rl);
                if (res.isEmpty()) {
                    return Optional.empty();
                }
                try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    int w = img.getWidth(), h = img.getHeight();
                    int[] argb = new int[w * h];
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int abgr = img.getPixelRGBA(x, y);
                            argb[y * w + x] = (abgr & 0xFF00FF00) | (abgr & 0xFF) << 16 | (abgr >> 16) & 0xFF;
                        }
                    }
                    return Optional.of(new Sheet(w, h, argb));
                }
            } catch (Exception e) {
                SkyBlocksMod.LOGGER.warn("Shadered+ armour paint: couldn't read {}: {}", rl, e.toString());
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static Method hookTexture;
    private static boolean noHook;

    /** The texture an armour piece's worn with (its first layer): as NeoForge works it out (mods can change it), else its own. */
    @Nullable
    static ResourceLocation texture(@Nullable LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
        if (!(stack.getItem() instanceof ArmorItem armor)) {
            return null;
        }
        List<ArmorMaterial.Layer> layers = armor.getMaterial().value().layers();
        if (layers.isEmpty()) {
            return null;
        }
        ArmorMaterial.Layer layer = layers.get(0);
        boolean inner = slot == EquipmentSlot.LEGS;
        if (entity != null && !noHook) {
            try {
                if (hookTexture == null) {
                    for (Method m : net.neoforged.neoforge.client.ClientHooks.class.getMethods()) {
                        if (m.getName().equals("getArmorTexture") && m.getParameterCount() == 5 && m.getReturnType() == ResourceLocation.class
                                && m.getParameterTypes()[2] == ArmorMaterial.Layer.class) {
                            hookTexture = m;
                        }
                    }
                    noHook = hookTexture == null;
                }
                if (hookTexture != null) {
                    Object rl = hookTexture.invoke(null, entity, stack, layer, inner, slot);
                    if (rl instanceof ResourceLocation r) {
                        return r;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                noHook = true;
            }
        }
        return layer.texture(inner);
    }

    /** Whether the armour's first layer is dyed (leather): its colour, else -1. */
    static int tint(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            List<ArmorMaterial.Layer> layers = armor.getMaterial().value().layers();
            if (!layers.isEmpty() && layers.get(0).dyeable()) {
                return 0xFF000000 | net.minecraft.world.item.component.DyedItemColor.getOrDefault(stack, -6265536);
            }
        }
        return -1;
    }

    static int multiply(int argb, int tint) {
        if (tint == -1) {
            return argb;
        }
        int r = ((argb >> 16) & 255) * ((tint >> 16) & 255) / 255, g = ((argb >> 8) & 255) * ((tint >> 8) & 255) / 255, b = (argb & 255) * (tint & 255) / 255;
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    // ---- an icon's skies, spread over the worn armour ----

    /** The icon's sky (most of it), and whether it covers the armour (else trims its edges). */
    record Auto(int style, boolean full) {}

    /** From the skies on an icon (w x h) and which of its pixels are there: its sky, how much; null if none. */
    @Nullable
    static Auto auto(int[] styles, int w, int h, java.util.function.BiPredicate<Integer, Integer> solid) {
        Map<Integer, Integer> counts = new HashMap<>();
        int painted = 0, there = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int s = styles[y * w + x];
                if (solid.test(x, y)) {
                    there++;
                }
                if (s != 0) {
                    painted++;
                    counts.merge(s, 1, Integer::sum);
                }
            }
        }
        int best = 0, most = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        return best == 0 ? null : new Auto(best, painted >= FULL * Math.max(1, there));
    }

    private static final WeakHashMap<byte[], Optional<Auto>> AUTOS = new WeakHashMap<>();

    @Nullable
    private static Auto auto(ItemStack stack, ModComponents.ShaderPaint paint) {
        return AUTOS.computeIfAbsent(paint.pixels(), k -> {
            java.util.function.BiPredicate<Integer, Integer> solid = (x, y) -> true;
            ModComponents.ItemTexture tex = stack.get(ModComponents.ITEM_TEXTURE.get());
            if (tex != null && tex.w() == paint.w() && tex.h() == paint.h()) {
                solid = (x, y) -> (tex.at(x, y) >>> 24) > 16;
            } else {
                try {
                    ItemStack plain = stack.copy();
                    plain.remove(ModComponents.ITEM_TEXTURE.get());
                    BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(plain, null, null, 0);
                    TextureAtlasSprite sprite = ShaderPaintRender.sprite(model);
                    if (sprite != null && sprite.contents().width() == paint.w() && sprite.contents().height() == paint.h()) {
                        solid = (x, y) -> ShaderPaintRender.opaque(sprite, x, y);
                    }
                } catch (RuntimeException ignored) {
                    // (everything counted as there)
                }
            }
            return Optional.ofNullable(auto(paint.styles(), paint.w(), paint.h(), solid));
        }).orElse(null);
    }

    // ---- worn ----

    private static Field innerF, outerF;
    private static boolean noModels;
    private static int failures;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    private static HumanoidModel<?> model(HumanoidArmorLayer<?, ?, ?> layer, boolean inner) {
        if (noModels) {
            return null;
        }
        try {
            if (innerF == null) {
                List<Field> found = new ArrayList<>();
                for (Field f : HumanoidArmorLayer.class.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && HumanoidModel.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        found.add(f);
                        if (f.getName().equals("innerModel")) innerF = f;
                        if (f.getName().equals("outerModel")) outerF = f;
                    }
                }
                if ((innerF == null || outerF == null) && found.size() == 2) {
                    innerF = found.get(0);
                    outerF = found.get(1);
                }
                if (innerF == null || outerF == null) {
                    noModels = true;
                    return null;
                }
            }
            return (HumanoidModel<?>) (inner ? innerF : outerF).get(layer);
        } catch (ReflectiveOperationException | RuntimeException e) {
            noModels = true;
            return null;
        }
    }

    /** The redrawn pixels of painted armour, as textures (by their pixels: the same component, the same texture). */
    private static final LinkedHashMap<byte[], ResourceLocation> TEXTURES = new LinkedHashMap<>(16, 0.75F, true);
    private static int textureCount;

    private static ResourceLocation colours(ModComponents.ArmorPaint paint) {
        ResourceLocation rl = TEXTURES.get(paint.argb());
        if (rl != null) {
            return rl;
        }
        NativeImage img = new NativeImage(paint.w(), paint.h(), true);
        for (int y = 0; y < paint.h(); y++) {
            for (int x = 0; x < paint.w(); x++) {
                int c = paint.colour(x, y);
                int abgr = (c >>> 24) > 16 ? 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16) & 0xFF : 0;
                img.setPixelRGBA(x, y, abgr);
            }
        }
        rl = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "armor_paint/" + textureCount++);
        Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
        TEXTURES.put(paint.argb(), rl);
        if (TEXTURES.size() > 48) {
            var it = TEXTURES.entrySet().iterator();
            ResourceLocation old = it.next().getValue();
            it.remove();
            Minecraft.getInstance().getTextureManager().release(old);
        }
        return rl;
    }

    /** After the armour layer has drawn an entity's armour: the skies (and redrawn pixels) painted on it, over it. */
    public static void renderWorn(HumanoidArmorLayer<?, ?, ?> layer, PoseStack ps, MultiBufferSource buffers, int light, LivingEntity entity) {
        if (failures > 8 || buffers == null || entity == null) {
            return;
        }
        try {
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (stack.isEmpty() || slotOf(stack) != slot) {
                    continue;
                }
                ModComponents.ArmorPaint paint = stack.get(ModComponents.ARMOR_PAINT.get());
                ModComponents.ShaderPaint icon = paint == null ? stack.get(ModComponents.SHADER_PAINT.get()) : null;
                if (paint == null && icon == null) {
                    continue;
                }
                drawPiece(layer, ps, buffers, light, entity, stack, slot, paint, icon);
            }
        } catch (RuntimeException e) {
            if (failures++ == 0) {
                SkyBlocksMod.LOGGER.warn("Shadered+ armour paint: couldn't draw on worn armour", e);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void drawPiece(HumanoidArmorLayer<?, ?, ?> layer, PoseStack ps, MultiBufferSource buffers, int light, LivingEntity entity, ItemStack stack,
                                  EquipmentSlot slot, @Nullable ModComponents.ArmorPaint paint, @Nullable ModComponents.ShaderPaint icon) {
        HumanoidModel model = model(layer, slot == EquipmentSlot.LEGS);
        if (model == null || !(layer.getParentModel() instanceof HumanoidModel parent)) {
            return;
        }
        // (placed as the game placed it for this entity: its pose, then only this piece's parts)
        parent.copyPropertiesTo(model);
        showFor(model, slot);
        if (model.young) {
            return; // (a baby's parts are scaled as a whole: not worked out here)
        }
        Sheet sheet = sheet(texture(entity, stack, slot));
        MultiBufferSource.BufferSource now = DrawNow.now(buffers);
        if (paint != null) {
            if (paint.hasColours()) {
                drawTextured(ps, model, colours(paint), light, now);
            }
            boolean scaled = sheet != null && (sheet.w() != paint.w() || sheet.h() != paint.h());
            drawSkies(ps, model, paint.w(), paint.h(), (x, y, rect) -> {
                int s = paint.sky(x, y);
                // (a sky only where there's armour: its own pixel, or one drawn in)
                return s != 0 && (sheet == null || scaled || sheet.opaque(x, y) || (paint.colour(x, y) >>> 24) > 16) ? s : 0;
            }, now);
        } else if (sheet != null) {
            Auto auto = auto(stack, icon);
            if (auto != null) {
                drawSkies(ps, model, sheet.w(), sheet.h(), (x, y, rect) -> sheet.opaque(x, y) && (auto.full() || border(x, y, rect)) ? auto.style() : 0, now);
            }
        }
    }
}
