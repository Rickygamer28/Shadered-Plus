#version 150

// The light cycle sky: one of The Valley Mod's skybox shaders (TVM, by D0nkey100 - used with permission; see
// shaders/include/tvm_skies.glsl and CREDITS.md for whose each one is), as a Shadered+ shader sky.

uniform float Time;     // ticks
uniform vec2 ScreenSize;
uniform float CamYaw;   // the camera's turn (radians)
uniform float CamPitch;
uniform float Music;    // the music here (client/MusicReactor): how loud, its bass, mids, highs
uniform float Bass;
uniform float Mid;
uniform float High;
uniform float Flight;   // how far it's flown (seconds at the normal speed; faster with the bass)
uniform sampler2D Sampler0; // the End's sky (the End Portal skies)
uniform sampler2D Sampler1; // the End Portal's stars

#define EndSkySampler Sampler0
#define EndPortalSampler Sampler1

in vec3 dir;

out vec4 fragColor;

#moj_import <skyblocks:tvm_skies.glsl>

void main() {
    tvmSetup(dir, Time, ScreenSize, CamYaw, CamPitch);
    LcMusic = Music;
    LcBass = Bass;
    LcMid = Mid;
    LcHigh = High;
    LcFlight = Flight;
    fragColor = vec4(lightCycleSkybox(), 1.0);
}
