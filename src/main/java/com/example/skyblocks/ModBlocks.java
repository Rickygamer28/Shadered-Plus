package com.example.skyblocks;

import com.example.skyblocks.island.IslandPaintingBlock;
import com.example.skyblocks.island.IslandScannerBlock;
import com.example.skyblocks.island.SkyIslandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Model blocks (like Shadered's Maxwell). Skies are not blocks here: they are Shadered sky types (ModItems). */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SkyBlocksMod.MOD_ID);

    public static final RegistryObject<OiiaCatBlock> OIIA_CAT = BLOCKS.register("oiia_cat", () -> new OiiaCatBlock(
            BlockBehaviour.Properties.copy(Blocks.STONE).sound(SoundType.WOOL).noOcclusion()));

    /** What a cube of OIIA cats turns into (no item: it is only made by building the cube). */
    public static final RegistryObject<BigOiiaCatBlock> BIG_OIIA_CAT = BLOCKS.register("big_oiia_cat", () -> new BigOiiaCatBlock(
            BlockBehaviour.Properties.copy(Blocks.STONE).sound(SoundType.WOOL).noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    /** Scans the area around it into a Sky Island item (island/IslandScannerBlock). */
    public static final RegistryObject<IslandScannerBlock> ISLAND_SCANNER = BLOCKS.register("island_scanner",
            () -> new IslandScannerBlock(BlockBehaviour.Properties.copy(Blocks.LODESTONE).noOcclusion().lightLevel(state -> 7)));

    /** A placed Sky Island: a pedestal with the scanned island floating above it (island/SkyIslandBlock). */
    public static final RegistryObject<SkyIslandBlock> SKY_ISLAND = BLOCKS.register("sky_island", () -> new SkyIslandBlock(
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.AMETHYST).noOcclusion()));

    /** A framed shadow box for the wall that shows a Sky Island inside (island/IslandPaintingBlock). */
    public static final RegistryObject<IslandPaintingBlock> ISLAND_PAINTING = BLOCKS.register("island_painting",
            () -> new IslandPaintingBlock(BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    private ModBlocks() {}
}
