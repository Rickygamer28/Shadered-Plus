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

    public static final Supplier<BlockEntityType<GlowColourBlockEntity>> WALL_FIRE = TYPES.register("wall_fire",
            () -> BlockEntityType.Builder.of(GlowColourBlockEntity::wallFire, ModBlocks.WALL_FIRE.get()).build(null));

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

    private ModBlockEntities() {}
}
