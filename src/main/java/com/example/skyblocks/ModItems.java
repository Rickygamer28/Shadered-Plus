package com.example.skyblocks;

import com.example.skyblocks.island.SkyIslandItem;

import com.noodlegamer76.shadered.block.InitBlocks;
import com.noodlegamer76.shadered.item.SkyblockItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * One Shadered skyblock item per sky. They are real Shadered SkyblockItems: they place Shadered's own
 * skyblock with our sky type, use Shadered's item renderer, work in the Sky Emitter, with filters, etc.
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SkyBlocksMod.MOD_ID);

    public static final List<RegistryObject<SkyblockItem>> SKY_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < SkyNames.NAMES.length; i++) {
            final int index = i;
            SKY_ITEMS.add(ITEMS.register(SkyNames.itemId(i),
                    () -> new SkyblockItem(InitBlocks.SKYBLOCK.get(), new Item.Properties(), SkyTypes.itemType(index))));
        }
    }

    /** The OIIA cat model block (its renderer is registered in client/ClientSetup). */
    public static final RegistryObject<BlockItem> OIIA_CAT = ITEMS.register("oiia_cat",
            () -> new OiiaCatBlockItem(ModBlocks.OIIA_CAT.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> ISLAND_SCANNER = ITEMS.register("island_scanner",
            () -> new BlockItem(ModBlocks.ISLAND_SCANNER.get(), new Item.Properties()));

    /** Holds a scanned island (only made by the Island Scanner). */
    public static final RegistryObject<SkyIslandItem> SKY_ISLAND = ITEMS.register("sky_island",
            () -> new SkyIslandItem(ModBlocks.SKY_ISLAND.get(), new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockItem> ISLAND_PAINTING = ITEMS.register("island_painting",
            () -> new BlockItem(ModBlocks.ISLAND_PAINTING.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> MIRROR = ITEMS.register("mirror",
            () -> new BlockItem(ModBlocks.MIRROR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> INFINITY_MIRROR = ITEMS.register("infinity_mirror",
            () -> new BlockItem(ModBlocks.INFINITY_MIRROR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> INFINITY_CUBE = ITEMS.register("infinity_cube",
            () -> new BlockItem(ModBlocks.INFINITY_CUBE.get(), new Item.Properties()));

    private ModItems() {}
}
