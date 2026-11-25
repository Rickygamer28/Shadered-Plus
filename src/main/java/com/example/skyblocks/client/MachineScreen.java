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
    private static final String[] SIZES = {"none", "small", "medium", "large"};
    private final Button[] modes = new Button[4];
    private Button forgeMode, forgePower;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        boolean weather = menu.machine() instanceof WeatherMachineBlockEntity;
        imageWidth = weather ? 220 : 176;
        imageHeight = weather ? 116 : MachineMenu.INVENTORY_Y + 82;
        inventoryLabelY = MachineMenu.INVENTORY_Y - 10;
    }

    private int accent() {
        MachineBlockEntity m = menu.machine();
        if (m instanceof StarForgeBlockEntity f) {
            return new int[] {0xFFFF6A3A, 0xFFFFC23A, 0xFF5EA8FF, 0xFFE8ECFF}[f.colour()];
        }
        return m instanceof WeatherMachineBlockEntity ? 0xFF7FB8FF : 0xFF3FE0F0;
    }

    @Override
    protected void init() {
        super.init();
        MachineBlockEntity m = menu.machine();
        if (m instanceof WeatherMachineBlockEntity) {
            int bw = (imageWidth - 16 - 3 * 4) / 4;
            for (int i = 0; i < 4; i++) {
                final int id = i;
                modes[i] = addRenderableWidget(MachineUi.button(leftPos + 8 + i * (bw + 4), topPos + 24, bw, 14,
                        Component.translatable("screen.skyblocks.weather_machine.mode." + WeatherMachineBlockEntity.MODES[i]), b -> press(id), this::accent));
            }
        } else if (m instanceof StarForgeBlockEntity) {
            forgePower = addRenderableWidget(MachineUi.button(leftPos + imageWidth - 110, topPos + 2, 48, 14, Component.empty(), b -> press(11), this::accent));
            forgeMode = addRenderableWidget(MachineUi.button(leftPos + imageWidth - 60, topPos + 2, 56, 14, Component.empty(), b -> press(10), this::accent));
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
        MachineUi.panel(g, x, y, imageWidth, imageHeight, accent());
        MachineUi.slots(g, menu, x, y);
        MachineBlockEntity m = menu.machine();
        if (!(m instanceof WeatherMachineBlockEntity)) {
            MachineUi.divider(g, x + 8, y + MachineMenu.INVENTORY_Y - 13, imageWidth - 16);
        }
        if (m instanceof StarForgeBlockEntity f) {
            // its fuel slot's star: how much fuel's left (or power stored), in its colour; the arrow: how far through
            int fx = x + 12, fy = y + 34;
            g.fill(fx - 1, fy - 1, fx + 17, fy + 31, MachineUi.SLOT_EDGE);
            g.fill(fx, fy, fx + 16, fy + 30, MachineUi.TRACK);
            int h = Math.round(30 * f.fuelLeft());
            g.fill(fx, fy + 30 - h, fx + 16, fy + 30, accent());
            int ax = x + 95, ay = y + 58;
            g.fill(ax, ay, ax + 12, ay + 4, MachineUi.SLOT_EDGE);
            g.fill(ax, ay, ax + Math.round(12 * f.progress()), ay + 4, accent());
            for (int k = 0; k < 4; k++) {
                g.fill(ax + 12 + k, ay - 2 + k, ax + 13 + k, ay + 6 - k, f.progress() > 0.99F ? accent() : MachineUi.SLOT_EDGE);
            }
        } else if (m instanceof ParticleAcceleratorBlockEntity) {
            // its item to smash: a ring round it in its colour
            int cx = x + MachineMenu.ACCELERATOR[0][0], cy = y + MachineMenu.ACCELERATOR[0][1];
            g.fill(cx - 4, cy - 4, cx + 20, cy + 20, accent());
            g.fill(cx - 3, cy - 3, cx + 19, cy + 19, MachineUi.BG);
            MachineUi.slot(g, cx, cy);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineBlockEntity m = menu.machine();
        Component status = null;
        int statusColour = accent();
        if (m != null && m.size() == 0) {
            status = Component.translatable("screen.skyblocks.machine.not_built");
            statusColour = MachineUi.BAD;
        } else if (m instanceof ParticleAcceleratorBlockEntity a) {
            int r = ParticleAcceleratorBlockEntity.RADIUS[a.size()] * 2 + 1;
            status = Component.translatable("screen.skyblocks.machine.ring_status", size(a.size()), r, r);
        } else if (m instanceof WeatherMachineBlockEntity w) {
            status = Component.translatable("screen.skyblocks.machine.masts_status", size(w.size()), w.masts());
        }
        MachineUi.header(g, font, title, m instanceof StarForgeBlockEntity ? null : status, 0, 0, imageWidth, statusColour);
        if (!(m instanceof WeatherMachineBlockEntity)) {
            MachineUi.label(g, font, playerInventoryTitle, 8, inventoryLabelY);
        }
        if (m == null) {
            return;
        }
        if (m.size() == 0) {
            int y0 = m instanceof WeatherMachineBlockEntity ? 46 : 92;
            g.drawString(font, Component.translatable("screen.skyblocks.machine.unformed"), 8, y0, MachineUi.BAD, false);
            g.drawString(font, MachineUi.fit(font, Component.translatable("screen.skyblocks.machine.unformed." + kind(m)).getString(), imageWidth - 16), 8, y0 + 11,
                    MachineUi.DIM, false);
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

    private static Component size(int size) {
        Component c = Component.translatable("screen.skyblocks.machine.size." + SIZES[Math.max(0, Math.min(3, size))]);
        String s = c.getString();
        return Component.literal(s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1));
    }

    private static String kind(MachineBlockEntity m) {
        return m instanceof ParticleAcceleratorBlockEntity ? "accelerator" : m instanceof WeatherMachineBlockEntity ? "weather" : "forge";
    }

    private void accelerator(GuiGraphics g, ParticleAcceleratorBlockEntity a) {
        int sx = MachineMenu.ACCELERATOR[0][0], gx = MachineMenu.ACCELERATOR[1][0];
        Component smash = Component.translatable("screen.skyblocks.particle_accelerator.smash");
        MachineUi.label(g, font, smash, sx + 8 - font.width(smash) / 2, 24);
        MachineUi.label(g, font, Component.translatable("screen.skyblocks.particle_accelerator.made"), gx, 24);
        // what's in it makes for sure (or, else, what it made last)
        var recipe = ParticleAcceleratorBlockEntity.smash(menu.slots.get(0).getItem(), a.size());
        ItemStack shown = recipe != null ? new ItemStack(recipe.out(), recipe.count()) : a.last();
        if (!shown.isEmpty()) {
            MachineUi.label(g, font, Component.translatable(recipe != null ? "screen.skyblocks.particle_accelerator.makes" : "screen.skyblocks.particle_accelerator.last"),
                    8, 72);
            g.renderItem(shown, 52, 68);
            g.renderItemDecorations(font, shown, 52, 68);
        }
        boolean empty = menu.slots.get(0).getItem().isEmpty();
        Component state = empty ? Component.translatable("screen.skyblocks.particle_accelerator.waiting")
                : a.speed() > 0.01F ? Component.translatable("screen.skyblocks.particle_accelerator.accelerating", Math.round(a.speed() * 100))
                : Component.translatable("screen.skyblocks.particle_accelerator.no_power");
        String hits = Component.translatable("screen.skyblocks.machine.hits", a.collisions()).getString();
        g.drawString(font, hits, imageWidth - 8 - font.width(hits), 92, MachineUi.DIM, false);
        g.drawString(font, MachineUi.fit(font, state.getString(), imageWidth - 22 - font.width(hits)), 8, 92, empty ? MachineUi.WARN : accent(), false);
        MachineUi.bar(g, 8, 103, imageWidth - 16, 3, a.speed(), accent());
        g.drawString(font, MachineUi.fit(font, Component.translatable("screen.skyblocks.machine.energy", compact(a.energy.getEnergyStored()),
                compact(a.energy.getMaxEnergyStored()), String.format("%,d", a.drive())).getString(), imageWidth - 16), 8, 110, MachineUi.TEXT, false);
    }

    private void weather(GuiGraphics g, WeatherMachineBlockEntity w) {
        Component state;
        int colour;
        if (w.mode() == 0) {
            state = Component.translatable("screen.skyblocks.weather_machine.off");
            colour = MachineUi.DIM;
        } else if (w.holding()) {
            state = Component.translatable("screen.skyblocks.weather_machine.holding",
                    Component.translatable("screen.skyblocks.weather_machine.mode." + WeatherMachineBlockEntity.MODES[w.mode()]), w.holdCost());
            colour = MachineUi.GOOD;
        } else {
            state = Component.translatable("screen.skyblocks.weather_machine.turning", Math.round(w.charge() * 100));
            colour = MachineUi.WARN;
        }
        g.drawString(font, state, 8, 46, colour, false);
        MachineUi.bar(g, 8, 57, imageWidth - 16, 3, w.charge(), accent());
        g.drawString(font, Component.translatable("screen.skyblocks.machine.energy_plain", compact(w.energy.getEnergyStored()),
                compact(w.energy.getMaxEnergyStored())), 8, 66, MachineUi.TEXT, false);
        g.drawString(font, Component.translatable("screen.skyblocks.weather_machine.caught", compactLong(w.harvested())), 8, 77, 0xFFD8B8FF, false);
        MachineUi.divider(g, 8, 89, imageWidth - 16);
        g.drawString(font, Component.translatable("screen.skyblocks.weather_machine.reach", w.reach()), 8, 94, MachineUi.DIM, false);
        g.drawString(font, MachineUi.fit(font, Component.translatable("screen.skyblocks.weather_machine.hint").getString(), imageWidth - 16), 8, 104,
                MachineUi.FAINT, false);
    }

    private void forge(GuiGraphics g, StarForgeBlockEntity f) {
        MachineUi.label(g, font, Component.translatable("screen.skyblocks.star_forge.fuel"), 8, 24);
        MachineUi.label(g, font, Component.translatable("screen.skyblocks.star_forge.in"), 38, 24);
        MachineUi.label(g, font, Component.translatable("screen.skyblocks.star_forge.out"), 114, 24);
        Component colour = Component.translatable("screen.skyblocks.star_forge.colour." + StarForgeBlockEntity.CLASSES[f.colour()]);
        Component star = f.powered() ? Component.translatable("screen.skyblocks.star_forge.star_powered", colour, String.format("%,d", f.powerCost()))
                : f.lit() ? Component.translatable("screen.skyblocks.star_forge.star", colour, String.format("%,d", f.makes()))
                : Component.translatable("screen.skyblocks.star_forge.out_of_fuel", String.format("%,d", f.powerCost()));
        g.drawString(font, MachineUi.fit(font, star.getString(), imageWidth - 16), 8, 96, f.lit() ? accent() : MachineUi.BAD, false);
        Component work = f.progress() > 0 ? Component.translatable("screen.skyblocks.star_forge.working", Math.round(f.progress() * 100))
                : Component.translatable(f.mode() == 0 ? "screen.skyblocks.star_forge.idle_smelt" : "screen.skyblocks.star_forge.idle_forge");
        g.drawString(font, MachineUi.fit(font, work.getString(), imageWidth - 16), 8, 107, f.progress() > 0 ? MachineUi.TEXT : MachineUi.DIM, false);
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
