package com.example.skyblocks.client;

import com.example.skyblocks.StationBlockEntity;
import com.example.skyblocks.TrainPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** A station: its name, and a train called to it. */
public class StationScreen extends Screen {
    private final BlockPos pos;
    private final String was;
    private EditBox input;

    private StationScreen(BlockPos pos, StationBlockEntity be) {
        super(Component.translatable("screen.skyblocks.station"));
        this.pos = pos;
        this.was = be.name();
    }

    public static void open(BlockPos pos, StationBlockEntity be) {
        Minecraft.getInstance().setScreen(new StationScreen(pos, be));
    }

    @Override
    protected void init() {
        int x = width / 2 - 100, y = height / 2 - 40;
        input = addRenderableWidget(new EditBox(font, x, y + 20, 200, 18, Component.translatable("screen.skyblocks.station.name")));
        input.setMaxLength(24);
        input.setValue(was);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.station.call"), b -> {
            save();
            PacketDistributor.sendToServer(new TrainPayload(-1, TrainPayload.CALL, 0, pos, ""));
            onClose();
        }).bounds(x, y + 46, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 102, y + 46, 98, 20).build());
    }

    private void save() {
        if (!input.getValue().strip().equals(was)) {
            PacketDistributor.sendToServer(new TrainPayload(-1, TrainPayload.RENAME, 0, pos, input.getValue()));
        }
    }

    @Override
    public void onClose() {
        save();
        super.onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 34, 0x8FEFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
