#version 150

// Adapted from Draconic Evolution by brandon3055 (https://github.com/Draconic-Inc/Draconic-Evolution), used under its
// "Don't Be a Jerk" licence (portions of the code may be copied into other projects; source kept public).
// Shadered+ credits brandon3055 / Draconic Evolution in the item's tooltip. All original rights his.
// (chaos_block.fsh drawn as a Shadered sky; the texture it reads is Shadered+'s own, not Draconic Evolution's.)

#define CHAOS_FULL_BRIGHT
#moj_import <skyblocks:brandon_chaos.glsl>

uniform sampler2D Sampler0;
uniform float Time;

in vec3 dir;

out vec4 fragColor;

void main() {
    // (the direction is already the world's, so no turning by the camera's yaw / pitch)
    fragColor = chaos(Sampler0, Time, 0.0, 0.0, 0.0, dir, vec2(0.0));
}
