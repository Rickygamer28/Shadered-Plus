#version 150

// The Ink Swirl sky: The Valley Mod's Colored Fluid (ink swirl style; TVM by D0nkey100 - used
// with permission; shaders/include/tvm_fluid.glsl), as a Shadered+ shader sky with its own colours.

uniform float Time;     // ticks

in vec3 dir;

out vec4 fragColor;

#moj_import <skyblocks:tvm_fluid.glsl>

void main() {
    fluidLiquid[0] = vec3(0.95, 0.15, 0.55);
    fluidLiquid[1] = vec3(0.10, 0.75, 0.95);
    fluidLiquid[2] = vec3(1.00, 0.80, 0.20);
    fluidLiquid[3] = vec3(0.45, 0.20, 0.90);
    fluidCount = 4;
    fragColor = vec4(coloredFluid(dir, 0, vec3(0.02, 0.02, 0.06), Time / 20.0, 3.0), 1.0);
}
