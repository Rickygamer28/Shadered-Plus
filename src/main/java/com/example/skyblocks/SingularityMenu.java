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
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inv, buf) -> new SingularityMenu(id, inv, buf.readBlockPos())));

    /** Where your inventory starts in the menu (pixels; client/SingularityScreen draws round it). */
    public static final int INVENTORY_Y = 140;

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

    public SingularityMenu(int id, Inventory inventory, BlockPos pos) {
        super(TYPE.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
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
        if (be != null && player instanceof ServerPlayer sp && be.version() != sentVersion && (sentVersion == -1 || sp.tickCount - sentAt >= 5)) {
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
            PacketDistributor.sendToPlayer(sp, new SingularityPayloads.Contents(containerId, list, amounts, gone, tagNames, be.vacuum()));
        }
    }

    // ---- the actions from the menu (SingularityPayloads.Action, on the server) ----

    public static final int TAKE = 0, PUT = 1, DESTROY = 2, VACUUM = 3, DESTROY_TAG = 4;

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
            case DESTROY_TAG -> be.toggleDestroyTag(text);
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
        items.clear();
        items.addAll(c.items());
        counts.clear();
        counts.addAll(c.counts());
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
