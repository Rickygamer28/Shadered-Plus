package com.example.skyblocks.client;

import com.example.skyblocks.MachineBlockEntity;
import com.example.skyblocks.MachineMenu;
import com.example.skyblocks.ParticleAcceleratorBlockEntity;
import com.example.skyblocks.StarForgeBlockEntity;
import com.example.skyblocks.WeatherMachineBlockEntity;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A machine's screen (MachineMenu): the particle accelerator's (what to smash, what came of it, how fast its beams are
 * going, its energy), the weather machine's (its mode buttons, how far through turning the weather, its energy and the
 * lightning it's caught) and the star forge's (its fuel and star, what it's working, smelting or forging).
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int PANEL = 0xF0101420, SLOT = 0xFF20283A, SLOT_EDGE = 0xFF3C4A60, TEXT = 0xFFE4ECF6, DIM = 0xFF8C98AC;
    private static final String[] SIZES = {"none", "small", "medium", "large"};
    private final Button[] modes = new Button[4];
    private Button forgeMode, forgePower;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        boolean weather = menu.machine() instanceof WeatherMachineBlockEntity;
        imageHeight = weather ? 116 : MachineMenu.INVENTORY_Y + 82;
        inventoryLabelY = MachineMenu.INVENTORY_Y - 11;
    }

    private int accent() {
        MachineBlockEntity m = menu.machine();
        if (m instanceof StarForgeBlockEntity f) {
            return new int[] {0xFFFF6A3A, 0xFFFFC23A, 0xFF5EA8FF, 0xFFF2F0FF}[f.colour()];
        }
        return m instanceof WeatherMachineBlockEntity ? 0xFF7FC8FF : 0xFF4FE8F0;
    }

    @Override
    protected void init() {
        super.init();
        MachineBlockEntity m = menu.machine();
        if (m instanceof WeatherMachineBlockEntity) {
            for (int i = 0; i < 4; i++) {
                final int id = i;
                modes[i] = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.weather_machine.mode." + WeatherMachineBlockEntity.MODES[i]),
                        b -> press(id)).bounds(leftPos + 8 + i * 40, topPos + 20, 38, 16).build());
            }
        } else if (m instanceof StarForgeBlockEntity) {
            forgeMode = addRenderableWidget(Button.builder(Component.empty(), b -> press(10)).bounds(leftPos + imageWidth - 62, topPos + 4, 56, 14).build());
            forgePower = addRenderableWidget(Button.builder(Component.empty(), b -> press(11)).bounds(leftPos + imageWidth - 112, topPos + 4, 48, 14).build());
        }
        labels();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void labels() {
        MachineBlockEntity m = menu.machine();
        if (m instanceof WeatherMachineBlockEntity w) {
            for (int i = 0; i < 4; i++) {
                if (modes[i] != null) {
                    modes[i].active = w.mode() != i;
                }
            }
        }
        if (m instanceof StarForgeBlockEntity f && forgeMode != null) {
            forgeMode.setMessage(Component.translatable(f.mode() == 0 ? "screen.skyblocks.star_forge.smelting" : "screen.skyblocks.star_forge.forging"));
            if (forgePower != null) {
                Component c = Component.translatable("screen.skyblocks.star_forge.colour." + StarForgeBlockEntity.CLASSES[f.powerColour()]);
                forgePower.setMessage(Component.literal("\u26A1 ").append(c));
                forgePower.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("screen.skyblocks.star_forge.power_tip", c, String.format("%,d", f.powerCost()))));
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        labels();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, accent());
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        for (Slot s : menu.slots) {
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, SLOT_EDGE);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT);
        }
        MachineBlockEntity m = menu.machine();
        if (m instanceof StarForgeBlockEntity f) {
            // its fuel slot's star: how much fuel's left, in its colour; the arrow: how far through
            int fx = x + 12, fy = y + 26;
            g.fill(fx - 1, fy - 1, fx + 17, fy + 31, SLOT_EDGE);
            g.fill(fx, fy, fx + 16, fy + 30, 0xFF0C0F18);
            int h = Math.round(30 * f.fuelLeft());
            g.fill(fx, fy + 30 - h, fx + 16, fy + 30, accent());
            int ax = x + 94, ay = y + 46;
            g.fill(ax, ay, ax + 16, ay + 6, 0xFF26303E);
            g.fill(ax, ay, ax + Math.round(16 * f.progress()), ay + 6, accent());
            g.fill(ax + 16, ay - 2, ax + 18, ay + 8, 0xFF26303E);
        } else if (m instanceof ParticleAcceleratorBlockEntity) {
            // its item to smash: a ring round it
            int cx = x + MachineMenu.ACCELERATOR[0][0], cy = y + MachineMenu.ACCELERATOR[0][1];
            g.fill(cx - 4, cy - 4, cx + 20, cy + 20, accent());
            g.fill(cx - 3, cy - 3, cx + 19, cy + 19, PANEL);
            g.fill(cx - 1, cy - 1, cx + 17, cy + 17, SLOT_EDGE);
            g.fill(cx, cy, cx + 16, cy + 16, SLOT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        MachineBlockEntity m = menu.machine();
        if (!(m instanceof WeatherMachineBlockEntity)) {
            g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, DIM, false);
        }
        if (m == null) {
            return;
        }
        if (m.size() == 0) {
            g.drawString(font, Component.translatable("screen.skyblocks.machine.unformed"), 8, m instanceof WeatherMachineBlockEntity ? 44 : 84, 0xFFFF8A7A, false);
            g.drawString(font, Component.translatable("screen.skyblocks.machine.unformed." + kind(m)), 8, m instanceof WeatherMachineBlockEntity ? 56 : 96, DIM, false);
            return;
        }
        if (m instanceof ParticleAcceleratorBlockEntity a) {
            accelerator(g, a);
        } else if (m instanceof WeatherMachineBlockEntity w) {
            weather(g, w);
        } else if (m instanceof StarForgeBlockEntity f) {
            forge(g, f);
        }
    }

    private static String kind(MachineBlockEntity m) {
        return m instanceof ParticleAcceleratorBlockEntity ? "accelerator" : m instanceof WeatherMachineBlockEntity ? "weather" : "forge";
    }

    private void accelerator(GuiGraphics g, ParticleAcceleratorBlockEntity a) {
        int r = ParticleAcceleratorBlockEntity.RADIUS[a.size()] * 2 + 1;
        g.drawCenteredString(font, Component.translatable("screen.skyblocks.particle_accelerator.smash"), 43, 30, DIM);
        g.drawString(font, Component.translatable("screen.skyblocks.particle_accelerator.made"), 98, 16, DIM, false);
        boolean empty = menu.slots.get(0).getItem().isEmpty();
        Component state = empty ? Component.translatable("screen.skyblocks.particle_accelerator.waiting")
                : a.speed() > 0.01F ? Component.translatable("screen.skyblocks.particle_accelerator.accelerating", Math.round(a.speed() * 100))
                : Component.translatable("screen.skyblocks.particle_accelerator.no_power");
        g.drawString(font, state, 8, 84, empty ? 0xFFFFC870 : 0xFF8FF0E8, false);
        bar(g, 8, 94, imageWidth - 16, a.speed(), 0xFF1A6A90, 0xFF6FFFF0);
        g.drawString(font, Component.translatable("screen.skyblocks.machine.energy", compact(a.energy.getEnergyStored()), compact(a.energy.getMaxEnergyStored()),
                String.format("%,d", a.drive())), 8, 102, TEXT, false);
        Component ring = Component.translatable("screen.skyblocks.particle_accelerator.ring", Component.translatable("screen.skyblocks.machine.size." + SIZES[a.size()]),
                r, r, a.collisions());
        g.drawString(font, ring, 8, 112, DIM, false);
        // (what's in it makes something for sure: shown)
        var recipe = ParticleAcceleratorBlockEntity.smash(menu.slots.get(0).getItem(), a.size());
        if (recipe != null) {
            g.drawString(font, Component.translatable("screen.skyblocks.particle_accelerator.recipe", ""), 8, 18, 0xFF8FF0E8, false);
            ItemStack out = new ItemStack(recipe.out(), recipe.count());
            int ix = 8 + font.width(Component.translatable("screen.skyblocks.particle_accelerator.recipe", ""));
            g.renderItem(out, ix, 13);
            g.renderItemDecorations(font, out, ix, 13);
        }
        ItemStack last = a.last();
        if (!last.isEmpty()) {
            g.drawString(font, Component.translatable("screen.skyblocks.particle_accelerator.last"), 8, 70, DIM, false);
            g.renderItem(last, 36, 66);
            g.renderItemDecorations(font, last, 36, 66);
        }
    }

    private void weather(GuiGraphics g, WeatherMachineBlockEntity w) {
        Component state;
        int colour;
        if (w.mode() == 0) {
            state = Component.translatable("screen.skyblocks.weather_machine.off");
            colour = DIM;
        } else if (w.holding()) {
            state = Component.translatable("screen.skyblocks.weather_machine.holding",
                    Component.translatable("screen.skyblocks.weather_machine.mode." + WeatherMachineBlockEntity.MODES[w.mode()]), w.holdCost());
            colour = 0xFF8FF0A0;
        } else {
            state = Component.translatable("screen.skyblocks.weather_machine.turning", Math.round(w.charge() * 100));
            colour = 0xFFFFC870;
        }
        g.drawString(font, state, 8, 42, colour, false);
        bar(g, 8, 53, imageWidth - 16, w.charge(), 0xFF2A5A9A, 0xFF9FD8FF);
        g.drawString(font, Component.translatable("screen.skyblocks.machine.energy_plain", compact(w.energy.getEnergyStored()),
                compact(w.energy.getMaxEnergyStored())), 8, 62, TEXT, false);
        g.drawString(font, Component.translatable("screen.skyblocks.weather_machine.masts", w.masts(),
                Component.translatable("screen.skyblocks.machine.size." + SIZES[w.size()]), w.reach()), 8, 74, DIM, false);
        g.drawString(font, Component.translatable("screen.skyblocks.weather_machine.caught", compactLong(w.harvested())), 8, 86, 0xFFD8B8FF, false);
        g.drawString(font, Component.translatable("screen.skyblocks.weather_machine.hint"), 8, 98, DIM, false);
    }

    private void forge(GuiGraphics g, StarForgeBlockEntity f) {
        g.drawString(font, Component.translatable("screen.skyblocks.star_forge.fuel"), 6, 16, DIM, false);
        g.drawString(font, Component.translatable("screen.skyblocks.star_forge.in"), 38, 16, DIM, false);
        g.drawString(font, Component.translatable("screen.skyblocks.star_forge.out"), 114, 16, DIM, false);
        Component colour = Component.translatable("screen.skyblocks.star_forge.colour." + StarForgeBlockEntity.CLASSES[f.colour()]);
        Component star = f.powered() ? Component.translatable("screen.skyblocks.star_forge.star_powered", colour, String.format("%,d", f.powerCost()))
                : f.lit() ? Component.translatable("screen.skyblocks.star_forge.star", colour, String.format("%,d", f.makes()))
                : Component.translatable("screen.skyblocks.star_forge.out_of_fuel", String.format("%,d", f.powerCost()));
        g.drawString(font, star, 8, 84, f.lit() ? accent() : 0xFFFF8A7A, false);
        Component work = f.progress() > 0 ? Component.translatable("screen.skyblocks.star_forge.working", Math.round(f.progress() * 100))
                : Component.translatable(f.mode() == 0 ? "screen.skyblocks.star_forge.idle_smelt" : "screen.skyblocks.star_forge.idle_forge");
        g.drawString(font, work, 8, 96, TEXT, false);
        g.drawString(font, Component.translatable(f.mode() == 0 ? "screen.skyblocks.star_forge.hint_smelt" : "screen.skyblocks.star_forge.hint_forge"), 8, 108, DIM, false);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, float f, int from, int to) {
        g.fill(x - 1, y - 1, x + w + 1, y + 6, 0xFF2A3344);
        g.fill(x, y, x + w, y + 5, 0xFF121722);
        int end = x + Math.round(w * Math.max(0.0F, Math.min(1.0F, f)));
        for (int i = x; i < end; i++) {
            float k = (i - x) / (float) Math.max(1, w);
            int r = (int) (((from >> 16) & 255) * (1 - k) + ((to >> 16) & 255) * k);
            int gg = (int) (((from >> 8) & 255) * (1 - k) + ((to >> 8) & 255) * k);
            int b = (int) ((from & 255) * (1 - k) + (to & 255) * k);
            g.fill(i, y, i + 1, y + 5, 0xFF000000 | r << 16 | gg << 8 | b);
        }
    }

    private static String compact(int fe) {
        return compactLong(fe);
    }

    private static String compactLong(long fe) {
        if (fe >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", fe / 1e9);
        }
        if (fe >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", fe / 1e6);
        }
        if (fe >= 10_000) {
            return String.format(Locale.ROOT, "%.0fk", fe / 1e3);
        }
        return String.format("%,d", fe);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
