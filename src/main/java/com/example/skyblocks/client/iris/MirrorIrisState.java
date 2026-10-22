package com.example.skyblocks.client.iris;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.joml.Matrix4f;

/**
 * While a mirror draws with a shader pack, the pack's "where am I in the world" matrices (gbufferModelView,
 * gbufferProjection and their inverses, the sun's direction...) are set to the mirror's. Packs work out each pixel's
 * world position from them (for shadows, waving plants...): with the real camera's, the ground in a mirror took the
 * shadows from the other side of the mirror. The model-view given keeps positions relative to the real camera
 * (cameraPosition stays the same, and so does the shadow map, which is centred on it). Programs re-read these matrices
 * once a frame; mixin/sodium/OculusUniformsMixin makes them read again whenever {@link #epoch} changes.
 */
public final class MirrorIrisState {
    /** Changes each time the matrices are swapped (programs then upload them again). */
    public static volatile int epoch;
    private static boolean looked, usable;
    private static Object state;
    private static Method getModelView, setModelView, getProjection, setProjection;
    private static Matrix4f savedModelView, savedProjection;

    private MirrorIrisState() {}

    /**
     * The mirror's matrices: {@code modelView} turns positions relative to the mirrored eye (rx, ry, rz) into its view;
     * (cx, cy, cz): the real camera.
     */
    public static void mirror(Matrix4f modelView, Matrix4f projection, double rx, double ry, double rz, double cx, double cy, double cz) {
        if (!look()) {
            return;
        }
        try {
            if (savedModelView == null) {
                savedModelView = new Matrix4f((Matrix4f) getModelView.invoke(state));
                savedProjection = new Matrix4f((Matrix4f) getProjection.invoke(state));
            }
            // positions relative to the real camera -> relative to the mirrored eye -> the mirror's view
            Matrix4f view = new Matrix4f(modelView).translate((float) (cx - rx), (float) (cy - ry), (float) (cz - rz));
            setModelView.invoke(state, view);
            setProjection.invoke(state, new Matrix4f(projection));
            epoch++;
        } catch (ReflectiveOperationException | RuntimeException e) {
            usable = false;
        }
    }

    /** The real view's matrices back (after the mirrors). */
    public static void restore() {
        if (savedModelView == null || !usable) {
            savedModelView = null;
            return;
        }
        try {
            setModelView.invoke(state, savedModelView);
            setProjection.invoke(state, savedProjection);
        } catch (ReflectiveOperationException | RuntimeException e) {
            usable = false;
        } finally {
            savedModelView = null;
            savedProjection = null;
            epoch++;
        }
    }

    private static boolean look() {
        if (!looked) {
            looked = true;
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
                Field instance = c.getField("INSTANCE");
                state = instance.get(null);
                getModelView = c.getMethod("getGbufferModelView");
                setModelView = c.getMethod("setGbufferModelView", Matrix4f.class);
                getProjection = c.getMethod("getGbufferProjection");
                setProjection = c.getMethod("setGbufferProjection", Matrix4f.class);
                usable = state != null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                usable = false;
            }
        }
        return usable;
    }
}
