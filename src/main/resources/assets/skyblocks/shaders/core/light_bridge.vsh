#version 150

// Shadered+'s light bridge: the plate of hard light. UV0: along it (blocks), across it (0-1); Color: the face's shade.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out float shade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    shade = Color.r;
}
