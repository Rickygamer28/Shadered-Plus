package com.example.skyblocks;

import java.util.List;

/** The client's copy of the Sky Studio's skies (StudioSkies): names and settings, per slot. */
public final class StudioSkiesStore {
    public static final String[] NAMES = new String[StudioSkies.SLOTS];
    public static final float[][] PARAMS = new float[StudioSkies.SLOTS][];

    static {
        for (int i = 0; i < StudioSkies.SLOTS; i++) {
            NAMES[i] = "";
            PARAMS[i] = StudioSkies.preset(i);
        }
    }

    static void set(List<String> names, List<float[]> params) {
        for (int i = 0; i < Math.min(StudioSkies.SLOTS, names.size()); i++) {
            NAMES[i] = names.get(i);
            PARAMS[i] = params.get(i);
        }
    }

    /** A studio sky's own name for a language key of it (its sky, its sky block), or null. */
    public static String nameFor(String key) {
        if (!key.contains("studio_")) {
            return null;
        }
        for (int i = 0; i < StudioSkies.SLOTS; i++) {
            if (NAMES[i].isEmpty()) {
                continue;
            }
            String id = "studio_" + (i + 1);
            if (key.equals("skyblock_type.skyblocks." + id)) {
                return NAMES[i];
            }
            if (key.equals("item.skyblocks." + id + "_sky_block")) {
                return NAMES[i] + " Sky Block";
            }
        }
        return null;
    }

    private StudioSkiesStore() {}
}
