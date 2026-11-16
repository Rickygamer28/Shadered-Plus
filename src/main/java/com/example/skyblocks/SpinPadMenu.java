package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * A spin pad's screen (client/SpinPadScreen): its cats' slot (OIIA cat blocks: one on a pad alone, up to its side cubed
 * on a big pad) and your inventory; what it's doing is read from the pad itself (kept up to date on the client).
 */
public class SpinPadMenu extends AbstractContainerMenu {
    public static final DeferredHolder<MenuType<?>, MenuType<SpinPadMenu>> TYPE = SingularityMenu.MENUS.register("spin_pad",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inv, buf) -> new SpinPadMenu(id, inv, buf.readBlockPos())));
    /** Where things are in the screen (pixels). */
    public static final int CAT_X = 26, CAT_Y = 44, INVENTORY_Y = 110;

    public final BlockPos pos;
    private final Player player;

    public SpinPadMenu(int id, Inventory inventory, BlockPos pos) {
        super(TYPE.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        IItemHandler handler = !player.level().isClientSide && player.level().getBlockEntity(pos) instanceof SpinPadBlockEntity pad ? pad.cats
                : new ItemStackHandler(1) {
                    @Override
                    public int getSlotLimit(int slot) {
                        SpinPadBlockEntity pad = pad();
                        return pad == null ? 64 : pad.maxCats();
                    }

                    @Override
                    public boolean isItemValid(int slot, ItemStack stack) {
                        return stack.is(ModItems.OIIA_CAT.get());
                    }
                };
        addSlot(new SlotItemHandler(handler, 0, CAT_X, CAT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    public SpinPadBlockEntity pad() {
        return player.level().getBlockEntity(pos) instanceof SpinPadBlockEntity p ? p : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return pad() != null && player.distanceToSqr(pos.getCenter()) <= 10.0 * 10.0;
    }

    /** Shift-click: cats from your inventory into it, or out of it into your inventory. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!stack.is(ModItems.OIIA_CAT.get()) || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
