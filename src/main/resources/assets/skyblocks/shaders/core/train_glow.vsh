#version 150

// Light along a maglev's rails, and a station's board edge (client/TrainRenderer): UV0.x across (-1 .. 1), UV0.y along
// (blocks); Color its colour, alpha how bright. Added to what's there.

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
