package com.example.skyblocks;

import com.example.skyblocks.island.IslandScannerBlock;
import com.example.skyblocks.island.SkyIslandBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Model blocks (like Shadered's Maxwell). Skies are not blocks here: they are Shadered sky types (ModItems). */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyBlocksMod.MOD_ID);

    public static final DeferredBlock<OiiaCatBlock> OIIA_CAT = BLOCKS.registerBlock("oiia_cat", OiiaCatBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).sound(SoundType.WOOL).noOcclusion());

    /** What a 3x3x3 cube of OIIA cats turns into (no item: it is only made by building the cube). */
    public static final DeferredBlock<BigOiiaCatBlock> BIG_OIIA_CAT = BLOCKS.registerBlock("big_oiia_cat", BigOiiaCatBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).sound(SoundType.WOOL).noOcclusion()
                    .pushReaction(PushReaction.BLOCK));

    /** Scans the area around it into a Sky Island item (island/IslandScannerBlock). */
    public static final DeferredBlock<IslandScannerBlock> ISLAND_SCANNER = BLOCKS.registerBlock("island_scanner",
            IslandScannerBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.LODESTONE).noOcclusion().lightLevel(state -> 7));

    /** A placed Sky Island: a pedestal with the scanned island floating above it (island/SkyIslandBlock). */
    public static final DeferredBlock<SkyIslandBlock> SKY_ISLAND = BLOCKS.registerBlock("sky_island", SkyIslandBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.AMETHYST).noOcclusion());

    private ModBlocks() {}
}
