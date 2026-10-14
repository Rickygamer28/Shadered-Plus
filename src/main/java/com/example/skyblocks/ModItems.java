package com.example.skyblocks;

import com.example.skyblocks.island.SkyIslandItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Our items (this version, for Shadered 1.3.7 - 1.4.2, has no skies of its own). */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SkyBlocksMod.MOD_ID);

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

    private ModItems() {}
}
