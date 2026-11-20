package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * A sonar: right-click sends out a pulse - a glowing ring sweeping out over everything round you - and what it's set to
 * find lights up as it passes, seen through walls: blocks outlined, mobs glowing, each in its colour (client/SonarPulse).
 * What it finds is a list (sneak + right-click: client/SonarScreen): blocks or mobs, each a name ("minecraft:diamond_ore",
 * "zombie"), a tag ("#c:ores", "#minecraft:logs") or, for mobs, a kind ("monster", "creature", "water_creature").
 */
public class SonarItem extends Item {
    public static final int MOST = 16;
    public static final int[] PALETTE = {0x4FE8FF, 0xFF4A4A, 0xFFD34A, 0x6CFF6C, 0xC07CFF, 0xFF8AD8, 0xFFFFFF, 0xFF9A3A};

    /** One thing it finds: a block or a mob's name, tag (#...) or kind, and the colour it lights up in. */
    public record Entry(String id, boolean mob, int colour) {}

    public SonarItem(Properties properties) {
        super(properties);
    }

    public static List<Entry> entries(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        List<Entry> out = new ArrayList<>();
        if (!tag.contains("entries")) {
            out.add(new Entry("#c:ores", false, PALETTE[0]));
            out.add(new Entry("monster", true, PALETTE[1]));
            return out;
        }
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MOST); i++) {
            CompoundTag e = list.getCompound(i);
            String id = e.getString("id").trim();
            if (!id.isEmpty() && id.length() <= 96) {
                out.add(new Entry(id, e.getBoolean("mob"), e.getInt("colour") & 0xFFFFFF));
            }
        }
        return out;
    }

    public static CompoundTag write(List<Entry> entries) {
        ListTag list = new ListTag();
        for (int i = 0; i < Math.min(entries.size(), MOST); i++) {
            Entry e = entries.get(i);
            CompoundTag t = new CompoundTag();
            t.putString("id", e.id().length() > 96 ? e.id().substring(0, 96) : e.id());
            t.putBoolean("mob", e.mob());
            t.putInt("colour", e.colour() & 0xFFFFFF);
            list.add(t);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("entries", list);
        return tag;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                com.example.skyblocks.client.SonarScreen.open(hand, entries(stack));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level.isClientSide) {
            com.example.skyblocks.client.SonarPulse.start(player, entries(stack));
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.9F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.sonar").withStyle(ChatFormatting.GRAY));
        for (Entry e : entries(stack)) {
            tooltip.add(Component.literal(" ■ ").withStyle(s -> s.withColor(TextColor.fromRgb(e.colour())))
                    .append(Component.literal(e.id() + (e.mob() ? "  (mob)" : "  (block)")).withStyle(ChatFormatting.DARK_GRAY)));
        }
    }
}
