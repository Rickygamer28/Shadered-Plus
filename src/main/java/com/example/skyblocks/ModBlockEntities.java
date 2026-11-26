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
            () -> BlockEntityType.Builder.of(SkyIslandBlockEntity::new, ModBlocks.SKY_ISLAND.get(),
                    ModBlocks.ISLAND_PAINTING.get()).build(null));

    public static final Supplier<BlockEntityType<GlowColourBlockEntity>> LAVA_LAMP = TYPES.register("lava_lamp",
            () -> BlockEntityType.Builder.of(GlowColourBlockEntity::lavaLamp, ModBlocks.LAVA_LAMP.get(), ModBlocks.BIG_LAVA_LAMP.get()).build(null));

    public static final Supplier<BlockEntityType<GlowColourBlockEntity>> FIRE = TYPES.register("fire",
            () -> BlockEntityType.Builder.of(GlowColourBlockEntity::fire, ModBlocks.FIRE_TORNADO.get()).build(null));
    public static final Supplier<BlockEntityType<GlowColourBlockEntity>> VISUALIZER = TYPES.register("music_visualizer",
            () -> BlockEntityType.Builder.of(GlowColourBlockEntity::visualizer, ModBlocks.MUSIC_VISUALIZER.get()).build(null));
    public static final Supplier<BlockEntityType<HologramBlockEntity>> HOLOGRAM = TYPES.register("hologram_projector",
            () -> BlockEntityType.Builder.of(HologramBlockEntity::new, ModBlocks.HOLOGRAM_PROJECTOR.get()).build(null));
    public static final Supplier<BlockEntityType<GlowColourBlockEntity>> PLASMA_BALL = TYPES.register("plasma_ball",
            () -> BlockEntityType.Builder.of(GlowColourBlockEntity::plasmaBall, ModBlocks.PLASMA_BALL.get()).build(null));
    public static final Supplier<BlockEntityType<GlowColourBlockEntity>> MAGMA_BASIN = TYPES.register("magma_basin",
            () -> BlockEntityType.Builder.of(GlowColourBlockEntity::magmaBasin, ModBlocks.MAGMA_BASIN.get()).build(null));
    public static final Supplier<BlockEntityType<GlowColourBlockEntity>> BLACK_HOLE = TYPES.register("black_hole",
            () -> BlockEntityType.Builder.of((pos, state) -> GlowColourBlockEntity.blackHole(pos, state,
                    state.getBlock() instanceof BlackHoleBlock b && b.big()), ModBlocks.BLACK_HOLE.get(), ModBlocks.BIG_BLACK_HOLE.get()).build(null));
    public static final Supplier<BlockEntityType<GlowColourBlockEntity>> JELLYFISH_TANK = TYPES.register("jellyfish_tank",
            () -> BlockEntityType.Builder.of(GlowColourBlockEntity::jellyTank, ModBlocks.JELLYFISH_TANK.get()).build(null));
    public static final Supplier<BlockEntityType<KoiPondBlockEntity>> KOI_POND = TYPES.register("koi_pond",
            () -> BlockEntityType.Builder.of(KoiPondBlockEntity::new, ModBlocks.KOI_POND.get(), ModBlocks.VANILLA_KOI_POND.get()).build(null));
    public static final Supplier<BlockEntityType<BambooSpoutBlockEntity>> BAMBOO_SPOUT = TYPES.register("bamboo_spout",
            () -> BlockEntityType.Builder.of(BambooSpoutBlockEntity::new, ModBlocks.BAMBOO_SPOUT.get()).build(null));
    public static final Supplier<BlockEntityType<MusicBoxBlockEntity>> MUSIC_BOX = TYPES.register("music_box",
            () -> BlockEntityType.Builder.of(MusicBoxBlockEntity::new, ModBlocks.MUSIC_BOX.get()).build(null));
    public static final Supplier<BlockEntityType<BrickHearthBlockEntity>> BRICK_HEARTH = TYPES.register("brick_hearth",
            () -> BlockEntityType.Builder.of(BrickHearthBlockEntity::new, ModBlocks.BRICK_HEARTH.get()).build(null));

    public static final Supplier<BlockEntityType<NeonSignBlockEntity>> NEON_SIGN = TYPES.register("neon_sign",
            () -> BlockEntityType.Builder.of(NeonSignBlockEntity::new, ModBlocks.NEON_SIGN.get()).build(null));

    public static final Supplier<BlockEntityType<WaterCausticsBlockEntity>> WATER_CAUSTICS = TYPES.register("water_caustics",
            () -> BlockEntityType.Builder.of(WaterCausticsBlockEntity::new, ModBlocks.WATER_CAUSTICS.get(), ModBlocks.RAIN_GLASS.get()).build(null));

    public static final Supplier<BlockEntityType<DiscoBallBlockEntity>> DISCO_BALL = TYPES.register("disco_ball",
            () -> BlockEntityType.Builder.of(DiscoBallBlockEntity::new, ModBlocks.DISCO_BALL.get()).build(null));

    public static final Supplier<BlockEntityType<FogMachineBlockEntity>> FOG_MACHINE = TYPES.register("fog_machine",
            () -> BlockEntityType.Builder.of(FogMachineBlockEntity::new, ModBlocks.FOG_MACHINE.get()).build(null));

    public static final Supplier<BlockEntityType<SpeakerBlockEntity>> SPEAKER = TYPES.register("speaker",
            () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new, ModBlocks.SPEAKER.get()).build(null));

    public static final Supplier<BlockEntityType<SmallTankBlockEntity>> SMALL_TANK = TYPES.register("small_tank",
            () -> BlockEntityType.Builder.of(SmallTankBlockEntity::new, ModBlocks.FISHBOWL.get(), ModBlocks.TALL_TANK.get(), ModBlocks.SLIM_TANK.get()).build(null));

    public static final Supplier<BlockEntityType<GlowColourBlockEntity>> TESLA_COIL = TYPES.register("tesla_coil",
            () -> BlockEntityType.Builder.of((pos, state) -> new GlowColourBlockEntity(ModBlockEntities.TESLA_COIL.get(), pos, state, 0x9FD8FF), ModBlocks.TESLA_COIL.get()).build(null));
    public static final Supplier<BlockEntityType<GalaxyProjectorBlockEntity>> GALAXY_PROJECTOR = TYPES.register("galaxy_projector",
            () -> BlockEntityType.Builder.of(GalaxyProjectorBlockEntity::new, ModBlocks.GALAXY_PROJECTOR.get()).build(null));

    public static final Supplier<BlockEntityType<ContainmentFrameBlockEntity>> CONTAINMENT_FRAME = TYPES.register("containment_frame",
            () -> BlockEntityType.Builder.of(ContainmentFrameBlockEntity::new, ModBlocks.CONTAINMENT_FRAME.get()).build(null));
    public static final Supplier<BlockEntityType<WhirlpoolBlockEntity>> WHIRLPOOL = TYPES.register("whirlpool",
            () -> BlockEntityType.Builder.of(WhirlpoolBlockEntity::new, ModBlocks.WHIRLPOOL.get()).build(null));
    public static final Supplier<BlockEntityType<TankFrameBlockEntity>> TANK_FRAME = TYPES.register("tank_frame",
            () -> BlockEntityType.Builder.of(TankFrameBlockEntity::new, ModBlocks.TANK_FRAME.get()).build(null));
    public static final Supplier<BlockEntityType<PowerCoreBlockEntity>> POWER_CORE = TYPES.register("power_core",
            () -> BlockEntityType.Builder.of(PowerCoreBlockEntity::new, ModBlocks.POWER_CORE.get()).build(null));
    public static final Supplier<BlockEntityType<CoreFrameBlockEntity>> CORE_FRAME = TYPES.register("core_frame",
            () -> BlockEntityType.Builder.of(CoreFrameBlockEntity::new, ModBlocks.CORE_FRAME.get()).build(null));
    public static final Supplier<BlockEntityType<MachineFrameBlockEntity>> MACHINE_FRAME = TYPES.register("machine_frame",
            () -> BlockEntityType.Builder.of(MachineFrameBlockEntity::new, ModBlocks.ACCELERATOR_FRAME.get(), ModBlocks.WEATHER_MAST.get(),
                    ModBlocks.FORGE_FRAME.get()).build(null));
    public static final Supplier<BlockEntityType<ParticleAcceleratorBlockEntity>> PARTICLE_ACCELERATOR = TYPES.register("particle_accelerator",
            () -> BlockEntityType.Builder.of(ParticleAcceleratorBlockEntity::new, ModBlocks.PARTICLE_ACCELERATOR.get()).build(null));
    public static final Supplier<BlockEntityType<WeatherMachineBlockEntity>> WEATHER_MACHINE = TYPES.register("weather_machine",
            () -> BlockEntityType.Builder.of(WeatherMachineBlockEntity::new, ModBlocks.WEATHER_MACHINE.get()).build(null));
    public static final Supplier<BlockEntityType<StarForgeBlockEntity>> STAR_FORGE = TYPES.register("star_forge",
            () -> BlockEntityType.Builder.of(StarForgeBlockEntity::new, ModBlocks.STAR_FORGE.get()).build(null));
    public static final Supplier<BlockEntityType<SpecialFrameBlockEntity>> SPECIAL_FRAME = TYPES.register("special_frame",
            () -> BlockEntityType.Builder.of(SpecialFrameBlockEntity::new, ModBlocks.PORT_FRAME.get(), ModBlocks.DISPLAY_FRAME.get(),
                    ModBlocks.MONITOR_FRAME.get(), ModBlocks.REDSTONE_FRAME.get(), ModBlocks.CRAFTING_FRAME.get()).build(null));
    public static final Supplier<BlockEntityType<SingularityBlockEntity>> SINGULARITY = TYPES.register("singularity",
            () -> BlockEntityType.Builder.of(SingularityBlockEntity::new, ModBlocks.SINGULARITY.get()).build(null));
    public static final Supplier<BlockEntityType<EffectBlockEntity>> EFFECT_BLOCK = TYPES.register("effect_block",
            () -> BlockEntityType.Builder.of(EffectBlockEntity::new, ModBlocks.EFFECT_BLOCK.get(), ModBlocks.EFFECT_GLASS.get()).build(null));
    public static final Supplier<BlockEntityType<WeatherGlobeBlockEntity>> WEATHER_GLOBE = TYPES.register("weather_globe",
            () -> BlockEntityType.Builder.of(WeatherGlobeBlockEntity::new, ModBlocks.WEATHER_GLOBE.get()).build(null));
    public static final Supplier<BlockEntityType<LavaPillarBlockEntity>> LAVA_PILLAR = TYPES.register("lava_pillar",
            () -> BlockEntityType.Builder.of(LavaPillarBlockEntity::new, ModBlocks.LAVA_PILLAR.get()).build(null));
    public static final Supplier<BlockEntityType<LightBridgeEmitterBlockEntity>> LIGHT_BRIDGE_EMITTER = TYPES.register("light_bridge_emitter",
            () -> BlockEntityType.Builder.of(LightBridgeEmitterBlockEntity::new, ModBlocks.LIGHT_BRIDGE_EMITTER.get()).build(null));
    public static final Supplier<BlockEntityType<TeleportPadBlockEntity>> TELEPORT_PAD = TYPES.register("teleport_pad",
            () -> BlockEntityType.Builder.of(TeleportPadBlockEntity::new, ModBlocks.TELEPORT_PAD.get()).build(null));
    public static final Supplier<BlockEntityType<NeonTubeBlockEntity>> NEON_TUBE = TYPES.register("neon_tube",
            () -> BlockEntityType.Builder.of(NeonTubeBlockEntity::new, ModBlocks.NEON_TUBE.get()).build(null));
    public static final Supplier<BlockEntityType<ClawMachineBlockEntity>> CLAW_MACHINE = TYPES.register("claw_machine",
            () -> BlockEntityType.Builder.of(ClawMachineBlockEntity::new, ModBlocks.CLAW_MACHINE.get()).build(null));
    public static final Supplier<BlockEntityType<TrophyBlockEntity>> TROPHY = TYPES.register("trophy",
            () -> BlockEntityType.Builder.of(TrophyBlockEntity::new, ModBlocks.TROPHY.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> WATERFALL = TYPES.register("waterfall",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.WATERFALL.get(), p, st), ModBlocks.WATERFALL.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> STAGE = TYPES.register("stage",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.STAGE.get(), p, st), ModBlocks.STAGE.get()).build(null));
    public static final Supplier<BlockEntityType<SpotlightBlockEntity>> SPOTLIGHT = TYPES.register("spotlight",
            () -> BlockEntityType.Builder.of(SpotlightBlockEntity::new, ModBlocks.SPOTLIGHT.get(), ModBlocks.BEAM_LIGHT.get()).build(null));
    public static final Supplier<BlockEntityType<TerrariumBlockEntity>> TERRARIUM = TYPES.register("terrarium",
            () -> BlockEntityType.Builder.of(TerrariumBlockEntity::new, ModBlocks.TERRARIUM.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> BIG_SCREEN = TYPES.register("big_screen",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.BIG_SCREEN.get(), p, st), ModBlocks.BIG_SCREEN.get()).build(null));
    public static final Supplier<BlockEntityType<ArcadeBlockEntity>> ARCADE = TYPES.register("arcade",
            () -> BlockEntityType.Builder.of(ArcadeBlockEntity::new, ModBlocks.ARCADE.get()).build(null));
    public static final Supplier<BlockEntityType<LaserProjectorBlockEntity>> LASER_PROJECTOR = TYPES.register("laser_projector",
            () -> BlockEntityType.Builder.of(LaserProjectorBlockEntity::new, ModBlocks.LASER_PROJECTOR.get()).build(null));
    public static final Supplier<BlockEntityType<KaleidoscopeBlockEntity>> KALEIDOSCOPE = TYPES.register("kaleidoscope",
            () -> BlockEntityType.Builder.of(KaleidoscopeBlockEntity::new, ModBlocks.KALEIDOSCOPE.get()).build(null));
    public static final Supplier<BlockEntityType<SolarSystemBlockEntity>> SOLAR_SYSTEM = TYPES.register("solar_system",
            () -> BlockEntityType.Builder.of(SolarSystemBlockEntity::new, ModBlocks.ORRERY.get(), ModBlocks.PLANETARIUM.get()).build(null));
    public static final Supplier<BlockEntityType<BonsaiBlockEntity>> BONSAI = TYPES.register("bonsai",
            () -> BlockEntityType.Builder.of(BonsaiBlockEntity::new, ModBlocks.BONSAI.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> HOT_SPRING = TYPES.register("hot_spring",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.HOT_SPRING.get(), p, st), ModBlocks.HOT_SPRING.get()).build(null));
    public static final Supplier<BlockEntityType<InfusionAltarBlockEntity>> INFUSION_ALTAR = TYPES.register("infusion_altar",
            () -> BlockEntityType.Builder.of(InfusionAltarBlockEntity::new, ModBlocks.INFUSION_ALTAR.get()).build(null));
    public static final Supplier<BlockEntityType<SkyHolderBlockEntity>> SKY_RIFT = TYPES.register("sky_rift",
            () -> BlockEntityType.Builder.of(SkyHolderBlockEntity::rift, ModBlocks.SKY_RIFT.get()).build(null));
    public static final Supplier<BlockEntityType<SkyHolderBlockEntity>> SKY_BEACON = TYPES.register("sky_beacon",
            () -> BlockEntityType.Builder.of(SkyHolderBlockEntity::beacon, ModBlocks.SKY_BEACON.get()).build(null));
    public static final Supplier<BlockEntityType<SkyHolderBlockEntity>> SKY_FOUNTAIN = TYPES.register("sky_fountain",
            () -> BlockEntityType.Builder.of(SkyHolderBlockEntity::fountain, ModBlocks.SKY_FOUNTAIN.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> WATER_FOUNTAIN = TYPES.register("water_fountain",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.WATER_FOUNTAIN.get(), p, st), ModBlocks.WATER_FOUNTAIN.get()).build(null));
    public static final Supplier<BlockEntityType<PlanetGlobeBlock.Entity>> PLANET_GLOBE = TYPES.register("planet_globe",
            () -> BlockEntityType.Builder.of(PlanetGlobeBlock.Entity::new, ModBlocks.PLANET_GLOBE.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> AURORA_CURTAIN = TYPES.register("aurora_curtain",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.AURORA_CURTAIN.get(), p, st), ModBlocks.AURORA_CURTAIN.get()).build(null));
    public static final Supplier<BlockEntityType<SkyStudioBlockEntity>> SKY_STUDIO = TYPES.register("sky_studio",
            () -> BlockEntityType.Builder.of(SkyStudioBlockEntity::new, ModBlocks.SKY_STUDIO.get()).build(null));
    public static final Supplier<BlockEntityType<SkyLanternBlockEntity>> SKY_LANTERN = TYPES.register("sky_lantern",
            () -> BlockEntityType.Builder.of(SkyLanternBlockEntity::new, ModBlocks.SKY_LANTERN.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> CAT_WINDOW_PERCH = TYPES.register("cat_window_perch",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.CAT_WINDOW_PERCH.get(), p, st), ModBlocks.CAT_WINDOW_PERCH.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> FERROFLUID = TYPES.register("ferrofluid",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.FERROFLUID.get(), p, st), ModBlocks.FERROFLUID.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> SKY_CLOCK = TYPES.register("sky_clock",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.SKY_CLOCK.get(), p, st), ModBlocks.SKY_CLOCK.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> PRISM = TYPES.register("prism",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.PRISM.get(), p, st), ModBlocks.PRISM.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> BOTTOMLESS_PIT = TYPES.register("bottomless_pit",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.BOTTOMLESS_PIT.get(), p, st), ModBlocks.BOTTOMLESS_PIT.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> ROOM_WINDOW = TYPES.register("room_window",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.ROOM_WINDOW.get(), p, st), ModBlocks.ROOM_WINDOW.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> MOON_JAR = TYPES.register("moon_jar",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.MOON_JAR.get(), p, st), ModBlocks.MOON_JAR.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> SUN_JAR = TYPES.register("sun_jar",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.SUN_JAR.get(), p, st), ModBlocks.SUN_JAR.get()).build(null));
    public static final Supplier<BlockEntityType<DarkFountainBlockEntity>> DARK_FOUNTAIN = TYPES.register("dark_fountain",
            () -> BlockEntityType.Builder.of(DarkFountainBlockEntity::new, ModBlocks.DARK_FOUNTAIN.get()).build(null));
    public static final Supplier<BlockEntityType<FilterBlockEntity>> FILTER = TYPES.register("filter",
            () -> BlockEntityType.Builder.of(FilterBlockEntity::new, java.util.Arrays.stream(ModBlocks.FILTERS).map(b -> (net.minecraft.world.level.block.Block) b.get())
                    .toArray(net.minecraft.world.level.block.Block[]::new)).build(null));
    public static final Supplier<BlockEntityType<ShaderLightBlockEntity>> SHADER_LIGHT = TYPES.register("shader_light",
            () -> BlockEntityType.Builder.of(ShaderLightBlockEntity::new, ModBlocks.SHADER_LIGHT.get()).build(null));
    public static final Supplier<BlockEntityType<SpinPadBlockEntity>> SPIN_PAD = TYPES.register("spin_pad",
            () -> BlockEntityType.Builder.of(SpinPadBlockEntity::new, ModBlocks.SPIN_PAD.get()).build(null));
    public static final Supplier<BlockEntityType<TimeCrystalBlockEntity>> TIME_CRYSTAL = TYPES.register("time_crystal",
            () -> BlockEntityType.Builder.of(TimeCrystalBlockEntity::new, ModBlocks.TIME_CRYSTAL.get()).build(null));
    public static final Supplier<BlockEntityType<ElevatorBaseBlockEntity>> ELEVATOR_BASE = TYPES.register("elevator_base",
            () -> BlockEntityType.Builder.of(ElevatorBaseBlockEntity::new, ModBlocks.ELEVATOR_BASE.get()).build(null));
    public static final Supplier<BlockEntityType<PhaseBlockEntity>> PHASE_BLOCK = TYPES.register("phase_block",
            () -> BlockEntityType.Builder.of(PhaseBlockEntity::new, ModBlocks.PHASE_BLOCK.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> ECHO_FIELD = TYPES.register("echo_field",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.ECHO_FIELD.get(), p, st), ModBlocks.ECHO_FIELD.get()).build(null));
    public static final Supplier<BlockEntityType<HourglassBlockEntity>> HOURGLASS = TYPES.register("hourglass",
            () -> BlockEntityType.Builder.of(HourglassBlockEntity::new, ModBlocks.HOURGLASS.get()).build(null));
    public static final Supplier<BlockEntityType<ChalkRuneBlockEntity>> CHALK_RUNE = TYPES.register("chalk_rune",
            () -> BlockEntityType.Builder.of(ChalkRuneBlockEntity::new, ModBlocks.CHALK_RUNE.get()).build(null));
    public static final Supplier<BlockEntityType<CatFlapBlockEntity>> CAT_FLAP = TYPES.register("cat_flap",
            () -> BlockEntityType.Builder.of(CatFlapBlockEntity::new, ModBlocks.CAT_FLAP.get()).build(null));
    public static final Supplier<BlockEntityType<SimpleRenderedBlockEntity>> GEODE_LAMP = TYPES.register("geode_lamp",
            () -> BlockEntityType.Builder.of((p, st) -> new SimpleRenderedBlockEntity(ModBlockEntities.GEODE_LAMP.get(), p, st), ModBlocks.GEODE_LAMP.get()).build(null));
    public static final Supplier<BlockEntityType<PhotoFrameBlockEntity>> PHOTO_FRAME = TYPES.register("photo_frame",
            () -> BlockEntityType.Builder.of(PhotoFrameBlockEntity::new, ModBlocks.PHOTO_FRAME.get()).build(null));

    private ModBlockEntities() {}
}
