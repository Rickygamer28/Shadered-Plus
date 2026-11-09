package com.example.skyblocks.client;

import com.example.skyblocks.EffectBlockEntity;
import com.example.skyblocks.EffectBlockPayload;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The effect block's menu: the effect; a button for each of its colours (1-8, - and + for how many), the chosen one set
 * with red, green and blue sliders or a hex code; speed, size, brightness, blur, pixelated. Changes go to the whole
 * group as you make them.
 */
public class EffectBlockScreen extends Screen {
    private static final int PANEL_W = 276;
    private static final int PANEL_H = 278;

    private final BlockPos pos;
    private int effect, count, speed, size, brightness, blur;
    private boolean pixel;
    private final int[] colours;
    /** The background block's id ("": the background colour). */
    private String block;
    /** The menu's page: 0 the effect, 1 the background block. */
    private static int page;
    private EditBox search;
    private java.util.List<net.minecraft.world.item.ItemStack> found = java.util.List.of();
    private double gridScroll;
    private static final int GRID_COLS = 12, GRID_ROWS = 9, CELL = 21;
    private static java.util.List<net.minecraft.world.item.ItemStack> allBlocks;
    /** The colour being set. */
    private int sel;
    private EditBox hex;
    private boolean settingHex;
    private Button effectButton;
    private final Button[] swatches = new Button[EffectBlockEntity.MAX_COLOURS];
    private final Slider[] rgb = new Slider[3];
    private boolean dirty;
    private int sinceSent;
    /** Saving: the next preset button clicked keeps the current look. */
    private boolean saving;

    public EffectBlockScreen(BlockPos pos, EffectBlockEntity.Settings s) {
        super(Component.translatable("screen.skyblocks.effect_block"));
        this.pos = pos;
        effect = s.effect();
        count = s.count();
        colours = s.colours().clone();
        speed = s.speed();
        size = s.size();
        brightness = s.brightness();
        blur = s.blur();
        pixel = s.pixel();
        block = s.block();
    }

