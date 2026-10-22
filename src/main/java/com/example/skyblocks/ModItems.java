package com.example.skyblocks;

import com.example.skyblocks.island.SkyIslandItem;

import com.noodlegamer76.shadered.block.InitBlocks;
import com.noodlegamer76.shadered.item.SkyblockItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One Shadered skyblock item per sky. They are real Shadered SkyblockItems: they place Shadered's own
 * skyblock with our sky type, use Shadered's item renderer, work in the Sky Emitter, with filters, etc.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyBlocksMod.MOD_ID);

    public static final List<DeferredItem<SkyblockItem>> SKY_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < SkyNames.NAMES.length; i++) {
            final int index = i;
            SKY_ITEMS.add(ITEMS.register(SkyNames.itemId(i),
                    () -> new SkyblockItem(InitBlocks.SKYBLOCK.get(), new Item.Properties(), SkyTypes.itemType(index))));
        }
    }

    /** The OIIA cat model block (its renderer is registered in client/ClientSetup). */
    public static final DeferredItem<BlockItem> OIIA_CAT = ITEMS.registerSimpleBlockItem("oiia_cat", ModBlocks.OIIA_CAT);

    public static final DeferredItem<BlockItem> ISLAND_SCANNER = ITEMS.registerSimpleBlockItem("island_scanner", ModBlocks.ISLAND_SCANNER);

    /** Holds a scanned island (only made by the Island Scanner). */
    public static final DeferredItem<SkyIslandItem> SKY_ISLAND = ITEMS.register("sky_island",
            () -> new SkyIslandItem(ModBlocks.SKY_ISLAND.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> ISLAND_PAINTING = ITEMS.registerSimpleBlockItem("island_painting", ModBlocks.ISLAND_PAINTING);

    public static final DeferredItem<BlockItem> MIRROR = ITEMS.registerSimpleBlockItem("mirror", ModBlocks.MIRROR);

    public static final DeferredItem<BlockItem> INFINITY_MIRROR = ITEMS.registerSimpleBlockItem("infinity_mirror", ModBlocks.INFINITY_MIRROR);

    private ModItems() {}
}
