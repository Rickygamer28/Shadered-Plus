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
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The whirlpool's screen: how much it holds, each fluid with how much of it (click one: void it - what comes in of it
 * is gone from then on), drain mode, and the void filter (fluids and fluid tags; click one to take it out).
 */
public class WhirlpoolScreen extends Screen {
    private static final int W = 262, H = 238, ROW = 20, ROWS = 5, ACCENT = 0xFF3FA0F0;
    private final BlockPos pos;
    private int left, top, scroll;
    private EditBox tagBox;
    private Button drainButton, viewButton, skinButton;
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
        PacketDistributor.sendToServer(new WhirlpoolPayload(pos, action, text));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int bw = (W - 16 - 2 * 4) / 3;
        drainButton = addRenderableWidget(MachineUi.button(left + 8, top + H - 20, bw, 14, Component.empty(), b -> send(WhirlpoolPayload.DRAIN, ""), () -> ACCENT));
        viewButton = addRenderableWidget(MachineUi.button(left + 8 + bw + 4, top + H - 20, bw, 14, Component.empty(), b -> send(WhirlpoolPayload.STILL, ""),
                () -> ACCENT));
        skinButton = addRenderableWidget(MachineUi.button(left + 8 + 2 * (bw + 4), top + H - 20, bw, 14, Component.empty(), b -> send(WhirlpoolPayload.SKIN, ""),
                () -> ACCENT));
        tagBox = addRenderableWidget(new EditBox(font, left + 12, top + 172, W - 82, 10, Component.translatable("screen.skyblocks.whirlpool.tag")));
        tagBox.setBordered(false);
        tagBox.setMaxLength(120);
        tagBox.setTextColor(MachineUi.TEXT);
        tagBox.setHint(Component.translatable("screen.skyblocks.whirlpool.tag_hint").withStyle(st -> st.withColor(MachineUi.FAINT & 0xFFFFFF)));
        addRenderableWidget(MachineUi.button(left + W - 62, top + 168, 54, 14, Component.translatable("screen.skyblocks.whirlpool.add"), b -> {
            if (!tagBox.getValue().isBlank()) {
                send(WhirlpoolPayload.VOID_TAG, tagBox.getValue());
                tagBox.setValue("");
            }
        }, () -> 0xFFFF6A7A));
    }

    @Override
    public void tick() {
        WhirlpoolBlockEntity be = be();
        if (be == null || minecraft == null || minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 15.0 * 15.0) {
            onClose();
            return;
        }
        ((MachineUi.Flat) drainButton).lit = be.drain();
        drainButton.setMessage(Component.translatable(be.drain() ? "screen.skyblocks.whirlpool.drain_on" : "screen.skyblocks.whirlpool.drain_off"));
        viewButton.setMessage(Component.translatable(be.still() ? "screen.skyblocks.whirlpool.view_tank" : "screen.skyblocks.whirlpool.view_whirlpool"));
        skinButton.setMessage(Component.translatable("screen.skyblocks.whirlpool.skin",
                Component.translatable("screen.skyblocks.whirlpool.skin." + WhirlpoolBlockEntity.SKINS[Math.floorMod(be.skin(), WhirlpoolBlockEntity.SKINS.length)])));
        skinButton.active = !be.still();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        WhirlpoolBlockEntity be = be();
        if (be == null) {
            return;
        }
        MachineUi.header(g, font, title, be.tank() > 0 ? Component.translatable("screen.skyblocks.whirlpool.tank_status", be.tank()) : null, left, top, W, ACCENT);
        // how full: kinds held out of the most it can hold
        g.drawString(font, Component.translatable("screen.skyblocks.whirlpool.each", WhirlpoolBlock.buckets(be.capacity())), left + 8, top + 25, MachineUi.TEXT, false);
        Component kinds = Component.translatable("screen.skyblocks.whirlpool.kinds", be.entries().size() + " / " + be.maxKinds());
        g.drawString(font, kinds, left + W - 8 - font.width(kinds), top + 25, MachineUi.DIM, false);
        MachineUi.bar(g, left + 8, top + 37, W - 16, 3, Math.min(1.0F, (float) be.entries().size() / be.maxKinds()), ACCENT);
        // the fluids
        List<WhirlpoolBlockEntity.Entry> list = be.entries();
        scroll = Math.max(0, Math.min(scroll, list.size() - ROWS));
        int y0 = top + 46;
        if (list.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.skyblocks.whirlpool.empty"), left + W / 2, y0 + 42, MachineUi.DIM);
        }
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            WhirlpoolBlockEntity.Entry e = list.get(scroll + i);
            int y = y0 + i * ROW;
            boolean hover = mouseX >= left + 8 && mouseX < left + W - 8 && mouseY >= y && mouseY < y + ROW - 2;
            g.fill(left + 8, y, left + W - 8, y + ROW - 2, hover ? 0xFF222C3E : 0xFF182030);
            // (how full this fluid is, of its own limit)
            g.fill(left + 8, y + ROW - 3, left + 8 + (int) ((W - 16) * Math.min(1.0, (double) e.amount / be.capacity())), y + ROW - 2, ACCENT);
            drawFluid(g, e.fluid, left + 10, y + 1);
            boolean voided = be.voids(e.fluid);
            String amount = WhirlpoolBlock.buckets(e.amount) + " / " + WhirlpoolBlock.buckets(be.capacity());
            int room = W - 52 - font.width(amount) - (voided ? 40 : 0);
            g.drawString(font, MachineUi.fit(font, e.fluid.getHoverName().getString(), room), left + 31, y + 5, voided ? MachineUi.BAD : MachineUi.TEXT, false);
            g.drawString(font, amount, left + W - 12 - font.width(amount), y + 5, MachineUi.DIM, false);
            if (voided) {
                Component v = Component.translatable("screen.skyblocks.whirlpool.void");
                g.drawString(font, v, left + W - 18 - font.width(amount) - font.width(v), y + 5, 0xFFFF6A6A, false);
            }
        }
        if (list.size() > ROWS) {
            String page = (scroll + 1) + "-" + Math.min(list.size(), scroll + ROWS) + " / " + list.size();
            g.drawString(font, page, left + W - 8 - font.width(page), y0 + ROWS * ROW + 1, MachineUi.FAINT, false);
        }
        g.drawString(font, MachineUi.fit(font, Component.translatable("screen.skyblocks.whirlpool.hint").getString(), list.size() > ROWS ? W - 70 : W - 16), left + 8,
                y0 + ROWS * ROW + 1,
                MachineUi.FAINT, false);
        // the void filter: fluids and tags, click to take out
        chipBoxes.clear();
        chipWhat.clear();
        int x = left + 8, y = top + 186;
        for (Fluid f : be.voided()) {
            String id = BuiltInRegistries.FLUID.getKey(f).toString();
            int[] at = chip(g, new FluidStack(f, 1).getHoverName().getString(), x, y, mouseX, mouseY);
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
        g.fill(x, y, x + w, y + 12, hover ? 0xFF552433 : 0xFF3A1A24);
        g.fill(x, y + 11, x + w, y + 12, 0xFFFF6A7A);
        g.drawString(font, text, x + 5, y + 2, 0xFFFFC0CC, false);
        return new int[] {x, y, w};
    }

    /** A fluid's picture, as it looks in the world (its still texture, tinted). */
    static void drawFluid(GuiGraphics g, FluidStack fluid, int x, int y) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
        int tint = ext.getTintColor(fluid);
        g.blit(x, y, 0, 16, 16, sprite, ((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F, 1.0F);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        MachineUi.panel(g, left, top, W, H, ACCENT);
        MachineUi.divider(g, left + 8, top + 160, W - 16);
        // (the tag field's bed)
        g.fill(left + 8, top + 168, left + W - 66, top + 182, MachineUi.SLOT_EDGE);
        g.fill(left + 9, top + 169, left + W - 67, top + 181, MachineUi.TRACK);
        MachineUi.divider(g, left + 8, top + H - 25, W - 16);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        WhirlpoolBlockEntity be = be();
        if (be != null && button == 0) {
            int y0 = top + 46;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
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