    /** Every block that's drawn by its model (any mod's), as items to pick from. */
    private static java.util.List<net.minecraft.world.item.ItemStack> allBlocks() {
        if (allBlocks == null) {
            allBlocks = new java.util.ArrayList<>();
            for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (item instanceof net.minecraft.world.item.BlockItem b && !(b.getBlock() instanceof com.example.skyblocks.EffectBlock)
                        && b.getBlock().defaultBlockState().getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) {
                    allBlocks.add(new net.minecraft.world.item.ItemStack(item));
                }
            }
        }
        return allBlocks;
    }

    private void find(String text) {
        String q = text.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
        for (net.minecraft.world.item.ItemStack stack : allBlocks()) {
            if (q.isEmpty() || stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(q)
                    || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(q)) {
                out.add(stack);
            }
        }
        found = out;
        gridScroll = 0;
    }

    private static String idOf(net.minecraft.world.item.ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(((net.minecraft.world.item.BlockItem) stack.getItem()).getBlock()).toString();
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        // the two pages: the effect, and a block for its background
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.effect_block.page_effect"), b -> {
            page = 0;
            rebuildWidgets();
        }).bounds(l + 12, t + 3, 124, 18).build()).active = page != 0;
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.effect_block.page_block"), b -> {
            page = 1;
            rebuildWidgets();
        }).bounds(l + 140, t + 3, 124, 18).build()).active = page != 1;
        if (page == 1) {
            initBlockPage(l, t);
            return;
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> setEffect(effect - 1)).bounds(l + 12, t + 24, 20, 20).build());
        effectButton = addRenderableWidget(Button.builder(effectLabel(), b -> setEffect(effect + 1)).bounds(l + 36, t + 24, 204, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> setEffect(effect + 1)).bounds(l + 244, t + 24, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            count = Math.max(1, count - 1);
            if (sel != EffectBlockEntity.BACKGROUND && sel >= count) {
                select(count - 1);
            }
            showSwatches();
            changed();
        }).bounds(l + 196, t + 50, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            count = Math.min(EffectBlockEntity.MAX_COLOURS, count + 1);
            showSwatches();
            select(count - 1);
            changed();
        }).bounds(l + 246, t + 50, 18, 18).build());
        for (int i = 0; i < EffectBlockEntity.MAX_COLOURS; i++) {
            final int index = i;
            swatches[i] = addRenderableWidget(new Button(l + 12 + i * 32, t + 72, 28, 20, Component.empty(), b -> select(index),
                    s -> Component.literal("colour " + (index + 1))) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    super.renderWidget(g, mx, my, pt);
                    g.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3, 0xFF000000 | colours[index]);
                    if (index == sel) {
                        g.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, 0xFFFFFFFF);
                    }
                }
            });
        }
        showSwatches();
        // the background (behind the effects that light only part of the side): set like the colours
        addRenderableWidget(new Button(l + 70, t + 49, 118, 20, Component.translatable("screen.skyblocks.effect_block.background"),
                b -> select(EffectBlockEntity.BACKGROUND), s -> Component.literal("background")) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                super.renderWidget(g, mx, my, pt);
                g.fill(getX() + 4, getY() + 4, getX() + 16, getY() + height - 4, 0xFFFFFFFF);
                g.fill(getX() + 5, getY() + 5, getX() + 15, getY() + height - 5, 0xFF000000 | colours[EffectBlockEntity.BACKGROUND]);
                if (sel == EffectBlockEntity.BACKGROUND) {
                    g.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, 0xFFFFFFFF);
                }
            }
        });
        String[] names = {"red", "green", "blue"};
        for (int k = 0; k < 3; k++) {
            final int shift = 16 - 8 * k;
            rgb[k] = addRenderableWidget(new Slider(l + 12, t + 100 + k * 24, 180, names[k], 0, 255, (colours[sel] >> shift) & 255, 1, v -> {
                colours[sel] = (colours[sel] & ~(255 << shift)) | (v << shift);
                showHex();
                changed();
            }));
        }
        hex = new EditBox(font, l + 198, t + 148, 66, 18, Component.literal("hex"));
        hex.setMaxLength(7);
        hex.setResponder(v -> {
            if (settingHex) {
                return;
            }
            String s = v.startsWith("#") ? v.substring(1) : v;
            if (s.length() == 6) {
                try {
                    colours[sel] = Integer.parseInt(s, 16) & 0xFFFFFF;
                    showSliders();
                    changed();
                } catch (NumberFormatException ignored) {
                    // (not a colour yet)
                }
            }
        });
        addRenderableWidget(hex);
        showHex();
        addRenderableWidget(new Slider(l + 12, t + 176, 124, "speed", 0, 400, speed, 5, v -> speed = v));
        addRenderableWidget(new Slider(l + 140, t + 176, 124, "size", 25, 800, size, 5, v -> size = v));
        addRenderableWidget(new Slider(l + 12, t + 200, 124, "brightness", 10, 100, brightness, 1, v -> brightness = v));
        addRenderableWidget(new Slider(l + 140, t + 200, 124, "blur", 0, 100, blur, 1, v -> blur = v));
        addRenderableWidget(Button.builder(pixelLabel(), b -> {
            pixel = !pixel;
            b.setMessage(pixelLabel());
            changed();
        }).bounds(l + 12, t + 226, 124, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(l + 188, t + 226, 76, 20).build());
        // the presets: click one to use it; "Save", then one, to keep this look there
        addRenderableWidget(Button.builder(saveLabel(), b -> {
            saving = !saving;
            b.setMessage(saveLabel());
        }).bounds(l + 12, t + 250, 48, 20).build());
        for (int i = 0; i < EffectPresets.SLOTS; i++) {
            final int slot = i;
            addRenderableWidget(new Button(l + 64 + i * 25, t + 250, 22, 20, Component.empty(), b -> preset(slot),
                    s -> Component.literal("preset " + (slot + 1))) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    super.renderWidget(g, mx, my, pt);
                    EffectBlockEntity.Settings p = EffectPresets.get(slot);
                    if (p == null) {
                        g.drawCenteredString(font, Integer.toString(slot + 1), getX() + width / 2, getY() + 6, 0x808080);
                        return;
                    }
                    int n = Math.min(p.count(), 4); // (its first colours, as stripes)
                    for (int k = 0; k < n; k++) {
                        int x0 = getX() + 3 + k * (width - 6) / n, x1 = getX() + 3 + (k + 1) * (width - 6) / n;
                        g.fill(x0, getY() + 3, x1, getY() + height - 3, 0xFF000000 | p.colours()[k]);
                    }
                    if (saving) {
                        g.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, 0xFFFFC040);
                    }
                }
            }).setTooltip(net.minecraft.client.gui.components.Tooltip.create(presetTip(slot)));
        }
    }

    private void initBlockPage(int l, int t) {
        search = new EditBox(font, l + 12, t + 25, 252, 16, Component.translatable("screen.skyblocks.effect_block.search"));
        search.setHint(Component.translatable("screen.skyblocks.effect_block.search"));
        search.setResponder(this::find);
        addRenderableWidget(search);
        find(search.getValue());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.effect_block.use_colour"), b -> {
            block = "";
            changed();
        }).bounds(l + 12, t + 250, 124, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(l + 188, t + 250, 76, 20).build());
    }

    private int gridRows() {
        return (found.size() + GRID_COLS - 1) / GRID_COLS;
    }

    /** The block in the grid at this point (null: none). */
    private net.minecraft.world.item.ItemStack cellAt(double mx, double my) {
        int l = left() + 12, t = top() + 46;
        if (mx < l || my < t || mx >= l + GRID_COLS * CELL || my >= t + GRID_ROWS * CELL) {
            return null;
        }
        int i = ((int) (my - t) / CELL + (int) gridScroll) * GRID_COLS + (int) (mx - l) / CELL;
        return i >= 0 && i < found.size() ? found.get(i) : null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (page == 1) {
            net.minecraft.world.item.ItemStack stack = cellAt(mx, my);
            if (stack != null) {
                block = idOf(stack);
                changed();
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (page == 1) {
            gridScroll = Math.max(0, Math.min(Math.max(0, gridRows() - GRID_ROWS), gridScroll - dy));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private void renderBlockPage(GuiGraphics g, int mx, int my) {
        int l = left() + 12, t = top() + 46;
        g.fill(l - 1, t - 1, l + GRID_COLS * CELL + 1, t + GRID_ROWS * CELL + 1, 0xFF000000);
        net.minecraft.world.item.ItemStack hovered = null;
        for (int r = 0; r < GRID_ROWS; r++) {
            for (int c = 0; c < GRID_COLS; c++) {
                int i = (r + (int) gridScroll) * GRID_COLS + c;
                if (i >= found.size()) {
                    break;
                }
                net.minecraft.world.item.ItemStack stack = found.get(i);
                int x = l + c * CELL, y = t + r * CELL;
                boolean chosen = idOf(stack).equals(block);
                boolean over = mx >= x && mx < x + CELL && my >= y && my < y + CELL;
                g.fill(x, y, x + CELL - 1, y + CELL - 1, chosen ? 0xFF8B5CF6 : over ? 0xFF4A4A5A : 0xFF24242C);
                g.renderItem(stack, x + 2, y + 2);
                if (over) {
                    hovered = stack;
                }
            }
        }
        if (gridRows() > GRID_ROWS) { // (a bar: there's more)
            int h = GRID_ROWS * CELL, bar = Math.max(10, h * GRID_ROWS / gridRows());
            int by = t + (int) ((h - bar) * gridScroll / (gridRows() - GRID_ROWS));
            g.fill(l + GRID_COLS * CELL + 2, by, l + GRID_COLS * CELL + 4, by + bar, 0xFF8080A0);
        }
        Component now = block.isEmpty() ? Component.translatable("screen.skyblocks.effect_block.bg_colour")
                : Component.translatable("screen.skyblocks.effect_block.bg_block", blockName(block));
        g.drawString(font, now, l, top() + 238, 0xC0C0C0);
        if (hovered != null) {
            g.renderTooltip(font, hovered.getHoverName(), mx, my);
        }
    }

    private static Component blockName(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        return rl != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(rl)
                ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl).getName() : Component.literal(id);
    }

    /** A slider for a number from {@code min} to {@code max}, in steps. */
    private class Slider extends AbstractSliderButton {
        private final String name;
        private final int min, max, step;
        private final IntConsumer set;

        Slider(int x, int y, int w, String name, int min, int max, int value, int step, IntConsumer set) {
            super(x, y, w, 20, Component.empty(), (value - min) / (double) (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.step = step;
            this.set = set;
            updateMessage();
        }

        int amount() {
            int v = (int) Math.round(min + value * (max - min));
            return Math.round(v / (float) step) * step;
        }

        void show(int v) {
            value = (v - min) / (double) (max - min);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.skyblocks.effect_block." + name, amount()));
        }

        @Override
        protected void applyValue() {
            set.accept(amount());
            changed();
        }
    }

    private Component effectLabel() {
        return Component.translatable("screen.skyblocks.effect_block.effect",
                Component.translatable("effect_block.skyblocks." + EffectBlockEntity.EFFECTS[effect]));
    }

    private Component saveLabel() {
        return Component.translatable(saving ? "screen.skyblocks.effect_block.saving" : "screen.skyblocks.effect_block.save");
    }

    private Component presetTip(int slot) {
        EffectBlockEntity.Settings p = EffectPresets.get(slot);
        return p == null ? Component.translatable("screen.skyblocks.effect_block.preset_empty")
                : Component.translatable("effect_block.skyblocks." + EffectBlockEntity.EFFECTS[p.effect()]);
    }

    /** A preset button: keeps this look there (saving), or puts its look on the block. */
    private void preset(int slot) {
        if (saving) {
            EffectPresets.set(slot, current());
            saving = false;
            rebuildWidgets();
            return;
        }
        EffectBlockEntity.Settings p = EffectPresets.get(slot);
        if (p == null) {
            return;
        }
        effect = p.effect();
        count = p.count();
        System.arraycopy(p.colours(), 0, colours, 0, Math.min(colours.length, p.colours().length));
        speed = p.speed();
        size = p.size();
        brightness = p.brightness();
        blur = p.blur();
        pixel = p.pixel();
        block = p.block();
        sel = 0;
        changed();
        rebuildWidgets();
    }

    private EffectBlockEntity.Settings current() {
        return new EffectBlockEntity.Settings(effect, count, colours, speed, size, brightness, pixel, blur, block);
    }

    private Component pixelLabel() {
        return Component.translatable(pixel ? "screen.skyblocks.effect_block.pixel_on" : "screen.skyblocks.effect_block.pixel_off");
    }

    private void setEffect(int e) {
        effect = Math.floorMod(e, EffectBlockEntity.EFFECTS.length);
        effectButton.setMessage(effectLabel());
        changed();
    }

    private void select(int i) {
        sel = i;
        showSliders();
        showHex();
    }

    /** Only as many colour buttons as colours used. */
    private void showSwatches() {
        for (int i = 0; i < swatches.length; i++) {
            if (swatches[i] != null) {
                swatches[i].visible = i < count;
            }
        }
    }

    private void showSliders() {
        for (int k = 0; k < 3; k++) {
            if (rgb[k] != null) {
                rgb[k].show((colours[sel] >> (16 - 8 * k)) & 255);
            }
        }
    }

    private void showHex() {
        if (hex != null) {
            settingHex = true;
            hex.setValue(String.format("#%06X", colours[sel]));
            settingHex = false;
        }
    }

    private void changed() {
        dirty = true;
    }

    @Override
    public void tick() {
        super.tick();
        sinceSent++;
        if (dirty && sinceSent >= 2) {
            send();
        }
    }

    private void send() {
        dirty = false;
        sinceSent = 0;
        PacketDistributor.sendToServer(EffectBlockPayload.of(pos, current()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left(), top(), left() + PANEL_W, top() + PANEL_H, 0xC0101018);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();
        if (page == 1) {
            renderBlockPage(g, mouseX, mouseY);
            return;
        }
        g.drawString(font, Component.translatable("screen.skyblocks.effect_block.colours"), l + 12, t + 55, 0xA0A0A0);
        Component editing = sel == EffectBlockEntity.BACKGROUND ? Component.translatable("screen.skyblocks.effect_block.background")
                : Component.translatable("screen.skyblocks.effect_block.editing", sel + 1);
        g.drawCenteredString(font, Component.literal(Integer.toString(count)), l + 230, t + 55, 0xFFFFFF);
        // the colour being set, big
        g.fill(l + 198, t + 100, l + 264, t + 142, 0xFFFFFFFF);
        g.fill(l + 199, t + 101, l + 263, t + 141, 0xFF000000 | colours[sel]);
        g.drawCenteredString(font, editing, l + 231, t + 116,
                (((colours[sel] >> 16) & 255) * 3 + ((colours[sel] >> 8) & 255) * 6 + (colours[sel] & 255)) > 1300 ? 0x202020 : 0xFFFFFF);
    }

    @Override
    public void onClose() {
        send();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
