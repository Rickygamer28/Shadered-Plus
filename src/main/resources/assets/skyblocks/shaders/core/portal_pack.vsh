#version 150

// A portal's picture packed: its colour, and in its alpha how far away each pixel is (client/MirrorRenderer).

in vec3 Position;

out vec2 uv;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    uv = Position.xy * 0.5 + 0.5;
}
