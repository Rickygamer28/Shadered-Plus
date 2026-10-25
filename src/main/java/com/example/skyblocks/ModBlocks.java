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

    /** A fog machine: low fog drifting along the floor (FogMachineBlock, client/FogParticle). */
    public static final DeferredBlock<FogMachineBlock> FOG_MACHINE = BLOCKS.registerBlock("fog_machine", FogMachineBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().mapColor(net.minecraft.world.level.material.MapColor.METAL));

    /** A speaker: its cone thumps with the bass, and it carries the music's reaction further (SpeakerBlock). */
    public static final DeferredBlock<SpeakerBlock> SPEAKER = BLOCKS.registerBlock("speaker", SpeakerBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.WOOD)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));

    /** An aquarium: touching blocks make one tank with fish in it (AquariumBlock, client/AquariumRenderer). */
    public static final DeferredBlock<AquariumBlock> AQUARIUM = BLOCKS.registerBlock("aquarium", AquariumBlock::new,
            BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 6));

    /** The ocean aquarium: the same, with sharks, rays, turtles... for giant tanks. */
    public static final DeferredBlock<AquariumBlock> OCEAN_AQUARIUM = BLOCKS.registerBlock("ocean_aquarium",
            properties -> new AquariumBlock(properties, true),
            BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 6));

    /** A disco ball throwing real light spots around (DiscoBallBlock, client/DiscoBallRenderer). */
    public static final DeferredBlock<DiscoBallBlock> DISCO_BALL = BLOCKS.registerBlock("disco_ball", DiscoBallBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(DiscoBallBlock.MODE) > 0 ? 12 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL));

    /** A dance floor tile: touching tiles play one animation together (DanceFloorBlock, client/DanceFloorRenderer). */
    public static final DeferredBlock<DanceFloorBlock> DANCE_FLOOR = BLOCKS.registerBlock("dance_floor", DanceFloorBlock::new,
            BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GLASS).lightLevel(state -> 10)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_MAGENTA).isValidSpawn((s, l, p, e) -> false));

    /**
     * Water caustics: the moving light on the bottom of a pool (animated textures, tools/make_water_caustics.py); right-click
     * it with a full block to show that block's texture under the light (WaterCausticsBlock, client/WaterCausticsModel).
     */
    public static final DeferredBlock<WaterCausticsBlock> WATER_CAUSTICS = BLOCKS.registerBlock("water_caustics", WaterCausticsBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()
                    .mapColor(net.minecraft.world.level.material.MapColor.DIAMOND));

    /** Rain glass: rain running down a window, over any block put behind it (RainGlassBlock). */
    public static final DeferredBlock<RainGlassBlock> RAIN_GLASS = BLOCKS.registerBlock("rain_glass", RainGlassBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE));

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


    private ModBlocks() {}
}
