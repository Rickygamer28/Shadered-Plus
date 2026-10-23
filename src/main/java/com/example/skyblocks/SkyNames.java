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
    public static final String[] NAMES = {"jupiter", "cat", "twilight", "hell", "nebula", "golden_hour", "scadu_altus", "cerulean_coast", "gravesite_plain", "mountaintops", "altus_plateau_night", "altus_plateau_sunset", "altus_plateau", "divine_tower", "caelid", "siofra_river", "limgrave_sunset", "limgrave_rainy", "academy_of_raya_lucaria", "liurnia_sunset", "liurnia_rainy", "liurnia", "chaos", "synthwave", "synthwave_outrun", "synthwave_night", "synthwave_miami", "darksynth", "synthwave_terminal", "vaporwave"};

    /** The chaos sky: not a picture but Draconic Evolution's chaos shader (adapted with credit, client/ShaderSky). */
    public static final String CHAOS = "chaos";

    /** The synthwave sky: Shadered+'s own shader (client/ShaderSky). */
    public static final String SYNTHWAVE = "synthwave";

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
