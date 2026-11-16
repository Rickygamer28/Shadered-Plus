#version 150

// The Marble sky: The Valley Mod's Colored Fluid (marble style; TVM by D0nkey100 - used
// with permission; shaders/include/tvm_fluid.glsl), as a Shadered+ shader sky with its own colours.

uniform float Time;     // ticks

in vec3 dir;

out vec4 fragColor;

#moj_import <skyblocks:tvm_fluid.glsl>

void main() {
    fluidLiquid[0] = vec3(0.18, 0.20, 0.24);
    fluidLiquid[1] = vec3(0.35, 0.60, 0.58);
    fluidLiquid[2] = vec3(0.80, 0.68, 0.55);
    fluidLiquid[3] = vec3(0.55, 0.55, 0.60);
    fluidCount = 4;
    fragColor = vec4(coloredFluid(dir, 2, vec3(0.97, 0.95, 0.90), Time / 20.0, 11.0), 1.0);
}
