package com.example.skyblocks.client;

import com.example.skyblocks.InfusionAltarBlockEntity;
import com.example.skyblocks.ModComponents;
import com.example.skyblocks.ShaderPaintPayload;
import com.example.skyblocks.ShaderPaintStyles;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The infusion altar's editor, laid out like a paint program: the tools down the left (pencil, eraser, bucket fill,
 * picker, rectangle, line; brush size; undo / redo; grid; all / clear; back to the original), then the colours the
 * item's texture uses (pick one, or type any colour) to redraw the texture itself with - add pixels, recolour them, rub
 * them out - then the texture big in the middle to paint on, and on the right Shadered's sky blocks' skies to paint
 * onto it (each sky seen through the look chosen: normal, inverted, posterised, blueprint, game boy...) over the item
 * itself, turning, as it will look. Left mouse paints with the tool, right mouse rubs out. Quick styles under the
 * canvas fill it with the sky picked in a pattern (all of it, its edges, stripes, a checkerboard, top, bottom).
 * <p>Armour is painted as it's worn, too ("Worn"): its worn texture - the part its piece uses - on the armour itself in
 * 3D, on a player, to turn round (drag) and paint on (click), or flat. Until that's painted, the worn armour takes its
 * icon's skies, spread over it ("Auto": most of the icon painted, all over; less, round each piece's edges).
 */
public class ShaderPaintScreen extends Screen {
    private enum Tool { PENCIL, ERASER, FILL, PICKER, RECT, LINE }

