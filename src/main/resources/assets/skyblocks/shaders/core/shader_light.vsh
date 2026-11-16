#version 150

// The shader lights (client/ShaderLightRenderer): one sheet over the whole screen.

in vec3 Position;

out vec2 screenUv;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    screenUv = Position.xy * 0.5 + 0.5;
}
