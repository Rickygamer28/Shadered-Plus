package com.example.skyblocks.client;

import com.example.skyblocks.ModBlocks;
import com.example.skyblocks.PondMaterialsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The vanilla koi pond's menu: what its rim and its floor are made of - any full block, picked from a grid (search it
 * by name). It changes the whole pond (every vanilla koi pond joined to this one), as you pick.
 */
public class VanillaPondScreen extends Screen {
    private static final int PANEL_W = 232, PANEL_H = 234;
    private static final int COLS = 11, ROWS = 6, TILE = 19;
    private static final int GRID_X = 11, GRID_Y = 74;

    private static List<ItemStack> allBlocks;

    private final BlockPos pos;
    private String rim, floor;
    private boolean floorTab;
    private EditBox search;
    private Button rimTab, floorTabButton;
    private List<ItemStack> shown = new ArrayList<>();
    private int scroll;

    public VanillaPondScreen(BlockPos pos, @Nullable BlockState rim, @Nullable BlockState floor) {
        super(Component.translatable("screen.skyblocks.vanilla_pond"));
        this.pos = pos;
        this.rim = rim == null ? "" : BuiltInRegistries.BLOCK.getKey(rim.getBlock()).toString();
        this.floor = floor == null ? "" : BuiltInRegistries.BLOCK.getKey(floor.getBlock()).toString();
    }

    /** Every full, plain block there is (as items, to show), in the order the game lists them. */
    private static List<ItemStack> allBlocks() {
        if (allBlocks == null) {
            allBlocks = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (!(item instanceof BlockItem bi)) {
                    continue;
                }
                Block b = bi.getBlock();
                if (b == ModBlocks.KOI_POND.get() || b == ModBlocks.VANILLA_KOI_POND.get()) {
                    continue;
                }
                try {
                    BlockState st = b.defaultBlockState();
                    if (st.getRenderShape() == RenderShape.MODEL && Block.isShapeFullBlock(st.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
                        allBlocks.add(new ItemStack(item));
                    }
                } catch (RuntimeException e) {
                    // (a block that can't say its shape without a world: left out)
                }
            }
        }
        return allBlocks;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        rimTab = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.pond_rim"), b -> tab(false))
                .bounds(left() + 11, top() + 24, 104, 20).build());
        floorTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.pond_floor"), b -> tab(true))
                .bounds(left() + PANEL_W - 115, top() + 24, 104, 20).build());
        search = new EditBox(font, left() + 11, top() + 50, PANEL_W - 22, 18, Component.translatable("screen.skyblocks.pond_search"));
        search.setHint(Component.translatable("screen.skyblocks.pond_search"));
        search.setResponder(v -> filter());
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.pond_default"), b -> pick(""))
                .bounds(left() + 11, top() + PANEL_H - 28, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + PANEL_W - 87, top() + PANEL_H - 28, 76, 20).build());
        tab(floorTab);
        filter();
    }

    private void tab(boolean floor) {
        floorTab = floor;
        rimTab.active = floor;
        floorTabButton.active = !floor;
    }

    private void filter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        for (ItemStack s : allBlocks()) {
            if (q.isEmpty() || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains(q)) {
                shown.add(s);
            }
        }
        scroll = 0;
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + COLS - 1) / COLS - ROWS);
    }

    private static String idOf(ItemStack s) {
        return BuiltInRegistries.BLOCK.getKey(((BlockItem) s.getItem()).getBlock()).toString();
    }

    private void pick(String id) {
        if (floorTab) {
            floor = id;
        } else {
            rim = id;
        }
        PacketDistributor.sendToServer(new PondMaterialsPayload(pos, rim, floor));
    }

    /** Which block of the grid is under the mouse (-1: none). */
    private int tileAt(double mx, double my) {
        int gx = (int) Math.floor((mx - left() - GRID_X) / TILE), gy = (int) Math.floor((my - top() - GRID_Y) / TILE);
        if (gx < 0 || gx >= COLS || gy < 0 || gy >= ROWS || mx < left() + GRID_X || my < top() + GRID_Y) {
            return -1;
        }
        int i = (gy + scroll) * COLS + gx;
        return i < shown.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = tileAt(mx, my);
        if (i >= 0 && button == 0) {
            pick(idOf(shown.get(i)));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left(), top(), left() + PANEL_W, top() + PANEL_H, 0xC0101018);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left() + PANEL_W / 2, top() + 9, 0xFFFFFF);
        // the grid of blocks
        String chosen = floorTab ? floor : rim;
        int gx0 = left() + GRID_X, gy0 = top() + GRID_Y;
        graphics.fill(gx0 - 1, gy0 - 1, gx0 + COLS * TILE + 1, gy0 + ROWS * TILE + 1, 0x60000000);
        int hover = tileAt(mouseX, mouseY);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int i = (r + scroll) * COLS + c;
                if (i >= shown.size()) {
                    break;
                }
                int x = gx0 + c * TILE, y = gy0 + r * TILE;
                ItemStack s = shown.get(i);
                if (idOf(s).equals(chosen)) {
                    graphics.fill(x, y, x + TILE - 1, y + TILE - 1, 0x8040C0FF);
                    graphics.renderOutline(x, y, TILE - 1, TILE - 1, 0xFFFFFFFF);
                } else if (i == hover) {
                    graphics.fill(x, y, x + TILE - 1, y + TILE - 1, 0x40FFFFFF);
                }
                graphics.renderItem(s, x + 1, y + 1);
            }
        }
        // the scroll bar
        int max = maxScroll();
        if (max > 0) {
            int bx = gx0 + COLS * TILE + 3, h = ROWS * TILE;
            graphics.fill(bx, gy0, bx + 4, gy0 + h, 0x60000000);
            int bh = Math.max(10, h * ROWS / (ROWS + max)), by = gy0 + (h - bh) * scroll / max;
            graphics.fill(bx, by, bx + 4, by + bh, 0xFFA0A0A0);
        }
        // what it is now
        Component now = chosen.isEmpty() ? Component.translatable("screen.skyblocks.pond_default")
                : BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(chosen)).getName();
        graphics.drawString(font, Component.translatable(floorTab ? "screen.skyblocks.pond_floor_now" : "screen.skyblocks.pond_rim_now", now),
                left() + 11, gy0 + ROWS * TILE + 6, 0xA0A0A0);
        if (hover >= 0) {
            graphics.renderTooltip(font, shown.get(hover), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
