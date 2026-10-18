#version 150

// Draws Island Paintings and island cats (rendered into Shadered+'s own target while an Iris shader pack is active)
// on top of the pack's final image, except where something ended up in front of them later in the frame (the held
// item, water, island blocks in front of a cat).
// Adapted from Shadered Compat Fix's iris_overlay (github.com/Rickygamer28/shadered-compat-fix, MIT License).

// Not called Sampler0: Minecraft fills Sampler0-11 from RenderSystem's shader textures on every draw.
uniform sampler2D OverlaySampler;    // paintings' colors (premultiplied, transparent where nothing was drawn)
uniform sampler2D DepthSampler;      // the depth of each painting's picture where it is visible (1.0 elsewhere)
uniform sampler2D CatSampler;        // the colors of Sky Islands' cats
uniform sampler2D CatDepthSampler;   // their depth (1.0 where there is no cat)
uniform sampler2D MainDepthSampler;  // the frame's final depth (world, entities, translucents, hand)
uniform vec2 Used;                   // x: paintings were drawn this frame, y: cats were
uniform mat4 WorldProjMat;           // projection the world was rendered with

in vec2 texCoord0;

out vec4 fragColor;

// Depth buffer value -> distance from the camera in blocks.
float distanceOf(float depth) {
    float ndc = depth * 2.0 - 1.0;
    return WorldProjMat[3][2] / (ndc + WorldProjMat[2][2]);
}

// Whether something in the frame's final depth is in front of a point at depth own (with a small margin: island
// blocks are tiny; growing with distance for the depth buffer's precision).
bool hidden(float own, float scene) {
    if (scene >= own) {
        return false;
    }
    float d = distanceOf(own);
    return distanceOf(scene) < d - max(0.02, d * 0.004);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float scene = texelFetch(MainDepthSampler, pixel, 0).r;
    vec4 result = vec4(0.0);

    if (Used.x > 0.5) {
        vec4 color = texelFetch(OverlaySampler, pixel, 0);
        if (color.a > 0.0 || dot(color.rgb, color.rgb) > 0.0) {
            // A pixel with color but no alpha (alpha writes were masked off) is solid.
            if (color.a <= 0.0) {
                color.a = 1.0;
            }
            float own = texelFetch(DepthSampler, pixel, 0).r;
            if (!(own < 1.0 && hidden(own, scene))) {
                result = color;
            }
        }
    }

    if (Used.y > 0.5) {
        float own = texelFetch(CatDepthSampler, pixel, 0).r;
        if (own < 1.0 && !hidden(own, scene)) {
            vec4 cat = texelFetch(CatSampler, pixel, 0);
            if (cat.a <= 0.0) {
                cat.a = 1.0;
            }
            result = cat + result * (1.0 - cat.a);
        }
    }

    if (result.a <= 0.0) {
        discard;
    }
    fragColor = result;
}
