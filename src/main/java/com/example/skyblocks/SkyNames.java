package com.example.skyblocks;

import java.util.Locale;

/**
 * THE list of Shadered+ skies. Add a new sky here (and in tools/skies/order.txt, same position).
 * No dependencies on purpose: it is read while Shadered's SkyblockType enum is being created.
 *
 * For each name:
 *  - Shadered sky type   SKYBLOCKS_<NAME>   (SkyblockType, added by SkyblockTypeMixin)
 *  - Shadered item type  SKYBLOCKS_<NAME>   (SkyblockItemTypes, added by SkyblockItemTypesMixin)
 *  - item                skyblocks:<name>_sky_block (a Shadered SkyblockItem)
 *  - skybox textures     skyblocks:textures/environment/<name>/
 *  - lang keys           skyblock_type.skyblocks.<name>, item.skyblocks.<name>_sky_block
 */
public final class SkyNames {
    public static final String[] NAMES = {"jupiter", "cat", "twilight", "hell", "nebula", "golden_hour", "scadu_altus", "cerulean_coast", "gravesite_plain", "mountaintops", "altus_plateau_night", "altus_plateau_sunset", "altus_plateau", "divine_tower", "caelid", "siofra_river", "limgrave_sunset", "limgrave_rainy", "academy_of_raya_lucaria", "liurnia_sunset", "liurnia_rainy", "liurnia", "chaos", "synthwave", "synthwave_outrun", "synthwave_night", "synthwave_miami", "darksynth", "synthwave_terminal", "vaporwave", "aurora", "underwater", "swirl", "light_cycle", "universe_within", "data_cube", "end_portal", "end_gateway", "classic_end_portal", "ink_swirl", "lava_blobs", "marble", "studio_1", "studio_2", "studio_3", "studio_4", "studio_5", "studio_6", "studio_7", "studio_8"};

    /** The chaos sky: not a picture but Draconic Evolution's chaos shader (adapted with credit, client/ShaderSky). */
    public static final String CHAOS = "chaos";

    /** The synthwave sky: Shadered+'s own shader (client/ShaderSky). */
    public static final String SYNTHWAVE = "synthwave";

    /** The aurora sky and the underwater sky: Shadered+'s own shaders (client/ShaderSky). */
    public static final String AURORA = "aurora", UNDERWATER = "underwater";

    /**
     * The Valley Mod's skyboxes (TVM, by D0nkey100 - used with permission; the Shadertoy ones by their own authors, see
     * CREDITS.md): shader skies (client/ShaderSky, shaders/include/tvm_skies.glsl).
     */
    public static final String[] TVM = {"swirl", "light_cycle", "universe_within", "data_cube", "end_portal", "end_gateway", "classic_end_portal", "ink_swirl", "lava_blobs", "marble"};

    /** The Sky Studio's skies (StudioSkies): drawn by one shader from settings players make (client/ShaderSky). */
    public static boolean studio(String name) {
        return name.startsWith("studio_");
    }

    public static boolean tvm(String name) {
        return java.util.Arrays.asList(TVM).contains(name);
    }

    /** Position of the chaos sky in NAMES. */
    public static int chaosIndex() {
        return java.util.Arrays.asList(NAMES).indexOf(CHAOS);
    }

    public static String enumName(int index) {
        return "SKYBLOCKS_" + NAMES[index].toUpperCase(Locale.ROOT);
    }

    public static String itemId(int index) {
        return NAMES[index] + "_sky_block";
    }

    private SkyNames() {}
}
