package com.example.skyblocks.compat;

import net.minecraft.world.item.DyeColor;

/** Small things 1.21.1 has that 1.20.1 does differently (for the code ported from 1.21.1). */
public final class Compat {
    private Compat() {}

    /** A dye's colour as 0xAARRGGBB (1.21: com.example.skyblocks.compat.Compat.dyeColour(DyeColor)). */
    public static int dyeColour(DyeColor dye) {
        float[] c = dye.getTextureDiffuseColors();
        return 0xFF000000 | Math.round(c[0] * 255.0F) << 16 | Math.round(c[1] * 255.0F) << 8 | Math.round(c[2] * 255.0F);
    }
}
