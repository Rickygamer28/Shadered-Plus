#version 150

// The Sky Studio screen's preview of the sky being made (include/studio_sky.glsl): View = the camera's turn and tilt.

#moj_import <skyblocks:studio_sky.glsl>

uniform float Time;
uniform vec2 View;

in vec2 uv;

out vec4 fragColor;

void main() {
    float yaw = View.x + (uv.x - 0.5) * 1.9;
    float pitch = View.y + (0.5 - uv.y) * 1.05;
    vec3 d = vec3(cos(pitch) * sin(yaw), sin(pitch), cos(pitch) * cos(yaw));
    fragColor = vec4(studioSky(normalize(d), Time), 1.0);
}
