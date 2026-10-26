#version 150

// Shadered+'s music visualizer: UV0 = where in the whole joined screen (0..1 across, 0..1 up); Color = its colour
// (black: rainbow), alpha = look * 64 + how many blocks wide.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 vColor;

void main() {
    uv = UV0;
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
