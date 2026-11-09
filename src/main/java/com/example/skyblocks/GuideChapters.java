package com.example.skyblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Shadered+'s blocks, like with like: the creative tab's order and the guide book's chapters. (The structures - things
 * built of several blocks - are pages of their own in the guide, and in JEI.)
 */
public final class GuideChapters {
    public record Chapter(String id, ItemStack icon, List<ItemStack> items) {}

    private GuideChapters() {}

    @SafeVarargs
    private static Chapter of(String id, Supplier<? extends ItemLike>... items) {
        List<ItemStack> list = new ArrayList<>();
        for (Supplier<? extends ItemLike> i : items) {
            list.add(new ItemStack(i.get()));
        }
        return new Chapter(id, list.get(0).copy(), list);
    }

    public static List<Chapter> all() {
        List<Chapter> out = new ArrayList<>();
        out.add(of("lights", ModItems.EFFECT_BLOCK, ModItems.EFFECT_GLASS, ModItems.NEON_SIGN, ModItems.LAVA_LAMP, ModItems.BIG_LAVA_LAMP,
                ModItems.LAVA_PILLAR, ModItems.PLASMA_BALL, ModItems.TESLA_COIL, ModItems.KALEIDOSCOPE, ModItems.HOLOGRAM_PROJECTOR,
                ModItems.GALAXY_PROJECTOR, ModItems.WEATHER_GLOBE, ModItems.NEON_TUBE));
        out.add(of("music", ModItems.DISCO_BALL, ModItems.DANCE_FLOOR, ModItems.LASER_PROJECTOR, ModItems.FOG_MACHINE, ModItems.SPEAKER,
                ModItems.MUSIC_VISUALIZER, ModItems.MUSIC_BOX, ModItems.ARCADE, ModItems.CLAW_MACHINE, ModItems.STAGE, ModItems.SPOTLIGHT));
        out.add(of("fire_water", ModItems.BRICK_HEARTH, ModItems.FIRE_TORNADO, ModItems.MAGMA_BASIN, ModItems.WATER_CAUSTICS,
                ModItems.RAIN_GLASS, ModItems.WATERFALL));
        out.add(of("aquariums", ModItems.AQUARIUM, ModItems.OCEAN_AQUARIUM, ModItems.DEEP_SEA_AQUARIUM, ModItems.REEF_AQUARIUM,
                ModItems.JUNGLE_AQUARIUM, ModItems.VANILLA_AQUARIUM, ModItems.VANILLA_OCEAN_AQUARIUM, ModItems.AQUARIUM_STAND,
                ModItems.AQUARIUM_LID, ModItems.FISHBOWL, ModItems.TALL_TANK, ModItems.SLIM_TANK, ModItems.JELLYFISH_TANK, ModItems.TERRARIUM));
        out.add(of("ponds", ModItems.KOI_POND, ModItems.VANILLA_KOI_POND, ModItems.POND_STEPPING_STONE, ModItems.POND_BRIDGE,
                ModItems.BAMBOO_SPOUT));
        out.add(of("space", ModItems.ORRERY, ModItems.PLANETARIUM, ModItems.BLACK_HOLE, ModItems.BIG_BLACK_HOLE, ModItems.SINGULARITY,
                ModItems.CONTAINMENT_FRAME, ModItems.PORT_FRAME, ModItems.DISPLAY_FRAME, ModItems.MONITOR_FRAME, ModItems.REDSTONE_FRAME,
                ModItems.WHIRLPOOL, ModItems.TANK_FRAME, ModItems.TANK_GLASS));
        out.add(of("mirrors", ModItems.MIRROR, ModItems.ROUND_MIRROR, ModItems.CRACKED_MIRROR, ModItems.VANITY_MIRROR,
                ModItems.INFINITY_MIRROR, ModItems.INFINITY_CUBE, ModItems.PORTAL_MIRROR, ModItems.LIGHT_BRIDGE_EMITTER));
        List<ItemStack> toys = new ArrayList<>();
        for (var p : ModItems.PLUSHIES) {
            toys.add(new ItemStack(p.get()));
        }
        toys.add(new ItemStack(ModItems.TROPHY.get()));
        out.add(new Chapter("toys", toys.get(0).copy(), toys));
        out.add(of("islands", ModItems.ISLAND_SCANNER, ModItems.ISLAND_PAINTING, ModItems.OIIA_CAT));
        List<ItemStack> skies = SkyBlocksMod.skyStacks();
        out.add(new Chapter("skies", skies.isEmpty() ? new ItemStack(ModItems.GUIDE_BOOK.get()) : skies.get(0).copy(), skies));
        return out;
    }
}
