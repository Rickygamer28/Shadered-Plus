#version 150

// The Sky Studio's skies (include/studio_sky.glsl), drawn round the camera like the other shader skies.

#moj_import <skyblocks:studio_sky.glsl>

uniform float Time; // ticks

in vec3 dir;

out vec4 fragColor;

void main() {
    fragColor = vec4(studioSky(normalize(dir), Time), 1.0);
}
