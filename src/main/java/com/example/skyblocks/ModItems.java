package com.example.skyblocks;

import com.example.skyblocks.island.SkyIslandItem;

import com.noodlegamer76.shadered.block.InitBlocks;
import com.noodlegamer76.shadered.item.SkyblockItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One Shadered skyblock item per sky. They are real Shadered SkyblockItems: they place Shadered's own
 * skyblock with our sky type, use Shadered's item renderer, work in the Sky Emitter, with filters, etc.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyBlocksMod.MOD_ID);

    public static final List<DeferredItem<SkyblockItem>> SKY_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < SkyNames.NAMES.length; i++) {
            final int index = i;
            SKY_ITEMS.add(ITEMS.register(SkyNames.itemId(i),
                    () -> new SkyblockItem(InitBlocks.SKYBLOCK.get(), new Item.Properties(), SkyTypes.itemType(index))));
        }
    }

    /** The OIIA cat model block (its renderer is registered in client/ClientSetup). */
    public static final DeferredItem<BlockItem> OIIA_CAT = ITEMS.registerSimpleBlockItem("oiia_cat", ModBlocks.OIIA_CAT);

    public static final DeferredItem<BlockItem> ISLAND_SCANNER = ITEMS.registerSimpleBlockItem("island_scanner", ModBlocks.ISLAND_SCANNER);

    /** Holds a scanned island (only made by the Island Scanner). */
    public static final DeferredItem<SkyIslandItem> SKY_ISLAND = ITEMS.register("sky_island",
            () -> new SkyIslandItem(ModBlocks.SKY_ISLAND.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> ISLAND_PAINTING = ITEMS.registerSimpleBlockItem("island_painting", ModBlocks.ISLAND_PAINTING);

    public static final DeferredItem<BlockItem> MIRROR = ITEMS.registerSimpleBlockItem("mirror", ModBlocks.MIRROR);
    public static final DeferredItem<BlockItem> ROUND_MIRROR = ITEMS.registerSimpleBlockItem("round_mirror", ModBlocks.ROUND_MIRROR);
    public static final DeferredItem<BlockItem> CRACKED_MIRROR = ITEMS.registerSimpleBlockItem("cracked_mirror", ModBlocks.CRACKED_MIRROR);
    public static final DeferredItem<BlockItem> VANITY_MIRROR = ITEMS.registerSimpleBlockItem("vanity_mirror", ModBlocks.VANITY_MIRROR);

    public static final DeferredItem<BlockItem> DANCE_FLOOR = ITEMS.registerSimpleBlockItem("dance_floor", ModBlocks.DANCE_FLOOR);

    public static final DeferredItem<BlockItem> FOG_MACHINE = ITEMS.registerSimpleBlockItem("fog_machine", ModBlocks.FOG_MACHINE);

    public static final DeferredItem<BlockItem> SPEAKER = ITEMS.registerSimpleBlockItem("speaker", ModBlocks.SPEAKER);

    public static final DeferredItem<BlockItem> AQUARIUM = ITEMS.registerSimpleBlockItem("aquarium", ModBlocks.AQUARIUM);

    public static final DeferredItem<BlockItem> OCEAN_AQUARIUM = ITEMS.registerSimpleBlockItem("ocean_aquarium", ModBlocks.OCEAN_AQUARIUM);
    public static final DeferredItem<BlockItem> VANILLA_AQUARIUM = ITEMS.registerSimpleBlockItem("vanilla_aquarium", ModBlocks.VANILLA_AQUARIUM);
    public static final DeferredItem<BlockItem> DEEP_SEA_AQUARIUM = ITEMS.registerSimpleBlockItem("deep_sea_aquarium", ModBlocks.DEEP_SEA_AQUARIUM);
    public static final DeferredItem<BlockItem> REEF_AQUARIUM = ITEMS.registerSimpleBlockItem("reef_aquarium", ModBlocks.REEF_AQUARIUM);
    public static final DeferredItem<BlockItem> JUNGLE_AQUARIUM = ITEMS.registerSimpleBlockItem("jungle_aquarium", ModBlocks.JUNGLE_AQUARIUM);
    public static final DeferredItem<BlockItem> AQUARIUM_STAND = ITEMS.registerSimpleBlockItem("aquarium_stand", ModBlocks.AQUARIUM_STAND);
    public static final DeferredItem<BlockItem> AQUARIUM_LID = ITEMS.registerSimpleBlockItem("aquarium_lid", ModBlocks.AQUARIUM_LID);
    public static final DeferredItem<BlockItem> VANILLA_OCEAN_AQUARIUM = ITEMS.registerSimpleBlockItem("vanilla_ocean_aquarium", ModBlocks.VANILLA_OCEAN_AQUARIUM);

    public static final DeferredItem<BlockItem> DISCO_BALL = ITEMS.registerSimpleBlockItem("disco_ball", ModBlocks.DISCO_BALL);

    public static final DeferredItem<BlockItem> WATER_CAUSTICS = ITEMS.registerSimpleBlockItem("water_caustics", ModBlocks.WATER_CAUSTICS);
    public static final DeferredItem<BlockItem> RAIN_GLASS = ITEMS.registerSimpleBlockItem("rain_glass", ModBlocks.RAIN_GLASS);
    public static final DeferredItem<BlockItem> LAVA_LAMP = ITEMS.registerSimpleBlockItem("lava_lamp", ModBlocks.LAVA_LAMP);
    public static final DeferredItem<BlockItem> PLASMA_BALL = ITEMS.registerSimpleBlockItem("plasma_ball", ModBlocks.PLASMA_BALL);
    public static final DeferredItem<BlockItem> MAGMA_BASIN = ITEMS.registerSimpleBlockItem("magma_basin", ModBlocks.MAGMA_BASIN);
    public static final DeferredItem<BlockItem> BLACK_HOLE = ITEMS.registerSimpleBlockItem("black_hole", ModBlocks.BLACK_HOLE);
    public static final DeferredItem<BlockItem> BIG_BLACK_HOLE = ITEMS.registerSimpleBlockItem("big_black_hole", ModBlocks.BIG_BLACK_HOLE);
    public static final DeferredItem<BlockItem> CONTAINMENT_FRAME = ITEMS.registerSimpleBlockItem("containment_frame", ModBlocks.CONTAINMENT_FRAME);
    public static final DeferredItem<BlockItem> WHIRLPOOL = ITEMS.registerSimpleBlockItem("whirlpool", ModBlocks.WHIRLPOOL,
            new net.minecraft.world.item.Item.Properties().stacksTo(1).fireResistant());
    public static final DeferredItem<BlockItem> TANK_FRAME = ITEMS.registerSimpleBlockItem("tank_frame", ModBlocks.TANK_FRAME);
    public static final DeferredItem<BlockItem> TANK_GLASS = ITEMS.registerSimpleBlockItem("tank_glass", ModBlocks.TANK_GLASS);
    public static final DeferredItem<BlockItem> PORT_FRAME = ITEMS.registerSimpleBlockItem("port_frame", ModBlocks.PORT_FRAME);
    public static final DeferredItem<BlockItem> DISPLAY_FRAME = ITEMS.registerSimpleBlockItem("display_frame", ModBlocks.DISPLAY_FRAME);
    public static final DeferredItem<BlockItem> MONITOR_FRAME = ITEMS.registerSimpleBlockItem("monitor_frame", ModBlocks.MONITOR_FRAME);
    public static final DeferredItem<BlockItem> REDSTONE_FRAME = ITEMS.registerSimpleBlockItem("redstone_frame", ModBlocks.REDSTONE_FRAME);
    public static final DeferredItem<BlockItem> SINGULARITY = ITEMS.registerSimpleBlockItem("singularity", ModBlocks.SINGULARITY,
            new net.minecraft.world.item.Item.Properties().stacksTo(1).fireResistant());
    public static final DeferredItem<BlockItem> BIG_LAVA_LAMP = ITEMS.registerSimpleBlockItem("big_lava_lamp", ModBlocks.BIG_LAVA_LAMP);
    public static final DeferredItem<BlockItem> FIRE_TORNADO = ITEMS.registerSimpleBlockItem("fire_tornado", ModBlocks.FIRE_TORNADO);
    public static final DeferredItem<BlockItem> BRICK_HEARTH = ITEMS.registerSimpleBlockItem("brick_hearth", ModBlocks.BRICK_HEARTH);
    public static final DeferredItem<BlockItem> MUSIC_VISUALIZER = ITEMS.registerSimpleBlockItem("music_visualizer", ModBlocks.MUSIC_VISUALIZER);
    public static final DeferredItem<BlockItem> HOLOGRAM_PROJECTOR = ITEMS.registerSimpleBlockItem("hologram_projector", ModBlocks.HOLOGRAM_PROJECTOR);
    public static final DeferredItem<BlockItem> JELLYFISH_TANK = ITEMS.registerSimpleBlockItem("jellyfish_tank", ModBlocks.JELLYFISH_TANK);
    public static final DeferredItem<BlockItem> KOI_POND = ITEMS.registerSimpleBlockItem("koi_pond", ModBlocks.KOI_POND);
    public static final DeferredItem<BlockItem> VANILLA_KOI_POND = ITEMS.registerSimpleBlockItem("vanilla_koi_pond", ModBlocks.VANILLA_KOI_POND);
    public static final DeferredItem<BlockItem> BAMBOO_SPOUT = ITEMS.registerSimpleBlockItem("bamboo_spout", ModBlocks.BAMBOO_SPOUT);
    public static final DeferredItem<BlockItem> MUSIC_BOX = ITEMS.registerSimpleBlockItem("music_box", ModBlocks.MUSIC_BOX);
    public static final DeferredItem<BlockItem> POND_STEPPING_STONE = ITEMS.registerSimpleBlockItem("pond_stepping_stone", ModBlocks.POND_STEPPING_STONE);
    public static final DeferredItem<BlockItem> POND_BRIDGE = ITEMS.registerSimpleBlockItem("pond_bridge", ModBlocks.POND_BRIDGE);
    public static final DeferredItem<BlockItem> NEON_SIGN = ITEMS.registerSimpleBlockItem("neon_sign", ModBlocks.NEON_SIGN);

    public static final DeferredItem<BlockItem> INFINITY_MIRROR = ITEMS.registerSimpleBlockItem("infinity_mirror", ModBlocks.INFINITY_MIRROR);

    public static final DeferredItem<BlockItem> INFINITY_CUBE = ITEMS.registerSimpleBlockItem("infinity_cube", ModBlocks.INFINITY_CUBE);

    public static final DeferredItem<BlockItem> FISHBOWL = ITEMS.registerSimpleBlockItem("fishbowl", ModBlocks.FISHBOWL);
    public static final DeferredItem<BlockItem> TALL_TANK = ITEMS.registerSimpleBlockItem("tall_tank", ModBlocks.TALL_TANK);
    public static final DeferredItem<BlockItem> SLIM_TANK = ITEMS.registerSimpleBlockItem("slim_tank", ModBlocks.SLIM_TANK);

    public static final DeferredItem<BlockItem> TESLA_COIL = ITEMS.registerSimpleBlockItem("tesla_coil", ModBlocks.TESLA_COIL);
    public static final DeferredItem<BlockItem> GALAXY_PROJECTOR = ITEMS.registerSimpleBlockItem("galaxy_projector", ModBlocks.GALAXY_PROJECTOR);

    public static final DeferredItem<BlockItem> PORTAL_MIRROR = ITEMS.registerSimpleBlockItem("portal_mirror", ModBlocks.PORTAL_MIRROR);
    /** The effect block: put against another, joins its effect (EffectBlockItem). */
    public static final DeferredItem<EffectBlockItem> EFFECT_BLOCK = ITEMS.register("effect_block",
            () -> new EffectBlockItem(ModBlocks.EFFECT_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<EffectBlockItem> EFFECT_GLASS = ITEMS.register("effect_glass",
            () -> new EffectBlockItem(ModBlocks.EFFECT_GLASS.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> WEATHER_GLOBE = ITEMS.registerSimpleBlockItem("weather_globe", ModBlocks.WEATHER_GLOBE);
    public static final DeferredItem<BlockItem> KALEIDOSCOPE = ITEMS.registerSimpleBlockItem("kaleidoscope", ModBlocks.KALEIDOSCOPE);
    public static final DeferredItem<BlockItem> ORRERY = ITEMS.registerSimpleBlockItem("orrery", ModBlocks.ORRERY);
    public static final DeferredItem<BlockItem> PLANETARIUM = ITEMS.registerSimpleBlockItem("planetarium", ModBlocks.PLANETARIUM);

    private ModItems() {}
}
