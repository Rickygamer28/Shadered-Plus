package com.example.skyblocks;

import com.example.skyblocks.island.IslandScannerBlockEntity;
import com.example.skyblocks.island.SkyIslandBlockEntity;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SkyBlocksMod.MOD_ID);

    public static final Supplier<BlockEntityType<OiiaCatBlockEntity>> OIIA_CAT = TYPES.register("oiia_cat",
            () -> BlockEntityType.Builder.of(OiiaCatBlockEntity::new, ModBlocks.OIIA_CAT.get()).build(null));

    public static final Supplier<BlockEntityType<BigOiiaCatBlockEntity>> BIG_OIIA_CAT = TYPES.register("big_oiia_cat",
            () -> BlockEntityType.Builder.of(BigOiiaCatBlockEntity::new, ModBlocks.BIG_OIIA_CAT.get()).build(null));

    public static final Supplier<BlockEntityType<IslandScannerBlockEntity>> ISLAND_SCANNER = TYPES.register("island_scanner",
            () -> BlockEntityType.Builder.of(IslandScannerBlockEntity::new, ModBlocks.ISLAND_SCANNER.get()).build(null));

    public static final Supplier<BlockEntityType<SkyIslandBlockEntity>> SKY_ISLAND = TYPES.register("sky_island",
            () -> BlockEntityType.Builder.of(SkyIslandBlockEntity::new, ModBlocks.SKY_ISLAND.get()).build(null));

    private ModBlockEntities() {}
}
