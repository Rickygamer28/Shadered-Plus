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

    public static final DeferredItem<BlockItem> DANCE_FLOOR = ITEMS.registerSimpleBlockItem("dance_floor", ModBlocks.DANCE_FLOOR);

    public static final DeferredItem<BlockItem> FOG_MACHINE = ITEMS.registerSimpleBlockItem("fog_machine", ModBlocks.FOG_MACHINE);

    public static final DeferredItem<BlockItem> SPEAKER = ITEMS.registerSimpleBlockItem("speaker", ModBlocks.SPEAKER);

    public static final DeferredItem<BlockItem> AQUARIUM = ITEMS.registerSimpleBlockItem("aquarium", ModBlocks.AQUARIUM);

    public static final DeferredItem<BlockItem> OCEAN_AQUARIUM = ITEMS.registerSimpleBlockItem("ocean_aquarium", ModBlocks.OCEAN_AQUARIUM);

    public static final DeferredItem<BlockItem> DISCO_BALL = ITEMS.registerSimpleBlockItem("disco_ball", ModBlocks.DISCO_BALL);

    public static final DeferredItem<BlockItem> WATER_CAUSTICS = ITEMS.registerSimpleBlockItem("water_caustics", ModBlocks.WATER_CAUSTICS);
    public static final DeferredItem<BlockItem> RAIN_GLASS = ITEMS.registerSimpleBlockItem("rain_glass", ModBlocks.RAIN_GLASS);
    public static final DeferredItem<BlockItem> LAVA_LAMP = ITEMS.registerSimpleBlockItem("lava_lamp", ModBlocks.LAVA_LAMP);
    public static final DeferredItem<BlockItem> BIG_LAVA_LAMP = ITEMS.registerSimpleBlockItem("big_lava_lamp", ModBlocks.BIG_LAVA_LAMP);
    public static final DeferredItem<BlockItem> FIRE_TORNADO = ITEMS.registerSimpleBlockItem("fire_tornado", ModBlocks.FIRE_TORNADO);
    public static final DeferredItem<BlockItem> BRICK_HEARTH = ITEMS.registerSimpleBlockItem("brick_hearth", ModBlocks.BRICK_HEARTH);
    public static final DeferredItem<BlockItem> MUSIC_VISUALIZER = ITEMS.registerSimpleBlockItem("music_visualizer", ModBlocks.MUSIC_VISUALIZER);
    public static final DeferredItem<BlockItem> HOLOGRAM_PROJECTOR = ITEMS.registerSimpleBlockItem("hologram_projector", ModBlocks.HOLOGRAM_PROJECTOR);
    public static final DeferredItem<BlockItem> JELLYFISH_TANK = ITEMS.registerSimpleBlockItem("jellyfish_tank", ModBlocks.JELLYFISH_TANK);
    public static final DeferredItem<BlockItem> KOI_POND = ITEMS.registerSimpleBlockItem("koi_pond", ModBlocks.KOI_POND);
    public static final DeferredItem<BlockItem> VANILLA_KOI_POND = ITEMS.registerSimpleBlockItem("vanilla_koi_pond", ModBlocks.VANILLA_KOI_POND);
    public static final DeferredItem<BlockItem> NEON_SIGN = ITEMS.registerSimpleBlockItem("neon_sign", ModBlocks.NEON_SIGN);

    public static final DeferredItem<BlockItem> INFINITY_MIRROR = ITEMS.registerSimpleBlockItem("infinity_mirror", ModBlocks.INFINITY_MIRROR);

    public static final DeferredItem<BlockItem> INFINITY_CUBE = ITEMS.registerSimpleBlockItem("infinity_cube", ModBlocks.INFINITY_CUBE);

    private ModItems() {}
}
