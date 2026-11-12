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

    /** The mirror's variants: a round mirror in a gold ring, a cracked mirror, a vanity mirror framed with bulbs. */
    public static final DeferredBlock<RoundMirrorBlock> ROUND_MIRROR = BLOCKS.registerBlock("round_mirror", RoundMirrorBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.GOLD).isValidSpawn((s, l, p, e) -> false));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CRACKED_MIRROR = BLOCKS.registerSimpleBlock("cracked_mirror",
            BlockBehaviour.Properties.of().strength(0.4F).sound(SoundType.GLASS)
                    .mapColor(net.minecraft.world.level.material.MapColor.ICE).isValidSpawn((s, l, p, e) -> false));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> VANITY_MIRROR = BLOCKS.registerSimpleBlock("vanity_mirror",
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).lightLevel(state -> 12)
                    .mapColor(net.minecraft.world.level.material.MapColor.SNOW).isValidSpawn((s, l, p, e) -> false));

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

    /** The coral reef aquarium: white sand, corals and anemones, reef fish (clownfish, tangs, pufferfish...). */
    public static final DeferredBlock<AquariumBlock> REEF_AQUARIUM = BLOCKS.registerBlock("reef_aquarium",
            properties -> new AquariumBlock(properties, false, false, 1),
            BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 7));

    /** The jungle river aquarium: tea-coloured water, driftwood and plants, piranhas, tetras, angelfish, an axolotl. */
    public static final DeferredBlock<AquariumBlock> JUNGLE_AQUARIUM = BLOCKS.registerBlock("jungle_aquarium",
            properties -> new AquariumBlock(properties, false, false, 2),
            BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS).lightLevel(state -> 5));

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

    /** A plasma ball: filaments of light in a glass globe (PlasmaBallBlock, client/PlasmaBallRenderer). */
    public static final DeferredBlock<PlasmaBallBlock> PLASMA_BALL = BLOCKS.registerBlock("plasma_ball", PlasmaBallBlock::new,
            BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(LavaLampBlock.LIT) ? 9 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE));

    /** A tesla coil: lightning crackling from its ring (TeslaCoilBlock, client/TeslaCoilRenderer). */
    public static final DeferredBlock<TeslaCoilBlock> TESLA_COIL = BLOCKS.registerBlock("tesla_coil", TeslaCoilBlock::new,
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.COPPER).noOcclusion().requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(TeslaCoilBlock.LIT) ? 10 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE));

    /** A galaxy projector: stars and nebulae thrown on the room round it (GalaxyProjectorBlock, client/GalaxyProjectorRenderer). */
    public static final DeferredBlock<GalaxyProjectorBlock> GALAXY_PROJECTOR = BLOCKS.registerBlock("galaxy_projector", GalaxyProjectorBlock::new,
            BlockBehaviour.Properties.of().strength(0.8F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(GalaxyProjectorBlock.LIT) ? 4 : 0)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE));

    /** Working models of the solar system (SolarSystemBlock, client/SolarSystemRenderer): a brass orrery, a hologram planetarium. */
    public static final DeferredBlock<SolarSystemBlock> ORRERY = BLOCKS.registerBlock("orrery", p -> new SolarSystemBlock(p, false),
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 7)
                    .mapColor(net.minecraft.world.level.material.MapColor.GOLD));
    public static final DeferredBlock<SolarSystemBlock> PLANETARIUM = BLOCKS.registerBlock("planetarium", p -> new SolarSystemBlock(p, true),
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 9)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE));

    /** An effect block: a full block showing an animated shader effect of your choosing (EffectBlock, client/EffectBlockRenderer). */
    public static final DeferredBlock<EffectBlock> EFFECT_BLOCK = BLOCKS.registerBlock("effect_block", EffectBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F, 6.0F).sound(SoundType.GLASS).lightLevel(state -> 12)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_MAGENTA));

    /** Effect Glass: the effect block, see-through (EffectGlassBlock). */
    public static final DeferredBlock<EffectGlassBlock> EFFECT_GLASS = BLOCKS.registerBlock("effect_glass", EffectGlassBlock::new,
            BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> 12)
                    .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false).mapColor(net.minecraft.world.level.material.MapColor.NONE));

    /** A weather globe: a glass ball showing the weather and time of day outside (WeatherGlobeBlock). */
    public static final DeferredBlock<WeatherGlobeBlock> WEATHER_GLOBE = BLOCKS.registerBlock("weather_globe", WeatherGlobeBlock::new,
            BlockBehaviour.Properties.of().strength(0.8F).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> 3)
                    .mapColor(net.minecraft.world.level.material.MapColor.WOOD));

    /** A lava lamp pillar: stack them for one tall lamp (LavaPillarBlock). */
    public static final DeferredBlock<LavaPillarBlock> LAVA_PILLAR = BLOCKS.registerBlock("lava_pillar", LavaPillarBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> 11)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE));

    /** A light bridge emitter: two facing each other make a walkable bridge of light (LightBridgeEmitterBlock). */
    public static final DeferredBlock<LightBridgeEmitterBlock> LIGHT_BRIDGE_EMITTER = BLOCKS.registerBlock("light_bridge_emitter", LightBridgeEmitterBlock::new,
            BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).lightLevel(state -> 7).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL));
    /** Linked by colour: crouch on one to beam to the next (TeleportPadBlock). */
    public static final DeferredBlock<TeleportPadBlock> TELEPORT_PAD = BLOCKS.registerBlock("teleport_pad", TeleportPadBlock::new,
            BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).lightLevel(state -> 7).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL));
    /** Turns a light bridge round a corner (LightBridgeBenderBlock). */
    public static final DeferredBlock<LightBridgeBenderBlock> LIGHT_BRIDGE_BENDER = BLOCKS.registerBlock("light_bridge_bender", LightBridgeBenderBlock::new,
            BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).lightLevel(state -> 6).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL));
    /** The bridge's blocks, put down and taken away by the emitters (LightBridgeBlock). */
    public static final DeferredBlock<LightBridgeBlock> LIGHT_BRIDGE = BLOCKS.registerBlock("light_bridge", LightBridgeBlock::new,
            BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().lightLevel(state -> 8)
                    .isValidSpawn((s, l, p, e) -> false).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                    .mapColor(net.minecraft.world.level.material.MapColor.NONE));

    /** A party laser projector (LaserProjectorBlock, client/LaserProjectorRenderer). */
    public static final DeferredBlock<LaserProjectorBlock> LASER_PROJECTOR = BLOCKS.registerBlock("laser_projector", LaserProjectorBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 5)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));

    /** An arcade cabinet, two blocks tall: Snake and Breakout, its best scores kept (ArcadeBlock). */
    public static final DeferredBlock<ArcadeBlock> ARCADE = BLOCKS.registerBlock("arcade", ArcadeBlock::new,
            BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.WOOD).noOcclusion().lightLevel(state -> 7)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE));

    /** A big screen for the arcade: a wall of them is one screen, showing the nearest game being played (BigScreenBlock). */
    public static final DeferredBlock<BigScreenBlock> BIG_SCREEN = BLOCKS.registerBlock("big_screen", BigScreenBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 10)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));

    /** A claw machine, two blocks tall: win plushies (ClawMachineBlock). */
    public static final DeferredBlock<ClawMachineBlock> CLAW_MACHINE = BLOCKS.registerBlock("claw_machine", ClawMachineBlock::new,
            BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 9)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY).mapColor(net.minecraft.world.level.material.MapColor.COLOR_PINK));
    /** A high-score trophy (TrophyBlock). */
    public static final DeferredBlock<TrophyBlock> TROPHY = BLOCKS.registerBlock("trophy", TrophyBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.METAL).noOcclusion().mapColor(net.minecraft.world.level.material.MapColor.GOLD));
    /** Neon tubes (NeonTubeBlock). */
    public static final DeferredBlock<NeonTubeBlock> NEON_TUBE = BLOCKS.registerBlock("neon_tube", NeonTubeBlock::new,
            BlockBehaviour.Properties.of().strength(0.3F).sound(SoundType.GLASS).noOcclusion().noCollission().lightLevel(state -> 9)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PINK));
    /** A waterfall down a wall (WaterfallBlock). */
    public static final DeferredBlock<WaterfallBlock> WATERFALL = BLOCKS.registerBlock("waterfall", WaterfallBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.STONE).noOcclusion().mapColor(net.minecraft.world.level.material.MapColor.STONE));
    /** A stage (StageBlock) and a spotlight (SpotlightBlock). */
    public static final DeferredBlock<StageBlock> STAGE = BLOCKS.registerBlock("stage", StageBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD).mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));
    public static final DeferredBlock<SpotlightBlock> SPOTLIGHT = BLOCKS.registerBlock("spotlight", SpotlightBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 7)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));
    /** A pet terrarium (TerrariumBlock). */
    public static final DeferredBlock<TerrariumBlock> TERRARIUM = BLOCKS.registerBlock("terrarium", TerrariumBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> 6)
                    .mapColor(net.minecraft.world.level.material.MapColor.SAND));

    /** The plushies (PlushieBlock): won from a claw machine. */
    public static final String[] PLUSHIE_NAMES = {"cat", "ember", "frost", "moss", "bloom", "frog", "axolotl", "clownfish", "blue_tang", "goldfish", "angelfish", "shark", "octopus", "crab", "ocean_sunfish"};
    public static final java.util.List<DeferredBlock<PlushieBlock>> PLUSHIES = new java.util.ArrayList<>();

    static {
        for (String name : PLUSHIE_NAMES) {
            PLUSHIES.add(BLOCKS.registerBlock("plushie_" + name, PlushieBlock::new,
                    BlockBehaviour.Properties.of().strength(0.4F).sound(SoundType.WOOL).noOcclusion()
                            .mapColor(net.minecraft.world.level.material.MapColor.WOOL)));
        }
    }

    /** A kaleidoscope: coloured glass turning in windows on every side of a brass frame (KaleidoscopeBlock). */
    public static final DeferredBlock<KaleidoscopeBlock> KALEIDOSCOPE = BLOCKS.registerBlock("kaleidoscope", KaleidoscopeBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.COPPER).lightLevel(state -> 10)
                    .mapColor(net.minecraft.world.level.material.MapColor.GOLD));

    /** A portal mirror: look through it into the world past the other one of its colour, walk through it to get there (PortalMirrorBlock). */
    public static final DeferredBlock<PortalMirrorBlock> PORTAL_MIRROR = BLOCKS.registerBlock("portal_mirror", PortalMirrorBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F, 6.0F).sound(SoundType.AMETHYST).lightLevel(state -> 8)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_PURPLE));

    /** A magma basin: churning lava in a stone basin, basins side by side make one pool (MagmaBasinBlock). */
    public static final DeferredBlock<MagmaBasinBlock> MAGMA_BASIN = BLOCKS.registerBlock("magma_basin", MagmaBasinBlock::new,
            BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(SoundType.POLISHED_DEEPSLATE).noOcclusion().requiresCorrectToolForDrops()
                    .lightLevel(state -> 13)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE));

    /** A black hole ornament: the world bent round it, in a swirl of glowing dust (BlackHoleBlock). */
    public static final DeferredBlock<BlackHoleBlock> BLACK_HOLE = BLOCKS.registerBlock("black_hole", p -> new BlackHoleBlock(p, false),
            BlockBehaviour.Properties.of().strength(3.0F, 9.0F).sound(SoundType.AMETHYST).noOcclusion().lightLevel(state -> 6)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));
    /** The big black hole: three blocks across, with a hot accretion disk (BlackHoleBlock). */
    public static final DeferredBlock<BlackHoleBlock> BIG_BLACK_HOLE = BLOCKS.registerBlock("big_black_hole", p -> new BlackHoleBlock(p, true),
            BlockBehaviour.Properties.of().strength(3.0F, 9.0F).sound(SoundType.AMETHYST).noOcclusion().lightLevel(state -> 11)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));

    /** The singularity: a black hole in a containment frame that stores items (SingularityBlock). */
    public static final DeferredBlock<SingularityBlock> SINGULARITY = BLOCKS.registerBlock("singularity", SingularityBlock::new,
            BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(state -> 7)
                    .requiresCorrectToolForDrops().mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));

    /** A containment frame: built round a singularity, a chamber the black hole grows to fill (ContainmentFrameBlock). */
    public static final DeferredBlock<ContainmentFrameBlock> CONTAINMENT_FRAME = BLOCKS.registerBlock("containment_frame", ContainmentFrameBlock::new,
            BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(ContainmentFrameBlock.FORMED) ? 9 : 2)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));

    /** The whirlpool singularity: a vortex in a frame that stores fluids (WhirlpoolBlock). */
    public static final DeferredBlock<WhirlpoolBlock> WHIRLPOOL = BLOCKS.registerBlock("whirlpool", WhirlpoolBlock::new,
            BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(state -> 6)
                    .requiresCorrectToolForDrops().mapColor(net.minecraft.world.level.material.MapColor.WATER));

    /** A tank frame: built round a whirlpool with glass sides, a containment tank it fills (TankFrameBlock). */
    public static final DeferredBlock<TankFrameBlock> TANK_FRAME = BLOCKS.registerBlock("tank_frame", TankFrameBlock::new,
            BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(TankFrameBlock.FORMED) ? 9 : 2)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_CYAN));

    /** Tank glass: clear glass without a border - a containment tank's sides of it read as one pane. */
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> TANK_GLASS = BLOCKS.registerBlock("tank_glass",
            net.minecraft.world.level.block.TransparentBlock::new, BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS));

    /** The special containment frames: a frame that does something more (SpecialFrameBlock). */
    public static final DeferredBlock<SpecialFrameBlock> PORT_FRAME = specialFrame("port_frame", SpecialFrameBlock.Kind.PORT);
    public static final DeferredBlock<SpecialFrameBlock> DISPLAY_FRAME = specialFrame("display_frame", SpecialFrameBlock.Kind.DISPLAY);
    public static final DeferredBlock<SpecialFrameBlock> MONITOR_FRAME = specialFrame("monitor_frame", SpecialFrameBlock.Kind.MONITOR);
    public static final DeferredBlock<SpecialFrameBlock> REDSTONE_FRAME = specialFrame("redstone_frame", SpecialFrameBlock.Kind.REDSTONE);

    private static DeferredBlock<SpecialFrameBlock> specialFrame(String name, SpecialFrameBlock.Kind kind) {
        return BLOCKS.registerBlock(name, p -> new SpecialFrameBlock(p, kind),
                BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()
                        .lightLevel(state -> state.getValue(ContainmentFrameBlock.FORMED) ? 9 : 2)
                        .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK));
    }

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

    /** The small fish tanks (SmallTankBlock, client/SmallTankRenderer): a fishbowl, a tall cylinder tank, a slim wall tank. */
    public static final DeferredBlock<SmallTankBlock> FISHBOWL = BLOCKS.registerBlock("fishbowl", p -> new SmallTankBlock(p, SmallTankBlock.BOWL),
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.GLASS).noOcclusion()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<SmallTankBlock> TALL_TANK = BLOCKS.registerBlock("tall_tank", p -> new SmallTankBlock(p, SmallTankBlock.TALL),
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> 6)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<SmallTankBlock> SLIM_TANK = BLOCKS.registerBlock("slim_tank", p -> new SmallTankBlock(p, SmallTankBlock.SLIM),
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.GLASS).noOcclusion().lightLevel(state -> 6)
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE));

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

    // ---- the garden, keepsakes ----

    /** Zen sand: rake patterns into it (ZenSandBlock, ZenRakeItem). */
    public static final DeferredBlock<ZenSandBlock> ZEN_SAND = BLOCKS.registerBlock("zen_sand", ZenSandBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.SAND).mapColor(net.minecraft.world.level.material.MapColor.SAND));
    /** Garden stones for zen sand (ZenStoneBlock). */
    public static final DeferredBlock<ZenStoneBlock> ZEN_STONE = BLOCKS.registerBlock("zen_stone", ZenStoneBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.DESTROY)
                    .mapColor(net.minecraft.world.level.material.MapColor.STONE));
    /** A bonsai pot and the little tree growing in it (BonsaiBlock, client/BonsaiRenderer). */
    public static final DeferredBlock<BonsaiBlock> BONSAI = BLOCKS.registerBlock("bonsai", BonsaiBlock::new,
            BlockBehaviour.Properties.of().strength(0.6F).sound(SoundType.DECORATED_POT).noOcclusion().pushReaction(PushReaction.DESTROY)
                    .mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_BROWN));
    /** A hot spring (HotSpringBlock, client/HotSpringRenderer). */
    public static final DeferredBlock<HotSpringBlock> HOT_SPRING = BLOCKS.registerBlock("hot_spring", HotSpringBlock::new,
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.TUFF).noOcclusion().lightLevel(state -> 6)
                    .mapColor(net.minecraft.world.level.material.MapColor.WATER).isValidSpawn((s, l, p, e) -> false));
    /** A framed photo (PhotoFrameBlock; placed from a PhotoItem, gives the photo back when broken). */
    public static final DeferredBlock<PhotoFrameBlock> PHOTO_FRAME = BLOCKS.registerBlock("photo_frame", PhotoFrameBlock::new,
            BlockBehaviour.Properties.of().strength(0.3F).sound(SoundType.WOOD).noOcclusion().noCollission().noLootTable()
                    .pushReaction(PushReaction.DESTROY));

    private ModBlocks() {}
}
