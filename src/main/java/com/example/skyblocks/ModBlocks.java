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

    /** A mirror: its faces show a live reflection of the world (client/MirrorRenderer); a plain shiny block otherwise. */
    public static final RegistryObject<Block> MIRROR = BLOCKS.register("mirror", () -> new Block(
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS)
                    .mapColor(net.minecraft.world.level.material.MapColor.ICE).isValidSpawn((s, l, p, e) -> false)));

    /** The mirror's variants: a round mirror in a gold ring, a cracked mirror, a vanity mirror framed with bulbs. */
    public static final RegistryObject<RoundMirrorBlock> ROUND_MIRROR = BLOCKS.register("round_mirror", () -> new RoundMirrorBlock(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.GOLD).isValidSpawn((s, l, p, e) -> false)));
    public static final RegistryObject<net.minecraft.world.level.block.Block> CRACKED_MIRROR = BLOCKS.register("cracked_mirror", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().strength(0.4F).sound(SoundType.GLASS)
                    .mapColor(net.minecraft.world.level.material.MapColor.ICE).isValidSpawn((s, l, p, e) -> false)));
    public static final RegistryObject<net.minecraft.world.level.block.Block> VANITY_MIRROR = BLOCKS.register("vanity_mirror", () -> new net.minecraft.world.level.block.Block(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).lightLevel(state -> 12)
                    .mapColor(net.minecraft.world.level.material.MapColor.SNOW).isValidSpawn((s, l, p, e) -> false)));

    /** A fog machine: low fog drifting along the floor (FogMachineBlock, client/FogParticle). */
    public static final RegistryObject<FogMachineBlock> FOG_MACHINE = BLOCKS.register("fog_machine", () -> new FogMachineBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().mapColor(net.minecraft.world.level.material.MapColor.METAL)));

    /** A speaker: its cone thumps with the bass, and it carries the music's reaction further (SpeakerBlock). */
    public static final RegistryObject<SpeakerBlock> SPEAKER = BLOCKS.register("speaker", () -> new SpeakerBlock(BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.WOOD)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)));

    /** An aquarium: touching blocks make one tank with fish in it (AquariumBlock, client/AquariumRenderer). */
    public static final RegistryObject<AquariumBlock> AQUARIUM = BLOCKS.register("aquarium", () -> new AquariumBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 6)));

    /** The ocean aquarium: the same, with sharks, rays, turtles... for giant tanks. */
    public static final RegistryObject<AquariumBlock> OCEAN_AQUARIUM = BLOCKS.register("ocean_aquarium", () -> new AquariumBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 6), true));

    /** The deep sea aquarium: always dark, its creatures glowing (anglerfish, lanternfish, jellyfish, glow squid). */
    public static final RegistryObject<AquariumBlock> DEEP_SEA_AQUARIUM = BLOCKS.register("deep_sea_aquarium", () -> new AquariumBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 2), false, true));

    /** The coral reef aquarium: white sand, corals and anemones, reef fish (clownfish, tangs, pufferfish...). */
    public static final RegistryObject<AquariumBlock> REEF_AQUARIUM = BLOCKS.register("reef_aquarium", () -> new AquariumBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 7), false, false, 1));

    /** The jungle river aquarium: tea-coloured water, driftwood and plants, piranhas, tetras, angelfish, an axolotl. */
    public static final RegistryObject<AquariumBlock> JUNGLE_AQUARIUM = BLOCKS.register("jungle_aquarium", () -> new AquariumBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 5), false, false, 2));

    /** An aquarium stand: a dark wood cabinet for a tank to sit on. */
    public static final RegistryObject<AquariumStandBlock> AQUARIUM_STAND = BLOCKS.register("aquarium_stand", () -> new AquariumStandBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS)));
    /** An aquarium lid: a hood with a lamp over a tank; click it to change the tank's light. */
    public static final RegistryObject<AquariumLidBlock> AQUARIUM_LID = BLOCKS.register("aquarium_lid", () -> new AquariumLidBlock(BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 10)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)));

    /** The aquariums in plain Minecraft style (VanillaAquariumBlock, client/VanillaAquariumRenderer). */
    public static final RegistryObject<VanillaAquariumBlock> VANILLA_AQUARIUM = BLOCKS.register("vanilla_aquarium", () -> new VanillaAquariumBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 6), false));
    public static final RegistryObject<VanillaAquariumBlock> VANILLA_OCEAN_AQUARIUM = BLOCKS.register("vanilla_ocean_aquarium", () -> new VanillaAquariumBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 6), true));

    /** A disco ball throwing real light spots around (DiscoBallBlock, client/DiscoBallRenderer). */
    public static final RegistryObject<DiscoBallBlock> DISCO_BALL = BLOCKS.register("disco_ball", () -> new DiscoBallBlock(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(DiscoBallBlock.MODE) > 0 ? 12 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL)));

    /** A dance floor tile: touching tiles play one animation together (DanceFloorBlock, client/DanceFloorRenderer). */
    public static final RegistryObject<DanceFloorBlock> DANCE_FLOOR = BLOCKS.register("dance_floor", () -> new DanceFloorBlock(BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GLASS).lightLevel(state -> 10)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_MAGENTA).isValidSpawn((s, l, p, e) -> false)));

    /**
     * Water caustics: the moving light on the bottom of a pool (animated textures, tools/make_water_caustics.py); right-click
     * it with a full block to show that block's texture under the light (WaterCausticsBlock, client/WaterCausticsModel).
     */
    public static final RegistryObject<WaterCausticsBlock> WATER_CAUSTICS = BLOCKS.register("water_caustics", () -> new WaterCausticsBlock(BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()
                    .mapColor(net.minecraft.world.level.material.MapColor.DIAMOND)));

    /** Rain glass: rain running down a window, over any block put behind it (RainGlassBlock). */
    public static final RegistryObject<RainGlassBlock> RAIN_GLASS = BLOCKS.register("rain_glass", () -> new RainGlassBlock(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE)));

    /** A lava lamp: glowing wax rising and sinking in a glass (LavaLampBlock, client/LavaLampRenderer). */
    public static final RegistryObject<LavaLampBlock> LAVA_LAMP = BLOCKS.register("lava_lamp", () -> new LavaLampBlock(BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(LavaLampBlock.LIT) ? 12 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE)));

    /** A plasma ball: filaments of light in a glass globe (PlasmaBallBlock, client/PlasmaBallRenderer). */
    public static final RegistryObject<PlasmaBallBlock> PLASMA_BALL = BLOCKS.register("plasma_ball", () -> new PlasmaBallBlock(BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(LavaLampBlock.LIT) ? 9 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)));

    /** A tesla coil: lightning crackling from its ring (TeslaCoilBlock, client/TeslaCoilRenderer). */
    public static final RegistryObject<TeslaCoilBlock> TESLA_COIL = BLOCKS.register("tesla_coil", () -> new TeslaCoilBlock(BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.COPPER).noOcclusion().requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(TeslaCoilBlock.LIT) ? 10 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE)));

    /** A galaxy projector: stars and nebulae thrown on the room round it (GalaxyProjectorBlock, client/GalaxyProjectorRenderer). */
    public static final RegistryObject<GalaxyProjectorBlock> GALAXY_PROJECTOR = BLOCKS.register("galaxy_projector", () -> new GalaxyProjectorBlock(BlockBehaviour.Properties.of().strength(0.8F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(GalaxyProjectorBlock.LIT) ? 4 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)));

    /** Working models of the solar system (SolarSystemBlock, client/SolarSystemRenderer): a brass orrery, a hologram planetarium. */
    public static final RegistryObject<SolarSystemBlock> ORRERY = BLOCKS.register("orrery", () -> new SolarSystemBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 7)
                    .mapColor(net.minecraft.world.level.material.MapColor.GOLD), false));
    public static final RegistryObject<SolarSystemBlock> PLANETARIUM = BLOCKS.register("planetarium", () -> new SolarSystemBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 9)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE), true));

    /** A kaleidoscope: coloured glass turning in windows on every side of a brass frame (KaleidoscopeBlock). */
    public static final RegistryObject<KaleidoscopeBlock> KALEIDOSCOPE = BLOCKS.register("kaleidoscope", () -> new KaleidoscopeBlock(BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.COPPER).lightLevel(state -> 10)
                    .mapColor(net.minecraft.world.level.material.MapColor.GOLD)));

    /** A portal mirror: look through it into the world past the other one of its colour, walk through it to get there (PortalMirrorBlock). */
    public static final RegistryObject<PortalMirrorBlock> PORTAL_MIRROR = BLOCKS.register("portal_mirror", () -> new PortalMirrorBlock(BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.AMETHYST).lightLevel(state -> 8)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE)));

    /** A magma basin: churning lava in a stone basin, basins side by side make one pool (MagmaBasinBlock). */
    public static final RegistryObject<MagmaBasinBlock> MAGMA_BASIN = BLOCKS.register("magma_basin", () -> new MagmaBasinBlock(BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.POLISHED_DEEPSLATE).noOcclusion().requiresCorrectToolForDrops()
                    .lightLevel(state -> 13)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE)));

    /** A black hole ornament: the world bent round it, in a swirl of glowing dust (BlackHoleBlock). */
    public static final RegistryObject<BlackHoleBlock> BLACK_HOLE = BLOCKS.register("black_hole", () -> new BlackHoleBlock(BlockBehaviour.Properties.of().strength(3.0F, 9.0F).sound(SoundType.AMETHYST).noOcclusion().lightLevel(state -> 6)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK), false));
    /** The big black hole: three blocks across, with a hot accretion disk (BlackHoleBlock). */
    public static final RegistryObject<BlackHoleBlock> BIG_BLACK_HOLE = BLOCKS.register("big_black_hole", () -> new BlackHoleBlock(BlockBehaviour.Properties.of().strength(3.0F, 9.0F).sound(SoundType.AMETHYST).noOcclusion().lightLevel(state -> 11)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK), true));

    /** The singularity: a black hole in a containment frame that stores items (SingularityBlock). */
    public static final RegistryObject<SingularityBlock> SINGULARITY = BLOCKS.register("singularity", () -> new SingularityBlock(BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(state -> 7)
                    .requiresCorrectToolForDrops().mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)));

    /** A containment frame: built round a singularity, a chamber the black hole grows to fill (ContainmentFrameBlock). */
    public static final RegistryObject<ContainmentFrameBlock> CONTAINMENT_FRAME = BLOCKS.register("containment_frame", () -> new ContainmentFrameBlock(BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(ContainmentFrameBlock.FORMED) ? 9 : 2)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)));

    /** The whirlpool singularity: a vortex in a frame that stores fluids (WhirlpoolBlock). */
    public static final RegistryObject<WhirlpoolBlock> WHIRLPOOL = BLOCKS.register("whirlpool", () -> new WhirlpoolBlock(BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(state -> 6)
                    .requiresCorrectToolForDrops().mapColor(net.minecraft.world.level.material.MapColor.WATER)));

    /** A tank frame: built round a whirlpool with glass sides, a containment tank it fills (TankFrameBlock). */
    public static final RegistryObject<TankFrameBlock> TANK_FRAME = BLOCKS.register("tank_frame", () -> new TankFrameBlock(BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(TankFrameBlock.FORMED) ? 9 : 2)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_CYAN)));

    /** Tank glass: clear glass without a border - a containment tank's sides of it read as one pane. */
    public static final RegistryObject<net.minecraft.world.level.block.TransparentBlock> TANK_GLASS = BLOCKS.register("tank_glass", () -> new net.minecraft.world.level.block.TransparentBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GLASS)));

    /** The special containment frames: a frame that does something more (SpecialFrameBlock). */
    public static final RegistryObject<SpecialFrameBlock> PORT_FRAME = specialFrame("port_frame", SpecialFrameBlock.Kind.PORT);
    public static final RegistryObject<SpecialFrameBlock> DISPLAY_FRAME = specialFrame("display_frame", SpecialFrameBlock.Kind.DISPLAY);
    public static final RegistryObject<SpecialFrameBlock> MONITOR_FRAME = specialFrame("monitor_frame", SpecialFrameBlock.Kind.MONITOR);
    public static final RegistryObject<SpecialFrameBlock> REDSTONE_FRAME = specialFrame("redstone_frame", SpecialFrameBlock.Kind.REDSTONE);

    private static RegistryObject<SpecialFrameBlock> specialFrame(String name, SpecialFrameBlock.Kind kind) {
        return BLOCKS.register(name, () -> new SpecialFrameBlock(BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
                        .lightLevel(state -> state.getValue(ContainmentFrameBlock.FORMED) ? 9 : 2)
                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK), kind));
    }

    /** The big lava lamp: two blocks tall (BigLavaLampBlock). */
    public static final RegistryObject<BigLavaLampBlock> BIG_LAVA_LAMP = BLOCKS.register("big_lava_lamp", () -> new BigLavaLampBlock(BlockBehaviour.Properties.of().strength(0.8F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(LavaLampBlock.LIT) ? 14 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE)));

    /** The fire tornado: a whirl of flame in a tall glass case (FireTornadoBlock, client/WallFireRenderer). */
    public static final RegistryObject<FireTornadoBlock> FIRE_TORNADO = BLOCKS.register("fire_tornado", () -> new FireTornadoBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(LavaLampBlock.LIT) ? 15 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)));

    /** A music visualizer screen (VisualizerBlock, client/VisualizerRenderer). */
    public static final RegistryObject<VisualizerBlock> MUSIC_VISUALIZER = BLOCKS.register("music_visualizer", () -> new VisualizerBlock(BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 7)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)));

    /** A hologram projector (HologramProjectorBlock, client/HologramRenderer). */
    public static final RegistryObject<HologramProjectorBlock> HOLOGRAM_PROJECTOR = BLOCKS.register("hologram_projector", () -> new HologramProjectorBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 6)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GRAY)));

    /** A jellyfish tank, 2 tall (JellyfishTankBlock, client/JellyTankRenderer). */
    public static final RegistryObject<JellyfishTankBlock> JELLYFISH_TANK = BLOCKS.register("jellyfish_tank", () -> new JellyfishTankBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(LavaLampBlock.LIT) ? 11 : 3)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)));

    /** The small fish tanks (SmallTankBlock, client/SmallTankRenderer): a fishbowl, a tall cylinder tank, a slim wall tank. */
    public static final RegistryObject<SmallTankBlock> FISHBOWL = BLOCKS.register("fishbowl", () -> new SmallTankBlock(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE), SmallTankBlock.BOWL));
    public static final RegistryObject<SmallTankBlock> TALL_TANK = BLOCKS.register("tall_tank", () -> new SmallTankBlock(BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> 6)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE), SmallTankBlock.TALL));
    public static final RegistryObject<SmallTankBlock> SLIM_TANK = BLOCKS.register("slim_tank", () -> new SmallTankBlock(BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> 6)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE), SmallTankBlock.SLIM));

    /** A koi pond (KoiPondBlock, client/KoiPondRenderer). */
    public static final RegistryObject<KoiPondBlock> KOI_POND = BLOCKS.register("koi_pond", () -> new KoiPondBlock(BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.STONE).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.WATER)));
    /** The koi pond in plain Minecraft style: the game's own water, lily pads, frogs, tropical fish, a turtle. */
    public static final RegistryObject<KoiPondBlock> VANILLA_KOI_POND = BLOCKS.register("vanilla_koi_pond", () -> new KoiPondBlock(BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.STONE).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.WATER)));

    /** A bamboo water spout (a shishi-odoshi) for beside a pond: it fills, tips, pours and clacks (BambooSpoutBlock). */
    public static final RegistryObject<BambooSpoutBlock> BAMBOO_SPOUT = BLOCKS.register("bamboo_spout", () -> new BambooSpoutBlock(BlockBehaviour.Properties.of().strength(1.0F, 3.0F).sound(SoundType.BAMBOO_WOOD).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.PLANT)));
    /** A music box: its lid opens, a dancer turns, it plays a tune; fish and fireflies near it move in time (MusicBoxBlock). */
    public static final RegistryObject<MusicBoxBlock> MUSIC_BOX = BLOCKS.register("music_box", () -> new MusicBoxBlock(BlockBehaviour.Properties.of().strength(1.0F, 3.0F).sound(SoundType.CHERRY_WOOD).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PINK)));
    /** A flat stepping stone, for across a koi pond (frogs sit on it). */
    public static final RegistryObject<SteppingStoneBlock> POND_STEPPING_STONE = BLOCKS.register("pond_stepping_stone", () -> new SteppingStoneBlock(BlockBehaviour.Properties.of().strength(1.0F, 6.0F).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops()
                    .mapColor(net.minecraft.world.level.material.MapColor.STONE)));
    /** A little arched wooden bridge, for over a koi pond. */
    public static final RegistryObject<PondBridgeBlock> POND_BRIDGE = BLOCKS.register("pond_bridge", () -> new PondBridgeBlock(BlockBehaviour.Properties.of().strength(1.5F, 3.0F).sound(SoundType.WOOD).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED)));

    /** A classic brick hearth, 2 wide and 2 tall (BrickHearthBlock, client/WallFireRenderer). */
    public static final RegistryObject<BrickHearthBlock> BRICK_HEARTH = BLOCKS.register("brick_hearth", () -> new BrickHearthBlock(BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(BrickHearthBlock.LIT) ? 15 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BROWN)));

    /** A neon sign: your own glowing text on a clear panel (NeonSignBlock, client/NeonSignRenderer). */
    public static final RegistryObject<NeonSignBlock> NEON_SIGN = BLOCKS.register("neon_sign", () -> new NeonSignBlock(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion().noCollission().lightLevel(state -> 8)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PINK)));

    /** An infinity mirror: a tunnel of LED rings going on forever (InfinityMirrorBlock, client/InfinityMirrorRenderer). */
    public static final RegistryObject<InfinityMirrorBlock> INFINITY_MIRROR = BLOCKS.register("infinity_mirror",
            () -> new InfinityMirrorBlock(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE).lightLevel(state -> 11)
                    .isValidSpawn((s, l, p, e) -> false)));

    /** An infinity cube: a glowing grid of LED edges going on forever in every direction (client/InfinityCubeRenderer). */
    public static final RegistryObject<InfinityCubeBlock> INFINITY_CUBE = BLOCKS.register("infinity_cube",
            () -> new InfinityCubeBlock(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE).lightLevel(state -> 12)
                    .isValidSpawn((s, l, p, e) -> false)));


    private ModBlocks() {}
}
