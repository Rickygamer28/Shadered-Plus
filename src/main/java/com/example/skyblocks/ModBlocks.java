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

    /** The deep sea aquarium: always dark, its creatures glowing (anglerfish, lanternfish, jellyfish, glow squid). */
    public static final DeferredBlock<AquariumBlock> DEEP_SEA_AQUARIUM = BLOCKS.registerBlock("deep_sea_aquarium",
            properties -> new AquariumBlock(properties, false, true),
            BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 2));

    /** An aquarium stand: a dark wood cabinet for a tank to sit on. */
    public static final DeferredBlock<AquariumStandBlock> AQUARIUM_STAND = BLOCKS.registerBlock("aquarium_stand", AquariumStandBlock::new,
            BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS));
    /** An aquarium lid: a hood with a lamp over a tank; click it to change the tank's light. */
    public static final DeferredBlock<AquariumLidBlock> AQUARIUM_LID = BLOCKS.registerBlock("aquarium_lid", AquariumLidBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 10)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));

    /** The aquariums in plain Minecraft style (VanillaAquariumBlock, client/VanillaAquariumRenderer). */
    public static final DeferredBlock<VanillaAquariumBlock> VANILLA_AQUARIUM = BLOCKS.registerBlock("vanilla_aquarium",
            properties -> new VanillaAquariumBlock(properties, false),
            BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 6));
    public static final DeferredBlock<VanillaAquariumBlock> VANILLA_OCEAN_AQUARIUM = BLOCKS.registerBlock("vanilla_ocean_aquarium",
            properties -> new VanillaAquariumBlock(properties, true),
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
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE));

    /** A lava lamp: glowing wax rising and sinking in a glass (LavaLampBlock, client/LavaLampRenderer). */
    public static final DeferredBlock<LavaLampBlock> LAVA_LAMP = BLOCKS.registerBlock("lava_lamp", LavaLampBlock::new,
            BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(LavaLampBlock.LIT) ? 12 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE));

    /** The big lava lamp: two blocks tall (BigLavaLampBlock). */
    public static final DeferredBlock<BigLavaLampBlock> BIG_LAVA_LAMP = BLOCKS.registerBlock("big_lava_lamp", BigLavaLampBlock::new,
            BlockBehaviour.Properties.of().strength(0.8F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(LavaLampBlock.LIT) ? 14 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE));

    /** The fire tornado: a whirl of flame in a tall glass case (FireTornadoBlock, client/WallFireRenderer). */
    public static final DeferredBlock<FireTornadoBlock> FIRE_TORNADO = BLOCKS.registerBlock("fire_tornado", FireTornadoBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(LavaLampBlock.LIT) ? 15 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));

    /** A music visualizer screen (VisualizerBlock, client/VisualizerRenderer). */
    public static final DeferredBlock<VisualizerBlock> MUSIC_VISUALIZER = BLOCKS.registerBlock("music_visualizer", VisualizerBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 7)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));

    /** A hologram projector (HologramProjectorBlock, client/HologramRenderer). */
    public static final DeferredBlock<HologramProjectorBlock> HOLOGRAM_PROJECTOR = BLOCKS.registerBlock("hologram_projector", HologramProjectorBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 6)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GRAY));

    /** A jellyfish tank, 2 tall (JellyfishTankBlock, client/JellyTankRenderer). */
    public static final DeferredBlock<JellyfishTankBlock> JELLYFISH_TANK = BLOCKS.registerBlock("jellyfish_tank", JellyfishTankBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(LavaLampBlock.LIT) ? 11 : 3)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE));

    /** A koi pond (KoiPondBlock, client/KoiPondRenderer). */
    public static final DeferredBlock<KoiPondBlock> KOI_POND = BLOCKS.registerBlock("koi_pond", KoiPondBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.STONE).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.WATER));
    /** The koi pond in plain Minecraft style: the game's own water, lily pads, frogs, tropical fish, a turtle. */
    public static final DeferredBlock<KoiPondBlock> VANILLA_KOI_POND = BLOCKS.registerBlock("vanilla_koi_pond", KoiPondBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.STONE).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.WATER));

    /** A bamboo water spout (a shishi-odoshi) for beside a pond: it fills, tips, pours and clacks (BambooSpoutBlock). */
    public static final DeferredBlock<BambooSpoutBlock> BAMBOO_SPOUT = BLOCKS.registerBlock("bamboo_spout", BambooSpoutBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F, 3.0F).sound(SoundType.BAMBOO_WOOD).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.PLANT));
    /** A music box: its lid opens, a dancer turns, it plays a tune; fish and fireflies near it move in time (MusicBoxBlock). */
    public static final DeferredBlock<MusicBoxBlock> MUSIC_BOX = BLOCKS.registerBlock("music_box", MusicBoxBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F, 3.0F).sound(SoundType.CHERRY_WOOD).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PINK));
    /** A flat stepping stone, for across a koi pond (frogs sit on it). */
    public static final DeferredBlock<SteppingStoneBlock> POND_STEPPING_STONE = BLOCKS.registerBlock("pond_stepping_stone", SteppingStoneBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F, 6.0F).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops()
                    .mapColor(net.minecraft.world.level.material.MapColor.STONE));
    /** A little arched wooden bridge, for over a koi pond. */
    public static final DeferredBlock<PondBridgeBlock> POND_BRIDGE = BLOCKS.registerBlock("pond_bridge", PondBridgeBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F, 3.0F).sound(SoundType.WOOD).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED));

    /** A classic brick hearth, 2 wide and 2 tall (BrickHearthBlock, client/WallFireRenderer). */
    public static final DeferredBlock<BrickHearthBlock> BRICK_HEARTH = BLOCKS.registerBlock("brick_hearth", BrickHearthBlock::new,
            BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BrickHearthBlock.LIT) ? 15 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN));

    /** A neon sign: your own glowing text on a clear panel (NeonSignBlock, client/NeonSignRenderer). */
    public static final DeferredBlock<NeonSignBlock> NEON_SIGN = BLOCKS.registerBlock("neon_sign", NeonSignBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion().noCollission().lightLevel(state -> 8)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PINK));

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
