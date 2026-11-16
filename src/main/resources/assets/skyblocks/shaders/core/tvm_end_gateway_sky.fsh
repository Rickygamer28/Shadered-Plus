#version 150

// The end gateway sky: one of The Valley Mod's skybox shaders (TVM, by D0nkey100 - used with permission; see
// shaders/include/tvm_skies.glsl and CREDITS.md for whose each one is), as a Shadered+ shader sky.

uniform float Time;     // ticks
uniform vec2 ScreenSize;
uniform float CamYaw;   // the camera's turn (radians)
uniform float CamPitch;
uniform sampler2D Sampler0; // the End's sky (the End Portal skies)
uniform sampler2D Sampler1; // the End Portal's stars

#define EndSkySampler Sampler0
#define EndPortalSampler Sampler1

in vec3 dir;

out vec4 fragColor;

#moj_import <skyblocks:tvm_skies.glsl>

void main() {
    tvmSetup(dir, Time, ScreenSize, CamYaw, CamPitch);
    fragColor = vec4(endPortalSkybox(16), 1.0);
}
