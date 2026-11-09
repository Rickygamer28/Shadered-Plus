#version 150

// Shadered+'s party laser: a beam (UV0.x along 0-1, UV0.y across -1..1) or the spot where it lands (UV0.x 2-4: the
// spot's own across). Color: the beam's colour, its strength in alpha.

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
