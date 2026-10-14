package com.example.skyblocks.island;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The Island Painting's frame look. Right-click a painting with the item to change its frame (the item is used up);
 * a stick puts the normal oak frame back.
 */
public enum FrameStyle implements StringRepresentable {
    OAK("oak", Items.STICK),
    GOLD("gold", Items.GOLD_INGOT),
    DARK_OAK("dark_oak", Items.DARK_OAK_PLANKS),
    PRISMARINE("prismarine", Items.PRISMARINE_SHARD),
    QUARTZ("quartz", Items.QUARTZ),
    BLACKSTONE("blackstone", Items.BLACKSTONE);

    private final String name;
    private final Item item;

    FrameStyle(String name, Item item) {
        this.name = name;
        this.item = item;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The frame an item gives, or null if it isn't one of them. */
    @Nullable
    public static FrameStyle forItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (FrameStyle style : values()) {
            if (stack.is(style.item)) {
                return style;
            }
        }
        return null;
    }
}
