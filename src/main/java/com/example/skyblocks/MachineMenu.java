package com.example.skyblocks;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * A machine's screen (client/MachineScreen): its slots, laid out for its kind - the particle accelerator's item to smash
 * and what came of it; the star forge's fuel, what to work and what it made; the weather machine has none, only its
 * buttons - and your inventory. What it's doing is read from the machine itself (kept up to date on the client).
 */
public class MachineMenu extends AbstractContainerMenu {
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> TYPE = SingularityMenu.MENUS.register("machine",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inv, buf) -> new MachineMenu(id, inv, buf.readBlockPos())));
    public static final int INVENTORY_Y = 134;
    /** Where each kind's slots are: (x, y) for each, in order. */
    public static final int[][] ACCELERATOR = slots(new int[] {35, 40}, 98, 34);
    public static final int[][] FORGE = forge();

    public final BlockPos pos;
    private final Player player;
    private final int machineSlots;

    private static int[][] slots(int[] first, int gx, int gy) {
        int[][] out = new int[10][];
        out[0] = first;
        for (int i = 0; i < 9; i++) {
            out[1 + i] = new int[] {gx + (i % 3) * 18, gy + (i / 3) * 18};
        }
        return out;
    }

    private static int[][] forge() {
        int[][] out = new int[19][];
        out[0] = new int[] {12, 70};
        for (int i = 0; i < 9; i++) {
            out[1 + i] = new int[] {38 + (i % 3) * 18, 34 + (i / 3) * 18};
            out[10 + i] = new int[] {114 + (i % 3) * 18, 34 + (i / 3) * 18};
        }
        return out;
    }

    public MachineMenu(int id, Inventory inventory, BlockPos pos) {
        super(TYPE.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        MachineBlockEntity be = machine();
        int[][] layout = be instanceof ParticleAcceleratorBlockEntity ? ACCELERATOR : be instanceof StarForgeBlockEntity ? FORGE : new int[0][];
        IItemHandler handler = !player.level().isClientSide && be != null ? be.items : new ItemStackHandler(layout.length);
        machineSlots = layout.length;
        for (int i = 0; i < layout.length; i++) {
            final int slot = i;
            final boolean output = be instanceof ParticleAcceleratorBlockEntity ? i >= 1 : be instanceof StarForgeBlockEntity && i >= StarForgeBlockEntity.OUT;
            addSlot(new SlotItemHandler(handler, i, layout[i][0], layout[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (output) {
                        return false;
                    }
                    return !(machine() instanceof StarForgeBlockEntity) || slot != StarForgeBlockEntity.FUEL || StarForgeBlockEntity.FUELS.containsKey(stack.getItem());
                }
            });
        }
        if (layout.length == 0) {
            return; // (nothing to put in it: no inventory shown)
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    @Nullable
    public MachineBlockEntity machine() {
        return player.level().getBlockEntity(pos) instanceof MachineBlockEntity m ? m : null;
    }

    /** Its buttons: the weather machine's modes (0 off .. 3 thunder); 10, the star forge's smelting / forging. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        MachineBlockEntity m = machine();
        if (m instanceof WeatherMachineBlockEntity w && id >= 0 && id < WeatherMachineBlockEntity.MODES.length) {
            w.setMode(id);
            return true;
        }
        if (m instanceof StarForgeBlockEntity f && id == 10) {
            f.toggleMode();
            return true;
        }
        if (m instanceof StarForgeBlockEntity f && id == 11) {
            f.cyclePowerColour();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine() != null && player.distanceToSqr(pos.getCenter()) <= 12.0 * 12.0;
    }

    /** Shift-click: into it (fuel to a forge's fuel slot), or out of it into your inventory. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            MachineBlockEntity m = machine();
            boolean moved = false;
            if (m instanceof StarForgeBlockEntity && StarForgeBlockEntity.FUELS.containsKey(stack.getItem())) {
                moved = moveItemStackTo(stack, StarForgeBlockEntity.FUEL, StarForgeBlockEntity.FUEL + 1, false);
            }
            if (!moved && m instanceof StarForgeBlockEntity) {
                moved = moveItemStackTo(stack, StarForgeBlockEntity.IN, StarForgeBlockEntity.IN + StarForgeBlockEntity.SLOTS, false);
            }
            if (!moved && m instanceof ParticleAcceleratorBlockEntity) {
                moved = moveItemStackTo(stack, 0, 1, false);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
