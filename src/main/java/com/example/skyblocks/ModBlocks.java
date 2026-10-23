package com.example.skyblocks;

import com.example.skyblocks.island.IslandPaintingBlock;
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

    /** A framed shadow box for the wall that shows a Sky Island inside (island/IslandPaintingBlock). */
    public static final DeferredBlock<IslandPaintingBlock> ISLAND_PAINTING = BLOCKS.registerBlock("island_painting",
            IslandPaintingBlock::new, BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion()
                    .pushReaction(PushReaction.DESTROY));

    /** A mirror: its faces show a live reflection of the world (client/MirrorRenderer); a plain shiny block otherwise. */
    public static final DeferredBlock<net.minecraft.world.level.block.Block> MIRROR = BLOCKS.registerSimpleBlock("mirror",
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS)
                    .mapColor(net.minecraft.world.level.material.MapColor.ICE).isValidSpawn((s, l, p, e) -> false));

    /** An infinity mirror: a tunnel of LED rings going on forever (InfinityMirrorBlock, client/InfinityMirrorRenderer). */
    public static final DeferredBlock<InfinityMirrorBlock> INFINITY_MIRROR = BLOCKS.registerBlock("infinity_mirror",
            InfinityMirrorBlock::new, BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE).lightLevel(state -> 11)
                    .isValidSpawn((s, l, p, e) -> false));

    /** An infinity cube: a glowing grid of LED edges going on forever in every direction (client/InfinityCubeRenderer). */
    public static final DeferredBlock<InfinityCubeBlock> INFINITY_CUBE = BLOCKS.registerBlock("infinity_cube",
            InfinityCubeBlock::new, BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE).lightLevel(state -> 12)
                    .isValidSpawn((s, l, p, e) -> false));

    /** The chaos block: Draconic Evolution's chaos shader on its faces (adapted with credit; client/ChaosBlockRenderer). */
    public static final DeferredBlock<ChaosBlock> CHAOS_BLOCK = BLOCKS.registerBlock("chaos_block", ChaosBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.AMETHYST).lightLevel(state -> 10)
                    .isValidSpawn((s, l, q, e) -> false));

    private ModBlocks() {}
}