    private final BlockPos pos;
    private ItemStack item = ItemStack.EMPTY;
    private TextureAtlasSprite sprite;
    private int w, h;
    /** The texture as it's being redrawn (ARGB), as it was, and the skies painted on it (styles: ShaderPaintStyles). */
    private int[] colours, original;
    private int[] skies;
    private ArrayDeque<Object[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Tool tool = Tool.PENCIL;
    /** Painting with a sky (else a colour), which, through which look; the colour. */
    private boolean skyBrush = true;
    private int sky, pass, colour = 0xFFFFFFFF, brush = 1;
    private boolean grid = true;
    // the canvas, the colours, the skies on screen
    private int cx, cy, cell;
    private int colX, colY, colCols = 4, colRows;
    /** The colour picker (a square of saturation and brightness, a hue bar beside it): open, where, and being dragged. */
    private boolean picking;
    private int pickX, pickY, pickDrag; // (pickDrag: 0 none, 1 the square, 2 the hue bar)
    private float hue, sat = 0.0F, val = 1.0F;
    private static final int PICK = 104, HUE_W = 12;
    /** The colours used lately (kept between visits to the altar), newest first. */
    private static final java.util.LinkedList<Integer> RECENT = new java.util.LinkedList<>();
    /** Where the shade ramp and the recent colours are (y). */
    private int shadeY, recentY;
    private int px0, py0, swatch = 18, cols = 5, rowsShown, scroll, colScroll;
    private final List<Integer> used = new ArrayList<>();
    private EditBox hex;
    private Button lookButton;
    // a stroke in progress
    private boolean dragging, erasing;
    private int startX = -1, startY = -1, hoverX = -1, hoverY = -1;
    private final Button[] toolButtons = new Button[Tool.values().length];
    private Component problem;

    // ---- armour, painted as it's worn ----

    /** A picture being painted: the icon's texture, or (armour) the part of its worn texture its piece uses. */
    private static final class Canvas {
        int w, h;
        int[] colours, original, skies;
        boolean[] region;
        ArrayDeque<Object[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    }

    private Canvas iconCanvas, wornCanvas, active;
    private boolean loaded;
    /** Which pixels can be painted (worn: those the piece shows), or null (all of them). */
    private boolean[] region;
    @Nullable
    private EquipmentSlot slot;
    /** Painting it as worn; flat (else in 3D); the worn armour left to take its icon's skies. */
    private boolean worn, flat, wornAuto;
    /** The whole worn texture (w x h, as worn: tinted, ARGB), and where the worn canvas is in it. */
    private int sheetW, sheetH, ox, oy;
    private int[] sheet;
    /** Each face's pixels (canvas places), and which face each worn canvas pixel is on (-1: none). */
    private List<int[]> rects = List.of();
    private int[] owner;
    private HumanoidModel<LivingEntity> armourModel, bodyModel;
    private ResourceLocation skin;
    private DynamicTexture editTexture;
    private static final ResourceLocation EDIT_TEXTURE = ResourceLocation.fromNamespaceAndPath(com.example.skyblocks.SkyBlocksMod.MOD_ID, "armor_edit");
    private float yaw = -28.0F, pitch = 10.0F;
    /** The armour's faces where they were last drawn on screen (ArmorPaintRender.faces). */
    private List<float[]> shown = List.of();
    private boolean turning, failed3D;
    /** The 3D view (x0, y0, x1, y1). */
    private int ax0, ay0, ax1, ay1;
    private static final String[] QUICK = {"full", "trim", "stripes", "checker", "top", "bottom"};
    /** (the render test: open as worn - 1: in 3D, 2: flat) */
    static int testStart;

    public ShaderPaintScreen(BlockPos pos) {
        super(Component.translatable("screen.skyblocks.shader_paint"));
        this.pos = pos;
    }

    private int skyCount() {
        return ShaderPaintRender.skies().length;
    }

    private int style() {
        return ShaderPaintStyles.of(sky, pass);
    }

    @Override
    protected void init() {
        if (!loaded) {
            loaded = true;
            load();
        }
        layout();
    }

    /** What's being painted: the item on the altar, its texture, its skies (and, armour, as it's worn). */
    private void load() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof InfusionAltarBlockEntity altar) {
            item = altar.item().copy();
        }
        problem = null;
        if (item.isEmpty()) {
            problem = Component.translatable("message.skyblocks.infusion_altar.empty");
        } else {
            ItemStack plain = item.copy();
            plain.remove(ModComponents.ITEM_TEXTURE.get());
            BakedModel model = mc.getItemRenderer().getModel(plain, mc.level, mc.player, 0);
            sprite = ShaderPaintRender.sprite(model);
            if (sprite == null || model.isCustomRenderer()) {
                problem = Component.translatable("screen.skyblocks.shader_paint.cannot");
            } else {
                w = sprite.contents().width();
                h = sprite.contents().height();
                if (w > 64 || h > 64) {
                    problem = Component.translatable("screen.skyblocks.shader_paint.too_big");
                }
            }
        }
        if (colours == null && problem == null) {
            original = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    original[y * w + x] = ShaderPaintRender.argb(sprite, x, y);
                }
            }
            colours = original.clone();
            ModComponents.ItemTexture tex = item.get(ModComponents.ITEM_TEXTURE.get());
            if (tex != null && tex.w() == w && tex.h() == h) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        colours[y * w + x] = tex.at(x, y);
                    }
                }
            }
            skies = new int[w * h];
            ModComponents.ShaderPaint had = item.get(ModComponents.SHADER_PAINT.get());
            if (had != null && had.w() == w && had.h() == h) {
                skies = had.styles();
            }
            refreshUsed();
            for (int c : used) {
                colour = c; // (start with the brightest it uses)
            }
            iconCanvas = new Canvas();
            active = iconCanvas;
            save();
            loadWorn(mc);
            if (testStart > 0 && wornCanvas != null) {
                flat = testStart == 2;
                switchTo(true, false);
            }
        }
    }

    private void layout() {
        // layout: tools | colours | canvas | skies + preview
        int left = 8, toolW = 76, colW = colCols * 14 + 8, right = cols * (swatch + 2) + 16;
        colX = left + toolW + 8;
        shadeY = 28 + 40;
        recentY = shadeY + 26;
        colY = recentY + 40;
        colRows = Math.max(3, (height - colY - 40) / 14);
        px0 = width - right - 4;
        py0 = 28;
        // the canvas (or the 3D armour) between the colours and the skies, the quick styles under it
        int areaX0 = colX + colW + 8, areaX1 = px0 - 8, top = worn ? 46 : 28, bottom = height - 62;
        cell = Math.max(2, Math.min((areaX1 - areaX0) / Math.max(1, w), (bottom - top) / Math.max(1, h)));
        cx = areaX0 + Math.max(0, (areaX1 - areaX0 - cell * w) / 2);
        cy = top + Math.max(0, (bottom - top - cell * h) / 2);
        ax0 = areaX0;
        ax1 = areaX1;
        ay0 = 46;
        ay1 = bottom;
        rowsShown = Math.max(3, Math.min((skyCount() + cols - 1) / cols, (height - py0 - 170) / (swatch + 2)));
        int y = 28;
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            Tool t = tools[i];
            toolButtons[i] = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.tool." + t.name().toLowerCase()), b -> {
                tool = t;
                refreshTools();
            }).bounds(left, y, toolW, 18).build());
            y += 20;
        }
        y += 4;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> brush = Math.max(1, brush - 1)).bounds(left, y, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> brush = Math.min(8, brush + 1)).bounds(left + toolW - 18, y, 18, 18).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.undo"), b -> undo()).bounds(left, y, toolW / 2 - 1, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.redo"), b -> redo()).bounds(left + toolW / 2 + 1, y, toolW / 2 - 1, 18).build());
        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.grid"), b -> grid = !grid).bounds(left, y, toolW, 18).build());
        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.all"), b -> skyAll(style()))
                .bounds(left, y, toolW / 2 - 1, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.clear"), b -> skyAll(0))
                .bounds(left + toolW / 2 + 1, y, toolW / 2 - 1, 18).build());
        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.original"), b -> {
            if (colours != null) {
                remember();
                colours = original.clone();
                refreshUsed();
            }
        }).bounds(left, y, toolW, 18).build());
        // the colour being painted with, typed in
        hex = new EditBox(font, colX, 28 + 22, colW, 14, Component.translatable("screen.skyblocks.shader_paint.colour"));
        hex.setMaxLength(7);
        showHex();
        hex.setResponder(s -> {
            if (showingHex) {
                return; // (it's us putting the colour in it: not typed)
            }
            String t = s.startsWith("#") ? s.substring(1) : s;
            if (t.length() == 6) {
                try {
                    colour = 0xFF000000 | Integer.parseInt(t, 16);
                    syncHsv();
                    skyBrush = false;
                } catch (NumberFormatException ignored) {
                    // (not a colour yet)
                }
            }
        });
        addRenderableWidget(hex);
        // the look the skies are seen through
        lookButton = addRenderableWidget(Button.builder(lookName(), b -> {
            pass = (pass + 1) % Math.min(ShaderPaintStyles.PASSES, SkyblockPass.values().length);
            b.setMessage(lookName());
            skyBrush = true;
        }).bounds(px0, py0 + rowsShown * (swatch + 2) + 4, cols * (swatch + 2) - 2, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.apply"), b -> apply())
                .bounds(width / 2 - 104, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 + 4, height - 26, 100, 20).build());
        if (problem == null) {
            // quick styles: the sky picked, in a pattern
            int bw = Math.max(24, Math.min(52, (ax1 - ax0) / QUICK.length - 2)), qx = (ax0 + ax1) / 2 - (bw + 2) * QUICK.length / 2;
            boolean narrow = bw < 46; // (short names where the full ones won't fit)
            for (int i = 0; i < QUICK.length; i++) {
                int kind = i;
                addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.quick." + QUICK[i] + (narrow ? ".short" : "")), b -> pattern(kind))
                        .tooltip(Tooltip.create(Component.translatable("screen.skyblocks.shader_paint.quick.tip")))
                        .bounds(qx + i * (bw + 2), height - 56, bw, 16).build());
            }
        }
        if (problem == null && slot != null) {
            // the icon, or as it's worn
            Button iconB = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.mode.icon"), b -> switchTo(false, true))
                    .bounds(8, 4, 37, 16).build());
            Button wornB = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.mode.worn"), b -> switchTo(true, true))
                    .tooltip(Tooltip.create(Component.translatable(wornCanvas != null ? "screen.skyblocks.shader_paint.mode.worn.tip"
                            : "screen.skyblocks.shader_paint.mode.worn.cannot"))).bounds(47, 4, 37, 16).build());
            iconB.active = worn;
            wornB.active = !worn && wornCanvas != null;
        }
        if (worn) {
            // (3D / flat and Auto up by Icon / Worn; front, side, back over the 3D armour, as wide as there's room for)
            addRenderableWidget(Button.builder(Component.translatable(flat ? "screen.skyblocks.shader_paint.view.3d" : "screen.skyblocks.shader_paint.view.flat"), b -> {
                flat = !flat;
                dragging = false;
                turning = false;
                rebuildWidgets();
            }).bounds(88, 2, 34, 14).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.auto"), b -> {
                remember();
                wornAuto = true;
                autoWorn();
                refreshUsed();
            }).tooltip(Tooltip.create(Component.translatable("screen.skyblocks.shader_paint.auto.tip"))).bounds(124, 2, 34, 14).build());
            if (!flat) {
                String[] views = {"front", "side", "back"};
                float[] yaws = {0.0F, 90.0F, 180.0F};
                int vw = Math.max(20, Math.min(40, (ax1 - ax0) / 3 - 2));
                for (int i = 0; i < 3; i++) {
                    float to = yaws[i];
                    addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.shader_paint.view." + views[i] + (vw < 34 ? ".short" : "")), b -> {
                        yaw = to;
                        pitch = 10.0F;
                    }).bounds(ax0 + i * (vw + 2), 28, vw, 14).build());
                }
            }
        }
        refreshTools();
    }

    // ---- armour, as it's worn ----

    /** Whether the armour's being painted in 3D. */
    private boolean view3D() {
        return worn && !flat && armourModel != null && !failed3D;
    }

    private void save() {
        if (active == null) {
            return;
        }
        active.w = w;
        active.h = h;
        active.colours = colours;
        active.original = original;
        active.skies = skies;
        active.region = region;
        active.undo = undo;
        active.redo = redo;
    }

    private void use(Canvas c) {
        active = c;
        w = c.w;
        h = c.h;
        colours = c.colours;
        original = c.original;
        skies = c.skies;
        region = c.region;
        undo = c.undo;
        redo = c.redo;
    }

    /** To painting the icon, or as worn. */
    private void switchTo(boolean toWorn, boolean rebuild) {
        if (toWorn && wornCanvas == null || toWorn == worn || iconCanvas == null) {
            return;
        }
        save();
        worn = toWorn;
        if (toWorn && wornAuto) {
            autoWorn(); // (the icon's skies as they are now)
        }
        use(toWorn ? wornCanvas : iconCanvas);
        dragging = false;
        turning = false;
        picking = false;
        hoverX = hoverY = -1;
        refreshUsed();
        if (rebuild) {
            rebuildWidgets();
        }
    }

    /** The worn armour's texture, model, and how it's painted (if it can be). */
    private void loadWorn(Minecraft mc) {
        slot = ArmorPaintRender.slotOf(item);
        if (slot == null) {
            return;
        }
        try {
            ArmorPaintRender.Sheet s = ArmorPaintRender.sheet(ArmorPaintRender.texture(mc.player, item, slot));
            if (s == null || s.w() * s.h() > 64 * 64) {
                return; // (none to paint; or too big to send)
            }
            armourModel = new HumanoidModel<>(mc.getEntityModels().bakeLayer(slot == EquipmentSlot.LEGS ? ModelLayers.PLAYER_INNER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR));
            boolean slim = mc.player != null && mc.player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
            bodyModel = new HumanoidModel<>(mc.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER));
            skin = mc.player != null ? mc.player.getSkin().texture() : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
            for (HumanoidModel<?> m : List.of(armourModel, bodyModel)) {
                m.young = false;
                m.rightArm.zRot = 0.1F; // (arms and legs a little apart: their insides can be seen, and painted)
                m.leftArm.zRot = -0.1F;
                m.rightLeg.zRot = 0.03F;
                m.leftLeg.zRot = -0.03F;
            }
            ArmorPaintRender.showFor(armourModel, slot);
            List<int[]> all = ArmorPaintRender.rects(armourModel, s.w(), s.h());
            if (all.isEmpty()) {
                armourModel = null;
                return;
            }
            // (just the part of the texture this piece uses)
            int x0 = s.w(), y0 = s.h(), x1 = 0, y1 = 0;
            for (int[] r : all) {
                x0 = Math.min(x0, Math.max(0, r[0]));
                y0 = Math.min(y0, Math.max(0, r[1]));
                x1 = Math.max(x1, Math.min(s.w(), r[2]));
                y1 = Math.max(y1, Math.min(s.h(), r[3]));
            }
            if (x1 <= x0 || y1 <= y0) {
                armourModel = null;
                return;
            }
            sheetW = s.w();
            sheetH = s.h();
            ox = x0;
            oy = y0;
            int tint = ArmorPaintRender.tint(item);
            sheet = new int[sheetW * sheetH];
            for (int i = 0; i < sheet.length; i++) {
                sheet[i] = ArmorPaintRender.multiply(s.argb()[i], tint);
            }
            Canvas c = new Canvas();
            c.w = x1 - x0;
            c.h = y1 - y0;
            c.region = new boolean[c.w * c.h];
            owner = new int[c.w * c.h];
            java.util.Arrays.fill(owner, -1);
            List<int[]> local = new ArrayList<>();
            for (int r = 0; r < all.size(); r++) {
                int[] q = all.get(r);
                int[] l = {q[0] - ox, q[1] - oy, q[2] - ox, q[3] - oy};
                local.add(l);
                for (int y = Math.max(0, l[1]); y < Math.min(c.h, l[3]); y++) {
                    for (int x = Math.max(0, l[0]); x < Math.min(c.w, l[2]); x++) {
                        c.region[y * c.w + x] = true;
                        if (owner[y * c.w + x] < 0) {
                            owner[y * c.w + x] = r;
                        }
                    }
                }
            }
            rects = local;
            c.original = new int[c.w * c.h];
            for (int y = 0; y < c.h; y++) {
                for (int x = 0; x < c.w; x++) {
                    c.original[y * c.w + x] = sheet[(y + oy) * sheetW + x + ox];
                }
            }
            c.colours = c.original.clone();
            c.skies = new int[c.w * c.h];
            ModComponents.ArmorPaint had = item.get(ModComponents.ARMOR_PAINT.get());
            if (had != null && had.w() == sheetW && had.h() == sheetH) {
                for (int y = 0; y < c.h; y++) {
                    for (int x = 0; x < c.w; x++) {
                        int i = y * c.w + x;
                        if (!c.region[i]) {
                            continue;
                        }
                        c.skies[i] = had.sky(x + ox, y + oy);
                        int col = had.colour(x + ox, y + oy);
                        if ((col >>> 24) > 16) {
                            c.colours[i] = col | 0xFF000000;
                        }
                    }
                }
                wornAuto = false;
            } else {
                wornAuto = true;
            }
            wornCanvas = c;
        } catch (RuntimeException e) {
            com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Shadered+ infusion altar: couldn't set up painting {} as worn", item, e);
            wornCanvas = null;
            armourModel = null;
        }
    }

    /** The worn armour, from the icon's skies as they are now: its sky all over (most of the icon painted), or round its edges. */
    private void autoWorn() {
        Canvas ic = iconCanvas, wc = wornCanvas;
        if (ic == null || wc == null) {
            return;
        }
        ArmorPaintRender.Auto a = ArmorPaintRender.auto(ic.skies, ic.w, ic.h, (x, y) -> (ic.colours[y * ic.w + x] >>> 24) > 16);
        for (int y = 0; y < wc.h; y++) {
            for (int x = 0; x < wc.w; x++) {
                int i = y * wc.w + x;
                wc.colours[i] = wc.original[i];
                int r = owner[i];
                boolean on = a != null && wc.region[i] && (wc.original[i] >>> 24) > 16
                        && (a.full() || r >= 0 && r < rects.size() && ArmorPaintRender.border(x, y, rects.get(r)));
                wc.skies[i] = on ? a.style() : 0;
            }
        }
    }

    /** The worn canvas as the armour's paint: the whole texture's size, the skies, the pixels redrawn (if any). */
    private ModComponents.ArmorPaint armourPaint() {
        Canvas c = wornCanvas;
        int[] styles = new int[sheetW * sheetH];
        int[] argb = null;
        for (int y = 0; y < c.h; y++) {
            for (int x = 0; x < c.w; x++) {
                int i = y * c.w + x, j = (y + oy) * sheetW + x + ox;
                if (!c.region[i]) {
                    continue;
                }
                styles[j] = c.skies[i];
                if ((c.colours[i] >>> 24) > 16 && (c.colours[i] | 0xFF000000) != (c.original[i] | 0xFF000000)) {
                    if (argb == null) {
                        argb = new int[sheetW * sheetH];
                    }
                    argb[j] = c.colours[i] | 0xFF000000;
                }
            }
        }
        return ModComponents.ArmorPaint.of(sheetW, sheetH, styles, argb);
    }

    /** The sky on a pixel of the whole worn texture (0: none). */
    private int wornSky(int x, int y) {
        Canvas c = wornCanvas;
        int lx = x - ox, ly = y - oy;
        if (c == null || lx < 0 || ly < 0 || lx >= c.w || ly >= c.h || !c.region[ly * c.w + lx]) {
            return 0;
        }
        return c.skies[ly * c.w + lx];
    }

    /** The worn armour's texture as it's being redrawn, for the 3D armour. */
    private void uploadEdit() {
        Minecraft mc = Minecraft.getInstance();
        if (editTexture == null) {
            editTexture = new DynamicTexture(sheetW, sheetH, true);
            mc.getTextureManager().register(EDIT_TEXTURE, editTexture);
        }
        NativeImage img = editTexture.getPixels();
        if (img == null) {
            return;
        }
        Canvas c = wornCanvas;
        for (int y = 0; y < sheetH; y++) {
            for (int x = 0; x < sheetW; x++) {
                int a = sheet[y * sheetW + x], lx = x - ox, ly = y - oy;
                if (lx >= 0 && ly >= 0 && lx < c.w && ly < c.h) {
                    a = c.colours[ly * c.w + lx];
                }
                img.setPixelRGBA(x, y, (a >>> 24) > 16 ? (a & 0xFF000000) | (a & 0xFF) << 16 | (a & 0xFF00) | (a >> 16) & 0xFF : 0);
            }
        }
        editTexture.upload();
    }

    @Override
    public void removed() {
        super.removed();
        if (editTexture != null) {
            Minecraft.getInstance().getTextureManager().release(EDIT_TEXTURE);
            editTexture = null;
        }
    }

    /** The pixel of the worn canvas the mouse is on in 3D (x, y), or null: the nearest face under it. */
    @Nullable
    private int[] pick(double mx, double my) {
        float best = -Float.MAX_VALUE;
        int[] hit = null;
        for (float[] f : shown) {
            float ex = f[3] - f[0], ey = f[4] - f[1], fx = f[6] - f[0], fy = f[7] - f[1];
            float det = ex * fy - ey * fx;
            if (Math.abs(det) < 0.01F) {
                continue; // (edge on)
            }
            float dx = (float) mx - f[0], dy = (float) my - f[1];
            float s = (dx * fy - dy * fx) / det, t = (ex * dy - ey * dx) / det;
            if (s < 0.0F || s >= 1.0F || t < 0.0F || t >= 1.0F) {
                continue;
            }
            float z = f[2] + s * (f[5] - f[2]) + t * (f[8] - f[2]);
            if (z > best) {
                int tx = Math.min((int) f[14] - 1, (int) (f[12] + s * (f[14] - f[12]))), ty = Math.min((int) f[15] - 1, (int) (f[13] + t * (f[15] - f[13])));
                best = z;
                hit = new int[] {tx - ox, ty - oy};
            }
        }
        return hit != null && onCanvas(hit[0], hit[1]) ? hit : null;
    }

    /** Where the 3D view's centred (height on the model, blocks) and how near, for each piece. */
    private float[] focus() {
        return switch (slot == null ? EquipmentSlot.CHEST : slot) {
            case HEAD -> new float[] {-0.25F, 2.3F};
            case LEGS -> new float[] {0.95F, 1.35F};
            case FEET -> new float[] {1.28F, 2.1F};
            default -> new float[] {0.38F, 1.45F};
        };
    }

    private void draw3D(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(ax0, ay0, ax1, ay1, 0x50000000);
        g.renderOutline(ax0 - 1, ay0 - 1, ax1 - ax0 + 2, ay1 - ay0 + 2, 0xFF8866CC);
        float[] focus = focus();
        float scale = Math.min((ay1 - ay0) / 2.2F, (ax1 - ax0) / 1.3F) * focus[1];
        PoseStack ps = g.pose();
        g.enableScissor(ax0, ay0, ax1, ay1);
        ps.pushPose();
        try {
            uploadEdit();
            ps.translate((ax0 + ax1) / 2.0F, (ay0 + ay1) / 2.0F, 150.0F);
            ps.mulPose(Axis.XP.rotationDegrees(-pitch));
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            ps.scale(scale, scale, -scale);
            ps.translate(0.0F, -focus[0], 0.0F);
            Lighting.setupForEntityInInventory();
            MultiBufferSource.BufferSource buffers = g.bufferSource();
            if (bodyModel != null && skin != null) {
                bodyModel.renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(skin)), 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            }
            ArmorPaintRender.showFor(armourModel, slot);
            armourModel.renderToBuffer(ps, buffers.getBuffer(RenderType.armorCutoutNoCull(EDIT_TEXTURE)), 0xF000F0, OverlayTexture.NO_OVERLAY, -1);
            buffers.endBatch();
            ArmorPaintRender.drawSkies(ps, armourModel, sheetW, sheetH, (x, y, rect) -> wornSky(x, y), buffers);
            shown = ArmorPaintRender.faces(ps, armourModel, sheetW, sheetH);
        } catch (RuntimeException e) {
            failed3D = true; // (flat from now on)
            shown = List.of();
            com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Shadered+ infusion altar: couldn't draw the armour in 3D", e);
        } finally {
            ps.popPose();
            Lighting.setupFor3DItems();
        }
        // the pixel the mouse is on
        int[] at = pick(mouseX, mouseY);
        if (at != null && mouseX >= ax0 && mouseX < ax1 && mouseY >= ay0 && mouseY < ay1) {
            hoverX = at[0];
            hoverY = at[1];
            float[] f = face(at[0] + ox, at[1] + oy, mouseX, mouseY);
            if (f != null) {
                float rw = f[14] - f[12], rh = f[15] - f[13];
                float s0 = (at[0] + ox - f[12]) / rw, s1 = s0 + 1.0F / rw, t0 = (at[1] + oy - f[13]) / rh, t1 = t0 + 1.0F / rh;
                var m = g.pose().last().pose();
                var vc = g.bufferSource().getBuffer(RenderType.gui());
                int c = tool == Tool.ERASER ? 0x90FF6060 : 0x90FFFFFF;
                float[][] q = {{s0, t0}, {s0, t1}, {s1, t1}, {s1, t0}};
                for (float[] st : q) {
                    vc.addVertex(m, f[0] + st[0] * (f[3] - f[0]) + st[1] * (f[6] - f[0]), f[1] + st[0] * (f[4] - f[1]) + st[1] * (f[7] - f[1]), 290.0F).setColor(c);
                }
                g.flush();
            }
        } else {
            hoverX = hoverY = -1;
        }
        g.disableScissor();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        if (wornAuto) {
            g.drawString(font, Component.translatable("screen.skyblocks.shader_paint.auto.on"), ax0 + 4, ay0 + 4, 0xFFFFE080);
        }
        Component turn = Component.translatable("screen.skyblocks.shader_paint.turn");
        if (ax1 - ax0 > font.width(turn) + 16) {
            g.drawString(font, turn, ax1 - 4 - font.width(turn), ay1 - 12, 0xA0C0B0E0);
        }
        g.pose().popPose();
    }

    /** The nearest face under the mouse showing that pixel (of the whole worn texture). */
    @Nullable
    private float[] face(int tx, int ty, double mx, double my) {
        float best = -Float.MAX_VALUE;
        float[] found = null;
        for (float[] f : shown) {
            if (tx < f[12] || tx >= f[14] || ty < f[13] || ty >= f[15]) {
                continue;
            }
            float ex = f[3] - f[0], ey = f[4] - f[1], fx = f[6] - f[0], fy = f[7] - f[1];
            float det = ex * fy - ey * fx;
            if (Math.abs(det) < 0.01F) {
                continue;
            }
            float dx = (float) mx - f[0], dy = (float) my - f[1];
            float s = (dx * fy - dy * fx) / det, t = (ex * dy - ey * dx) / det;
            if (s < -0.01F || s > 1.01F || t < -0.01F || t > 1.01F) {
                continue;
            }
            float z = f[2] + s * (f[5] - f[2]) + t * (f[8] - f[2]);
            if (z > best) {
                best = z;
                found = f;
            }
        }
        return found;
    }

    /** A quick style: the sky picked, over the pixels in a pattern (worn: of each face), the rest cleared. */
    private void pattern(int kind) {
        if (skies == null) {
            return;
        }
        remember();
        int st = style();
        skyBrush = true;
        int bx0 = w, by0 = h, bx1 = 0, by1 = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (opaque(x, y)) {
                    bx0 = Math.min(bx0, x);
                    by0 = Math.min(by0, y);
                    bx1 = Math.max(bx1, x + 1);
                    by1 = Math.max(by1, y + 1);
                }
            }
        }
        int[] box = {bx0, by0, bx1, by1};
        int sx = worn ? ox : 0, sy = worn ? oy : 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (region != null && !region[i]) {
                    continue;
                }
                if (!opaque(x, y)) {
                    skies[i] = 0;
                    continue;
                }
                int[] r = worn && owner != null && owner[i] >= 0 && owner[i] < rects.size() ? rects.get(owner[i]) : box;
                boolean edge = worn ? ArmorPaintRender.border(x, y, r) : !opaque(x - 1, y) || !opaque(x + 1, y) || !opaque(x, y - 1) || !opaque(x, y + 1);
                int ly = y - r[1], rh = Math.max(1, r[3] - r[1]);
                boolean on = switch (kind) {
                    case 0 -> true;
                    case 1 -> edge;
                    case 2 -> ((x + sx + y + sy) >> 1 & 1) == 0;
                    case 3 -> (((x + sx) >> 1) + ((y + sy) >> 1) & 1) == 0;
                    case 4 -> ly * 2 < rh;
                    default -> ly * 2 >= rh;
                };
                skies[i] = on ? st : 0;
            }
        }
    }

    private Component lookName() {
        return Component.translatable("screen.skyblocks.shader_paint.look", SkyblockPass.values()[pass].getDisplayName());
    }

    private void refreshTools() {
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            if (toolButtons[i] != null) {
                toolButtons[i].active = tools[i] != tool;
            }
        }
    }

    /** The colours the texture uses now, darkest first. */
    private void refreshUsed() {
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        for (int c : colours) {
            if ((c >>> 24) > 16) {
                set.add(c | 0xFF000000);
            }
        }
        used.clear();
        used.addAll(set);
        used.sort((a, b) -> Integer.compare(luma(a), luma(b)));
        colScroll = Math.max(0, Math.min(colScroll, (used.size() + colCols - 1) / colCols - colRows));
    }

    private static int luma(int c) {
        return ((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255);
    }

    // ---- painting ----

    private boolean opaque(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && (colours[y * w + x] >>> 24) > 16;
    }

    /** One pixel: the brush there (a sky only on a pixel that's there; a colour anywhere), or rubbed out. */
    private void set(int x, int y, boolean rubOut) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return;
        }
        int i = y * w + x;
        if (region != null && !region[i]) {
            return; // (a pixel the worn piece doesn't show)
        }
        if (skyBrush) {
            if (rubOut) {
                skies[i] = 0;
            } else if (opaque(x, y)) {
                skies[i] = style();
            }
        } else if (rubOut && worn) {
            colours[i] = original[i]; // (worn armour's own pixels can't be taken away: back to them)
            if ((original[i] >>> 24) <= 16) {
                skies[i] = 0;
            }
        } else if (rubOut) {
            colours[i] = 0;
            skies[i] = 0;
        } else {
            colours[i] = colour | 0xFF000000;
        }
    }

    private void dab(int x, int y, boolean rubOut) {
        int r0 = -(brush - 1) / 2;
        for (int dy = 0; dy < brush; dy++) {
            for (int dx = 0; dx < brush; dx++) {
                set(x + r0 + dx, y + r0 + dy, rubOut);
            }
        }
    }

    private void line(int x0, int y0, int x1, int y1, boolean rubOut) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            dab(x0, y0, rubOut);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private void rect(int x0, int y0, int x1, int y1, boolean rubOut) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                set(x, y, rubOut);
            }
        }
    }

    /** Fills the patch of one colour round (x, y) (with shift: that colour everywhere) - empty space too, with a colour. */
    private void fill(int x, int y, boolean rubOut, boolean everywhere) {
        int target = colours[y * w + x];
        boolean empty = (target >>> 24) <= 16;
        if (empty && (skyBrush || rubOut)) {
            return;
        }
        boolean[] seen = new boolean[w * h];
        ArrayDeque<int[]> todo = new ArrayDeque<>();
        List<int[]> patch = new ArrayList<>();
        if (everywhere) {
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    if (same(colours[j * w + i], target)) {
                        patch.add(new int[] {i, j});
                    }
                }
            }
        } else {
            todo.add(new int[] {x, y});
            seen[y * w + x] = true;
            while (!todo.isEmpty()) {
                int[] p = todo.poll();
                patch.add(p);
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = p[0] + d[0], ny = p[1] + d[1];
                    if (nx >= 0 && ny >= 0 && nx < w && ny < h && !seen[ny * w + nx] && same(colours[ny * w + nx], target)) {
                        seen[ny * w + nx] = true;
                        todo.add(new int[] {nx, ny});
                    }
                }
            }
        }
        for (int[] p : patch) {
            set(p[0], p[1], rubOut);
        }
    }

    private static boolean same(int a, int b) {
        boolean ea = (a >>> 24) <= 16, eb = (b >>> 24) <= 16;
        return ea || eb ? ea == eb : (a | 0xFF000000) == (b | 0xFF000000);
    }

    private void skyAll(int s) {
        if (skies == null) {
            return;
        }
        remember();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (region != null && !region[y * w + x]) {
                    continue;
                }
                if (s == 0 || opaque(x, y)) {
                    skies[y * w + x] = s;
                }
            }
        }
    }

    private void remember() {
        if (worn) {
            wornAuto = false; // (painted as worn now: its own)
        }
        undo.push(new Object[] {colours.clone(), skies.clone()});
        if (undo.size() > 64) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void undo() {
        if (!undo.isEmpty()) {
            if (worn) {
                wornAuto = false;
            }
            redo.push(new Object[] {colours.clone(), skies.clone()});
            Object[] s = undo.pop();
            colours = (int[]) s[0];
            skies = (int[]) s[1];
            refreshUsed();
        }
    }

    private void redo() {
        if (!redo.isEmpty()) {
            if (worn) {
                wornAuto = false;
            }
            undo.push(new Object[] {colours.clone(), skies.clone()});
            Object[] s = redo.pop();
            colours = (int[]) s[0];
            skies = (int[]) s[1];
            refreshUsed();
        }
    }

    private int pixelX(double mx) {
        return mx < cx ? -1 : (int) ((mx - cx) / cell);
    }

    private int pixelY(double my) {
        return my < cy ? -1 : (int) ((my - cy) / cell);
    }

    private boolean onCanvas(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (problem == null && !hex.isMouseOver(mx, my)) {
            int sw = skyAt(mx, my);
            if (sw >= 0) {
                sky = sw;
                skyBrush = true;
                if (tool == Tool.PICKER) {
                    tool = Tool.PENCIL;
                    refreshTools();
                }
                return true;
            }
            if (picking && pickAt(mx, my, true)) {
                return true;
            }
            for (var child : children()) {
                if (child != hex && child.isMouseOver(mx, my)) {
                    picking = false;
                    return super.mouseClicked(mx, my, button); // (a button: its own)
                }
            }
            if (picking && !inPicker(mx, my) && !(mx >= colX && mx < colX + colCols * 14 && my >= 28 && my < 28 + 18)) {
                picking = false; // (a click elsewhere puts it away)
            }
            if (mx >= colX && mx < colX + colCols * 14 && my >= 28 && my < 28 + 18) {
                picking = !picking;
                skyBrush = false;
                syncHsv();
                return true;
            }
            int cc = colourAt(mx, my);
            if (cc != 0) {
                colour = cc;
                syncHsv();
                remember(colour);
                skyBrush = false;
                showHex();
                skyBrush = false;
                if (tool == Tool.PICKER) {
                    tool = Tool.PENCIL;
                    refreshTools();
                }
                return true;
            }
            if (view3D()) {
                if (mx >= ax0 && mx < ax1 && my >= ay0 && my < ay1) {
                    int[] at = button == 0 || button == 1 ? pick(mx, my) : null;
                    if (at != null) {
                        press(at[0], at[1], button);
                    } else {
                        turning = true; // (off the armour: turning it)
                    }
                    return true;
                }
                return super.mouseClicked(mx, my, button);
            }
            int x = pixelX(mx), y = pixelY(my);
            if (onCanvas(x, y) && (button == 0 || button == 1)) {
                press(x, y, button);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** A click on a pixel of the canvas (flat, or on the 3D armour). */
    private void press(int x, int y, int button) {
        erasing = button == 1 || tool == Tool.ERASER;
        switch (tool) {
            case PICKER -> {
                int i = y * w + x;
                if (skyBrush && skies[i] != 0) {
                    sky = ShaderPaintStyles.sky(skies[i]);
                    pass = ShaderPaintStyles.pass(skies[i]);
                    lookButton.setMessage(lookName());
                } else if (!skyBrush && opaque(x, y)) {
                    colour = colours[i] | 0xFF000000;
                    showHex();
                    syncHsv();
                    remember(colour);
                    skyBrush = false;
                }
                tool = Tool.PENCIL;
                refreshTools();
            }
            case FILL -> {
                remember();
                fill(x, y, erasing, hasShiftDown());
                refreshUsed();
            }
            default -> {
                remember();
                startX = x;
                startY = y;
                dragging = true;
                if (tool == Tool.PENCIL || tool == Tool.ERASER || view3D()) {
                    dab(x, y, erasing); // (in 3D, rectangles and lines paint as the pencil does)
                }
            }
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pickDrag != 0) {
            pickAt(mx, my, false);
            return true;
        }
        if (turning) {
            yaw += (float) dx * 1.4F;
            pitch = Mth.clamp(pitch + (float) dy * 1.2F, -70.0F, 70.0F);
            return true;
        }
        if (dragging && view3D()) {
            int[] at = pick(mx, my);
            if (at != null) {
                if (Math.abs(at[0] - startX) <= 3 && Math.abs(at[1] - startY) <= 3) {
                    line(startX, startY, at[0], at[1], erasing);
                } else {
                    dab(at[0], at[1], erasing);
                }
                startX = at[0];
                startY = at[1];
            }
            return true;
        }
        if (dragging) {
            int x = pixelX(mx), y = pixelY(my);
            if ((tool == Tool.PENCIL || tool == Tool.ERASER) && onCanvas(x, y)) {
                line(startX, startY, x, y, erasing);
                startX = x;
                startY = y;
            }
            hoverX = x;
            hoverY = y;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (pickDrag != 0) {
            pickDrag = 0;
            remember(colour);
            return true;
        }
        if (turning) {
            turning = false;
            return true;
        }
        if (dragging && view3D()) {
            dragging = false;
            refreshUsed();
            if (!skyBrush && !erasing) {
                remember(colour);
            }
            return true;
        }
        if (dragging) {
            dragging = false;
            int x = Math.max(0, Math.min(w - 1, pixelX(mx))), y = Math.max(0, Math.min(h - 1, pixelY(my)));
            if (tool == Tool.RECT) {
                rect(startX, startY, x, y, erasing);
            } else if (tool == Tool.LINE) {
                line(startX, startY, x, y, erasing);
            }
            refreshUsed();
            if (!skyBrush && !erasing) {
                remember(colour);
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= px0) {
            int rows = (skyCount() + cols - 1) / cols;
            scroll = Math.max(0, Math.min(rows - rowsShown, scroll + (sy > 0 ? -1 : 1)));
            return true;
        }
        if (mx >= colX && mx < cx - 4) {
            int rows = (used.size() + colCols - 1) / colCols;
            colScroll = Math.max(0, Math.min(rows - colRows, colScroll + (sy > 0 ? -1 : 1)));
            return true;
        }
        brush = Math.max(1, Math.min(8, brush + (sy > 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (hex != null && hex.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                hex.setFocused(false);
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) redo(); else undo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        Tool t = switch (key) {
            case GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_P -> Tool.PENCIL;
            case GLFW.GLFW_KEY_E -> Tool.ERASER;
            case GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_F -> Tool.FILL;
            case GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_I -> Tool.PICKER;
            case GLFW.GLFW_KEY_R -> Tool.RECT;
            case GLFW.GLFW_KEY_L -> Tool.LINE;
            default -> null;
        };
        if (t != null && !ctrl) {
            tool = t;
            refreshTools();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT_BRACKET) { brush = Math.max(1, brush - 1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT_BRACKET) { brush = Math.min(8, brush + 1); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private int skyAt(double mx, double my) {
        if (mx < px0 || my < py0) {
            return -1;
        }
        int col = (int) ((mx - px0) / (swatch + 2)), row = (int) ((my - py0) / (swatch + 2));
        int i = (row + scroll) * cols + col;
        return col < cols && row < rowsShown && i < skyCount() ? i : -1;
    }

    /** The colour swatch under the mouse (its colour), or 0. */
    private int colourAt(double mx, double my) {
        if (mx >= colX && mx < colX + colCols * 14 && my >= shadeY && my < shadeY + 12) {
            int[] ramp = shades();
            int i = (int) ((mx - colX) / (colCols * 14 / (float) ramp.length));
            return i >= 0 && i < ramp.length ? ramp[i] : 0;
        }
        if (mx >= colX && mx < colX + colCols * 14 && my >= recentY && my < recentY + 28) {
            int i = (int) ((my - recentY) / 14) * colCols + (int) ((mx - colX) / 14);
            return i < RECENT.size() ? RECENT.get(i) : 0;
        }
        if (mx < colX || my < colY || mx >= colX + colCols * 14) {
            return 0;
        }
        int col = (int) ((mx - colX) / 14), row = (int) ((my - colY) / 14);
        int i = (row + colScroll) * colCols + col;
        return row < colRows && i < used.size() ? used.get(i) : 0;
    }

    // ---- the colour picker, the shade ramp, the recent colours ----

    /** The picker's hue, saturation and brightness, from the colour (after it's been set some other way). */
    private boolean showingHex;

    /** The colour, shown in the box (not as if typed there). */
    private void showHex() {
        if (hex == null) {
            return;
        }
        String v = String.format("#%06X", colour & 0xFFFFFF);
        if (!v.equalsIgnoreCase(hex.getValue())) {
            showingHex = true;
            try {
                hex.setValue(v);
            } finally {
                showingHex = false;
            }
        }
    }

    private void syncHsv() {
        float[] hsb = java.awt.Color.RGBtoHSB((colour >> 16) & 255, (colour >> 8) & 255, colour & 255, null);
        if (hsb[1] > 0.001F && hsb[2] > 0.001F) {
            hue = hsb[0]; // (grey or black: keep the hue it had)
        }
        sat = hsb[1];
        val = hsb[2];
        if (hex != null && !hex.isFocused()) {
            showHex();
        }
    }

    private void fromHsv() {
        colour = 0xFF000000 | Mth.hsvToRgb(hue, sat, val);
        skyBrush = false;
        if (hex != null) {
            showHex();
        }
    }

    private boolean inPicker(double mx, double my) {
        return mx >= pickX - 4 && mx < pickX + PICK + 6 + HUE_W + 4 && my >= pickY - 4 && my < pickY + PICK + 4;
    }

    /** A click (or drag) in the picker: the square sets saturation and brightness, the bar the hue. */
    private boolean pickAt(double mx, double my, boolean click) {
        int part = click ? (mx >= pickX && mx < pickX + PICK && my >= pickY && my < pickY + PICK ? 1
                : mx >= pickX + PICK + 6 && mx < pickX + PICK + 6 + HUE_W && my >= pickY && my < pickY + PICK ? 2 : 0) : pickDrag;
        if (part == 0) {
            return inPicker(mx, my);
        }
        pickDrag = part;
        float fy = Mth.clamp((float) (my - pickY) / PICK, 0.0F, 1.0F);
        if (part == 1) {
            sat = Mth.clamp((float) (mx - pickX) / PICK, 0.0F, 1.0F);
            val = 1.0F - fy;
        } else {
            hue = Math.min(fy, 0.999F);
        }
        fromHsv();
        return true;
    }

    /** Darker and lighter shades of the colour, as a pixel artist shades: darkest .. the colour .. lightest. */
    private int[] shades() {
        float[] hsb = java.awt.Color.RGBtoHSB((colour >> 16) & 255, (colour >> 8) & 255, colour & 255, null);
        float h = hsb[1] > 0.001F ? hsb[0] : hue, s = hsb[1], v = hsb[2];
        return new int[] {
                0xFF000000 | Mth.hsvToRgb((h + 0.02F) % 1.0F, Math.min(1.0F, s * 1.15F), v * 0.45F),
                0xFF000000 | Mth.hsvToRgb((h + 0.01F) % 1.0F, Math.min(1.0F, s * 1.08F), v * 0.7F),
                colour | 0xFF000000,
                0xFF000000 | Mth.hsvToRgb((h + 0.99F) % 1.0F, s * 0.8F, Math.min(1.0F, v * 1.15F + 0.1F)),
                0xFF000000 | Mth.hsvToRgb((h + 0.98F) % 1.0F, s * 0.55F, Math.min(1.0F, v * 1.25F + 0.25F))};
    }

    /** A colour used: to the front of the recent ones. */
    private static void remember(int c) {
        Integer v = c | 0xFF000000;
        RECENT.remove(v);
        RECENT.addFirst(v);
        while (RECENT.size() > 8) {
            RECENT.removeLast();
        }
    }

    private void drawPicker(GuiGraphics g) {
        if (!picking) {
            return;
        }
        pickX = colX + colCols * 14 + 10;
        pickY = 28;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(pickX - 5, pickY - 5, pickX + PICK + 6 + HUE_W + 5, pickY + PICK + 5, 0xF0181020);
        g.renderOutline(pickX - 5, pickY - 5, PICK + 6 + HUE_W + 10, PICK + 10, 0xFF8866CC);
        // the square: white to the hue across, darkening to black down
        var m = g.pose().last().pose();
        var vc = g.bufferSource().getBuffer(RenderType.gui());
        int pure = 0xFF000000 | Mth.hsvToRgb(hue, 1.0F, 1.0F);
        vc.addVertex(m, pickX, pickY, 0).setColor(0xFFFFFFFF);
        vc.addVertex(m, pickX, pickY + PICK, 0).setColor(0xFFFFFFFF);
        vc.addVertex(m, pickX + PICK, pickY + PICK, 0).setColor(pure);
        vc.addVertex(m, pickX + PICK, pickY, 0).setColor(pure);
        vc.addVertex(m, pickX, pickY, 0.1F).setColor(0x00000000);
        vc.addVertex(m, pickX, pickY + PICK, 0.1F).setColor(0xFF000000);
        vc.addVertex(m, pickX + PICK, pickY + PICK, 0.1F).setColor(0xFF000000);
        vc.addVertex(m, pickX + PICK, pickY, 0.1F).setColor(0x00000000);
        g.flush();
        // the hue bar
        for (int i = 0; i < 6; i++) {
            int y0 = pickY + PICK * i / 6, y1 = pickY + PICK * (i + 1) / 6;
            g.fillGradient(pickX + PICK + 6, y0, pickX + PICK + 6 + HUE_W, y1, 0xFF000000 | Mth.hsvToRgb(i / 6.0F, 1.0F, 1.0F),
                    0xFF000000 | Mth.hsvToRgb(((i + 1) / 6.0F) % 1.0F, 1.0F, 1.0F));
        }
        // where it's at
        int sx = pickX + Math.round(sat * PICK), sy = pickY + Math.round((1.0F - val) * PICK);
        g.renderOutline(sx - 2, sy - 2, 5, 5, val > 0.5F ? 0xFF000000 : 0xFFFFFFFF);
        int hy = pickY + Math.round(hue * PICK);
        g.fill(pickX + PICK + 4, hy - 1, pickX + PICK + 8 + HUE_W, hy + 1, 0xFFFFFFFF);
        g.pose().popPose();
    }

    /** Does the texture differ from the item's own? */
    private boolean redrawn() {
        return redrawn(colours, original);
    }

    private static boolean redrawn(int[] colours, int[] original) {
        for (int i = 0; i < colours.length; i++) {
            if (!same(colours[i], original[i]) || ((colours[i] >>> 24) > 16 && (colours[i] | 0xFF000000) != (original[i] | 0xFF000000))) {
                return true;
            }
        }
        return false;
    }

    private void apply() {
        if (problem == null && skies != null && iconCanvas != null) {
            save();
            Canvas ic = iconCanvas;
            Optional<ModComponents.ItemTexture> tex = redrawn(ic.colours, ic.original) ? Optional.of(ModComponents.ItemTexture.of(ic.w, ic.h, ic.colours)) : Optional.empty();
            // (worn: as painted; left to its icon's skies, none of its own)
            Optional<ModComponents.ArmorPaint> armor = wornCanvas == null || wornAuto ? Optional.empty() : Optional.of(armourPaint());
            PacketDistributor.sendToServer(new ShaderPaintPayload(pos, ModComponents.ShaderPaint.of(ic.w, ic.h, ic.skies), tex, armor));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFE8D8FF);
        if (problem != null) {
            g.drawCenteredString(font, problem, width / 2, height / 2, 0xFFFF8080);
            return;
        }
        save();
        hoverX = pixelX(mouseX);
        hoverY = pixelY(mouseY);
        int left = 8, toolW = 76;
        g.drawCenteredString(font, Component.translatable("screen.skyblocks.shader_paint.brush", brush), left + toolW / 2, 28 + Tool.values().length * 20 + 9, 0xFFFFFFFF);
        // the colours: the one painting with (big), then each the texture uses
        g.drawString(font, Component.translatable("screen.skyblocks.shader_paint.colours"), colX, 28 - 11, 0xFFD0C0F0);
        g.fill(colX, 28, colX + colCols * 14, 28 + 18, 0xFF000000 | colour);
        // (a little rainbow at its end: click it for the colour picker)
        for (int i = 0; i < 18; i++) {
            g.fill(colX + colCols * 14 - 7, 28 + i, colX + colCols * 14, 28 + i + 1, 0xFF000000 | Mth.hsvToRgb(i / 18.0F, 0.9F, 1.0F));
        }
        g.renderOutline(colX - 1, 27, colCols * 14 + 2, 20, !skyBrush ? 0xFFFFFFFF : 0xFF606060);
        // its shades, darkest to lightest; the colours used lately
        int[] ramp = shades();
        float sw = colCols * 14 / (float) ramp.length;
        for (int i = 0; i < ramp.length; i++) {
            g.fill(colX + Math.round(i * sw), shadeY, colX + Math.round((i + 1) * sw) - 1, shadeY + 12, ramp[i]);
        }
        g.drawString(font, Component.translatable("screen.skyblocks.shader_paint.recent"), colX, recentY - 10 + 1, 0xFF9080B0);
        for (int i = 0; i < 8; i++) {
            int x = colX + (i % colCols) * 14, y = recentY + (i / colCols) * 14;
            if (i < RECENT.size()) {
                g.fill(x, y, x + 12, y + 12, RECENT.get(i));
            } else {
                g.renderOutline(x, y, 12, 12, 0x30FFFFFF);
            }
        }
        g.drawString(font, Component.translatable("screen.skyblocks.shader_paint.used"), colX, colY - 10 + 1, 0xFF9080B0);
        for (int i = colScroll * colCols; i < Math.min(used.size(), (colScroll + colRows) * colCols); i++) {
            int col = i % colCols, row = i / colCols - colScroll;
            int x = colX + col * 14, y = colY + row * 14;
            g.fill(x, y, x + 12, y + 12, used.get(i));
            if (!skyBrush && (used.get(i) | 0xFF000000) == (colour | 0xFF000000)) {
                g.renderOutline(x - 1, y - 1, 14, 14, 0xFFFFFFFF);
            }
        }
        if (view3D()) {
            draw3D(g, mouseX, mouseY);
        } else {
            drawCanvas(g);
        }
        // the skies to paint with
        drawRight(g, mouseX, mouseY, partialTick);
    }

    private void drawCanvas(GuiGraphics g) {
        // the canvas: a checkerboard under the texture, the texture as it's being redrawn, the skies painted over it, the grid
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int x0 = cx + x * cell, y0 = cy + y * cell;
                if (region != null && !region[y * w + x]) {
                    g.fill(x0, y0, x0 + cell, y0 + cell, 0xFF120E18); // (not on the worn piece)
                    continue;
                }
                g.fill(x0, y0, x0 + cell, y0 + cell, ((x + y) & 1) == 0 ? 0xFF3A3A44 : 0xFF2C2C34);
                int c = colours[y * w + x];
                if ((c >>> 24) > 16) {
                    g.fill(x0, y0, x0 + cell, y0 + cell, c | 0xFF000000);
                }
            }
        }
        drawSkies(g, cx, cy, cell);
        if (grid && cell >= 6) {
            for (int x = 0; x <= w; x++) {
                g.fill(cx + x * cell, cy, cx + x * cell + 1, cy + h * cell, 0x30FFFFFF);
            }
            for (int y = 0; y <= h; y++) {
                g.fill(cx, cy + y * cell, cx + w * cell, cy + y * cell + 1, 0x30FFFFFF);
            }
        }
        if (worn) {
            // (each face of the worn piece)
            g.pose().pushPose();
            g.pose().translate(0, 0, 45);
            for (int[] r : rects) {
                g.renderOutline(cx + r[0] * cell, cy + r[1] * cell, (r[2] - r[0]) * cell, (r[3] - r[1]) * cell, 0x70FFD080);
            }
            g.pose().popPose();
            if (wornAuto) {
                g.drawString(font, Component.translatable("screen.skyblocks.shader_paint.auto.on"), cx, cy + h * cell + 4, 0xFFFFE080);
            }
        }
        g.renderOutline(cx - 1, cy - 1, w * cell + 2, h * cell + 2, 0xFF8866CC);
        // where the tool will go
        if (onCanvas(hoverX, hoverY) || dragging) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 50);
            if (dragging && (tool == Tool.RECT || tool == Tool.LINE)) {
                int x0 = Math.min(startX, hoverX), x1 = Math.max(startX, hoverX), y0 = Math.min(startY, hoverY), y1 = Math.max(startY, hoverY);
                if (tool == Tool.RECT) {
                    g.renderOutline(cx + x0 * cell, cy + y0 * cell, (x1 - x0 + 1) * cell, (y1 - y0 + 1) * cell, 0xFFFFFFFF);
                } else {
                    g.fill(cx + startX * cell + cell / 2, cy + startY * cell + cell / 2, cx + hoverX * cell + cell / 2 + 1, cy + hoverY * cell + cell / 2 + 1, 0xA0FFFFFF);
                }
            } else if (tool == Tool.PENCIL || tool == Tool.ERASER) {
                int r0 = -(brush - 1) / 2;
                g.renderOutline(cx + (hoverX + r0) * cell, cy + (hoverY + r0) * cell, brush * cell, brush * cell, tool == Tool.ERASER ? 0xFFFF6060 : 0xFFFFFFFF);
            } else {
                g.renderOutline(cx + hoverX * cell, cy + hoverY * cell, cell, cell, 0xFFFFFF80);
            }
            g.pose().popPose();
        }
    }

    private void drawRight(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.drawString(font, Component.translatable("screen.skyblocks.shader_paint.skies"), px0, py0 - 11, 0xFFD0C0F0);
        drawSwatches(g);
        g.pose().pushPose();
        g.pose().translate(0, 0, 60);
        int selRow = sky / cols - scroll;
        if (skyBrush && selRow >= 0 && selRow < rowsShown) {
            g.renderOutline(px0 + (sky % cols) * (swatch + 2) - 1, py0 + selRow * (swatch + 2) - 1, swatch + 2, swatch + 2, 0xFFFFFFFF);
        }
        int totalRows = (skyCount() + cols - 1) / cols;
        if (totalRows > rowsShown) {
            int barH = rowsShown * (swatch + 2), knob = Math.max(8, barH * rowsShown / totalRows);
            int knobY = py0 + (barH - knob) * scroll / Math.max(1, totalRows - rowsShown);
            g.fill(px0 + cols * (swatch + 2) + 2, py0, px0 + cols * (swatch + 2) + 5, py0 + barH, 0x40FFFFFF);
            g.fill(px0 + cols * (swatch + 2) + 2, knobY, px0 + cols * (swatch + 2) + 5, knobY + knob, 0xC0D0C0F0);
        }
        g.pose().popPose();
        int below = py0 + rowsShown * (swatch + 2) + 24;
        g.drawString(font, skyBrush ? ShaderPaintRender.name(style()) : Component.literal(String.format("#%06X", colour & 0xFFFFFF)), px0, below, 0xFFFFFFFF);
        // the item as it'll look, turning
        drawPreview(g, px0 + (cols * (swatch + 2)) / 2, below + 14 + 50, partialTick);
        if (!Minecraft.getInstance().player.getAbilities().instabuild) {
            g.drawCenteredString(font, Component.translatable("screen.skyblocks.shader_paint.cost"), width / 2, height - 38, 0xFFB0A0D0);
        }
        drawPicker(g);
        if (!picking && mouseX >= colX && mouseX < colX + colCols * 14 && mouseY >= 28 && mouseY < 28 + 18) {
            g.renderTooltip(font, Component.translatable("screen.skyblocks.shader_paint.pick"), mouseX, mouseY);
            return;
        }
        if (picking && inPicker(mouseX, mouseY)) {
            return; // (no tooltips over the picker)
        }
        int hover = skyAt(mouseX, mouseY);
        if (hover >= 0) {
            g.renderTooltip(font, ShaderPaintRender.name(ShaderPaintStyles.of(hover, pass)), mouseX, mouseY);
        } else if (onCanvas(hoverX, hoverY) && skies[hoverY * w + hoverX] != 0) {
            g.renderTooltip(font, ShaderPaintRender.name(skies[hoverY * w + hoverX]), mouseX, mouseY);
        }
    }

    private void drawSkies(GuiGraphics g, int x0, int y0, int c) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 40); // (in front of the canvas: the skies' render types draw only nearer than what's there)
        var m = g.pose().last().pose();
        java.util.TreeSet<Integer> has = new java.util.TreeSet<>();
        for (int b : skies) {
            if (b != 0) {
                has.add(b);
            }
        }
        for (int s : has) {
            RenderType type = ShaderPaintRender.type(s);
            if (type == null) {
                continue;
            }
            VertexConsumer vc = g.bufferSource().getBuffer(type);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (skies[y * w + x] == s) {
                        ShaderPaintRender.square(vc, m, x0 + x * c, y0 + y * c, c, c, s, x / (float) w, y / (float) h, (x + 1) / (float) w, (y + 1) / (float) h, w);
                    }
                }
            }
            g.bufferSource().endBatch(type);
        }
        g.pose().popPose();
    }

    private void drawSwatches(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 40); // (in front of the screen's background)
        var m = g.pose().last().pose();
        for (int i = scroll * cols; i < Math.min(skyCount(), (scroll + rowsShown) * cols); i++) {
            int s = ShaderPaintStyles.of(i, pass);
            RenderType type = ShaderPaintRender.type(s);
            if (type == null) {
                continue;
            }
            int col = i % cols, row = i / cols - scroll;
            VertexConsumer vc = g.bufferSource().getBuffer(type);
            ShaderPaintRender.square(vc, m, px0 + col * (swatch + 2), py0 + row * (swatch + 2), swatch, swatch, s, 0, 0, 1, 1, 8);
            g.bufferSource().endBatch(type);
        }
        g.pose().popPose();
    }

    private void drawPreview(GuiGraphics g, int x, int y, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack preview = item.copy();
        Canvas ic = iconCanvas;
        if (ic == null) {
            return;
        }
        boolean any = false;
        for (int b : ic.skies) {
            any |= b != 0;
        }
        if (any) {
            preview.set(ModComponents.SHADER_PAINT.get(), ModComponents.ShaderPaint.of(ic.w, ic.h, ic.skies));
        } else {
            preview.remove(ModComponents.SHADER_PAINT.get());
        }
        if (redrawn(ic.colours, ic.original)) {
            preview.set(ModComponents.ITEM_TEXTURE.get(), ModComponents.ItemTexture.of(ic.w, ic.h, ic.colours));
        } else {
            preview.remove(ModComponents.ITEM_TEXTURE.get());
        }
        float turn = (mc.level == null ? 0 : mc.level.getGameTime() + partialTick) * 2.5F;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 150);
        pose.scale(96, -96, 96);
        pose.mulPose(Axis.XP.rotationDegrees(-12));
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        Lighting.setupForEntityInInventory();
        mc.getItemRenderer().renderStatic(preview, ItemDisplayContext.FIXED, 0x00F000F0, OverlayTexture.NO_OVERLAY, pose, g.bufferSource(), mc.level, 0);
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, 0x60100818);
    }
}
