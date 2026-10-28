package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/** Client things 1.21.1 has that 1.20.1 does differently (for the code ported from 1.21.1). */
public final class ClientCompat {
    private ClientCompat() {}

    /** A model part in a colour (0xAARRGGBB; 1.21: ModelPart.render(..., colour)). */
    public static void render(ModelPart part, PoseStack pose, VertexConsumer vc, int light, int overlay, int argb) {
        part.render(pose, vc, light, overlay, ((argb >> 16) & 255) / 255.0F, ((argb >> 8) & 255) / 255.0F, (argb & 255) / 255.0F,
                ((argb >>> 24) & 255) / 255.0F);
    }

    /** A model in a colour (0xAARRGGBB; 1.21: Model.renderToBuffer(..., colour)). */
    public static void render(Model model, PoseStack pose, VertexConsumer vc, int light, int overlay, int argb) {
        model.renderToBuffer(pose, vc, light, overlay, ((argb >> 16) & 255) / 255.0F, ((argb >> 8) & 255) / 255.0F, (argb & 255) / 255.0F,
                ((argb >>> 24) & 255) / 255.0F);
    }
}
