package com.example.skyblocks.client;

import com.example.skyblocks.SingularityMenu;
import com.example.skyblocks.SingularityPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The singularity's menu: what it holds (searchable, scrolling; biggest first), your inventory below. Click a kind to
 * take a stack (right-click: one, shift-click: a stack into your inventory); click with something held to put it in
 * (right-click: one); shift-click in your inventory to put a whole stack in. Destroy mode: clicking marks a kind (or
 * what you hold) to be crushed to nothing when it falls in. Vacuum: pull in dropped items from far around.
 */
public class SingularityScreen extends AbstractContainerScreen<SingularityMenu> {
    private static final int COLS = 9, ROWS = 5, GRID_X = 8, GRID_Y = 20, CELL = 18;
    private EditBox search;
    private Button vacuumButton, destroyButton;
    private boolean destroyMode;
    private int scroll;
    private final List<Integer> shown = new ArrayList<>(); // (indices into the menu's items; -1 - k: a destroyed kind not held)
    private final List<ItemStack> destroyedOnly = new ArrayList<>();
    private int builtVersion = -1;
    private String builtSearch = "";

    public SingularityScreen(SingularityMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 194;
        imageHeight = 222;
        inventoryLabelY = SingularityMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 96, topPos + 5, 88, 11, Component.translatable("gui.skyblocks.singularity.search"));
        search.setHint(Component.translatable("gui.skyblocks.singularity.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setBordered(true);
        search.setMaxLength(50);
        addRenderableWidget(search);
        vacuumButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(SingularityMenu.VACUUM, ItemStack.EMPTY, 0))
                .bounds(leftPos + 8, topPos + GRID_Y + ROWS * CELL + 3, 70, 12).build());
        destroyButton = addRenderableWidget(Button.builder(Component.empty(), b -> destroyMode = !destroyMode)
                .bounds(leftPos + 80, topPos + GRID_Y + ROWS * CELL + 3, 70, 12).build());
    }

    private void send(int action, ItemStack item, int button) {
        PacketDistributor.sendToServer(new SingularityPayloads.Action(menu.containerId, action, item.copyWithCount(Math.max(1, item.getCount())), button));
    }

    /** The kinds shown, filtered by the search, most held first; then kinds marked to destroy that it doesn't hold. */
    private void rebuild() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (builtVersion == menu.contentsVersion && q.equals(builtSearch)) {
            return;
        }
        builtVersion = menu.contentsVersion;
        builtSearch = q;
        shown.clear();
        for (int i = 0; i < menu.items.size(); i++) {
            if (q.isEmpty() || menu.items.get(i).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(i);
            }
        }
        shown.sort((a, b) -> Long.compare(menu.counts.get(b), menu.counts.get(a)));
        destroyedOnly.clear();
        for (net.minecraft.world.item.Item item : menu.destroyed) {
            boolean held = false;
            for (ItemStack s : menu.items) {
                if (s.is(item)) {
                    held = true;
                    break;
                }
            }
            ItemStack s = new ItemStack(item);
            if (!held && (q.isEmpty() || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q))) {
                shown.add(-1 - destroyedOnly.size());
                destroyedOnly.add(s);
            }
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + COLS - 1) / COLS - ROWS);
    }

    private ItemStack itemAt(int k) {
        return k >= 0 ? menu.items.get(k) : destroyedOnly.get(-1 - k);
    }

    private long countAt(int k) {
        return k >= 0 ? menu.counts.get(k) : 0L;
    }

    /** The entry under the mouse (index into shown), -1 none; -2: an empty cell of the grid. */
    private int cellAt(double mx, double my) {
        int x = (int) Math.floor((mx - leftPos - GRID_X) / CELL), y = (int) Math.floor((my - topPos - GRID_Y) / CELL);
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS) {
            return -1;
        }
        int i = (y + scroll) * COLS + x;
        return i < shown.size() ? i : -2;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        vacuumButton.setMessage(Component.translatable(menu.vacuum ? "gui.skyblocks.singularity.vacuum_on" : "gui.skyblocks.singularity.vacuum_off"));
        destroyButton.setMessage(Component.translatable(destroyMode ? "gui.skyblocks.singularity.destroy_on" : "gui.skyblocks.singularity.destroy_off")
                .withStyle(destroyMode ? ChatFormatting.RED : ChatFormatting.WHITE));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        rebuild();
        int x0 = leftPos, y0 = topPos;
        // the panel: deep space, a purple rim
        g.fill(x0, y0, x0 + imageWidth, y0 + imageHeight, 0xFF2A1840);
        g.fill(x0 + 2, y0 + 2, x0 + imageWidth - 2, y0 + imageHeight - 2, 0xFF0D0716);
        // the grid
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int cx = x0 + GRID_X + c * CELL, cy = y0 + GRID_Y + r * CELL;
                g.fill(cx, cy, cx + 17, cy + 17, 0xFF231533);
                g.fill(cx + 1, cy + 1, cx + 17, cy + 17, 0xFF140B20);
            }
        }
        // the scroll bar
        int bx = x0 + GRID_X + COLS * CELL + 4, by = y0 + GRID_Y, bh = ROWS * CELL - 1;
        g.fill(bx, by, bx + 6, by + bh, 0xFF140B20);
        int max = maxScroll();
        int knob = Math.max(10, bh / Math.max(1, max + 1));
        int ky = by + (max == 0 ? 0 : (bh - knob) * scroll / max);
        g.fill(bx, ky, bx + 6, ky + knob, max == 0 ? 0xFF3A2A50 : 0xFF9A6BFF);
        // your inventory's slots
        for (int i = 0; i < 36; i++) {
            int sx = x0 + 7 + (i % 9) * 18, sy = y0 + SingularityMenu.INVENTORY_Y - 1 + (i < 27 ? (i / 9) * 18 : 58);
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF231533);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF140B20);
        }
        // the kinds it holds, with how many
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int i = (r + scroll) * COLS + c;
                if (i >= shown.size()) {
                    continue;
                }
                int k = shown.get(i);
                ItemStack stack = itemAt(k);
                int cx = leftPos + GRID_X + c * CELL + 1, cy = topPos + GRID_Y + r * CELL + 1;
                g.renderItem(stack, cx, cy);
                g.pose().pushPose();
                g.pose().translate(0.0F, 0.0F, 200.0F);
                if (menu.destroyed.contains(stack.getItem())) {
                    g.fill(cx, cy, cx + 16, cy + 16, 0x88C02020);
                    g.drawString(font, "x", cx + 1, cy, 0xFFFF6060, true);
                }
                long n = countAt(k);
                if (n > 0) {
                    String s = SingularityMenu.amount(n);
                    float k2 = font.width(s) * 0.75F <= 12.0F ? 0.75F : 0.5F; // (smaller when it wouldn't fit in the slot)
                    g.pose().translate(cx + 17.0F, cy + 17.0F, 0.0F);
                    g.pose().scale(k2, k2, 1.0F);
                    g.drawString(font, s, -font.width(s), -font.lineHeight + 1, 0xFFFFFFFF, true);
                }
                g.pose().popPose();
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xFFD9C2FF, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFFB9A2DF, false);
        long total = 0;
        for (long c : menu.counts) {
            total += c;
        }
        Component info = Component.translatable("gui.skyblocks.singularity.info", SingularityMenu.amount(total), menu.items.size());
        g.drawString(font, info, imageWidth - 8 - font.width(info), inventoryLabelY, 0xFF8F7AAF, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int hover = cellAt(mouseX, mouseY);
        if (hover >= 0) {
            int c = hover % COLS, r = hover / COLS - scroll;
            int cx = leftPos + GRID_X + c * CELL + 1, cy = topPos + GRID_Y + r * CELL + 1;
            g.fill(cx, cy, cx + 16, cy + 16, 0x60FFFFFF);
            if (menu.getCarried().isEmpty()) {
                int k = shown.get(hover);
                ItemStack stack = itemAt(k);
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, stack));
                lines.add(Component.translatable("gui.skyblocks.singularity.stored", String.format(Locale.ROOT, "%,d", countAt(k)))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                if (menu.destroyed.contains(stack.getItem())) {
                    lines.add(Component.translatable("gui.skyblocks.singularity.destroyed").withStyle(ChatFormatting.RED));
                }
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cell = cellAt(mx, my);
        if (cell != -1 && (button == 0 || button == 1)) {
            boolean shift = hasShiftDown();
            ItemStack item = cell >= 0 ? itemAt(shown.get(cell)) : ItemStack.EMPTY;
            if (destroyMode) {
                if (!item.isEmpty() || !menu.getCarried().isEmpty()) {
                    send(SingularityMenu.DESTROY, item, 0);
                }
            } else if (!menu.getCarried().isEmpty()) {
                send(cell >= 0 && countAt(shown.get(cell)) > 0 ? SingularityMenu.TAKE : SingularityMenu.PUT, item, button);
            } else if (cell >= 0 && countAt(shown.get(cell)) > 0) {
                send(SingularityMenu.TAKE, item, shift ? 2 : button);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int max = maxScroll();
        if (max > 0) {
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (search.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(key, scan, modifiers);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
