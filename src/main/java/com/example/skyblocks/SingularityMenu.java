package com.example.skyblocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The singularity's menu: your inventory (shift-click puts a stack in), and what it holds - sent over as it changes
 * (SingularityPayloads), shown and clicked on in client/SingularityScreen.
 */
public class SingularityMenu extends AbstractContainerMenu {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, SkyBlocksMod.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<SingularityMenu>> TYPE = MENUS.register("singularity",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inv, buf) -> new SingularityMenu(id, inv, buf.readBlockPos(), buf.readBoolean())));

    /** Where your inventory starts in the menu (pixels; client/SingularityScreen draws round it). */
    public static final int INVENTORY_Y = 148;

    public final BlockPos pos;
    private final Player player;
    private int sentVersion = -1, sentAt;

    // (client: what it holds, as last sent)
    public final List<ItemStack> items = new ArrayList<>();
    public final List<Long> counts = new ArrayList<>();
    public final Set<Item> destroyed = new HashSet<>();
    public final List<String> tags = new ArrayList<>();
    public boolean vacuum;
    public int contentsVersion;

    /** Opened from a crafting frame: a crafting grid beside it (slots 36: the result, 37-45: the grid), fed from what it holds. */
    public final boolean crafting;
    public static final int RESULT = 36, GRID = 37, CRAFT_X = 200, CRAFT_Y = 40;
    private final net.minecraft.world.inventory.CraftingContainer grid;
    private final net.minecraft.world.inventory.ResultContainer result = new net.minecraft.world.inventory.ResultContainer();

    public SingularityMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, false);
    }

    public SingularityMenu(int id, Inventory inventory, BlockPos pos, boolean crafting) {
        super(TYPE.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        this.crafting = crafting;
        this.grid = new net.minecraft.world.inventory.TransientCraftingContainer(this, 3, 3);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
        if (crafting) {
            addSlot(new Result(player, grid, result, 0, CRAFT_X + 76, CRAFT_Y + 18));
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    addSlot(new Slot(grid, c + r * 3, CRAFT_X + c * 18, CRAFT_Y + r * 18));
                }
            }
        }
    }

    /** The crafted thing: taking it uses up one of each in the grid - then the grid is filled again from what it holds. */
    private class Result extends net.minecraft.world.inventory.ResultSlot {
        Result(Player player, net.minecraft.world.inventory.CraftingContainer grid, net.minecraft.world.Container result, int index, int x, int y) {
            super(player, grid, result, index, x, y);
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            ItemStack[] before = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                before[i] = grid.getItem(i).copyWithCount(1);
            }
            super.onTake(taker, stack);
            SingularityBlockEntity be = blockEntity();
            if (be != null && !taker.level().isClientSide) {
                for (int i = 0; i < 9; i++) {
                    if (!before[i].isEmpty() && grid.getItem(i).isEmpty()) {
                        ItemStack got = be.extract(before[i], 1, false);
                        if (!got.isEmpty()) {
                            grid.setItem(i, got);
                        }
                    }
                }
            }
        }
    }

    /** (The grid changed: what it makes, worked out on the server.) */
    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        if (crafting && container == grid && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            var input = grid.asCraftInput();
            ItemStack made = level.getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                    .map(h -> h.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
            result.setItem(0, made);
            broadcastChanges();
        }
        super.slotsChanged(container);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    /** (Closed: what's left in the grid back into it - or, if it won't take it, to you.) */
    @Override
    public void removed(Player who) {
        super.removed(who);
        if (crafting && !who.level().isClientSide) {
            clearGrid(who);
        }
    }

    private void clearGrid(Player who) {
        SingularityBlockEntity be = blockEntity();
        for (int i = 0; i < 9; i++) {
            ItemStack s = grid.removeItemNoUpdate(i);
            if (!s.isEmpty()) {
                ItemStack left = be == null ? s : be.insert(s, false);
                if (!left.isEmpty() && !who.getInventory().add(left)) {
                    who.drop(left, false);
                }
            }
        }
        slotsChanged(grid);
    }

    public SingularityBlockEntity blockEntity() {
        return player.level().getBlockEntity(pos) instanceof SingularityBlockEntity be ? be : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity() != null && player.distanceToSqr(pos.getCenter()) <= 11.0 * 11.0; // (from its chamber's frame too)
    }

    /** Shift-click in your inventory: the whole stack into the singularity. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (crafting && index == RESULT) { // (shift-click what it makes: as many as it can, into your inventory)
            if (!slot.hasItem()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = slot.getItem(), copy = stack.copy();
            stack.getItem().onCraftedBy(stack, player.level(), player);
            if (!moveItemStackTo(stack, 0, 36, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == copy.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
            return copy;
        }
        if (crafting && index >= GRID) { // (from the grid: into your inventory)
            if (!slot.hasItem()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = slot.getItem(), copy = stack.copy();
            if (!moveItemStackTo(stack, 0, 36, false)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
            return copy;
        }
        SingularityBlockEntity be = blockEntity();
        if (be == null || !slot.hasItem() || player.level().isClientSide) {
            return ItemStack.EMPTY;
        }
        ItemStack left = be.insert(slot.getItem().copy(), false);
        slot.set(left);
        return ItemStack.EMPTY;
    }

    /** What it holds, to the player, whenever it changed. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        SingularityBlockEntity be = blockEntity();
        // (what it holds sent when it changed - at most 4 times a second: thousands of kinds, a hopper feeding it)
        // (more often the fewer kinds: a big one's pages take a while to send)
        int every = 5 + (be == null ? 0 : be.entries().size() / 1024);
        if (be != null && player instanceof ServerPlayer sp && be.version() != sentVersion && (sentVersion == -1 || sp.tickCount - sentAt >= every)) {
            sentAt = sp.tickCount;
            sentVersion = be.version();
            List<ItemStack> list = new ArrayList<>();
            List<Long> amounts = new ArrayList<>();
            for (SingularityBlockEntity.Entry e : be.entries()) {
                list.add(e.item);
                amounts.add(e.count);
            }
            List<String> gone = new ArrayList<>();
            for (Item item : be.destroyed()) {
                gone.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
            }
            List<String> tagNames = new ArrayList<>();
            for (net.minecraft.tags.TagKey<Item> t : be.destroyTags()) {
                tagNames.add(t.location().toString());
            }
            // in pages of 256 kinds (the first with the filter and vacuum mode)
            int total = list.size();
            for (int start = 0; start == 0 || start < total; start += PAGE) {
                int end = Math.min(total, start + PAGE);
                PacketDistributor.sendToPlayer(sp, new SingularityPayloads.Contents(containerId, start, total,
                        new ArrayList<>(list.subList(start, end)), new ArrayList<>(amounts.subList(start, end)),
                        start == 0 ? gone : List.of(), start == 0 ? tagNames : List.of(), be.vacuum()));
            }
        }
    }

    // ---- the actions from the menu (SingularityPayloads.Action, on the server) ----

    public static final int TAKE = 0, PUT = 1, DESTROY = 2, VACUUM = 3, DESTROY_TAG = 4, SKIN = 5, CLEAR_GRID = 6;
    private static final int PAGE = 256;
    // (client: the pages coming in)
    private final List<ItemStack> incomingItems = new ArrayList<>();
    private final List<Long> incomingCounts = new ArrayList<>();

    /** button: 0 left (a stack / all you hold), 1 right (one), 2 shift (a stack into your inventory). */
    public void act(int action, ItemStack item, int button, String text) {
        SingularityBlockEntity be = blockEntity();
        if (be == null) {
            return;
        }
        switch (action) {
            case TAKE -> {
                if (button == 2) {
                    ItemStack out = be.extract(item, Integer.MAX_VALUE, true);
                    if (out.isEmpty()) return;
                    ItemStack copy = out.copy();
                    player.getInventory().add(copy); // (as much as fits)
                    int moved = out.getCount() - copy.getCount();
                    if (moved > 0) be.extract(item, moved, false);
                } else if (getCarried().isEmpty()) {
                    setCarried(be.extract(item, button == 1 ? 1 : Integer.MAX_VALUE, false));
                } else if (ItemStack.isSameItemSameComponents(getCarried(), item) && getCarried().getCount() < getCarried().getMaxStackSize()) {
                    ItemStack more = be.extract(item, button == 1 ? 1 : getCarried().getMaxStackSize() - getCarried().getCount(), false);
                    getCarried().grow(more.getCount());
                } else {
                    put(be, button);
                }
            }
            case PUT -> put(be, button);
            case DESTROY -> {
                ItemStack which = getCarried().isEmpty() ? item : getCarried();
                if (!which.isEmpty()) be.toggleDestroy(which.getItem());
            }
            case VACUUM -> be.toggleVacuum();
            case SKIN -> be.cycleSkin();
            case DESTROY_TAG -> be.toggleDestroyTag(text);
            case CLEAR_GRID -> {
                if (crafting) {
                    clearGrid(player);
                }
            }
            default -> {
            }
        }
        broadcastChanges();
    }

    private void put(SingularityBlockEntity be, int button) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }
        if (button == 1) {
            if (be.insert(carried.copyWithCount(1), false).isEmpty()) carried.shrink(1);
        } else {
            setCarried(be.insert(carried.copy(), false));
        }
    }

    /** A count, short: 950, 12.3k, 4.5M, 1.2B. */
    public static String amount(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return String.format(Locale.ROOT, n < 10_000 ? "%.1fk" : "%.0fk", n / 1000.0);
        if (n < 1_000_000_000L) return String.format(Locale.ROOT, n < 10_000_000 ? "%.1fM" : "%.0fM", n / 1.0e6);
        return String.format(Locale.ROOT, "%.1fB", n / 1.0e9);
    }

    /** (client) The contents sent over. */
    public void setContents(SingularityPayloads.Contents c) {
        if (c.start() == 0) {
            incomingItems.clear();
            incomingCounts.clear();
        } else if (c.start() != incomingItems.size()) {
            return; // (a page out of turn: wait for the next whole set)
        }
        incomingItems.addAll(c.items());
        incomingCounts.addAll(c.counts());
        if (c.start() == 0) {
            setFilter(c);
        }
        if (incomingItems.size() < c.total()) {
            return; // (more pages to come)
        }
        items.clear();
        items.addAll(incomingItems);
        counts.clear();
        counts.addAll(incomingCounts);
        contentsVersion++;
    }

    private void setFilter(SingularityPayloads.Contents c) {
        destroyed.clear();
        for (String id : c.destroyed()) {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (rl != null) net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(rl).ifPresent(destroyed::add);
        }
        tags.clear();
        tags.addAll(c.tags());
        vacuum = c.vacuum();
        contentsVersion++;
    }

}
