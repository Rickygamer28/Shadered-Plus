// Adapted from Shadered Compat Fix (github.com/Rickygamer28/shadered-compat-fix, version 13), MIT License.
package com.example.skyblocks.client.iris;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Talks to Iris through its public API ({@code net.irisshaders.iris.api.v0.IrisApi}) by reflection, so
 * this addon doesn't need Iris installed to load. Every method returns false when Iris isn't there.
 */
public final class IrisCompat {
    private static boolean initialized;
    private static Object api;
    private static Method shaderPackInUse;
    private static Method renderingShadowPass;

    private IrisCompat() {}

    private static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // Forge 1.20.1: Oculus (Iris for Forge) has the same API
        if (!ModList.get().isLoaded("oculus") && !ModList.get().isLoaded("iris")) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            shaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            renderingShadowPass = apiClass.getMethod("isRenderingShadowPass");
            com.example.skyblocks.SkyBlocksMod.LOGGER.info("Iris detected; Island Paintings and island cats will be drawn on top of shader packs");
        }
        catch (ReflectiveOperationException | LinkageError e) {
            api = null;
            com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Iris is installed but its API couldn't be loaded; Iris support disabled", e);
        }
    }

    /** True while an Iris shader pack is active (not just Iris installed). */
    public static boolean shaderPackInUse() {
        init();
        return call(shaderPackInUse);
    }

    /** True while Iris is rendering its shadow map (the world from the sun's point of view). */
    private static java.lang.reflect.Method getPipeline, getPhase, setPhase;
    private static boolean phaseLooked;

    /** The shader pack pipeline's current rendering phase (to put back after drawing the world again), or null. */
    public static Object phase() {
        try {
            Object pipeline = pipeline();
            return pipeline == null ? null : getPhase.invoke(pipeline);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** Puts back a phase from {@link #phase} (drawing the sky for a mirror moves the pipeline to the sky's phases). */
    public static void phase(Object phase) {
        if (phase == null) {
            return;
        }
        try {
            Object pipeline = pipeline();
            if (pipeline != null) {
                setPhase.invoke(pipeline, phase);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // left as it is
        }
    }

    private static Object pipeline() throws ReflectiveOperationException {
        if (!phaseLooked) {
            phaseLooked = true;
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object manager = iris.getMethod("getPipelineManager").invoke(null);
            getPipeline = manager.getClass().getMethod("getPipelineNullable");
            Class<?> pipelineClass = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline");
            Class<?> phaseClass = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase");
            getPhase = pipelineClass.getMethod("getPhase");
            setPhase = pipelineClass.getMethod("setPhase", phaseClass);
        }
        if (getPipeline == null) {
            return null;
        }
        Object manager = Class.forName("net.irisshaders.iris.Iris").getMethod("getPipelineManager").invoke(null);
        return getPipeline.invoke(manager);
    }

    public static boolean renderingShadowPass() {
        init();
        return call(renderingShadowPass);
    }

    private static boolean call(Method method) {
        if (api == null || method == null) {
            return false;
        }
        try {
            return (Boolean) method.invoke(api);
        }
        catch (ReflectiveOperationException | ClassCastException e) {
            return false;
        }
    }
}
