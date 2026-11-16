#version 150

// The Lava Blobs sky: The Valley Mod's Colored Fluid (lava lamp blobs style; TVM by D0nkey100 - used
// with permission; shaders/include/tvm_fluid.glsl), as a Shadered+ shader sky with its own colours.

uniform float Time;     // ticks

in vec3 dir;

out vec4 fragColor;

#moj_import <skyblocks:tvm_fluid.glsl>

void main() {
    fluidLiquid[0] = vec3(1.00, 0.35, 0.05);
    fluidLiquid[1] = vec3(0.95, 0.10, 0.25);
    fluidLiquid[2] = vec3(1.00, 0.75, 0.10);
    fluidCount = 3;
    fragColor = vec4(coloredFluid(dir, 1, vec3(0.10, 0.02, 0.12), Time / 20.0, 7.0), 1.0);
}
