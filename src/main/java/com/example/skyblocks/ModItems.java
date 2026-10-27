package com.example.skyblocks;

import com.example.skyblocks.island.SkyIslandItem;

import com.noodlegamer76.shadered.block.InitBlocks;
import com.noodlegamer76.shadered.item.SkyblockItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * One Shadered skyblock item per sky. They are real Shadered SkyblockItems: they place Shadered's own
 * skyblock with our sky type, use Shadered's item renderer, work in the Sky Emitter, with filters, etc.
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SkyBlocksMod.MOD_ID);

    public static final List<RegistryObject<SkyblockItem>> SKY_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < SkyNames.NAMES.length; i++) {
            final int index = i;
            SKY_ITEMS.add(ITEMS.register(SkyNames.itemId(i),
                    () -> new SkyblockItem(InitBlocks.SKYBLOCK.get(), new Item.Properties(), SkyTypes.itemType(index))));
        }
    }

    /** The OIIA cat model block (its renderer is registered in client/ClientSetup). */
    public static final RegistryObject<BlockItem> OIIA_CAT = ITEMS.register("oiia_cat",
            () -> new OiiaCatBlockItem(ModBlocks.OIIA_CAT.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> ISLAND_SCANNER = ITEMS.register("island_scanner",
            () -> new BlockItem(ModBlocks.ISLAND_SCANNER.get(), new Item.Properties()));

    /** Holds a scanned island (only made by the Island Scanner). */
    public static final RegistryObject<SkyIslandItem> SKY_ISLAND = ITEMS.register("sky_island",
            () -> new SkyIslandItem(ModBlocks.SKY_ISLAND.get(), new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockItem> ISLAND_PAINTING = ITEMS.register("island_painting",
            () -> new BlockItem(ModBlocks.ISLAND_PAINTING.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> MIRROR = ITEMS.register("mirror",
            () -> new BlockItem(ModBlocks.MIRROR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> DANCE_FLOOR = ITEMS.register("dance_floor",
            () -> new BlockItem(ModBlocks.DANCE_FLOOR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> FOG_MACHINE = ITEMS.register("fog_machine",
            () -> new BlockItem(ModBlocks.FOG_MACHINE.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> SPEAKER = ITEMS.register("speaker",
            () -> new BlockItem(ModBlocks.SPEAKER.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> AQUARIUM = ITEMS.register("aquarium",
            () -> new BlockItem(ModBlocks.AQUARIUM.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> OCEAN_AQUARIUM = ITEMS.register("ocean_aquarium",
            () -> new BlockItem(ModBlocks.OCEAN_AQUARIUM.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> VANILLA_AQUARIUM = ITEMS.register("vanilla_aquarium",
            () -> new BlockItem(ModBlocks.VANILLA_AQUARIUM.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> DEEP_SEA_AQUARIUM = ITEMS.register("deep_sea_aquarium",
            () -> new BlockItem(ModBlocks.DEEP_SEA_AQUARIUM.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> AQUARIUM_STAND = ITEMS.register("aquarium_stand",
            () -> new BlockItem(ModBlocks.AQUARIUM_STAND.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> AQUARIUM_LID = ITEMS.register("aquarium_lid",
            () -> new BlockItem(ModBlocks.AQUARIUM_LID.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> VANILLA_OCEAN_AQUARIUM = ITEMS.register("vanilla_ocean_aquarium",
            () -> new BlockItem(ModBlocks.VANILLA_OCEAN_AQUARIUM.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> DISCO_BALL = ITEMS.register("disco_ball",
            () -> new RenderedBlockItem(ModBlocks.DISCO_BALL.get(), new Item.Properties(), "disco_ball"));

    public static final RegistryObject<BlockItem> WATER_CAUSTICS = ITEMS.register("water_caustics",
            () -> new BlockItem(ModBlocks.WATER_CAUSTICS.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> RAIN_GLASS = ITEMS.register("rain_glass",
            () -> new BlockItem(ModBlocks.RAIN_GLASS.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> LAVA_LAMP = ITEMS.register("lava_lamp",
            () -> new RenderedBlockItem(ModBlocks.LAVA_LAMP.get(), new Item.Properties(), "lava_lamp"));
    public static final RegistryObject<BlockItem> BIG_LAVA_LAMP = ITEMS.register("big_lava_lamp",
            () -> new RenderedBlockItem(ModBlocks.BIG_LAVA_LAMP.get(), new Item.Properties(), "big_lava_lamp"));
    public static final RegistryObject<BlockItem> FIRE_TORNADO = ITEMS.register("fire_tornado",
            () -> new RenderedBlockItem(ModBlocks.FIRE_TORNADO.get(), new Item.Properties(), "fire_tornado"));
    public static final RegistryObject<BlockItem> BRICK_HEARTH = ITEMS.register("brick_hearth",
            () -> new BlockItem(ModBlocks.BRICK_HEARTH.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> MUSIC_VISUALIZER = ITEMS.register("music_visualizer",
            () -> new BlockItem(ModBlocks.MUSIC_VISUALIZER.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> HOLOGRAM_PROJECTOR = ITEMS.register("hologram_projector",
            () -> new BlockItem(ModBlocks.HOLOGRAM_PROJECTOR.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> JELLYFISH_TANK = ITEMS.register("jellyfish_tank",
            () -> new RenderedBlockItem(ModBlocks.JELLYFISH_TANK.get(), new Item.Properties(), "jellyfish_tank"));
    public static final RegistryObject<BlockItem> KOI_POND = ITEMS.register("koi_pond",
            () -> new BlockItem(ModBlocks.KOI_POND.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> VANILLA_KOI_POND = ITEMS.register("vanilla_koi_pond",
            () -> new BlockItem(ModBlocks.VANILLA_KOI_POND.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> BAMBOO_SPOUT = ITEMS.register("bamboo_spout",
            () -> new BlockItem(ModBlocks.BAMBOO_SPOUT.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> MUSIC_BOX = ITEMS.register("music_box",
            () -> new BlockItem(ModBlocks.MUSIC_BOX.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> POND_STEPPING_STONE = ITEMS.register("pond_stepping_stone",
            () -> new BlockItem(ModBlocks.POND_STEPPING_STONE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> POND_BRIDGE = ITEMS.register("pond_bridge",
            () -> new BlockItem(ModBlocks.POND_BRIDGE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> NEON_SIGN = ITEMS.register("neon_sign",
            () -> new BlockItem(ModBlocks.NEON_SIGN.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> INFINITY_MIRROR = ITEMS.register("infinity_mirror",
            () -> new BlockItem(ModBlocks.INFINITY_MIRROR.get(), new Item.Properties()));

    public static final RegistryObject<BlockItem> INFINITY_CUBE = ITEMS.register("infinity_cube",
            () -> new BlockItem(ModBlocks.INFINITY_CUBE.get(), new Item.Properties()));

    private ModItems() {}
}
