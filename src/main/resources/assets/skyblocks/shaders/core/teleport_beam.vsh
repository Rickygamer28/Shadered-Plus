#version 150

// Shadered+'s teleport pad beam: a column of light (UV0.x round it 0-1, UV0.y up it 0-1). Color: its colour, its
// strength in alpha.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 col;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    col = Color;
}
