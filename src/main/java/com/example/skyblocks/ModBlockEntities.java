package com.example.skyblocks;

import com.example.skyblocks.island.IslandScannerBlockEntity;
import com.example.skyblocks.island.SkyIslandBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SkyBlocksMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<OiiaCatBlockEntity>> OIIA_CAT = TYPES.register("oiia_cat",
            () -> BlockEntityType.Builder.of(OiiaCatBlockEntity::new, ModBlocks.OIIA_CAT.get()).build(null));

    public static final RegistryObject<BlockEntityType<BigOiiaCatBlockEntity>> BIG_OIIA_CAT = TYPES.register("big_oiia_cat",
            () -> BlockEntityType.Builder.of(BigOiiaCatBlockEntity::new, ModBlocks.BIG_OIIA_CAT.get()).build(null));

    public static final RegistryObject<BlockEntityType<IslandScannerBlockEntity>> ISLAND_SCANNER = TYPES.register("island_scanner",
            () -> BlockEntityType.Builder.of(IslandScannerBlockEntity::new, ModBlocks.ISLAND_SCANNER.get()).build(null));

    public static final RegistryObject<BlockEntityType<SkyIslandBlockEntity>> SKY_ISLAND = TYPES.register("sky_island",
            () -> BlockEntityType.Builder.of(SkyIslandBlockEntity::new, ModBlocks.SKY_ISLAND.get(),
                    ModBlocks.ISLAND_PAINTING.get()).build(null));

    private ModBlockEntities() {}
}
