package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;
import com.noodlegamer76.shadered.client.renderer.assimp.AssimpRenderer;
import com.noodlegamer76.shadered.client.renderer.assimp.RenderableModel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Hands a model to Shadered's renderer, or, when Shadered+ is drawing the inside of an Island Painting, to Shadered+'s
 * batch for that painting (models drawn later at their usual time would end up behind the wall). Shadered+ is
 * optional: everything goes through reflection and falls back to the normal queue.
 */
final class IslandCompat {
    private static boolean looked;
    private static Field inWindow;
    private static Method addToWindow;

    private IslandCompat() {}

    static void draw(RenderableModel model) {
        look();
        if (inWindow != null && addToWindow != null) {
            try {
                if (inWindow.getBoolean(null)) {
                    addToWindow.invoke(null, model);
                    return;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                inWindow = null;
            }
        }
        AssimpRenderer.getInstance().addModel(model);
    }

    private static void look() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            inWindow = Class.forName("com.example.skyblocks.client.island.IslandRendering").getField("inWindow");
            addToWindow = Class.forName("com.example.skyblocks.client.IslandCatBatch").getDeclaredMethod("add", RenderableModel.class);
            addToWindow.setAccessible(true);
            ZinogreMod.LOGGER.info("Zinogre: Shadered+ found, Zinogres show inside Island Paintings too");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            inWindow = null;
            addToWindow = null; // no Shadered+ (or a version without the batch)
        }
    }
}
