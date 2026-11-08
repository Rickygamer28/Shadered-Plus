package com.example.skyblocks.client;

import com.example.skyblocks.WhirlpoolBlock;
import com.example.skyblocks.WhirlpoolBlockEntity;
import com.example.skyblocks.WhirlpoolPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/**
 * The whirlpool's screen: how much it holds, each fluid with how much of it (click one: void it - what comes in of it
 * is gone from then on), drain mode, and the void filter (fluids and fluid tags; click one to take it out).
 */
public class WhirlpoolScreen extends Screen {
    private static final int W = 260, H = 236, ROW = 20, ROWS = 5;
    private final BlockPos pos;
    private int left, top, scroll;
    private EditBox tagBox;
    private Button drainButton, viewButton;
    /** (Where the void filter's chips were drawn, to click: x, y, w, and what each is.) */
    private final List<int[]> chipBoxes = new ArrayList<>();
    private final List<String[]> chipWhat = new ArrayList<>();

    public WhirlpoolScreen(BlockPos pos) {
        super(Component.translatable("block.skyblocks.whirlpool"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new WhirlpoolScreen(pos));
    }

    private WhirlpoolBlockEntity be() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof WhirlpoolBlockEntity w ? w : null;
    }

    private void send(int action, String text) {
        com.example.skyblocks.SkyBlocksMod.NETWORK.sendToServer(new WhirlpoolPayload(pos, action, text));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        drainButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(WhirlpoolPayload.DRAIN, ""))
                .bounds(left + W - 98, top + 6, 90, 18).build());
        viewButton = addRenderableWidget(Button.builder(Component.empty(), b -> send(WhirlpoolPayload.STILL, ""))
                .bounds(left + 8, top + H - 24, 130, 18).build());
        tagBox = addRenderableWidget(new EditBox(font, left + 8, top + 168, W - 70, 16, Component.translatable("screen.skyblocks.whirlpool.tag")));
        tagBox.setMaxLength(120);
        tagBox.setHint(Component.translatable("screen.skyblocks.whirlpool.tag_hint"));
        addRenderableWidget(Button.builder(Component.translatable("screen.skyblocks.whirlpool.add"), b -> {
            if (!tagBox.getValue().isBlank()) {
                send(WhirlpoolPayload.VOID_TAG, tagBox.getValue());
                tagBox.setValue("");
            }
        }).bounds(left + W - 58, top + 167, 50, 18).build());
    }

    @Override
    public void tick() {
        WhirlpoolBlockEntity be = be();
        if (be == null || minecraft == null || minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 15.0 * 15.0) {
            onClose();
            return;
        }
        drainButton.setMessage(Component.translatable(be.drain() ? "screen.skyblocks.whirlpool.drain_on" : "screen.skyblocks.whirlpool.drain_off"));
        viewButton.setMessage(Component.translatable(be.still() ? "screen.skyblocks.whirlpool.view_tank" : "screen.skyblocks.whirlpool.view_whirlpool"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); // (1.20.1: a screen draws its own background first)
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, 0xFF2F8FE0);
        g.fill(left, top, left + W, top + H, 0xF00D1621);
        super.render(g, mouseX, mouseY, partialTick);
        WhirlpoolBlockEntity be = be();
        if (be == null) {
            return;
        }
        g.drawString(font, title, left + 8, top + 8, 0xFFE6F4FF, false);
        if (be.tank() > 0) {
            g.drawString(font, Component.translatable("screen.skyblocks.whirlpool.tank", be.tank()), left + 8, top + 18, 0xFF7FD4FF, false);
        }
        // how full
        g.drawString(font, Component.translatable("screen.skyblocks.whirlpool.each", WhirlpoolBlock.buckets(be.capacity())), left + 8, top + 32, 0xFFCFE0EE, false);
        String kinds = be.entries().size() + " / " + be.maxKinds();
        g.drawString(font, Component.translatable("screen.skyblocks.whirlpool.kinds", kinds), left + W - 8 - font.width(Component.translatable("screen.skyblocks.whirlpool.kinds", kinds)), top + 32, 0xFFCFE0EE, false);
        // (kinds used out of the most it can hold)
        g.fill(left + 8, top + 44, left + W - 8, top + 50, 0xFF1A2A3A);
        g.fill(left + 8, top + 44, left + 8 + (int) ((W - 16) * Math.min(1.0F, (float) be.entries().size() / be.maxKinds())), top + 50, 0xFF2F8FE0);
        // the fluids
        List<WhirlpoolBlockEntity.Entry> list = be.entries();
        scroll = Math.max(0, Math.min(scroll, list.size() - ROWS));
        int y0 = top + 56;
        if (list.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.skyblocks.whirlpool.empty"), left + W / 2, y0 + 40, 0xFF7A8FA3);
        }
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            WhirlpoolBlockEntity.Entry e = list.get(scroll + i);
            int y = y0 + i * ROW;
            boolean hover = mouseX >= left + 6 && mouseX < left + W - 6 && mouseY >= y && mouseY < y + ROW - 2;
            g.fill(left + 6, y, left + W - 6, y + ROW - 2, hover ? 0xFF233B52 : 0xFF16232F);
            // (how full this fluid is, of its own limit)
            g.fill(left + 6, y + ROW - 4, left + 6 + (int) ((W - 12) * Math.min(1.0, (double) e.amount / be.capacity())), y + ROW - 2, 0xFF2F8FE0);
            drawFluid(g, e.fluid, left + 9, y + 1);
            boolean voided = be.voids(e.fluid);
            g.drawString(font, e.fluid.getDisplayName(), left + 30, y + 5, voided ? 0xFFFF8A8A : 0xFFE6F4FF, false);
            String amount = WhirlpoolBlock.buckets(e.amount) + " / " + WhirlpoolBlock.buckets(be.capacity());
            g.drawString(font, amount, left + W - 10 - font.width(amount), y + 5, 0xFFA9BFD2, false);
            if (voided) {
                g.drawString(font, Component.translatable("screen.skyblocks.whirlpool.void"), left + W - 70 - font.width(amount), y + 5, 0xFFFF6A6A, false);
            }
        }
        if (list.size() > ROWS) {
            g.drawString(font, (scroll + 1) + "-" + Math.min(list.size(), scroll + ROWS) + " / " + list.size(), left + 8, y0 + ROWS * ROW, 0xFF7A8FA3, false);
        }
        g.drawString(font, Component.translatable("screen.skyblocks.whirlpool.hint"), left + 8, top + 156, 0xFF7A8FA3, false);
        // the void filter: fluids and tags, click to take out
        chipBoxes.clear();
        chipWhat.clear();
        int x = left + 8, y = top + 190;
        for (Fluid f : be.voided()) {
            String id = BuiltInRegistries.FLUID.getKey(f).toString();
            int[] at = chip(g, new FluidStack(f, 1).getDisplayName().getString(), x, y, mouseX, mouseY);
            chipBoxes.add(at);
            chipWhat.add(new String[] {"fluid", id});
            x = at[0] + at[2] + 4;
            if (x > left + W - 60) {
                x = left + 8;
                y += 14;
            }
        }
        for (TagKey<Fluid> t : be.voidTags()) {
            int[] at = chip(g, "#" + t.location(), x, y, mouseX, mouseY);
            chipBoxes.add(at);
            chipWhat.add(new String[] {"tag", t.location().toString()});
            x = at[0] + at[2] + 4;
            if (x > left + W - 60) {
                x = left + 8;
                y += 14;
            }
        }
    }

    private int[] chip(GuiGraphics g, String text, int x, int y, int mouseX, int mouseY) {
        int w = font.width(text) + 10;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 12;
        g.fill(x, y, x + w, y + 12, hover ? 0xFF5A2030 : 0xFF3A1420);
        g.drawString(font, text, x + 5, y + 2, 0xFFFFC0D0, false);
        return new int[] {x, y, w};
    }

    /** A fluid's picture, as it looks in the world (its still texture, tinted). */
    static void drawFluid(GuiGraphics g, FluidStack fluid, int x, int y) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
        int tint = ext.getTintColor(fluid);
        g.setColor(((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F, 1.0F);
        g.blit(x, y, 0, 16, 16, sprite);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        WhirlpoolBlockEntity be = be();
        if (be != null && button == 0) {
            int y0 = top + 56;
            List<WhirlpoolBlockEntity.Entry> list = be.entries();
            for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
                int y = y0 + i * ROW;
                if (mouseX >= left + 6 && mouseX < left + W - 6 && mouseY >= y && mouseY < y + ROW - 2) {
                    send(WhirlpoolPayload.VOID_FLUID, BuiltInRegistries.FLUID.getKey(list.get(scroll + i).fluid.getFluid()).toString());
                    playClick();
                    return true;
                }
            }
            for (int i = 0; i < chipBoxes.size(); i++) {
                int[] c = chipBoxes.get(i);
                if (mouseX >= c[0] && mouseX < c[0] + c[2] && mouseY >= c[1] && mouseY < c[1] + 12) {
                    String[] what = chipWhat.get(i);
                    send(what[0].equals("fluid") ? WhirlpoolPayload.VOID_FLUID : WhirlpoolPayload.VOID_TAG, what[1]);
                    playClick();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dy) {
        scroll -= (int) Math.signum(dy);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (tagBox.isFocused() && key != 256) {
            return tagBox.keyPressed(key, scan, modifiers) || tagBox.canConsumeInput() || super.keyPressed(key, scan, modifiers);
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
