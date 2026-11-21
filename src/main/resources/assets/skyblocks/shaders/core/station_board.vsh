#version 150

// A station's hologram board (client/StationRenderer): UV0 across it (0 .. 1 each way); Color its colour (by whether a
// train's here, coming, or none).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 col;
out vec2 uv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    col = Color;
    uv = UV0;
}
