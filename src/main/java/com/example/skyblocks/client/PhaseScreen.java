package com.example.skyblocks.client;

import com.example.skyblocks.PhaseBlockEntity;
import com.example.skyblocks.PhasePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A phase wall's look and how it opens: type any block's name (or take the block in your hand; or its own panel), pick
 * one of its five ways of going, Done - set on the whole wall.
 */
public class PhaseScreen extends Screen {
    private final BlockPos pos;
    private EditBox input;
    private String typed;
    private int mode;
    private final Button[] modes = new Button[PhaseBlockEntity.MODES.length];

    private PhaseScreen(BlockPos pos, PhaseBlockEntity be) {
        super(Component.translatable("screen.skyblocks.phase"));
        this.pos = pos;
        this.typed = be.disguise().isAir() ? "" : BuiltInRegistries.BLOCK.getKey(be.disguise().getBlock()).toString();
        this.mode = be.mode();
    }

    public static void open(BlockPos pos, PhaseBlockEntity be) {
        Minecraft.getInstance().setScreen(new PhaseScreen(pos, be));
    }

    private int left() {
        return width / 2 - 120;
    }

    private int top() {
        return height / 2 - 80;
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        input = addRenderableWidget(new EditBox(font, x + 22, y + 30, 218, 18, Component.translatable("screen.skyblocks.phase.block")));
        input.setMaxLength(96);
        input.setValue(typed);
        input.setHint(Component.translatable("screen.skyblocks.phase.hint"));
        input.setResponder(s -> typed = s);
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.phase.hand"), b -> {
            ItemStack held = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
            if (held.getItem() instanceof BlockItem bi) {
                typed = BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString();
                input.setValue(typed);
            }
        }).bounds(x, y + 52, 118, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.phase.own"), b -> {
            typed = "";
            input.setValue("");
        }).bounds(x + 122, y + 52, 118, 18).build());
        int w = (240 - 4 * 4) / 5;
        for (int i = 0; i < modes.length; i++) {
            int k = i;
            modes[i] = addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.phase.mode." + PhaseBlockEntity.MODES[i]), b -> mode = k)
                    .bounds(x + i * (w + 4), y + 96, w, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 50, y + 130, 100, 18).build());
    }

    @Override
    public void tick() {
        for (int i = 0; i < modes.length; i++) {
            modes[i].active = mode != i;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        g.drawCenteredString(font, title, width / 2, y, 0x7FF4FF);
        g.drawString(font, Component.translatable("screen.skyblocks.phase.look"), x, y + 18, 0xC8C8C8);
        g.drawString(font, Component.translatable("screen.skyblocks.phase.how"), x, y + 84, 0xC8C8C8);
        // what it'll look like: the block's item (none: its own panel's); red if there's no such block
        BlockState look = PhasePayload.look(typed);
        boolean bad = !typed.isBlank() && look.isAir();
        ItemStack icon = look.isAir() ? new ItemStack(com.example.skyblocks.ModItems.PHASE_BLOCK.get()) : new ItemStack(look.getBlock().asItem());
        g.renderItem(icon, x + 1, y + 31);
        if (bad) {
            g.drawString(font, Component.translatable("screen.skyblocks.phase.bad"), x + 22, y + 74, 0xFF6A6A);
        }
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new PhasePayload(pos, PhasePayload.look(typed).isAir() ? "" : typed.trim(), mode));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
